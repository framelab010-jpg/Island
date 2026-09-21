package com.islandbridge;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class TaskManager {

    public static final String TASK_TITLE = "§2Tugasan";

    private final IslandBridgeAmongUs plugin;
    private final Random random = new Random();

    /** Tugasan yang diberikan kepada setiap crewmate. */
    private final Map<UUID, List<Location>> assigned = new HashMap<>();
    /** Tugasan yang sudah disiapkan (kunci lokasi). */
    private final Map<UUID, Set<String>> completed = new HashMap<>();
    /** Tugasan yang sedang dibuka dalam GUI. */
    private final Map<UUID, Location> pending = new HashMap<>();
    /** Slot jawapan betul untuk GUI yang sedang dibuka. */
    private final Map<UUID, Integer> answerSlot = new HashMap<>();

    public TaskManager(IslandBridgeAmongUs plugin) {
        this.plugin = plugin;
    }

    public List<Location> getTaskPoints() {
        return plugin.getLocationList("tasks");
    }

    private String key(Location loc) {
        if (loc == null || loc.getWorld() == null) return "?";
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    /** Cari titik tugasan yang sepadan dengan blok yang diklik. */
    public Location findTaskPoint(Location clicked) {
        for (Location point : getTaskPoints()) {
            if (Util.sameBlock(point, clicked)) return point;
        }
        return null;
    }

    public void resetAssignments() {
        assigned.clear();
        completed.clear();
        pending.clear();
        answerSlot.clear();

        Game game = plugin.getGame();
        List<Location> points = getTaskPoints();
        int perCrew = game.getTasksPerCrew();

        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!game.isCrewmate(p)) continue;
            List<Location> pool = new ArrayList<>(points);
            Collections.shuffle(pool, random);
            List<Location> mine = new ArrayList<>(pool.subList(0, Math.min(perCrew, pool.size())));
            assigned.put(p.getUniqueId(), mine);
            completed.put(p.getUniqueId(), new HashSet<>());
            sendTaskList(p);
        }
    }

    public void sendTaskList(Player p) {
        List<Location> mine = assigned.get(p.getUniqueId());
        if (mine == null || mine.isEmpty()) {
            p.sendMessage("§7Anda tiada senarai tugasan.");
            return;
        }
        Set<String> done = completed.getOrDefault(p.getUniqueId(), new HashSet<>());
        p.sendMessage("§6=== Tugasan Anda ===");
        int i = 1;
        for (Location loc : mine) {
            boolean isDone = done.contains(key(loc));
            p.sendMessage((isDone ? "§a✔ " : "§e✖ ") + i + ". §7X:" + loc.getBlockX()
                    + " Y:" + loc.getBlockY() + " Z:" + loc.getBlockZ());
            i++;
        }
    }

    /** Pemain klik kanan blok tugasan. Pulangkan true jika blok itu memang titik tugasan. */
    public boolean handleInteract(Player p, Location clicked) {
        Location point = findTaskPoint(clicked);
        if (point == null) return false;

        Game game = plugin.getGame();
        if (!game.isRunning()) {
            return true;
        }
        if (game.getState() == GameState.MEETING) {
            p.sendMessage("§cTidak boleh membuat tugasan semasa mesyuarat.");
            return true;
        }
        if (plugin.getSabotage().isReactorActive()) {
            p.sendMessage("§cBaiki kecemasan dahulu sebelum menyambung tugasan!");
            return true;
        }
        if (!game.isInGame(p)) {
            p.sendMessage("§7Tiada apa-apa berlaku.");
            return true;
        }
        if (game.isImpostor(p)) {
            p.sendMessage("§cAnda impostor — anda hanya berpura-pura membuat tugasan.");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            openTaskGui(p, point, true);
            return true;
        }

        List<Location> mine = assigned.get(p.getUniqueId());
        if (mine == null || mine.stream().noneMatch(l -> Util.sameBlock(l, point))) {
            p.sendMessage("§7Ini bukan tugasan yang diberikan kepada anda.");
            return true;
        }
        Set<String> done = completed.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (done.contains(key(point))) {
            p.sendMessage("§aTugasan ini sudah siap.");
            return true;
        }

        openTaskGui(p, point, false);
        return true;
    }

    private void openTaskGui(Player p, Location point, boolean fake) {
        Inventory inv = org.bukkit.Bukkit.createInventory(null, 27, TASK_TITLE);
        ItemStack filler = Util.item(Material.GRAY_STAINED_GLASS_PANE, "§8...");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }
        int correct = random.nextInt(27);
        inv.setItem(correct, Util.item(Material.LIME_DYE, "§aSambungkan wayar",
                "§7Klik untuk menyiapkan tugasan."));

        pending.put(p.getUniqueId(), fake ? null : point);
        answerSlot.put(p.getUniqueId(), correct);
        p.openInventory(inv);
    }

    /** Dipanggil daripada GameListener apabila pemain klik dalam GUI tugasan. */
    public void handleGuiClick(Player p, int slot) {
        Integer correct = answerSlot.get(p.getUniqueId());
        if (correct == null || slot != correct) return;

        Location point = pending.get(p.getUniqueId());
        pending.remove(p.getUniqueId());
        answerSlot.remove(p.getUniqueId());
        p.closeInventory();

        if (point == null) {
            // Impostor berpura-pura: tiada progres sebenar
            p.sendMessage("§7Anda berpura-pura menyiapkan tugasan.");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            return;
        }

        Game game = plugin.getGame();
        Set<String> done = completed.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (done.contains(key(point))) return;
        done.add(key(point));

        game.addTaskDone(p);
        int myDone = game.getTasksDone(p);
        p.sendMessage("§aTugasan siap! §7(" + myDone + "/" + game.getTasksPerCrew() + ")");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        game.checkWin();
    }

    public void closeGui(Player p) {
        pending.remove(p.getUniqueId());
        answerSlot.remove(p.getUniqueId());
    }
}
