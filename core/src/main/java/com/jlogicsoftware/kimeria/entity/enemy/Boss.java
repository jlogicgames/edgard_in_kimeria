package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.effects.BossFeedback;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

/** Forest boss. Damage is resolved by Level AFTER the player's sword and movement. */
public final class Boss extends Enemy<Boss.State> implements CollideBody {
    public enum State { THRONE, WALK, IDLE, NORMAL, HURT, WARNING, COUNTER, COUNTER_RECOVERY, RECOVERY, DEAD, ROCKS, ROOTS, BLOW }
    public static final int MAX_ENERGY = 12;
    private static final int FIREBALL_DAMAGE = 2;
    public static final float SPEED = 65f, WAKE_DISTANCE = 160f;
    public static final float ATTACK_SPEED = 1.5f * 1.5f;
    /** Blue channel of the orange tint drawn over the counter sequence (red stays 1, green pulses). */
    private static final float COUNTER_TINT_BLUE = .15f;
    public static final float NORMAL_FRAME = .35f / ATTACK_SPEED, NORMAL_DURATION = NORMAL_FRAME * 4;
    public static final float HURT_DURATION = .2f, WARNING_DURATION = 1.15f;
    public static final float COUNTER_DURATION = .3f / ATTACK_SPEED, RECOVERY_DURATION = .3f, DEATH_DURATION = .8f;
    /** A special attack is ready this long after the boss wakes and after the last special ended (#625, D157). */
    public static final float SPECIAL_COOLDOWN = 3f;
    /** The ready special by the hero's place: a blow at most this far (body centres), rocks beyond {@link #ROCKS_DISTANCE}. */
    public static final float BLOW_DISTANCE = 48f, ROCKS_DISTANCE = 160f;
    /** Poise of the sweep: from the crouch pose to the end of the damaging frame a landed hit does not interrupt it. */
    public static final float POISE_FROM = NORMAL_FRAME, POISE_TO = NORMAL_FRAME * 3;
    /** The hero's feet this far above the floor line, or more, is "on a platform". */
    private static final float FLOOR_SLACK = 1f;
    private static final float HALF = .5f;
    private static final float BLOOD_IMPACT_Y_FRACTION = .45f;
    private static final Hitbox NORMAL_BOX = Hitbox.rect(24, 22, 16, 8);
    private static final Hitbox COUNTER_BOX = Hitbox.rect(24, 12, 16, 15);
    /** A one-frame pose: its frame time and size, and its place on the sheet (rows 2 and 6, docs/BOSS.md). */
    private static final float POSE_SECONDS = 1f, POSE_WIDTH = 48, POSE_HEIGHT = 32;
    private static final float ROCKS_POSE_X = 0, ROCKS_POSE_Y = 64, ROOTS_POSE_X = 144, ROOTS_POSE_Y = 192,
        BLOW_POSE_X = 48, BLOW_POSE_Y = 192;
    private static final String NORMAL_SOUND = "boss_sweep", COUNTER_SOUND = "boss_sweep_counter";
    private final Level level;
    private int energy = MAX_ENERGY;
    private final BossFeedback feedback = new BossFeedback();
    private float elapsed;
    private boolean awakened, completedStrike, abandoned, strikeHeard;
    private boolean quickSand;
    private final BossAttacks attacks;
    /** Fight seconds since the boss woke or the last special ended. */
    private float specialClock;
    /** The one hit a poise window lets through has landed on this attack. */
    private boolean poiseSpent;
    private Escalator escalator;

    /** @param arena where the special attacks may reach ({@link Level#bossArena}) */
    public Boss(Assets assets, GameContext game, Level level, float x, float y, BossArena arena) {
        super(assets, game, "Boss", x, y, 48, 32, 0, 0, State.class);
        this.level = level;
        attacks = arena != null ? new BossAttacks(assets, game, level, arena) : null;
        // The body is drawn within x 8..24, y 5..32 of every live frame (boss.grid), a pixel inside this box (D10); the axe
        // fills NORMAL_BOX on the sweep and COUNTER_BOX on both counter frames (tools/test_enemy_sprites.py).
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
        // The special attacks hold one existing pose each (#625): the axe over the horns (untinted, unlike the counter), the
        // axe in the ground, the low crouch.
        putAnimation(State.ROCKS, pose(ROCKS_POSE_X, ROCKS_POSE_Y), false);
        putAnimation(State.ROOTS, pose(ROOTS_POSE_X, ROOTS_POSE_Y), false);
        putAnimation(State.BLOW, pose(BLOW_POSE_X, BLOW_POSE_Y), false);
        facingRight = false;
        change(State.THRONE);
    }

