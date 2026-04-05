package com.m13.egghunt;

import com.m13.egghunt.commands.EggHuntCommand;
import com.m13.egghunt.listeners.AdminWandListener;
import com.m13.egghunt.listeners.ChunkEggIndexListener;
import com.m13.egghunt.listeners.EggCollectListener;
import com.m13.egghunt.listeners.EntityTrackListener;
import com.m13.egghunt.listeners.PlayerJoinListener;
import com.m13.egghunt.listeners.WorldChangeListener;
import com.m13.egghunt.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class EggHuntPlugin extends JavaPlugin {

    private static EggHuntPlugin instance;
    private static final int CONFIG_VERSION = 3; // bumped for ProtocolLib changes

    private TierManager tierManager;
    private PrizeManager prizeManager;
    private AdminEggManager adminEggManager;
    private PlayerEggSpawner playerEggSpawner;
    private LeaderboardManager leaderboardManager;
    private EggVisualManager eggVisualManager;
    private ActionBarManager actionBarManager;

    @Override
    public void onEnable() {
        instance = this;
        migrateConfig();

        tierManager = new TierManager(this);
        prizeManager = new PrizeManager(this);
        leaderboardManager = new LeaderboardManager(this);
        adminEggManager = new AdminEggManager(this, tierManager);
        playerEggSpawner = new PlayerEggSpawner(this, tierManager);

        // ProtocolLib visual manager -- must init after adminEggManager
        eggVisualManager = new EggVisualManager(this);

        // Re-index entity IDs after world load (IDs change across restarts).
        // 100 ticks (5s) gives worlds more time to load nearby chunks.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            adminEggManager.reindexEntityIds();
        }, 100L);

        // Register listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(new EggCollectListener(
                this, tierManager, prizeManager,
                adminEggManager, playerEggSpawner, leaderboardManager), this);
        pm.registerEvents(new AdminWandListener(this, adminEggManager), this);
        pm.registerEvents(new PlayerJoinListener(this), this);
        pm.registerEvents(new EntityTrackListener(this, adminEggManager), this);
        pm.registerEvents(new ChunkEggIndexListener(this, adminEggManager), this);
        pm.registerEvents(new WorldChangeListener(this), this);

        // Register command
        var cmd = getCommand("egghunt");
        if (cmd != null) {
            var handler = new EggHuntCommand(
                    this, tierManager, adminEggManager, playerEggSpawner, leaderboardManager);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        // Action bar manager
        actionBarManager = new ActionBarManager(this);

        // Resume spawner and action bar if event was active
        if (isEventActive()) {
            if (getConfig().getBoolean("player-spawning.enabled", true)) {
                playerEggSpawner.start();
            }
            actionBarManager.start();
        }

        // PlaceholderAPI hook (optional)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.m13.egghunt.hooks.EggHuntPlaceholders(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        getLogger().info("EggHunt enabled -- ProtocolLib visual swapping active");
    }

    @Override
    public void onDisable() {
        if (actionBarManager != null) actionBarManager.stop();
        if (playerEggSpawner != null) playerEggSpawner.stop();
        if (eggVisualManager != null) eggVisualManager.shutdown();
        if (adminEggManager != null) adminEggManager.saveData();
        if (leaderboardManager != null) leaderboardManager.saveData();
        getLogger().info("EggHunt disabled.");
    }

    // ---- Config Migration ----

    private void migrateConfig() {
        saveDefaultConfig();
        int fileVersion = getConfig().getInt("config-version", -1);
        if (fileVersion == CONFIG_VERSION) return;

        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.exists()) {
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String backupName = "config-v" + (fileVersion == -1 ? "old" : fileVersion)
                    + "-" + timestamp + ".yml.bak";
            File backup = new File(getDataFolder(), backupName);
            if (configFile.renameTo(backup)) {
                getLogger().warning("Config outdated (v" + fileVersion + " -> v" + CONFIG_VERSION
                        + "). Backed up to " + backupName);
            } else {
                getLogger().severe("Failed to back up old config!");
                configFile.delete();
            }
        }
        saveDefaultConfig();
        reloadConfig();
        getLogger().info("Fresh config.yml (v" + CONFIG_VERSION + ") written.");
    }

    // ---- Convenience ----

    public static EggHuntPlugin getInstance() { return instance; }

    public boolean isEventActive() { return getConfig().getBoolean("event-active", false); }

    public void setEventActive(boolean active) {
        getConfig().set("event-active", active);
        saveConfig();
    }

    /** Admin/command message with [EggHunt] prefix */
    public String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "");
        String raw = getConfig().getString("messages." + key, "&cMissing: " + key);
        return colorize(prefix + raw);
    }

    /** Player-facing message with player-prefix (default: no prefix) */
    public String playerMsg(String key) {
        String prefix = getConfig().getString("messages.player-prefix", "");
        String raw = getConfig().getString("messages." + key, "&cMissing: " + key);
        return colorize(prefix + raw);
    }

    public String msgRaw(String key) {
        return colorize(getConfig().getString("messages." + key, "&cMissing: " + key));
    }

    public static String colorize(String text) {
        return text == null ? "" : text.replace('&', '\u00A7');
    }

    // ---- Getters ----

    public TierManager getTierManager() { return tierManager; }
    public PrizeManager getPrizeManager() { return prizeManager; }
    public AdminEggManager getAdminEggManager() { return adminEggManager; }
    public PlayerEggSpawner getPlayerEggSpawner() { return playerEggSpawner; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public EggVisualManager getEggVisualManager() { return eggVisualManager; }
    public ActionBarManager getActionBarManager() { return actionBarManager; }
}
