package com.m13.egghunt.listeners;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.managers.AdminEggManager;
import com.m13.egghunt.managers.TierManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class AdminWandListener implements Listener {

    private final EggHuntPlugin plugin;
    private final AdminEggManager adminManager;

    public AdminWandListener(EggHuntPlugin plugin, AdminEggManager adminManager) {
        this.plugin = plugin;
        this.adminManager = adminManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("egghunt.admin")) return;
        if (!adminManager.isWand(player.getInventory().getItemInMainHand())) return;

        Action action = event.getAction();

        // LEFT CLICK -> cycle tier
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            String newTier = adminManager.cycleTier(player);
            String msg = plugin.msgRaw("wand-tier")
                    .replace("{tier}", adminManager.tierDisplayName(newTier));
            player.sendMessage(EggHuntPlugin.colorize(
                    plugin.getConfig().getString("messages.prefix", "") + msg));
            return;
        }

        // RIGHT CLICK BLOCK
        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Block clicked = event.getClickedBlock();
            if (clicked == null) return;

            // SNEAK + RIGHT -> remove nearest
            if (player.isSneaking()) {
                boolean removed = adminManager.removeNearest(player.getLocation());
                player.sendMessage(removed
                        ? plugin.msg("removed-admin-egg")
                        : EggHuntPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")
                        + "&7No admin egg within 3 blocks."));
                return;
            }

            // Place egg
            Location placeLoc = clicked.getRelative(BlockFace.UP).getLocation().add(0.5, 0, 0.5);
            String tierKey = adminManager.getSelectedTier(player);
            boolean success = adminManager.placeEgg(placeLoc, tierKey, player.getLocation().getYaw());

            if (success) {
                String msg = plugin.msgRaw("placed-admin-egg")
                        .replace("{tier}", adminManager.tierDisplayName(tierKey));
                player.sendMessage(EggHuntPlugin.colorize(
                        plugin.getConfig().getString("messages.prefix", "") + msg));
            } else {
                player.sendMessage(EggHuntPlugin.colorize(
                        plugin.getConfig().getString("messages.prefix", "")
                                + "&cFailed to place. Check Nexo IDs."));
            }
        }
    }
}
