package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.Settings;
import com.jlogicsoftware.kimeria.entity.objects.Bramble;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code forest-1-bramble} (#364, D89): the creeping bramble's teaching room, fifth in forest-1's play order (after the
 * lever lesson). The hero walks the floor under a shelf with the bramble cycling in view and out of reach, climbs a static
 * ladder that stands one tile off the right wall, waits on it under the thorns, climbs out when the arm has pulled back
 * and runs left along the shelf to the exit. World Y grows downward.
 */
class Forest1BrambleTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final String ROOM_NAME = "forest-1-bramble";
    private static final float ROOM_X = 5600, START_X = 5760, SHELF_TOP = 208, FLOOR = 320, LADDER_X = 6192, VIEW_W = 640, VIEW_H = 360;
    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    @TempDir Path directory;

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void tearDown() { h.restoreGlobals(); }

    private Level room() { return h.room("forest-1", ROOM_NAME); }
    private void frame(Level level) { level.update(STEP); h.pressed.clear(); }
    private void hold(int... keys) { h.held.clear(); for (int k : keys) h.held.add(k); }
    private static Bramble bramble(Level level) {
        return level.objects.stream().filter(Bramble.class::isInstance).map(Bramble.class::cast).findFirst().orElseThrow();
    }
    /** The thorn root (x 5872) is jumped from 30 px before it (its window is 7 to 63 px, ROOMS.md). */
    private static final float ROOT_X = 5872, ROOT_TAKE_OFF = 30;
    private boolean jumpedRoot;
    private void walkRight(Level level) {
        Rectangle me = level.player.hitboxBounds();
        boolean jump = !jumpedRoot && me.x + me.width >= ROOT_X - ROOT_TAKE_OFF && level.player.isOnGround();
        if (jump) { jumpedRoot = true; hold(Input.Keys.D, Input.Keys.J); h.pressed.add(Input.Keys.J); } else hold(Input.Keys.D);
    }
    private static float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }

    private void assist(boolean on) {
        Preferences prefs = new HeadlessPreferences(new FileHandle(directory.resolve("settings" + on + ".xml").toFile()));
        prefs.putBoolean(Settings.KEY_LONGER_TRAP_WARNINGS, on);
        h.game().trapWarnings().load(new Settings(prefs));
    }

    /** Walk to the ladder, climb to the waiting place, wait for the arm to come back, climb out, run to the exit; frames. */
    private int solve(Level level) {
        int frames = 0;
        jumpedRoot = false;
        while (level.player.hitboxBounds().x + level.player.hitboxBounds().width / 2 < LADDER_X + 1) {
            walkRight(level); frame(level); frames++;
            assertFalse(level.player.isGotHit(), "died on the floor at " + level.player.position);
            assertTrue(frames < 60 * 20, "ladder unreachable from " + level.player.position);
        }
        while (level.player.hitboxBounds().y > SHELF_TOP) {
            hold(Input.Keys.W); frame(level); frames++;
            assertFalse(level.player.isGotHit(), "died on the ladder at " + level.player.position);
            assertTrue(frames < 60 * 30, "climb stuck at " + level.player.position);
        }
        hold();
        Bramble b = bramble(level);
        // A mastered player goes at once when the lane is open long enough, else waits for the arm to come back.
        boolean openNow = b.currentPhase() == Bramble.Phase.REST && b.cycleTime() < 0.6f;
        if (!openNow) {
            while (b.currentPhase() != Bramble.Phase.RETRACT) { frame(level); frames++; }
            while (b.currentPhase() == Bramble.Phase.RETRACT) { frame(level); frames++; }
        }
        while (!level.atRoomExit()) {
            boolean up = level.player.onLadder() || feet(level) > SHELF_TOP + 1;
            hold(up ? Input.Keys.W : Input.Keys.A); frame(level); frames++;
            assertFalse(level.player.isGotHit(), "the bramble caught the climb-out at " + level.player.position);
            assertTrue(frames < 60 * 40, "exit unreachable from " + level.player.position);
        }
        hold();
        return frames;
    }

    @Test void theRoomFollowsTheLeverRoomAndHoldsOneBrambleAndOneStaticLadder() {
        Level level = room();
        assertEquals("forest-1-bramble", level.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, 640, 368), level.currentRoomBounds());
        assertEquals(1, level.objects.stream().filter(Bramble.class::isInstance).count());
        assertEquals(1, level.ladders.size());
        Ladder ladder = level.ladders.get(0);
        assertEquals(LADDER_X, ladder.getX(), 0f);
        assertEquals(SHELF_TOP, ladder.topY(), 0f, "the ladder is down from the start, no lever");
        assertEquals(FLOOR, ladder.bottomY(), 0f);
        assertTrue(level.triggers.isEmpty(), "no lever in this room");
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Enemy<?>), "no monster here");
        Bramble b = bramble(level);
        assertTrue(b.rootRight());
        Rectangle full = b.fullBounds();
        assertEquals(SHELF_TOP - 18f, full.y, 0f, "the band's top is 18 px above the shelf line (Y down)");
        assertEquals(SHELF_TOP - 2f, full.y + full.height, 0f);
        assertTrue(full.x < LADDER_X && full.x + full.width > LADDER_X + 16, "the grown arm covers the ladder top");
        assertTrue(LADDER_X + 16 <= full.x + full.width - Bramble.CLUMP_REACH,
            "the ladder stands clear of the always-lethal clump (D89 recipe)");
    }

    @Test void theBrambleIsInViewFromTheEntranceAndNeverReachesTheFloor() {
        Level level = room();
        assertEquals(START_X, level.player.position.x, 0.01f);
        Vector2 cam = level.player.roomCameraTarget();
        Rectangle view = new Rectangle(cam.x, cam.y, VIEW_W, VIEW_H);
        assertTrue(view.contains(bramble(level).fullBounds()), "the whole arm is on screen from the entrance: " + view);
        Rectangle onFloor = new Rectangle(ROOM_X + 16, FLOOR - 22, 640 - 32, 22);
        assertFalse(bramble(level).everLethalIn(onFloor), "a hero walking the floor is never in reach");
    }

    @Test void theWarningIsAudibleFromTheRoomEntranceBecauseTheBrambleIsInTheCameraView() {
        // The cues are gated on the camera's visible world rect, not on the distance to the hero (review of #364): the
        // bramble's centre is about 560 px from the entrance, yet the whole one-screen room is on screen there.
        Rectangle view = new Rectangle();
        Level level = h.room("forest-1", ROOM_NAME, () -> view);
        Vector2 cam = level.player.roomCameraTarget();
        view.set(cam.x, cam.y, VIEW_W, VIEW_H);
        Bramble b = bramble(level);
        assertTrue(Math.abs(b.centerX() - level.player.centerX()) > VIEW_W / 2f, "the hero is more than half a view from it");
        assertTrue(level.brambleInView(b), "the rustle and the lash are heard from the entrance");
        view.set(ROOM_X - VIEW_W, 0, VIEW_W, VIEW_H);
        assertFalse(level.brambleInView(b), "a view that does not show it hears nothing");
        view.set(0, 0, 0, 0);
        assertFalse(level.brambleInView(b), "no view (a headless level without a camera): silent");
    }

    /** The mastered route from a fresh start: the room card's beat time (D105). */
    static final float MASTERED_S = 9.47f;

    @Test void theMasteredRouteAndItsTimes() {
        float plain = solve(room()) * STEP;
        assist(true);
        float assisted = solve(room()) * STEP;
        assist(false);
        System.out.printf("BRAMBLE ROOM mastered route %.2f s, %.2f s with the longer-warnings assist%n", plain, assisted);
        assertTrue(plain < 20f, "D19 mastered target about 20 s: " + plain);
        assertEquals(MASTERED_S, plain, 0.1f, "the mastered route (D105's beat time), pinned within 0.1 s");
        assertTrue(assisted < 20f, "with the assist: " + assisted);
    }

    @Test void theWaitingRouteReachesTheExitFromEveryStartOffset() {
        Level level = room();
        int worst = 0, best = Integer.MAX_VALUE;
        for (float offset = 0f; offset < 4f; offset += 0.05f) {
            level.resetCurrentRoom(new Player(h.assets(), h.game(), 0, 0));
            hold();
            for (int i = 0, n = Math.round(offset / STEP); i < n; i++) frame(level);
            int frames = solve(level);
            worst = Math.max(worst, frames);
            best = Math.min(best, frames);
        }
        System.out.printf("BRAMBLE ROOM route over every start offset: %.2f to %.2f s%n", best * STEP, worst * STEP);
    }

    /** The retry cost on the card: a blind climb from a fresh start is caught this long after the entrance. */
    static final float BLIND_CLIMB_DEATH_S = 5.42f;

    @Test void aDeathAtTheLadderTopRetriesTheRoomFromItsEntrance() {
        Level level = room();
        int frames = 0;
        jumpedRoot = false;
        while (level.player.hitboxBounds().x + level.player.hitboxBounds().width / 2 < LADDER_X + 1) { walkRight(level); frame(level); frames++; }
        // Climb straight out without looking; keep climbing and running until the arm catches the hero.
        while (!level.player.isGotHit() && !level.atRoomExit()) {
            boolean up = level.player.onLadder() || feet(level) > SHELF_TOP + 1;
            hold(up ? Input.Keys.W : Input.Keys.A); frame(level); frames++;
            assertTrue(frames < 60 * 30);
        }
        hold();
        assertTrue(level.player.isGotHit(), "the blind climb is caught, it does not get out");
        assertEquals(BLIND_CLIMB_DEATH_S, frames * STEP, 0.1f, "the card's retry cost; D105 admits the bramble as beat 3 only at 6 s or less");
        System.out.printf("BRAMBLE ROOM a blind climb from a fresh start: %s after %.2f s%n", level.player.isGotHit() ? "caught" : "got out", frames * STEP);
        level.resetCurrentRoom(new Player(h.assets(), h.game(), 0, 0));
        assertEquals("forest-1-bramble", level.currentRoomName());
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals(1.28f, bramble(level).cycleTime(), 1e-4f, "the retry restarts the clock at the authored phase (1.28 s: the entrance is 128 px nearer, D105)");
        assertTrue(frames * STEP < 20f, "a death costs at most the walk to the ladder and the climb");
    }
}
