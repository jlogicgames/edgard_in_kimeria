package com.jlogicsoftware.kimeria.entity.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
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
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.ui.InteractionHighlight;
import com.jlogicsoftware.kimeria.effects.SilverThread;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;
import com.jlogicsoftware.kimeria.world.RoomFade;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Port of Dart's {@code Player}. */
public class Player extends Actor<Player.State> implements CollideBody {
    public enum State {IDLE, LOOK_UP, LOOK_DOWN, RUNNING, JUMPING, FALLING, HIT, ATTACKING, APPEARING, DISAPPEARING, CLIMBING, LADDER}

    /**
     * Death and respawn, Katana Zero style, with the silver thread (#341, D81). DYING: the world slows to a crawl so
     * the player sees what killed them, while the thread draws out from the wrist to the room start, tightens and
     * pulls under the death fade; the game rebuilds the room at black and replaces this player. RESETTING (a fall
     * into a pit, nothing to see): the game rebuilds the room on its next frame. On the new player, LANDING (after
     * a thread pull): he lands at the start under the fade-in with the thread slack, and takes input on the first
     * frame after the fade; ARRIVING (after a pit): the camera pans back while he lands, as long as the old appear clip.
     */
    public enum RespawnPhase {NONE, DYING, RESETTING, ARRIVING, LANDING}

    /** The Entity box: position and size, the spawn and room geometry and every hitbox are measured in it (D79). */
    private static final int BOX_SIZE = 48;
    /**
     * The drawn frame (#343, D79): 64x64, feet on its bottom row, body centre on x {@link #FRAME_PIVOT_X}. It is drawn at
     * {@code (position.x + pivotX() - FRAME_PIVOT_X, position.y + BOX_SIZE - FRAME_SIZE)} = (x - 8, y - 16): world Y grows
     * down, so the frame's top is 16 px above the box and its bottom is the hitbox bottom, the floor line.
     */
    public static final int FRAME_SIZE = SpriteSheets.HERO_RIGHT.width();
    public static final float FRAME_PIVOT_X = 31.5f;
    private static final float PICKUP_REACH = 3, PLACEMENT_GAP = 2;
    private static final float FIREBALL_SPAWN_OFFSET_Y = 3;
    private static final float HINT_PLAYER_OFFSET_Y = -6, HINT_TARGET_OFFSET_Y = 24, HINT_ZONE_HEIGHT_FRACTION = .45f;
    private static final float CAMERA_TARGET_TOLERANCE = .001f;
    private static final float MAX_PHYSICS_FRAME_SECONDS = .05f;
    private record Clip(State state, int frames, int row, int column, boolean loop) { }
    /** Rows of hero-right.png and hero-left.png (tools/sprite_layouts.py HERO_CLIPS); ATTACKING, HIT, APPEARING and DISAPPEARING keep their old frame counts, which are gameplay timing. */
    private static final Clip[] CLIPS = {
        new Clip(State.IDLE, 4, 0, 0, true), new Clip(State.RUNNING, 6, 1, 0, true),
        new Clip(State.JUMPING, 2, 2, 0, false), new Clip(State.FALLING, 2, 3, 0, true),
        new Clip(State.ATTACKING, 7, 4, 0, false), new Clip(State.LOOK_UP, 1, 5, 0, false),
        new Clip(State.LOOK_DOWN, 1, 5, 1, false), new Clip(State.CLIMBING, 1, 6, 0, true),
        new Clip(State.HIT, 2, 7, 0, false), new Clip(State.APPEARING, 4, 8, 0, false),
        new Clip(State.DISAPPEARING, 4, 9, 0, true)
    };
    /** Rows of hero-ladder.png, a back view used by both facings: climbing up, climbing down, holding still. */
    private static final int LADDER_UP_ROW = 0, LADDER_DOWN_ROW = 1, LADDER_HOLD_ROW = 2, LADDER_CLIMB_FRAMES = 4;
    /**
     * The second set (#343 part 2): rows of both facing sheets drawn over the state's own clip without changing the state
     * machine or any timing. Carrying draws the carry clips (the sword on his back) in place of idle, look and run; a
     * throw plays its three frames, and a dusty landing (not carrying) its squat, without blocking input.
     */
    private static final int CARRY_IDLE_ROW = 10, CARRY_RUN_ROW = 11, THROW_ROW = 12, LAND_ROW = 13, PULL_ROW = 14;
    private static final int CARRY_IDLE_FRAMES = 4, CARRY_RUN_FRAMES = 6, THROW_FRAMES = 3, LAND_FRAMES = 1;
    private static final float THROW_SECONDS = THROW_FRAMES * Player.STEP_TIME, LAND_SECONDS = LAND_FRAMES * Player.STEP_TIME, OVERLAY_OFF = -1f;
    /**
     * The thread pull for #341: tug, lift and land, drawn in place of everything else while {@link #showPull} holds one.
     * The thread is tied to the LEFT wrist in both facings (the far hand facing right, the near one facing left).
     */
    public static final int NO_PULL = -1, PULL_TUG = 0, PULL_LIFT = 1, PULL_LAND = 2, PULL_FRAMES = 3;
    /** The left wrist per pull frame: the forearm pixel next to the hand, in frame pixels, measured on each facing sheet. */
    private static final int[][] PULL_WRIST_RIGHT = {{17, 44}, {17, 44}, {23, 52}}, PULL_WRIST_LEFT = {{46, 44}, {46, 44}, {24, 52}};
    private static final float PIXEL_CENTRE = .5f;

