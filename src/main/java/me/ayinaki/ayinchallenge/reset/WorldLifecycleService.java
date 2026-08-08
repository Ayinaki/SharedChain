package me.ayinaki.ayinchallenge.reset;

import me.ayinaki.ayinchallenge.AyinChallenge;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles the lifecycle of Minecraft worlds: unloading, deleting, and creating.
 */
public class WorldLifecycleService {
    private final AyinChallenge plugin;

    public WorldLifecycleService(AyinChallenge plugin) {
        this.plugin = plugin;
    }

    public boolean unloadWorld(@NotNull World world, boolean save) {
        // Detailed logging before unload
        List<String> remainingPlayers = world.getPlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());

        plugin.getComponentLogger().info("Attempting to unload world: " + world.getName());
        plugin.getComponentLogger().info("Players still in world: " + remainingPlayers);
        plugin.getComponentLogger().info("Entity count: " + world.getEntityCount());

        boolean success = Bukkit.unloadWorld(world, save);

        plugin.getComponentLogger().info("Unload result for " + world.getName() + ": " + success);

        if (!success) {
            // Log reasons why it might have failed
            if (!remainingPlayers.isEmpty()) {
                plugin.getComponentLogger().warn("FAILED: Remaining players: " + remainingPlayers);
            }
            if (Bukkit.getWorlds().get(0).getName().equalsIgnoreCase(world.getName())) {
                plugin.getComponentLogger().warn("FAILED: Target world is the primary server world!");
            }
            plugin.getComponentLogger().warn("FAILED: Remaining entities count: " + world.getEntities().size());
        }

