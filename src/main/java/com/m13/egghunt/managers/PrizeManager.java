package com.m13.egghunt.managers;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.models.Prize;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Parses the flat reward string lists from config.
 * Each entry in a tier has equal chance. Add duplicates to weight things.
 */
public class PrizeManager {

    private final EggHuntPlugin plugin;
    private final Map<String, List<Prize>> prizeTables = new HashMap<>();

    public PrizeManager(EggHuntPlugin plugin) {
        this.plugin = plugin;
        loadPrizes();
    }

    public void loadPrizes() {
        prizeTables.clear();
        var section = plugin.getConfig().getConfigurationSection("rewards");
        if (section == null) {
            plugin.getLogger().warning("No 'rewards' section in config!");
            return;
        }

        for (String tierKey : section.getKeys(false)) {
            List<String> lines = plugin.getConfig().getStringList("rewards." + tierKey);
            List<Prize> prizes = new ArrayList<>();

            for (String line : lines) {
                try {
                    prizes.add(Prize.parse(line.trim()));
                } catch (Exception e) {
                    plugin.getLogger().warning("Bad reward in '" + tierKey + "': \""
                            + line + "\" -- " + e.getMessage());
                }
            }

            prizeTables.put(tierKey, prizes);
            plugin.getLogger().info("Loaded " + prizes.size() + " rewards for tier '" + tierKey + "'");
        }
    }

    /**
     * Pick a random prize from the tier. Equal chance per entry.
     */
    public Prize rollPrize(String tierKey) {
        List<Prize> prizes = prizeTables.get(tierKey);
        if (prizes == null || prizes.isEmpty()) return null;
        return prizes.get(ThreadLocalRandom.current().nextInt(prizes.size()));
    }
}
