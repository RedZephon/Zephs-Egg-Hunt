package com.m13.egghunt.hooks;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.managers.LeaderboardManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * PlaceholderAPI expansion for EggHunt.
 *
 * Placeholders:
 *   %egghunt_collected%       - player's egg count
 *   %egghunt_total%           - global total collected
 *   %egghunt_active%          - event active (true/false)
 *   %egghunt_admin_count%     - admin-placed egg count
 *   %egghunt_player_count%    - active player-spawned egg count
 *   %egghunt_rank%            - player's leaderboard rank
 *   %egghunt_top_<N>_name%    - Nth top collector name
 *   %egghunt_top_<N>_count%   - Nth top collector count
 */
public class EggHuntPlaceholders extends PlaceholderExpansion {

    private final EggHuntPlugin plugin;

    public EggHuntPlaceholders(EggHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "egghunt"; }

    @Override
    public @NotNull String getAuthor() { return "RedZephon"; }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        LeaderboardManager lb = plugin.getLeaderboardManager();

        switch (params.toLowerCase()) {
            case "collected":
                return player != null ? String.valueOf(lb.getCount(player.getUniqueId())) : "0";
            case "event_collected":
                return player != null ? String.valueOf(lb.getEventCount(player.getUniqueId())) : "0";
            case "random_collected":
                return player != null ? String.valueOf(lb.getRandomCount(player.getUniqueId())) : "0";
            case "total":
                return String.valueOf(lb.getTotalCollected());
            case "total_event":
                return String.valueOf(lb.getTotalEventCollected());
            case "total_random":
                return String.valueOf(lb.getTotalRandomCollected());
            case "active":
                return plugin.isEventActive() ? "true" : "false";
            case "admin_count":
                return String.valueOf(plugin.getAdminEggManager().count());
            case "player_count":
                return String.valueOf(plugin.getPlayerEggSpawner().count());
            case "rank":
                if (player == null) return "0";
                int rank = lb.getRank(player.getUniqueId());
                return rank > 0 ? String.valueOf(rank) : "\u2014";
            default:
                break;
        }

        // Handle top_<N>_name and top_<N>_count
        if (params.toLowerCase().startsWith("top_")) {
            return handleTopPlaceholder(params.toLowerCase());
        }

        return null; // unknown placeholder
    }

    private String handleTopPlaceholder(String params) {
        // Expected format: top_<N>_name or top_<N>_count
        String[] parts = params.split("_");
        if (parts.length != 3) return null;

        int n;
        try { n = Integer.parseInt(parts[1]); }
        catch (NumberFormatException e) { return null; }
        if (n < 1) return null;

        List<Map.Entry<String, Integer>> top = plugin.getLeaderboardManager().getTopCollectors(n);
        if (n > top.size()) return "—";

        Map.Entry<String, Integer> entry = top.get(n - 1);
        return switch (parts[2]) {
            case "name" -> entry.getKey();
            case "count" -> String.valueOf(entry.getValue());
            default -> null;
        };
    }
}
