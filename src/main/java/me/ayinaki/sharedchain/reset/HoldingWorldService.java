package me.ayinaki.sharedchain.reset;

import me.ayinaki.sharedchain.SharedChain;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Manages the holding (limbo) world where players stay during world resets.
 */
public class HoldingWorldService {
    private final SharedChain plugin;

    public HoldingWorldService(SharedChain plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets or creates the holding world.
     * @return The holding world.
     */
    public @NotNull World getOrCreateHoldingWorld() {
        World world = Bukkit.getWorld(plugin.getLimboWorldKey());
        if (world != null) {
            return world;
        }

        WorldCreator creator = new WorldCreator(plugin.getLimboWorldKey())
                .type(WorldType.FLAT)
                .generateStructures(false)
                .generatorSettings("{\"biome\":\"minecraft:the_end\",\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}]}");
        
        return Objects.requireNonNull(creator.createWorld(), "Could not create holding world");
    }
}
