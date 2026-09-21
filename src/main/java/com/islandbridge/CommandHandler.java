package com.islandbridge;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "islandbridge.admin";
    private static final List<String> SET_TYPES =
            Arrays.asList("lobby", "meeting", "button", "lights", "reactor");

    private final IslandBridgeAmongUs plugin;

    public CommandHandler(IslandBridgeAmongUs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help" -> sendHelp(sender);

            case "start" -> {
                if (!checkAdmin(sender)) return true;
                plugin.getGame().start(sender);
            }

            case "stop" -> {
                if (!checkAdmin(sender)) return true;
                if (!plugin.getGame().isRunning()) {
                    sender.sendMessage("§cTiada permainan sedang berjalan.");
                } else {
                    plugin.getGame().forceStop();
                }
            }

            case "set" -> {
                if (!checkAdmin(sender)) return true;
                Player p = asPlayer(sender);
                if (p == null) return true;
                if (args.length != 2) {
                    sender.sendMessage("§cGuna: /ib set <" + String.join("|", SET_TYPES) + ">");
                    return true;
                }
                String type = args[1].toLowerCase(Locale.ROOT);
                if (!SET_TYPES.contains(type)) {
                    sender.sendMessage("§cJenis tidak sah. Pilihan: " + String.join(", ", SET_TYPES));
                    return true;
                }
                if (type.equals("button") || type.equals("lights") || type.equals("reactor")) {
                    Location target = targetBlock(p);
                    if (target == null) {
                        p.sendMessage("§cPandang blok yang hendak ditetapkan (dalam jarak 6 blok).");
                        return true;
                    }
                    plugin.setLocation(type, target);
                    p.sendMessage("§aBlok " + type + " ditetapkan pada X:" + target.getBlockX()
                            + " Y:" + target.getBlockY() + " Z:" + target.getBlockZ());
                } else {
                    plugin.setLocation(type, p.getLocation());
                    p.sendMessage("§aLokasi " + type + " disimpan di kedudukan anda.");
                }
            }

            case "addtask" -> {
                if (!checkAdmin(sender)) return true;
                Player p = asPlayer(sender);
                if (p == null) return true;
                Location target = targetBlock(p);
                if (target == null) {
                    p.sendMessage("§cPandang blok tugasan (dalam jarak 6 blok).");
                    return true;
                }
                if (plugin.getTasks().findTaskPoint(target) != null) {
                    p.sendMessage("§cBlok itu sudah menjadi titik tugasan.");
                    return true;
                }
                plugin.addLocationToList("tasks", target);
                p.sendMessage("§aTitik tugasan ditambah. Jumlah: "
                        + plugin.getTasks().getTaskPoints().size());
            }

            case "addvent" -> {
                if (!checkAdmin(sender)) return true;
                Player p = asPlayer(sender);
                if (p == null) return true;
                Location target = targetBlock(p);
                if (target == null) {
                    p.sendMessage("§cPandang blok vent (dalam jarak 6 blok).");
                    return true;
                }
                if (plugin.getVents().isVent(target)) {
                    p.sendMessage("§cBlok itu sudah menjadi vent.");
                    return true;
                }
                plugin.addLocationToList("vents", target);
                p.sendMessage("§aVent ditambah. Jumlah: " + plugin.getVents().getVents().size());
            }

            case "cleartasks" -> {
                if (!checkAdmin(sender)) return true;
                plugin.clearLocationList("tasks");
                sender.sendMessage("§aSemua titik tugasan dipadam.");
            }

            case "clearvents" -> {
                if (!checkAdmin(sender)) return true;
                plugin.clearLocationList("vents");
                sender.sendMessage("§aSemua vent dipadam.");
            }

            case "list" -> {
                if (!checkAdmin(sender)) return true;
                sender.sendMessage("§6=== Tetapan Peta ===");
                for (String type : SET_TYPES) {
                    Location loc = plugin.getLocation(type);
                    sender.sendMessage("§7" + type + ": " + (loc == null
                            ? "§cbelum ditetapkan"
                            : "§aX:" + loc.getBlockX() + " Y:" + loc.getBlockY() + " Z:" + loc.getBlockZ()));
                }
                sender.sendMessage("§7titik tugasan: §f" + plugin.getTasks().getTaskPoints().size());
                sender.sendMessage("§7vent: §f" + plugin.getVents().getVents().size());
            }

            case "tasks" -> {
                Player p = asPlayer(sender);
                if (p == null) return true;
                if (!plugin.getGame().isRunning()) {
                    p.sendMessage("§cTiada permainan sedang berjalan.");
                    return true;
                }
                plugin.getTasks().sendTaskList(p);
            }

            case "role" -> {
                Player p = asPlayer(sender);
                if (p == null) return true;
                Game game = plugin.getGame();
                if (!game.isRunning() || !game.isInGame(p)) {
                    p.sendMessage("§cAnda tiada dalam pusingan ini.");
                    return true;
                }
                Role role = game.getRole(p);
                p.sendMessage("§7Peranan anda: " + (role == null ? "§7tiada" : role.getDisplay()));
                if (game.isCrewmate(p)) {
                    p.sendMessage("§7Tugasan: §f" + game.getTasksDone(p) + "§7/§f" + game.getTasksPerCrew());
                }
                p.sendMessage("§7Status: " + (game.isAlive(p) ? "§ahidup" : "§8hantu"));
            }

            case "sabotage" -> {
                Player p = asPlayer(sender);
                if (p == null) return true;
                plugin.getSabotage().openMenu(p);
            }

            case "reload" -> {
                if (!checkAdmin(sender)) return true;
                plugin.reloadConfig();
                sender.sendMessage("§aKonfigurasi dimuat semula.");
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private Location targetBlock(Player p) {
        org.bukkit.block.Block block = p.getTargetBlockExact(6);
        return block == null ? null : block.getLocation();
    }

    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage("Arahan ini hanya untuk pemain dalam permainan.");
        return null;
    }

    private boolean checkAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) return true;
        sender.sendMessage("§cAnda tiada kebenaran untuk arahan ini.");
        return false;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== IslandBridge Among Us ===");
        sender.sendMessage("§e/ib role §7- Semak peranan anda");
        sender.sendMessage("§e/ib tasks §7- Senarai tugasan anda");
        sender.sendMessage("§e/ib sabotage §7- Menu sabotaj (impostor)");
        if (sender.hasPermission(ADMIN)) {
            sender.sendMessage("§c--- Admin ---");
            sender.sendMessage("§e/ib start §7- Mula permainan");
            sender.sendMessage("§e/ib stop §7- Henti permainan");
            sender.sendMessage("§e/ib set <" + String.join("|", SET_TYPES) + ">");
            sender.sendMessage("§e/ib addtask §7- Tambah titik tugasan (pandang blok)");
            sender.sendMessage("§e/ib addvent §7- Tambah vent (pandang blok)");
            sender.sendMessage("§e/ib cleartasks §7| §e/ib clearvents");
            sender.sendMessage("§e/ib list §7- Semak tetapan peta");
            sender.sendMessage("§e/ib reload §7- Muat semula config");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("role", "tasks", "sabotage", "help"));
            if (sender.hasPermission(ADMIN)) {
                subs.addAll(Arrays.asList("start", "stop", "set", "addtask", "addvent",
                        "cleartasks", "clearvents", "list", "reload"));
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String s : subs) {
                if (s.startsWith(prefix)) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String s : SET_TYPES) {
                if (s.startsWith(prefix)) out.add(s);
            }
        }
        return out;
    }
}
