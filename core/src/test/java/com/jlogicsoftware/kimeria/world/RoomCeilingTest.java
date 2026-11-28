package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #215, D38: a room without a ceiling let the hero climb a side wall and leave over the top. Walls can no
 * longer be grabbed (D121), but every room of every level must still keep the hero inside, however long he keeps
 * jumping against a side wall (the ceiling is now a guard, not the answer to a climb).
 */
class RoomCeilingTest {
    private static final float STEP = 1f / 60f;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final Set<Integer> held = harness.held, pressed = harness.pressed;

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void restoreGlobals() { harness.restoreGlobals(); }

    private void frame(Level level) {
        level.update(STEP);
        pressed.clear();
    }

    private Level roomOf(String name, int index) { return harness.roomOf(name, index); }

    /** Stands the hero on the floor against one side wall, holds into it and jumps as often as the game allows. */
    private float highestPointReached(Level level, boolean leftWall, int frames) {
        return highestPointReached(level, leftWall, frames, 0);
    }

    /** {@code lift} stands the hero that many px higher, e.g. on a ledge instead of the floor. */
    private float highestPointReached(Level level, boolean leftWall, int frames, float lift) {
        Rectangle room = level.currentRoomBounds();
        Player p = level.player;
        Rectangle b = p.hitboxBounds();
        float dx = leftWall ? room.x + 16 + 1 - b.x : room.x + room.width - 16 - 1 - (b.x + b.width);
        p.position.x += dx;
        p.position.y -= lift;
        float top = Float.MAX_VALUE;
        for (int i = 0; i < frames; i++) {
            held.clear();
            held.add(leftWall ? Input.Keys.A : Input.Keys.D);
            if (i % 2 == 0) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
            frame(level);
            Rectangle hb = p.hitboxBounds();
            top = Math.min(top, hb.y);
            assertTrue(hb.x >= room.x - 1 && hb.x + hb.width <= room.x + room.width + 1,
                level.currentRoomName() + (leftWall ? " left" : " right") + ": left the room sideways at frame " + i + " " + hb);
            assertTrue(hb.y >= room.y - 1, level.currentRoomName() + (leftWall ? " left" : " right")
                + ": climbed out over the top at frame " + i + " " + hb + " room " + room);
        }
        held.clear();
        return top;
    }

    private void assertCannotLeave(String map, int rooms) {
        for (int room = 0; room < rooms; room++) {
            for (boolean left : new boolean[]{true, false}) {
                // The lever lesson room (#418) differs from the route room: its exit ledge is 192 x 112 px of solid block against the right wall, so this sweep, which puts the hero on the floor
                // there, places him inside it and collision resolution ejects him through the floor. Real play cannot reach that state; the dedicated ledge test below covers the real wall.
                // That wall is swept from the ledge's top by theLeverLessonRoomLedgeDoesNotLetTheHeroOverTheTop.
                if (map.equals("forest-1") && room == 4 && !left) continue;
                // The hive room (#365) has the same kind of exit ledge (240 x 112 px of solid block against its right wall): its
                // wall is swept from the ledge's top by theHiveRoomLedgeDoesNotLetTheHeroOverTheTop.
                if (map.equals("forest-1") && room == 10 && !left) continue;
                // The fissure room (#366) too: its exit ledge is 224 x 96 px of solid block against its right wall, swept from
                // the ledge's top by theFissureRoomLedgeDoesNotLetTheHeroOverTheTop.
                if (map.equals("forest-2") && room == 2 && !left) continue;
                highestPointReached(roomOf(map, room), left, 60 * 20);
            }
        }
    }

    @Test void forest0RoomsKeepTheHeroInsideWhateverWallHeJumpsAgainst() { assertCannotLeave("forest-0", 6); }
    @Test void forest1KeepsTheHeroInside() { assertCannotLeave("forest-1", 12); }

    /** The route room's exit ledge (top y 144, against the right wall) is a 176 px head start up that wall. */
    @Test void theRouteRoomLedgeDoesNotLetTheHeroOverTheTop() {
        highestPointReached(roomOf("forest-1", 8), false, 60 * 20, 176);
    }

    /** The lever lesson room's exit ledge (top y 208, against the right wall, #418) is a 112 px head start up that wall. */
    @Test void theLeverLessonRoomLedgeDoesNotLetTheHeroOverTheTop() {
        highestPointReached(roomOf("forest-1", 4), false, 60 * 20, 112);
    }
    /** The hive room's exit ledge (top y 208, against the right wall, #365) is a 112 px head start up that wall. */
    @Test void theHiveRoomLedgeDoesNotLetTheHeroOverTheTop() {
        highestPointReached(roomOf("forest-1", 10), false, 60 * 20, 112);
    }

    /** Entry, walls, fissure, glade, roost, hollow, ridge and canopy; the boss room (index 8) is enclosed by plain blocks and has no side walls to stand against. */
    @Test void forest2KeepsTheHeroInside() { assertCannotLeave("forest-2", 8); }

