package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The shared flight helper ({@link FlightFx}, D132). World and screen Y grow downward: a bow up is a smaller Y. */
class FlightFxTest {
    private static final float STEP = 1f / 60f, AX = 300, AY = 200, BX = 26, BY = 26, ARC = 24;
    private final TextureRegion sprite = new TextureRegion();
    private final float[] head = new float[2];

    private int fly(FlightFx fx, byte ease, float delay, float seconds) {
        return fx.start(sprite, AX, AY, BX, BY, ARC, 20, 10, delay, seconds, ease, .65f, 1, 1, 1, true, true);
    }

    @Test void theHeadIsExactlyAtAWhenItStartsAndAtBWhenItArrives() {
        for (byte ease : new byte[]{FlightFx.EASE_IN, FlightFx.EASE_OUT, FlightFx.EASE_IN_OUT}) {
            FlightFx fx = new FlightFx(1);
            int slot = fly(fx, ease, 0, .4f);
            fx.headOf(slot, 0f, head);
            assertEquals(AX, head[0], 1e-4f);
            assertEquals(AY, head[1], 1e-4f);
            fx.headOf(slot, 1f, head);
            assertEquals(BX, head[0], 1e-4f);
            assertEquals(BY, head[1], 1e-4f);
        }
    }

    @Test void everyEaseAdvancesTheHeadMonotonicallyAlongTheChord() {
        for (byte ease : new byte[]{FlightFx.EASE_IN, FlightFx.EASE_OUT, FlightFx.EASE_IN_OUT}) {
            FlightFx fx = new FlightFx(1);
            int slot = fly(fx, ease, 0, .4f);
            float previous = Float.MAX_VALUE;
            for (int n = 0; n <= 40; n++) {
                fx.headOf(slot, n / 40f, head);
                assertTrue(head[0] <= previous + 1e-3f, "x only moves towards B (smaller x) at " + n);
                previous = head[0];
            }
        }
    }

    @Test void theFlightBowsUpOnScreenNeverDown() {
        FlightFx fx = new FlightFx(1);
        int slot = fly(fx, FlightFx.EASE_IN_OUT, 0, .4f);
        fx.headOf(slot, .5f, head);
        float chordY = (AY + BY) / 2f;
        assertEquals(chordY - ARC / 2f, head[1], 1e-3f, "the Bezier's midpoint is half the bow above the chord: smaller y is higher");
        for (int n = 0; n <= 40; n++) {
            fx.headOf(slot, n / 40f, head);
            float chord = AY + (BY - AY) * ((AX - head[0]) / (AX - BX));
            assertTrue(head[1] <= chord + 1e-2f, "never below the chord at " + n);
        }
    }

    @Test void aFlightLandsOnceFreesItsSlotAndTwinklesAtB() {
        FlightFx fx = new FlightFx(1);
        int slot = fly(fx, FlightFx.EASE_IN, 0, .4f);
        assertEquals(1, fx.count());
        boolean landed = false;
        for (int n = 0; n < 40 && !landed; n++) { fx.update(STEP); landed = fx.landed(slot); }
        assertTrue(landed, "lands within 0.4 s plus a frame");
        assertEquals(0, fx.count());
        assertFalse(fx.active(slot));
        int sparks = 0;
        for (int i = 0; i < fx.trail().count(); i++) if (Math.abs(fx.trail().x(i) - BX) < 3) sparks++;
        assertTrue(sparks >= 4, "the landing twinkle starts at B");
        fx.update(STEP);
        assertFalse(fx.landed(slot), "landed is true for one update only");
    }

    @Test void aDelayedFlightWaitsAndShowsNothingUntilItsTime() {
        FlightFx fx = new FlightFx(1);
        int slot = fly(fx, FlightFx.EASE_IN, .5f, .6f);
        for (int n = 0; n < 29; n++) fx.update(STEP);
        assertEquals(0f, fx.progress(slot), 0f, "still waiting at 0.48 s");
        assertEquals(0, fx.trail().count(), "no trail while it waits");
        for (int n = 0; n < 12; n++) fx.update(STEP);
        assertTrue(fx.progress(slot) > 0f);
    }

    @Test void aFullPoolDropsTheNewRequestNeverALiveFlight() {
        FlightFx fx = new FlightFx(1);
        for (int n = 0; n < FlightFx.CAPACITY; n++) assertTrue(fly(fx, FlightFx.EASE_IN, 0, .4f) >= 0);
        assertEquals(-1, fly(fx, FlightFx.EASE_IN, 0, .4f));
        assertEquals(FlightFx.CAPACITY, fx.count());
    }

    @Test void noUpdateMeansNoChangeSoPauseFreezesAFlight() {
        FlightFx fx = new FlightFx(1);
        int slot = fly(fx, FlightFx.EASE_IN, 0, .4f);
        for (int n = 0; n < 10; n++) fx.update(STEP);
        float before = fx.progress(slot);
        assertTrue(before > 0f);
        assertEquals(before, fx.progress(slot), 0f);
    }

    @Test void aHitchIsClampedSoAFlightNeverTeleports() {
        FlightFx fx = new FlightFx(1);
        int slot = fly(fx, FlightFx.EASE_IN, 0, .4f);
        fx.update(5f);
        assertEquals(FlightFx.MAX_STEP / .4f, fx.progress(slot), 1e-4f);
        fx.update(-1f);
        assertEquals(FlightFx.MAX_STEP / .4f, fx.progress(slot), 1e-4f, "a negative dt does not run it backwards");
    }

    @Test void clearDropsEveryFlightAndSpark() {
        FlightFx fx = new FlightFx(1);
        fly(fx, FlightFx.EASE_IN, 0, .4f);
        for (int n = 0; n < 5; n++) fx.update(STEP);
        assertTrue(fx.trail().count() > 0);
        fx.clear();
        assertEquals(0, fx.count());
        assertEquals(0, fx.trail().count());
    }

    /** The trail pool must hold the busiest case: all slots flying at once, over many frame rates. */
    @Test void theTrailPoolNeverRunsFull() {
        for (int fps : new int[]{30, 60, 144, 240}) {
            FlightFx fx = new FlightFx(fps);
            int peak = 0;
            for (int frame = 0; frame < fps * 3; frame++) {
                if (frame % (fps / 6 + 1) == 0) fly(fx, FlightFx.EASE_IN, 0, .4f);
                fx.update(1f / fps);
                peak = Math.max(peak, fx.trail().count());
            }
            assertTrue(peak <= FlightFx.TRAIL_CAPACITY * 3 / 4, fps + " fps peak " + peak + " leaves a quarter free");
        }
    }
}
