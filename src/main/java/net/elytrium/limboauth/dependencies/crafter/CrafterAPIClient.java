/*
 * Copyright (C) 2021 - 2025 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.limboauth.dependencies.crafter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.dependencies.crafter.model.CrafterResponse;
import net.elytrium.limboauth.dependencies.crafter.model.CrafterWebsite;
import org.slf4j.Logger;

/**
 * API client for interacting with Crafter CMS authentication system.
 */
public class CrafterAPIClient {
  private static final String USER_AGENT = "CrafterAuth";

  private final HttpClient client;
  private final String apiUrl;
  private final String licenseKey;
  private final String secretKey;
  private final Gson gson;
  private final Logger logger;

  private CrafterWebsite website;
  private boolean isInitialized = false;

  /**
   * Creates a new Crafter API client.
   *
   * @param settings The application settings
   * @param logger The logger instance
   */
  public CrafterAPIClient(Settings settings, Logger logger) {
    this.client = HttpClient.newHttpClient();
    this.apiUrl = settings.DATABASE.API_URL;
    this.licenseKey = settings.DATABASE.LICENSE_KEY;
    this.secretKey = settings.DATABASE.API_SECRET;
    this.gson = new Gson();
    this.logger = logger;
  }

  /**
   * Initialize the Crafter API client by verifying the license key.
   *
   * @return true if initialization successful, false otherwise
   */
  public CompletableFuture<Boolean> initialize() {
    return this.verifyLicense()
        .thenApply(success -> {
          if (success) {
            this.isInitialized = true;
          }
          return success;
        });
  }

