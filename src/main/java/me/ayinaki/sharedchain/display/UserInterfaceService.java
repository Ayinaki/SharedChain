package me.ayinaki.sharedchain.display;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.run.RunState;
import me.ayinaki.sharedchain.tag.TagService;
import me.ayinaki.sharedchain.util.ComponentUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Consolidated service for managing all player-facing UI elements:
 * BossBars, Tab Lists, and Scoreboards.
 */
public class UserInterfaceService {
    // Attempt Glyphs
    private static final String DIGIT_GLYPHS = "\uE130\uE131\uE132\uE133\uE134\uE135\uE136\uE137\uE138\uE139";
    /**
     * The pill glyph (\uE001, a 113x18 near-black rounded badge) followed by a
     * pull-back advance that centers the text. The text is Daydream rendered by
     * the client (a ttf font provider, size 10, shifted 3.5px down so the badge
     * clears the top of the screen); digit advances are tabular, so each digit
     * count gets its own pull, computed from the font's advance widths
     * ("ATTEMPT" = 78.75px, space = 5px, digits = 10.3125px):
     *  - \uF809 = -108.6875: two digits (78.75 + 5 + 20.625 = 104.375px, pad 4.31)
     *  - \uF80C = -103.53125: one digit (78.75 + 5 + 10.3125 = 94.0625px, pad 9.47)
     */
    private static final String ATTEMPT_PREFIX = "\uE001\uF809";
    private static final String ATTEMPT_PREFIX_SINGLE = "\uE001\uF80C";
    /** "ATTEMPT" (no colon) - each character is a private-use glyph in the Daydream font. */
    private static final String ATTEMPT_LABEL = "\uE141\uE174\uE174\uE165\uE16D\uE170\uE174";
    /** A 5px space (a space-provider advance) between the label and the number. */
    private static final String ATTEMPT_SPACE = "\uF825";

    private final SharedChain plugin;

    // Scoreboard Elements
    private final Scoreboard scoreboard;
    private final Objective deathObjective;
    private final Team sponsorTeam;
    /** Dynamic teams (participant colors + tag groups + spectators), created lazily. */
    private final Map<String, Team> teams = new HashMap<>();
    private final TagService tagService;
    private UUID sponsorUuid;

    // BossBar Elements
    private final BossBar attemptsBossBar;
    private int lastAttempt = -1;
    private final Set<UUID> lastBossBarViewers = new HashSet<>();

    // Tab List Elements (content only rebuilt when it actually changes)
    private String lastHeaderText;
    private String lastFooterText;
    private final Set<UUID> lastTabViewers = new HashSet<>();
    private final Set<UUID> scoreboardApplied = new HashSet<>();
    private String lastTeamSignature = "";

    // Action Bar Element (component only rebuilt when the time string changes)
    private String lastActionBarTime;
    private Component actionBarComp;

    public UserInterfaceService(SharedChain plugin, TagService tagService) {
        this.plugin = plugin;
        this.tagService = tagService;

        // Setup Scoreboard
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.deathObjective = scoreboard.registerNewObjective("deaths", Criteria.DUMMY, ComponentUtil.parse("Deaths"));
        // The death counter shows under each player's nametag (not in the tab
        // list). The icon is added to the display name by refreshDeathDisplayName()
        // once the font images are loaded.
        this.deathObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);

        // Team names are number-prefixed so the tab list sorts them in the right
        // order: the current sponsor first, then run participants (grouped by their
        // chosen name color), then lobby-only spectators. The tab list sorts teams
        // by character value (digits before letters), so all prefixes use digits
        // (ac1_ < ac2... < ac3_) - an underscore prefix would sort after the digits.
        this.sponsorTeam = scoreboard.registerNewTeam("ac1_sponsor");
        this.sponsorTeam.prefix(ComponentUtil.parse("<green>❤ </green>"));
        this.sponsorTeam.color(NamedTextColor.GREEN);

        // Setup BossBar
        this.attemptsBossBar = BossBar.bossBar(
                Component.text("Attempts"),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
    }

