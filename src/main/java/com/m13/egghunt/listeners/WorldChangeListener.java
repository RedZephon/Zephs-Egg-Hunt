package com.m13.egghunt.listeners;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.managers.ActionBarManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Clears the persistent action bar when a player leaves the event world.
 */
public class WorldChangeListener implements Listener {

    private final EggHuntPlugin plugin;

    public WorldChangeListener(EggHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        String eventWorld = plugin.getConfig().getString("action-bar.event-world", "event");
        // Player LEFT the event world
        if (event.getFrom().getName().equals(eventWorld)) {
            ActionBarManager.clearBar(event.getPlayer());
        }
    }
}
