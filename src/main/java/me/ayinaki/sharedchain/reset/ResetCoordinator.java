package me.ayinaki.sharedchain.reset;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.run.RunState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrates the world reset process mirroring Fahare's implementation.
 */
public class ResetCoordinator {
    private final SharedChain plugin;
    private final Random random = new Random();
    private boolean resetting = false;

    public ResetCoordinator(SharedChain plugin) {
        this.plugin = plugin;
    }

    public synchronized void initiateReset() {
        if (resetting) return;

        World limboWorld = plugin.getResetService().getHoldingWorldService().getOrCreateHoldingWorld();
        if (limboWorld == null) {
            plugin.getComponentLogger().error("Reset aborted: Limbo world could not be found or created.");
            return;
        }

        // Deactivate active run services
        plugin.getChainService().deactivate();
        plugin.getRunManager().incrementResetId();
        plugin.getRunManager().setState(RunState.RESETTING);

        // Move all players to limbo in spectator mode
        Location destination = new Location(limboWorld, 0.5, 100, 0.5);
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            plugin.getRunManager().resetPlayer(player);
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(destination);
        }

        // Clear plugin boss bars from all players
        plugin.getUIService().shutdown();

        // Wait for worlds to stop ticking if necessary
        if (Bukkit.isTickingWorlds()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, $ -> initiateReset(), 1L);
            return;
        }

        resetting = true;
        plugin.getComponentLogger().info("Resetting the world...");

        List<World> worldsToReset = Bukkit.getWorlds().stream()
                .filter(w -> !w.getKey().equals(plugin.getLimboWorldKey()))
                .filter(w -> !w.getKey().equals(SharedChain.REAL_OVERWORLD_KEY))
                .collect(Collectors.toList());

        long sharedSeed = random.nextLong();
        plugin.getComponentLogger().info("Generated shared reset seed: " + sharedSeed);

        processNextWorld(worldsToReset, sharedSeed);
    }

    private void processNextWorld(List<World> worlds, long seed) {
        if (worlds.isEmpty()) {
            finalizeReset();
            return;
        }

        // Safety check for ticking worlds
        if (Bukkit.isTickingWorlds()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, $ -> processNextWorld(worlds, seed), 1L);
            return;
        }

        World world = worlds.remove(0);
        String name = world.getName();
        org.bukkit.NamespacedKey key = world.getKey();
        Path worldPath = world.getWorldFolder().toPath().toAbsolutePath().normalize();

        // Capture settings to restore them after re-creation
        int viewDistance = world.getViewDistance();
        int simulationDistance = world.getSimulationDistance();

        // Explicitly clear ender dragon boss bar if it exists
        if (world.getEnvironment() == World.Environment.THE_END) {
            org.bukkit.boss.DragonBattle battle = world.getEnderDragonBattle();
            if (battle != null && battle.getBossBar() != null) {
                battle.getBossBar().removeAll();
            }
        }

        plugin.getComponentLogger().info("Resetting world: " + name + " (Key: " + key + ")");

        // Unload world (must be on main thread)
        WorldLifecycleService lifecycle = plugin.getResetService().getWorldLifecycleService();
        if (lifecycle.unloadWorld(world, false)) {
            // Offload disk work (backup or deletion) to an async thread
            boolean backup = plugin.getConfig().getBoolean("world-reset.backup-before-reset", false);
            CompletableFuture<Void> diskOp = backup
                    ? lifecycle.backupWorldFolderAsync(worldPath)
                    : lifecycle.deleteWorldFolderAsync(worldPath);
            diskOp.whenCompleteAsync((v, throwable) -> {
                    // Resume world recreation on the Paper GlobalRegionScheduler (main thread)
                    Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                        if (throwable != null) {
                            plugin.getComponentLogger().error("Error processing world folder during reset: " + name, throwable);
                            processNextWorld(worlds, seed);
                            return;
                        }

                        // Re-create world (must be on main thread)
                        WorldCreator creator;
                        if (key.equals(plugin.getFakeOverworldKey())) {
                            creator = new WorldCreator(key);
                        } else {
                            creator = new WorldCreator(name);
                        }
                        creator.copy(world); // Mimic Fahare: copy settings from original world
                        creator.seed(seed);

                        World newWorld = creator.createWorld();
                        if (newWorld == null) {
                            plugin.getComponentLogger().error("Failed to re-create world: " + name);
                        } else {
                            plugin.getComponentLogger().info("Successfully re-created world: " + name);
                            // Restore distances
                            newWorld.setViewDistance(viewDistance);
                            newWorld.setSimulationDistance(simulationDistance);
                        }

                        // Process next world
                        processNextWorld(worlds, seed);
                    });
                });
        } else {
            plugin.getComponentLogger().error("Failed to unload world: " + name);
            processNextWorld(worlds, seed); // Skip and continue
        }
    }

    private void finalizeReset() {
        resetting = false;
        World challengeWorld = plugin.getFakeOverworld();
        plugin.getRunManager().onWorldResetComplete(challengeWorld);

        Location spawn = challengeWorld.getSpawnLocation();
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(spawn);
        }

        plugin.getComponentLogger().info("Reset complete!");
    }

    public boolean isResetting() {
        return resetting;
    }
}
