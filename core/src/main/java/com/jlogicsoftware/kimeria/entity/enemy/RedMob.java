package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

/** Port of Dart's {@code RedMob}: like {@link YellowMob} but attacks in melee range instead of just charging. */
public class RedMob extends Enemy<RedMob.State> implements CollideBody {
    public enum State {IDLE, RUN, HIT, ATTACK}

    private static final float STEP_TIME = 0.1f;
    private static final float RUN_SPEED = 80f;
    // The attack animation has 4 frames of STEP_TIME * 2 each: two wind-up frames (raised arm),
    // one strike frame (whip out) and one recovery frame. Only the strike frame is dangerous.
    private static final float ATTACK_FRAME_TIME = STEP_TIME * 2;
    private static final float WINDUP_TIME = ATTACK_FRAME_TIME * 2;
    private static final float ATTACK_TIME = ATTACK_FRAME_TIME * 4;

    private final Hitbox attackHitbox;
    private Level level;
    private float targetDirection = -1;
    private boolean isAttacking = false;
    private float attackElapsed = 0f;
    private boolean clambering, inQuickSand;
    private Escalator currentEscalator;

    public RedMob(Assets assets, GameContext game, float x, float y, float w, float h,
                  float offNeg, float offPos) {
        super(assets, game, "Mobs", x, y, w, h, offNeg, offPos, State.class);
        // The art body is x 7..25, y 5..32 of the 48x32 frame; the hitbox is a pixel larger on each
        // open side (bottom stays on the feet), so enemies are easy to hit and stomp (D10).
        hitbox = Hitbox.rect(6, 4, 20, 28);
        // What kills on touch stays inside the art, as it was originally.
        hurtbox = Hitbox.rect(10, 6, 14, 26);
        // The whip as drawn in the strike frame: from the body's front edge to its tip at x=32.
        attackHitbox = Hitbox.rect(26, 4, 6, 28);

        putAnimation(State.IDLE, spriteAnimation(4, STEP_TIME, 48, 32, 0, 32 * 5), true);
        putAnimation(State.RUN, spriteAnimation(4, STEP_TIME, 48, 32, 0, 32), true);
        putAnimation(State.HIT, spriteAnimation(4, STEP_TIME, 48, 32, 0, 32 * 4), false);
        putAnimation(State.ATTACK, spriteAnimation(4, STEP_TIME * 2, 48, 32, 0, 32 * 2), false);
        setState(State.IDLE);

        rangeNeg = x - offNeg * TILE_SIZE;
        rangePos = x + offPos * TILE_SIZE;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    @Override
    public void updateEnemy(float dt) {
        if (isAttacking) {
            attackElapsed += dt;
            if (attackElapsed >= ATTACK_TIME) {
                isAttacking = false;
                setState(State.IDLE);
            }
        } else {
            updateState();
            movement(dt);
        }
        checkHorizontalCollisions(level);
        applyGravity(dt);
        checkVerticalCollisions(level, dt);
        checkAttackCollision();
    }

    private void movement(float dt) {
        velocity.x = 0;
        if (playerInAttackRange()) {
            performAttack();
            return;
        } else if (playerInRange()) {
            targetDirection = directionToPlayer(targetDirection);
            velocity.x = targetDirection * RUN_SPEED;
        }

        moveDirection = MathUtils.lerp(moveDirection, targetDirection, 0.1f);
        position.x += velocity.x * dt;
    }

    /** The mob winds up as soon as the whip, where it will land, reaches the player. */
    private boolean playerInAttackRange() {
        return attackBounds().overlaps(player.hitboxBounds());
    }

    @Override
    public Rectangle attackBounds() {
        return boundsOf(attackHitbox);
    }

    @Override
    public boolean isAttackActive() {
        return isAttacking && attackElapsed >= WINDUP_TIME && attackElapsed < WINDUP_TIME + ATTACK_FRAME_TIME;
    }

    private void updateState() {
        setState(velocity.x != 0 ? State.RUN : State.IDLE);
        if ((moveDirection < 0 && facingRight) || (moveDirection > 0 && !facingRight)) {
            facingRight = !facingRight;
        }
    }

    private void performAttack() {
        if (isAttacking || player.isGotHit()) return;
        isAttacking = true;
        attackElapsed = 0f;
        setState(State.ATTACK);
    }

    private void checkAttackCollision() {
        if (isAttackActive() && attackBounds().overlaps(player.hitboxBounds())) {
            player.collidedWithActor(false);
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
