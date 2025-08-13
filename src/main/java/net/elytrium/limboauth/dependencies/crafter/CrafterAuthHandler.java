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

import com.velocitypowered.api.proxy.Player;
import java.util.concurrent.CompletableFuture;
import net.elytrium.limboauth.model.RegisteredPlayer;
import org.slf4j.Logger;

/**
 * Handles authentication logic for Crafter CMS integration.
 */
public class CrafterAuthHandler {
  private final CrafterAPIClient apiClient;
  private final Logger logger;

  public CrafterAuthHandler(CrafterAPIClient apiClient, Logger logger) {
    this.apiClient = apiClient;
    this.logger = logger;
  }

  /**
   * Check if a user exists in Crafter CMS.
   *
   * @param username The username to check
   * @return CompletableFuture that completes with user info or null if not found
   */
  public CompletableFuture<RegisteredPlayer> checkUserExists(String username) {
    if (!this.apiClient.isInitialized()) {
      this.logger.warn("Crafter CMS API not initialized, cannot check user existence");
      return CompletableFuture.completedFuture(null);
    }

    return this.apiClient.checkUserExists(username)
        .thenApply(response -> {
          if (response.isSuccess() && response.hasUserData()) {
            // User exists in Crafter CMS, create a RegisteredPlayer object
            // Note: This is a simplified approach - you might want to store more user data
            RegisteredPlayer player = new RegisteredPlayer();
            player.setNickname(username); // This also sets lowercase nickname
            player.setHash(""); // Empty hash indicates premium account
            player.setPremiumUuid(""); // Will be set when player connects
            return player;
          } else {
            // User not found in Crafter CMS
            return null;
          }
        })
        .exceptionally(throwable -> {
          this.logger.error("Error checking user existence in Crafter CMS: " + throwable.getMessage(), throwable);
          return null;
        });
  }

  /**
   * Authenticate a user with Crafter CMS.
   *
   * @param username The username
   * @param password The password
   * @param ipAddress The user's IP address for backend IP limit checks
   * @return CompletableFuture that completes with authentication result
   */
  public CompletableFuture<Boolean> authenticateUser(String username, String password, String ipAddress) {
    if (!this.apiClient.isInitialized()) {
      this.logger.warn("Crafter CMS API not initialized, cannot authenticate user");
      return CompletableFuture.completedFuture(false);
    }

    return this.apiClient.signIn(username, password, ipAddress)
        .thenApply(response -> {
          if (response.isSuccess()) {
            this.logger.info("User {} authenticated successfully via Crafter CMS", username);
            return true;
          } else {
            this.logger.warn("User {} authentication failed via Crafter CMS: {}", username, response.getMessage());
            return false;
          }
        })
        .exceptionally(throwable -> {
          this.logger.error("Error authenticating user via Crafter CMS: " + throwable.getMessage(), throwable);
          return false;
        });
  }

  /**
   * Register a new user with Crafter CMS.
   *
   * @param username The username
   * @param email The email
   * @param password The password
   * @param passwordConfirm The password confirmation
   * @param ipAddress The user's IP address for backend IP limit checks
   * @return CompletableFuture that completes with registration result
   */
  public CompletableFuture<Boolean> registerUser(String username, String email, String password, String passwordConfirm, String ipAddress) {
    if (!this.apiClient.isInitialized()) {
      this.logger.warn("Crafter CMS API not initialized, cannot register user");
      return CompletableFuture.completedFuture(false);
    }

    return this.apiClient.signUp(username, email, password, passwordConfirm, ipAddress)
        .thenApply(response -> {
          if (response.isSuccess()) {
            this.logger.info("User {} registered successfully via Crafter CMS", username);
            return true;
          } else {
            this.logger.warn("User {} registration failed via Crafter CMS: {}", username, response.getMessage());
            return false;
          }
        })
        .exceptionally(throwable -> {
          this.logger.error("Error registering user via Crafter CMS: " + throwable.getMessage(), throwable);
          return false;
        });
  }

  /**
   * Request password reset for a user with Crafter CMS.
   *
   * @param username The username
   * @param email The email
   * @param ipAddress The user's IP address for backend IP limit checks
   * @return CompletableFuture that completes with password reset result
   */
  public CompletableFuture<Boolean> forgotPassword(String username, String email, String ipAddress) {
    if (!this.apiClient.isInitialized()) {
      this.logger.warn("Crafter CMS API not initialized, cannot process password reset");
      return CompletableFuture.completedFuture(false);
    }

    return this.apiClient.forgotPassword(username, email, ipAddress)
        .thenApply(response -> {
          if (response.isSuccess()) {
            this.logger.info("Password reset requested successfully for user {} via Crafter CMS", username);
            return true;
          } else {
            this.logger.warn("Password reset failed for user {} via Crafter CMS: {}", username, response.getMessage());
            return false;
          }
        })
        .exceptionally(throwable -> {
          this.logger.error("Error processing password reset via Crafter CMS: " + throwable.getMessage(), throwable);
          return false;
        });
  }

  /**
   * Check if the handler is ready to process requests.
   *
   * @return true if ready, false otherwise
   */
  public boolean isReady() {
    return this.apiClient != null && this.apiClient.isInitialized();
  }
}
