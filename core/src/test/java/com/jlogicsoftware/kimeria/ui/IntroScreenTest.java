package com.jlogicsoftware.kimeria.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntroScreenTest {
    private static final String STORY = "a\n\nb\n\nc"; // 100 px each: a and b share page one, c is page two

    private static IntroScreen story(boolean instant) {
        return new IntroScreen(STORY, IntroPages.split(STORY, p -> 100f), null, instant, 640f, null);
    }

    private static IntroScreen reading(boolean instant) {
        IntroScreen intro = story(instant);
        intro.update(IntroScreen.FADE_IN_SECONDS);
        return intro;
    }

    private static void waitOutTheLock(IntroScreen intro) { intro.update(IntroScreen.INPUT_LOCK_SECONDS); }

    @Test void fadesInFromBlackAndShowsNoTextUntilThePagesStart() {
        IntroScreen intro = story(false);
        assertEquals(1f, intro.alpha());
        assertEquals(0f, intro.paragraphAlpha(0));
        intro.update(IntroScreen.FADE_IN_SECONDS / 2f);
        assertEquals(0.5f, intro.volume());
        assertEquals(0f, intro.paragraphAlpha(0));
        assertFalse(intro.turn(), "a press during the fade-in does nothing");
        intro.update(IntroScreen.FADE_IN_SECONDS / 2f);
        assertEquals(IntroScreen.State.READING, intro.state());
        assertEquals(0, intro.alpha());
    }

    @Test void paragraphsAppearOneAfterAnotherAndStay() {
        IntroScreen intro = reading(false);
        assertEquals(IntroScreen.Phase.REVEAL, intro.phase());
        assertEquals(0f, intro.paragraphAlpha(0));
        intro.update(IntroScreen.PARAGRAPH_FADE_SECONDS / 2f);
        assertEquals(0.5f, intro.paragraphAlpha(0), 0.001f);
        assertEquals(0f, intro.paragraphAlpha(1));
        intro.update(IntroScreen.PARAGRAPH_FADE_SECONDS / 2f);
        assertEquals(1f, intro.paragraphAlpha(0), 0.001f);
        intro.update(IntroScreen.PARAGRAPH_STAGGER_SECONDS - IntroScreen.PARAGRAPH_FADE_SECONDS + IntroScreen.PARAGRAPH_FADE_SECONDS / 2f);
        assertEquals(1f, intro.paragraphAlpha(0), 0.001f, "an earlier paragraph stays");
        assertEquals(0.5f, intro.paragraphAlpha(1), 0.001f);
        assertEquals(0f, intro.paragraphAlpha(2), "paragraphs of the next page stay hidden");
        intro.update(IntroScreen.PARAGRAPH_FADE_SECONDS);
        assertEquals(IntroScreen.Phase.WAIT, intro.phase());
        intro.update(30f);
        assertEquals(1f, intro.paragraphAlpha(0));
        assertEquals(1f, intro.paragraphAlpha(1));
        assertEquals(0, intro.pageIndex(), "nothing turns the page by itself: there is no scrolling and no timer");
    }

    @Test void aPressWhileAppearingShowsTheWholePageThenAnotherTurnsIt() {
        IntroScreen intro = reading(false);
        assertFalse(intro.turn(), "locked right after the page starts");
        waitOutTheLock(intro);
        assertTrue(intro.turn());
        assertEquals(IntroScreen.Phase.WAIT, intro.phase());
        assertEquals(1f, intro.paragraphAlpha(0));
        assertEquals(1f, intro.paragraphAlpha(1));
        assertEquals(0, intro.pageIndex());
        assertFalse(intro.turn(), "locked again once the page is whole");
        waitOutTheLock(intro);
        assertTrue(intro.turn());
        assertEquals(IntroScreen.Phase.TURN, intro.phase());
        intro.update(IntroScreen.PAGE_TURN_SECONDS / 2f);
        assertEquals(0.5f, intro.paragraphAlpha(0), 0.001f);
        intro.update(IntroScreen.PAGE_TURN_SECONDS / 2f);
        assertEquals(1, intro.pageIndex());
        assertEquals(IntroScreen.Phase.REVEAL, intro.phase());
        assertEquals(0f, intro.paragraphAlpha(0));
        assertEquals(0f, intro.paragraphAlpha(2));
    }

    @Test void aHeldButtonTurnsExactlyOnePageBecauseInputIsLockedAfterEachPageAppears() {
        IntroScreen intro = reading(true);
        int turned = 0;
        for (int frame = 0; frame < (int) (IntroScreen.INPUT_LOCK_SECONDS * 60f) - 2; frame++) {
            if (intro.turn()) turned++; // the button is held down every frame
            intro.update(1f / 60f);
        }
        assertEquals(0, turned, "the lock swallows a button that was already down when the page appeared");
        intro.update(1f / 30f);
        assertTrue(intro.turn());
        assertEquals(1, intro.pageIndex());
        assertTrue(intro.locked());
        assertFalse(intro.turn(), "the held button cannot also turn the next page");
        assertEquals(1, intro.pageIndex());
    }

    @Test void instantTextShowsEachPageWholeAndTurnsItAtOnce() {
        IntroScreen intro = reading(true);
        assertEquals(IntroScreen.Phase.WAIT, intro.phase());
        assertEquals(1f, intro.paragraphAlpha(0));
        assertEquals(1f, intro.paragraphAlpha(1));
        assertEquals(0f, intro.paragraphAlpha(2));
        waitOutTheLock(intro);
        assertTrue(intro.turn());
        assertEquals(1, intro.pageIndex());
        assertEquals(1f, intro.paragraphAlpha(2));
        assertEquals(0f, intro.paragraphAlpha(0));
        assertEquals(IntroScreen.Phase.WAIT, intro.phase());
    }

    @Test void turningTheLastPageFadesToSilenceAndEndsTheStory() {
        IntroScreen intro = reading(true);
        waitOutTheLock(intro); intro.turn();
        waitOutTheLock(intro);
        assertTrue(intro.turn());
        assertEquals(IntroScreen.State.FADE_OUT, intro.state());
        assertFalse(intro.done());
        intro.update(IntroScreen.FADE_OUT_SECONDS / 2f);
        assertEquals(0.5f, intro.alpha());
        assertEquals(0.5f, intro.volume());
        intro.update(IntroScreen.FADE_OUT_SECONDS / 2f);
        assertTrue(intro.done());
        assertEquals(0f, intro.volume());
        assertEquals(1f, intro.alpha());
        assertFalse(intro.turn());
    }

    @Test void skipDuringFadeInStartsFromCurrentOpacityAndRepeatedSkipsDoNotRestartIt() {
        IntroScreen intro = story(false);
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

    @Test void skipStaysAvailableOnEveryPageAndEvenWhileTheInputIsLocked() {
        IntroScreen intro = reading(false);
        assertTrue(intro.locked());
        intro.skip();
        assertEquals(IntroScreen.State.FADE_OUT, intro.state());
        intro.update(10f);
        assertTrue(intro.done());
    }

    @Test void theCueIsCentredOnTheSameWidthAsTheText() {
        assertEquals(320f, story(false).cueCenterX());
        String text = "a\n\nb";
        assertEquals(400f, new IntroScreen(text, IntroPages.split(text, p -> 100f), null, false, 800f, null).cueCenterX());
    }

    @Test void theCueSpriteIsAnEightPixelTriangleEveryRowNarrowerThanTheOneAbove() throws Exception {
        java.awt.image.BufferedImage png;
        try (var in = IntroScreenTest.class.getClassLoader().getResourceAsStream("images/HUD/touch/cue.png")) { png = javax.imageio.ImageIO.read(in); }
        assertEquals(IntroScreen.CUE_SIZE, png.getWidth());
        assertEquals(IntroScreen.CUE_SIZE, png.getHeight());
        int above = Integer.MAX_VALUE;
        for (int y = 0; y < png.getHeight(); y++) {
            int opaque = 0;
            for (int x = 0; x < png.getWidth(); x++) if ((png.getRGB(x, y) >>> 24) != 0) opaque++;
            assertTrue(opaque > 0, "row " + y + " has pixels: no negative or empty width");
            assertTrue(opaque <= above, "row " + y + " is not wider than the row above");
            above = opaque;
        }
        assertEquals(2, above, "the triangle ends in a point 2 px wide");
    }

    @Test void longFrameClampsFadeAndNewRunStartsFromBlackAgain() {
        IntroScreen intro = story(false);
        intro.update(10f);
        intro.skip();
        intro.update(10f);
        assertTrue(intro.done());
        assertEquals(1f, intro.alpha());
        assertEquals(IntroScreen.State.FADE_IN, story(false).state());
    }
}
