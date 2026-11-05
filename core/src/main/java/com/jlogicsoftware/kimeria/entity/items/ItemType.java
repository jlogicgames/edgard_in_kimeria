package com.jlogicsoftware.kimeria.entity.items;

import java.util.Locale;

/**
 * Collectables. The coin keeps its original two frames in Items.png; the healer ingredients
 * (Bat, Mushroom, #229) are their own two-frame 32x16 strips, shared by world items and
 * (as 128px redraws in images/HUD) the inventory icons.
 */
public enum ItemType {
    COIN("images/Items.png", 0, 0), BAT("images/objects/bat.png", 0, 0), MUSHROOM("images/objects/mushroom.png", 0, 0);

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
