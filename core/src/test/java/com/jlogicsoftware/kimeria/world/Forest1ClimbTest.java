package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * forest-1-climb (#484, D72, D103, the author's Option A; reworked under D105 with the snail, #500, D106): the spider climbs to the
 * hero while he must wait, and a snail on the block top makes him jump. A one-screen room at the map's end (x 8000-8640 since
 * forest-1-spider grew by 480 px), seventh in the Rooms list. A falling stone (column x 8112) over the approach; two one-way steps
 * (x 8176 y 272, x 8240 y 240) lead onto a block (x 8288-8432, top y 208); the exit is on a shelf above it (x 8288-8384, top y 96),
 * reached only by a running lift (x 8384-8416, its top between y 96 and 160, so its underside clears the spider's leap and the
 * snail). The snail patrols the block top (x 8336-8352) left of the lift. The spider lives on the floor right of the block (x
 * 8432-8512), climbs the block's right face (x 8432, 112 px) and comes along its top (x 8352-8432). Its pounce on the top reaches
 * x 8304 at most, so the block's left 16 px are out of its reach. Y grows downward. Scripted hero at 60 frames per second.
 */
class Forest1ClimbTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    static final String ROOM = "forest-1-climb";
    private static final float ROOM_X = 8000, ROOM_END = 8640, START_X = 8032, FACE_X = 8432, BLOCK_X = 8288, BLOCK_TOP = 192, FLOOR = 304;
    private static final float VIEW_W = 640, SHELF_TOP = 80, LIFT_X = 8384, LIFT_BOTTOM = 144, EXIT_X = 8304, STONE_X = 8112, SNAIL_X = 8336;
    /** Where the scripted hero waits on the block: under the lift, at the block's left end (out of its reach), and in its reach to bait it. */
    private static final float UNDER_LIFT = 8392, LEFT_END = 8289, BAIT_SPOT = 8320;
    /** Pinned (scripted, 60 fps, pause 0 on the top step). */
    static final float BAIT_S = 13.33f, FIREBALL_S = 10.77f, NO_SPIDER_S = 10.77f;
    /** The fireball route at each of {@link #PAUSES}, in frames: a late start on the step can meet the snail and miss a lift. */
    static final int[] FIREBALL_FRAMES = {645, 645, 645, 645, 645, 645, 645, 645, 645, 645, 645, 645, 645, 645, 645, 645, 645, 645, 645, 645,
        645, 645, 645, 645, 645, 645, 645, 645, 799, 799, 799};
    /** The deaths, in frames from the entrance, at each of {@link #PAUSES}: waiting under the lift, and walking to it once it holds the block top. */
    static final int[] UNDER_LIFT_DEATH_FRAMES = {320, 321, 325, 329, 333, 337, 337, 337, 337, 337, 337, 338, 342, 346, 350, 354, 357, 359,
        361, 363, 366, 369, 373, 377, 381, 385, 388, 390, 392, 395, 398};
    static final int[] WALK_IN_DEATH_FRAMES = {320, 321, 325, 329, 333, 337, 337, 337, 337, 337, 337, 338, 342, 346, 350, 391, 393, 393,
        394, 394, 395, 395, 397, 398, 399, 399, 398, 397, 396, 395, 398};
    /** The latest death over the pauses on the top step: the room's retry cost. */
    static final float RETRY_COST_S = 6.65f;
    /** The snail's death (a hero who stands at the block's left end on its line) and the stone's (one who stops under it), pause 0. */
    static final float GOB_DEATH_S = 4.73f, STONE_DEATH_S = 1.98f;
    private static final float TOLERANCE = 6 * STEP;
    /** Every pause on the second step from 0 to 2 s, 4 frames apart. */
    private static final int[] PAUSES = java.util.stream.IntStream.rangeClosed(0, 30).map(i -> 4 * i).toArray();

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() { h.setUp(); h.playSounds = true; }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Level room() { return h.room("forest-1", ROOM); }
    private static Spider spider(Level l) { return Forest1SpiderTest.spider(l); }
    private static boolean onTopOfTheBlock(Spider s) { return !s.onWall() && s.position.y < BLOCK_TOP; }

    enum Plan { NO_SPIDER, UNDER_LIFT, WALK_IN, BAIT, FIREBALL, LATE_SHOT, STAND_IN_THE_LINE }

    /** Outcome of one scripted run: frames from the entrance, and whether it ended bitten or at the exit. */
    record Run(int frames, boolean bitten, boolean spiderDead, String why) {
        float seconds() { return frames * STEP; }
    }

    /**
     * The scripted hero: up the two steps onto the block (pausing {@code pause} frames on the second step), then {@code plan} on
     * the block, then the lift: stand under it, jump on at its bottom, ride up and walk left along the shelf to the exit.
     */
    Run run(Plan plan, int pause) {
        Level l = room();
        Spider s = spider(l);
        if (plan == Plan.NO_SPIDER) s.collidedWithActor(true);
        Escalator lift = l.escalators.get(0);
        com.jlogicsoftware.kimeria.entity.enemy.Snail snail = ScriptedAnswers.snail(l, ROOM_X, ROOM_END);
        boolean snailSwing = false;
        boolean a = false, b = false, c = false, shot = false, committed = false, swung = false, walked = false;
        int paused = 0, back = 0;
        for (int f = 1; f < 60 * 30; f++) {
            held.clear(); pressed.clear();
            Rectangle me = l.player.hitboxBounds();
            float feet = me.y + me.height;
            boolean onStep2 = b && !c && l.player.isOnGround() && feet <= 241;
            boolean onBlock = c && l.player.isOnGround() && Math.abs(feet - BLOCK_TOP) < 1.5f;
            boolean riding = l.player.currentEscalator() == lift;
            boolean onShelf = l.player.isOnGround() && Math.abs(feet - SHELF_TOP) < 1.5f;
            boolean alive = !s.isDefeated();
            if (!c) {   // the steps
                if (!(b && !l.player.isOnGround() && me.x >= 8246)) held.add(Input.Keys.D);
                if (onStep2 && paused < pause) { held.clear(); paused++; }
                else if (!a && me.x >= 8148) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); a = true; }
                else if (a && !b && l.player.isOnGround() && feet <= 273 && me.x >= 8194) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); b = true; }
                else if (onStep2 && me.x >= 8258) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); c = true; }
            } else if (riding || onShelf) {   // ride up, then walk left along the shelf to the exit
                if (onShelf || lift.getY() <= SHELF_TOP + 1) held.add(Input.Keys.A);
            } else if (!l.player.isOnGround()) {
                if (me.x < BLOCK_X + 8) held.add(Input.Keys.D);   // still in the jump onto the block
            } else if (onBlock && plan == Plan.STAND_IN_THE_LINE) {
                // stands still at the block's left end, on the snail's line: no jump, no swing
            } else if (onBlock && !snail.isDefeated()
                && !(alive && !shot && onTopOfTheBlock(s) && (plan == Plan.FIREBALL || plan == Plan.LATE_SHOT))) {
                // the snail first: jump its gob, finish it with the sword (a shooter shoots a spider already on the block top first)
                snailSwing = ScriptedAnswers.finish(l, snail, snailSwing, held, pressed);
            } else if (onBlock) {
                float spot = UNDER_LIFT;
                boolean board = true;
                if (alive) switch (plan) {
                    case WALK_IN -> { if (!walked && lift.getY() >= LIFT_BOTTOM - 26) walked = true; spot = walked ? UNDER_LIFT : LEFT_END; board = walked; }
                    case BAIT -> {
                        board = false;
                        if (!committed && s.state() == Spider.State.WINDUP) { committed = true; back = 12; }
                        if (back > 0) { held.add(Input.Keys.A); back--; spot = Float.NaN; }
                        else if (s.state() == Spider.State.WINDUP || s.state() == Spider.State.LEAP || s.state() == Spider.State.BITE) spot = Float.NaN;
                        else if (committed && s.state() == Spider.State.RECOVER) {
                            Rectangle e = s.hitboxBounds();
                            if (e.x - (me.x + me.width) < 8) { if (!swung) { pressed.add(Input.Keys.K); swung = true; } spot = Float.NaN; }
                            else spot = e.x;
                        } else spot = BAIT_SPOT;
                    }
                    case FIREBALL -> {   // walk on to the lift and shoot the moment it tops the block's far edge
                        board = shot;
                        if (onTopOfTheBlock(s) && !shot) { pressed.add(Input.Keys.L); shot = true; spot = Float.NaN; }
                    }
                    case LATE_SHOT -> {   // wait under the lift and shoot only once its leap has begun (turning to it first)
                        board = false;
                        if (s.state() == Spider.State.LEAP && !shot) {
                            if (l.player.facingRight == (s.hitboxBounds().x > me.x)) { pressed.add(Input.Keys.L); shot = true; }
                            else held.add(s.hitboxBounds().x > me.x ? Input.Keys.D : Input.Keys.A);
                        }
                        spot = shot || s.isCommitted() ? Float.NaN : UNDER_LIFT;   // committed, it is shot where the hero stands
                    }
                    default -> { }
                }
                if (!Float.isNaN(spot) && held.isEmpty() && pressed.isEmpty()) {
                    if (Math.abs(me.x - spot) > 2) held.add(me.x < spot ? Input.Keys.D : Input.Keys.A);
                    else if (board && spot == UNDER_LIFT && lift.getY() >= LIFT_BOTTOM - 4) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                }
            }
            l.update(STEP);
            if (l.player.isGotHit()) return new Run(f, true, s.isDefeated(), "bitten in " + s.state() + ", snail " + snail.state());
            if (l.atRoomExit()) return new Run(f, false, s.isDefeated(), "exit");
        }
        Rectangle me = l.player.hitboxBounds();
        return new Run(60 * 30, false, s.isDefeated(), "stuck at " + me.x + "," + (me.y + me.height) + " " + s.state());
    }

    @Test void theRoomHoldsTheBlockTheShelfTheLiftTheStepsAndOneSpiderWhoseRouteClimbsTheBlocksFarFace() {
        Level l = room();
        assertEquals(ROOM, l.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, ROOM_END - ROOM_X, 368), l.currentRoomBounds());
        assertEquals(START_X, l.player.position.x, 0.01f);
        assertEquals(2, l.objects.stream().filter(o -> o instanceof Enemy<?>).count(), "two monsters, the spider and the snail");
        com.jlogicsoftware.kimeria.entity.enemy.Snail snail = ScriptedAnswers.snail(l, ROOM_X, ROOM_END);
        assertEquals(SNAIL_X, snail.position.x, 0.01f);
        assertEquals(BLOCK_TOP, snail.floorY(), 0.01f, "the snail patrols the block top");
        assertTrue(snail.rangePos() + com.jlogicsoftware.kimeria.entity.enemy.Snail.FRAME_WIDTH <= LIFT_X, "left of the lift's column");
        assertEquals(1, l.objects.stream().filter(com.jlogicsoftware.kimeria.entity.objects.FallingStone.class::isInstance).count(), "one falling stone over the approach");
        assertEquals(1, l.escalators.size(), "one lift");
        Escalator lift = l.escalators.get(0);
        assertTrue(lift.isVertical && lift.isRunning(), "a running lift (known since forest-1-traps)");
        assertEquals(LIFT_X, lift.getX(), 0.01f);
        Spider.Route r = spider(l).route();
        assertEquals(3, r.legs(), "floor, wall, floor");
        assertFalse(r.isWall(0));
        assertTrue(r.isWall(1));
        assertFalse(r.isWall(2));
        assertEquals(FLOOR, r.line(0), 0.01f);
        assertEquals(FACE_X, r.line(1), 0.01f);
        assertFalse(r.solidRight(1), "it climbs the block's right face, the block on its left");
        assertEquals(BLOCK_TOP, r.line(2), 0.01f);
        assertFalse(r.hasThread(0) || r.hasThread(1), "no thread here: one new ability per room (D103)");
        assertTrue(r.lo(2) - 46 >= BLOCK_X + 16, "its pounce on the block top leaves the block's left 16 px out of reach");
        assertTrue(l.ladders.isEmpty() && l.fallingPlatforms.isEmpty() && l.triggers.isEmpty());
        assertEquals("", l.player.zoneHint(), "no hint for a monster (D100)");
        Vector2 cam = l.player.roomCameraTarget();
        assertTrue(cam.x <= ROOM_X && ROOM_END <= cam.x + VIEW_W, "the whole room, the spider's floor and face included, is in view");
        l.enterNextRoom(new Player(h.assets(), h.game(), 0, 0));
        assertEquals("forest-1-route", l.currentRoomName(), "the route room follows");
    }

    /** The lift alone, with the spider out of the way: the exit is reached only by riding it from the block. */
    @Test void theLiftRideReachesTheExitShelf() {
        Run r = run(Plan.NO_SPIDER, 0);
        System.out.println("climb no spider: " + r);
        assertFalse(r.bitten(), r.why());
        assertEquals("exit", r.why());
        assertEquals(NO_SPIDER_S, r.seconds(), TOLERANCE);
    }

    /** Death: a hero who waits under the lift is bitten when the spider comes over the block's far edge and along its top, at every pause. */
    @Test void waitingUnderTheLiftIsBittenAtEveryPause() {
        int[] frames = new int[PAUSES.length];
        for (int i = 0; i < PAUSES.length; i++) {
            Run r = run(Plan.UNDER_LIFT, PAUSES[i]);
            assertTrue(r.bitten(), "pause " + PAUSES[i] + ": " + r.why());
            frames[i] = r.frames();
        }
        System.out.println("climb under-lift deaths: " + java.util.Arrays.toString(frames));
        assertArrayEquals(UNDER_LIFT_DEATH_FRAMES, frames);
    }

    /**
     * Death: out of its reach at the block's left end it waits on the face; walking to the lift as it comes down then walks into
     * its reach on the block top, at every pause. The latest of these is the room's retry cost.
     */
    @Test void walkingToTheLiftWhileItHoldsTheBlockTopIsBittenAtEveryPause() {
        int latest = 0;
        int[] frames = new int[PAUSES.length];
        for (int i = 0; i < PAUSES.length; i++) {
            Run r = run(Plan.WALK_IN, PAUSES[i]);
            assertTrue(r.bitten(), "pause " + PAUSES[i] + ": " + r.why());
            frames[i] = r.frames();
            latest = Math.max(latest, r.frames());
        }
        System.out.println("climb walk-in deaths: " + java.util.Arrays.toString(frames));
        assertArrayEquals(WALK_IN_DEATH_FRAMES, frames);
        for (int f : UNDER_LIFT_DEATH_FRAMES) latest = Math.max(latest, f);
        assertEquals(RETRY_COST_S, latest * STEP, TOLERANCE, "the room's retry cost: the latest death");
    }

    /** Survival without a charge: bait its pounce on the block top, step back out of the locked bite, strike in the recovery, ride. */
    @Test void baitItsPounceStepBackAndStrikeThenRideTheLift() {
        for (int pause : PAUSES) {
            Run r = run(Plan.BAIT, pause);
            assertFalse(r.bitten(), "pause " + pause + ": " + r.why());
            assertTrue(r.spiderDead(), "struck in its recovery");
            assertEquals(BAIT_S, r.seconds(), TOLERANCE, "pause " + pause);
        }
        assertEquals(run(Plan.BAIT, 0).frames(), run(Plan.BAIT, 0).frames(), "a retry plays the same (D7)");
    }

    /** Survival with one charge: shoot it the moment it tops the block's far edge, then ride. */
    @Test void aShotAsItTopsTheBlockKillsItThenRideTheLift() {
        int[] frames = new int[PAUSES.length];
        for (int i = 0; i < PAUSES.length; i++) {
            Run r = run(Plan.FIREBALL, PAUSES[i]);
            assertFalse(r.bitten(), "pause " + PAUSES[i] + ": " + r.why());
            assertTrue(r.spiderDead());
            frames[i] = r.frames();
        }
        System.out.println("climb fireball: " + java.util.Arrays.toString(frames));
        assertEquals(FIREBALL_S, frames[0] * STEP, TOLERANCE, "pause 0");
        assertArrayEquals(FIREBALL_FRAMES, frames);
    }

    /** A shot is never too late while it is on the block top: fired once its leap has begun, it still kills it before the bite (D102's tie rule). */
    @Test void evenAShotFiredDuringItsLeapWins() {
        for (int pause : PAUSES) {
            Run r = run(Plan.LATE_SHOT, pause);
            assertFalse(r.bitten(), "pause " + pause + ": " + r.why());
            assertTrue(r.spiderDead());
        }
    }

    /**
     * Lift clearance: a hero riding the lift draws the spider along the block top to the point under him, so it waits under the
     * lift's column while the lift runs. Over two full cycles the spider stays on the block top where it stands, is never moved,
     * crushed or stopped by the lift, never overlaps it, and never reaches the hero. (Waiting, it dithers by up to two chase steps
     * about its target, about 2.8 px, with the same rhythm wherever the lift is: that is the chase, not the lift.)
     */
    @Test void aSpiderWaitingUnderTheRunningLiftIsNeverTouchedByIt() {
        Level l = room();
        Spider s = spider(l);
        Escalator lift = l.escalators.get(0);
        while (lift.getY() < LIFT_BOTTOM - 1) l.update(STEP);   // the lift at its lowest: put the hero on it
        l.player.position.set(LIFT_X + 16 - 23.5f, lift.getY() - 48);
        l.player.velocity.setZero();
        held.clear(); pressed.clear();
        int frames = 0;
        while (!(onTopOfTheBlock(s) && s.state() == Spider.State.CHASE)) { l.update(STEP); assertTrue(++frames < 60 * 10, "it never came up: " + s.state()); }
        for (int i = 0; i < 60; i++) l.update(STEP);   // settle under him
        Rectangle body = s.hitboxBounds(), liftBox = new Rectangle();
        assertTrue(body.x + body.width > LIFT_X && body.x < LIFT_X + 32, "it waits under the lift's column: " + body);
        float x = s.position.x, y = s.position.y, lowestUnderside = 0, highestTop = Float.MAX_VALUE;
        int cycle = (int) Math.ceil(2 * 2 * (LIFT_BOTTOM - SHELF_TOP) / 50f / STEP);   // up and down at 50 px/s, twice
        for (int i = 0; i < cycle; i++) {
            l.update(STEP);
            assertSame(lift, l.player.currentEscalator(), "the hero rides throughout");
            assertFalse(s.isDefeated() || s.isRemoved(), "not crushed");
            // the chase steps 85 px/s about its target, so a waiting spider dithers by under two steps, lift or no lift
            assertEquals(x, s.position.x, 2 * Spider.CHASE_SPEED * STEP, "not pushed sideways");
            assertEquals(y, s.position.y, 0.01f, "not pushed down or up");
            assertEquals(Spider.State.CHASE, s.state(), "it waits; nothing stops or turns it");
            liftBox.set(lift.getX(), lift.getY(), lift.getWidth(), lift.getHeight());
            assertFalse(liftBox.overlaps(s.hitboxBounds()), "the lift never overlaps its body");
            lowestUnderside = Math.max(lowestUnderside, lift.getY() + lift.getHeight());
            highestTop = Math.min(highestTop, lift.getY());
            assertFalse(l.player.isGotHit());
        }
        assertEquals(SHELF_TOP, highestTop, 0.5f, "the lift ran its whole travel: up to the shelf");
        assertEquals(LIFT_BOTTOM + 16, lowestUnderside, 0.5f, "and down to its lowest stop");
        assertTrue(lowestUnderside <= s.hitboxBounds().y, "the lift's lowest underside (y " + lowestUnderside + ") stays above its body (y " + s.hitboxBounds().y + ")");
    }

    /** Death by the snail: a hero who comes up onto the block and stands at its left end, on the snail's line, is hit by the gob. */
    @Test void standingOnTheSnailsLineIsHitByTheGob() {
        Run r = run(Plan.STAND_IN_THE_LINE, 0);
        System.out.println("climb gob death: " + r);
        assertTrue(r.bitten(), r.why());
        assertTrue(r.why().startsWith("bitten in CRAWL"), "the spider never struck; the gob did: " + r.why());
        assertEquals(GOB_DEATH_S, r.seconds(), TOLERANCE, "retry cost of the snail");
    }

    /** Death by the stone: a hero who stops in its column on the approach when the shadow grows. */
    @Test void stoppingUnderTheStoneOnTheApproachIsItsDeath() {
        Level l = room();
        int f = 0;
        while (!l.player.isGotHit()) {
            assertTrue(++f < 60 * 10, "never struck");
            held.clear(); pressed.clear();
            if (l.player.hitboxBounds().x + 6 < STONE_X) held.add(Input.Keys.D);
            l.update(STEP);
        }
        System.out.printf(java.util.Locale.ROOT, "climb stone death: %.2f s%n", f * STEP);
        assertEquals(STONE_DEATH_S, f * STEP, TOLERANCE, "retry cost of the stone");
    }

    /** A retry rebuilds it calm on its floor at the block's foot. */
    @Test void aRetryResetsItCalmOnItsFloor() {
        Level l = room();
        Spider s = spider(l);
        float x = s.position.x;
        l.resetCurrentRoom(new Player(h.assets(), h.game(), 0, 0));
        Spider again = spider(l);
        assertEquals(Spider.State.CRAWL, again.state());
        assertFalse(again.onWall());
        assertEquals(x, again.position.x, 0.01f);
        assertEquals(FLOOR - 16, again.position.y, 0.01f);
    }
}
