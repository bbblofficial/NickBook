package com.mikaeel.nickbook.command;

import com.mikaeel.nickbook.NickBookPlugin;
import com.mikaeel.nickbook.service.NickService;
import com.mikaeel.nickbook.ui.NickBook;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class NickCommand implements TabExecutor {
    private final NickBookPlugin plugin;
    private final NickService s;

    public NickCommand(NickBookPlugin plugin, NickService s) {
        this.plugin = plugin;
        this.s = s;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use /nick.");
            return true;
        }
        Player x = (Player) sender;
        if (!x.hasPermission("nickbook.use")) {
            x.sendMessage(s.color("&cNo permission."));
            return true;
        }
        if (a.length == 0) {
            NickBook.openRank(plugin, x, s);
            return true;
        }
        String q = a[0].toLowerCase(Locale.ROOT);
        switch (q) {
            case "reset":
                s.reset(x);
                break;
            case "rank":
                if (a.length > 1) {
                    if (!s.setRank(x, a[1])) {
                        x.sendMessage(s.color("&cUnknown rank: &f" + a[1]));
                        NickBook.openRank(plugin, x, s);
                        break;
                    }
                    x.sendMessage(s.color("&aYou set your nick rank to " + s.findRank(a[1])));
                    NickBook.openSkin(plugin, x, s);
                } else NickBook.openRank(plugin, x, s);
                break;
            case "skin":
                if (a.length > 1) {
                    s.setSkin(x, a[1]);
                    x.sendMessage(s.color("&aYou set your nick skin to " + a[1].toUpperCase(Locale.ROOT) + "!"));
                    NickBook.openName(plugin, x, s);
                } else NickBook.openSkin(plugin, x, s);
                break;
            case "name":
                if (a.length > 1 && a[1].equalsIgnoreCase("random")) {
                    x.sendMessage(s.color("&aGenerating a unique random name. Please wait..."));
                    NickBook.openRandomName(plugin, x, s);
                } else if (a.length > 1 && a[1].equalsIgnoreCase("previous")) {
                    useName(x);
                } else NickBook.openName(plugin, x, s);
                break;
            case "_use":
                useName(x);
                break;
            default:
                NickBook.openRank(plugin, x, s);
        }
        return true;
    }

    private void useName(Player x) {
        s.useCandidate(x);
        x.sendMessage(s.color("&aYou are now nicked as " + s.get(x).name() + "!"));
        NickBook.openConfirmation(plugin, x, s);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command c, String alias, String[] x) {
        List<String> options;
        if (x.length == 1) options = Arrays.asList("rank", "skin", "name", "reset");
        else if (x.length == 2 && x[0].equalsIgnoreCase("rank")) options = s.ranks();
        else if (x.length == 2 && x[0].equalsIgnoreCase("skin")) options = Arrays.asList("normal", "alex", "random");
        else if (x.length == 2 && x[0].equalsIgnoreCase("name")) options = Arrays.asList("random", "previous");
        else return Collections.emptyList();
        List<String> out = new ArrayList<String>();
        String typed = x[x.length - 1].toLowerCase(Locale.ROOT);
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(o);
        return out;
    }
}
