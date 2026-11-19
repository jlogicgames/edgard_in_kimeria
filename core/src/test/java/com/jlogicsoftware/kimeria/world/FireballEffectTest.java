package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.effects.FireballFx;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fireball's trail and hit through the real {@code Level} (#397): the trail is shed only while the ball flies, one burst
 * where it stops (terrain, a monster, the boss heavier), nothing after it is gone, and a room reset clears it all.
 */
class FireballEffectTest {
    private static final float STEP = HeadlessLevelHarness.STEP, SPEED = 280, GAP = 1, INSET = 2, LIFETIME = 2;
    private static final int FLIGHT_FRAMES = 5, AFTER_FRAMES = 30, BURN_OUT_FLAMES = 4;
    private static final String FIZZLE = "fireball_fizzle";
    /** Trail flames live at most 0.17 s, embers at least 0.2 s. */
    private static final float EMBER_LIFE = .18f;
    /** After this long the sparks of a wall hit are well clear of the wall, further than the trail ever trails a ball. */
    private static final float SPRAY_SECONDS = .12f, SPRAY_CLEAR = 10;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.playSounds = true; }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private static int total(FireballFx fx) { return fx.soft().count() + fx.hard().count(); }

    /** A spot in the room where a ball flies right for {@value #FLIGHT_FRAMES} frames without touching anything. */
    private static Fireball freeShot(Level level) {
        Rectangle room = level.currentRoomBounds(), probe = new Rectangle();
        float reach = SPEED * STEP * FLIGHT_FRAMES + Fireball.BODY_SIZE;
        for (float y = room.y + Fireball.BODY_SIZE; y < room.y + room.height - Fireball.BODY_SIZE; y += Fireball.BODY_SIZE)
            for (float x = room.x + Fireball.BODY_SIZE; x < room.x + room.width - reach; x += Fireball.BODY_SIZE) {
                if (level.blocksObject(probe.set(x, y, reach, Fireball.BODY_SIZE), false) || touchesAnObject(level, probe)) continue;
                return new Fireball(level, x, y, true);
            }
        throw new AssertionError("no free line in the room");
    }
    private static boolean touchesAnObject(Level level, Rectangle r) {
        for (int i = 0; i < level.objects.size(); i++) {
            if (level.objects.get(i) instanceof com.jlogicsoftware.kimeria.entity.Entity o
                && r.overlaps(new Rectangle(o.getX(), o.getY(), o.getWidth(), o.getHeight()))) return true;
        }
        return false;
    }

    private static void fly(Level level, Fireball ball, int frames) {
        for (int i = 0; i < frames && !ball.isRemoved(); i++) { ball.update(STEP); level.fireballFx().update(STEP); }
    }

    /** No ball lives after its burst: the level updates on and the particle count only falls. */
    private static void nothingMoreAfter(Level level) {
        int before = total(level.fireballFx());
        for (int i = 0; i < AFTER_FRAMES; i++) {
            level.update(STEP);
            int now = total(level.fireballFx());
            assertTrue(now <= before, "nothing is spawned after the ball is gone");
            before = now;
        }
    }

    @Test void aFlyingBallShedsOneFlamePerSpacing() {
        Level level = harness.roomOf("forest-1", 2);
        Fireball ball = freeShot(level);
        level.objects.add(ball);
        fly(level, ball, 1);
        // No trail flame lives shorter than 0.04 s, so every one shed in one frame (0.017 s) is still there.
        int expected = (int) (SPEED * STEP / FireballFx.TRAIL_SPACING_PX), flames = 0;
        KimeriaParticleSystem hard = level.fireballFx().hard();
        for (int i = 0; i < hard.count(); i++) if (hard.maxLife(i) <= EMBER_LIFE) flames++;
        assertEquals(expected, flames);
        assertEquals(0, level.fireballFx().soft().count(), "no halo per step: the ball draws its own");
        fly(level, ball, FLIGHT_FRAMES);
        assertFalse(ball.isRemoved(), "still flying");
        float tail = hard.x(hard.count() - 1);
        assertTrue(tail < ball.centerX(), "the trail is behind a ball flying right");
        assertFalse(harness.sounds.contains(FIZZLE), "silent in flight");
    }

    @Test void aBallThatBurnsOutFizzlesOnce() {
        Level level = harness.roomOf("forest-1", 2);
        Fireball ball = freeShot(level);
        level.objects.add(ball);
        ball.update(LIFETIME);
        assertTrue(ball.isRemoved());
        assertEquals(1, level.fireballFx().soft().count(), "one small halo");
        assertEquals(BURN_OUT_FLAMES, level.fireballFx().hard().count(), "a few flames, no sparks");
        assertEquals(java.util.List.of(FIZZLE), harness.sounds, "the fizzle plays once");
        assertEquals(0, level.fireballFx().ringCount(), "no ring on a burn-out");
        assertNull(level.activeRipple(), "no screen distortion");
    }

    /** #503, D93: a ball in flight when the hero dies goes out seen and heard, like one that burns out, not silently. */
    @Test void aBallInFlightWhenTheHeroDiesFizzlesSeenAndHeard() {
        Level level = harness.roomOf("forest-1", 2);
        Fireball ball = freeShot(level);
        level.objects.add(ball);
        level.player.collidedWithActor(false);
        assertFalse(level.player.canFight());
        harness.sounds.clear();
        ball.update(STEP);
        assertTrue(ball.isRemoved());
        assertEquals(1, level.fireballFx().soft().count(), "one small halo");
        assertEquals(BURN_OUT_FLAMES, level.fireballFx().hard().count(), "a few flames");
        assertEquals(java.util.List.of(FIZZLE), harness.sounds, "the fizzle plays once");
    }

    @Test void aWallHitBurstsOnceAndSpraysBack() {
        Level level = harness.roomOf("forest-1", 2);
        Rectangle probe = new Rectangle();
        Fireball ball = null;
        float face = 0;
        for (CollisionBlock b : level.collisionBlocks) {
            if (b.isPlatform() || b.isQuickSand() || b.getHeight() < Fireball.BODY_SIZE + 2 * INSET) continue;
            float x = b.getX() - Fireball.BODY_SIZE - GAP, y = b.getY() + INSET;
            if (level.blocksObject(probe.set(x, y, Fireball.BODY_SIZE, Fireball.BODY_SIZE), false)) continue;
            ball = new Fireball(level, x, y, true);
            face = b.getX();
            break;
        }
        assertNotNull(ball, "the room has a solid block to hit");
        level.objects.add(ball);
        ball.update(STEP);
        assertTrue(ball.isRemoved(), "terrain stops it");
        assertEquals(java.util.List.of(FIZZLE), harness.sounds, "the fizzle plays once");
        assertEquals(1, level.fireballFx().ringCount(), "one ring per stop");
        assertTrue(level.fireballFx().soft().x(0) <= face, "the burst sits on the wall face, not inside the wall");
        assertNull(level.activeRipple(), "a ring only: no screen distortion");
        KimeriaParticleSystem sparks = level.fireballFx().hard();
        assertTrue(sparks.count() > 0);
        float wall = ball.getX() + Fireball.BODY_SIZE;
        level.fireballFx().update(SPRAY_SECONDS);
        int back = 0;
        for (int i = 0; i < sparks.count(); i++) if (sparks.x(i) < wall - SPRAY_CLEAR) back++;
        assertTrue(back > 0, "sparks fly back off the wall");
        nothingMoreAfter(level);
    }

    @Test void aMonsterHitBurstsOnceAndTheBossHitIsHeavier() {
        Level mobRoom = harness.roomOf("forest-1", 0);
        Enemy<?> mob = null;
        for (int i = 0; i < mobRoom.objects.size(); i++) if (mobRoom.objects.get(i) instanceof Enemy<?> e && !(e instanceof Boss)) mob = e;
        assertNotNull(mob, "the mob room has a monster");
        int light = burstAgainst(mobRoom, mob.hitboxBounds());
        assertTrue(mob.isDefeated(), "the fireball reached the monster");
        assertEquals(1, mobRoom.fireballFx().ringCount(), "one ring per stop");
        float lightRing = mobRoom.fireballFx().ringRadius(0);
        assertFalse(harness.sounds.contains(FIZZLE), "a creature hit has its own sound, not the fizzle");
        assertEquals(0, mobRoom.bloodParticles().count(), "a fireball hit sprays no blood");
        nothingMoreAfter(mobRoom);

        Level bossRoom = harness.roomOf("forest-2", 4);
        int heavy = burstAgainst(bossRoom, bossRoom.boss().hitboxBounds());
        assertEquals(0, bossRoom.bloodParticles().count(), "a fireball hit on the boss sprays no blood either");
        assertEquals(1, bossRoom.fireballFx().ringCount(), "one ring per stop");
        assertTrue(bossRoom.fireballFx().ringRadius(0) > lightRing, "the boss ring is larger");
        assertTrue(heavy > light, "the boss burst (" + heavy + ") is heavier than a monster's (" + light + ")");
    }

    private static int burstAgainst(Level level, Rectangle body) {
        Fireball ball = new Fireball(level, body.x - Fireball.BODY_SIZE, body.y + INSET, true);
        level.objects.add(ball);
        ball.update(STEP);
        assertTrue(ball.isRemoved(), "the ball is spent on the body");
        return total(level.fireballFx());
    }

    @Test void aRoomResetClearsTheTrailAndTheBurst() {
        Level level = harness.roomOf("forest-1", 2);
        Fireball ball = freeShot(level);
        level.objects.add(ball);
        fly(level, ball, FLIGHT_FRAMES);
        level.fireballFx().hit(ball.centerX(), ball.centerY(), 1);
        assertTrue(total(level.fireballFx()) > 0);
        assertEquals(1, level.fireballFx().ringCount());
        level.resetCurrentRoom(level.player);
        assertEquals(0, total(level.fireballFx()));
        assertEquals(0, level.fireballFx().ringCount(), "the ring is cleared with the room");
    }
}
