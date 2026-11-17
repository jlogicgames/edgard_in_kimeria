package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.environment.Checkpoint;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.FireballRefill;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.entity.objects.LethalHazard;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest-1 lever lesson room (#418, D86, after the D84 playtest): one lever drives a ladder and a lift in opposite states, with both
 * in view at the moment of the pull, and a wrong pull costs nothing. The lever stands on a ledge that only the running lift reaches; the
 * ladder to the exit ledge only comes down when the lever is pulled, so the way on needs both mechanisms, one after the other. Ordinary keyboard
 * input drives the hero through the real shipped map. Times are author (mastered) measurements of a scripted hero, not novice playtests.
 * Recipe and card: docs/ROOMS.md, docs/ROOM_CARDS.md.
 *
 * <p>Y grows downward: the floor top is y 320; the lift runs between y 304 (bottom) and y 240 (top); the lever ledge A and the exit ledge E both have
 * their top at y 208, 112 px above the floor, which a hop (about 58 px) cannot reach; the lever ledge is a floating slab, so the floor runs beneath it. Everything below is a map coordinate (the room starts at x 4320).
 */
class Forest1LeverLessonTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    /** The room is authored after the end of the map (x 4320-4960), like forest-1-mob (#375); the Rooms list puts it between the trap course and the route room. */
    private static final float ROOM_X = 4320, ROOM_END = ROOM_X + 640, START_X = ROOM_X + 32, FLOOR = 320;
    private static final float LIFT_X = ROOM_X + 96, LIFT_RIGHT = ROOM_X + 128, LIFT_TOP = 240, LIFT_BOTTOM = 304;
    private static final float A_X = ROOM_X + 128, A_END = ROOM_X + 240, TOP = 208;
    private static final float LEVER_X = ROOM_X + 208, LADDER_X = ROOM_X + 416, E_X = ROOM_X + 432, EXIT_X = ROOM_X + 544;
    /** The budget for a mastered route (D19, ROOMS.md "Replay budget"): about 20 s. */
    private static final float BUDGET = 20f;
    /** The scripted rider hops onto the lever ledge once the lift is this high (the ledge is 32 px above its top, a hop is about 58 px). */
    private static final float RIDE_JUMP_Y = 246;
    /** The camera shows 640 x 360 world px (KimeriaGame.LOGICAL_W / LOGICAL_H). */
    private static final float VIEW_W = 640, VIEW_H = 360;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;
    /** True from the hop off the lift until the hero lands. */
    private boolean hopping;

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Player player() { return new Player(h.assets(), h.game(), 0, 0); }

    /** The whole level from a restart, standing at the lever lesson room's entrance. */
    private Level room() {
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new);
        level.enterNextRoom(player()); // forest-1-mob (#375) -> forest-1-deadfall (#363)
        level.enterNextRoom(player()); // -> forest-1-traps
        level.enterNextRoom(player()); // -> forest-1-lever
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
    private boolean won(Level level) { return level.atRoomExit(); }
    private void alive(Level level) { assertFalse(level.player.isGotHit(), "died at " + level.player.position); }
    private void jump() { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
    private boolean onFloor(Level level) { return level.player.isOnGround() && feet(level) > FLOOR - 2; }
    private boolean onLedge(Level level, float from, float to) {
        return level.player.isOnGround() && Math.abs(feet(level) - TOP) < 2 && centerX(level) >= from && centerX(level) <= to;
    }
    /** On the lever slab (the body's centre may overhang its ends by a few px: the body is 11 px wide). */
    private boolean onA(Level level) { return onLedge(level, A_X - 8, A_END + 8); }
    /** On the exit ledge or on the ladder's top rung, which is level with it and part of the way onto it. */
    private boolean onE(Level level) { return onLedge(level, LADDER_X, ROOM_END); }

    /** Up + Action, as the lesson rooms teach: Action is a fresh press while Up is held. */
    private int operate(Level level) {
        held.clear(); held.add(Input.Keys.W); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
        held.clear(); frame(level);
        return 3;
    }

    /** One frame of the scripted hero on the way from the floor to the lift: walk to it, stand under it, jump on when it comes down within reach. */
    private void towardsTheLift(Level level) {
        held.clear();
        if (centerX(level) < LIFT_X + 4) held.add(Input.Keys.D);
        else if (centerX(level) > LIFT_RIGHT - 4) held.add(Input.Keys.A); // under the lever ledge: back to the lift's side
        else if (level.player.isOnGround() && !riding(level) && feet(level) > 300 && lift(level).getY() >= 280) jump();
        frame(level); alive(level);
    }

    /** One frame on the lift: stand still while it rises, then hop right onto the lever ledge (32 px above the lift's top) once it is high enough. */
    private void rideFrame(Level level) {
        held.clear();
        if (lift(level).getY() <= RIDE_JUMP_Y && level.player.isOnGround()) { hopping = true; held.add(Input.Keys.D); jump(); }
        frame(level); alive(level);
    }

    /** One frame in the air: carry on to the right during the hop onto the ledge (or when the ladder is down), otherwise keep still. */
    private void airFrame(Level level) {
        held.clear();
        if (hopping || ladder(level).isLowered()) held.add(Input.Keys.D);
        frame(level); alive(level);
        if (level.player.isOnGround()) hopping = false;
    }

    /** From the floor (lever state as authored): to the lever ledge by the running lift, with ordinary keys. */
    private int rideToTheLedge(Level level) {
        int frames = 0;
        while (!onA(level)) {
            if (riding(level)) rideFrame(level);
            else if (!level.player.isOnGround()) airFrame(level);
            else towardsTheLift(level);
            assertTrue(++frames < 60 * 30, "lever ledge unreachable from " + level.player.position + " lift y=" + lift(level).getY() + " riding=" + riding(level) + " ground=" + level.player.isOnGround() + " running=" + lift(level).isRunning() + " cx=" + centerX(level) + " feet=" + feet(level));
        }
        held.clear();
        return frames;
    }

    private int walkToLever(Level level) {
        int frames = 0;
        while (!level.player.canInteract()) {
            held.clear(); held.add(centerX(level) > LEVER_X + 8 ? Input.Keys.A : Input.Keys.D);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 10, "lever never came into reach at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** From the ledge: walk off its right end (a 112 px drop) and on, under the lowered ladder, then climb it and walk to the exit. */
    private int dropAndClimb(Level level) {
        int frames = 0;
        while (!won(level)) {
            held.clear();
            if (level.player.onLadder()) held.add(Input.Keys.W);
            else if (onE(level)) held.add(Input.Keys.D);
            else if (onFloor(level) && centerX(level) >= LADDER_X + 2) held.add(Input.Keys.W);
            else held.add(Input.Keys.D);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 30, "exit unreachable from " + level.player.position + " ladder lowered=" + ladder(level).isLowered());
        }
        held.clear();
        return frames;
    }

    /** The mandatory route from a standing start at the entrance: ride, pull, drop, climb, walk. */
    private int mandatory(Level level) {
        int frames = rideToTheLedge(level);
        frames += walkToLever(level);
        frames += operate(level);
        assertTrue(ladder(level).isLowered() && !lift(level).isRunning());
        return frames + dropAndClimb(level);
    }

    /**
     * One frame-run of the "solver": from wherever the hero stands, in whatever lever state, with ordinary keys, until the exit. It rides the running
     * lift to the ledge and pulls when the ladder is raised, and climbs the ladder when it is lowered; it never uses anything but the lever, the lift and the ladder.
     */
    private int solve(Level level) {
        int frames = 0;
        while (!won(level)) {
            Player p = level.player;
            boolean lowered = ladder(level).isLowered();
            if (p.onLadder()) { held.clear(); held.add(Input.Keys.W); frame(level); alive(level); }
            else if (onE(level)) { held.clear(); held.add(Input.Keys.D); frame(level); alive(level); }
            else if (lowered && onA(level)) { held.clear(); held.add(Input.Keys.D); frame(level); alive(level); } // off the right end
            else if (lowered && riding(level)) { held.clear(); held.add(Input.Keys.A); frame(level); alive(level); } // off the left side to the floor
            else if (lowered && onFloor(level)) {
                held.clear();
                held.add(centerX(level) >= LADDER_X + 2 ? Input.Keys.W : Input.Keys.D);
                frame(level); alive(level);
            } else if (!lowered && onA(level)) {
                if (p.canInteract()) frames += operate(level);
                else { held.clear(); held.add(centerX(level) > LEVER_X + 8 ? Input.Keys.A : Input.Keys.D); frame(level); alive(level); }
            } else if (!lowered && riding(level)) rideFrame(level); else if (!p.isOnGround() && !riding(level)) airFrame(level);
            else towardsTheLift(level); // the floor, the ladder lowered or raised alike: raised means the lift
            assertTrue(++frames < 60 * 60, "no way on from " + p.position + " lowered=" + lowered + " lift y=" + lift(level).getY() + " state=" + p.getState() + " ground=" + p.isOnGround() + " ladder=" + p.onLadder() + " vel=" + p.velocity + " held=" + held + " ladderY=" + ladder(level).getY());
        }
        held.clear();
        return frames;
    }

    // ---- census, links, view ----

    @Test void theRoomOffersOneLeverOneRaisedLadderOneRunningLiftAndNothingElse() {
        Level level = room();
        assertEquals("forest-1-lever", level.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, 640, 368), level.currentRoomBounds());
        assertTrue(level.hasNextRoom(), "the route room follows");
        assertEquals(1, level.triggers.size());
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.ladders.size());
        assertEquals(1, level.escalators.size());
        assertTrue(level.fallingPlatforms.isEmpty(), "no one-shot object: a wrong pull costs nothing");
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof Enemy<?> || o instanceof LethalHazard || o instanceof Collectable
            || o instanceof FireballRefill || o instanceof Checkpoint || o instanceof com.jlogicsoftware.kimeria.entity.items.Wall),
            "no enemy, no hazard, no pickup: the lever is the only new thing (D72) and a wrong pull is free");
        assertEquals("", level.player.zoneHint(), "no hints: forest-1 shows none (D33)");
        // Authored state: the ladder is raised, the lift runs from its top, the lever shows "down" (the route room's own start state).
        assertFalse(ladder(level).isLowered());
        assertTrue(lift(level).isRunning());
        assertTrue(lift(level).isVertical);
        assertEquals(LIFT_TOP, lift(level).getY(), 0.01f);
        assertEquals(LIFT_X, lift(level).getX(), 0.01f);
        assertFalse(lever(level).isUp());
        assertEquals(LADDER_X, ladder(level).getX(), 0.01f);
        assertEquals(96, ladder(level).topY(), 0.01f);
        assertEquals(TOP, ladder(level).bottomY(), 0.01f, "raised: its foot hangs 112 px above the floor, level with the ledges' tops");
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals(272, level.player.position.y, 0.01f);
    }

    @Test void oneLeverDrivesTheLadderAndTheLiftInOppositeStatesAndShowsIt() {
        Level level = room();
        rideToTheLedge(level);
        walkToLever(level);
        for (int pull = 1; pull <= 5; pull++) {
            operate(level);
            boolean odd = pull % 2 == 1;
            assertEquals(odd, ladder(level).isLowered(), "pull " + pull + ": the ladder follows the lever");
            assertEquals(!odd, lift(level).isRunning(), "pull " + pull + ": the lift is the opposite");
            assertEquals(odd, lever(level).isUp(), "the lever shows the ladder");
            settle(level, 60);
        }
        // Five pulls: lowered. The lowered ladder reaches the floor from the exit ledge's top.
        assertTrue(ladder(level).isLowered());
        assertEquals(TOP, ladder(level).topY(), 0.01f);
        assertEquals(FLOOR, ladder(level).bottomY(), 0.01f);
        assertEquals(LADDER_X + 16, E_X, 0.01f, "the ladder stands against the exit ledge's left face");
    }

    @Test void theLeverTheLadderAndTheLiftAreInOneCameraViewAtTheMomentOfThePull() {
        Level level = room();
        rideToTheLedge(level);
        walkToLever(level);
        // The camera target for a hero standing at the lever (the production rule, Player.roomCameraTarget): a room no wider than the view is shown whole.
        Vector2 target = level.player.roomCameraTarget();
        assertEquals(ROOM_X, target.x, 0.01f, "the room is one screen wide, so the whole room is always in view");
        Rectangle view = new Rectangle(target.x, target.y, VIEW_W, VIEW_H);
        Rectangle leverBox = new Rectangle(LEVER_X, TOP - 16, 16, 16);
        Rectangle liftRange = new Rectangle(LIFT_X, LIFT_TOP, 32, LIFT_BOTTOM + 16 - LIFT_TOP);
        Rectangle ladderRange = new Rectangle(LADDER_X, 96, 16, FLOOR - 96); // raised y 96-208, lowered y 208-320
        for (Rectangle thing : new Rectangle[]{leverBox, liftRange, ladderRange})
            assertTrue(view.contains(thing), thing + " is inside the view " + view);
        // And close to each other: both mechanisms within about 200 px of the lever (nearest edges).
        assertTrue(LEVER_X - LIFT_RIGHT <= 100, "the lift is " + (LEVER_X - LIFT_RIGHT) + " px from the lever");
        assertTrue(LADDER_X - (LEVER_X + 16) <= 200, "the ladder is " + (LADDER_X - (LEVER_X + 16)) + " px from the lever");
        // Wherever the hero stands in the room the same whole-room view holds (the horizontal target never moves).
        for (float x = ROOM_X + 24; x < ROOM_END - 24; x += 40) {
            level.player.position.set(x - 24, 272);
            settle(level, 2);
            assertEquals(ROOM_X, level.player.roomCameraTarget().x, 0.01f, "x=" + x);
            assertTrue(level.player.roomCameraTarget().y <= 8 && level.player.roomCameraTarget().y >= 0, "the 368 px room leaves at most 8 px of vertical play");
        }
    }

    @Test void theLeverIsOnlyEverInReachStandingOnTheLeverLedgeFarFromTheLadderAndTheLift() {
        for (boolean lowered : new boolean[]{false, true}) {
            Level level = room();
            if (lowered) { rideToTheLedge(level); walkToLever(level); operate(level); settle(level, 60); }
            assertEquals(lowered, ladder(level).isLowered());
            for (float x = ROOM_X + 24; x < ROOM_END - 24; x += 4) {
                for (float y : new float[]{272, TOP - 48, 96, 160}) {
                    level.player.position.set(x - 24, y);
                    settle(level, 2);
                    if (level.player.canInteract()) {
                        assertTrue(Math.abs(feet(level) - TOP) < 4 && centerX(level) >= A_X && centerX(level) < A_END,
                            "the lever is only in reach standing on its ledge: x=" + x + " y=" + y + " at " + level.player.position);
                        assertTrue(Math.abs(centerX(level) - (LEVER_X + 8)) < 40, "x=" + x);
                    }
                }
            }
        }
    }

    @Test void theHeroIsNeverOnTheLadderOrTheLiftWhenTheLeverIsInReach() {
        // The lever's reach never includes a support the lever moves, so neither can be taken from under the hero.
        Level level = room();
        for (float y = 96; y <= 288; y += 16)
            for (float x = LADDER_X - 8; x <= LADDER_X + 8; x += 8) {
                level.player.position.set(x - 8, y);
                settle(level, 2);
                assertFalse(level.player.canInteract(), "at the ladder x=" + x + " y=" + y);
            }
        for (float y = LIFT_TOP - 48; y <= LIFT_BOTTOM - 48; y += 8)
            for (float x = LIFT_X; x <= LIFT_RIGHT; x += 8) {
                level.player.position.set(x - 24, y);
                settle(level, 2);
                assertFalse(level.player.canInteract(), "at the lift x=" + x + " y=" + y);
            }
    }

    // ---- the way on needs the lift and then the ladder ----

    @Test void fromTheFloorNeitherWalkingNorJumpingGetsTheHeroUpWithoutTheLiftOrTheLadder() {
        Level level = room();
        level.escalators.clear(); // the lift out of the way: nothing else may raise the hero
        level.objects.removeIf(Escalator.class::isInstance);
        for (float x = ROOM_X + 24; x <= ROOM_END - 32; x += 4) {
            level.player.position.set(x - 24, 272);
            for (int i = 0; i < 60 * 2; i++) {
                held.clear(); held.add(Input.Keys.D); held.add(Input.Keys.W);
                if (i % 30 == 0) jump();
                frame(level);
                if (feet(level) < FLOOR - 70 && !level.player.onLadder()) {
                    // standing on a ledge is only fair when it was a sweep position over that ledge; a ledge top is 112 px up
                    fail("hero got above a hop's reach from the floor at x=" + x + ": " + level.player.position);
                }
            }
        }
    }

    @Test void withTheLadderRaisedNeitherWalkingNorJumpingFromTheLeverLedgeReachesTheLadderOrTheExitLedge() {
        Level level = room();
        assertFalse(ladder(level).isLowered());
        float closest = Float.MAX_VALUE;
        for (float x = A_X + 8; x <= A_END; x += 4) {
            for (boolean jumping : new boolean[]{false, true}) {
                level.player.position.set(x - 24, TOP - 48);
                level.player.velocity.setZero();
                settle(level, 3);
                for (int i = 0; i < 120; i++) {
                    held.clear(); held.add(Input.Keys.D);
                    if (jumping && i % 12 == 0) jump();
                    frame(level); alive(level);
                    assertFalse(level.player.onLadder(), "got onto the raised ladder from x=" + x);
                    assertFalse(onE(level), "got onto the exit ledge from x=" + x);
                    if (centerX(level) > A_END && feet(level) <= TOP + 48) closest = Math.min(closest, LADDER_X - centerX(level)); // while up at the ladder's height
                }
            }
        }
        System.out.printf(Locale.ROOT, "Forest1LeverLessonTest: the farthest a leaping hero gets from the slab leaves the raised ladder %.1f px away%n", closest);
        assertTrue(closest >= 48, "the farthest a leaping hero gets leaves the ladder at least 48 px away: " + closest);
        assertTrue(LADDER_X - A_END >= 100, "the gap between the lever ledge and the ladder is " + (LADDER_X - A_END));
    }

    @Test void withTheLadderRaisedTheFloorBelowItHasNoWayUp() {
        Level level = room();
        level.escalators.clear();
        level.objects.removeIf(Escalator.class::isInstance);
        for (float x = LADDER_X - 40; x <= LADDER_X + 16; x += 4) {
            level.player.position.set(x - 24, 272);
            for (int i = 0; i < 90; i++) {
                held.clear(); held.add(Input.Keys.W); held.add(Input.Keys.D);
                if (i % 20 == 0) jump();
                frame(level); alive(level);
                assertFalse(level.player.onLadder(), "grabbed the raised ladder from x=" + x + " at " + level.player.position);
            }
        }
    }

    // ---- the mastered route, with ordinary keys ----

    @Test void theRequiredRouteRidesPullsDropsClimbsAndWalksToTheExitInUnderTwentySeconds() {
        Level level = room();
        int frames = mandatory(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "Forest1LeverLessonTest mandatory route: %d frames = %.2f s%n", frames, seconds);
        assertTrue(won(level));
        assertTrue(seconds <= BUDGET, "a mastered route fits the 20 s target (D19): " + seconds);
        assertTrue(ladder(level).isLowered(), "the lever was pulled once: the ladder is down and the lift stopped");
        assertFalse(lift(level).isRunning());
    }

    @Test void theRequiredRouteTakesTheSameTimeOnThreeAttemptsAfterRetries() {
        Level level = room();
        int first = mandatory(level);
        for (int attempt = 0; attempt < 2; attempt++) {
            level.resetCurrentRoom(player());
            assertEquals("forest-1-lever", level.currentRoomName(), "a retry stays in the lever room, not the trap course");
            assertEquals(first, mandatory(level), "attempt " + (attempt + 2));
        }
    }

    @Test void aSolverThatPullsTwiceAndGoesBackToTheLiftStillReachesTheExit() {
        // The player pulls, sees the ladder come down, pulls again (ladder up, lift back), and rides again: nothing was lost.
        Level level = room();
        rideToTheLedge(level);
        walkToLever(level);
        operate(level); settle(level, 90);
        operate(level); settle(level, 30);
        assertFalse(ladder(level).isLowered());
        assertTrue(lift(level).isRunning());
        solve(level);
        assertTrue(won(level));
    }

    // ---- D26: every state, every moment, reaches the exit without Restart Level ----

    @Test void everyLiftMomentPullCountAndDropStillReachesTheExitWithoutRestartLevel() {
        // The hero waits 0-2.5 s on the ledge (the lift's whole 2.56 s cycle, 0.1 s apart), pulls the lever 1-4 times,
        // then drops to the floor to the right, to the left, or stays: from each state the solver reaches the exit. 26 x 4 x 3 = 312 runs.
        int states = 0;
        float lowestStop = Float.MAX_VALUE, highestStop = -Float.MAX_VALUE;
        for (int step = 0; step <= 25; step++) {
            for (int pulls = 1; pulls <= 4; pulls++) {
                for (int drop = 0; drop < 3; drop++) {
                    Level level = room();
                    rideToTheLedge(level);
                    walkToLever(level);
                    settle(level, step * 6); // wait on the ledge: the lift keeps running, so the pull stops it at a different height each time
                    for (int i = 0; i < pulls; i++) { operate(level); settle(level, i * 20); }
                    float stopped = lift(level).getY();
                    if (!lift(level).isRunning()) { lowestStop = Math.min(lowestStop, stopped); highestStop = Math.max(highestStop, stopped); }
                    if (drop > 0) {
                        for (int i = 0; i < 60 * 4 && !onFloor(level); i++) {
                            held.clear(); held.add(drop == 1 ? Input.Keys.D : Input.Keys.A);
                            frame(level); alive(level);
                        }
                        held.clear(); settle(level, 20);
                        assertTrue(onFloor(level) || riding(level) || onA(level), "settled after the drop " + level.player.position);
                    }
                    solve(level);
                    assertTrue(won(level), "wait " + step * 6 + " frames, pulls " + pulls + " drop " + drop);
                    states++;
                }
            }
        }
        assertEquals(312, states);
        assertTrue(highestStop - lowestStop >= 50, "the lift was stopped at heights spanning its whole travel: " + (highestStop - lowestStop));
    }

    @Test void startingAnywhereOnTheFloorInEitherLeverStateStillReachesTheExit() {
        for (boolean lowered : new boolean[]{false, true}) {
            for (float x = ROOM_X + 24; x <= E_X - 16; x += 16) { // the floor, left of the exit ledge's face
                Level level = room();
                if (lowered) { rideToTheLedge(level); walkToLever(level); operate(level); settle(level, 90); }
                level.player.position.set(x - 24, 272);
                level.player.velocity.setZero();
                settle(level, 3);
                solve(level);
                assertTrue(won(level), "lowered=" + lowered + " x=" + x);
            }
        }
    }

    @Test void aWrongPullCostsNothingNoHazardNoPitNoDamage() {
        Level level = room();
        // The floor is one block along the whole room: no pit, so every drop lands on it; the level Deadline lies below it.
        assertTrue(level.collisionBlocks.stream().anyMatch(b -> b.getX() <= ROOM_X && b.getX() + b.getWidth() >= ROOM_END && b.getY() == FLOOR && !b.isPlatform()),
            "one unbroken floor");
        rideToTheLedge(level);
        walkToLever(level);
        for (int i = 0; i < 6; i++) { operate(level); settle(level, 7 * i); alive(level); }
        for (int i = 0; i < 60 * 4 && !onFloor(level); i++) { held.clear(); held.add(Input.Keys.D); frame(level); alive(level); }
        held.clear(); settle(level, 30);
        alive(level);
        assertTrue(onFloor(level));
    }

    // ---- state boundary ----

    @Test void deathAndRetryRestartAtTheRoomStartWithTheAuthoredState() {
        Level level = room();
        rideToTheLedge(level); walkToLever(level); operate(level); settle(level, 90);
        assertTrue(ladder(level).isLowered());
        level.resetCurrentRoom(player());
        assertFalse(ladder(level).isLowered());
        assertTrue(lift(level).isRunning());
        assertEquals(LIFT_TOP, lift(level).getY(), 0.01f);
        assertFalse(lever(level).isUp());
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals("forest-1-lever", level.currentRoomName());
        mandatory(level);
        assertTrue(won(level));
    }

    @Test void theRoomIsEnteredFromTheTrapCourseExitAndLeadsToTheRouteRoom() {
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new);
        assertEquals("forest-1-mob", level.currentRoomName());
        level.enterNextRoom(player());
        assertEquals("forest-1-deadfall", level.currentRoomName());
        level.enterNextRoom(player());
        assertEquals("forest-1-traps", level.currentRoomName());
        level.enterNextRoom(player());
        assertEquals("forest-1-lever", level.currentRoomName());
        assertTrue(level.hasNextRoom());
        level.enterNextRoom(player());
        assertEquals("forest-1-route", level.currentRoomName());
    }

    @Test void theEntranceIsSafeAndFarFromEverythingTheLeverMoves() {
        Level level = room();
        // A safe 48 x 48 area with floor under it, nothing to hurt and nothing in reach to trip over.
        assertFalse(level.player.canInteract());
        assertTrue(LIFT_X - (START_X + 48) >= 16, "the lift starts clear of the entrance");
        assertTrue(ROOM_END - 16 - (EXIT_X + 48) >= 16, "the exit rectangle ends before the right wall");
        settle(level, 120);
        alive(level);
        assertTrue(onFloor(level));
    }

    @Test void theExitRectangleStandsOnTheExitLedge() {
        Level level = room();
        level.player.position.set(EXIT_X - 24 - 12, TOP - 48);
        settle(level, 10);
        assertFalse(level.atRoomExit(), "12 px short of the exit rectangle's left edge");
        assertTrue(feet(level) <= TOP + 1, "standing on the ledge top: " + level.player.position);
        level.player.position.set(EXIT_X - 24 + 4, TOP - 48);
        settle(level, 10);
        assertTrue(level.atRoomExit());
    }

    @Test void theTransitionsTrapCourseToLeverRoomAndLeverRoomToRouteRoomPlaceTheHeroAtTheRightRoomStartInsideTheCamera() {
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new);
        level.enterNextRoom(player()); // forest-1-mob -> forest-1-deadfall (#363)
        level.enterNextRoom(player()); // -> forest-1-traps
        assertEquals("forest-1-traps", level.currentRoomName());
        level.enterNextRoom(player()); // the trap course's exit leads to the lever room, which lies after the map's old end
        assertEquals("forest-1-lever", level.currentRoomName());
        assertEquals(START_X, level.player.position.x, 0.01f, "the lever room's RoomStart");
        assertEquals(272, level.player.position.y, 0.01f);
        Rectangle room = level.currentRoomBounds();
        assertEquals(new Rectangle(ROOM_X, 0, 640, 368), room);
        Vector2 cam = level.player.roomCameraTarget();
        assertTrue(cam.x >= room.x && cam.x + VIEW_W <= room.x + room.width, "the camera lies inside the room: " + cam);
        assertTrue(room.contains(level.player.hitboxBounds()), "the hero starts inside the room");
        settle(level, 60);
        alive(level);
        assertTrue(onFloor(level));
        level.enterNextRoom(player()); // the lever room's exit leads to the route room, back near the start of the map
        assertEquals("forest-1-route", level.currentRoomName());
        Rectangle route = level.currentRoomBounds();
        assertEquals(1280, route.x, 0.01f);
        assertEquals(1344, level.player.position.x, 0.01f, "the route room's RoomStart");
        Vector2 cam2 = level.player.roomCameraTarget();
        assertTrue(cam2.x >= route.x && cam2.x + VIEW_W <= route.x + route.width, "the camera lies inside the route room: " + cam2);
        settle(level, 60);
        assertFalse(level.player.isGotHit());
    }
}
