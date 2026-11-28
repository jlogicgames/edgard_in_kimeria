package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vertical follow while the hero is carried past mid-screen for real, on a shipped lift and a shipped ladder, at several
 * frame rates (D154, D98, #678). The camera the game is given is measured on every frame. World Y grows downward.
 */
class VerticalCameraRideTest {
    private static final float FLOOR_CAMERA = 8, HIGH_CAMERA = 0;
    /** The spring's peak over those 8 px, 8 x 8 / e = 23.5 px/s, rounded up; the snap onto the target adds at most 0.05 px. */
    private static final float PEAK_PX_PER_SECOND = 23.6f, SNAP_PX = .05f;
    /** An 8 px scroll that takes less than this reads as a jump. */
    private static final float SHORTEST_SCROLL_SECONDS = .3f;
    private static final float SETTLE_SECONDS = 1.5f, RIDE_LIMIT_SECONDS = 6f;
    /** The hero's box: its body centre and its feet, from the top left corner. */
    private static final float BODY_CENTRE_X = 23.5f, FEET = 48f;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private Level level;
    private float dt;

    @BeforeEach void setUp() {
        harness.setUp();
        harness.invulnerable = true; // the ride is under test, not the room's dangers
    }

    @AfterEach void tearDown() { harness.restoreGlobals(); }

    @ParameterizedTest @ValueSource(ints = {30, 60, 144})
    void aLiftRidePastMidScreenScrollsGraduallyBothWays(int framesPerSecond) {
        enter("forest-0", "forest-0-platform", framesPerSecond);
        Escalator lift = level.escalators.get(0);
        stand(lift.getX() + lift.getWidth() / 2f, lift.getY());
        run(SETTLE_SECONDS);
        assertEquals(FLOOR_CAMERA, harness.camera.y, 0f, "boarding at the bottom");
        lift.performAction();
        float bottom = lift.getY();
        float[] top = {bottom};
        // Up to the top of the travel: the lift turns round there.
        Scroll up = scroll(-1, () -> {
            boolean turned = lift.getY() > top[0];
            top[0] = Math.min(top[0], lift.getY());
            return turned;
        });
        assertTrue(bottom - top[0] > 100, "the lift rose: " + (bottom - top[0]));
        assertEquals(HIGH_CAMERA, harness.camera.y, 0f);
        assertRiding(lift);
        up.assertGradual();
        Scroll down = scroll(1, () -> lift.getY() >= bottom);
        assertRiding(lift);
        lift.performAction(); // stopped at the bottom: the camera, a little behind, comes to rest
        scroll(down, 1, () -> harness.camera.y == FLOOR_CAMERA);
        down.assertGradual();
    }

    @ParameterizedTest @ValueSource(ints = {30, 60, 144})
    void aLadderClimbPastMidScreenScrollsGraduallyBothWays(int framesPerSecond) {
        enter("forest-1", "forest-1-bramble", framesPerSecond);
        Ladder ladder = level.ladders.get(0);
        stand(ladder.getX() + ladder.getWidth() / 2f, ladder.bottomY());
        run(SETTLE_SECONDS);
        assertEquals(FLOOR_CAMERA, harness.camera.y, 0f, "at the foot of the ladder");
        harness.held.add(Input.Keys.W);
        boolean[] climbed = {false};
        Scroll up = scroll(-1, () -> {
            climbed[0] |= level.player.onLadder();
            return climbed[0] && !level.player.onLadder();
        });
        assertEquals(ladder.topY(), level.player.position.y + FEET, 1f, "he stepped off at the top");
        harness.held.clear();
        scroll(up, -1, () -> harness.camera.y == HIGH_CAMERA);
        run(SETTLE_SECONDS);
        assertEquals(HIGH_CAMERA, harness.camera.y, 0f, "it stays there");
        up.assertGradual();
        harness.held.add(Input.Keys.S);
        climbed[0] = false;
        Scroll down = scroll(1, () -> {
            climbed[0] |= level.player.onLadder();
            return climbed[0] && !level.player.onLadder();
        });
        harness.held.clear();
        scroll(down, 1, () -> harness.camera.y == FLOOR_CAMERA);
        run(SETTLE_SECONDS);
        assertEquals(FLOOR_CAMERA, harness.camera.y, 0f, "it stays there");
        down.assertGradual();
    }

    @ParameterizedTest @ValueSource(ints = {30, 60, 144})
    void aFallPastMidScreenScrollsGradually(int framesPerSecond) {
        enter("forest-1", "forest-1-escalators", framesPerSecond);
        run(SETTLE_SECONDS);
        float floor = level.player.position.y;
        for (int i = frames(SETTLE_SECONDS); i > 0; i--) { // held in the air until the camera is at rest
            level.player.position.y = floor - 156;
            level.player.velocity.setZero();
            level.update(dt);
        }
        assertEquals(HIGH_CAMERA, harness.camera.y, 0f);
        Scroll down = scroll(1, () -> level.player.isOnGround() && harness.camera.y == FLOOR_CAMERA);
        assertEquals(floor, level.player.position.y, 1f, "he landed");
        down.assertGradual();
    }

    private void enter(String map, String room, int framesPerSecond) {
        level = harness.room(map, room);
        dt = 1f / framesPerSecond;
    }

    private void stand(float centreX, float floorY) {
        level.player.position.set(centreX - BODY_CENTRE_X, floorY - FEET);
        level.player.velocity.setZero();
    }

    private void assertRiding(Escalator lift) {
        assertEquals(lift.getY(), level.player.position.y + FEET, 3f, "he is still on the lift");
    }

    private int frames(float seconds) { return Math.round(seconds / dt); }

    private void run(float seconds) {
        for (int i = frames(seconds); i > 0; i--) level.update(dt);
    }

    private interface Until { boolean reached(); }

    /** Runs until {@code until}, measuring the camera on every frame; {@code direction} is -1 up the screen, 1 down it. */
    private Scroll scroll(int direction, Until until) { return scroll(new Scroll(), direction, until); }

    /** As {@link #scroll(int, Until)}, adding to the measures of {@code scroll}: the rest of one ride. */
    private Scroll scroll(Scroll scroll, int direction, Until until) {
        float previous = harness.camera.y;
        for (int frame = 0; ; frame++) {
            assertTrue(frame < frames(RIDE_LIMIT_SECONDS), "the ride never ended: " + level.player.position);
            level.update(dt);
            float step = (harness.camera.y - previous) * direction;
            previous = harness.camera.y;
            assertTrue(step >= 0, "frame " + frame + " went back by " + -step);
            assertTrue(harness.camera.y >= HIGH_CAMERA && harness.camera.y <= FLOOR_CAMERA, "outside the room: " + harness.camera.y);
            scroll.largestStep = Math.max(scroll.largestStep, step);
            if (step > 0) scroll.movingFrames++;
            if (until.reached()) return scroll;
        }
    }

    private final class Scroll {
        private float largestStep;
        private int movingFrames;

        private void assertGradual() {
            assertTrue(largestStep <= PEAK_PX_PER_SECOND * dt + SNAP_PX, "largest step " + largestStep);
            assertTrue(movingFrames * dt >= SHORTEST_SCROLL_SECONDS, "scrolled in " + movingFrames * dt + " s");
        }
    }
}
