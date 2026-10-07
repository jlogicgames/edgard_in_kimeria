package com.jlogicsoftware.kimeria.entity.enemy;

import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.Hitbox;

/** Port of Dart's {@code Bat}: patrols a fixed horizontal or vertical range, never touches the ground. */
public class Bat extends Enemy<Bat.State> {
    public enum State {IDLE, RUN, HIT}

    private static final float BAT_SPEED = 0.03f;
    private static final float MOVE_SPEED = 50f;

    public final boolean isVertical;

    public Bat(Assets assets, GameContext game, float x, float y, float w, float h,
               boolean isVertical, float offNeg, float offPos) {
        super(assets, game, "Bat", x, y, w, h, offNeg, offPos, State.class);
        this.isVertical = isVertical;
        moveDirection = 1;
        // Art spans x 0..16, y 3..14 of the 16x16 frame. The body (sword, stomp) covers it fully; what
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
        position.x += moveDirection * MOVE_SPEED * dt;
    }

    @Override
    protected void setDefeated() {
        setState(State.HIT);
    }
}
