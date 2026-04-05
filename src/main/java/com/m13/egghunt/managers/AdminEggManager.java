package com.m13.egghunt.managers;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.models.AdminEgg;
import com.m13.egghunt.models.EggTier;
import com.nexomc.nexo.api.NexoFurniture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mode 1: Admin-placed Nexo furniture eggs.
 * Single entity per egg. ProtocolLib handles per-player visual swapping.
 */
public class AdminEggManager {

    private final EggHuntPlugin plugin;
    private final TierManager tierManager;
    private final File dataFile;

    // entityUUID -> AdminEgg
    private final Map<UUID, AdminEgg> eggs = new ConcurrentHashMap<>();
    // entityId (int) -> AdminEgg for fast packet lookups
    private final Map<Integer, AdminEgg> entityIdIndex = new ConcurrentHashMap<>();

    // Wand
    private final Map<UUID, String> wandSelection = new HashMap<>();
    public static final NamespacedKey WAND_KEY = new NamespacedKey("egghunt", "admin_wand");

    public AdminEggManager(EggHuntPlugin plugin, TierManager tierManager) {
        this.plugin = plugin;
        this.tierManager = tierManager;
        this.dataFile = new File(plugin.getDataFolder(), "admin_eggs.yml");
        loadData();
    }

    // ================================================================
    // Wand
    // ================================================================

