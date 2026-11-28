package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.jlogicsoftware.kimeria.testsupport.HeroBody;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.objects.LegHold;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D128 (#561): the real Carryable and Fireball paths against a leg-hold on {@code leghold-test.tmx} (a stone at x 96, the jaws at x 224,
 * the litter's box x 228-252, floor-6..floor). A thrown stone that lands on the litter snaps the jaws empty and is not consumed; a fireball
 * flies at floor-19..floor-9 with no gravity, passes over the flat litter, and never snaps it.
 */
class LegHoldProjectilesTest {
    private static final float STEP = 1f / 60f;
    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Rectangle view = new Rectangle(0, 0, 640, 368);

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Level room() { return h.room("leghold-test", "floor", () -> view); }
    private void frame(Level level) { level.update(STEP); h.pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private static LegHold legHold(Level level) { return level.objects.stream().filter(LegHold.class::isInstance).map(LegHold.class::cast).findFirst().orElseThrow(); }
    private static Carryable stone(Level level) { return level.objects.stream().filter(Carryable.class::isInstance).map(Carryable.class::cast).findFirst().orElseThrow(); }
    private static Rectangle body(Level level) { return level.player.hitboxBounds(); }

    /** Picks up the stone, walks until the body's right edge reaches {@code from} (outside the zone, which starts at x 204) and throws it. */
    private void throwStoneFrom(Level level, float from) {
        level.player.position.set(108 - HeroBody.RIGHT, 272);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        h.held.clear(); frame(level); h.held.add(Input.Keys.W); frame(level); // Up alone interacts (D126)
        h.held.clear(); frame(level);
        assertSame(stone(level), level.player.carriedObject(), "picked up the stone");
        for (int f = 0; body(level).x + body(level).width < from && f < 600; f++) { h.held.clear(); h.held.add(Input.Keys.D); frame(level); }
        h.held.clear();
        settle(level, 20);
        h.held.add(Input.Keys.L); h.pressed.add(Input.Keys.L); frame(level);
        h.held.clear();
    }

    @Test void aThrownStoneLandingOnTheLitterSnapsItEmptyAndIsNotConsumed() {
        int hits = 0;
        float nearest = Float.MAX_VALUE, farthest = 0;
        for (float from = 120; from <= 202; from += 2) {
            Level level = room();
            int ammo = level.rangedStock.count();
            throwStoneFrom(level, from);
            LegHold t = legHold(level);
            boolean snapped = false;
            for (int i = 0; i < 90 && !snapped; i++) { frame(level); snapped = t.phase() == LegHold.Phase.SNAP || t.phase() == LegHold.Phase.SHUT; }
            if (!snapped) continue;
            hits++;
            nearest = Math.min(nearest, from); farthest = Math.max(farthest, from);
            assertFalse(level.player.isGotHit(), "a snap from afar catches nobody");
            settle(level, 60);
            assertTrue(stone(level).available(), "the stone dropped and lies at rest (D39)");
            assertFalse(stone(level).isRemoved(), "not consumed");
            assertEquals(ammo, level.rangedStock.count(), "a stone costs no charge");
        }
        System.out.println("leg-hold stone: " + hits + " hits, thrown from body right edge " + nearest + " to " + farthest);
        assertTrue(hits >= 3, "a stone thrown from the right range lands on the litter and snaps it (found " + hits + ")");
    }

    @Test void aFireballFliesOverTheFlatLitterAndNeverSnapsIt() {
        Level level = room();
        level.rangedStock.restore(3);
        level.player.position.set(140 - HeroBody.RIGHT, 272);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        settle(level, 20);
        LegHold t = legHold(level);
        Rectangle box = t.openBoxInto(new Rectangle());
        int charges = level.rangedStock.count();
        assertTrue(charges > 0);
        h.held.add(Input.Keys.L); h.pressed.add(Input.Keys.L); frame(level); h.held.clear();
        assertEquals(charges - 1, level.rangedStock.count(), "a charge is paid");
        boolean flew = false, overlapped = false;
        for (int i = 0; i < 150; i++) {
            frame(level);
            for (var o : level.objects) if (o instanceof Fireball ball) {
                flew = true;
                Rectangle b = new Rectangle(ball.getX(), ball.getY(), ball.getWidth(), ball.getHeight());
                if (b.overlaps(box)) overlapped = true;
                assertTrue(b.y + b.height <= box.y, "the ball flies over the litter: ball bottom " + (b.y + b.height) + " vs litter top " + box.y);
            }
            assertEquals(LegHold.Phase.OPEN, t.phase(), "a fireball never snaps it (frame " + i + ")");
        }
        assertTrue(flew, "the fireball was in the air");
        assertFalse(overlapped);
    }
}
