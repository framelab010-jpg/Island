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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MeetingManager {

    public static final String VOTE_TITLE = "§8Undian Mesyuarat";
    private static final UUID SKIP = new UUID(0L, 0L);

    private enum Phase { DISCUSSION, VOTING }

    private final IslandBridgeAmongUs plugin;

    private boolean active = false;
    private Phase phase = Phase.DISCUSSION;
    private int secondsLeft = 0;
    private BukkitTask task;
    private BossBar bar;

    private final Map<UUID, UUID> votes = new HashMap<>();

    public MeetingManager(IslandBridgeAmongUs plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    // =========================================================
    // Mula mesyuarat
    // =========================================================
    public void start(String reason, Player caller) {
        Game game = plugin.getGame();
        if (active || game.getState() != GameState.RUNNING) return;

        active = true;
        votes.clear();
        game.setState(GameState.MEETING);
        plugin.getSabotage().cancelAll();
        plugin.getCorpses().clearAll();

        Location meetLoc = plugin.getLocation("meeting");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (meetLoc != null) p.teleport(meetLoc);
            p.closeInventory();
            p.sendTitle("§e§lMESYUARAT", "§7" + Util.stripColor(reason), 10, 50, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.3f);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§c§l" + reason);
        Bukkit.broadcastMessage("§7Anda boleh berbual sekarang. Taip dalam chat.");
        Bukkit.broadcastMessage("");

        if (bar == null) {
            bar = Bukkit.createBossBar("§eMesyuarat", BarColor.YELLOW, BarStyle.SOLID);
        }
        bar.removeAll();
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        bar.setColor(BarColor.YELLOW);
        bar.setVisible(true);

        phase = Phase.DISCUSSION;
        secondsLeft = Math.max(5, plugin.setting("discussion-seconds", 45));
        startTimer();
    }

    private void startTimer() {
        if (task != null) task.cancel();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tick() {
        if (!active) {
            cancelTimer();
            return;
        }
        secondsLeft--;

        if (bar != null) {
            int total = phase == Phase.DISCUSSION
                    ? Math.max(5, plugin.setting("discussion-seconds", 45))
                    : Math.max(5, plugin.setting("voting-seconds", 30));
            double progress = Math.max(0.0, Math.min(1.0, (double) secondsLeft / (double) total));
            bar.setProgress(progress);
            bar.setTitle(phase == Phase.DISCUSSION
                    ? "§eBerbincang §7— §f" + Util.formatTime(Math.max(0, secondsLeft))
                    : "§6Masa Mengundi §7— §f" + Util.formatTime(Math.max(0, secondsLeft)));
        }

        if (phase == Phase.VOTING && everyoneVoted()) {
            secondsLeft = 0;
        }

        if (secondsLeft > 0) return;

        if (phase == Phase.DISCUSSION) {
            beginVoting();
        } else {
            tallyAndEject();
        }
    }

    private void cancelTimer() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // =========================================================
    // Undian
    // =========================================================
    private void beginVoting() {
        phase = Phase.VOTING;
        secondsLeft = Math.max(5, plugin.setting("voting-seconds", 30));
        if (bar != null) bar.setColor(BarColor.RED);

        Bukkit.broadcastMessage("§6§lMASA MENGUNDI! §7Pilih seseorang dalam menu.");
        for (Player p : plugin.getGame().getAlivePlayers()) {
            openVoteGui(p);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f);
        }
        for (Player ghost : plugin.getGame().getGhostPlayers()) {
            ghost.sendMessage("§8[HANTU] §7Anda tidak boleh mengundi.");
        }
    }

    public void openVoteGui(Player voter) {
        Game game = plugin.getGame();
        Inventory inv = Bukkit.createInventory(null, 54, VOTE_TITLE);

        List<Player> aliveList = game.getAlivePlayers();
        int slot = 0;
        for (Player target : aliveList) {
            if (slot >= 45) break;
            String suffix = target.equals(voter) ? " §7(anda)" : "";
            inv.setItem(slot, Util.playerHead(target, "§f" + target.getName() + suffix,
                    "§7Klik untuk mengundi pemain ini."));
            slot++;
        }
        inv.setItem(53, Util.item(Material.BARRIER, "§7Langkau Undi",
                "§7Klik untuk tidak mengundi sesiapa."));

        UUID existing = votes.get(voter.getUniqueId());
        if (existing != null) {
            inv.setItem(49, Util.item(Material.PAPER, "§aAnda sudah mengundi",
                    "§7Undian anda tidak boleh diubah."));
        }
        voter.openInventory(inv);
    }

    /** Dipanggil daripada GameListener apabila pemain klik dalam GUI undian. */
    public void handleVoteClick(Player voter, ItemStack clicked) {
        if (!active || phase != Phase.VOTING) return;
        if (clicked == null || clicked.getItemMeta() == null) return;

        Game game = plugin.getGame();
        if (!game.isAlive(voter)) {
            voter.sendMessage("§8[HANTU] §7Hantu tidak boleh mengundi.");
            voter.closeInventory();
            return;
        }
        if (votes.containsKey(voter.getUniqueId())) {
            voter.sendMessage("§cAnda sudah mengundi.");
            voter.closeInventory();
            return;
        }

        String display = Util.stripColor(clicked.getItemMeta().getDisplayName()).trim();

        if (clicked.getType() == Material.BARRIER) {
            votes.put(voter.getUniqueId(), SKIP);
            voter.sendMessage("§7Anda memilih untuk melangkau undi.");
        } else if (clicked.getType() == Material.PLAYER_HEAD) {
            String name = display.replace("(anda)", "").trim();
            Player target = Bukkit.getPlayerExact(name);
            if (target == null || !game.isAlive(target)) {
                voter.sendMessage("§cPemain itu tidak sah untuk diundi.");
                return;
            }
            votes.put(voter.getUniqueId(), target.getUniqueId());
            voter.sendMessage("§eAnda mengundi §f" + target.getName() + "§e.");
        } else {
            return;
        }

        voter.closeInventory();
        voter.playSound(voter.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        Bukkit.broadcastMessage("§7" + voter.getName() + " telah mengundi. §8("
                + votes.size() + "/" + game.getAlivePlayers().size() + ")");
    }

    private boolean everyoneVoted() {
        List<Player> aliveList = plugin.getGame().getAlivePlayers();
        for (Player p : aliveList) {
            if (!votes.containsKey(p.getUniqueId())) return false;
        }
        return true;
    }

    // =========================================================
    // Kiraan undi
    // =========================================================
    private void tallyAndEject() {
        Game game = plugin.getGame();
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID targetId : votes.values()) {
            counts.merge(targetId, 1, Integer::sum);
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("§6§l=== KEPUTUSAN UNDIAN ===");
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            String name = entry.getKey().equals(SKIP)
                    ? "Langkau"
                    : String.valueOf(Bukkit.getOfflinePlayer(entry.getKey()).getName());
            Bukkit.broadcastMessage("§7" + name + ": §f" + entry.getValue() + " undi");
        }

        UUID best = null;
        int bestCount = 0;
        boolean tie = false;
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
                tie = false;
            } else if (entry.getValue() == bestCount) {
                tie = true;
            }
        }

        if (best == null || tie || best.equals(SKIP)) {
            Bukkit.broadcastMessage("§7Tiada siapa disingkirkan. "
                    + (tie ? "§8(undian seri)" : "§8(majoriti melangkau)"));
            Bukkit.broadcastMessage("");
            finishMeeting();
            return;
        }

        Player ejected = Bukkit.getPlayer(best);
        if (ejected == null) {
            Bukkit.broadcastMessage("§7Pemain itu sudah tiada dalam permainan.");
            Bukkit.broadcastMessage("");
            finishMeeting();
            return;
        }

        boolean wasImpostor = game.isImpostor(ejected);
        game.makeGhost(ejected);
        ejected.sendTitle("§4§lANDA DILONTAR", "§7Anda kini hantu", 10, 60, 20);

        String message = "§f" + ejected.getName() + " §7telah dilontar keluar.";
        if (plugin.setting("confirm-ejects", true)) {
            message += wasImpostor ? " §cDia memang Impostor." : " §bDia bukan Impostor.";
        }
        Bukkit.broadcastMessage(message);
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
        }

        finishMeeting();
    }

    private void finishMeeting() {
        Game game = plugin.getGame();
        active = false;
        cancelTimer();
        votes.clear();
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.closeInventory();
        }

        if (game.getState() == GameState.MEETING) {
            game.setState(GameState.RUNNING);
        }
        game.resetAllKillCooldowns();
        game.checkWin();

        if (game.isRunning()) {
            Bukkit.broadcastMessage("§a§lMESYUARAT TAMAT — permainan diteruskan!");
        }
    }

    /** Hentikan mesyuarat tanpa kiraan undi (contoh: permainan tamat). */
    public void forceEnd() {
        if (!active) return;
        active = false;
        cancelTimer();
        votes.clear();
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
        Game game = plugin.getGame();
        if (game != null && game.getState() == GameState.MEETING) {
            game.setState(GameState.RUNNING);
        }
    }

    public void handleQuit(Player p) {
        votes.remove(p.getUniqueId());
    }

    public boolean isVotingPhase() {
        return active && phase == Phase.VOTING;
    }

    public boolean hasVoted(Player p) {
        return votes.containsKey(p.getUniqueId());
    }
}
