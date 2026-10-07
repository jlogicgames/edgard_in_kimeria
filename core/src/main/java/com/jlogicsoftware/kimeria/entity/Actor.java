package com.jlogicsoftware.kimeria.entity;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.physics.CollisionUtils;

import java.util.EnumMap;
import java.util.Map;

/**
 * Port of Dart's {@code Actor}: a {@code SpriteAnimationGroupComponent} with
 * a hitbox that may differ from the drawn sprite bounds.
 *
 * <p>Flame's {@code animationTicker.completed} future (awaited all over the
 * original code to sequence things after a one-shot animation finishes) has
 * no synchronous equivalent, so subclasses instead poll
 * {@link #isCurrentAnimationFinished()} once per frame from their own
 * update() to drive their phase state machines -- see {@code Player} for
 * the respawn/checkpoint sequences.
 *
 * @param <S> the actor's state enum (idle/running/... etc.)
 */
public abstract class Actor<S extends Enum<S>> extends Entity {
    public Hitbox hitbox = Hitbox.rect(0, 0, 0, 0);
    protected final Map<S, Animation<TextureRegion>> animations;
    protected S current;
    protected float stateTime = 0f;

    protected Actor(float x, float y, float w, float h, Class<S> stateType) {
        super(x, y, w, h);
        animations = new EnumMap<>(stateType);
    }

    protected void putAnimation(S state, Animation<TextureRegion> animation, boolean loop) {
        animation.setPlayMode(loop ? Animation.PlayMode.LOOP : Animation.PlayMode.NORMAL);
        animations.put(state, animation);
    }

    public void setState(S state) {
        if (state != current) {
            current = state;
            stateTime = 0f;
        }
    }

    public S getState() {
        return current;
    }

    protected void tickAnimation(float dt) {
        stateTime += dt;
    }

    public boolean isCurrentAnimationFinished() {
        Animation<TextureRegion> anim = animations.get(current);
        return anim == null || anim.isAnimationFinished(stateTime);
    }

    /**
     * Sprite-space X of the line this actor turns around: the centre of its body hitbox (or of
     * the sprite box if it has none). Turning mirrors everything about this line, so the body
     * stays exactly where it is and only the art and any attached boxes swap sides. Mirroring
     * about the sprite centre instead shifted an off-centre hitbox sideways on every turn.
     */
    protected float pivotX() {
        float w = hitbox.radius > 0 ? hitbox.radius * 2 : hitbox.width;
        return w > 0 ? hitbox.offsetX + w / 2f : size.x / 2f;
    }

    /** Horizontal hitbox offset for collision code. The body hitbox never moves when turning. */
    public float facingAwareOffsetX() {
        return hitbox.offsetX;
    }

    /** The hitbox in world space as a new rectangle (circles become their bounding square). */
    public Rectangle hitboxBounds() {
        return boundsOf(hitbox);
    }

    /** True when this actor's body hitbox (round if it is a circle) overlaps {@code rect}. */
    public boolean hitboxOverlaps(Rectangle rect) {
        return CollisionUtils.overlaps(hitbox, hitboxBounds(), rect);
    }

    /** Any box authored for the unflipped sprite, placed in world space and mirrored about {@link #pivotX()} when facing left. */
    public Rectangle boundsOf(Hitbox box) {
        float w = box.radius > 0 ? box.radius * 2 : box.width;
        float h = box.radius > 0 ? box.radius * 2 : box.height;
        float offsetX = facingRight ? box.offsetX : 2 * pivotX() - box.offsetX - w;
        return new Rectangle(position.x + offsetX, position.y + box.offsetY, w, h);
    }

    public void collidedWithActor(boolean gotHit) {
    }

    @Override
    public void render(Batch batch) {
        Animation<TextureRegion> anim = animations.get(current);
        if (anim == null) return;
        TextureRegion frame = anim.getKeyFrame(stateTime);
        if (facingRight) batch.draw(frame, position.x, position.y, size.x, size.y);
        else batch.draw(frame, position.x + 2 * pivotX(), position.y, -size.x, size.y);
    }
}
