package com.mikaeel.nickbook.ui;

import com.mikaeel.nickbook.NickBookPlugin;
import com.mikaeel.nickbook.service.NickService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.Arrays;
import java.util.Collections;

public final class NickBook {
    private NickBook() {}
    /** Rank page is built from config.yml "ranks", so custom ranks show up automatically. */
    public static void openRank(NickBookPlugin p, Player player, NickService s) {
        java.util.List<String> pages = new java.util.ArrayList<String>();
        java.util.List<String> parts = new java.util.ArrayList<String>();
        parts.add("{\"text\":\"Let's get you set up with your nickname! First, you'll need to choose which RANK you would like to be shown as when nicked.\\n\\n\",\"color\":\"black\",\"bold\":false}");
        int perPage = 5;
        for (String rank : s.ranks()) {
            if (parts.size() > perPage) {
                pages.add(LegacyBook.compose(parts.toArray(new String[0])));
                parts.clear();
                perPage = 11;
            }
            String color = jsonColor(p.getConfig().getString("ranks." + rank, "&7"));
            parts.add(LegacyBook.option("\u27a4 " + rank + "\n", "/nick rank " + rank, "Click here to be shown as " + rank, color));
        }
        pages.add(LegacyBook.compose(parts.toArray(new String[0])));
        open(player, pages);
    }

    private static String jsonColor(String rankPrefix) {
        String[] names = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
                "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
        for (int i = 0; i < rankPrefix.length() - 1; i++) {
            char c = rankPrefix.charAt(i);
            if (c == '&' || c == '\u00a7') {
                int idx = "0123456789abcdef".indexOf(Character.toLowerCase(rankPrefix.charAt(i + 1)));
                if (idx >= 0) return idx == 15 ? "black" : names[idx];
            }
        }
        return "gray";
    }
    public static void openSkin(NickBookPlugin p, Player player, NickService s) {
        open(player, Collections.singletonList(LegacyBook.compose(
                "{\"text\":\"Awesome! Now, which SKIN would you like to have while nicked?\\n\\n\",\"color\":\"black\"}",
                LegacyBook.option("➤ My normal skin\n", "/nick skin normal", "Click here to use your normal skin", "black"),
                LegacyBook.option("➤ Steve/Alex skin\n", "/nick skin alex", "Click here to use a Steve/Alex skin", "black"),
                LegacyBook.option("➤ Random skin", "/nick skin random", "Click here to use a random skin", "black"))));
    }
    public static void openName(NickBookPlugin p, Player player, NickService s) {
        open(player, Collections.singletonList(LegacyBook.compose(
                "{\"text\":\"Alright, now you'll need to choose the NAME to use!\\n\\n\",\"color\":\"black\"}",
                LegacyBook.option("➤ Use a random name\n", "/nick name random", "Click here to use a randomly generated name.", "black"),
                "{\"text\":\"\\nTo go back to being your usual self, type:\\n/nick reset\",\"color\":\"black\"}")));
    }
    public static void openRandomName(NickBookPlugin p, Player player, NickService s) {
        s.randomName(player);
        open(player, Collections.singletonList(LegacyBook.compose(
                "{\"text\":\"We've generated a random username for you:\\n\",\"color\":\"black\"}",
                "{\"text\":\""+LegacyBook.escape(s.candidate(player))+"\\n\\n\",\"color\":\"black\",\"bold\":true}",
                LegacyBook.option("USE NAME\n", "/nick name previous", "Click here to use this name.", "dark_green"),
                LegacyBook.option("TRY AGAIN", "/nick name random", "Click here to generate another name.", "red"))));
    }
    public static void openConfirmation(NickBookPlugin p, Player player, NickService s) {
        open(player, Collections.singletonList(LegacyBook.page("You have finished setting up your nickname!\n\nWhen you go into a game, you will be nicked as " + s.get(player).name() + ".\n\nYou will not be nicked in lobbies.\n\nTo go back to being your usual self, type:\n/nick reset")));
    }
    private static void open(Player player, java.util.List<String> pages) { LegacyBook.open(player, pages); }
}
