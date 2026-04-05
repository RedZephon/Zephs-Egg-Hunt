package com.m13.egghunt.managers;

import com.m13.egghunt.EggHuntPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Sends a persistent action bar to players in the event world
 * showing their event egg count vs total placed admin eggs.
 */
public class ActionBarManager {

    private final EggHuntPlugin plugin;
    private BukkitTask task;

    public ActionBarManager(EggHuntPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        // Tick every 30 ticks (1.5s) — action bars last ~2s so this keeps it visible
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 30L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        if (!plugin.isEventActive()) return;

        String eventWorld = plugin.getConfig().getString("action-bar.event-world", "event");
        String format = plugin.getConfig().getString("action-bar.event-format",
                "&a\u00a7l\uD83E\uDD5A Eggs Found: &f{count} &7/ &f{total}");
        int totalEggs = plugin.getAdminEggManager().count();
        LeaderboardManager lb = plugin.getLeaderboardManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equals(eventWorld)) {
                int count = lb.getEventCount(player.getUniqueId());
                String text = EggHuntPlugin.colorize(
                        format.replace("{count}", String.valueOf(count))
                              .replace("{total}", String.valueOf(totalEggs)));
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
            }
        }
    }

    /**
     * Send an empty action bar to clear it for a player.
     */
    public static void clearBar(Player player) {
        player.sendActionBar(Component.empty());
    }
}
