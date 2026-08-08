package me.ayinaki.sharedchain.listener;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.run.RunState;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;

import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class RunListener implements Listener {
    private final SharedChain plugin;

    public RunListener(SharedChain plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getUIService().updateDeathCount(player, plugin.getDeathTrackerService().getDeaths(player));
        plugin.getUIService().refreshAttemptBossBar();

        if (plugin.getRunManager().getState() == RunState.RESETTING) {
            // Send to limbo if resetting (redundant with RedirectionListener but safe)
            World limbo = plugin.getResetService().getHoldingWorldService().getOrCreateHoldingWorld();
            player.teleport(limbo.getSpawnLocation());
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            return;
        }

        if (plugin.getRunManager().isWorldEnabled(player.getWorld())) {
            autoJoin(player);
        }
    }

    private void autoJoin(Player player) {
        if (!plugin.getConfig().getBoolean("run.auto-join", true)) return;
        plugin.getRunManager().addParticipant(player);
        RunState state = plugin.getRunManager().getState();
        if (state == RunState.WIPED) {
            player.setGameMode(GameMode.SPECTATOR);
        }
        if (state == RunState.RUNNING) {
            plugin.getHealthService().syncHealth();
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        RunState state = plugin.getRunManager().getState();
        if (state != RunState.RUNNING && plugin.getRunManager().isWorldEnabled(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        RunState state = plugin.getRunManager().getState();
        if (state != RunState.RUNNING && plugin.getRunManager().isWorldEnabled(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        RunState state = plugin.getRunManager().getState();
        if (state != RunState.RUNNING && plugin.getRunManager().isWorldEnabled(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPVP(EntityDamageByEntityEvent event) {
        RunState state = plugin.getRunManager().getState();
        if (state != RunState.RUNNING
                && event.getEntity() instanceof Player victim
                && plugin.getRunManager().isWorldEnabled(victim.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        RunState state = plugin.getRunManager().getState();
        if (!(event.getEntity() instanceof Player player)) return;

        if (state == RunState.RUNNING && plugin.getRunManager().isParticipant(player)) {
            plugin.getRunStatsService().addDamage(player, event.getFinalDamage());
            return;
        }

        if (state != RunState.RUNNING && plugin.getRunManager().isWorldEnabled(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        RunState state = plugin.getRunManager().getState();
        if (state != RunState.RUNNING
                && event.getEntity() instanceof Player player
                && plugin.getRunManager().isWorldEnabled(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExhaustion(org.bukkit.event.entity.EntityExhaustionEvent event) {
        RunState state = plugin.getRunManager().getState();
        if (state != RunState.RUNNING
                && event.getEntity() instanceof Player player
                && plugin.getRunManager().isWorldEnabled(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getRunManager().removeParticipant(event.getPlayer());
        plugin.getUIService().onPlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getUIService().refreshAttemptBossBar();
        if (plugin.getRunManager().isWorldEnabled(player.getWorld())) {
            player.getWorld().setHardcore(true);
            player.getWorld().setGameRule(org.bukkit.GameRules.IMMEDIATE_RESPAWN, true);
            autoJoin(player);
        } else {
            plugin.getRunManager().removeParticipant(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostRespawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin.getRunManager().getState() == RunState.RUNNING && plugin.getRunManager().isParticipant(player)) {
            // Re-sync after respawn
            plugin.getHealthService().syncHealth();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChainAnchorDamage(EntityDamageEvent event) {
        if (plugin.getChainService().isChainAnchor(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChainAnchorInteract(PlayerInteractEntityEvent event) {
        if (plugin.getChainService().isChainAnchor(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChainAnchorUnleash(EntityUnleashEvent event) {
        if (plugin.getChainService().isChainAnchor(event.getEntity())) {
            event.setDropLeash(false);
            event.setCancelled(true);
        }
    }
}
