package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.Collidable;
import com.jlogicsoftware.kimeria.entity.items.Actionable;
import com.jlogicsoftware.kimeria.entity.objects.FallingStone;
import com.jlogicsoftware.kimeria.entity.objects.FallingTrap;
import com.jlogicsoftware.kimeria.entity.objects.HangingVine;
import com.jlogicsoftware.kimeria.entity.objects.ThornVine;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stone-linked scenery vine (#239 step 5, docs/ART_DIRECTION.md section 2): the Tiled type, the swing a falling
 * stone's impact starts, the Screen shake option, pause, and the placement of the vines in the shipped maps.
 *
 * <p>Y grows downward: a vine's {@code y} is its top (anchor) edge, which must be the room's ceiling line, and it hangs to
 * {@code y + height}.
 */
class HangingVineTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    /** Vines authored per shipped map; forest-1 has no falling stone, so it has none (docs/ART_DIRECTION.md, placement). */
    private static final Map<String, Integer> VINES = Map.of("forest-0", 3, "forest-1", 0, "forest-2", 3);
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private HangingVine vine(float x) { return new HangingVine(harness.assets(), x, 0, 8, 40); }

    private static TiledObject object(float w, float h) {
        TiledObject o = new TiledObject();
        o.id = 9; o.name = "Vine"; o.type = "HangingVine"; o.x = 100; o.y = 0; o.width = w; o.height = h;
        return o;
    }

    private static FallingStone stone(Level level) {
        return level.objects.stream().filter(FallingStone.class::isInstance).map(FallingStone.class::cast).findFirst().orElseThrow();
    }

    /** The room of {@code map} that holds its falling stone (the maps have one each). */
    private Level stoneRoom(String map) {
        for (int room = 0; ; room++) {
            Level level = harness.roomOf(map, room);
            if (level.objects.stream().anyMatch(FallingStone.class::isInstance)) return level;
            assertTrue(level.hasNextRoom(), map + " has a falling stone");
        }
    }

    /** Puts the hero in the stone's zone but not under its column and runs until the stone lands (the frame it lands is the last). */
    private void landTheStone(Level level) {
        FallingStone stone = stone(level);
        level.player.position.set(stone.zone().x - 18, 272); // body left = zone left, 32 px left of the stone's column
        for (int i = 0; i < 600 && !stone.isSpent(); i++) level.update(STEP);
        assertTrue(stone.isSpent(), "the stone must land");
        assertFalse(level.player.isGotHit(), "the hero stood beside the stone's column");
    }

    @Test void theTypeLoadsAndABadSizeFailsTheLoad() {
        HangingVine v = HangingVine.fromMap(harness.assets(), object(8, 40), "x");
        assertEquals(104f, v.anchorX(), 0f);
        assertEquals(0f, v.anchorY(), 0f);
        assertThrows(IllegalArgumentException.class, () -> HangingVine.fromMap(harness.assets(), object(0, 40), "'Vine' [9]"));
        assertFalse(Collidable.class.isAssignableFrom(HangingVine.class) || Actionable.class.isAssignableFrom(HangingVine.class),
            "a vine is neither collidable nor actionable");
    }

    @Test void aVineAtRestIsStillAndThereIsNoIdleSway() {
        HangingVine v = vine(100);
        for (int i = 0; i < 600; i++) { v.update(STEP); assertEquals(0f, v.angle(), 0f); }
        assertFalse(v.swaying());
    }

    @Test void aShakeWithinReachSwingsAwayFromTheStoneAndDecaysToRest() {
        HangingVine right = vine(100), left = vine(100);
        right.shake(60, HangingVine.STONE_STRENGTH); // stone to the left of the anchor (104): swings right first
        left.shake(150, HangingVine.STONE_STRENGTH);
        assertTrue(right.swaying() && left.swaying());
        float peakRight = 0, peakLeft = 0, last = Float.MAX_VALUE;
        int firstSignRight = 0, firstSignLeft = 0;
        for (int i = 0; i < 40; i++) {
            right.update(STEP); left.update(STEP);
            if (firstSignRight == 0 && right.angle() != 0) firstSignRight = right.angle() > 0 ? 1 : -1;
            if (firstSignLeft == 0 && left.angle() != 0) firstSignLeft = left.angle() > 0 ? 1 : -1;
            peakRight = Math.max(peakRight, Math.abs(right.angle())); peakLeft = Math.max(peakLeft, Math.abs(left.angle()));
        }
        assertEquals(1, firstSignRight, "to the right of the stone it swings right first");
        assertEquals(-1, firstSignLeft, "to the left of the stone it swings left first");
        float limit = (float) Math.toRadians(HangingVine.PEAK_DEGREES);
        assertTrue(peakRight > 0.05f && peakRight <= limit, "a visible swing, never beyond the peak: " + peakRight);
        assertTrue(peakLeft > 0.02f && peakLeft <= limit);
        int frames = 0;
        while (right.swaying() && frames++ < 600) right.update(STEP);
        assertEquals(Math.round(HangingVine.DURATION / STEP), frames + 40, 1, "the swing lasts DURATION seconds");
        assertEquals(0f, right.angle(), 0f, "back at rest");
    }

    @Test void aStoneBeyondReachMovesNothingAndAWeakerShakeNeverCutsAStrongerOne() {
        HangingVine v = vine(100);
        v.shake(104 + HangingVine.REACH, 1f);
        v.shake(104 - HangingVine.REACH - 1, 1f);
        assertFalse(v.swaying(), "at or beyond the reach");
        v.shake(104 + HangingVine.REACH - 1, 1f);
        assertTrue(v.swaying(), "just inside the reach");

        HangingVine w = vine(100);
        w.shake(104, 1f);
        for (int i = 0; i < 10; i++) w.update(STEP);
        float before = w.angle();
        w.shake(104 + 120, 1f);
        assertEquals(before, w.angle(), 0f, "the weaker, farther shake is ignored");
        for (int i = 0; i < 60; i++) w.update(STEP);
        w.shake(104, 1f); // stronger than what is left of the old swing: it restarts at the start of a full swing
        assertTrue(w.swaying());
        w.update(STEP);
        assertNotEquals(0f, w.angle());
    }

    @Test void pausingFreezesTheSwingBecauseOnlyUpdateAdvancesIt() {
        HangingVine v = vine(100);
        v.shake(60, 1f);
        for (int i = 0; i < 12; i++) v.update(STEP);
        float frozen = v.angle();
        assertNotEquals(0f, frozen);
        assertEquals(frozen, v.angle(), 0f, "no update (the paused level), no motion");
        v.update(STEP);
        assertNotEquals(frozen, v.angle(), "it moves again once updates resume");
    }

    @Test void theVineHangsDownFromItsAnchorAtTheTopOfTheRotation() {
        // World Y grows downward: at rest the sprite's top edge is the anchor and its bottom edge is length lower.
        HangingVine v = vine(100);
        assertEquals(0f, v.getY(), 0f);
        assertEquals(40f, v.getY() + v.getHeight(), 0f);
        v.shake(60, 1f);
        for (int i = 0; i < 6; i++) v.update(STEP);
        float a = v.angle();
        assertNotEquals(0f, a);
        // The tip of a vine rotated clockwise by a (positive = swing right) is at anchor + length * (sin a, cos a).
        float tipX = v.anchorX() + 40 * (float) Math.sin(a), tipY = v.anchorY() + 40 * (float) Math.cos(a);
        assertTrue(tipY > v.anchorY() + 39f, "still hangs below its anchor: " + tipY);
        assertEquals(a > 0, tipX > v.anchorX(), "a positive angle moves the tip right");
    }

    @Test void everyShippedVineHangsFromTheCeilingInsideItsRoomAndHasNoCollision() {
        VINES.forEach((map, expected) -> {
            int found = 0;
            for (int room = 0; ; room++) {
                Level level = harness.roomOf(map, room);
                for (HangingVine v : level.vines) {
                    found++;
                    Rectangle rect = new Rectangle(v.getX(), v.getY(), v.getWidth(), v.getHeight());
                    String label = map + " vine at " + rect;
                    Rectangle bounds = level.currentRoomBounds();
                    assertEquals(bounds.y, rect.y, label + ": the top edge is the room's ceiling line (world Y grows downward)");
                    // Rectangle.contains is strict at the edges, and the vine touches the top edge by design.
                    assertTrue(rect.x >= bounds.x && rect.y >= bounds.y && rect.x + rect.width <= bounds.x + bounds.width
                        && rect.y + rect.height <= bounds.y + bounds.height, label + " must lie inside its room");
                    assertEquals(8f, v.getWidth(), label);
                    assertEquals(40f, v.getHeight(), label);
                    assertTrue(rect.y + rect.height > rect.y, label + " hangs down");
                    boolean stoneNear = level.objects.stream().filter(FallingStone.class::isInstance).map(FallingStone.class::cast)
                        .anyMatch(s -> Math.abs(s.getX() + s.getWidth() / 2f - v.anchorX()) < HangingVine.REACH);
                    assertTrue(stoneNear, label + ": a falling stone of its room within reach, or nothing can move it");
                    for (var b : level.collisionBlocks)
                        assertFalse(b.isActive() && !b.isQuickSand() && rect.overlaps(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight())),
                            label + " overlaps a solid block");
                }
                for (var o : level.objects) assertFalse(o instanceof HangingVine, "vines live in Level.vines, drawn with the decor");
                if (!level.hasNextRoom()) break;
            }
            assertEquals(expected, found, map + ": vines authored");
        });
    }

    @Test void aLandingStoneSwaysEveryVineInReachInTheRealLevelsAndTheThornVineIsUntouched() {
        for (String map : new String[]{"forest-0", "forest-2"}) {
            Level level = stoneRoom(map);
            assertEquals(3, level.vines.size(), map);
            for (HangingVine v : level.vines) assertFalse(v.swaying(), map + ": still until the stone lands");
            ThornVine thorn = level.objects.stream().filter(ThornVine.class::isInstance).map(ThornVine.class::cast).findFirst().orElse(null);
            landTheStone(level);
            for (HangingVine v : level.vines) assertTrue(v.swaying(), map + ": vine at " + v.getX() + " must sway");
            if (thorn != null) {
                // The lethal pendulum is a pure function of its own clock: the vines never feed it.
                assertEquals(thorn.angleAt(thorn.elapsed()), thorn.angle(), 0f);
                Rectangle body = level.player.hitboxBounds();
                assertEquals(thorn.lethalAt(thorn.angle(), body), thorn.lethalTo(body));
            }
        }
    }

    @Test void theScreenShakeOptionOffKeepsEveryVineStill() {
        harness.screenShakeDisabled = true;
        Level level = stoneRoom("forest-0");
        landTheStone(level);
        for (HangingVine v : level.vines) { assertFalse(v.swaying()); assertEquals(0f, v.angle(), 0f); }
    }

    @Test void theOptionOnSwaysThemAndARoomRebuildRestsThemAgain() {
        Level level = stoneRoom("forest-0");
        landTheStone(level);
        assertTrue(level.vines.stream().anyMatch(HangingVine::swaying));
        level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(harness.assets(), harness.game(), 0, 0));
        assertEquals(3, level.vines.size(), "cleared and respawned, not accumulated");
        assertTrue(level.vines.stream().noneMatch(HangingVine::swaying), "recreated at rest");
        assertEquals(FallingTrap.State.ARMED, stone(level).state());
    }
}
