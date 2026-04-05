package com.m13.egghunt.managers;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.models.EggTier;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class TierManager {

    private final EggHuntPlugin plugin;
    private final Map<String, EggTier> tiers = new LinkedHashMap<>();
    private int totalWeight = 0;

    public TierManager(EggHuntPlugin plugin) {
        this.plugin = plugin;
        loadTiers();
    }

    public void loadTiers() {
        tiers.clear();
        totalWeight = 0;
        var section = plugin.getConfig().getConfigurationSection("eggs");
        if (section == null) {
            plugin.getLogger().warning("No 'eggs' section in config!");
            return;
        }
        for (String key : section.getKeys(false)) {
            List<String> nexoIds = plugin.getConfig().getStringList("eggs." + key + ".nexo-ids");
            if (nexoIds.isEmpty()) {
                // Fallback: single nexo-id field
                String single = section.getString(key + ".nexo-id", key);
                nexoIds = List.of(single);
            }
            int weight = section.getInt(key + ".weight", 1);
            String name = EggHuntPlugin.colorize(
                    section.getString(key + ".name", "&f" + key));

            tiers.put(key, new EggTier(key, nexoIds, weight, name));
            totalWeight += weight;
        }
        plugin.getLogger().info("Loaded " + tiers.size() + " egg tiers (" + totalWeight + " total weight)");
    }

    public EggTier randomTier() {
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cum = 0;
        for (EggTier tier : tiers.values()) {
            cum += tier.weight();
            if (roll < cum) return tier;
        }
        return tiers.values().iterator().next();
    }

    public EggTier getTier(String key) { return tiers.get(key); }
    public List<String> tierKeys() { return new ArrayList<>(tiers.keySet()); }
    public Collection<EggTier> allTiers() { return Collections.unmodifiableCollection(tiers.values()); }
}
