package com.mikaeel.nickbook.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Plays the first valid sound from a comma separated list, so one config works on 1.8 and 1.9+. */
public final class Sounds {
    private Sounds() {}

    public static void play(Player player, String names, float volume, float pitch) {
        if (player == null || names == null || names.trim().isEmpty() || names.equalsIgnoreCase("none")) return;
        for (String name : names.split(",")) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(name.trim().toUpperCase()), volume, pitch);
                return;
            } catch (Throwable ignored) { }
        }
    }
}
