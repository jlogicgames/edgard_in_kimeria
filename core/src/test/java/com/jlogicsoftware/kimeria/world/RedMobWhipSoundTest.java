package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.RedMob;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #447, D93: the RedMob's whip is heard once per strike, at the start of its strike frame, hit or miss; not during the wind-up. */
class RedMobWhipSoundTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); h.playSounds = true; }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Level room() {
        return new Level(h.assets(), h.game(), new Player(h.assets(), h.game(), 0, 0), "forest-1", Rectangle::new);
    }
    private RedMob mob(Level l) { return l.objects.stream().filter(RedMob.class::isInstance).map(RedMob.class::cast).findFirst().orElseThrow(); }
    private long cracks() { return h.sounds.stream().filter("whip_crack"::equals).count(); }

    /** Puts the hero's hitbox on the whip's box (what the strike reaches), or just above it, from the mob's own geometry. */
    private void heroAt(Level l, RedMob m, boolean inReach) {
        Rectangle whip = m.attackBounds(), hero = l.player.hitboxBoundsInto(new Rectangle());
        l.player.position.add(whip.x - hero.x, (inReach ? whip.y : whip.y - hero.height - whip.height) - hero.y);
        l.player.velocity.y = 0;
    }

    /** Frames until the mob's whip is active, with the hero standing in its reach. */
    private RedMob startStrike(Level l) {
        RedMob m = mob(l);
        for (int f = 0; f < 600 && !m.isAttackActive(); f++) {
            if (m.getState() != RedMob.State.ATTACK) heroAt(l, m, true);
            l.update(STEP);
            if (!m.isAttackActive()) assertEquals(0, cracks(), "silent during the wind-up");
        }
        assertTrue(m.isAttackActive(), "the mob struck");
        return m;
    }

    @Test void theWhipIsHeardOnceAtTheStartOfItsStrikeFrameWhenItHits() {
        Level l = room();
        startStrike(l);
        assertEquals(1, cracks());
        for (int f = 0; f < 60; f++) l.update(STEP);
        assertEquals(1, cracks(), "one cue per strike");
    }

    @Test void aMissedWhipIsHeardToo() {
        Level l = room();
        RedMob m = mob(l);
        for (int f = 0; f < 600 && m.getState() != RedMob.State.ATTACK; f++) { heroAt(l, m, true); l.update(STEP); }
        assertEquals(RedMob.State.ATTACK, m.getState());
        for (int f = 0; f < 90; f++) { heroAt(l, m, false); l.update(STEP); }  // out of reach, above the whip
        assertFalse(l.player.isGotHit(), "the whip missed");
        assertEquals(1, cracks(), "a miss is heard (D93)");
    }

    @Test void aMobKilledDuringItsWindUpNeverCracks() {
        Level l = room();
        RedMob m = mob(l);
        for (int f = 0; f < 600 && m.getState() != RedMob.State.ATTACK; f++) { heroAt(l, m, true); l.update(STEP); }
        assertEquals(RedMob.State.ATTACK, m.getState());
        m.collidedWithActor(true);
        for (int f = 0; f < 90; f++) l.update(STEP);
        assertEquals(0, cracks(), "an abandoned strike is silent");
    }
}
