package me.ayinaki.ayinchallenge.timer;

import me.ayinaki.ayinchallenge.AyinChallenge;
import me.ayinaki.ayinchallenge.run.RunState;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class SpeedrunTimerService {
    private final AyinChallenge plugin;
    private BukkitTask timerTask;
    private final SimpleDateFormat dateFormat;

    public SpeedrunTimerService(AyinChallenge plugin) {
        this.plugin = plugin;
        this.dateFormat = new SimpleDateFormat(plugin.getConfig().getString("timer.format", "HH:mm:ss.SS"));
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void start() {
        if (timerTask != null) return;
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void stop() {
        // We don't actually stop the task if we want to keep the action bar updated
        // with the final paused time.
    }

    public void shutdown() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void tick() {
        RunState state = plugin.getRunManager().getState();
        // Allow ticking even when FINISHED to keep the action bar alive
        if (state != RunState.RUNNING && state != RunState.WIPED && state != RunState.FINISHED) return;
        
        // Update displays
        plugin.getUIService().updateAll();
    }

    public String getFormattedTime() {
        return formatTime(plugin.getRunManager().getElapsedTime());
    }

    public String getFormattedTotalTime() {
        return formatTime(plugin.getRunManager().getTotalElapsedTime());
    }

    public String formatTime(long elapsed) {
        long totalSeconds = elapsed / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.valueOf(seconds);
        }
    }
}
