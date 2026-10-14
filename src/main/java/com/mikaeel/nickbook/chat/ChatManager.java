package com.mikaeel.nickbook.chat;

import com.mikaeel.nickbook.NickBookPlugin;
import com.mikaeel.nickbook.util.ActionBar;
import com.mikaeel.nickbook.util.Colors;
import com.mikaeel.nickbook.util.GroupResolver;
import com.mikaeel.nickbook.util.JsonText;
import com.mikaeel.nickbook.util.Placeholders;
import com.mikaeel.nickbook.util.Reflect;
import com.mikaeel.nickbook.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Everything configured in chat.yml: formats, moderation, mentions, join/quit, announcer. */
public final class ChatManager implements Listener {
    static final String MSG = "\u0001";
    static final String NAME = "\u0002";

    private final NickBookPlugin plugin;
    private volatile YamlConfiguration cfg = new YamlConfiguration();
    private volatile Pattern filterPattern, adPattern;
    private volatile List<String> adWhitelist = Collections.emptyList();
    private volatile boolean muted;
    private final Map<UUID, Long> lastChat = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<UUID, String>();
    private final Map<AsyncPlayerChatEvent, Pending> pending = new ConcurrentHashMap<AsyncPlayerChatEvent, Pending>();
    private BukkitTask announcer;
    private int announceIndex;

    private static final class Pending {
        final String left, name, right, hover, clickAction, clickValue;
        Pending(String left, String name, String right, String hover, String clickAction, String clickValue) {
            this.left = left; this.name = name; this.right = right; this.hover = hover;
            this.clickAction = clickAction; this.clickValue = clickValue;
        }
    }

    public ChatManager(NickBookPlugin plugin) { this.plugin = plugin; }

