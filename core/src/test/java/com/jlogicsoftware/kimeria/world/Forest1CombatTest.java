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
import com.jlogicsoftware.kimeria.entity.objects.Puffball;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest-1 guard room (#104, D68): a RedMob stands in a low tunnel on the floor and a spore puffball's cloud covers exactly the places from which the
 * sword reaches it, so melee is a timed risk. The tunnel's roof is the optional route: a lever lowers a ladder to it, a fireball refill lies on it. The
 * floor (timed melee) is the required route and needs no charge. Ordinary keyboard input drives a scripted hero through the real shipped map; the times
 * are author (mastered) measurements, not novice playtests. Recipe and finding: docs/ROOMS.md and D68.
 *
 * <p>Y grows downward: the floor top is y 320, the tunnel's ceiling y 288 (32 px up), the roof top y 208 (112 px up). Everything below is a map
 * coordinate (room 3 starts at x 2112).
 */
class Forest1CombatTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float ROOM_X = 2112, ROOM_END = 2880, START_X = 2176, FLOOR = 320, DECK = 208, TUNNEL_CEILING = 288;
    private static final float LEVER_X = 2224, LADDER_X = 2288, LADDER_CENTRE = 2296, CHECKPOINT_X = 2800, REFILL_X = 2640, SLAB_END = 2704;
    private static final float ROOF_LEFT = 2304, ROOF_RIGHT = 2560;
    /** The guard's art x (its body is x + 6 .. x + 26); it never moves (range 0). The puffball (16 x 16 at x 2375) casts a cloud x 2373-2393. */
    private static final float GUARD_X = 2394, PUFF_X = 2375, CLOUD_LEFT = 2373, CLOUD_RIGHT = 2393;
    /** The hero waits (patient policies) with his centre at or left of this x, outside the cloud (its left edge is 2373; his body is 11 px wide). */
    private static final float WAIT_CENTRE = 2360;
    /** The puffball's authored phase and cycle (period 4 s: calm 2.2, swell 0.8, cloud 1.0). */
    private static final float PHASE = 2.2f, CALM_END = 2.2f;
    /** How much calm a patient hero wants left when he steps in: to walk in and swing (melee) or only to cross the cloud's width (a shot guard). */
    private static final float NEED_MELEE = 1.0f, NEED_CROSS = 0.5f;
    private static final float BUDGET = 20f;
    /** Pinned measurements (D68): scripted hero, 60 frames per second. A route may differ from these by at most this much (6 frames). */
    private static final float PIN_MARGIN = 0.1f;
    private static final float MELEE_S = 6.83f, SHOT_S = 6.15f, ROOF_S = 7.67f;
    private static final int IMPATIENT_OK = 30;
    private static final int RUNS = 216;

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
        level.enterNextRoom(player()); // forest-1-mob (#375) -> forest-1-traps
        level.enterNextRoom(player()); // -> forest-1-lever (#418)
        level.enterNextRoom(player());
        level.enterNextRoom(player());
        theMob = mob(level);
        return level;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private Ladder ladder(Level level) { return level.ladders.get(0); }
    private Lever lever(Level level) { return level.objects.stream().filter(Lever.class::isInstance).map(Lever.class::cast).findFirst().orElseThrow(); }
    private Puffball puff(Level level) { return level.objects.stream().filter(Puffball.class::isInstance).map(Puffball.class::cast).findFirst().orElseThrow(); }
    /** The room's guard; after a retry the level builds a new one, so this looks it up (a slain guard leaves the list, then the old one is returned). */
    private RedMob mob(Level level) { return level.objects.stream().filter(RedMob.class::isInstance).map(RedMob.class::cast).findFirst().orElse(theMob); }
    private FireballRefill refill(Level level) { return level.objects.stream().filter(FireballRefill.class::isInstance).map(FireballRefill.class::cast).findFirst().orElse(null); }
    private boolean refillGone(Level level) { return level.objects.stream().noneMatch(o -> o instanceof FireballRefill && !o.isRemoved()); }
    private float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }
    private float centerX(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }
    /** Since #349 the guard room ends at a RoomExit (the checkpoint moved to the tree room's top), at the x the checkpoint stood at. */
    private boolean won(Level level) { return level.atRoomExit(); }
    private boolean dead(Level level) { return level.player.isGotHit(); }
    private void alive(Level level) { assertFalse(dead(level), "died at " + level.player.position + " guard " + theMob.getState() + " cloud " + puff(level).currentPhase()); }
    private boolean onFloor(Level level) { return feet(level) > FLOOR - 2 && !level.player.onLadder(); }
    /** The hero's hitbox centre is 23.5 px right of position.x (hitbox offset 18, width 11). */
    private void putHero(Level level, float cx, float y) { level.player.position.set(cx - 23.5f, y); level.player.velocity.setZero(); }
    private boolean guardUntouched() { return theMob.getState() == RedMob.State.IDLE && theMob.getX() == GUARD_X; }

    /** A live enemy on the hero's row, ahead of him and closer than {@code within} px (body to body). */
    private boolean enemyAhead(Level level, float within) {
        Rectangle me = level.player.hitboxBounds();
        Rectangle en = theMob.hitboxBounds();
        return !theMob.isDefeated() && level.player.isOnGround() && en.x >= me.x && en.x - (me.x + me.width) < within
            && en.y < me.y + me.height && en.y + en.height > me.y;
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

    /** Is the cloud far enough away for a hero who needs {@code need} seconds of calm? (Calm is cycle time 0 to 2.2 s; swell and cloud follow.) */
    private boolean calmFor(Level level, float need) { return puff(level).cycleTime() <= CALM_END - need; }

    /**
     * The floor route with ordinary keys. A patient hero stops short of the cloud (his centre at or left of {@value #WAIT_CENTRE}) until the calm is
     * long enough ({@code need}), then walks in; with the guard alive he swings once from within 16 px of it; he never stops inside the cloud's width
     * except to swing. An impatient hero does not wait. Ends at the room exit or at his death (the caller checks).
     */
    private int floorRoute(Level level, boolean patient) {
        int frames = 0;
        actionWasDown = false;
        while (!won(level) && !dead(level)) {
            held.clear(); held.add(Input.Keys.D);
            boolean guardUp = !theMob.isDefeated();
            float need = guardUp ? NEED_MELEE : NEED_CROSS;
            boolean atWait = centerX(level) >= WAIT_CENTRE && centerX(level) <= WAIT_CENTRE + 4;
            if (patient && atWait && !calmFor(level, need)) held.clear();   // wait outside the cloud
            boolean melee = enemyAhead(level, 16);
            if (melee && !actionWasDown) pressed.add(Input.Keys.K);
            actionWasDown = melee;
            frame(level);
            assertTrue(++frames < 60 * 40, "the room exit was not reached from " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** The timed melee, patient: the required route. */
    private int meleeRoute(Level level) { int f = floorRoute(level, true); alive(level); return f; }

    /** The shot: fired from {@code fromX} (his centre) by a hero walking on, then the patient walk. */
    private int shotRoute(Level level, float fromX) {
        int frames = 0;
        boolean shot = false;
        actionWasDown = false;
        while (!won(level) && !dead(level)) {
            held.clear(); held.add(Input.Keys.D);
            if (!shot && centerX(level) >= fromX) { pressed.add(Input.Keys.L); shot = true; }
            boolean up = !theMob.isDefeated();
            boolean atWait = centerX(level) >= WAIT_CENTRE && centerX(level) <= WAIT_CENTRE + 4;
            if (atWait && (up || !calmFor(level, NEED_CROSS))) held.clear();
            frame(level); frames++;
            assertTrue(frames < 60 * 40, "shot route stuck at " + level.player.position);
        }
        held.clear();
        alive(level);
        assertTrue(shot);
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

    /** The roof route: lever, ladder, roof, refill, drop, room exit; never touches the floor under the roof. */
    private int roofRoute(Level level) {
        int frames = walkToLever(level);
        frames += operate(level);
        assertTrue(ladder(level).isLowered());
        frames += walkToLadder(level);
        frames += climbTo(level, DECK);
        held.clear(); frame(level); frames++;
        while (!won(level)) {
            held.clear(); held.add(Input.Keys.D);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 40, "roof walk stuck at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    // ---- census and geometry ----

    @Test void theRoomHoldsOneLeverOneRaisedLadderOneTunnelWithAGuardAndAPuffballOneRefillAndTheRoomExitAndNothingElse() {
        Level level = room(0);
        assertEquals("forest-1-guard", level.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, 768, 368), level.currentRoomBounds());
        assertTrue(level.hasNextRoom(), "the tree room follows (#349): the guard room's checkpoint became its RoomExit");
        assertEquals(1, level.triggers.size());
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.ladders.size());
        assertEquals(0, level.escalators.size());
        assertEquals(0, level.fallingPlatforms.size(), "no one-shot support: the lever is a reversible toggle");
        assertEquals(1, level.objects.stream().filter(o -> o instanceof Enemy<?>).count(), "one monster, the RedMob");
        assertEquals(1, level.objects.stream().filter(Puffball.class::isInstance).count(), "one trap, the spore puffball: no thorns, no second monster");
        assertEquals(1, level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.objects.LethalHazard).count());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Wall));
        assertEquals(0, level.objects.stream().filter(Checkpoint.class::isInstance).count(), "the checkpoint is at the tree room's top (#349)");
        assertEquals(1, level.objects.stream().filter(FireballRefill.class::isInstance).count(), "one refill, and no coin (coins feed the healer recipe)");
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Collectable));
        assertFalse(ladder(level).isLowered());
        assertFalse(lever(level).isUp());
        assertEquals(LADDER_X, ladder(level).getX(), 0.01f);
        assertEquals(DECK, ladder(level).bottomY(), 0.01f, "raised: its foot hangs 112 px above the floor");
        assertEquals(GUARD_X, theMob.getX(), 0.01f);
        assertEquals(FLOOR, theMob.getY() + theMob.getHeight(), 0.01f, "the guard stands on the floor");
        assertTrue(theMob.hitboxBounds().x > ROOF_LEFT + 64 && theMob.hitboxBounds().x + theMob.hitboxBounds().width < ROOF_RIGHT, "inside the tunnel");
        Puffball puff = puff(level);
        assertEquals(PUFF_X, puff.getX(), 0.01f);
        assertEquals(PHASE, puff.cycleTime(), 0.05f, "the authored phase");
        assertEquals(4f, puff.period(), 0.01f);
        assertEquals(1f, puff.cloudSeconds(), 0.01f);
        Rectangle cloud = puff.cloudBounds();
        assertEquals(CLOUD_LEFT, cloud.x, 0.01f);
        assertEquals(CLOUD_RIGHT, cloud.x + cloud.width, 0.01f);
        var roof = level.collisionBlocks.stream().filter(b -> b.getX() == ROOF_LEFT && b.getY() == 224).findFirst().orElseThrow();
        assertEquals(TUNNEL_CEILING, roof.getY() + roof.getHeight(), 0.01f);
        assertEquals(ROOF_RIGHT, roof.getX() + roof.getWidth(), 0.01f);
        FireballRefill refill = refill(level);
        assertEquals(REFILL_X, refill.getX(), 0.01f);
        assertEquals(DECK, refill.getY() + refill.getHeight(), 0.01f, "the refill sits on the slab beyond the tunnel's roof");
        Rectangle exit = new RoomLayout(com.jlogicsoftware.kimeria.tiled.TiledMapData.load("tiles/forest-1.tmx", h.assets())).rooms.get(4).exit(); // room 5 since #375 and #418
        assertEquals(CHECKPOINT_X, exit.x, 0.01f, "the exit stands where the checkpoint stood, so the route times keep their meaning");
        assertEquals(FLOOR, exit.y + exit.height, 0.01f, "it stands on the floor");
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals("", level.player.zoneHint(), "no hints");
    }

    @Test void theEntranceIsSafeAndFarFromTheTrapsAndTheExitIsClearOfTheRightWallAndTheDrop() {
        Level level = room(0);
        assertEquals(272, level.player.position.y, 0.01f);
        assertFalse(level.player.canInteract());
        settle(level, 60 * 9);   // two and a bit puffball cycles
        alive(level);
        assertEquals(START_X, level.player.position.x, 0.5f, "standing still at the entrance: nothing moves the hero");
        assertTrue(guardUntouched());
        assertTrue(CLOUD_LEFT - (START_X + 48) >= 140, "the cloud is far from the entrance's 48 px square");
        assertTrue(ROOM_END - 16 - (CHECKPOINT_X + 16) >= 48, "the exit is clear of the right wall");
        assertTrue(CHECKPOINT_X - SLAB_END >= 48, "and clear of where a hero drops off the slab's end");
    }

    @Test void theGuardNeverMovesAndOnlyStrikesAHeroWhoComesWithin6PxOfIt() {
        Level level = room(0);
        putHero(level, 2352, 272);                       // outside the cloud, 40 px short of the guard's body: stands for 10 s (2.5 cycles)
        for (int i = 0; i < 600; i++) { frame(level); alive(level); assertTrue(guardUntouched()); }
        // A hero at 7-17 px (the sword's reach beyond the whip's) is never touched by the whip: only by the cloud, which is what the room is about.
        float guardLeft = theMob.hitboxBounds().x;
        for (int gap = 7; gap <= 17; gap++) {
            Level l = room(0);
            putHero(l, guardLeft - gap - 5.5f, 272);
            for (int i = 0; i < 40; i++) { frame(l); assertTrue(!l.player.isGotHit() || puff(l).currentPhase() == Puffball.Phase.CLOUD, "gap " + gap); }
            assertTrue(guardUntouched(), "gap " + gap);
        }
    }

    @Test void theTunnelIsTooLowToJumpOverTheGuardOrToStompIt() {
        Level level = room(0);
        float maxX = 0;
        for (int i = 0; i < 60 * 20 && !dead(level); i++) {
            held.clear(); held.add(Input.Keys.D);
            if (i % 10 == 0) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
            if (centerX(level) >= WAIT_CENTRE && !calmFor(level, 0.6f) && centerX(level) < CLOUD_LEFT) held.remove(Input.Keys.D);
            if (centerX(level) > 2395) break;
            frame(level);
            maxX = Math.max(maxX, centerX(level));
            Rectangle body = level.player.hitboxBounds();
            if (body.x + body.width > ROOF_LEFT && body.x < ROOF_RIGHT)
                assertTrue(body.y >= TUNNEL_CEILING - 0.01f, "the hero cannot rise above the guard's top inside the tunnel: " + body);
        }
        assertFalse(theMob.isDefeated(), "no stomp is possible under a 32 px ceiling");
        assertTrue(maxX < GUARD_X + 6, "a jumping hero never gets past the guard: " + maxX);
    }

    @Test void theCloudCoversEveryPlaceFromWhichTheSwordReachesTheGuardAndTheTellIsTheShared0Point8SecondSwell() {
        Level level = room(0);
        Puffball puff = puff(level);
        Rectangle guard = theMob.hitboxBounds();
        // Sword reach: 17 px past the hero's body; whip: 6 px past the guard's. A hero stands at a gap of 7 to 17 px (and fractions between).
        int covered = 0, tried = 0;
        for (float gap = 7; gap <= 17; gap += 0.5f) {
            Rectangle body = new Rectangle(guard.x - gap - 11, FLOOR - 22, 11, 22);
            tried++;
            if (puff.cloudBounds().overlaps(body)) covered++;
        }
        assertEquals(tried, covered, "every standing place within the sword's reach and outside the whip's is inside the cloud's width");
        int calm = 0, swell = 0, cloud = 0;
        for (int i = 0; i < 4 * 60; i++) {
            switch (puff.currentPhase()) { case CALM -> calm++; case SWELL -> swell++; case CLOUD -> cloud++; }
            frame(level);
        }
        assertEquals(2.2f, calm * STEP, 0.05f);
        assertEquals(0.8f, swell * STEP, 0.05f);
        assertEquals(1.0f, cloud * STEP, 0.05f);
        for (boolean inCloud : new boolean[]{true, false}) {
            Level l = room(0);
            while ((puff(l).currentPhase() == Puffball.Phase.CLOUD) != inCloud) frame(l);
            putHero(l, guard.x - 12 - 5.5f, 272);
            frame(l); frame(l);
            assertEquals(inCloud, dead(l), "standing in the band, cloud out: " + inCloud);
        }
    }

    // ---- the choices and the numbers behind them ----

    private record Attempt(boolean alive, boolean guardDead, int frames) {}

    /** Waits {@code idle} frames at the entrance (so that the cloud is at a known phase), then puts the hero 30 px before the cloud and runs the floor route. */
    private Attempt meleeAfter(int idle, boolean patient) {
        Level level = room(0);
        settle(level, idle);
        putHero(level, 2330, 272);
        settle(level, 1);
        int frames = floorRoute(level, patient);
        return new Attempt(!dead(level), theMob.isDefeated(), frames);
    }

    @Test void aPatientMeleeAlwaysSucceedsAnImpatientOneDiesInAboutHalfTheCyclesAndTheWaitIsTheCost() {
        int cycleFrames = 240, step = 4, tried = 0, impatientOk = 0, patientOk = 0;
        long impatientFrames = 0, patientFrames = 0;
        StringBuilder window = new StringBuilder();
        for (int idle = 0; idle < cycleFrames; idle += step) {
            Attempt rash = meleeAfter(idle, false);
            Attempt careful = meleeAfter(idle, true);
            tried++;
            if (rash.alive && rash.guardDead) { impatientOk++; impatientFrames += rash.frames; }
            if (careful.alive && careful.guardDead) { patientOk++; patientFrames += careful.frames; }
            window.append(rash.alive && rash.guardDead ? 'K' : 'x');
        }
        System.out.printf(Locale.ROOT, "forest-1 guard room, melee from 30 px before the cloud, started 0, 4, .. 236 frames after the room began (K kill, x dead): %s%n"
            + "  impatient: %d of %d succeed, %.2f s on average when they do; patient (waits for 1.0 s of calm): %d of %d, %.2f s on average%n",
            window, impatientOk, tried, impatientFrames / (double) Math.max(1, impatientOk) * STEP, patientOk, tried, patientFrames / (double) Math.max(1, patientOk) * STEP);
        assertEquals(IMPATIENT_OK, impatientOk, "the melee must come while 0.5 s or more of calm are left: " + window);
        assertEquals(tried, patientOk, "a hero who waits for the calm always succeeds");
    }

    /** A real player's first walk: from the room's entry (no teleport, so the cloud is at its authored phase), idle {@code delay} frames, then hold Right and swing from 16 px. */
    private Attempt straightRun(int delay) {
        Level level = room(0);
        settle(level, delay);
        int frames = floorRoute(level, false);
        return new Attempt(!dead(level), theMob.isDefeated(), frames + delay);
    }

    @Test void aStraightRunFromTheRoomsEntryAlwaysMeetsTheSamePhaseSoItIsADeterministicFirstDeathAndWaitingASecondOrSoSucceeds() {
        StringBuilder row = new StringBuilder();
        int firstOk = -1, lastDead = -1;
        for (int delay = 0; delay <= 90; delay += 3) {
            Attempt a = straightRun(delay);
            boolean ok = a.alive && a.guardDead;
            row.append(ok ? 'K' : 'x');
            if (ok && firstOk < 0) firstOk = delay;
            if (!ok) lastDead = delay;
        }
        Attempt none = straightRun(0), same = straightRun(0);
        System.out.println("forest-1 guard room, a straight run from the room's entry after a delay of 0, 3, .. 90 frames (K kill, x dead): " + row
            + "; the delay-0 run dies at frame " + none.frames + " and takes the same course twice");
        assertFalse(none.alive, "holding Right from the entry and swinging from 16 px walks into the cloud: the first death");
        assertEquals(none.alive, same.alive, "the retry replays the identical phase");
        assertEquals(FIRST_WORKING_DELAY, firstOk, "the first delay that works: " + row);
        assertEquals(6, lastDead, "every delay up to 6 frames (0.1 s) dies, every one from 9 frames (0.15 s) on works: " + row);
    }
    private static final int FIRST_WORKING_DELAY = 9;

    @Test void theMasteredMeleeNeedsNoChargeAndKillsTheGuardWithoutAHit() {
        Level level = room(0);
        int frames = meleeRoute(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 guard room, mastered melee (zero charges): %.2f s%n", seconds);
        assertTrue(won(level));
        assertTrue(theMob.isDefeated());
        assertEquals(0, stock.count());
        assertTrue(level.objects.stream().noneMatch(Fireball.class::isInstance));
        assertFalse(ladder(level).isLowered());
        assertFalse(refillGone(level), "the refill is optional: the floor leaves it where it is");
        assertEquals(MELEE_S, seconds, PIN_MARGIN);
    }

    @Test void aMistimedMeleeDiesInTheCloudAndRetryRestoresTheAuthoredRoomAndPhase() {
        Level level = room(0);
        settle(level, 10);                               // the swell is about to start (cycle time 2.2 s at the room's start)
        putHero(level, 2330, 272);
        floorRoute(level, false);
        assertTrue(dead(level), "an impatient hero who steps into the band while the swell runs out is killed by the cloud");
        level.resetCurrentRoom(player());
        theMob = mob(level);
        assertEquals(PHASE, puff(level).cycleTime(), 0.05f, "the cycle restarts at its authored phase");
        assertFalse(theMob.isDefeated());
        assertEquals(GUARD_X, theMob.getX(), 0.01f);
        assertFalse(ladder(level).isLowered());
        assertFalse(refillGone(level));
        assertEquals(START_X, level.player.position.x, 0.01f);
        settle(level, 90);
        float first = meleeRoute(level) * STEP;
        level.resetCurrentRoom(player());
        theMob = mob(level);
        settle(level, 90);
        assertEquals(first, meleeRoute(level) * STEP, STEP, "a retry reproduces the route time");
    }

    @Test void aShotFromAfarKillsTheGuardOutsideTheCloudAndTheHeroStillCrossesInTheCalm() {
        Level level = room(1);
        int frames = shotRoute(level, 2290);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 guard room, fireball from the ladder's foot, then the patient crossing: %.2f s%n", seconds);
        assertTrue(won(level));
        assertEquals(0, stock.count(), "one charge spent");
        assertTrue(theMob.isDefeated());
        assertEquals(SHOT_S, seconds, PIN_MARGIN);
        int ok = 0, tried = 0;
        for (int idle = 0; idle < 240; idle += 8) {
            Level l = room(1);
            settle(l, idle);
            putHero(l, 2280, 272);
            settle(l, 1);
            shotRoute(l, 2290);
            tried++;
            if (won(l) && !dead(l)) ok++;
        }
        System.out.println("forest-1 guard room, a shot then a patient crossing, " + tried + " start phases: " + ok + " succeed");
        assertEquals(tried, ok);
    }

    @Test void theRoofRouteSkipsTheTunnelTakesTheRefillAndNeedsNoChargeAtAnyPhase() {
        Level level = room(0);
        int frames = roofRoute(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 guard room, roof route: %.2f s%n", seconds);
        assertTrue(won(level));
        assertEquals(1, stock.count(), "the refill paid one charge");
        assertTrue(refillGone(level));
        assertTrue(guardUntouched());
        assertFalse(theMob.isDefeated());
        assertEquals(ROOF_S, seconds, PIN_MARGIN);
        assertTrue(ladder(level).isLowered());
        for (int idle = 0; idle < 240; idle += 24) {
            Level l = room(0);
            settle(l, idle);
            roofRoute(l);
            assertTrue(won(l));
        }
    }

    @Test void aFullStockMakesTheRefillWorthlessAndTheRoofIsThenOnlyTheSafeSlowRoute() {
        Level full = room(3);
        roofRoute(full);
        assertTrue(won(full));
        assertEquals(3, stock.count(), "a full stock gains nothing");
        assertFalse(refillGone(full), "and the pickup stays where it is (D27)");
        Level low = room(2);
        roofRoute(low);
        assertEquals(3, stock.count(), "below a full stock the refill is a charge won");
    }

    @Test void eachChoiceLeavesADifferentWorldAndEveryMasteredRouteFitsTheBudget() {
        Level melee = room(1);
        float meleeS = meleeRoute(melee) * STEP;
        int meleeCharges = stock.count(); boolean meleeDead = theMob.isDefeated(), meleeRefill = refillGone(melee), meleeLadder = ladder(melee).isLowered();
        Level shot = room(1);
        float shotS = shotRoute(shot, 2290) * STEP;
        int shotCharges = stock.count(); boolean shotDead = theMob.isDefeated(), shotRefill = refillGone(shot);
        Level roof = room(1);
        float roofS = roofRoute(roof) * STEP;
        int roofCharges = stock.count(); boolean roofDead = theMob.isDefeated(), roofRefill = refillGone(roof), roofLadder = ladder(roof).isLowered();
        System.out.printf(Locale.ROOT, "forest-1 guard room outcome (stock 1): melee %.2f s charges %d guard dead %s; shot %.2f s charges %d guard dead %s; roof %.2f s charges %d guard dead %s refill taken %s%n",
            meleeS, meleeCharges, meleeDead, shotS, shotCharges, shotDead, roofS, roofCharges, roofDead, roofRefill);
        for (float s : new float[]{meleeS, shotS, roofS}) assertTrue(s <= 12f && s <= BUDGET, "well inside the 20 s budget: " + s);
        assertEquals(1, meleeCharges); assertEquals(0, shotCharges); assertEquals(2, roofCharges);
        assertTrue(meleeDead && shotDead && !roofDead);
        assertTrue(!meleeRefill && !shotRefill && roofRefill);
        assertTrue(!meleeLadder && roofLadder);
    }

    // ---- clearances for a standing, a jumping and a climbing hero ----

    private static float gap(Rectangle a, Rectangle b) {
        float dx = Math.max(0, Math.max(a.x - (b.x + b.width), b.x - (a.x + a.width)));
        float dy = Math.max(0, Math.max(a.y - (b.y + b.height), b.y - (a.y + a.height)));
        return (float) Math.hypot(dx, dy);
    }

    @Test void theTrapsAndTheGuardStayFortyEightPxClearOfTheLeversReachAndOfTheLadderForAStandingAJumpingAndAClimbingHero() {
        Level probe = room(0);
        Rectangle reach = null;
        for (float x = ROOM_X + 24; x < ROOM_END - 24; x += 2) {
            for (float y = 272 - 60; y <= 272; y += 4) {                 // standing, and every height of a jump over the lever
                putHero(probe, x, y);
                settle(probe, 2);
                if (probe.player.canInteract()) {
                    Rectangle b = new Rectangle(probe.player.hitboxBounds());
                    reach = reach == null ? b : reach.merge(b);
                }
            }
        }
        assertNotNull(reach);
        Rectangle climb = null;                                           // the climbing hero: every position from the foot to the top
        Level c = room(0);
        walkToLever(c); operate(c); walkToLadder(c);
        for (int i = 0; i < 60 * 8 && (c.player.onLadder() || feet(c) > DECK + 1 || climb == null); i++) {
            held.clear(); held.add(Input.Keys.W);
            frame(c);
            Rectangle b = new Rectangle(c.player.hitboxBounds());
            climb = climb == null ? b : climb.merge(b);
        }
        Rectangle guard = theMob.hitboxBounds();
        Rectangle danger = new Rectangle(guard.x - 6, guard.y, guard.width + 6, guard.height).merge(puff(c).cloudBounds());
        float toReach = gap(reach, danger), toClimb = gap(climb, danger);
        System.out.printf(Locale.ROOT, "forest-1 guard room: lever reach (standing and jumping) x %.1f-%.1f y %.1f-%.1f; the climber x %.1f-%.1f y %.1f-%.1f; the danger (guard, whip, cloud) x %.1f-%.1f y %.1f-%.1f; gaps %.1f px and %.1f px%n",
            reach.x, reach.x + reach.width, reach.y, reach.y + reach.height, climb.x, climb.x + climb.width, climb.y, climb.y + climb.height,
            danger.x, danger.x + danger.width, danger.y, danger.y + danger.height, toReach, toClimb);
        assertTrue(toReach >= 48, "the traps are " + toReach + " px from the lever's reach");
        assertTrue(toClimb >= 48, "and " + toClimb + " px from a climber");
        Rectangle cloud = puff(c).cloudBounds();
        assertTrue(cloud.x >= ROOF_LEFT && cloud.x + cloud.width <= ROOF_RIGHT, "the cloud lies wholly under the roof");
    }

    @Test void theRoofIsUnreachableWithoutTheLeverWhateverIsJumpedWallJumpedOrClamberedAgainstTheTunnelBlockOrTheLadder() {
        long seed = 12345;
        float highest = Float.MAX_VALUE;
        for (int run = 0; run < 40; run++) {
            Level level = room(0);
            putHero(level, ROOM_X + 24 + (run * 17) % 200, 272);          // x 2136-2336 on the floor, left of the tunnel and under the raised ladder
            int key = 0;
            for (int i = 0; i < 60 * 6 && !dead(level); i++) {
                if (i % 8 == 0) {
                    seed = seed * 6364136223846793005L + 1442695040888963407L;
                    key = (int) ((seed >>> 33) % 8);
                }
                held.clear();
                if ((key & 1) != 0) held.add(Input.Keys.D); else if ((key & 2) != 0) held.add(Input.Keys.A);
                if ((key & 4) != 0 || i % 8 == 0) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
                if (key == 3 || key == 7) held.add(Input.Keys.W);          // Up against the ladder or a wall
                frame(level);
                highest = Math.min(highest, feet(level));
                assertFalse(level.player.onLadder(), "a raised ladder cannot be taken: " + level.player.position);
                assertFalse(feet(level) <= DECK + 2 && centerX(level) > ROOF_LEFT && centerX(level) < SLAB_END,
                    "the hero reached the roof without the lever: " + level.player.position + " run " + run + " frame " + i);
            }
        }
        System.out.println("forest-1 guard room, 40 fuzzed 6 s runs of jumps, wall-jumps and clambers with the ladder raised: the highest the feet ever got was y " + highest);
        assertTrue(highest > DECK + 2, "the roof level (y 208) is never reached: " + highest);
        Level base = room(0);
        putHero(base, ROOF_LEFT - 8, 272);                                  // pressed against the tunnel block's left face, jumping on the spot
        for (int i = 0; i < 120; i++) {
            held.clear(); held.add(Input.Keys.D); held.add(Input.Keys.J); pressed.add(Input.Keys.J); held.add(Input.Keys.W);
            frame(base);
            assertTrue(feet(base) > TUNNEL_CEILING - 0.5f + 10 || !base.player.onLadder(), "no climbing the block's face");
        }
        assertTrue(feet(base) > DECK + 50, "pressed against the block's face the hero is still low: " + feet(base));
    }

    // ---- the zero-fireball guarantee and D26 ----

    @Test void everyRouteThatNeedsNoChargeWorksWithAnEmptyStock() {
        Level a = room(0); meleeRoute(a); assertTrue(won(a));
        Level b = room(0); roofRoute(b); assertTrue(won(b));
        assertEquals(1, stock.count(), "only the refill changed the stock");
    }

    /** From wherever the hero stands (the ladder, the roof, the floor) to the room exit, patient, no lever, no charge. */
    private int recover(Level level) {
        int frames = 0;
        if (level.player.onLadder() || feet(level) < FLOOR - 2) {
            boolean right = centerX(level) > ROOF_LEFT + 20;
            while (!onFloor(level)) {
                held.clear();
                held.add(level.player.onLadder() ? Input.Keys.S : right ? Input.Keys.D : Input.Keys.A);
                frame(level);
                if (dead(level)) fail("died leaving the roof at " + level.player.position);
                assertTrue(++frames < 60 * 20, "cannot leave the roof from " + level.player.position);
            }
            held.clear();
        }
        frames += floorRoute(level, true);
        alive(level);
        return frames;
    }

    @Test void everyLeverGuardRefillCloudPhaseAndHeroStateStillReachesTheCheckpointWithZeroChargesAndWithoutRestartLevel() {
        String[] places = {"entrance", "ladder foot", "mouth of the tunnel", "ladder top", "roof above the tunnel", "slab by the refill", "right of the guard"};
        int runs = 0;
        for (int lever = 0; lever <= 2; lever++) {                    // pulls: 0 = raised, 1 = lowered, 2 = raised again
            for (boolean guardDead : new boolean[]{false, true}) {
                for (boolean taken : new boolean[]{false, true}) {
                    for (int where = 0; where < places.length; where++) {
                        if (where >= 3 && where <= 5 && lever != 1) continue;   // the roof is reached, so stood on, only with the ladder down
                        if (where == 6 && !guardDead) continue;                 // right of the guard needs the guard dead
                        for (int phase : new int[]{0, 70, 140, 210}) {          // where the cloud is in its cycle when the hero starts (0, 1.2, 2.3, 3.5 s)
                            String ctx = "lever pulls " + lever + ", guard " + (guardDead ? "dead" : "alive") + ", refill taken " + taken + ", hero at the " + places[where] + ", cloud " + phase / 60f + " s on";
                            Level level = room(0);
                            if (lever > 0) { walkToLever(level); for (int p = 0; p < lever; p++) { operate(level); settle(level, 60); } }
                            if (guardDead) theMob.collidedWithActor(true);     // any kill goes through here: sword, fireball or stone
                            int initial = stock.count();
                            if (taken) refill(level).collideWithPlayer();
                            settle(level, phase);
                            float[][] at = {{START_X + 23.5f, 272}, {LADDER_CENTRE, 272}, {2330, 272}, {LADDER_CENTRE, DECK - 48 - 1}, {2450, DECK - 48 - 1}, {2600, DECK - 48 - 1}, {2480, 272}};
                            putHero(level, at[where][0], at[where][1]);
                            settle(level, 2);
                            try { recover(level); } catch (AssertionError e) { throw new AssertionError(ctx + ": " + e.getMessage(), e); }
                            assertTrue(won(level), ctx);
                            assertTrue(stock.count() >= initial, ctx);
                            assertTrue(level.objects.stream().noneMatch(Fireball.class::isInstance), ctx + ": no charge needed");
                            runs++;
                        }
                    }
                }
            }
        }
        System.out.println("forest-1 guard room D26 sweep: " + runs + " runs, 0 failures, no Restart Level");
        assertEquals(RUNS, runs);
    }

    @Test void aSecuredKillASecuredRefillAndASecuredLoweredLadderStayAsTheyWereAndTheExitStillWorks() {
        // Securing is the game's job, not the room's: a forward exit or a healer return calls keepPickups() and secureVisit(); then resetCurrentRoom
        // applies the snapshot as a revisit does.
        Level level = room(0);
        walkToLever(level); operate(level);
        level.keepPickups();
        level.secureVisit();
        level.resetCurrentRoom(player());
        assertTrue(ladder(level).isLowered(), "the ladder stays as left (D26)");
        assertTrue(lever(level).isUp());
        theMob = mob(level);
        assertFalse(theMob.isDefeated());
        settle(level, 90);
        meleeRoute(level);                                  // the floor still finishes, timed melee and all
        assertTrue(won(level));
        Level killed = room(0);
        meleeRoute(killed);                                 // guard killed, then the room exit secured the room
        killed.secureVisit();
        killed.resetCurrentRoom(player());
        assertTrue(killed.objects.stream().noneMatch(o -> o instanceof RedMob m && !m.isDefeated()), "a secured kill stays a kill");
        assertFalse(refillGone(killed), "an untouched refill is still there");
        Level taken = room(0);
        roofRoute(taken);
        taken.keepPickups();
        taken.secureVisit();
        taken.resetCurrentRoom(player());
        assertTrue(refillGone(taken), "a secured refill does not come back");
        assertTrue(ladder(taken).isLowered());
    }

    @Test void deathMidRoofRouteAfterTheRefillRestoresTheRefillAndTheChargeRollsBack() {
        Level level = room(1);
        walkToLever(level); operate(level); walkToLadder(level); climbTo(level, DECK);
        for (int i = 0; i < 60 * 20 && !refillGone(level); i++) { held.clear(); held.add(Input.Keys.D); frame(level); alive(level); }
        held.clear();
        assertTrue(refillGone(level));
        assertEquals(2, stock.count());
        level.resetCurrentRoom(player());
        theMob = mob(level);
        assertEquals(1, stock.count(), "a retry rolls the unsecured charge back");
        assertFalse(refillGone(level), "and the refill is back");
        assertFalse(ladder(level).isLowered());
        assertFalse(lever(level).isUp());
        assertEquals(START_X, level.player.position.x, 0.01f);
    }

    @Test void restartLevelRebuildsTheGuardTheCloudTheRefillAndTheLadderFromTheMap() {
        Level level = room(0);
        roofRoute(level);
        Level again = room(0);
        assertFalse(ladder(again).isLowered());
        assertFalse(theMob.isDefeated());
        assertEquals(PHASE, puff(again).cycleTime(), 0.05f);
        assertFalse(refillGone(again));
        assertEquals(0, stock.count());
        settle(again, 1);
        meleeRoute(again);
        assertTrue(won(again));
    }

    @Test void theRoomIsEnteredFromTheRouteRoomExitAndRetriesStayInTheGuardRoom() {
        stock = new RangedStock();
        stock.restore(0);
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new, stock);
        level.enterNextRoom(player()); // forest-1-mob (#375) -> forest-1-traps
        level.enterNextRoom(player());
        assertEquals("forest-1-lever", level.currentRoomName());
        level.enterNextRoom(player());
        assertEquals("forest-1-route", level.currentRoomName());
        assertTrue(level.hasNextRoom());
        level.enterNextRoom(player());
        assertEquals("forest-1-guard", level.currentRoomName());
        assertTrue(level.hasNextRoom(), "the tree room follows (#349)");
        level.resetCurrentRoom(player());
        assertEquals("forest-1-guard", level.currentRoomName());
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals(272, level.player.position.y, 0.01f);
    }

    @Test void theLeverIsOnlyEverInReachOnTheFloorAndNeverFromTheLadderOrTheRoof() {
        for (boolean lowered : new boolean[]{false, true}) {
            Level level = room(0);
            if (lowered) { walkToLever(level); operate(level); settle(level, 60); }
            for (float x = ROOM_X + 24; x < ROOM_END - 24; x += 4) {
                for (float y : new float[]{DECK - 48, 96 - 48}) {
                    putHero(level, x, y);
                    settle(level, 2);
                    if (level.player.canInteract()) fail("the lever is in reach from the ladder or the roof: x=" + x + " y=" + y);
                }
            }
        }
    }
}
