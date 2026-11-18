package com.jlogicsoftware.kimeria.world;

import com.jlogicsoftware.kimeria.effects.Firefly;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Gameplay fireflies in a Forest room are the glowing light of #472, not the black dot (the menu variant is unchanged). */
class ForestFirefliesTest {
    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    /** Every gameplay firefly in forest-2 is the Forest light: after its first flight its glow is about 4 px across. */
    @Test void forestGameplayFirefliesAreTheSmallGlow() throws Exception {
        Level level = h.roomOf("forest-2", 0);
        var ambienceField = Level.class.getDeclaredField("ambience");
        ambienceField.setAccessible(true);
        List<?> ambience = (List<?>) ambienceField.get(level);
        List<Firefly> fireflies = ambience.stream().filter(Firefly.class::isInstance).map(Firefly.class::cast).toList();
        var radius = Firefly.class.getDeclaredField("radius");
        radius.setAccessible(true);

        assertEquals(24, fireflies.size(), "forest-2 authors fireflies=24 in its TMX");
        assertEquals(fireflies.size(), ambience.size(), "the room has no other ambience");
        for (Firefly firefly : fireflies) {
            firefly.update(1f); // the first hide lasts at most 0.6 s, so the flight has started
            float r = radius.getFloat(firefly);
            assertTrue(r >= 1.75f && r <= 2.25f, "forest firefly radius " + r);
        }
    }

    /** The Forest light is about 4 px across (radius ~2 px); the menu's glow keeps its own, larger radius. */
    @Test void forestLightIsSmallAndTheMenuGlowIsUnchanged() throws Exception {
        var radius = Firefly.class.getDeclaredField("radius");
        radius.setAccessible(true);
        var area = new com.badlogic.gdx.math.Vector2(640, 360);
        var color = com.badlogic.gdx.graphics.Color.valueOf("b8f23a");
        Firefly forest = Firefly.smallGlow(area, color);
        Firefly menu = Firefly.glowing(area, color);
        for (int i = 0; i < 20; i++) { // each flight starts after at most 0.6 s of hiding
            forest.update(1f);
            menu.update(1f);
            assertTrue(radius.getFloat(forest) >= 1.75f && radius.getFloat(forest) <= 2.25f, "forest radius " + radius.getFloat(forest));
            assertTrue(radius.getFloat(menu) >= 4f && radius.getFloat(menu) <= 6f, "menu radius " + radius.getFloat(menu));
        }
    }

}
