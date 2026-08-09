package me.ayinaki.sharedchain.command;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.run.RunManager;
import me.ayinaki.sharedchain.run.RunState;
import me.ayinaki.sharedchain.util.ComponentUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SharedChainCommand implements CommandExecutor, TabCompleter {
    private final SharedChain plugin;

    public SharedChainCommand(SharedChain plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("sharedchain.use")) {
            sender.sendMessage(ComponentUtil.parse(plugin.getConfig().getString("messages.no-permission", "<red>You don't have permission to do that.</red>")));
            return true;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start", "startconfirm" -> handleStart(sender);
            case "reset" -> handleReset(sender);
            case "stop" -> handleStop(sender);
            case "status" -> handleStatus(sender);
            case "timer" -> handleTimer(sender);
            case "reload" -> {
                if (!sender.hasPermission("sharedchain.admin")) return true;
                plugin.reloadConfig();
                // The below-name death counter reads config (icon + toggle) at refresh time.
                plugin.getUIService().refreshDeathDisplayName();
                sender.sendMessage(ComponentUtil.parse("<green>Config reloaded!</green>"));
            }
            case "stats" -> handleStatsCommand(sender, args);
            case "fonts" -> handleFontsCommand(sender, args);
            case "color" -> handleColorCommand(sender, args);
            default -> sender.sendMessage(ComponentUtil.parse("<red>Unknown subcommand '<yellow>" + args[0] + "</yellow>'. Run <yellow>/sharedchain</yellow> for help.</red>"));
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ComponentUtil.parse("<gold>SharedChain</gold> <gray>v" + plugin.getPluginMeta().getVersion() + "</gray> — commands:"));
        sender.sendMessage(ComponentUtil.parse("  <aqua>/sharedchain start</aqua> <gray>— begin the run (lobby countdown)</gray>"));
        sender.sendMessage(ComponentUtil.parse("  <aqua>/sharedchain reset</aqua> <gray>— regenerate the world & return to the lobby</gray>"));
        sender.sendMessage(ComponentUtil.parse("  <aqua>/sharedchain stop</aqua> <gray>— end the current run</gray>"));
        sender.sendMessage(ComponentUtil.parse("  <aqua>/sharedchain status</aqua> <gray>— run state, run #, timers, shared HP</gray>"));
        sender.sendMessage(ComponentUtil.parse("  <aqua>/sharedchain timer</aqua> <gray>— current run time</gray>"));
        sender.sendMessage(ComponentUtil.parse("  <aqua>/sharedchain color <color> [player]</aqua> <gray>— set your name color</gray>"));
        sender.sendMessage(ComponentUtil.parse("  <aqua>/sharedchain stats</aqua> <gray>— view/edit run counter & death counts</gray>"));
        sender.sendMessage(ComponentUtil.parse("  <aqua>/sharedchain fonts</aqua> <gray>— font images & resource pack</gray>"));
        sender.sendMessage(ComponentUtil.parse("  <aqua>/sharedchain reload</aqua> <gray>— reload the config</gray>"));
    }

    private void handleStart(CommandSender sender) {
        if (!sender.hasPermission("sharedchain.admin")) return;
        switch (plugin.getRunManager().getState()) {
            case RUNNING -> sender.sendMessage(ComponentUtil.parse("<red>A run is already in progress. Use <yellow>/sharedchain stop</yellow> to end it.</red>"));
            case STARTING -> sender.sendMessage(ComponentUtil.parse("<yellow>The countdown is already running.</yellow>"));
            case RESETTING -> sender.sendMessage(ComponentUtil.parse("<red>The world is still resetting — try again in a moment.</red>"));
            case WIPED, FINISHED -> sender.sendMessage(ComponentUtil.parse("<red>The previous run has ended. Run <yellow>/sharedchain reset</yellow> to generate a fresh world, then <yellow>/sharedchain start</yellow>.</red>"));
            default -> {
                plugin.getLobbyService().startCountdown();
                sender.sendMessage(ComponentUtil.parse("<green>Countdown started — the run begins in a few seconds!</green>"));
            }
        }
    }

    private void handleReset(CommandSender sender) {
        if (!sender.hasPermission("sharedchain.admin")) return;
        if (plugin.getRunManager().getState() == RunState.RESETTING) {
            sender.sendMessage(ComponentUtil.parse("<red>The world is already resetting.</red>"));
            return;
        }
        plugin.getResetService().triggerReset();
        sender.sendMessage(ComponentUtil.parse("<yellow>Regenerating the world... you'll be moved to the lobby when it's ready.</yellow>"));
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("sharedchain.admin")) return;
        if (plugin.getRunManager().getState() != RunState.RUNNING) {
            sender.sendMessage(ComponentUtil.parse("<red>No run is currently in progress.</red>"));
            return;
        }
        plugin.getRunManager().stop();
        // The run turned off natural regen and unfroze the world clock. The team may
        // wait in-world between a stop and the next start/reset, so restore the
        // lobby rules (regen on, day/night cycle frozen) without touching the
        // border - shrinking it while players are scattered would hurt them.
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.getRunManager().isWorldEnabled(world)) continue;
            world.setGameRule(org.bukkit.GameRules.NATURAL_HEALTH_REGENERATION, true);
            world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
            world.setFullTime(0);
        }
        sender.sendMessage(plugin.getComponentUtil().getMessage("run-stopped"));
    }

    private void handleStatus(CommandSender sender) {
        RunManager runManager = plugin.getRunManager();
        RunState state = runManager.getState();
        sender.sendMessage(ComponentUtil.parse("<gold>Status: <white>" + state + "</white></gold>"));
        sender.sendMessage(ComponentUtil.parse("<gold>Run #: <white>" + runManager.getRunCounter() + "</white></gold>"));
        sender.sendMessage(ComponentUtil.parse("<gold>Participants: <white>" + runManager.getParticipants().size() + "</white></gold>"));
        sender.sendMessage(ComponentUtil.parse("<gold>Current time: <white>" + (state == RunState.RUNNING ? plugin.getTimerService().getFormattedTime() : "—") + "</white></gold>"));
        sender.sendMessage(ComponentUtil.parse("<gold>Total time: <white>" + plugin.getTimerService().getFormattedTotalTime() + "</white></gold>"));
        if (state == RunState.RUNNING) {
            sender.sendMessage(ComponentUtil.parse("<gold>Shared HP: <red>" + String.format("%.1f", runManager.getSharedState().getHealth()) + "</red></gold>"));
        }
        // Live world-border diagnostic: shows what the client is being told on join.
        World challenge = plugin.getFakeOverworld();
        if (challenge != null) {
            org.bukkit.WorldBorder border = challenge.getWorldBorder();
            sender.sendMessage(ComponentUtil.parse("<gold>World: <white>" + challenge.getName()
                    + "</white> <gray>(<white>" + challenge.getKey().asString() + "</white>)</gray></gold>"));
            sender.sendMessage(ComponentUtil.parse("<gold>Border: <white>" + String.format("%.1f", border.getSize())
                    + "</white> <gray>center <white>" + String.format("%.1f, %.1f", border.getCenter().getX(), border.getCenter().getZ())
                    + "</white></gray></gold>"));
        }
    }

    private void handleTimer(CommandSender sender) {
        sender.sendMessage(ComponentUtil.parse("<gold>Current time: <white>" + plugin.getTimerService().getFormattedTime() + "</white></gold>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(List.of("start", "stop", "reset", "status", "timer", "reload", "help", "stats", "fonts", "color"));
            return subcommands.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && "color".equalsIgnoreCase(args[0])) {
            return new ArrayList<>(me.ayinaki.sharedchain.display.UserInterfaceService.getColorNames());
        }
        if (args.length == 3 && "color".equalsIgnoreCase(args[0])) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            return List.of("runs", "deaths");
        }
        if (args.length == 2 && "fonts".equalsIgnoreCase(args[0])) {
            return List.of("list", "reload", "test");
        }
        if (args.length == 3 && "fonts".equalsIgnoreCase(args[0]) && "test".equalsIgnoreCase(args[1])) {
            return plugin.getFontImageService().getImageNames();
        }
        if (args.length == 3 && "stats".equalsIgnoreCase(args[0])) {
            if ("runs".equalsIgnoreCase(args[1])) {
                return List.of("get", "set", "add");
            }
            if ("deaths".equalsIgnoreCase(args[1])) {
                return List.of("get", "set", "add");
            }
        }
        if (args.length == 4 && "stats".equalsIgnoreCase(args[0]) && "deaths".equalsIgnoreCase(args[1])) {
            if ("set".equalsIgnoreCase(args[2]) || "add".equalsIgnoreCase(args[2]) || "get".equalsIgnoreCase(args[2])) {
                return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
            }
        }
        return List.of();
    }

    private void handleStatsCommand(CommandSender sender, String[] args) {
        if (!isOpOrConsole(sender)) {
            sender.sendMessage(ComponentUtil.parse(plugin.getConfig().getString("messages.no-permission", "<red>You don't have permission to do that.</red>")));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ComponentUtil.parse("<yellow>Usage:</yellow> <white>/sharedchain stats runs <get|set|add> [value]</white>"));
            sender.sendMessage(ComponentUtil.parse("<yellow>Usage:</yellow> <white>/sharedchain stats deaths <get|set|add> <player> [value]</white>"));
            return;
        }

        if ("runs".equalsIgnoreCase(args[1])) {
            handleRunStats(sender, args);
            return;
        }
        if ("deaths".equalsIgnoreCase(args[1])) {
            handleDeathStats(sender, args);
            return;
        }

        sender.sendMessage(ComponentUtil.parse("<red>Unknown stats category.</red>"));
    }

    private void handleRunStats(CommandSender sender, String[] args) {
        String action = args[2].toLowerCase();
        int current = plugin.getRunManager().getRunCounter();

        switch (action) {
            case "get" -> sender.sendMessage(ComponentUtil.parse("<gold>Runs: <aqua>" + current + "</aqua></gold>"));
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(ComponentUtil.parse("<red>Usage: /sharedchain stats runs set <value></red>"));
                    return;
                }
                Integer value = parseNonNegativeInt(args[3]);
                if (value == null) {
                    sender.sendMessage(ComponentUtil.parse("<red>Value must be a non-negative integer.</red>"));
                    return;
                }
                plugin.getRunManager().setRunCounter(value);
                plugin.getUIService().refreshAttemptBossBar();
                plugin.getUIService().updateAll();
                sender.sendMessage(ComponentUtil.parse("<green>Run counter set to <aqua>" + value + "</aqua>.</green>"));
            }
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(ComponentUtil.parse("<red>Usage: /sharedchain stats runs add <value></red>"));
                    return;
                }
                Integer value = parseNonNegativeInt(args[3]);
                if (value == null) {
                    sender.sendMessage(ComponentUtil.parse("<red>Value must be a non-negative integer.</red>"));
                    return;
                }
                int updated = current + value;
                plugin.getRunManager().setRunCounter(updated);
                plugin.getUIService().refreshAttemptBossBar();
                plugin.getUIService().updateAll();
                sender.sendMessage(ComponentUtil.parse("<green>Run counter is now <aqua>" + updated + "</aqua>.</green>"));
            }
            default -> sender.sendMessage(ComponentUtil.parse("<red>Usage: /sharedchain stats runs <get|set|add> [value]</red>"));
        }
    }

    private void handleDeathStats(CommandSender sender, String[] args) {
        String action = args[2].toLowerCase();
        if (args.length < 4) {
            sender.sendMessage(ComponentUtil.parse("<red>Usage: /sharedchain stats deaths <get|set|add> <player> [value]</red>"));
            return;
        }

        String targetName = args[3];

        final Integer value;
        if ("set".equalsIgnoreCase(action) || "add".equalsIgnoreCase(action)) {
            if (args.length < 5) {
                sender.sendMessage(ComponentUtil.parse("<red>Usage: /sharedchain stats deaths " + action + " <player> <value></red>"));
                return;
            }
            value = parseNonNegativeInt(args[4]);
            if (value == null) {
                sender.sendMessage(ComponentUtil.parse("<red>Value must be a non-negative integer.</red>"));
                return;
            }
        } else if (!"get".equalsIgnoreCase(action)) {
            sender.sendMessage(ComponentUtil.parse("<red>Usage: /sharedchain stats deaths <get|set|add> <player> [value]</red>"));
            return;
        } else {
            value = null;
        }

        // Perform offline player lookup asynchronously to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var offline = plugin.getServer().getOfflinePlayer(targetName);
            if ((offline.getName() == null || offline.getName().isBlank()) && !offline.isOnline() && !offline.hasPlayedBefore()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ComponentUtil.parse("<red>Unknown player: " + targetName + "</red>"))
                );
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                int current = plugin.getDeathTrackerService().getDeaths(offline.getUniqueId());
                switch (action) {
                    case "get" -> sender.sendMessage(ComponentUtil.parse("<gold>Deaths for <yellow>" + targetName + "</yellow>: <red>" + current + "</red></gold>"));
                    case "set" -> {
                        plugin.getDeathTrackerService().setDeaths(offline.getUniqueId(), value);
                        sender.sendMessage(ComponentUtil.parse("<green>Deaths for <yellow>" + targetName + "</yellow> set to <red>" + value + "</red>.</green>"));
                    }
                    case "add" -> {
                        int updated = current + value;
                        plugin.getDeathTrackerService().setDeaths(offline.getUniqueId(), updated);
                        sender.sendMessage(ComponentUtil.parse("<green>Deaths for <yellow>" + targetName + "</yellow> are now <red>" + updated + "</red>.</green>"));
                    }
                }
            });
        });
    }

    private void handleColorCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ComponentUtil.parse("<red>Usage:</red> <white>/sharedchain color <color> [player]</white>"));
                return;
            }
            me.ayinaki.sharedchain.display.UserInterfaceService ui = plugin.getUIService();
            net.kyori.adventure.text.format.NamedTextColor current = ui.getNameColor(player.getUniqueId());
            sender.sendMessage(ComponentUtil.parse("<gold>Your name color: <" + current + ">" + current + "</" + current + "></gold>"));
            sender.sendMessage(ComponentUtil.parse("<gray>Colors: <white>" + String.join(", ", me.ayinaki.sharedchain.display.UserInterfaceService.getColorNames()) + "</white></gray>"));
            return;
        }
        if (args.length == 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ComponentUtil.parse("<red>Usage:</red> <white>/sharedchain color <color> [player]</white>"));
                return;
            }
            net.kyori.adventure.text.format.NamedTextColor color = me.ayinaki.sharedchain.display.UserInterfaceService.parseColor(args[1]);
            if (color == null) {
                sender.sendMessage(ComponentUtil.parse("<red>Unknown color '<yellow>" + args[1] + "</yellow>'. Available: <white>"
                        + String.join(", ", me.ayinaki.sharedchain.display.UserInterfaceService.getColorNames()) + "</white></red>"));
                return;
            }
            plugin.getUIService().setNameColor(player.getUniqueId(), color);
            sender.sendMessage(ComponentUtil.parse("<green>Name color set to <" + color + ">" + color + "</" + color + ">!</green>"));
            return;
        }
        if (args.length == 3) {
            if (!sender.hasPermission("sharedchain.admin")) {
                sender.sendMessage(ComponentUtil.parse(plugin.getConfig().getString("messages.no-permission", "<red>You don't have permission to do that.</red>")));
                return;
            }
            net.kyori.adventure.text.format.NamedTextColor color = me.ayinaki.sharedchain.display.UserInterfaceService.parseColor(args[2]);
            if (color == null) {
                sender.sendMessage(ComponentUtil.parse("<red>Unknown color '<yellow>" + args[2] + "</yellow>'. Available: <white>"
                        + String.join(", ", me.ayinaki.sharedchain.display.UserInterfaceService.getColorNames()) + "</white></red>"));
                return;
            }
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ComponentUtil.parse("<red>Player '<yellow>" + args[1] + "</yellow>' is not online.</red>"));
                return;
            }
            plugin.getUIService().setNameColor(target.getUniqueId(), color);
            sender.sendMessage(ComponentUtil.parse("<green>Set <yellow>" + target.getName() + "</yellow>'s name color to <" + color + ">" + color + "</" + color + ">.</green>"));
            return;
        }
        sender.sendMessage(ComponentUtil.parse("<red>Usage:</red> <white>/sharedchain color <color> [player]</white>"));
    }

    private void handleFontsCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            listFonts(sender);
            return;
        }
        if ("reload".equalsIgnoreCase(args[1])) {
            if (!isOpOrConsole(sender)) {
                sender.sendMessage(ComponentUtil.parse(plugin.getConfig().getString("messages.no-permission", "<red>You don't have permission to do that.</red>")));
                return;
            }
            plugin.getFontImageService().reload();
            sender.sendMessage(ComponentUtil.parse("<green>Font images reloaded!</green>"));
            return;
        }
        if ("test".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sender.sendMessage(ComponentUtil.parse("<red>Usage:</red> <white>/sharedchain fonts test <name></white>"));
                return;
            }
            String name = args[2];
            if (!plugin.getFontImageService().getImageNames().contains(name)) {
                sender.sendMessage(ComponentUtil.parse("<red>Unknown font image '<yellow>" + name + "</yellow>'. Use /sharedchain fonts to list them.</red>"));
                return;
            }
            String prefix = plugin.getConfig().getString("messages.prefix", "<dark_gray>[<gold>SharedChain</gold>]</dark_gray> ");
            Bukkit.broadcast(ComponentUtil.parse(prefix + "<gray>Font test:</gray> <white>%" + name + "%</white>"));
            return;
        }
        sender.sendMessage(ComponentUtil.parse("<red>Usage:</red> <white>/sharedchain fonts <list|reload|test></white>"));
    }

    private void listFonts(CommandSender sender) {
        List<String> names = plugin.getFontImageService().getImageNames();
        sender.sendMessage(ComponentUtil.parse("<gold>Font images (<white>" + names.size() + "</white>):</gold>"));
        for (String name : names) {
            sender.sendMessage(ComponentUtil.parse("  <white>%" + name + "%</white>"));
        }
        String packUrl = plugin.getFontImageService().getPackUrl();
        sender.sendMessage(ComponentUtil.parse(packUrl != null
                ? "<gray>Pack URL: <aqua>" + packUrl + "</aqua></gray>"
                : "<gray>Pack server is not running.</gray>"));
    }

    private boolean isOpOrConsole(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        return player.isOp();
    }

    private Integer parseNonNegativeInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) return null;
            return parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
