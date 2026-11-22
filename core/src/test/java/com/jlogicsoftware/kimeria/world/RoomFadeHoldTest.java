package com.jlogicsoftware.kimeria.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #553: the fade's clear hold before the fade-out; a fade without a hold is exactly what it was. */
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
