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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.elytrium.commons.utils.updates.UpdatesChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateChecker {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateChecker.class);
  public static final String REPO = "Crafter-CMS/LimboAuth";
  public static final String RELEASES_URL = "https://github.com/" + REPO + "/releases";
  public static final String API_RELEASES_LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
  public static final String RAW_VERSION_URL = "https://raw.githubusercontent.com/" + REPO + "/master/VERSION";

  private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+(\\.\\d+)+)");

  private final String currentVersion;

  public UpdateChecker(String currentVersion) {
    this.currentVersion = currentVersion;
  }

  public void checkForUpdates() {
    // 1. Check via GitHub Releases API
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL(API_RELEASES_LATEST).openConnection();
      connection.setRequestMethod("GET");
      connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
      connection.setRequestProperty("User-Agent", "Crafter-LimboAuth-UpdateChecker");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
        JsonObject json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)).getAsJsonObject();
        String tagName = json.has("tag_name") && !json.get("tag_name").isJsonNull() ? json.get("tag_name").getAsString() : "";
        String name = json.has("name") && !json.get("name").isJsonNull() ? json.get("name").getAsString() : "";
        String downloadUrl = json.has("html_url") && !json.get("html_url").isJsonNull() ? json.get("html_url").getAsString() : RELEASES_URL;

        String tagVersion = extractVersion(tagName);
        String nameVersion = extractVersion(name);
        String assetVersion = null;

        if (json.has("assets") && json.get("assets").isJsonArray()) {
          JsonArray assets = json.getAsJsonArray("assets");
          for (JsonElement assetElem : assets) {
            if (assetElem.isJsonObject()) {
              JsonObject asset = assetElem.getAsJsonObject();
              if (asset.has("name") && !asset.get("name").isJsonNull()) {
                String ver = extractVersion(asset.get("name").getAsString());
                if (ver != null) {
                  assetVersion = getHighestVersion(assetVersion, ver);
                }
              }
            }
          }
        }

        String latestVersion = getHighestVersion(tagVersion, nameVersion, assetVersion);

        if (latestVersion != null) {
          if (isNewerVersion(latestVersion, this.currentVersion)) {
            notifyUpdate(latestVersion, downloadUrl);
            return;
          } else {
            LOGGER.debug("LimboAuth is up to date (current: {}, latest: {}).", this.currentVersion, latestVersion);
            return;
          }
        }
      }
    } catch (Exception e) {
      LOGGER.debug("Could not check updates via GitHub Releases API: {}", e.getMessage());
    }

    // 2. Fallback to raw VERSION file from master branch
    try {
      if (!UpdatesChecker.checkVersionByURL(RAW_VERSION_URL, this.currentVersion)) {
        notifyUpdate(null, RELEASES_URL);
      }
    } catch (Exception e) {
      LOGGER.debug("Could not check updates via raw VERSION URL: {}", e.getMessage());
    }
  }

  private void notifyUpdate(String latestVersion, String downloadUrl) {
    LOGGER.warn("****************************************************");
    LOGGER.warn("Yeni bir LimboAuth güncellemesi mevcut! / A new LimboAuth update is available!");
    if (latestVersion != null && !latestVersion.isEmpty()) {
      LOGGER.warn("En son sürüm / Latest version: {} (Mevcut / Current: {})", latestVersion, this.currentVersion);
    }
    LOGGER.warn("İndirme adresi / Download: {}", downloadUrl);
    LOGGER.warn("****************************************************");
  }

  public static String extractVersion(String input) {
    if (input == null || input.isEmpty()) {
      return null;
    }
    Matcher matcher = VERSION_PATTERN.matcher(input);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  public static String getHighestVersion(String... versions) {
    String highest = null;
    for (String v : versions) {
      if (v == null) {
        continue;
      }
      if (highest == null || isNewerVersion(v, highest)) {
        highest = v;
      }
    }
    return highest;
  }

  public static boolean isNewerVersion(String latest, String current) {
    if (latest == null || current == null) {
      return false;
    }

    String v1 = normalizeVersion(latest);
    String v2 = normalizeVersion(current);

    String[] parts1 = v1.split("[.-]");
    String[] parts2 = v2.split("[.-]");

    int length = Math.max(parts1.length, parts2.length);
    for (int i = 0; i < length; i++) {
      int num1 = i < parts1.length ? parseNumber(parts1[i]) : 0;
      int num2 = i < parts2.length ? parseNumber(parts2[i]) : 0;

      if (num1 > num2) {
        return true;
      }
      if (num1 < num2) {
        return false;
      }
    }

    return false;
  }

  private static String normalizeVersion(String v) {
    String trimmed = v.trim();
    if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
      trimmed = trimmed.substring(1);
    }
    return trimmed;
  }

  private static int parseNumber(String s) {
    try {
      return Integer.parseInt(s.replaceAll("\\D+", ""));
    } catch (Exception e) {
      return 0;
    }
  }
}