    public void updateAll() {
        RunState state = plugin.getRunManager().getState();

        // 1. Action Bar Timer (If enabled)
        if (state == RunState.RUNNING || state == RunState.WIPED || state == RunState.FINISHED) {
            String mode = plugin.getConfig().getString("timer.display-mode", "ACTION_BAR");
            String time = plugin.getTimerService().getFormattedTime();
            // updateAll() runs every tick; skip the MiniMessage parse when the time is unchanged
            if (!time.equals(lastActionBarTime)) {
                lastActionBarTime = time;
                actionBarComp = ComponentUtil.parse("<timer>", Placeholder.parsed("timer", time));
            }

            for (UUID uuid : plugin.getRunManager().getParticipants()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline() && "ACTION_BAR".equals(mode)) {
                    player.sendActionBar(actionBarComp);
                }
            }
        }

        // 2. Tab Header/Footer + player-list team colors
        if (plugin.getConfig().getBoolean("display.tab-enabled", true)) {
            boolean scoreboardEnabled = plugin.getConfig().getBoolean("display.scoreboard-enabled", true);
            String headerText = buildHeaderText();
            String footerText = buildFooterText(state);

            Set<UUID> viewers = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                viewers.add(player.getUniqueId());
            }
            // updateAll() runs every tick; only re-send when the content or audience changed
            boolean contentChanged = !headerText.equals(lastHeaderText) || !footerText.equals(lastFooterText);
            boolean audienceChanged = !viewers.equals(lastTabViewers);
            lastHeaderText = headerText;
            lastFooterText = footerText;
            lastTabViewers.clear();
            lastTabViewers.addAll(viewers);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (contentChanged || audienceChanged) {
                    player.sendPlayerListHeaderAndFooter(ComponentUtil.parse(headerText), ComponentUtil.parse(footerText));
                }

