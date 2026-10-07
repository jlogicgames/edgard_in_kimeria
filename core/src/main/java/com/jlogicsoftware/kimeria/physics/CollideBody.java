package com.jlogicsoftware.kimeria.physics;

import com.jlogicsoftware.kimeria.entity.Collidable;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.world.Level;

/**
 * Port of Dart's {@code CollideMixin}: horizontal/vertical AABB sweeps
 * against a level's collision blocks, escalators and falling platforms.
 */
public interface CollideBody extends GravityBody {
    Hitbox hitbox();

    float facingAwareOffsetX();

    boolean isClambering();

    void setClambering(boolean value);

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

    default void checkHorizontalCollisions(Iterable<CollisionBlock> blocks) {
        for (CollisionBlock block : blocks) {
            if (!block.isActive()) continue;
            if (block.isQuickSand()) {
                setInQuickSand(CollisionUtils.checkCollision(this, block));
            } else if (block.isPlatform()) {
                // One-way platforms only support from above. Their overlap test is built for
                // landing (it even adds a catch-up margin at rest, e.g. at a jump's apex), so using
                // it sideways snapped actors across the platform to its far edge.
                continue;
            } else if (block.isWall()) {
                if (CollisionUtils.checkCollision(this, block)) {
                    if (velocity().x > 0) {
                        velocity().x = 0;
                        position().x = block.getX() - facingAwareOffsetX() - hitbox().width;
                        if (!isOnGround()) setClambering(true);
                        break;
                    }
                    if (velocity().x < 0) {
                        velocity().x = 0;
                        position().x = block.getX() + block.getWidth() - facingAwareOffsetX();
                        if (!isOnGround()) setClambering(true);
                        break;
                    }
                } else {
                    setClambering(false);
                }
            } else {
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

        for (CollisionBlock block : level.collisionBlocks) {
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

        for (Escalator escalator : level.escalators) {
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

        for (FallingPlatform platform : level.fallingPlatforms) {
            if (CollisionUtils.checkCollision(this, (Collidable) platform)) {
                if (velocity().y > 0) {
                    velocity().y = 0;
                    position().y = platform.getY() - hitbox().height - hitbox().offsetY;
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
}