    private static final float STEP_TIME = 0.1f;
    private static final float MOVE_SPEED = 100f;
    /**
     * Downward speed (px/s, gravity adds 9.8 per step) at which touching the floor counts as a landing that kicks up dust:
     * 140 is about 14 frames of falling, a 16 px drop. Walking off a ledge keeps the coyote flag until the speed passes 147,
     * so a few pixels never puff, and a wall slide (speed times 0.1) never does either.
     */
    public static final float LANDING_MIN_FALL_SPEED = 140f;
    /** The landing scuff plays at this fraction of the sound volume. */
    private static final float LANDING_SOUND_VOLUME = .6f;
    /** How slow the world runs from the killing frame until the death fade reaches black. */
    private static final float DEATH_TIME_SCALE = 0.2f;
    /**
     * The silver thread (#341, D81), in real seconds from the killing frame: it draws out from the wrist toward the
     * room start, then tightens while a glint runs from the start to the wrist; when it is taut the pull starts and
     * the game's death fade (RoomFade, 0.25 s to black) covers it. Black falls at 0.70 s, the end of the old slow
     * motion; control returns after the 0.25 s fade-in, at 58 frames at 60 Hz (was 67: KimeriaGameDeathTimingTest).
     */
    public static final float THREAD_DRAW_START = .10f, THREAD_DRAW_END = .30f, THREAD_TAUT = .45f;
    /** The slack thread's sag while it draws out, px below the chord's middle (world Y down). */
    public static final float THREAD_SAG = 16f;
    /** How far the pull draws the hero toward the start (render offset only; the box stays) and lifts him, px. */
    private static final float PULL_DISTANCE = 10f, PULL_RISE = 2f;
    /** A death this close to the start (px, wrist to anchor) sends the thread straight up, to the healer's end. */
    private static final float THREAD_NEAR = 32f, THREAD_UP = 400f;
    /** After a pull: the landing pose under the fade-in, then idle; the slack thread dissolves over the fade-in. */
    private static final float LANDING_POSE_SECONDS = .20f, LANDING_SLACK_SECONDS = RoomFade.HALF_SECONDS;
    /** After a pit: the landing pose, then idle; the arrival takes as long as the appear clip it replaced (4 x 0.1 s). */
    private static final float PIT_LANDING_POSE_SECONDS = .25f, PIT_ARRIVAL_SECONDS = .40f;
    /** The pluck as the thread goes taut plays at this fraction of the sound volume. */
    private static final float THREAD_SOUND_VOLUME = .7f;
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
    /** Reused by the per-frame passive highlight and pickup-reach code (see {@link #pickupReachBounds}): never allocated per frame. */
    private static final int OUTLINE_CAPACITY = 8;
    private final Array<Entity> outlinedScratch = new Array<>(false, OUTLINE_CAPACITY);
    private final Rectangle reachScratch = new Rectangle();
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
    /**
     * The thread beat: the room-start anchor; the facing of the pull (away from the start: the pull frames are drawn
     * yanked backward by the left wrist, #343); the pull's direction along X (+1 toward a start on the right, -1 left,
     * 0 straight up); whether the fade was asked; the landing clock; a reused wrist point.
     */
    private final Vector2 threadAnchor = new Vector2(), wristScratch = new Vector2();
    private boolean threadFacesRight, deathFadeRequested;
    private float pullDirection, arrivalTimer;
    private final SilverThread thread;
    public String collideWithTriggerId = "";

