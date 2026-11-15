package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.RangedStock;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.items.FireballRefill;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest-1 route room (#187, D66): one lever drives a ladder and a lift in opposite states. The lift is the
 * mandatory way to the checkpoint ledge and runs from the start; pulling the lever stops it and lowers the ladder to
 * the optional ledge that holds a fireball refill. Ordinary keyboard input drives the hero through the real shipped
 * map. Times are author (mastered) measurements of a scripted hero, not novice playtests. Recipe: docs/ROOMS.md.
 *
 * <p>Y grows downward: the floor top is y 320, the refill ledge 112 px high has its top at y 208 and the lift runs between
 * y 304 (bottom) and y 176 (top); the exit ledge's top is y 160, 16 px above the lift's top. Everything below is a map coordinate (room 2 starts at x 1280).
 */
class Forest1RouteTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float ROOM_X = 1280, ROOM_END = 2048, START_X = 1344, FLOOR = 320;
    private static final float LEVER_X = 1440, LADDER_X = 1584, SLAB_X = 1600, SLAB_END = 1680, SLAB_TOP = 208, REFILL_X = 1632;
    private static final float LIFT_X = 1872, LIFT_RIGHT = 1904, BOTTOM = 304, TOP = 176, LEDGE_X = 1904, LEDGE_TOP = 160, CHECKPOINT_X = 1984;
    /** The budget for a mastered route (D19, ROOMS.md "Replay budget"): about 20 s. */
    private static final float BUDGET = 20f;
    /** The scripted rider hops onto the exit ledge once the lift is this high (the ledge is 16 px above its top, a hop is about 58 px). */
    private static final float RIDE_JUMP_Y = 200;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;
    private RangedStock stock;

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Player player() { return new Player(h.assets(), h.game(), 0, 0); }

    /** The whole level from a restart, with no charges (the required route needs none), standing at the route room's entrance. */
    private Level room() {
        stock = new RangedStock();
        stock.restore(0);
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new, stock);
        level.enterNextRoom(player());
        return level;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private Ladder ladder(Level level) { return level.ladders.get(0); }
    private Escalator lift(Level level) { return level.escalators.get(0); }
    private Lever lever(Level level) { return level.objects.stream().filter(Lever.class::isInstance).map(Lever.class::cast).findFirst().orElseThrow(); }
    private float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }
    private float centerX(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }
    private boolean riding(Level level) { return level.player.currentEscalator() == lift(level); }
    private boolean won(Level level) { return level.player.getState() == Player.State.DISAPPEARING; }
    private void alive(Level level) { assertFalse(level.player.isGotHit(), "died at " + level.player.position); }
    private boolean refillGone(Level level) { return level.objects.stream().noneMatch(o -> o instanceof FireballRefill && !o.isRemoved()); }
    private void jump() { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }

    /** Up + Action, as the lesson rooms teach: Action is a fresh press while Up is held. */
    private int operate(Level level) {
        held.clear(); held.add(Input.Keys.W); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
        held.clear(); frame(level);
        return 3;
    }

    private int walkToLever(Level level) {
        int frames = 0;
        while (!level.player.canInteract()) {
            held.clear(); held.add(centerX(level) > LEVER_X + 8 ? Input.Keys.A : Input.Keys.D);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 30, "lever never came into reach at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** From the lever (or anywhere on the floor): to the exit ledge by the lift, with ordinary keys; the lever is not touched. */
    private int rideToCheckpoint(Level level) {
        int frames = 0;
        while (!won(level)) {
            held.clear(); held.add(Input.Keys.D);
            boolean overLift = centerX(level) >= LIFT_X + 4;
            if (!riding(level) && feet(level) > 300 && overLift && lift(level).getY() >= 292) jump();
            if (riding(level) && lift(level).getY() <= RIDE_JUMP_Y && level.player.isOnGround()) jump(); // up onto the ledge, 16 px above the lift's top
            frame(level); alive(level);
            assertTrue(++frames < 60 * 40, "checkpoint unreachable from " + level.player.position + " lift y=" + lift(level).getY());
        }
        held.clear();
        return frames;
    }

    /** The mandatory route from a standing start at the entrance: walk, ride, walk. */
    private int mandatory(Level level) { return rideToCheckpoint(level); }

    /** Walks Right until the body is over the ladder's rungs. */
    private int walkToLadder(Level level) {
        int frames = 0;
        while (centerX(level) < LADDER_X + 2) {
            held.clear(); held.add(Input.Keys.D);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 20, "ladder unreachable from " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** Holds Up until the hero stands on the ledge slab. */
    private int climb(Level level) {
        int frames = 0;
        boolean grabbed = false;
        while (!(grabbed && !level.player.onLadder() && feet(level) <= SLAB_TOP + 1)) {
            held.clear(); held.add(Input.Keys.W);
            frame(level);
            grabbed |= level.player.onLadder();
            assertTrue(++frames < 60 * 20, "climb stuck at " + level.player.position + " ladderY=" + ladder(level).getY());
        }
        held.clear(); frame(level);
        return frames;
    }

    /** Walks right along the slab until the refill is collected, then keeps going until the hero has dropped to the floor. */
    private int takeTheRefillAndDrop(Level level) {
        int frames = 0;
        while (!(refillGone(level) && feet(level) > FLOOR - 2)) {
            held.clear(); held.add(Input.Keys.D);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 20, "refill route stuck at " + level.player.position);
        }
        held.clear(); frame(level);
        return frames;
    }

    /** The optional detour from a standing start: lever, ladder, refill, back to the lever, lever again; the hero ends by the lever with the lift running. */
    private int detour(Level level) {
        int frames = walkToLever(level);
        frames += operate(level);
        assertTrue(ladder(level).isLowered() && !lift(level).isRunning());
        frames += walkToLadder(level);
        frames += climb(level);
        frames += takeTheRefillAndDrop(level);
        frames += walkToLever(level);
        frames += operate(level);
        assertTrue(!ladder(level).isLowered() && lift(level).isRunning());
        return frames;
    }

    /** From wherever the hero stands (floor, slab or ladder foot) to the checkpoint, flipping the lever back if the lift is stopped. */
    private int recover(Level level) {
        int frames = 0;
        if (level.player.onLadder() || feet(level) <= SLAB_TOP + 2 && centerX(level) < SLAB_END + 40) {
            // On the ledge (or its ladder): walk right off the end of the slab.
            while (feet(level) < FLOOR - 2 || level.player.onLadder()) {
                held.clear(); held.add(level.player.onLadder() ? Input.Keys.S : Input.Keys.D);
                frame(level); alive(level);
                assertTrue(++frames < 60 * 20, "cannot leave the ledge from " + level.player.position);
            }
            held.clear(); settle(level, 5);
        }
        frames += walkToLever(level);
        if (!lift(level).isRunning()) frames += operate(level);
        assertTrue(lift(level).isRunning());
        return frames + rideToCheckpoint(level);
    }

    // ---- census and links ----

    @Test void theRoomOffersOneLeverOneRaisedLadderOneRunningLiftAndOneRefillAndNothingElse() {
        Level level = room();
        assertEquals("forest-1-route", level.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, 768, 368), level.currentRoomBounds());
        assertEquals(1, level.triggers.size());
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.ladders.size());
        assertEquals(1, level.escalators.size());
        assertEquals(1, level.objects.stream().filter(FireballRefill.class::isInstance).count());
        assertTrue(level.fallingPlatforms.isEmpty());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Wall));
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof Enemy<?>), "no monsters: the combination is #104");
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.objects.LethalHazard), "no hazards in the route room");
        assertEquals(1, level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.environment.Checkpoint.class::isInstance).count());
        // Authored state: the ladder is raised, the lift runs from the bottom, the lever shows "down".
        assertFalse(ladder(level).isLowered());
        assertTrue(lift(level).isRunning());
        assertTrue(lift(level).isVertical);
        assertEquals(BOTTOM, lift(level).getY(), 0.01f);
        assertEquals(LIFT_X, lift(level).getX(), 0.01f);
        assertFalse(lever(level).isUp());
        assertEquals(LADDER_X, ladder(level).getX(), 0.01f);
        assertEquals(96, ladder(level).topY(), 0.01f);
        assertEquals(208, ladder(level).bottomY(), 0.01f, "raised: its foot hangs 112 px above the floor");
        FireballRefill refill = level.objects.stream().filter(FireballRefill.class::isInstance).map(FireballRefill.class::cast).findFirst().orElseThrow();
        assertEquals(REFILL_X, refill.getX(), 0.01f);
        assertEquals(SLAB_TOP, refill.getY() + refill.getHeight(), 0.01f, "the refill sits on the ledge slab");
        assertEquals(START_X, level.player.position.x, 0.01f);
    }

    @Test void theMapKeepsNoHintsAndTheCheckpointStandsOnTheExitLedge() {
        Level level = room();
        var checkpoint = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.environment.Checkpoint.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.environment.Checkpoint.class::cast).findFirst().orElseThrow();
        assertEquals(CHECKPOINT_X, checkpoint.getX(), 0.01f);
        assertEquals(LEDGE_TOP, checkpoint.getY() + checkpoint.getHeight(), 0.01f, "it stands on the exit ledge's top, not in the air");
        assertTrue(checkpoint.getX() - LEDGE_X >= 64, "the checkpoint is well in from the ledge edge");
        assertEquals("", level.player.zoneHint());
    }

    @Test void oneLeverDrivesTheLadderAndTheLiftInOppositeStatesAndShowsIt() {
        Level level = room();
        walkToLever(level);
        for (int pull = 1; pull <= 5; pull++) {
            operate(level);
            boolean odd = pull % 2 == 1;
            assertEquals(odd, ladder(level).isLowered(), "pull " + pull + ": the ladder follows the lever");
            assertEquals(!odd, lift(level).isRunning(), "pull " + pull + ": the lift is the opposite");
            assertEquals(odd, lever(level).isUp(), "the lever shows the ladder");
            settle(level, 60);
        }
        // The lowered ladder reaches the floor from the ledge's top (about 0.7 s at 160 px/s).
        assertTrue(ladder(level).isLowered());
        settle(level, 60);
        assertEquals(SLAB_TOP, ladder(level).topY(), 0.01f);
        assertEquals(FLOOR, ladder(level).bottomY(), 0.01f);
    }

    @Test void theLeverIsOnlyEverInReachOnTheFloorFarFromTheLadderAndTheLift() {
        for (boolean lowered : new boolean[]{false, true}) {
            Level level = room();
            if (lowered) { walkToLever(level); operate(level); settle(level, 60); }
            assertEquals(lowered, ladder(level).isLowered());
            for (float x = ROOM_X + 24; x < CHECKPOINT_X - 24; x += 4) {
                for (float y : new float[]{272, SLAB_TOP - 48, LEDGE_TOP - 48}) {
                    level.player.position.set(x - 24, y);
                    settle(level, 2);
                    if (level.player.canInteract()) {
                        assertTrue(feet(level) > FLOOR - 4, "the lever is only in reach standing on the floor: x=" + x + " y=" + y);
                        assertTrue(Math.abs(centerX(level) - LEVER_X) < 40 && Math.abs(centerX(level) - LADDER_X) > 100, "x=" + x);
                    }
                }
            }
        }
    }

    // ---- the optional route is closed without the lever ----

    @Test void withTheLadderRaisedNeitherWalkingNorJumpingNorTheLiftReachesTheRefillLedge() {
        Level level = room();
        for (float x = ROOM_X + 24; x <= ROOM_END - 32; x += 8) {
            level.player.position.set(x - 24, 272);
            for (int i = 0; i < 60 * 2; i++) {
                held.clear(); held.add(Input.Keys.D); held.add(Input.Keys.W);
                if (i % 30 == 0) jump();
                frame(level);
                if (level.player.getState() == Player.State.DISAPPEARING) break;
                assertFalse(refillGone(level), "refill collected without the lever from x=" + x);
                assertFalse(centerX(level) > SLAB_X && centerX(level) < SLAB_END && feet(level) <= SLAB_TOP + 4,
                    "hero got onto the refill ledge unaided from x=" + x + " at " + level.player.position);
            }
        }
    }

    @Test void theLiftNeverCarriesTheHeroToTheRefillLedgeEitherWayRound() {
        // From the lift's top the ledge is 192 px away and 32 px lower; from the exit ledge 224 px away at the same height.
        for (float fromX : new float[]{LIFT_X + 4, LIFT_RIGHT + 8, LEDGE_X + 16}) {
            for (float topY : new float[]{TOP, LEDGE_TOP}) {
                Level level = room();
                level.player.position.set(fromX - 24, topY - 48);
                settle(level, 3);
                for (int i = 0; i < 60 * 3; i++) {
                    held.clear(); held.add(Input.Keys.A);
                    if (i % 20 == 0) jump();
                    frame(level);
                    assertFalse(centerX(level) > SLAB_X - 16 && centerX(level) < SLAB_END + 16 && feet(level) <= SLAB_TOP + 4,
                        "the hero crossed to the refill ledge from " + fromX + "," + topY);
                    assertFalse(refillGone(level));
                }
            }
        }
    }

    @Test void withTheLiftStoppedAtAnyHeightTheExitLedgeIsNotReachableWithoutTheLever() {
        for (int step = 0; step <= 52; step++) {
            float delay = step * 0.1f;
            Level level = room();
            walkToLever(level);
            settle(level, Math.round(delay * 60));
            operate(level); // stops the lift wherever it is
            assertFalse(lift(level).isRunning());
            settle(level, 60);
            float stopped = lift(level).getY();
            assertTrue(stopped >= TOP - 1 && stopped <= BOTTOM + 1);
            for (float x = ROOM_X + 24; x <= LEDGE_X - 8; x += 12) {
                level.player.position.set(x - 24, 272);
                for (int i = 0; i < 60 * 2; i++) {
                    held.clear(); held.add(Input.Keys.D);
                    if (i % 30 == 0) jump();
                    frame(level);
                    assertFalse(won(level), "stopped lift at y=" + stopped + ": the exit was reached without the lever from x=" + x);
                    assertFalse(centerX(level) >= LEDGE_X && feet(level) <= LEDGE_TOP + 4, "up on the exit ledge unaided, lift y=" + stopped + " x=" + x);
                }
            }
        }
    }

    // ---- the required route ----

    @Test void theRequiredRouteReachesTheCheckpointWithZeroChargesInUnderTwentySecondsAndNeverTouchesTheLever() {
        Level level = room();
        int frames = mandatory(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 route room required: %.2f s%n", seconds);
        assertTrue(won(level));
        assertEquals(0, stock.count(), "no charges used or needed");
        assertTrue(level.objects.stream().noneMatch(Fireball.class::isInstance));
        assertTrue(lift(level).isRunning() && !ladder(level).isLowered(), "the lever was never touched");
        assertTrue(seconds <= BUDGET, "required route " + seconds + " s");
    }

    @Test void theRequiredRouteTakesTheSameTimeOnThreeAttemptsAfterRetriesAndTheRefillIsLeftBehind() {
        Level level = room();
        float first = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) { level.resetCurrentRoom(player()); settle(level, 0); }
            held.clear(); pressed.clear();
            float seconds = mandatory(level) * STEP;
            if (attempt == 0) first = seconds; else assertEquals(first, seconds, STEP, "a retry reproduces the route time");
            assertEquals(0, stock.count());
            assertFalse(refillGone(level), "the refill is optional: the required route leaves it where it is");
        }
        System.out.printf(Locale.ROOT, "forest-1 route room required, three identical attempts: %.2f s%n", first);
    }

    // ---- the optional route ----

    @Test void theOptionalDetourCollectsTheRefillThenStillReachesTheCheckpointWithinTheBudget() {
        Level level = room();
        int detourFrames = detour(level);
        assertEquals(1, stock.count(), "the refill grants exactly one charge (D27)");
        assertTrue(refillGone(level));
        int rest = rideToCheckpoint(level);
        float detour = detourFrames * STEP, total = (detourFrames + rest) * STEP;
        System.out.printf(Locale.ROOT, "forest-1 route room with the refill: detour (lever, ladder, refill, back, lever) %.2f s, whole room %.2f s%n", detour, total);
        assertTrue(won(level));
        assertEquals(1, stock.count(), "the charge is kept to the checkpoint");
        assertTrue(total <= 2 * BUDGET, "the collection route is a separate measurement and stays modest: " + total);
    }

    @Test void theRefillCanOnlyBeHadWithTheLeverAndALowerLadderWhileTheLiftStandsStill() {
        Level level = room();
        walkToLever(level);
        operate(level);
        assertFalse(lift(level).isRunning(), "while the hero is on the ledge the lift is stopped: the way on is closed until the lever is pulled again");
        walkToLadder(level); climb(level);
        assertFalse(lift(level).isRunning());
        assertTrue(ladder(level).isLowered());
    }

    // ---- D26: every state still reaches the exit ----

    @Test void everyLiftStopHeightAndLeverStateStillReachesTheCheckpointWithoutRestartLevel() {
        // The lever is pulled once at 0.1 s steps from 0 to 5.2 s (a bit over one lift cycle, 5.12 s): the lift stands still anywhere
        // between its ends. From each stopped state the hero flips the lever back and finishes; from the ledge likewise.
        int states = 0;
        float lowestStop = Float.MAX_VALUE, highestStop = -Float.MAX_VALUE;
        for (int step = 0; step <= 52; step++) {
            float delay = step * 0.1f;
            for (boolean onLedge : new boolean[]{false, true}) {
                Level level = room();
                walkToLever(level);
                settle(level, Math.round(delay * 60));
                operate(level);
                settle(level, 60);
                float stopped = lift(level).getY();
                lowestStop = Math.min(lowestStop, stopped); highestStop = Math.max(highestStop, stopped);
                if (onLedge) { walkToLadder(level); climb(level); }
                recover(level);
                assertTrue(won(level), "delay " + delay + " ledge=" + onLedge + " stopped at " + stopped);
                states++;
            }
        }
        assertEquals(106, states);
        assertTrue(highestStop - lowestStop >= 90, "stop heights span the lift's whole travel: " + (highestStop - lowestStop));
    }

    @Test void pullingTheLeverAnyNumberOfTimesAtAnyRhythmNeverTrapsTheHero() {
        for (int delay = 0; delay <= 150; delay += 15) {
            Level level = room();
            walkToLever(level);
            for (int i = 0; i < 5; i++) { operate(level); settle(level, delay); }
            recover(level);
            assertTrue(won(level), "delay " + delay);
        }
    }

    @Test void droppingOffTheLedgeEitherWayOrOffTheLadderLeavesTheLeverWithinWalkingReach() {
        for (boolean toTheRight : new boolean[]{true, false}) {
            Level level = room();
            walkToLever(level); operate(level);
            walkToLadder(level); climb(level);
            // Walk off the slab's right end (toTheRight) or back over the ladder's side (left): both drops land on the floor.
            for (int i = 0; i < 60 * 6 && (feet(level) < FLOOR - 2 || level.player.onLadder()); i++) {
                held.clear(); held.add(toTheRight ? Input.Keys.D : Input.Keys.A);
                frame(level); alive(level);
            }
            held.clear(); settle(level, 30);
            assertTrue(feet(level) > FLOOR - 2, "back on the floor: " + level.player.position);
            walkToLever(level);
            assertTrue(level.player.canInteract());
            if (!lift(level).isRunning()) operate(level);
            rideToCheckpoint(level);
            assertTrue(won(level));
        }
    }

    @Test void theHeroNeverCanBeOnTheLiftOrTheLadderWhenTheLeverIsPulled() {
        // The lever's reach never includes any support the lever moves, so neither can be taken from under the hero.
        Level level = room();
        for (float y = 96; y <= 288; y += 16)
            for (float x = LADDER_X - 8; x <= LADDER_X + 8; x += 8) {
                level.player.position.set(x - 8, y);
                settle(level, 2);
                assertFalse(level.player.canInteract(), "at the ladder x=" + x + " y=" + y);
            }
        for (float y = TOP - 48; y <= BOTTOM - 48; y += 16)
            for (float x = LIFT_X; x <= LIFT_RIGHT; x += 8) {
                level.player.position.set(x - 24, y);
                settle(level, 2);
                assertFalse(level.player.canInteract(), "at the lift x=" + x + " y=" + y);
            }
    }

    // ---- retry and restart ----

    @Test void deathAndRetryRestartAtTheRoomStartWithTheAuthoredStateAndRollBackTheCharge() {
        Level level = room();
        detour(level);
        assertEquals(1, stock.count());
        // Part way through the second ride: the hero is on the lift, the lever up again; then he dies.
        for (int i = 0; i < 60 * 40 && !(riding(level) && lift(level).getY() < 250); i++) {
            held.clear(); held.add(Input.Keys.D);
            boolean overLift = centerX(level) >= LIFT_X + 4;
            if (!riding(level) && feet(level) > 300 && overLift && lift(level).getY() >= 292) jump();
            frame(level);
        }
        held.clear();
        assertTrue(riding(level));
        level.resetCurrentRoom(player());
        assertEquals(0, stock.count(), "death rolls the unsecured charge back");
        assertFalse(refillGone(level), "and puts the refill back");
        assertFalse(ladder(level).isLowered());
        assertTrue(lift(level).isRunning());
        assertEquals(BOTTOM, lift(level).getY(), 0.01f);
        assertFalse(lever(level).isUp());
        assertEquals(START_X, level.player.position.x, 0.01f, "the hero restarts at the room's entrance");
        assertEquals("forest-1-route", level.currentRoomName(), "not in the trap course");
        mandatory(level);
        assertTrue(won(level));

        // Retry in the lowered-ladder state (lift stopped): the authored state comes back, the route still works.
        Level lowered = room();
        walkToLever(lowered); operate(lowered); settle(lowered, 100);
        assertTrue(ladder(lowered).isLowered());
        lowered.resetCurrentRoom(player());
        assertFalse(ladder(lowered).isLowered());
        assertTrue(lift(lowered).isRunning());
        mandatory(lowered);
        assertTrue(won(lowered));
    }

    @Test void restartLevelRebuildsRoomOneAndTheRouteRoomFromTheMap() {
        Level level = room();
        detour(level);
        // A restart is a fresh Level: both rooms authored, the stock at the level-entry value.
        Level again = room();
        assertEquals("forest-1-route", again.currentRoomName());
        assertFalse(ladder(again).isLowered());
        assertTrue(lift(again).isRunning());
        assertFalse(refillGone(again));
        assertEquals(0, stock.count());
        mandatory(again);
        assertTrue(won(again));
    }

    @Test void theRouteRoomIsEnteredFromTheTrapCourseExitAndRetriesStayInTheRouteRoom() {
        stock = new RangedStock();
        stock.restore(0);
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new, stock);
        assertEquals("forest-1-traps", level.currentRoomName());
        assertTrue(level.hasNextRoom());
        level.enterNextRoom(player());
        assertEquals("forest-1-route", level.currentRoomName());
        assertFalse(level.hasNextRoom(), "the route room is the last: the checkpoint ends the level (room 3, #104, is appended after it later)");
        level.resetCurrentRoom(player());
        assertEquals("forest-1-route", level.currentRoomName());
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals(272, level.player.position.y, 0.01f);
    }

    @Test void theEntranceIsSafeAndFarFromEverythingTheLeverMovesAndFromTheCheckpoint() {
        Level level = room();
        // A safe 48 x 48 area with floor under it, nothing to hurt and nothing in reach to trip over.
        assertEquals(272, level.player.position.y, 0.01f);
        assertFalse(level.player.canInteract());
        assertTrue(LEVER_X - (START_X + 48) >= 40);
        assertTrue(LADDER_X - LEVER_X >= 120, "the lever is well clear of the ladder");
        assertTrue(LIFT_X - SLAB_END >= 160, "the refill ledge and the lift are far apart");
        assertTrue(ROOM_END - 16 - CHECKPOINT_X >= 48, "the checkpoint is clear of the right wall");
        settle(level, 120);
        alive(level);
    }
}
