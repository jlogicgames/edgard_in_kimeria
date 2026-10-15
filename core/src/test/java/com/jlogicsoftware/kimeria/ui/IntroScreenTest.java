package com.jlogicsoftware.kimeria.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntroScreenTest {
    @Test void scrollsAtConstantSpeedUntilTheLastLineLeavesThenFadesToSilence() {
        IntroScreen intro = new IntroScreen("Story", 120f, 360f);
        assertEquals(1f, intro.alpha());
        intro.update(IntroScreen.FADE_IN_SECONDS / 2f);
        assertEquals(0.5f, intro.volume());
        intro.update(IntroScreen.FADE_IN_SECONDS / 2f);
        assertEquals(IntroScreen.State.SCROLLING, intro.state());
        intro.update(1f);
        assertEquals(360f - IntroScreen.SCROLL_SPEED, intro.textTop());
        intro.update((480f - IntroScreen.SCROLL_SPEED) / IntroScreen.SCROLL_SPEED);
        assertEquals(IntroScreen.State.FADE_OUT, intro.state());
        assertFalse(intro.done());
        intro.update(IntroScreen.FADE_OUT_SECONDS / 2f);
        assertEquals(0.5f, intro.alpha());
        assertEquals(0.5f, intro.volume());
        intro.update(IntroScreen.FADE_OUT_SECONDS / 2f);
        assertTrue(intro.done());
        assertEquals(0f, intro.volume());
        assertEquals(1f, intro.alpha());
    }

    @Test void skipDuringFadeInStartsFromCurrentOpacityAndRepeatedSkipsDoNotRestartIt() {
        IntroScreen intro = new IntroScreen("Story", 100f, 360f);
        intro.update(IntroScreen.FADE_IN_SECONDS / 2f);
        intro.skip();
        intro.update(IntroScreen.FADE_OUT_SECONDS / 2f);
        assertEquals(0.25f, intro.volume());
        assertEquals(0.75f, intro.alpha());
        intro.skip();
        intro.update(IntroScreen.FADE_OUT_SECONDS / 2f);
        assertTrue(intro.done());
        intro.skip();
        intro.update(10f);
        assertTrue(intro.done());
    }

    @Test void longFrameClampsFadeAndNewRunStartsFromBlackAgain() {
        IntroScreen intro = new IntroScreen("Story", 100f, 360f);
        intro.update(10f);
        intro.skip();
        intro.update(10f);
        assertTrue(intro.done());
        assertEquals(1f, intro.alpha());
        assertEquals(IntroScreen.State.FADE_IN, new IntroScreen("Story", 100f, 360f).state());
    }
}