    private com.badlogic.gdx.graphics.g2d.Animation<com.badlogic.gdx.graphics.g2d.TextureRegion> pose(float x, float y) {
        return assets.animation("images/enemy/Boss.png", 1, POSE_SECONDS, POSE_WIDTH, POSE_HEIGHT, x, y, 1);
    }

    /** One cue per sweep, at the start of its active frame, hit or miss (D93, #447); never after an abandon. */
    private void sweepSound(String name) {
        if (strikeHeard) return;
        strikeHeard = true;
        if (game.playSounds()) game.playSound(name);
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
    /** The special attacks, for the room's load check and tests; null for a boss built without an arena. */
    public BossAttacks attacks() { return attacks; }
    public boolean specialReady() { return attacks != null && specialClock >= SPECIAL_COOLDOWN; }
    /** True during a special attack, its whole wind-up included. */
    public boolean isSpecial() { return current == State.ROCKS || current == State.ROOTS || current == State.BLOW; }
    /** True while a landed hit takes its energy but cannot interrupt: a special attack, or the committed part of the sweep. */
    public boolean isPoised() { return isSpecial() || (current == State.NORMAL && elapsed >= POISE_FROM && elapsed < POISE_TO); }
    /** True while a hit changes nothing: the orange sequence, and a poised attack that has already taken its one hit. */
    public boolean rejectsHits() { return isCounterSequence() || (isPoised() && poiseSpent); }

    private void change(State state) {
        setState(state);
        elapsed = stateTime = 0;
        strikeHeard = poiseSpent = false;
        velocity.x = 0;
    }

    /**
     * Starts the ready special chosen by where the hero stands (D7, no chance): rocks when on a platform or far, the blow
     * when close, else the roots. False, the boss fighting on and the special staying ready, while the hero is in the air or
     * the chosen attack cannot be placed off the protected spans.
     */
    private boolean startSpecial() {
        if (!specialReady() || !player.isOnGround()) return false;
        Rectangle me = hitboxBoundsInto(bodyScratch), hero = player.hitboxBoundsInto(otherScratch);
        float heroX = hero.x + hero.width * HALF, bossX = me.x + me.width * HALF, distance = Math.abs(heroX - bossX);
        boolean onPlatform = hero.y + hero.height < attacks.arena().floor() - FLOOR_SLACK;
        State state = onPlatform || distance > ROCKS_DISTANCE ? State.ROCKS : distance <= BLOW_DISTANCE ? State.BLOW : State.ROOTS;
        BossAttacks.Kind kind = state == State.ROCKS ? BossAttacks.Kind.ROCKS
            : state == State.BLOW ? BossAttacks.Kind.BLOW : BossAttacks.Kind.ROOTS;
        if (!attacks.start(kind, heroX, bossX)) return false;
        change(state);
        return true;
    }

    private void updateAttacks(float dt) {
        if (attacks == null) return;
        Rectangle me = hitboxBoundsInto(bodyScratch), hero = player.hitboxBoundsInto(otherScratch);
        attacks.update(dt, hero.x + hero.width * HALF, me.x + me.width * HALF, me.y + me.height * HALF);
    }

    /** True once a death or transition abandoned this boss: it takes no more hits ({@link #hitByFireball} returns at once). */
    public boolean isAbandoned() { return abandoned; }

    /** Death/transition abandons the attack without emitting damage or a completion event. */
    public void abandonAttack() {
        abandoned = true;
        completedStrike = false;
        velocity.setZero();
        if (attacks != null) attacks.settle();
    }

    @Override public boolean touchesDuringSwing() { return true; }

    /** After the hero's death: the attack under way finishes, harmless, since resolveCombat is closed to an abandoned boss. */
    private void playOutAttacks(float dt) {
        updateAttacks(dt);
        if (attacks != null && attacks.blowDue()) {
            Rectangle me = hitboxBoundsInto(bodyScratch);
            attacks.detonate(me.x + me.width * HALF, player.hitboxBoundsInto(otherScratch));
        }
    }

    @Override public void update(float dt) {
        if (!game.isGameStarted() || isRemoved()) return;
        dt = Math.min(dt, .05f);
        if (abandoned) {
            playOutAttacks(dt);
            return;
        }
        elapsed += dt;
        if (isDefeated()) {
            stateTime = elapsed;
            if (deathFinished()) removeFromParent();
            return;
        }
        if (!player.canFight()) { if (player.isGotHit()) abandonAttack(); return; }
        if (awakened && !isSpecial()) specialClock += dt;
        updateAttacks(dt);
        switch (current) {
            case THRONE -> {
                Rectangle me = hitboxBoundsInto(bodyScratch), p = player.hitboxBoundsInto(otherScratch);
                if (Math.abs(p.x + p.width / 2 - me.x - me.width / 2) <= WAKE_DISTANCE
                    && p.y + p.height > me.y && p.y < me.y + me.height) {
                    awakened = true;
                    specialClock = 0;
                    change(State.WALK);
                }
            }
            case WALK, IDLE -> {
                facingRight = directionToPlayer(facingRight ? 1 : -1) > 0;
                if (startSpecial()) break;
                if (attackTouchesPlayer()) change(State.NORMAL);
                else {
                    setState(State.WALK);
                    velocity.x = (facingRight ? 1 : -1) * SPEED;
                    position.x += velocity.x * dt;
                }
            }
            case NORMAL -> {
                if (elapsed >= NORMAL_FRAME * 2) sweepSound(NORMAL_SOUND);
                if (elapsed >= NORMAL_DURATION) {
                    // Commit only in resolveCombat, after this frame's sword hit can cancel it.
                    completedStrike = true;
                }
            }
            case HURT -> { if (elapsed >= HURT_DURATION) change(State.WARNING); }
            case WARNING -> { if (elapsed >= WARNING_DURATION) change(State.COUNTER); }
            case COUNTER -> { sweepSound(COUNTER_SOUND); if (elapsed >= COUNTER_DURATION) completedStrike = true; }
            case RECOVERY, COUNTER_RECOVERY -> { if (elapsed >= RECOVERY_DURATION) change(State.WALK); }
            case ROCKS, ROOTS, BLOW -> {
                if (!attacks.holdsBoss()) {
                    specialClock = 0;
                    change(State.RECOVERY);
                }
            }
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
            boolean empowered = game.artifacts().bossWeaponDamageMultiplier(level.name) > 1;
            int damage = (int) game.artifacts().bossWeaponDamageMultiplier(level.name);
            resolveLandedHit(damage, empowered, true);
            return;
        }
        resolveSolidContact();
    }

    /**
     * A landed magical fireball always removes two energy, regardless of the sword elixir. It sprays no blood: blood comes
     * only from the sword; the fireball's own burst ({@code FireballFx.heavyHit}) is its impact.
     */
    public void hitByFireball() {
        if (isDefeated() || abandoned || !player.canFight()) return;
        resolveLandedHit(FIREBALL_DAMAGE, false, false);
    }

    /** Shared landed-hit feedback and state handling; damage, empowered visuals and blood stay source-specific. */
    private void resolveLandedHit(int damage, boolean empowered, boolean bleeds) {
        // The orange retaliation sequence is a single protected interval. Keep this before
        // energy, feedback, death, and shake mutations so every damage source shares the rule.
        // A poised attack lets one hit through (#625, D157); later ones are rejected like hits on the orange boss.
        if (rejectsHits()) return;
        boolean poised = isPoised();
        if (poised) poiseSpent = true;
        int before = energy;
        energy = Math.max(0, energy - damage);
        feedback.damaged(before, energy);
        if (bleeds) {
            Rectangle body = hitboxBounds();
            float direction = player.hitboxBounds().x + player.hitbox.width / 2 < body.x + body.width / 2 ? 1 : -1;
            level.blood(direction > 0 ? body.x : body.x + body.width,
                body.y + body.height * BLOOD_IMPACT_Y_FRACTION, direction, body.y + body.height, empowered);
        }
        if (game.playSounds()) game.playSound("boss_hit");
        if (energy == 0) {
            completedStrike = false;
            if (attacks != null) attacks.clear();
            change(State.DEAD);
            level.bossDefeated();
        } else if (poised) {
            awakened = true; // the attack goes on: no recoil, no counter, its pending strike stands
        } else {
            awakened = true;
            completedStrike = false;
            change(State.HURT);
            if (game.playSounds()) game.playSound("bounce");
        }
        if (empowered) level.shakeAfterEmpoweredHit();
    }

    /** Living body contact blocks movement but never deals damage. Axe strikes remain lethal. */
    private void resolveSolidContact() {
        Rectangle body = hitboxBoundsInto(bodyScratch), hero = player.hitboxBoundsInto(otherScratch);
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
            hero = player.hitboxBoundsInto(otherScratch);
            if (body.overlaps(hero)) {
                position.x = (fromLeft ? hero.x + hero.width : hero.x - body.width) - hitbox.offsetX;
                velocity.x = 0;
            }
        }
    }

