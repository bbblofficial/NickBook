package com.mikaeel.nickbook.tab;

import com.mikaeel.nickbook.NickBookPlugin;
import com.mikaeel.nickbook.util.Colors;
import com.mikaeel.nickbook.util.GroupResolver;
import com.mikaeel.nickbook.util.Placeholders;
import com.mikaeel.nickbook.util.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything configured in tab.yml: header/footer, tab names, nametags, sorting, objectives. */
public final class TabManager implements Listener {
    private static final String TAB_OBJ = "nbtab", BELOW_OBJ = "nbbelow";

    private final NickBookPlugin plugin;
    private YamlConfiguration cfg = new YamlConfiguration();
    private BukkitTask task;
    private boolean active;
    private final Map<UUID, String> teams = new HashMap<UUID, String>();
    private final Map<UUID, Integer> ids = new HashMap<UUID, Integer>();
    private final Map<UUID, String> lastHeaderFooter = new HashMap<UUID, String>();
    private int nextId;
    private static Method setTeamColor;
    private static boolean teamColorChecked;

    public TabManager(NickBookPlugin plugin) { this.plugin = plugin; }

    public boolean isActive() { return active; }

    public void reload() {
        cfg = plugin.loadYaml("tab.yml");
        plugin.getAnimations().load(cfg.getConfigurationSection("animations"));
        if (task != null) task.cancel();
        task = null;
        boolean tabPlugin = Bukkit.getPluginManager().isPluginEnabled("TAB");
        boolean wanted = cfg.getBoolean("enabled", true);
        if (wanted && tabPlugin && cfg.getBoolean("disable-if-tab-plugin-installed", true)) {
            plugin.getLogger().info("TAB plugin found: NickBook tab module disabled (use %nickbook_...% placeholders in TAB instead).");
            wanted = false;
        }
        if (!wanted) {
            if (active) cleanup();
            active = false;
            return;
        }
        if (active) cleanup(); // rebuild everything with new settings
        active = true;
        lastHeaderFooter.clear();
        long interval = Math.max(1, cfg.getLong("update-interval", 10));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 1L, interval);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        if (active) cleanup();
        active = false;
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    public void update(Player p) {
        if (!active || !p.isOnline()) return;
        if (cfg.getStringList("disabled-worlds").contains(p.getWorld().getName())) return;
        try {
            headerFooter(p);
            ConfigurationSection g = group(p);
            listName(p, g);
            nametag(p, g);
            objectives(p);
        } catch (Throwable t) {
            plugin.getLogger().warning("Tab update failed for " + p.getName() + ": " + t);
        }
    }

    private ConfigurationSection group(Player p) {
        ConfigurationSection groups = cfg.getConfigurationSection("groups");
        if (groups == null) return null;
        String key = GroupResolver.resolve(plugin, p, groups, cfg.getString("nicked-group", "default"), cfg.getString("group-source", "luckperms"));
        return groups.getConfigurationSection(key);
    }

    private String render(Player p, String text) { return Placeholders.format(p, text); }

