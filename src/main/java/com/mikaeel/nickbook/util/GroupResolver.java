package com.mikaeel.nickbook.util;

import com.mikaeel.nickbook.NickBookPlugin;
import com.mikaeel.nickbook.hook.LuckPermsHook;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks the chat/tab group for a player.
 * source "luckperms": LuckPerms primary group (falls back to permissions).
 * source "permission": highest priority group the player has nickbook.group.<group> for.
 * Nicked players always get the configured nicked-group, so their real rank never leaks.
 */
public final class GroupResolver {
    private GroupResolver() {}

    public static String resolve(NickBookPlugin plugin, Player p, ConfigurationSection groups, String nickedGroup, String source) {
        if (groups == null) return "default";
        if (plugin.getNickService().isNicked(p)) {
            String key = find(groups, nickedGroup);
            if (key == null) key = find(groups, plugin.getNickService().get(p).rank());
            return key != null ? key : fallback(groups);
        }
        if (source == null || source.equalsIgnoreCase("luckperms")) {
            String key = find(groups, LuckPermsHook.primaryGroup(p));
            if (key != null) return key;
        }
        List<String> keys = new ArrayList<String>(groups.getKeys(false));
        keys.sort((a, b) -> Integer.compare(groups.getInt(b + ".priority", 0), groups.getInt(a + ".priority", 0)));
        for (String key : keys) {
            if (!key.equalsIgnoreCase("default") && p.hasPermission("nickbook.group." + key.toLowerCase())) return key;
        }
        return fallback(groups);
    }

    public static String find(ConfigurationSection groups, String name) {
        if (name == null || name.isEmpty()) return null;
        for (String key : groups.getKeys(false)) if (key.equalsIgnoreCase(name)) return key;
        return null;
    }

    private static String fallback(ConfigurationSection groups) {
        String key = find(groups, "default");
        if (key != null) return key;
        for (String k : groups.getKeys(false)) return k;
        return "default";
    }
}
