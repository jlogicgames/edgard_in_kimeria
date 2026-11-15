package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.RangedStock;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.RedMob;
import com.jlogicsoftware.kimeria.entity.environment.Checkpoint;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.items.FireballRefill;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.entity.objects.LethalHazard;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest-1 guard room (#104, D67): the floor is the guard-free required route; a lever lowers a ladder to a slab above it, a RedMob stands on the
 * slab right at the ladder's top and a fireball refill lies on the slab's far part. The sword is unavailable on a ladder, so the climb is the contested
 * point. Ordinary keyboard input drives a scripted hero through the real shipped map; the times are author (mastered) measurements, not novice
 * playtests. Recipe and finding: docs/ROOMS.md and D67.
 *
 * <p>Y grows downward: the floor top is y 320, the slab top y 208 (112 px up), the guard stands on the slab with its feet on y 208 (its art's y is 176).
 * Everything below is a map coordinate (room 3 starts at x 2112).
 */
class Forest1CombatTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float ROOM_X = 2112, ROOM_END = 2880, START_X = 2176, FLOOR = 320, DECK = 208;
    private static final float LEVER_X = 2224, LADDER_X = 2288, LADDER_CENTRE = 2296, CHECKPOINT_X = 2800, REFILL_X = 2640, SLAB_END = 2704;
    /** The guard's post (the art's x; its body is x + 6 .. x + 26, y 180-208) and its range: the hero's centre between these two x, on its row. */
    private static final float GUARD_X = 2300, ZONE_LEFT = 2268, ZONE_RIGHT = 2364;
    /** The hero's centre is 23.5 px right of position.x (hitbox offset 18, width 11). The guard's row is wakened by a hero whose feet are above y 230. */
    private static final float ROW_FEET = 230, SHOT_FEET = 214;
    /** The hero walks 100 px/s, the guard 80 px/s; the climb is 70 px/s. */
    private static final float BUDGET = 20f;
    /** Pinned measurements (D67): scripted hero, 60 frames per second. A route may differ from these by at most this much (about 6 frames). */
    private static final float PIN_MARGIN = 0.1f;
    private static final float FLOOR_ROUTE_S = 5.95f, CLIMB_ROUTE_S = 8.33f, SHOT_ROUTE_S = 7.62f;
    /** Pinned sweeps: the number of swing delays (0-14 frames after stepping off) that kill, and of shot heights that stop the guard. */
    private static final int SWING_DELAYS_TRIED = 15, SWING_DELAYS_KILLING = 6, SHOT_HEIGHTS_KILLING = 5, SHOT_DELAYS_KILLING = 4;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;
    private RangedStock stock;
    private RedMob theMob;
    private boolean actionWasDown;

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Player player() { return new Player(h.assets(), h.game(), 0, 0); }

    /** The whole level from a restart, standing at the guard room's entrance with {@code charges} fireballs. */
    private Level room(int charges) {
        stock = new RangedStock();
        stock.restore(charges);
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new, stock);
        level.enterNextRoom(player());
        level.enterNextRoom(player());
        theMob = mob(level);
        return level;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private Ladder ladder(Level level) { return level.ladders.get(0); }
    private Lever lever(Level level) { return level.objects.stream().filter(Lever.class::isInstance).map(Lever.class::cast).findFirst().orElseThrow(); }
    /** The room's guard; after a retry the level builds a new one, so this looks it up (a slain guard leaves the list, then the old one is returned). */
    private RedMob mob(Level level) { return level.objects.stream().filter(RedMob.class::isInstance).map(RedMob.class::cast).findFirst().orElse(theMob); }
    private FireballRefill refill(Level level) { return level.objects.stream().filter(FireballRefill.class::isInstance).map(FireballRefill.class::cast).findFirst().orElse(null); }
    private boolean refillGone(Level level) { return level.objects.stream().noneMatch(o -> o instanceof FireballRefill && !o.isRemoved()); }
    private float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }
    private float centerX(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }
    private boolean won(Level level) { return level.player.getState() == Player.State.DISAPPEARING; }
    private void alive(Level level) { assertFalse(level.player.isGotHit(), "died at " + level.player.position + " guard x " + theMob.getX() + " " + theMob.getState()); }
    private boolean onFloor(Level level) { return feet(level) > FLOOR - 2 && !level.player.onLadder(); }
    private void putHero(Level level, float cx, float y) { level.player.position.set(cx - 23.5f, y); level.player.velocity.setZero(); }
    private boolean guardUntouched() { return theMob.getState() == RedMob.State.IDLE && theMob.getX() == GUARD_X; }

    /** A live enemy on the hero's row, ahead of him and closer than {@code within} px (body to body). */
    private boolean enemyAhead(Level level, float within) {
        Rectangle me = level.player.hitboxBounds();
        Rectangle en = theMob.hitboxBounds();
        return !theMob.isDefeated() && level.player.isOnGround() && en.x >= me.x && en.x - (me.x + me.width) < within
            && en.y < me.y + me.height && en.y + en.height > me.y;
    }

    /** What a hero does when the guard is within sword reach on either side: turn to face it and swing once per press. */
    private boolean defend(Level level) {
        Rectangle me = level.player.hitboxBounds(), en = theMob.hitboxBounds();
        if (theMob.isDefeated() || !level.player.isOnGround() || en.y >= me.y + me.height || en.y + en.height <= me.y) return false;
        boolean ahead = en.x >= me.x;
        float gap = ahead ? en.x - (me.x + me.width) : me.x - (en.x + en.width);
        if (gap > 16) return false;
        held.add(ahead ? Input.Keys.D : Input.Keys.A);
        if (!actionWasDown) pressed.add(Input.Keys.K);
        return true;
    }

    /** Up + Action, as the lesson rooms teach. */
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

    /** Walks right with ordinary keys until the checkpoint; from the slab this walks the slab, takes the refill on the way and drops off the end. */
    private int floorRoute(Level level) {
        int frames = 0;
        actionWasDown = false;
        while (!won(level)) {
            held.clear(); held.add(Input.Keys.D);
            boolean melee = enemyAhead(level, 16);
            if (melee && !actionWasDown) pressed.add(Input.Keys.K);
            actionWasDown = melee;
            frame(level); alive(level);
            assertTrue(++frames < 60 * 30, "the checkpoint was not reached from " + level.player.position);
        }
        held.clear();
        return frames;
    }

    private int walkToLadder(Level level) {
        int frames = 0;
        while (centerX(level) < LADDER_CENTRE + 2) {
            held.clear(); held.add(Input.Keys.D);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 20, "ladder unreachable from " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** Takes hold of the ladder and holds Up until the hero's feet are at or above {@code feetTarget} (still on the ladder, or stepped off at its top). */
    private int climbTo(Level level, float feetTarget) {
        int frames = 0;
        while (feet(level) > feetTarget) {
            held.clear(); held.add(Input.Keys.W);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 20, "climb stuck at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** From the entrance: the lever, the ladder foot, up to {@code feetTarget}. */
    private int toTheLadder(Level level, float feetTarget) {
        int frames = walkToLever(level);
        frames += operate(level);
        assertTrue(ladder(level).isLowered());
        frames += walkToLadder(level);
        return frames + climbTo(level, feetTarget);
    }

    /** Holds Up until the hero steps off the ladder's top, then waits {@code delay} frames and swings once. Returns the frames used; the hero may die. */
    private int climbOnAndSwing(Level level, int delay) {
        int frames = 0;
        while (level.player.onLadder() || feet(level) > DECK + 1) {
            held.clear(); held.add(Input.Keys.W);
            frame(level);
            frames++;
            if (level.player.isGotHit()) return frames;
            assertTrue(frames < 60 * 20, "climb stuck at " + level.player.position);
        }
        held.clear();
        for (int i = 0; i < delay; i++) { frame(level); frames++; if (level.player.isGotHit()) return frames; }
        pressed.add(Input.Keys.K);
        for (int i = 0; i < 30; i++) { frame(level); frames++; if (level.player.isGotHit() || theMob.isDefeated()) break; }
        return frames;
    }

    /** The climb route: lever, ladder, climb on, swing at once, then the slab, the refill, the drop, the checkpoint. */
    private int climbRoute(Level level) {
        int frames = toTheLadder(level, DECK);
        frames += climbOnAndSwing(level, 0);
        alive(level);
        assertTrue(theMob.isDefeated(), "the swing on arrival killed the guard");
        return frames + floorRoute(level);
    }

    /**
     * The shot route: climb on until the feet are at {@value #SHOT_FEET} (the fireball leaves the hero 19 px above his feet, so a lower shot hits the slab's
     * edge), shoot from the ladder with no sword at all, keep climbing, then as the climb route. The guard must be dead before the hero steps off.
     */
    private int shotRoute(Level level) {
        int frames = toTheLadder(level, SHOT_FEET);
        pressed.add(Input.Keys.L);
        for (int i = 0; i < 12 && !theMob.isDefeated(); i++) { held.clear(); held.add(Input.Keys.W); frame(level); frames++; alive(level); }
        assertTrue(theMob.isDefeated(), "the fireball killed the guard");
        assertTrue(level.player.onLadder() || feet(level) <= DECK + 1);
        return frames + floorRoute(level);
    }

    // ---- census and geometry ----

    @Test void theRoomHoldsOneLeverOneRaisedLadderOneSlabOneGuardOneRefillAndTheCheckpointAndNothingElse() {
        Level level = room(0);
        assertEquals("forest-1-guard", level.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, 768, 368), level.currentRoomBounds());
        assertFalse(level.hasNextRoom(), "the guard room is the last: its checkpoint ends the level");
        assertEquals(1, level.triggers.size());
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.ladders.size());
        assertEquals(0, level.escalators.size());
        assertEquals(0, level.fallingPlatforms.size(), "no one-shot support: the lever is a reversible toggle");
        assertEquals(1, level.objects.stream().filter(o -> o instanceof Enemy<?>).count(), "one monster, the RedMob");
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof LethalHazard), "no hazards in the guard room");
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Wall));
        assertEquals(1, level.objects.stream().filter(Checkpoint.class::isInstance).count());
        assertEquals(1, level.objects.stream().filter(FireballRefill.class::isInstance).count(), "one refill, and no coin (coins feed the healer recipe)");
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Collectable));
        assertFalse(ladder(level).isLowered());
        assertFalse(lever(level).isUp());
        assertEquals(LADDER_X, ladder(level).getX(), 0.01f);
        assertEquals(96, ladder(level).topY(), 0.01f);
        assertEquals(DECK, ladder(level).bottomY(), 0.01f, "raised: its foot hangs 112 px above the floor");
        assertEquals(GUARD_X, theMob.getX(), 0.01f);
        assertEquals(DECK, theMob.getY() + theMob.getHeight(), 0.01f, "the guard stands on the slab, not on the floor");
        FireballRefill refill = refill(level);
        assertEquals(REFILL_X, refill.getX(), 0.01f);
        assertEquals(DECK, refill.getY() + refill.getHeight(), 0.01f, "the refill sits on the slab's far part");
        assertTrue(refill.getX() - (GUARD_X + 48) >= 200, "far from the guard, and above any floor threat (the floor has none)");
        var checkpoint = level.objects.stream().filter(Checkpoint.class::isInstance).map(Checkpoint.class::cast).findFirst().orElseThrow();
        assertEquals(CHECKPOINT_X, checkpoint.getX(), 0.01f);
        assertEquals(FLOOR, checkpoint.getY() + checkpoint.getHeight(), 0.01f, "it stands on the floor");
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals("", level.player.zoneHint(), "no hints");
        // The slab is one piece: no hole (D67: the hole served a floor guard; nothing below the slab is left to drop onto).
        long slabs = level.collisionBlocks.stream().filter(b -> b.getY() == DECK && b.getHeight() == 16).count();
        assertEquals(1, slabs);
    }

    @Test void theEntranceIsSafeAndFarFromTheGuardAndTheCheckpointIsClearOfTheRightWallAndTheDrop() {
        Level level = room(0);
        assertEquals(272, level.player.position.y, 0.01f);
        assertFalse(level.player.canInteract());
        settle(level, 60 * 3);
        alive(level);
        assertEquals(START_X, level.player.position.x, 0.5f, "standing still at the entrance: nothing moves the hero");
        assertTrue(guardUntouched());
        assertTrue(ZONE_LEFT - (START_X + 48) >= 40, "the guard's range starts well clear of the entrance's 48 px square");
        assertTrue(ROOM_END - 16 - (CHECKPOINT_X + 16) >= 48, "the checkpoint is clear of the right wall");
        assertTrue(CHECKPOINT_X - SLAB_END >= 48, "and clear of where a hero drops off the slab's end");
    }

    @Test void theGuardHoldsItsPostForAFloorHeroWalkingRunningAndJumpingAllTheWayAndTheFloorRouteNeverTouchesIt() {
        Level level = room(0);
        int frames = 0;
        while (!won(level)) {
            held.clear(); held.add(Input.Keys.D);
            if (frames % 20 == 0) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }   // a hero who jumps along the whole floor
            frame(level); alive(level);
            assertTrue(guardUntouched(), "frame " + frames + " at " + level.player.position);
            assertTrue(++frames < 60 * 30);
        }
        // Standing still for 10 s at the foot of the ladder (the nearest floor point) and under the slab: still asleep.
        Level still = room(0);
        putHero(still, LADDER_CENTRE, 272);
        for (int i = 0; i < 600; i++) { frame(still); assertTrue(guardUntouched()); }
    }

    @Test void theGuardWakesOnlyWhenTheClimbersBodyReachesItsRowAndWaitingBelowItCostsNothing() {
        Level level = room(0);
        toTheLadder(level, ROW_FEET + 6);                      // feet at 236: just below the row
        assertTrue(level.player.onLadder());
        for (int i = 0; i < 60 * 5; i++) { held.clear(); frame(level); alive(level); assertTrue(guardUntouched(), "waiting on the ladder at " + level.player.position); }
        float wokeAt = -1;
        for (int i = 0; i < 60 && wokeAt < 0; i++) {
            held.clear(); held.add(Input.Keys.W);
            float before = feet(level);
            frame(level);
            if (theMob.getState() != RedMob.State.IDLE) wokeAt = before;
        }
        System.out.println("forest-1 guard room: the guard wakes when the climber's feet reach y " + wokeAt);
        assertTrue(wokeAt > 0 && wokeAt <= ROW_FEET && wokeAt >= ROW_FEET - 12, "it wakes as the body reaches its row: " + wokeAt);
    }

    // ---- clearances, for a standing, a jumping and a climbing hero ----

    private static float gap(Rectangle a, Rectangle b) {
        float dx = Math.max(0, Math.max(a.x - (b.x + b.width), b.x - (a.x + a.width)));
        float dy = Math.max(0, Math.max(a.y - (b.y + b.height), b.y - (a.y + a.height)));
        return (float) Math.hypot(dx, dy);
    }

    @Test void theGuardsReachStaysFortyEightPxClearOfTheLeversReachForAStandingAJumpingAndAClimbingHero() {
        // Where the lever can be pulled: hero hitboxes standing on the floor and at every height of a jump over the lever (canInteract is true there).
        Level probe = room(0);
        Rectangle reach = null;
        for (float x = ROOM_X + 24; x < ROOM_END - 24; x += 2) {
            for (float y = 272 - 60; y <= 272; y += 4) {
                putHero(probe, x, y);
                settle(probe, 2);
                if (probe.player.canInteract()) {
                    Rectangle b = new Rectangle(probe.player.hitboxBounds());
                    reach = reach == null ? b : reach.merge(b);
                }
            }
        }
        assertNotNull(reach);
        // Where the guard and its whip can be: a climber who never swings (dies), one who swings at once, one standing on the ladder's top, one who
        // retreats; hold each until the hero is hit or 3 s have passed.
        Rectangle danger = null;
        for (int mode = 0; mode < 4; mode++) {
            Level level = room(0);
            toTheLadder(level, ROW_FEET + 6);
            for (int i = 0; i < 60 * 3 && !level.player.isGotHit(); i++) {
                held.clear();
                if (mode == 0 || mode == 1 || (mode == 2 && feet(level) > DECK + 1)) held.add(Input.Keys.W);
                if (mode == 3) held.add(i < 20 ? Input.Keys.W : Input.Keys.S);
                if (mode == 1 && !level.player.onLadder() && feet(level) <= DECK + 1 && !theMob.isDefeated()) pressed.add(Input.Keys.K);
                frame(level);
                for (Rectangle r : new Rectangle[]{theMob.hitboxBounds(), theMob.attackBounds()}) {
                    Rectangle c = new Rectangle(r);
                    danger = danger == null ? c : danger.merge(c);
                }
            }
        }
        float clear = gap(reach, danger);
        System.out.printf(Locale.ROOT, "forest-1 guard room: lever reach (standing and jumping) x %.1f-%.1f y %.1f-%.1f, the guard's danger x %.1f-%.1f y %.1f-%.1f, gap %.1f px%n",
            reach.x, reach.x + reach.width, reach.y, reach.y + reach.height, danger.x, danger.x + danger.width, danger.y, danger.y + danger.height, clear);
        assertTrue(clear >= 48, "the guard's reach is " + clear + " px from the lever's reach");
        assertTrue(danger.x >= ZONE_LEFT - 16, "the guard never leaves the ladder's top by more than its range: " + danger.x);
        assertTrue(danger.y + danger.height <= DECK, "and never comes down off the slab: " + (danger.y + danger.height));
    }

    // ---- the choices ----

    @Test void theFloorRouteIsGuardFreeNeedsNoChargeAndNothingElse() {
        Level level = room(0);
        int frames = floorRoute(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 guard room, floor route: %.2f s%n", seconds);
        assertTrue(won(level));
        assertEquals(0, stock.count());
        assertTrue(guardUntouched());
        assertFalse(theMob.isDefeated());
        assertFalse(ladder(level).isLowered());
        assertFalse(refillGone(level), "the refill is optional: the floor leaves it where it is");
        assertEquals(FLOOR_ROUTE_S, seconds, PIN_MARGIN);
    }

    @Test void aClimbThatSwingsAtOnceKillsTheGuardTakesTheRefillAndNeedsNoCharge() {
        Level level = room(0);
        int frames = climbRoute(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 guard room, climb and swing: %.2f s%n", seconds);
        assertTrue(won(level));
        assertTrue(theMob.isDefeated());
        assertEquals(1, stock.count(), "the refill paid one charge");
        assertTrue(refillGone(level));
        assertTrue(level.objects.stream().noneMatch(Fireball.class::isInstance));
        assertEquals(CLIMB_ROUTE_S, seconds, PIN_MARGIN);
    }

    @Test void aSwingThatComesTooLateAfterSteppingOffIsWhippedAndTheWindowIsSmall() {
        StringBuilder window = new StringBuilder();
        int kills = 0;
        for (int delay = 0; delay < SWING_DELAYS_TRIED; delay++) {
            Level level = room(0);
            toTheLadder(level, DECK);
            climbOnAndSwing(level, delay);
            boolean killed = theMob.isDefeated() && !level.player.isGotHit();
            if (killed) kills++;
            window.append(killed ? 'K' : level.player.isGotHit() ? 'x' : '.');
        }
        System.out.println("forest-1 guard room, a swing d frames after stepping off the ladder, d = 0.." + (SWING_DELAYS_TRIED - 1) + " (K kill, x whipped): " + window);
        assertEquals(SWING_DELAYS_KILLING, kills, "the swing must come within the wind-up (0.4 s) left after the climb: " + window);
        assertEquals('K', window.charAt(0));
        assertEquals('x', window.charAt(SWING_DELAYS_TRIED - 1));
    }

    @Test void aClimberWhoNeverSwingsIsWhippedAndRetryRestoresTheAuthoredRoom() {
        Level level = room(0);
        toTheLadder(level, DECK);
        for (int i = 0; i < 60 * 3 && !level.player.isGotHit(); i++) { held.clear(); frame(level); }
        assertTrue(level.player.isGotHit(), "standing at the ladder's top beside the guard without swinging is deadly");
        level.resetCurrentRoom(player());
        theMob = mob(level);
        assertFalse(theMob.isDefeated());
        assertEquals(GUARD_X, theMob.getX(), 0.01f, "back at its post");
        assertFalse(ladder(level).isLowered());
        assertFalse(lever(level).isUp());
        assertFalse(refillGone(level));
        assertEquals(START_X, level.player.position.x, 0.01f);
        floorRoute(level);
        assertTrue(won(level));
    }

    @Test void aFireballShotFromTheLadderKillsTheGuardBeforeItStrikesAndTheRefillReturnsTheCharge() {
        Level level = room(1);
        int frames = shotRoute(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 guard room, shot from the ladder: %.2f s%n", seconds);
        assertTrue(won(level));
        assertEquals(1, stock.count(), "one charge spent, one won back from the slab");
        assertTrue(refillGone(level));
        assertEquals(SHOT_ROUTE_S, seconds, PIN_MARGIN);
        // The window, swept: a shot fired when the climber's feet pass each height from 238 to 188.
        StringBuilder window = new StringBuilder();
        int stops = 0;
        for (int at = 238; at >= 188; at -= 2) {
            Level l = room(1);
            toTheLadder(l, 262);                              // climbing on from just below the row; the shot is fired when the feet pass `at`
            boolean fired = false;
            for (int i = 0; i < 90 && !theMob.isDefeated() && !l.player.isGotHit(); i++) {
                held.clear();
                if (l.player.onLadder() || feet(l) > DECK + 1) held.add(Input.Keys.W);
                if (!fired && feet(l) <= at) { pressed.add(Input.Keys.L); fired = true; }
                frame(l);
            }
            boolean ok = theMob.isDefeated() && !l.player.isGotHit();
            if (ok) stops++;
            window.append(ok ? 'K' : l.player.isGotHit() ? 'x' : '.');
        }
        System.out.println("forest-1 guard room, a shot with the climber's feet at 238, 236, .. 188 (K guard dead, x hero whipped): " + window);
        assertEquals(SHOT_HEIGHTS_KILLING, stops, "the shot must leave 19 px above the feet clear of the slab's edge and land before the strike: " + window);
        // ... and fired d frames after stepping off the ladder (the guard is beside him).
        StringBuilder after = new StringBuilder();
        int late = 0;
        for (int d = 0; d < SWING_DELAYS_TRIED; d++) {
            Level l = room(1);
            toTheLadder(l, DECK);
            for (int i = 0; i < 60 && (l.player.onLadder() || feet(l) > DECK + 1); i++) { held.clear(); held.add(Input.Keys.W); frame(l); }
            held.clear();
            for (int i = 0; i < d; i++) frame(l);
            pressed.add(Input.Keys.L);
            for (int i = 0; i < 40 && !theMob.isDefeated() && !l.player.isGotHit(); i++) frame(l);
            boolean ok = theMob.isDefeated() && !l.player.isGotHit();
            if (ok) late++;
            after.append(ok ? 'K' : 'x');
        }
        System.out.println("forest-1 guard room, a shot d frames after stepping off, d = 0.." + (SWING_DELAYS_TRIED - 1) + ": " + after);
        assertEquals(SHOT_DELAYS_KILLING, late, after.toString());
    }

    @Test void aFullStockMakesTheRefillWorthlessAndASpentChargeIsWonBack() {
        Level full = room(3);
        int frames = climbRoute(full);
        assertTrue(won(full));
        assertEquals(3, stock.count(), "a full stock gains nothing");
        assertFalse(refillGone(full), "and the pickup stays where it is (D27)");
        Level spent = room(3);
        shotRoute(spent);
        assertTrue(won(spent));
        assertEquals(3, stock.count(), "stock 3: shoot (2), take the refill (3): the guard cleared for free");
        assertTrue(refillGone(spent));
        assertTrue(frames > 0);
    }

    @Test void backingDownTheLadderWhileTheWhipIsRaisedLetsTheStrikeHitAirAndCostsNothingElse() {
        Level level = room(0);
        toTheLadder(level, ROW_FEET - 2);
        int windUpFrames = 0;
        for (int i = 0; i < 60 * 3; i++) {
            held.clear();
            boolean windingUp = theMob.getState() == RedMob.State.ATTACK;
            if (windingUp || i > 10) { held.add(Input.Keys.S); windUpFrames++; }   // the whip is up: down the ladder
            else held.add(Input.Keys.W);
            frame(level); alive(level);
            if (onFloor(level)) break;
        }
        assertTrue(windUpFrames > 0);
        settle(level, 60);
        alive(level);
        assertFalse(theMob.isDefeated());
        assertTrue(theMob.getX() <= GUARD_X + 0.01f && theMob.getX() >= GUARD_X - 16, "it stays at the ladder's top: " + theMob.getX());
    }

    @Test void eachChoiceLeavesADifferentWorldAndEveryMasteredRouteFitsTheBudget() {
        record Outcome(String name, float seconds, int charges, boolean guardDead, boolean refillTaken, boolean ladderDown) {}
        List<Outcome> all = new ArrayList<>();
        Level floor = room(1);
        int f = floorRoute(floor);
        all.add(new Outcome("floor", f * STEP, stock.count(), theMob.isDefeated(), refillGone(floor), ladder(floor).isLowered()));
        Level climb = room(1);
        f = climbRoute(climb);
        all.add(new Outcome("climb", f * STEP, stock.count(), theMob.isDefeated(), refillGone(climb), ladder(climb).isLowered()));
        Level shot = room(1);
        f = shotRoute(shot);
        all.add(new Outcome("shot", f * STEP, stock.count(), theMob.isDefeated(), refillGone(shot), ladder(shot).isLowered()));
        for (Outcome o : all) {
            System.out.printf(Locale.ROOT, "forest-1 guard room outcome (stock 1): %-6s %5.2f s  charges %d  guard dead %-5s refill taken %-5s ladder down %-5s%n",
                o.name, o.seconds, o.charges, o.guardDead, o.refillTaken, o.ladderDown);
            assertTrue(o.seconds <= 12f, o.name + " is well inside the 20 s budget: " + o.seconds);
        }
        assertEquals(1, all.get(0).charges); assertEquals(2, all.get(1).charges); assertEquals(1, all.get(2).charges);
        assertTrue(!all.get(0).guardDead && all.get(1).guardDead && all.get(2).guardDead);
        assertTrue(!all.get(0).refillTaken && all.get(1).refillTaken && all.get(2).refillTaken);
        assertTrue(!all.get(0).ladderDown && all.get(1).ladderDown && all.get(2).ladderDown);
        assertTrue(all.get(0).seconds < all.get(2).seconds && all.get(2).seconds < all.get(1).seconds, "floor < shot < climb (the shot skips the swing's pause)");
        assertTrue(all.get(0).seconds <= BUDGET && all.get(1).seconds <= BUDGET);
    }

    // ---- the zero-fireball guarantee and D26 ----

    @Test void everyRouteThatNeedsNoChargeWorksWithAnEmptyStock() {
        Level a = room(0); floorRoute(a); assertTrue(won(a));
        Level b = room(0); climbRoute(b); assertTrue(won(b));
        assertEquals(1, stock.count(), "only the refill changed the stock");
    }

    /** Lures the guard to the ladder's top: the hero climbs into its row, then backs down at once. */
    private void lure(Level level) {
        if (!ladder(level).isLowered()) { walkToLever(level); operate(level); }
        walkToLadder(level);
        climbTo(level, ROW_FEET - 6);
        for (int i = 0; i < 60 && !onFloor(level); i++) { held.clear(); held.add(Input.Keys.S); frame(level); alive(level); }
        held.clear(); settle(level, 90);
        alive(level);
    }

    /** From wherever the hero stands (the ladder, the slab, the floor) to the checkpoint with the sword, no lever, no charge. */
    private int recover(Level level) {
        int frames = 0;
        if (level.player.onLadder() || feet(level) < FLOOR - 2) {
            boolean right = centerX(level) > 2400;
            actionWasDown = false;
            while (!onFloor(level)) {
                held.clear();
                held.add(level.player.onLadder() ? Input.Keys.S : right ? Input.Keys.D : Input.Keys.A);
                actionWasDown = defend(level);
                frame(level);
                if (level.player.isGotHit()) fail("died leaving the slab at " + level.player.position);
                assertTrue(++frames < 60 * 20, "cannot leave the slab from " + level.player.position);
            }
            held.clear();
        }
        return frames + floorRoute(level);
    }

    @Test void everyLeverGuardRefillAndHeroStateStillReachesTheCheckpointWithZeroChargesAndWithoutRestartLevel() {
        String[] guardStates = {"at its post", "lured to the ladder's top", "dead"};
        String[] places = {"entrance", "ladder foot", "ladder top", "slab outside the range", "slab by the refill"};
        int runs = 0;
        for (int lever = 0; lever <= 2; lever++) {                    // pulls: 0 = raised, 1 = lowered, 2 = raised again
            for (int g = 0; g < guardStates.length; g++) {
                for (boolean taken : new boolean[]{false, true}) {
                    for (int where = 0; where < places.length; where++) {
                        if (where >= 2 && lever != 1) continue;       // the slab can only be reached, and so be stood on, with the ladder down
                        if (g == 1 && (lever != 1 || where == 2)) continue;   // luring the guard needs the ladder down; the ladder top is reached by the climb, never beside a lured guard
                        String ctx = "lever pulls " + lever + ", guard " + guardStates[g] + ", refill taken " + taken + ", hero at the " + places[where];
                        Level level = room(0);
                        if (lever > 0) { walkToLever(level); for (int p = 0; p < lever; p++) { operate(level); settle(level, 90); } }
                        if (g == 1) lure(level);
                        if (g == 2) theMob.collidedWithActor(true);   // any kill goes through here: sword, fireball, stone or a stomp
                        int initial = stock.count();
                        if (taken) refill(level).collideWithPlayer();
                        float[][] at = {{START_X + 23.5f, 272}, {LADDER_CENTRE, 272}, {LADDER_CENTRE, DECK - 48 - 1}, {2450, DECK - 48 - 1}, {2600, DECK - 48 - 1}};
                        putHero(level, at[where][0], at[where][1]);
                        settle(level, 20);
                        try { recover(level); } catch (AssertionError e) { throw new AssertionError(ctx + ": " + e.getMessage(), e); }
                        assertTrue(won(level), ctx);
                        assertTrue(stock.count() >= initial, ctx);
                        assertTrue(level.objects.stream().noneMatch(Fireball.class::isInstance), ctx + ": no charge needed");
                        runs++;
                    }
                }
            }
        }
        System.out.println("forest-1 guard room D26 sweep: " + runs + " runs, 0 failures, no Restart Level");
        assertEquals(8 + 28 + 8, runs);
    }

    @Test void deathAndRetryRestoreTheAuthoredRoomWhateverTheClimbDidAndAGuardThatMovedIsBackAtItsPost() {
        Level level = room(1);
        toTheLadder(level, DECK);
        climbOnAndSwing(level, 0);
        for (int i = 0; i < 60 * 20 && !refillGone(level); i++) { held.clear(); held.add(Input.Keys.D); frame(level); alive(level); }
        held.clear();
        assertTrue(refillGone(level));
        assertEquals(2, stock.count());       // guard dead, refill taken, not yet secured (the checkpoint secures it)
        level.resetCurrentRoom(player());
        theMob = mob(level);
        assertEquals(1, stock.count(), "a retry rolls the unsecured charge back");
        assertFalse(theMob.isDefeated(), "the guard is back alive on a first visit");
        assertEquals(GUARD_X, theMob.getX(), 0.01f);
        assertFalse(refillGone(level), "and the refill is back");
        assertFalse(ladder(level).isLowered());
        assertFalse(lever(level).isUp());
        assertEquals(START_X, level.player.position.x, 0.01f);
        settle(level, 90);
        // a retry reproduces the time
        float first = climbRoute(level) * STEP;
        level.resetCurrentRoom(player());
        theMob = mob(level);
        settle(level, 90);
        float second = climbRoute(level) * STEP;
        assertEquals(first, second, STEP, "a retry reproduces the route time");
        // a guard lured off its post comes back to it
        Level lured = room(0);
        lure(lured);
        lured.resetCurrentRoom(player());
        theMob = mob(lured);
        assertEquals(GUARD_X, theMob.getX(), 0.01f);
        assertTrue(guardUntouched());
    }

    @Test void aSecuredKillASecuredRefillAndASecuredLoweredLadderStayAsTheyWereAndTheExitStillWorks() {
        // The guard room is last, so nothing secures it: a forward exit or a healer return calls keepPickups() and secureVisit(); then resetCurrentRoom
        // applies the snapshot as a revisit does. State to secure: ladder down, guard dead, refill taken (stock 1).
        Level level = room(0);
        climbRoute(level);
        level.keepPickups();
        level.secureVisit();
        level.resetCurrentRoom(player());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof RedMob m && !m.isDefeated()), "a secured kill stays a kill");
        assertTrue(refillGone(level), "a secured refill does not come back");
        assertTrue(ladder(level).isLowered(), "the ladder stays as left (D26)");
        assertTrue(lever(level).isUp());
        settle(level, 10);
        floorRoute(level);
        assertTrue(won(level));
        // ... and a secured live guard is back at its post, the ladder as left, the refill still there, and the floor route works.
        Level live = room(0);
        walkToLever(live); operate(live); settle(live, 90);
        live.keepPickups();
        live.secureVisit();
        live.resetCurrentRoom(player());
        theMob = mob(live);
        assertFalse(theMob.isDefeated());
        assertTrue(ladder(live).isLowered());
        assertFalse(refillGone(live));
        floorRoute(live);
        assertTrue(won(live));
    }

    @Test void restartLevelRebuildsTheGuardTheRefillAndTheLadderFromTheMap() {
        Level level = room(0);
        climbRoute(level);
        Level again = room(0);
        assertFalse(ladder(again).isLowered());
        assertFalse(theMob.isDefeated());
        assertEquals(GUARD_X, theMob.getX(), 0.01f);
        assertFalse(refillGone(again));
        assertEquals(0, stock.count());
        floorRoute(again);
        assertTrue(won(again));
    }

    @Test void theRoomIsEnteredFromTheRouteRoomExitAndRetriesStayInTheGuardRoom() {
        stock = new RangedStock();
        stock.restore(0);
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new, stock);
        level.enterNextRoom(player());
        assertEquals("forest-1-route", level.currentRoomName());
        assertTrue(level.hasNextRoom());
        level.enterNextRoom(player());
        assertEquals("forest-1-guard", level.currentRoomName());
        assertFalse(level.hasNextRoom());
        level.resetCurrentRoom(player());
        assertEquals("forest-1-guard", level.currentRoomName());
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals(272, level.player.position.y, 0.01f);
    }

    @Test void theLeverIsOnlyEverInReachOnTheFloorAndNeverFromTheLadderOrTheSlab() {
        for (boolean lowered : new boolean[]{false, true}) {
            Level level = room(0);
            if (lowered) { walkToLever(level); operate(level); settle(level, 60); }
            for (float x = ROOM_X + 24; x < ROOM_END - 24; x += 4) {
                for (float y : new float[]{DECK - 48, 96 - 48}) {
                    putHero(level, x, y);
                    settle(level, 2);
                    if (level.player.canInteract()) fail("the lever is in reach from the ladder or the slab: x=" + x + " y=" + y);
                }
            }
        }
    }
}