    public YamlConfiguration cfg() { return cfg; }
    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }
    private boolean on() { return cfg.getBoolean("enabled", true); }

    public void reload() {
        cfg = plugin.loadYaml("chat.yml");
        List<String> words = cfg.getStringList("filter.words");
        if (words.isEmpty()) filterPattern = null;
        else {
            StringBuilder b = new StringBuilder();
            for (String w : words) {
                if (w.trim().isEmpty()) continue;
                if (b.length() > 0) b.append('|');
                b.append(Pattern.quote(w.trim()));
            }
            String body = "(" + b + ")";
            filterPattern = b.length() == 0 ? null : Pattern.compile(cfg.getBoolean("filter.whole-words", true)
                    ? "(?<![\\p{L}\\p{N}])" + body + "(?![\\p{L}\\p{N}])" : body, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
        adPattern = Pattern.compile("((?:\\d{1,3}[.,\\s]+){3}\\d{1,3}(?::\\d{2,5})?)|([a-z0-9-]{2,}\\s*(?:\\.|\\(dot\\)|\\[dot\\]|\\sdot\\s)\\s*"
                + "(?:com|net|org|me|io|gg|xyz|tk|ml|ga|cf|co|us|uk|de|eu|ru|ir|pl|club|online|fun|world|network|host|pro|site|store|tv|cc)\\b)", Pattern.CASE_INSENSITIVE);
        List<String> wl = new ArrayList<String>();
        for (String s : cfg.getStringList("anti-advertising.whitelist")) wl.add(s.toLowerCase(Locale.ROOT).replace(" ", ""));
        adWhitelist = wl;
        startAnnouncer();
    }

    public void shutdown() {
        if (announcer != null) announcer.cancel();
        announcer = null;
    }

    // ------------------------------------------------------------------ helpers

    public String message(String key) {
        String raw = cfg.getString("messages." + key, "");
        return Colors.color(raw.replace("{prefix}", cfg.getString("messages.prefix", "")));
    }

    public void send(CommandSender to, String key, String... replacements) {
        String text = message(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) text = text.replace(replacements[i], replacements[i + 1]);
        if (!text.isEmpty()) to.sendMessage(text);
    }

    public String groupOf(Player p) {
        return GroupResolver.resolve(plugin, p, cfg.getConfigurationSection("formats"), cfg.getString("nicked-group", "default"), cfg.getString("group-source", "luckperms"));
    }

    private ConfigurationSection group(Player p) {
        ConfigurationSection formats = cfg.getConfigurationSection("formats");
        return formats == null ? null : formats.getConfigurationSection(groupOf(p));
    }

    /** Colors a message typed by a player according to their chat permissions. */
    public static String colorTyped(CommandSender p, String message) {
        return Colors.colorUser(message,
                p.hasPermission("nickbook.chat.color"),
                p.hasPermission("nickbook.chat.format"),
                p.hasPermission("nickbook.chat.magic"),
                p.hasPermission("nickbook.chat.hex"));
    }

    private boolean deferToLpc() {
        return cfg.getBoolean("defer-to-lpc", true) && Bukkit.getPluginManager().isPluginEnabled("LPC");
    }

    private void cancel(AsyncPlayerChatEvent e, String key, String... rep) {
        e.setCancelled(true);
        send(e.getPlayer(), key, rep);
    }

    // ------------------------------------------------------------------ moderation (LOWEST)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onModerate(AsyncPlayerChatEvent e) {
        if (!on()) return;
        Player p = e.getPlayer();
        String msg = e.getMessage();
        UUID id = p.getUniqueId();

        if (muted && !p.hasPermission("nickbook.mutechat.bypass")) { cancel(e, "chat-muted"); return; }

        if (cfg.getBoolean("cooldown.enabled") && !p.hasPermission("nickbook.chat.bypass.cooldown")) {
            long wait = (long) (cfg.getDouble("cooldown.seconds", 2) * 1000);
            Long last = lastChat.get(id);
            long now = System.currentTimeMillis();
            if (last != null && now - last < wait) {
                cancel(e, "cooldown", "{seconds}", String.valueOf((int) Math.ceil((wait - (now - last)) / 1000.0)));
                return;
            }
        }

        if (cfg.getBoolean("anti-repeat.enabled") && !p.hasPermission("nickbook.chat.bypass.repeat")) {
            String prev = lastMessage.get(id);
            if (prev != null && similarity(prev, msg) >= cfg.getInt("anti-repeat.similarity", 85)) { cancel(e, "repeat"); return; }
        }

        if (cfg.getBoolean("anti-caps.enabled") && !p.hasPermission("nickbook.chat.bypass.caps")) {
            int letters = 0, upper = 0;
            for (char c : Colors.strip(msg).toCharArray()) {
                if (Character.isLetter(c)) { letters++; if (Character.isUpperCase(c)) upper++; }
            }
            if (letters >= cfg.getInt("anti-caps.min-length", 6) && upper * 100 / Math.max(1, letters) > cfg.getInt("anti-caps.max-percent", 60)) {
                if (cfg.getString("anti-caps.action", "lowercase").equalsIgnoreCase("block")) { cancel(e, "caps"); return; }
                msg = msg.toLowerCase(Locale.ROOT);
            }
        }

        if (cfg.getBoolean("anti-advertising.enabled") && !p.hasPermission("nickbook.chat.bypass.advertising")) {
            Matcher m = adPattern.matcher(msg);
            while (m.find()) {
                String found = m.group().toLowerCase(Locale.ROOT).replace(" ", "");
                boolean ok = false;
                for (String w : adWhitelist) if (!w.isEmpty() && found.contains(w)) { ok = true; break; }
                if (!ok) {
                    cancel(e, "advertising");
                    final String alert = message("advertising-alert").replace("{player}", p.getName()).replace("{message}", msg);
                    if (!alert.isEmpty()) Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission("nickbook.chat.notify")) staff.sendMessage(alert);
                        Bukkit.getConsoleSender().sendMessage(alert);
                    });
                    return;
                }
            }
        }

        Pattern filter = filterPattern;
        if (cfg.getBoolean("filter.enabled") && filter != null && !p.hasPermission("nickbook.chat.bypass.filter")) {
            Matcher m = filter.matcher(msg);
            if (m.find()) {
                if (cfg.getBoolean("filter.block", false)) { cancel(e, "filtered"); return; }
                String ch = cfg.getString("filter.replacement", "*");
                StringBuffer sb = new StringBuffer();
                m.reset();
                while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(repeat(ch, m.group().length())));
                m.appendTail(sb);
                msg = sb.toString();
            }
        }

        e.setMessage(msg);
        lastChat.put(id, System.currentTimeMillis());
        lastMessage.put(id, msg);
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    /** 0-100 similarity based on Levenshtein distance. */
    static int similarity(String a, String b) {
        a = Colors.strip(a).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        b = Colors.strip(b).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        if (a.isEmpty() && b.isEmpty()) return 100;
        if (a.length() > 120) a = a.substring(0, 120);
        if (b.length() > 120) b = b.substring(0, 120);
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        int max = Math.max(a.length(), b.length());
        return (int) Math.round((1.0 - (double) prev[b.length()] / max) * 100);
    }

    // ------------------------------------------------------------------ format (HIGHEST)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFormat(AsyncPlayerChatEvent e) {
        if (!on()) return;
        Player p = e.getPlayer();
        String msg = e.getMessage();
        Set<Player> recipients = e.getRecipients();

        // channel: range chat / per-world chat
        String channel = "";
        if (cfg.getBoolean("range-chat.enabled")) {
            String gp = cfg.getString("range-chat.global-prefix", "!");
            if (!gp.isEmpty() && msg.startsWith(gp) && msg.length() > gp.length() && p.hasPermission("nickbook.chat.global")) {
                msg = msg.substring(gp.length()).trim();
                channel = cfg.getString("range-chat.global-tag", "");
            } else {
                channel = cfg.getString("range-chat.local-tag", "");
                double r = cfg.getDouble("range-chat.radius", 100);
                filter(recipients, p, r * r);
            }
        } else if (cfg.getBoolean("per-world-chat")) {
            filter(recipients, p, -1);
        }

        ConfigurationSection g = group(p);
        boolean lpc = deferToLpc();
        String base = lpc ? "" : Colors.color(g == null ? "&f" : g.getString("message-color", "&f"));
        String body = lpc ? msg : colorTyped(p, msg);
        body = mentions(p, body, base, recipients);
        e.setMessage(base + body);
        if (lpc) return;

        String format = g == null ? "{prefix}{name}{suffix}&7: {message}" : g.getString("format", "{prefix}{name}{suffix}&7: {message}");
        if (!format.contains("{message}")) format = format + " {message}";
        format = format.replace("{message}", MSG).replace("{channel}", channel);
        boolean hover = cfg.getBoolean("hover.enabled", false) && format.contains("{name}");
        String rendered = Placeholders.format(p, format.replace("{name}", NAME));
        String shown = plugin.getNickService().shownName(p);

        if (hover) {
            int n = rendered.indexOf(NAME);
            String left = rendered.substring(0, n);
            String right = rendered.substring(n + 1).replace(NAME, shown);
            List<String> lines = cfg.getStringList("hover.lines");
            StringBuilder hb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) hb.append('\n');
                hb.append(Placeholders.format(p, lines.get(i)));
            }
            String click = cfg.getString("hover.click", "suggest:/msg {name} ");
            String action = null, value = null;
            int colon = click.indexOf(':');
            if (colon > 0) {
                String type = click.substring(0, colon).toLowerCase(Locale.ROOT);
                value = Placeholders.apply(p, click.substring(colon + 1));
                if (type.equals("suggest")) action = "suggest_command";
                else if (type.equals("run")) action = "run_command";
                else if (type.equals("url")) action = "open_url";
                else if (type.equals("copy")) action = "copy_to_clipboard";
            }
            pending.put(e, new Pending(left, shown, right, hb.toString(), action, value));
        }

        String legacy = rendered.replace(NAME, shown).replace("%", "%%").replace(MSG, "%2$s");
        e.setFormat(legacy);
    }

    private static void filter(Set<Player> recipients, Player p, double maxDistSq) {
        try {
            for (Iterator<Player> it = recipients.iterator(); it.hasNext(); ) {
                Player r = it.next();
                if (r == p || r.hasPermission("nickbook.chat.spy")) continue;
                if (!r.getWorld().equals(p.getWorld())) { it.remove(); continue; }
                if (maxDistSq >= 0 && r.getLocation().distanceSquared(p.getLocation()) > maxDistSq) it.remove();
            }
        } catch (UnsupportedOperationException ignored) { }
    }

    private String mentions(Player sender, String body, String restore, Set<Player> recipients) {
        if (!cfg.getBoolean("mentions.enabled", true) || !sender.hasPermission("nickbook.chat.mention")) return body;
        String fmt = cfg.getString("mentions.format", "&e@{name}");
        boolean needAt = cfg.getBoolean("mentions.require-at", false);
        List<Player> pinged = new ArrayList<Player>();
        for (Player t : new ArrayList<Player>(recipients)) {
            if (t == sender) continue;
            String name = plugin.getNickService().shownName(t);
            Pattern pat = Pattern.compile("(?<![A-Za-z0-9_])" + (needAt ? "@" : "@?") + Pattern.quote(name) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
            Matcher m = pat.matcher(body);
            if (!m.find()) continue;
            String highlight = Placeholders.format(t, fmt) + restore;
            body = m.replaceAll(Matcher.quoteReplacement(highlight));
            if (!plugin.getData().isIgnoring(t.getUniqueId(), sender.getUniqueId())) pinged.add(t);
        }
        if (!pinged.isEmpty()) {
            final String senderName = plugin.getNickService().shownName(sender);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player t : pinged) {
                    if (!t.isOnline()) continue;
                    Sounds.play(t, cfg.getString("mentions.sound", "ORB_PICKUP,ENTITY_EXPERIENCE_ORB_PICKUP"),
                            (float) cfg.getDouble("mentions.volume", 1), (float) cfg.getDouble("mentions.pitch", 1.2));
                    String bar = cfg.getString("mentions.actionbar", "");
                    if (!bar.isEmpty()) ActionBar.send(t, Placeholders.format(t, bar.replace("{sender}", senderName)));
                }
            });
        }
        return body;
    }

    // ------------------------------------------------------------------ hover/click delivery (MONITOR)

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeliver(AsyncPlayerChatEvent e) {
        Pending pd = pending.remove(e);
        if (pd == null || e.isCancelled()) return;
        String message = e.getMessage();
        String rightFull = Colors.lastColors(pd.left + pd.name) + pd.right.replace(MSG, message);
        String json = JsonText.root(
                JsonText.components(pd.left),
                JsonText.interactive(Colors.lastColors(pd.left) + pd.name, pd.hover, pd.clickAction, pd.clickValue),
                JsonText.components(rightFull));
        String fallback = pd.left + pd.name + pd.right.replace(MSG, message);
        Set<Player> targets = new HashSet<Player>(e.getRecipients());
        try {
            e.getRecipients().clear();
        } catch (UnsupportedOperationException ex) {
            return; // recipients locked by another plugin: the normal format is still delivered
        }
        for (Player t : targets) Reflect.sendJson(t, json, fallback);
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        if (!on() || !cfg.getBoolean("join-quit.enabled", true)) return;
        if (p.hasPermission("nickbook.silentjoin")) { e.setJoinMessage(null); }
        else {
            String fmt = p.hasPlayedBefore() ? cfg.getString("join-quit.join", "") : cfg.getString("join-quit.first-join", cfg.getString("join-quit.join", ""));
            e.setJoinMessage(fmt.isEmpty() ? null : Placeholders.format(p, fmt));
        }
        Sounds.play(p, cfg.getString("join-quit.join-sound", ""), 1f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        lastChat.remove(p.getUniqueId());
        lastMessage.remove(p.getUniqueId());
        if (!on() || !cfg.getBoolean("join-quit.enabled", true)) return;
        if (p.hasPermission("nickbook.silentjoin")) { e.setQuitMessage(null); return; }
        String fmt = cfg.getString("join-quit.quit", "");
        e.setQuitMessage(fmt.isEmpty() ? null : Placeholders.format(p, fmt));
    }

    // ------------------------------------------------------------------ announcer

    private void startAnnouncer() {
        shutdown();
        if (!on() || !cfg.getBoolean("auto-broadcast.enabled", false)) return;
        long interval = Math.max(10, cfg.getLong("auto-broadcast.interval", 300)) * 20L;
        announceIndex = 0;
        announcer = Bukkit.getScheduler().runTaskTimer(plugin, this::announce, interval, interval);
    }

    private void announce() {
        List<?> entries = cfg.getList("auto-broadcast.messages", Collections.emptyList());
        if (entries.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        int idx = cfg.getBoolean("auto-broadcast.random", false) ? ThreadLocalRandom.current().nextInt(entries.size()) : (announceIndex++ % entries.size());
        Object entry = entries.get(idx);
        List<String> lines = new ArrayList<String>();
        if (entry instanceof List) for (Object o : (List<?>) entry) lines.add(String.valueOf(o));
        else for (String s : String.valueOf(entry).split("\n")) lines.add(s);
        String sound = cfg.getString("auto-broadcast.sound", "");
        List<String> disabled = cfg.getStringList("auto-broadcast.disabled-worlds");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (disabled.contains(p.getWorld().getName())) continue;
            for (String line : lines) p.sendMessage(Placeholders.format(p, line));
            Sounds.play(p, sound, 1f, 1f);
        }
    }
}
