package com.mikaeel.nickbook;

import com.mikaeel.nickbook.chat.ChatCommands;
import com.mikaeel.nickbook.chat.ChatManager;
import com.mikaeel.nickbook.command.NickCommand;
import com.mikaeel.nickbook.data.PlayerData;
import com.mikaeel.nickbook.hook.LuckPermsHook;
import com.mikaeel.nickbook.hook.PapiHook;
import com.mikaeel.nickbook.listener.NickListener;
import com.mikaeel.nickbook.service.NickService;
import com.mikaeel.nickbook.tab.TabManager;
import com.mikaeel.nickbook.util.ActionBar;
import com.mikaeel.nickbook.util.Animations;
import com.mikaeel.nickbook.util.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class NickBookPlugin extends JavaPlugin {
    private static NickBookPlugin instance;

    private PlayerData data;
    private NickService nickService;
    private ChatManager chat;
    private TabManager tab;
    private final Animations animations = new Animations();
    private boolean papi;

    public static NickBookPlugin get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        for (String file : new String[]{"chat.yml", "tab.yml"}) {
            if (!new File(getDataFolder(), file).exists()) saveResource(file, false);
        }
        data = new PlayerData(this);
        data.load();
        nickService = new NickService(this, data);
        chat = new ChatManager(this);
        tab = new TabManager(this);

        NickCommand command = new NickCommand(this, nickService);
        getCommand("nick").setExecutor(command);
        getCommand("nick").setTabCompleter(command);
        ChatCommands chatCommands = new ChatCommands(this);
        for (String name : ChatCommands.commandNames()) {
            PluginCommand c = getCommand(name);
            if (c == null) continue;
            c.setExecutor(chatCommands);
            c.setTabCompleter(chatCommands);
        }
        getServer().getPluginManager().registerEvents(new NickListener(this, nickService), this);
        getServer().getPluginManager().registerEvents(chat, this);
        getServer().getPluginManager().registerEvents(tab, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                papi = true;
                if (PapiHook.register(this)) getLogger().info("Hooked into PlaceholderAPI (%nickbook_...% placeholders).");
            } catch (Throwable t) {
                papi = false;
                getLogger().warning("PlaceholderAPI hook failed: " + t);
            }
        } else {
            getLogger().warning("PlaceholderAPI not found: only NickBook's {built-in} placeholders will work.");
        }

        // Run module setup one tick later so TAB/LPC/LuckPerms are fully enabled.
        Bukkit.getScheduler().runTask(this, this::reloadModules);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            String text = getConfig().getString("nicked-actionbar", "&fYou are currently &cNICKED");
            if (text == null || text.isEmpty()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (nickService.isNicked(player)) ActionBar.send(player, Placeholders.format(player, text));
            }
        }, 20L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, () -> data.save(), 200L, 200L);
    }

    @Override
    public void onDisable() {
        if (tab != null) tab.shutdown();
        if (chat != null) chat.shutdown();
        if (data != null) data.save();
    }

    private void reloadModules() {
        LuckPermsHook.reset();
        chat.reload();
        tab.reload();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (nickService.isNicked(p)) nickService.applyNames(p);
        }
    }

    public void reloadAll() {
        reloadConfig();
        reloadModules();
    }

    /** Loads a yml from the data folder with the jar copy as defaults for missing keys. */
    public YamlConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        InputStream in = getResource(name);
        if (in != null) yml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        return yml;
    }

    public PlayerData getData() { return data; }
    public NickService getNickService() { return nickService; }
    public ChatManager getChat() { return chat; }
    public TabManager getTab() { return tab; }
    public Animations getAnimations() { return animations; }
    public boolean hasPapi() { return papi; }
}
