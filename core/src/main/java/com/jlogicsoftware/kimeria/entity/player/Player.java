package com.jlogicsoftware.kimeria.entity.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;

import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.environment.Checkpoint;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.Trigger;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.input.KeyBindings;
import com.jlogicsoftware.kimeria.input.GameplayInput;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.items.FireballRefill;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.jlogicsoftware.kimeria.ui.InteractionHighlight;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

import java.util.HashSet;
import java.util.Set;

/** Port of Dart's {@code Player}. */
public class Player extends Actor<Player.State> implements CollideBody {
    public enum State {IDLE, LOOK_UP, LOOK_DOWN, RUNNING, JUMPING, FALLING, HIT, ATTACKING, APPEARING, DISAPPEARING, CLIMBING, LADDER}

    /**
     * Death and respawn, Katana Zero style. DYING: the world slows to a crawl so the player sees
     * what killed them. RESETTING: the game rebuilds the room on its next frame and replaces this
     * player. ARRIVING (on the new player): the camera pans back while the sprite appears.
     */
    private enum RespawnPhase {NONE, DYING, RESETTING, ARRIVING}

    private static final int FRAME_SIZE = SpriteSheets.PLAYER.width();
    private static final float PICKUP_REACH = 3, PLACEMENT_GAP = 2;
    private static final float FIREBALL_SPAWN_OFFSET_Y = 3;
    private static final float HINT_PLAYER_OFFSET_Y = 12, HINT_TARGET_OFFSET_Y = 24;
    private static final float CAMERA_TARGET_TOLERANCE = .001f;
    private static final float MAX_PHYSICS_FRAME_SECONDS = .05f;
    private record Clip(State state, int frames, int row, boolean loop) { }
    private static final Clip[] CLIPS = {
        new Clip(State.IDLE, 4, 9, true), new Clip(State.RUNNING, 4, 0, true),
        new Clip(State.JUMPING, 1, 8, true), new Clip(State.FALLING, 1, 4, true),
        new Clip(State.HIT, 2, 4, false), new Clip(State.ATTACKING, 7, 1, false),
        new Clip(State.APPEARING, 4, 3, false), new Clip(State.DISAPPEARING, 4, 6, true),
        new Clip(State.CLIMBING, 1, 3, true), new Clip(State.LADDER, 4, 8, true)
    };

    private static final float STEP_TIME = 0.1f;
    private static final float MOVE_SPEED = 100f;
    /** Real seconds the death slow motion lasts, and how slow the world runs during it. */
    private static final float DEATH_SLOWMO_SECONDS = 0.7f;
    private static final float DEATH_TIME_SCALE = 0.2f;
    /** Longest real frame the death timer will count, so a stalled window cannot skip the slow motion. */
    private static final float MAX_REAL_STEP = 0.05f;
    private static final float UP_FOLLOW = 200f;
    /** Ladder tuning: climb speed, how much of a jump a ladder leaves, and how long a release ignores the ladder. */
    private static final float LADDER_CLIMB_SPEED = 70f;
    private static final float LADDER_JUMP_SPEED_FACTOR = 0.7f;
    private static final float LADDER_REGRAB_SECONDS = 0.25f;
    /** Feet within this distance of a ladder's top surface count as standing on it (descent entry). */
    private static final float LADDER_TOP_REACH = 2f;
    /** The first grounded intermediate platform is slightly above half-height in the authored room. */
    private static final float LADDER_BIDIRECTIONAL_ENTRY_FRACTION = 1f / 3f;
    private record LadderEntry(Ladder ladder, boolean canClimbUp, boolean canClimbDown) { }
    private record InteractionTarget(Carryable carryable, Trigger mechanism) { }

    private final GameContext game;
    private Level level;

    public final Vector2 velocity = new Vector2();
    private boolean onGround = false;
    private boolean clambering = false;
    private boolean wallJumping = false;
    private float wallJumpTimer = 0f;
    private boolean inQuickSand = false;
    private Escalator currentEscalator;
    private Ladder ladder;
    private float ladderRegrabTimer = 0f;
    /** Up/Down that joined an Action press belongs to that action until released: it cannot grab or climb a ladder. */
    private boolean verticalSpentOnAction;