    private String lines(Player p, List<String> lines) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) b.append('\n');
            b.append(render(p, lines.get(i)));
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- header / footer

    private void headerFooter(Player p) {
        if (!cfg.getBoolean("header-footer.enabled", true)) return;
        String world = "per-world." + p.getWorld().getName() + ".";
        List<String> header = cfg.isList(world + "header") ? cfg.getStringList(world + "header") : cfg.getStringList("header-footer.header");
        List<String> footer = cfg.isList(world + "footer") ? cfg.getStringList(world + "footer") : cfg.getStringList("header-footer.footer");
        String h = lines(p, header), f = lines(p, footer);
        String key = h + "\n<<NB>>\n" + f;
        if (key.equals(lastHeaderFooter.get(p.getUniqueId()))) return;
        lastHeaderFooter.put(p.getUniqueId(), key);
        Reflect.setHeaderFooter(p, h, f);
    }

    // ---------------------------------------------------------------- tab list name

    private void listName(Player p, ConfigurationSection g) {
        if (!cfg.getBoolean("tabname.enabled", true)) return;
        String fmt = g != null ? g.getString("tabname", cfg.getString("tabname.default-format", "{prefix}{name}{suffix}")) : cfg.getString("tabname.default-format", "{prefix}{name}{suffix}");
        // Suffixes belong in chat/nametags, not in the player list.
        // Strip the token before rendering so custom group formats cannot leak it back into TAB.
        fmt = fmt.replace("{suffix}", "");
        String name = render(p, fmt);
        int max = cfg.getInt("tabname.max-length", 0);
        if (max > 0) name = Colors.cut(name, max);
        if (name.equals(p.getPlayerListName())) return;
        try {
            p.setPlayerListName(name);
        } catch (IllegalArgumentException tooLong) {
            p.setPlayerListName(Colors.cut(name, 16));
        }
    }

    // ---------------------------------------------------------------- nametags + sorting (scoreboard teams)

    private Set<Scoreboard> scoreboards() {
        Set<Scoreboard> set = new LinkedHashSet<Scoreboard>();
        set.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player p : Bukkit.getOnlinePlayers()) set.add(p.getScoreboard());
        return set;
    }

    private String teamName(Player p, ConfigurationSection g) {
        int sort = g == null ? 99 : Math.max(0, Math.min(99, g.getInt("sort", 50)));
        Integer id = ids.get(p.getUniqueId());
        if (id == null) { id = nextId++; ids.put(p.getUniqueId(), id); }
        String name = plugin.getNickService().shownName(p).toLowerCase(Locale.ROOT);
        if (!cfg.getBoolean("sorting.alphabetical", true)) name = "";
        if (name.length() > 10) name = name.substring(0, 10);
        String suffix = Integer.toString(id % 46656, 36);
        while (suffix.length() < 3) suffix = "0" + suffix;
        return String.format("%02d", sort) + name + suffix;
    }

    private void nametag(Player p, ConfigurationSection g) {
        boolean tags = cfg.getBoolean("nametags.enabled", true);
        boolean sorting = cfg.getBoolean("sorting.enabled", true);
        boolean nicked = plugin.getNickService().isNicked(p);
        // When NickBook nametags/sorting are off, leave the normal player's
        // scoreboard team completely alone so LuckPerms can render the rank.
        if (!tags && !sorting && !nicked) {
            p.setCustomName(null);
            p.setCustomNameVisible(false);
            return;
        }
        String teamName = teamName(p, g);
        String old = teams.put(p.getUniqueId(), teamName);
        String prefix = tags ? render(p, g != null ? g.getString("tagprefix", "{prefix}") : "{prefix}") : "";
        String suffix = tags ? render(p, g != null ? g.getString("tagsuffix", "{suffix}") : "{suffix}") : "";
        for (Scoreboard sb : scoreboards()) {
            if (old != null && !old.equals(teamName)) {
                Team stale = sb.getTeam(old);
                if (stale != null) stale.unregister();
            }
            Team team = sb.getTeam(teamName);
            if (team == null) team = sb.registerNewTeam(teamName);
            setPrefix(team, prefix);
            setSuffix(team, suffix);
            applyColor(team, prefix);
            // Hide the real username for nicked players without rendering a
            // fake nametag. LuckPerms still owns the visible rank formatting.
            if (tags || nicked) {
                visibility(team, nicked);
            }
            // Keep LuckPerms' rank nametag untouched, but replace the real
            // username with the nickname above the head.
            if (nicked) {
                p.setCustomName(plugin.getNickService().shownName(p));
                p.setCustomNameVisible(true);
            } else {
                p.setCustomName(null);
                p.setCustomNameVisible(false);
            }
            if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
        }
    }

    private static void setPrefix(Team team, String value) {
        if (value.equals(team.getPrefix())) return;
        try { team.setPrefix(value); } catch (IllegalArgumentException e) { team.setPrefix(Colors.cut(value, 16)); }
    }

    private static void setSuffix(Team team, String value) {
        if (value.equals(team.getSuffix())) return;
        try { team.setSuffix(value); } catch (IllegalArgumentException e) { team.setSuffix(Colors.cut(value, 16)); }
    }

    /** 1.13+ colors the name with the team color instead of the prefix's last color. */
    private static void applyColor(Team team, String prefix) {
        if (!teamColorChecked) {
            teamColorChecked = true;
            try { setTeamColor = Team.class.getMethod("setColor", ChatColor.class); } catch (Throwable ignored) { }
        }
        if (setTeamColor == null) return;
        String last = Colors.lastColors(prefix);
        ChatColor color = ChatColor.WHITE;
        for (int i = 0; i + 1 < last.length(); i += 2) {
            ChatColor c = ChatColor.getByChar(last.charAt(i + 1));
            if (c != null && c.isColor()) color = c;
        }
        try { setTeamColor.invoke(team, color); } catch (Throwable ignored) { }
    }

    private void visibility(Team team, boolean nicked) {
        if (nicked) {
            try {
                if (team.getNameTagVisibility() != NameTagVisibility.NEVER) team.setNameTagVisibility(NameTagVisibility.NEVER);
            } catch (Throwable ignored) { }
            return;
        }
        String v = cfg.getString("nametags.visibility", "always").toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            NameTagVisibility vis = NameTagVisibility.valueOf(v);
            if (team.getNameTagVisibility() != vis) team.setNameTagVisibility(vis);
        } catch (Throwable ignored) { }
    }

    // ---------------------------------------------------------------- objectives (yellow number + below name)

    private void objectives(Player p) {
        objective(p, "tab-objective", TAB_OBJ, DisplaySlot.PLAYER_LIST);
        objective(p, "belowname", BELOW_OBJ, DisplaySlot.BELOW_NAME);
    }

    private void objective(Player p, String path, String id, DisplaySlot slot) {
        if (!cfg.getBoolean(path + ".enabled", false)) return;
        int value;
        String type = cfg.getString(path + ".type", "ping").toLowerCase(Locale.ROOT);
        if (type.equals("ping")) value = Reflect.ping(p);
        else if (type.equals("health")) value = (int) Math.ceil(p.getHealth());
        else if (type.equals("level")) value = p.getLevel();
        else {
            try { value = (int) Double.parseDouble(Colors.strip(render(p, cfg.getString(path + ".value", "0"))).trim()); }
            catch (NumberFormatException e) { value = 0; }
        }
        String title = Colors.cut(render(p, cfg.getString(path + ".title", "")), 32);
        for (Scoreboard sb : scoreboards()) {
            Objective o = sb.getObjective(id);
            if (o == null) o = sb.registerNewObjective(id, "dummy");
            if (o.getDisplaySlot() != slot) o.setDisplaySlot(slot);
            if (!title.isEmpty() && !title.equals(o.getDisplayName())) o.setDisplayName(title);
            Score score = o.getScore(p.getName());
            if (score.getScore() != value) score.setScore(value);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    private void remove(Player p) {
        String team = teams.remove(p.getUniqueId());
        lastHeaderFooter.remove(p.getUniqueId());
        ids.remove(p.getUniqueId());
        for (Scoreboard sb : scoreboards()) {
            if (team != null) {
                Team t = sb.getTeam(team);
                if (t != null) t.unregister();
            }
            if (sb.getObjective(TAB_OBJ) != null || sb.getObjective(BELOW_OBJ) != null) sb.resetScores(p.getName());
        }
    }

    private void cleanup() {
        for (Scoreboard sb : scoreboards()) {
            for (String team : teams.values()) {
                Team t = sb.getTeam(team);
                if (t != null) t.unregister();
            }
            Objective a = sb.getObjective(TAB_OBJ);
            if (a != null) a.unregister();
            Objective b = sb.getObjective(BELOW_OBJ);
            if (b != null) b.unregister();
        }
        teams.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setPlayerListName(null);
            if (lastHeaderFooter.containsKey(p.getUniqueId())) Reflect.setHeaderFooter(p, "", "");
        }
        lastHeaderFooter.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!active) return;
        Bukkit.getScheduler().runTaskLater(plugin, this::updateAll, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (active) remove(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        if (!active) return;
        final Player p = e.getPlayer();
        lastHeaderFooter.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> update(p), 2L);
    }
}
