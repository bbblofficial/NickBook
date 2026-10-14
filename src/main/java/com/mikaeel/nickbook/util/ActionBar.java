package com.mikaeel.nickbook.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Action bar (line above hearts/hunger). Uses the Spigot API on 1.9+ and
 * PacketPlayOutChat type 2 through reflection on 1.8.8.
 */
public final class ActionBar {
    private static boolean modernBroken, legacyBroken;
    private static Method modernSend, parse;
    private static Object actionBarType;
    private static Method serialize;
    private static Constructor<?> packetCtor;

    private ActionBar() {}

    /** @param text legacy colored text (already translated). */
    public static void send(Player player, String text) {
        String json = "{\"text\":\"" + JsonText.escape(text) + "\"}";
        if (!modernBroken) {
            try {
                if (modernSend == null) {
                    Class<?> type = Class.forName("net.md_5.bungee.api.ChatMessageType");
                    Class<?> base = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
                    actionBarType = type.getField("ACTION_BAR").get(null);
                    parse = Class.forName("net.md_5.bungee.chat.ComponentSerializer").getMethod("parse", String.class);
                    modernSend = Class.forName("org.bukkit.entity.Player$Spigot").getMethod("sendMessage", type, Array.newInstance(base, 0).getClass());
                }
                Object spigot = player.getClass().getMethod("spigot").invoke(player);
                modernSend.invoke(spigot, actionBarType, parse.invoke(null, json));
                return;
            } catch (Throwable t) {
                modernBroken = true;
            }
        }
        if (legacyBroken) return;
        try {
            if (packetCtor == null) {
                Class<?> base = Reflect.nms("IChatBaseComponent");
                serialize = Reflect.nms("IChatBaseComponent$ChatSerializer").getMethod("a", String.class);
                packetCtor = Reflect.nms("PacketPlayOutChat").getConstructor(base, byte.class);
            }
            Reflect.sendPacket(player, packetCtor.newInstance(serialize.invoke(null, json), (byte) 2));
        } catch (Throwable t) {
            legacyBroken = true;
        }
    }
}
