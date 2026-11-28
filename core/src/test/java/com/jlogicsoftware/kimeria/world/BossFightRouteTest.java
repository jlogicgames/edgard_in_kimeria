package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.enemy.BossAttacks;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whole fights of the forest boss with real input only (#625, D157): no teleport, no invulnerability. A scripted hero who
 * reads the tells (dodges every special, retreats from the counter, swings only into an open boss) wins on the sword, with the
 * elixir and with fireballs; one who only swings does not. The times are the mastered ones of the room card (forest-boss): from
 * the hero's arrival at the entrance to the boss's defeat. World Y grows downward.
 */
class BossFightRouteTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float HERO_CENTRE = 23.5f, HERO_HALF = 5.5f, BOSS_CENTRE = 16, BOSS_HALF = 9;
    /** Body centres: the sword reaches at this distance; the counter and the blow are escaped beyond these. */
    private static final float SWORD_REACH = 29f, COUNTER_ESCAPE = 44f, BLOW_ESCAPE = 40 + HERO_HALF + 3;
    private static final float SHOT_DISTANCE = 70;
    /** A hero this close to the wall behind them stands and shoots. */
    private static final float CORNER = 24;
    private static final int LIMIT_FRAMES = 60 * 180;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private Level level;
    private Boss boss;
    private final EnumSet<Boss.State> seen = EnumSet.noneOf(Boss.State.class);

    /** The energy each landed hit took, in order. */
    private final java.util.List<Integer> drops = new java.util.ArrayList<>();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private enum Weapon { SWORD, FIREBALL }

    /** Fights until the boss or the hero falls; returns the seconds it took. */
    private float fight(Weapon weapon, boolean elixir) {
        level = harness.room("forest-2", "forest-boss");
        if (elixir) harness.game().artifacts().drinkBossElixir("forest-2");
        boss = level.boss();
        int frame = 0;
        while (!level.player.canFight()) { step(); frame++; }
        int start = frame;
        while (!boss.isDefeated()) {
            decide(weapon);
            int before = boss.energy();
            step();
            if (boss.energy() < before) drops.add(before - boss.energy());
            seen.add(boss.getState());
            assertFalse(level.player.isGotHit(), "hit at " + (frame - start) * STEP + " s, boss " + boss.getState() + " energy "
                + boss.energy() + ", hero " + heroX() + ", boss " + bossX());
            assertTrue(++frame < LIMIT_FRAMES, "no end: energy " + boss.energy() + " hero " + heroX() + " boss " + bossX()
                + " " + boss.getState());
        }
        return (frame - start) * STEP;
    }

    private void step() {
        level.update(STEP);
        harness.pressed.clear();
    }

    private float heroX() { return level.player.getX() + HERO_CENTRE; }
    private float bossX() { return boss.getX() + BOSS_CENTRE; }

    private void walk(float direction) {
        harness.held.clear();
        if (direction < 0) harness.held.add(Input.Keys.A);
        else if (direction > 0) harness.held.add(Input.Keys.D);
    }

    private void walkTo(float target) { walk(Math.abs(target - heroX()) < 1.5f ? 0 : Math.signum(target - heroX())); }

    private void decide(Weapon weapon) {
        if (!level.player.canFight()) { walk(0); return; }
        float distance = Math.abs(bossX() - heroX()), toward = Math.signum(bossX() - heroX());
        switch (boss.getState()) {
            case ROCKS -> walk(-toward); // each rock is aimed where the hero was: keep moving
            case ROOTS -> walkTo(gap());
            case BLOW -> walk(distance > BLOW_ESCAPE ? 0 : -toward);
            case HURT, WARNING, COUNTER, COUNTER_RECOVERY -> walk(distance > COUNTER_ESCAPE ? 0 : -toward);
            case THRONE -> walk(toward);
            default -> {
                if (weapon == Weapon.SWORD) swordAt(distance, toward);
                else shootAt(distance, toward);
            }
        }
    }

    private void swordAt(float distance, float toward) {
        if (boss.isPoised()) { walk(-toward); return; }
        boolean facing = level.player.facingRight == toward > 0;
        if (distance > SWORD_REACH) { walk(toward); return; }
        if (!facing) { walk(toward); return; }
        walk(0);
        harness.pressed.add(Input.Keys.K);
    }

    private void shootAt(float distance, float toward) {
        if (level.rangedStock.count() == 0) level.rangedStock.restore(level.rangedStock.CAPACITY); // the six-hit path needs a resupply
        boolean facing = level.player.facingRight == toward > 0;
        if (distance < SHOT_DISTANCE && !boss.isPoised() && !cornered(toward)) { walk(-toward); return; }
        if (!facing) { walk(toward); return; }
        walk(0);
        harness.pressed.add(Input.Keys.L);
    }

    /** True when the wall behind the hero (away from the boss) is close. */
    private boolean cornered(float toward) {
        return toward > 0 ? heroX() < boss.attacks().arena().left() + CORNER : heroX() > boss.attacks().arena().right() - CORNER;
    }

    /** The nearest place between or beside the roots that none of them reaches and the boss's body does not fill. */
    private float gap() {
        BossAttacks attacks = boss.attacks();
        float best = heroX(), bestDistance = Float.MAX_VALUE;
        for (float x = attacks.arena().left() + HERO_HALF; x <= attacks.arena().right() - HERO_HALF; x += 1) {
            boolean clear = Math.abs(x - bossX()) > BOSS_HALF + HERO_HALF + 1 && Math.signum(x - bossX()) == Math.signum(heroX() - bossX());
            for (int i = 0; i < BossAttacks.ROOTS && clear; i++)
                if (attacks.rootStands(i) && Math.abs(x - attacks.rootX(i)) < 4 + HERO_HALF + 1.5f) clear = false;
            if (clear && Math.abs(x - heroX()) < bestDistance) {
                best = x;
                bestDistance = Math.abs(x - heroX());
            }
        }
        return best;
    }

    @Test void aHeroWhoReadsTheTellsWinsOnTheSwordAlone() {
        float seconds = fight(Weapon.SWORD, false);
        assertTrue(seen.contains(Boss.State.ROCKS) || seen.contains(Boss.State.ROOTS) || seen.contains(Boss.State.BLOW),
            "the fight meets a special: " + seen);
        assertEquals(UNEMPOWERED_SECONDS, seconds, .1f);
    }

    @Test void theElixirPathStillWinsInTwoSwings() {
        float seconds = fight(Weapon.SWORD, true);
        assertEquals(2, drops.size(), "two swings");
        assertEquals(ELIXIR_SECONDS, seconds, .1f);
    }

    @Test void sixFireballsStillWin() {
        float seconds = fight(Weapon.FIREBALL, false);
        assertEquals(java.util.Collections.nCopies(6, 2), drops, "six fireballs, no sword");
        assertEquals(FIREBALL_SECONDS, seconds, .1f);
    }

    /** The old answer: walk in and swing on every chance, reading nothing. */
    @Test void aHeroWhoOnlySwingsLoses() {
        level = harness.room("forest-2", "forest-boss");
        boss = level.boss();
        for (int frame = 0; frame < LIMIT_FRAMES && !level.player.isGotHit(); frame++) {
            if (level.player.canFight()) {
                float toward = Math.signum(bossX() - heroX());
                if (Math.abs(bossX() - heroX()) > SWORD_REACH || level.player.facingRight != toward > 0) walk(toward);
                else {
                    walk(0);
                    harness.pressed.add(Input.Keys.K);
                }
            }
            step();
        }
        assertTrue(level.player.isGotHit(), "energy " + boss.energy());
        assertTrue(boss.energy() > 0);
    }

    private static final float UNEMPOWERED_SECONDS = 28.18f, ELIXIR_SECONDS = 3.78f, FIREBALL_SECONDS = 11.18f;
}
