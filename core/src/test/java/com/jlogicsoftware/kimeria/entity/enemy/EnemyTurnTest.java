package com.jlogicsoftware.kimeria.entity.enemy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import org.junit.jupiter.api.Test;

class EnemyTurnTest {
    private enum None {}

    private static class Probe extends Enemy<None> {
        Probe() {
            super(null, null, "", 100, 50, 48, 32, 0, 0, None.class);
            hitbox = Hitbox.rect(10, 6, 14, 26);
        }

        @Override
        protected void setDefeated() {
        }

        @Override
        public void updateEnemy(float dt) {
        }
    }

    @Test
    void turningDoesNotMoveTheBody() {
        Probe p = new Probe();
        Rectangle right = p.hitboxBounds();
        p.facingRight = false;
        Rectangle left = p.hitboxBounds();
        assertEquals(right.x, left.x);
        assertEquals(right.width, left.width);
        assertEquals(right.x - p.getX(), p.facingAwareOffsetX());
    }

    @Test
    void anAttackBoxSwitchesSidesAroundTheBody() {
        Probe p = new Probe();
        Hitbox whip = Hitbox.rect(24, 6, 8, 26);
        Rectangle body = p.hitboxBounds();
        assertEquals(body.x + body.width, p.boundsOf(whip).x);
        p.facingRight = false;
        assertEquals(body.x - 8f, p.boundsOf(whip).x);
    }
}
