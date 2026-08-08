package me.ayinaki.ayinchallenge.death;

import me.ayinaki.ayinchallenge.AyinChallenge;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeathTrackerService {
    private final AyinChallenge plugin;
    private final Map<UUID, Integer> wipeCount = new HashMap<>();

    public DeathTrackerService(AyinChallenge plugin) {
        this.plugin = plugin;
        loadFromStats();
    }

    public void incrementDeaths(Player player) {
        UUID uuid = player.getUniqueId();
        int count = wipeCount.getOrDefault(uuid, 0) + 1;
        wipeCount.put(uuid, count);
        plugin.getStatsConfig().set("deaths." + uuid, count);
        plugin.saveStats();
        plugin.getUIService().updateDeathCount(player, count);
    }

    public int getDeaths(Player player) {
        return wipeCount.getOrDefault(player.getUniqueId(), 0);
    }

    public int getDeaths(UUID uuid) {
        return wipeCount.getOrDefault(uuid, 0);
    }

    public void setDeaths(UUID uuid, int count) {
        int clamped = Math.max(0, count);
        if (clamped == 0) {
            wipeCount.remove(uuid);
            plugin.getStatsConfig().set("deaths." + uuid, null);
        } else {
            wipeCount.put(uuid, clamped);
            plugin.getStatsConfig().set("deaths." + uuid, clamped);
        }
        plugin.saveStats();

        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null && online.isOnline()) {
            plugin.getUIService().updateDeathCount(online, clamped);
        }
    }

    private void loadFromStats() {
        var section = plugin.getStatsConfig().getConfigurationSection("deaths");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int count = section.getInt(key, 0);
                if (count > 0) {
                    wipeCount.put(uuid, count);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getComponentLogger().warn("Skipping invalid UUID in stats.yml deaths section: " + key);
            }
        }
    }
}
