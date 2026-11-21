package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** D121: a wall is solid and nothing more. In the air, pressing into it does not slow the fall and a jump press does nothing. */
class WallContactTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    /** forest-walls' first wall (x 912-928, top y 208, floor y 320): the hero's body centre just left of its face, feet at y 230. */
    private static final float BODY_CENTRE_X = 906, FEET_Y = 230;
    private static final int FRAMES = 18;
    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void tearDown() { h.restoreGlobals(); }

    private Level airborneBesideTheWall() {
        Level level = h.room("forest-2", "forest-walls");
        level.player.position.set(BODY_CENTRE_X - 23.5f, FEET_Y - 48);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        h.held.clear(); h.pressed.clear();
        return level;
    }

    @Test void pressingIntoAWallInTheAirDoesNotSlowTheFallAndAJumpPressDoesNothing() {
        Level free = airborneBesideTheWall(), pressing = airborneBesideTheWall();
        for (int f = 0; f < FRAMES; f++) {
            h.held.clear(); h.pressed.clear();
            free.update(STEP);
            h.held.add(Input.Keys.D);
            if (f == 4 || f == 9) { h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); }
            pressing.update(STEP);
            Rectangle body = pressing.player.hitboxBounds();
            assertTrue(body.x + body.width >= 912 - 0.5f, "the hero is at the wall's face at frame " + f + ": " + body);
            assertFalse(pressing.player.isOnGround(), "still falling at frame " + f);
            assertTrue(pressing.player.velocity().y >= 0, "no upward push at frame " + f + ": " + pressing.player.velocity().y);
            assertEquals(free.player.velocity().y, pressing.player.velocity().y, 1e-3f, "the same fall speed at frame " + f);
            assertEquals(free.player.position.y, pressing.player.position.y, 1e-3f, "the same height at frame " + f);
        }
    }
}
