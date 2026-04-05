package com.m13.egghunt.managers;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.models.EggTier;
import com.m13.egghunt.models.PlacedEgg;
import com.nexomc.nexo.api.NexoFurniture;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mode 2: Nexo furniture eggs that auto-spawn near players.
 * Single-use -- removed from the world on claim or after timeout.
 */
public class PlayerEggSpawner {

    private final EggHuntPlugin plugin;
    private final TierManager tierManager;
    private final Map<UUID, PlacedEgg> eggs = new ConcurrentHashMap<>();

    private BukkitTask spawnTask;
    private BukkitTask despawnTask;
    private BukkitTask particleTask;
    private BukkitTask soundTask;

    public PlayerEggSpawner(EggHuntPlugin plugin, TierManager tierManager) {
        this.plugin = plugin;
        this.tierManager = tierManager;
    }

    // ---- Lifecycle ----

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("player-spawning.enabled", true)) return;

        long spawnTicks = plugin.getConfig().getInt("player-spawning.interval-seconds", 300) * 20L;

        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::rollForAll, spawnTicks, spawnTicks);
        despawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkDespawns, 100L, 100L);

        // Optional ambient effects to help players spot nearby eggs
        int particleTicks = plugin.getConfig().getInt("player-spawning.particle-interval-ticks", 30);
        if (particleTicks > 0) {
            particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pulseParticles, particleTicks, particleTicks);
        }
        int soundTicks = plugin.getConfig().getInt("player-spawning.sound-interval-ticks", 100);
        if (soundTicks > 0) {
            soundTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pulseSound, soundTicks, soundTicks);
        }

        plugin.getLogger().info("Player egg spawner started.");
    }

    public void stop() {
        if (spawnTask != null) { spawnTask.cancel(); spawnTask = null; }
        if (despawnTask != null) { despawnTask.cancel(); despawnTask = null; }
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
        if (soundTask != null) { soundTask.cancel(); soundTask = null; }
    }

    // ---- Spawn Logic ----

    private void rollForAll() {
        if (!plugin.isEventActive()) return;
        int max = plugin.getConfig().getInt("player-spawning.max-active-eggs", 50);
        if (eggs.size() >= max) return;
        double chance = plugin.getConfig().getDouble("player-spawning.chance-per-player", 0.35);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("egghunt.collect")) continue;
            if (eggs.size() >= max) break;
            if (ThreadLocalRandom.current().nextDouble() < chance) spawnNear(player);
        }
    }

    /**
     * Force-spawn an egg near a player (for debug/testing).
     * Bypasses event-active check and chance roll.
     * Also ensures particle/sound tasks are running.
     */
    public void forceSpawnNear(Player player) {
        ensureEffectTasks();
        spawnNear(player);
    }

    /**
     * Start the ambient particle/sound tasks if they aren't already running.
     * Called by forceSpawnNear so debug-spawned eggs get effects
     * even when the full spawner hasn't been started.
     */
    private void ensureEffectTasks() {
        if (particleTask == null) {
            int particleTicks = plugin.getConfig().getInt("player-spawning.particle-interval-ticks", 30);
            if (particleTicks > 0) {
                particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pulseParticles, particleTicks, particleTicks);
            }
        }
        if (soundTask == null) {
            int soundTicks = plugin.getConfig().getInt("player-spawning.sound-interval-ticks", 100);
            if (soundTicks > 0) {
                soundTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pulseSound, soundTicks, soundTicks);
            }
        }
        if (despawnTask == null) {
            despawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkDespawns, 100L, 100L);
        }
    }

    private void spawnNear(Player player) {
        Location origin = player.getLocation();
        int minDist = plugin.getConfig().getInt("player-spawning.min-distance", 8);
        int maxDist = plugin.getConfig().getInt("player-spawning.max-distance", 30);

        Location spawnLoc = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(2 * Math.PI);
            int dist = ThreadLocalRandom.current().nextInt(minDist, maxDist + 1);
            int x = origin.getBlockX() + (int) (Math.cos(angle) * dist);
            int z = origin.getBlockZ() + (int) (Math.sin(angle) * dist);

            Block highest = origin.getWorld().getHighestBlockAt(x, z);
            if (!highest.getType().isSolid()) continue;
            if (!highest.getRelative(BlockFace.UP).getType().isAir()) continue;
            if (highest.isLiquid()) continue;
            spawnLoc = highest.getRelative(BlockFace.UP).getLocation().add(0.5, 0, 0.5);
            break;
        }
        if (spawnLoc == null) return;

        EggTier tier = tierManager.randomTier();
        String nexoId = tier.randomNexoId();
        float yaw = ThreadLocalRandom.current().nextFloat() * 360f;

        try {
            Entity entity = NexoFurniture.place(nexoId, spawnLoc, yaw, BlockFace.UP);
            if (entity == null) {
                plugin.getLogger().warning("Nexo returned null for player egg: " + nexoId);
                return;
            }
            entity.setInvulnerable(true);
            entity.setPersistent(true);

            eggs.put(entity.getUniqueId(), new PlacedEgg(
                    entity.getUniqueId(), tier.key(), spawnLoc, System.currentTimeMillis()));

            // Immediate spawn ding so the player knows something appeared
            playSpawnDing(player, spawnLoc);

            if (plugin.getConfig().getBoolean("player-spawning.notify-player", true)) {
                player.sendMessage(plugin.playerMsg("spawned-near"));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn player egg: " + e.getMessage());
        }
    }

    /**
     * Play a short ding at the egg's location so the player knows
     * something just appeared. Uses the same configured ambient sound
     * but at a slightly higher pitch for a distinct "spawn" feel.
     */
    private void playSpawnDing(Player player, Location eggLoc) {
        String sStr = plugin.getConfig().getString("player-spawning.ambient-sound", "block.amethyst_block.chime");
        if (sStr.equalsIgnoreCase("NONE")) return;
        Sound sound = resolveSound(sStr);
        if (sound == null) return;
        float vol = (float) plugin.getConfig().getDouble("player-spawning.sound-volume", 0.3);
        // Slightly higher pitch than the ambient pulse for a distinct "ding"
        player.playSound(eggLoc, sound, Math.max(vol, 0.5f), 1.8f);
    }

    // ---- Ambient Effects (optional hints) ----

    private void pulseParticles() {
        String pStr = plugin.getConfig().getString("player-spawning.particle", "ENCHANT");
        if (pStr.equalsIgnoreCase("NONE")) return;
        Particle particle;
        try { particle = Particle.valueOf(pStr.toUpperCase()); }
        catch (IllegalArgumentException e) { particle = Particle.ENCHANT; }

        for (PlacedEgg egg : eggs.values()) {
            Location loc = egg.location();
            if (loc == null || loc.getWorld() == null) continue;
            boolean nearby = false;
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) < 1024) { nearby = true; break; }
            }
            if (!nearby) continue;
            loc.getWorld().spawnParticle(particle, loc.clone().add(0, 0.6, 0), 5, 0.2, 0.2, 0.2, 0.02);
        }
    }

    private void pulseSound() {
        String sStr = plugin.getConfig().getString("player-spawning.ambient-sound", "block.amethyst_block.chime");
        if (sStr.equalsIgnoreCase("NONE")) return;
        Sound sound = resolveSound(sStr);
        if (sound == null) return;
        float vol = (float) plugin.getConfig().getDouble("player-spawning.sound-volume", 0.3);
        float pitch = (float) plugin.getConfig().getDouble("player-spawning.sound-pitch", 1.5);

        // Use configured max-distance for the hearing range (not a hardcoded 16 blocks)
        int maxDist = plugin.getConfig().getInt("player-spawning.max-distance", 30);
        double maxDistSq = (double) maxDist * maxDist;

        for (PlacedEgg egg : eggs.values()) {
            Location loc = egg.location();
            if (loc == null || loc.getWorld() == null) continue;
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) < maxDistSq) {
                    p.playSound(loc, sound, vol, pitch);
                }
            }
        }
    }

    /**
     * Resolve a sound name from config. Tries enum valueOf first,
     * then falls back to matching by namespaced key.
     */
    private Sound resolveSound(String name) {
        // Try enum name: "block.amethyst_block.chime" -> "BLOCK_AMETHYST_BLOCK_CHIME"
        try { return Sound.valueOf(name.toUpperCase().replace(".", "_")); }
        catch (IllegalArgumentException ignored) {}

        // Fallback: match by namespaced key (key uses dots, e.g. "block.amethyst_block.chime")
        String keyName = name.toLowerCase();
        for (Sound s : Sound.values()) {
            if (s.key().value().equalsIgnoreCase(keyName)) return s;
        }
        plugin.getLogger().warning("Unknown ambient sound: " + name);
        return null;
    }

    // ---- Despawn ----

    private void checkDespawns() {
        int despawnMs = plugin.getConfig().getInt("player-spawning.despawn-seconds", 180) * 1000;
        long now = System.currentTimeMillis();

        var it = eggs.entrySet().iterator();
        while (it.hasNext()) {
            PlacedEgg egg = it.next().getValue();
            if ((now - egg.placedAt()) > despawnMs) {
                Location loc = egg.location();
                if (loc != null && loc.getWorld() != null) {
                    Entity entity = loc.getWorld().getEntity(egg.entityUuid());
                    if (entity != null && entity.isValid()) {
                        loc.getWorld().spawnParticle(Particle.CLOUD,
                                entity.getLocation().add(0, 0.3, 0), 8, 0.2, 0.2, 0.2, 0.02);
                        NexoFurniture.remove(entity, null);
                    }
                    // Notify nearby players
                    String despawnMsg = plugin.playerMsg("egg-despawned");
                    for (Player p : loc.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(loc) < 1024) { // 32 blocks
                            p.sendMessage(despawnMsg);
                        }
                    }
                }
                it.remove();
            }
        }
    }

    // ---- Lookup ----

    public PlacedEgg getEgg(UUID uuid) { return eggs.get(uuid); }
    public PlacedEgg removeEgg(UUID uuid) { return eggs.remove(uuid); }
    public boolean isPlayerEgg(UUID uuid) { return eggs.containsKey(uuid); }
    public int count() { return eggs.size(); }

    public int clearAll() {
        int c = eggs.size();
        for (PlacedEgg egg : eggs.values()) {
            if (egg.location() != null && egg.location().getWorld() != null) {
                Entity e = egg.location().getWorld().getEntity(egg.entityUuid());
                if (e != null && e.isValid()) NexoFurniture.remove(e, null);
            }
        }
        eggs.clear();
        return c;
    }
}
