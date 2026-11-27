package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.math.MathUtils;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.Hitbox;

/** Port of Dart's {@code YellowMob}: a ground patrol enemy that charges the player when in range. */
public class YellowMob extends FallenEnemy<YellowMob.State> {
    public enum State {IDLE, RUN, HIT}

    private static final float STEP_TIME = 0.05f;
    private static final float RUN_SPEED = 80f;
    /** The lying body in the 48x32 frame, the death clip's last (D101): what a weapon or a stomp reaches. */
    private static final Hitbox BODY = Hitbox.rect(4, 23, 26, 9);
    /** The body's crumble chips, its live palette (D117): oxblood hide, bone mask, rust lights and shade. */
    private static final float[][] CHIPS = {{.369f, .133f, .094f}, {.690f, .643f, .455f}, {.557f, .204f, .125f}, {.180f, .102f, .086f}};

    private float targetDirection = -1;

    public YellowMob(Assets assets, GameContext game, float x, float y, float w, float h,
                      float offNeg, float offPos) {
        super(assets, game, "yellow_mob", x, y, w, h, offNeg, offPos, State.class, State.HIT, BODY, CHIPS, true);
        // Every live frame is drawn within x 7..25, y 5..32 (yellow-mob.grid); the hitbox is a pixel larger on each open side (D10).
        hitbox = Hitbox.rect(6, 4, 20, 28);
        hurtbox = Hitbox.rect(10, 6, 14, 26); // what kills on touch stays inside the art

        putAnimation(State.IDLE, spriteAnimation(4, STEP_TIME, 48, 32, 0, 32 * 5), true);
        putAnimation(State.RUN, spriteAnimation(4, STEP_TIME, 48, 32, 0, 32), true);
        putAnimation(State.HIT, spriteAnimation(4, STEP_TIME, 48, 32, 0, 32 * 4), false);
        setState(State.IDLE);

        rangeNeg = x - offNeg * TILE_SIZE;
        rangePos = x + offPos * TILE_SIZE;
    }

    @Override
    public void updateEnemy(float dt) {
        updateState();
        movement(dt);
        checkHorizontalCollisions(level);
        applyGravity(dt);
        checkVerticalCollisions(level, dt);
    }

    private void movement(float dt) {
        velocity.x = 0;
        if (playerInRange()) {
            targetDirection = directionToPlayer(targetDirection);
            velocity.x = targetDirection * RUN_SPEED;
        }

        moveDirection = MathUtils.lerp(moveDirection, targetDirection, 0.1f);
        position.x += velocity.x * dt;
    }

    private void updateState() {
        setState(velocity.x != 0 ? State.RUN : State.IDLE);
        if ((moveDirection < 0 && facingRight) || (moveDirection > 0 && !facingRight)) {
            facingRight = !facingRight;
        }
    }
}
