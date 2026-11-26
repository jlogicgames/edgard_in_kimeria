package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.BeetleFx;
import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import com.jlogicsoftware.kimeria.entity.objects.EmberFissure;
import com.jlogicsoftware.kimeria.entity.objects.StaticPlatform;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The teaching room of the ember fissure, {@code forest-fissure} (#366, D91, D72): room 2 of forest-2, after
 * {@code forest-walls}. A fissure on open floor met alone, then a second whose jet pierces a one-way branch on the step up to
 * the exit ledge. Scripted heroes with the ordinary controls measure the mastered route (D19) over every start offset with
 * and without the assist (D26: timing alone), a careless walker, and the retry. On the ledge a beetle guards the exit (#327,
 * D100): the lure, the fireball and the walk into it are measured and pinned to 0.1 s (D68). World Y grows downward.
 */
class ForestFissureTest {
    private static final float STEP = 1f / 60f;
    private static final int FISSURE_ROOM = 2;
    private static final float FLOOR = 304f, OFFSET_STEP = 0.05f, CYCLE = 3.5f;
    private static final int ROUTE_LIMIT_FRAMES = 60 * 30;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() throws Exception { h.setUp(); setLonger(false); }

    @AfterEach void restoreGlobals() throws Exception { setLonger(false); h.restoreGlobals(); }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    /** entry, walls, then the fissure room (#376, #366). */
    private Level room() {
        Level level = h.roomOf("forest-2", FISSURE_ROOM);
        assertEquals("forest-fissure", level.currentRoomName());
        return level;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private boolean alive(Level level) { return !level.touchesDeadline(level.player.hitboxBounds()) && !level.player.isGotHit(); }

    // -- the room --

    @Test void theRoomHoldsTwoFissuresTheSecondPiercingTheBranchAndBothInViewFromTheEntrance() {
        Level level = room();
        List<EmberFissure> fissures = ForestFissureRoute.fissures(level);
        assertEquals(2, fissures.size());
        EmberFissure floor = fissures.get(0), branch = fissures.get(1);
        Rectangle column = new Rectangle();
        for (EmberFissure f : fissures) {
            assertEquals(FLOOR, f.floorY(), 0.01f, "each stands on the floor line (the SpawnPoints layer is drawn 16 px up)");
            assertEquals(EmberFissure.DEFAULT_HEIGHT, f.columnHeight(), 0.01f, "nothing solid cuts either column");
            assertEquals(EmberFissure.DEFAULT_PERIOD_SECONDS, f.period(), 0.001f);
        }
        branch.columnInto(column);
        assertTrue(column.y < ForestFissureRoute.BRANCH_TOP, "the branch fissure's jet passes up through the branch");
        assertEquals(ForestFissureRoute.LEDGE_TOP, column.y, 0.01f, "and reaches the ledge's top");
        StaticPlatform over = level.staticPlatforms().stream().filter(p -> p.getX() <= branch.centerX() && branch.centerX() <= p.getX() + p.getWidth())
            .findFirst().orElseThrow();
        assertEquals(ForestFissureRoute.BRANCH_TOP, over.getY(), 0.01f, "a one-way branch stands over the second fissure");
        assertTrue(level.staticPlatforms().stream().noneMatch(p -> p.getX() <= floor.centerX() && floor.centerX() <= p.getX() + p.getWidth()),
            "the first fissure is met alone, on open floor");
        Rectangle room = level.currentRoomBounds();
        assertEquals(640f, room.width, 0.01f, "one screen: both fissures are in view from the entrance");
        assertEquals(2, level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.objects.LethalHazard).count(), "no other trap");
        assertEquals(1, level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Enemy<?>).count(),
            "one monster: the beetle on the ledge (#327)");
    }

    // -- the route --

    /** The mastered route after {@code delay} s of standing at the entrance: seconds to the exit after the delay, or -1 on a death. */
    private float route(Level level, float delay) {
        held.clear();
        settle(level, Math.round(delay * 60));
        ForestFissureRoute route = new ForestFissureRoute(level);
        int frames = 0;
        while (!level.atRoomExit()) {
            held.clear();
            route.drive(level, held, pressed);
            frame(level);
            if (!alive(level)) { held.clear(); return -1; }
            assertTrue(++frames < ROUTE_LIMIT_FRAMES, "stuck at " + level.player.position);
        }
        held.clear();
        return frames * STEP;
    }

    @Test void theMasteredRouteIsMeasuredAndEveryStartOffsetReachesTheExitWithAndWithoutTheAssist() throws Exception {
        float first = route(room(), 0f);
        assertTrue(first > 0, "the mastered route from the entrance");
        float slowest = 0, fastest = Float.MAX_VALUE;
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (float delay = 0f; delay < CYCLE; delay += OFFSET_STEP) {
                float t = route(room(), delay);
                assertTrue(t > 0, "longer=" + longer + " delay " + delay + ": died");
                slowest = Math.max(slowest, t); fastest = Math.min(fastest, t);
            }
        }
        System.out.println("forest-fissure route: from the entrance " + first + " s; over start offsets 0-3.5 s " + fastest + " to " + slowest + " s");
        assertEquals(LURE_S, first, PIN_MARGIN);
        assertEquals(ROUTE_FASTEST_S, fastest, PIN_MARGIN, "the mastered route over start offsets, fastest (pinned, #327)");
        assertEquals(ROUTE_SLOWEST_S, slowest, PIN_MARGIN, "and slowest");
        assertTrue(slowest < 20f, "D19: the mastered route stays about 20 s at worst (" + slowest + ")");
    }

    @Test void aHeroWhoIgnoresTheFissuresDiesSomewhereAndTheRetryCostsAtMostTheRoom() {
        int deaths = 0, passes = 0;
        float latest = 0;
        for (float delay = 0f; delay < CYCLE; delay += OFFSET_STEP) {
            Level level = room();
            float floorColumn = ForestFissureRoute.fissures(level).get(0).centerX() + EmberFissure.JET_WIDTH;
            held.clear();
            settle(level, Math.round(delay * 60));
            int frames = 0;
            while (alive(level) && level.player.hitboxBounds().x < floorColumn && frames < 60 * 10) {
                held.clear(); held.add(Input.Keys.D);
                frame(level); frames++;
            }
            held.clear();
            if (alive(level)) passes++;
            else { deaths++; latest = Math.max(latest, delay + frames * STEP); }
        }
        System.out.println("forest-fissure careless walk past the floor fissure: " + passes + " passed, " + deaths + " died, latest death "
            + latest + " s after entering");
        assertTrue(deaths > 0, "the jet kills a careless walker");
        assertTrue(passes > deaths, "the open window (2.5 s of 3.5) is most of the cycle");
        assertTrue(latest < 20f, "a death replays at most the room");
    }

    @Test void aRetryRebuildsBothFissuresAtTheirAuthoredPhase() {
        Level level = room();
        List<EmberFissure> before = ForestFissureRoute.fissures(level);
        float[] fresh = {before.get(0).cycleTime(), before.get(1).cycleTime()};
        assertNotEquals(fresh[0], fresh[1], 0.01f, "the two fissures are out of step");
        settle(level, 100);
        level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(h.assets(), h.game(), 0, 0));
        List<EmberFissure> after = ForestFissureRoute.fissures(level);
        assertEquals(fresh[0], after.get(0).cycleTime(), 0.001f);
        assertEquals(fresh[1], after.get(1).cycleTime(), 0.001f);
    }

    // -- the beetle on the ledge (#327, D100, D101) --

    /** The beetle (24 x 16, map x 3508, offNeg 0, offPos 2): its centre crawls x 3520-3552, so its blast covers at most x 3480-3592. */
    private static final float BEETLE_X = 3508, BEETLE_CENTRE_MIN = 3520, BEETLE_CENTRE_MAX = 3552;
    private static final float LEDGE_LIP = 3424, EXIT_X = 3600, LIP_CENTRE = 3440;
    /**
     * Pinned measurements (#327), scripted hero at 60 frames per second, held to {@link #PIN_MARGIN} (D68): the lure and the
     * fireball from the entrance; the death in the burst from the entrance at start offset 0 and at worst over the offsets
     * (seconds from entering, the wait included); the mastered route over the offsets, fastest and slowest.
     */
    private static final float LURE_S = 9.30f, FIREBALL_S = 8.60f, DEATH_S = 8.35f, DEATH_WORST_S = 11.70f, DEATH_WORST_AFTER_WAIT_S = 8.75f, ROUTE_FASTEST_S = 6.40f,
        ROUTE_SLOWEST_S = 10.27f;
    private static final float PIN_MARGIN = 0.1f;

    private float heroRight(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width; }

    /** Seconds from the entrance to the exit on the shared route ({@code fire}: one charge spent from the lip), or -1 on a death. */
    private float ledgeRoute(Level level, boolean fire) {
        held.clear();
        ForestFissureRoute route = new ForestFissureRoute(level, fire);
        int frames = 0;
        while (!level.atRoomExit()) {
            held.clear();
            route.drive(level, held, pressed);
            frame(level);
            if (!alive(level)) { held.clear(); return -1; }
            assertTrue(++frames < ROUTE_LIMIT_FRAMES, "stuck at " + level.player.position);
        }
        held.clear();
        return frames * STEP;
    }

    @Test void theBeetleCrawlsTheLedgeAndItsBurstNeverReachesTheLipTheBranchOrTheExit() {
        Level level = room();
        Beetle b = ForestFissureRoute.beetle(level);
        assertNotNull(b);
        assertEquals(BEETLE_X, b.getX(), 0.01f);
        assertEquals(ForestFissureRoute.LEDGE_TOP, b.floorY(), 0.01f, "it crawls on the ledge (the SpawnPoints layer is drawn 16 px up)");
        assertEquals(BEETLE_CENTRE_MIN, b.rangeNeg() + Beetle.FRAME_WIDTH / 2, 0.01f);
        assertEquals(BEETLE_CENTRE_MAX, b.rangePos() + Beetle.FRAME_WIDTH / 2, 0.01f);
        RoomLayout.Room room = level.roomLayout.rooms.get(FISSURE_ROOM);
        Rectangle exit = room.exit();
        assertEquals(EXIT_X, exit.x, 0.01f, "the exit moved 16 px right, against the wall (#327)");
        // Every blast position, from the range and the burst's real size: the leftmost and rightmost boxes and their sweep.
        Rectangle left = new Rectangle(), right = new Rectangle();
        b.position.x = b.rangeNeg(); b.blastBoxInto(left);
        b.position.x = b.rangePos(); b.blastBoxInto(right);
        b.position.x = BEETLE_X;
        assertEquals(BeetleFx.BLAST_WIDTH, left.width, 0.01f);
        assertEquals(BeetleFx.BLAST_HEIGHT, left.height, 0.01f);
        Rectangle sweep = new Rectangle(left.x, left.y, right.x + right.width - left.x, left.height);
        assertFalse(sweep.overlaps(exit), "no blast reaches the exit: " + sweep + " vs " + exit);
        Rectangle entrance = new Rectangle(room.entrance().x, room.entrance().y, 48, 48);
        assertFalse(sweep.overlaps(entrance), "nor the entrance");
        assertTrue(exit.x - (sweep.x + sweep.width) >= 8, "8 px clear of the exit: " + (sweep.x + sweep.width));
        assertTrue(sweep.x - LEDGE_LIP >= 48, "the ledge's lip, from x " + LEDGE_LIP + " to " + sweep.x + ", is outside every blast");
        Rectangle trigger = new Rectangle();
        b.position.x = b.rangeNeg(); b.triggerBoxInto(trigger); b.position.x = BEETLE_X;
        assertTrue(trigger.x >= sweep.x, "its trigger never reaches further left than its blast");
        float heroOnBranchTop = ForestFissureRoute.BRANCH_TOP - level.player.hitbox.height;
        assertTrue(heroOnBranchTop >= sweep.y + sweep.height, "a hero on the branch is below the blast, which ends at the ledge's top: "
            + heroOnBranchTop);
        for (int i = 0; i < 60 * 8; i++) {
            frame(level);
            assertTrue(alive(level));
            assertTrue(b.centreX() >= BEETLE_CENTRE_MIN - 0.01f && b.centreX() <= BEETLE_CENTRE_MAX + 0.01f, "inside its range: " + b.centreX());
        }
        assertEquals(Beetle.State.CRAWL, b.state(), "a hero at the entrance is never heard");
    }

    @Test void aHeroOnTheLipIsHeardButNeverHurtAndTheBeetleWaitsFacingHim() {
        Level level = room();
        Beetle b = ForestFissureRoute.beetle(level);
        level.player.position.set(LIP_CENTRE - level.player.hitbox.offsetX - level.player.hitbox.width / 2f, ForestFissureRoute.LEDGE_TOP - 48);
        settle(level, 2);
        boolean noticed = false;
        for (int i = 0; i < 60 * 10; i++) {
            frame(level);
            assertTrue(alive(level));
            noticed |= b.state() == Beetle.State.NOTICE;
            assertNotEquals(Beetle.State.WINDUP, b.state(), "it never reaches a hero on the lip");
        }
        assertTrue(noticed, "it hears him on the lip and shows it in its body first");
        assertEquals(Beetle.State.CHASE, b.state());
        assertEquals(BEETLE_CENTRE_MIN, b.centreX(), 0.5f, "it waits at its range end, facing him");
    }

    @Test void theLureIsTheMasteredRouteAndCostsNoCharge() {
        Level level = room();
        Beetle b = ForestFissureRoute.beetle(level);
        float t = ledgeRoute(level, false);
        assertTrue(t > 0, "the lure reaches the exit");
        assertTrue(b.isRemoved(), "it burst on the ledge, behind the hero on the lip");
        System.out.printf(Locale.ROOT, "forest-fissure, the lure from the entrance to the exit: %.2f s%n", t);
        assertEquals(LURE_S, t, PIN_MARGIN);
    }

    @Test void aFireballFromTheLipKillsItQuietlyAndTheHeroWalksPastItsBody() {
        Level level = room();
        level.rangedStock.restore(1);
        Beetle b = ForestFissureRoute.beetle(level);
        float t = ledgeRoute(level, true);
        assertTrue(t > 0, "the shot reaches the exit");
        assertEquals(0, level.rangedStock.count(), "one charge spent");
        assertTrue(b.isRemains(), "a quiet kill leaves its body on the ledge (D101)");
        System.out.printf(Locale.ROOT, "forest-fissure, a fireball from the lip, entrance to exit: %.2f s%n", t);
        assertEquals(FIREBALL_S, t, PIN_MARGIN);
    }

    /**
     * The mastered climb after {@code delay} s at the entrance, then on the ledge a hero who walks at the beetle and stops
     * beside it the moment it commits. Seconds from entering (the wait included) to the death, or -1 if he lives.
     */
    private float stopBesideTheBeetle(float delay) {
        Level level = room();
        held.clear();
        settle(level, Math.round(delay * 60));
        Beetle b = ForestFissureRoute.beetle(level);
        ForestFissureRoute route = new ForestFissureRoute(level);
        int frames = Math.round(delay * 60);
        boolean onLedge = false;
        while (alive(level) && !level.atRoomExit()) {
            held.clear();
            Rectangle body = level.player.hitboxBounds();
            onLedge |= level.player.isOnGround() && body.y + body.height <= ForestFissureRoute.LEDGE_TOP + 0.5f;
            if (!onLedge) route.drive(level, held, pressed);
            else if (!b.isCommitted() && !b.isRemoved()) held.add(Input.Keys.D);
            frame(level);
            assertTrue(++frames < ROUTE_LIMIT_FRAMES + 60 * CYCLE, "stuck at " + level.player.position);
        }
        held.clear();
        return alive(level) ? -1 : frames * STEP;
    }

    /**
     * The card's retry cost, on the old card's basis (the worst climb, then the death): the mastered climb after every start
     * offset of one fissure cycle (0.05 s steps), then a stop beside the beetle when it commits. At offset 0 and at worst, both
     * from entering (the wait included) and from the end of the wait (the basis of the route's offset times above).
     */
    @Test void aDeathInTheBeetlesBurstAfterTheMasteredClimbIsPinnedAtOffsetZeroAndAtWorst() {
        float first = stopBesideTheBeetle(0f);
        assertTrue(first > 0, "standing beside it when it bursts kills");
        float worst = 0, worstAfterWait = 0;
        for (float delay = 0f; delay < CYCLE; delay += OFFSET_STEP) {
            float t = stopBesideTheBeetle(delay);
            assertTrue(t > 0, "delay " + delay + ": standing beside it kills");
            worst = Math.max(worst, t);
            worstAfterWait = Math.max(worstAfterWait, t - Math.round(delay * 60) * STEP);
        }
        System.out.printf(Locale.ROOT, "forest-fissure, death in the beetle's burst after the mastered climb: %.2f s from the entrance at "
            + "offset 0; at worst over offsets 0-3.5 s %.2f s from entering, %.2f s after the wait%n", first, worst, worstAfterWait);
        assertEquals(DEATH_S, first, PIN_MARGIN);
        assertEquals(DEATH_WORST_S, worst, PIN_MARGIN, "from entering, the wait at the entrance included");
        assertEquals(DEATH_WORST_AFTER_WAIT_S, worstAfterWait, PIN_MARGIN, "from the end of the wait: the basis of the route's offset times");
    }

    /** Puts the hero standing on the ledge's lip after {@code wait} s of the beetle crawling, then walks him right to the exit: true if he lives. */
    private boolean walkFromTheLip(float wait) {
        Level level = room();
        held.clear();
        settle(level, Math.round(wait * 60));
        level.player.position.set(LIP_CENTRE - level.player.hitbox.offsetX - level.player.hitbox.width / 2f, ForestFissureRoute.LEDGE_TOP - 48);
        int frames = 0;
        while (alive(level) && !level.atRoomExit()) {
            held.clear(); held.add(Input.Keys.D); frame(level);
            assertTrue(++frames < 60 * 10, "stuck at " + level.player.position);
        }
        held.clear();
        return alive(level);
    }

    /**
     * Walking straight at it and on is safe, as in forest-1: once it commits he walks 80 px in the 0.8 s wind-up, the blast's
     * whole width, so it bursts behind him; only a stop beside it kills. One full crawl cycle (32 px each way at 24 px/s,
     * 2.67 s) in 0.1 s steps, with and without the assist.
     */
    @Test void walkingStraightPastItReachesTheExitAtEveryCrawlPhase() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (float wait = 0f; wait < 2.7f; wait += 0.1f) assertTrue(walkFromTheLip(wait), "longer=" + longer + " wait " + wait + ": died");
        }
    }

    @Test void aRetryRebuildsTheBeetleAtItsSpawn() {
        Level level = room();
        Beetle before = ForestFissureRoute.beetle(level);
        before.burst();
        settle(level, 2);
        level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(h.assets(), h.game(), 0, 0));
        Beetle after = ForestFissureRoute.beetle(level);
        assertNotNull(after, "a retry brings the beetle back");
        assertEquals(BEETLE_X, after.getX(), 0.01f);
        assertEquals(Beetle.State.CRAWL, after.state());
    }
}
