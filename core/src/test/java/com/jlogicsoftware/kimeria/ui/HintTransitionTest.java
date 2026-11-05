package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HintTransitionTest {
    @Test void labelAppearsGraduallyAndFadesAtItsLastAnchor() {
        var hint = new HintTransition();
        var anchor = new Vector2(80, 240);
        hint.update("Put down\nThrow", anchor, .03f);
        assertTrue(hint.alpha() > 0 && hint.alpha() < 1);
        assertTrue(hint.offsetY() > 0, "Enter from below the normal label position");
        hint.update("Put down\nThrow", anchor, .2f);
        assertEquals(1, hint.alpha());
        assertEquals(0, hint.offsetY());
        hint.update("", new Vector2(400, 0), .1f);
        assertTrue(hint.alpha() > 0 && hint.alpha() < 1);
        assertTrue(hint.offsetY() < 0, "Exit upward from the normal label position");
        assertEquals("Put down\nThrow", hint.text());
        var outgoing = new Vector2(); hint.position(outgoing);
        assertEquals(anchor, outgoing);
        hint.update("", new Vector2(400, 0), .2f);
        assertEquals(0, hint.alpha());
        assertEquals("", hint.text());
    }

    @Test void pickupTextFadesBeforeBothCarryingInstructionsAppear() {
        var hint = new HintTransition();
        var before = new Vector2(80, 240);
        var after = new Vector2(85, 230);
        hint.update("Pick up", before, .2f);
        hint.update("Put down\nThrow", after, .1f);
        assertEquals("Pick up", hint.text());
        var position = new Vector2(); hint.position(position);
        assertEquals(before, position);
        hint.update("Put down\nThrow", after, .2f);
        assertEquals(0, hint.alpha());
        assertEquals("Put down\nThrow", hint.text());
        hint.position(position); assertEquals(after, position);
        hint.update("Put down\nThrow", after, .09f);
        assertTrue(hint.alpha() > 0 && hint.alpha() < 1);
        assertTrue(hint.offsetY() > 0);
        hint.update("Put down\nThrow", after, .1f);
        assertEquals(1, hint.alpha());
        assertEquals(0, hint.offsetY());
    }

    @Test void returningToAnObjectReversesTheFadeWithoutFlashing() {
        var hint = new HintTransition();
        var position = new Vector2();
        hint.update("Pick up", position, .2f);
        hint.update("", position, .1f);
        float fading = hint.alpha();
        hint.update("Pick up", position, .02f);
        assertTrue(hint.alpha() > fading && hint.alpha() < 1);
    }

    @Test void newPlayerClearsTheOutgoingLesson() {
        var hint = new HintTransition();
        hint.update("Put down\nThrow", new Vector2(80, 240), .2f);
        hint.clear();
        hint.update("", new Vector2(), .1f);
        assertEquals("", hint.text());
        assertEquals(0, hint.alpha());
    }

    @Test void interruptedEntryAndReentryKeepTheCurrentSlidePosition() {
        var hint = new HintTransition();
        var position = new Vector2();
        hint.update("Pick up", position, .09f);
        float entering = hint.offsetY();
        hint.update("", position, 0);
        assertEquals(entering, hint.offsetY(), "Direction change must not teleport the label");
        hint.update("", position, .05f);
        assertTrue(hint.offsetY() < entering);
        float leaving = hint.offsetY();
        hint.update("Pick up", position, 0);
        assertEquals(leaving, hint.offsetY());
        hint.update("Pick up", position, .2f);
        assertEquals(0, hint.offsetY());
        assertEquals(1, hint.alpha());
    }

    @Test void attentionHintWaitsThenFadesInWithAnOvershootingSlide() {
        var hint = new HintTransition();
        var anchor = new Vector2(80, 160);
        float t = 0;
        while (t < HintTransition.ATTENTION_DELAY - .05f) {
            hint.update("Walk right", anchor, .05f, true); t += .05f;
            assertEquals("", hint.text(), "Held back at t=" + t);
            assertEquals(0, hint.alpha());
        }
        hint.update("Walk right", anchor, .1f, true);
        assertEquals("Walk right", hint.text());
        assertEquals(0, hint.alpha(), "Starts fully transparent");
        assertTrue(hint.offsetY() > 0, "Starts below its resting place");
        float previous = 0, lowest = Float.MAX_VALUE;
        for (int i = 0; i < 40; i++) {
            hint.update("Walk right", anchor, .02f, true);
            assertTrue(hint.alpha() >= previous, "Alpha never dips while fading in");
            previous = hint.alpha();
            lowest = Math.min(lowest, hint.offsetY());
        }
        assertEquals(1, hint.alpha(), "Fully readable afterwards");
        assertEquals(0, hint.offsetY(), 1e-4, "Settles on its resting place");
        assertTrue(lowest <= -4 && lowest >= -9, "A clearly visible but bounded pulse: " + lowest);
    }

    @Test void attentionHintFadesInSlowerThanAnImmediateLabel() {
        var attention = new HintTransition();
        var plain = new HintTransition();
        var anchor = new Vector2();
        attention.update("Walk", anchor, HintTransition.ATTENTION_DELAY, true);
        attention.update("Walk", anchor, .18f, true);
        plain.update("Walk", anchor, .18f);
        assertEquals(1, plain.alpha());
        assertTrue(attention.alpha() < 1);
    }

    @Test void leavingTheZoneBeforeTheDelayShowsNothingAndRestartsTheWait() {
        var hint = new HintTransition();
        var anchor = new Vector2();
        hint.update("Walk", anchor, .6f, true);
        hint.update("", anchor, .1f, false);
        hint.update("Walk", anchor, .6f, true);
        assertEquals("", hint.text(), "The earlier 0.6 s must not count towards the new visit");
        hint.update("Walk", anchor, .3f, true);
        assertEquals("Walk", hint.text());
    }

    @Test void immediateLabelsIgnoreTheDelayAndAttentionLabelsExitNormally() {
        var hint = new HintTransition();
        var anchor = new Vector2();
        hint.update("Pick up", anchor, .01f, false);
        assertEquals("Pick up", hint.text());
        hint.clear();
        hint.update("Walk", anchor, 1f, true);
        hint.update("Walk", anchor, 1f, true);
        assertEquals(1, hint.alpha());
        hint.update("", anchor, .3f, false);
        assertEquals(0, hint.alpha());
        assertEquals("", hint.text());
    }

    @Test void swappingBetweenTwoAttentionLabelsWaitsAndPulsesAgain() {
        var hint = new HintTransition();
        var anchor = new Vector2();
        hint.update("A", anchor, HintTransition.ATTENTION_DELAY, true);
        for (int i = 0; i < 40; i++) hint.update("A", anchor, .02f, true);
        assertEquals(1, hint.alpha());
        for (int i = 0; i < 20; i++) hint.update("B", anchor, .02f, true);
        assertEquals("", hint.text(), "A fades out, then B waits out the delay");
        hint.update("B", anchor, HintTransition.ATTENTION_DELAY, true);
        assertEquals("B", hint.text());
        assertEquals(0, hint.alpha());
        hint.update("B", anchor, .18f, true);
        assertTrue(hint.alpha() > 0 && hint.alpha() < 1, "Slow attention fade, not the plain 0.18 s one");
        float lowest = 0;
        for (int i = 0; i < 40; i++) { hint.update("B", anchor, .02f, true); lowest = Math.min(lowest, hint.offsetY()); }
        assertTrue(lowest <= -4, "B pulses too: " + lowest);
    }

    @Test void frameTimeIsClampedSoAHitchCannotSkipTheDelay() {
        assertEquals(HintTransition.MAX_FRAME, HintTransition.clampFrame(2f));
        assertEquals(0, HintTransition.clampFrame(-1f));
        assertEquals(.01f, HintTransition.clampFrame(.01f));
        var hint = new HintTransition();
        hint.update("Walk", new Vector2(), HintTransition.clampFrame(3f), true);
        assertEquals("", hint.text());
    }
}
