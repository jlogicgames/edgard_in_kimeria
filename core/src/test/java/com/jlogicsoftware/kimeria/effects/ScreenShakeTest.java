package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The shake manager (#239): decay, strongest-wins, determinism, the room clamp and the Screen shake option. */
class ScreenShakeTest {
    private static final float STEP = 1f / 60f;
    /** A room much larger than the view on both axes, so the clamp does not interfere unless a test wants it to. */
    private static final Rectangle ROOM = new Rectangle(0, 0, 2000, 1000);
    private static final Rectangle VIEW = new Rectangle(500, 300, 640, 360);

    private static float peak(ScreenShake shake, Rectangle room, Rectangle view, int frames) {
        float peak = 0;
        for (int i = 0; i < frames; i++) {
            Vector2 offset = shake.offset(room, view);
            peak = Math.max(peak, Math.max(Math.abs(offset.x), Math.abs(offset.y)));
            shake.update(STEP);
        }
        return peak;
    }

    @Test void noShakeIsZero() {
        ScreenShake shake = new ScreenShake(() -> true);
        assertEquals(new Vector2(), shake.offset(ROOM, VIEW));
    }

    @Test void optionOffGivesZeroForEveryShakeIncludingTheBossOne() {
        boolean[] on = {false};
        ScreenShake shake = new ScreenShake(() -> on[0]);
        shake.start(ScreenShake.BOSS_AMPLITUDE_PX, ScreenShake.BOSS_SECONDS);
        shake.start(ScreenShake.LEVER_AMPLITUDE_PX, 1f);
        shake.update(.05f);
        assertEquals(new Vector2(), shake.offset(ROOM, VIEW));
        on[0] = true; // the option is read live: turning it on shows the running shake
        assertNotEquals(new Vector2(), shake.offset(ROOM, VIEW));
    }

    @Test void amplitudeDecaysLinearlyToZeroAtTheEnd() {
        ScreenShake shake = new ScreenShake(() -> true);
        shake.start(1.5f, 1f);
        assertEquals(1.5f, shake.currentAmplitude(), 1e-6f);
        shake.update(.5f);
        assertEquals(.75f, shake.currentAmplitude(), 1e-6f);
        shake.update(.5f);
        assertEquals(0f, shake.currentAmplitude());
        assertFalse(shake.running());
        assertEquals(new Vector2(), shake.offset(ROOM, VIEW));
        assertEquals(0f, peak(shake, ROOM, VIEW, 10));
    }

    @Test void offsetNeverExceedsTheAmplitudeAndIsNonZeroWhileRunning() {
        ScreenShake shake = new ScreenShake(() -> true);
        shake.start(1.5f, 1f);
        float peak = peak(shake, ROOM, VIEW, 60);
        assertTrue(peak > 0 && peak <= 1.5f, "peak " + peak);
    }

    @Test void aStrongerShakeReplacesAWeakerOneAndAWeakerOneDoesNotReplaceAStrongerOne() {
        ScreenShake shake = new ScreenShake(() -> true);
        shake.start(1f, 1f);
        shake.start(.5f, 5f); // weaker: ignored, the 5 s must not stretch the running shake
        assertEquals(1f, shake.currentAmplitude(), 1e-6f);
        shake.update(1f);
        assertFalse(shake.running());
        shake.start(.5f, 1f);
        shake.start(2f, 1f); // stronger: replaces
        assertEquals(2f, shake.currentAmplitude(), 1e-6f);
        shake.update(.9f);
        shake.start(.5f, 1f); // the running one has decayed to .2 px: now .5 px is the stronger and replaces it
        assertEquals(.5f, shake.currentAmplitude(), 1e-6f);
    }

    @Test void anEqualShakeRestartsLikeTheBossAttackAlwaysDid() {
        ScreenShake shake = new ScreenShake(() -> true);
        shake.start(ScreenShake.BOSS_AMPLITUDE_PX, ScreenShake.BOSS_SECONDS);
        shake.update(.1f);
        shake.start(ScreenShake.BOSS_AMPLITUDE_PX, ScreenShake.BOSS_SECONDS);
        assertEquals(ScreenShake.BOSS_AMPLITUDE_PX, shake.currentAmplitude(), 1e-6f);
    }

    @Test void invalidShakesAreIgnored() {
        ScreenShake shake = new ScreenShake(() -> true);
        shake.start(0, 1f); shake.start(1f, 0); shake.start(-1f, 1f); shake.start(Float.NaN, 1f); shake.start(1f, Float.NaN);
        assertFalse(shake.running());
    }

    @Test void clearStopsItAtOnce() {
        ScreenShake shake = new ScreenShake(() -> true);
        shake.start(1.5f, 1f);
        shake.clear();
        assertEquals(new Vector2(), shake.offset(ROOM, VIEW));
    }

    @Test void deterministicAcrossTwoRuns() {
        float[] first = run(), second = run();
        assertArrayEquals(first, second);
        assertTrue(java.util.stream.IntStream.range(0, first.length).anyMatch(i -> first[i] != 0), "the run must actually shake");
    }

    private static float[] run() {
        ScreenShake shake = new ScreenShake(() -> true);
        shake.start(1.5f, 1f);
        float[] out = new float[120];
        for (int i = 0; i < 60; i++) {
            Vector2 offset = shake.offset(ROOM, VIEW);
            out[2 * i] = offset.x; out[2 * i + 1] = offset.y;
            shake.update(STEP);
        }
        return out;
    }

    /** The body of {@code Level.cameraShakeOffset} as it was before the migration (age, amplitude, sines, clamp), copied verbatim. */
    private static Vector2 oldLevelOffset(float shakeRemaining, Rectangle room, Rectangle view) {
        float age = .18f - shakeRemaining;
        float amplitude = 1.25f * Math.max(0, shakeRemaining / .18f);
        float dx = (float) Math.sin(age * 110) * amplitude;
        float dy = (float) Math.sin(age * 137) * amplitude;
        if (room.width <= view.width) dx = 0;
        else dx = Math.max(room.x, Math.min(room.x + room.width - view.width, view.x + dx))
            - Math.max(room.x, Math.min(room.x + room.width - view.width, view.x));
        if (room.height <= view.height) dy = 0;
        else dy = Math.max(room.y, Math.min(room.y + room.height - view.height, view.y + dy))
            - Math.max(room.y, Math.min(room.y + room.height - view.height, view.y));
        return new Vector2(dx, dy);
    }

    @Test void theBossShakeIsBitIdenticalToTheOldLevelFormula() {
        Rectangle narrow = new Rectangle(0, 0, 640, 360); // same size as the view: both axes keep centred framing
        Rectangle[] rooms = {ROOM, narrow, new Rectangle(500, 0, 640, 1000), new Rectangle(0, 300, 2000, 360)};
        for (Rectangle room : rooms) {
            ScreenShake shake = new ScreenShake(() -> true);
            shake.start(ScreenShake.BOSS_AMPLITUDE_PX, ScreenShake.BOSS_SECONDS);
            float remaining = .18f;
            for (int i = 0; i < 14; i++) { // two frames past the end
                assertEquals(oldLevelOffset(remaining, room, VIEW), shake.offset(room, VIEW), "frame " + i + " room " + room);
                shake.update(STEP);
                remaining = Math.max(0, remaining - STEP);
            }
        }
    }

    @Test void theRoomClampIsHonoured() {
        ScreenShake shake = new ScreenShake(() -> true);
        shake.start(10f, 1f);
        shake.update(.01f);
        // A room as wide and tall as the view keeps its centred framing: no movement at all.
        assertEquals(new Vector2(), shake.offset(new Rectangle(500, 300, 640, 360), VIEW));
        // View flush with the room's left and top edge: a shake can only move it inwards, never outside the room.
        Rectangle edgeView = new Rectangle(0, 0, 640, 360);
        for (int i = 0; i < 40; i++) {
            Vector2 offset = shake.offset(ROOM, edgeView);
            assertTrue(offset.x >= 0 && offset.y >= 0, "left/top clamp at " + i + ": " + offset);
            shake.update(.01f);
        }
        // View flush with the right and bottom edge: only outwards-clamped, i.e. never positive.
        shake.start(10f, 1f);
        Rectangle farView = new Rectangle(ROOM.width - 640, ROOM.height - 360, 640, 360);
        for (int i = 0; i < 40; i++) {
            Vector2 offset = shake.offset(ROOM, farView);
            assertTrue(offset.x <= 0 && offset.y <= 0, "right/bottom clamp at " + i + ": " + offset);
            shake.update(.01f);
        }
    }
}
