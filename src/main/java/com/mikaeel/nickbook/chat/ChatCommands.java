package com.mikaeel.nickbook.chat;

import com.mikaeel.nickbook.NickBookPlugin;
import com.mikaeel.nickbook.data.PlayerData;
import com.mikaeel.nickbook.service.NickService;
import com.mikaeel.nickbook.util.Colors;
import com.mikaeel.nickbook.util.Placeholders;
import com.mikaeel.nickbook.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** /msg /reply /socialspy /msgtoggle /clearchat /mutechat /broadcast /nickbook */
public final class ChatCommands implements TabExecutor {
    private static final String CONSOLE = "CONSOLE";
    private final NickBookPlugin plugin;
    private final Map<String, String> replies = new ConcurrentHashMap<String, String>();

    public ChatCommands(NickBookPlugin plugin) { this.plugin = plugin; }

    private ChatManager chat() { return plugin.getChat(); }
    private YamlConfiguration cfg() { return chat().cfg(); }
    private NickService nick() { return plugin.getNickService(); }
    private PlayerData data() { return plugin.getData(); }

    private boolean perm(CommandSender s, String node) {
        if (s.hasPermission(node)) return true;
        chat().send(s, "no-permission");
        return false;
    }

    private static String join(String[] a, int from) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < a.length; i++) { if (i > from) b.append(' '); b.append(a[i]); }
        return b.toString();
    }

    private static String key(CommandSender s) { return s instanceof Player ? ((Player) s).getUniqueId().toString() : CONSOLE; }

    private CommandSender fromKey(String key) {
        if (key == null) return null;
        if (key.equals(CONSOLE)) return Bukkit.getConsoleSender();
        Player p = Bukkit.getPlayer(UUID.fromString(key));
        return p != null && p.isOnline() ? p : null;
    }

    /** Nick aware lookup: matches shown names; real names of nicked players only with nickbook.seenicks. */
    public Player find(CommandSender viewer, String name) {
        List<Player> online = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        for (Player t : online) if (nick().shownName(t).equalsIgnoreCase(name)) return t;
        for (Player t : online) {
            if (t.getName().equalsIgnoreCase(name) && (!nick().isNicked(t) || viewer.hasPermission("nickbook.seenicks"))) return t;
        }
        Player match = null;
        String lower = name.toLowerCase(Locale.ROOT);
        for (Player t : online) {
            if (nick().shownName(t).toLowerCase(Locale.ROOT).startsWith(lower)) {
                if (match != null) return null; // ambiguous
                match = t;
            }
        }
        return match;
    }

    private String shown(CommandSender s, String what) {
        if (!(s instanceof Player)) return what.equals("name") || what.equals("displayname") ? cfg().getString("private-messages.console-name", "Console") : "";
        Player p = (Player) s;
        switch (what) {
            case "name": return nick().shownName(p);
            case "prefix": return nick().shownPrefix(p);
            case "suffix": return nick().shownSuffix(p);
            default: return nick().shownPrefix(p) + nick().shownName(p) + nick().shownSuffix(p);
        }
    }

    /** Fills {sender_*}/{receiver_*} placeholders, PAPI for the sender, then inserts the (already colored) message. */
    private String pmFormat(String fmt, CommandSender from, CommandSender to, String message) {
        String out = fmt.replace("{message}", ChatManager.MSG);
        for (String w : new String[]{"name", "displayname", "prefix", "suffix"}) {
            out = out.replace("{sender_" + w + "}", shown(from, w)).replace("{receiver_" + w + "}", shown(to, w));
        }
        out = Placeholders.format(from instanceof Player ? (Player) from : null, out);
        return out.replace(ChatManager.MSG, message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "msg": return msg(sender, a);
            case "reply": return reply(sender, a);
            case "socialspy": return toggle(sender, "nickbook.socialspy", "socialspy", "socialspy-on", "socialspy-off");
            case "msgtoggle": return toggle(sender, "nickbook.msgtoggle", "msgtoggle", "msgtoggle-off", "msgtoggle-on");
            case "clearchat": return clearChat(sender);
            case "mutechat": return muteChat(sender);
            case "broadcast": return broadcast(sender, a);
            case "nickbook": return admin(sender, a);
            default: return false;
        }
    }

    private boolean msg(CommandSender sender, String[] a) {
        if (!perm(sender, "nickbook.msg")) return true;
        if (!cfg().getBoolean("private-messages.enabled", true)) { chat().send(sender, "pm-disabled"); return true; }
        if (a.length < 2) { chat().send(sender, "usage", "{usage}", "/msg <player> <message>"); return true; }
        Player target = find(sender, a[0]);
        if (target == null) { chat().send(sender, "player-not-found", "{player}", a[0]); return true; }
        deliver(sender, target, join(a, 1));
        return true;
    }

    private boolean reply(CommandSender sender, String[] a) {
        if (!perm(sender, "nickbook.msg")) return true;
        if (!cfg().getBoolean("private-messages.enabled", true)) { chat().send(sender, "pm-disabled"); return true; }
        if (a.length < 1) { chat().send(sender, "usage", "{usage}", "/reply <message>"); return true; }
        CommandSender target = fromKey(replies.get(key(sender)));
        if (target == null) { chat().send(sender, "no-reply"); return true; }
        deliver(sender, target, join(a, 0));
        return true;
    }

    private void deliver(CommandSender from, CommandSender to, String raw) {
        if (from == to) { chat().send(from, "pm-self"); return; }
        boolean bypass = from.hasPermission("nickbook.msg.bypass") || from instanceof ConsoleCommandSender;
        if (to instanceof Player && !bypass) {
            Player t = (Player) to;
            if (data().getBool(t.getUniqueId(), "msgtoggle", false)) { chat().send(from, "pm-target-disabled", "{player}", shown(t, "name")); return; }
            if (from instanceof Player && data().isIgnoring(t.getUniqueId(), ((Player) from).getUniqueId())) {
                chat().send(from, "pm-target-disabled", "{player}", shown(t, "name"));
                return;
            }
        }
        String message = ChatManager.colorTyped(from, raw);
        from.sendMessage(pmFormat(cfg().getString("private-messages.format-sent", "&d[me -> {receiver_displayname}&d] &f{message}"), from, to, message));
        to.sendMessage(pmFormat(cfg().getString("private-messages.format-received", "&d[{sender_displayname}&d -> me] &f{message}"), from, to, message));
        if (to instanceof Player) {
            Sounds.play((Player) to, cfg().getString("private-messages.sound", "ORB_PICKUP,ENTITY_EXPERIENCE_ORB_PICKUP"), 1f, 1f);
        }
        replies.put(key(from), key(to));
        replies.put(key(to), key(from));
        String spy = pmFormat(cfg().getString("private-messages.format-spy", "&8[Spy] &7{sender_name} -> {receiver_name}: {message}"), from, to, message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == from || p == to) continue;
            if (p.hasPermission("nickbook.socialspy") && data().getBool(p.getUniqueId(), "socialspy", false)) p.sendMessage(spy);
        }
        if (cfg().getBoolean("private-messages.log-to-console", true) && !(from instanceof ConsoleCommandSender) && !(to instanceof ConsoleCommandSender)) {
            Bukkit.getConsoleSender().sendMessage(spy);
        }
    }

    private boolean toggle(CommandSender sender, String node, String key, String onMsg, String offMsg) {
        if (!(sender instanceof Player)) { chat().send(sender, "players-only"); return true; }
        if (!perm(sender, node)) return true;
        Player p = (Player) sender;
        boolean now = !data().getBool(p.getUniqueId(), key, false);
        data().set(p.getUniqueId(), key, now);
        chat().send(p, now ? onMsg : offMsg);
        return true;
    }

    private boolean clearChat(CommandSender sender) {
        if (!perm(sender, "nickbook.clearchat")) return true;
        int lines = Math.max(1, cfg().getInt("clear-chat.lines", 100));
        String by = shown(sender, "name");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("nickbook.clearchat.bypass")) for (int i = 0; i < lines; i++) p.sendMessage(" ");
            chat().send(p, "chat-cleared", "{player}", by);
        }
        chat().send(Bukkit.getConsoleSender(), "chat-cleared", "{player}", by);
        return true;
    }

    private boolean muteChat(CommandSender sender) {
        if (!perm(sender, "nickbook.mutechat")) return true;
        chat().setMuted(!chat().isMuted());
        String by = shown(sender, "name");
        String key = chat().isMuted() ? "chat-muted-on" : "chat-muted-off";
        for (Player p : Bukkit.getOnlinePlayers()) chat().send(p, key, "{player}", by);
        chat().send(Bukkit.getConsoleSender(), key, "{player}", by);
        return true;
    }

    private boolean broadcast(CommandSender sender, String[] a) {
        if (!perm(sender, "nickbook.broadcast")) return true;
        if (a.length == 0) { chat().send(sender, "usage", "{usage}", "/broadcast <message>"); return true; }
        String message = Colors.color(join(a, 0));
        String fmt = cfg().getString("broadcast.format", "&8[&c&lBROADCAST&8] &f{message}").replace("{message}", ChatManager.MSG);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Placeholders.format(p, fmt).replace(ChatManager.MSG, message));
            Sounds.play(p, cfg().getString("broadcast.sound", ""), 1f, 1f);
        }
        Bukkit.getConsoleSender().sendMessage(Placeholders.format(null, fmt).replace(ChatManager.MSG, message));
        return true;
    }

    private boolean admin(CommandSender sender, String[] a) {
        if (!perm(sender, "nickbook.admin")) return true;
        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            chat().send(sender, "reloaded");
            return true;
        }
        sender.sendMessage(Colors.color("&6NickBook &7v" + plugin.getDescription().getVersion()));
        sender.sendMessage(Colors.color("&e/nickbook reload &7- reload config.yml, chat.yml and tab.yml"));
        sender.sendMessage(Colors.color("&7Chat module: " + (chat().cfg().getBoolean("enabled", true) ? "&aon" : "&coff")
                + " &8| &7Tab module: " + (plugin.getTab().isActive() ? "&aon" : "&coff")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        List<String> options;
        if (name.equals("nickbook") && a.length == 1) options = Collections.singletonList("reload");
        else if (name.equals("msg") && a.length == 1) {
            options = new ArrayList<String>();
            for (Player p : Bukkit.getOnlinePlayers()) if (p != sender) options.add(nick().shownName(p));
        } else return Collections.emptyList();
        List<String> out = new ArrayList<String>();
        String typed = a[a.length - 1].toLowerCase(Locale.ROOT);
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(o);
        return out;
    }

    public static List<String> commandNames() {
        return Arrays.asList("msg", "reply", "socialspy", "msgtoggle", "clearchat", "mutechat", "broadcast", "nickbook");
    }
}
