package me.ayinaki.ayinchallenge.listener;

import me.ayinaki.ayinchallenge.AyinChallenge;
import me.ayinaki.ayinchallenge.run.RunState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Handles redirecting players from the real overworld to the fake overworld.
 * Mirrored from Fahare.
 */
public class RedirectionListener implements Listener {
    private final AyinChallenge plugin;

    public RedirectionListener(AyinChallenge plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPortal(EntityPortalEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        World toWorld = to.getWorld();
        if (toWorld == null) return;
        
        // If they are going to the real overworld, send them to the fake one instead.
        if (toWorld.getKey().equals(AyinChallenge.REAL_OVERWORLD_KEY)) {
            to.setWorld(plugin.getFakeOverworld());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        World toWorld = to.getWorld();
        if (toWorld == null) return;

        // If they are going to the real overworld, send them to the fake one instead.
        if (toWorld.getKey().equals(AyinChallenge.REAL_OVERWORLD_KEY)) {
            // If they are returning from the end, send to spawn
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
                event.setTo(plugin.getFakeOverworld().getSpawnLocation());
            } else {
                to.setWorld(plugin.getFakeOverworld());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Redirect if they join in the real overworld
        if (player.getWorld().getKey().equals(AyinChallenge.REAL_OVERWORLD_KEY)) {
            player.teleport(plugin.getFakeOverworld().getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        RunState state = plugin.getRunManager().getState();
        
        // If run is wiped, ensure they respawn at death location in spectator
        if (state == RunState.WIPED) {
            Location deathLoc = player.getLastDeathLocation();
            if (deathLoc != null) {
                event.setRespawnLocation(deathLoc);
                // Fallback: Teleport again in 1 tick to be absolutely sure
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, $ -> {
                    if (player.isOnline()) {
                        player.teleport(deathLoc);
                    }
                }, 1L);
                return;
            }
        }

        Location respawnLoc = event.getRespawnLocation();
        // Redirect if they would respawn in the real overworld
        if (respawnLoc.getWorld().getKey().equals(AyinChallenge.REAL_OVERWORLD_KEY)) {
            event.setRespawnLocation(plugin.getFakeOverworld().getSpawnLocation());
        }
    }
}
