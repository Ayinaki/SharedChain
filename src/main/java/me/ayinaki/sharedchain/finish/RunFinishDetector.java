package me.ayinaki.sharedchain.finish;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.run.RunState;
import me.ayinaki.sharedchain.util.ComponentUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class RunFinishDetector implements Listener {
    private final SharedChain plugin;
    private boolean dragonKilled = false;

    public RunFinishDetector(SharedChain plugin) {
        this.plugin = plugin;
    }

    public void reset() {
        this.dragonKilled = false;
    }

    public boolean isDragonKilled() {
        return dragonKilled;
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (plugin.getRunManager().getState() != RunState.RUNNING) return;
        if (event.getEntityType() == EntityType.ENDER_DRAGON) {
            dragonKilled = true;
            plugin.getComponentLogger().info("Dragon death detected. Finishing run in 10 seconds...");
            
            // Wait 10 seconds for the death animation/portal opening
            Bukkit.getScheduler().runTaskLater(plugin, this::finishRun, 10 * 20L);
        }
    }

    public void finishRun() {
        // Double check state to avoid double-finish
        if (plugin.getRunManager().getState() != RunState.RUNNING) return;
        
        plugin.getRunManager().finish();
        dragonKilled = false;

        String time = plugin.getTimerService().getFormattedTime();
        String messageStr = plugin.getConfig().getString("messages.run-finished", "<gold><b>Congratulations!</b> The challenge has been completed in <white><timer></white>!</gold>");
        var msg = ComponentUtil.parse(messageStr, Placeholder.parsed("timer", time));
        Bukkit.broadcast(msg);
        
        // The timer task keeps ticking in FINISHED state so the final time stays on the action bar.
        plugin.getComponentLogger().info("Run officially finished.");
    }
}
