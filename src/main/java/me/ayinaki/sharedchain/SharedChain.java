package me.ayinaki.sharedchain;

import me.ayinaki.sharedchain.chain.ChainService;
import me.ayinaki.sharedchain.command.SharedChainCommand;
import me.ayinaki.sharedchain.font.FontImageService;
import me.ayinaki.sharedchain.death.DeathInfo;
import me.ayinaki.sharedchain.death.DeathTrackerService;
import me.ayinaki.sharedchain.listener.RedirectionListener;
import me.ayinaki.sharedchain.run.RunState;
import me.ayinaki.sharedchain.stats.RunStatsService;
import me.ayinaki.sharedchain.display.UserInterfaceService;
import me.ayinaki.sharedchain.finish.RunFinishDetector;
import me.ayinaki.sharedchain.health.SharedHealthService;
import me.ayinaki.sharedchain.listener.DeathListener;
import me.ayinaki.sharedchain.listener.RunListener;
import me.ayinaki.sharedchain.listener.SharedHealthListener;
import me.ayinaki.sharedchain.lobby.LobbyService;
import me.ayinaki.sharedchain.reset.WorldResetService;
import me.ayinaki.sharedchain.run.RunManager;
import me.ayinaki.sharedchain.tag.TagService;
import me.ayinaki.sharedchain.timer.SpeedrunTimerService;
import me.ayinaki.sharedchain.util.ComponentUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Random;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SharedChain extends JavaPlugin {

    public static final NamespacedKey REAL_OVERWORLD_KEY = NamespacedKey.minecraft("overworld");
    private final NamespacedKey fakeOverworldKey = new NamespacedKey(this, "overworld");
    private final NamespacedKey limboWorldKey = new NamespacedKey(this, "limbo");
    private final Random random = new Random();
    private final Object statsLock = new Object();

    private RunManager runManager;
    private SharedHealthService healthService;
    private SpeedrunTimerService timerService;
    private DeathTrackerService deathTrackerService;
    private UserInterfaceService uiService;
    private RunFinishDetector finishDetector;
    private WorldResetService resetService;
    private RunStatsService runStatsService;
    private LobbyService lobbyService;
    private ComponentUtil componentUtil;
    private ChainService chainService;
    private FontImageService fontImageService;
    private TagService tagService;
    private File statsFile;
    private YamlConfiguration statsConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadStats();

        componentUtil = new ComponentUtil(this);
        lobbyService = new LobbyService(this);
        runManager = new RunManager(this);
        healthService = new SharedHealthService(this);
        timerService = new SpeedrunTimerService(this);
        deathTrackerService = new DeathTrackerService(this);
        tagService = new TagService(this);
        uiService = new UserInterfaceService(this, tagService);
        finishDetector = new RunFinishDetector(this);
        resetService = new WorldResetService(this);
        runStatsService = new RunStatsService();
        chainService = new ChainService(this);
        fontImageService = new FontImageService(this);
        ComponentUtil.setFontImages(fontImageService);

        // Validate and create worlds
        validateWorlds();

        runManager.restoreAfterEnable();
        resumeActiveState();
        uiService.refreshAttemptBossBar();

        // Keep the display loop running in every state (lobby included) so the tab
        // list header/footer and team colors stay fresh even before a run starts.
        timerService.start();

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new RunListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SharedHealthListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RedirectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(finishDetector, this);
        Bukkit.getPluginManager().registerEvents(fontImageService, this);
        Bukkit.getPluginManager().registerEvents(chainService, this);

        // Generate the font-image resource pack and start the pack server.
        fontImageService.load();

        // The below-name death counter needs the deaths icon, which only exists
        // after the font images have been scanned.
        uiService.refreshDeathDisplayName();

        // Register command
        SharedChainCommand command = new SharedChainCommand(this);
        getCommand("sharedchain").setExecutor(command);
        getCommand("sharedchain").setTabCompleter(command);

        getComponentLogger().info("SharedChain enabled!");
    }

    @Override
    public void onDisable() {
        if (runManager != null) runManager.pauseTimingForShutdown();
        if (timerService != null) timerService.shutdown();
        if (healthService != null) healthService.shutdown();
        if (chainService != null) chainService.deactivate();
        if (uiService != null) uiService.shutdown();
        if (lobbyService != null) lobbyService.shutdown();
        if (fontImageService != null) fontImageService.shutdown();
        saveStatsSync();
        getComponentLogger().info("SharedChain disabled!");
    }

    /**
     * Restores the active gameplay state after a server restart. Everything below is
     * in-memory only and dies with the process: chain order/anchors, the lobby setup
     * (border, time freeze, [Start] button) and the countdown task.
     */
    private void resumeActiveState() {
        RunState state = runManager.getState();
        World challenge = getFakeOverworld();

        // Leftover anchor entities from an unclean shutdown would otherwise linger
        // invisible in the world and pile up across restarts.
        chainService.purgeStaleAnchors();

        switch (state) {
            case IDLE, STARTING -> {
                // Re-run the lobby setup so the border, time freeze, and [Start] button return.
                lobbyService.setupLobby(challenge);
                // A crash/force-kill can leave stale player data on disk (pre-reset items or
                // gamemode). The lobby invariant is a clean slate, so reset everyone back to
                // it now that we're resuming into the lobby.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (runManager.isWorldEnabled(player.getWorld())) {
                        runManager.resetPlayer(player);
                    }
                }
            }
            case RUNNING -> {
                // Timer already restored; re-apply challenge rules and let chains reform
                // as players reconnect (RunListener chains them on join).
                runManager.applyRunRules();
                // The world border is persisted with the world. If the server restarted
                // shortly after the run started (before the next autosave), the on-disk
                // border can still be the small lobby size - which would trap the team
                // in an invisible lobby zone mid-run. Restore the active size and make
                // sure the day/night cycle is running, mirroring finishCountdown.
                if (challenge != null) {
                    challenge.getWorldBorder().setSize(
                            getConfig().getDouble("lobby.active-border-size", 100000.0));
                    challenge.setGameRule(GameRules.ADVANCE_TIME, true);
                }
                chainService.activateFor(Bukkit.getOnlinePlayers());
            }
            case RESETTING -> {
                // An interrupted reset: the world is whatever it is, so return to a usable
                // lobby instead of leaving players stuck in limbo forever.
                runManager.setState(RunState.IDLE);
                lobbyService.setupLobby(challenge);
            }
            default -> {
                // WIPED / FINISHED: join handler keeps players in spectator; an operator
                // still needs to reset to start the next run.
            }
        }
    }

    public RunManager getRunManager() {
        return runManager;
    }

    public SharedHealthService getHealthService() {
        return healthService;
    }

    public SpeedrunTimerService getTimerService() {
        return timerService;
    }

    public DeathTrackerService getDeathTrackerService() {
        return deathTrackerService;
    }

    public UserInterfaceService getUIService() {
        return uiService;
    }

    public RunFinishDetector getFinishDetector() {
        return finishDetector;
    }

    public WorldResetService getResetService() {
        return resetService;
    }

    public RunStatsService getRunStatsService() {
        return runStatsService;
    }

    public LobbyService getLobbyService() {
        return lobbyService;
    }

    public ComponentUtil getComponentUtil() {
        return componentUtil;
    }

    public ChainService getChainService() {
        return chainService;
    }

    public FontImageService getFontImageService() {
        return fontImageService;
    }

    public TagService getTagService() {
        return tagService;
    }

    public YamlConfiguration getStatsConfig() {
        return statsConfig;
    }

    public NamespacedKey getFakeOverworldKey() {
        return fakeOverworldKey;
    }

    public NamespacedKey getLimboWorldKey() {
        return limboWorldKey;
    }

    public World getFakeOverworld() {
        World world = Bukkit.getWorld(fakeOverworldKey);
        return world != null ? world : createFakeOverworld();
    }

    private World createFakeOverworld() {
        World realOverworld = Bukkit.getWorld(REAL_OVERWORLD_KEY);
        if (realOverworld == null) throw new IllegalStateException("Real overworld not found!");

        long seed = random.nextLong();
        getComponentLogger().info("Creating fake overworld with seed: " + seed);
        WorldCreator creator = new WorldCreator(fakeOverworldKey)
                .copy(realOverworld)
                .seed(seed);
        
        return creator.createWorld();
    }

    public void saveStats() {
        saveStats(true);
    }

    public void saveStatsSync() {
        saveStats(false);
    }

    public void saveStats(boolean async) {
        if (statsConfig == null || statsFile == null) return;

        final String yamlData;
        synchronized (statsLock) {
            yamlData = statsConfig.saveToString();
        }

        Runnable saveTask = () -> {
            synchronized (statsLock) {
                File tmpFile = new File(statsFile.getParentFile(), statsFile.getName() + ".tmp");
                try {
                    Files.writeString(tmpFile.toPath(), yamlData, StandardCharsets.UTF_8);
                    try {
                        Files.move(tmpFile.toPath(), statsFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        Files.move(tmpFile.toPath(), statsFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    getComponentLogger().warn("Failed to save stats.yml atomically", e);
                }
            }
        };

        if (async && isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, saveTask);
        } else {
            saveTask.run();
        }
    }

    private void validateWorlds() {
        // Ensure limbo world exists
        resetService.getHoldingWorldService().getOrCreateHoldingWorld();
        // Ensure fake overworld exists
        getFakeOverworld();
    }

    public synchronized void handleRunWipe(DeathInfo info) {
        if (runManager.getState() != RunState.RUNNING) return;

        runManager.wipe(info);
        deathTrackerService.incrementDeaths(info.deadPlayer());

        // Dramatic effect: Configurable Sound
        if (getConfig().getBoolean("death-tracking.dramatic-wipe", true)) {
            String soundKey = getConfig().getString("death-tracking.wipe-sound", "entity.illusioner.prepare_blindness");
            float pitch = (float) getConfig().getDouble("death-tracking.wipe-sound-pitch", 1.0);
            
            // This works for both vanilla keys and custom resource pack keys (e.g. "my:sound")
            net.kyori.adventure.key.Key key = net.kyori.adventure.key.Key.key(soundKey);
            net.kyori.adventure.sound.Sound wipeSound = net.kyori.adventure.sound.Sound.sound(
                    key, 
                    net.kyori.adventure.sound.Sound.Source.MASTER, 
                    1.5f, pitch
            );
            Bukkit.getServer().playSound(wipeSound);
        }

        String messageStr = getConfig().getString("messages.run-wiped", "<red><b>WIPE!</b> <player> died due to <cause>. Total deaths: <deaths>.</red>");
        Component wipeMessage = ComponentUtil.parse(messageStr,
                Placeholder.parsed("player", info.deadPlayer().getName()),
                Placeholder.component("cause", info.formattedDescription()),
                Placeholder.parsed("deaths", String.valueOf(deathTrackerService.getDeaths(info.deadPlayer())))
        );
        Bukkit.broadcast(wipeMessage);

        // Display Post-Run Summary
        displayRunSummary();

        Map<UUID, Location> spectatorLocations = runManager.getParticipants().stream()
                .map(Bukkit::getPlayer)
                .filter(player -> player != null && player.isOnline())
                .collect(Collectors.toMap(Player::getUniqueId, player -> player.getLocation().clone()));

        for (UUID uuid : runManager.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            dropPlayerInventoryLikeVanilla(player);
            player.setGameMode(GameMode.SPECTATOR);
            
            if (!player.getUniqueId().equals(info.deadPlayer().getUniqueId())) {
                Location playerLocation = spectatorLocations.get(uuid);
                if (playerLocation != null) {
                    player.teleport(playerLocation);
                }
            }
        }
        chainService.deactivate();

        Component plainPrompt = Component.text("Run over: ask an operator to reset.", NamedTextColor.YELLOW);
        Component opPrompt = Component.text("Run over: ", NamedTextColor.YELLOW)
                .append(Component.text("[Reset]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/sharedchain reset")))
                .append(Component.text(" (OP only)", NamedTextColor.GRAY));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp()) {
                online.sendMessage(opPrompt);
            } else {
                online.sendMessage(plainPrompt);
            }
        }
    }

    private void dropPlayerInventoryLikeVanilla(Player player) {
        // If this player is already in a real death event, vanilla handles drops.
        if (player.isDead()) return;

        Boolean keepInventory = player.getWorld().getGameRuleValue(GameRules.KEEP_INVENTORY);
        if (Boolean.TRUE.equals(keepInventory)) return;

        var inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
        }

        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
        player.updateInventory();
    }

    private void loadStats() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getComponentLogger().warn("Could not create plugin data folder for stats persistence.");
        }
        statsFile = new File(getDataFolder(), "stats.yml");
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    private void displayRunSummary() {
        UUID topSponsor = runStatsService.getTopSponsor();
        UUID topSponge = runStatsService.getTopSponge();

        if (topSponsor == null && topSponge == null) return;

        Component summary = Component.text("\n--- Run Summary ---", NamedTextColor.GOLD);
        
        if (topSponsor != null) {
            String name = Bukkit.getOfflinePlayer(topSponsor).getName();
            double amount = runStatsService.getHealedAmount(topSponsor);
            if (amount > 0) {
                summary = summary.append(Component.text("\n❇ Support: ", NamedTextColor.GREEN))
                        .append(Component.text(name != null ? name : "Unknown", NamedTextColor.WHITE))
                        .append(Component.text(" (Healed " + String.format("%.1f", amount) + " HP)", NamedTextColor.GRAY));
            }
        }

        if (topSponge != null) {
            String name = Bukkit.getOfflinePlayer(topSponge).getName();
            double amount = runStatsService.getDamageAmount(topSponge);
            if (amount > 0) {
                summary = summary.append(Component.text("\n🛡 Victim: ", NamedTextColor.RED))
                        .append(Component.text(name != null ? name : "Unknown", NamedTextColor.WHITE))
                        .append(Component.text(" (Took " + String.format("%.1f", amount) + " damage)", NamedTextColor.GRAY));
            }
        }

        summary = summary.append(Component.text("\n------------------\n", NamedTextColor.GOLD));
        Bukkit.broadcast(summary);
    }
}
