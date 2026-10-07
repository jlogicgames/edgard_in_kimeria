package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.math.MathUtils;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

/** Port of Dart's {@code YellowMob}: a ground patrol enemy that charges the player when in range. */
public class YellowMob extends Enemy<YellowMob.State> implements CollideBody {
    public enum State {IDLE, RUN, HIT}

    private static final float STEP_TIME = 0.05f;
    private static final float RUN_SPEED = 80f;

    private float targetDirection = -1;
    private boolean clambering, wallJumping, inQuickSand;
    private Escalator currentEscalator;
    private Level level;

    public YellowMob(Assets assets, GameContext game, float x, float y, float w, float h,
                      float offNeg, float offPos) {
        super(assets, game, "yellow_mob", x, y, w, h, offNeg, offPos, State.class);
        // Art body is x 7..25, y 5..32; the hitbox is a pixel larger on each open side (D10).
        hitbox = Hitbox.rect(6, 4, 20, 28);
        hurtbox = Hitbox.rect(10, 6, 14, 26); // what kills on touch stays inside the art

        putAnimation(State.IDLE, spriteAnimation(4, STEP_TIME, 48, 32, 0, 32 * 5), true);
        putAnimation(State.RUN, spriteAnimation(4, STEP_TIME, 48, 32, 0, 32), true);
        putAnimation(State.HIT, spriteAnimation(4, STEP_TIME, 48, 32, 0, 32 * 4), false);
        setState(State.IDLE);

        rangeNeg = x - offNeg * TILE_SIZE;
        rangePos = x + offPos * TILE_SIZE;
    }

    public void setLevel(Level level) {
        this.level = level;
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

    @Override
    protected void setDefeated() {
        setState(State.HIT);
    }

    @Override
    public Hitbox hitbox() {
        return hitbox;
    }

    @Override
    public boolean isClambering() {
        return clambering;
    }

    @Override
    public void setClambering(boolean value) {
        clambering = value;
    }

    @Override
    public boolean isInQuickSand() {
        return inQuickSand;
    }

    @Override
    public void setInQuickSand(boolean value) {
        inQuickSand = value;
    }

    @Override
    public Escalator currentEscalator() {
        return currentEscalator;
    }

    @Override
    public void setCurrentEscalator(Escalator escalator) {
        currentEscalator = escalator;
    }
}
