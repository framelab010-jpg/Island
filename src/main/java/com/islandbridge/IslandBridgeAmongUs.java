package com.islandbridge;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class IslandBridgeAmongUs extends JavaPlugin {

    private Game game;
    private CorpseManager corpses;
    private TaskManager tasks;
    private VentManager vents;
    private SabotageManager sabotage;
    private MeetingManager meetings;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        game = new Game(this);
        corpses = new CorpseManager(this);
        tasks = new TaskManager(this);
        vents = new VentManager(this);
        sabotage = new SabotageManager(this);
        meetings = new MeetingManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        if (getCommand("ib") != null) {
            CommandHandler handler = new CommandHandler(this);
            getCommand("ib").setExecutor(handler);
            getCommand("ib").setTabCompleter(handler);
        }

        getLogger().info("IslandBridgeAmongUs aktif.");
    }

    @Override
    public void onDisable() {
        if (meetings != null) meetings.forceEnd();
        if (sabotage != null) sabotage.cancelAll();
        if (corpses != null) corpses.clearAll();
        if (game != null) game.shutdown();
    }

    // ---------------------------------------------------------
    // Getter manager
    // ---------------------------------------------------------
    public Game getGame() {
        return game;
    }

    public CorpseManager getCorpses() {
        return corpses;
    }

    public TaskManager getTasks() {
        return tasks;
    }

    public VentManager getVents() {
        return vents;
    }

    public SabotageManager getSabotage() {
        return sabotage;
    }

    public MeetingManager getMeetings() {
        return meetings;
    }

    // ---------------------------------------------------------
    // Pembantu konfigurasi
    // ---------------------------------------------------------
    public int setting(String key, int fallback) {
        return getConfig().getInt("settings." + key, fallback);
    }

    public boolean setting(String key, boolean fallback) {
        return getConfig().getBoolean("settings." + key, fallback);
    }

    public Location getLocation(String key) {
        return getConfig().getLocation("locations." + key);
    }

    public void setLocation(String key, Location loc) {
        getConfig().set("locations." + key, loc);
        saveConfig();
    }

    public List<Location> getLocationList(String key) {
        List<Location> out = new ArrayList<>();
        List<?> raw = getConfig().getList("points." + key);
        if (raw == null) return out;
        for (Object o : raw) {
            if (o instanceof Location loc) out.add(loc);
        }
        return out;
    }

    public void addLocationToList(String key, Location loc) {
        List<Location> current = getLocationList(key);
        current.add(loc);
        getConfig().set("points." + key, current);
        saveConfig();
    }

    public void clearLocationList(String key) {
        getConfig().set("points." + key, new ArrayList<Location>());
        saveConfig();
    }
}
