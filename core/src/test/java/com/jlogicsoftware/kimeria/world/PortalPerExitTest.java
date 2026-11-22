package com.jlogicsoftware.kimeria.world;

import com.jlogicsoftware.kimeria.effects.RoomRippleFx;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.effects.SoftDot;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #552, #553 (D123): every room exit of every shipped map has a portal on it, and the portal and the room-change ripple are cosmetic: the
 * hero, the exit trigger and the room's pickups behave identically with and without them.
 */
class PortalPerExitTest {
    private static final String[] MAPS = {"forest-0", "forest-1", "forest-2"};
    private static final float STEP = HeadlessLevelHarness.STEP;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { Shaders.dispose(); SoftDot.dispose(); harness.restoreGlobals(); }

    @Test void everyRoomExitOfEveryShippedMapHasAPortalOnIt() {
        int exits = 0;
        for (String map : MAPS) {
            Level level = harness.roomOf(map, 0);
            do {
                boolean hasExit = level.hasNextRoom();
                assertEquals(hasExit, level.portal().placed(), map + " room " + level.currentRoomName() + ": a portal exactly where there is an exit");
                if (hasExit) {
                    exits++;
                    // The hero standing on the portal's centre is at the exit: the portal is on the RoomExit trigger.
                    var body = level.player;
                    float hx = body.size.x / 2, hy = body.size.y / 2;
                    body.position.set(level.portal().centreX() - hx, level.portal().centreY() - hy);
                    assertTrue(level.atRoomExit(), map + " " + level.currentRoomName() + ": the portal's centre is inside the exit rectangle");
                    level.enterNextRoom(new com.jlogicsoftware.kimeria.entity.player.Player(harness.assets(), harness.game(), 0, 0));
                }
            } while (level.portal().placed() || level.hasNextRoom());
            assertTrue(!level.portal().placed(), map + ": the last room has no exit and no portal");
        }
        assertEquals(5 + 11 + 5, exits, "21 exits in the shipped maps (6 + 12 + 6 rooms); a new room must bring its portal");
    }

