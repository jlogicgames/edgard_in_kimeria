package com.jlogicsoftware.kimeria.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #553, #606: the fade's clear holds, before the fade-out and after the fade-in; a fade without a hold is exactly what it was. */
class RoomFadeHoldTest {
    private static final float STEP = 1f / 60f;

    @Test void theHoldIsClearAndDoesNotSwitchTheRoom() {
        RoomFade fade = new RoomFade();
        int[] switched = {0};
        fade.start(0.2f);
        assertTrue(fade.active() && fade.holding());
        for (float t = 0; t < 0.19f; t += STEP) {
            fade.update(STEP, () -> switched[0]++);
            assertEquals(0f, fade.alpha(), 0f, "the screen stays clear during the hold");
        }
        assertEquals(0, switched[0]);
        float total = 0;
        while (fade.active() && total < 2) { fade.update(STEP, () -> switched[0]++); total += STEP; }
        assertEquals(1, switched[0], "the room switch runs exactly once");
        assertTrue(total < RoomFade.HALF_SECONDS * 2 + 0.05f && total > RoomFade.HALF_SECONDS * 2 - 0.05f - 0.02f, "after the hold the fade takes its usual 0.5 s: " + total);
    }

    /** Frames a whole fade takes; {@code after} is asked for by the room switch, as the game does (#606). */
    private static int frames(RoomFade fade, float after) {
        fade.start();
        int frames = 0;
        while (fade.active() && frames < 600) {
            fade.update(STEP, () -> fade.holdAfter(after));
            frames++;
        }
        return frames;
    }

    @Test void theHoldAfterTheFadeInIsClearAndKeepsTheFadeActive() {
        int plain = frames(new RoomFade(), 0);
        assertEquals(plain + Math.round(0.35f / STEP), frames(new RoomFade(), 0.35f), 1, "the hold is added after the fade-in");
        RoomFade fade = new RoomFade();
        fade.start();
        while (!fade.dark()) fade.update(STEP, () -> fade.holdAfter(0.35f));
        for (float t = 0; t < RoomFade.HALF_SECONDS + 0.3f; t += STEP) {
            fade.update(STEP, () -> { });
            if (t > RoomFade.HALF_SECONDS) assertEquals(0f, fade.alpha(), 0f, "clear during the hold");
        }
        assertTrue(fade.active(), "still holding");
    }

    @Test void aHoldAfterTheFadeIsOnlyTheSwitchesAndDoesNotComeBack() {
        int plain = frames(new RoomFade(), 0);
        RoomFade fade = new RoomFade();
        fade.holdAfter(0.35f);
        fade.start();
        fade.holdAfter(0.35f); // before the switch: the healer meeting cancels there, so nothing may be kept
        int frames = 0;
        while (fade.active() && frames < 600) { fade.update(STEP, () -> { }); frames++; }
        assertEquals(plain, frames);
        frames(fade, 0.35f);
        assertEquals(plain, frames(fade, 0), "the next fade starts without it");
        fade.start();
        while (!fade.dark()) fade.update(STEP, () -> fade.holdAfter(0.35f));
        fade.cancel();
        assertEquals(plain, frames(fade, 0), "a cancelled hold does not come back");
    }

    @Test void aFadeWithoutAHoldIsUnchangedAndCancelClearsTheHold() {
        RoomFade fade = new RoomFade();
        fade.start();
        assertTrue(!fade.holding());
        fade.update(STEP, () -> { });
        assertTrue(fade.alpha() > 0);
        fade.cancel();
        fade.start(0.2f);
        fade.cancel();
        fade.start();
        assertTrue(!fade.holding(), "a cancelled hold does not come back");
    }
}
