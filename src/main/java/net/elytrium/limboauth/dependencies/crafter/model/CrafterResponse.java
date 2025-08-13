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

/**
 * Model class for Crafter CMS API responses.
 */
public class CrafterResponse {
  private final boolean success;
  private final String message;
  private final JsonObject userData;

  public CrafterResponse(boolean success, String message) {
    this.success = success;
    this.message = message;
    this.userData = null;
  }

  public CrafterResponse(boolean success, String message, JsonObject userData) {
    this.success = success;
    this.message = message;
    this.userData = userData;
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

  @Override
  public String toString() {
    return "CrafterResponse{"
        + "success=" + this.success
        + ", message='" + this.message + '\''
        + ", userData=" + (this.userData != null ? "available" : "null")
        + ", hashCode=" + System.identityHashCode(this)
        + '}';
  }
}
