package me.ayinaki.sharedchain.display;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.run.RunState;
import me.ayinaki.sharedchain.util.ComponentUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Consolidated service for managing all player-facing UI elements:
 * BossBars, Tab Lists, and Scoreboards.
 */
public class UserInterfaceService {
    // Attempt Glyphs
    private static final String DIGIT_GLYPHS = "\uE130\uE131\uE132\uE133\uE134\uE135\uE136\uE137\uE138\uE139";
    private static final String PREFIX_SINGLE = "\uF804\uF804\uF804\uE001\uF808\uF822\uF822";
    private static final String PREFIX_DOUBLE = "\uF804\uF804\uF802\uE001\uF808\uF822\uF822";
    private static final String ATTEMPT_LABEL = "\uE141\uE174\uE174\uE165\uE16D\uE170\uE174\uE13A\uE120\uE120";
    private static final String PAD_SINGLE = "\uE120\uE120\uE120\uE120\uE120\uE120";
    private static final String PAD_DOUBLE = "\uE120\uE120\uE120";

    private final SharedChain plugin;
    
    // Scoreboard Elements
    private final Scoreboard scoreboard;
    private final Objective deathObjective;
    private final org.bukkit.scoreboard.Team sponsorTeam;

    // BossBar Elements
    private final BossBar attemptsBossBar;
    private int lastAttempt = -1;
    private final Set<UUID> lastBossBarViewers = new HashSet<>();

    // Tab List Elements (content only rebuilt when it actually changes)
    private String lastFooterText;
    private final Set<UUID> lastTabViewers = new HashSet<>();
    private final Set<UUID> scoreboardApplied = new HashSet<>();

    // Action Bar Element (component only rebuilt when the time string changes)
    private String lastActionBarTime;
    private Component actionBarComp;

    public UserInterfaceService(SharedChain plugin) {
        this.plugin = plugin;
        
        // Setup Scoreboard
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.deathObjective = scoreboard.registerNewObjective("deaths", Criteria.DUMMY, ComponentUtil.parse("Deaths"));
        this.deathObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);

        this.sponsorTeam = scoreboard.registerNewTeam("ac_sponsor");
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

        // 2. Tab Header/Footer
        if (plugin.getConfig().getBoolean("display.tab-enabled", true)) {
            String time = plugin.getTimerService().getFormattedTime();
            String totalTime = plugin.getTimerService().getFormattedTotalTime();
            String footerText = "Timer: " + time + " | Total: " + totalTime;

            Set<UUID> viewers = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                viewers.add(player.getUniqueId());
            }
            // updateAll() runs every tick; only re-send when the content or audience changed
            boolean contentChanged = !footerText.equals(lastFooterText);
            boolean audienceChanged = !viewers.equals(lastTabViewers);
            lastFooterText = footerText;
            lastTabViewers.clear();
            lastTabViewers.addAll(viewers);

            boolean scoreboardEnabled = plugin.getConfig().getBoolean("display.scoreboard-enabled", true);
            boolean scoreboardActive = scoreboardEnabled && state == RunState.RUNNING;

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (contentChanged || audienceChanged) {
                    Component header = ComponentUtil.parse(plugin.getConfig().getString("display.tab-header", "<gold><b>SharedChain</b></gold>"));
                    Component footer = Component.text("Timer: ", NamedTextColor.GRAY)
                            .append(Component.text(time, NamedTextColor.WHITE))
                            .append(Component.text(" | Total: ", NamedTextColor.GRAY))
                            .append(Component.text(totalTime, NamedTextColor.GOLD));
                    player.sendPlayerListHeaderAndFooter(header, footer);
                }

                // 3. Scoreboard (only for participants in running state, applied once per session)
                if (scoreboardActive && plugin.getRunManager().isParticipant(player)) {
                    if (scoreboardApplied.add(player.getUniqueId())) {
                        player.setScoreboard(scoreboard);
                    }
                } else {
                    scoreboardApplied.remove(player.getUniqueId());
                }
            }
        }

        refreshAttemptBossBar();
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
            sponsorTeam.addEntry(player.getName());
        }
    }

    public void clearSponsor() {
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
        String digits = toGlyphDigits(attempt);
        boolean singleDigit = attempt < 10;

        return (singleDigit ? PREFIX_SINGLE : PREFIX_DOUBLE)
                + ATTEMPT_LABEL
                + (singleDigit ? PAD_SINGLE : PAD_DOUBLE)
                + digits;
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
