package me.ayinaki.ayinchallenge.run;

import me.ayinaki.ayinchallenge.AyinChallenge;
import me.ayinaki.ayinchallenge.death.DeathInfo;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RunManager {
    private final AyinChallenge plugin;
    private RunState state = RunState.IDLE;
    private SharedState sharedState;
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private DeathInfo lastDeathInfo;
    private long startTime;
    private long endTime;
    private long totalElapsedTime;
    private int currentResetId = 0;
    private int runCounter = 0;

    public RunManager(AyinChallenge plugin) {
        this.plugin = plugin;
        this.sharedState = new SharedState(plugin.getConfig().getDouble("shared-health.max-health", 20.0));
        this.runCounter = plugin.getStatsConfig().getInt("run-counter", 0);
        this.totalElapsedTime = plugin.getStatsConfig().getLong("total-elapsed-time", 0L);
        this.startTime = plugin.getStatsConfig().getLong("run-state.start-time", 0L);
        this.endTime = plugin.getStatsConfig().getLong("run-state.end-time", 0L);
        this.currentResetId = plugin.getStatsConfig().getInt("run-state.current-reset-id", 0);

        String storedState = plugin.getStatsConfig().getString("run-state.state", RunState.IDLE.name());
        try {
            this.state = RunState.valueOf(storedState);
        } catch (IllegalArgumentException ignored) {
            this.state = RunState.IDLE;
        }
    }

    public void start() {
        if (state == RunState.RUNNING) return;

        state = RunState.RUNNING;
        plugin.getRunStatsService().reset();
        sharedState.reset(plugin.getConfig().getDouble("shared-health.max-health", 20.0));

        participants.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isWorldEnabled(player.getWorld())) {
                participants.add(player.getUniqueId());
            }
        }
        startTime = System.currentTimeMillis();
        endTime = 0;

        plugin.getFinishDetector().reset();
        applyImmediateRespawnRule();
        applyHardcoreRule();
        applyNaturalRegenerationRule(false);
        plugin.getHealthService().syncHealth();
        plugin.getHealthService().clearDamageFrames();
        plugin.getTimerService().start();
        persistRunState();
    }

    public void stop() {
        if (state == RunState.RUNNING) {
            totalElapsedTime += getElapsedTime();
        }
        state = RunState.IDLE;
        endTime = System.currentTimeMillis();
        plugin.getHealthService().clearDamageFrames();
        persistRunState();
    }

    public void onWorldResetComplete(org.bukkit.World world) {
        runCounter++;
        plugin.getStatsConfig().set("run-counter", runCounter);
        state = RunState.IDLE;
        applyImmediateRespawnRule();
        applyHardcoreRule();
        applyNaturalRegenerationRule(false);
        persistRunState();
        plugin.getLobbyService().setupLobby(world);
    }

    public void finish() {
        if (state != RunState.RUNNING) return;
        totalElapsedTime += getElapsedTime();
        state = RunState.FINISHED;
        endTime = System.currentTimeMillis();
        persistRunState();
    }

    public void wipe(DeathInfo deathInfo) {
        if (state != RunState.RUNNING) return;
        totalElapsedTime += getElapsedTime();
        state = RunState.WIPED;
        lastDeathInfo = deathInfo;
        endTime = System.currentTimeMillis();
        plugin.getHealthService().clearDamageFrames();
        persistRunState();
    }

    public RunState getState() {
        return state;
    }

    public void setState(RunState state) {
        this.state = state;
        persistRunState();
    }

    public SharedState getSharedState() {
        return sharedState;
    }

    public Set<UUID> getParticipants() {
        return participants;
    }

    public boolean isParticipant(Player player) {
        return participants.contains(player.getUniqueId());
    }

    public void addParticipant(Player player) {
        participants.add(player.getUniqueId());
    }

    public void removeParticipant(Player player) {
        participants.remove(player.getUniqueId());
    }

    public boolean isWorldEnabled(World world) {
        if (world.getKey().equals(plugin.getFakeOverworldKey())) {
            return true;
        }
        // Nether and End are enabled if they are the default ones and we are in a run
        if (world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END) {
            return true;
        }
        return false;
    }

    public void resetPlayer(Player player) {
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.closeInventory();
        player.getInventory().clear();
        player.getEnderChest().clear();
        player.setLevel(0);
        player.setExp(0);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.clearActivePotionEffects();

        // Revoke advancements (similar to Fahare)
        try {
            java.util.Iterator<org.bukkit.advancement.Advancement> advancements = Bukkit.advancementIterator();
            while (advancements.hasNext()) {
                org.bukkit.advancement.Advancement advancement = advancements.next();
                org.bukkit.advancement.AdvancementProgress progress = player.getAdvancementProgress(advancement);
                for (String criteria : progress.getAwardedCriteria()) {
                    progress.revokeCriteria(criteria);
                }
            }
        } catch (Exception e) {
            plugin.getComponentLogger().warn("Failed to revoke advancements for " + player.getName(), e);
        }
    }

    public int getCurrentResetId() {
        return currentResetId;
    }

    public void incrementResetId() {
        currentResetId++;
        persistRunState();
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getTotalElapsedTime() {
        long current = 0;
        if (state == RunState.RUNNING) {
            current = getElapsedTime();
        }
        return totalElapsedTime + current;
    }

    public DeathInfo getLastDeathInfo() {
        return lastDeathInfo;
    }

    public long getElapsedTime() {
        if (state == RunState.RUNNING) {
            return System.currentTimeMillis() - startTime;
        } else if (startTime > 0 && endTime > 0) {
            return endTime - startTime;
        }
        return 0;
    }

    public int getRunCounter() {
        return runCounter;
    }

    public void setRunCounter(int runCounter) {
        this.runCounter = Math.max(0, runCounter);
        plugin.getStatsConfig().set("run-counter", this.runCounter);
        persistRunState();
    }

    public void pauseTimingForShutdown() {
        if (state == RunState.RUNNING && endTime == 0) {
            endTime = System.currentTimeMillis();
            persistRunState();
        }
    }

    public void restoreAfterEnable() {
        if (state == RunState.RUNNING && startTime > 0 && endTime > startTime) {
            long elapsed = endTime - startTime;
            startTime = System.currentTimeMillis() - elapsed;
            endTime = 0;
            persistRunState();
        }
        if (state == RunState.RUNNING || state == RunState.WIPED) {
            plugin.getTimerService().start();
        }
    }

    private void persistRunState() {
        plugin.getStatsConfig().set("run-state.state", state.name());
        plugin.getStatsConfig().set("run-state.start-time", startTime);
        plugin.getStatsConfig().set("run-state.end-time", endTime);
        plugin.getStatsConfig().set("run-state.current-reset-id", currentResetId);
        plugin.getStatsConfig().set("total-elapsed-time", totalElapsedTime);
        plugin.saveStats();
    }

    private void applyImmediateRespawnRule() {
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) continue;
            world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        }
    }

    private void applyHardcoreRule() {
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) continue;
            world.setHardcore(true);
        }
    }

    private void applyNaturalRegenerationRule(boolean enabled) {
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) continue;
            world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, enabled);
        }
    }
}
