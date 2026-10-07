package com.jlogicsoftware.kimeria.entity.enemy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StompTest {
    private static final float ENEMY_TOP = 100f;

    @Test
    void fallingOntoTheTopIsAStomp() {
        assertTrue(Enemy.isStomp(300f, ENEMY_TOP - 5f, ENEMY_TOP));
    }

    @Test
    void feetExactlyOnTheTopAtTheStartOfTheFrameStillCounts() {
        assertTrue(Enemy.isStomp(300f, ENEMY_TOP, ENEMY_TOP));
    }

    @Test
    void fallingBesideTheEnemyWithFeetBelowItsTopIsNotAStomp() {
        assertFalse(Enemy.isStomp(300f, ENEMY_TOP + 10f, ENEMY_TOP));
    }

    @Test
    void landingOnTheSameGroundAsTheEnemyIsNotAStomp() {
        assertFalse(Enemy.isStomp(0f, ENEMY_TOP + 26f, ENEMY_TOP));
    }

    @Test
    void risingIntoTheEnemyFromBelowIsNotAStomp() {
        assertFalse(Enemy.isStomp(-200f, ENEMY_TOP + 30f, ENEMY_TOP));
    }

    @Test
    void risingPastTheTopIsNotAStompEvenIfStartedAbove() {
        assertFalse(Enemy.isStomp(-200f, ENEMY_TOP - 5f, ENEMY_TOP));
    }
}
