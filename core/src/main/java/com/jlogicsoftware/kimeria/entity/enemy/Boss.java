package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.effects.BossFeedback;
import com.jlogicsoftware.kimeria.effects.BloodBurst;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

/** Forest boss. Damage is resolved by Level AFTER the player's sword and movement. */
public final class Boss extends Enemy<Boss.State> implements CollideBody {
    public enum State { THRONE, WALK, IDLE, NORMAL, HURT, WARNING, COUNTER, COUNTER_RECOVERY, RECOVERY, DEAD }
    public static final int MAX_ENERGY = 12;
    public static final float SPEED = 65f, WAKE_DISTANCE = 160f;
    public static final float ATTACK_SPEED = 1.5f * 1.5f;
    public static final float NORMAL_FRAME = .35f / ATTACK_SPEED, NORMAL_DURATION = NORMAL_FRAME * 4;
    public static final float HURT_DURATION = .2f, WARNING_DURATION = 1.15f;
    public static final float COUNTER_DURATION = .3f / ATTACK_SPEED, RECOVERY_DURATION = .3f, DEATH_DURATION = .8f;
    private static final Hitbox NORMAL_BOX = Hitbox.rect(24, 22, 16, 8);
    private static final Hitbox COUNTER_BOX = Hitbox.rect(24, 12, 16, 15);
    private final Level level;
    private int energy = MAX_ENERGY;
    private final BossFeedback feedback = new BossFeedback();
    private float elapsed;
    private boolean awakened, completedStrike, abandoned;
    private boolean clambering, quickSand;
    private Escalator escalator;

    public Boss(Assets assets, GameContext game, Level level, float x, float y) {
        super(assets, game, "Boss", x, y, 48, 32, 0, 0, State.class);
        this.level = level;
        hitbox = Hitbox.rect(7, 4, 18, 28);
        putAnimation(State.THRONE, spriteAnimation(4, .2f, 48, 32, 0, 224), true);
        putAnimation(State.WALK, spriteAnimation(4, .1f, 48, 32, 0, 32), true);
        putAnimation(State.IDLE, spriteAnimation(4, .1f, 48, 32, 0, 160), true);
        putAnimation(State.NORMAL, spriteAnimation(4, NORMAL_FRAME, 48, 32, 0, 192), false);
        putAnimation(State.HURT, spriteAnimation(4, .05f, 48, 32, 0, 96), false);
        // Show the WHOLE third sheet row (one-based): its two distinct preparation poses during
        // the warning, then both sweep poses and the extended-axe recovery. No idle loop.
        putAnimation(State.WARNING, spriteAnimation(2, WARNING_DURATION / 2, 48, 32, 0, 64), false);
        putAnimation(State.COUNTER, assets.animation("images/enemy/Boss.png", 2, COUNTER_DURATION / 2, 48, 32, 96, 64, 2), false);
        putAnimation(State.COUNTER_RECOVERY, assets.animation("images/enemy/Boss.png", 1, RECOVERY_DURATION, 48, 32, 144, 64, 1), false);
        putAnimation(State.RECOVERY, spriteAnimation(4, .1f, 48, 32, 0, 160), true);
        putAnimation(State.DEAD, spriteAnimation(4, .2f, 48, 32, 0, 128), false);
        facingRight = false;
        change(State.THRONE);
    }

    public int energy() { return energy; }
    public BossFeedback feedback() { return feedback; }
    public void updatePresentation(float realDt) { feedback.update(realDt); }

    /** A retry already has full real energy; only its display animates during the existing arrival. */
    public void showRestoration(int previousEnergy) {
        feedback.restored(previousEnergy, MAX_ENERGY);
        if (game.playSounds()) game.playSound("boss_restore");
    }
    public boolean isDefeated() { return energy == 0; }
    public boolean deathFinished() { return isDefeated() && elapsed >= DEATH_DURATION; }
    public boolean awakened() { return awakened; }

    private void change(State state) {
        setState(state);
        elapsed = stateTime = 0;
        velocity.x = 0;
    }

    /** Death/transition abandons the attack without emitting damage or a completion event. */
    public void abandonAttack() {
        abandoned = true;
        completedStrike = false;
        velocity.setZero();
    }

    @Override public void update(float dt) {
        if (!game.isGameStarted() || abandoned || isRemoved()) return;
        dt = Math.min(dt, .05f);
        elapsed += dt;
        if (isDefeated()) {
            stateTime = elapsed;
            if (deathFinished()) removeFromParent();
            return;
        }
        if (!player.canFight()) { if (player.isGotHit()) abandonAttack(); return; }
        switch (current) {
            case THRONE -> {
                Rectangle me = hitboxBounds(), p = player.hitboxBounds();
                if (Math.abs(p.x + p.width / 2 - me.x - me.width / 2) <= WAKE_DISTANCE
                    && p.y + p.height > me.y && p.y < me.y + me.height) {
                    awakened = true;
                    change(State.WALK);
                }
            }
            case WALK, IDLE -> {
                facingRight = directionToPlayer(facingRight ? 1 : -1) > 0;
                if (attackBounds().overlaps(player.hitboxBounds())) change(State.NORMAL);
                else {
                    setState(State.WALK);
                    velocity.x = (facingRight ? 1 : -1) * SPEED;
                    position.x += velocity.x * dt;
                }
            }
            case NORMAL -> {
                if (elapsed >= NORMAL_DURATION) {
                    // Commit only in resolveCombat, after this frame's sword hit can cancel it.
                    completedStrike = true;
                }
            }
            case HURT -> { if (elapsed >= HURT_DURATION) change(State.WARNING); }
            case WARNING -> { if (elapsed >= WARNING_DURATION) change(State.COUNTER); }
            case COUNTER -> { if (elapsed >= COUNTER_DURATION) completedStrike = true; }
            case RECOVERY, COUNTER_RECOVERY -> { if (elapsed >= RECOVERY_DURATION) change(State.WALK); }
            default -> { }
        }
        checkHorizontalCollisions(level);
        applyGravity(dt);
        checkVerticalCollisions(level, dt);
        Rectangle room = level.currentRoomBounds();
        position.x = Math.max(room.x - hitbox.offsetX,
            Math.min(room.x + room.width - hitbox.offsetX - hitbox.width, position.x));
        if (current == State.WALK) tickAnimation(dt);
        else stateTime = elapsed;
    }

