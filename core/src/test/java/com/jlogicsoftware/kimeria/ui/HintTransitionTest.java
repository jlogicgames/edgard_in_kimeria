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
        hint.update("Walk", anchor, .3f, true);
        hint.update("", anchor, HintTransition.REQUEST_GRACE + .05f, false);
        hint.update("Walk", anchor, .3f, true);
        assertEquals("", hint.text(), "The earlier 0.3 s must not count towards the new visit");
        hint.update("Walk", anchor, .1f, true);
        assertEquals("Walk", hint.text());
    }

    @Test void immediateLabelsIgnoreTheDelayAndReplaceAShownMessage() {
        var hint = new HintTransition();
        var anchor = new Vector2();
        hint.update("Pick up", anchor, .01f, false);
        assertEquals("Pick up", hint.text());
        hint.clear();
        hint.update("Walk", anchor, 1f, true);
        hint.update("Walk", anchor, 1f, true);
        assertEquals(1, hint.alpha());
        hint.update("Pick up", anchor, .3f, false);
        assertEquals("Pick up", hint.text(), "An immediate label swaps in as soon as the old one has faded");
        hint.update("Pick up", anchor, .3f, false);
        assertEquals("Pick up", hint.text());
        assertEquals(1, hint.alpha());
    }

    private static HintTransition showing(String text, Vector2 anchor) {
        var hint = new HintTransition();
        hint.update(text, anchor, HintTransition.ATTENTION_DELAY, true);
        for (int i = 0; i < 100; i++) hint.update(text, anchor, .02f, true); // 2 s: well past MIN_SHOW
        assertEquals(1, hint.alpha());
        return hint;
    }

    @Test void appearDelayIsShortAndAShortGapDoesNotRestartIt() {
        assertTrue(HintTransition.ATTENTION_DELAY <= .4f && HintTransition.ATTENTION_DELAY >= .3f);
        var hint = new HintTransition();
        var anchor = new Vector2();
        hint.update("Walk", anchor, .2f, true);
        hint.update("", anchor, .1f, false); // boundary jitter inside the grace period
        hint.update("Walk", anchor, .2f, true);
        assertEquals("Walk", hint.text(), "0.2 + 0.2 s of request, the 0.1 s gap is forgiven");
    }

    @Test void attentionLabelLingersAfterTheRequestEndsThenFadesOut() {
        var anchor = new Vector2(80, 160);
        var hint = showing("Lever", anchor);
        float t = 0;
        while (t < HintTransition.LINGER - .1f) {
            hint.update("", new Vector2(900, 0), .02f, false); t += .02f;
            assertEquals("Lever", hint.text()); assertEquals(1, hint.alpha(), "Fully readable while lingering at t=" + t);
        }
        var where = new Vector2(); hint.position(where);
        assertEquals(anchor, where, "A lingering label stays where it was shown");
        for (int i = 0; i < 40; i++) hint.update("", anchor, .02f, false);
        assertEquals("", hint.text());
        assertEquals(0, hint.alpha());
    }

    @Test void returningToTheZoneCancelsTheLingerWithoutFlicker() {
        var anchor = new Vector2();
        var hint = showing("Lever", anchor);
        for (int i = 0; i < 100; i++) { // Walk back and forth over the boundary for 4 s.
            hint.update(i % 20 < 10 ? "" : "Lever", anchor, .02f, i % 20 >= 10);
            assertEquals("Lever", hint.text()); assertEquals(1, hint.alpha(), "Never dips at frame " + i);
        }
        for (int i = 0; i < 100; i++) hint.update("Lever", anchor, .02f, true);
        for (int i = 0; i < 20; i++) hint.update("", anchor, .02f, false); // the linger starts from zero again
        assertEquals("Lever", hint.text());
        assertEquals(1, hint.alpha());
    }

    @Test void anotherTutorialMessageWaitsForTheMinimumShowTimeThenCrossfadesWithoutTheDelay() {
        var anchor = new Vector2();
        var hint = new HintTransition();
        hint.update("A", anchor, HintTransition.ATTENTION_DELAY, true);
        for (int i = 0; i < 25; i++) hint.update("A", anchor, .02f, true); // 0.5 s up
        hint.update("B", anchor, .1f, true);
        assertEquals("A", hint.text(), "Held: not yet up for MIN_SHOW");
        assertTrue(hint.alpha() > .9f, "A is not faded while held: " + hint.alpha());
        for (int i = 0; i < 20; i++) hint.update("B", anchor, .02f, true); // past MIN_SHOW, A leaves
        assertEquals("B", hint.text(), "B follows A as soon as A has faded: no fresh appear delay");
        assertTrue(hint.alpha() < .6f);
        for (int i = 0; i < 40; i++) hint.update("B", anchor, .02f, true);
        assertEquals(1, hint.alpha());
    }

    @Test void immediateLabelReplacesAFullyShownLingeringMessage() {
        var anchor = new Vector2();
        var hint = showing("Lever", anchor);
        hint.update("", anchor, .5f, false); // lingering
        hint.update("Turn on", anchor, .02f, false);
        assertTrue(hint.alpha() < 1, "The message starts leaving at once");
        hint.update("Turn on", anchor, .3f, false);
        hint.update("Turn on", anchor, .3f, false);
        assertEquals("Turn on", hint.text());
        assertEquals(1, hint.alpha());
    }

    @Test void anImmediateLabelInItsOwnChannelNeverTouchesAMessageThatIsStillFadingIn() {
        var anchor = new Vector2();
        var message = new HintTransition(); // the HUD's zone channel
        var label = new HintTransition();   // the HUD's contextual channel
        message.update("Lever", anchor, HintTransition.ATTENTION_DELAY, true);
        for (int i = 0; i < 10; i++) message.update("Lever", anchor, .02f, true); // 0.2 s into the 0.45 s fade-in
        float fading = message.alpha();
        assertTrue(fading > 0 && fading < 1);
        for (int i = 0; i < 10; i++) {
            label.update("Turn on", anchor, .02f);
            message.update("Lever", anchor, .02f, true);
            assertEquals("Lever", message.text()); assertTrue(message.alpha() >= fading, "Keeps fading in");
            fading = message.alpha();
            assertEquals("Turn on", label.text(), "The label is immediate: it is there from its first frame");
            assertTrue(label.alpha() > 0);
        }
        for (int i = 0; i < 30; i++) { label.update("Turn on", anchor, .02f); message.update("Lever", anchor, .02f, true); }
        assertEquals(1, message.alpha(), .0001f); assertEquals(1, label.alpha(), .0001f);
        assertEquals("Lever", message.text());
    }

    @Test void aLingeringMessageIsNotResetOrShortenedWhileALabelComesAndGoesInItsOwnChannel() {
        var anchor = new Vector2();
        var message = showing("Lever", anchor);
        var label = new HintTransition();
        float t = 0;
        for (int i = 0; i < 20; i++) { message.update("", anchor, .02f, false); t += .02f; } // lingering, request over
        for (int i = 0; i < 60; i++) { // a label shows and swaps text through zero: no effect on the message
            label.update(i < 30 ? "Turn on" : "Turn off", anchor, .02f); message.update("", anchor, .02f, false); t += .02f;
            assertEquals("Lever", message.text()); assertEquals(1, message.alpha(), .0001f);
        }
        assertTrue(t < HintTransition.LINGER);
        for (int i = 0; i < 100; i++) message.update("", anchor, .02f, false);
        assertEquals("", message.text(), "It still ends on its own linger schedule");
    }

    @Test void aMessageReturnsWithoutDelayAfterAnImmediateLabelEnds() {
        var anchor = new Vector2();
        var hint = new HintTransition();
        hint.update("Turn on", anchor, .3f, false);
        hint.update("Turn on", anchor, .3f, false);
        hint.update("Lever", anchor, .3f, true);
        assertEquals("Lever", hint.text(), "Already seen labels crossfade; only a first message waits");
    }

    @Test void swappingBetweenTwoAttentionLabelsFadesOutThenPulsesAgain() {
        var hint = new HintTransition();
        var anchor = new Vector2();
        hint.update("A", anchor, HintTransition.ATTENTION_DELAY, true);
        for (int i = 0; i < 40; i++) hint.update("A", anchor, .02f, true);
        assertEquals(1, hint.alpha());
        for (int i = 0; i < 20; i++) hint.update("B", anchor, .02f, true);
        assertEquals("B", hint.text(), "A fades out, then B enters at once");
        assertTrue(hint.alpha() < 1, "Slow attention fade, not the plain 0.18 s one");
        float lowest = 0;
        for (int i = 0; i < 60; i++) { hint.update("B", anchor, .02f, true); lowest = Math.min(lowest, hint.offsetY()); }
        assertEquals(1, hint.alpha());
        assertTrue(lowest <= -4, "B pulses too: " + lowest);
    }

    @Test void attentionLabelExitsToNothingOnceItsRequestEnds() {
        var hint = showing("Lever", new Vector2());
        for (int i = 0; i < 200; i++) hint.update("", new Vector2(), .02f, false);
        assertEquals("", hint.text());
        assertEquals(0, hint.alpha());
    }

    @Test void clearEndsALingeringLabelAtOnce() {
        var hint = showing("Lever", new Vector2());
        hint.update("", new Vector2(), .1f, false);
        hint.clear(); // the HUD does this when the hero dies or reaches a checkpoint
        hint.update("", new Vector2(), .02f, false);
        assertEquals("", hint.text());
        assertEquals(0, hint.alpha());
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