    public void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(EggHuntPlugin.colorize("&a&lEgg Placement Wand"));
        meta.setLore(List.of(
                EggHuntPlugin.colorize("&7Right-click: place egg"),
                EggHuntPlugin.colorize("&7Left-click: cycle tier"),
                EggHuntPlugin.colorize("&7Sneak + right-click: remove egg")
        ));
        meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BOOLEAN, true);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        wandSelection.put(player.getUniqueId(), tierManager.tierKeys().getFirst());
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BOOLEAN);
    }

    public String getSelectedTier(Player player) {
        return wandSelection.getOrDefault(player.getUniqueId(), tierManager.tierKeys().getFirst());
    }

    public String cycleTier(Player player) {
        List<String> keys = new ArrayList<>(tierManager.tierKeys());
        keys.add("random");
        String current = getSelectedTier(player);
        int idx = keys.indexOf(current);
        String next = keys.get((idx + 1) % keys.size());
        wandSelection.put(player.getUniqueId(), next);
        return next;
    }

    public String tierDisplayName(String tierKey) {
        if (tierKey.equals("random")) return EggHuntPlugin.colorize("&b&lRandom");
        EggTier tier = tierManager.getTier(tierKey);
        return tier != null ? tier.displayName() : tierKey;
    }

    // ================================================================
    // Placement -- single entity
    // ================================================================

    public boolean placeEgg(Location location, String tierKey, float yaw) {
        if (tierKey.equals("random")) {
            tierKey = tierManager.randomTier().key();
        }

        EggTier tier = tierManager.getTier(tierKey);
        if (tier == null) return false;

        String nexoId = tier.randomNexoId();

        try {
            Entity entity = NexoFurniture.place(nexoId, location, yaw, BlockFace.UP);
            if (entity == null) {
                plugin.getLogger().warning("Nexo returned null for: " + nexoId);
                return false;
            }
            entity.setInvulnerable(true);
            entity.setPersistent(true);

            AdminEgg egg = new AdminEgg(
                    entity.getUniqueId(), entity.getEntityId(),
                    tierKey, nexoId, location, System.currentTimeMillis()
            );
            eggs.put(entity.getUniqueId(), egg);
            entityIdIndex.put(entity.getEntityId(), egg);

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to place admin egg: " + e.getMessage());
            return false;
        }
    }

    // ================================================================
    // Claiming
    // ================================================================

    public void claimEgg(Player player, AdminEgg egg) {
        egg.markClaimed(player.getUniqueId());
        // The EggVisualManager packet listener will handle the visual swap
        // on the next metadata packet. Force a resend now:
        plugin.getEggVisualManager().sendClaimedVisual(player, egg);
    }

    // ================================================================
    // Lookup
    // ================================================================

    public AdminEgg getByEntityUuid(UUID uuid) { return eggs.get(uuid); }
    public AdminEgg getByEntityId(int entityId) { return entityIdIndex.get(entityId); }
    public boolean isAdminEgg(UUID uuid) { return eggs.containsKey(uuid); }
    public boolean isAdminEggId(int entityId) { return entityIdIndex.containsKey(entityId); }
    public int count() { return eggs.size(); }
    public Collection<AdminEgg> allEggs() { return Collections.unmodifiableCollection(eggs.values()); }

    // ================================================================
    // Removal
    // ================================================================

    public boolean removeNearest(Location location) {
        AdminEgg nearest = null;
        double nearestDist = 9.0;
        for (AdminEgg egg : eggs.values()) {
            if (!egg.location().getWorld().equals(location.getWorld())) continue;
            double dist = egg.location().distanceSquared(location);
            if (dist < nearestDist) { nearestDist = dist; nearest = egg; }
        }
        if (nearest == null) return false;
        removeEgg(nearest);
        return true;
    }

    private void removeEgg(AdminEgg egg) {
        eggs.remove(egg.entityUuid());
        entityIdIndex.remove(egg.entityId());
        World world = egg.location().getWorld();
        if (world != null) {
            Entity entity = world.getEntity(egg.entityUuid());
            if (entity != null) NexoFurniture.remove(entity, null);
        }
    }

    public int clearAll() {
        int count = eggs.size();
        for (AdminEgg egg : new ArrayList<>(eggs.values())) removeEgg(egg);
        return count;
    }

    // ================================================================
    // Persistence
    // ================================================================

    public void saveData() {
        YamlConfiguration data = new YamlConfiguration();
        int i = 0;
        for (AdminEgg egg : eggs.values()) {
            String p = "eggs." + i;
            data.set(p + ".uuid", egg.entityUuid().toString());
            data.set(p + ".entity-id", egg.entityId());
            data.set(p + ".tier", egg.tierKey());
            data.set(p + ".nexo-id", egg.nexoId());
            data.set(p + ".world", egg.location().getWorld().getName());
            data.set(p + ".x", egg.location().getX());
            data.set(p + ".y", egg.location().getY());
            data.set(p + ".z", egg.location().getZ());
            data.set(p + ".placed-at", egg.placedAt());
            data.set(p + ".claimed-by", egg.claimedBy().stream()
                    .map(UUID::toString).toList());
            i++;
        }
        data.set("count", i);
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("Failed to save admin eggs: " + e.getMessage()); }
    }

    public void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        var section = data.getConfigurationSection("eggs");
        if (section == null) return;

        // Detect format: old has "unclaimed-uuid", new has "uuid"
        String firstKey = section.getKeys(false).iterator().next();
        boolean isOldFormat = data.contains("eggs." + firstKey + ".unclaimed-uuid");

        if (isOldFormat) {
            loadOldFormat(data, section);
        } else {
            loadNewFormat(data, section);
        }
    }

    /**
     * Load new single-entity format.
     */
    private void loadNewFormat(YamlConfiguration data, org.bukkit.configuration.ConfigurationSection section) {
        int loaded = 0;
        for (String key : section.getKeys(false)) {
            try {
                String p = "eggs." + key;
                UUID uuid = UUID.fromString(data.getString(p + ".uuid"));
                String tier = data.getString(p + ".tier");
                String nexoId = data.getString(p + ".nexo-id", "");
                World world = Bukkit.getWorld(data.getString(p + ".world"));
                if (world == null) continue;
                double x = data.getDouble(p + ".x");
                double y = data.getDouble(p + ".y");
                double z = data.getDouble(p + ".z");
                long placedAt = data.getLong(p + ".placed-at", System.currentTimeMillis());

                Set<UUID> claimers = new HashSet<>();
                for (String s : data.getStringList(p + ".claimed-by")) {
                    try { claimers.add(UUID.fromString(s)); } catch (Exception ignored) {}
                }

                Entity entity = world.getEntity(uuid);
                int entityId = entity != null ? entity.getEntityId() : data.getInt(p + ".entity-id", -1);

                Location loc = new Location(world, x, y, z);
                AdminEgg egg = new AdminEgg(uuid, entityId, tier, nexoId, loc, placedAt, claimers);
                eggs.put(uuid, egg);
                if (entityId > 0) entityIdIndex.put(entityId, egg);
                loaded++;
            } catch (Exception e) {
                plugin.getLogger().warning("Skipped bad admin egg: " + key);
            }
        }
        plugin.getLogger().info("Loaded " + loaded + " admin eggs.");
    }

    /**
     * Migrate old dual-entity format (unclaimed-uuid + claimed-uuid).
     * Keeps the unclaimed entity as the real egg, schedules removal
     * of the orphaned claimed overlay entities, backs up the old file,
     * and saves in the new format.
     */
    private void loadOldFormat(YamlConfiguration data, org.bukkit.configuration.ConfigurationSection section) {
        plugin.getLogger().warning("Detected old admin_eggs.yml format. Migrating...");

        // Back up old file
        File backup = new File(plugin.getDataFolder(), "admin_eggs.yml.v2-backup");
        try {
            java.nio.file.Files.copy(dataFile.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Old admin_eggs.yml backed up to admin_eggs.yml.v2-backup");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to back up old admin_eggs.yml: " + e.getMessage());
        }

        List<UUID> orphanedClaimedEntities = new ArrayList<>();
        int loaded = 0;

        for (String key : section.getKeys(false)) {
            try {
                String p = "eggs." + key;
                UUID unclaimedUuid = UUID.fromString(data.getString(p + ".unclaimed-uuid"));
                String tier = data.getString(p + ".tier");
                String nexoId = data.getString(p + ".nexo-id", "");
                World world = Bukkit.getWorld(data.getString(p + ".world"));
                if (world == null) continue;
                double x = data.getDouble(p + ".x");
                double y = data.getDouble(p + ".y");
                double z = data.getDouble(p + ".z");
                long placedAt = data.getLong(p + ".placed-at", System.currentTimeMillis());

                // Claimed players
                Set<UUID> claimers = new HashSet<>();
                for (String s : data.getStringList(p + ".claimed-by")) {
                    try { claimers.add(UUID.fromString(s)); } catch (Exception ignored) {}
                }

                // Track the old claimed entity for removal
                String claimedUuidStr = data.getString(p + ".claimed-uuid");
                if (claimedUuidStr != null) {
                    try { orphanedClaimedEntities.add(UUID.fromString(claimedUuidStr)); }
                    catch (Exception ignored) {}
                }

                // Use the unclaimed entity as the single entity going forward
                Entity entity = world.getEntity(unclaimedUuid);
                int entityId = entity != null ? entity.getEntityId() : -1;

                Location loc = new Location(world, x, y, z);
                AdminEgg egg = new AdminEgg(unclaimedUuid, entityId, tier, nexoId, loc, placedAt, claimers);
                eggs.put(unclaimedUuid, egg);
                if (entityId > 0) entityIdIndex.put(entityId, egg);
                loaded++;
            } catch (Exception e) {
                plugin.getLogger().warning("Skipped bad old-format egg: " + key);
            }
        }

        plugin.getLogger().info("Migrated " + loaded + " admin eggs to new format.");

        // Schedule removal of orphaned claimed entities (delayed so worlds are loaded)
        if (!orphanedClaimedEntities.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int removed = 0;
                for (UUID claimedUuid : orphanedClaimedEntities) {
                    for (World world : Bukkit.getWorlds()) {
                        Entity entity = world.getEntity(claimedUuid);
                        if (entity != null && entity.isValid()) {
                            NexoFurniture.remove(entity, null);
                            removed++;
                            break;
                        }
                    }
                }
                plugin.getLogger().info("Removed " + removed + " orphaned claimed overlay entities.");
            }, 10L);
        }

        // Save immediately in new format
        saveData();
        plugin.getLogger().info("admin_eggs.yml saved in new format.");
    }

    /**
     * Re-index entity IDs after world load (entity IDs change across restarts).
     */
    public void reindexEntityIds() {
        entityIdIndex.clear();
        int found = 0;
        int missing = 0;
        for (AdminEgg egg : eggs.values()) {
            World world = egg.location().getWorld();
            if (world == null) { missing++; continue; }
            Entity entity = world.getEntity(egg.entityUuid());
            if (entity != null) {
                AdminEgg updated = new AdminEgg(
                        egg.entityUuid(), entity.getEntityId(),
                        egg.tierKey(), egg.nexoId(), egg.location(),
                        egg.placedAt(), egg.claimedBy()
                );
                eggs.put(egg.entityUuid(), updated);
                entityIdIndex.put(entity.getEntityId(), updated);
                found++;
            } else {
                missing++;
            }
        }
        plugin.getLogger().info("Re-indexed " + found + " admin egg entity IDs"
                + (missing > 0 ? " (" + missing + " not yet loaded -- will index on chunk load)" : "")
                + ".");
    }

    /**
     * Try to reindex a single egg by UUID. Called when a chunk loads
     * that might contain an egg whose entity wasn't available at startup.
     * Returns true if the egg was found and indexed.
     */
    public boolean tryReindexEgg(UUID entityUuid) {
        AdminEgg egg = eggs.get(entityUuid);
        if (egg == null) return false;
        // Already indexed with a valid entity ID?
        if (egg.entityId() > 0 && entityIdIndex.containsKey(egg.entityId())) return false;

        World world = egg.location().getWorld();
        if (world == null) return false;
        Entity entity = world.getEntity(entityUuid);
        if (entity == null) return false;

        AdminEgg updated = new AdminEgg(
                egg.entityUuid(), entity.getEntityId(),
                egg.tierKey(), egg.nexoId(), egg.location(),
                egg.placedAt(), egg.claimedBy()
        );
        eggs.put(entityUuid, updated);
        entityIdIndex.put(entity.getEntityId(), updated);
        return true;
    }

    /**
     * Get all egg entity UUIDs that haven't been indexed yet
     * (entity ID <= 0 or not in the entityIdIndex).
     */
    public Collection<AdminEgg> getUnindexedEggs() {
        List<AdminEgg> unindexed = new ArrayList<>();
        for (AdminEgg egg : eggs.values()) {
            if (egg.entityId() <= 0 || !entityIdIndex.containsKey(egg.entityId())) {
                unindexed.add(egg);
            }
        }
        return unindexed;
    }
}
