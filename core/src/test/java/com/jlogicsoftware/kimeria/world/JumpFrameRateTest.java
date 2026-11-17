package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The jump is as high at every frame rate (D98). The desktop build runs at 60 fps, a browser at the display's refresh
 * rate (120 Hz on a ProMotion Mac, 144 Hz on a gaming monitor) with uneven frame times; gravity used to be added per
 * frame, so the web jump on a 120 Hz display was about half as high and the forest-1-lever ledge could not be reached.
 */
class JumpFrameRateTest {
    private static final float ROOM_X = 4320, START_X = ROOM_X + 32, FLOOR = 320, LIFT_X = ROOM_X + 96, LIFT_RIGHT = ROOM_X + 128;
    private static final float A_X = ROOM_X + 128, A_END = ROOM_X + 240, LEDGE_TOP = 208;
    /** The apex of a standing jump at 60 Hz, measured before and after D98: feet rise this many px. */
    private static final float DESIGNED_APEX = 59.67f;
    /** Allowed spread of the apex across frame rates (measured 0.00 px after D98; 94 px before it, 30 Hz against 144 Hz). */
    private static final float APEX_TOLERANCE = 0.1f;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    /** A named frame-time pattern: the deltas repeat in order. */
    private record Rate(String name, float... deltas) {
        float dt(int frame) { return deltas[frame % deltas.length]; }
    }

    private static final Rate[] RATES = {
        new Rate("30 Hz", 1f / 30),
        new Rate("60 Hz", 1f / 60),
        new Rate("120 Hz", 1f / 120),
        new Rate("144 Hz", 1f / 144),
        // A browser at 120 Hz: rAF timestamps jitter around 8.3 ms, now and then a frame is late or dropped.
        new Rate("browser 120 Hz", 0.0081f, 0.0086f, 0.0079f, 0.0167f, 0.0083f, 0.0090f, 0.0076f, 0.0084f, 0.0250f, 0.0082f),
        // A browser at 60 Hz with the same kind of jitter.
        new Rate("browser 60 Hz", 0.0161f, 0.0172f, 0.0158f, 0.0333f, 0.0166f, 0.0180f, 0.0153f, 0.0168f),
    };

    private Level leverRoom() {
        Level level = new Level(h.assets(), h.game(), new Player(h.assets(), h.game(), 0, 0), "forest-1", Rectangle::new);
        for (int i = 0; i < 3; i++) level.enterNextRoom(new Player(h.assets(), h.game(), 0, 0)); // -> forest-1-lever
        return level;
    }

    private static float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }
    private static float centerX(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }

    private int frame;
    private void step(Level level, Rate rate) {
        level.update(rate.dt(frame++));
        pressed.clear();
        assertFalse(level.player.isGotHit(), "died at " + level.player.position);
    }

    /** Feet rise of one standing jump from the room's floor (larger Y is lower, so the rise is start minus the smallest feet Y). */
    private float standingApex(Rate rate) {
        Level level = leverRoom();
        frame = 0;
        for (int i = 0; i < 240 && !(level.player.isOnGround() && Math.abs(feet(level) - FLOOR) < 0.5f); i++) step(level, rate);
        for (int i = 0; i < 30; i++) step(level, rate);
        assertTrue(level.player.isOnGround(), "not standing at " + level.player.position);
        float start = feet(level), top = start;
        held.add(Input.Keys.J); pressed.add(Input.Keys.J);
        for (int i = 0; i < 600; i++) {
            step(level, rate);
            held.clear();
            top = Math.min(top, feet(level));
            if (i > 2 && level.player.isOnGround()) break;
        }
        return start - top;
    }

    @Test void theStandingJumpRisesAsHighAtEveryFrameRate() {
        StringBuilder report = new StringBuilder();
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (Rate rate : RATES) {
            float apex = standingApex(rate);
            report.append(String.format(Locale.ROOT, "%s: %.2f px%n", rate.name(), apex));
            min = Math.min(min, apex);
            max = Math.max(max, apex);
        }
        assertTrue(max - min <= APEX_TOLERANCE, "apex differs by frame rate:\n" + report);
    }

    @Test void theDesignedSixtyHertzJumpHeightIsUnchanged() {
        assertEquals(DESIGNED_APEX, standingApex(RATES[1]), 0.05f);
    }

    /**
     * The reported spot: ride the running lift up beside the lever ledge (its top 32 px above the lift's highest point)
     * and hop right onto it. It must work at every frame rate, hopping only once the lift has reached its top.
     */
    @Test void theLeverLedgeIsReachedFromTheLiftAtEveryFrameRate() {
        for (Rate rate : RATES) {
            Level level = leverRoom();
            Escalator lift = level.escalators.get(0);
            frame = 0;
            boolean hopping = false;
            int frames = 0;
            while (!(level.player.isOnGround() && Math.abs(feet(level) - LEDGE_TOP) < 2 && centerX(level) >= A_X - 8 && centerX(level) <= A_END + 8)) {
                held.clear();
                boolean riding = level.player.currentEscalator() == lift;
                if (riding) {
                    // The hardest case for the hop: wait until the lift has stopped rising, at the top of its run.
                    if (lift.getY() <= 241 && level.player.isOnGround()) { hopping = true; held.add(Input.Keys.D); held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                } else if (!level.player.isOnGround()) {
                    if (hopping) held.add(Input.Keys.D);
                } else if (centerX(level) < LIFT_X + 4) held.add(Input.Keys.D);
                else if (centerX(level) > LIFT_RIGHT - 4) held.add(Input.Keys.A);
                else if (feet(level) > 300 && lift.getY() >= 280) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                step(level, rate);
                if (level.player.isOnGround() && level.player.currentEscalator() != lift) hopping = false;
                assertTrue(++frames < 144 * 30, rate.name() + ": lever ledge unreachable, hero at " + level.player.position
                    + " lift y=" + lift.getY() + " feet=" + feet(level));
            }
            held.clear();
        }
    }
}
