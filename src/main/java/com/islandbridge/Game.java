package com.islandbridge;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Game {

    private final IslandBridgeAmongUs plugin;

    private GameState state = GameState.LOBBY;

    private final Map<UUID, Role> roles = new HashMap<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Map<UUID, Integer> tasksDone = new HashMap<>();
    private final Map<UUID, Long> killCooldown = new HashMap<>();
    private final Map<UUID, Integer> emergencyUsed = new HashMap<>();

    private BossBar taskBar;
    private Team ghostTeam;

    private int tasksPerCrew = 3;

    public Game(IslandBridgeAmongUs plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    // Keadaan
    // =========================================================
    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public boolean isRunning() {
        return state == GameState.RUNNING || state == GameState.MEETING;
    }

    public boolean isInGame(Player p) {
        return roles.containsKey(p.getUniqueId());
    }

    public Role getRole(Player p) {
        return roles.get(p.getUniqueId());
    }

    public boolean isImpostor(Player p) {
        return roles.get(p.getUniqueId()) == Role.IMPOSTOR;
    }

    public boolean isCrewmate(Player p) {
        return roles.get(p.getUniqueId()) == Role.CREWMATE;
    }

    public boolean isAlive(Player p) {
        return alive.contains(p.getUniqueId());
    }

    public boolean isGhost(Player p) {
        return isInGame(p) && !isAlive(p);
    }

    public int getTasksPerCrew() {
        return tasksPerCrew;
    }

    public List<Player> getAlivePlayers() {
        List<Player> out = new ArrayList<>();
        for (UUID id : alive) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    public List<Player> getGhostPlayers() {
        List<Player> out = new ArrayList<>();
        for (UUID id : roles.keySet()) {
            if (alive.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    public List<Player> getImpostorPlayers() {
        List<Player> out = new ArrayList<>();
        for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
            if (entry.getValue() != Role.IMPOSTOR) continue;
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    // =========================================================
    // Mula permainan
    // =========================================================
    public boolean start(CommandSender sender) {
        if (isRunning()) {
            sender.sendMessage("§cPermainan sudah berjalan. Guna /ib stop dahulu.");
            return false;
        }
        if (plugin.getLocation("meeting") == null) {
            sender.sendMessage("§cLokasi mesyuarat belum ditetapkan. Guna /ib set meeting.");
            return false;
        }

        int minPlayers = plugin.setting("min-players", 3);
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.size() < minPlayers) {
            sender.sendMessage("§cPerlu sekurang-kurangnya " + minPlayers + " pemain. Sekarang: " + players.size());
            return false;
        }

        int taskPoints = plugin.getTasks().getTaskPoints().size();
        if (taskPoints == 0) {
            sender.sendMessage("§cTiada titik tugasan. Guna /ib addtask sambil melihat blok tugasan.");
            return false;
        }

        clearAll();

        tasksPerCrew = Math.max(1, Math.min(plugin.setting("tasks-per-crewmate", 3), taskPoints));
        if (tasksPerCrew < plugin.setting("tasks-per-crewmate", 3)) {
            sender.sendMessage("§eAmaran: hanya " + taskPoints + " titik tugasan wujud, "
                    + "jadi setiap crewmate diberi " + tasksPerCrew + " tugasan.");
        }

        Collections.shuffle(players);
        int threshold = plugin.setting("large-threshold", 7);
        int impostorCount = players.size() >= threshold
                ? plugin.setting("impostors-large", 2)
                : plugin.setting("impostors-small", 1);
        impostorCount = Math.max(1, Math.min(impostorCount, players.size() - 1));

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            preparePlayer(p);
            alive.add(p.getUniqueId());
            emergencyUsed.put(p.getUniqueId(), 0);

            if (i < impostorCount) {
                roles.put(p.getUniqueId(), Role.IMPOSTOR);
            } else {
                roles.put(p.getUniqueId(), Role.CREWMATE);
                tasksDone.put(p.getUniqueId(), 0);
            }
        }

        state = GameState.RUNNING;
        setupGhostTeam();
        plugin.getTasks().resetAssignments();

        long ready = System.currentTimeMillis() + (plugin.setting("kill-cooldown-seconds", 20) * 1000L);
        for (Player p : players) {
            if (isImpostor(p)) {
                killCooldown.put(p.getUniqueId(), ready);
                giveImpostorItems(p);
                p.sendTitle("§c§lIMPOSTOR", "§7Bunuh, sabotaj dan menang", 10, 60, 20);
                p.sendMessage("§cAnda IMPOSTOR.");
                p.sendMessage("§7Pukul crewmate untuk membunuh (cooldown "
                        + plugin.setting("kill-cooldown-seconds", 20) + "s).");
                p.sendMessage("§7Klik kanan §cKompas §7untuk menu sabotaj.");
                p.sendMessage("§7Menunduk + klik kanan pada vent untuk berpindah.");

                List<String> partners = new ArrayList<>();
                for (Player other : getImpostorPlayers()) {
                    if (!other.equals(p)) partners.add(other.getName());
                }
                if (!partners.isEmpty()) {
                    p.sendMessage("§cRakan impostor: §f" + String.join(", ", partners));
                }
            } else {
                p.sendTitle("§b§lCREWMATE", "§7Siapkan tugasan anda", 10, 60, 20);
                p.sendMessage("§bAnda CREWMATE. Siapkan " + tasksPerCrew + " tugasan.");
                p.sendMessage("§7Klik kanan blok tugasan yang bercahaya dalam peta.");
                p.sendMessage("§7Klik kanan mayat untuk melaporkannya.");
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
        }

        createTaskBar();
        updateTaskBar();
        updateVisibility();

        Bukkit.broadcastMessage("§a§lPERMAINAN BERMULA! §7(" + impostorCount + " impostor daripada "
                + players.size() + " pemain)");
        return true;
    }

    private void preparePlayer(Player p) {
        p.setGameMode(GameMode.ADVENTURE);
        p.getInventory().clear();
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        Location lobby = plugin.getLocation("lobby");
        if (lobby != null) p.teleport(lobby);
    }

    public void giveImpostorItems(Player p) {
        p.getInventory().clear();
        p.getInventory().setItem(0, Util.item(Material.IRON_SWORD, "§cPisau Impostor",
                "§7Pukul crewmate untuk membunuh."));
        p.getInventory().setItem(8, Util.item(Material.COMPASS, "§cMenu Sabotaj",
                "§7Klik kanan untuk membuka menu."));
    }

    // =========================================================
    // Kematian dan hantu
    // =========================================================
    public void killPlayer(Player victim, Player killer) {
        if (!isAlive(victim)) return;

        plugin.getCorpses().spawn(victim, victim.getLocation());
        makeGhost(victim);

        victim.sendTitle("§4§lANDA MATI", "§7Anda kini hantu — teruskan tugasan", 10, 50, 20);
        victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        if (killer != null) {
            killer.sendMessage("§cAnda membunuh " + victim.getName() + ".");
        }
        checkWin();
    }

    /** Tukar pemain menjadi hantu tanpa meninggalkan mayat (contoh: disingkir undi). */
    public void makeGhost(Player p) {
        alive.remove(p.getUniqueId());
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setFlySpeed(0.12f);
        p.setHealth(20.0);
        p.setFireTicks(0);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        if (ghostTeam != null) ghostTeam.addEntry(p.getName());
        p.sendMessage("§8[HANTU] §7Hanya hantu lain boleh melihat dan membaca mesej anda.");
        updateVisibility();
        updateTaskBar();
    }

    /** Sembunyikan hantu daripada pemain yang masih hidup. */
    public void updateVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean viewerGhost = isGhost(viewer);
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                boolean targetGhost = isGhost(target);
                if (targetGhost && !viewerGhost) {
                    viewer.hidePlayer(plugin, target);
                } else {
                    viewer.showPlayer(plugin, target);
                }
            }
        }
    }

    private void setupGhostTeam() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getMainScoreboard();
        Team existing = board.getTeam("ib_ghost");
        if (existing != null) existing.unregister();
        ghostTeam = board.registerNewTeam("ib_ghost");
        ghostTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        ghostTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        ghostTeam.setCanSeeFriendlyInvisibles(true);
    }

    private void removeGhostTeam() {
        if (ghostTeam != null) {
            try {
                ghostTeam.unregister();
            } catch (IllegalStateException ignored) {
                // pasukan sudah dibuang
            }
            ghostTeam = null;
        }
    }

    // =========================================================
    // Cooldown bunuh
    // =========================================================
    public long getKillReadyAt(Player p) {
        return killCooldown.getOrDefault(p.getUniqueId(), 0L);
    }

    public void applyKillCooldown(Player p) {
        killCooldown.put(p.getUniqueId(),
                System.currentTimeMillis() + (plugin.setting("kill-cooldown-seconds", 20) * 1000L));
    }

    public void resetAllKillCooldowns() {
        long ready = System.currentTimeMillis() + (plugin.setting("kill-cooldown-seconds", 20) * 1000L);
        for (Player p : getImpostorPlayers()) {
            killCooldown.put(p.getUniqueId(), ready);
        }
    }

    // =========================================================
    // Mesyuarat kecemasan
    // =========================================================
    public boolean canCallEmergency(Player p) {
        int limit = plugin.setting("emergency-meetings-per-player", 1);
        return emergencyUsed.getOrDefault(p.getUniqueId(), 0) < limit;
    }

    public void useEmergency(Player p) {
        emergencyUsed.merge(p.getUniqueId(), 1, Integer::sum);
    }

    public int getEmergencyLeft(Player p) {
        int limit = plugin.setting("emergency-meetings-per-player", 1);
        return Math.max(0, limit - emergencyUsed.getOrDefault(p.getUniqueId(), 0));
    }

    // =========================================================
    // Tugasan
    // =========================================================
    public int getTasksDone(Player p) {
        return tasksDone.getOrDefault(p.getUniqueId(), 0);
    }

    public void addTaskDone(Player p) {
        tasksDone.merge(p.getUniqueId(), 1, Integer::sum);
        updateTaskBar();
    }

    public int totalTasksNeeded() {
        int crew = 0;
        for (Role role : roles.values()) {
            if (role == Role.CREWMATE) crew++;
        }
        return crew * tasksPerCrew;
    }

    public int totalTasksDone() {
        int total = 0;
        for (int v : tasksDone.values()) total += v;
        return total;
    }

    private void createTaskBar() {
        if (taskBar == null) {
            taskBar = Bukkit.createBossBar("§aTugasan", BarColor.GREEN, BarStyle.SEGMENTED_10);
        }
        taskBar.removeAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            taskBar.addPlayer(p);
        }
        taskBar.setVisible(true);
    }

    public void updateTaskBar() {
        if (taskBar == null) return;
        int needed = totalTasksNeeded();
        int done = Math.min(totalTasksDone(), needed);
        double progress = needed <= 0 ? 0.0 : (double) done / (double) needed;
        taskBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        taskBar.setTitle("§aTugasan Crewmate §7— §f" + done + "§7/§f" + needed);
    }

    public void addToTaskBar(Player p) {
        if (taskBar != null && taskBar.isVisible()) taskBar.addPlayer(p);
    }

    // =========================================================
    // Kemenangan
    // =========================================================
    public void checkWin() {
        if (!isRunning()) return;

        int aliveImp = 0;
        int aliveCrew = 0;
        for (UUID id : alive) {
            if (roles.get(id) == Role.IMPOSTOR) aliveImp++;
            else aliveCrew++;
        }

        if (aliveImp == 0) {
            end("§b§lCREWMATE MENANG!", "§7Semua impostor telah disingkirkan.");
            return;
        }
        if (aliveCrew <= aliveImp) {
            end("§c§lIMPOSTOR MENANG!", "§7Jumlah impostor menyamai jumlah crewmate.");
            return;
        }
        int needed = totalTasksNeeded();
        if (needed > 0 && totalTasksDone() >= needed) {
            end("§b§lCREWMATE MENANG!", "§7Semua tugasan telah siap.");
        }
    }

    public void impostorWinBySabotage() {
        if (!isRunning()) return;
        end("§c§lIMPOSTOR MENANG!", "§7Sabotaj tidak diperbaiki tepat pada masanya.");
    }

    public void end(String title, String subtitle) {
        if (state == GameState.ENDED || state == GameState.LOBBY) return;
        state = GameState.ENDED;

        plugin.getMeetings().forceEnd();
        plugin.getSabotage().cancelAll();
        plugin.getCorpses().clearAll();

        List<String> impostorNames = new ArrayList<>();
        for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
            if (entry.getValue() != Role.IMPOSTOR) continue;
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            impostorNames.add(name != null ? name : "?");
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(title);
        Bukkit.broadcastMessage(subtitle);
        Bukkit.broadcastMessage("§7Impostor ialah: §c" + String.join(", ", impostorNames));
        Bukkit.broadcastMessage("");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(title, subtitle, 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::resetToLobby, 120L);
    }

    private void resetToLobby() {
        Location lobby = plugin.getLocation("lobby");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setFlySpeed(0.1f);
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            if (lobby != null) p.teleport(lobby);
        }
        clearAll();
        state = GameState.LOBBY;
        Bukkit.broadcastMessage("§7Semua pemain telah dikembalikan ke lobi.");
    }

    /** Hentikan permainan serta-merta tanpa pemenang. */
    public void forceStop() {
        if (state == GameState.LOBBY) return;
        state = GameState.ENDED;
        plugin.getMeetings().forceEnd();
        plugin.getSabotage().cancelAll();
        plugin.getCorpses().clearAll();
        Bukkit.broadcastMessage("§e§lPERMAINAN DIHENTIKAN OLEH ADMIN.");
        resetToLobby();
    }

    private void clearAll() {
        roles.clear();
        alive.clear();
        tasksDone.clear();
        killCooldown.clear();
        emergencyUsed.clear();
        if (taskBar != null) {
            taskBar.removeAll();
            taskBar.setVisible(false);
        }
        removeGhostTeam();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(target)) viewer.showPlayer(plugin, target);
            }
        }
    }

    /** Dipanggil ketika plugin dimatikan. */
    public void shutdown() {
        if (taskBar != null) {
            taskBar.removeAll();
            taskBar = null;
        }
        removeGhostTeam();
        roles.clear();
        alive.clear();
    }

    /** Pemain keluar dari server. */
    public void handleQuit(Player p) {
        UUID id = p.getUniqueId();
        if (!roles.containsKey(id)) return;
        boolean wasAlive = alive.remove(id);
        if (wasAlive) {
            Bukkit.broadcastMessage("§7" + p.getName() + " telah meninggalkan permainan.");
        }
        killCooldown.remove(id);
        if (ghostTeam != null) ghostTeam.removeEntry(p.getName());
        plugin.getMeetings().handleQuit(p);
        Bukkit.getScheduler().runTaskLater(plugin, this::checkWin, 2L);
    }

    public Set<UUID> getAliveIds() {
        return new HashSet<>(alive);
    }
}