    @Override public void updateEnemy(float dt) { update(dt); }

    /** Called once per player swing by Player, never once per overlapping frame. */
    @Override public void collidedWithActor(boolean weapon) {
        if (isDefeated() || abandoned || !player.canFight()) return;
        if (weapon) {
            int before = energy;
            energy = Math.max(0, energy - (int) game.artifacts().bossWeaponDamageMultiplier(level.name));
            feedback.damaged(before, energy);
            Rectangle body = hitboxBounds();
            float direction = player.hitboxBounds().x + player.hitbox.width / 2 < body.x + body.width / 2 ? 1 : -1;
            level.queueSpawn(new BloodBurst(direction > 0 ? body.x : body.x + body.width,
                body.y + body.height * .45f, direction, body.y + body.height));
            if (game.playSounds()) game.playSound("boss_hit");
            if (energy == 0) {
                completedStrike = false;
                change(State.DEAD);
                level.bossDefeated();
            } else if (!isCounterSequence()) {
                awakened = true;
                completedStrike = false;
                change(State.HURT);
                if (game.playSounds()) game.playSound("bounce");
            }
            return;
        }
        resolveSolidContact();
    }

    /** Living body contact blocks movement but never deals damage. Axe strikes remain lethal. */
    private void resolveSolidContact() {
        Rectangle body = hitboxBounds(), hero = player.hitboxBounds();
        if (!body.overlaps(hero)) return;
        if (isStomp(player.velocity.y, player.previousFeetY(), body.y)) {
            player.position.y = body.y - player.hitbox.offsetY - player.hitbox.height;
            player.velocity.y = -260f;
            player.setOnGround(false);
            if (game.playSounds()) game.playSound("bounce");
        } else if (player.velocity.y < 0 && player.previousFeetY() - hero.height >= body.y + body.height - 2f) {
            // Jumping into the underside cannot pass through the boss either.
            player.position.y = body.y + body.height - player.hitbox.offsetY;
            player.velocity.y = 0;
        } else {
            boolean fromLeft = player.previousBodyX() + hero.width / 2 < body.x + body.width / 2;
            player.position.x = (fromLeft ? body.x - hero.width : body.x + body.width) - player.hitbox.offsetX;
            float incoming = player.velocity.x;
            // Respect terrain if the moving boss has crowded the hero against a wall.
            player.velocity.x = fromLeft ? -1 : 1;
            player.checkHorizontalCollisions(level);
            player.velocity.x = fromLeft ? Math.min(0, incoming) : Math.max(0, incoming);
            hero = player.hitboxBounds();
            if (body.overlaps(hero)) {
                position.x = (fromLeft ? hero.x + hero.width : hero.x - body.width) - hitbox.offsetX;
                velocity.x = 0;
            }
        }
    }

    public void resolveCombat() {
        if (!game.isGameStarted()) return;
        if (isDefeated() || abandoned || !player.canFight()) { completedStrike = false; return; }
        if (isAttackActive() && attackBounds().overlaps(player.hitboxBounds())) player.collidedWithActor(false);
        if (!player.canFight()) { abandonAttack(); return; }
        if (completedStrike) {
            completedStrike = false;
            level.shakeAfterBossAttack();
            change(current == State.COUNTER ? State.COUNTER_RECOVERY : State.RECOVERY);
        }
    }

    /** Only the warned retaliation, including its recovery, resists interruption. */
    public boolean isCounterSequence() {
        return current == State.HURT || current == State.WARNING || current == State.COUNTER
            || current == State.COUNTER_RECOVERY;
    }

    @Override public Rectangle attackBounds() {
        return boundsOf(isCounterSequence() ? COUNTER_BOX : NORMAL_BOX);
    }
    @Override public boolean isAttackActive() {
        return !abandoned && !isDefeated() && ((current == State.NORMAL && elapsed >= NORMAL_FRAME * 2
            && elapsed < NORMAL_FRAME * 3) || (current == State.COUNTER && elapsed < COUNTER_DURATION));
    }
    @Override protected void setDefeated() { change(State.DEAD); }

    @Override public void render(Batch batch) {
        if (isRemoved()) return;
        Color before = new Color(batch.getColor());
        if (isCounterSequence()) {
            float pulse = current == State.HURT || current == State.WARNING
                ? (((int) (elapsed / .12f) % 2 == 0) ? .35f : .75f) : .65f;
            batch.setColor(1f, pulse, .15f, before.a);
        }
        super.render(batch);
        batch.setColor(before);
    }

    @Override public Hitbox hitbox() { return hitbox; }
    @Override public boolean isClambering() { return clambering; }
    @Override public void setClambering(boolean value) { clambering = value; }
    @Override public boolean isInQuickSand() { return quickSand; }
    @Override public void setInQuickSand(boolean value) { quickSand = value; }
    @Override public Escalator currentEscalator() { return escalator; }
    @Override public void setCurrentEscalator(Escalator value) { escalator = value; }
}
