package me.ayinaki.sharedchain.tag;

import me.ayinaki.sharedchain.SharedChain;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves the role tags shown next to players' names (tab list + nametag).
 * Three fixed tags, all config-driven and dependency-free:
 * <ul>
 *   <li>{@code dev} - bound to a single configured player (name or UUID)</li>
 *   <li>{@code sponsor} - whoever is currently the healing sponsor (the UI layer
 *       knows who that is and passes the UUID in)</li>
 *   <li>{@code record-holder} - whoever was last set via
 *       {@code /sharedchain record}, persisted in stats.yml (the future
 *       leaderboard will set this automatically)</li>
 * </ul>
 */
public class TagService {

    /** A tag bound to a player: the config slot (dev/sponsor/record-holder) and the font image it renders. */
    public record Tag(String slot, String image) {
        /** Single-char team-name code so tag combinations stay within the 16-char team name limit. */
        public String code() {
            return slot.substring(0, 1);
        }
    }

    private final SharedChain plugin;

    public TagService(SharedChain plugin) {
        this.plugin = plugin;
    }

    /** True when the player matches the configured dev player (accepts a name or a UUID). */
    public boolean isDev(Player player) {
        String dev = plugin.getConfig().getString("tags.dev.player", "");
        if (dev == null || dev.isBlank()) return false;
        try {
            return player.getUniqueId().equals(UUID.fromString(dev));
        } catch (IllegalArgumentException ignored) {
            return dev.equalsIgnoreCase(player.getName());
        }
    }

    /** The current record holder, or null when none is set. */
    public UUID getRecordHolder() {
        String uuid = plugin.getStatsConfig().getString("record-holder.uuid", "");
        if (uuid == null || uuid.isBlank()) return null;
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Sets (or clears, with null) the record holder, persisting to stats.yml. */
    public void setRecordHolder(UUID uuid) {
        plugin.getStatsConfig().set("record-holder.uuid", uuid != null ? uuid.toString() : null);
        plugin.saveStats();
    }

    /**
     * The tags applying to a player, in display order. Only tags whose image is
     * actually loaded are returned, so a missing PNG never leaks a literal
     * {@code %name%} placeholder into a name prefix.
     *
     * @param sponsorUuid the current sponsor's UUID (from the UI layer), or null
     */
    public List<Tag> tagsFor(Player player, UUID sponsorUuid) {
        List<Tag> tags = new ArrayList<>();
        if (isDev(player)) {
            addIfLoaded(tags, "tags.dev.image", "dev");
        }
        if (sponsorUuid != null && player.getUniqueId().equals(sponsorUuid)) {
            addIfLoaded(tags, "tags.sponsor.image", "sponsor");
        }
        UUID record = getRecordHolder();
        if (record != null && player.getUniqueId().equals(record)) {
            addIfLoaded(tags, "tags.record-holder.image", "record-holder");
        }
        return tags;
    }

    private void addIfLoaded(List<Tag> tags, String configKey, String slot) {
        String image = plugin.getConfig().getString(configKey, "");
        if (image != null && !image.isBlank() && plugin.getFontImageService().hasImage(image)) {
            tags.add(new Tag(slot, image));
        }
    }
}
