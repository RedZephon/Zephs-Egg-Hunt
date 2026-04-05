package com.m13.egghunt.models;

import com.m13.egghunt.EggHuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A reward parsed from a single config string.
 *
 * Formats:
 *   "diamond 5"                        -> item
 *   "golden_apple 1 &eShiny Apple"     -> item with custom name
 *   "cmd: eco give {player} 500"       -> console command
 */
public sealed interface Prize {

    String description();
    void award(Player player);

    /**
     * Parse a reward string from config into a Prize.
     */
    static Prize parse(String line) {
        if (line.toLowerCase().startsWith("cmd:")) {
            String command = line.substring(4).trim();
            return new CommandPrize(command);
        }

        // Item format: "MATERIAL AMOUNT [optional display name]"
        String[] parts = line.split(" ", 3);
        Material mat;
        try {
            mat = Material.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown material: " + parts[0]);
        }
        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        String name = parts.length > 2 ? parts[2] : null;

        return new ItemPrize(mat, amount, name);
    }

    // ---- Item Prize ----
    record ItemPrize(Material material, int amount, String name) implements Prize {
        @Override
        public String description() {
            if (name != null && !name.isBlank()) return EggHuntPlugin.colorize(name) + " x" + amount;
            return material.name().toLowerCase().replace('_', ' ') + " x" + amount;
        }

        @Override
        public void award(Player player) {
            ItemStack stack = new ItemStack(material, amount);
            if (name != null && !name.isBlank()) {
                ItemMeta meta = stack.getItemMeta();
                meta.setDisplayName(EggHuntPlugin.colorize(name));
                stack.setItemMeta(meta);
            }
            var overflow = player.getInventory().addItem(stack);
            overflow.values().forEach(r ->
                    player.getWorld().dropItemNaturally(player.getLocation(), r));
        }
    }

    // ---- Command Prize ----
    record CommandPrize(String command) implements Prize {
        @Override
        public String description() {
            // Try to make a readable description from the command
            // e.g. "eco give {player} 500" -> "eco give 500"
            return command.replace("{player}", "").replaceAll("\\s+", " ").trim();
        }

        @Override
        public void award(Player player) {
            String resolved = command.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }
}
