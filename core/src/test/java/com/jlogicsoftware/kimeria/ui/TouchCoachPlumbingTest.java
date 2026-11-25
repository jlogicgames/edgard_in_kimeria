package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.Input;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The touch tutorial (#496, D139) wired through the real forest-0 map: the {@code coach} and {@code touchMessage} properties of the Hint zones reach the
 * coach, the hero's reach and carrying reach it, a keyboard moving the hero ends it, and the zone message has its touch variant only while the
 * touch tutorial guides the player.
 */
class TouchCoachPlumbingTest {
    private static final float STEP = HeadlessLevelHarness.STEP, FLOOR_Y = 272, WALK_ZONE_X = 64, LEVER_X = 376, STONE_ZONE_X = 3100, STONE_X = 3328, STONE_REACH = 24;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private TouchControls touchLevelAt(String room, float x, Level[] out) {
        TouchControls touch = harness.touchDevice();
        Level level = harness.room("forest-0", room);
        harness.coachSignals = signals -> level.player.coachSignals(signals);
        level.player.position.set(x, FLOOR_Y);
        out[0] = level;
        return touch;
    }

    private void frames(Level level, TouchControls touch, float seconds) {
        for (int i = 0, n = Math.round(seconds * 60); i < n; i++) {
            touch.update(true);
            level.update(STEP);
            harness.pressed.clear();
        }
    }

    @Test void standingAtTheStartOfTheFirstRoomOnATouchDeviceShowsTheStickHandAndTheTouchText() {
        Level[] level = new Level[1];
        TouchControls touch = touchLevelAt("forest-0-lever", WALK_ZONE_X, level);
        frames(level[0], touch, 0.2f);
        assertNull(touch.coach().step(), "not before 0.35 s");
        frames(level[0], touch, 0.5f);
        assertEquals(TouchCoach.Step.STICK_DRAG, touch.coach().step());
        assertEquals(Msg.HINT_TUTORIAL_WALK_TOUCH.t(Language.ENGLISH), level[0].player.zoneHint());
        assertEquals("Drag the stick right to find the way out", level[0].player.zoneHint());
        assertTrue(touch.touchGuided());
    }

    @Test void aKeyboardMovingTheHeroEndsTheCoachAndTheTouchTextOnAHybridDevice() {
        Level[] level = new Level[1];
        TouchControls touch = touchLevelAt("forest-0-lever", WALK_ZONE_X, level);
        frames(level[0], touch, 1f);
        assertNotNull(touch.coach().step());
        harness.held.add(Input.Keys.D);
        frames(level[0], touch, 0.1f);
        harness.held.clear();
        level[0].player.position.set(WALK_ZONE_X, FLOOR_Y);
        frames(level[0], touch, 1f);
        assertNull(touch.coach().step());
        assertFalse(touch.touchGuided());
        assertEquals(Msg.HINT_TUTORIAL_WALK.t(Language.ENGLISH), level[0].player.zoneHint(), "the key-neutral text is back");
    }

    @Test void aDeviceWithoutATouchScreenHasNeitherTheCoachNorTheTouchText() {
        Level level = harness.room("forest-0", "forest-0-lever");
        level.player.position.set(WALK_ZONE_X, FLOOR_Y);
        for (int i = 0; i < 60; i++) level.update(STEP);
        assertEquals(Msg.HINT_TUTORIAL_WALK.t(Language.ENGLISH), level.player.zoneHint());
    }

    @Test void theUpHandNeedsTheLeverInReachAndNothingBeforeTheLeverRoomsWalkZoneEnds() {
        Level[] level = new Level[1];
        TouchControls touch = touchLevelAt("forest-0-lever", 200, level);
        frames(level[0], touch, 1f);
        assertNull(touch.coach().step(), "the first step shows only in its own zone, and nothing is in reach at x 200");
        level[0].player.position.set(LEVER_X - 20, FLOOR_Y);
        frames(level[0], touch, 1f);
        assertTrue(level[0].player.canInteract(), "the lever is in reach");
        assertEquals(TouchCoach.Step.STICK_UP, touch.coach().step());
    }

    @Test void theRingShowsInTheStoneRoomOnlyWhileTheStoneIsCarriedAndEndsOnAThrow() {
        Level[] level = new Level[1];
        TouchControls touch = touchLevelAt("forest-0-stone", STONE_ZONE_X, level);
        frames(level[0], touch, 1.5f);
        assertNull(touch.coach().step(), "empty-handed: no ring");
        TouchCoach.Signals signals = new TouchCoach.Signals();
        level[0].player.coachSignals(signals);
        assertTrue((signals.zones & (1 << TouchCoach.Step.RANGED.ordinal())) != 0, "the stone room's controls zone arms the ranged step");
        assertFalse(signals.carrying);

        // Pick the stone up with the keyboard (the test has no finger), then hand the game a fresh touch device: the ring is for the one carrying it.
        level[0].player.position.set(STONE_X - STONE_REACH, FLOOR_Y);
        harness.held.add(Input.Keys.D); frames(level[0], touch, 0.05f); harness.held.clear(); frames(level[0], touch, 0.1f);
        harness.held.add(Input.Keys.W); frames(level[0], touch, 0.05f); harness.held.clear(); frames(level[0], touch, 0.1f);
        assertNotNull(level[0].player.carriedObject(), "the stone is carried");
        TouchControls fresh = harness.touchDevice();
        frames(level[0], fresh, 0.2f);
        assertNull(fresh.coach().step(), "not before 0.35 s");
        frames(level[0], fresh, 0.4f);
        assertEquals(TouchCoach.Step.RANGED, fresh.coach().step());
        harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L);
        frames(level[0], fresh, STEP * 2);
        harness.held.clear();
        frames(level[0], fresh, 0.5f);
        assertNull(fresh.coach().step());
        assertTrue(fresh.coach().over(TouchCoach.Step.RANGED));
    }
}
