package com.mikaeel.nickbook.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Time based text animations, usable anywhere as {animation:name}. */
public final class Animations {
    private static final class Anim {
        final long interval;
        final List<String> frames;
        Anim(long interval, List<String> frames) { this.interval = interval; this.frames = frames; }
    }

    private volatile Map<String, Anim> animations = Collections.emptyMap();

    public void load(ConfigurationSection section) {
        Map<String, Anim> map = new HashMap<String, Anim>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                List<String> frames = new ArrayList<String>(section.getStringList(key + ".frames"));
                if (frames.isEmpty()) continue;
                map.put(key.toLowerCase(Locale.ROOT), new Anim(Math.max(50L, section.getLong(key + ".interval", 500L)), frames));
            }
        }
        animations = map;
    }

    public String frame(String name) {
        Anim anim = animations.get(name.toLowerCase(Locale.ROOT));
        if (anim == null) return "";
        int index = (int) ((System.currentTimeMillis() / anim.interval) % anim.frames.size());
        return anim.frames.get(index);
    }
}
