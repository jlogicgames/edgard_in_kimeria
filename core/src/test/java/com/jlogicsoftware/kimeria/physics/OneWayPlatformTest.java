package com.jlogicsoftware.kimeria.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import java.util.List;
import org.junit.jupiter.api.Test;

class OneWayPlatformTest {
    private static class Body implements CollideBody {
        final Vector2 position = new Vector2();
        final Vector2 velocity = new Vector2();
        final Hitbox hitbox = Hitbox.rect(18, 26, 11, 22);

        public Vector2 position() { return position; }
        public Vector2 velocity() { return velocity; }
        public boolean isOnGround() { return false; }
        public void setOnGround(boolean value) { }
        public Hitbox hitbox() { return hitbox; }
        public float facingAwareOffsetX() { return hitbox.offsetX; }
        public boolean isClambering() { return false; }
        public void setClambering(boolean value) { }
        public boolean isInQuickSand() { return false; }
        public void setInQuickSand(boolean value) { }
        public Escalator currentEscalator() { return null; }
        public void setCurrentEscalator(Escalator escalator) { }
    }

    /** At a jump's apex (vy ~ 0) just above a platform, walking sideways must not snap the body across it. */
    @Test
    void walkingSidewaysAboveAPlatformAtTheApexDoesNotSnapToItsFarEdge() {
        var platform = CollisionBlock.platform(240, 272, 32, 16);
        var body = new Body();
        body.position.set(250 - 18, 272 - 22 - 26 - 10); // feet 10px above the platform's top
        body.velocity.set(-80, 0);
        float startX = body.position.x;

        body.checkHorizontalCollisions(List.of(platform));

        assertEquals(startX, body.position.x);
        assertEquals(-80f, body.velocity.x);
    }

    @Test
    void solidBlocksStillStopSidewaysMovement() {
        var block = CollisionBlock.plain(240, 272, 32, 16);
        var body = new Body();
        body.position.set(250 - 18, 272 - 26 - 4); // feet 18px below the block's top: overlapping its side
        body.velocity.set(-80, 0);

        body.checkHorizontalCollisions(List.of(block));

        assertEquals(272f - 18f, body.position.x);
        assertEquals(0f, body.velocity.x);
    }
}
