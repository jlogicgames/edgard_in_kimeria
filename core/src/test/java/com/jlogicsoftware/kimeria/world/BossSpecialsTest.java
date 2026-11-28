package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.Settings;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.enemy.BossArena;
import com.jlogicsoftware.kimeria.entity.enemy.BossAttacks;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The forest boss's special attacks and poise (#625, D157) in the shipped arena. World Y grows downward. */
class BossSpecialsTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 304, PLATFORM = 240;
    /** The hero's body: 11x22, its centre this far right of the frame's left edge and its feet this far under the top. */
    private static final float HERO_CENTRE = 23.5f, HERO_FEET = 48, HERO_HALF = 5.5f;
    /** The boss's body centre, right of its frame's left edge. */
    private static final float BOSS_CENTRE = 16;
    /** The centre of the lower left platform, x 2688-2720 at y 240. */
    private static final float LOW_PLATFORM_X = 2704;

    @TempDir Path directory;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private Level level;
    private Boss boss;

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private void enter() {
        level = harness.room("forest-2", "forest-boss");
        frames(40);
        boss = level.boss();
        assertEquals(Boss.State.THRONE, boss.getState());
    }

    /** A woken, walking boss with its body centred on {@code bossX} and the hero standing at {@code heroX}, no special ready. */
    private void fight(float bossX, float heroX) {
        enter();
        place(boss.getX() + BOSS_CENTRE - 100, FLOOR);
        frames(1);
        assertEquals(Boss.State.WALK, boss.getState());
        boss.position.x = bossX - BOSS_CENTRE;
        place(heroX, FLOOR);
    }

    /** {@link #fight} with the special ready, run one frame so the boss has chosen. */
    private Boss.State chosen(float bossX, float heroX, float feet) {
        fight(bossX, heroX);
        place(heroX, feet);
        specialClock(Boss.SPECIAL_COOLDOWN);
        frames(1);
        return boss.getState();
    }

    private void place(float heroX, float feet) {
        level.player.position.set(heroX - HERO_CENTRE, feet - HERO_FEET);
        level.player.velocity.setZero();
        level.player.setOnGround(true);
    }

    private void specialClock(float value) {
        try {
            var field = Boss.class.getDeclaredField("specialClock");
            field.setAccessible(true);
            field.setFloat(boss, value);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private void frames(int count) {
        for (int i = 0; i < count; i++) { level.update(STEP); harness.pressed.clear(); }
    }

    private float bossX() { return boss.getX() + BOSS_CENTRE; }
    private float heroX() { return level.player.getX() + HERO_CENTRE; }
    private boolean hit() { return level.player.isGotHit(); }
    private BossAttacks attacks() { return boss.attacks(); }

    private int framesWhile(java.util.function.BooleanSupplier condition, int limit) {
        int frames = 0;
        while (condition.getAsBoolean()) {
            frames(1);
            assertTrue(++frames <= limit, "still true after " + limit + " frames, boss " + boss.getState());
        }
        return frames;
    }

    private void longerWarnings() {
        harness.game().trapWarnings().toggle(new Settings(new HeadlessPreferences(
            new FileHandle(directory.resolve("prefs.xml").toFile()))));
        assertTrue(harness.game().trapWarnings().longer());
    }

    // ---------------------------------------------------------------- the choice

    @Test void aSpecialIsReadyThreeSecondsAfterTheWakeAndAfterTheLast() {
        harness.invulnerable = true;
        fight(2880, 2760);
        Runnable hold = () -> { boss.position.x = 2880 - BOSS_CENTRE; place(2780, FLOOR); }; // both held in place
        int first = framesWhile(() -> { hold.run(); return !boss.isSpecial(); }, 400);
        assertEquals(Boss.SPECIAL_COOLDOWN, first * STEP, 3 * STEP);
        framesWhile(boss::isSpecial, 300);
        int second = framesWhile(() -> { hold.run(); return !boss.isSpecial(); }, 400);
        assertEquals(Boss.SPECIAL_COOLDOWN, second * STEP, 3 * STEP);
    }

    @Test void theChoiceFollowsWhereTheHeroStands() {
        assertEquals(Boss.State.BLOW, chosen(2860, 2860 - Boss.BLOW_DISTANCE + 1, FLOOR));
        assertEquals(Boss.State.ROOTS, chosen(2900, 2900 - Boss.BLOW_DISTANCE - 2, FLOOR));
        assertEquals(Boss.State.ROOTS, chosen(2900, 2900 - Boss.ROCKS_DISTANCE + 2, FLOOR));
        assertEquals(Boss.State.ROCKS, chosen(2900, 2900 - Boss.ROCKS_DISTANCE - 2, FLOOR));
        assertEquals(Boss.State.ROCKS, chosen(LOW_PLATFORM_X + 40, LOW_PLATFORM_X, PLATFORM), "a platform calls rocks even when close");
    }

    @Test void theSameInputsGiveTheSameFight() {
        List<String> first = recordedFight(), second = recordedFight();
        assertEquals(first, second);
        assertTrue(first.stream().anyMatch(line -> line.startsWith("ROOTS") || line.startsWith("BLOW") || line.startsWith("ROCKS")));
    }

    private List<String> recordedFight() {
        harness.invulnerable = true;
        enter();
        List<String> lines = new ArrayList<>();
        harness.held.add(Input.Keys.D);
        for (int i = 0; i < 900; i++) {
            if (i == 90) harness.held.clear();
            frames(1);
            lines.add(boss.getState() + " " + boss.getX() + " " + heroX() + " " + boss.energy());
        }
        harness.held.clear();
        return lines;
    }

    @Test void aHeroInTheAirDelaysTheChoiceUntilTheLanding() {
        fight(2880, 2780);
        harness.pressed.add(Input.Keys.J);
        harness.held.add(Input.Keys.J);
        frames(1);
        harness.held.clear();
        frames(5);
        assertFalse(level.player.isOnGround());
        specialClock(Boss.SPECIAL_COOLDOWN);
        while (!level.player.isOnGround()) {
            assertFalse(boss.isSpecial(), "no choice while the hero is in the air");
            frames(1);
        }
        frames(1);
        assertTrue(boss.isSpecial());
    }

    // ---------------------------------------------------------------- warnings and sounds

    private float secondsToAct(Boss.State state) {
        float distance = state == Boss.State.ROCKS ? 200 : state == Boss.State.ROOTS ? 100 : 30;
        harness.invulnerable = true;
        assertEquals(state, chosen(2900, 2900 - distance, FLOOR));
        return framesWhile(() -> switch (state) {
            case ROCKS -> attacks().rockBounds(0).y == 96;
            case ROOTS -> attacks().rootTop() >= FLOOR;
            default -> boss.getState() == Boss.State.BLOW;
        }, 200) * STEP;
    }

    @Test void eachAttackWarnsForItsOwnSharedTimeAndLongerWithTheAssist() {
        assertEquals(TrapWarnings.BOSS_ROCKS_WARNING_SECONDS, secondsToAct(Boss.State.ROCKS), 2 * STEP);
        assertEquals(TrapWarnings.BOSS_ROOTS_WARNING_SECONDS, secondsToAct(Boss.State.ROOTS), 2 * STEP);
        assertEquals(TrapWarnings.BOSS_BLOW_WARNING_SECONDS, secondsToAct(Boss.State.BLOW), 2 * STEP);
        longerWarnings();
        for (Boss.State state : new Boss.State[]{Boss.State.ROCKS, Boss.State.ROOTS, Boss.State.BLOW}) {
            float seconds = secondsToAct(state);
            float base = state == Boss.State.ROCKS ? TrapWarnings.BOSS_ROCKS_WARNING_SECONDS
                : state == Boss.State.ROOTS ? TrapWarnings.BOSS_ROOTS_WARNING_SECONDS : TrapWarnings.BOSS_BLOW_WARNING_SECONDS;
            assertEquals(TrapWarnings.longestDuration(base), seconds, 2 * STEP, state.name());
        }
    }

    @Test void theBossIsBusyForTheCardsTimes() {
        float[] seconds = new float[3];
        Boss.State[] states = {Boss.State.ROCKS, Boss.State.ROOTS, Boss.State.BLOW};
        float[] distances = {200, 100, 30};
        harness.invulnerable = true;
        for (int i = 0; i < states.length; i++) {
            assertEquals(states[i], chosen(2900, 2900 - distances[i], FLOOR));
            seconds[i] = framesWhile(boss::isSpecial, 300) * STEP;
            assertEquals(Boss.State.RECOVERY, boss.getState());
        }
        assertEquals(2.55f, seconds[0], 3 * STEP);
        assertEquals(1.3f, seconds[1], 3 * STEP);
        assertEquals(0.9f, seconds[2], 3 * STEP);
    }

    @Test void everyAttackIsHeardAtItsWindUpAndWhenItActsHitOrMiss() {
        harness.playSounds = true;
        harness.invulnerable = true;
        String[][] cues = {{"boss_call", "stone_impact"}, {"root_creak", "root_thrust"}, {"boss_blow_charge", "boss_blow"}};
        float[] distances = {200, 100, 30};
        int[] acts = {BossAttacks.ROCKS, 1, 1};
        for (int i = 0; i < cues.length; i++) {
            fight(2900, 2900 - distances[i]);
            specialClock(Boss.SPECIAL_COOLDOWN);
            harness.sounds.clear();
            frames(1);
            assertEquals(1, count(cues[i][0]), cues[i][0]);
            place(2640 + HERO_CENTRE, FLOOR); // away from everything: a miss
            framesWhile(boss::isSpecial, 300);
            assertEquals(acts[i], count(cues[i][1]), cues[i][1]);
        }
        longerWarnings();
        fight(2900, 2870);
        specialClock(Boss.SPECIAL_COOLDOWN);
        harness.sounds.clear();
        frames(1);
        assertEquals(1, count("boss_blow_charge_long"));
        assertEquals(0, count("boss_blow_charge"));
    }

    private long count(String sound) { return harness.sounds.stream().filter(sound::equals).count(); }

    // ---------------------------------------------------------------- rocks

    @Test void eachRockIsAimedAtTheHeroAsItsShadowAppears() {
        harness.invulnerable = true;
        chosen(2900, 2700, FLOOR);
        assertEquals(1, attacks().rocksLaunched());
        assertEquals(2700, attacks().rockBounds(0).x + 8, .01f);
        int interval = Math.round(BossAttacks.ROCK_INTERVAL / STEP);
        place(2780, FLOOR);
        frames(interval + 1);
        assertEquals(2, attacks().rocksLaunched());
        assertEquals(2780, attacks().rockBounds(1).x + 8, .01f);
        place(2840, FLOOR);
        frames(interval);
        assertEquals(3, attacks().rocksLaunched());
        assertEquals(2840, attacks().rockBounds(2).x + 8, .01f);
        assertEquals(2700, attacks().rockBounds(0).x + 8, .01f, "a launched rock never moves aside");
    }

    @Test void aHeroWhoStandsStillIsHitByTheFirstRock() {
        chosen(2900, 2700, FLOOR);
        int frames = framesWhile(() -> !hit(), 200);
        float flight = (float) Math.sqrt(2 * (FLOOR - HERO_FEET / 2 - 96) / 900f);
        assertTrue(frames * STEP > TrapWarnings.BOSS_ROCKS_WARNING_SECONDS, "never before the warning ends");
        assertTrue(frames * STEP < TrapWarnings.BOSS_ROCKS_WARNING_SECONDS + flight + 3 * STEP);
    }

    @Test void aHeroWhoKeepsWalkingIsNeverHit() {
        chosen(2920, 2740, FLOOR);
        harness.held.add(Input.Keys.A);
        framesWhile(boss::isSpecial, 300);
        assertFalse(hit());
    }

    @Test void fourteenPixelsOffTheShadowAreSafe() {
        chosen(2920, 2740, FLOOR);
        int interval = Math.round(BossAttacks.ROCK_INTERVAL / STEP);
        frames(interval + 2);
        frames(interval);
        assertEquals(3, attacks().rocksLaunched());
        float last = attacks().rockBounds(2).x + 8;
        assertEquals(2740, last, .01f, "the hero has not moved: all three share a column");
        place(last - 14, FLOOR);
        framesWhile(boss::isSpecial, 300);
        assertFalse(hit());
    }

    @Test void aPlatformIsNoShelterFromARock() {
        chosen(2900, LOW_PLATFORM_X, PLATFORM);
        framesWhile(() -> !hit(), 200);
        assertTrue(level.player.getY() + HERO_FEET <= PLATFORM + 1, "hit while on the platform");
    }

    @Test void aLandedRockIsHarmlessAndThenGone() {
        harness.invulnerable = true;
        chosen(2900, 2700, FLOOR);
        framesWhile(() -> !attacks().rockLanded(0), 200);
        assertEquals(FLOOR, attacks().rockBounds(0).y + BossAttacks.ROCK_SIZE, .01f);
        framesWhile(boss::isSpecial, 300);
        harness.invulnerable = false;
        place(attacks().rockBounds(2).x + 8, FLOOR);
        frames(2);
        assertFalse(hit());
        framesWhile(() -> attacks().kind() != BossAttacks.Kind.NONE, 60);
    }

    // ---------------------------------------------------------------- roots

    private void rootsAt(float heroX) { assertEquals(Boss.State.ROOTS, chosen(heroX + 100, heroX, FLOOR)); }

    @Test void fiveRootsRiseRoundTheHeroAndAStandingHeroIsBitten() {
        rootsAt(2780);
        for (int i = 0; i < BossAttacks.ROOTS; i++) {
            assertTrue(attacks().rootStands(i), "root " + i);
            assertEquals(2780 + (i - 2) * BossAttacks.ROOT_SPACING, attacks().rootX(i), .01f);
        }
        int frames = framesWhile(() -> !hit(), 120);
        assertEquals(TrapWarnings.BOSS_ROOTS_WARNING_SECONDS, frames * STEP, 5 * STEP);
    }

    @Test void theGapsTheOuterSideAndAPlatformAreSafeFromTheRoots() {
        for (float safe : new float[]{2780 - 16, 2780 + 16, 2780 - 80}) {
            rootsAt(2780);
            place(safe, FLOOR);
            framesWhile(boss::isSpecial, 120);
            assertFalse(hit(), "x " + safe);
        }
        rootsAt(2736);
        place(LOW_PLATFORM_X, PLATFORM);
        framesWhile(boss::isSpecial, 120);
        assertFalse(hit(), "the columns stop " + (FLOOR - PLATFORM - BossAttacks.ROOT_HEIGHT) + " px under the platform");
    }

    @Test void aRootKillsExactlyItsDrawnColumn() {
        float beside = 4 + HERO_HALF;
        rootsAt(2780);
        place(2780 + beside + .5f, FLOOR);
        framesWhile(boss::isSpecial, 120);
        assertFalse(hit());
        rootsAt(2780);
        place(2780 + beside - .5f, FLOOR);
        framesWhile(() -> !hit(), 120);
    }

    // ---------------------------------------------------------------- the blow

    private void blowEnds(float heroX, float feet) {
        assertEquals(Boss.State.BLOW, chosen(2860, 2830, FLOOR));
        int almost = (int) (attacks().warningSeconds() / STEP) - 2;
        place(2860 - 80, FLOOR);
        frames(almost);
        assertEquals(Boss.State.BLOW, boss.getState());
        assertFalse(hit(), "the wind-up does not harm");
        for (int i = 0; i < 4 && boss.getState() == Boss.State.BLOW; i++) {
            place(heroX, feet);
            frames(1);
        }
        assertNotEquals(Boss.State.BLOW, boss.getState());
    }

    @Test void theBlowKillsInItsDomeAndNowhereElse() {
        blowEnds(2860 - 30, FLOOR);
        assertTrue(hit());
        blowEnds(2860 - 40 - HERO_HALF + 1, FLOOR);
        assertTrue(hit(), "along the floor it reaches 40 px");
        blowEnds(2860 - 40 - HERO_HALF - 1, FLOOR);
        assertFalse(hit());
        blowEnds(2860 - 20, FLOOR - 34);
        assertFalse(hit(), "a hero over its top is passed");
        blowEnds(2860 - 40, FLOOR - 24);
        assertFalse(hit(), "the box's upper corner is outside the dome");
    }

    @Test void aBlowThatMissesShakesTheScreen() {
        blowEnds(2860 - 80, FLOOR);
        assertTrue(level.bossFx().soft().count() > 0, "the flash");
        frames(1);
        Vector2 shake = level.cameraShakeOffset(new Rectangle(2464, 0, 640, 360));
        assertNotEquals(Vector2.Zero, shake);
    }

    // ---------------------------------------------------------------- poise

    private void startSweep() {
        fight(2860, 2760);
        place(2860 - 28.5f, FLOOR);
        level.player.facingRight = true;
        framesWhile(() -> boss.getState() != Boss.State.NORMAL, 3);
    }

    private float sweepElapsed() {
        try {
            var field = Boss.class.getDeclaredField("elapsed");
            field.setAccessible(true);
            return field.getFloat(boss);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @Test void theSweepIsPoisedFromTheCrouchToTheEndOfTheStrike() {
        assertEquals(0.1556f, Boss.POISE_FROM, .0001f);
        assertEquals(0.4667f, Boss.POISE_TO, .0001f);
        startSweep();
        place(2760, FLOOR);
        framesWhile(() -> sweepElapsed() + STEP < Boss.POISE_FROM, 20);
        assertFalse(boss.isPoised());
        boss.collidedWithActor(true);
        assertEquals(Boss.State.HURT, boss.getState(), "the first pose can still be interrupted");

        for (float at : new float[]{Boss.POISE_FROM, (Boss.POISE_FROM + Boss.POISE_TO) / 2, Boss.POISE_TO - 1.5f * STEP}) {
            startSweep();
            place(2760, FLOOR);
            framesWhile(() -> sweepElapsed() < at, 40);
            assertTrue(boss.isPoised(), "at " + sweepElapsed());
            boss.collidedWithActor(true);
            assertEquals(Boss.State.NORMAL, boss.getState());
            assertEquals(11, boss.energy(), "the hit lands");
            assertTrue(boss.rejectsHits());
            boss.collidedWithActor(true);
            boss.hitByFireball();
            assertEquals(11, boss.energy(), "only one hit in a poise window");
        }

        startSweep();
        place(2760, FLOOR);
        framesWhile(() -> sweepElapsed() < Boss.POISE_TO, 40);
        assertFalse(boss.isPoised());
        boss.collidedWithActor(true);
        assertEquals(Boss.State.HURT, boss.getState(), "the recovery after the strike can be interrupted");
    }

    @Test void swingingIntoTheCommittedSweepCostsAHit() {
        startSweep();
        framesWhile(() -> sweepElapsed() < Boss.POISE_FROM, 20);
        harness.pressed.add(Input.Keys.K);
        frames(1);
        assertEquals(11, boss.energy());
        assertEquals(Boss.State.NORMAL, boss.getState());
        framesWhile(() -> !hit(), 20);
    }

    @Test void everySpecialTakesOneHitAndGoesOn() {
        float[] distances = {200, 100, 30};
        Boss.State[] states = {Boss.State.ROCKS, Boss.State.ROOTS, Boss.State.BLOW};
        harness.invulnerable = true;
        for (int i = 0; i < states.length; i++) {
            assertEquals(states[i], chosen(2900, 2900 - distances[i], FLOOR));
            assertTrue(boss.isPoised());
            boss.hitByFireball();
            assertEquals(10, boss.energy(), states[i].name());
            assertEquals(states[i], boss.getState(), "no recoil, no counter");
            boss.collidedWithActor(true);
            boss.hitByFireball();
            assertEquals(10, boss.energy(), "later hits are rejected");
            framesWhile(boss::isSpecial, 300);
            assertFalse(boss.rejectsHits());
        }
    }

    @Test void aRejectedFireballShowsTheLightBurst() {
        assertEquals(Boss.State.ROOTS, chosen(2900, 2800, FLOOR));
        boss.hitByFireball();
        Rectangle body = boss.hitboxBounds();
        Fireball second = new Fireball(level, body.x - Fireball.BODY_SIZE, body.y, true);
        level.objects.add(second);
        second.update(STEP);
        assertTrue(second.isRemoved());
        assertEquals(10, boss.energy());
    }

    @Test void aLethalSwingIntoTheCommittedSweepWins() {
        startSweep();
        framesWhile(() -> sweepElapsed() < Boss.POISE_FROM, 20);
        energy(1);
        harness.pressed.add(Input.Keys.K);
        frames(1);
        assertTrue(boss.isDefeated());
        frames(30);
        assertFalse(hit());
    }

    private void energy(int value) {
        try {
            var field = Boss.class.getDeclaredField("energy");
            field.setAccessible(true);
            field.setInt(boss, value);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    // ---------------------------------------------------------------- the end of an attack

    @Test void theBossesDefeatRemovesEveryAttack() {
        float[] distances = {200, 100, 30};
        for (float distance : distances) {
            chosen(2900, 2900 - distance, FLOOR);
            energy(1);
            boss.collidedWithActor(true);
            assertTrue(boss.isDefeated());
            assertEquals(BossAttacks.Kind.NONE, attacks().kind());
            frames(150);
            assertFalse(hit());
        }
    }

    /** A root bites with its first pixel: it then rises, so the death is seen to have a cause (D5), and nothing new comes. */
    @Test void theAttackThatKillsPlaysOnAndNoNewRockFollows() {
        rootsAt(2780);
        framesWhile(() -> !hit(), 120);
        float biting = attacks().rootTop();
        float highest = biting;
        for (int i = 0; i < 60; i++) {
            frames(1);
            highest = Math.min(highest, attacks().rootTop());
        }
        assertEquals(FLOOR - BossAttacks.ROOT_HEIGHT, highest, .01f, "bit at " + biting + ", then rose to its full height");
        assertEquals(BossAttacks.Kind.NONE, attacks().kind());

        // Abandoned after the first shadow only: that rock still lands, and the other two never come.
        chosen(2900, 2700, FLOOR);
        frames(12);
        assertEquals(1, attacks().rocksLaunched());
        boss.abandonAttack();
        int interval = Math.round(BossAttacks.ROCK_INTERVAL / STEP);
        frames(2 * interval);
        assertEquals(1, attacks().rocksLaunched(), "no further rock is thrown");
        framesWhile(() -> !attacks().rockLanded(0), 200);
        framesWhile(() -> attacks().kind() != BossAttacks.Kind.NONE, 60);
        assertFalse(hit(), "an abandoned attack harms nobody");
    }

    @Test void aBlowUnderWayStillGoesOffHarmlesslyAfterTheHeroFalls() {
        harness.playSounds = true;
        assertEquals(Boss.State.BLOW, chosen(2860, 2830, FLOOR));
        boss.abandonAttack();
        harness.sounds.clear();
        framesWhile(() -> attacks().kind() != BossAttacks.Kind.NONE, 120);
        assertEquals(1, count("boss_blow"));
        assertTrue(level.bossFx().soft().count() > 0, "the flash");
        assertFalse(hit());
    }

    @Test void aDeathResetsTheBossAndItsAttacks() {
        chosen(2900, 2700, FLOOR);
        framesWhile(() -> !hit(), 200);
        level.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
        Boss rebuilt = level.boss();
        assertNotSame(boss, rebuilt);
        assertEquals(Boss.State.THRONE, rebuilt.getState());
        assertEquals(Boss.MAX_ENERGY, rebuilt.energy());
        assertEquals(BossAttacks.Kind.NONE, rebuilt.attacks().kind());
        assertFalse(rebuilt.specialReady());
        assertEquals(0, level.bossFx().hard().count());
        boss = rebuilt;
        frames(600);
        assertEquals(Boss.State.THRONE, boss.getState(), "a respawned hero meets a sleeping boss");
        assertFalse(hit());
    }

    // ---------------------------------------------------------------- the protected spans (D59)

    @Test void theShippedArenaKeepsTheEntranceAndTheExit() {
        enter();
        BossArena arena = attacks().arena();
        assertEquals(FLOOR, arena.floor());
        assertEquals(2576, arena.left());
        assertEquals(2992, arena.right());
        assertEquals(2640, arena.entranceLeft());
        assertEquals(2688, arena.entranceRight());
        assertEquals(2968, arena.exitLeft());
        assertEquals(2992, arena.exitRight());
        assertEquals(96, arena.ceilingOver(2700, 2716));
    }

    /** Every lethal area of rocks and roots, with the hero at every 4 px of the arena, on the floor. */
    @Test void noRockOrRootEverOverlapsAProtectedSpan() {
        harness.invulnerable = true;
        enter();
        BossArena arena = attacks().arena();
        for (float x = arena.left() + HERO_HALF; x <= arena.right() - HERO_HALF; x += 4) {
            for (BossAttacks.Kind kind : new BossAttacks.Kind[]{BossAttacks.Kind.ROCKS, BossAttacks.Kind.ROOTS}) {
                if (!attacks().start(kind, x, 2900)) continue;
                if (kind == BossAttacks.Kind.ROCKS) {
                    Rectangle rock = attacks().rockBounds(0);
                    assertOutside(arena, rock.x, rock.x + rock.width, "rock for a hero at " + x);
                } else for (int i = 0; i < BossAttacks.ROOTS; i++) {
                    if (attacks().rootStands(i))
                        assertOutside(arena, attacks().rootX(i) - 4, attacks().rootX(i) + 4, "root for a hero at " + x);
                }
            }
        }
    }

    private static void assertOutside(BossArena arena, float from, float to, String what) {
        assertTrue(from >= arena.left() && to <= arena.right(), what + " leaves the arena");
        assertFalse(from < arena.entranceRight() && to > arena.entranceLeft(), what + " covers the entrance");
        assertFalse(from < arena.exitRight() && to > arena.exitLeft(), what + " covers the exit");
    }

    @Test void aRockForAHeroAtTheEntranceFallsBesideIt() {
        harness.invulnerable = true;
        chosen(2900, 2660, FLOOR);
        assertEquals(2632, attacks().rockBounds(0).x + 8, .01f);
    }

    @Test void noBlowStartsOverAProtectedSpanAndTheSpecialStaysReady() {
        for (float bossX : new float[]{2688 + 39, 2968 - 39}) {
            float heroX = bossX < 2800 ? bossX + 30 : bossX - 30;
            assertNotEquals(Boss.State.BLOW, chosen(bossX, heroX, FLOOR), "boss at " + bossX);
            assertTrue(boss.specialReady());
        }
        assertEquals(Boss.State.BLOW, chosen(2688 + 40, 2688 + 70, FLOOR));
        assertEquals(Boss.State.BLOW, chosen(2968 - 40, 2968 - 70, FLOOR));
    }

    @Test void aBadArenaFailsTheLoad() {
        Rectangle room = new Rectangle(0, 0, 448, 368);
        assertDoesNotThrow(() -> BossArena.of(room, FLOOR, 80, 48, 408, 24, List.of(block(0, FLOOR, 448, 64, ""))));
        assertThrows(IllegalArgumentException.class,
            () -> BossArena.of(room, FLOOR, 80, 48, 408, 24, List.of(block(0, FLOOR, 200, 64, ""), block(232, FLOOR, 216, 64, ""))));
        assertThrows(IllegalArgumentException.class,
            () -> BossArena.of(room, FLOOR, 80, 48, 408, 24, List.of(block(0, FLOOR, 448, 64, ""), block(200, 280, 32, 24, ""))));
        assertThrows(IllegalArgumentException.class,
            () -> BossArena.of(room, FLOOR, 80, 48, 200, 24, List.of(block(0, FLOOR, 448, 64, ""))));
    }

    @Test void aCeilingReachingIntoTheRoomFromAboveStillBoundsTheRocks() {
        Rectangle room = new Rectangle(0, 0, 448, 368);
        BossArena arena = BossArena.of(room, FLOOR, 80, 48, 408, 24,
            List.of(block(0, FLOOR, 448, 64, ""), block(100, -16, 64, 112, "")));
        assertEquals(96, arena.ceilingOver(120, 136));
        assertEquals(0, arena.ceilingOver(300, 316));
    }

    private static TiledObject block(float x, float y, float width, float height, String type) {
        TiledObject o = new TiledObject();
        o.x = x;
        o.y = y;
        o.width = width;
        o.height = height;
        o.type = type;
        return o;
    }
}
