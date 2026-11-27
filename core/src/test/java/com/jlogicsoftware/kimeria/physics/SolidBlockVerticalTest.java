package com.jlogicsoftware.kimeria.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which face of a solid block an overlapping body leaves by in the vertical pass (#600). World Y grows downward. */
class SolidBlockVerticalTest {
    private static final float BLOCK_X = 240, BLOCK_Y = 272, BLOCK_W = 64, THICK = 64, THIN = 16;
    private static final float BLOCK_BOTTOM = BLOCK_Y + THICK;
    private static final float OFFSET_X = 18, OFFSET_Y = 26, WIDTH = 11, HEIGHT = 22;
    /** The smallest walking body (the spider's and the snail's box is 13 px high). */
    private static final float SMALL_HEIGHT = 13;
    private static final float FRAME = 1f / 60f, LONG_FRAME = .05f, STALL = .25f;
    private static final float SPEED = 120, SHALLOW = 4, EXACT = .001f;
    private static final float[] FRAMES = {1f / 144f, FRAME, LONG_FRAME, .1f, STALL};

    private static class Body implements CollideBody {
        final Vector2 position = new Vector2();
        final Vector2 velocity = new Vector2();
        final Hitbox hitbox;
        boolean onGround;

        Body(float height) { hitbox = Hitbox.rect(OFFSET_X, OFFSET_Y, WIDTH, height); }

        /** Puts the hitbox's left and top edges at these world coordinates. */
        Body at(float left, float top) {
            position.set(left - OFFSET_X, top - OFFSET_Y);
            return this;
        }

        float left() { return position.x + OFFSET_X; }
        float top() { return position.y + OFFSET_Y; }
        float bottom() { return top() + hitbox.height; }

        public Vector2 position() { return position; }
        public Vector2 velocity() { return velocity; }
        public boolean isOnGround() { return onGround; }
        public void setOnGround(boolean value) { onGround = value; }
        public Hitbox hitbox() { return hitbox; }
        public float facingAwareOffsetX() { return hitbox.offsetX; }
        public boolean isInQuickSand() { return false; }
        public void setInQuickSand(boolean value) { }
        public Escalator currentEscalator() { return null; }
        public void setCurrentEscalator(Escalator escalator) { }
    }

    private static List<CollisionBlock> block(float height) {
        return List.of(CollisionBlock.plain(BLOCK_X, BLOCK_Y, BLOCK_W, height));
    }

    /** Left edges of a body inside the block's span and sticking out of its left and right sides. */
    private static final float[] LEFTS = {BLOCK_X + 20, BLOCK_X - WIDTH + SHALLOW, BLOCK_X + BLOCK_W - SHALLOW};
    private static final float[] SIGNS = {-SPEED, SPEED};

    @Test
    void anEmbeddedBodyNearTheTopFaceLeavesByTheTop() {
        for (float left : LEFTS) {
            for (float vy : SIGNS) {
                var body = new Body(HEIGHT).at(left, BLOCK_Y - HEIGHT + SHALLOW + 8);
                body.velocity.y = vy;

                body.checkVerticalCollisions(block(THICK), FRAME);

                String where = "left " + left + ", vy " + vy;
                assertEquals(BLOCK_Y, body.bottom(), EXACT, where);
                assertEquals(left, body.left(), EXACT, where);
                // Moving up it is already leaving: it keeps rising and does not stand. Moving down it lands.
                assertEquals(vy < 0 ? vy : 0, body.velocity.y, where);
                assertEquals(vy > 0, body.onGround, where);
            }
        }
    }

    @Test
    void anEmbeddedBodyNearTheBottomFaceLeavesByTheBottom() {
        for (float left : LEFTS) {
            for (float vy : SIGNS) {
                var body = new Body(HEIGHT).at(left, BLOCK_BOTTOM - SHALLOW - 8);
                body.velocity.y = vy;

                body.checkVerticalCollisions(block(THICK), FRAME);

                String where = "left " + left + ", vy " + vy;
                assertEquals(BLOCK_BOTTOM, body.top(), EXACT, where);
                assertEquals(left, body.left(), EXACT, where);
                // Moving up this is a ceiling hit. Moving down it is already leaving and keeps falling.
                assertEquals(vy > 0 ? vy : 0, body.velocity.y, where);
                assertFalse(body.onGround, where);
            }
        }
    }

    /** A ledge standing on a floor: its underside is nearer but leads into the floor, so the body comes out on top. */
    @Test
    void anUndersideLeadingIntoAnotherBlockIsNoExit() {
        var ledge = CollisionBlock.plain(BLOCK_X, BLOCK_Y, BLOCK_W, THICK);
        var floor = CollisionBlock.plain(BLOCK_X, BLOCK_BOTTOM, BLOCK_W, THICK);
        for (float vy : SIGNS) {
            var body = new Body(HEIGHT).at(BLOCK_X + 20, BLOCK_BOTTOM - HEIGHT);
            body.velocity.y = vy;

            body.checkVerticalCollisions(List.of(ledge, floor), FRAME);

            assertEquals(BLOCK_Y, body.bottom(), EXACT, "vy " + vy);
        }
    }

    /**
     * A body inside a floor that also clips a wall from the side comes out on the floor's top, in either list order. The
     * one exception is the behaviour from before #600: falling, with the wall listed first (as lever walls are), it
     * leaves by the wall's top.
     */
    @Test
    void aSideClipInTheFloorLeavesByTheFloorsTopUnlessAFallMeetsTheWallFirst() {
        float wallTop = 16;
        var floor = CollisionBlock.plain(BLOCK_X, BLOCK_Y, BLOCK_W, THICK);
        var wall = CollisionBlock.wall(BLOCK_X + 48, wallTop, THIN, BLOCK_Y - wallTop);
        for (float vy : SIGNS) {
            for (boolean wallFirst : new boolean[] {false, true}) {
                var body = new Body(HEIGHT).at(BLOCK_X + 48 - SHALLOW, BLOCK_Y - 10);
                body.velocity.y = vy;

                body.checkVerticalCollisions(wallFirst ? List.of(wall, floor) : List.of(floor, wall), FRAME);

                assertEquals(vy > 0 && wallFirst ? wallTop : BLOCK_Y, body.bottom(), EXACT,
                    "vy " + vy + ", wall first " + wallFirst);
                assertEquals(BLOCK_X + 48 - SHALLOW, body.left(), EXACT);
            }
        }
    }

    /** A jump lifts the body a reference step before gravity moves it: still a ceiling hit under two uneven ceilings. */
    @Test
    void aJumpIntoUnevenCeilingsNeverPutsTheBodyOnTop() {
        var low = CollisionBlock.plain(BLOCK_X, 200, 32, 56);
        var lower = CollisionBlock.plain(BLOCK_X + 32, 200, 32, 58);
        for (var ceilings : List.of(List.of(low, lower), List.of(lower, low))) {
            var body = new Body(HEIGHT).at(BLOCK_X + 26, 259);
            body.velocity.y = -body.jumpForce();
            body.position.y += body.velocity.y * GravityBody.REFERENCE_STEP;
            body.applyGravity(FRAME);

            body.checkVerticalCollisions(ceilings, FRAME);

            assertTrue(body.top() >= 256 - EXACT, "top " + body.top());
            assertEquals(0, body.velocity.y);
        }
    }

    /** A landing keeps the top face at every frame time, even where the step is deeper than half of block plus body. */
    @Test
    void aFallingBodyLandsOnAThinBlockAtAnyFrameTime() {
        int deep = 0;
        for (float dt : FRAMES) {
            for (float speed : new float[] {0, SPEED, 300}) {
                for (float height : new float[] {SMALL_HEIGHT, HEIGHT}) {
                    var body = new Body(height).at(BLOCK_X + 20, BLOCK_Y - height);
                    body.velocity.y = speed;
                    body.applyGravity(dt);
                    // A step longer than block plus body passes clean through: tunnelling, not this rule's case.
                    if (body.top() >= BLOCK_Y + THIN) continue;
                    if (body.bottom() - BLOCK_Y > (THIN + height) / 2) deep++;

                    body.checkVerticalCollisions(block(THIN), dt);

                    String where = "dt " + dt + ", speed " + speed + ", height " + height;
                    assertEquals(BLOCK_Y, body.bottom(), EXACT, where);
                    assertEquals(0, body.velocity.y, where);
                    assertTrue(body.onGround, where);
                }
            }
        }
        assertTrue(deep > 0, "no landing was deeper than the nearer face, the case a plain least-overlap rule gets wrong");
    }

    @Test
    void aRisingBodyHitsAThinCeilingAtAnyFrameTime() {
        float ceilingBottom = BLOCK_Y + THIN;
        int deep = 0;
        for (float dt : FRAMES) {
            for (float speed : new float[] {SPEED, 260}) {
                for (float height : new float[] {SMALL_HEIGHT, HEIGHT}) {
                    var body = new Body(height).at(BLOCK_X + 20, ceilingBottom);
                    body.velocity.y = -speed;
                    body.applyGravity(dt);
                    // A long step can end falling again, or pass clean through: neither is a ceiling hit.
                    if (body.velocity.y >= 0 || body.top() >= ceilingBottom || body.bottom() <= BLOCK_Y) continue;
                    if (ceilingBottom - body.top() > (THIN + height) / 2) deep++;

                    body.checkVerticalCollisions(block(THIN), dt);

                    String where = "dt " + dt + ", speed " + speed + ", height " + height;
                    assertEquals(ceilingBottom, body.top(), EXACT, where);
                    assertEquals(0, body.velocity.y, where);
                    assertFalse(body.onGround, where);
                }
            }
        }
        assertTrue(deep > 0, "no ceiling hit was deeper than the nearer face");
    }

    @Test
    void aBodyWithNoVerticalVelocityIsLeftAlone() {
        var body = new Body(HEIGHT).at(BLOCK_X + 20, BLOCK_Y - SHALLOW);
        float y = body.position.y;

        body.checkVerticalCollisions(block(THICK), FRAME);

        assertEquals(y, body.position.y);
        assertFalse(body.onGround);
    }
}
