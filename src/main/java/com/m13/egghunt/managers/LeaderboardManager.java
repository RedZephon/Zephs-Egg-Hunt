package com.m13.egghunt.managers;

import com.m13.egghunt.EggHuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardManager {

    private final EggHuntPlugin plugin;
    private final File dataFile;
    private final Map<UUID, Integer> eventCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> randomCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private int totalEventCollected = 0;
    private int totalRandomCollected = 0;

    public LeaderboardManager(EggHuntPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "leaderboard.yml");
        loadData();
    }

    /**
     * Record an egg collection.
     * @param isEvent true for admin/event eggs, false for player/random eggs
     */
    public void recordCollection(Player player, boolean isEvent) {
        UUID uuid = player.getUniqueId();
        cooldowns.put(uuid, System.currentTimeMillis());
        if (isEvent) {
            eventCounts.merge(uuid, 1, Integer::sum);
            totalEventCollected++;
        } else {
            randomCounts.merge(uuid, 1, Integer::sum);
            totalRandomCollected++;
        }
    }

    // ---- Getters ----

    public int getEventCount(UUID playerId) {
        return eventCounts.getOrDefault(playerId, 0);
    }

    public int getRandomCount(UUID playerId) {
        return randomCounts.getOrDefault(playerId, 0);
    }

    public int getCount(UUID playerId) {
        return getEventCount(playerId) + getRandomCount(playerId);
    }

    public int getTotalCollected() {
        return totalEventCollected + totalRandomCollected;
    }

    public int getTotalEventCollected() { return totalEventCollected; }
    public int getTotalRandomCollected() { return totalRandomCollected; }

    public boolean isOnCooldown(Player player) {
        long cooldownMs = plugin.getConfig().getInt("limits.cooldown-seconds", 3) * 1000L;
        Long last = cooldowns.get(player.getUniqueId());
        return last != null && (System.currentTimeMillis() - last) < cooldownMs;
    }

    public boolean hasReachedLimit(Player player) {
        int max = plugin.getConfig().getInt("limits.max-per-player", -1);
        if (max <= 0) return false;
        return getCount(player.getUniqueId()) >= max;
    }

    public int uniquePlayerCount() {
        Set<UUID> all = new HashSet<>();
        all.addAll(eventCounts.keySet());
        all.addAll(randomCounts.keySet());
        return all.size();
    }

    /**
     * Get a player's rank (1-indexed) by total eggs. Returns 0 if no collections.
     */
    public int getRank(UUID playerId) {
        int playerTotal = getCount(playerId);
        if (playerTotal == 0) return 0;
        int rank = 1;
        Set<UUID> allPlayers = new HashSet<>();
        allPlayers.addAll(eventCounts.keySet());
        allPlayers.addAll(randomCounts.keySet());
        for (UUID uuid : allPlayers) {
            if (getCount(uuid) > playerTotal) rank++;
        }
        return rank;
    }

    public List<Map.Entry<String, Integer>> getTopCollectors(int n) {
        Map<UUID, Integer> totals = new HashMap<>();
        for (UUID uuid : eventCounts.keySet()) totals.merge(uuid, eventCounts.get(uuid), Integer::sum);
        for (UUID uuid : randomCounts.keySet()) totals.merge(uuid, randomCounts.get(uuid), Integer::sum);

        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (var e : totals.entrySet()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            String name = op.getName() != null ? op.getName() : e.getKey().toString().substring(0, 8);
            entries.add(Map.entry(name, e.getValue()));
        }
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        return entries.subList(0, Math.min(n, entries.size()));
    }

    public void reset() {
        eventCounts.clear();
        randomCounts.clear();
        cooldowns.clear();
        totalEventCollected = 0;
        totalRandomCollected = 0;
        saveData();
    }

    // ---- Persistence ----

    public void saveData() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("format-version", 2);
        data.set("total-event-collected", totalEventCollected);
        data.set("total-random-collected", totalRandomCollected);

        Set<UUID> allPlayers = new HashSet<>();
        allPlayers.addAll(eventCounts.keySet());
        allPlayers.addAll(randomCounts.keySet());

        for (UUID uuid : allPlayers) {
            String key = "players." + uuid.toString();
            int ev = getEventCount(uuid);
            int ra = getRandomCount(uuid);
            if (ev > 0) data.set(key + ".event", ev);
            if (ra > 0) data.set(key + ".random", ra);
        }
        try { data.save(dataFile); }
        catch (IOException ex) { plugin.getLogger().severe("Failed to save leaderboard: " + ex.getMessage()); }
    }

    public void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);

        int version = data.getInt("format-version", 1);

        if (version >= 2) {
            // New split format
            totalEventCollected = data.getInt("total-event-collected", 0);
            totalRandomCollected = data.getInt("total-random-collected", 0);
            var section = data.getConfigurationSection("players");
            if (section == null) return;
            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int ev = data.getInt("players." + key + ".event", 0);
                    int ra = data.getInt("players." + key + ".random", 0);
                    if (ev > 0) eventCounts.put(uuid, ev);
                    if (ra > 0) randomCounts.put(uuid, ra);
                } catch (Exception ignored) {}
            }
        } else {
            // Old unified format — migrate: treat all old counts as event eggs
            int oldTotal = data.getInt("total-collected", 0);
            totalEventCollected = oldTotal;
            totalRandomCollected = 0;
            var section = data.getConfigurationSection("players");
            if (section == null) return;
            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int count = data.getInt("players." + key);
                    if (count > 0) eventCounts.put(uuid, count);
                } catch (Exception ignored) {}
            }
            plugin.getLogger().info("Migrated leaderboard from v1 to v2 format ("
                    + eventCounts.size() + " players).");
            saveData(); // Persist in new format
        }
    }
}
