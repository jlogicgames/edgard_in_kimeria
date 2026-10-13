package com.jlogicsoftware.kimeria.entity.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.enemy.Bat;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.environment.Checkpoint;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.Trigger;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.input.KeyBindings;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

/** Port of Dart's {@code Player}. */
public class Player extends Actor<Player.State> implements CollideBody {
    public enum State {IDLE, RUNNING, JUMPING, FALLING, HIT, ATTACKING, APPEARING, DISAPPEARING, CLIMBING}

    /**
     * Death and respawn, Katana Zero style. DYING: the world slows to a crawl so the player sees
     * what killed them. RESETTING: the game rebuilds the room on its next frame and replaces this
     * player. ARRIVING (on the new player): the camera pans back while the sprite appears.
     */
    private enum RespawnPhase {NONE, DYING, RESETTING, ARRIVING}

    private static final float STEP_TIME = 0.1f;
    private static final float MOVE_SPEED = 100f;
    /** Real seconds the death slow motion lasts, and how slow the world runs during it. */
    private static final float DEATH_SLOWMO_SECONDS = 0.7f;
    private static final float DEATH_TIME_SCALE = 0.2f;
    /** Longest real frame the death timer will count, so a stalled window cannot skip the slow motion. */
    private static final float MAX_REAL_STEP = 0.05f;
    private static final float UP_FOLLOW = 200f;

    private final GameContext game;
    private Level level;

    public final Vector2 velocity = new Vector2();
    private boolean onGround = false;
    private boolean clambering = false;
    private boolean wallJumping = false;
    private float wallJumpTimer = 0f;
    private boolean inQuickSand = false;
    private Escalator currentEscalator;

    public float horizontalMovement = 0f;
    private boolean isJumping = false;
    private boolean isAttacking = false;
    private boolean attackHitboxAdded = false;
    private float attackElapsed = 0f;
    private final Rectangle attackHitbox = new Rectangle();
    /** The sword's reach, authored for the right-facing sprite: 17px past the body in front, 9px behind it. */
    private static final Hitbox SWORD_HITBOX = Hitbox.rect(9, 12, 37, 36);

    private boolean isGotHit = false;
    private float previousFeetY;
    private boolean isReachedCheckpoint = false;
    private RespawnPhase respawnPhase = RespawnPhase.NONE;
    private float checkpointTimer = 0f;
    private float deathTimer = 0f;
    public String collideWithTriggerId = "";

    private final Vector2 startingPosition = new Vector2();

    public Player(Assets assets, GameContext game, float x, float y) {
        super(x, y, 48, 48, State.class);
        this.game = game;
        startingPosition.set(x, y);
        viewHalfWidth = game.logicalResolution().x / 2f;
        hitbox = Hitbox.rect(18, 26, 11, 22);
        loadAnimations(assets);
        setState(State.IDLE);
    }