    private final Vector2 startingPosition = new Vector2();
    /** Facing-left clips, drawn instead of mirroring so the sword stays in the right hand (#343, playtest F11). */
    private final Map<State, Animation<TextureRegion>> leftAnimations = new EnumMap<>(State.class);
    private Animation<TextureRegion> ladderUp, ladderDown, ladderHold;
    /** One row of both facing sheets. */
    private record Facing(Animation<TextureRegion> right, Animation<TextureRegion> left, int row) {
        Animation<TextureRegion> of(boolean facingRight) { return facingRight ? right : left; }
    }
    private Facing carryIdle, carryRun, throwClip, landClip, pullClip;
    private float throwTime = OVERLAY_OFF, landTime = OVERLAY_OFF;
    private int pullFrame = NO_PULL;
    /** What {@link #selectDrawn()} chose this frame: no allocation, read by render and the measuring tests. */
    private Animation<TextureRegion> drawn;
    private float drawnTime;
    private int drawnRow, drawnColumn;

    public Player(Assets assets, GameContext game, float x, float y) {
        super(x, y, BOX_SIZE, BOX_SIZE, State.class);
        this.game = game;
        startingPosition.set(x, y);
        viewHalfWidth = game.logicalResolution().x / 2f;
        hitbox = Hitbox.rect(18, 26, 11, 22);
        loadAnimations(assets);
        thread = new SilverThread(assets);
        setState(State.IDLE);
    }

    private void loadAnimations(Assets assets) {
        for (Clip clip : CLIPS) {
            // The facing-right clips are the actor's animations (durations, finished checks); the left ones only draw.
            putAnimation(clip.state(), clip(assets, SpriteSheets.HERO_RIGHT, clip), clip.loop());
            Animation<TextureRegion> left = clip(assets, SpriteSheets.HERO_LEFT, clip);
            left.setPlayMode(clip.loop() ? Animation.PlayMode.LOOP : Animation.PlayMode.NORMAL);
            leftAnimations.put(clip.state(), left);
        }
        ladderUp = ladderClip(assets, LADDER_UP_ROW, LADDER_CLIMB_FRAMES);
        ladderDown = ladderClip(assets, LADDER_DOWN_ROW, LADDER_CLIMB_FRAMES);
        ladderHold = ladderClip(assets, LADDER_HOLD_ROW, 1);
        putAnimation(State.LADDER, ladderUp, true);
        carryIdle = facing(assets, CARRY_IDLE_ROW, CARRY_IDLE_FRAMES, true);
        carryRun = facing(assets, CARRY_RUN_ROW, CARRY_RUN_FRAMES, true);
        throwClip = facing(assets, THROW_ROW, THROW_FRAMES, false);
        landClip = facing(assets, LAND_ROW, LAND_FRAMES, false);
        pullClip = facing(assets, PULL_ROW, PULL_FRAMES, false);
    }

    private static Facing facing(Assets assets, int row, int frames, boolean loop) {
        Clip clip = new Clip(State.IDLE, frames, row, 0, loop);
        Animation<TextureRegion> right = clip(assets, SpriteSheets.HERO_RIGHT, clip), left = clip(assets, SpriteSheets.HERO_LEFT, clip);
        Animation.PlayMode mode = loop ? Animation.PlayMode.LOOP : Animation.PlayMode.NORMAL;
        right.setPlayMode(mode);
        left.setPlayMode(mode);
        return new Facing(right, left, row);
    }

    private static Animation<TextureRegion> clip(Assets assets, SpriteSheets.Sheet sheet, Clip clip) {
        return assets.animation(sheet.path(), clip.frames(), STEP_TIME, FRAME_SIZE, FRAME_SIZE,
            FRAME_SIZE * clip.column(), FRAME_SIZE * clip.row(), clip.frames());
    }

