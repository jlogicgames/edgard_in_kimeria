package com.jlogicsoftware.kimeria.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

class HitboxBoundsTest {
    private enum None {}

    private static class Probe extends Actor<None> {
        Probe() {
            super(100, 50, 48, 32, None.class);
            hitbox = Hitbox.rect(10, 6, 14, 26);
        }

        @Override
        public void update(float dt) {
        }
    }

    @Test
    void facingRightUsesTheAuthoredOffset() {
        Rectangle b = new Probe().hitboxBounds();
        assertEquals(110f, b.x);
        assertEquals(56f, b.y);
        assertEquals(14f, b.width);
        assertEquals(26f, b.height);
    }

    @Test
    void turningLeavesTheBodyWhereItIs() {
        Probe p = new Probe();
        Rectangle right = p.hitboxBounds();
        p.facingRight = false;
        assertEquals(right.x, p.hitboxBounds().x);
        assertEquals(right.width, p.hitboxBounds().width);
    }

    @Test
    void circleBecomesItsBoundingSquare() {
        Probe p = new Probe();
        p.hitbox = Hitbox.circle(8);
        Rectangle b = p.hitboxBounds();
        assertEquals(16f, b.width);
        assertEquals(16f, b.height);
    }

    @Test
    void anAttackBoxSwapsSidesAroundTheBodyCentre() {
        Probe p = new Probe();
        Hitbox whip = Hitbox.rect(24, 6, 8, 26);
        Rectangle body = p.hitboxBounds();
        assertEquals(body.x + body.width, p.boundsOf(whip).x);
        p.facingRight = false;
        assertEquals(body.x - 8f, p.boundsOf(whip).x);
    }

    /** The player's sword (9..46 beside an 18..29 body): same reach in front whichever way it faces. */
    @Test
    void anOffCentreBodyStillGetsEqualReachBothWays() {
        Probe p = new Probe();
        p.hitbox = Hitbox.rect(18, 26, 11, 22);
        Hitbox sword = Hitbox.rect(9, 12, 37, 36);
        Rectangle body = p.hitboxBounds();

        Rectangle facingRight = p.boundsOf(sword);
        float reachRight = facingRight.x + facingRight.width - (body.x + body.width);
        p.facingRight = false;
        Rectangle facingLeft = p.boundsOf(sword);
        float reachLeft = body.x - facingLeft.x;

        assertEquals(17f, reachRight);
        assertEquals(reachRight, reachLeft);
    }

    @Test
    void anActorWithoutAHitboxTurnsAboutTheSpriteCentre() {
        Probe p = new Probe();
        p.hitbox = Hitbox.rect(0, 0, 0, 0);
        Hitbox box = Hitbox.rect(0, 0, 10, 10);
        assertEquals(100f, p.boundsOf(box).x);
        p.facingRight = false;
        assertEquals(100f + 48f - 10f, p.boundsOf(box).x);
    }
}
