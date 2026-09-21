package com.islandbridge;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Util {

    private Util() {
    }

    /** Adakah dua lokasi merujuk blok yang sama? */
    public static boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().getName().equals(b.getWorld().getName())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    /** Bina item mudah dengan nama dan lore. */
    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                List<String> lines = new ArrayList<>(Arrays.asList(lore));
                meta.setLore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Bina kepala pemain dengan nama paparan tertentu. */
    public static ItemStack playerHead(OfflinePlayer owner, String name, String... lore) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
            skull.setDisplayName(name);
            if (lore.length > 0) {
                skull.setLore(new ArrayList<>(Arrays.asList(lore)));
            }
            stack.setItemMeta(skull);
        }
        return stack;
    }

    /** Buang kod warna daripada teks. */
    public static String stripColor(String input) {
        if (input == null) return "";
        return input.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    /** Tukar saat kepada format mm:ss. */
    public static String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
