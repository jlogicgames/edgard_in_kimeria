package com.jlogicsoftware.kimeria.entity.items;

import java.util.Locale;

/**
 * Collectables. Each is a two-frame 32x16 strip in images/objects: the coin, which carries the sun of
 * the Kimeria arms (#350), and the healer ingredients (Bat, Mushroom, #229), whose frames are also
 * redrawn at 128px in images/HUD as the inventory icons.
 */
public enum ItemType {
    COIN("images/objects/coin.png", 0, 0), BAT("images/objects/bat.png", 0, 0), MUSHROOM("images/objects/mushroom.png", 0, 0);

    public final String sheet;
    public final int spriteX, spriteY;

    ItemType(String sheet, int spriteX, int spriteY) {
        this.sheet = sheet;
        this.spriteX = spriteX;
        this.spriteY = spriteY;
    }

    /** Base name of the HUD icon under images/HUD (ingredients only). */
    public String iconName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ItemType fromMapName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "coin" -> COIN;
            case "bat" -> BAT;
            case "mushroom" -> MUSHROOM;
            default -> throw new IllegalArgumentException("Unknown collectable: " + name);
        };
    }
}
