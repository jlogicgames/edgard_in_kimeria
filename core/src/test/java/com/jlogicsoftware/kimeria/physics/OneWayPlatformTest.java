package com.jlogicsoftware.kimeria.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jlogicsoftware.kimeria.testsupport.HeroBody;

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
        final Hitbox hitbox = Hitbox.rect(HeroBody.LEFT, HeroBody.TOP, HeroBody.W, HeroBody.H);

        public Vector2 position() { return position; }
        public Vector2 velocity() { return velocity; }
        public boolean isOnGround() { return false; }
        public void setOnGround(boolean value) { }
        public Hitbox hitbox() { return hitbox; }
        public float facingAwareOffsetX() { return hitbox.offsetX; }
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
        body.position.set(250 - HeroBody.LEFT, 272 - HeroBody.FEET - 10); // feet 10px above the platform's top
        body.velocity.set(-80, 0);
        float startX = body.position.x;

        body.checkHorizontalCollisions(List.of(platform));

        assertEquals(startX, body.position.x);
        assertEquals(-80f, body.velocity.x);
    }

    /** D164: a fall longer than the platform is thick (19 px against 16 at the hero's top speed in a 0.05 s frame) still lands. */
    @Test
    void aFallLongerThanThePlatformIsThickStillLandsOnIt() {
        var platform = CollisionBlock.platform(240, 272, 32, 16);
        var body = new Body();
        float dt = .05f, speed = 387f, travel = speed * dt;
        body.position.set(250 - HeroBody.LEFT, 272 - HeroBody.FEET - 2 + travel); // the feet went from 2 px above to past its bottom
        body.velocity.set(0, speed);

        body.checkVerticalCollisions(List.of(platform), dt);

        assertEquals(272 - HeroBody.FEET, body.position.y);
        assertEquals(0f, body.velocity.y);

        body.position.set(250 - HeroBody.LEFT, 272 - HeroBody.FEET + 17); // already below its top before the step
        body.velocity.set(0, 100);
        body.checkVerticalCollisions(List.of(platform), 1f / 60f);
        assertEquals(272 - HeroBody.FEET + 17, body.position.y, "a body that never was above it is not lifted onto it");
    }

    @Test
    void solidBlocksStillStopSidewaysMovement() {
        var block = CollisionBlock.plain(240, 272, 32, 16);
        var body = new Body();
        body.position.set(250 - HeroBody.LEFT, 272 - HeroBody.TOP - 4); // feet 24px below the block's top: overlapping its side
        body.velocity.set(-80, 0);

        body.checkHorizontalCollisions(List.of(block));

        assertEquals(272f - HeroBody.LEFT, body.position.x);
        assertEquals(0f, body.velocity.x);
    }
}
