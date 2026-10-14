package com.mikaeel.nickbook.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;

/** Version independent helpers (1.8.8 NMS fallbacks, modern Bukkit API when present). */
public final class Reflect {
    private static String nmsVersion;
    private static Method parseJson, spigotSend;
    private static boolean jsonBroken;
    private static Boolean modernHeaderFooter;
    private static boolean headerFooterBroken;
    private static final DecimalFormat TPS = new DecimalFormat("0.0");

    private Reflect() {}

    public static String nmsVersion() {
        if (nmsVersion == null) {
            String[] parts = Bukkit.getServer().getClass().getPackage().getName().split("\\.");
            nmsVersion = parts.length > 3 ? parts[3] : "";
        }
        return nmsVersion;
    }

    public static Class<?> nms(String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + nmsVersion() + "." + name);
    }

    public static Object handle(Player player) throws Exception {
        return player.getClass().getMethod("getHandle").invoke(player);
    }

    public static void sendPacket(Player player, Object packet) throws Exception {
        Object handle = handle(player);
        Object connection = handle.getClass().getField("playerConnection").get(handle);
        connection.getClass().getMethod("sendPacket", nms("Packet")).invoke(connection, packet);
    }

    public static int ping(Player player) {
        try {
            return (Integer) player.getClass().getMethod("getPing").invoke(player);
        } catch (Throwable ignored) { }
        try {
            Object handle = handle(player);
            Field f = handle.getClass().getField("ping");
            return f.getInt(handle);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static String tps() {
        double value = 20.0;
        try {
            double[] tps = (double[]) Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
            value = tps[0];
        } catch (Throwable t) {
            try {
                Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                double[] tps = (double[]) server.getClass().getField("recentTps").get(server);
                value = tps[0];
            } catch (Throwable ignored) { }
        }
        return TPS.format(Math.min(20.0, value));
    }

    /** Sends a raw JSON chat message. Falls back to the plain legacy text if JSON chat is unavailable. */
    public static void sendJson(Player player, String json, String fallback) {
        if (!jsonBroken) {
            try {
                if (parseJson == null) {
                    Class<?> serializer = Class.forName("net.md_5.bungee.chat.ComponentSerializer");
                    parseJson = serializer.getMethod("parse", String.class);
                    Class<?> base = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
                    Class<?> spigot = Class.forName("org.bukkit.entity.Player$Spigot");
                    spigotSend = spigot.getMethod("sendMessage", Array.newInstance(base, 0).getClass());
                }
                Object components = parseJson.invoke(null, json);
                Object spigot = player.getClass().getMethod("spigot").invoke(player);
                spigotSend.invoke(spigot, components);
                return;
            } catch (Throwable t) {
                jsonBroken = true;
            }
        }
        player.sendMessage(fallback);
    }

    public static void setHeaderFooter(Player player, String header, String footer) {
        if (headerFooterBroken) return;
        if (modernHeaderFooter == null || modernHeaderFooter) {
            try {
                Player.class.getMethod("setPlayerListHeaderFooter", String.class, String.class).invoke(player, header, footer);
                modernHeaderFooter = true;
                return;
            } catch (Throwable t) {
                modernHeaderFooter = false;
            }
        }
        try {
            Class<?> component = nms("IChatBaseComponent");
            Method a = nms("IChatBaseComponent$ChatSerializer").getMethod("a", String.class);
            Object h = a.invoke(null, "{\"text\":\"" + JsonText.escape(header) + "\"}");
            Object f = a.invoke(null, "{\"text\":\"" + JsonText.escape(footer) + "\"}");
            Class<?> packetClass = nms("PacketPlayOutPlayerListHeaderFooter");
            Constructor<?> ctor = packetClass.getConstructor(component);
            Object packet = ctor.newInstance(h);
            Field b = packetClass.getDeclaredField("b");
            b.setAccessible(true);
            b.set(packet, f);
            sendPacket(player, packet);
        } catch (Throwable t) {
            headerFooterBroken = true;
            Bukkit.getLogger().warning("[NickBook] Tab header/footer is not supported on this server version: " + t);
        }
    }
}
