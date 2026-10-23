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
}