  /**
   * Verify the license key with Crafter CMS API.
   *
   * @return true if license is valid, false otherwise
   */
  private CompletableFuture<Boolean> verifyLicense() {
    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("key", this.licenseKey);

      String endpoint = this.apiUrl + "/website/key/verify";

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(endpoint))
          .header("Content-Type", "application/json")
          .header("User-Agent", USER_AGENT)
          .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(response -> {
            if (response.statusCode() == 200) {
              try {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

                // Check if success field exists
                if (!jsonResponse.has("success")) {
                  this.logger.error("License verification failed - Response missing 'success' field");
                  return false;
                }

                boolean success = jsonResponse.get("success").getAsBoolean();

                if (success) {
                  // Check if website field exists
                  if (!jsonResponse.has("website")) {
                    this.logger.error("License verification failed - Response missing 'website' field");
                    return false;
                  }

                  JsonObject websiteData = jsonResponse.get("website").getAsJsonObject();

                  // Check if required website fields exist
                  if (!websiteData.has("id") || !websiteData.has("name")) {
                    this.logger.error("License verification failed - Website data missing required fields");
                    return false;
                  }

                  // Extract URL if available, otherwise use a default value
                  String websiteUrl = websiteData.has("url") ? websiteData.get("url").getAsString() : "";

                  this.website = new CrafterWebsite(
                      websiteData.get("id").getAsString(),
                      websiteData.get("name").getAsString(),
                      websiteUrl
                  );
                  return true;
                } else {
                  this.logger.error("License verification failed - API returned success=false");
                  return false;
                }
              } catch (Exception e) {
                this.logger.error("License verification failed - Response parsing exception: " + e.getMessage());
                return false;
              }
            } else {
              this.logger.error("License verification failed - HTTP error status: " + response.statusCode());
              return false;
            }
          })
          .exceptionally(throwable -> {
            this.logger.error("License verification failed with exception: " + throwable.getMessage());
            return false;
          });

    } catch (Exception e) {
      this.logger.error("License verification failed during request creation: " + e.getMessage());
      return CompletableFuture.completedFuture(false);
    }
  }

  /**
   * Helper method to create a request builder with common headers including x-crafter-client-ip.
   */
  private HttpRequest.Builder createRequestBuilder(String endpoint, String ipAddress) {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(endpoint))
        .header("Content-Type", "application/json")
        .header("User-Agent", USER_AGENT)
        .header("X-API-Secret", this.secretKey);

    if (ipAddress != null && !ipAddress.isEmpty()) {
      builder.header("x-crafter-client-ip", ipAddress)
          .header("X-Forwarded-For", ipAddress)
          .header("X-Real-IP", ipAddress);
    }

    String originValue = this.getOriginHeaderValue();
    if (originValue != null) {
      builder.header("Origin", originValue);
    }

    return builder;
  }

  /**
   * Authenticate a user (sign in).
   *
   * @param username The user's username
   * @param password The user's password
   * @param ipAddress The user's IP address for backend IP limit checks
   * @return The authentication response
   */
  public CompletableFuture<CrafterResponse> signIn(String username, String password, String ipAddress) {
    if (!this.isInitialized) {
      this.logger.error("Sign in request failed - API client not initialized");
      return CompletableFuture.completedFuture(new CrafterResponse(false, "API client not initialized"));
    }

    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("username", username);
      requestBody.addProperty("password", password);

      String endpoint = this.apiUrl + "/website/v2/" + this.website.getId() + "/auth/signin";

      HttpRequest request = this.createRequestBuilder(endpoint, ipAddress)
          .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(response -> {
            CrafterResponse result = this.parseResponse(response);

            if (!result.isSuccess()) {
              this.logger.error("Sign in request failed - " + result.getMessage());
            }

            return result;
          })
          .exceptionally(throwable -> {
            this.logger.error("Sign in request failed with exception: " + throwable.getMessage());
            return new CrafterResponse(false, "Request failed: " + throwable.getMessage());
          });

    } catch (Exception e) {
      this.logger.error("Sign in request failed during request creation: " + e.getMessage());
      return CompletableFuture.completedFuture(new CrafterResponse(false, "Request creation failed: " + e.getMessage()));
    }
  }

  /**
   * Register a new user (sign up).
   *
   * @param username The user's username
   * @param email The user's email
   * @param password The user's password
   * @param passwordConfirm The password confirmation
   * @param ipAddress The user's IP address for backend IP limit checks
   * @return The registration response
   */
  public CompletableFuture<CrafterResponse> signUp(String username, String email, String password,
      String passwordConfirm, String ipAddress) {
    if (!this.isInitialized) {
      this.logger.error("Sign up request failed - API client not initialized");
      return CompletableFuture.completedFuture(new CrafterResponse(false, "API client not initialized"));
    }

    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("username", username);
      requestBody.addProperty("email", (email != null && !email.isEmpty()) ? email : username + "@temp.com");
      requestBody.addProperty("password", password);
      requestBody.addProperty("confirm_password", passwordConfirm);

      String endpoint = this.apiUrl + "/website/v2/" + this.website.getId() + "/auth/signup";

      HttpRequest request = this.createRequestBuilder(endpoint, ipAddress)
          .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(response -> {
            CrafterResponse result = this.parseResponse(response);

            if (!result.isSuccess()) {
              this.logger.error("Sign up request failed - " + result.getMessage());
            }

            return result;
          })
          .exceptionally(throwable -> {
            this.logger.error("Sign up request failed with exception: " + throwable.getMessage());
            return new CrafterResponse(false, "Request failed: " + throwable.getMessage());
          });

    } catch (Exception e) {
      this.logger.error("Sign up request failed during request creation: " + e.getMessage());
      return CompletableFuture.completedFuture(new CrafterResponse(false, "Request creation failed: " + e.getMessage()));
    }
  }

  /**
   * Verify 2FA code during signin.
   *
   * @param tempToken The temporary JWT token
   * @param code The 2FA code or recovery code
   * @param method Optional method ('authenticator', 'email', 'discord', 'recovery_code')
   * @param ipAddress The player IP address
   * @return Response from Crafter CMS API
   */
  public CompletableFuture<CrafterResponse> verify2FA(String tempToken, String code, String method, String ipAddress) {
    if (!this.isInitialized) {
      this.logger.error("2FA verify request failed - API client not initialized");
      return CompletableFuture.completedFuture(new CrafterResponse(false, "API client not initialized"));
    }

    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("tempToken", tempToken);
      requestBody.addProperty("code", code);
      if (method != null && !method.isEmpty()) {
        requestBody.addProperty("method", method);
      }

      String endpoint = this.apiUrl + "/website/v2/" + this.website.getId() + "/auth/2fa/verify";

      HttpRequest request = this.createRequestBuilder(endpoint, ipAddress)
          .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(this::parseResponse)
          .exceptionally(throwable -> {
            this.logger.error("2FA verify request failed with exception: " + throwable.getMessage());
            return new CrafterResponse(false, "Request failed: " + throwable.getMessage());
          });

    } catch (Exception e) {
      this.logger.error("2FA verify request failed during creation: " + e.getMessage());
      return CompletableFuture.completedFuture(new CrafterResponse(false, "Request creation failed: " + e.getMessage()));
    }
  }

  /**
   * Request resending 2FA code via email.
   *
   * @param tempToken The temporary JWT token
   * @param ipAddress The player IP address
   * @return Response from Crafter CMS API
   */
  public CompletableFuture<CrafterResponse> send2FAEmailCode(String tempToken, String ipAddress) {
    if (!this.isInitialized) {
      this.logger.error("Send 2FA email code request failed - API client not initialized");
      return CompletableFuture.completedFuture(new CrafterResponse(false, "API client not initialized"));
    }

    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("tempToken", tempToken);

      String endpoint = this.apiUrl + "/website/v2/" + this.website.getId() + "/auth/2fa/send-email-code";

      HttpRequest request = this.createRequestBuilder(endpoint, ipAddress)
          .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(this::parseResponse)
          .exceptionally(throwable -> {
            this.logger.error("Send 2FA email code failed with exception: " + throwable.getMessage());
            return new CrafterResponse(false, "Request failed: " + throwable.getMessage());
          });

    } catch (Exception e) {
      this.logger.error("Send 2FA email code request creation failed: " + e.getMessage());
      return CompletableFuture.completedFuture(new CrafterResponse(false, "Request creation failed: " + e.getMessage()));
    }
  }

  /**
   * Request resending 2FA code via Discord.
   *
   * @param tempToken The temporary JWT token
   * @param ipAddress The player IP address
   * @return Response from Crafter CMS API
   */
  public CompletableFuture<CrafterResponse> send2FADiscordCode(String tempToken, String ipAddress) {
    if (!this.isInitialized) {
      this.logger.error("Send 2FA Discord code request failed - API client not initialized");
      return CompletableFuture.completedFuture(new CrafterResponse(false, "API client not initialized"));
    }

    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("tempToken", tempToken);

      String endpoint = this.apiUrl + "/website/v2/" + this.website.getId() + "/auth/2fa/send-discord-code";

      HttpRequest request = this.createRequestBuilder(endpoint, ipAddress)
          .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(this::parseResponse)
          .exceptionally(throwable -> {
            this.logger.error("Send 2FA Discord code failed with exception: " + throwable.getMessage());
            return new CrafterResponse(false, "Request failed: " + throwable.getMessage());
          });

    } catch (Exception e) {
      this.logger.error("Send 2FA Discord code request creation failed: " + e.getMessage());
      return CompletableFuture.completedFuture(new CrafterResponse(false, "Request creation failed: " + e.getMessage()));
    }
  }

  /**
   * Verify email verification OTP code.
   *
   * @param tempToken The temporary JWT token
   * @param code The 6-digit OTP code
   * @param ipAddress The player IP address
   * @return Response from Crafter CMS API
   */
  public CompletableFuture<CrafterResponse> verifyLoginEmail(String tempToken, String code, String ipAddress) {
    if (!this.isInitialized) {
      this.logger.error("Verify email request failed - API client not initialized");
      return CompletableFuture.completedFuture(new CrafterResponse(false, "API client not initialized"));
    }

    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("tempToken", tempToken);
      requestBody.addProperty("code", code);

      String endpoint = this.apiUrl + "/website/v2/" + this.website.getId() + "/auth/verify-email";

      HttpRequest request = this.createRequestBuilder(endpoint, ipAddress)
          .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(this::parseResponse)
          .exceptionally(throwable -> {
            this.logger.error("Verify email failed with exception: " + throwable.getMessage());
            return new CrafterResponse(false, "Request failed: " + throwable.getMessage());
          });

    } catch (Exception e) {
      this.logger.error("Verify email request creation failed: " + e.getMessage());
      return CompletableFuture.completedFuture(new CrafterResponse(false, "Request creation failed: " + e.getMessage()));
    }
  }

  /**
   * Resend login email verification code.
   *
   * @param tempToken The temporary JWT token
   * @param ipAddress The player IP address
   * @return Response from Crafter CMS API
   */
  public CompletableFuture<CrafterResponse> resendLoginEmail(String tempToken, String ipAddress) {
    if (!this.isInitialized) {
      this.logger.error("Resend email request failed - API client not initialized");
      return CompletableFuture.completedFuture(new CrafterResponse(false, "API client not initialized"));
    }

    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("tempToken", tempToken);

      String endpoint = this.apiUrl + "/website/v2/" + this.website.getId() + "/auth/resend-email";

      HttpRequest request = this.createRequestBuilder(endpoint, ipAddress)
          .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(this::parseResponse)
          .exceptionally(throwable -> {
            this.logger.error("Resend email failed with exception: " + throwable.getMessage());
            return new CrafterResponse(false, "Request failed: " + throwable.getMessage());
          });

    } catch (Exception e) {
      this.logger.error("Resend email request creation failed: " + e.getMessage());
      return CompletableFuture.completedFuture(new CrafterResponse(false, "Request creation failed: " + e.getMessage()));
    }
  }

  /**
   * Update temporary (@temp.com) email address and send verification code.
   *
   * @param tempToken The temporary JWT token
   * @param email The new real email address
   * @param ipAddress The player IP address
   * @return Response from Crafter CMS API
   */
  public CompletableFuture<CrafterResponse> updateTempEmail(String tempToken, String email, String ipAddress) {
    if (!this.isInitialized) {
      this.logger.error("Update temp email request failed - API client not initialized");
      return CompletableFuture.completedFuture(new CrafterResponse(false, "API client not initialized"));
    }

    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("tempToken", tempToken);
      requestBody.addProperty("email", email);

      String endpoint = this.apiUrl + "/website/v2/" + this.website.getId() + "/auth/update-temp-email";

      HttpRequest request = this.createRequestBuilder(endpoint, ipAddress)
          .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(this::parseResponse)
          .exceptionally(throwable -> {
            this.logger.error("Update temp email failed with exception: " + throwable.getMessage());
            return new CrafterResponse(false, "Request failed: " + throwable.getMessage());
          });

    } catch (Exception e) {
      this.logger.error("Update temp email request creation failed: " + e.getMessage());
      return CompletableFuture.completedFuture(new CrafterResponse(false, "Request creation failed: " + e.getMessage()));
    }
  }

  /**
   * Request password reset (forgot password).
   *
   * @param username The user's username
   * @param email The user's email
   * @param ipAddress The user's IP address for backend IP limit checks
   * @return The password reset response
   */
  public CompletableFuture<CrafterResponse> forgotPassword(String username, String email, String ipAddress) {
    if (!this.isInitialized) {
      this.logger.error("Forgot password request failed - API client not initialized");
      return CompletableFuture.completedFuture(new CrafterResponse(false, "API client not initialized"));
    }

    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("username", username);
      requestBody.addProperty("email", email);

      String endpoint = this.apiUrl + "/website/v2/" + this.website.getId() + "/auth/forgot-password";

      HttpRequest request = this.createRequestBuilder(endpoint, ipAddress)
          .POST(HttpRequest.BodyPublishers.ofString(this.gson.toJson(requestBody)))
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(response -> {
            CrafterResponse result = this.parseResponse(response);

            if (!result.isSuccess()) {
              this.logger.error("Forgot password request failed - " + result.getMessage());
            }

            return result;
          })
          .exceptionally(throwable -> {
            this.logger.error("Forgot password request failed with exception: " + throwable.getMessage());
            return new CrafterResponse(false, "Request failed: " + throwable.getMessage());
          });

    } catch (Exception e) {
      this.logger.error("Forgot password request failed during request creation: " + e.getMessage());
      return CompletableFuture.completedFuture(new CrafterResponse(false, "Request creation failed: " + e.getMessage()));
    }
  }

  /**
   * Parse HTTP response into CrafterResponse object.
   *
   * @param response The HTTP response to parse
   * @return The parsed CrafterResponse
   */
  private CrafterResponse parseResponse(HttpResponse<String> response) {
    try {
      if (response.statusCode() == 200) {
        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

        boolean requires2FA = jsonResponse.has("requires2FA") && jsonResponse.get("requires2FA").getAsBoolean();
        boolean requiresEmailVerification = jsonResponse.has("requiresEmailVerification")
            && jsonResponse.get("requiresEmailVerification").getAsBoolean();
        boolean success = jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean();
        boolean isTempEmail = jsonResponse.has("isTempEmail") && jsonResponse.get("isTempEmail").getAsBoolean();

        String message = jsonResponse.has("message") ? jsonResponse.get("message").getAsString() : "";
        String tempToken = jsonResponse.has("tempToken") ? jsonResponse.get("tempToken").getAsString()
            : (jsonResponse.has("emailToken") ? jsonResponse.get("emailToken").getAsString() : null);
        String primaryMethod = jsonResponse.has("primaryMethod") ? jsonResponse.get("primaryMethod").getAsString() : "authenticator";
        String maskedEmail = jsonResponse.has("maskedEmail") ? jsonResponse.get("maskedEmail").getAsString() : "";
        String accessToken = jsonResponse.has("accessToken") ? jsonResponse.get("accessToken").getAsString() : null;
        String refreshToken = jsonResponse.has("refreshToken") ? jsonResponse.get("refreshToken").getAsString() : null;

        List<String> methods = new ArrayList<>();
        if (jsonResponse.has("methods") && jsonResponse.get("methods").isJsonArray()) {
          for (JsonElement el : jsonResponse.getAsJsonArray("methods")) {
            methods.add(el.getAsString());
          }
        }

        JsonObject userData = jsonResponse.has("user") && jsonResponse.get("user").isJsonObject()
            ? jsonResponse.getAsJsonObject("user")
            : null;

        return new CrafterResponse(
            success || requires2FA || requiresEmailVerification,
            message,
            userData,
            requires2FA,
            requiresEmailVerification,
            isTempEmail,
            tempToken,
            primaryMethod,
            methods,
            maskedEmail,
            accessToken,
            refreshToken,
            null
        );
      } else {
        String errorMessage = "HTTP " + response.statusCode();
        String errorCode = null;
        try {
          JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
          if (jsonResponse.has("message")) {
            if (jsonResponse.get("message").isJsonArray()) {
              errorMessage = jsonResponse.getAsJsonArray("message").get(0).getAsString();
            } else {
              errorMessage = jsonResponse.get("message").getAsString();
            }
          }
          if (jsonResponse.has("error")) {
            errorCode = jsonResponse.get("error").getAsString();
          }
        } catch (Exception ignored) {
          errorMessage = response.body();
        }

        this.logger.error("Response parsing failed - HTTP error status: {} - {}", response.statusCode(), errorMessage);
        return new CrafterResponse(
            false,
            errorMessage,
            null,
            false,
            false,
            false,
            null,
            null,
            Collections.emptyList(),
            null,
            null,
            null,
            errorCode
        );
      }
    } catch (Exception e) {
      this.logger.error("Response parsing failed with exception: " + e.getMessage());
      return new CrafterResponse(false, "Response parsing failed: " + e.getMessage());
    }
  }

  /**
   * Check if the API client is initialized.
   *
   * @return true if initialized, false otherwise
   */
  public boolean isInitialized() {
    return this.isInitialized;
  }

  /**
   * Test the API connection and license verification manually.
   * This method can be called for debugging purposes.
   *
   * @return A CompletableFuture that completes with the test result
   */
  public CompletableFuture<String> testConnection() {
    this.logger.info("Testing Crafter CMS API connection...");

    return this.verifyLicense()
        .thenApply(success -> {
          if (success) {
            return "SUCCESS: License verified successfully. Website: " + this.website.toString();
          } else {
            return "FAILED: License verification failed. Check logs for details.";
          }
        })
        .exceptionally(throwable -> {
          return "ERROR: " + throwable.getMessage();
        });
  }

  /**
   * Get the website information.
   *
   * @return The website information
   */
  public CrafterWebsite getWebsite() {
    return this.website;
  }

  /**
   * Get the Origin header value for requests.
   * Returns the website URL if available, otherwise returns null.
   *
   * @return The Origin header value or null if not available
   */
  private String getOriginHeaderValue() {
    if (this.website != null && this.website.getUrl() != null && !this.website.getUrl().isEmpty()) {
      return this.website.getUrl();
    }
    return null;
  }

  /**
   * Check if a user exists and get their information.
   *
   * @param username The username to check
   * @param ipAddress Optional player IP address
   * @return The user information response
   */
  public CompletableFuture<CrafterResponse> checkUserExists(String username, String ipAddress) {
    if (!this.isInitialized) {
      this.logger.error("User check request failed - API client not initialized");
      return CompletableFuture.completedFuture(new CrafterResponse(false, "API client not initialized"));
    }

    try {
      String endpoint = this.apiUrl + "/website/v2/" + this.website.getId() + "/users/" + username;

      HttpRequest request = this.createRequestBuilder(endpoint, ipAddress)
          .GET()
          .build();

      return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
          .thenApply(response -> {
            if (response.statusCode() == 200) {
              try {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

                // Check if the response contains user data by looking for common user fields
                if (jsonResponse.has("username") && jsonResponse.has("email")) {
                  return new CrafterResponse(true, "User found", jsonResponse);
                } else {
                  this.logger.error("User check request failed - Response missing user data fields");
                  return new CrafterResponse(false, "User data not found in response");
                }
              } catch (Exception e) {
                this.logger.error("User check request failed - Response parsing exception: " + e.getMessage());
                return new CrafterResponse(false, "Response parsing failed: " + e.getMessage());
              }
            } else if (response.statusCode() == 404) {
              return new CrafterResponse(false, "User not found");
            } else {
              this.logger.error("User check request failed - HTTP error status: " + response.statusCode());
              return new CrafterResponse(false, "HTTP " + response.statusCode() + ": " + response.body());
            }
          })
          .exceptionally(throwable -> {
            this.logger.error("User check request failed with exception: " + throwable.getMessage());
            return new CrafterResponse(false, "Request failed: " + throwable.getMessage());
          });

    } catch (Exception e) {
      this.logger.error("User check request failed during request creation: " + e.getMessage());
      return CompletableFuture.completedFuture(new CrafterResponse(false, "Request creation failed: " + e.getMessage()));
    }
  }

  /**
   * Check if a user exists and get their information.
   *
   * @param username The username to check
   * @return The user information response
   */
  public CompletableFuture<CrafterResponse> checkUserExists(String username) {
    return this.checkUserExists(username, null);
  }

  /**
   * Get user information by username.
   *
   * @param username The username to get information for
   * @return The user information response
   */
  public CompletableFuture<CrafterResponse> getUserInfo(String username) {
    return this.checkUserExists(username);
  }
}