    public void resolveCombat() {
        if (!game.isGameStarted()) return;
        if (isDefeated() || abandoned || !player.canFight()) { completedStrike = false; return; }
        if (isAttackActive() && attackTouchesPlayer()) player.collidedWithActor(false);
        if (player.canFight()) resolveSpecial();
        if (!player.canFight()) { abandonAttack(); return; }
        if (completedStrike) {
            completedStrike = false;
            level.shakeAfterBossAttack();
            change(current == State.COUNTER ? State.COUNTER_RECOVERY : State.RECOVERY);
        }
    }

    /** The special attacks' harm, after the frame's sword hit, so a lethal one wins. A blow that misses shakes the screen. */
    private void resolveSpecial() {
        if (attacks == null) return;
        Rectangle hero = player.hitboxBoundsInto(otherScratch);
        if (attacks.strikes(hero)) player.collidedWithActor(false);
        else if (attacks.blowDue()) {
            Rectangle me = hitboxBoundsInto(bodyScratch);
            if (attacks.detonate(me.x + me.width * HALF, hero)) player.collidedWithActor(false);
            if (player.canFight()) level.shakeAfterBossAttack();
        }
    }

    /** The orange retaliation interval, including recovery, is invulnerable. */
    public boolean isCounterSequence() {
        return current == State.HURT || current == State.WARNING || current == State.COUNTER
            || current == State.COUNTER_RECOVERY;
    }

