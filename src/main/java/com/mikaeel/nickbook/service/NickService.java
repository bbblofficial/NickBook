package com.mikaeel.nickbook.service;

import com.mikaeel.nickbook.NickBookPlugin;
import com.mikaeel.nickbook.data.PlayerData;
import com.mikaeel.nickbook.hook.LuckPermsHook;
import com.mikaeel.nickbook.model.NickProfile;
import com.mikaeel.nickbook.util.Colors;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class NickService {
    private final NickBookPlugin plugin;
    private final PlayerData data;

    public NickService(NickBookPlugin plugin, PlayerData data) {
        this.plugin = plugin;
        this.data = data;
    }

    public NickProfile get(Player player) {
        UUID id = player.getUniqueId();
        return new NickProfile(data.getString(id, "rank", "DEFAULT"), data.getString(id, "skin", "normal"), data.getString(id, "name", player.getName()));
    }

    /** @return the real rank key (case corrected) or null if it does not exist. */
    public String findRank(String rank) {
        ConfigurationSection ranks = plugin.getConfig().getConfigurationSection("ranks");
        if (ranks == null || rank == null) return null;
        for (String key : ranks.getKeys(false)) if (key.equalsIgnoreCase(rank)) return key;
        return null;
    }

    public boolean setRank(Player p, String rank) {
        String key = findRank(rank);
        if (key == null) return false;
        data.set(p.getUniqueId(), "rank", key);
        if (isNicked(p)) applySilently(p);
        return true;
    }

    public void setSkin(Player p, String skin) {
        data.set(p.getUniqueId(), "skin", skin);
    }

    public String randomName(Player p) {
        List<String> names = new ArrayList<String>(plugin.getConfig().getStringList("random-names"));
        if (names.isEmpty()) names.add("PixelPilot");
        String old = get(p).name();
        if (names.size() > 1) names.removeIf(n -> n.equalsIgnoreCase(old));
        if (names.size() > 1) names.removeIf(n -> Bukkit.getPlayerExact(n) != null); // never copy a real online player
        String value = names.get(ThreadLocalRandom.current().nextInt(names.size()));
        data.set(p.getUniqueId(), "candidate", value);
        return value;
    }

    public String candidate(Player p) {
        String value = data.getString(p.getUniqueId(), "candidate", null);
        return value != null ? value : randomName(p);
    }

    public void useCandidate(Player p) {
        data.set(p.getUniqueId(), "name", candidate(p));
        applySilently(p);
    }

    public void reset(Player p) {
        data.set(p.getUniqueId(), "active", false);
        applyNames(p);
        p.sendMessage(color("&aNickname reset."));
    }

    public List<String> ranks() {
        ConfigurationSection ranks = plugin.getConfig().getConfigurationSection("ranks");
        return ranks == null ? Collections.<String>emptyList() : new ArrayList<String>(ranks.getKeys(false));
    }

    /** Marks the player nicked and refreshes every place their name is shown. */
    public void applySilently(Player p) {
        data.set(p.getUniqueId(), "active", true);
        applyNames(p);
    }

    /** Display name, tab name and nametag (through the tab module when it is active). */
    public void applyNames(Player p) {
        p.setDisplayName(isNicked(p) ? formattedName(p) : p.getName());
        if (plugin.getTab() != null && plugin.getTab().isActive()) {
            plugin.getTab().update(p);
            return;
        }
        if (isNicked(p)) {
            String list = Colors.cut(formattedName(p), 16); // 1.8 safe length
            p.setPlayerListName(list);
        } else {
            p.setPlayerListName(null);
        }
    }

    public String formattedName(Player p) { return shownPrefix(p) + shownName(p); }
    public String shownName(Player p) { return isNicked(p) ? get(p).name() : p.getName(); }

    public String shownPrefix(Player p) {
        if (isNicked(p)) return color(plugin.getConfig().getString("ranks." + get(p).rank(), "&7"));
        return color(LuckPermsHook.prefix(p));
    }

    public String shownSuffix(Player p) {
        if (isNicked(p)) return "";
        return color(LuckPermsHook.suffix(p));
    }

    public boolean isNicked(Player p) { return data.getBool(p.getUniqueId(), "active", false); }

    public String color(String text) { return Colors.color(text); }
}
