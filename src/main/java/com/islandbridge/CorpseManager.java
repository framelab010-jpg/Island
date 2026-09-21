package com.islandbridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.EulerAngle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public class CorpseManager {

    private final IslandBridgeAmongUs plugin;

    /** Kunci: UUID entiti armor stand. Nilai: UUID mangsa. */
    private final Map<UUID, UUID> corpses = new HashMap<>();

    public CorpseManager(IslandBridgeAmongUs plugin) {
        this.plugin = plugin;
    }

    public void spawn(Player victim, Location loc) {
        if (loc == null || loc.getWorld() == null) return;

        Location spot = loc.clone();
        spot.setPitch(0f);

        ArmorStand corpse = (ArmorStand) spot.getWorld().spawnEntity(spot, EntityType.ARMOR_STAND);
        corpse.setVisible(false);
        corpse.setBasePlate(false);
        corpse.setArms(false);
        corpse.setGravity(false);
        corpse.setInvulnerable(true);
        corpse.setSilent(true);
        corpse.setCustomName("§c\u2620 " + victim.getName());
        corpse.setCustomNameVisible(true);
        corpse.setHeadPose(new EulerAngle(Math.toRadians(-85), 0, 0));

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(victim);
            head.setItemMeta(skull);
        }
        EntityEquipment equipment = corpse.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(head);
        }

        corpses.put(corpse.getUniqueId(), victim.getUniqueId());
    }

    public boolean isCorpse(Entity entity) {
        return entity != null && corpses.containsKey(entity.getUniqueId());
    }

    public String getVictimName(Entity entity) {
        if (entity == null) return null;
        UUID victimId = corpses.get(entity.getUniqueId());
        if (victimId == null) return null;
        String name = Bukkit.getOfflinePlayer(victimId).getName();
        return name != null ? name : "seseorang";
    }

    public void clearAll() {
        for (UUID entityId : new HashSet<>(corpses.keySet())) {
            Entity ent = Bukkit.getEntity(entityId);
            if (ent != null) ent.remove();
        }
        corpses.clear();
    }

    public int count() {
        return corpses.size();
    }

    public IslandBridgeAmongUs getPlugin() {
        return plugin;
    }
}
