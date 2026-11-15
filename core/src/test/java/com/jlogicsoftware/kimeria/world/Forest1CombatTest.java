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
 * The forest-1 guard room (#104, D67): a RedMob holds the floor under a high route. One lever lowers a ladder to a slab that runs over the guard, with
 * a one-tile-pair hole right above it. The floor is the required route and never needs the lever or a charge. Ordinary keyboard input drives a scripted
 * hero through the real shipped map; the times are author (mastered) measurements, not novice playtests. Recipe and finding: docs/ROOMS.md and D67.
 *
 * <p>Y grows downward: the floor top is y 320, the slab has its top at y 208 (112 px up) and the guard stands with its feet on y 320. Everything below is
 * a map coordinate (room 3 starts at x 2112).
 */
class Forest1CombatTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float ROOM_X = 2112, ROOM_END = 2880, START_X = 2176, FLOOR = 320, DECK = 208;
    private static final float LEVER_X = 2224, LADDER_X = 2288, CHECKPOINT_X = 2800;
    /** The slab runs 2304-2496, the hole above the guard 2496-2528, the far part 2528-2704. */
    private static final float SLAB_LEFT = 2304, HOLE_LEFT = 2496, HOLE_RIGHT = 2528, SLAB_END = 2704;
    /** The guard's post (the art's x; its body is x + 6 .. x + 26) and its range, which is the hero's centre between these two x. */
    private static final float MOB_X = 2496, ZONE_LEFT = 2384, ZONE_RIGHT = 2624;
    /** The budget for a mastered route (D19, ROOMS.md "Replay budget"): about 20 s. */
    private static final float BUDGET = 20f;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;
    private RangedStock stock;
    private RedMob theMob;
    private boolean actionWasDown;
    private int retreatFrames;

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
    private float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }
    private float centerX(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }
    private float bodyRight(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width; }
    private boolean won(Level level) { return level.player.getState() == Player.State.DISAPPEARING; }
    private void alive(Level level) { assertFalse(level.player.isGotHit(), "died at " + level.player.position + " guard x " + theMob.getX() + " " + theMob.getState()); }
    private void jump() { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
    /** Puts the hero's hitbox centre at {@code cx} (the hitbox sits 18 px right of {@code position.x} and is 11 px wide) with his top at {@code y}, at rest. */
    private void putHero(Level level, float cx, float y) { level.player.position.set(cx - 23.5f, y); level.player.velocity.setZero(); }
    private boolean onFloor(Level level) { return feet(level) > FLOOR - 2 && !level.player.onLadder(); }
    private boolean onSlab(Level level) { return level.player.isOnGround() && Math.abs(feet(level) - DECK) < 2; }

    /** A live enemy on the hero's row, ahead of him and closer than {@code within} px (body to body). */
    private boolean enemyAhead(Level level, float within) {
        Rectangle me = level.player.hitboxBounds();
        Rectangle en = theMob.hitboxBounds();
        return !theMob.isDefeated() && level.player.isOnGround() && en.x >= me.x && en.x - (me.x + me.width) < within
            && en.y < me.y + me.height && en.y + en.height > me.y;
    }

    /**
     * What a hero does when the guard is within sword reach on either side: turn to face it (a key held toward it) and swing once per press. Returns
     * whether he is fighting this frame; the caller keeps {@code actionWasDown} for the fresh-press rule.
     */
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

    /** The floor route with ordinary keys, the sword only: walk right, swing when the guard is within the sword's reach. Ends at the checkpoint. */
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

    /**
     * The timing route: do not swing first. Stand still when the guard wakes, let it raise the whip, step back out of the whip's reach so that it
     * strikes the air, then walk in and strike once its attack is over.
     */
    private int timingRoute(Level level) {
        int frames = 0;
        boolean sawWindUp = false;
        retreatFrames = 0;
        actionWasDown = false;
        while (!won(level)) {
            held.clear();
            RedMob mob = mob(level);
            boolean windingUp = !mob.isDefeated() && mob.getState() == RedMob.State.ATTACK;
            boolean awake = !mob.isDefeated() && mob.getState() != RedMob.State.IDLE;
            if (windingUp) { sawWindUp = true; held.add(Input.Keys.A); retreatFrames++; }   // the whip is up: back off
            else if (sawWindUp || !awake) held.add(Input.Keys.D);                              // asleep, or its attack is spent: go on
            // awake and not yet attacking: stand still and let it come
            boolean melee = sawWindUp && !windingUp && enemyAhead(level, 16);
            if (melee && !actionWasDown) pressed.add(Input.Keys.K);
            actionWasDown = melee;
            frame(level); alive(level);
            assertTrue(++frames < 60 * 30, "timing route stuck at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** Shoots once the hero's centre passes {@code fromX} (he keeps walking: the fireball flies on the hero's row), then goes on to the checkpoint. */
    private int fireballRoute(Level level, float fromX) {
        int frames = 0;
        boolean shot = false;
        actionWasDown = false;
        while (!won(level)) {
            held.clear(); held.add(Input.Keys.D);
            if (!shot && centerX(level) >= fromX) { pressed.add(Input.Keys.L); shot = true; }
            boolean melee = enemyAhead(level, 16);
            if (melee && !actionWasDown) pressed.add(Input.Keys.K);
            actionWasDown = melee;
            frame(level); alive(level);
            assertTrue(++frames < 60 * 30, "fireball route stuck at " + level.player.position);
        }
        held.clear();
        assertTrue(shot);
        return frames;
    }

    /** Walks right until the body is over the ladder's rungs. */
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

    /** Holds Up until the hero stands on the slab. */
    private int climb(Level level) {
        int frames = 0;
        boolean grabbed = false;
        while (!(grabbed && !level.player.onLadder() && feet(level) <= DECK + 1)) {
            held.clear(); held.add(Input.Keys.W);
            frame(level);
            grabbed |= level.player.onLadder();
            assertTrue(++frames < 60 * 20, "climb stuck at " + level.player.position + " ladderY=" + ladder(level).getY());
        }
        held.clear(); frame(level);
        return frames + 1;
    }

    /** From the foot of the ladder, with the lever pulled: up, then right along the slab, hopping the hole (a 32 px gap, one jump), and off the far end. */
    private int walkTheSlab(Level level) {
        int frames = 0;
        while (!onFloor(level) && !won(level)) {
            held.clear(); held.add(Input.Keys.D);
            if (onSlab(level) && bodyRight(level) >= HOLE_LEFT - 6 && bodyRight(level) < HOLE_RIGHT - 8 && centerX(level) < HOLE_LEFT) jump();
            frame(level); alive(level);
            assertTrue(++frames < 60 * 30, "slab walk stuck at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** The overhead route from a standing start: lever, ladder, slab, the hole, the drop, the walk to the checkpoint. The guard is never touched. */
    private int overheadRoute(Level level) {
        int frames = walkToLever(level);
        frames += operate(level);
        assertTrue(ladder(level).isLowered());
        frames += walkToLadder(level);
        frames += climb(level);
        frames += walkTheSlab(level);
        return frames + floorRoute(level);
    }

    // ---- census and geometry ----

    @Test void theRoomHoldsOneLeverOneRaisedLadderOneSlabWithAHoleOneRedMobAndTheCheckpointAndNothingElse() {
        Level level = room(0);
        assertEquals("forest-1-guard", level.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, 768, 368), level.currentRoomBounds());
        assertFalse(level.hasNextRoom(), "the guard room is the last: its checkpoint ends the level");
        assertEquals(1, level.triggers.size());
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.ladders.size());
        assertEquals(0, level.escalators.size(), "no lift and no ferry: the lift is room 2's");
        assertEquals(0, level.fallingPlatforms.size(), "no one-shot support in this room: the lever is a reversible toggle");
        assertEquals(1, level.objects.stream().filter(o -> o instanceof Enemy<?>).count(), "one monster, the RedMob");
        assertEquals(1, level.objects.stream().filter(RedMob.class::isInstance).count());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof LethalHazard), "no hazards in the guard room");
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Wall));
        assertEquals(1, level.objects.stream().filter(Checkpoint.class::isInstance).count());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof FireballRefill), "no refill in this room: the fireball is the one the hero carries in");
        assertFalse(ladder(level).isLowered());
        assertFalse(lever(level).isUp());
        assertEquals(LADDER_X, ladder(level).getX(), 0.01f);
        assertEquals(96, ladder(level).topY(), 0.01f);
        assertEquals(208, ladder(level).bottomY(), 0.01f, "raised: its foot hangs 112 px above the floor");
        assertEquals(MOB_X, theMob.getX(), 0.01f);
        assertEquals(FLOOR, theMob.getY() + theMob.getHeight(), 0.01f, "the guard stands on the floor");
        Rectangle body = theMob.hitboxBounds();
        assertTrue(body.x >= HOLE_LEFT && body.x + body.width <= HOLE_RIGHT, "the hole is right above the guard: " + body);
        var checkpoint = level.objects.stream().filter(Checkpoint.class::isInstance).map(Checkpoint.class::cast).findFirst().orElseThrow();
        assertEquals(CHECKPOINT_X, checkpoint.getX(), 0.01f);
        assertEquals(FLOOR, checkpoint.getY() + checkpoint.getHeight(), 0.01f, "it stands on the floor");
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals("", level.player.zoneHint(), "no hints");
    }

    @Test void theEntranceIsSafeAndFarFromTheGuardAndTheCheckpointIsClearOfTheRightWallAndTheDrop() {
        Level level = room(0);
        assertEquals(272, level.player.position.y, 0.01f);
        assertFalse(level.player.canInteract());
        settle(level, 60 * 3);
        alive(level);
        assertEquals(START_X, level.player.position.x, 0.5f, "standing still at the entrance: nothing moves the hero");
        assertEquals(MOB_X, theMob.getX(), 0.01f, "the guard never leaves its post for a hero outside its range");
        assertTrue(ZONE_LEFT - (START_X + 48) >= 150, "the guard's range starts far from the entrance's 48 px square");
        assertTrue(ROOM_END - 16 - (CHECKPOINT_X + 16) >= 48, "the checkpoint is clear of the right wall");
        assertTrue(CHECKPOINT_X - SLAB_END >= 48, "and clear of where a hero drops off the slab's end");
    }

    @Test void theGuardHoldsItsPostAndNeverPatrols() {
        Level level = room(0);
        // A RedMob has no patrol: it stands until the hero's body is inside its range and on its row.
        putHero(level, 2300, 272);
        for (int i = 0; i < 60 * 10; i++) { frame(level); alive(level); }
        assertEquals(MOB_X, theMob.getX(), 0.01f);
        assertEquals(RedMob.State.IDLE, theMob.getState());
        assertEquals(FLOOR, theMob.getY() + theMob.getHeight(), 0.01f);
    }

    @Test void aHeroOnTheSlabNeverWakesTheGuardHoweverCloseHeWalksAboveIt() {
        Level level = room(0);
        walkToLever(level); operate(level); walkToLadder(level); climb(level);
        for (int i = 0; i < 60 * 30 && !onFloor(level); i++) {
            held.clear(); held.add(Input.Keys.D);
            if (onSlab(level) && bodyRight(level) >= HOLE_LEFT - 6 && centerX(level) < HOLE_LEFT) jump();
            frame(level); alive(level);
            if (feet(level) < FLOOR - 2) {
                assertEquals(MOB_X, theMob.getX(), 0.01f, "at " + level.player.position);
                assertEquals(RedMob.State.IDLE, theMob.getState(), "at " + level.player.position);
            }
        }
        assertTrue(onFloor(level), "the walk ends on the floor beyond the slab: " + level.player.position);
        // Standing on the slab for 3 s right above the guard: the same.
        held.clear();
        Level above = room(0);
        putHero(above, HOLE_RIGHT + 14, DECK - 48 - 1);   // the slab's far side of the hole, above the guard's right half
        for (int i = 0; i < 180; i++) { frame(above); assertEquals(RedMob.State.IDLE, theMob.getState()); }
        assertTrue(onSlab(above), "the hero stands on the slab above the guard: " + above.player.position + " feet " + feet(above));
        assertEquals(MOB_X, theMob.getX(), 0.01f);
    }

    @Test void theGuardsReachStaysClearOfTheLeverTheLadderTheEndOfTheSlabAndTheEntrance() {
        // Lever reach: every hero x on the floor from which the lever can be pulled.
        Level probe = room(0);
        float reachLeft = Float.MAX_VALUE, reachRight = -Float.MAX_VALUE;
        for (float x = ROOM_X + 24; x < ROOM_END - 24; x += 2) {
            putHero(probe, x, 272);
            settle(probe, 2);
            if (probe.player.canInteract()) { reachLeft = Math.min(reachLeft, centerX(probe)); reachRight = Math.max(reachRight, centerX(probe)); }
        }
        assertTrue(reachLeft < Float.MAX_VALUE, "the lever has a reach");
        // The guard's farthest excursion: hold the hero still at each edge of its range and watch the guard come (until it hits him).
        float minDanger = Float.MAX_VALUE, maxDanger = -Float.MAX_VALUE;
        for (float edge : new float[]{ZONE_LEFT + 1, ZONE_RIGHT - 1}) {
            Level level = room(0);
            putHero(level, edge, 272);
            for (int i = 0; i < 60 * 6 && !level.player.isGotHit(); i++) {
                frame(level);
                Rectangle body = theMob.hitboxBounds(), whip = theMob.attackBounds();
                minDanger = Math.min(minDanger, Math.min(body.x, whip.x));
                maxDanger = Math.max(maxDanger, Math.max(body.x + body.width, whip.x + whip.width));
            }
        }
        System.out.printf(Locale.ROOT, "forest-1 guard room: lever reach x %.1f-%.1f, the guard's danger spans x %.1f-%.1f%n", reachLeft, reachRight, minDanger, maxDanger);
        assertTrue(minDanger - reachRight >= 48, "the lever's reach ends " + reachRight + ", the guard's nearest danger is " + minDanger);
        assertTrue(minDanger - (LADDER_X + 16 + 12) >= 48, "a hero on the ladder (x 2288-2304, body 12 px either side) is 48+ px from the guard's nearest danger " + minDanger);
        assertTrue(SLAB_END - maxDanger >= 48, "the slab's end is clear of the guard's farthest danger " + maxDanger);
        assertTrue(ZONE_LEFT - (START_X + 48) >= 150);
        assertEquals(MOB_X - 7 * 16, ZONE_LEFT, 0.01f, "the range starts 7 tiles left of the guard's x");
        assertEquals(MOB_X + 8 * 16, ZONE_RIGHT, 0.01f, "and ends 8 tiles right of it");
    }

    // ---- the four choices, each with zero charges unless it is the fireball ----

    @Test void theSwordRouteNeedsNoChargeAndKillsTheGuardWithoutAHit() {
        Level level = room(0);
        int frames = floorRoute(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 guard room, sword route: %.2f s%n", seconds);
        assertTrue(won(level));
        assertTrue(theMob.isDefeated());
        assertEquals(0, stock.count());
        assertTrue(level.objects.stream().noneMatch(Fireball.class::isInstance));
        assertFalse(ladder(level).isLowered(), "the sword route touches nothing else");
        assertTrue(seconds <= 12f, "mastered sword route " + seconds + " s, well inside the 20 s budget");
    }

    @Test void theTimingRouteLetsTheWhipMissAndStillKillsTheGuardWithoutAHit() {
        Level level = room(0);
        int frames = timingRoute(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 guard room, timing route (back off the raised whip, then punish): %.2f s, backed off for %d frames%n", seconds, retreatFrames);
        assertTrue(won(level));
        assertTrue(theMob.isDefeated());
        assertEquals(0, stock.count());
        assertTrue(retreatFrames >= 12, "the hero really waited out the wind-up: " + retreatFrames);
        assertTrue(seconds <= 14f, "timing route " + seconds + " s");
    }

    @Test void aStompOnTheFloorTakesTheGuardFromSeveralTakeoffDistancesAndNeedsNoCharge() {
        int ok = 0, tried = 0;
        for (int gap = 8; gap <= 90; gap += 2) {
            Level level = room(0);
            putHero(level, ZONE_LEFT - 60, 272);
            settle(level, 3);
            tried++;
            for (int i = 0; i < 60 * 4 && !theMob.isDefeated() && !level.player.isGotHit(); i++) {
                held.clear(); held.add(Input.Keys.D);
                float g = theMob.hitboxBounds().x - bodyRight(level);
                if (level.player.isOnGround() && g <= gap && g > gap - 4) jump();
                frame(level);
            }
            if (theMob.isDefeated() && !level.player.isGotHit()) ok++;
        }
        System.out.println("forest-1 guard room: a hop onto the guard works from " + ok + " of " + tried + " takeoff gaps");
        assertTrue(ok >= 3, "a stomp takes the guard from several takeoff distances: " + ok);
    }

    @Test void theSwordIsNoRiskAnySingleSwingKillsAndAHopOverTheGuardUsuallyPassesOrStomps() {
        // The finding behind D67 (the honest assessment of #104): what the guard is worth. One swing per attempt, started when the gap (body to body)
        // first drops to g. The swing keeps its hitbox for the whole animation and the guard walks into it, so there is no window to miss.
        StringBuilder window = new StringBuilder();
        int kills = 0, whipped = 0, tried = 0;
        for (int g = 0; g <= 64; g += 2) {
            Level level = room(0);
            putHero(level, ZONE_LEFT - 60, 272);
            settle(level, 3);
            boolean swung = false;
            for (int i = 0; i < 60 * 5 && !theMob.isDefeated() && !level.player.isGotHit(); i++) {
                held.clear(); held.add(Input.Keys.D);
                float gap = theMob.hitboxBounds().x - bodyRight(level);
                if (!swung && gap <= g && level.player.isOnGround()) { pressed.add(Input.Keys.K); swung = true; }
                frame(level);
            }
            tried++;
            boolean killed = theMob.isDefeated() && !level.player.isGotHit();
            if (killed) kills++; else if (level.player.isGotHit()) whipped++;
            window.append(killed ? 'K' : level.player.isGotHit() ? 'x' : '.');
        }
        System.out.println("forest-1 guard room, one swing started at gap 0,2,..64 (K kill, x whipped): " + window);
        int passed = 0, stomped = 0, hit = 0, hopTries = 0;
        for (int gap = 8; gap <= 90; gap += 2) {
            Level level = room(0);
            putHero(level, ZONE_LEFT - 60, 272);
            settle(level, 3);
            hopTries++;
            for (int i = 0; i < 60 * 6 && !theMob.isDefeated() && !level.player.isGotHit() && centerX(level) < ZONE_RIGHT + 40; i++) {
                held.clear(); held.add(Input.Keys.D);
                float g = theMob.hitboxBounds().x - bodyRight(level);
                if (level.player.isOnGround() && g <= gap && g > gap - 4) jump();
                frame(level);
            }
            if (level.player.isGotHit()) hit++; else if (theMob.isDefeated()) stomped++; else passed++;
        }
        System.out.println("forest-1 guard room, one hop from 42 takeoff gaps: passed over " + passed + ", stomped " + stomped + ", whipped " + hit);
        assertTrue(kills >= tried - 2, "one swing started at any gap of 0-64 px kills (measured 33 of 33): " + kills + " of " + tried + ", whipped " + whipped);
        assertTrue(passed >= 10, "a hop over the guard passes it from many takeoff gaps (measured 22 of 42): " + passed + " of " + hopTries);
        assertTrue(stomped >= 3, "and a stomp kills it from several (measured 17 of 42): " + stomped);
    }

    @Test void theFireballRouteSpendsOneChargeFromBeyondTheRangeAndIsTheFastest() {
        Level level = room(1);
        int frames = fireballRoute(level, ZONE_LEFT - 60);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 guard room, fireball route: %.2f s%n", seconds);
        assertTrue(won(level));
        assertEquals(0, stock.count(), "one charge spent");
        assertTrue(theMob.isDefeated());
        Level sword = room(0);
        float swordSeconds = floorRoute(sword) * STEP;
        assertTrue(seconds < swordSeconds - 0.2f, "the shot saves the stop-and-swing: " + seconds + " against " + swordSeconds);
        assertTrue(seconds <= 12f);
    }

    @Test void theOverheadRouteSkipsTheFightAndLeavesTheGuardAliveAtItsPost() {
        Level level = room(0);
        int frames = 0;
        frames += walkToLever(level);
        frames += operate(level);
        frames += walkToLadder(level);
        frames += climb(level);
        float onSlab = frames * STEP;
        frames += walkTheSlab(level);
        assertEquals(MOB_X, theMob.getX(), 0.01f);
        assertEquals(RedMob.State.IDLE, theMob.getState(), "the guard never woke on the way over");
        frames += floorRoute(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 guard room, overhead route: %.2f s (on the slab at %.2f s)%n", seconds, onSlab);
        assertTrue(won(level));
        assertFalse(theMob.isDefeated(), "the guard is alive: the hero never fought it");
        assertEquals(0, stock.count());
        assertTrue(ladder(level).isLowered(), "the ladder stays down");
        assertTrue(seconds <= 12f, "overhead route " + seconds + " s, inside the 20 s budget");
    }

    /** Steps into the hole from the slab and keeps the key down for {@code extra} more frames in the air, then lets go; the hero falls straight on. */
    private void dropThroughTheHole(Level level, int extra) {
        walkToLever(level); operate(level); walkToLadder(level); climb(level);
        for (int i = 0; i < 60 * 15 && level.player.isOnGround(); i++) { held.clear(); held.add(Input.Keys.D); frame(level); alive(level); }
        for (int i = 0; i < extra; i++) { held.clear(); held.add(Input.Keys.D); frame(level); }
        held.clear();
        assertFalse(level.player.isOnGround());
    }

    @Test void theHoleAboveTheGuardCanBeHoppedOrDroppedThroughAndTheDropIsAStompFromSeveralReleasePoints() {
        // Hopping it: swept over the takeoff point (a 32 px gap, one jump of about 88 px). Dropping through it: the hero walks to the edge, steps into
        // the hole and lets go of the key after 0-14 more frames in the air, falling straight down; where he lands decides whether it is a stomp.
        int hopped = 0, hopTries = 0, stomped = 0, dropTries = 0;
        for (float takeoff = HOLE_LEFT - 40; takeoff <= HOLE_LEFT + 4; takeoff += 4) {
            Level level = room(0);
            walkToLever(level); operate(level); walkToLadder(level); climb(level);
            hopTries++;
            for (int i = 0; i < 60 * 15 && !onFloor(level) && !level.player.isGotHit(); i++) {
                held.clear(); held.add(Input.Keys.D);
                if (onSlab(level) && bodyRight(level) >= takeoff && centerX(level) < HOLE_LEFT) jump();
                frame(level);
            }
            if (!level.player.isGotHit() && centerX(level) > SLAB_END - 20) hopped++;
        }
        for (int extra = 0; extra <= 14; extra++) {
            Level level = room(0);
            dropThroughTheHole(level, extra);
            dropTries++;
            for (int i = 0; i < 60 * 3 && !theMob.isDefeated() && !level.player.isGotHit() && !onFloor(level); i++) { held.clear(); frame(level); }
            frame(level);
            if (theMob.isDefeated() && !level.player.isGotHit()) { stomped++; assertEquals(RedMob.State.HIT, theMob.getState()); assertEquals(0, stock.count()); }
        }
        System.out.println("forest-1 guard room, the hole: hopped from " + hopped + " of " + hopTries + " takeoff points; a drop stomps the guard from "
            + stomped + " of " + dropTries + " release points");
        assertTrue(hopped >= 6, "the hop clears the hole from many takeoff points: " + hopped + " of " + hopTries);
        assertTrue(stomped >= 3, "the drop is a stomp from several release points: " + stomped + " of " + dropTries);
    }

    @Test void eachChoiceHasADifferentOutcomeAndEveryMasteredRouteFitsTheBudget() {
        record Outcome(String name, float seconds, int charges, boolean guardDead, boolean ladderDown) {}
        List<Outcome> all = new ArrayList<>();
        Level sword = room(1);
        int f = floorRoute(sword);
        all.add(new Outcome("sword", f * STEP, stock.count(), theMob.isDefeated(), ladder(sword).isLowered()));
        Level timing = room(1);
        f = timingRoute(timing);
        all.add(new Outcome("timing", f * STEP, stock.count(), theMob.isDefeated(), ladder(timing).isLowered()));
        Level shot = room(1);
        f = fireballRoute(shot, ZONE_LEFT - 60);
        all.add(new Outcome("fireball", f * STEP, stock.count(), theMob.isDefeated(), ladder(shot).isLowered()));
        Level over = room(1);
        f = overheadRoute(over);
        all.add(new Outcome("overhead", f * STEP, stock.count(), theMob.isDefeated(), ladder(over).isLowered()));
        for (Outcome o : all) {
            System.out.printf(Locale.ROOT, "forest-1 guard room outcome: %-9s %5.2f s  charges left %d  guard dead %-5s ladder down %-5s%n", o.name, o.seconds, o.charges, o.guardDead, o.ladderDown);
            assertTrue(o.seconds <= BUDGET, o.name + " fits the 20 s mastered budget: " + o.seconds);
        }
        // Different choices leave different worlds behind.
        assertEquals(1, all.get(0).charges); assertEquals(1, all.get(1).charges); assertEquals(0, all.get(2).charges); assertEquals(1, all.get(3).charges);
        assertTrue(all.get(0).guardDead && all.get(1).guardDead && all.get(2).guardDead && !all.get(3).guardDead);
        assertTrue(!all.get(0).ladderDown && !all.get(1).ladderDown && !all.get(2).ladderDown && all.get(3).ladderDown);
        assertTrue(all.get(2).seconds < all.get(0).seconds, "the shot is the fastest kill");
        assertTrue(all.get(3).seconds > all.get(0).seconds, "the overhead route costs more time than the sword");
        assertTrue(all.get(1).seconds > all.get(0).seconds, "the timing route costs more time than the sword");
    }

    // ---- the zero-fireball guarantee and D26 ----

    @Test void everyRouteThatNeedsNoChargeWorksWithAnEmptyStock() {
        Level a = room(0); floorRoute(a); assertTrue(won(a));
        Level b = room(0); timingRoute(b); assertTrue(won(b));
        Level c = room(0); overheadRoute(c); assertTrue(won(c));
        assertEquals(0, stock.count());
    }

    /** Lures the guard out of its post: the hero stands on the floor at {@code edge} until it has come {@code px} px, then walks out of its range. */
    private void lure(Level level, float edge, float px) {
        putHero(level, edge, 272);
        float start = theMob.getX();
        for (int i = 0; i < 60 * 6 && Math.abs(theMob.getX() - start) < px; i++) { held.clear(); frame(level); alive(level); }
        boolean right = edge > MOB_X;
        for (int i = 0; i < 60 * 3 && (right ? centerX(level) < ZONE_RIGHT + 20 : centerX(level) > ZONE_LEFT - 20); i++) {
            held.clear(); held.add(right ? Input.Keys.D : Input.Keys.A);
            frame(level); alive(level);
        }
        held.clear(); settle(level, 10);
    }

    /** From wherever the hero stands (the slab, the ladder, the floor) to the checkpoint with the sword, no lever, no charge. */
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

    @Test void everyLeverAndGuardStateStillReachesTheCheckpointWithZeroChargesAndWithoutRestartLevel() {
        String[] guardStates = {"at its post", "lured left", "lured right", "dead"};
        String[] places = {"entrance", "ladder foot", "left slab", "above the hole's far side"};
        int runs = 0;
        for (int lever = 0; lever <= 2; lever++) {                    // pulls: 0 = raised, 1 = lowered, 2 = raised again
            for (int g = 0; g < guardStates.length; g++) {
                for (int where = 0; where < places.length; where++) {
                    if (where >= 2 && lever != 1) continue;           // the slab can only be reached, and so be stood on, with the ladder down
                    String ctx = "lever pulls " + lever + ", guard " + guardStates[g] + ", hero at the " + places[where];
                    Level level = room(0);
                    if (lever > 0) { walkToLever(level); for (int p = 0; p < lever; p++) { operate(level); settle(level, 90); } }
                    if (g == 1) lure(level, ZONE_LEFT + 1, 40);
                    if (g == 2) lure(level, ZONE_RIGHT - 1, 40);
                    if (g == 3) theMob.collidedWithActor(true);       // any kill goes through here: sword, fireball, stone or a stomp
                    float[] at = {START_X + 23.5f, LADDER_X + 8, 2400, HOLE_RIGHT + 20};
                    putHero(level, at[where], where >= 2 ? DECK - 48 - 1 : 272);
                    settle(level, 20);
                    try { recover(level); } catch (AssertionError e) { throw new AssertionError(ctx + ": " + e.getMessage(), e); }
                    assertTrue(won(level), ctx);
                    assertEquals(0, stock.count());
                    runs++;
                }
            }
        }
        System.out.println("forest-1 guard room D26 sweep: " + runs + " runs, 0 failures, no Restart Level");
        assertEquals(4 * 2 + 4 * 4 + 4 * 2, runs);
    }

    @Test void aHeroWhoStepsIntoTheHoleFallsNextToTheGuardAndTheSwordStillFinishes() {
        for (int extra = 0; extra <= 14; extra += 2) {
            Level level = room(0);
            dropThroughTheHole(level, extra);
            recover(level);
            assertTrue(won(level), "drop with " + extra + " frames of key in the air");
            assertEquals(0, stock.count());
        }
    }

    @Test void deathAndRetryRestoreTheAuthoredRoomAndBringTheGuardBackAndTheTimeIsTheSame() {
        Level level = room(1);
        float first = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) { level.resetCurrentRoom(player()); theMob = mob(level); }
            assertEquals(1, stock.count(), "a retry rolls the unsecured charge back");
            assertFalse(theMob.isDefeated(), "the guard is back alive on a first visit");
            assertEquals(MOB_X, theMob.getX(), 0.01f);
            assertFalse(ladder(level).isLowered());
            held.clear(); pressed.clear();
            float seconds = fireballRoute(level, ZONE_LEFT - 60) * STEP;
            if (attempt == 0) first = seconds; else assertEquals(first, seconds, STEP, "a retry reproduces the route time");
            assertEquals(0, stock.count());
        }
        // Death by the whip rolls the world back too, and a lured or killed guard comes back to its post.
        Level hurt = room(0);
        putHero(hurt, ZONE_LEFT + 4, 272);
        for (int i = 0; i < 60 * 5 && !hurt.player.isGotHit(); i++) { held.clear(); frame(hurt); }
        assertTrue(hurt.player.isGotHit(), "a hero who stands in front of the guard and does nothing is whipped");
        assertNotEquals(MOB_X, theMob.getX(), "the guard had left its post to do it");
        hurt.resetCurrentRoom(player());
        theMob = mob(hurt);
        assertEquals(START_X, hurt.player.position.x, 0.01f);
        assertFalse(theMob.isDefeated());
        assertEquals(MOB_X, theMob.getX(), 0.01f, "back at its post");
        floorRoute(hurt);
        assertTrue(won(hurt));
    }

    @Test void deathAfterTheLeverPullRestoresTheRaisedLadderAndTheLeverPicture() {
        Level level = room(0);
        walkToLever(level); operate(level); settle(level, 90);
        assertTrue(ladder(level).isLowered());
        assertTrue(lever(level).isUp());
        level.resetCurrentRoom(player());
        assertFalse(ladder(level).isLowered(), "an unsecured lever pull is undone by a death");
        assertFalse(lever(level).isUp());
        overheadRoute(level);
        assertTrue(won(level));
    }

    @Test void aSecuredKillAndASecuredLoweredLadderStayAsTheyWereAndTheExitStillWorks() {
        // The guard room is last, so nothing secures it: secureVisit() is what a forward exit or a healer return calls, then resetCurrentRoom applies
        // the snapshot as a revisit does. State to secure: ladder down, guard killed (here with the sword, back from the slab's far end).
        Level level = room(0);
        walkToLever(level); operate(level); walkToLadder(level); climb(level); walkTheSlab(level);
        assertTrue(onFloor(level));
        actionWasDown = false;
        for (int i = 0; i < 60 * 10 && !theMob.isDefeated(); i++) {
            held.clear(); held.add(Input.Keys.A);
            actionWasDown = defend(level);
            frame(level); alive(level);
        }
        assertTrue(theMob.isDefeated());
        level.secureVisit();
        level.resetCurrentRoom(player());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof RedMob m && !m.isDefeated()), "a secured kill stays a kill");
        assertTrue(ladder(level).isLowered(), "the ladder stays as left (D26)");
        assertTrue(lever(level).isUp());
        settle(level, 10);
        int frames = 0;
        while (!won(level)) {
            held.clear(); held.add(Input.Keys.D);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 30);
        }
        assertEquals(0, stock.count());
        // ... and a secured live guard is back at its post, the ladder as left, and the floor route still works.
        Level live = room(0);
        walkToLever(live); operate(live); settle(live, 90);
        live.secureVisit();
        live.resetCurrentRoom(player());
        theMob = mob(live);
        assertFalse(theMob.isDefeated());
        assertTrue(ladder(live).isLowered());
        floorRoute(live);
        assertTrue(won(live));
    }

    @Test void restartLevelRebuildsTheGuardAndTheLadderFromTheMap() {
        Level level = room(0);
        overheadRoute(level);
        Level again = room(0);
        assertFalse(ladder(again).isLowered());
        assertFalse(theMob.isDefeated());
        assertEquals(MOB_X, theMob.getX(), 0.01f);
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
                for (float y : new float[]{272, DECK - 48, 96 - 48}) {
                    putHero(level, x, y);
                    settle(level, 2);
                    if (level.player.canInteract()) assertTrue(feet(level) > FLOOR - 4, "x=" + x + " y=" + y);
                }
            }
        }
    }
}
