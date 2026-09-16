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

package net.elytrium.limboauth.dependencies.crafter.model;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model class for Crafter CMS API responses.
 */
public class CrafterResponse {
  private final boolean success;
  private final String message;
  private final JsonObject userData;

  private final boolean requires2FA;
  private final boolean requiresEmailVerification;
  private final boolean isTempEmail;
  private final String tempToken;
  private final String primaryMethod;
  private final List<String> methods;
  private final String maskedEmail;
  private final String accessToken;
  private final String refreshToken;
  private final String errorCode;

  public CrafterResponse(boolean success, String message) {
    this(success, message, null, false, false, false, null, null, Collections.emptyList(), null, null, null, null);
  }

  public CrafterResponse(boolean success, String message, JsonObject userData) {
    this(success, message, userData, false, false, false, null, null, Collections.emptyList(), null, null, null, null);
  }

  public CrafterResponse(
      boolean success,
      String message,
      JsonObject userData,
      boolean requires2FA,
      boolean requiresEmailVerification,
      boolean isTempEmail,
      String tempToken,
      String primaryMethod,
      List<String> methods,
      String maskedEmail,
      String accessToken,
      String refreshToken,
      String errorCode
  ) {
    this.success = success;
    this.message = message != null ? message : "";
    this.userData = userData;
    this.requires2FA = requires2FA;
    this.requiresEmailVerification = requiresEmailVerification;
    this.isTempEmail = isTempEmail;
    this.tempToken = tempToken;
    this.primaryMethod = primaryMethod != null ? primaryMethod : "authenticator";
    this.methods = methods != null ? Collections.unmodifiableList(new ArrayList<>(methods)) : Collections.emptyList();
    this.maskedEmail = maskedEmail != null ? maskedEmail : "";
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.errorCode = errorCode;
  }

  /**
   * Check if the request was successful.
   *
   * @return true if successful, false otherwise
   */
  public boolean isSuccess() {
    return this.success;
  }

  /**
   * Get the response message.
   *
   * @return The response message
   */
  public String getMessage() {
    return this.message;
  }

  /**
   * Get the user data from the response.
   *
   * @return The user data as JsonObject, or null if not available
   */
  public JsonObject getUserData() {
    return this.userData;
  }

  /**
   * Check if user data is available.
   *
   * @return true if user data is available, false otherwise
   */
  public boolean hasUserData() {
    return this.userData != null;
  }

  /**
   * Check if two-factor authentication is required.
   *
   * @return true if 2FA is required, false otherwise
   */
  public boolean isRequires2FA() {
    return this.requires2FA;
  }

  /**
   * Check if email verification is required.
   *
   * @return true if email verification is required, false otherwise
   */
  public boolean isRequiresEmailVerification() {
    return this.requiresEmailVerification;
  }

  /**
   * Check if user has a temporary email (@temp.com).
   *
   * @return true if user has temporary email, false otherwise
   */
  public boolean isTempEmail() {
    return this.isTempEmail;
  }

  /**
   * Get the temporary JWT token for 2FA or email verification.
   *
   * @return The temp token, or null if not available
   */
  public String getTempToken() {
    return this.tempToken;
  }

  /**
   * Get primary 2FA method ('authenticator', 'email', 'discord').
   *
   * @return The primary method
   */
  public String getPrimaryMethod() {
    return this.primaryMethod;
  }

  /**
   * Get list of available 2FA methods.
   *
   * @return List of methods
   */
  public List<String> getMethods() {
    return this.methods;
  }

  /**
   * Get masked email for display.
   *
   * @return Masked email
   */
  public String getMaskedEmail() {
    return this.maskedEmail;
  }

  /**
   * Get JWT access token.
   *
   * @return Access token
   */
  public String getAccessToken() {
    return this.accessToken;
  }

  /**
   * Get JWT refresh token.
   *
   * @return Refresh token
   */
  public String getRefreshToken() {
    return this.refreshToken;
  }

  /**
   * Get error code if available.
   *
   * @return Error code string
   */
  public String getErrorCode() {
    return this.errorCode;
  }

  @Override
  public String toString() {
    return "CrafterResponse{"
        + "success=" + this.success
        + ", message='" + this.message + '\''
        + ", requires2FA=" + this.requires2FA
        + ", requiresEmailVerification=" + this.requiresEmailVerification
        + ", isTempEmail=" + this.isTempEmail
        + ", primaryMethod='" + this.primaryMethod + '\''
        + ", userData=" + (this.userData != null ? "available" : "null")
        + ", hashCode=" + System.identityHashCode(this)
        + '}';
  }
}
