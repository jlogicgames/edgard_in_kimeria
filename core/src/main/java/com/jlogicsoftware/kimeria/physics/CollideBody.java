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
    /** Float rounding allowed on top of a step's own travel when judging how deep a fresh contact can be, px (#600). */
    static final float CONTACT_SLACK = .5f;

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

        checkVerticalCollisions(level.collisionBlocks, dt);

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

    /** The collision-block part of the vertical pass: platforms, quicksand and solid blocks. */
    default void checkVerticalCollisions(List<CollisionBlock> blocks, float dt) {
        for (int b = 0; b < blocks.size(); b++) {
            CollisionBlock block = blocks.get(b);
            if (!block.isActive()) continue;
            if (block.isPlatform()) {
                if (CollisionUtils.checkCollision(this, block) || fellThrough(block, dt)) {
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
                if (velocity().y != 0 && CollisionUtils.checkCollision(this, block) && leaveSolid(blocks, block, dt)) break;
            }
        }
    }

    /**
     * True when this step carried a falling body's feet from above a one-way platform's top to at or below its bottom: a fall
     * longer than the platform is thick (the hero at his top speed in a long frame, D164) still lands on it.
     */
    default boolean fellThrough(CollisionBlock block, float dt) {
        float vy = velocity().y;
        if (vy <= 0) return false;
        float feet = position().y + hitbox().offsetY + hitbox().height;
        float travel = vy * dt + gravityPerSecond() * dt * Math.max(0f, REFERENCE_STEP - dt) / 2f;
        float left = position().x + facingAwareOffsetX();
        return feet >= block.getY() + block.getHeight() && feet - travel <= block.getY()
            && left < block.getX() + block.getWidth() && left + hitbox().width > block.getX();
    }

    /**
     * Moves a body that overlaps a solid block out of it, up or down; true when it lands on the top face (#600). A contact
     * no deeper than this step's travel is fresh: a fall lands on the top face and a rise hits the underside, however thin
     * the block or long the frame. A deeper one means the body was already inside, so it leaves by the nearer of the two
     * faces; the underside counts only when it leads clear of the other solid blocks, else the body leaves by the top.
     * A rising body that only clips such a block from the side is not moved by it: its top may be far above. Velocity
     * stops only when it points into the face the body leaves by. Sideways overlap is the horizontal pass's business.
     */
    default boolean leaveSolid(List<CollisionBlock> blocks, CollisionBlock block, float dt) {
        float vy = velocity().y;
        float top = position().y + hitbox().offsetY;
        float up = top + hitbox().height - block.getY(), down = block.getY() + block.getHeight() - top;
        float bottomExit = block.getY() + block.getHeight() - hitbox().offsetY;
        // The most the body can have moved this step: velocity times dt plus applyGravity's frame-rate term (D98),
        // and, rising, the launch frame's own lift of a jump.
        float reach = Math.abs(vy) * dt + gravityPerSecond() * dt * Math.abs(dt - REFERENCE_STEP) / 2f + CONTACT_SLACK;
        if (vy < 0) reach += jumpForce() * REFERENCE_STEP;
        boolean byTop = vy > 0;
        if ((byTop ? up : down) > reach) {
            // On a tie the face the body is moving into wins: the top for a fall, the underside for a rise.
            byTop = up < down || up == down && vy > 0;
            if (!byTop && overlapsSolid(blocks, block, bottomExit)) {
                float left = position().x + facingAwareOffsetX();
                boolean clipsSide = left < block.getX() || left + hitbox().width > block.getX() + block.getWidth();
                if (vy < 0 && clipsSide) return false;
                byTop = true;
            }
        }
        if (!byTop) {
            position().y = bottomExit;
            if (vy < 0) velocity().y = 0;
            return false;
        }
        position().y = block.getY() - hitbox().height - hitbox().offsetY;
        if (vy < 0) return false; // already on its way out
        velocity().y = 0;
        setOnGround(true);
        return true;
    }

    /** True when the body, moved to {@code y}, would overlap a solid block (not a one-way platform or quicksand). No allocation: an index loop and no boxing, checked by `tools/check_allocations.py` (HOT_METHODS), not by a budget scene. */
    default boolean overlapsSolidAt(Level level, float y) {
        return overlapsSolid(level.collisionBlocks, null, y);
    }

    /** {@link #overlapsSolidAt} over a list of blocks, leaving out {@code except} (may be null). */
    default boolean overlapsSolid(List<CollisionBlock> blocks, CollisionBlock except, float y) {
        float original = position().y;
        position().y = y;
        boolean overlaps = false;
        for (int i = 0; i < blocks.size() && !overlaps; i++) {
            CollisionBlock block = blocks.get(i);
            overlaps = block != except && block.isActive() && !block.isPlatform() && !block.isQuickSand()
                && CollisionUtils.checkCollision(this, block);
        }
        position().y = original;
        return overlaps;
    }
}
