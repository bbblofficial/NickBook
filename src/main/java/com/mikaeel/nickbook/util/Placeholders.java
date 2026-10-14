package com.mikaeel.nickbook.util;

import com.mikaeel.nickbook.NickBookPlugin;
import com.mikaeel.nickbook.hook.PapiHook;
import com.mikaeel.nickbook.service.NickService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Built-in placeholders (nick aware) + PlaceholderAPI.
 * {player} real name | {name} shown name | {displayname} | {prefix} | {suffix} | {group}
 * {world} {online} {max} {ping} {health} {maxhealth} {food} {level} {gamemode} {x} {y} {z}
 * {time} {date} {tps} {server} {nicked} {animation:name}
 */
public final class Placeholders {
    private Placeholders() {}

    /** Replaces placeholders. Does NOT translate colors (call Colors.color after). */
    public static String apply(Player p, String text) {
        if (text == null || text.isEmpty()) return "";
        NickBookPlugin plugin = NickBookPlugin.get();
        String out = text;
        if (out.indexOf('{') >= 0) {
            out = animations(plugin, out);
            out = global(plugin, out);
            if (p != null) {
                NickService s = plugin.getNickService();
                out = rep(out, "{player}", p.getName());
                out = rep(out, "{name}", s.shownName(p));
                if (out.contains("{prefix}")) out = out.replace("{prefix}", s.shownPrefix(p));
                if (out.contains("{suffix}")) out = out.replace("{suffix}", s.shownSuffix(p));
                if (out.contains("{displayname}")) out = out.replace("{displayname}", s.shownPrefix(p) + s.shownName(p) + s.shownSuffix(p));
                if (out.contains("{group}")) out = out.replace("{group}", plugin.getChat().groupOf(p));
                out = rep(out, "{world}", p.getWorld().getName());
                if (out.contains("{ping}")) out = out.replace("{ping}", String.valueOf(Reflect.ping(p)));
                if (out.contains("{health}")) out = out.replace("{health}", String.valueOf((int) Math.ceil(p.getHealth())));
                if (out.contains("{maxhealth}")) out = out.replace("{maxhealth}", String.valueOf((int) Math.ceil(p.getMaxHealth())));
                out = rep(out, "{food}", String.valueOf(p.getFoodLevel()));
                out = rep(out, "{level}", String.valueOf(p.getLevel()));
                out = rep(out, "{gamemode}", p.getGameMode().name().toLowerCase());
                out = rep(out, "{x}", String.valueOf(p.getLocation().getBlockX()));
                out = rep(out, "{y}", String.valueOf(p.getLocation().getBlockY()));
                out = rep(out, "{z}", String.valueOf(p.getLocation().getBlockZ()));
                out = rep(out, "{nicked}", String.valueOf(s.isNicked(p)));
            }
        }
        if (p != null && out.indexOf('%') >= 0 && plugin.hasPapi()) {
            try {
                out = PapiHook.set(p, out);
            } catch (Throwable ignored) { }
        }
        return out;
    }

    /** Placeholders + color in one call. */
    public static String format(Player p, String text) {
        return Colors.color(apply(p, text));
    }

    private static String global(NickBookPlugin plugin, String out) {
        out = rep(out, "{online}", String.valueOf(Bukkit.getOnlinePlayers().size()));
        out = rep(out, "{max}", String.valueOf(Bukkit.getMaxPlayers()));
        if (out.contains("{tps}")) out = out.replace("{tps}", Reflect.tps());
        if (out.contains("{time}")) out = out.replace("{time}", new SimpleDateFormat(plugin.getConfig().getString("time-format", "HH:mm")).format(new Date()));
        if (out.contains("{date}")) out = out.replace("{date}", new SimpleDateFormat(plugin.getConfig().getString("date-format", "dd/MM/yyyy")).format(new Date()));
        if (out.contains("{server}")) out = out.replace("{server}", plugin.getConfig().getString("server-name", "Server"));
        return out;
    }

    private static String animations(NickBookPlugin plugin, String out) {
        int start;
        int guard = 0;
        while ((start = out.indexOf("{animation:")) >= 0 && guard++ < 20) {
            int end = out.indexOf('}', start);
            if (end < 0) break;
            String name = out.substring(start + 11, end);
            out = out.substring(0, start) + plugin.getAnimations().frame(name) + out.substring(end + 1);
        }
        return out;
    }

    private static String rep(String text, String key, String value) {
        return text.contains(key) ? text.replace(key, value == null ? "" : value) : text;
    }
}