    /** The fissure room's exit ledge (top y 224, against the right wall, #366) is a 96 px head start up that wall. */
    @Test void theFissureRoomLedgeDoesNotLetTheHeroOverTheTop() {
        highestPointReached(roomOf("forest-2", 2), false, 60 * 20, 96);
    }

    @Test void everyRoomIsClosedFromAboveByASlabEndingAtItsTopEdge() {
        for (String map : new String[]{"forest-0", "forest-1", "forest-2"}) {
            int rooms = map.equals("forest-0") ? 6 : map.equals("forest-1") ? 11 : map.equals("forest-2") ? 4 : 2;
            for (int index = 0; index < rooms; index++) {
                Level level = roomOf(map, index);
                Rectangle room = level.currentRoomBounds();
                String where = map + " room " + index;
                var slab = level.collisionBlocks.stream().filter(b -> b.getY() + b.getHeight() == room.y).findFirst().orElseThrow();
                assertEquals(room.x, slab.getX(), 0.01f, where);
                assertEquals(room.width, slab.getWidth(), 0.01f, where);
                assertTrue(slab.getHeight() >= 32, where + ": thick enough that no frame of jump travel tunnels through");
                assertFalse(slab.isPlatform() || slab.isWall() || slab.isQuickSand(), where);
            }
        }
    }

    /** The ladder room's exit ledge (y 208, against the right wall) is a 112 px head start up that wall. */
    @Test void theLadderRoomLedgeDoesNotLetTheHeroOverTheTop() {
        highestPointReached(roomOf("forest-0", 1), false, 60 * 20, 112);
    }

    /** Same for the platform room's exit ledge, which sits against the right wall. */
    @Test void thePlatformRoomLedgeDoesNotLetTheHeroOverTheTop() {
        highestPointReached(roomOf("forest-0", 3), false, 60 * 20, 112);
    }

    /** D48: the platform room's falling stone starts flush with the room top, inside the room and under the D38 slab. */
    @Test void theFallingStoneOfThePlatformRoomStartsInsideTheRoomUnderTheSlab() {
        Level level = roomOf("forest-0", 3);
        Rectangle room = level.currentRoomBounds();
        var stone = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.objects.FallingStone.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.objects.FallingStone.class::cast).findFirst().orElseThrow();
        assertEquals(room.y, stone.bodyBounds().y, 0.01f, "flush with the room top");
        assertTrue(level.collisionBlocks.stream().anyMatch(b -> b.getY() + b.getHeight() == room.y
            && b.getX() <= stone.bodyBounds().x && b.getX() + b.getWidth() >= stone.bodyBounds().x + stone.bodyBounds().width), "the slab lies over it");
        assertTrue(stone.bodyBounds().x >= room.x && stone.bodyBounds().x + stone.bodyBounds().width <= room.x + room.width);
    }

    /**
     * #360 (D71): the stone's lip must be on screen while the hero stands on the floor. Rooms are 8 px taller than the view and the
     * camera then shows the bottom of the room: forest-0 hangs it at y 128, a third of the view down (#550, under a soil block under the canopy), forest-canopy hangs it under the dirt block over
     * the stone with {@code lipTop} 48, forest-roost (#330, D146) under a deeper one at 96, where no cloud lies behind it. Checked against the hero's own camera target,
     * standing on the floor, in every room with a stone here.
     */
    @Test void theStoneLipIsInsideTheCameraViewWhileTheHeroStandsOnTheFloor() {
        String[][] rooms = {{"forest-0", "3", "128"}, {"forest-2", "4", "96"}, {"forest-2", "7", "48"}};
        for (String[] r : rooms) {
            Level level = roomOf(r[0], Integer.parseInt(r[1]));
            var stone = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.objects.FallingStone.class::isInstance)
                .map(com.jlogicsoftware.kimeria.entity.objects.FallingStone.class::cast).findFirst().orElseThrow();
            Player p = level.player;
            p.position.x = stone.getX() - 200;  // on the floor before the stone's zone, the stone in view
            for (int i = 0; i < 60; i++) frame(level);
            assertTrue(p.isOnGround(), r[0] + ": the hero stands on the floor");
            var camera = p.roomCameraTarget();
            Rectangle view = new Rectangle(camera.x, camera.y, harness.game().logicalResolution().x, harness.game().logicalResolution().y);
            Rectangle lip = stone.lipBounds();
            String where = r[0] + " lip " + lip + " view " + view;
            assertEquals(Float.parseFloat(r[2]), lip.y, 0.01f, where + ": the lip's top");
            assertEquals(0, stone.bodyBounds().y, 0.01f, where + ": the stone still starts at the room top (timing unchanged)");
            assertTrue(lip.y >= view.y && lip.y + lip.height <= view.y + view.height, where + ": vertically on screen");
            assertTrue(lip.x >= view.x && lip.x + lip.width <= view.x + view.width, where + ": horizontally on screen");
            assertEquals(com.jlogicsoftware.kimeria.entity.objects.FallingStone.LIP_LOOSE, stone.lipFrame(), where);
        }
    }
}
