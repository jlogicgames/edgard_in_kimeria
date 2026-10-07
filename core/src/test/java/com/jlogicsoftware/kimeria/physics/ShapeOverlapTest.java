package com.jlogicsoftware.kimeria.physics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import org.junit.jupiter.api.Test;

class ShapeOverlapTest {
    private static final Hitbox BAT = Hitbox.circle(8);
    private static final Rectangle BAT_AT = new Rectangle(100, 100, 16, 16);

    @Test
    void aRectangleOverlappingTheRoundPartHits() {
        assertTrue(CollisionUtils.overlaps(BAT, BAT_AT, new Rectangle(114, 106, 10, 4)));
    }

    @Test
    void theEmptyCornersOfTheBoundingSquareDoNotHit() {
        // Touches the square's top-left corner only; that point is ~3.3px outside the circle.
        assertFalse(CollisionUtils.overlaps(BAT, BAT_AT, new Rectangle(95, 95, 6, 6)));
    }

    @Test
    void aRectangleCentredInsideTheCircleHits() {
        assertTrue(CollisionUtils.overlaps(BAT, BAT_AT, new Rectangle(106, 106, 4, 4)));
    }

    @Test
    void aPlainBoxStillUsesItsBounds() {
        assertTrue(CollisionUtils.overlaps(Hitbox.rect(0, 0, 16, 16), BAT_AT, new Rectangle(95, 95, 6, 6)));
    }
}