    private static Animation<TextureRegion> ladderClip(Assets assets, int row, int frames) {
        Animation<TextureRegion> animation = assets.animation(SpriteSheets.HERO_LADDER.path(), frames, STEP_TIME,
            FRAME_SIZE, FRAME_SIZE, 0, FRAME_SIZE * row, frames);
        animation.setPlayMode(Animation.PlayMode.LOOP);
        return animation;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    /** Called once by {@link Level} right after the player is placed at its spawn point. */
    public void spawned() {
        carried = null;
        ladder = null;
        throwTime = landTime = OVERLAY_OFF;
        pullFrame = NO_PULL;
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
        if (respawnPhase == RespawnPhase.LANDING) { // the fade is over: play
            respawnPhase = RespawnPhase.NONE;
            showPull(NO_PULL);
        }
        if (respawnPhase != RespawnPhase.NONE) {
            resetJumpInput();
            // Death owns its time scale and camera; normal movement stays suspended.
            // Dying and arriving take no other input, but pausing must stay possible.
            if (game.isGameStarted() && pausePressed()) game.togglePause();
            tickAnimation(dt);
            handleRespawnPhase(dt);
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
        // A queued jump goes first: it lets go with a hop and the held direction then drives it away.
        if (ladder != null && horizontalMovement != 0 && !jumpBuffer.pending()) releaseLadder();
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
            boolean airborne = !onGround;
            float fallSpeed = velocity.y;
            checkVerticalCollisions(level, stepDt);
            if (airborne && onGround && fallSpeed >= LANDING_MIN_FALL_SPEED && !inQuickSand) landingPuff();
        }
        // Landing or grabbing a wall can make a buffered press usable in this very frame.
        tryJump(0f);
        if (wasOnLadder && ladder == null) updatePlayerState();
        checkGameplayOverlaps();

        updateCameraPosition();
        // The climbing pose holds still while the hero does.
        if (ladder == null || ladderDirection() != 0) tickAnimation(dt);
        tickOverlays(dt);
    }

    private void tickOverlays(float dt) {
        if (throwTime != OVERLAY_OFF) throwTime = throwTime + dt >= THROW_SECONDS ? OVERLAY_OFF : throwTime + dt;
        // The squat counts from the first frame it shows (the landing frame may still be FALLING); leaving the ground ends it.
        if (landTime != OVERLAY_OFF && !onGround) landTime = OVERLAY_OFF;
        else if (landTime != OVERLAY_OFF && overlaid()) landTime = landTime + dt >= LAND_SECONDS ? OVERLAY_OFF : landTime + dt;
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
        Rectangle body = hitboxBoundsInto(bodyScratch);
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
        return Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || game.gamepad().pausePressed()
            || (game.touch() != null && game.touch().pausePressed());
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

    // The near hand's palm in Entity-box coordinates (frame pixel minus (8, 16)), measured on hero-right.png and
    // hero-left.png (#343). Facing right the near hand is the sword hand; facing left it is the empty left hand.
    private static final int[] IDLE_GRIP_RIGHT = {31, 34}, RUN_GRIP_RIGHT = {29, 33}, JUMP_GRIP_RIGHT = {31, 22},
        FALL_GRIP_RIGHT = {29, 22}, LADDER_GRIP = {28, 20};
    private static final int[] IDLE_GRIP_LEFT = {16, 34}, RUN_GRIP_LEFT = {32, 33}, JUMP_GRIP_LEFT = {16, 22},
        FALL_GRIP_LEFT = {17, 22}, ATTACK_GRIP_LEFT = {18, 34};
    /** The near fist of the carry clips (#343 part 2), the sword on his back. */
    private static final int[] CARRY_IDLE_GRIP_RIGHT = {32, 32}, CARRY_IDLE_GRIP_LEFT = {15, 32},
        CARRY_RUN_GRIP_RIGHT = {32, 31}, CARRY_RUN_GRIP_LEFT = {15, 31};
    private static final int[][] ATTACK_GRIP_RIGHT = {{31, 22}, {31, 14}, {35, 22}, {33, 27}, {32, 32}, {30, 33}, {31, 34}};

    /** Attachment follows the authored pose of the facing being drawn. */
    public void heldObjectGrip(Vector2 out) {
        var animation = animations.get(current);
        int frame = animation == null ? 0 : animation.getKeyFrameIndex(stateTime);
        int[] grip = carried != null && overlaid() ? (current == State.RUNNING
            ? (facingRight ? CARRY_RUN_GRIP_RIGHT : CARRY_RUN_GRIP_LEFT)
            : (facingRight ? CARRY_IDLE_GRIP_RIGHT : CARRY_IDLE_GRIP_LEFT)) : switch (current) {
            case IDLE, LOOK_UP, LOOK_DOWN -> facingRight ? IDLE_GRIP_RIGHT : IDLE_GRIP_LEFT;
            case RUNNING -> facingRight ? RUN_GRIP_RIGHT : RUN_GRIP_LEFT;
            case ATTACKING -> facingRight ? ATTACK_GRIP_RIGHT[frame % ATTACK_GRIP_RIGHT.length]
                : frame == ATTACK_GRIP_RIGHT.length - 1 ? IDLE_GRIP_LEFT : ATTACK_GRIP_LEFT;
            case JUMPING -> facingRight ? JUMP_GRIP_RIGHT : JUMP_GRIP_LEFT;
            case LADDER -> LADDER_GRIP;
            case FALLING, HIT -> facingRight ? FALL_GRIP_RIGHT : FALL_GRIP_LEFT;
            default -> facingRight ? IDLE_GRIP_RIGHT : IDLE_GRIP_LEFT;
        };
        out.set(position.x + grip[0], position.y + grip[1]);
    }

    /** World X of the drawn frame's left edge: the frame's body axis on the hitbox centre. */
    public float frameX() { return position.x + pivotX() - FRAME_PIVOT_X; }

    /** World Y of the drawn frame's top edge (Y grows down): the frame's bottom row lands on the box bottom, the floor line. */
    public float frameY() { return position.y + size.y - FRAME_SIZE; }

    /** True in the states the second set draws over: standing, looking and running. */
    private boolean overlaid() {
        return current == State.IDLE || current == State.RUNNING || current == State.LOOK_UP || current == State.LOOK_DOWN;
    }

    /**
     * Chooses what is drawn this frame, in priority order: a thread-pull frame (#341), the ladder's up, down or hold
     * clip, then over standing, looking and running the throw, the carry clips and the landing squat, else the state's
     * clip in the facing sheet.
     */
    private void selectDrawn() {
        drawnTime = stateTime;
        drawnColumn = 0;
        if (pullFrame != NO_PULL) {
            drawn = pullClip.of(facingRight); drawnRow = PULL_ROW; drawnTime = pullFrame * STEP_TIME;
        } else if (current == State.LADDER) {
            float direction = ladderDirection();
            drawn = direction < 0 ? ladderUp : direction > 0 ? ladderDown : ladderHold;
            drawnRow = direction < 0 ? LADDER_UP_ROW : direction > 0 ? LADDER_DOWN_ROW : LADDER_HOLD_ROW;
        } else if (overlaid() && throwTime != OVERLAY_OFF) {
            drawn = throwClip.of(facingRight); drawnRow = THROW_ROW; drawnTime = throwTime;
        } else if (overlaid() && carried != null) {
            Facing clip = current == State.RUNNING ? carryRun : carryIdle;
            drawn = clip.of(facingRight); drawnRow = clip.row();
        } else if (overlaid() && landTime != OVERLAY_OFF) {
            drawn = landClip.of(facingRight); drawnRow = LAND_ROW; drawnTime = landTime;
        } else {
            drawn = facingRight ? animations.get(current) : leftAnimations.get(current);
            Clip clip = clipOf(current);
            drawnRow = clip.row(); drawnColumn = clip.column();
        }
    }

    /** The region drawn this frame. */
    public TextureRegion currentFrame() {
        selectDrawn();
        return drawn == null ? null : drawn.getKeyFrame(drawnTime);
    }

    /** The sheet {@link #currentFrame()} comes from, for tests that measure its pixels. */
    public SpriteSheets.Sheet currentSheet() {
        if (current == State.LADDER && pullFrame == NO_PULL) return SpriteSheets.HERO_LADDER;
        return facingRight ? SpriteSheets.HERO_RIGHT : SpriteSheets.HERO_LEFT;
    }

    /** The cell (column, row) of {@link #currentFrame()} in {@link #currentSheet()}. */
    public void currentCell(GridPoint2 out) {
        selectDrawn();
        out.set(drawnColumn + (drawn == null ? 0 : drawn.getKeyFrameIndex(drawnTime)), drawnRow);
    }

    /** #341: draws pull frame {@link #PULL_TUG}, {@link #PULL_LIFT} or {@link #PULL_LAND} in place of everything else; {@link #NO_PULL} stops. */
    public void showPull(int frame) {
        if (frame < NO_PULL || frame >= PULL_FRAMES) throw new IllegalArgumentException("pull frame " + frame);
        pullFrame = frame;
    }

    public int pullFrame() { return pullFrame; }

    /**
     * #341: where the thread is tied in pull frame {@code frame} in the current facing: the centre of the left wrist's
     * pixel, in world coordinates (Y grows down), written into {@code out} (returned). Allocates nothing.
     */
    public Vector2 pullWrist(int frame, Vector2 out) {
        int[] wrist = (facingRight ? PULL_WRIST_RIGHT : PULL_WRIST_LEFT)[frame];
        return out.set(frameX() + wrist[0] + PIXEL_CENTRE, frameY() + wrist[1] + PIXEL_CENTRE);
    }

    private static Clip clipOf(State state) {
        for (Clip clip : CLIPS) if (clip.state() == state) return clip;
        throw new IllegalStateException("no clip for " + state);
    }

    @Override public void render(Batch batch) {
        if (respawnPhase == RespawnPhase.DYING && pullFrame != NO_PULL) {
            renderPull(batch);
            return;
        }
        TextureRegion frame = currentFrame();
        if (frame != null) batch.draw(frame, frameX(), frameY(), FRAME_SIZE, FRAME_SIZE);
        if (carried != null) carried.renderHeld(batch);
        if (respawnPhase == RespawnPhase.LANDING || respawnPhase == RespawnPhase.ARRIVING) {
            // The slack thread hangs from the landing frame's left wrist (facing right after a respawn) and dissolves.
            float slackSeconds = respawnPhase == RespawnPhase.LANDING ? LANDING_SLACK_SECONDS : PIT_ARRIVAL_SECONDS;
            pullWrist(PULL_LAND, wristScratch);
            thread.drawSlack(batch, wristScratch.x, wristScratch.y, position.y + size.y, 1f - arrivalTimer / slackSeconds);
        }
    }

    /** Where the hero is in the death and respawn sequence (D81). */
    public RespawnPhase respawnPhase() { return respawnPhase; }

    /** True while the landing pull frame is drawn: after a pull (under the fade-in) or after a pit, for its first seconds. */
    public boolean drawsLandingPose() { return pullFrame == PULL_LAND; }

    /** The thread beat on the dying hero: the tug or lift frame (facing away from the start), the pull offset, the thread. */
    private void renderPull(Batch batch) {
        float pull = Math.max(0f, Math.min(1f, (deathTimer - THREAD_TAUT) / RoomFade.HALF_SECONDS));
        float dx = PULL_DISTANCE * pullDirection * pull, dy = -PULL_RISE * pull; // toward the start; up is -Y
        TextureRegion frame = currentFrame(); // the pull frame showPull holds
        if (frame != null) batch.draw(frame, frameX() + dx, frameY() + dy, FRAME_SIZE, FRAME_SIZE);
        pullWrist(pullFrame, wristScratch);
        float drawn = Math.min(1f, (deathTimer - THREAD_DRAW_START) / (THREAD_DRAW_END - THREAD_DRAW_START));
        float length = 1f - (1f - drawn) * (1f - drawn); // ease out: the tip slows as it reaches the start
        float tighten = Math.max(0f, Math.min(1f, (deathTimer - THREAD_DRAW_END) / (THREAD_TAUT - THREAD_DRAW_END)));
        float sag = THREAD_SAG * (1f - tighten * tighten); // ease in: it snaps taut at the end
        float glint = tighten > 0 && tighten < 1 ? 1f - tighten : -1f; // from the start (1) to the wrist (0)
        // The sag stops at the higher of the two floor lines (the hero's and the start's; Y down, so the smaller Y).
        float floorY = Math.min(position.y + size.y, startingPosition.y + size.y);
        thread.draw(batch, wristScratch.x + dx, wristScratch.y + dy, threadAnchor.x, threadAnchor.y, sag, length, glint, floorY);
    }
    public int ammunition() { return level.rangedStock.count(); }
    public float verticalMovement() { return gameplayInput.verticalMovement(); }

    private float targetDistance(com.jlogicsoftware.kimeria.entity.Entity target) {
        Rectangle b = hitboxBoundsInto(bodyScratch);
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
        Rectangle reach = hitboxBoundsInto(reachScratch);
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
            throwTime = 0;
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

    /**
     * The contextual (interaction) label: the fireball-stock status message, or inside a map {@code controls}
     * zone the pickup/lever/ladder instructions (also while carrying). Immediate, and independent of the
     * zone message ({@link #zoneHint()}): the two are drawn together (#211).
     */
    public String contextHint() {
        if (fullFireballRefill() != null) return Msg.FIREBALL_STOCK_FULL.t(game.language());
        if (level == null || !canFight() || isAttacking
            || (gameplayInput.up() && gameplayInput.down())) return "";
        return controlHint();
    }

    /**
     * The map-authored tutorial message of the hint zone the hero stands in (D33), or "". The HUD holds it
     * back briefly, animates it in and lets it linger; it is never hidden or replaced by {@link #contextHint()}.
     * It depends only on the hero's position and whether they can act, so an attack or a conflicting
     * direction input does not retire it.
     */
    public String zoneHint() {
        if (level == null || !canFight()) return "";
        var zone = level.messageHint();
        return zone == null ? "" : zone.message().t(game.language());
    }

    private String controlHint() {
        if (level == null || !level.controlHintsActive()) return "";
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

    /** Where the zone message sits: centred on its zone, mid-screen above the standing hero's head. */
    public void zoneHintPosition(Vector2 out) {
        var zone = level == null ? null : level.messageHint();
        if (zone == null) return;
        out.set(zone.bounds().x + zone.bounds().width / 2, zone.bounds().y + zone.bounds().height * HINT_ZONE_HEIGHT_FRACTION);
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
        if (selectedTarget instanceof Lever lever) {
            for (Entity linked : level.linkedMechanisms(lever.trigger)) InteractionHighlight.drawPassive(batch, linked);
        }
        if (selectedTarget != null) InteractionHighlight.draw(batch, selectedTarget);
    }

    /** True the first time {@code target} is added (identity, like the HashSet it replaces). */
    private static boolean outline(Array<Entity> outlined, Entity target) {
        if (outlined.contains(target, true)) return false;
        outlined.add(target);
        return true;
    }

    private void renderPassiveInteractionTargets(Batch batch, Entity selectedTarget) {
        if (level == null || !canFight()) return;
        Array<Entity> outlined = outlinedScratch;
        outlined.clear();
        if (selectedTarget != null) outlined.add(selectedTarget);

        if (carryablesEligible()) {
            Rectangle reach = pickupReachBounds();
            for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Carryable target
                && target.available() && reach.overlaps(target.bounds()) && outline(outlined, target))
                InteractionHighlight.drawPassive(batch, target);
        }
        for (int i = 0; i < level.triggers.size(); i++) {
            Trigger trigger = level.triggers.get(i);
            if (!mechanismEligible(trigger)) continue;
            Entity target = level.mechanismFeedbackTarget(trigger);
            if (target != null && outline(outlined, target)) InteractionHighlight.drawPassive(batch, target);
        }
        outlined.clear();
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
                velocity.x += currentEscalator.riderVelocityX();
            }
        }
        position.x += velocity.x * dt;

        if (clambering) velocity.y *= 0.1f;

        if (wallJumping) {
            wallJumpTimer -= dt;
            if (wallJumpTimer <= 0) wallJumping = false;
        }
    }

    /** A puff at the feet (the floor line, larger Y is lower) and a quieter scuff than the jump's. */
    private void landingPuff() {
        if (carried == null) landTime = 0;
        if (level != null) level.fx().landingPuff(position.x + hitbox.offsetX + hitbox.width / 2f, position.y + hitbox.offsetY + hitbox.height);
        if (game.playSounds()) game.playSound("dust_puff", LANDING_SOUND_VOLUME);
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
            // Ordinary ground jump only: a puff at the feet (feetY is the floor line, larger Y is lower) and its scuff.
            if (level != null) level.fx().dustPuff(position.x + hitbox.offsetX + hitbox.width / 2f, position.y + hitbox.offsetY + hitbox.height);
            if (game.playSounds()) game.playSound("dust_puff");
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
            deathFadeRequested = false;
            aimThread();
            game.setTimeScale(DEATH_TIME_SCALE);
        } else {
            requestRoomReset();
        }
    }

