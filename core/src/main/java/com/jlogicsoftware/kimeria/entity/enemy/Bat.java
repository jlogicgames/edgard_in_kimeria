package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.Hitbox;

/**
 * Port of Dart's {@code Bat}: patrols a fixed horizontal or vertical range and never touches the ground alive. Killed, it falls
 * and lies on the floor below, a small body that rocks; over a pit nothing stays (D101, {@link FallenEnemy}).
 */
public class Bat extends FallenEnemy<Bat.State> {
    public enum State {IDLE, RUN, HIT}

    private static final float BAT_SPEED = 0.03f;
    private static final float MOVE_SPEED = 50f;
    /** Drops of a killed bat fall this far (px) below it, further than they travel in their life. */
    private static final float BLOOD_FALL = 256f;
    /** The lying body in the frame (bat.grid `dead`, cell (4, 4)): the whole width, down to the body hitbox's floor line. */
    private static final float FRAME_SIZE = 16, BODY_Y = 9, BODY_HEIGHT = 6;
    private static final float DEAD_CELL_X = 4 * FRAME_SIZE, DEAD_CELL_Y = 4 * FRAME_SIZE;
    /** The body's crumble chips: the sooty body, the scarlet finger bones and the wine membrane (bat.grid, D117). */
    private static final float[][] CHIPS = {{.165f, .078f, .094f}, {.973f, .016f, .251f}, {.424f, 0, .078f}, {.298f, .165f, .188f}};

    public final boolean isVertical;
    private final TextureRegion deadFrame;

    public Bat(Assets assets, GameContext game, float x, float y, float w, float h,
               boolean isVertical, float offNeg, float offPos) {
        super(assets, game, "Bat", x, y, w, h, offNeg, offPos, State.class, State.HIT,
            Hitbox.rect(0, BODY_Y, FRAME_SIZE, BODY_HEIGHT), CHIPS, false);
        deadFrame = assets.region("images/enemy/Bat.png", DEAD_CELL_X, DEAD_CELL_Y, FRAME_SIZE, FRAME_SIZE);
        this.isVertical = isVertical;
        moveDirection = 1;
        // Art spans x 0..16, y 2..14 of the 16x16 frame (bat.grid). The body (sword, stomp) covers it fully; what
        // kills on touch is a smaller box inside it, so the empty air around the wings is safe.
        hitbox = Hitbox.rect(0, 2, 16, 13);
        hurtbox = Hitbox.rect(3, 4, 10, 9);

        if (isVertical) {
            rangeNeg = y - offNeg * TILE_SIZE;
            rangePos = y + offPos * TILE_SIZE;
        } else {
            rangeNeg = x - offNeg * TILE_SIZE;
            rangePos = x + offPos * TILE_SIZE;
        }

        putAnimation(State.IDLE, spriteAnimation(5, BAT_SPEED, 16, 16, 0, 0), true);
        putAnimation(State.RUN, spriteAnimation(5, BAT_SPEED, 16, 16, 0, 32), true);
        putAnimation(State.HIT, spriteAnimation(4, BAT_SPEED, 16, 16, 0, 64), false);
        setState(State.IDLE);
    }

    @Override
    public void updateEnemy(float dt) {
        if (isVertical) {
            moveVertically(dt);
        } else {
            moveHorizontally(dt);
        }
    }

    private void moveVertically(float dt) {
        if (position.y >= rangePos) moveDirection = -1;
        else if (position.y <= rangeNeg) moveDirection = 1;
        position.y += moveDirection * MOVE_SPEED * dt;
    }

    private void moveHorizontally(float dt) {
        if (position.x >= rangePos) moveDirection = -1;
        else if (position.x <= rangeNeg) moveDirection = 1;
        velocity.x = moveDirection * MOVE_SPEED; // a mover moves bodies and bushes (D119)
        position.x += velocity.x * dt;
    }

    /** A bat hangs in the air: its drops fall past the body and fade out before they could land. */
    @Override
    protected float bloodFloorY(Rectangle body) { return body.y + body.height + BLOOD_FALL; }

    @Override
    protected TextureRegion restFrame() { return deadFrame; }
}