                // 3. Scoreboard (team colors + death counts) applies to everyone, in every
                // state, so the tab list looks right while waiting in the lobby too.
                if (scoreboardEnabled) {
                    if (scoreboardApplied.add(player.getUniqueId())) {
                        player.setScoreboard(scoreboard);
                    }
                } else {
                    scoreboardApplied.remove(player.getUniqueId());
                }
            }

            if (scoreboardEnabled) {
                refreshTeams();
            }
        }

        refreshAttemptBossBar();
    }

    // ------------------------------------------------------------------
    // Tab list content
    // ------------------------------------------------------------------

    /**
     * Header: the logo image (the client centers each header line) with the title
     * text underneath. Falls back to the title alone when no logo image is set or
     * the configured image name isn't loaded.
     */
    private String buildHeaderText() {
        String logo = plugin.getConfig().getString("display.tab-logo", "title");
        String title = plugin.getConfig().getString("display.tab-header", "");
        if (logo != null && !logo.isBlank() && plugin.getFontImageService().hasImage(logo)) {
            // The client anchors the tab header to the top of the screen and bitmap
            // glyphs draw upward from the baseline, so a tall logo clips off the top.
            // Pad with blank lines to push the logo down into view: each header line
            // occupies 9px, and the logo's top edge sits ascent (64) px above its
            // line's baseline. Each pad line carries a single space so it is a real
            // (invisible) line regardless of any client-side trimming. By default
            // the padding is computed from the logo's own glyph height so it works
            // no matter what size the image is; set display.tab-logo-padding to a
            // number to override.
            int padding = plugin.getConfig().getInt("display.tab-logo-padding", -1);
            if (padding < 0) {
                int height = plugin.getFontImageService().getImageHeight(logo);
                if (height > 0) {
                    // The header starts at y=10, the baseline sits ~8px below the line
                    // top, and the logo's top edge is baseline - height. Solve for the
                    // blank lines (9px each) so the top edge clears the screen, plus
                    // one line of breathing room.
                    padding = (int) Math.ceil((height - 18) / 9.0) + 1;
                } else {
                    padding = 0;
                }
            }
            StringBuilder header = new StringBuilder(" \n".repeat(Math.max(0, padding)));
            header.append('%').append(logo).append('%');
            // Optional subtitle under the logo; blank means just the logo.
            if (title != null && !title.isBlank()) {
                header.append('\n').append(title);
            }
            return header.toString();
        }
        return title;
    }

    /**
     * Footer: a separator, the run number + status, the current and total run
     * timers, and (when there is data) the current run's top healer and sponge.
     */
    private String buildFooterText(RunState state) {
        String status = switch (state) {
            case RUNNING -> "<green>● Running</green>";
            case STARTING -> "<yellow>● Starting</yellow>";
            case WIPED -> "<red>● Wiped</red>";
            case FINISHED -> "<aqua>● Finished</aqua>";
            case RESETTING -> "<light_purple>● Resetting</light_purple>";
            default -> "<gray>● Lobby</gray>";
        };

        boolean inRun = state == RunState.RUNNING || state == RunState.WIPED || state == RunState.FINISHED;
        String current = inRun ? plugin.getTimerService().getFormattedTime() : "<gray>—</gray>";

        StringBuilder sb = new StringBuilder();
        sb.append("<dark_gray>──────────────────</dark_gray>\n");
        sb.append("<yellow>Run <white>#").append(plugin.getRunManager().getRunCounter())
                .append("</white></yellow>  <dark_gray>|</dark_gray>  ").append(status).append('\n');
        sb.append("<yellow>Current</yellow> <white>").append(current)
                .append("</white>  <dark_gray>|</dark_gray>  <yellow>Total</yellow> <white>")
                .append(plugin.getTimerService().getFormattedTotalTime()).append("</white>");

        String healer = statName(plugin.getRunStatsService().getTopSponsor());
        String sponge = statName(plugin.getRunStatsService().getTopSponge());
        if (healer != null || sponge != null) {
            sb.append('\n');
            if (healer != null) {
                sb.append("<green>Healer</green> <white>").append(healer).append("</white>");
            }
            if (sponge != null) {
                if (healer != null) sb.append("   ");
                sb.append("<red>Sponge</red> <white>").append(sponge).append("</white>");
            }
        }
        return sb.toString();
    }

    private String statName(UUID uuid) {
        if (uuid == null) return null;
        return Bukkit.getOfflinePlayer(uuid).getName();
    }

    // ------------------------------------------------------------------
    // Player-list team colors
    // ------------------------------------------------------------------

    /**
     * Assigns every online player to the right scoreboard team (sponsor,
     * participant or spectator) so the tab list shows colored names. Only touches
     * the scoreboard when membership actually changes, to avoid packet spam.
     */
    private void refreshTeams() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName));

        StringBuilder signature = new StringBuilder();
        for (Player player : players) {
            signature.append(player.getName()).append(':');
            List<TagService.Tag> tags = tagService.tagsFor(player, sponsorUuid);
            if (player.getUniqueId().equals(sponsorUuid)) {
                signature.append("s;");
            } else if (plugin.getRunManager().isParticipant(player)) {
                // Include the color so a /sharedchain color change re-applies immediately.
                signature.append('p').append(getNameColor(player.getUniqueId()).toString()).append(';');
            } else {
                signature.append("o;");
            }
            // Include the tag set so sponsor flips, record-holder changes and
            // config reloads re-apply immediately.
            signature.append(tags).append(';');
        }
        String currentSignature = signature.toString();
        if (currentSignature.equals(lastTeamSignature)) return;
        lastTeamSignature = currentSignature;

        for (Team team : teams.values()) {
            for (String entry : team.getEntries()) {
                team.removeEntry(entry);
            }
        }
        for (Player player : players) {
            List<TagService.Tag> tags = tagService.tagsFor(player, sponsorUuid);
            if (player.getUniqueId().equals(sponsorUuid)) {
                sponsorTeam.prefix(tagPrefix(tags, true));
                sponsorTeam.addEntry(player.getName());
            } else if (plugin.getRunManager().isParticipant(player)) {
                participantTeam(getNameColor(player.getUniqueId()), tags).addEntry(player.getName());
            } else {
                spectatorTeam(tags).addEntry(player.getName());
            }
        }
    }

    /**
     * The team for a run participant. Tagged players get their own team (keyed by
     * color + tag codes) so the tag glyphs can ride the team prefix; team names
     * stay within the 16-char limit via short color codes.
     */
    private Team participantTeam(NamedTextColor color, List<TagService.Tag> tags) {
        String key = tags.isEmpty()
                ? "ac2" + colorCode(color)
                : "ac2" + colorCode(color) + "t" + tagCodes(tags);
        return teams.computeIfAbsent(key, k -> {
            Team team = scoreboard.registerNewTeam(k);
            team.color(color);
            if (!tags.isEmpty()) {
                team.prefix(tagPrefix(tags, false));
            }
            return team;
        });
    }

    /** The team for a lobby-only spectator (gray), split by tag group. */
    private Team spectatorTeam(List<TagService.Tag> tags) {
        String key = tags.isEmpty() ? "ac3" : "ac3t" + tagCodes(tags);
        return teams.computeIfAbsent(key, k -> {
            Team team = scoreboard.registerNewTeam(k);
            team.color(NamedTextColor.GRAY);
            if (!tags.isEmpty()) {
                team.prefix(tagPrefix(tags, false));
            }
            return team;
        });
    }

    /**
     * The tag glyphs (plus the sponsor heart or a trailing space) shown before a
     * name. The glyphs are wrapped in an explicit white because the client tints
     * bitmap glyphs by the current text color and the team prefix would otherwise
     * inherit the player's name color, recoloring the whole badge.
     */
    private Component tagPrefix(List<TagService.Tag> tags, boolean sponsor) {
        Component prefix = Component.empty();
        boolean hasSponsorImage = false;
        for (TagService.Tag tag : tags) {
            if ("sponsor".equals(tag.slot())) hasSponsorImage = true;
            prefix = prefix.append(ComponentUtil.parse("<white>%" + tag.image() + "%</white>"));
        }
        if (sponsor) {
            if (hasSponsorImage) {
                // A sponsor tag image replaces the old text heart.
                prefix = prefix.append(Component.text(" "));
            } else {
                prefix = prefix.append(ComponentUtil.parse("<green>❤ </green>"));
            }
        } else if (!tags.isEmpty()) {
            prefix = prefix.append(Component.text(" "));
        }
        return prefix;
    }

    /** Concatenated single-char tag codes for the team name (e.g. dev+record -> "dr"). */
    private String tagCodes(List<TagService.Tag> tags) {
        StringBuilder codes = new StringBuilder();
        for (TagService.Tag tag : tags) {
            codes.append(tag.code());
        }
        return codes.toString();
    }

    /** Short stable code per name color, to keep team names under 16 chars. */
    private static String colorCode(NamedTextColor color) {
        return switch (color.toString()) {
            case "black" -> "blk";
            case "dark_blue" -> "dbl";
            case "dark_green" -> "dgr";
            case "dark_aqua" -> "daq";
            case "dark_red" -> "drd";
            case "dark_purple" -> "dpr";
            case "gold" -> "gld";
            case "gray" -> "gry";
            case "dark_gray" -> "dgy";
            case "blue" -> "blu";
            case "green" -> "grn";
            case "aqua" -> "aqu";
            case "red" -> "red";
            case "light_purple" -> "lpr";
            case "yellow" -> "yel";
            case "white" -> "wht";
            default -> "unk";
        };
    }

    // ------------------------------------------------------------------
    // Per-player name colors
    // ------------------------------------------------------------------

    /** All selectable name colors, keyed by lowercase name. */
    private static final Map<String, NamedTextColor> NAME_COLORS = new LinkedHashMap<>();

    static {
        NAME_COLORS.put("black", NamedTextColor.BLACK);
        NAME_COLORS.put("dark_blue", NamedTextColor.DARK_BLUE);
        NAME_COLORS.put("dark_green", NamedTextColor.DARK_GREEN);
        NAME_COLORS.put("dark_aqua", NamedTextColor.DARK_AQUA);
        NAME_COLORS.put("dark_red", NamedTextColor.DARK_RED);
        NAME_COLORS.put("dark_purple", NamedTextColor.DARK_PURPLE);
        NAME_COLORS.put("gold", NamedTextColor.GOLD);
        NAME_COLORS.put("gray", NamedTextColor.GRAY);
        NAME_COLORS.put("dark_gray", NamedTextColor.DARK_GRAY);
        NAME_COLORS.put("blue", NamedTextColor.BLUE);
        NAME_COLORS.put("green", NamedTextColor.GREEN);
        NAME_COLORS.put("aqua", NamedTextColor.AQUA);
        NAME_COLORS.put("red", NamedTextColor.RED);
        NAME_COLORS.put("light_purple", NamedTextColor.LIGHT_PURPLE);
        NAME_COLORS.put("yellow", NamedTextColor.YELLOW);
        NAME_COLORS.put("white", NamedTextColor.WHITE);
    }

    /** Parses a color name (case-insensitive), or null when unknown. */
    public static NamedTextColor parseColor(String name) {
        if (name == null || name.isBlank()) return null;
        return NAME_COLORS.get(name.toLowerCase(Locale.ROOT));
    }

    public static Set<String> getColorNames() {
        return NAME_COLORS.keySet();
    }

    /** The player's chosen name color, defaulting to gold. */
    public NamedTextColor getNameColor(UUID uuid) {
        NamedTextColor color = parseColor(plugin.getStatsConfig().getString("name-colors." + uuid, ""));
        return color != null ? color : NamedTextColor.GOLD;
    }

    /** Sets and persists a player's name color, applying it to the tab list immediately. */
    public void setNameColor(UUID uuid, NamedTextColor color) {
        plugin.getStatsConfig().set("name-colors." + uuid, color.toString());
        plugin.saveStats();
        refreshTeams();
    }

    public void refreshAttemptBossBar() {
        int attempt = Math.max(0, Math.min(99, plugin.getRunManager().getRunCounter()));

        // updateAll() runs every tick; only rebuild the boss bar when the attempt
        // number or the viewer set has actually changed.
        Set<UUID> viewers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            viewers.add(player.getUniqueId());
        }
        if (attempt == lastAttempt && viewers.equals(lastBossBarViewers)) return;

        lastAttempt = attempt;
        lastBossBarViewers.clear();
        lastBossBarViewers.addAll(viewers);

        attemptsBossBar.name(Component.text(buildAttemptTitle(attempt)));
        attemptsBossBar.color(BossBar.Color.YELLOW);
        attemptsBossBar.overlay(BossBar.Overlay.PROGRESS);
        attemptsBossBar.progress(1.0f);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(attemptsBossBar);
        }
    }

    public void updateDeathCount(Player player, int count) {
        deathObjective.getScore(player.getName()).setScore(count);
    }

    /**
     * Rebuilds the below-name death counter: the "Deaths" label plus the deaths
     * icon (a font image, e.g. deaths.png), with the player's count appended by
     * the client afterwards ("Deaths <icon>: <count>"). Called once the font
     * images are loaded and again on /sharedchain fonts reload, because the
     * objective is registered before the resource pack exists. Falls back to the
     * plain label when no icon is configured or loaded, and hides the counter
     * entirely when death-tracking.show-death-counts is false.
     */
    public void refreshDeathDisplayName() {
        if (!plugin.getConfig().getBoolean("death-tracking.show-death-counts", true)) {
            deathObjective.setDisplaySlot(null);
            return;
        }
        deathObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);

        String icon = plugin.getConfig().getString("death-tracking.death-icon", "deaths");
        Component name;
        if (icon != null && !icon.isBlank() && plugin.getFontImageService().hasImage(icon)) {
            name = ComponentUtil.parse("Deaths %" + icon + "%");
        } else {
            name = ComponentUtil.parse("Deaths");
        }
        if (!name.equals(deathObjective.displayName())) {
            deathObjective.displayName(name);
        }
    }

    /**
     * Called when a player disconnects. A reconnecting client starts with a blank
     * scoreboard, so the player must be re-registered before the scoreboard is
     * applied again (otherwise they would miss it for the rest of the run).
     */
    public void onPlayerQuit(Player player) {
        scoreboardApplied.remove(player.getUniqueId());
    }

    public void setSponsor(Player player) {
        clearSponsor();
        if (player != null) {
            sponsorUuid = player.getUniqueId();
            sponsorTeam.addEntry(player.getName());
        }
    }

    public void clearSponsor() {
        sponsorUuid = null;
        for (String entry : sponsorTeam.getEntries()) {
            sponsorTeam.removeEntry(entry);
        }
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(attemptsBossBar);
        }
    }

    private String buildAttemptTitle(int attempt) {
        // The pull-back (chosen per digit count) centers the whole run in the pill;
        // the digits follow the label after a 5px space char (\uF825).
        StringBuilder sb = new StringBuilder(attempt < 10 ? ATTEMPT_PREFIX_SINGLE : ATTEMPT_PREFIX);
        sb.append(ATTEMPT_LABEL).append(ATTEMPT_SPACE).append(toGlyphDigits(attempt));
        return sb.toString();
    }

    private String toGlyphDigits(int number) {
        String numeric = String.valueOf(number);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < numeric.length(); i++) {
            int digit = numeric.charAt(i) - '0';
            out.append(DIGIT_GLYPHS.charAt(digit));
        }
        return out.toString();
    }
}
