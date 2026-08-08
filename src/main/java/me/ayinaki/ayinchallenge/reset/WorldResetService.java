package me.ayinaki.ayinchallenge.reset;

import me.ayinaki.ayinchallenge.AyinChallenge;
import me.ayinaki.ayinchallenge.run.RunState;
import me.ayinaki.ayinchallenge.util.ComponentUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public class WorldResetService {
    private final AyinChallenge plugin;
    private final HoldingWorldService holdingWorldService;
    private final WorldLifecycleService worldLifecycleService;
    private final ResetCoordinator resetCoordinator;

    public WorldResetService(AyinChallenge plugin) {
        this.plugin = plugin;
        this.holdingWorldService = new HoldingWorldService(plugin);
        this.worldLifecycleService = new WorldLifecycleService(plugin);
        this.resetCoordinator = new ResetCoordinator(plugin);
    }

    public void triggerReset() {
        String mode = plugin.getConfig().getString("world-reset.mode", "INTERNAL");
        plugin.getComponentLogger().info("World reset requested. Configured mode: " + mode);
        
        switch (mode.toUpperCase()) {
            case "FAHARE_COMPAT":
                String command = plugin.getConfig().getString("world-reset.fahare-reset-command", "fahare reset");
                plugin.getComponentLogger().warn("Using FAHARE_COMPAT mode. Seed generation is controlled by the external Fahare command.");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                break;
            case "INTERNAL":
                resetCoordinator.initiateReset();
                break;
            default:
                plugin.getComponentLogger().info("Reset mode is NONE or invalid, skipping automatic reset.");
                break;
        }
    }

    public HoldingWorldService getHoldingWorldService() {
        return holdingWorldService;
    }

    public WorldLifecycleService getWorldLifecycleService() {
        return worldLifecycleService;
    }

    public ResetCoordinator getResetCoordinator() {
        return resetCoordinator;
    }
}
