package me.ayinaki.ayinchallenge.health;

import me.ayinaki.ayinchallenge.AyinChallenge;
import me.ayinaki.ayinchallenge.run.RunState;
import me.ayinaki.ayinchallenge.run.SharedState;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class SharedHealthService {
    private final AyinChallenge plugin;
    private boolean syncing = false;
    private BukkitTask heartbeatTask;
    private long slowRegenTickCounter = 0;
    private java.util.UUID lastSponsorUuid;
    private int sponsorLingerTicks = 0;

    public SharedHealthService(AyinChallenge plugin) {
        this.plugin = plugin;
        startHeartbeat();
    }

    private void startHeartbeat() {
        // Run every 10 ticks (0.5 seconds), matching Vanilla Fast Regen frequency.
        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRegeneration, 10L, 10L);
    }

    public void shutdown() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
    }

    private void tickRegeneration() {
        if (plugin.getRunManager().getState() != RunState.RUNNING) {
            plugin.getUIService().clearSponsor();
            sponsorLingerTicks = 0;
            return;
        }

        SharedState state = plugin.getRunManager().getSharedState();
        boolean teamHurt = state.getHealth() < state.getMaxHealth();

        // Handle lingering indicator even if team isn't hurt anymore or nobody is regening
        if (sponsorLingerTicks > 0) {
            sponsorLingerTicks -= 10;
            if (sponsorLingerTicks <= 0) {
                plugin.getUIService().clearSponsor();
                lastSponsorUuid = null;
            } else if (lastSponsorUuid != null) {
                Player p = Bukkit.getPlayer(lastSponsorUuid);
                if (p == null || !p.isOnline() || p.isDead()) {
                    plugin.getUIService().clearSponsor();
                    sponsorLingerTicks = 0;
                    lastSponsorUuid = null;
                }
            }
        }

        if (!teamHurt) return;

        slowRegenTickCounter += 10;

        List<Player> participants = new ArrayList<>();
        for (UUID uuid : plugin.getRunManager().getParticipants()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && !p.isDead()) {
                participants.add(p);
            }
        }

        if (participants.isEmpty()) {
            plugin.getUIService().clearSponsor();
            return;
        }

        // 1. Fast Regen Check (Hunger 20 + Saturation > 0)
        Player fastSponsor = participants.stream()
                .filter(p -> p.getFoodLevel() >= 20 && p.getSaturation() > 0)
                .max(Comparator.comparingDouble(Player::getSaturation))
                .orElse(null);

        if (fastSponsor != null) {
            applyRegen(fastSponsor, 1.0);
            updateSponsorVisual(fastSponsor);
            return;
        }

        // 2. Slow Regen Check (Hunger >= 18, triggers every 80 ticks / 4 seconds)
        if (slowRegenTickCounter >= 80) {
            slowRegenTickCounter = 0;
            Player slowSponsor = participants.stream()
                    .filter(p -> p.getFoodLevel() >= 18)
                    .max(Comparator.comparingDouble(p -> p.getFoodLevel() + p.getSaturation()))
                    .orElse(null);

            if (slowSponsor != null) {
                applyRegen(slowSponsor, 1.0);
                updateSponsorVisual(slowSponsor);
            }
        }
    }

    private void updateSponsorVisual(Player sponsor) {
        lastSponsorUuid = sponsor.getUniqueId();
        sponsorLingerTicks = 40; // Linger for 2 seconds (40 ticks)
        plugin.getUIService().setSponsor(sponsor);
    }

    private void applyRegen(Player sponsor, double amount) {
        // Vanilla cost: 6.0 exhaustion points per 1.0 HP (half-heart)
        sponsor.setExhaustion(Math.min(40.0f, sponsor.getExhaustion() + 6.0f));
        
        // Track stats
        plugin.getRunStatsService().addHeal(sponsor, amount);

        SharedState state = plugin.getRunManager().getSharedState();
        state.addHealth(amount);
        syncHealth();
    }

    public synchronized void syncHealth() {
        if (plugin.getRunManager().getState() != RunState.RUNNING) return;
        if (syncing) return;

        syncing = true;
        try {
            SharedState state = plugin.getRunManager().getSharedState();
            double sharedHealth = state.getHealth();
            double maxHealth = state.getMaxHealth();
            double precision = plugin.getConfig().getDouble("shared-health.precision", 0.001);

            for (UUID uuid : plugin.getRunManager().getParticipants()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;

                var attr = player.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null && Math.abs(attr.getBaseValue() - maxHealth) > precision) {
                    attr.setBaseValue(maxHealth);
                }

                double clampedHealth = Math.max(0, Math.min(maxHealth, sharedHealth));
                if (Math.abs(player.getHealth() - clampedHealth) > precision) {
                    player.setHealth(clampedHealth);
                }
            }
        } finally {
            syncing = false;
        }
    }

    public synchronized double syncFromPlayerHealth(Player source) {
        if (source == null || !source.isOnline()) return 0.0;
        if (plugin.getRunManager().getState() != RunState.RUNNING) return 0.0;
        if (!plugin.getRunManager().isParticipant(source)) return 0.0;
        if (syncing) return 0.0;

        double sourceHealth = source.getHealth();
        if (sourceHealth <= 0.0) return 0.0;

        SharedState state = plugin.getRunManager().getSharedState();
        double previous = state.getHealth();
        state.setHealth(sourceHealth);
        syncHealth();
        return Math.max(0.0, previous - state.getHealth());
    }

    public void clearDamageFrames() {
        slowRegenTickCounter = 0;
    }
}
