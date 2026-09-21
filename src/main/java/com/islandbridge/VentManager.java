package com.islandbridge;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

public class VentManager {

    private final IslandBridgeAmongUs plugin;

    public VentManager(IslandBridgeAmongUs plugin) {
        this.plugin = plugin;
    }

    public List<Location> getVents() {
        return plugin.getLocationList("vents");
    }

    public boolean isVent(Location clicked) {
        for (Location vent : getVents()) {
            if (Util.sameBlock(vent, clicked)) return true;
        }
        return false;
    }

    /**
     * Impostor menggunakan vent. Pulangkan true jika blok itu memang vent.
     */
    public boolean handleInteract(Player p, Location clicked) {
        List<Location> vents = getVents();
        int index = -1;
        for (int i = 0; i < vents.size(); i++) {
            if (Util.sameBlock(vents.get(i), clicked)) {
                index = i;
                break;
            }
        }
        if (index < 0) return false;

        Game game = plugin.getGame();
        if (!game.isRunning()) return true;
        if (!game.isImpostor(p) || !game.isAlive(p)) {
            p.sendMessage("§7Lubang ini terlalu sempit untuk anda.");
            return true;
        }
        if (game.getState() == GameState.MEETING) {
            p.sendMessage("§cTidak boleh menggunakan vent semasa mesyuarat.");
            return true;
        }
        if (vents.size() < 2) {
            p.sendMessage("§cHanya ada satu vent di peta ini.");
            return true;
        }

        Location next = vents.get((index + 1) % vents.size());
        Location dest = next.clone().add(0.5, 0.2, 0.5);
        dest.setYaw(p.getLocation().getYaw());
        dest.setPitch(p.getLocation().getPitch());

        p.teleport(dest);
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1f, 1.3f);
        p.sendMessage("§8Anda meluncur melalui vent...");
        return true;
    }
}
