package me.ayinaki.sharedchain.stats;

import me.ayinaki.sharedchain.SharedChain;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RunStatsService {
    private final SharedChain plugin;
    private final Map<UUID, Double> hpHealed = new HashMap<>();
    private final Map<UUID, Double> damageTaken = new HashMap<>();

    public RunStatsService(SharedChain plugin) {
        this.plugin = plugin;
    }

    public void reset() {
        hpHealed.clear();
        damageTaken.clear();
    }

    public void addHeal(Player player, double amount) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        hpHealed.put(uuid, hpHealed.getOrDefault(uuid, 0.0) + amount);
    }

    public void addDamage(Player player, double amount) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        damageTaken.put(uuid, damageTaken.getOrDefault(uuid, 0.0) + amount);
    }

    public UUID getTopSponsor() {
        return hpHealed.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public double getHealedAmount(UUID uuid) {
        return hpHealed.getOrDefault(uuid, 0.0);
    }

    public UUID getTopSponge() {
        return damageTaken.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public double getDamageAmount(UUID uuid) {
        return damageTaken.getOrDefault(uuid, 0.0);
    }
}
