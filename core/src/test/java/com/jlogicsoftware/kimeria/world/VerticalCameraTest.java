package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness.STEP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The vertical follow in a shipped room (D154, #678): the hero moves for real and the camera the game is given is measured. */
class VerticalCameraTest {
    private static final float FLOOR_CAMERA = 8, HIGH_CAMERA = 0, PEAK_STEP = .4f;
    /** A hero height far above the floor, in open air at the room entrance. */
    private static final float HIGH_Y = 100;
    private static final int SETTLE_FRAMES = 90;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private Level level;
    private float previous, largestStep, highestHero, highestCamera;

    @BeforeEach void setUp() {
        harness.setUp();
        level = harness.room("forest-1", "forest-1-escalators");
        frames(SETTLE_FRAMES);
        previous = harness.camera.y;
        largestStep = 0;
        highestHero = highestCamera = Float.MAX_VALUE;
    }

    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private void frames(int count) {
        for (int i = 0; i < count; i++) {
            level.update(STEP);
            harness.pressed.clear();
            largestStep = Math.max(largestStep, Math.abs(harness.camera.y - previous));
            previous = harness.camera.y;
            highestHero = Math.min(highestHero, level.player.position.y);
            highestCamera = Math.min(highestCamera, harness.camera.y);
        }
    }

    @Test void aJumpFromTheFloorDoesNotScroll() {
        assertEquals(FLOOR_CAMERA, harness.camera.y, 0f);
        float floor = level.player.position.y;
        harness.pressed.add(Input.Keys.J);
        harness.held.add(Input.Keys.J);
        frames(SETTLE_FRAMES);
        assertTrue(highestHero < floor - 50, "the hero jumped: " + highestHero);
        assertEquals(FLOOR_CAMERA, highestCamera, 0f);
        assertEquals(FLOOR_CAMERA, harness.camera.y, 0f);
    }

    @Test void aRoomEntryStartsOnTheFloorCamera() {
        level = harness.room("forest-1", "forest-1-escalators");
        level.update(STEP);
        assertEquals(FLOOR_CAMERA, harness.camera.y, 0f);
    }

    @Test void theEaseWaitsForAPan() {
        harness.cameraPanning = true;
        holdHigh(SETTLE_FRAMES);
        assertEquals(FLOOR_CAMERA, harness.camera.y, 0f);
        harness.cameraPanning = false;
        holdHigh(1);
        assertTrue(FLOOR_CAMERA - harness.camera.y <= PEAK_STEP, "it eases from where the pan left it: " + harness.camera.y);
    }

    @Test void aMapReloadSettlesTheFollowWhereTheHeroWasLeft() {
        Vector2 saved = new Vector2(level.player.position.x, HIGH_Y);
        level.reloadAt(saved);
        assertEquals(HIGH_CAMERA, harness.camera.y, 0f, "the pan goes to the saved place");
        level.player.velocity.setZero();
        level.update(STEP);
        assertTrue(harness.camera.y <= PEAK_STEP, "no jump after the pan: " + harness.camera.y);
    }

    private void holdHigh(int count) {
        for (int i = 0; i < count; i++) {
            level.player.position.y = HIGH_Y;
            level.player.velocity.setZero();
            frames(1);
        }
    }

    @Test void aRiseAndAFallPastMidScreenScrollGraduallyAndSettleInsideTheRoom() {
        holdHigh(SETTLE_FRAMES); // in the air
        assertEquals(HIGH_CAMERA, harness.camera.y, 0f);
        assertTrue(largestStep <= PEAK_STEP, "up: " + largestStep);
        largestStep = 0;
        frames(SETTLE_FRAMES); // let go: the hero falls to the floor
        assertEquals(FLOOR_CAMERA, harness.camera.y, 0f);
        assertTrue(largestStep <= PEAK_STEP, "down: " + largestStep);
    }
}
