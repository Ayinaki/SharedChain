package me.ayinaki.ayinchallenge.lobby;

import me.ayinaki.ayinchallenge.AyinChallenge;
import me.ayinaki.ayinchallenge.run.RunState;
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
    private final AyinChallenge plugin;
    private BukkitTask countdownTask;
    private BukkitTask timeLockTask;

    public LobbyService(AyinChallenge plugin) {
        this.plugin = plugin;
    }

    public void setupLobby(World world) {
        double lobbySize = plugin.getConfig().getDouble("lobby.lobby-border-size", 10.0);
        Location spawn = world.getSpawnLocation();
        
        world.getWorldBorder().setCenter(spawn);
        world.getWorldBorder().setSize(lobbySize);
        world.setGameRule(org.bukkit.GameRules.NATURAL_HEALTH_REGENERATION, true);

        // Reset time and start the manual lock since gamerules are unavailable
        world.setTime(0);
        startTimeLock(world);

        Component startButton = Component.text("[Start]")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand("/ayinchallenge startconfirm"));

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

    private void startTimeLock(World world) {
        stopTimeLock();
        // Lock time to 0 every 10 ticks while in lobby/starting phase
        timeLockTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            RunState state = plugin.getRunManager().getState();
            if (state == RunState.IDLE || state == RunState.STARTING || state == RunState.RESETTING) {
                world.setTime(0);
            } else {
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

        // Expand border
        double activeSize = plugin.getConfig().getDouble("lobby.active-border-size", 100000.0);
        World world = plugin.getFakeOverworld();
        if (world != null) {
            world.getWorldBorder().setSize(activeSize); 
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
