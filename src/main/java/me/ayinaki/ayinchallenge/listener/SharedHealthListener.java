package me.ayinaki.ayinchallenge.listener;

import me.ayinaki.ayinchallenge.AyinChallenge;
import me.ayinaki.ayinchallenge.run.RunState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;

import java.text.DecimalFormat;

public class SharedHealthListener implements Listener {
    private final AyinChallenge plugin;
    private static final DecimalFormat HEART_FORMAT = new DecimalFormat("0.##");

    public SharedHealthListener(AyinChallenge plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.getRunManager().getState() != RunState.RUNNING) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getRunManager().isParticipant(player)) return;
        if (event.getFinalDamage() <= 0) return;

        // Keep vanilla damage pipeline fully intact (sounds, knockback, I-frames),
        // then mirror resulting health to the team on next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.getRunManager().getState() != RunState.RUNNING) return;
            if (!player.isOnline() || !plugin.getRunManager().isParticipant(player)) return;

            double actualDamage = plugin.getHealthService().syncFromPlayerHealth(player);
            if (actualDamage <= 0.0) return;

            double hearts = actualDamage / 2.0;
            Bukkit.broadcast(
                    Component.text("[A] ", NamedTextColor.GOLD)
                            .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                            .append(Component.text(" has taken ", NamedTextColor.GRAY))
                            .append(Component.text(HEART_FORMAT.format(hearts), NamedTextColor.RED))
                            .append(Component.text(" ❤ damage.", NamedTextColor.RED))
            );

        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (plugin.getRunManager().getState() != RunState.RUNNING) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getRunManager().isParticipant(player)) return;

        // Natural regeneration is now handled by SharedHealthService's heartbeat
        // and is disabled via gamerule to prevent exhaustion leaks.
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
            return;
        }

        // For other sources (Golden Apples, Potions, etc.), sync from the player.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.getRunManager().getState() == RunState.RUNNING) {
                plugin.getHealthService().syncFromPlayerHealth(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent event) {
        if (plugin.getRunManager().getState() != RunState.RUNNING) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getRunManager().isParticipant(player)) return;
        if (!plugin.getConfig().getBoolean("shared-health.totem-save-all", true)) return;

        Bukkit.getScheduler().runTask(plugin, () -> plugin.getHealthService().syncFromPlayerHealth(player));
    }
}
