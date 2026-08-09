package me.ayinaki.sharedchain.chain;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.run.RunState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ChainService implements Listener {
    public static final String CHAIN_ANCHOR_TAG = "sharedchain_chain_anchor";

    private final SharedChain plugin;
    private final List<UUID> chainOrder = new ArrayList<>();
    private final Map<UUID, Bat> anchorsByPlayer = new HashMap<>();
    private BukkitTask task;
    private boolean active = false;
    private int tickCounter = 0;

    public ChainService(SharedChain plugin) {
        this.plugin = plugin;
    }

    public void activateFor(Collection<? extends Player> players) {
        if (!plugin.getConfig().getBoolean("chain.enabled", true)) {
            deactivate();
            return;
        }

        List<Player> eligible = new ArrayList<>();
        for (Player player : players) {
            if (!player.isOnline()) continue;
            if (!plugin.getRunManager().isWorldEnabled(player.getWorld())) continue;
            eligible.add(player);
        }

        chainOrder.clear();
        for (Player player : eligible) {
            chainOrder.add(player.getUniqueId());
        }

        if (chainOrder.size() < 2) {
            deactivate();
            return;
        }

        Collections.shuffle(chainOrder, ThreadLocalRandom.current());
        active = true;
        tickCounter = 0;
        ensureTask();
        plugin.getComponentLogger().info("Chain order set: " + chainOrder);
    }

    public String getCenterPlayerName() {
        if (chainOrder.isEmpty()) return null;
        int middleIndex = chainOrder.size() / 2;
        UUID centerUuid = chainOrder.get(middleIndex);
        Player player = Bukkit.getPlayer(centerUuid);
        return player != null ? player.getName() : "Unknown";
    }

    /**
     * Adds a player to the chain. Used when a player joins during the lobby or a run
     * (e.g. after a server restart), where the in-memory chain order was lost.
     * The per-tick pass handles anchor creation and physics once enough players are present.
     */
    public void addPlayer(Player player) {
        if (!plugin.getConfig().getBoolean("chain.enabled", true)) return;
        if (!plugin.getRunManager().isWorldEnabled(player.getWorld())) return;
        if (chainOrder.contains(player.getUniqueId())) return;

        chainOrder.add(player.getUniqueId());
        if (chainOrder.size() >= 2 && !active) {
            active = true;
            tickCounter = 0;
            ensureTask();
        }
        plugin.getComponentLogger().info("Added " + player.getName() + " to the chain (order: " + chainOrder + ")");
    }

    /**
     * Removes leftover anchor entities saved by an unclean shutdown (crash/hard kill).
     * On a clean stop the anchors are removed by {@link #deactivate()}, so this only
     * cleans up after interrupted sessions where the plugin's onDisable never ran.
     * <p>
     * Only entities in <em>loaded</em> chunks can be seen here; stale anchors that
     * were saved in unloaded chunks (e.g. the team was far from spawn when the
     * server died) are caught by {@link #onChunkLoad} the moment their chunk loads.
     */
    public void purgeStaleAnchors() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.getRunManager().isWorldEnabled(world)) continue;
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(CHAIN_ANCHOR_TAG)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getComponentLogger().info("Purged " + removed + " stale chain anchor(s) from a previous session.");
        }
    }

    /**
     * Sweeps every chunk as it loads and removes any chain anchor that does not
     * belong to the live chain. This is what actually cleans up anchors saved in
     * unloaded chunks by an unclean shutdown: they are deleted the instant their
     * chunk loads (before the player sees them), instead of lingering forever.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getRunManager().isWorldEnabled(event.getWorld())) return;
        for (Entity entity : event.getChunk().getEntities()) {
            if (!entity.getScoreboardTags().contains(CHAIN_ANCHOR_TAG)) continue;
            // Anchors the live chain is actively tracking are legitimate; anything
            // else tagged as an anchor is a leftover from a previous session.
            if (anchorsByPlayer.containsValue(entity)) continue;
            Location loc = entity.getLocation();
            entity.remove();
            plugin.getComponentLogger().info("Removed stale chain anchor at "
                    + loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
        }
    }

    public void deactivate() {
        active = false;
        chainOrder.clear();
        clearAllAnchors();
        stopTask();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isChainAnchor(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(CHAIN_ANCHOR_TAG);
    }

    private void ensureTask() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!active) return;

        RunState state = plugin.getRunManager().getState();
        if (state == RunState.WIPED || state == RunState.RESETTING) return;

        List<Player> orderedOnline = resolveOrderedPlayers();
        if (orderedOnline.size() < 2) {
            clearAllAnchors();
            return;
        }

        tickCounter++;
        int leashRefreshInterval = Math.max(20, plugin.getConfig().getInt("chain.leash-refresh-interval", 100));
        boolean forceRefresh = tickCounter % leashRefreshInterval == 0;

        updateAnchorsAndLeashes(orderedOnline, forceRefresh);
        applyPhysics(orderedOnline);
    }

    private List<Player> resolveOrderedPlayers() {
        List<Player> result = new ArrayList<>();

        for (UUID uuid : chainOrder) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || player.isDead()) continue;
            if (!plugin.getRunManager().isWorldEnabled(player.getWorld())) continue;
            result.add(player);
        }

        return result;
    }

    private void updateAnchorsAndLeashes(List<Player> orderedOnline, boolean forceRefresh) {
        Set<UUID> activePlayers = new HashSet<>();
        for (Player player : orderedOnline) {
            activePlayers.add(player.getUniqueId());

            Bat anchor = anchorsByPlayer.get(player.getUniqueId());
            if (anchor == null || !anchor.isValid() || anchor.isDead()) {
                anchor = spawnAnchor(player);
                if (anchor == null) continue;
                anchorsByPlayer.put(player.getUniqueId(), anchor);
            }

            Location target = getWaistAnchorLocation(player);
            anchor.teleport(target);
        }

        removeUnusedAnchors(activePlayers);

        for (int i = 0; i < orderedOnline.size(); i++) {
            Player current = orderedOnline.get(i);
            Bat currentAnchor = anchorsByPlayer.get(current.getUniqueId());
            if (currentAnchor == null || !currentAnchor.isValid()) continue;

            Bat nextAnchor = null;
            if (i + 1 < orderedOnline.size()) {
                nextAnchor = anchorsByPlayer.get(orderedOnline.get(i + 1).getUniqueId());
            }

            if (i + 1 < orderedOnline.size()) {
                Player nextPlayer = orderedOnline.get(i + 1);
                if (!current.getWorld().equals(nextPlayer.getWorld())) {
                    if (currentAnchor.isLeashed()) {
                        currentAnchor.setLeashHolder(null);
                    }
                    continue;
                }
            }

            if (nextAnchor == null || !nextAnchor.isValid()) {
                if (currentAnchor.isLeashed()) {
                    currentAnchor.setLeashHolder(null);
                }
                continue;
            }

            boolean needsAttach = forceRefresh;
            if (currentAnchor.isLeashed()) {
                try {
                    needsAttach = needsAttach || !nextAnchor.equals(currentAnchor.getLeashHolder());
                } catch (IllegalStateException ignored) {
                    needsAttach = true;
                }
            } else {
                needsAttach = true;
            }

            if (needsAttach) {
                currentAnchor.setLeashHolder(nextAnchor);
            }
        }
    }

    private Bat spawnAnchor(Player player) {
        Location spawnLoc = getWaistAnchorLocation(player);
        return player.getWorld().spawn(spawnLoc, Bat.class, bat -> {
            bat.setAI(false);
            bat.setAware(false);
            bat.setInvulnerable(true);
            bat.setSilent(true);
            bat.setInvisible(true);
            bat.setGravity(false);
            bat.setCollidable(false);
            bat.setAwake(true);
            bat.setPersistent(true);
            bat.setRemoveWhenFarAway(false);
            bat.setCanPickupItems(false);
            bat.addScoreboardTag(CHAIN_ANCHOR_TAG);
            applyOptionalTinyScale(bat);

        });
    }

    private void applyOptionalTinyScale(Bat bat) {
        // Keep waist-attached visuals while minimizing hitbox interception.
        if (bat.getAttribute(Attribute.SCALE) != null) {
            bat.getAttribute(Attribute.SCALE).setBaseValue(
                    plugin.getConfig().getDouble("chain.anchor-scale", 0.1)
            );
        }
    }

    private void applyPhysics(List<Player> orderedOnline) {
        double maxDistance = plugin.getConfig().getDouble("chain.max-distance", 8.0);
        double slack = plugin.getConfig().getDouble("chain.slack-distance", 0.75);
        double threshold = maxDistance + slack;
        double thresholdSq = threshold * threshold;
        
        double pullStrength = plugin.getConfig().getDouble("chain.pull-strength", 0.08);
        double maxPullPerTick = plugin.getConfig().getDouble("chain.max-pull-per-tick", 0.18);

        for (int i = 0; i < orderedOnline.size() - 1; i++) {
            Player p1 = orderedOnline.get(i);
            Player p2 = orderedOnline.get(i + 1);

            if (!p1.getWorld().equals(p2.getWorld())) continue;

            double distSq = p1.getLocation().distanceSquared(p2.getLocation());
            if (distSq <= thresholdSq) continue;

            double distance = Math.sqrt(distSq);
            Vector direction = p2.getLocation().toVector().subtract(p1.getLocation().toVector());
            if (direction.lengthSquared() < 0.0001) continue;
            direction.normalize();

            double pullAmount = Math.min((distance - maxDistance) * pullStrength, maxPullPerTick);
            Vector p1Velocity = direction.clone().multiply(pullAmount);
            Vector p2Velocity = direction.clone().multiply(-pullAmount);

            p1.setVelocity(limitVelocity(p1.getVelocity().add(p1Velocity)));
            p2.setVelocity(limitVelocity(p2.getVelocity().add(p2Velocity)));
        }
    }

    private Vector limitVelocity(Vector velocity) {
        double max = plugin.getConfig().getDouble("chain.max-result-velocity", 1.6);
        double len = velocity.length();
        if (len > max) {
            return velocity.normalize().multiply(max);
        }
        return velocity;
    }

    private void removeUnusedAnchors(Set<UUID> activePlayers) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Bat> entry : anchorsByPlayer.entrySet()) {
            UUID playerId = entry.getKey();
            Bat anchor = entry.getValue();

            if (!activePlayers.contains(playerId) || anchor == null || !anchor.isValid()) {
                if (anchor != null && anchor.isValid()) {
                    anchor.setLeashHolder(null);
                    anchor.remove();
                }
                toRemove.add(playerId);
            }
        }

        for (UUID id : toRemove) {
            anchorsByPlayer.remove(id);
        }
    }

    private void clearAllAnchors() {
        for (Bat anchor : anchorsByPlayer.values()) {
            if (anchor == null || !anchor.isValid()) continue;
            anchor.setLeashHolder(null);
            anchor.remove();
        }
        anchorsByPlayer.clear();
    }

    private Location getWaistAnchorLocation(Player player) {
        Location loc = player.getLocation();
        // Small forward prediction makes lead visuals feel less delayed at 20 TPS.
        double predictionTicks = plugin.getConfig().getDouble("chain.anchor-prediction-ticks", 0.35);
        Vector velocity = player.getVelocity().clone();
        Vector predictedOffset = velocity.multiply(predictionTicks);

        Location anchor = loc.clone().add(predictedOffset);
        // Use yaw-only vectors so offset remains stable even when looking straight down/up.
        double yawRad = Math.toRadians(loc.getYaw());
        Vector forward = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();

        double backOffset = plugin.getConfig().getDouble("chain.anchor-back-offset", 0.38);
        double sideOffset = plugin.getConfig().getDouble("chain.anchor-side-offset", 0.0);
        anchor.add(forward.clone().multiply(-backOffset));
        anchor.add(right.multiply(sideOffset));

        anchor.setY(anchor.getY() + plugin.getConfig().getDouble("chain.anchor-y-offset", 0.7));
        return anchor;
    }
}
