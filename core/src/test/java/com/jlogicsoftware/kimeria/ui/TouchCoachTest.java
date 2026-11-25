package com.jlogicsoftware.kimeria.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The touch tutorial's timing and rules (#496, D139): when each step appears and ends, the loop's phases, and that a step that ended never
 * returns. Pure: the coach is fed synthetic signals at 60 frames a second.
 */
class TouchCoachTest {
    private static final float STEP = 1f / 60f, EPS = 0.02f;

    private final TouchCoach coach = new TouchCoach();
    private final TouchCoach.Signals signals = new TouchCoach.Signals();

    private void run(float seconds, boolean stickHeld) {
        for (int i = 0, n = Math.round(seconds * 60); i < n; i++) coach.update(STEP, true, stickHeld, signals);
    }

    private void inWalkZone() { signals.clear(); signals.zone(TouchCoach.Step.STICK_DRAG); signals.heroX = 64; }

    @Test void theStickHandAppearsAfterThreeFifthsOfASecondStandingStillAndNotBefore() {
        inWalkZone();
        run(TouchCoach.APPEAR_DELAY - EPS, false);
        assertNull(coach.step());
        run(2 * EPS, false);
        assertEquals(TouchCoach.Step.STICK_DRAG, coach.step());
    }

    @Test void aHeroWhoKeepsMovingNeverGetsTheHandAndTheDelayRestartsWhenHeStops() {
        inWalkZone();
        for (int i = 0; i < 60; i++) { signals.heroX += 1f; coach.update(STEP, true, false, signals); }
        assertNull(coach.step());
        run(TouchCoach.APPEAR_DELAY - EPS, false);
        assertNull(coach.step(), "the delay counts from the moment he stands still");
        run(2 * EPS, false);
        assertEquals(TouchCoach.Step.STICK_DRAG, coach.step());
    }

    @Test void theLoopIsFadeInPressDragReleasePauseAndRepeatsEvery1_6Seconds() {
        inWalkZone();
        run(TouchCoach.APPEAR_DELAY + 0.001f, false);
        assertEquals(TouchCoach.Step.STICK_DRAG, coach.step());
        assertEquals(0f, coach.handAlpha(), 0.1f, "starts invisible and fades in");
        run(TouchCoach.FADE_IN_END, false);
        assertEquals(1f, coach.handAlpha(), 0.1f);
        assertTrue(coach.pressed(), "the hand presses the knob");
        assertEquals(0f, coach.slide(), 0.001f, "pressed, not yet dragging");
        run(TouchCoach.DRAG_END - TouchCoach.FADE_IN_END - 0.35f, false);
        assertTrue(coach.slide() > 0.4f && coach.slide() < 1f, "mid-drag: " + coach.slide());
        assertTrue(coach.chevronAlpha() > 0.9f, "the chevrons show while dragging");
        run(0.35f + EPS, false);
        assertEquals(1f, coach.slide(), 0.001f, "the ghost knob has reached its place (36 units)");
        assertFalse(coach.pressed(), "released");
        assertEquals(0f, coach.chevronAlpha(), 0.001f);
        run(TouchCoach.RELEASE_END - TouchCoach.DRAG_END, false);
        run(TouchCoach.CYCLE - TouchCoach.RELEASE_END - EPS * 2, false);
        assertEquals(0f, coach.handAlpha(), 0.001f, "the 0.4 s pause leaves nothing on screen");
        run(EPS * 4 + TouchCoach.FADE_IN_END, false);
        assertTrue(coach.pressed() && coach.handAlpha() > 0.9f, "the next loop has begun");
        assertEquals(0f, coach.slide(), 0.001f, "the ghost knob starts again from the stick's centre");
    }

    @Test void thePauseBetweenLoopsIsFourTenthsOfASecond() {
        assertEquals(0.4f, TouchCoach.CYCLE - TouchCoach.RELEASE_END, 0.0001f);
        assertEquals(36f, TouchCoach.SLIDE_UNITS, 0.0001f);
    }

