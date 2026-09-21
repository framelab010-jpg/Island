package com.islandbridge;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final IslandBridgeAmongUs plugin;

    public GameListener(IslandBridgeAmongUs plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    // Sambung / keluar
    // =========================================================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Game game = plugin.getGame();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            game.addToTaskBar(p);
            if (game.isRunning()) {
                if (game.isGhost(p)) {
                    game.makeGhost(p);
                } else if (!game.isInGame(p)) {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.sendMessage("§7Permainan sedang berjalan. Anda akan menyertai pusingan seterusnya.");
                }
            }
            game.updateVisibility();
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getGame().handleQuit(e.getPlayer());
    }

    // =========================================================
    // Kematian
    // =========================================================
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Game game = plugin.getGame();
        Player victim = e.getEntity();
        if (!game.isRunning() || !game.isAlive(victim)) return;

        e.setDeathMessage(null);
        e.getDrops().clear();
        e.setDroppedExp(0);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (victim.isOnline()) {
                game.killPlayer(victim, null);
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Game game = plugin.getGame();
        if (!game.isRunning()) return;
        Location loc = plugin.getLocation("meeting");
        if (loc == null) loc = plugin.getLocation("lobby");
        if (loc != null) e.setRespawnLocation(loc);
    }

    // =========================================================
    // Kecederaan & pembunuhan
    // =========================================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        Game game = plugin.getGame();
        if (!game.isRunning()) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (!game.isInGame(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKill(EntityDamageByEntityEvent e) {
        Game game = plugin.getGame();
        if (!game.isRunning()) return;
        if (!(e.getDamager() instanceof Player killer)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        e.setCancelled(true);

        if (!game.isImpostor(killer) || !game.isAlive(killer)) return;
        if (game.getState() == GameState.MEETING) {
            killer.sendMessage("§cTidak boleh membunuh semasa mesyuarat.");
            return;
        }
        if (!game.isAlive(victim)) return;
        if (game.isImpostor(victim)) {
            killer.sendMessage("§cDia rakan impostor anda.");
            return;
        }

        long ready = game.getKillReadyAt(killer);
        long now = System.currentTimeMillis();
        if (now < ready) {
            killer.sendMessage("§cCooldown bunuh: " + ((ready - now) / 1000 + 1) + " saat.");
            return;
        }

        game.applyKillCooldown(killer);
        Location spot = victim.getLocation().clone();
        game.killPlayer(victim, killer);
        killer.teleport(spot);
    }

    // =========================================================
    // Interaksi blok
    // =========================================================
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player p = e.getPlayer();
        Game game = plugin.getGame();
        Action action = e.getAction();

        // Menu sabotaj melalui kompas
        ItemStack held = e.getItem();
        if (held != null && held.getType() == Material.COMPASS
                && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
            plugin.getSabotage().openMenu(p);
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Location clicked = e.getClickedBlock().getLocation();

        // Butang mesyuarat kecemasan
        Location button = plugin.getLocation("button");
        if (Util.sameBlock(button, clicked)) {
            e.setCancelled(true);
            handleEmergencyButton(p);
            return;
        }

        // Titik baik pulih sabotaj
        if (plugin.getSabotage().handleFixInteract(p, clicked)) {
            e.setCancelled(true);
            return;
        }

        // Vent (menunduk + klik kanan)
        if (plugin.getVents().isVent(clicked)) {
            e.setCancelled(true);
            if (p.isSneaking()) {
                plugin.getVents().handleInteract(p, clicked);
            } else {
                p.sendMessage("§7Menunduk (Shift) + klik kanan untuk menggunakan vent.");
            }
            return;
        }

        // Titik tugasan
        if (plugin.getTasks().handleInteract(p, clicked)) {
            e.setCancelled(true);
            return;
        }

        // Halang pemain mengusik dunia semasa permainan
        if (game.isRunning() && game.isInGame(p)) {
            Material type = e.getClickedBlock().getType();
            if (type.isInteractable()) {
                e.setCancelled(true);
            }
        }
    }

    private void handleEmergencyButton(Player p) {
        Game game = plugin.getGame();
        if (!game.isRunning() || !game.isInGame(p)) return;
        if (game.getState() == GameState.MEETING) return;
        if (!game.isAlive(p)) {
            p.sendMessage("§8[HANTU] §7Anda tidak boleh memanggil mesyuarat.");
            return;
        }
        if (plugin.getSabotage().isReactorActive()) {
            p.sendMessage("§cTidak boleh memanggil mesyuarat semasa kecemasan!");
            return;
        }
        if (!game.canCallEmergency(p)) {
            p.sendMessage("§cAnda sudah kehabisan mesyuarat kecemasan.");
            return;
        }
        game.useEmergency(p);
        p.sendMessage("§7Baki mesyuarat kecemasan anda: " + game.getEmergencyLeft(p));
        plugin.getMeetings().start(p.getName() + " menekan butang kecemasan!", p);
    }

    // =========================================================
    // Lapor mayat
    // =========================================================
    @EventHandler
    public void onCorpseClick(PlayerInteractAtEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof ArmorStand stand)) return;
        if (!plugin.getCorpses().isCorpse(stand)) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        Game game = plugin.getGame();

        if (!game.isRunning() || game.getState() == GameState.MEETING) return;
        if (!game.isAlive(p)) {
            p.sendMessage("§8[HANTU] §7Anda tidak boleh melaporkan mayat.");
            return;
        }
        if (plugin.getSabotage().isReactorActive()) {
            p.sendMessage("§cBaiki kecemasan dahulu!");
            return;
        }

        String victimName = plugin.getCorpses().getVictimName(stand);
        plugin.getMeetings().start(p.getName() + " menjumpai mayat " + victimName + "!", p);
    }

    // =========================================================
    // Pergerakan semasa mesyuarat
    // =========================================================
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (plugin.getGame().getState() != GameState.MEETING) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getWorld() == null || to.getWorld() == null) return;
        if (!from.getWorld().getName().equals(to.getWorld().getName())) return;

        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            e.setTo(locked);
        }
    }

    // =========================================================
    // Chat
    // =========================================================
    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Game game = plugin.getGame();
        if (!game.isRunning() || !game.isInGame(p)) return;

        e.setCancelled(true);
        String message = e.getMessage();

        if (game.isGhost(p)) {
            String out = "§8[HANTU] §7" + p.getName() + ": §f" + message;
            for (Player ghost : game.getGhostPlayers()) {
                ghost.sendMessage(out);
            }
            Bukkit.getConsoleSender().sendMessage(out);
            return;
        }

        if (game.getState() != GameState.MEETING && plugin.setting("chat-only-in-meeting", true)) {
            p.sendMessage("§cAnda hanya boleh berbual semasa mesyuarat.");
            return;
        }

        String out = "§f" + p.getName() + "§7: §f" + message;
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.sendMessage(out);
        }
        Bukkit.getConsoleSender().sendMessage(out);
    }

    // =========================================================
    // GUI
    // =========================================================
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (title.equals(MeetingManager.VOTE_TITLE)) {
            e.setCancelled(true);
            plugin.getMeetings().handleVoteClick(p, e.getCurrentItem());
        } else if (title.equals(SabotageManager.SABOTAGE_TITLE)) {
            e.setCancelled(true);
            plugin.getSabotage().handleMenuClick(p, e.getCurrentItem());
        } else if (title.equals(TaskManager.TASK_TITLE)) {
            e.setCancelled(true);
            if (e.getRawSlot() >= 0 && e.getRawSlot() < 27) {
                plugin.getTasks().handleGuiClick(p, e.getRawSlot());
            }
        } else if (plugin.getGame().isRunning() && plugin.getGame().isInGame(p)) {
            // Halang pemain mengubah inventori semasa permainan
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        String title = e.getView().getTitle();

        if (title.equals(TaskManager.TASK_TITLE)) {
            plugin.getTasks().closeGui(p);
        } else if (title.equals(MeetingManager.VOTE_TITLE)) {
            MeetingManager meetings = plugin.getMeetings();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (meetings.isVotingPhase()
                        && plugin.getGame().isAlive(p)
                        && !meetings.hasVoted(p)) {
                    meetings.openVoteGui(p);
                }
            }, 5L);
        }
    }

    // =========================================================
    // Perlindungan dunia
    // =========================================================
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Game game = plugin.getGame();
        if (game.isRunning() && game.isInGame(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game game = plugin.getGame();
        if (game.isRunning() && game.isInGame(p)) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Game game = plugin.getGame();
        if (game.isRunning() && game.isInGame(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Game game = plugin.getGame();
        if (game.isRunning() && game.isInGame(e.getPlayer())) {
            e.setCancelled(true);
        }
    }
}
