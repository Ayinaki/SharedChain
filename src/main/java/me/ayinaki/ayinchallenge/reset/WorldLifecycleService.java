package me.ayinaki.ayinchallenge.reset;

import me.ayinaki.ayinchallenge.AyinChallenge;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles the lifecycle of Minecraft worlds: unloading, deleting, and backing up.
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

    public CompletableFuture<Void> deleteWorldFolderAsync(@NotNull String worldName) {
        return deleteWorldFolderAsync(getWorldPath(worldName));
    }

    public CompletableFuture<Void> deleteWorldFolderAsync(@NotNull Path worldPath) {
        return CompletableFuture.runAsync(() -> {
            if (!Files.exists(worldPath)) return;
            plugin.getComponentLogger().info("Deleting world folder asynchronously: " + worldPath);
            try {
                deleteDirectory(worldPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete world folder: " + worldPath, e);
            }

            if (Files.exists(worldPath)) {
                plugin.getComponentLogger().error("VERIFICATION FAILED: World folder still exists after deletion attempt: " + worldPath);
                throw new RuntimeException("Failed to delete world folder: " + worldPath);
            } else {
                plugin.getComponentLogger().info("Successfully deleted world folder: " + worldPath);
            }
        });
    }

    /**
     * Moves the world folder into the backup directory instead of deleting it.
     */
    public CompletableFuture<Void> backupWorldFolderAsync(@NotNull String worldName) {
        return backupWorldFolderAsync(getWorldPath(worldName));
    }

    public CompletableFuture<Void> backupWorldFolderAsync(@NotNull Path worldPath) {
        return CompletableFuture.runAsync(() -> {
            if (!Files.exists(worldPath)) return;

            Path backupDir = Bukkit.getWorldContainer().toPath().resolve("ayinchallenge-backups");
            try {
                if (!Files.exists(backupDir)) {
                    Files.createDirectories(backupDir);
                }

                String folderName = worldPath.getFileName() != null ? worldPath.getFileName().toString() : "world";
                Path targetPath = backupDir.resolve(folderName + "_" + System.currentTimeMillis());
                try {
                    Files.move(worldPath, targetPath);
                } catch (IOException e) {
                    // Retry once: Windows can briefly hold file locks right after a world unload
                    Files.move(worldPath, targetPath);
                }
                plugin.getComponentLogger().info("Backed up world folder " + worldPath + " to " + targetPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to back up world folder: " + worldPath, e);
            }
        });
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
                    // Retry once on Windows without Thread.sleep
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
