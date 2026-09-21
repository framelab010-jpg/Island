package com.islandbridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class SabotageManager {

    public static final String SABOTAGE_TITLE = "§4Menu Sabotaj";

    private final IslandBridgeAmongUs plugin;

    private boolean lightsActive = false;
    private int lightsLeft = 0;
    private BukkitTask lightsTask;

    private boolean reactorActive = false;
    private int reactorLeft = 0;
    private BukkitTask reactorTask;
    private BossBar reactorBar;

    private long cooldownUntil = 0L;

    public SabotageManager(IslandBridgeAmongUs plugin) {
        this.plugin = plugin;
    }

    public boolean isLightsActive() {
        return lightsActive;
    }

    public boolean isReactorActive() {
        return reactorActive;
    }

    // =========================================================
    // Menu sabotaj
    // =========================================================
    public void openMenu(Player p) {
        Game game = plugin.getGame();
        if (!game.isRunning() || !game.isImpostor(p)) {
            p.sendMessage("§cHanya impostor boleh menggunakan menu ini.");
            return;
        }
        if (!game.isAlive(p)) {
            p.sendMessage("§cHantu tidak boleh mensabotaj.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, SABOTAGE_TITLE);
        ItemStack filler = Util.item(Material.BLACK_STAINED_GLASS_PANE, "§8 ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        long left = Math.max(0L, cooldownUntil - System.currentTimeMillis()) / 1000L;
        String status = left > 0 ? "§cCooldown: " + left + "s" : "§aSedia";

        inv.setItem(11, Util.item(Material.REDSTONE_TORCH, "§eMatikan Lampu",
                "§7Crewmate menjadi buta seketika.",
                "§7Tempoh: " + plugin.setting("lights-seconds", 30) + "s",
                status));
        inv.setItem(15, Util.item(Material.TNT, "§cKebocoran Reaktor",
                "§7Crewmate mesti membaiki sebelum masa tamat.",
                "§7Masa: " + plugin.setting("reactor-seconds", 45) + "s",
                "§7Jika gagal, impostor menang.",
                status));
        inv.setItem(22, Util.item(Material.BARRIER, "§7Tutup"));

        p.openInventory(inv);
    }

    /** Dipanggil daripada GameListener untuk klik dalam menu sabotaj. */
    public void handleMenuClick(Player p, ItemStack clicked) {
        if (clicked == null) return;
        Game game = plugin.getGame();
        if (!game.isRunning() || !game.isImpostor(p) || !game.isAlive(p)) {
            p.closeInventory();
            return;
        }

        if (clicked.getType() == Material.BARRIER) {
            p.closeInventory();
            return;
        }
        if (System.currentTimeMillis() < cooldownUntil) {
            long left = (cooldownUntil - System.currentTimeMillis()) / 1000L + 1;
            p.sendMessage("§cSabotaj dalam cooldown: " + left + " saat.");
            p.closeInventory();
            return;
        }
        if (game.getState() == GameState.MEETING) {
            p.sendMessage("§cTidak boleh mensabotaj semasa mesyuarat.");
            p.closeInventory();
            return;
        }

        if (clicked.getType() == Material.REDSTONE_TORCH) {
            if (lightsActive) {
                p.sendMessage("§cLampu sudah pun dimatikan.");
            } else {
                triggerLights();
                p.closeInventory();
            }
        } else if (clicked.getType() == Material.TNT) {
            if (reactorActive) {
                p.sendMessage("§cReaktor sudah pun bocor.");
            } else if (plugin.getLocation("reactor") == null) {
                p.sendMessage("§cLokasi baik pulih reaktor belum ditetapkan (/ib set reactor).");
            } else {
                triggerReactor();
                p.closeInventory();
            }
        }
    }

    private void startCooldown() {
        cooldownUntil = System.currentTimeMillis()
                + (plugin.setting("sabotage-cooldown-seconds", 30) * 1000L);
    }

    // =========================================================
    // Sabotaj lampu
    // =========================================================
    public void triggerLights() {
        if (lightsActive) return;
        lightsActive = true;
        lightsLeft = Math.max(5, plugin.setting("lights-seconds", 30));
        startCooldown();

        Bukkit.broadcastMessage("§e§lSABOTAJ: §7Lampu telah dimatikan!");
        if (plugin.getLocation("lights") != null) {
            Bukkit.broadcastMessage("§7Pergi ke suis lampu dan klik kanan untuk membaikinya.");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 0.6f);
        }

        if (lightsTask != null) lightsTask.cancel();
        lightsTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!lightsActive || !plugin.getGame().isRunning()) {
                    fixLights(null);
                    cancel();
                    return;
                }
                lightsLeft--;
                if (lightsLeft <= 0) {
                    fixLights(null);
                    Bukkit.broadcastMessage("§aLampu telah pulih dengan sendirinya.");
                    cancel();
                    return;
                }
                for (Player p : plugin.getGame().getAlivePlayers()) {
                    if (plugin.getGame().isImpostor(p)) continue;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void fixLights(Player fixer) {
        if (!lightsActive) return;
        lightsActive = false;
        lightsLeft = 0;
        if (lightsTask != null) {
            lightsTask.cancel();
            lightsTask = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        if (fixer != null) {
            Bukkit.broadcastMessage("§a" + fixer.getName() + " telah membaiki lampu!");
            fixer.playSound(fixer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        }
    }

    // =========================================================
    // Sabotaj reaktor
    // =========================================================
    public void triggerReactor() {
        if (reactorActive) return;
        reactorActive = true;
        reactorLeft = Math.max(10, plugin.setting("reactor-seconds", 45));
        startCooldown();

        Bukkit.broadcastMessage("§c§lKECEMASAN: §7Reaktor bocor! Baiki sebelum masa tamat!");
        if (reactorBar == null) {
            reactorBar = Bukkit.createBossBar("§cReaktor", BarColor.RED, BarStyle.SOLID);
        }
        reactorBar.removeAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            reactorBar.addPlayer(p);
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.4f);
        }
        reactorBar.setVisible(true);

        final int total = reactorLeft;
        if (reactorTask != null) reactorTask.cancel();
        reactorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!reactorActive || !plugin.getGame().isRunning()) {
                    clearReactorBar();
                    cancel();
                    return;
                }
                reactorLeft--;
                reactorBar.setProgress(Math.max(0.0, Math.min(1.0, (double) reactorLeft / (double) total)));
                reactorBar.setTitle("§c§lREAKTOR BOCOR §7— §f" + Util.formatTime(Math.max(0, reactorLeft)));

                if (reactorLeft <= 5 && reactorLeft > 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                    }
                }
                if (reactorLeft <= 0) {
                    reactorActive = false;
                    clearReactorBar();
                    cancel();
                    plugin.getGame().impostorWinBySabotage();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void fixReactor(Player fixer) {
        if (!reactorActive) return;
        reactorActive = false;
        reactorLeft = 0;
        if (reactorTask != null) {
            reactorTask.cancel();
            reactorTask = null;
        }
        clearReactorBar();
        if (fixer != null) {
            Bukkit.broadcastMessage("§a" + fixer.getName() + " telah membaiki reaktor!");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            }
        }
    }

    private void clearReactorBar() {
        if (reactorBar != null) {
            reactorBar.removeAll();
            reactorBar.setVisible(false);
        }
    }

    /**
     * Cuba baiki sabotaj melalui blok yang diklik.
     * Pulangkan true jika blok itu memang titik baik pulih.
     */
    public boolean handleFixInteract(Player p, Location clicked) {
        Location lightsLoc = plugin.getLocation("lights");
        Location reactorLoc = plugin.getLocation("reactor");

        if (Util.sameBlock(lightsLoc, clicked)) {
            if (!plugin.getGame().isAlive(p)) {
                p.sendMessage("§8[HANTU] §7Anda tidak boleh membaiki apa-apa.");
            } else if (lightsActive) {
                fixLights(p);
            } else {
                p.sendMessage("§7Lampu berfungsi dengan baik.");
            }
            return true;
        }
        if (Util.sameBlock(reactorLoc, clicked)) {
            if (!plugin.getGame().isAlive(p)) {
                p.sendMessage("§8[HANTU] §7Anda tidak boleh membaiki apa-apa.");
            } else if (reactorActive) {
                fixReactor(p);
            } else {
                p.sendMessage("§7Reaktor stabil.");
            }
            return true;
        }
        return false;
    }

    public void cancelAll() {
        if (lightsTask != null) {
            lightsTask.cancel();
            lightsTask = null;
        }
        if (reactorTask != null) {
            reactorTask.cancel();
            reactorTask = null;
        }
        lightsActive = false;
        reactorActive = false;
        lightsLeft = 0;
        reactorLeft = 0;
        cooldownUntil = 0L;
        clearReactorBar();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
        }
    }
}
