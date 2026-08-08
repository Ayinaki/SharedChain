package me.ayinaki.ayinchallenge.command;

import me.ayinaki.ayinchallenge.AyinChallenge;
import me.ayinaki.ayinchallenge.run.RunState;
import me.ayinaki.ayinchallenge.util.ComponentUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AyinChallengeCommand implements CommandExecutor, TabCompleter {
    private final AyinChallenge plugin;

    public AyinChallengeCommand(AyinChallenge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("ayinchallenge.use")) {
            sender.sendMessage(ComponentUtil.parse(plugin.getConfig().getString("messages.no-permission", "<red>You don't have permission to do that.</red>")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ComponentUtil.parse("<gold>AyinChallenge</gold> <gray>v" + plugin.getPluginMeta().getVersion() + "</gray>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                if (!sender.hasPermission("ayinchallenge.admin")) return true;
                plugin.getResetService().triggerReset();
                break;
            case "startconfirm":
                if (!sender.hasPermission("ayinchallenge.admin")) return true;
                plugin.getLobbyService().startCountdown();
                break;
            case "stop":
                if (!sender.hasPermission("ayinchallenge.admin")) return true;
                plugin.getRunManager().stop();
                sender.sendMessage(plugin.getComponentUtil().getMessage("run-stopped"));
                break;
            case "reset":
                if (!sender.hasPermission("ayinchallenge.admin")) return true;
                plugin.getResetService().triggerReset();
                break;
            case "status":
                sender.sendMessage(ComponentUtil.parse("<gold>Status: <white>" + plugin.getRunManager().getState() + "</white></gold>"));
                sender.sendMessage(ComponentUtil.parse("<gold>Participants: <white>" + plugin.getRunManager().getParticipants().size() + "</white></gold>"));
                if (plugin.getRunManager().getState() == RunState.RUNNING) {
                    sender.sendMessage(ComponentUtil.parse("<gold>Time: <white>" + plugin.getTimerService().getFormattedTime() + "</white></gold>"));
                    sender.sendMessage(ComponentUtil.parse("<gold>HP: <red>" + String.format("%.1f", plugin.getRunManager().getSharedState().getHealth()) + "</red></gold>"));
                }
                break;
            case "timer":
                sender.sendMessage(ComponentUtil.parse("<gold>Current Time: <white>" + plugin.getTimerService().getFormattedTime() + "</white></gold>"));
                break;
            case "reload":
                if (!sender.hasPermission("ayinchallenge.admin")) return true;
                plugin.reloadConfig();
                sender.sendMessage(ComponentUtil.parse("<green>Config reloaded!</green>"));
                break;
            case "stats":
                handleStatsCommand(sender, args);
                break;
            default:
                sender.sendMessage(ComponentUtil.parse("<red>Unknown subcommand.</red>"));
                break;
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(List.of("start", "stop", "reset", "status", "timer", "reload", "startconfirm", "stats"));
            return subcommands.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            return List.of("runs", "deaths");
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
            sender.sendMessage(ComponentUtil.parse("<yellow>Usage:</yellow> <white>/ayinchallenge stats runs <get|set|add> [value]</white>"));
            sender.sendMessage(ComponentUtil.parse("<yellow>Usage:</yellow> <white>/ayinchallenge stats deaths <get|set|add> <player> [value]</white>"));
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
                    sender.sendMessage(ComponentUtil.parse("<red>Usage: /ayinchallenge stats runs set <value></red>"));
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
                    sender.sendMessage(ComponentUtil.parse("<red>Usage: /ayinchallenge stats runs add <value></red>"));
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
            default -> sender.sendMessage(ComponentUtil.parse("<red>Usage: /ayinchallenge stats runs <get|set|add> [value]</red>"));
        }
    }

    private void handleDeathStats(CommandSender sender, String[] args) {
        String action = args[2].toLowerCase();
        if (args.length < 4) {
            sender.sendMessage(ComponentUtil.parse("<red>Usage: /ayinchallenge stats deaths <get|set|add> <player> [value]</red>"));
            return;
        }

        String targetName = args[3];

        final Integer value;
        if ("set".equalsIgnoreCase(action) || "add".equalsIgnoreCase(action)) {
            if (args.length < 5) {
                sender.sendMessage(ComponentUtil.parse("<red>Usage: /ayinchallenge stats deaths " + action + " <player> <value></red>"));
                return;
            }
            value = parseNonNegativeInt(args[4]);
            if (value == null) {
                sender.sendMessage(ComponentUtil.parse("<red>Value must be a non-negative integer.</red>"));
                return;
            }
        } else if (!"get".equalsIgnoreCase(action)) {
            sender.sendMessage(ComponentUtil.parse("<red>Usage: /ayinchallenge stats deaths <get|set|add> <player> [value]</red>"));
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
