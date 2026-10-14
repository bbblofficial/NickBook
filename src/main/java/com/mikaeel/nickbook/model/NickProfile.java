package com.mikaeel.nickbook.model;
public final class NickProfile {
    private final String rank, skin, name;
    public NickProfile(String rank, String skin, String name) { this.rank = rank; this.skin = skin; this.name = name; }
    public String rank() { return rank; } public String skin() { return skin; } public String name() { return name; }
}