    public float horizontalMovement = 0f;
    private final JumpBuffer jumpBuffer = new JumpBuffer();
    private final GameplayInput gameplayInput = new GameplayInput();
    private final LookAround look = new LookAround();
    private boolean lookLayout;
    private Carryable carried;
    private final Vector2 carryHintAnchor = new Vector2();
    private final Vector2 carryHintCameraAnchor = new Vector2();
    private boolean carryHintVisible;
    private final Set<Boss> bossesHit = new HashSet<>();
    private boolean victoryWalk;
    private boolean isAttacking = false;
    private boolean attackHitboxAdded = false;
    private float attackElapsed = 0f;
    private final Rectangle attackHitbox = new Rectangle();
    /** The sword's reach, authored for the right-facing sprite: 17px past the body in front, 9px behind it. */
    private static final Hitbox SWORD_HITBOX = Hitbox.rect(9, 12, 37, 36);

    private boolean isGotHit = false;
    private float previousFeetY;
    private float previousBodyX;
    private boolean isReachedCheckpoint = false;
    private RespawnPhase respawnPhase = RespawnPhase.NONE;
    private float checkpointTimer = 0f;
    private float deathTimer = 0f;
    public String collideWithTriggerId = "";

    private final Vector2 startingPosition = new Vector2();

    public Player(Assets assets, GameContext game, float x, float y) {
        super(x, y, FRAME_SIZE, FRAME_SIZE, State.class);
        this.game = game;
        startingPosition.set(x, y);
        viewHalfWidth = game.logicalResolution().x / 2f;
        hitbox = Hitbox.rect(18, 26, 11, 22);
        loadAnimations(assets);
        setState(State.IDLE);
    }

