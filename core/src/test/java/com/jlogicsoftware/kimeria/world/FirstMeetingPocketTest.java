package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.enemy.Bat;
import com.jlogicsoftware.kimeria.entity.enemy.Snail;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.entity.objects.Bramble;
import com.jlogicsoftware.kimeria.entity.objects.EmberFissure;
import com.jlogicsoftware.kimeria.entity.objects.HangingHive;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D105 (#499): a new element's first meeting has a safe pocket. Each test stands the hero still, for
 * {@link #POCKET_SECONDS}, at the pocket its room card names (hero centre x, on the required route just before that beat)
 * and checks, every frame: nothing harms him; the element is inside the game camera's view (the target the player hands
 * the camera, {@code Player.roomCameraTarget}, at the logical resolution); and over the wait the element is seen acting
 * (a named state or phase, or {@link #MOVES} for one that patrols). {@code RoomCardsTest} checks that each card's
 * `First meeting` names one of these methods, with the room, the element and the pocket x in it; the 96 px clearance is
 * measured on the map there. First meetings that cannot pass (an enemy that waits idle until the hero is in its reach, a
 * touch trap that acts only when tripped, a late or crowded meeting) are tracked by #500 and not claimed here.
 */
class FirstMeetingPocketTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    /** Longer than every clock in these rooms (the slowest, the hive under the ladder, runs 4.5 s), twice over. */
    private static final float POCKET_SECONDS = 12f;
    /** The signal of an element whose position changed during the wait (a bat on its patrol). */
    static final String MOVES = "MOVES";

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    /** What the element is doing: its state, its clock's phase, and whether it has moved from where it started. */
    private static void signals(Entity e, float startX, float startY, Set<String> out) {
        if (e instanceof Actor<?> a) out.add(String.valueOf(a.getState()));
        if (e instanceof Bramble b) out.add(String.valueOf(b.currentPhase()));
        if (e instanceof HangingHive b) out.add(String.valueOf(b.currentPhase()));
        if (e instanceof EmberFissure b) out.add(String.valueOf(b.currentPhase()));
        if (e.getX() != startX || e.getY() != startY) out.add(MOVES);
    }

    /**
     * Stands the hero with his centre at {@code pocketX} in {@code room} and checks the pocket around the first (lowest x)
     * {@code type} there, the {@code element} of the room card, which must show {@code acting} during the wait.
     */
    private Set<String> pocket(String map, String room, String element, Class<? extends Entity> type, float pocketX, String acting) {
        return pocket(map, room, element, type, pocketX, Float.NaN, acting);
    }

    /**
     * As {@link #pocket(String, String, String, Class, float, String)}, with the hero standing on a support whose top is at
     * {@code standY} (world Y, growing downward) instead of the room's floor: a pocket raised out of a firing line (the snail, D106).
     */
    private Set<String> pocket(String map, String room, String element, Class<? extends Entity> type, float pocketX, float standY, String acting) {
        Rectangle view = new Rectangle();
        Level level = h.room(map, room, () -> view);
        Rectangle bounds = level.currentRoomBounds();
        Entity target = null;
        for (var o : level.objects)
            if (type.isInstance(o) && ((Entity) o).getX() >= bounds.x && ((Entity) o).getX() < bounds.x + bounds.width
                && (target == null || ((Entity) o).getX() < target.getX())) target = (Entity) o;
        assertNotNull(target, room + " holds no " + type.getSimpleName() + " (" + element + ")");
        level.player.position.x = pocketX - level.player.getWidth() / 2;
        if (!Float.isNaN(standY)) {
            level.player.position.y = standY - level.player.getHeight();
            level.player.velocity.setZero();
        }
        assertEquals(pocketX, level.player.centerX(), 0.01f);
        float startX = target.getX(), startY = target.getY();
        Vector2 resolution = h.game().logicalResolution();
        Set<String> seen = new TreeSet<>();
        Rectangle box = new Rectangle();
        for (int f = 0; f < POCKET_SECONDS / STEP; f++) {
            Vector2 camera = level.player.roomCameraTarget();
            view.set(camera.x, camera.y, resolution.x, resolution.y);
            level.update(STEP);
            assertFalse(level.player.isGotHit(), room + ": the hero in the pocket at x " + pocketX + " was hit at " + f * STEP + " s");
            assertTrue(view.overlaps(box.set(target.getX(), target.getY(), target.getWidth(), target.getHeight())), room + ": the " + element + " left the camera's view at " + f * STEP + " s");
            signals(target, startX, startY, seen);
            if (!Float.isNaN(standY)) assertEquals(standY, level.player.position.y + level.player.getHeight(), 0.5f, room + ": the hero left the raised pocket");
        }
        assertTrue(seen.contains(acting), room + ": the " + element + " never showed " + acting + " from the pocket, only " + seen);
        return seen;
    }

    /** The snail sees him on the one-way step (inside its sight, above its firing line): it notices and watches, never spits (D106). */
    @Test void forest1SpiderTheSnail() {
        Set<String> seen = pocket("forest-1", "forest-1-spider", "snail", Snail.class, 7456, 288, "NOTICE");
        for (Snail.State never : new Snail.State[]{Snail.State.WINDUP, Snail.State.SPIT, Snail.State.RELOAD})
            assertFalse(seen.contains(never.name()), "the snail never spits at the hero on the step: it showed " + seen);
        assertTrue(seen.contains(Snail.State.WATCH.name()), "it watches him after the notice: " + seen);
    }
    @Test void forest1SpiderTheSpider() { pocket("forest-1", "forest-1-spider", "spider", Spider.class, 7030, "NOTICE"); }
    @Test void forest1BrambleTheBramble() { pocket("forest-1", "forest-1-bramble", "bramble", Bramble.class, 6170, "GROW"); }
    @Test void forest1RouteTheBat() { pocket("forest-1", "forest-1-route", "bat-enemy", Bat.class, 1760, MOVES); }
    @Test void forest1HiveTheFloorHive() { pocket("forest-1", "forest-1-hive", "hanging-hive", HangingHive.class, 6420, "RELEASE"); }
    @Test void forestFissureTheFirstFissure() { pocket("forest-2", "forest-fissure", "ember-fissure", EmberFissure.class, 3200, "JET"); }
}
