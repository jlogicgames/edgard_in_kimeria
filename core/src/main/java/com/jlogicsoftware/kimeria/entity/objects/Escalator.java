package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Collidable;
import com.jlogicsoftware.kimeria.entity.items.Actionable;

/** Port of Dart's {@code Escalator}: a moving platform that can be paused by a {@link Actionable} trigger. */
public class Escalator extends Actor<Escalator.State> implements Collidable, Actionable {
    public enum State {IDLE, RUN}

    private static final float TILE_SIZE = 32f;
    /**
     * Native sprite height (see the frame-size comment in the constructor).
     * The Tiled object's own height (16px) is the collision footprint, not
     * the sprite size -- stretching an 8px-tall frame to fill it distorted
     * the art, so the sprite is drawn at its native size instead, centered
     * in that footprint.
     */
    private static final float VISUAL_HEIGHT = 8f;

    public final boolean isVertical;
    private final float offNeg, offPos;
    private final float moveSpeed;
    private final String targetId;
    private float rangeNeg, rangePos;
    private int moveDirection = 1;
    private boolean active = true;

    public Escalator(Assets assets, float x, float y, float w, float h,
                      boolean isVertical, float offNeg, float offPos, String targetId) {
        super(x, y, w, h, State.class);
        this.isVertical = isVertical;
        this.offNeg = offNeg;
        this.offPos = offPos;
        this.moveSpeed = 50f;
        this.targetId = targetId == null ? "" : targetId;

        if (isVertical) {
            rangeNeg = y - offNeg * TILE_SIZE;
            rangePos = y + offPos * TILE_SIZE;
        } else {
            rangeNeg = x - offNeg * TILE_SIZE;
            rangePos = x + offPos * TILE_SIZE;
        }

        String offImage = "images/objects/GreyOff.png";
        String onImage = "images/objects/GreyOn.png";
        // Filenames deliberately have no spaces or parentheses: the gdx-teavm
        // dev server 404s on percent-encoded paths, which broke the web build.
        // Both sheets are 8px tall (GreyOff.png: 32x8, one frame; GreyOn.png:
        // 256x8, 8 frames in a single row) -- not the 16px the
        // original Dart code assumed, and without an explicit amountPerRow
        // the default (>4 frames wraps at 4-per-row) would also have sliced
        // the single-row "on" sheet into two bogus rows.
        putAnimation(State.IDLE, assets.animation(offImage, 1, 0.05f, 32, 8, 0, 0, 1), true);
        putAnimation(State.RUN, assets.animation(onImage, 8, 0.05f, 32, 8, 0, 0, 8), true);
        setState(State.RUN);
    }

    /** (1,0)/(−1,0) for horizontal escalators, (0,1)/(0,−1) for vertical ones. */
    public Vector2 currentMoveDirection() {
        return isVertical ? new Vector2(0, moveDirection) : new Vector2(moveDirection, 0);
    }

    public float moveSpeed() {
        return moveSpeed;
    }

    @Override
    public void update(float dt) {
        if (current == State.RUN) {
            if (isVertical) {
                moveVertically(dt);
            } else {
                moveHorizontally(dt);
            }
        }
        tickAnimation(dt);
    }

    private void moveVertically(float dt) {
        if (position.y >= rangePos) {
            moveDirection = -1;
        } else if (position.y <= rangeNeg) {
            moveDirection = 1;
        }
        position.y += moveDirection * moveSpeed * dt;
    }

    private void moveHorizontally(float dt) {
        if (position.x >= rangePos) {
            moveDirection = -1;
            facingRight = !facingRight;
        } else if (position.x <= rangeNeg) {
            moveDirection = 1;
            facingRight = !facingRight;
        }
        position.x += moveDirection * moveSpeed * dt;
    }

    @Override
    public void performAction() {
        setState(current == State.IDLE ? State.RUN : State.IDLE);
    }

    @Override
    public void render(Batch batch) {
        Animation<TextureRegion> anim = animations.get(current);
        if (anim == null) return;
        TextureRegion frame = anim.getKeyFrame(stateTime);
        float drawY = position.y + (size.y - VISUAL_HEIGHT) / 2f;
        float w = facingRight ? size.x : -size.x;
        float x = facingRight ? position.x : position.x + size.x;
        batch.draw(frame, x, drawY, w, VISUAL_HEIGHT);
    }

    @Override
    public String getTargetId() {
        return targetId;
    }

    @Override
    public float getX() {
        return position.x;
    }

    @Override
    public float getY() {
        return position.y;
    }

    @Override
    public float getWidth() {
        return size.x;
    }

    @Override
    public float getHeight() {
        return size.y;
    }

    @Override
    public boolean isQuickSand() {
        return false;
    }

    @Override
    public boolean isPlatform() {
        // Deliberately not "false" like the original: that made
        // CollisionUtils.checkCollision test the rider's TOP edge against
        // this escalator's Y band (much shorter than the rider is tall)
        // instead of a normal one-way "feet on top" contact test, which
        // dropped and regained contact almost every frame while riding --
        // visible as a jitter, worse the faster the escalator moves.
        // Treating it as a platform gets the same feet-based check (and its
        // catch-up margin for a fast-moving surface) already proven to work
        // for FallingPlatform.
        return true;
    }

    @Override
    public boolean isWall() {
        return false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isEscalator() {
        return true;
    }
}
