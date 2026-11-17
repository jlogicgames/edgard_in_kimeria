package com.jlogicsoftware.kimeria.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** D92 (#430): the map property {@code background} is the flat colour behind the world; a malformed one fails the load. */
class LevelBackgroundTest {
    private static final float EXACT = 0f;

    @Test void tiledColoursParseWithOrWithoutOpaqueAlpha() {
        float[] rgb = new float[3];
        Level.parseBackground("#ff4a92b8", rgb);
        assertArrayEquals(new float[] {0x4a / 255f, 0x92 / 255f, 0xb8 / 255f}, rgb, EXACT);
        Level.parseBackground("#2c3f66", rgb);
        assertArrayEquals(new float[] {0x2c / 255f, 0x3f / 255f, 0x66 / 255f}, rgb, EXACT);
    }

    @Test void theDefaultIsTheForestBlue() {
        float[] rgb = new float[3];
        Level.parseBackground(Level.DEFAULT_BACKGROUND, rgb);
        assertArrayEquals(new float[] {0x4a / 255f, 0x92 / 255f, 0xb8 / 255f}, rgb, EXACT);
    }

    @Test void aMalformedOrTranslucentColourFailsLoudly() {
        for (String bad : new String[] {"#804a92b8", "#4a92b", "blue", "#gg92b8", "", "#-4a92b", "#+4a92b8", "#ff-4a92b"}) {
            assertThrows(IllegalArgumentException.class, () -> Level.parseBackground(bad, new float[3]), bad);
        }
    }
}
