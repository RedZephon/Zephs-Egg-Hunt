package com.m13.egghunt.listeners;

import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.managers.*;
import com.m13.egghunt.models.AdminEgg;
import com.m13.egghunt.models.EggTier;
import com.m13.egghunt.models.PlacedEgg;
import com.m13.egghunt.models.Prize;
import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public class EggCollectListener implements Listener {

    private final EggHuntPlugin plugin;
    private final TierManager tierManager;
    private final PrizeManager prizeManager;
    private final AdminEggManager adminManager;
    private final PlayerEggSpawner playerSpawner;
    private final LeaderboardManager leaderboard;

    public EggCollectListener(EggHuntPlugin plugin, TierManager tierManager,
                              PrizeManager prizeManager, AdminEggManager adminManager,
                              PlayerEggSpawner playerSpawner, LeaderboardManager leaderboard) {
        this.plugin = plugin;
        this.tierManager = tierManager;
        this.prizeManager = prizeManager;
        this.adminManager = adminManager;
        this.playerSpawner = playerSpawner;
        this.leaderboard = leaderboard;
    }

    // ---- Furniture Break ----

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFurnitureBreak(NexoFurnitureBreakEvent event) {
        Player player = event.getPlayer();
        Entity base = event.getBaseEntity();
        if (base == null || player == null) return;

        UUID id = base.getUniqueId();

        if (adminManager.isAdminEgg(id)) {
            event.setCancelled(true);
            handleAdminClaim(player, id);
            return;
        }
        if (playerSpawner.isPlayerEgg(id)) {
            event.setCancelled(true);
            handlePlayerClaim(player, base);
        }
    }

    // ---- Furniture Interact ----

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFurnitureInteract(NexoFurnitureInteractEvent event) {
        Player player = event.getPlayer();
        Entity base = event.getBaseEntity();
        if (base == null || player == null) return;

        UUID id = base.getUniqueId();

        if (adminManager.isAdminEgg(id)) {
            handleAdminClaim(player, id);
            return;
        }
        if (playerSpawner.isPlayerEgg(id)) {
            handlePlayerClaim(player, base);
        }
    }

    // ---- Admin: claim per-player, ProtocolLib swaps the visual ----

    private void handleAdminClaim(Player player, UUID entityUuid) {
        if (!preCheck(player)) return;

        AdminEgg egg = adminManager.getByEntityUuid(entityUuid);
        if (egg == null) return;

        if (egg.hasClaimed(player.getUniqueId())) {
            player.sendMessage(plugin.playerMsg("already-claimed"));
            // Re-send the claimed visual in case it's showing the wrong model
            var visualManager = plugin.getEggVisualManager();
            if (visualManager != null) {
                visualManager.sendClaimedVisualNow(player, egg);
            }
            return;
        }

        // Claim -- ProtocolLib sends the visual swap packet
        adminManager.claimEgg(player, egg);

        EggTier tier = tierManager.getTier(egg.tierKey());
        if (tier != null) {
            playBreakEffects(player.getLocation());
            sendCollectMessage(player, tier);
            rollAndAward(player, egg.tierKey());
        }
        leaderboard.recordCollection(player, true);
    }

    // ---- Player: single-use, remove from world ----

    private void handlePlayerClaim(Player player, Entity entity) {
        if (!preCheck(player)) return;

        PlacedEgg egg = playerSpawner.getEgg(entity.getUniqueId());
        if (egg == null) return;

        playerSpawner.removeEgg(entity.getUniqueId());
        NexoFurniture.remove(entity, null);

        EggTier tier = tierManager.getTier(egg.tierKey());
        if (tier != null) {
            playBreakEffects(egg.location());
            sendCollectMessage(player, tier);
            rollAndAward(player, egg.tierKey());
        }
        leaderboard.recordCollection(player, false);

        // Action bar for random egg collection
        String fmt = plugin.getConfig().getString("action-bar.random-format",
                "&a\u00a7lYou've found &f{count} &a\u00a7leggs!");
        int count = leaderboard.getRandomCount(player.getUniqueId());
        String text = EggHuntPlugin.colorize(fmt.replace("{count}", String.valueOf(count)));
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    // ---- Shared ----

    private boolean preCheck(Player player) {
        if (!plugin.isEventActive()) {
            player.sendMessage(plugin.playerMsg("not-active"));
            return false;
        }
        if (!player.hasPermission("egghunt.collect")) {
            player.sendMessage(plugin.playerMsg("no-permission"));
            return false;
        }
        if (leaderboard.isOnCooldown(player)) {
            player.sendMessage(plugin.playerMsg("on-cooldown"));
            return false;
        }
        if (leaderboard.hasReachedLimit(player)) {
            player.sendMessage(plugin.playerMsg("limit-reached"));
            return false;
        }
        return true;
    }

    private void playBreakEffects(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String soundStr = plugin.getConfig().getString("break-effect.sound", "entity.player.levelup");
        String particleStr = plugin.getConfig().getString("break-effect.particle", "TOTEM_OF_UNDYING");
        int count = plugin.getConfig().getInt("break-effect.particle-count", 25);
        try {
            Sound sound = Sound.valueOf(soundStr.toUpperCase().replace(".", "_"));
            loc.getWorld().playSound(loc, sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
        try {
            Particle particle = Particle.valueOf(particleStr.toUpperCase());
            loc.getWorld().spawnParticle(particle, loc.clone().add(0, 0.5, 0), count, 0.3, 0.3, 0.3, 0.05);
        } catch (IllegalArgumentException ignored) {}
    }

    private void sendCollectMessage(Player player, EggTier tier) {
        String prefix = plugin.getConfig().getString("messages.player-prefix", "");
        String msg = plugin.msgRaw("egg-collected").replace("{egg_name}", tier.displayName());
        player.sendMessage(EggHuntPlugin.colorize(prefix + msg));
    }

    private void rollAndAward(Player player, String tierKey) {
        Prize prize = prizeManager.rollPrize(tierKey);
        if (prize != null) {
            prize.award(player);
            String prefix = plugin.getConfig().getString("messages.player-prefix", "");
            String msg = plugin.msgRaw("prize-received").replace("{prize}", prize.description());
            player.sendMessage(EggHuntPlugin.colorize(prefix + msg));
        }
    }
}