    private void requestRoomReset() {
        respawnPhase = RespawnPhase.RESETTING;
        game.resetRoom();
    }

    /**
     * The thread's far end: the left wrist of the landing frame at the room start (facing right, as every respawn
     * faces), where he will land; or straight up out of the view when he dies at the start. The pull frames are drawn
     * yanked backward by the left wrist, so the pull faces away from the start: a start on the left (smaller X) means
     * facing right. World Y grows down: "up" is a smaller Y.
     */
    private void aimThread() {
        int[] land = PULL_WRIST_RIGHT[PULL_LAND];
        float toFrameX = pivotX() - FRAME_PIVOT_X, toFrameY = size.y - FRAME_SIZE; // box -> frame, as frameX/frameY
        threadAnchor.set(startingPosition.x + toFrameX + land[0] + PIXEL_CENTRE, startingPosition.y + toFrameY + land[1] + PIXEL_CENTRE);
        boolean startOnRight = threadAnchor.x >= centerX();
        threadFacesRight = !startOnRight;
        pullDirection = startOnRight ? 1f : -1f;
        int[] tug = (threadFacesRight ? PULL_WRIST_RIGHT : PULL_WRIST_LEFT)[PULL_TUG];
        float wristX = position.x + toFrameX + tug[0] + PIXEL_CENTRE, wristY = position.y + toFrameY + tug[1] + PIXEL_CENTRE;
        if (Math.abs(threadAnchor.x - wristX) + Math.abs(threadAnchor.y - wristY) < THREAD_NEAR) {
            threadFacesRight = facingRight;
            pullDirection = 0f;
            tug = (threadFacesRight ? PULL_WRIST_RIGHT : PULL_WRIST_LEFT)[PULL_TUG];
            threadAnchor.set(position.x + toFrameX + tug[0] + PIXEL_CENTRE, position.y + toFrameY + tug[1] + PIXEL_CENTRE - THREAD_UP);
        }
    }

