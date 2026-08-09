package me.ayinaki.sharedchain.listener;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.health.SharedHealthService;
import me.ayinaki.sharedchain.run.RunState;
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
    private final SharedChain plugin;
    private static final DecimalFormat HEART_FORMAT = new DecimalFormat("0.##");

    public SharedHealthListener(SharedChain plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.getRunManager().getState() != RunState.RUNNING) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getRunManager().isParticipant(player)) return;
        // Lowering health via the API (e.g. our own pool sync pushes) fires a
        // CUSTOM damage event - counting it would double-subtract from the pool.
        if (event.getCause() == EntityDamageEvent.DamageCause.CUSTOM) return;
        if (event.getFinalDamage() <= 0) return;

        // Keep vanilla damage pipeline fully intact (sounds, knockback, I-frames),
        // but apply the pool delta NOW: at MONITOR the damage hasn't been applied
        // yet, so health/absorption are the pre-hit values. Applying the delta at
        // event time (instead of snapshotting the victim's health next tick) means
        // two players hit in the same tick each subtract their own loss from the
        // pool - a snapshot model loses the second hit's damage entirely.
        double healthLoss = SharedHealthService.computeHealthLoss(
                player.getHealth(), player.getAbsorptionAmount(), event.getFinalDamage());
        double applied = plugin.getHealthService().applyDamageToPool(healthLoss);
        if (applied <= 0.0) return;

        // Mirror the updated pool to the whole team on the next tick, once vanilla
        // has finished applying this hit to the victim.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.getRunManager().getState() == RunState.RUNNING) {
                plugin.getHealthService().syncHealth();
            }
        });

        double hearts = applied / 2.0;
        Bukkit.broadcast(
                Component.text("[A] ", NamedTextColor.GOLD)
                        .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                        .append(Component.text(" has taken ", NamedTextColor.GRAY))
                        .append(Component.text(HEART_FORMAT.format(hearts), NamedTextColor.RED))
                        .append(Component.text(" ❤ damage.", NamedTextColor.RED))
        );
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
        // Raising health via the API (e.g. our own pool sync pushes) fires a
        // CUSTOM regain event - the pool is already updated, counting it again
        // would double-heal the team.
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.CUSTOM) return;

        // At MONITOR the heal hasn't been applied yet, so health is the pre-heal
        // value. Apply the delta immediately (see onDamage); only the amount that
        // actually fits below max health counts towards the shared pool.
        double gain = SharedHealthService.computeHealthGain(
                player.getHealth(), plugin.getRunManager().getSharedState().getMaxHealth(), event.getAmount());
        if (gain <= 0.0) return;

        plugin.getHealthService().applyHealToPool(gain);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.getRunManager().getState() == RunState.RUNNING) {
                plugin.getHealthService().syncHealth();
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