    /** One room change of forest-1, with or without the effects, then a retry and a restart: what the player keeps must be identical. */
    private String scenario(boolean effects) {
        HeadlessLevelHarness h = new HeadlessLevelHarness();
        h.setUp();
        try {
            Level level = h.roomOf("forest-1", 0);
            for (int i = 0; i < 60; i++) level.update(STEP);
            level.recordPickup(7);                       // a provisional pickup, secured by the room change (kimeria-room-state)
            h.game().artifacts().collect("forest-1", 7, com.jlogicsoftware.kimeria.entity.items.ItemType.BAT);
            StringBuilder out = new StringBuilder();
            if (effects) {
                level.beginLeaving();
                for (int i = 0; i < 12; i++) level.updateRoomChange(STEP);
                assertTrue(level.heroLeft(), "the hero has gone into the portal in the middle of the pause");
            }
            level.enterNextRoom(new com.jlogicsoftware.kimeria.entity.player.Player(h.assets(), h.game(), 0, 0));
            assertTrue(!level.heroLeft(), "the new room starts with the hero drawn");
            if (effects) for (int i = 0; i < 30; i++) level.updateRoomChange(STEP); // the fade-in
            assertTrue(!level.heroLeft(), "and the fade-in does not hide him again");
            snapshot(out, "entered", level, h);
            for (int i = 0; i < 40; i++) level.update(STEP);
            level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(h.assets(), h.game(), 0, 0)); // retry after a death (D3)
            assertTrue(!level.heroLeft());
            snapshot(out, "retry", level, h);
            h.game().artifacts().restartLevel();
            Level fresh = h.roomOf("forest-1", 0);                                                       // level restart
            snapshot(out, "restart", fresh, h);
            return out.toString();
        } finally {
            Shaders.dispose(); SoftDot.dispose(); h.restoreGlobals();
        }
    }

    private static void snapshot(StringBuilder out, String label, Level level, HeadlessLevelHarness h) {
        out.append(label).append(": room ").append(level.currentRoomName()).append(" kept ").append(new java.util.TreeSet<>(level.keptPickups()))
            .append(" hero ").append(level.player.position.x).append(',').append(level.player.position.y)
            .append(" exit ").append(level.atRoomExit()).append(" inventory ").append(h.game().artifacts().items().size()).append('\n');
    }

    /** #553 (D123): a real room change, a retry and a restart leave the same room, kept pickups, inventory and hero with the effects as without. */
    @Test void theRoomChangeEffectsLeaveTheStateIdenticalThroughChangeRetryAndRestart() {
        assertEquals(scenario(false), scenario(true));
    }

    /** The hero is hidden only by a leave: a fight's fade and the debug fade never begin one, and the fade-in of the next room does not hide him. */
    @Test void onlyALeaveHidesTheHeroAndTheNextRoomShowsHimAgain() {
        Level fight = harness.roomOf("forest-1", 0);
        for (int i = 0; i < 40; i++) fight.updateRoomChange(STEP); // the FIGHT path and the F6 fade run the fade's updates with no beginLeaving
        assertTrue(!fight.heroLeft());
        Level level = harness.roomOf("forest-1", 0);
        level.beginLeaving();
        for (int i = 0; i < 12; i++) level.updateRoomChange(STEP);
        assertTrue(level.heroLeft());
        level.enterNextRoom(new com.jlogicsoftware.kimeria.entity.player.Player(harness.assets(), harness.game(), 0, 0)); // the room switch
        for (int i = 0; i < 40; i++) level.updateRoomChange(STEP);                                                       // the fade-in
        assertTrue(!level.heroLeft(), "the hero is drawn after every room change");
        level.beginLeaving();
        level.cancelLeaving();
        for (int i = 0; i < 40; i++) level.updateRoomChange(STEP);
        assertTrue(!level.heroLeft(), "a cancelled change (the healer meeting) leaves him drawn");
    }

    /** The pause freezes the hero, and the keys he holds are polled state: a direction held through it carries him on as soon as the room starts. */
    @Test void aHeldDirectionSurvivesThePause() {
        Level level = harness.roomOf("forest-1", 0);
        for (int i = 0; i < 120; i++) level.update(STEP);
        harness.held.add(com.badlogic.gdx.Input.Keys.D);
        for (int i = 0; i < 20; i++) level.update(STEP);
        float before = level.player.position.x;
        level.player.resetJumpInput();
        level.beginLeaving();
        for (int i = 0; i < 12; i++) level.updateRoomChange(STEP);
        assertEquals(before, level.player.position.x, 0f, "the hero is frozen during the pause");
        for (int i = 0; i < 20; i++) level.update(STEP);
        assertTrue(level.player.position.x > before, "the held direction is still held after it");
        // the real case: the next room has a new Player, and the key is still down
        level.enterNextRoom(new com.jlogicsoftware.kimeria.entity.player.Player(harness.assets(), harness.game(), 0, 0));
        float arrived = level.player.position.x;
        for (int i = 0; i < 90; i++) level.update(STEP);
        assertTrue(level.player.position.x > arrived, "the new hero of the next room walks on with the key still held");
    }

    /** A jump key held through the pause makes no jump: the exit reset (as in the game) needs a fresh press. */
    @Test void aJumpHeldThroughThePauseNeedsAFreshPress() {
        Level level = harness.roomOf("forest-1", 0);
        for (int i = 0; i < 120; i++) level.update(STEP);
        float floor = level.player.position.y;
        harness.held.add(com.badlogic.gdx.Input.Keys.J);
        level.player.resetJumpInput();                 // what KimeriaGame does when the hero reaches the exit
        level.beginLeaving();
        for (int i = 0; i < 12; i++) level.updateRoomChange(STEP);
        for (int i = 0; i < 10; i++) level.update(STEP);
        assertEquals(floor, level.player.position.y, 0.01f, "no jump from a key that was already down");
        harness.held.remove(com.badlogic.gdx.Input.Keys.J);
        for (int i = 0; i < 3; i++) level.update(STEP);
        harness.held.add(com.badlogic.gdx.Input.Keys.J);
        for (int i = 0; i < 10; i++) level.update(STEP);
        assertTrue(level.player.position.y < floor - 1f, "a fresh press jumps");
    }

    @Test void enteringARoomPlaysTheRippleAndRetryDoesNot() {
        Level level = harness.roomOf("forest-1", 0);
        level.enterNextRoom(new com.jlogicsoftware.kimeria.entity.player.Player(harness.assets(), harness.game(), 0, 0));
        for (int i = 0; i < 3; i++) level.updateRoomChange(STEP);
        assertTrue(level.roomRipple().ringCount() > 0 || level.roomRipple().motes().count() > 0, "the ripple plays where the hero appears");
        level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(harness.assets(), harness.game(), 0, 0));
        assertEquals(0, level.roomRipple().ringCount() + level.roomRipple().motes().count(), "a retry after death is instant (D3): no ripple, nothing left over");
        assertNull(level.activeRipple());
        assertEquals(0.20f, RoomRippleFx.LEAVE_HOLD_SECONDS, 0f);
    }
}