    private void loadAnimations(Assets assets) {
        String img = "images/hero/Player.png";
        putAnimation(State.IDLE, assets.animation(img, 4, STEP_TIME, 48, 48, 0, 48 * 9), true);
        putAnimation(State.RUNNING, assets.animation(img, 4, STEP_TIME, 48, 48, 0, 0), true);
        putAnimation(State.JUMPING, assets.animation(img, 1, STEP_TIME, 48, 48, 0, 48 * 8), true);
        putAnimation(State.FALLING, assets.animation(img, 1, STEP_TIME, 48, 48, 0, 48 * 4), true);
        putAnimation(State.HIT, assets.animation(img, 2, STEP_TIME, 48, 48, 0, 48 * 4), false);
        putAnimation(State.ATTACKING, assets.animation(img, 7, STEP_TIME, 48, 48, 0, 48 * 1), false);
        putAnimation(State.APPEARING, assets.animation(img, 4, STEP_TIME, 48, 48, 0, 48 * 3), false);
        putAnimation(State.DISAPPEARING, assets.animation(img, 4, STEP_TIME, 48, 48, 0, 48 * 6), true);
        putAnimation(State.CLIMBING, assets.animation(img, 1, STEP_TIME, 48, 48, 0, 48 * 3), true);
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    /** Called once by {@link Level} right after the player is placed at its spawn point. */
    public void spawned() {
        startingPosition.set(position);
        resetCamera();
    }

    public boolean isGotHit() {
        return isGotHit;
    }

    /** World Y of the hitbox's bottom edge at the start of this frame's physics step, before the player moved. */
    public float previousFeetY() {
        return previousFeetY;
    }

    @Override
    public void update(float dt) {
        if (respawnPhase != RespawnPhase.NONE) {
            // Bullet time and the camera chase are off while dying: the death owns both.
            // Dying and arriving take no other input, but pausing must stay possible.
            if (game.isGameStarted() && pausePressed()) game.togglePause();
            tickAnimation(dt);
            handleRespawnPhase();
            return;
        }
        updateBulletTime(dt);
        updateCameraPosition();
        if (isReachedCheckpoint) {
            tickAnimation(dt);
            checkpointTimer -= dt;
            if (checkpointTimer <= 0) {
                isReachedCheckpoint = false;
                game.loadNextLevel();
            }
            return;
        }

        readInput();

        // A fixed 1/60 accumulator here (as in the original) fights the
        // renderer's own variable frame time: even at a rock-steady 60fps
        // average, real frame durations wobble a little around 1/60, so the
        // accumulator sometimes ticks physics twice in one rendered frame
        // and zero times in the next. That's invisible in the FPS counter
        // but very visible as jittery movement/camera. Stepping physics
        // once per rendered frame with its own dt (clamped against a stall,
        // e.g. a dragged window) avoids that aliasing entirely; this game
        // has no networked/replay determinism requirement that would need
        // fixed steps.
        float stepDt = Math.min(dt, 0.05f);
        previousFeetY = position.y + hitbox.offsetY + hitbox.height;
        checkAttackCollisions(stepDt);
        updatePlayerState();
        updatePlayerMovement(stepDt);
        checkHorizontalCollisions(level);
        applyGravity(stepDt);
        checkVerticalCollisions(level, stepDt);
        checkGameplayOverlaps();

        tickAnimation(dt);
    }

    private void updateBulletTime(float dt) {
        boolean nearBat = false;
        float px = position.x + facingAwareOffsetX();
        float py = position.y + hitbox.offsetY;
        for (var obj : level.objects) {
            if (obj instanceof Bat bat) {
                float bx = bat.getX(), by = bat.getY();
                float dx = Math.max(0, Math.max(bx - (px + hitbox.width), px - (bx + bat.getWidth())));
                float dy = Math.max(0, Math.max(by - (py + hitbox.height), py - (by + bat.getHeight())));
                if (Math.sqrt(dx * dx + dy * dy) < 50) {
                    nearBat = true;
                    break;
                }
            }
        }
        if (nearBat && !game.isSlowTime()) game.setSlowTime();
        else if (!nearBat && game.isSlowTime()) game.setNormalTime();
    }

    // The camera used to pick one of two look-ahead targets from facingRight,
    // ~220px apart, so every left/right tap re-aimed it and the screen
    // jittered. Horizontally it now follows a focus point inside a dead zone
    // around the player: the focus stays put while the player moves within
    // the zone and is only pushed along once the player reaches a border.
    // Turning around changes nothing until the opposite border is reached.
    // Vertically there is no slack; the camera tracks the player's Y.
    private static final float CAMERA_CHASE_SPEED = 1400f;
    /** Respawn pan: fixed length whatever the distance, never a hard cut (D3). */
    private static final float CAMERA_PAN_SECONDS = 0.35f;
    /** Dead zone width as a multiple of the hitbox width; tune by feel. */
    private static final float DEAD_ZONE_WIDTH_FACTOR = 3f;

    /** World X the camera is centred on; only moves when the player leaves the dead zone. */
    private float cameraFocusX;
    private float viewHalfWidth;

    private void updateCameraPosition() {
        // centerX() ignores facing, unlike the hitbox, so turning in place can't nudge the zone.
        cameraFocusX = clampToZone(cameraFocusX, centerX(), hitbox.width * DEAD_ZONE_WIDTH_FACTOR);
        game.moveCameraTo(cameraTarget(cameraFocusX, position.y), CAMERA_CHASE_SPEED);
    }

    /** Re-centres the dead zone on the player and pans the camera there (level start, respawn). */
    private void resetCamera() {
        cameraFocusX = centerX();
        game.panCameraTo(cameraTarget(cameraFocusX, startingPosition.y), CAMERA_PAN_SECONDS);
    }

    private Vector2 cameraTarget(float focusX, float playerY) {
        return new Vector2(focusX - viewHalfWidth, playerY - UP_FOLLOW);
    }

    /** Returns {@code focus} moved the least distance needed to lie within {@code zoneWidth} centred on {@code playerX}. */
    static float clampToZone(float focus, float playerX, float zoneWidth) {
        float half = zoneWidth / 2f;
        return Math.max(playerX - half, Math.min(playerX + half, focus));
    }

    private boolean pausePressed() {
        return Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || game.gamepad().pausePressed();
    }

    private void readInput() {
        if (!game.isGameStarted()) return;
        var gamepad = game.gamepad();
        var touch = game.touch();

        if (pausePressed()) game.togglePause();

        var keys = KeyBindings.of(game.leftHanded());

        boolean left = keys.moveLeftHeld();
        boolean right = keys.moveRightHeld();
        float padX = gamepad.horizontal() + touch.horizontal();

        if (!isAttacking) {
            horizontalMovement = 0;
            if (left || padX < 0) horizontalMovement -= 1;
            if (right || padX > 0) horizontalMovement += 1;
        }

        isJumping = (keys.jumpHeld() || gamepad.jumpHeld() || touch.jumpHeld()) && !isAttacking;

        // The one "other" button: interacts inside a trigger zone, attacks everywhere else.
        if (keys.actionJustPressed() || gamepad.actionPressed() || touch.actionPressed()) {
            if (canInteract()) {
                interact();
            } else if (!isAttacking && onGround && !isJumping && !clambering) {
                isAttacking = true;
            }
        }

        if (keys.shootJustPressed() || gamepad.shootPressed() || touch.shootPressed()) {
            shoot();
        }
    }

    /** Whether the action button currently interacts (player is inside a {@link Trigger} zone) rather than attacks. */
    public boolean canInteract() {
        return !collideWithTriggerId.isEmpty();
    }

    private void interact() {
        for (Trigger trigger : level.triggers) {
            if (trigger.targetId.equals(collideWithTriggerId)) {
                level.activateTrigger(trigger);
            }
        }
    }

    /** Shoot button hook: the ranged attack (fireball or throwing knife) is a separate, not yet built feature. */
    private void shoot() {
    }

    private void updatePlayerState() {
        State next = State.IDLE;

        if (horizontalMovement < 0 && facingRight) {
            facingRight = false;
        } else if (horizontalMovement > 0 && !facingRight) {
            facingRight = true;
        }

        if (horizontalMovement != 0) next = State.RUNNING;
        if (velocity.y > 0) next = State.FALLING;
        if (velocity.y < 0) next = State.JUMPING;
        if (clambering) next = State.CLIMBING;
        if (isAttacking) next = State.ATTACKING;

        setState(next);
    }

    private void updatePlayerMovement(float dt) {
        if (isJumping && (onGround || clambering)) playerJump(dt);

        if (velocity.y > gravityAcceleration() * 15) onGround = false; // coyote time

        if (!wallJumping) {
            velocity.x = horizontalMovement * MOVE_SPEED;
            if (inQuickSand) velocity.x *= 0.1f;
            if (currentEscalator != null) {
                Vector2 escVel = currentEscalator.currentMoveDirection().scl(currentEscalator.moveSpeed());
                velocity.x += escVel.x;
            }
        }
        position.x += velocity.x * dt;

        if (clambering) velocity.y *= 0.1f;

        if (wallJumping) {
            wallJumpTimer -= dt;
            if (wallJumpTimer <= 0) wallJumping = false;
        }

        if (position.y > 380) die(false);
    }

    private void playerJump(float dt) {
        if (game.playSounds()) game.playSound("jump");
        if (clambering) {
            velocity.y = -jumpForce() * 0.7f;
            if (horizontalMovement < 0) velocity.x = jumpForce() * 0.5f;
            else if (horizontalMovement > 0) velocity.x = -jumpForce() * 0.5f;
            else velocity.x = facingRight ? -jumpForce() * 0.5f : jumpForce() * 0.5f;
            clambering = false;
            wallJumping = true;
            wallJumpTimer = 0.1f;
        } else {
            velocity.y = -jumpForce();
            if (inQuickSand) velocity.y *= 0.1f;
        }
        position.y += velocity.y * dt;
        onGround = false;
        isJumping = false;
    }

    /**
     * @param showCause whether to slow time so the player can see what killed them. A fall into a
     *                  pit has nothing to look at (the player is off screen), so it resets at once.
     */
    private void die(boolean showCause) {
        if (isGotHit || game.invulnerable()) return;
        if (game.playSounds()) game.playSound("hit");
        isGotHit = true;
        velocity.setZero();
        horizontalMovement = 0;
        setState(State.HIT);
        if (showCause) {
            respawnPhase = RespawnPhase.DYING;
            deathTimer = 0f;
            game.setTimeScale(DEATH_TIME_SCALE);
        } else {
            requestRoomReset();
        }
    }

    private void requestRoomReset() {
        respawnPhase = RespawnPhase.RESETTING;
        game.resetRoom();
    }

    private void handleRespawnPhase() {
        if (respawnPhase == RespawnPhase.DYING) {
            // Real time: the world is slowed, but the slow motion is a fixed length of it.
            if (game.isGameStarted()) deathTimer += Math.min(Gdx.graphics.getDeltaTime(), MAX_REAL_STEP);
            if (deathTimer >= DEATH_SLOWMO_SECONDS) requestRoomReset();
        } else if (respawnPhase == RespawnPhase.ARRIVING) {
            if (isCurrentAnimationFinished()) {
                setState(State.IDLE);
                respawnPhase = RespawnPhase.NONE;
            }
        }
    }

    /**
     * Called on the player built for a fresh room attempt, right after the level placed it: shows
     * the appear animation while the camera pans back (started by {@link #spawned()}), and takes
     * input again on the first frame after it. Held keys then act at once.
     */
    public void arriveAfterReset() {
        facingRight = true;
        respawnPhase = RespawnPhase.ARRIVING;
        setState(State.APPEARING);
    }

    /** F5 dev hotkey: reach a checkpoint without walking to one. */
    public void debugTriggerCheckpoint() {
        if (!isReachedCheckpoint && respawnPhase == RespawnPhase.NONE) {
            reachedCheckpoint();
        }
    }

    private void reachedCheckpoint() {
        isReachedCheckpoint = true;
        if (game.playSounds()) game.playSound("disappear");
        setState(State.DISAPPEARING);
        checkpointTimer = 3f;
    }

    private void checkAttackCollisions(float dt) {
        if (!isAttacking) return;

        horizontalMovement = 0;
        velocity.x = 0;

        if (!attackHitboxAdded) {
            attackHitboxAdded = true;
            attackElapsed = 0f;
        }
        attackHitbox.set(boundsOf(SWORD_HITBOX));

        attackElapsed += dt;
        var anim = animations.get(State.ATTACKING);
        if (attackElapsed >= anim.getAnimationDuration()) {
            isAttacking = false;
            attackHitboxAdded = false;
            resumeMovementAfterAttack();
            return;
        }

        for (var obj : level.objects) {
            if (obj instanceof Enemy<?> enemy) {
                if (enemy.hitboxOverlaps(attackHitbox)) {
                    enemy.collidedWithActor(true);
                    break;
                }
            }
        }
    }

    private void resumeMovementAfterAttack() {
        var keys = KeyBindings.of(game.leftHanded());
        boolean left = keys.moveLeftHeld();
        boolean right = keys.moveRightHeld();
        horizontalMovement = 0;
        if (left) horizontalMovement -= 1;
        if (right) horizontalMovement += 1;
    }

    /** Overlap checks against collectables/enemies/checkpoint/bomb/triggers -- Dart's onCollisionStart/End. */
    private void checkGameplayOverlaps() {
        if (isReachedCheckpoint) return;

        boolean touchingTrigger = false;
        for (var obj : level.objects) {
            if (obj instanceof Collectable c && overlapsEntity(c)) {
                c.collideWithPlayer();
            } else if (obj instanceof Enemy<?> enemy && !isAttacking && enemy.hitboxOverlaps(hitboxBounds())) {
                enemy.collidedWithActor(false);
            } else if (obj instanceof Checkpoint cp && overlapsEntity(cp)) {
                reachedCheckpoint();
            } else if (obj instanceof Bomb bomb && hitboxBounds().overlaps(bomb.hitboxBounds())) {
                bomb.collideWithPlayer();
                die(true);
            } else if (obj instanceof Trigger trigger && overlapsEntity(trigger)) {
                touchingTrigger = true;
                collideWithTriggerId = trigger.targetId;
            }
        }
        if (!touchingTrigger) collideWithTriggerId = "";
    }

    private boolean overlapsEntity(com.jlogicsoftware.kimeria.entity.Entity other) {
        return com.jlogicsoftware.kimeria.physics.CollisionUtils.overlaps(this, other);
    }

    @Override
    public void collidedWithActor(boolean gotHit) {
        die(true);
    }

    // -- CollideBody / GravityBody plumbing --

    @Override
    public Vector2 position() {
        return position;
    }

    @Override
    public Vector2 velocity() {
        return velocity;
    }

    @Override
    public boolean isOnGround() {
        return onGround;
    }

    @Override
    public void setOnGround(boolean value) {
        onGround = value;
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
