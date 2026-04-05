package com.m13.egghunt.commands;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.managers.*;
import com.nexomc.nexo.api.NexoFurniture;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class EggHuntCommand implements CommandExecutor, TabCompleter {

    private final EggHuntPlugin plugin;
    private final TierManager tierManager;
    private final AdminEggManager adminManager;
    private final PlayerEggSpawner playerSpawner;
    private final LeaderboardManager leaderboard;

    private static final List<String> ADMIN_SUBCOMMANDS =
            List.of("start", "stop", "wand", "clear", "cleanup", "reload", "status", "leaderboard", "debugspawn", "reindex");
    private static final List<String> PLAYER_SUBCOMMANDS =
            List.of("stats", "leaderboard", "lb", "top");

    public EggHuntCommand(EggHuntPlugin plugin, TierManager tierManager,
                          AdminEggManager adminManager, PlayerEggSpawner playerSpawner,
                          LeaderboardManager leaderboard) {
        this.plugin = plugin;
        this.tierManager = tierManager;
        this.adminManager = adminManager;
        this.playerSpawner = playerSpawner;
        this.leaderboard = leaderboard;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendUsage(sender); return true; }

        // Player commands (no admin perm needed)
        switch (args[0].toLowerCase()) {
            case "stats" -> {
                if (!sender.hasPermission("egghunt.stats")) {
                    sender.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                handleStats(sender);
                return true;
            }
            case "leaderboard", "lb", "top" -> {
                if (!sender.hasPermission("egghunt.leaderboard")) {
                    sender.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                handleLeaderboard(sender);
                return true;
            }
        }

        // Admin commands
        if (!sender.hasPermission("egghunt.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "wand" -> handleWand(sender);
            case "clear" -> handleClear(sender, args);
            case "cleanup" -> handleCleanup(sender);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "debugspawn" -> handleDebugSpawn(sender);
            case "reindex" -> handleReindex(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleStart(CommandSender sender) {
        if (plugin.isEventActive()) {
            sender.sendMessage(plugin.msg("already-active"));
            return;
        }
        plugin.setEventActive(true);
        leaderboard.reset();
        playerSpawner.start();
        plugin.getActionBarManager().start();

        String startMsg = plugin.msg("event-started");
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(startMsg));

        // Re-apply visibility for all online players (fresh event)
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.getEggVisualManager().applyAllForPlayer(p);
        }

        sender.sendMessage(EggHuntPlugin.colorize("&aEvent started."));
    }

    private void handleStop(CommandSender sender) {
        if (!plugin.isEventActive()) {
            sender.sendMessage(plugin.msg("not-active"));
            return;
        }
        plugin.setEventActive(false);
        playerSpawner.stop();
        plugin.getActionBarManager().stop();

        String stopMsg = plugin.msg("event-stopped");
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(stopMsg));

        if (plugin.getConfig().getBoolean("leaderboard.enabled", true)) {
            int topN = plugin.getConfig().getInt("leaderboard.announce-top", 5);
            List<Map.Entry<String, Integer>> top = leaderboard.getTopCollectors(topN);
            if (!top.isEmpty()) {
                String header = EggHuntPlugin.colorize("&6&l--- Top Egg Hunters ---");
                Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(header));
                for (int i = 0; i < top.size(); i++) {
                    var e = top.get(i);
                    String line = EggHuntPlugin.colorize(
                            "&e#" + (i + 1) + " &f" + e.getKey() + " &8- &a" + e.getValue() + " eggs");
                    Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(line));
                }
            }
        }

        sender.sendMessage(EggHuntPlugin.colorize("&aEvent stopped. Admin eggs stay in world."));
    }

    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(EggHuntPlugin.colorize("&cPlayers only."));
            return;
        }
        adminManager.giveWand(player);
        player.sendMessage(plugin.msg("wand-given"));
    }

    private void handleClear(CommandSender sender, String[] args) {
        String target = args.length > 1 ? args[1].toLowerCase() : "all";
        int a = 0, p = 0;
        if (target.equals("admin") || target.equals("all")) a = adminManager.clearAll();
        if (target.equals("player") || target.equals("all")) p = playerSpawner.clearAll();
        sender.sendMessage(EggHuntPlugin.colorize(
                plugin.getConfig().getString("messages.prefix", "")
                        + "&cCleared &f" + a + " &cadmin + &f" + p + " &cplayer eggs."));
    }

    private void handleCleanup(CommandSender sender) {
        String claimedId = plugin.getConfig().getString("claimed-egg-id", "claimed_easter_egg");
        sender.sendMessage(EggHuntPlugin.colorize(
                plugin.getConfig().getString("messages.prefix", "")
                        + "&eScanning for orphaned &f" + claimedId + " &eentities..."));

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof ItemDisplay)) continue;

                // Skip entities we're actively tracking
                if (adminManager.isAdminEgg(entity.getUniqueId())) continue;

                // Check if it's the claimed furniture ID
                var mechanic = NexoFurniture.furnitureMechanic(entity);
                if (mechanic != null && claimedId.equals(mechanic.getItemID())) {
                    NexoFurniture.remove(entity, null);
                    removed++;
                }
            }
        }

        sender.sendMessage(EggHuntPlugin.colorize(
                plugin.getConfig().getString("messages.prefix", "")
                        + "&aRemoved &f" + removed + " &aorphaned claimed egg entities."));
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        tierManager.loadTiers();
        plugin.getPrizeManager().loadPrizes();
        if (plugin.getEggVisualManager() != null) {
            plugin.getEggVisualManager().reload();
        }
        sender.sendMessage(plugin.msg("reload"));
    }

    private void handleStatus(CommandSender sender) {
        String msg = plugin.msgRaw("status")
                .replace("{active}", plugin.isEventActive() ? "&aYES" : "&cNO")
                .replace("{admin_count}", String.valueOf(adminManager.count()))
                .replace("{player_count}", String.valueOf(playerSpawner.count()))
                .replace("{total_collected}", String.valueOf(leaderboard.getTotalCollected()));
        sender.sendMessage(EggHuntPlugin.colorize(
                plugin.getConfig().getString("messages.prefix", "") + msg));
    }

    private void handleLeaderboard(CommandSender sender) {
        int topN = plugin.getConfig().getInt("leaderboard.announce-top", 5);
        List<Map.Entry<String, Integer>> top = leaderboard.getTopCollectors(topN);
        if (top.isEmpty()) {
            sender.sendMessage(EggHuntPlugin.colorize("&7No eggs collected yet."));
            return;
        }
        sender.sendMessage(EggHuntPlugin.colorize("&6&l--- Top Egg Hunters ---"));
        for (int i = 0; i < top.size(); i++) {
            var e = top.get(i);
            sender.sendMessage(EggHuntPlugin.colorize(
                    "&e#" + (i + 1) + " &f" + e.getKey() + " &8- &a" + e.getValue() + " eggs"));
        }
    }

    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(EggHuntPlugin.colorize("&cPlayers only."));
            return;
        }
        int eventEggs = leaderboard.getEventCount(player.getUniqueId());
        int randomEggs = leaderboard.getRandomCount(player.getUniqueId());
        int total = eventEggs + randomEggs;
        int rank = leaderboard.getRank(player.getUniqueId());
        int hunters = leaderboard.uniquePlayerCount();
        String status = plugin.isEventActive() ? "&aActive" : "&cInactive";

        player.sendMessage(EggHuntPlugin.colorize(plugin.msgRaw("stats-header")));
        player.sendMessage(EggHuntPlugin.colorize(plugin.msgRaw("stats-event-eggs")
                .replace("{count}", String.valueOf(eventEggs))));
        player.sendMessage(EggHuntPlugin.colorize(plugin.msgRaw("stats-random-eggs")
                .replace("{count}", String.valueOf(randomEggs))));
        player.sendMessage(EggHuntPlugin.colorize(plugin.msgRaw("stats-total")
                .replace("{count}", String.valueOf(total))));
        if (rank > 0) {
            player.sendMessage(EggHuntPlugin.colorize(plugin.msgRaw("stats-rank")
                    .replace("{rank}", String.valueOf(rank))
                    .replace("{hunters}", String.valueOf(hunters))));
        } else {
            player.sendMessage(EggHuntPlugin.colorize(plugin.msgRaw("stats-no-rank")));
        }
        player.sendMessage(EggHuntPlugin.colorize(plugin.msgRaw("stats-event-status")
                .replace("{status}", status)));
    }

    private void handleReindex(CommandSender sender) {
        int before = adminManager.count() - adminManager.getUnindexedEggs().size();
        adminManager.reindexEntityIds();
        int after = adminManager.count() - adminManager.getUnindexedEggs().size();
        int fixed = after - before;
        sender.sendMessage(EggHuntPlugin.colorize(
                plugin.getConfig().getString("messages.prefix", "")
                        + "&aReindexed admin eggs. &f" + after + "/" + adminManager.count()
                        + " &alinked" + (fixed > 0 ? " &e(+" + fixed + " newly found)" : "")
                        + ". &7Unlinked: &f" + adminManager.getUnindexedEggs().size()));
    }

    private void handleDebugSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(EggHuntPlugin.colorize("&cPlayers only."));
            return;
        }
        playerSpawner.forceSpawnNear(player);
        player.sendMessage(EggHuntPlugin.colorize(
                plugin.getConfig().getString("messages.prefix", "")
                        + "&bDebug: forced egg spawn near you. &7Active player eggs: &f"
                        + playerSpawner.count()));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(EggHuntPlugin.colorize("&6&lEggHunt Commands:"));
        // Player commands
        if (sender.hasPermission("egghunt.stats")) {
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt stats &8- &7Your egg collection stats"));
        }
        if (sender.hasPermission("egghunt.leaderboard")) {
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt leaderboard &8- &7Top collectors"));
        }
        // Admin commands
        if (sender.hasPermission("egghunt.admin")) {
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt start &8- &7Start the event"));
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt stop &8- &7End event + show leaderboard"));
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt wand &8- &7Get egg placement wand"));
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt clear [admin|player|all] &8- &7Remove eggs"));
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt cleanup &8- &7Remove orphaned claimed egg entities"));
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt reload &8- &7Reload config"));
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt status &8- &7Event info"));
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt debugspawn &8- &7Force-spawn a player egg nearby"));
            sender.sendMessage(EggHuntPlugin.colorize("&e/egghunt reindex &8- &7Re-link admin egg entity IDs"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new java.util.ArrayList<>();
            if (sender.hasPermission("egghunt.stats")) completions.add("stats");
            if (sender.hasPermission("egghunt.leaderboard")) completions.add("leaderboard");
            if (sender.hasPermission("egghunt.admin")) completions.addAll(ADMIN_SUBCOMMANDS);
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear") && sender.hasPermission("egghunt.admin"))
            return List.of("admin", "player", "all").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        return List.of();
    }
}
