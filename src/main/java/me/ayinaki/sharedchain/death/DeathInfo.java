package me.ayinaki.sharedchain.death;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;

/**
 * Record of a run-ending death.
 */
public record DeathInfo(
        Player deadPlayer,
        Entity killer,
        EntityDamageEvent.DamageCause cause,
        Instant timestamp,
        Location location,
        Component formattedDescription
) {}
