package com.mikaeel.nickbook.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reads the player's REAL LuckPerms prefix/suffix/primary group through reflection (cached). */
public final class LuckPermsHook {
    private static boolean initialised, available;
    private static Object adapter;
    private static Method getMeta, getUser, getPrefix, getSuffix, getPrimaryGroup;

    private LuckPermsHook() {}

    private static synchronized boolean init() {
        if (initialised) return available;
        initialised = true;
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return false;
        try {
            Object lp = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Class<?> lpClass = Class.forName("net.luckperms.api.LuckPerms");
            Class<?> adapterClass = Class.forName("net.luckperms.api.platform.PlayerAdapter");
            Class<?> metaClass = Class.forName("net.luckperms.api.cacheddata.CachedMetaData");
            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            adapter = lpClass.getMethod("getPlayerAdapter", Class.class).invoke(lp, Player.class);
            getMeta = findAdapterMethod(adapterClass, "getMetaData");
            getUser = findAdapterMethod(adapterClass, "getUser");
            getPrefix = metaClass.getMethod("getPrefix");
            getSuffix = metaClass.getMethod("getSuffix");
            getPrimaryGroup = userClass.getMethod("getPrimaryGroup");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
        return available;
    }

    private static Method findAdapterMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == 1) return method;
        }
        throw new NoSuchMethodException(name);
    }

    /** Called on reload so a late-loading LuckPerms is picked up. */
    public static synchronized void reset() { initialised = false; }

    public static String prefix(Player player) { return meta(player, getPrefixRef()); }
    public static String suffix(Player player) { return meta(player, getSuffixRef()); }

    private static Method getPrefixRef() { return init() ? getPrefix : null; }
    private static Method getSuffixRef() { return init() ? getSuffix : null; }

    private static String meta(Player player, Method getter) {
        if (getter == null) return "";
        try {
            Object value = getter.invoke(getMeta.invoke(adapter, player));
            return value == null ? "" : value.toString();
        } catch (InvocationTargetException t) {
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    public static String primaryGroup(Player player) {
        if (!init()) return null;
        try {
            Object value = getPrimaryGroup.invoke(getUser.invoke(adapter, player));
            return value == null ? null : value.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
