package com.mikaeel.nickbook.util;

/** Converts legacy (section sign) text to raw JSON chat components, used for hover/click chat. */
public final class JsonText {
    private static final String[] NAMES = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
    private static final String LEGACY = "0123456789abcdef";

    private JsonText() {}

    public static String escape(String value) {
        StringBuilder b = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.toString();
    }

    /** Comma separated component list (no surrounding brackets). */
    public static String components(String legacy) {
        StringBuilder out = new StringBuilder();
        StringBuilder text = new StringBuilder();
        String color = null;
        boolean bold = false, italic = false, under = false, strike = false, magic = false;
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == '\u00a7' && i + 1 < legacy.length()) {
                char k = Character.toLowerCase(legacy.charAt(i + 1));
                String newColor = null;
                int skip = 1;
                if (k == 'x' && i + 14 <= legacy.length()) {
                    StringBuilder hex = new StringBuilder("#");
                    for (int j = i + 3; j < i + 14; j += 2) hex.append(legacy.charAt(j));
                    newColor = hex.toString();
                    skip = 13;
                } else if (LEGACY.indexOf(k) >= 0) {
                    newColor = NAMES[LEGACY.indexOf(k)];
                } else if ("klmnor".indexOf(k) < 0) {
                    text.append(c);
                    continue;
                }
                flush(out, text, color, bold, italic, under, strike, magic);
                if (newColor != null || k == 'r') {
                    color = k == 'r' ? "white" : newColor;
                    bold = italic = under = strike = magic = false;
                } else {
                    switch (k) {
                        case 'k': magic = true; break;
                        case 'l': bold = true; break;
                        case 'm': strike = true; break;
                        case 'n': under = true; break;
                        case 'o': italic = true; break;
                        default: break;
                    }
                }
                i += skip;
                continue;
            }
            text.append(c);
        }
        flush(out, text, color, bold, italic, under, strike, magic);
        if (out.length() == 0) out.append("{\"text\":\"\"}");
        return out.toString();
    }

    private static void flush(StringBuilder out, StringBuilder text, String color, boolean bold, boolean italic,
                              boolean under, boolean strike, boolean magic) {
        if (text.length() == 0) return;
        if (out.length() > 0) out.append(',');
        out.append("{\"text\":\"").append(escape(text.toString())).append('"');
        if (color != null) out.append(",\"color\":\"").append(color).append('"');
        if (bold) out.append(",\"bold\":true");
        if (italic) out.append(",\"italic\":true");
        if (under) out.append(",\"underlined\":true");
        if (strike) out.append(",\"strikethrough\":true");
        if (magic) out.append(",\"obfuscated\":true");
        out.append('}');
        text.setLength(0);
    }

    /** One component wrapping legacy text, with optional hover text and click action (suggest/run/url). */
    public static String interactive(String legacy, String hoverLegacy, String clickAction, String clickValue) {
        StringBuilder b = new StringBuilder("{\"text\":\"\",\"extra\":[").append(components(legacy)).append(']');
        if (hoverLegacy != null && !hoverLegacy.isEmpty()) {
            b.append(",\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"text\":\"\",\"extra\":[")
                    .append(components(hoverLegacy)).append("]}}");
        }
        if (clickAction != null && clickValue != null && !clickValue.isEmpty()) {
            b.append(",\"clickEvent\":{\"action\":\"").append(clickAction).append("\",\"value\":\"")
                    .append(escape(clickValue)).append("\"}");
        }
        return b.append('}').toString();
    }

    public static String root(String... parts) {
        StringBuilder b = new StringBuilder("{\"text\":\"\",\"extra\":[");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) b.append(',');
            b.append(parts[i]);
        }
        return b.append("]}").toString();
    }
}
