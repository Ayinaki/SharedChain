package me.ayinaki.sharedchain.listener;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.death.DeathInfo;
import me.ayinaki.sharedchain.run.RunState;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.time.Instant;

public class DeathListener implements Listener {
    private final SharedChain plugin;

    public DeathListener(SharedChain plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.getRunManager().getState() != RunState.RUNNING) return;
        Player deadPlayer = event.getEntity();
        if (!plugin.getRunManager().isParticipant(deadPlayer)) return;

        Entity killer = deadPlayer.getKiller();
        if (killer == null && deadPlayer.getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity) {
            killer = byEntity.getDamager();
        }

        // Record death info
        DeathInfo info = new DeathInfo(
            deadPlayer,
            killer,
            deadPlayer.getLastDamageCause() != null ? deadPlayer.getLastDamageCause().getCause() : null,
            Instant.now(),
            deadPlayer.getLocation(),
            buildCauseDescription(killer, deadPlayer.getLastDamageCause() != null ? deadPlayer.getLastDamageCause().getCause() : null)
        );

        // Hide vanilla death message
        event.deathMessage(null);

        plugin.handleRunWipe(info);
    }

    private Component buildCauseDescription(Entity killer, org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        if (killer instanceof Player killerPlayer) {
            return Component.text("an attack by " + killerPlayer.getName());
        }
        if (killer != null) {
            return Component.text("an attack by " + killer.getType().name().toLowerCase().replace('_', ' '));
        }
        if (cause != null) {
            return Component.text(cause.name().toLowerCase().replace('_', ' '));
        }
        return Component.text("unknown causes");
    }
}