    private void loadAnimations(Assets assets) {
        for (Clip clip : CLIPS) {
            putAnimation(clip.state(), assets.animation(SpriteSheets.PLAYER.path(), clip.frames(), STEP_TIME,
                FRAME_SIZE, FRAME_SIZE, 0, FRAME_SIZE * clip.row()), clip.loop());
        }
        putAnimation(State.LOOK_UP, new com.badlogic.gdx.graphics.g2d.Animation<>(STEP_TIME,
            SpriteSheets.PLAYER_LOOK.frame(assets, 0, 0)), false);
        putAnimation(State.LOOK_DOWN, new com.badlogic.gdx.graphics.g2d.Animation<>(STEP_TIME,
            SpriteSheets.PLAYER_LOOK.frame(assets, 1, 0)), false);
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    /** Called once by {@link Level} right after the player is placed at its spawn point. */
    public void spawned() {
        carried = null;
        ladder = null;
        carryHintVisible = false;
        look.clear();
        resetJumpInput();
        startingPosition.set(position);
        resetCamera();
    }

    /** A lethal overlap wins over a room exit on the same frame. */
    public boolean canLeaveRoom() {
        return respawnPhase == RespawnPhase.NONE && !isReachedCheckpoint;
    }

    /** Camera relocation happens under the room transition's fully black frame. */
    public Vector2 roomCameraTarget() { return cameraTarget(centerX(), position.y); }

    public boolean canFight() { return respawnPhase == RespawnPhase.NONE && !isReachedCheckpoint && !victoryWalk; }

    public void beginVictoryWalk() {
        victoryWalk = true;
        isAttacking = attackHitboxAdded = false;
        horizontalMovement = 0;
        velocity.setZero();
        collideWithTriggerId = "";
        resetJumpInput();
        facingRight = true;
        setState(State.IDLE);
    }

    /** Scripted exit ignores terrain, hazards and camera chasing. */
    public void advanceVictoryWalk(float dt) {
        setState(State.RUNNING);
        position.x += MOVE_SPEED * dt;
        tickAnimation(dt);
    }

    public boolean victoryPausePressed() { return pausePressed(); }

    public boolean isGotHit() {
        return isGotHit;
    }

    /** Body X before this frame's movement, used to keep solid boss contact on the arrival side. */
    public float previousBodyX() { return previousBodyX; }

    /** World Y of the feet before this frame's physics step. */
    public float previousFeetY() {
        return previousFeetY;
    }

    @Override
    public void update(float dt) {
        if (!game.isGameStarted()) return;
        if (respawnPhase != RespawnPhase.NONE) {
            resetJumpInput();
            // Death owns its time scale and camera; normal movement stays suspended.
            // Dying and arriving take no other input, but pausing must stay possible.
            if (game.isGameStarted() && pausePressed()) game.togglePause();
            tickAnimation(dt);
            handleRespawnPhase();
            return;
        }
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
        if (!game.isGameStarted()) return;
        boolean wasOnLadder = ladder != null;
        if (ladder != null && horizontalMovement != 0) releaseLadder();
        updateLadderCooldown(dt);
        tryGrabLadder();

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
        if (lookLayout != game.leftHanded()) {
            look.suppress();
            lookLayout = game.leftHanded();
        }
        boolean jumpHeld = KeyBindings.of(game.leftHanded()).jumpHeld()
            || game.gamepad().jumpHeld() || game.touch().jumpHeld();
        boolean inspecting = !jumpHeld && onGround && horizontalMovement == 0 && !wallJumping && !clambering
            && ladder == null && !isAttacking && !jumpBuffer.pending() && !victoryWalk;
        // Give pickup/activation chords extra time before inspection moves their target.
        // Use real eligibility: pressing Up can hide the pickup label without changing reach.
        boolean interactionAvailable = inspecting && (selectedCarryable() != null || selectedMechanism() != null);
        boolean interactionHighlighted = interactionFeedbackTarget() != null;
        int lookDirection = inspecting && !verticalSpentOnAction && !interactionHighlighted
            ? gameplayInput.lookDirection() : 0;
        look.update(stepDt, lookDirection, gameplayInput.verticalHeld(), interactionAvailable && !interactionHighlighted);
        previousFeetY = position.y + hitbox.offsetY + hitbox.height;
        previousBodyX = position.x + hitbox.offsetX;
        checkAttackCollisions(stepDt);
        if (victoryWalk) return;
        updatePlayerState();
        if (ladder != null) climbLadder(stepDt);
        if (ladder == null) {
            updatePlayerMovement(stepDt);
            checkHorizontalCollisions(level);
            applyGravity(stepDt);
            checkVerticalCollisions(level, stepDt);
        }
        // Landing or grabbing a wall can make a buffered press usable in this very frame.
        tryJump(0f);
        if (wasOnLadder && ladder == null) updatePlayerState();
        checkGameplayOverlaps();

        updateCameraPosition();
        // The climbing pose holds still while the hero does.
        if (ladder == null || ladderDirection() != 0) tickAnimation(dt);
    }

    public boolean onLadder() { return ladder != null; }

    /** -1 up, +1 down, 0 while Action or its spent chord owns the vertical input. */
    private float ladderDirection() { return verticalSpentOnAction ? 0 : gameplayInput.verticalMovement(); }

    private void updateLadderCooldown(float dt) {
        ladderRegrabTimer = Math.max(0f, ladderRegrabTimer - dt);
        // A direction held with Action is spent on that action; releasing Action must not then climb.
        if (!gameplayInput.verticalHeld()) verticalSpentOnAction = false;
        else if (gameplayInput.verticalMovement() == 0) verticalSpentOnAction = true;
    }

    /** Which vertical directions can take hold at the player's current ladder entry point. */
    private LadderEntry ladderEntry() {
        if (level == null || ladder != null) return null;
        Rectangle body = hitboxBounds();
        float feet = body.y + body.height, bodyCenter = body.x + body.width / 2f;
        for (Ladder candidate : level.ladders) {
            if (bodyCenter < candidate.getX() || bodyCenter > candidate.getX() + candidate.getWidth()) continue;
            float top = candidate.topY(), bottom = candidate.bottomY();
            if (onGround && Math.abs(feet - top) <= LADDER_TOP_REACH)
                return new LadderEntry(candidate, false, true);
            if (onGround && Math.abs(feet - bottom) <= LADDER_TOP_REACH)
                return new LadderEntry(candidate, true, false);
            if (onGround && feet > top + LADDER_TOP_REACH && feet < bottom - LADDER_TOP_REACH)
                return new LadderEntry(candidate, true,
                    feet - top >= candidate.getHeight() * LADDER_BIDIRECTIONAL_ENTRY_FRACTION);
            if (feet > top + LADDER_TOP_REACH && body.y < bottom)
                return new LadderEntry(candidate, true, false);
        }
        return null;
    }

    /** Held Up/Down takes hold, unless an Action chord still owns that direction. */
    private void tryGrabLadder() {
        if (ladderRegrabTimer > 0 || verticalSpentOnAction || isAttacking || !canFight()) return;
        float vertical = ladderDirection();
        LadderEntry entry = ladderEntry();
        if (entry == null || vertical == 0 || (vertical < 0 && !entry.canClimbUp())
            || (vertical > 0 && !entry.canClimbDown())) return;
        Rectangle body = hitboxBounds();
        position.x += entry.ladder().centerX() - (body.x + body.width / 2f);
        ladder = entry.ladder();
        clambering = wallJumping = onGround = false;
        horizontalMovement = 0;
        velocity.setZero();
    }

    /** Moves along the ladder and steps off at either end onto whatever supports the hero there. */
    private void climbLadder(float dt) {
        tryJump(dt);
        if (ladder == null) return;
        velocity.setZero();
        float direction = ladderDirection();
        position.y += direction * LADDER_CLIMB_SPEED * dt;
        float feet = position.y + hitbox.offsetY + hitbox.height;
        if (direction < 0 && feet <= ladder.topY()) stepOffLadder(ladder.topY());
        else if (direction > 0 && feet >= ladder.bottomY()) stepOffLadder(ladder.bottomY());
    }

    private void stepOffLadder(float surfaceY) {
        position.y = surfaceY - hitbox.height - hitbox.offsetY;
        onGround = true;
        releaseLadder();
    }

    private void releaseLadder() {
        ladder = null;
        ladderRegrabTimer = LADDER_REGRAB_SECONDS;
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
        Vector2 target = cameraTarget(cameraFocusX, position.y);
        // Deliberate inspection is a vertical exception to the ordinary room clamp.
        // Current rooms are only 8px taller than the viewport; clamping the peek would erase it.
        target.y += look.offset();
        // Compare the room-clamped camera target, not distance walked or screen shake.
        // Crossing a dead-zone edge at a room boundary does not scroll the view.
        if (carried != null && carryHintVisible && !game.isCameraPanning()
            && !target.epsilonEquals(carryHintCameraAnchor, CAMERA_TARGET_TOLERANCE)) carryHintVisible = false;
        game.moveCameraTo(target, look.cameraSpeed(CAMERA_CHASE_SPEED));
    }

    /** Re-centres the dead zone on the player and pans the camera there (level start, respawn). */
    private void resetCamera() {
        cameraFocusX = centerX();
        game.panCameraTo(cameraTarget(cameraFocusX, startingPosition.y), CAMERA_PAN_SECONDS);
    }

    private Vector2 cameraTarget(float focusX, float playerY) {
        Rectangle room = level.currentRoomBounds();
        float viewWidth = viewHalfWidth * 2;
        float viewHeight = game.logicalResolution().y;
        float x = room.width <= viewWidth ? room.x + (room.width - viewWidth) / 2
            : Math.max(room.x, Math.min(room.x + room.width - viewWidth, focusX - viewHalfWidth));
        float y = room.height <= viewHeight ? room.y + (room.height - viewHeight) / 2
            : Math.max(room.y, Math.min(room.y + room.height - viewHeight, playerY - UP_FOLLOW));
        return new Vector2(x, y);
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

        if (pausePressed()) {
            game.togglePause();
            return;
        }

        var keys = KeyBindings.of(game.leftHanded());

        boolean left = keys.moveLeftHeld();
        boolean right = keys.moveRightHeld();
        float padX = gamepad.horizontal() + touch.horizontal();

        if (!isAttacking) {
            horizontalMovement = 0;
            if (left || padX < 0) horizontalMovement -= 1;
            if (right || padX > 0) horizontalMovement += 1;
        }

        jumpBuffer.update(Gdx.graphics.getDeltaTime(), keys.jumpHeld(), gamepad.jumpHeld(), touch.jumpHeld());

        gameplayInput.update(game.leftHanded(), gamepad, touch);
        if (gameplayInput.actionPressed()) {
            // Conflicting directions consume the press without attacking or interacting.
            if (gameplayInput.up() && gameplayInput.down()) { /* wait for an unambiguous press */ }
            else if (gameplayInput.down()) putDownCarried();
            else if (gameplayInput.up()) {
                InteractionTarget target = selectedInteraction();
                if (target != null && target.carryable() != null) pickUp(target.carryable());
                else if (target != null) level.activateTrigger(target.mechanism());
            } else if (!isAttacking && onGround && ladder == null && !jumpBuffer.pending() && !clambering) isAttacking = true;
        }
        if (gameplayInput.rangedPressed()) shoot();
    }

    public boolean canInteract() { return selectedMechanism() != null; }
    public Carryable carriedObject() { return carried; }

    /** Releases the room-owned tool before a secured room snapshot is captured. */
    public void releaseCarriedForRoomSecure() {
        if (carried == null) return;
        Rectangle destination = level.findSafeCarryablePlacement(Carryable.BODY_SIZE);
        if (destination == null) {
            throw new IllegalStateException("No supported carryable placement in room " + level.currentRoomName());
        }
        carried.release(destination.x, destination.y, false, facingRight);
        carried = null;
    }

    // Free/front palm, measured on the original 48x48 Player.png animation frames.
    private static final int[][] IDLE_GRIP = {{31, 34}, {31, 35}, {31, 34}, {31, 32}};
    private static final int[][] RUN_GRIP = {{29, 34}, {24, 33}, {32, 34}, {24, 33}};
    private static final int[] JUMP_GRIP = {21, 17}, FALL_GRIP = {27, 32};
    private static final int[][] ATTACK_GRIP = {{31, 34}, {22, 28}, {25, 31}, {24, 32}, {29, 32}, {28, 32}, {27, 32}};

    /** Attachment follows the authored pose and the same body pivot as Actor.render. */
    public void heldObjectGrip(Vector2 out) {
        var animation = animations.get(current);
        int frame = animation == null ? 0 : animation.getKeyFrameIndex(stateTime);
        int[] grip = switch (current) {
            case IDLE -> IDLE_GRIP[frame % IDLE_GRIP.length];
            case LOOK_UP, LOOK_DOWN -> IDLE_GRIP[0];
            case RUNNING -> RUN_GRIP[frame % RUN_GRIP.length];
            case ATTACKING -> ATTACK_GRIP[frame % ATTACK_GRIP.length];
            case JUMPING, LADDER -> JUMP_GRIP;
            case FALLING, HIT -> FALL_GRIP;
            default -> IDLE_GRIP[0];
        };
        out.set(position.x + (facingRight ? grip[0] : 2 * pivotX() - grip[0]), position.y + grip[1]);
    }

    /** Rows of empty pixels under the feet in each climbing frame (measured on Player.png), so the feet reach the body box. */
    private static final float[] LADDER_FRAME_DROP = {5, 7, 5, 6};

    @Override public void render(Batch batch) {
        if (current == State.LADDER) {
            var animation = animations.get(current);
            float drop = LADDER_FRAME_DROP[animation.getKeyFrameIndex(stateTime) % LADDER_FRAME_DROP.length];
            position.y += drop;
            super.render(batch);
            position.y -= drop;
        } else super.render(batch);
        if (carried != null) carried.renderHeld(batch);
    }
    public int ammunition() { return level.rangedStock.count(); }
    public float verticalMovement() { return gameplayInput.verticalMovement(); }

    private float targetDistance(com.jlogicsoftware.kimeria.entity.Entity target) {
        Rectangle b = hitboxBounds();
        float dx = target.centerX() - (b.x + b.width / 2), dy = target.centerY() - (b.y + b.height / 2);
        return dx * dx + dy * dy;
    }

    public Trigger selectedMechanism() {
        if (level == null || !canFight()) return null;
        Trigger best = null;
        for (Trigger target : level.triggers) {
            if (!mechanismEligible(target)) continue;
            if (best == null || targetDistance(target) < targetDistance(best)
                || (targetDistance(target) == targetDistance(best) && target.id < best.id)) best = target;
        }
        return best;
    }

    private boolean mechanismEligible(Trigger target) {
        return level != null && canFight() && !target.isRemoved() && overlapsEntity(target);
    }

    private boolean carryablesEligible() {
        return level != null && carried == null && canFight() && !isAttacking && !clambering && ladder == null;
    }

    private Rectangle pickupReachBounds() {
        Rectangle reach = new Rectangle(hitboxBounds());
        // A small hand/contact tolerance, not a remote interaction radius.
        reach.x -= PICKUP_REACH; reach.y -= PICKUP_REACH;
        reach.width += PICKUP_REACH * 2; reach.height += PICKUP_REACH * 2;
        return reach;
    }

    public Carryable selectedCarryable() {
        if (!carryablesEligible()) return null;
        Rectangle reach = pickupReachBounds();
        Carryable best = null;
        for (var object : level.objects) if (object instanceof Carryable target && target.available() && reach.overlaps(target.bounds())) {
            if (best == null || targetDistance(target) < targetDistance(best)
                || (targetDistance(target) == targetDistance(best) && target.id < best.id)) best = target;
        }
        return best;
    }

    /** Picks the one target Up + Action will affect, using distance then Tiled ID across both types. */
    private InteractionTarget selectedInteraction() {
        if (level == null || !canFight()) return null;
        Trigger mechanism = selectedMechanism();
        Carryable carryable = selectedCarryable();
        if (mechanism == null) return carryable == null ? null : new InteractionTarget(carryable, null);
        if (carryable == null) return new InteractionTarget(null, mechanism);
        float mechanismDistance = targetDistance(mechanism);
        float carryableDistance = targetDistance(carryable);
        if (carryableDistance < mechanismDistance
            || (carryableDistance == mechanismDistance && carryable.id < mechanism.id))
            return new InteractionTarget(carryable, null);
        return new InteractionTarget(null, mechanism);
    }

    private Entity interactionFeedbackTarget(InteractionTarget target) {
        if (target == null) return null;
        return target.carryable() != null ? target.carryable() : level.mechanismFeedbackTarget(target.mechanism());
    }

    private Rectangle placementBounds() {
        Rectangle body = hitboxBounds();
        return new Rectangle(facingRight ? body.x + body.width + PLACEMENT_GAP : body.x - (Carryable.BODY_SIZE + PLACEMENT_GAP),
            body.y + body.height - Carryable.BODY_SIZE, Carryable.BODY_SIZE, Carryable.BODY_SIZE);
    }

    private void putDownCarried() {
        if (!canFight() || carried == null || isAttacking || clambering || ladder != null) return;
        Rectangle place = placementBounds();
        if (level.blocksObject(place, true)) return;
        carried.release(place.x, place.y, false, facingRight);
        carried = null;
    }

    private void pickUp(Carryable target) {
        if (!canFight() || target == null || isAttacking || clambering || ladder != null) return;
        carried = target;
        carryHintAnchor.set(centerX(), getY() + HINT_PLAYER_OFFSET_Y);
        carryHintCameraAnchor.set(cameraTarget(cameraFocusX, position.y));
        carryHintVisible = true;
        carried.pickUp(this);
    }

    /** Selection happens once: dropping the reference after a throw cannot fall through to a fireball. */
    private void shoot() {
        if (!canFight()) return;
        if (carried != null) {
            carried.followHolder();
            carried.release(carried.getX(), carried.getY(), true, facingRight);
            carried = null;
        } else if (level.rangedStock.spend()) {
            Rectangle body = hitboxBounds();
            level.queueSpawn(new Fireball(level, body.x + (body.width - Fireball.BODY_SIZE) / 2, body.y + FIREBALL_SPAWN_OFFSET_Y, facingRight));
        }
    }

    public Msg actionLabel() {
        if (gameplayInput.up() && gameplayInput.down()) return Msg.DIRECTION_CONFLICT;
        if (gameplayInput.down()) return carried == null ? Msg.NO_ACTION :
            level.blocksObject(placementBounds(), true) ? Msg.PLACE_BLOCKED : Msg.PUT_DOWN;
        if (!gameplayInput.up()) return Msg.ATTACK;
        InteractionTarget target = selectedInteraction();
        if (target == null) return Msg.NO_ACTION;
        return target.carryable() != null ? Msg.PICK_UP : mechanismAction(target.mechanism());
    }

    private Msg mechanismAction(Trigger trigger) {
        return level != null && level.mechanismFeedbackTarget(trigger) instanceof Lever lever
            ? lever.isUp() ? Msg.TURN_OFF : Msg.TURN_ON : Msg.INTERACT;
    }

    private Msg mechanismLabel(Trigger trigger) {
        Entity target = level.mechanismFeedbackTarget(trigger);
        return target instanceof Lever ? Msg.LEVER : target instanceof Torch ? Msg.TORCH : Msg.MECHANISM;
    }
    public Msg rangedLabel() { return carried != null ? Msg.THROW : ammunition() > 0 ? Msg.FIREBALL : Msg.NO_AMMO; }

    public String actionPrompt() {
        String target = "";
        Msg action = actionLabel();
        InteractionTarget selection = gameplayInput.up() && !gameplayInput.down() ? selectedInteraction() : null;
        if (action == Msg.PICK_UP && selection != null && selection.carryable() != null)
            target = " - " + selection.carryable().label().t(game.language());
        if (action == Msg.PUT_DOWN || action == Msg.PLACE_BLOCKED) target = " - " + carried.label().t(game.language());
        if ((action == Msg.INTERACT || action == Msg.TURN_ON || action == Msg.TURN_OFF)
            && selection != null && selection.mechanism() != null)
            target = " - " + mechanismLabel(selection.mechanism()).t(game.language());
        return actionBinding(false) + ": " + action.t(game.language()) + target;
    }
    public String rangedPrompt() {
        return actionBinding(true) + ": " + rangedLabel().t(game.language());
    }
    private String actionBinding(boolean ranged) {
        return actionBinding(ranged, gameplayInput.up(), gameplayInput.down());
    }

    /** Contextual teaching remains repeatable throughout the first room, including while carrying. */
    public String contextHint() {
        if (fullFireballRefill() != null) return Msg.FIREBALL_STOCK_FULL.t(game.language());
        if (level == null || !level.tutorialHintsActive() || !canFight() || isAttacking
            || (gameplayInput.up() && gameplayInput.down())) return "";
        String interaction = interactionHint();
        if (gameplayInput.actionHeld() && gameplayInput.verticalHeld()) return interaction;
        return interaction.isEmpty() ? ladderHint() : interaction;
    }

    /** Teaches how to take hold of a ladder in reach; a pickup or mechanism hint takes precedence. */
    private String ladderHint() {
        LadderEntry entry = ladderEntry();
        if (entry == null) return "";
        if (entry.canClimbUp() && entry.canClimbDown())
            return directionBinding(true, false) + ": " + Msg.CLIMB.t(game.language()) + "\n"
                + directionBinding(false, true) + ": " + Msg.CLIMB_DOWN.t(game.language());
        String directions = directionBinding(entry.canClimbUp(), entry.canClimbDown());
        Msg action = entry.canClimbDown() && !entry.canClimbUp() ? Msg.CLIMB_DOWN : Msg.CLIMB;
        return directions + ": " + action.t(game.language());
    }

    private String interactionHint() {
        if (carried != null) {
            Trigger mechanism = selectedMechanism();
            if (!carryHintVisible) return mechanism == null ? "" : mechanismHint(mechanism);
            String place = actionBinding(false, false, true) + ": " + Msg.PUT_DOWN.t(game.language());
            if (level.blocksObject(placementBounds(), true)) place += " - " + Msg.PLACE_BLOCKED.t(game.language());
            String carrying = place + "\n" + rangedPrompt();
            return mechanism == null ? carrying : mechanismHint(mechanism) + "\n" + carrying;
        }
        if (gameplayInput.down()) return "";
        InteractionTarget target = selectedInteraction();
        if (target == null) return "";
        if (gameplayInput.up()) return actionPrompt();
        if (target.carryable() != null)
            return actionBinding(false, true, false) + ": " + Msg.PICK_UP.t(game.language())
                + " - " + target.carryable().label().t(game.language());
        return mechanismHint(target.mechanism());
    }

    private String mechanismHint(Trigger mechanism) {
        return actionBinding(false, true, false) + ": " + mechanismAction(mechanism).t(game.language())
            + " - " + mechanismLabel(mechanism).t(game.language());
    }

    /** Mechanisms keep their local anchor even when the hero already carries another object. */
    public void contextHintPosition(Vector2 out) {
        FireballRefill refill = fullFireballRefill();
        if (refill != null) {
            out.set(refill.centerX(), refill.getY() - HINT_TARGET_OFFSET_Y);
            return;
        }
        LadderEntry entry = interactionHint().isEmpty() ? ladderEntry() : null;
        if (entry != null) { out.set(entry.ladder().centerX(), getY() + HINT_PLAYER_OFFSET_Y); return; }
        if (carried != null && selectedMechanism() == null) { out.set(carryHintAnchor); return; }
        InteractionTarget selection = selectedInteraction();
        Entity target = interactionFeedbackTarget(selection);
        out.set(target == null ? centerX() : target.centerX(),
            target == null ? getY() + HINT_PLAYER_OFFSET_Y : Math.min(target.getY() - HINT_TARGET_OFFSET_Y, getY() + HINT_PLAYER_OFFSET_Y));
    }

    /** The vertical input the current device uses for Up and/or Down. */
    private String directionBinding(boolean up, boolean down) {
        var keys = KeyBindings.of(game.leftHanded());
        var lang = game.language();
        return switch (gameplayInput.device()) {
            case KEYBOARD -> up && down ? keys.upName() + "/" + keys.downName() : up ? keys.upName() : keys.downName();
            case GAMEPAD -> (game.leftHanded() ? Msg.RIGHT_STICK : Msg.LEFT_STICK).t(lang) + " " + (up ? Msg.UP : Msg.DOWN).t(lang);
            case TOUCH -> Msg.STICK.t(lang) + " " + (up ? Msg.UP : Msg.DOWN).t(lang);
        };
    }

    private String actionBinding(boolean ranged, boolean up, boolean down) {
        var keys = KeyBindings.of(game.leftHanded());
        var lang = game.language();
        String direction = !ranged && (up || down) ? directionBinding(up, down) : "";
        String button = switch (gameplayInput.device()) {
            case KEYBOARD -> ranged ? keys.rangedName() : keys.actionName();
            case GAMEPAD -> game.leftHanded() ? (ranged ? Msg.DPAD_UP : Msg.DPAD_ACTION).t(lang) : ranged ? "Y" : "X/B";
            case TOUCH -> (ranged ? Msg.SHOOT : Msg.ACTION).t(lang);
        };
        return direction.isEmpty() ? button : direction + " + " + button;
    }

    public Entity interactionFeedbackTarget() {
        if (!canFight() || !gameplayInput.up() || gameplayInput.down()) return null;
        return interactionFeedbackTarget(selectedInteraction());
    }

    public void renderInteractionTarget(Batch batch) {
        Entity selectedTarget = interactionFeedbackTarget();
        renderPassiveInteractionTargets(batch, selectedTarget);
        if (selectedTarget != null) InteractionHighlight.draw(batch, selectedTarget);
    }

    private void renderPassiveInteractionTargets(Batch batch, Entity selectedTarget) {
        if (level == null || !canFight()) return;
        Set<Entity> outlined = new HashSet<>();
        if (selectedTarget != null) outlined.add(selectedTarget);

        if (carryablesEligible()) {
            Rectangle reach = pickupReachBounds();
            for (var object : level.objects) if (object instanceof Carryable target
                && target.available() && reach.overlaps(target.bounds()) && outlined.add(target))
                InteractionHighlight.drawPassive(batch, target);
        }
        for (Trigger trigger : level.triggers) {
            if (!mechanismEligible(trigger)) continue;
            Entity target = level.mechanismFeedbackTarget(trigger);
            if (target != null && outlined.add(target)) InteractionHighlight.drawPassive(batch, target);
        }
    }

    private void updatePlayerState() {
        State next = look.direction() < 0 ? State.LOOK_UP : look.direction() > 0 ? State.LOOK_DOWN : State.IDLE;

        if (horizontalMovement < 0 && facingRight) {
            facingRight = false;
        } else if (horizontalMovement > 0 && !facingRight) {
            facingRight = true;
        }

        if (horizontalMovement != 0) next = State.RUNNING;
        if (velocity.y > 0) next = State.FALLING;
        if (velocity.y < 0) next = State.JUMPING;
        if (clambering) next = State.CLIMBING;
        if (ladder != null) next = State.LADDER;
        if (isAttacking) next = State.ATTACKING;

        setState(next);
    }

    private void updatePlayerMovement(float dt) {
        tryJump(dt);

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
        if (ladder != null) {
            // Letting go with a hop: sideways drift comes from the held direction like any airborne jump.
            velocity.y = -jumpForce() * LADDER_JUMP_SPEED_FACTOR;
            releaseLadder();
        } else if (clambering) {
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
    }

    private void tryJump(float dt) {
        if (jumpBuffer.consume(!isAttacking && respawnPhase == RespawnPhase.NONE && (onGround || clambering || ladder != null))) {
            playerJump(dt);
        }
    }

    /** Used at gameplay boundaries so held or queued menu/death input cannot jump. */
    public void resetJumpInput() {
        jumpBuffer.reset();
        gameplayInput.reset();
        look.suppress();
    }

    /**
     * @param showCause whether to slow time so the player can see what killed them. A fall into a
     *                  pit has nothing to look at (the player is off screen), so it resets at once.
     */
    private void die(boolean showCause) {
        if (isGotHit || victoryWalk || game.invulnerable()) return;
        level.playerDied();
        if (game.playSounds()) game.playSound("hit");
        isGotHit = true;
        resetJumpInput();
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
     * input again on the first frame after it. Movement can stay held; jumping needs a new press.
     */
    public void arriveAfterReset() {
        resetJumpInput();
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
        if (level.hasBossEncounter()) return;
        level.keepPickups();
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
            bossesHit.clear();
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
                    if (!(enemy instanceof Boss boss) || bossesHit.add(boss)) enemy.collidedWithActor(true);
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

        for (var obj : level.objects) {
            if (obj instanceof FireballRefill refill && overlapsEntity(refill)) {
                refill.collideWithPlayer();
            } else if (obj instanceof Collectable c && overlapsEntity(c)) {
                c.collideWithPlayer();
            } else if (obj instanceof Enemy<?> enemy && (!isAttacking || enemy instanceof Boss) && enemy.hitboxOverlaps(hitboxBounds())) {
                enemy.collidedWithActor(false);
            } else if (obj instanceof Checkpoint cp && overlapsEntity(cp)) {
                reachedCheckpoint();
            } else if (obj instanceof Bomb bomb && hitboxBounds().overlaps(bomb.hitboxBounds())) {
                bomb.collideWithPlayer();
            }
        }
        Trigger selected = selectedMechanism();
        collideWithTriggerId = selected == null ? "" : selected.targetId;
    }

    private FireballRefill fullFireballRefill() {
        if (level == null || !canFight()) return null;
        return level.objects.stream().filter(o -> o instanceof FireballRefill refill
                && !refill.isRemoved() && refill.inventoryFull() && overlapsEntity(refill))
            .map(FireballRefill.class::cast).min(java.util.Comparator.comparingDouble(this::targetDistance)).orElse(null);
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
