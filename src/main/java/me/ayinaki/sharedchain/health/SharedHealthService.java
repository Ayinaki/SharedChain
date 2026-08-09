package me.ayinaki.sharedchain.health;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.run.RunState;
import me.ayinaki.sharedchain.run.SharedState;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class SharedHealthService {
    private final SharedChain plugin;
    private boolean syncing = false;
    private BukkitTask heartbeatTask;
    private long slowRegenTickCounter = 0;
    private int healthSaveTickCounter = 0;
    private int lastActiveTickCounter = 0;
    private java.util.UUID lastSponsorUuid;
    private int sponsorLingerTicks = 0;

    public SharedHealthService(SharedChain plugin) {
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

        // Periodically flush the shared health pool to stats.yml so a crash or hard
        // kill mid-run doesn't reset the team to full health on the next boot.
        healthSaveTickCounter += 10;
        if (healthSaveTickCounter >= 50) { // every 2.5 seconds
            healthSaveTickCounter = 0;
            if (plugin.getRunManager().consumeHealthDirty()) {
                plugin.getRunManager().persistRunState();
            }
        }

        // Periodically stamp last-active so a hard kill mid-run doesn't inflate the
        // run timer with server downtime on the next boot.
        lastActiveTickCounter += 10;
        if (lastActiveTickCounter >= 100) { // every 5 seconds
            lastActiveTickCounter = 0;
            plugin.getRunManager().persistRunState();
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
        plugin.getRunManager().markHealthDirty();
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

    /**
     * Applies a damage delta to the shared pool immediately and schedules nothing.
     * The caller is responsible for triggering {@link #syncHealth()} (usually on the
     * next tick). Returns the amount actually removed from the pool (clamped at 0).
     * <p>
     * Pool updates are <em>deltas</em>, not snapshots: when two participants take
     * damage (or heal) in the same tick, each event subtracts its own loss from the
     * pool, so the team can never silently regain (or lose) health that a snapshot-
     * from-a-single-player model would have dropped.
     */
    public synchronized double applyDamageToPool(double amount) {
        if (amount <= 0) return 0.0;
        if (plugin.getRunManager().getState() != RunState.RUNNING) return 0.0;

        SharedState state = plugin.getRunManager().getSharedState();
        double previous = state.getHealth();
        state.addHealth(-amount);
        plugin.getRunManager().markHealthDirty();
        return previous - state.getHealth();
    }

    /**
     * Applies a healing delta to the shared pool immediately (clamped at max
     * health). Returns the amount actually added to the pool.
     */
    public synchronized double applyHealToPool(double amount) {
        if (amount <= 0) return 0.0;
        if (plugin.getRunManager().getState() != RunState.RUNNING) return 0.0;

        SharedState state = plugin.getRunManager().getSharedState();
        double previous = state.getHealth();
        state.addHealth(amount);
        plugin.getRunManager().markHealthDirty();
        return state.getHealth() - previous;
    }

    /**
     * The amount of health a hit will actually remove, given the victim's health
     * and absorption <em>before</em> the damage is applied. Armor/enchantments are
     * already factored into {@code finalDamage}; absorption is consumed first and
     * overkill beyond the victim's remaining health is capped.
     */
    public static double computeHealthLoss(double healthBefore, double absorption, double finalDamage) {
        if (finalDamage <= 0) return 0.0;
        double absorbed = Math.min(absorption, finalDamage);
        return Math.max(0.0, Math.min(healthBefore, finalDamage - absorbed));
    }

    /**
     * The amount of health a heal will actually add, given the healer's health
     * <em>before</em> the heal is applied and the team's max health. Heals that
     * would overflow past max health only count the part that fits.
     */
    public static double computeHealthGain(double healthBefore, double maxHealth, double healAmount) {
        if (healAmount <= 0) return 0.0;
        return Math.max(0.0, Math.min(healAmount, maxHealth - healthBefore));
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
        plugin.getRunManager().markHealthDirty();
        syncHealth();
        return Math.max(0.0, previous - state.getHealth());
    }

    public void clearDamageFrames() {
        slowRegenTickCounter = 0;
    }
}
