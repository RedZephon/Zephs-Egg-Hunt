package com.m13.egghunt.managers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.m13.egghunt.EggHuntPlugin;
import com.m13.egghunt.models.AdminEgg;
import com.nexomc.nexo.api.NexoItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Uses ProtocolLib to intercept outgoing ENTITY_METADATA packets.
 * When the entity is a tracked admin egg and the receiving player
 * has claimed it, swaps the displayed ItemStack to the claimed egg texture.
 *
 * CRITICAL: The metadata serializer expects NMS ItemStack, not Bukkit.
 * We convert via MinecraftReflection.getMinecraftItemStack().
 *
 * ItemDisplay entities store their displayed item at metadata index 23
 * (Minecraft 1.21.4: Entity=0-7, Display=8-22, ItemDisplay.item=23).
 */
public class EggVisualManager {

    private static final int ITEM_DISPLAY_INDEX = 23;

    private final EggHuntPlugin plugin;
    private final ProtocolManager protocolManager;
    private volatile ItemStack claimedBukkitItem;
    private volatile Object claimedNmsItem; // net.minecraft.world.item.ItemStack
    private volatile boolean itemLoadAttempted = false;

    public EggVisualManager(EggHuntPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        registerListener();
        // Defer loading the claimed item -- Nexo may not have registered items yet
        // during onEnable. Schedule for next tick (after all plugins enabled).
        Bukkit.getScheduler().runTask(plugin, this::ensureClaimedItemLoaded);
    }

    /**
     * Attempt to load the claimed Nexo item. Safe to call multiple times;
     * keeps retrying until Nexo returns a valid item or gives up after
     * several attempts.
     */
    private synchronized void ensureClaimedItemLoaded() {
        if (claimedNmsItem != null) return; // already loaded

        String claimedId = plugin.getConfig().getString("claimed-egg-id", "claimed_easter_egg");
        var builder = NexoItems.itemFromId(claimedId);
        if (builder != null) {
            claimedBukkitItem = builder.build();
            claimedNmsItem = MinecraftReflection.getMinecraftItemStack(claimedBukkitItem);
            plugin.getLogger().info("Claimed egg visual loaded: " + claimedId);
        } else if (!itemLoadAttempted) {
            // First failure -- try again in 2 seconds (Nexo might still be loading)
            itemLoadAttempted = true;
            plugin.getLogger().warning("Claimed egg item '" + claimedId
                    + "' not found yet -- retrying in 2 seconds...");
            Bukkit.getScheduler().runTaskLater(plugin, this::ensureClaimedItemLoaded, 40L);
        } else {
            plugin.getLogger().severe("Claimed egg Nexo item '" + claimedId
                    + "' still not found after retry! Visual swapping will NOT work. "
                    + "Ensure the item exists in your Nexo config.");
        }
    }

    /**
     * Get the claimed NMS item, with lazy-loading fallback.
     * Returns null if the item genuinely doesn't exist.
     */
    private Object getClaimedNmsItem() {
        if (claimedNmsItem == null && !itemLoadAttempted) {
            ensureClaimedItemLoaded();
        }
        return claimedNmsItem;
    }

    /**
     * Register a packet listener that intercepts outgoing ENTITY_METADATA.
     * For admin egg entities where the player has claimed it,
     * swap the displayed item in the metadata to the claimed egg.
     *
     * Uses HIGHEST priority to run AFTER Nexo's own packet management,
     * so our swap is the final word on the metadata.
     */
    private void registerListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.HIGHEST,
                PacketType.Play.Server.ENTITY_METADATA
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    if (event.isCancelled()) return;
                    Object nmsItem = getClaimedNmsItem();
                    if (nmsItem == null) return;

                    int entityId = event.getPacket().getIntegers().read(0);
                    AdminEggManager adminManager = EggVisualManager.this.plugin.getAdminEggManager();
                    if (adminManager == null) return;

                    AdminEgg egg = adminManager.getByEntityId(entityId);
                    if (egg == null) return;

                    Player player = event.getPlayer();
                    if (!egg.hasClaimed(player.getUniqueId())) return;

                    // Clone the packet so we don't modify it for other players
                    PacketContainer packet = event.getPacket().deepClone();
                    event.setPacket(packet);

                    List<WrappedDataValue> values = packet.getDataValueCollectionModifier().read(0);
                    if (values == null || values.isEmpty()) return;

                    // Swap the item at the ItemDisplay index (23)
                    boolean swapped = false;
                    List<WrappedDataValue> modified = new ArrayList<>();
                    for (WrappedDataValue value : values) {
                        if (value.getIndex() == ITEM_DISPLAY_INDEX) {
                            modified.add(new WrappedDataValue(
                                    ITEM_DISPLAY_INDEX,
                                    WrappedDataWatcher.Registry.getItemStackSerializer(false),
                                    MinecraftReflection.getMinecraftItemStack(claimedBukkitItem.clone())
                            ));
                            swapped = true;
                        } else {
                            modified.add(value);
                        }
                    }

                    if (swapped) {
                        packet.getDataValueCollectionModifier().write(0, modified);
                    }
                } catch (Exception e) {
                    // Never let a packet error disconnect the player
                    EggVisualManager.this.plugin.getLogger().warning(
                            "EggVisual packet error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Force-send a metadata packet to a specific player to swap
     * the visual after claiming. Sends on next tick to ensure
     * any pending Nexo packets have been flushed first.
     */
    public void sendClaimedVisual(Player player, AdminEgg egg) {
        // Delay by 1 tick so Nexo's own packet management finishes first,
        // then our packet listener will intercept the metadata we send.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            sendClaimedVisualNow(player, egg);
        }, 1L);
    }

    public void sendClaimedVisualNow(Player player, AdminEgg egg) {
        Object nmsItem = getClaimedNmsItem();
        if (nmsItem == null) return;

        try {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, egg.entityId());

            List<WrappedDataValue> values = new ArrayList<>();
            values.add(new WrappedDataValue(
                    ITEM_DISPLAY_INDEX,
                    WrappedDataWatcher.Registry.getItemStackSerializer(false),
                    MinecraftReflection.getMinecraftItemStack(claimedBukkitItem.clone())
            ));
            packet.getDataValueCollectionModifier().write(0, values);

            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send claimed visual to " + player.getName()
                    + ": " + e.getMessage());
        }
    }

    /**
     * Re-send claimed visuals for all admin eggs to a player.
     * Called on join after entities are loaded.
     */
    public void applyAllForPlayer(Player player) {
        AdminEggManager adminManager = plugin.getAdminEggManager();
        if (adminManager == null) return;

        for (AdminEgg egg : adminManager.allEggs()) {
            if (egg.hasClaimed(player.getUniqueId())) {
                sendClaimedVisualNow(player, egg);
            }
        }
    }

    public void reload() {
        // Reset and re-load
        claimedNmsItem = null;
        claimedBukkitItem = null;
        itemLoadAttempted = false;
        ensureClaimedItemLoaded();
    }

    public void shutdown() {
        protocolManager.removePacketListeners(plugin);
    }
}