    private void handleRespawnPhase(float dt) {
        if (respawnPhase == RespawnPhase.DYING) {
            // Real time: the world is slowed, but the thread beat is a fixed length of it.
            if (game.isGameStarted()) deathTimer += Math.min(Gdx.graphics.getDeltaTime(), MAX_REAL_STEP);
            if (deathTimer >= THREAD_DRAW_START && pullFrame == NO_PULL) {
                // The thread appears: the hero turns away from the start, held by the left wrist.
                facingRight = threadFacesRight;
                showPull(PULL_TUG);
            }
            if (deathTimer >= THREAD_TAUT && pullFrame == PULL_TUG) showPull(PULL_LIFT);
            if (deathTimer >= THREAD_TAUT && !deathFadeRequested) {
                // Taut: the pull starts and the death fade covers it; the game rebuilds the room at black.
                deathFadeRequested = true;
                if (game.playSounds()) game.playSound("thread", THREAD_SOUND_VOLUME);
                game.beginDeathFade();
            }
        } else if (respawnPhase == RespawnPhase.ARRIVING) {
            arrivalTimer += dt;
            if (arrivalTimer >= PIT_LANDING_POSE_SECONDS) showPull(NO_PULL);
            if (arrivalTimer >= PIT_ARRIVAL_SECONDS) respawnPhase = RespawnPhase.NONE;
        }
    }

