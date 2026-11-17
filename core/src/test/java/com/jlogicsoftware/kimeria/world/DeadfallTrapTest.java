package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.objects.DeadfallTrap;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.ExpectedWarnings;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The trip-line deadfall (#363, D88) and its teaching room {@code forest-1-deadfall} (room 2 of forest-1, x 4960-5600).
 * World Y grows downward: the floor's top is y 320, the rope knot P is at (5296, 252), 68 px above it; the log swings
 * left, at a hero coming from the entrance; the trip vine's box is x 5176-5192, y 315-320.
 */
class DeadfallTrapTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float ROOM_X = 4960, ROOM_END = 5600, START_X = 4992, EXIT_X = 5536, FLOOR = 320;
    private static final float P_X = 5296, P_Y = 252, VINE_LEFT = 5176, VINE_RIGHT = 5192;
    /** Player hitbox offset and size (Player.hitbox): body left = position.x + 18, body bottom = position.y + 48. */
    private static final float BODY_OFFSET_X = 18, BODY_W = 11, BODY_H = 22, BODY_BOTTOM = 48, VIEW_W = 640;
    private static final float HEAD = FLOOR - BODY_H;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restore() throws Exception { setLonger(false); h.restoreGlobals(); }

    private Player player() { return new Player(h.assets(), h.game(), 0, 0); }
    private Level room() { return h.roomOf("forest-1", 1); }
    private DeadfallTrap trap(Level l) {
        return l.objects.stream().filter(DeadfallTrap.class::isInstance).map(DeadfallTrap.class::cast).findFirst().orElseThrow();
    }
    private void frame(Level l) { l.update(STEP); pressed.clear(); }
    private void step(Level l, boolean right, boolean jump) {
        held.clear();
        if (right) held.add(Input.Keys.D);
        if (jump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
        frame(l);
    }
    private boolean alive(Level l) { return !l.player.isGotHit(); }
    private float bodyRight(Level l) { Rectangle b = l.player.hitboxBounds(); return b.x + b.width; }
    private float centre(Level l) { Rectangle b = l.player.hitboxBounds(); return b.x + b.width / 2f; }
    /** Stands the hero still on the floor with the body's left edge at {@code bodyLeft}. */
    private void place(Level l, float bodyLeft) { l.player.position.set(bodyLeft - BODY_OFFSET_X, FLOOR - BODY_BOTTOM); l.player.velocity().setZero(); held.clear(); }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    // -- the room --

    @Test void theRoomHoldsOneTiedDeadfallAndTheExitAndNothingElse() {
        Level l = room();
        assertEquals("forest-1-deadfall", l.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, ROOM_END - ROOM_X, 368), l.currentRoomBounds());
        assertEquals(START_X, l.player.position.x, 0.01f);
        assertEquals(1, l.objects.stream().filter(DeadfallTrap.class::isInstance).count());
        assertEquals(0, l.objects.stream().filter(Enemy.class::isInstance).count(), "the deadfall is met alone");
        assertTrue(l.escalators.isEmpty() && l.fallingPlatforms.isEmpty() && l.ladders.isEmpty(), "no machinery");
        assertEquals("", l.player.zoneHint(), "no hints (forest-1 has none, D33)");
        DeadfallTrap t = trap(l);
        assertEquals(DeadfallTrap.Phase.TIED, t.phase());
        assertEquals(P_X, t.pivotX(), 0.01f);
        assertEquals(P_Y, t.pivotY(), 0.01f);
        assertEquals(FLOOR, t.floor(), 0.01f, "the floor line is 68 px under the knots");
        assertEquals(new Rectangle(VINE_LEFT, FLOOR - 5, 16, 5), t.vineBox());
        assertEquals(-1f, t.toward(), "swing=left: the log comes at a hero from the entrance");
        l.enterNextRoom(player());
        assertEquals("forest-1-traps", l.currentRoomName(), "the trap course follows");
    }

    /** One screen: the vine, the tied log and the exit are all in the camera's view from the entrance (D84). */
    @Test void theTrapAndTheExitAreInViewFromTheEntrance() {
        Level l = room();
        Vector2 cam = l.player.roomCameraTarget();
        Rectangle tied = trap(l).logBoxAt(DeadfallTrap.TIED_DEGREES, new Rectangle());
        assertTrue(cam.x <= VINE_LEFT && tied.x + tied.width <= cam.x + VIEW_W && EXIT_X + 32 <= cam.x + VIEW_W,
            "camera x " + cam.x + " shows the vine, the tied log and the exit");
    }

    // -- trigger and warning --

    @Test void theVineTripsOnTheFirstPixelOfOverlapAndNotBefore() {
        Level l = room();
        place(l, VINE_LEFT - BODY_W);
        for (int i = 0; i < 60; i++) frame(l);
        assertEquals(DeadfallTrap.Phase.TIED, trap(l).phase(), "touching the vine's edge is not a trip");
        place(l, VINE_LEFT - BODY_W + 1);
        frame(l);
        assertEquals(DeadfallTrap.Phase.WARNING, trap(l).phase(), "one pixel of overlap trips it");
    }

    @Test void theWarningIsExactlyTheSharedD7LengthNormallyAndWithTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level l = room();
            place(l, VINE_LEFT);
            frame(l);
            DeadfallTrap t = trap(l);
            assertEquals(DeadfallTrap.Phase.WARNING, t.phase());
            assertEquals(ExpectedWarnings.standard(longer), t.warningTotal(), 1e-6f, "the shared value, longer=" + longer);
            assertEquals(TrapWarnings.DEFAULT_WARNING_SECONDS * (longer ? 1.5f : 1f), t.warningTotal(), 1e-6f);
            int frames = 0;
            while (t.phase() == DeadfallTrap.Phase.WARNING && frames++ < 60 * 5) {
                assertEquals(frames * STEP < t.warningTotal() / 2f ? DeadfallTrap.FRAME_STRAIN : DeadfallTrap.FRAME_SLIP, t.logFrame(), 1,
                    "strain, then slip");
                frame(l);
            }
            assertEquals(t.warningTotal() * 60, frames, 2, "warning frames, longer=" + longer);
            assertEquals(DeadfallTrap.Phase.SWING, t.phase());
        }
    }

    // -- the swing: window and band --

    @Test void theLogIsAtBodyHeightOnlyFrom016To034SecondsAndWithin50PxOfTheKnots() {
        Level l = room();
        DeadfallTrap t = trap(l);
        Rectangle box = new Rectangle();
        float first = -1, last = -1, minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        for (int i = 0; i <= 500; i++) {
            float time = i / 1000f;
            t.logBoxAt(DeadfallTrap.angleAt(time), box);
            if (box.y + box.height > HEAD) {
                if (first < 0) first = time;
                last = time;
                minX = Math.min(minX, box.x);
                maxX = Math.max(maxX, box.x + box.width);
            }
        }
        System.out.printf(java.util.Locale.ROOT, "deadfall body-height window %.3f-%.3f s, x %.1f..%.1f (P %.0f)%n", first, last, minX, maxX, P_X);
        assertEquals(0.16f, first, 0.005f);
        assertEquals(0.34f, last, 0.005f);
        assertTrue(minX >= P_X - 50.5f && maxX <= P_X + 50.5f, "band " + minX + ".." + maxX);
        t.logBoxAt(DeadfallTrap.TIED_DEGREES, box);
        assertTrue(box.y + box.height <= HEAD - 30, "tied, the log hangs 31 px over the head: underside " + (box.y + box.height));
        assertEquals(DeadfallTrap.SNAG_DEGREES, DeadfallTrap.angleAt(DeadfallTrap.SWING_SECONDS), 1e-4f);
    }

    /** A hero standing in the band is safe before the release and after the catch; only the swinging log kills. */
    @Test void nothingKillsBeforeTheReleaseOrAfterTheCatch() {
        Rectangle hero = new Rectangle(VINE_LEFT, FLOOR - BODY_H, BODY_W, BODY_H);
        float[] killedAt = {-1};
        float[] clock = {0};
        DeadfallTrap t = new DeadfallTrap(h.assets(), h.game().trapWarnings(), P_X - DeadfallTrap.PIVOT_X, P_Y - DeadfallTrap.PIVOT_Y, false,
            () -> hero, () -> { if (killedAt[0] < 0) killedAt[0] = clock[0]; }, quiet());
        t.update(STEP);
        assertEquals(DeadfallTrap.Phase.WARNING, t.phase());
        hero.x = P_X - BODY_W / 2f; // under the knots, in the middle of the band
        while (t.phase() == DeadfallTrap.Phase.WARNING) { assertFalse(t.isLethal()); t.update(STEP); clock[0] += STEP; }
        assertEquals(-1, killedAt[0], 0f, "no kill during the warning");
        while (t.phase() == DeadfallTrap.Phase.SWING) { t.update(STEP); clock[0] += STEP; }
        float swingStart = TrapWarnings.DEFAULT_WARNING_SECONDS;
        assertTrue(killedAt[0] > swingStart + 0.15f && killedAt[0] < swingStart + 0.35f, "killed in the window, at " + killedAt[0]);
        killedAt[0] = -1;
        hero.set(t.snagX() - BODY_W / 2f, FLOOR - BODY_H, BODY_W, BODY_H);
        for (int i = 0; i < 600; i++) t.update(STEP);
        assertEquals(DeadfallTrap.Phase.CAUGHT, t.phase());
        assertEquals(-1, killedAt[0], 0f, "the caught log hangs over the head: harmless");
        assertEquals(0f, t.lethalBounds().width, 0f);
    }

    /**
     * Review of #363: the log must not jump at the catch. The swing ends at rest (eased) with the ropes across the snag's tip,
     * then the log eases in under the snag; the drawn centre moves less than {@value #CATCH_STEP_MAX} px per frame from three
     * frames before the catch until it hangs still, and the caught pose is reached and kept.
     */
    @Test void theLogDoesNotJumpAtTheCatch() {
        Rectangle hero = new Rectangle(VINE_LEFT, FLOOR - BODY_H, BODY_W, BODY_H);
        DeadfallTrap t = new DeadfallTrap(h.assets(), h.game().trapWarnings(), P_X - DeadfallTrap.PIVOT_X, P_Y - DeadfallTrap.PIVOT_Y, false,
            () -> hero, () -> { }, quiet());
        t.update(STEP);
        hero.x = -10_000; // out of the way after the trip
        while (t.phase() != DeadfallTrap.Phase.SWING) t.update(STEP);
        while (t.swingTime() < DeadfallTrap.SWING_SECONDS - CATCH_WINDOW_FRAMES * STEP) t.update(STEP);
        float x = t.logCentreX(), y = t.logCentreY(), largest = 0;
        int frames = CATCH_WINDOW_FRAMES + (int) Math.ceil(DeadfallTrap.SETTLE_SECONDS / STEP) + CATCH_WINDOW_FRAMES;
        for (int i = 0; i < frames; i++) {
            t.update(STEP);
            float nx = t.logCentreX(), ny = t.logCentreY();
            largest = Math.max(largest, (float) Math.hypot(nx - x, ny - y));
            x = nx; y = ny;
        }
        System.out.printf(java.util.Locale.ROOT, "deadfall catch: largest drawn step %.2f px/frame%n", largest);
        assertEquals(DeadfallTrap.Phase.CAUGHT, t.phase());
        assertTrue(largest < CATCH_STEP_MAX, "the log moved " + largest + " px in one frame across the catch");
        assertEquals(t.snagX(), t.logCentreX(), 1e-3f, "it hangs under the snag");
        assertEquals(t.snagY() + DeadfallTrap.CAUGHT_BELOW_SNAG, t.logCentreY(), 1e-3f);
        assertTrue(t.logCentreY() + DeadfallTrap.LOG_HEIGHT / 2f <= HEAD - 13, "above head height once caught");
    }
    private static final int CATCH_WINDOW_FRAMES = 3;
    private static final float CATCH_STEP_MAX = 3f;

    // -- the counters, played through the room --

    /** Walks right from the entrance, jumping once when the body's right edge is {@code gap} px before the vine; frames to the exit, or -1 on death. */
    private int jumpRun(Level l, float gap) {
        boolean jumped = false;
        for (int f = 0; f < 60 * 20; f++) {
            boolean jump = !jumped && bodyRight(l) >= VINE_LEFT - gap && l.player.isOnGround();
            jumped |= jump;
            step(l, true, jump);
            if (!alive(l)) return -1;
            if (l.atRoomExit()) return f + 1;
        }
        return -1;
    }

    @Test void jumpingTheVineFiresNothingAndReachesTheExitFromAWideTakeOffWindow() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            float lo = Float.MAX_VALUE, hi = -1;
            int best = Integer.MAX_VALUE;
            for (int gap = 0; gap <= 60; gap += 2) {
                Level l = room();
                int frames = jumpRun(l, gap);
                if (frames > 0 && trap(l).phase() == DeadfallTrap.Phase.TIED) { lo = Math.min(lo, gap); hi = Math.max(hi, gap); best = Math.min(best, frames); }
            }
            System.out.printf(java.util.Locale.ROOT, "deadfall jump take-offs %.0f-%.0f px before the vine, route %.2f s (longer=%s)%n", lo, hi, best * STEP, longer);
            assertTrue(lo <= 10 && hi >= 30, "a take-off window of at least 20 px: " + lo + ".." + hi);
            assertTrue(best * STEP < 20f, "the mastered route fits D19");
        }
    }

    @Test void aWalkerWhoTripsTheVineAndKeepsGoingIsHitWithEitherWarning() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level l = room();
            int f = 0;
            while (alive(l) && !l.atRoomExit() && f++ < 60 * 20) step(l, true, false);
            assertFalse(alive(l), "walking straight through is a hit, longer=" + longer);
            assertEquals(DeadfallTrap.Phase.SWING, trap(l).phase(), "the swinging log is what hit him");
            assertTrue(trap(l).swingTime() > 0.15f && trap(l).swingTime() < 0.35f, "hit in the body-height window: " + trap(l).swingTime());
            System.out.printf(java.util.Locale.ROOT, "deadfall walk-in death %.2f s after the entrance, %.2f s into the swing (longer=%s)%n",
                f * STEP, trap(l).swingTime(), longer);
        }
    }

    /** Trip, let go of the keys after {@code reactFrames}, wait for the catch, then walk to the exit; frames to the exit or -1. */
    private int tripAndStop(Level l, int reactFrames) {
        DeadfallTrap t = trap(l);
        int f = 0;
        while (t.phase() == DeadfallTrap.Phase.TIED && f++ < 60 * 10) step(l, true, false);
        for (int i = 0; i < reactFrames; i++, f++) step(l, true, false);
        while (t.phase() != DeadfallTrap.Phase.CAUGHT && f++ < 60 * 10) { step(l, false, false); if (!alive(l)) return -1; }
        while (!l.atRoomExit() && f++ < 60 * 30) { step(l, true, false); if (!alive(l)) return -1; }
        return l.atRoomExit() ? f : -1;
    }

    @Test void trippingTheVineThenStoppingWithinTwoThirdsOfASecondIsSafeWithEitherWarning() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            int lastSafe = -1;
            for (int react = 0; react <= 60; react++) { if (tripAndStop(room(), react) > 0) lastSafe = react; else break; }
            System.out.printf(java.util.Locale.ROOT, "deadfall: stopping up to %.2f s after the trip is safe (longer=%s)%n", lastSafe * STEP, longer);
            assertTrue(lastSafe * STEP >= 0.65f, "measured 0.68 s with either warning (#363): the stop must stay a generous answer");
            for (int react : new int[]{0, 18, 36}) {
                Level l = room();
                int frames = tripAndStop(l, react);
                assertTrue(frames > 0, "stopping " + react * STEP + " s after the trip must live and reach the exit, longer=" + longer);
                assertEquals(DeadfallTrap.Phase.CAUGHT, trap(l).phase());
                if (react == 0) System.out.printf(java.util.Locale.ROOT, "deadfall trip-and-stop route %.2f s (longer=%s)%n", frames * STEP, longer);
            }
        }
    }

    @Test void walkingBackFromTheTripIsAlsoSafe() {
        Level l = room();
        DeadfallTrap t = trap(l);
        while (t.phase() == DeadfallTrap.Phase.TIED) step(l, true, false);
        held.clear(); held.add(Input.Keys.A);
        while (t.phase() != DeadfallTrap.Phase.CAUGHT) { frame(l); assertTrue(alive(l)); }
        assertTrue(centre(l) < P_X - 56, "stepped back out of the band");
    }

    /** A hero who cannot be hurt yet (arriving, dying, resetting) is not seen: the vine does not trip. */
    @Test void aHeroWhoCannotBeHurtIsNotSeen() {
        DeadfallTrap t = new DeadfallTrap(h.assets(), h.game().trapWarnings(), P_X - DeadfallTrap.PIVOT_X, P_Y - DeadfallTrap.PIVOT_Y, false,
            () -> null, () -> fail("no kill"), quiet());
        for (int i = 0; i < 120; i++) t.update(STEP);
        assertEquals(DeadfallTrap.Phase.TIED, t.phase());
    }

    @Test void swingRightMirrorsTheVineTheTiedLogAndTheSnag() {
        float x = 1000, y = 100;
        DeadfallTrap t = new DeadfallTrap(h.assets(), h.game().trapWarnings(), x, y, true, () -> null, () -> { }, quiet());
        float p = x + DeadfallTrap.BOUGH_WIDTH - DeadfallTrap.PIVOT_X;
        assertEquals(p, t.pivotX(), 0f);
        assertEquals(new Rectangle(p + 104, t.floor() - 5, 16, 5), t.vineBox());
        Rectangle tied = t.logBoxAt(DeadfallTrap.TIED_DEGREES, new Rectangle());
        assertTrue(tied.x + tied.width < p, "tied on the left, the far side for a hero coming from the right");
        assertEquals(p + DeadfallTrap.SNAG_REACH, t.snagX(), 0f);
    }

    // -- reset and pause --

    @Test void retryAndRestartLevelRebuildItTied() {
        Level l = room();
        while (alive(l)) step(l, true, false);
        assertNotEquals(DeadfallTrap.Phase.TIED, trap(l).phase());
        l.resetCurrentRoom(player());
        frame(l);
        assertEquals(DeadfallTrap.Phase.TIED, trap(l).phase(), "retry rebuilds the room: tied, the vine whole");
        assertEquals(DeadfallTrap.Phase.TIED, trap(room()).phase(), "Restart Level builds the room again");
    }

    @Test void pausingStopsTheWarningAndResumingContinuesFromTheSameState() {
        // KimeriaGame stops calling Level.update while paused, so a pause is a run of frames with no update.
        Level paused = room(), straight = room();
        place(paused, VINE_LEFT); place(straight, VINE_LEFT);
        for (int i = 0; i < 20; i++) { frame(paused); frame(straight); }
        float before = trap(paused).warningRemaining();
        assertEquals(DeadfallTrap.Phase.WARNING, trap(paused).phase());
        assertEquals(before, trap(paused).warningRemaining(), 0f, "nothing advanced while paused");
        for (int i = 0; i < 20; i++) { frame(paused); frame(straight); }
        assertEquals(trap(straight).warningRemaining(), trap(paused).warningRemaining(), 1e-6f, "resumed exactly where it stopped");
    }

    // -- the load check --

    private static TiledObject obj(String type, float x, float y, float w, float h) {
        TiledObject o = new TiledObject();
        o.type = type; o.x = x; o.y = y; o.width = w; o.height = h;
        return o;
    }

    private String problem(TiledObject deadfall, List<TiledObject> collisions, Map<String, Rectangle> standing) {
        return DeadfallTrap.problem(deadfall, collisions, new ArrayList<>(), new Rectangle(0, 0, 640, 368), standing);
    }

    @Test void theLoadCheckRefusesABadSizeSwingFloorObstacleOrStandingPlace() {
        TiledObject ok = obj("Deadfall", 288, 243, 112, 24); // P.x 336, floor 320
        List<TiledObject> floor = new ArrayList<>(List.of(obj("", 0, 320, 640, 48)));
        Map<String, Rectangle> standing = new LinkedHashMap<>();
        standing.put("entrance", new Rectangle(50, 298, BODY_W, BODY_H));
        standing.put("exit", new Rectangle(576, 272, 32, 48));
        assertNull(problem(ok, floor, standing));
        assertTrue(problem(obj("Deadfall", 288, 243, 96, 24), floor, standing).contains("112x24"));
        ok.properties.put("swing", "up");
        assertTrue(problem(ok, floor, standing).contains("swing 'up'"));
        ok.properties.put("swing", "left");
        assertNull(problem(ok, floor, standing));
        List<TiledObject> pit = List.of(obj("", 0, 320, 300, 48), obj("", 340, 320, 300, 48));
        assertTrue(problem(ok, pit, standing).contains("solid floor"), "a pit under the swing");
        List<TiledObject> sand = List.of(obj("", 0, 320, 640, 48), obj("QuickSand", 150, 320, 40, 48));
        assertNull(problem(ok, sand, standing), "quicksand is neither floor nor an obstacle here; the block under it is the floor");
        List<TiledObject> pillar = new ArrayList<>(floor); pillar.add(obj("", 390, 280, 16, 40));
        assertTrue(problem(ok, pillar, standing).contains("swing"), "a pillar in the swing");
        List<TiledObject> low = new ArrayList<>(floor); low.add(obj("", 180, 240, 48, 16));
        assertTrue(problem(ok, low, standing).contains("free height"), "a low ceiling over the vine");
        Map<String, Rectangle> onVine = new LinkedHashMap<>(standing);
        onVine.put("entrance", new Rectangle(220, 298, BODY_W, BODY_H));
        assertTrue(problem(ok, floor, onVine).contains("trip vine under the room entrance"));
        Map<String, Rectangle> exitInBand = new LinkedHashMap<>(standing);
        exitInBand.put("exit", new Rectangle(330, 272, 32, 48));
        assertTrue(problem(ok, floor, exitInBand).contains("through the room exit"));
        assertTrue(DeadfallTrap.problem(ok, floor, new ArrayList<>(), new Rectangle(200, 0, 440, 368), standing).contains("inside its room"));
    }

    private static DeadfallTrap.Listener quiet() {
        return new DeadfallTrap.Listener() {
            @Override public void tripped(DeadfallTrap trap) { }
            @Override public void caught(DeadfallTrap trap) { }
        };
    }
}
