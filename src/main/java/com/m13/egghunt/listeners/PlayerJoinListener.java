package com.m13.egghunt.listeners;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.managers.EggVisualManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * On join, re-sends claimed egg visuals via ProtocolLib.
 * Delayed so entities are loaded for the player.
 */
public class PlayerJoinListener implements Listener {

    private final EggHuntPlugin plugin;

    public PlayerJoinListener(EggHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                EggVisualManager visualManager = plugin.getEggVisualManager();
                if (visualManager != null) {
                    visualManager.applyAllForPlayer(player);
                }
            }
        }, 60L); // Fallback -- EntityTrackListener handles the primary swap
    }
}
