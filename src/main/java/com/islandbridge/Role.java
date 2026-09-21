package com.islandbridge;

public enum Role {
    CREWMATE("§bCREWMATE"),
    IMPOSTOR("§cIMPOSTOR");

    private final String display;

    Role(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