    @Test void touchingTheRealStickEndsTheFirstStepForTheSession() {
        inWalkZone();
        run(1f, false);
        assertNotNull(coach.step());
        run(STEP, true);
        assertNull(coach.step());
        assertTrue(coach.over(TouchCoach.Step.STICK_DRAG));
        run(5f, false);
        assertNull(coach.step(), "it never returns, even standing still in the zone");
    }

    @Test void walkingNinetySixUnitsEndsTheFirstStepEvenWithoutTheStick() {
        inWalkZone();
        run(1f, false);
        assertNotNull(coach.step());
        signals.heroX += TouchCoach.WALKED_UNITS - 1f;
        run(STEP, false);
        assertFalse(coach.over(TouchCoach.Step.STICK_DRAG), "95 units is not enough");
        signals.heroX += 1f;
        run(STEP, false);
        assertTrue(coach.over(TouchCoach.Step.STICK_DRAG));
        signals.heroX = 64;
        run(3f, false);
        assertNull(coach.step(), "walking back to the start does not bring it back");
    }

    @Test void aKeyboardOrGamepadMovingTheHeroEndsTheWholeCoachForGood() {
        inWalkZone();
        run(1f, false);
        assertNotNull(coach.step());
        signals.nonTouchMoved = true;
        run(STEP, false);
        assertNull(coach.step());
        assertTrue(coach.nonTouchSeen());
        signals.nonTouchMoved = false;
        run(5f, false);
        assertNull(coach.step());
        signals.clear(); signals.zone(TouchCoach.Step.RANGED); signals.carrying = true;
        run(2f, false);
        assertNull(coach.step(), "the ring is gone too: this player is not on touch");
    }

    @Test void aDeviceWithoutATouchScreenNeverShowsAnything() {
        inWalkZone();
        for (int i = 0; i < 240; i++) coach.update(STEP, false, false, signals);
        assertNull(coach.step());
        assertFalse(coach.nonTouchSeen());
    }

    @Test void theUpHandShowsOnlyWhileALeverIsInReachAndTheStickIsFree() {
        signals.clear(); signals.zone(TouchCoach.Step.STICK_UP); signals.heroX = 300;
        run(1f, false);
        assertNull(coach.step(), "no lever in reach");
        signals.leverInReach = true;
        run(TouchCoach.APPEAR_DELAY - EPS, false);
        assertNull(coach.step());
        run(2 * EPS, false);
        assertEquals(TouchCoach.Step.STICK_UP, coach.step());
        run(0.5f, true);
        assertNull(coach.step(), "hidden while a finger is on the stick");
        run(0.5f, false);
        assertEquals(TouchCoach.Step.STICK_UP, coach.step());
        signals.leverInReach = false;
        run(STEP, false);
        assertNull(coach.step(), "out of reach");
        assertFalse(coach.over(TouchCoach.Step.STICK_UP), "walking past it does not retire it");
    }

    @Test void usingTheLeverEndsTheUpStepForTheSession() {
        signals.clear(); signals.zone(TouchCoach.Step.STICK_UP); signals.leverInReach = true;
        run(1f, false);
        assertEquals(TouchCoach.Step.STICK_UP, coach.step());
        signals.interacted = true;
        run(STEP, false);
        signals.interacted = false;
        assertNull(coach.step());
        assertTrue(coach.over(TouchCoach.Step.STICK_UP));
        run(2f, false);
        assertNull(coach.step());
    }

    @Test void theUpStepUsesTheSameLoopAsTheDragStep() {
        signals.clear(); signals.zone(TouchCoach.Step.STICK_UP); signals.leverInReach = true;
        run(TouchCoach.APPEAR_DELAY + TouchCoach.DRAG_END - 0.2f, false);
        assertTrue(coach.slide() > 0.7f && coach.pressed());
    }

