package com.mikaeel.nickbook.ui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The 1.8.8 API has no Player#openBook and no BookMeta.Spigot pages.
 * This uses reflection so the project compiles against spigot-api while the
 * running 1.8.8 server supplies its CraftBukkit/NMS implementation.
 */
public final class LegacyBook {
    private LegacyBook() {}

    public static void open(Player player, List<String> pages) {
        try {
            String version = player.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Class<?> nmsItemStack = Class.forName("net.minecraft.server." + version + ".ItemStack");
            Class<?> nbtCompound = Class.forName("net.minecraft.server." + version + ".NBTTagCompound");
            Class<?> nbtList = Class.forName("net.minecraft.server." + version + ".NBTTagList");
            Class<?> nbtString = Class.forName("net.minecraft.server." + version + ".NBTTagString");

            Method asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            Object nmsBook = asNmsCopy.invoke(null, new ItemStack(org.bukkit.Material.WRITTEN_BOOK));
            Object tag = nbtCompound.newInstance();
            Object pageList = nbtList.newInstance();
            Method add = nbtList.getMethod("add", Class.forName("net.minecraft.server." + version + ".NBTBase"));
            Constructor<?> stringCtor = nbtString.getConstructor(String.class);
            for (String page : pages) add.invoke(pageList, stringCtor.newInstance(page));
            Method set = nbtCompound.getMethod("set", String.class, Class.forName("net.minecraft.server." + version + ".NBTBase"));
            set.invoke(tag, "pages", pageList);
            set.invoke(tag, "title", stringCtor.newInstance("Nick Setup"));
            set.invoke(tag, "author", stringCtor.newInstance("NickBook"));
            Method setTag = nmsItemStack.getMethod("setTag", nbtCompound);
            setTag.invoke(nmsBook, tag);
            Method asBukkitCopy = craftItemStack.getMethod("asBukkitCopy", nmsItemStack);
            ItemStack book = (ItemStack) asBukkitCopy.invoke(null, nmsBook);

            int slot = player.getInventory().getHeldItemSlot();
            ItemStack old = player.getInventory().getItem(slot);
            player.getInventory().setItem(slot, book);
            try {
                openPacket(player, version);
            } finally {
                player.getInventory().setItem(slot, old);
            }
        } catch (Exception ex) {
            player.sendMessage("\u00a7cYour server does not expose the 1.8.8 book packet.");
        }
    }

    private static void openPacket(Player player, String version) throws Exception {
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
        Object handle = craftPlayer.getMethod("getHandle").invoke(player);
        Object connection = handle.getClass().getField("playerConnection").get(handle);
        Class<?> payload = Class.forName("net.minecraft.server." + version + ".PacketPlayOutCustomPayload");
        Class<?> serializer = Class.forName("net.minecraft.server." + version + ".PacketDataSerializer");
        Class<?> unpooled = Class.forName("io.netty.buffer.Unpooled");
        Object buffer = unpooled.getMethod("buffer", int.class).invoke(null, 1);
        buffer.getClass().getMethod("writeByte", int.class).invoke(buffer, 0);
        Object data = serializer.getConstructor(Class.forName("io.netty.buffer.ByteBuf")).newInstance(buffer);
        Object packet = payload.getConstructor(String.class, serializer).newInstance("MC|BOpen", data);
        connection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server." + version + ".Packet")).invoke(connection, packet);
    }

    public static String page(String body) {
        return "{\"text\":\"" + escape(body) + "\"}";
    }

    public static String option(String text, String command, String hover, String color) {
        return "{\"text\":\"" + escape(text) + "\",\"color\":\"" + color + "\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + escape(command) + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"text\":\"" + escape(hover) + "\",\"color\":\"white\"}}}";
    }

    public static String compose(String... components) {
        StringBuilder b = new StringBuilder("{\"text\":\"\",\"extra\":[");
        for (int i = 0; i < components.length; i++) {
            if (i > 0) b.append(',');
            b.append(components[i]);
        }
        return b.append("]}").toString();
    }

    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
