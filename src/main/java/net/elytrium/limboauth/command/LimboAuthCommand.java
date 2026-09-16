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

package net.elytrium.limboauth.command;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.elytrium.limboauth.LimboAuth;
import net.elytrium.limboauth.Messages;
import net.elytrium.limboauth.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class LimboAuthCommand extends RatelimitedCommand {

  private final LimboAuth plugin;

  public LimboAuthCommand(LimboAuth plugin) {
    this.plugin = plugin;
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 0) {
      return Arrays.stream(Subcommand.values())
          .filter(command -> command.hasPermission(source))
          .map(Subcommand::getCommand)
          .collect(Collectors.toList());
    } else if (args.length == 1) {
      String argument = args[0];
      return Arrays.stream(Subcommand.values())
          .filter(command -> command.hasPermission(source))
          .map(Subcommand::getCommand)
          .filter(str -> str.regionMatches(true, 0, argument, 0, argument.length()))
          .collect(Collectors.toList());
    } else {
      return ImmutableList.of();
    }
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    int argsAmount = args.length;
    if (argsAmount > 0) {
      try {
        Subcommand subcommand = Subcommand.valueOf(args[0].toUpperCase(Locale.ROOT));
        if (!subcommand.hasPermission(source)) {
          this.showHelp(source);
          return;
        }

        subcommand.executor.execute(this, source, args);
      } catch (IllegalArgumentException e) {
        this.showHelp(source);
      }
    } else {
      this.showHelp(source);
    }
  }

  @Override
  public boolean hasPermission(Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.HELP
        .hasPermission(invocation.source(), "limboauth.commands.help");
  }

  private void showHelp(CommandSource source) {
    source.sendMessage(Component.text(Messages.IMP.ADMIN.HELP_HEADER, NamedTextColor.YELLOW));
    source.sendMessage(Component.text(Messages.IMP.ADMIN.HELP_COPYRIGHT, NamedTextColor.YELLOW));
    source.sendMessage(Component.text(Messages.IMP.ADMIN.HELP_URL, NamedTextColor.GREEN));
    source.sendMessage(Component.empty());

    List<Subcommand> availableSubcommands = Arrays.stream(Subcommand.values())
        .filter(command -> command.hasPermission(source))
        .collect(Collectors.toList());

    if (availableSubcommands.size() > 0) {
      source.sendMessage(Component.text(Messages.IMP.ADMIN.SUBCOMMANDS_AVAILABLE, NamedTextColor.WHITE));
      availableSubcommands.forEach(command -> source.sendMessage(command.getMessageLine()));
    } else {
      source.sendMessage(Component.text(Messages.IMP.ADMIN.SUBCOMMANDS_NONE, NamedTextColor.WHITE));
    }
  }

  private enum Subcommand {
    RELOAD(() -> Messages.IMP.ADMIN.SUBCOMMAND_RELOAD_DESC, Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.RELOAD,
        (LimboAuthCommand parent, CommandSource source, String[] args) -> {
          parent.plugin.reload();
          source.sendMessage(LimboAuth.getSerializer().deserialize(Messages.IMP.GENERAL.RELOAD));
        }),
    TEST_CRAFTER(() -> Messages.IMP.ADMIN.SUBCOMMAND_TEST_CRAFTER_DESC, Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.RELOAD,
        (LimboAuthCommand parent, CommandSource source, String[] args) -> {
          if (parent.plugin.getCrafterAPIClient() != null) {
            source.sendMessage(LimboAuth.getSerializer().deserialize(Messages.IMP.ADMIN.TEST_CRAFTER_TESTING));
            parent.plugin.getCrafterAPIClient().testConnection().thenAccept(result -> {
              source.sendMessage(LimboAuth.getSerializer().deserialize(
                  MessageFormat.format(Messages.IMP.ADMIN.TEST_CRAFTER_RESULT, result)));
            });
          } else {
            source.sendMessage(LimboAuth.getSerializer().deserialize(Messages.IMP.ADMIN.TEST_CRAFTER_NOT_AVAILABLE));
          }
        });

    private final String command;
    private final Supplier<String> descriptionSupplier;
    private final CommandPermissionState permissionState;
    private final SubcommandExecutor executor;

    Subcommand(Supplier<String> descriptionSupplier, CommandPermissionState permissionState, SubcommandExecutor executor) {
      this.permissionState = permissionState;
      this.command = this.name().toLowerCase(Locale.ROOT);
      this.descriptionSupplier = descriptionSupplier;
      this.executor = executor;
    }

    public boolean hasPermission(CommandSource source) {
      return this.permissionState.hasPermission(source, "limboauth.admin." + this.command);
    }

    public Component getMessageLine() {
      return Component.textOfChildren(
          Component.text("  /limboauth " + this.command, NamedTextColor.GREEN),
          Component.text(" - ", NamedTextColor.DARK_GRAY),
          Component.text(this.descriptionSupplier.get(), NamedTextColor.YELLOW)
      );
    }

    public String getCommand() {
      return this.command;
    }
  }

  private interface SubcommandExecutor {
    void execute(LimboAuthCommand parent, CommandSource source, String[] args);
  }
}
