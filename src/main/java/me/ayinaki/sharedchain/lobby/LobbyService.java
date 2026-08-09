package me.ayinaki.sharedchain.lobby;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.run.RunState;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;

/**
 * Manages the pre-run lobby, world border, and countdown.
 */
public class LobbyService {
    private final SharedChain plugin;
    private BukkitTask countdownTask;
    private BukkitTask timeLockTask;

    public LobbyService(SharedChain plugin) {
        this.plugin = plugin;
    }

    public void setupLobby(World world) {
        Location spawn = world.getSpawnLocation();

        enforceLobbyState(world);
        world.setGameRule(org.bukkit.GameRules.NATURAL_HEALTH_REGENERATION, true);

        // Freeze the world clock in the lobby: reset the day counter to day 0 and
        // disable daylight progression so the day count stays put while waiting.
        world.setFullTime(0);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
        startTimeLock(world);

        Component startButton = Component.text("[Start]")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand("/sharedchain start"));

        Component message = Component.text("World ready: ")
                .color(NamedTextColor.YELLOW)
                .append(startButton);

        Bukkit.broadcast(message);
        
        plugin.getComponentLogger().info("Teleporting all players to lobby in world: " + world.getName() + " at " + spawn.getX() + ", " + spawn.getY() + ", " + spawn.getZ());
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(spawn);
            player.sendActionBar(Component.text("Run #" + plugin.getRunManager().getRunCounter(), NamedTextColor.GOLD));
        }

        plugin.getChainService().activateFor(Bukkit.getOnlinePlayers());
        String centerPlayer = plugin.getChainService().getCenterPlayerName();
        if (centerPlayer != null) {
            Bukkit.broadcast(Component.text("The center player for this run is: ", NamedTextColor.YELLOW)
                    .append(Component.text(centerPlayer, NamedTextColor.GOLD, TextDecoration.BOLD)));
        }
        
        plugin.getUIService().refreshAttemptBossBar();
        plugin.getUIService().updateAll();
    }

    /**
     * Applies the lobby world-border (small box centered on spawn with warning zone)
     * and freezes the world clock. Idempotent - safe to call on every lobby join so a
     * client that missed the border update on the way in gets it re-broadcast.
     */
    public void enforceLobbyState(World world) {
        double lobbySize = plugin.getConfig().getDouble("lobby.lobby-border-size", 10.0);
        Location spawn = world.getSpawnLocation();

        world.getWorldBorder().setCenter(spawn);
        world.getWorldBorder().setSize(lobbySize);
        world.getWorldBorder().setWarningDistance(5);
        world.getWorldBorder().setWarningTime(15);
        world.setFullTime(0);
        world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
    }

    private void startTimeLock(World world) {
        stopTimeLock();
        // Lock the full time to 0 every 10 ticks while in lobby/starting phase.
        // Must use setFullTime: World#setTime only shifts the time of day FORWARD to the
        // target, so calling setTime(0) from any other time of day rolls the day counter
        // ahead by one day on every call instead of freezing it.
        timeLockTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            RunState state = plugin.getRunManager().getState();
            if (state == RunState.IDLE || state == RunState.STARTING || state == RunState.RESETTING) {
                world.setFullTime(0);
            } else {
                // Run started (or ended) without going through the countdown - resume daylight.
                world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, true);
                stopTimeLock();
            }
        }, 0L, 10L);
    }

    private void stopTimeLock() {
        if (timeLockTask != null) {
            timeLockTask.cancel();
            timeLockTask = null;
        }
    }

    public void startCountdown() {
        if (countdownTask != null) return;
        
        plugin.getRunManager().setState(RunState.STARTING);
        int seconds = plugin.getConfig().getInt("lobby.countdown-seconds", 5);
        
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    finishCountdown();
                    return;
                }
                
                Component titleText = Component.text(remaining, NamedTextColor.GOLD, TextDecoration.BOLD);
                Title title = Title.title(
                        titleText,
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(100))
                );
                
                Sound tickSound = Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, Sound.Source.MASTER, 1f, 1f);
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.showTitle(title);
                    player.playSound(tickSound);
                }
                
                remaining--;
            }
        }, 0L, 20L);
    }

    private void finishCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        stopTimeLock();

        // Play start sound
        Sound startSound = Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, Sound.Source.MASTER, 1f, 1f);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(startSound);
        }

        // Expand border and resume the day/night cycle now that the run is starting.
        double activeSize = plugin.getConfig().getDouble("lobby.active-border-size", 100000.0);
        World world = plugin.getFakeOverworld();
        if (world != null) {
            world.getWorldBorder().setSize(activeSize);
            world.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, true);
        }

        plugin.getRunManager().start();
        Bukkit.broadcast(plugin.getComponentUtil().getMessage("run-started"));
    }

    public void shutdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        stopTimeLock();
    }
}