    @Test void theRingPulsesWhileAStoneIsCarriedInTheStoneZoneAndEndsOnAThrow() {
        signals.clear(); signals.zone(TouchCoach.Step.RANGED); signals.heroX = 3400;
        run(1f, false);
        assertNull(coach.step(), "nothing carried");
        signals.carrying = true;
        run(TouchCoach.APPEAR_DELAY + 0.3f, false);
        assertEquals(TouchCoach.Step.RANGED, coach.step());
        assertEquals(0f, coach.handAlpha(), 0f, "no hand on the ranged step");
        float first = coach.pulse();
        run(0.3f, false);
        assertNotEquals(first, coach.pulse(), 0.01f, "it pulses");
        assertTrue(coach.ringAlpha() > 0.4f && coach.ringAlpha() <= 1f);
        run(TouchCoach.PULSE_PERIOD, false);
        assertEquals(TouchCoach.Step.RANGED, coach.step(), "it keeps pulsing until the throw");
        signals.threw = true;
        run(STEP, false);
        signals.threw = false;
        assertNull(coach.step());
        assertTrue(coach.over(TouchCoach.Step.RANGED));
        run(2f, false);
        assertNull(coach.step(), "carrying a stone again does not bring it back");
    }

    @Test void theRingEndsWhenTheHeroLeavesTheZoneAfterItShowed() {
        signals.clear(); signals.zone(TouchCoach.Step.RANGED); signals.carrying = true;
        run(1f, false);
        assertEquals(TouchCoach.Step.RANGED, coach.step());
        signals.zones = 0;
        run(STEP, false);
        assertNull(coach.step());
        assertTrue(coach.over(TouchCoach.Step.RANGED));
        signals.zone(TouchCoach.Step.RANGED);
        run(2f, false);
        assertNull(coach.step(), "back in the zone: it has ended for the session");
    }

    @Test void aDeathAndRespawnWhileTheRingShowsDoesNotEndTheStepAndTheRingReturnsWithTheStone() {
        signals.clear(); signals.zone(TouchCoach.Step.RANGED); signals.carrying = true;
        run(1f, false);
        assertEquals(TouchCoach.Step.RANGED, coach.step());
        signals.clear(); signals.respawning = true; // dying: still in the zone, still holding it
        signals.zone(TouchCoach.Step.RANGED); signals.carrying = true;
        run(0.5f, false);
        signals.clear(); signals.respawning = true; // reset and arriving: outside the zone, nothing carried
        run(1f, false);
        signals.clear(); // the respawn is over: he stands at the room start
        run(1f, false);
        assertNull(coach.step());
        assertFalse(coach.over(TouchCoach.Step.RANGED), "a respawn is not a way out of the zone");
        signals.zone(TouchCoach.Step.RANGED); signals.carrying = true; // back to the stone
        run(1f, false);
        assertEquals(TouchCoach.Step.RANGED, coach.step());
        signals.zones = 0; // walking out on purpose still ends it
        run(STEP, false);
        assertTrue(coach.over(TouchCoach.Step.RANGED));
    }

    @Test void aStoneThatFallsHomeHidesTheRingButTheHeroMayPickItUpAgain() {
        signals.clear(); signals.zone(TouchCoach.Step.RANGED); signals.carrying = true;
        run(1f, false);
        signals.carrying = false;
        run(STEP, false);
        assertNull(coach.step());
        assertFalse(coach.over(TouchCoach.Step.RANGED));
        signals.carrying = true;
        run(1f, false);
        assertEquals(TouchCoach.Step.RANGED, coach.step());
    }

    @Test void theCoachPropertyNamesParseAndAMistypedOneFails() {
        for (TouchCoach.Step step : TouchCoach.Step.values()) assertSame(step, TouchCoach.Step.parse(step.property()));
        assertThrows(IllegalArgumentException.class, () -> TouchCoach.Step.parse("stick"));
    }

    @Test void theFirstStepBeatsTheOthersWhenTheirZonesOverlap() {
        signals.clear(); signals.zone(TouchCoach.Step.STICK_DRAG); signals.zone(TouchCoach.Step.STICK_UP); signals.leverInReach = true;
        run(1f, false);
        assertEquals(TouchCoach.Step.STICK_DRAG, coach.step());
    }
}
