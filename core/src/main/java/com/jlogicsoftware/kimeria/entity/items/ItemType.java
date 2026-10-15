package com.jlogicsoftware.kimeria.entity.items;

import java.util.Locale;

/** Two adjacent 16x16 frames in Items.png, shared by world items and HUD icons. */
public enum ItemType {
    COIN(0, 0), CHICKEN(32, 16), KEY(64, 16);

    public final int spriteX, spriteY;

    ItemType(int spriteX, int spriteY) {
        this.spriteX = spriteX;
        this.spriteY = spriteY;
    }

    public static ItemType fromMapName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "coin" -> COIN;
            case "chicken", "chiken" -> CHICKEN;
            case "key" -> KEY;
            default -> throw new IllegalArgumentException("Unknown collectable: " + name);
        };
    }
}
