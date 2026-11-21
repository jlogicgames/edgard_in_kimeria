package com.jlogicsoftware.kimeria.physics;

import com.jlogicsoftware.kimeria.entity.Collidable;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.world.Level;

import java.util.List;

/**
 * Port of Dart's {@code CollideMixin}: horizontal/vertical AABB sweeps
 * against a level's collision blocks, escalators and falling platforms.
 */
public interface CollideBody extends GravityBody {
    /** How fast a body resting in quicksand sinks, px/s: the 9.8 px per second the 60 Hz build always had (D98). */
    static final float QUICKSAND_SINK_SPEED = 9.8f;

    Hitbox hitbox();

    float facingAwareOffsetX();

    boolean isInQuickSand();

    void setInQuickSand(boolean value);

    Escalator currentEscalator();

    void setCurrentEscalator(Escalator escalator);

    /** Called when a falling platform hits the actor while it's already falling (Player only). */
    default void collidedWithFallingPlatform() {
    }

    default void checkHorizontalCollisions(Level level) {
        checkHorizontalCollisions(level.collisionBlocks);
    }

    /** Index loops, no iterator (AGENTS.md: no for-each over a List in per-frame code; #349, D80). */
    default void checkHorizontalCollisions(List<CollisionBlock> blocks) {
        for (int b = 0; b < blocks.size(); b++) {
            CollisionBlock block = blocks.get(b);
            if (!block.isActive()) continue;
            if (block.isQuickSand()) {
                setInQuickSand(CollisionUtils.checkCollision(this, block));
            } else if (block.isPlatform()) {
                // One-way platforms only support from above. Their overlap test is built for
                // landing (it even adds a catch-up margin at rest, e.g. at a jump's apex), so using
                // it sideways snapped actors across the platform to its far edge.
                continue;
            } else {
                // Walls and plain blocks alike: solid from the side, never grabbed (D121).
                if (CollisionUtils.checkCollision(this, block)) {
                    if (velocity().x > 0) {
                        velocity().x = 0;
                        position().x = block.getX() - facingAwareOffsetX() - hitbox().width;
                        break;
                    }
                    if (velocity().x < 0) {
                        velocity().x = 0;
                        position().x = block.getX() + block.getWidth() - facingAwareOffsetX();
                        break;
                    }
                }
            }
        }
    }

    default void checkVerticalCollisions(Level level, float dt) {
        setCurrentEscalator(null);

        for (int b = 0; b < level.collisionBlocks.size(); b++) {
            CollisionBlock block = level.collisionBlocks.get(b);
            if (!block.isActive()) continue;
            if (block.isPlatform()) {
                if (CollisionUtils.checkCollision(this, block)) {
                    if (velocity().y > 0) {
                        velocity().y = 0;
                        position().y = block.getY() - hitbox().height - hitbox().offsetY;
                        setOnGround(true);
                        break;
                    }
                }
            } else if (block.isQuickSand()) {
                if (CollisionUtils.checkCollision(this, block)) {
                    if (velocity().y > 0) {
                        // Quicksand holds the body without a snap: it sinks at QUICKSAND_SINK_SPEED at any frame rate (D98).
                        // The correction is zero at 60 Hz, where the resting gravity step already moves exactly that far.
                        if (fellFromRest(dt)) position().y += QUICKSAND_SINK_SPEED * dt - restingFallStep(dt);
                        velocity().y = 0;
                        setOnGround(true);
                        break;
                    }
                }
            } else {
                if (CollisionUtils.checkCollision(this, block)) {
                    if (velocity().y > 0) {
                        velocity().y = 0;
                        position().y = block.getY() - hitbox().height - hitbox().offsetY;
                        setOnGround(true);
                        break;
                    }
                    if (velocity().y < 0) {
                        velocity().y = 0;
                        position().y = block.getY() + block.getHeight() - hitbox().offsetY;
                    }
                }
            }
        }

        for (int e = 0; e < level.escalators.size(); e++) {
            Escalator escalator = level.escalators.get(e);
            if (!escalator.isActive()) continue;
            if (CollisionUtils.checkCollision(this, escalator)) {
                if (velocity().y > 0) {
                    velocity().y = 0;
                    position().y = escalator.getY() - hitbox().height - hitbox().offsetY;
                    setOnGround(true);
                    setCurrentEscalator(escalator);
                    break;
                }
            }
        }

        for (int f = 0; f < level.fallingPlatforms.size(); f++) {
            FallingPlatform platform = level.fallingPlatforms.get(f);
            if (CollisionUtils.checkCollision(this, (Collidable) platform)) {
                if (velocity().y > 0) {
                    float snapY = platform.getY() - hitbox().height - hitbox().offsetY;
                    // A collapsing platform that has sunk below a solid floor keeps falling as scenery, but it
                    // must not carry the body into that floor (#355, D69): the solid pass above already stood it there.
                    if (overlapsSolidAt(level, snapY)) continue;
                    velocity().y = 0;
                    position().y = snapY;
                    setOnGround(true);
                    if (!platform.isFalling()) {
                        platform.collideWithActor();
                    }
                    break;
                } else {
                    collidedWithFallingPlatform();
                }
            }
        }
    }

    /** True when the body, moved to {@code y}, would overlap a solid block (not a one-way platform or quicksand). No allocation: an index loop and no boxing, checked by `tools/check_allocations.py` (HOT_METHODS), not by a budget scene. */
    default boolean overlapsSolidAt(Level level, float y) {
        float original = position().y;
        position().y = y;
        boolean overlaps = false;
        for (int i = 0; i < level.collisionBlocks.size() && !overlaps; i++) {
            CollisionBlock block = level.collisionBlocks.get(i);
            overlaps = block.isActive() && !block.isPlatform() && !block.isQuickSand() && CollisionUtils.checkCollision(this, block);
        }
        position().y = original;
        return overlaps;
    }
}
