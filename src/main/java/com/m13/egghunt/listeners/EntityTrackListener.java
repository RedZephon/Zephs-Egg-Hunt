package com.m13.egghunt.listeners;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.managers.AdminEggManager;
import com.m13.egghunt.managers.EggVisualManager;
import com.m13.egghunt.models.AdminEgg;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Fires when a player starts tracking (seeing) an entity.
 * If the entity is a claimed admin egg, immediately send the
 * visual swap — this is the most reliable hook because it fires
 * at the exact moment the client receives the entity.
 */
public class EntityTrackListener implements Listener {

    private final EggHuntPlugin plugin;
    private final AdminEggManager adminManager;

    public EntityTrackListener(EggHuntPlugin plugin, AdminEggManager adminManager) {
        this.plugin = plugin;
        this.adminManager = adminManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent event) {
        Entity entity = event.getEntity();
        Player player = event.getPlayer();

        AdminEgg egg = adminManager.getByEntityUuid(entity.getUniqueId());
        if (egg == null) return;

        // Reindex immediately if entity ID is stale (chunks unloaded/reloaded)
        if (egg.entityId() != entity.getEntityId()) {
            adminManager.tryReindexEgg(entity.getUniqueId());
            egg = adminManager.getByEntityUuid(entity.getUniqueId());
            if (egg == null) return;
        }

        if (!egg.hasClaimed(player.getUniqueId())) return;

        // Send the visual swap at multiple delays to survive Nexo's
        // furniture packet manager re-sending the original metadata.
        // The passive ProtocolLib listener handles most cases, but
        // direct sends act as a safety net.
        EggVisualManager visualManager = plugin.getEggVisualManager();
        if (visualManager != null) {
            final AdminEgg trackedEgg = egg;
            visualManager.sendClaimedVisual(player, trackedEgg);
            // Second send at 10 ticks catches late Nexo re-renders
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    visualManager.sendClaimedVisualNow(player, trackedEgg);
                }
            }, 10L);
        }
    }
}
