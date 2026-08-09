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
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
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

            // Rebuild chains after a restart (or when joining mid-lobby / mid-run) and
            // return lobby players to spawn so nobody is stranded outside the border.
            RunState state = plugin.getRunManager().getState();
            if (state == RunState.IDLE || state == RunState.STARTING || state == RunState.RUNNING) {
                plugin.getChainService().addPlayer(player);
                if (state == RunState.IDLE || state == RunState.STARTING) {
                    // The lobby invariant: no items, survival, fresh state. If the server
                    // crashed or was killed while in the lobby, the on-disk player data can
                    // be stale (pre-reset inventory/gamemode), so re-apply the reset state
                    // on every lobby join. Also re-assert the lobby border so a client that
                    // missed the border update on the way in gets it re-broadcast.
                    plugin.getRunManager().resetPlayer(player);
                    plugin.getLobbyService().enforceLobbyState(plugin.getFakeOverworld());
                    player.teleport(plugin.getFakeOverworld().getSpawnLocation());
                }
            }
            plugin.getRunManager().updateTimerPause();
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
            // Our own pool sync pushes lower every participant via the API, which
            // fires a CUSTOM damage event per player. Counting those would inflate
            // the damage stats by the whole team's damage on every single hit.
            if (event.getCause() == EntityDamageEvent.DamageCause.CUSTOM) return;
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
        // Re-evaluate the timer pause a tick later, once the player is fully offline.
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getRunManager().updateTimerPause());
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
        plugin.getRunManager().updateTimerPause();
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

    /**
     * Safety net for stray chain-lead drops: leash breaks that slip past the unleash
     * handler spawn a Lead item at the chain anchor. Cancel those so the team never
     * collects the chain's visual leads.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLeadItemSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getItemStack().getType() != org.bukkit.Material.LEAD) return;

        org.bukkit.Location loc = event.getLocation();
        for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 3, 3, 3)) {
            if (plugin.getChainService().isChainAnchor(nearby)) {
                event.setCancelled(true);
                plugin.getComponentLogger().info("Cancelled stray chain-lead drop at "
                        + loc.getWorld().getName() + " "
                        + String.format("%.1f,%.1f,%.1f", loc.getX(), loc.getY(), loc.getZ()));
                return;
            }
        }

        // DIAGNOSTIC: a lead spawning away from any anchor is unexpected - worth logging.
        plugin.getComponentLogger().info("DEBUG lead spawned (not near a chain anchor) at "
                + loc.getWorld().getName() + " "
                + String.format("%.1f,%.1f,%.1f", loc.getX(), loc.getY(), loc.getZ()));
    }
}
