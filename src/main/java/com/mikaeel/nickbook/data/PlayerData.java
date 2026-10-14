package com.mikaeel.nickbook.data;

import com.mikaeel.nickbook.NickBookPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Player data in players.yml (thread safe, saved in batches).
 * Older versions stored this inside config.yml under "players"; that is migrated automatically.
 */
public final class PlayerData {
    private final NickBookPlugin plugin;
    private final File file;
    private YamlConfiguration yml = new YamlConfiguration();
    private boolean dirty;

    public PlayerData(NickBookPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public synchronized void load() {
        yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection old = plugin.getConfig().getConfigurationSection("players");
        if (old != null) {
            for (String key : old.getKeys(true)) {
                if (!old.isConfigurationSection(key)) yml.set(key, old.get(key));
            }
            plugin.getConfig().set("players", null);
            plugin.saveConfig();
            dirty = true;
            save();
            plugin.getLogger().info("Moved player data from config.yml to players.yml.");
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        try {
            yml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
        }
    }

    private static String path(UUID id, String key) { return id + "." + key; }

    public synchronized String getString(UUID id, String key, String def) { return yml.getString(path(id, key), def); }
    public synchronized boolean getBool(UUID id, String key, boolean def) { return yml.getBoolean(path(id, key), def); }
    public synchronized List<String> getList(UUID id, String key) { return new ArrayList<String>(yml.getStringList(path(id, key))); }

    public synchronized void set(UUID id, String key, Object value) {
        yml.set(path(id, key), value);
        dirty = true;
    }

    // ---- chat helpers ----
    public boolean isIgnoring(UUID who, UUID target) { return getList(who, "ignored").contains(target.toString()); }

    /** @return true if now ignoring, false if un-ignored. */
    public synchronized boolean toggleIgnore(UUID who, UUID target) {
        List<String> list = getList(who, "ignored");
        boolean now;
        if (list.remove(target.toString())) now = false;
        else { list.add(target.toString()); now = true; }
        set(who, "ignored", list);
        return now;
    }
}