    /**
     * Called on the player built for a fresh room attempt after a fall into a pit, right after the level placed it:
     * he lands with the thread slack while the camera pans back (started by {@link #spawned()}), and takes input
     * again on the first frame after it. Movement can stay held; jumping needs a new press.
     */
    public void arriveAfterReset() {
        resetJumpInput();
        facingRight = true;
        arrivalTimer = 0f;
        respawnPhase = RespawnPhase.ARRIVING;
        setState(State.IDLE);
        showPull(PULL_LAND);
    }

    /**
     * Called on the player built at the black of the death fade (#341, D81): he lands at the start with the thread
     * slack while the fade-in runs, frozen like the rest of the room; {@link #advanceLanding} is the fade's clock.
     * The first update after the fade takes input.
     */
    public void arriveUnderThread() {
        resetJumpInput();
        facingRight = true;
        arrivalTimer = 0f;
        respawnPhase = RespawnPhase.LANDING;
        setState(State.IDLE);
        showPull(PULL_LAND);
    }

    /** The fade-in's real time, for the landing pose and the slack thread while the room waits. */
    public void advanceLanding(float dt) {
        if (respawnPhase != RespawnPhase.LANDING) return;
        arrivalTimer += dt;
        if (arrivalTimer >= LANDING_POSE_SECONDS) showPull(NO_PULL);
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
        boundsInto(SWORD_HITBOX, attackHitbox);

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
                    if (enemy instanceof Boss boss) { if (bossesHit.add(boss)) boss.collidedWithActor(true); }
                    else enemy.hitBySword(level);
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

        if (level.touchesDeadline(hitboxBoundsInto(bodyScratch))) {
            die(false);
            return;
        }

        for (var obj : level.objects) {
            if (obj instanceof FireballRefill refill && overlapsEntity(refill)) {
                refill.collideWithPlayer();
            } else if (obj instanceof Collectable c && overlapsEntity(c)) {
                c.collideWithPlayer();
            } else if (obj instanceof Enemy<?> enemy && (!isAttacking || enemy instanceof Boss) && enemy.hitboxOverlaps(hitboxBoundsInto(bodyScratch))) {
                enemy.collidedWithActor(false);
            } else if (obj instanceof Checkpoint cp && overlapsEntity(cp)) {
                reachedCheckpoint();
            } else if (obj instanceof Bomb bomb && hitboxBoundsInto(bodyScratch).overlaps(bomb.hitboxBounds())) {
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
