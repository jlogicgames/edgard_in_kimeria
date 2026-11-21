package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.BeetleFx;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The forest beetle (#327, D100): a kamikaze. It crawls inside its authored range, hears the hero, notices in body language
 * (head up, antennae wiggle, a hop), chases, and once the hero is close it commits on the spot and bursts after the shared
 * D7 warning ({@link TrapWarnings}, longer with the assist), whatever the hero does. Its body never hurts: only the burst
 * kills, one 80x32 box tested once at the instant. A fireball or a thrown stone kills it in any state without a burst; the
 * sword in any state, and a stomp before it commits, burst it at once; a stomp on a committed beetle bounces the hero off.
 * No blood (D65 does not apply). Its effects are {@link BeetleFx}; its sprite is one strip of ten frames
 * ({@link SpriteSheets#MONSTER_BEETLE}). World Y grows downward: the floor line is the body's bottom edge.
 *
 * <p>Killed quietly (a fireball or a stone), it leaves its body (D101, {@link Remains}): the {@code dead} frame flips over, then
 * cross-fades into the body sheet, the same frame in the same live palette since D117 ({@link SpriteSheets#MONSTER_BEETLE_BODY}). The body rocks on its shell
 * when anyone comes near (a damped swing of the same frame about the bottom of the shell); a stomp bounces the hero off and
 * crumbles it, as the sword does; a fireball or a thrown torch burns it and flies on; a stone flies through it (D119). A burst leaves nothing. The body is told from the
 * live beetle by its pose: it lies on its back (D101).
 */
public class Beetle extends Enemy<Beetle.State> implements CollideBody, Remains {
    /** {@code DEAD}: killed, the body lying (D101); {@code BURN}: the body burning up. */
    public enum State { CRAWL, NOTICE, CHASE, WINDUP, DEAD, BURN }

    public static final float FRAME_WIDTH = 24, FRAME_HEIGHT = 16;
    /** The body hitbox in the 24x16 frame: the drawn body is x 1..22 (head and legs), y 2..15 (D10). */
    private static final float BODY_X = 1, BODY_Y = 2, BODY_WIDTH = 22, BODY_HEIGHT = 14;
    /** Left and right: the mirror spans twice the pivot (Actor.render), a reach counts on both sides, the shake alternates. */
    private static final int SIDES = 2;
    private static final int CRAWL1 = 0, CRAWL2 = 1, LOOK = 2, WIGGLE = 3, CROUCH = 4, AIR = 5, BRACE = 6, BRIGHT = 7, WHITE = 8,
        DEAD_FRAME = 9, FRAMES = 10;

    /** Patrol and chase speeds (px/s); the hero runs 100, so running away always works. */
    public static final float PATROL_SPEED = 24, CHASE_SPEED = 70;
    private static final float PATROL_FRAME_SECONDS = .15f, CHASE_FRAME_SECONDS = .075f;
    /** Hearing: the hero's centre within this many px either side, inside a band this tall above the floor line. */
    public static final float HEAR_RADIUS = 96, HEAR_BAND = 48;
    /** It loses the hero beyond this distance, or after this long out of the band. */
    public static final float LOSE_RADIUS = 144, LOSE_SECONDS = 1f;
    /** The notice in body language: look, wiggle, look, wiggle, crouch, the hop, landing (0.5 s, not scaled by the assist). */
    private static final int[] NOTICE_FRAMES = {LOOK, WIGGLE, LOOK, WIGGLE, CROUCH, AIR, CROUCH};
    private static final float[] NOTICE_SECONDS = {.1f, .1f, .1f, .1f, .06f, .1f, .04f};
    public static final float NOTICE_TOTAL;
    static {
        float total = 0;
        for (float s : NOTICE_SECONDS) total += s;
        NOTICE_TOTAL = total;
    }
    /** The hop: the {@code air} frame is drawn this many px up (the hitbox stays). */
    private static final float HOP_LIFT = 2;
    /** It commits when the hero's body touches its body grown this many px left and right, at body height. */
    public static final float TRIGGER_REACH = 8;
    /** The wind-up's blink between its own colours and the bright frame quickens from the first period to the second. */
    private static final float BLINK_START = .2f, BLINK_END = .05f;
    /** The last part of the wind-up is drawn white. */
    public static final float WHITE_SECONDS = .12f;
    /** The shake: the sprite drawn this many px left and right of its place, alternating every step (the hitbox stays). */
    private static final float SHAKE_PX = 1, SHAKE_STEP = .04f;
    /** Wind-up specks per second at the start and at the end (denser since the look check of 2026-11-18: 30 to 90 before). */
    public static final float SPECKS_START = 50, SPECKS_END = 150;
    /**
     * The death (a fireball or a stone): the poof starts this long after the hit, once the fireball's own flash is spent, so
     * the death reads over the ring (look check, 2026-11-18). The body flips over in a small hop this high and this long.
     */
    public static final float POOF_DELAY = .08f;
    private static final float FLIP_LIFT = 4, FLIP_SECONDS = .2f;
    /** The active {@code dead} frame holds this long, then cross-fades into the body sheet over the second (D101; the same live palette since D117). */
    public static final float BODY_FADE_DELAY = .3f, BODY_FADE_SECONDS = .6f;
    /** The body rocks when the hero's body enters its box grown this many px left and right (D101). */
    public static final float NEAR_REACH = 6;
    /** A push: the swing's first peak, in degrees, for a hero passing (the shared {@link RemainsSwing}). */
    public static final float KICK_DEGREES = RemainsSwing.KICK_DEGREES;
    /** The pivot, in the 24x16 frame facing right: the middle of the bottom row of the upturned shell. */
    private static final float PIVOT_X = 11, PIVOT_Y = FRAME_HEIGHT;
    /** The burn: the body darkens to this tint over the burn, with flames at this rate, then a wisp; it is gone at the end. */
    public static final float BURN_SECONDS = .3f;
    private static final float BURN_FLAMES_PER_SECOND = 70, CHAR_TINT = .15f;
    /** A hero falls onto the body when its feet were this close above the body's top on the previous frame (as a stomp). */
    private static final float LANDING_TOLERANCE = 2;

    static final String NOTICE_SOUND = "beetle_notice", WINDUP_SOUND = "beetle_windup", WINDUP_LONG_SOUND = "beetle_windup_long",
        POP_SOUND = "beetle_pop", DEATH_SOUND = "bounce";
    /** D101: shared by every monster's body; the rocking is silent. */
    public static final String CRUMBLE_SOUND = "remains_crumble", BURN_SOUND = "remains_burn";

    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    private final TextureRegion bodyFrame;
    private final float spawnX, spawnY;
    private final Rectangle triggerScratch = new Rectangle(), blastScratch = new Rectangle(), heroScratch = new Rectangle();
    private Level level;
    private boolean inQuickSand;
    private Escalator currentEscalator;
    private final Rectangle nearScratch = new Rectangle(), moverScratch = new Rectangle();
    /** The body's swing (D101); flames due while it burns. */
    private final RemainsSwing swing = new RemainsSwing();
    private float flameDue;
    /** Seconds since the kill: the flip, the poof and the cross-fade run on it, through a burn too. */
    private float deadClock;
    private boolean heroNear, moverNear, poofDone;
    private float stateClock, frameClock, outOfBand, blinkClock, speckDue, warningTotal;
    private boolean brightFrame, crawlFrame;
    private int noticeStep;

    public Beetle(Assets assets, GameContext game, float x, float y, float w, float h, float offNeg, float offPos) {
        super(assets, game, "beetle", x, y, w, h, offNeg, offPos, State.class);
        spawnX = x;
        spawnY = y;
        // The drawn body is x 1..22 (head and legs), y 2..15 of the 24x16 frame; the hitbox covers it (D10).
        hitbox = Hitbox.rect(BODY_X, BODY_Y, BODY_WIDTH, BODY_HEIGHT);
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.MONSTER_BEETLE.frame(assets, i, 0);
        bodyFrame = SpriteSheets.MONSTER_BEETLE_BODY.frame(assets, 0, 0);
        rangeNeg = x - offNeg * TILE_SIZE;
        rangePos = x + offPos * TILE_SIZE;
        moveDirection = -1;
        facingRight = false;
        current = State.CRAWL;
    }

    public void setLevel(Level level) { this.level = level; }

    /** Left and right ends of the range the beetle's {@code position.x} stays in, for the load-time checks and tests. */
    public float rangeNeg() { return rangeNeg; }
    public float rangePos() { return rangePos; }
    /** Seconds of the wind-up left (0 outside it). */
    public float warningRemaining() { return current == State.WINDUP ? Math.max(0, warningTotal - stateClock) : 0; }
    public float warningTotal() { return warningTotal; }
    public boolean isCommitted() { return current == State.WINDUP; }
    /** The body's swing now, in degrees (0 at rest; positive: its top leans right). */
    public float rockAngle() { return swing.angle(); }
    public State state() { return current; }

    /** The floor line the beetle stands on: the bottom of its body. */
    public float floorY() { return position.y + hitbox.offsetY + hitbox.height; }
    /** The middle of the body, horizontally. */
    public float centreX() { return hitboxBoundsInto(bodyScratch).x + hitbox.width / 2f; }

    /** The burst's box: {@link BeetleFx#BLAST_WIDTH} by {@link BeetleFx#BLAST_HEIGHT}, centred on the body, on its floor line. */
    public Rectangle blastBoxInto(Rectangle out) {
        float floor = floorY();
        return out.set(centreX() - BeetleFx.BLAST_WIDTH / 2f, floor - BeetleFx.BLAST_HEIGHT, BeetleFx.BLAST_WIDTH, BeetleFx.BLAST_HEIGHT);
    }

    /** The box the hero's body must touch to make it commit: the body grown {@link #TRIGGER_REACH} left and right. */
    public Rectangle triggerBoxInto(Rectangle out) {
        Rectangle body = hitboxBoundsInto(out);
        return out.set(body.x - TRIGGER_REACH, body.y, body.width + SIDES * TRIGGER_REACH, body.height);
    }

    @Override
    public void revisit(Player nextPlayer) {
        super.revisit(nextPlayer);
        // A beetle left behind returns calm at its spawn (D100): surviving enemies take their authored state.
        position.set(spawnX, spawnY);
        velocity.setZero();
        moveDirection = -1;
        facingRight = false;
        enter(State.CRAWL);
    }

    private void enter(State state) {
        current = state;
        stateClock = frameClock = blinkClock = speckDue = outOfBand = flameDue = 0;
        noticeStep = 0;
        brightFrame = false;
    }

    @Override
    public void update(float dt) {
        if (!game.isGameStarted() || isRemoved()) return;
        if (current == State.DEAD || current == State.BURN) {
            deadClock += dt;
            if (!poofDone && deadClock >= POOF_DELAY) poof();
            if (fall(dt)) return;
            if (current == State.DEAD) lie(dt);
            else burn(dt);
            return;
        }
        updateEnemy(dt);
    }

    /** The death's poof, once: {@link #POOF_DELAY} after the kill, or at once when the body is crumbled or burnt before that. */
    private void poof() {
        poofDone = true;
        float floor = floorY();
        if (level != null) level.beetleFx().poof(centreX(), floor - BeetleFx.BODY_CENTRE_ABOVE_FLOOR, floor);
    }

    /**
     * A body falls and settles on the floor under it, like any enemy (a kill on a moving or falling platform); one that falls
     * below the room, over a pit, is gone (D101). True when it is gone.
     */
    private boolean fall(float dt) {
        if (level == null) return false;
        velocity.x = 0;
        applyGravity(dt);
        checkVerticalCollisions(level, dt);
        Rectangle room = level.currentRoomBounds();
        if (position.y <= room.y + room.height) return false;
        removeFromParent();
        return true;
    }

    /** The body lying (D101): a stomp, the hero's pushes, and the damped swing. */
    private void lie(float dt) {
        stateClock += dt;
        if (heroPresent() && stompedOn()) { player.velocity.y = -BOUNCE_HEIGHT; crumble(); return; } // the normal bounce, and it crumbles
        boolean near = heroPresent() && heroNear();
        if (near && !heroNear) push();
        heroNear = near;
        // D119: anyone moving through the body rocks it, not only the hero (monsters, the boss).
        Enemy<?> mover = Remains.moverNear(level, this, grownBox(), moverScratch);
        if (mover != null && !moverNear) swing.push(Math.signum(mover.velocity.x));
        moverNear = mover != null;
        swing.update(dt);
    }

    /** The body's box grown {@link #NEAR_REACH} left and right, in {@code nearScratch}. */
    private Rectangle grownBox() {
        Rectangle body = hitboxBoundsInto(nearScratch);
        body.set(body.x - NEAR_REACH, body.y, body.width + SIDES * NEAR_REACH, body.height);
        return body;
    }

    /** True when the hero's body touches the body grown {@link #NEAR_REACH} left and right. */
    private boolean heroNear() {
        return grownBox().overlaps(player.hitboxBoundsInto(heroScratch));
    }

    /**
     * A stomp, checked every frame on its own: the hero's body touches the body while falling, its feet above the body's top on
     * the previous frame (as {@link Enemy#isStomp}), wherever the hero was before (beside it already, or never away).
     */
    private boolean stompedOn() {
        Rectangle body = hitboxBoundsInto(nearScratch);
        return body.overlaps(player.hitboxBoundsInto(heroScratch)) && player.velocity.y > 0
            && player.previousFeetY() <= body.y + LANDING_TOLERANCE;
    }

    /** The hero arrives beside it: a push the way it moves (or away from it when it stands still). */
    private void push() {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float direction = player.velocity.x != 0 ? Math.signum(player.velocity.x) : hero.x + hero.width / 2f < centreX() ? 1 : -1;
        swing.push(direction);
    }

    /** The body burning: flames at a steady rate while it darkens, then a wisp, then it is gone. */
    private void burn(float dt) {
        stateClock += dt;
        float cx = centreX(), floor = floorY();
        flameDue += BURN_FLAMES_PER_SECOND * dt;
        while (flameDue >= 1) { flameDue -= 1; if (level != null) level.remainsFx().burnFlame(cx, floor); }
        if (stateClock >= BURN_SECONDS) {
            if (level != null) level.remainsFx().burnOut(cx, floor);
            removeFromParent();
        }
    }

    @Override public boolean isRemains() { return current == State.DEAD && !isRemoved(); }
    @Override public Rectangle remainsInto(Rectangle out) { return hitboxBoundsInto(out); }

    @Override
    public void crumble() {
        if (!isRemains()) return;
        if (!poofDone) poof(); // crumbled before the poof was due: the kill's poof still plays, with the crumble
        if (level != null) level.remainsFx().crumble(centreX(), floorY(), BeetleFx.SHELL_CHIPS);
        if (game.playSounds()) game.playSound(CRUMBLE_SOUND);
        removeFromParent();
    }

    @Override
    public void burnUp() {
        if (!isRemains()) return;
        if (!poofDone) poof(); // burnt before the poof was due: the kill's poof still plays, under the flames
        enter(State.BURN);
        if (level != null) level.remainsFx().burnGlow(centreX(), floorY(), BURN_SECONDS);
        if (game.playSounds()) game.playSound(BURN_SOUND);
    }

    @Override
    public void updateEnemy(float dt) {
        stateClock += dt;
        velocity.x = 0;
        switch (current) {
            case CRAWL -> crawl(dt);
            case NOTICE -> notice(dt);
            case CHASE -> chase(dt);
            case WINDUP -> windup(dt);
            default -> { }
        }
        if (isRemoved()) return;
        position.x += velocity.x * dt;
        clampToRange();
        checkHorizontalCollisions(level);
        applyGravity(dt);
        checkVerticalCollisions(level, dt);
    }

    private boolean heroPresent() { return player != null && !player.isRemoved() && player.canLeaveRoom(); }

    private void crawl(float dt) {
        if (heroPresent() && hears()) {
            facingRight = directionToPlayer(facingRight ? 1 : -1) > 0;
            enter(State.NOTICE);
            if (game.playSounds()) game.playSound(NOTICE_SOUND);
            return;
        }
        if (position.x <= rangeNeg) moveDirection = 1;
        else if (position.x >= rangePos) moveDirection = -1;
        facingRight = moveDirection > 0;
        velocity.x = moveDirection * PATROL_SPEED;
        animateCrawl(dt, PATROL_FRAME_SECONDS);
    }

    private void notice(float dt) {
        float elapsed = 0;
        noticeStep = NOTICE_FRAMES.length - 1;
        for (int i = 0; i < NOTICE_SECONDS.length; i++) {
            elapsed += NOTICE_SECONDS[i];
            if (stateClock < elapsed) { noticeStep = i; break; }
        }
        if (stateClock >= NOTICE_TOTAL) enter(State.CHASE);
    }

    private void chase(float dt) {
        if (!heroPresent() || lostHero(dt)) { enter(State.CRAWL); return; }
        if (touchesTrigger()) { commit(); return; }
        float direction = directionToPlayer(facingRight ? 1 : -1);
        facingRight = direction > 0;
        boolean atEdge = (direction < 0 && position.x <= rangeNeg) || (direction > 0 && position.x >= rangePos);
        if (atEdge) return; // it waits at its range's end, facing the hero
        velocity.x = direction * CHASE_SPEED;
        animateCrawl(dt, CHASE_FRAME_SECONDS);
    }

    private void animateCrawl(float dt, float frameSeconds) {
        frameClock += dt;
        while (frameClock >= frameSeconds) { frameClock -= frameSeconds; crawlFrame = !crawlFrame; }
    }

    /** The beetle commits on the spot: the shared warning, sampled once, then the burst whatever the hero does. */
    private void commit() {
        enter(State.WINDUP);
        TrapWarnings warnings = game.trapWarnings();
        warningTotal = warnings != null ? warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS) : TrapWarnings.DEFAULT_WARNING_SECONDS;
        if (game.playSounds()) game.playSound(warningTotal > TrapWarnings.DEFAULT_WARNING_SECONDS ? WINDUP_LONG_SOUND : WINDUP_SOUND);
    }

    private void windup(float dt) {
        float progress = Math.min(1f, stateClock / warningTotal);
        blinkClock += dt;
        float period = BLINK_START + (BLINK_END - BLINK_START) * progress;
        while (blinkClock >= period) { blinkClock -= period; brightFrame = !brightFrame; }
        speckDue += (SPECKS_START + (SPECKS_END - SPECKS_START) * progress) * dt;
        float cx = centreX(), floor = floorY();
        while (speckDue >= 1) { speckDue -= 1; if (level != null) level.beetleFx().speck(cx, floor); }
        if (stateClock >= warningTotal) burst();
    }

    /** True when the hero's body touches the trigger box. */
    private boolean touchesTrigger() {
        return triggerBoxInto(triggerScratch).overlaps(player.hitboxBoundsInto(heroScratch));
    }

    private boolean hears() {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float floor = floorY();
        return Math.abs(hero.x + hero.width / 2f - centreX()) <= HEAR_RADIUS && hero.y < floor && hero.y + hero.height > floor - HEAR_BAND;
    }

    private boolean lostHero(float dt) {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float floor = floorY();
        if (Math.abs(hero.x + hero.width / 2f - centreX()) > LOSE_RADIUS) return true;
        boolean inBand = hero.y < floor && hero.y + hero.height > floor - HEAR_BAND;
        outOfBand = inBand ? 0 : outOfBand + dt;
        return outOfBand >= LOSE_SECONDS;
    }

    private void clampToRange() {
        if (position.x < rangeNeg) position.x = rangeNeg;
        else if (position.x > rangePos) position.x = rangePos;
    }

    /**
     * The burst: the hero's body is tested once against the blast box and killed through {@code collidedWithActor(false)},
     * as the explosive traps do (D47); then the beetle is gone (counted as defeated for the room's revisits).
     */
    public void burst() {
        if (isDefeated()) return;
        float cx = centreX(), floor = floorY();
        if (player != null && blastBoxInto(blastScratch).overlaps(player.hitboxBoundsInto(heroScratch))) player.collidedWithActor(false);
        if (level != null) level.beetleFx().burst(cx, floor);
        if (game.playSounds()) game.playSound(POP_SOUND);
        pendingRemoval = true;
        removeFromParent();
    }

    /** A fireball or a thrown stone ({@code gotHit}) kills it without a burst; body contact is a stomp or nothing (harmless). */
    @Override
    public void collidedWithActor(boolean gotHit) {
        if (isDefeated()) return;
        if (gotHit) { die(); return; }
        if (!stompedByPlayer()) return; // touch is harmless (D100)
        if (current == State.WINDUP) player.velocity.y = -BOUNCE_HEIGHT; // committed: the hero bounces off, no kill
        else burst(); // a stomp before it commits bursts it at once, with no wind-up
    }

    /** The sword bursts it at once in every state, with no wind-up and no blood (D100); on the body, it crumbles it (D101). */
    @Override
    public void hitBySword(Level level) {
        if (isRemains()) crumble();
        else burst();
    }

    /** Killed quietly: it flips onto its back and stays as a body (D101); the poof follows a moment later ({@link #POOF_DELAY}). */
    private void die() {
        enter(State.DEAD);
        pendingRemoval = true;
        poofDone = heroNear = moverNear = false;
        swing.reset();
        deadClock = 0;
        velocity.setZero();
        if (game.playSounds()) game.playSound(DEATH_SOUND);
    }

    @Override
    protected void setDefeated() { die(); }

    /** The frame index drawn now, and how far it is shifted (x for the shake, y for the hop). */
    int frameIndex() {
        return switch (current) {
            case CRAWL, CHASE -> crawlFrame ? CRAWL2 : CRAWL1;
            case NOTICE -> NOTICE_FRAMES[noticeStep];
            case WINDUP -> warningTotal - stateClock <= WHITE_SECONDS ? WHITE : brightFrame ? BRIGHT : BRACE;
            case DEAD, BURN -> DEAD_FRAME;
        };
    }

    /**
     * How far the body has faded from the active {@code dead} frame into the body sheet: 0 at the kill, 1 once settled. It runs
     * on through a burn, so a body burnt early keeps its fade and its flip.
     */
    public float bodyFade() {
        if (current != State.DEAD && current != State.BURN) return 0;
        return Math.min(1f, Math.max(0f, (deadClock - BODY_FADE_DELAY) / BODY_FADE_SECONDS));
    }

    @Override
    public void render(Batch batch) {
        if (current == State.DEAD || current == State.BURN) { renderBody(batch); return; }
        int index = frameIndex();
        float dx = current == State.WINDUP ? ((int) (stateClock / SHAKE_STEP) % SIDES == 0 ? SHAKE_PX : -SHAKE_PX) : 0;
        float dy = index == AIR ? -HOP_LIFT : 0;
        TextureRegion frame = frames[index];
        float x = position.x + dx, y = position.y + dy;
        if (facingRight) batch.draw(frame, x, y, FRAME_WIDTH, FRAME_HEIGHT);
        else batch.draw(frame, x + SIDES * pivotX(), y, -FRAME_WIDTH, FRAME_HEIGHT);
    }

    /**
     * The body: the flip (a small hop), the cross-fade from the active {@code dead} frame into the body sheet, the swing about
     * the bottom of the shell, and while burning a darkening tint. Nothing allocates; the batch colour is put back.
     */
    private void renderBody(Batch batch) {
        float lift = deadClock < FLIP_SECONDS ? FLIP_LIFT * MathUtils.sin(MathUtils.PI * deadClock / FLIP_SECONDS) : 0;
        // Facing left the frame is mirrored about its origin (the pivot, in the unmirrored frame) by a negative scale; this x puts
        // the mirrored frame where Actor's mirror about pivotX() puts it, so the pivot stays under the shell either way.
        float drawX = facingRight ? position.x : position.x + SIDES * (pivotX() - PIVOT_X), y = position.y - lift;
        float scaleX = facingRight ? 1 : -1, drawOrigin = PIVOT_X;
        float saved = batch.getPackedColor();
        float fade = bodyFade();
        float tint = current == State.BURN ? 1f - (1f - CHAR_TINT) * Math.min(1f, stateClock / BURN_SECONDS) : 1f;
        if (fade < 1) {
            batch.setColor(tint, tint, tint, 1f - fade);
            batch.draw(frames[DEAD_FRAME], drawX, y, drawOrigin, PIVOT_Y, FRAME_WIDTH, FRAME_HEIGHT, scaleX, 1, swing.angle());
        }
        if (fade > 0) {
            batch.setColor(tint, tint, tint, fade);
            batch.draw(bodyFrame, drawX, y, drawOrigin, PIVOT_Y, FRAME_WIDTH, FRAME_HEIGHT, scaleX, 1, swing.angle());
        }
        batch.setPackedColor(saved);
    }

    /**
     * The load-time checks (D100): the range plus 40 px either side inside the room, on a floor without gaps under the body's
     * whole sweep, and no blast position over the entrance body, the room exit or a checkpoint. Null when the beetle is
     * fine, else what is wrong.
     */
    public static String problem(float x, float y, float w, float h, float offNeg, float offPos, List<Rectangle> floors,
                                 Rectangle room, Map<String, Rectangle> standing) {
        if (!(w > 0) || !(h > 0)) return "needs positive bounds";
        if (!Float.isFinite(offNeg) || !Float.isFinite(offPos) || offNeg < 0 || offPos < 0) return "needs finite, non-negative offNeg and offPos";
        float left = x - offNeg * TILE_SIZE, right = x + offPos * TILE_SIZE + w, floor = y + h;
        float centreLeft = left + w / SIDES, centreRight = right - w / SIDES, half = BeetleFx.BLAST_WIDTH / SIDES;
        Rectangle swept = new Rectangle(centreLeft - half, floor - BeetleFx.BLAST_HEIGHT, centreRight - centreLeft + BeetleFx.BLAST_WIDTH,
            BeetleFx.BLAST_HEIGHT);
        if (swept.x < room.x || swept.y < room.y || swept.x + swept.width > room.x + room.width || floor > room.y + room.height)
            return "has its range plus the blast (" + (int) half + " px either side) leaving the room";
        float covered = left;
        List<Rectangle> spans = new ArrayList<>();
        for (Rectangle f : floors) if (Math.abs(f.y - floor) < 1f) spans.add(f);
        spans.sort((a, b) -> Float.compare(a.x, b.x));
        for (Rectangle f : spans) if (f.x <= covered + 1f) covered = Math.max(covered, f.x + f.width);
        if (covered < right) return "must crawl on floor without gaps under its whole range";
        for (Map.Entry<String, Rectangle> place : standing.entrySet())
            if (swept.overlaps(place.getValue())) return "can burst over the room " + place.getKey();
        return null;
    }

    @Override public Hitbox hitbox() { return hitbox; }
    @Override public boolean isInQuickSand() { return inQuickSand; }
    @Override public void setInQuickSand(boolean value) { inQuickSand = value; }
    @Override public Escalator currentEscalator() { return currentEscalator; }
    @Override public void setCurrentEscalator(Escalator escalator) { currentEscalator = escalator; }
}
