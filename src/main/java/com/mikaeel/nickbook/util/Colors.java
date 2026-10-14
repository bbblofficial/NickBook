package com.mikaeel.nickbook.util;

import org.bukkit.ChatColor;

import java.util.function.IntBinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Color translation for every NickBook text.
 *  &a &l ...                       classic codes
 *  &#FF5555  or  {#FF5555}         hex colors (1.16+, nearest legacy color on 1.8-1.15)
 *  <gradient:#FF0000:#FFFF00>x</gradient>
 *  <rainbow>x</rainbow>
 */
public final class Colors {
    private static final Pattern GRADIENT = Pattern.compile("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>", Pattern.DOTALL);
    private static final Pattern RAINBOW = Pattern.compile("<rainbow>(.*?)</rainbow>", Pattern.DOTALL);
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})|\\{#([A-Fa-f0-9]{6})\\}");
    private static final String ALL_CODES = "0123456789abcdefklmnor";
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final String LEGACY_CHARS = "0123456789abcdef";
    public static final boolean HEX_SUPPORTED = detectHex();

    private Colors() {}

    private static boolean detectHex() {
        try {
            Class.forName("net.md_5.bungee.api.ChatColor").getMethod("of", String.class);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Full translation (config text). */
    public static String color(String text) {
        if (text == null || text.isEmpty()) return "";
        return legacy(rich(text), ALL_CODES);
    }

    /** Translation of text typed by a player, limited by what they are allowed to use. */
    public static String colorUser(String text, boolean colors, boolean formats, boolean magic, boolean hex) {
        if (text == null || text.isEmpty()) return "";
        String allowed = (colors ? "0123456789abcdef" : "") + (formats ? "lmnor" : "") + (magic ? "k" : "");
        String out = hex ? rich(text) : text;
        return allowed.isEmpty() ? out : legacy(out, allowed);
    }

    public static String strip(String text) {
        return text == null ? "" : ChatColor.stripColor(text);
    }

    private static String legacy(String text, String allowed) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && allowed.indexOf(Character.toLowerCase(chars[i + 1])) >= 0) {
                chars[i] = '\u00a7';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    private static String rich(String text) {
        if (text.indexOf('<') >= 0) {
            Matcher g = GRADIENT.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (g.find()) {
                final int from = Integer.parseInt(g.group(1), 16), to = Integer.parseInt(g.group(2), 16);
                g.appendReplacement(sb, Matcher.quoteReplacement(paint(g.group(3), (i, n) -> lerp(from, to, n <= 1 ? 0 : (double) i / (n - 1)))));
            }
            g.appendTail(sb);
            text = sb.toString();
            Matcher r = RAINBOW.matcher(text);
            sb = new StringBuffer();
            while (r.find()) {
                r.appendReplacement(sb, Matcher.quoteReplacement(paint(r.group(1), (i, n) -> java.awt.Color.HSBtoRGB(n == 0 ? 0f : (float) i / n, 1f, 1f) & 0xFFFFFF)));
            }
            r.appendTail(sb);
            text = sb.toString();
        }
        if (text.indexOf('#') >= 0) {
            Matcher h = HEX.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (h.find()) {
                String hex = h.group(1) != null ? h.group(1) : h.group(2);
                h.appendReplacement(sb, Matcher.quoteReplacement(hexCode(Integer.parseInt(hex, 16))));
            }
            h.appendTail(sb);
            text = sb.toString();
        }
        return text;
    }

    private static boolean isCode(char c) {
        return ALL_CODES.indexOf(Character.toLowerCase(c)) >= 0;
    }

    /** Colors every visible character, keeping &l/&o/... formats active across color changes. */
    private static String paint(String text, IntBinaryOperator colorAt) {
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < text.length() && isCode(text.charAt(i + 1))) { i++; continue; }
            visible++;
        }
        StringBuilder out = new StringBuilder();
        StringBuilder formats = new StringBuilder();
        int index = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '\u00a7') && i + 1 < text.length() && isCode(text.charAt(i + 1))) {
                char k = Character.toLowerCase(text.charAt(++i));
                if ("klmno".indexOf(k) >= 0) formats.append('\u00a7').append(k);
                else if (k == 'r') formats.setLength(0);
                continue;
            }
            out.append(hexCode(colorAt.applyAsInt(index++, visible))).append(formats).append(c);
        }
        return out.toString();
    }

    private static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Hex color as a legacy string: real hex on 1.16+, nearest of the 16 colors before that. */
    public static String hexCode(int rgb) {
        if (HEX_SUPPORTED) {
            String hex = String.format("%06x", rgb & 0xFFFFFF);
            StringBuilder b = new StringBuilder("\u00a7x");
            for (char c : hex.toCharArray()) b.append('\u00a7').append(c);
            return b.toString();
        }
        return "\u00a7" + nearestLegacy(rgb);
    }

    public static char nearestLegacy(int rgb) {
        int best = 15;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int c = LEGACY_RGB[i];
            long dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            long dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            long db = (c & 0xFF) - (rgb & 0xFF);
            long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (d < bestDist) { bestDist = d; best = i; }
        }
        return LEGACY_CHARS.charAt(best);
    }

    /** Last active color + formats of a legacy string (hex aware). */
    public static String lastColors(String s) {
        if (s == null) return "";
        String color = "";
        StringBuilder fmt = new StringBuilder();
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) != '\u00a7') continue;
            char k = Character.toLowerCase(s.charAt(i + 1));
            if (k == 'x' && i + 14 <= s.length()) {
                color = s.substring(i, i + 14);
                fmt.setLength(0);
                i += 13;
            } else if (LEGACY_CHARS.indexOf(k) >= 0) {
                color = "\u00a7" + k;
                fmt.setLength(0);
                i++;
            } else if ("klmno".indexOf(k) >= 0) {
                fmt.append('\u00a7').append(k);
                i++;
            } else if (k == 'r') {
                color = "";
                fmt.setLength(0);
                i++;
            }
        }
        return color + fmt;
    }

    /** Cuts a legacy string to max visible+code length without leaving a dangling section sign. */
    public static String cut(String s, int max) {
        if (s == null || max <= 0 || s.length() <= max) return s;
        String out = s.substring(0, max);
        while (out.endsWith("\u00a7")) out = out.substring(0, out.length() - 1);
        return out;
    }
}