    @Override public Rectangle attackBounds() {
        return boundsOf(isCounterSequence() ? COUNTER_BOX : NORMAL_BOX);
    }
    @Override protected Rectangle attackBoundsInto(Rectangle out) {
        return boundsInto(isCounterSequence() ? COUNTER_BOX : NORMAL_BOX, out);
    }
    @Override public boolean isAttackActive() {
        return !abandoned && !isDefeated() && ((current == State.NORMAL && elapsed >= NORMAL_FRAME * 2
            && elapsed < NORMAL_FRAME * 3) || (current == State.COUNTER && elapsed < COUNTER_DURATION));
    }
    @Override protected void setDefeated() { change(State.DEAD); }

    @Override public void render(Batch batch) {
        if (isRemoved()) return;
        if (attacks != null) attacks.renderUnder(batch);
        Color tint = batch.getColor();
        float beforeR = tint.r, beforeG = tint.g, beforeB = tint.b, beforeA = tint.a;
        if (isCounterSequence()) {
            float pulse = current == State.HURT || current == State.WARNING
                ? (((int) (elapsed / .12f) % 2 == 0) ? .35f : .75f) : .65f;
            batch.setColor(1f, pulse, COUNTER_TINT_BLUE, beforeA);
        }
        super.render(batch);
        batch.setColor(beforeR, beforeG, beforeB, beforeA);
        if (attacks != null) attacks.renderOver(batch);
    }

    @Override public Hitbox hitbox() { return hitbox; }
    @Override public boolean isInQuickSand() { return quickSand; }
    @Override public void setInQuickSand(boolean value) { quickSand = value; }
    @Override public Escalator currentEscalator() { return escalator; }
    @Override public void setCurrentEscalator(Escalator value) { escalator = value; }
}
