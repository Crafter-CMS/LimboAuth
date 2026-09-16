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

package net.elytrium.limboauth.handler;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.google.common.primitives.Longs;
import com.j256.ormlite.dao.Dao;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.time.SystemTimeProvider;
import io.netty.buffer.ByteBuf;
import io.whitfin.siphash.SipHasher;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.elytrium.commons.kyori.serialization.Serializer;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Messages;
import net.elytrium.limboauth.Settings;
import net.elytrium.limboauth.dependencies.DatabaseLibrary;
import net.elytrium.limboauth.dependencies.crafter.model.CrafterResponse;
import net.elytrium.limboauth.event.PostAuthorizationEvent;
import net.elytrium.limboauth.event.PostRegisterEvent;
import net.elytrium.limboauth.event.TaskEvent;
import net.elytrium.limboauth.migration.MigrationHash;
import net.elytrium.limboauth.model.RegisteredPlayer;
import net.elytrium.limboauth.model.SQLRuntimeException;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthSessionHandler implements LimboSessionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthSessionHandler.class);

  public static final CodeVerifier TOTP_CODE_VERIFIER = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
  private static final BCrypt.Verifyer HASH_VERIFIER = BCrypt.verifyer();
  private static final BCrypt.Hasher HASHER = BCrypt.withDefaults();

  private static Serializer serializer;
  private static Component ratelimited;
  private static BossBar.Color bossbarColor;
  private static BossBar.Overlay bossbarOverlay;
  private static Component ipLimitKick;
  private static Component databaseErrorKick;
  private static String wrongNicknameCaseKick;
  private static Component timesUp;
  private static Component registerSuccessful;
  @Nullable
  private static Title registerSuccessfulTitle;
  private static Component[] loginWrongPassword;
  private static Component loginWrongPasswordKick;
  private static Component totp;
  @Nullable
  private static Title totpTitle;
  private static Component register;
  @Nullable
  private static Title registerTitle;
  private static Component[] login;
  @Nullable
  private static Title loginTitle;
  private static Component registerDifferentPasswords;
  private static Component registerPasswordTooLong;
  private static Component registerPasswordTooShort;
  private static Component registerPasswordUnsafe;
  private static Component loginSuccessful;
  private static Component sessionExpired;
  @Nullable
  private static Title loginSuccessfulTitle;
  @Nullable
  private static MigrationHash migrationHash;

  private final Dao<RegisteredPlayer, String> playerDao;
  private final Player proxyPlayer;
  private final LimboAuth plugin;

  public enum CrafterAuthState {
    NONE,
    AWAITING_2FA,
    AWAITING_EMAIL_INPUT,
    AWAITING_EMAIL_CODE
  }

  private long joinTime = System.currentTimeMillis();
  private final BossBar bossBar = BossBar.bossBar(
      Component.empty(),
      1.0F,
      bossbarColor,
      bossbarOverlay
  );
  private final boolean loginOnlyByMod = Settings.IMP.MAIN.MOD.ENABLED && Settings.IMP.MAIN.MOD.LOGIN_ONLY_BY_MOD;

  @Nullable
  private RegisteredPlayer playerInfo;

  private ScheduledFuture<?> authMainTask;

  private LimboPlayer player;
  private int attempts = Settings.IMP.MAIN.LOGIN_ATTEMPTS;
  private boolean totpState;
  private String tempPassword;
  private boolean tokenReceived;

  private CrafterAuthState crafterState = CrafterAuthState.NONE;
  private String crafterTempToken;
  private String crafterPrimaryMethod = "authenticator";
  private String crafterMaskedEmail = "";
  private boolean crafterIsTempEmail;

  public AuthSessionHandler(Dao<RegisteredPlayer, String> playerDao, Player proxyPlayer, LimboAuth plugin, @Nullable RegisteredPlayer playerInfo) {
    this.playerDao = playerDao;
    this.proxyPlayer = proxyPlayer;
    this.plugin = plugin;
    this.playerInfo = playerInfo;
  }

  @Override
  public void onSpawn(Limbo server, LimboPlayer player) {
    this.player = player;

    if (Settings.IMP.MAIN.DISABLE_FALLING) {
      this.player.disableFalling();
    } else {
      this.player.enableFalling();
    }

    Serializer serializer = LimboAuth.getSerializer();

    if (this.playerInfo == null) {
      // For Crafter CMS, skip IP limit checks since we don't have playerDao
      if (this.playerDao != null) {
        try {
          String ip = this.proxyPlayer.getRemoteAddress().getAddress().getHostAddress();
          List<RegisteredPlayer> alreadyRegistered = this.playerDao.queryForEq(RegisteredPlayer.IP_FIELD, ip);
          if (alreadyRegistered != null) {
            int sizeOfValidRegistrations = alreadyRegistered.size();
            if (Settings.IMP.MAIN.IP_LIMIT_VALID_TIME > 0) {
              for (RegisteredPlayer registeredPlayer : alreadyRegistered.stream()
                  .filter(registeredPlayer -> registeredPlayer.getRegDate() < System.currentTimeMillis() - Settings.IMP.MAIN.IP_LIMIT_VALID_TIME)
                  .collect(Collectors.toList())) {
                registeredPlayer.setIP("");
                this.playerDao.update(registeredPlayer);
                --sizeOfValidRegistrations;
              }
            }

            if (sizeOfValidRegistrations >= Settings.IMP.MAIN.IP_LIMIT_REGISTRATIONS) {
              this.proxyPlayer.disconnect(ipLimitKick);
              return;
            }
          }
        } catch (SQLException e) {
          this.proxyPlayer.disconnect(databaseErrorKick);
          throw new SQLRuntimeException(e);
        }
      }
    } else {
      if (!this.proxyPlayer.getUsername().equals(this.playerInfo.getNickname())) {
        this.proxyPlayer.disconnect(serializer.deserialize(
            MessageFormat.format(wrongNicknameCaseKick, this.playerInfo.getNickname(), this.proxyPlayer.getUsername()))
        );
        return;
      }

      this.plugin.addAuthenticatingPlayer(player.getProxyPlayer().getUsername(), this);
    }

    boolean bossBarEnabled = !this.loginOnlyByMod && Settings.IMP.MAIN.ENABLE_BOSSBAR;
    int authTime = Settings.IMP.MAIN.AUTH_TIME;
    float multiplier = 1000.0F / authTime;
    this.authMainTask = this.player.getScheduledExecutor().scheduleWithFixedDelay(() -> {
      if (System.currentTimeMillis() - this.joinTime > authTime) {
        this.proxyPlayer.disconnect(timesUp);
      } else {
        if (bossBarEnabled) {
          float secondsLeft = (authTime - (System.currentTimeMillis() - this.joinTime)) / 1000.0F;
          this.bossBar.name(serializer.deserialize(MessageFormat.format(Messages.IMP.AUTH.BOSSBAR, (int) secondsLeft)));
          // It's possible, that the progress value can overcome 1, e.g. 1.0000001.
          this.bossBar.progress(Math.min(1.0F, secondsLeft * multiplier));
        }
      }
    }, 0, 1, TimeUnit.SECONDS);

    if (bossBarEnabled) {
      this.proxyPlayer.showBossBar(this.bossBar);
    }

    if (!this.loginOnlyByMod) {
      this.sendMessage(true);
    }
  }

  @Override
  public void onChat(String message) {
    if (this.loginOnlyByMod) {
      return;
    }

    if (!LimboAuth.RATELIMITER.attempt(this.proxyPlayer.getRemoteAddress().getAddress())) {
      this.proxyPlayer.sendMessage(AuthSessionHandler.ratelimited);
      return;
    }

    String[] args = message.split(" ");
    if (args.length != 0 && this.checkArgsLength(args.length)) {
      Command command = Command.parse(args[0]);
      if (command == Command.REGISTER && !this.totpState && this.crafterState == CrafterAuthState.NONE && this.playerInfo == null) {
        String password = args[1];
        if (this.checkPasswordsRepeat(args) && this.checkPasswordLength(password) && this.checkPasswordStrength(password)) {
          this.saveTempPassword(password);
          
          // For Crafter CMS, we need to handle registration differently
          if (this.playerDao != null) {
            // Traditional database registration
            RegisteredPlayer registeredPlayer = new RegisteredPlayer(this.proxyPlayer).setPassword(password);

            try {
              this.playerDao.create(registeredPlayer);
              this.playerInfo = registeredPlayer;
            } catch (SQLException e) {
              this.proxyPlayer.disconnect(databaseErrorKick);
              throw new SQLRuntimeException(e);
            }
          } else {
            // Crafter CMS registration - use the API to register the user
            String ipAddress = this.proxyPlayer.getRemoteAddress().getAddress().getHostAddress();
            String email = "";
            
            // Get the plugin instance to access CrafterAuthHandler
            LimboAuth plugin = (LimboAuth) this.plugin;
            if (plugin.getCrafterAuthHandler() != null && plugin.getCrafterAuthHandler().isReady()) {
              CompletableFuture<CrafterResponse> registrationResult = plugin.getCrafterAuthHandler()
                  .registerUser(this.proxyPlayer.getUsername(), email, password, password, ipAddress);
              
              registrationResult.thenAccept(response -> {
                if (response.isSuccess()) {
                  // Registration successful, create a temporary player object
                  this.playerInfo = new RegisteredPlayer(this.proxyPlayer).setPassword(password);
                  
                  if (response.isRequiresEmailVerification()) {
                    this.crafterTempToken = response.getTempToken();
                    this.crafterMaskedEmail = response.getMaskedEmail();
                    this.crafterIsTempEmail = response.isTempEmail();
                    this.joinTime = System.currentTimeMillis();

                    if (response.isTempEmail()) {
                      this.crafterState = CrafterAuthState.AWAITING_EMAIL_INPUT;
                      this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.EMAIL_INPUT_PROMPT));
                    } else {
                      this.crafterState = CrafterAuthState.AWAITING_EMAIL_CODE;
                      this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.REGISTER_EMAIL_SENT, this.crafterMaskedEmail));
                    }
                    return;
                  }

                  this.proxyPlayer.sendMessage(registerSuccessful);
                  if (registerSuccessfulTitle != null) {
                    this.proxyPlayer.showTitle(registerSuccessfulTitle);
                  }

                  this.plugin.getServer().getEventManager()
                      .fire(new PostRegisterEvent(this::finishAuth, this.player, this.playerInfo, this.tempPassword))
                      .thenAcceptAsync(this::finishAuth);
                } else {
                  // Registration failed
                  String msg = response.getMessage();
                  this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.REGISTER_ERROR, msg != null && !msg.isEmpty() ? msg : "Error"));
                }
              }).exceptionally(throwable -> {
                this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.REGISTER_ERROR, throwable.getMessage()));
                return null;
              });
              
              return; // Exit early as we're handling this asynchronously
            } else {
              // Fallback if Crafter API is not available
              this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.SERVICE_UNAVAILABLE));
              return;
            }
          }

          this.proxyPlayer.sendMessage(registerSuccessful);
          if (registerSuccessfulTitle != null) {
            this.proxyPlayer.showTitle(registerSuccessfulTitle);
          }

          this.plugin.getServer().getEventManager()
              .fire(new PostRegisterEvent(this::finishAuth, this.player, this.playerInfo, this.tempPassword))
              .thenAcceptAsync(this::finishAuth);
        }

        return;
      } else if (command == Command.LOGIN && !this.totpState && this.crafterState == CrafterAuthState.NONE && this.playerInfo != null) {
        String password = args[1];
        this.saveTempPassword(password);

        // For Crafter CMS, we need to handle authentication differently
        boolean passwordValid = false;
        if (this.playerDao != null) {
          // Traditional database authentication
          passwordValid = password.length() > 0 && checkPassword(password, this.playerInfo, this.playerDao);
        } else {
          // Crafter CMS authentication - get IP address and authenticate via API
          String ipAddress = this.proxyPlayer.getRemoteAddress().getAddress().getHostAddress();
          
          // Get the plugin instance to access CrafterAuthHandler
          LimboAuth plugin = (LimboAuth) this.plugin;
          if (plugin.getCrafterAuthHandler() != null && plugin.getCrafterAuthHandler().isReady()) {
            CompletableFuture<CrafterResponse> authResult = plugin.getCrafterAuthHandler()
                .authenticateUser(this.proxyPlayer.getUsername(), password, ipAddress);
            
            authResult.thenAccept(response -> {
              if (response.isSuccess()) {
                // Check if 2FA is required
                if (response.isRequires2FA()) {
                  this.crafterState = CrafterAuthState.AWAITING_2FA;
                  this.totpState = true;
                  this.crafterTempToken = response.getTempToken();
                  this.crafterPrimaryMethod = response.getPrimaryMethod();
                  this.joinTime = System.currentTimeMillis();

                  if ("email".equalsIgnoreCase(this.crafterPrimaryMethod)) {
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_EMAIL_SENT));
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_CODE_PROMPT));
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_RESEND_HINT));
                  } else if ("discord".equalsIgnoreCase(this.crafterPrimaryMethod)) {
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_DISCORD_SENT));
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_CODE_PROMPT));
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_RESEND_HINT));
                  } else {
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_APP_REQUIRED));
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_APP_PROMPT));
                  }
                  return;
                }

                // Check if email verification is required
                if (response.isRequiresEmailVerification()) {
                  this.crafterTempToken = response.getTempToken();
                  this.crafterMaskedEmail = response.getMaskedEmail();
                  this.crafterIsTempEmail = response.isTempEmail();
                  this.joinTime = System.currentTimeMillis();

                  if (response.isTempEmail()) {
                    this.crafterState = CrafterAuthState.AWAITING_EMAIL_INPUT;
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.LOGIN_EMAIL_INPUT_REQUIRED));
                  } else {
                    this.crafterState = CrafterAuthState.AWAITING_EMAIL_CODE;
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.LOGIN_EMAIL_SENT, this.crafterMaskedEmail));
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.LOGIN_EMAIL_RESEND_HINT));
                  }
                  return;
                }

                // Authentication fully successful
                if (this.playerInfo.getTotpToken().isEmpty()) {
                  this.finishLogin();
                } else {
                  this.totpState = true;
                  this.sendMessage(true);
                }
              } else {
                // Authentication failed
                if (--this.attempts != 0) {
                  String msg = response.getMessage();
                  if (msg != null && !msg.isEmpty() && !msg.startsWith("HTTP")) {
                    this.proxyPlayer.sendMessage(msg(msg));
                  } else {
                    this.proxyPlayer.sendMessage(loginWrongPassword[this.attempts - 1]);
                  }
                  this.checkBruteforceAttempts();
                } else {
                  this.proxyPlayer.disconnect(loginWrongPasswordKick);
                }
              }
            }).exceptionally(throwable -> {
              this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.AUTH_ERROR, throwable.getMessage()));
              return null;
            });
            
            return; // Exit early as we're handling this asynchronously
          } else {
            // Fallback if Crafter API is not available
            passwordValid = password.length() > 0;
          }
        }

        if (passwordValid) {
          if (this.playerInfo.getTotpToken().isEmpty()) {
            this.finishLogin();
          } else {
            this.totpState = true;
            this.sendMessage(true);
          }
        } else if (--this.attempts != 0) {
          this.proxyPlayer.sendMessage(loginWrongPassword[this.attempts - 1]);
          this.checkBruteforceAttempts();
        } else {
          this.proxyPlayer.disconnect(loginWrongPasswordKick);
        }

        return;
      } else if (this.crafterState == CrafterAuthState.AWAITING_2FA && (command == Command.TOTP || command == Command.VERIFY)) {
        String input = args[1];
        String ipAddress = this.proxyPlayer.getRemoteAddress().getAddress().getHostAddress();
        LimboAuth plugin = (LimboAuth) this.plugin;

        if ("resend".equalsIgnoreCase(input)) {
          if (plugin.getCrafterAuthHandler() != null) {
            plugin.getCrafterAuthHandler().resend2FACode(this.crafterTempToken, this.crafterPrimaryMethod, ipAddress)
                .thenAccept(res -> {
                  if (res.isSuccess()) {
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.RESEND_SUCCESS));
                  } else {
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.RESEND_FAILED, res.getMessage()));
                  }
                }).exceptionally(throwable -> {
                  this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.RESEND_ERROR, throwable.getMessage()));
                  return null;
                });
          }
          return;
        }

        if (plugin.getCrafterAuthHandler() != null) {
          plugin.getCrafterAuthHandler().verify2FA(this.crafterTempToken, input, this.crafterPrimaryMethod, ipAddress)
              .thenAccept(res -> {
                if (res.isSuccess()) {
                  this.crafterState = CrafterAuthState.NONE;
                  this.totpState = false;
                  this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_SUCCESS));
                  this.finishLogin();
                } else {
                  this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_INVALID_CODE));
                  this.checkBruteforceAttempts();
                }
              }).exceptionally(throwable -> {
                this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.TWO_FACTOR_ERROR, throwable.getMessage()));
                return null;
              });
        }
        return;
      } else if (this.crafterState == CrafterAuthState.AWAITING_EMAIL_INPUT && (command == Command.EMAIL || command == Command.VERIFY)) {
        String email = args[1];
        String ipAddress = this.proxyPlayer.getRemoteAddress().getAddress().getHostAddress();
        LimboAuth plugin = (LimboAuth) this.plugin;

        if (plugin.getCrafterAuthHandler() != null) {
          plugin.getCrafterAuthHandler().updateTempEmail(this.crafterTempToken, email, ipAddress)
              .thenAccept(res -> {
                if (res.isSuccess()) {
                  if (res.getTempToken() != null && !res.getTempToken().isEmpty()) {
                    this.crafterTempToken = res.getTempToken();
                  }
                  this.crafterState = CrafterAuthState.AWAITING_EMAIL_CODE;
                  this.joinTime = System.currentTimeMillis();
                  this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.EMAIL_UPDATED_CODE_SENT, email));
                  this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.EMAIL_CODE_INPUT_PROMPT));
                } else {
                  String resMsg = res.getMessage();
                  this.proxyPlayer.sendMessage(msg(resMsg != null && !resMsg.isEmpty() ? resMsg : Messages.IMP.CRAFTER.EMAIL_UPDATE_FAILED));
                }
              }).exceptionally(throwable -> {
                this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.EMAIL_UPDATE_ERROR, throwable.getMessage()));
                return null;
              });
        }
        return;
      } else if (this.crafterState == CrafterAuthState.AWAITING_EMAIL_CODE
          && (command == Command.VERIFY || command == Command.EMAIL || command == Command.TOTP)) {
        String input = args[1];
        String ipAddress = this.proxyPlayer.getRemoteAddress().getAddress().getHostAddress();
        LimboAuth plugin = (LimboAuth) this.plugin;

        if ("resend".equalsIgnoreCase(input)) {
          if (plugin.getCrafterAuthHandler() != null) {
            plugin.getCrafterAuthHandler().resendLoginEmail(this.crafterTempToken, ipAddress)
                .thenAccept(res -> {
                  if (res.isSuccess()) {
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.RESEND_SUCCESS));
                  } else {
                    this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.RESEND_FAILED, res.getMessage()));
                  }
                }).exceptionally(throwable -> {
                  this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.RESEND_ERROR, throwable.getMessage()));
                  return null;
                });
          }
          return;
        }

        if (plugin.getCrafterAuthHandler() != null) {
          plugin.getCrafterAuthHandler().verifyLoginEmail(this.crafterTempToken, input, ipAddress)
              .thenAccept(res -> {
                if (res.isSuccess()) {
                  this.crafterState = CrafterAuthState.NONE;
                  this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.EMAIL_VERIFY_SUCCESS));
                  this.finishLogin();
                } else {
                  this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.EMAIL_VERIFY_INVALID_CODE));
                  this.checkBruteforceAttempts();
                }
              }).exceptionally(throwable -> {
                this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.EMAIL_VERIFY_ERROR, throwable.getMessage()));
                return null;
              });
        }
        return;
      } else if (command == Command.TOTP && this.totpState && this.playerInfo != null) {
        if (TOTP_CODE_VERIFIER.isValidCode(this.playerInfo.getTotpToken(), args[1])) {
          this.finishLogin();
          return;
        } else {
          this.checkBruteforceAttempts();
        }
      }
    }

    this.sendMessage(false);
  }

  @Override
  public void onGeneric(Object packet) {
    if (Settings.IMP.MAIN.MOD.ENABLED && packet instanceof PluginMessagePacket) {
      PluginMessagePacket pluginMessage = (PluginMessagePacket) packet;
      String channel = pluginMessage.getChannel();

      if (channel.equals("MC|Brand") || channel.equals("minecraft:brand")) {
        // Minecraft can't handle the plugin message immediately after going to the PLAY
        // state, so we have to postpone sending it
        if (Settings.IMP.MAIN.MOD.ENABLED) {
          this.proxyPlayer.sendPluginMessage(this.plugin.getChannelIdentifier(this.proxyPlayer), new byte[0]);
        }
      } else if (channel.equals(this.plugin.getChannelIdentifier(this.proxyPlayer).getId())) {
        if (this.tokenReceived) {
          this.checkBruteforceAttempts();
          this.proxyPlayer.disconnect(Component.empty());
          return;
        }

        this.tokenReceived = true;

        if (this.playerInfo == null) {
          return;
        }

        ByteBuf data = pluginMessage.content();

        if (data.readableBytes() < 16) {
          this.checkBruteforceAttempts();
          this.proxyPlayer.sendMessage(sessionExpired);
          return;
        }

        long issueTime = data.readLong();
        long hash = data.readLong();

        if (this.playerInfo.getTokenIssuedAt() > issueTime) {
          this.proxyPlayer.sendMessage(sessionExpired);
          return;
        }

        byte[] lowercaseNicknameSerialized = this.playerInfo.getLowercaseNickname().getBytes(StandardCharsets.UTF_8);
        long correctHash = SipHasher.init(Settings.IMP.MAIN.MOD.VERIFY_KEY)
            .update(lowercaseNicknameSerialized)
            .update(Longs.toByteArray(issueTime))
            .digest();

        if (hash != correctHash) {
          this.checkBruteforceAttempts();
          this.proxyPlayer.sendMessage(sessionExpired);
          return;
        }

        this.finishAuth();
      }
    }
  }

  private void checkBruteforceAttempts() {
    this.plugin.incrementBruteforceAttempts(this.proxyPlayer.getRemoteAddress().getAddress());
    if (this.plugin.getBruteforceAttempts(this.proxyPlayer.getRemoteAddress().getAddress()) >= Settings.IMP.MAIN.BRUTEFORCE_MAX_ATTEMPTS) {
      this.proxyPlayer.disconnect(loginWrongPasswordKick);
    }
  }

  private void saveTempPassword(String password) {
    this.tempPassword = password;
  }

  @Override
  public void onDisconnect() {
    if (this.authMainTask != null) {
      this.authMainTask.cancel(true);
    }

    this.proxyPlayer.hideBossBar(this.bossBar);
    this.plugin.removeAuthenticatingPlayer(this.player.getProxyPlayer().getUsername());
  }

  private void sendMessage(boolean sendTitle) {
    if (this.crafterState == CrafterAuthState.AWAITING_2FA) {
      this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.REMINDER_2FA));
    } else if (this.crafterState == CrafterAuthState.AWAITING_EMAIL_INPUT) {
      this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.REMINDER_EMAIL_INPUT));
    } else if (this.crafterState == CrafterAuthState.AWAITING_EMAIL_CODE) {
      this.proxyPlayer.sendMessage(msg(Messages.IMP.CRAFTER.REMINDER_EMAIL_CODE));
    } else if (this.totpState) {
      this.proxyPlayer.sendMessage(totp);
      if (sendTitle && totpTitle != null) {
        this.proxyPlayer.showTitle(totpTitle);
      }
    } else if (this.playerInfo == null) {
      this.proxyPlayer.sendMessage(register);
      if (sendTitle && registerTitle != null) {
        this.proxyPlayer.showTitle(registerTitle);
      }
    } else {
      this.proxyPlayer.sendMessage(login[this.attempts - 1]);
      if (sendTitle && loginTitle != null) {
        this.proxyPlayer.showTitle(loginTitle);
      }
    }
  }

  private boolean checkArgsLength(int argsLength) {
    if (this.crafterState != CrafterAuthState.NONE) {
      return argsLength == 2;
    } else if (this.playerInfo == null && Settings.IMP.MAIN.REGISTER_NEED_REPEAT_PASSWORD) {
      return argsLength == 3;
    } else {
      return argsLength == 2;
    }
  }

  private boolean checkPasswordsRepeat(String[] args) {
    if (!Settings.IMP.MAIN.REGISTER_NEED_REPEAT_PASSWORD || args[1].equals(args[2])) {
      return true;
    } else {
      this.proxyPlayer.sendMessage(registerDifferentPasswords);
      return false;
    }
  }

  private boolean checkPasswordLength(String password) {
    int length = password.length();
    if (length > Settings.IMP.MAIN.MAX_PASSWORD_LENGTH) {
      this.proxyPlayer.sendMessage(registerPasswordTooLong);
      return false;
    } else if (length < Settings.IMP.MAIN.MIN_PASSWORD_LENGTH) {
      this.proxyPlayer.sendMessage(registerPasswordTooShort);
      return false;
    } else {
      return true;
    }
  }

  private boolean checkPasswordStrength(String password) {
    if (Settings.IMP.MAIN.CHECK_PASSWORD_STRENGTH && this.plugin.getUnsafePasswords().contains(password)) {
      this.proxyPlayer.sendMessage(registerPasswordUnsafe);
      return false;
    } else {
      return true;
    }
  }

  public void finishLogin() {
    this.proxyPlayer.sendMessage(loginSuccessful);
    if (loginSuccessfulTitle != null) {
      this.proxyPlayer.showTitle(loginSuccessfulTitle);
    }

    this.plugin.clearBruteforceAttempts(this.proxyPlayer.getRemoteAddress().getAddress());

    this.plugin.getServer().getEventManager()
        .fire(new PostAuthorizationEvent(this::finishAuth, this.player, this.playerInfo, this.tempPassword))
        .thenAcceptAsync(this::finishAuth);
  }

  private void finishAuth(TaskEvent event) {
    if (event.getResult() == TaskEvent.Result.CANCEL) {
      this.proxyPlayer.disconnect(event.getReason());
      return;
    } else if (event.getResult() == TaskEvent.Result.WAIT) {
      return;
    }

    this.finishAuth();
  }

  private void finishAuth() {
    if (Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.CLEAR_AFTER_LOGIN) {
      this.proxyPlayer.clearTitle();
    }

    try {
      this.plugin.updateLoginData(this.proxyPlayer);
    } catch (SQLException e) {
      // For Crafter CMS, this is expected behavior
      if (this.plugin.getDatabaseLibrary() == DatabaseLibrary.CRAFTER) {
        LOGGER.debug("Skipping database update for Crafter CMS user: {}", this.proxyPlayer.getUsername());
      } else {
        throw new SQLRuntimeException(e);
      }
    } catch (Throwable e) {
      LOGGER.error("Error updating login data for player: {}", this.proxyPlayer.getUsername(), e);
    }

    this.plugin.cacheAuthUser(this.proxyPlayer);
    this.player.disconnect();
  }

  public static void reload() {
    serializer = LimboAuth.getSerializer();
    AuthSessionHandler.ratelimited = serializer.deserialize(Messages.IMP.GENERAL.RATELIMITED);
    bossbarColor = Settings.IMP.MAIN.BOSSBAR_COLOR;
    bossbarOverlay = Settings.IMP.MAIN.BOSSBAR_OVERLAY;
    ipLimitKick = serializer.deserialize(Messages.IMP.KICK.IP_LIMIT);
    databaseErrorKick = serializer.deserialize(Messages.IMP.GENERAL.DATABASE_ERROR_KICK);
    wrongNicknameCaseKick = Messages.IMP.KICK.WRONG_NICKNAME_CASE;
    timesUp = serializer.deserialize(Messages.IMP.AUTH.TIMES_UP);
    registerSuccessful = serializer.deserialize(Messages.IMP.REGISTER.REGISTER_SUCCESSFUL);
    if (Messages.IMP.REGISTER.REGISTER_SUCCESSFUL_TITLE.isEmpty() && Messages.IMP.REGISTER.REGISTER_SUCCESSFUL_SUBTITLE.isEmpty()) {
      registerSuccessfulTitle = null;
    } else {
      registerSuccessfulTitle = Title.title(
          serializer.deserialize(Messages.IMP.REGISTER.REGISTER_SUCCESSFUL_TITLE),
          serializer.deserialize(Messages.IMP.REGISTER.REGISTER_SUCCESSFUL_SUBTITLE),
          Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.toTimes()
      );
    }
    int loginAttempts = Settings.IMP.MAIN.LOGIN_ATTEMPTS;
    loginWrongPassword = new Component[loginAttempts];
    for (int i = 0; i < loginAttempts; ++i) {
      loginWrongPassword[i] = serializer.deserialize(MessageFormat.format(Messages.IMP.AUTH.LOGIN_WRONG_PASSWORD, i + 1));
    }
    loginWrongPasswordKick = serializer.deserialize(Messages.IMP.AUTH.LOGIN_WRONG_PASSWORD_KICK);
    totp = serializer.deserialize(Messages.IMP.TOTP.PROMPT);
    if (Messages.IMP.TOTP.TITLE.isEmpty() && Messages.IMP.TOTP.SUBTITLE.isEmpty()) {
      totpTitle = null;
    } else {
      totpTitle = Title.title(
          serializer.deserialize(Messages.IMP.TOTP.TITLE),
          serializer.deserialize(Messages.IMP.TOTP.SUBTITLE),
          Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.toTimes()
      );
    }
    register = serializer.deserialize(Messages.IMP.REGISTER.REGISTER);
    if (Messages.IMP.REGISTER.REGISTER_TITLE.isEmpty() && Messages.IMP.REGISTER.REGISTER_SUBTITLE.isEmpty()) {
      registerTitle = null;
    } else {
      registerTitle = Title.title(
          serializer.deserialize(Messages.IMP.REGISTER.REGISTER_TITLE),
          serializer.deserialize(Messages.IMP.REGISTER.REGISTER_SUBTITLE),
          Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.toTimes()
      );
    }
    login = new Component[loginAttempts];
    for (int i = 0; i < loginAttempts; ++i) {
      login[i] = serializer.deserialize(MessageFormat.format(Messages.IMP.AUTH.LOGIN, i + 1));
    }
    if (Messages.IMP.AUTH.LOGIN_TITLE.isEmpty() && Messages.IMP.AUTH.LOGIN_SUBTITLE.isEmpty()) {
      loginTitle = null;
    } else {
      loginTitle = Title.title(
          serializer.deserialize(MessageFormat.format(Messages.IMP.AUTH.LOGIN_TITLE, loginAttempts)),
          serializer.deserialize(MessageFormat.format(Messages.IMP.AUTH.LOGIN_SUBTITLE, loginAttempts)),
          Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.toTimes()
      );
    }
    registerDifferentPasswords = serializer.deserialize(Messages.IMP.REGISTER.REGISTER_DIFFERENT_PASSWORDS);
    registerPasswordTooLong = serializer.deserialize(Messages.IMP.REGISTER.REGISTER_PASSWORD_TOO_LONG);
    registerPasswordTooShort = serializer.deserialize(Messages.IMP.REGISTER.REGISTER_PASSWORD_TOO_SHORT);
    registerPasswordUnsafe = serializer.deserialize(Messages.IMP.REGISTER.REGISTER_PASSWORD_UNSAFE);
    loginSuccessful = serializer.deserialize(Messages.IMP.AUTH.LOGIN_SUCCESSFUL);
    sessionExpired = serializer.deserialize(Messages.IMP.AUTH.SESSION_EXPIRED);
    if (Messages.IMP.AUTH.LOGIN_SUCCESSFUL_TITLE.isEmpty() && Messages.IMP.AUTH.LOGIN_SUCCESSFUL_SUBTITLE.isEmpty()) {
      loginSuccessfulTitle = null;
    } else {
      loginSuccessfulTitle = Title.title(
          serializer.deserialize(Messages.IMP.AUTH.LOGIN_SUCCESSFUL_TITLE),
          serializer.deserialize(Messages.IMP.AUTH.LOGIN_SUCCESSFUL_SUBTITLE),
          Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.toTimes()
      );
    }

    migrationHash = Settings.IMP.MAIN.MIGRATION_HASH;
  }

  private static Component msg(String template, Object... args) {
    if (template == null || template.isEmpty()) {
      return Component.empty();
    }
    String text = args.length > 0 ? MessageFormat.format(template, args) : template;
    return serializer.deserialize(text);
  }

  public static boolean checkPassword(String password, RegisteredPlayer player, Dao<RegisteredPlayer, String> playerDao) {
    String hash = player.getHash();
    boolean isCorrect = HASH_VERIFIER.verify(
        password.getBytes(StandardCharsets.UTF_8),
        hash.replace("BCRYPT$", "$2a$").getBytes(StandardCharsets.UTF_8)
    ).verified;

    if (!isCorrect && migrationHash != null) {
      isCorrect = migrationHash.checkPassword(hash, password);
      if (isCorrect) {
        player.setPassword(password);
        try {
          playerDao.update(player);
        } catch (SQLException e) {
          throw new SQLRuntimeException(e);
        }
      }
    }

    return isCorrect;
  }

  public static RegisteredPlayer fetchInfo(Dao<RegisteredPlayer, String> playerDao, UUID uuid) {
    try {
      List<RegisteredPlayer> playerList = playerDao.queryForEq(RegisteredPlayer.PREMIUM_UUID_FIELD, uuid.toString());
      return (playerList != null ? playerList.size() : 0) == 0 ? null : playerList.get(0);
    } catch (SQLException e) {
      throw new SQLRuntimeException(e);
    }
  }

  public static RegisteredPlayer fetchInfo(Dao<RegisteredPlayer, String> playerDao, String nickname) {
    return AuthSessionHandler.fetchInfoLowercased(playerDao, nickname.toLowerCase(Locale.ROOT));
  }

  public static RegisteredPlayer fetchInfoLowercased(Dao<RegisteredPlayer, String> playerDao, String nickname) {
    // Check if playerDao is null (Crafter CMS case)
    if (playerDao == null) {
      // For Crafter CMS, we can't fetch from database, so return null
      // The actual authentication will be handled by Crafter CMS API
      return null;
    }
    
    try {
      List<RegisteredPlayer> playerList = playerDao.queryForEq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, nickname);
      return (playerList != null ? playerList.size() : 0) == 0 ? null : playerList.get(0);
    } catch (SQLException e) {
      throw new SQLRuntimeException(e);
    }
  }

  /**
   * Use {@link RegisteredPlayer#genHash(String)} or {@link RegisteredPlayer#setPassword}
   */
  @Deprecated()
  public static String genHash(String password) {
    return HASHER.hashToString(Settings.IMP.MAIN.BCRYPT_COST, password.toCharArray());
  }


  private enum Command {

    INVALID,
    REGISTER,
    LOGIN,
    TOTP,
    VERIFY,
    EMAIL;

    static Command parse(String command) {
      if (Settings.IMP.MAIN.REGISTER_COMMAND.contains(command)) {
        return Command.REGISTER;
      } else if (Settings.IMP.MAIN.LOGIN_COMMAND.contains(command)) {
        return Command.LOGIN;
      } else if (Settings.IMP.MAIN.TOTP_COMMAND.contains(command)) {
        return Command.TOTP;
      } else if (Settings.IMP.MAIN.CRAFTER_VERIFY_COMMAND.contains(command)) {
        return Command.VERIFY;
      } else if (Settings.IMP.MAIN.CRAFTER_EMAIL_COMMAND.contains(command)) {
        return Command.EMAIL;
      } else {
        return Command.INVALID;
      }
    }
  }
}