        return success;
    }

    public void clearEntities(@NotNull World world) {
        int count = 0;
        for (var entity : world.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
                count++;
            }
        }
        plugin.getComponentLogger().info("Cleared " + count + " non-player entities from world: " + world.getName());
    }

    public void deleteWorldFolder(@NotNull String worldName) throws IOException {
        Path worldPath = getWorldPath(worldName);
        deleteWorldFolder(worldPath);
    }

    public void deleteWorldFolder(@NotNull Path worldPath) throws IOException {
        if (Files.exists(worldPath)) {
            plugin.getComponentLogger().info("Deleting world folder: " + worldPath);
            deleteDirectory(worldPath);

            // Verification check
            if (Files.exists(worldPath)) {
                plugin.getComponentLogger().error("VERIFICATION FAILED: World folder still exists after deletion attempt: " + worldPath);
                throw new IOException("Failed to delete world folder: " + worldPath);
            } else {
                plugin.getComponentLogger().info("Successfully deleted world folder: " + worldPath);
            }
        }
    }

    public CompletableFuture<Void> deleteWorldFolderAsync(@NotNull String worldName) {
        Path worldPath = getWorldPath(worldName);
        return deleteWorldFolderAsync(worldPath);
    }

    public CompletableFuture<Void> deleteWorldFolderAsync(@NotNull Path worldPath) {
        return CompletableFuture.runAsync(() -> {
            if (Files.exists(worldPath)) {
                plugin.getComponentLogger().info("Deleting world folder asynchronously: " + worldPath);
                try {
                    deleteDirectory(worldPath);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete world folder: " + worldPath, e);
                }

                // Verification check
                if (Files.exists(worldPath)) {
                    plugin.getComponentLogger().error("VERIFICATION FAILED: World folder still exists after deletion attempt: " + worldPath);
                    throw new RuntimeException("Failed to delete world folder: " + worldPath);
                } else {
                    plugin.getComponentLogger().info("Successfully deleted world folder: " + worldPath);
                }
            }
        });
    }

    public void backupWorldFolder(@NotNull String worldName) throws IOException {
        Path worldPath = getWorldPath(worldName);
        backupWorldFolder(worldPath);
    }

    public void backupWorldFolder(@NotNull Path worldPath) throws IOException {
        if (!Files.exists(worldPath)) return;

        Path backupDir = Bukkit.getWorldContainer().toPath().resolve("ayinchallenge-backups");
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
        }

        String worldFolderName = worldPath.getFileName() != null ? worldPath.getFileName().toString() : "world";
        String backupName = worldFolderName + "_" + System.currentTimeMillis();
        Path targetPath = backupDir.resolve(backupName);

        try {
            Files.move(worldPath, targetPath);
        } catch (IOException e) {
            plugin.getComponentLogger().warn("Failed to backup world folder: " + worldPath + " - " + e.getMessage());
            // Retry once on Windows without Thread.sleep
            try {
                Files.move(worldPath, targetPath);
            } catch (Exception ignored) {
                throw e;
            }
        }

        if (Files.exists(worldPath)) {
            throw new IOException("Backup move completed but source world folder still exists: " + worldPath);
        }
        plugin.getComponentLogger().info("Backed up world folder from " + worldPath + " to " + targetPath);
    }

    public @Nullable World createNewWorld(@NotNull String worldName, long seed) {
        WorldCreator creator = new WorldCreator(worldName);
        creator.seed(seed);

        // Handle dimensions based on name suffix
        if (worldName.endsWith("_nether")) {
            creator.environment(World.Environment.NETHER);
        } else if (worldName.endsWith("_the_end")) {
            creator.environment(World.Environment.THE_END);
        }

        World world = creator.createWorld();
        if (world != null) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                world.setTime(0L);
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(0);
                world.setClearWeatherDuration(Integer.MAX_VALUE);
            }
            plugin.getComponentLogger().info("World '" + worldName + "' created/loaded. Actual seed: " + world.getSeed() + " Environment: " + world.getEnvironment());
        }
        return world;
    }

    public void ensureWorldFolderAbsent(@NotNull String worldName) throws IOException {
        ensureWorldFolderAbsent(getWorldPath(worldName));
    }

    public void ensureWorldFolderAbsent(@NotNull Path worldPath) throws IOException {
        if (Files.exists(worldPath)) {
            plugin.getComponentLogger().warn("World folder still exists before re-create, deleting leftover data: " + worldPath);
            deleteDirectory(worldPath);
            if (Files.exists(worldPath)) {
                throw new IOException("World folder still exists after forced deletion: " + worldPath);
            }
        }
    }

    public void logWorldFolderState(@NotNull String worldName, @NotNull String phase) {
        Path worldPath = getWorldPath(worldName);
        logWorldFolderState(worldPath, worldName, phase);
    }

    public void logWorldFolderState(@NotNull Path worldPath, @NotNull String worldName, @NotNull String phase) {
        Path levelDat = worldPath.resolve("level.dat");
        long regionCount = countRegionFiles(worldPath);
        plugin.getComponentLogger().info(
                "[" + phase + "] world='" + worldName
                        + "' path=" + worldPath
                        + " exists=" + Files.exists(worldPath)
                        + " level.dat=" + Files.exists(levelDat)
                        + " regionFiles=" + regionCount
        );
    }

    private long countRegionFiles(@NotNull Path worldPath) {
        Path regionPath = worldPath.resolve("region");
        if (!Files.exists(regionPath) || !Files.isDirectory(regionPath)) {
            return 0L;
        }
        try (Stream<Path> stream = Files.list(regionPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Objects::toString)
                    .filter(name -> name.endsWith(".mca"))
                    .count();
        } catch (IOException e) {
            plugin.getComponentLogger().warn("Failed to count region files in " + regionPath + ": " + e.getMessage());
            return -1L;
        }
    }

    private @NotNull Path getWorldPath(@NotNull String worldName) {
        World loadedWorld = Bukkit.getWorld(worldName);
        if (loadedWorld != null) {
            return loadedWorld.getWorldFolder().toPath().toAbsolutePath().normalize();
        }
        return Bukkit.getWorldContainer().toPath().resolve(worldName).toAbsolutePath().normalize();
    }

    private void deleteDirectory(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            for (Path p : paths) {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    plugin.getComponentLogger().warn("Failed to delete file: " + p + " - " + e.getMessage());
                    // Retry on Windows without Thread.sleep
                    try {
                        Files.delete(p);
                    } catch (Exception ignored) {
                        throw e; // Re-throw if retry fails
                    }
                }
            }
        }
    }
}
