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
 * #215: the side walls of a room are clamberable and wall-jumpable, so a room without a ceiling let the
 * hero climb out over the top and fall out of the level. Every room of every level must keep the hero
 * inside, whichever side wall he climbs and however long he keeps jumping. Rule: D38.
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
                highestPointReached(roomOf(map, room), left, 60 * 20);
            }
        }
    }

    @Test void forest0RoomsKeepTheHeroInsideWhateverWallHeClimbs() { assertCannotLeave("forest-0", 6); }
    @Test void forest1KeepsTheHeroInside() { assertCannotLeave("forest-1", 1); }
    /** The boss room (index 2) is enclosed by plain, non-climbable blocks and has no side walls to stand against. */
    @Test void forest2KeepsTheHeroInside() { assertCannotLeave("forest-2", 2); }

    @Test void everyRoomIsClosedFromAboveByASlabEndingAtItsTopEdge() {
        for (String map : new String[]{"forest-0", "forest-1", "forest-2"}) {
            int rooms = map.equals("forest-0") ? 6 : map.equals("forest-2") ? 3 : 1;
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
}
