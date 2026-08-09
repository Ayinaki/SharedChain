package me.ayinaki.sharedchain.timer;

import me.ayinaki.sharedchain.SharedChain;
import me.ayinaki.sharedchain.run.RunState;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class SpeedrunTimerService {
    private final SharedChain plugin;
    private BukkitTask timerTask;

    public SpeedrunTimerService(SharedChain plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (timerTask != null) return;
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void shutdown() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void tick() {
        // Update displays in every state so the tab list (header/footer/team colors)
        // stays live in the lobby too. Action-bar and timer updates are internally
        // guarded to active run states.
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
