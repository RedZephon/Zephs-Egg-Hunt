package com.m13.egghunt.listeners;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.managers.AdminEggManager;
import com.m13.egghunt.models.AdminEgg;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * When a chunk loads its entities, check if any unindexed admin eggs
 * live in that chunk and reindex them. This catches eggs in chunks
 * that weren't loaded during the initial reindex at startup.
 */
public class ChunkEggIndexListener implements Listener {

    private final EggHuntPlugin plugin;
    private final AdminEggManager adminManager;

    public ChunkEggIndexListener(EggHuntPlugin plugin, AdminEggManager adminManager) {
        this.plugin = plugin;
        this.adminManager = adminManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        Chunk chunk = event.getChunk();

        for (AdminEgg egg : adminManager.getUnindexedEggs()) {
            // Quick check: is this egg in the chunk that just loaded?
            if (egg.location().getWorld() != chunk.getWorld()) continue;
            int eggChunkX = egg.location().getBlockX() >> 4;
            int eggChunkZ = egg.location().getBlockZ() >> 4;
            if (eggChunkX != chunk.getX() || eggChunkZ != chunk.getZ()) continue;

            if (adminManager.tryReindexEgg(egg.entityUuid())) {
                plugin.getLogger().info("Late-indexed admin egg in chunk ["
                        + chunk.getX() + ", " + chunk.getZ() + "]");
            }
        }
    }
}
