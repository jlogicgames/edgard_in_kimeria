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
import com.jlogicsoftware.kimeria.effects.ScreenShake;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The forest flying beetle (#330, D145): a kamikaze that brings its burst down from the air. It flies to and fro along an
 * authored level line, rising over a solid or a one-way platform in its way and sinking behind it (D146). It hears the hero
 * below, notices in body language (it stops, claps its wing covers and drops, opens them, claps again and tips its head
 * down), follows along its line and, once the hero is close under it with a clear straight line
 * down, hangs there for the shared D7 warning ({@link TrapWarnings}, longer with the assist) while specks gather into it from
 * all round (D147) and others run down the line to the spot where he stood; then it dives along that line and bursts. Its body never hurts: only
 * the burst kills, one box {@value BeetleFx#BLAST_WIDTH} px wide and {@value BeetleFx#BLAST_AIR_HALF_HEIGHT} px above and
 * below its middle, cut at the surface under it (at the end of a dive, the ground beetle's 80x32), tested once. A burst due at the
 * end of a dive resolves on the next frame, so a hit of that frame wins. A fireball, a thrown stone or a torch kills it in any
 * state without a burst: it falls and leaves its body ({@link Remains}; over a pit, nothing). The sword in any state, and a
 * stomp before it commits, burst it at once; a stomp on a committed one bounces the hero off. No blood.
 *
 * <p>World Y grows downward: the body's bottom edge is the larger y, and a dive adds to y.
 */
public class FlyingBeetle extends Enemy<FlyingBeetle.State> implements CollideBody, Remains {
    /** {@code DIVE}: diving along the drawn line; {@code DEAD}: killed, the body falling or lying (D101); {@code BURN}: it burns. */
    public enum State { PATROL, NOTICE, CHASE, WINDUP, DIVE, DEAD, BURN }

    public static final float FRAME_WIDTH = 40, FRAME_HEIGHT = 32;
    /** The body hitbox in the 40x32 frame: thorax, belly, head and legs (x 10..31, y 17..31); wings, covers and antennae lie outside it (D10). */
    private static final float BODY_X = 10, BODY_Y = 17, BODY_WIDTH = 22, BODY_HEIGHT = 15;
    /** Left and right: the mirror spans twice the pivot, a reach counts on both sides, the shake alternates. */
    private static final int SIDES = 2;
    private static final float HALF = .5f;
    private static final int FLY1 = 0, FLY2 = 1, FLY3 = 2, CLAP = 3, TIP = 4, BRACE = 5, BRIGHT = 6, WHITE = 7, DIVE_FRAME = 8,
        DEAD_FRAME = 9, FRAMES = 10;

    /** Patrol and chase speeds (px/s); the hero runs 100, so running away always works. */
    public static final float PATROL_SPEED = 40, CHASE_SPEED = 70;
    /** The wingbeat: up, level, down, level, at this long each; faster in the chase. */
    private static final int[] WINGBEAT = {FLY1, FLY2, FLY3, FLY2};
    private static final float PATROL_BEAT_SECONDS = .08f, CHASE_BEAT_SECONDS = .05f;
    /** The body is drawn this many px lower on the wing-up frame (the hitbox stays). */
    private static final float BOB_PX = 1;
    /** Hearing: the hero's centre within this many px either side, in a band from the body's top this far down. */
    public static final float HEAR_RADIUS = 96, HEAR_BAND = 96;
    /** It loses the hero beyond this distance, or after this long out of the band. */
    public static final float LOSE_RADIUS = 144, LOSE_SECONDS = 1f;
    /** It commits with the hero's centre this close either side: no dive lands further from its range end. */
    public static final float COMMIT_REACH = 48;
    /**
     * The flight line: the body's bottom this far above every surface under the range, at least (a standing hero walks under
     * it and his sword cannot reach it) and at most (a jumping shot can). A dive goes down at most this far under the level
     * line, also when it starts higher, over something the line crosses (D146).
     */
    public static final float MIN_HEIGHT = 40, MAX_HEIGHT = 64;
    /**
     * Over a solid or a one-way platform in its way (D146) the body's bottom stays this far above the top; it rises at most
     * {@link #MAX_CLIMB} px from its line, so anything taller bars the line.
     */
    public static final float CLIMB_HEIGHT = 48, MAX_CLIMB = 96;
    private static final Rectangle[] NOTHING = new Rectangle[0];
    /**
     * The line to the target is tested in steps this long: the dive with a square this wide, the commit with a wider one that
     * covers every square the dive can test, so a committed dive never stops short on a corner.
     */
    private static final float LINE_STEP = 2, DIVE_PROBE = 2, COMMIT_PROBE = DIVE_PROBE + LINE_STEP;
    /** The load check samples dive targets, starts and moving tops this far apart, and each dive line in this many shares. */
    private static final float CHECK_STEP = 4;
    /** The load check walks the body along the route this far at a time. */
    private static final float CHECK_ROUTE_STEP = 1;
    private static final int CHECK_DIVE_SHARES = 16;
    /** Feet this far above a surface still count as standing on it. */
    private static final float STANDING_TOLERANCE = .5f;
    /** The notice in body language: clap (dropped), open, clap (dropped), head tipped down (0.5 s, not scaled by the assist). */
    private static final int[] NOTICE_FRAMES = {CLAP, FLY1, CLAP, TIP};
    private static final float[] NOTICE_SECONDS = {.15f, .1f, .1f, .15f};
    public static final float NOTICE_TOTAL;
    static {
        float total = 0;
        for (float s : NOTICE_SECONDS) total += s;
        NOTICE_TOTAL = total;
    }
    /** A clap drops the drawn body this many px (the hitbox stays). */
    private static final float CLAP_DROP = 3;
    /** The wind-up's blink between its own colours and the bright frame quickens from the first period to the second. */
    private static final float BLINK_START = .2f, BLINK_END = .05f;
    /** The last part of the wind-up is drawn white. */
    public static final float WHITE_SECONDS = .12f;
    /** The shake: the sprite drawn this many px left and right of its place, alternating every step (the hitbox stays). */
    private static final float SHAKE_PX = 1, SHAKE_STEP = .04f;
    /** Wind-up specks per second drawn in to the monster, at the start and at the end (D147); and along the dive line. */
    public static final float SPECKS_START = 50, SPECKS_END = 150, LINE_SPECKS = 60;
    /** The dive takes this long whatever its length (at most about 430 px/s, from over a platform to the floor). */
    public static final float DIVE_SECONDS = .25f;
    /** The death's poof starts this long after the hit, once the fireball's own flash is spent (as the ground beetle's). */
    public static final float POOF_DELAY = Beetle.POOF_DELAY;
    /** The poof's chips fall to the surface under it, or this far when there is none (over a pit). */
    private static final float PIT_CHIP_FALL = 64;
    /** The active {@code dead} frame holds this long, then cross-fades into the body sheet over the second (D101, D117). */
    public static final float BODY_FADE_DELAY = Beetle.BODY_FADE_DELAY, BODY_FADE_SECONDS = Beetle.BODY_FADE_SECONDS;
    /** The body rocks when an actor enters its box grown this many px left and right (D101). */
    public static final float NEAR_REACH = Beetle.NEAR_REACH;
    /** The pivot, in the 40x32 frame facing right: the middle of the bottom row of the upturned body. */
    private static final float PIVOT_X = 19;
    /** The burn: the body darkens to this tint over the burn, with flames at this rate, then a wisp; it is gone at the end. */
    public static final float BURN_SECONDS = Beetle.BURN_SECONDS;
    private static final float BURN_FLAMES_PER_SECOND = 70, CHAR_TINT = .15f;
    /** A hero falls onto the body when its feet were this close above the body's top on the previous frame (as a stomp). */
    private static final float LANDING_TOLERANCE = 2;

    static final String NOTICE_SOUND = "flying_beetle_notice", WINDUP_SOUND = "flying_beetle_windup",
        WINDUP_LONG_SOUND = "flying_beetle_windup_long", DIVE_SOUND = "flying_beetle_dive";

    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    private final TextureRegion bodyFrame;
    private final float spawnX, spawnY;
    /** What its line crosses and it flies over (D146); set once at load, empty on a clear line. */
    private Rectangle[] climbed = NOTHING;
    private final Rectangle blastScratch = new Rectangle(), heroScratch = new Rectangle(), nearScratch = new Rectangle(),
        moverScratch = new Rectangle(), probeScratch = new Rectangle();
    private Level level;
    private boolean inQuickSand;
    private Escalator currentEscalator;
    /** The body's swing (D101); flames due while it burns. */
    private final RemainsSwing swing = new RemainsSwing();
    private float flameDue;
    /** Seconds since the kill: the poof and the cross-fade run on it, through a burn too. */
    private float deadClock;
    private boolean heroNear, moverNear, poofDone;
    private float stateClock, beatClock, outOfBand, blinkClock, speckDue, lineDue, warningTotal;
    /** The dive reached the locked spot (nothing stopped it): its burst is cut at the drawn surface, whatever happens to it. */
    private boolean brightFrame, burstDue, arrived;
    private int beat, noticeStep;
    /** The dive, locked at the commit: from the body's middle where it hangs to its middle where it bursts, over a surface. */
    private float diveFromX, diveFromY, diveToX, diveToY, diveSurface;

    public FlyingBeetle(Assets assets, GameContext game, float x, float y, float w, float h, float offNeg, float offPos) {
        super(assets, game, "flying-beetle", x, y, w, h, offNeg, offPos, State.class);
        spawnX = x;
        spawnY = y;
        hitbox = Hitbox.rect(BODY_X, BODY_Y, BODY_WIDTH, BODY_HEIGHT);
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.MONSTER_FLYING_BEETLE.frame(assets, i, 0);
        bodyFrame = SpriteSheets.MONSTER_FLYING_BEETLE_BODY.frame(assets, 0, 0);
        rangeNeg = x - offNeg * TILE_SIZE;
        rangePos = x + offPos * TILE_SIZE;
        moveDirection = -1;
        facingRight = false;
        current = State.PATROL;
    }

    public void setLevel(Level level) { this.level = level; }

    /** Picks what it will fly over out of the room's static solids and one-way platforms, and takes its place on that route. */
    public void flyOver(List<Rectangle> floors) {
        climbed = climbed(floors, spawnX, spawnY, rangeNeg, rangePos);
        followRoute();
    }

    /** The body's bottom edge on the route when {@code position.x} is {@code x}. */
    public float routeBottom(float x) { return routeBottom(x, bottomAt(spawnY), climbed); }

    /** Takes the height the route has where it is now (outside a dive). */
    private void followRoute() { position.y = routeBottom(position.x) - BODY_Y - BODY_HEIGHT; }

    /** The body's bottom edge when the frame's top is at {@code y}. */
    private static float bottomAt(float y) { return y + BODY_Y + BODY_HEIGHT; }

    /**
     * The route (D146): the level line, lifted so that over each climbed box the body's bottom is {@link #CLIMB_HEIGHT} px
     * above its top. Beside a box it rises and sinks one px for each px along the line. No allocation.
     */
    private static float routeBottom(float x, float line, Rectangle[] climbed) {
        float bottom = line;
        for (int i = 0; i < climbed.length; i++) {
            Rectangle box = climbed[i];
            float gap = Math.max(0, Math.max(box.x - (x + BODY_X + BODY_WIDTH), x + BODY_X - (box.x + box.width)));
            bottom = Math.min(bottom, box.y - CLIMB_HEIGHT + gap);
        }
        return bottom;
    }

    /**
     * What the line from {@code left} to {@code right} flies over (D146): a box the body's sweep reaches into, whose top is not
     * below the body's bottom and which a rise of at most {@link #MAX_CLIMB} px clears. A floor below the line is not in its way.
     */
    private static Rectangle[] climbed(List<Rectangle> floors, float x, float y, float left, float right) {
        float line = bottomAt(y);
        List<Rectangle> found = new ArrayList<>();
        for (Rectangle f : floors)
            if (f.x < right + BODY_X + BODY_WIDTH && f.x + f.width > left + BODY_X && f.y <= line && f.y + f.height > line - BODY_HEIGHT
                && line - (f.y - CLIMB_HEIGHT) <= MAX_CLIMB) found.add(f);
        return found.toArray(NOTHING);
    }

    /** Left and right ends of the flight line {@code position.x} stays in outside a dive, for the load checks and tests. */
    public float rangeNeg() { return rangeNeg; }
    public float rangePos() { return rangePos; }
    /** Seconds of the wind-up left (0 outside it). */
    public float warningRemaining() { return current == State.WINDUP ? Math.max(0, warningTotal - stateClock) : 0; }
    public float warningTotal() { return warningTotal; }
    /** Winding up or diving: nothing the hero does changes where it bursts, and a stomp bounces him off. */
    public boolean isCommitted() { return current == State.WINDUP || current == State.DIVE; }
    /** The locked burst point of the dive: the middle of the body there, and the surface under it. */
    public float targetX() { return diveToX; }
    public float targetY() { return diveToY; }
    public float targetSurface() { return diveSurface; }
    /** The body's swing now, in degrees (0 at rest; positive: its top leans right). */
    public float rockAngle() { return swing.angle(); }
    public State state() { return current; }

    /** The body's bottom edge; on a surface, that surface's line. */
    public float floorY() { return position.y + hitbox.offsetY + hitbox.height; }
    /** The middle of the body. */
    public float centreX() { return hitboxBoundsInto(bodyScratch).x + hitbox.width * HALF; }
    public float centreY() { return position.y + hitbox.offsetY + hitbox.height * HALF; }

    /**
     * The burst's box where the monster is now: {@link BeetleFx#BLAST_WIDTH} wide, {@link BeetleFx#BLAST_AIR_HALF_HEIGHT} above
     * and below the body's middle, cut at the surface under it.
     */
    public Rectangle blastBoxInto(Rectangle out) {
        float cx = centreX(), cy = centreY(), top = cy - BeetleFx.BLAST_AIR_HALF_HEIGHT;
        float bottom = burstFloor(cx, cy);
        return out.set(cx - BeetleFx.BLAST_WIDTH * HALF, top, BeetleFx.BLAST_WIDTH, bottom - top);
    }

    /**
     * Where the burst ends downwards: after a dive that reached its spot, the surface the wind-up drew the box on, even if that
     * has gone since (D5); else the surface under the body's middle now, or the box's own lower edge over open air.
     */
    private float burstFloor(float cx, float cy) {
        if (arrived) return diveSurface;
        float lowest = cy + BeetleFx.BLAST_AIR_HALF_HEIGHT;
        if (level == null) return lowest;
        float surface = level.surfaceUnder(cx, cy, lowest);
        return Float.isNaN(surface) ? lowest : surface;
    }

    @Override
    public void revisit(Player nextPlayer) {
        super.revisit(nextPlayer);
        // Left behind, it returns calm to its line (D6): surviving enemies take their authored state.
        position.set(spawnX, spawnY);
        velocity.setZero();
        moveDirection = -1;
        facingRight = false;
        followRoute();
        enter(State.PATROL);
    }

    private void enter(State state) {
        current = state;
        stateClock = beatClock = blinkClock = speckDue = lineDue = outOfBand = flameDue = 0;
        beat = noticeStep = 0;
        brightFrame = burstDue = arrived = false;
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

    @Override
    public void updateEnemy(float dt) {
        stateClock += dt;
        velocity.x = 0;
        switch (current) {
            case PATROL -> patrol(dt);
            case NOTICE -> notice();
            case CHASE -> chase(dt);
            case WINDUP -> windup(dt);
            case DIVE -> dive();
            default -> { }
        }
    }

    private boolean heroPresent() { return player != null && !player.isRemoved() && player.canLeaveRoom(); }

    private void patrol(float dt) {
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
        position.x = MathUtils.clamp(position.x + velocity.x * dt, rangeNeg, rangePos);
        followRoute();
        beatWings(dt, PATROL_BEAT_SECONDS);
    }

    private void notice() {
        float elapsed = 0;
        noticeStep = NOTICE_FRAMES.length - 1;
        for (int i = 0; i < NOTICE_SECONDS.length; i++) {
            elapsed += NOTICE_SECONDS[i];
            if (stateClock < elapsed) { noticeStep = i; break; }
        }
        if (stateClock >= NOTICE_TOTAL) enter(State.CHASE);
    }

    private void chase(float dt) {
        if (!heroPresent() || lostHero(dt)) { enter(State.PATROL); return; }
        facingRight = directionToPlayer(facingRight ? 1 : -1) > 0;
        if (tryCommit()) return;
        beatWings(dt, CHASE_BEAT_SECONDS);
        // Towards the point of its line over the hero's middle, never past it and never off the line.
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float over = MathUtils.clamp(position.x + hero.x + hero.width * HALF - centreX(), rangeNeg, rangePos);
        float step = MathUtils.clamp(over - position.x, -CHASE_SPEED * dt, CHASE_SPEED * dt);
        if (dt > 0) velocity.x = step / dt;
        position.x += step;
        followRoute();
    }

    private void beatWings(float dt, float beatSeconds) {
        beatClock += dt;
        while (beatClock >= beatSeconds) { beatClock -= beatSeconds; beat = (beat + 1) % WINGBEAT.length; }
    }

    private boolean hears() {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        return Math.abs(hero.x + hero.width * HALF - centreX()) <= HEAR_RADIUS && inBand(hero);
    }

    /** The hero's body overlaps the band from the body's top {@link #HEAR_BAND} px down. */
    private boolean inBand(Rectangle hero) {
        float top = position.y + hitbox.offsetY;
        return hero.y < top + HEAR_BAND && hero.y + hero.height > top;
    }

    private boolean lostHero(float dt) {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        if (Math.abs(hero.x + hero.width * HALF - centreX()) > LOSE_RADIUS) return true;
        outOfBand = inBand(hero) ? 0 : outOfBand + dt;
        return outOfBand >= LOSE_SECONDS;
    }

    /**
     * Commits when the hero's middle is within {@link #COMMIT_REACH} either side, the first surface under him lies below the
     * body and at most {@link #MAX_HEIGHT} px under the level line, and the straight line to the spot over it is clear. The
     * spot is locked there; the shared warning is sampled once.
     */
    private boolean tryCommit() {
        if (level == null) return false;
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float heroX = hero.x + hero.width * HALF, heroFeet = hero.y + hero.height;
        float cx = centreX(), cy = centreY(), bottom = floorY();
        if (Math.abs(heroX - cx) > COMMIT_REACH) return false;
        float surface = level.surfaceUnder(heroX, heroFeet - STANDING_TOLERANCE, bottomAt(spawnY) + MAX_HEIGHT);
        if (Float.isNaN(surface) || surface < bottom) return false;
        float endY = surface - BeetleFx.BODY_CENTRE_ABOVE_FLOOR;
        if (clearShare(cx, cy, heroX, endY, COMMIT_PROBE) < 1) return false;
        enter(State.WINDUP);
        diveFromX = cx; diveFromY = cy; diveToX = heroX; diveToY = endY; diveSurface = surface;
        if (Math.abs(heroX - cx) >= 1) facingRight = heroX > cx;
        TrapWarnings warnings = game.trapWarnings();
        warningTotal = warnings != null ? warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS) : TrapWarnings.DEFAULT_WARNING_SECONDS;
        if (game.playSounds()) game.playSound(warningTotal > TrapWarnings.DEFAULT_WARNING_SECONDS ? WINDUP_LONG_SOUND : WINDUP_SOUND);
        return true;
    }

    /**
     * How much of the straight line from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}) is free of solids, one-way
     * platforms and moving objects, for a square {@code probe} px wide: 1 when all of it is, else the share up to the last
     * free step.
     */
    private float clearShare(float x0, float y0, float x1, float y1, float probe) {
        float dx = x1 - x0, dy = y1 - y0, length = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(1, MathUtils.ceil(length / LINE_STEP));
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            probeScratch.set(x0 + dx * t - probe * HALF, y0 + dy * t - probe * HALF, probe, probe);
            if (level.blocksObject(probeScratch, true)) return (i - 1) / (float) steps;
        }
        return 1;
    }

    private void windup(float dt) {
        float progress = Math.min(1f, stateClock / warningTotal);
        blinkClock += dt;
        float period = BLINK_START + (BLINK_END - BLINK_START) * progress;
        while (blinkClock >= period) { blinkClock -= period; brightFrame = !brightFrame; }
        speckDue += (SPECKS_START + (SPECKS_END - SPECKS_START) * progress) * dt;
        while (speckDue >= 1) { speckDue -= 1; if (level != null) level.beetleFx().gather(diveFromX, diveFromY); }
        lineDue += LINE_SPECKS * dt;
        while (lineDue >= 1) { lineDue -= 1; if (level != null) level.beetleFx().lineSpeck(diveFromX, diveFromY, diveToX, diveToY); }
        if (stateClock >= warningTotal) {
            enter(State.DIVE);
            if (game.playSounds()) game.playSound(DIVE_SOUND);
        }
    }

    /**
     * The dive along the drawn line. At its end, or where something has moved into the line, the burst falls due and happens on
     * the next frame, so a hit of this frame (a ball cast, a stone thrown) wins (#503, #505).
     */
    private void dive() {
        if (burstDue) { burst(); return; }
        float t = Math.min(1f, stateClock / DIVE_SECONDS);
        float cx = centreX(), cy = centreY();
        float nextX = diveFromX + (diveToX - diveFromX) * t, nextY = diveFromY + (diveToY - diveFromY) * t;
        float share = level == null ? 1 : clearShare(cx, cy, nextX, nextY, DIVE_PROBE);
        position.x += (nextX - cx) * share;
        position.y += (nextY - cy) * share;
        arrived = share >= 1 && t >= 1;
        burstDue = share < 1 || t >= 1;
    }

    /**
     * The burst: the hero's body is tested once against the blast box and killed through {@code collidedWithActor(false)}, as
     * the explosive traps do (D47); then it is gone (counted as defeated for the room's revisits).
     */
    public void burst() {
        if (isDefeated()) return;
        float cx = centreX(), cy = centreY(), floor = burstFloor(cx, cy);
        if (player != null && blastBoxInto(blastScratch).overlaps(player.hitboxBoundsInto(heroScratch))) player.collidedWithActor(false);
        if (level != null) level.beetleFx().burstAt(cx, cy, floor);
        if (game.playSounds()) game.playSound(Beetle.POP_SOUND);
        if (level != null && player != null && !player.isGotHit() && feelsBurst(cx, cy))
            level.fx().shake().start(ScreenShake.BURST_AMPLITUDE_PX, ScreenShake.BURST_SECONDS);
        pendingRemoval = true;
        removeFromParent();
    }

    /** The hero's centre is within {@link Beetle#SHAKE_RANGE} of the burst. */
    private boolean feelsBurst(float cx, float cy) {
        player.hitboxBoundsInto(heroScratch);
        float dx = heroScratch.x + heroScratch.width * HALF - cx, dy = heroScratch.y + heroScratch.height * HALF - cy;
        return dx * dx + dy * dy <= Beetle.SHAKE_RANGE * Beetle.SHAKE_RANGE;
    }

    /** A fireball, a stone or a torch ({@code gotHit}) kills it without a burst; body contact is a stomp or nothing (harmless). */
    @Override
    public void collidedWithActor(boolean gotHit) {
        if (isDefeated()) return;
        if (gotHit) { die(); return; }
        if (!stompedByPlayer()) return; // touch is harmless
        if (isCommitted()) player.velocity.y = -BOUNCE_HEIGHT; // the hero bounces off, no kill
        else burst();
    }

    /** The sword bursts it at once in every state (D145); on the body, it crumbles it (D101). */
    @Override
    public void hitBySword(Level level) {
        if (isRemains()) crumble();
        else burst();
    }

    /** Killed quietly: it turns over and falls, to lie as a body (D101); the poof follows a moment later ({@link #POOF_DELAY}). */
    private void die() {
        clearSides();
        enter(State.DEAD);
        pendingRemoval = true;
        poofDone = heroNear = moverNear = false;
        swing.reset();
        deadClock = 0;
        velocity.setZero();
        setOnGround(false);
        if (game.playSounds()) game.playSound(Beetle.DEATH_SOUND);
    }

    @Override
    protected void setDefeated() { die(); }

    /**
     * The dive's line is thin, so a diving body can overlap a wall or a ledge beside its middle. Killed there, it is moved off
     * it sideways before it falls; the landing sweep would otherwise lift it on to that top (D101: it lies on what is below).
     */
    private void clearSides() {
        if (level == null) return;
        for (int i = 0; i < level.collisionBlocks.size(); i++) {
            CollisionBlock block = level.collisionBlocks.get(i);
            if (block.isActive() && !block.isQuickSand()) pushOff(block);
        }
        for (int i = 0; i < level.escalators.size(); i++) pushOff(level.escalators.get(i));
        for (int i = 0; i < level.fallingPlatforms.size(); i++) pushOff(level.fallingPlatforms.get(i));
    }

    /** Moves the body sideways off {@code box} when they overlap and the box lies wholly to one side of the body's middle. */
    private void pushOff(Entity box) {
        float middle = centreX();
        Rectangle body = hitboxBoundsInto(nearScratch);
        if (!(box.getX() < body.x + body.width && box.getX() + box.getWidth() > body.x
            && box.getY() < body.y + body.height && box.getY() + box.getHeight() > body.y)) return;
        if (box.getX() + box.getWidth() <= middle) position.x += box.getX() + box.getWidth() - body.x;
        else if (box.getX() >= middle) position.x -= body.x + body.width - box.getX();
    }

    /** The death's poof, once: {@link #POOF_DELAY} after the kill, or at once when the body is crumbled or burnt before that. */
    private void poof() {
        poofDone = true;
        if (level == null) return;
        float cx = centreX(), cy = centreY();
        Rectangle room = level.currentRoomBounds();
        float surface = level.surfaceUnder(cx, cy, room.y + room.height);
        level.beetleFx().poof(cx, cy, Float.isNaN(surface) ? cy + PIT_CHIP_FALL : surface);
    }

    /** A killed one falls to the solid or platform under it; one that falls out of the room, over a pit, is gone (D101). */
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

    /** The body (D101): a stomp, the pushes of whoever passes, and the damped swing. */
    private void lie(float dt) {
        stateClock += dt;
        if (heroPresent() && stompedOn()) { player.velocity.y = -BOUNCE_HEIGHT; crumble(); return; } // the normal bounce, and it crumbles
        if (!isOnGround()) return; // still falling: nothing rocks in the air
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

    private boolean heroNear() {
        return grownBox().overlaps(player.hitboxBoundsInto(heroScratch));
    }

    /** A stomp on the body: the hero's body touches it while falling, its feet above the body's top on the previous frame. */
    private boolean stompedOn() {
        Rectangle body = hitboxBoundsInto(nearScratch);
        return body.overlaps(player.hitboxBoundsInto(heroScratch)) && player.velocity.y > 0
            && player.previousFeetY() <= body.y + LANDING_TOLERANCE;
    }

    /** The hero arrives beside it: a push the way it moves (or away from it when it stands still). */
    private void push() {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float direction = player.velocity.x != 0 ? Math.signum(player.velocity.x) : hero.x + hero.width * HALF < centreX() ? 1 : -1;
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
        if (game.playSounds()) game.playSound(Beetle.CRUMBLE_SOUND);
        removeFromParent();
    }

    @Override
    public void burnUp() {
        if (!isRemains()) return;
        if (!poofDone) poof(); // burnt before the poof was due: the kill's poof still plays, under the flames
        enter(State.BURN);
        if (level != null) level.remainsFx().burnGlow(centreX(), floorY(), BURN_SECONDS);
        if (game.playSounds()) game.playSound(Beetle.BURN_SOUND);
    }

    /** The frame index drawn now. */
    int frameIndex() {
        return switch (current) {
            case PATROL, CHASE -> WINGBEAT[beat];
            case NOTICE -> NOTICE_FRAMES[noticeStep];
            case WINDUP -> warningTotal - stateClock <= WHITE_SECONDS ? WHITE : brightFrame ? BRIGHT : BRACE;
            case DIVE -> DIVE_FRAME;
            case DEAD, BURN -> DEAD_FRAME;
        };
    }

    /** How far below its place the sprite is drawn now (the hitbox stays). */
    public float drawnDrop() { return drawnDrop(frameIndex()); }

    /** How far the drawn sprite sits below its place: the claps of the notice, the bob of the wingbeat. */
    private float drawnDrop(int index) {
        if (index == CLAP) return CLAP_DROP;
        return index == FLY1 && (current == State.PATROL || current == State.CHASE) ? BOB_PX : 0;
    }

    /** How far the body has faded from the active {@code dead} frame into the body sheet: 0 at the kill, 1 once settled. */
    public float bodyFade() {
        if (current != State.DEAD && current != State.BURN) return 0;
        return Math.min(1f, Math.max(0f, (deadClock - BODY_FADE_DELAY) / BODY_FADE_SECONDS));
    }

    @Override
    public void render(Batch batch) {
        if (current == State.DEAD || current == State.BURN) { renderBody(batch); return; }
        int index = frameIndex();
        float dx = current == State.WINDUP ? ((int) (stateClock / SHAKE_STEP) % SIDES == 0 ? SHAKE_PX : -SHAKE_PX) : 0;
        TextureRegion frame = frames[index];
        float x = position.x + dx, y = position.y + drawnDrop(index);
        if (facingRight) batch.draw(frame, x, y, FRAME_WIDTH, FRAME_HEIGHT);
        else batch.draw(frame, x + SIDES * pivotX(), y, -FRAME_WIDTH, FRAME_HEIGHT);
    }

    /**
     * The body: the cross-fade from the active {@code dead} frame into the body sheet, the swing about the bottom of the shell,
     * and while burning a darkening tint. Nothing allocates; the batch colour is put back.
     */
    private void renderBody(Batch batch) {
        // Facing left the frame is mirrored about its origin by a negative scale; this x puts the mirrored frame where Actor's
        // mirror about pivotX() puts it, so the pivot stays under the shell either way.
        float drawX = facingRight ? position.x : position.x + SIDES * (pivotX() - PIVOT_X), y = position.y;
        float scaleX = facingRight ? 1 : -1;
        float saved = batch.getPackedColor();
        float fade = bodyFade();
        float tint = current == State.BURN ? 1f - (1f - CHAR_TINT) * Math.min(1f, stateClock / BURN_SECONDS) : 1f;
        if (fade < 1) {
            batch.setColor(tint, tint, tint, 1f - fade);
            batch.draw(frames[DEAD_FRAME], drawX, y, PIVOT_X, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT, scaleX, 1, swing.angle());
        }
        if (fade > 0) {
            batch.setColor(tint, tint, tint, fade);
            batch.draw(bodyFrame, drawX, y, PIVOT_X, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT, scaleX, 1, swing.angle());
        }
        batch.setPackedColor(saved);
    }

    /**
     * The load checks (D145, D146), on the 40x32 monster whatever the object's size, all along its route: its range inside the
     * room; the body inside the room and the camera's view and clear of {@code blockers} (solids, platforms, hanging objects
     * and everything a moving object passes through); the highest static surface under any point of the level line, what it
     * flies over aside, {@link #MIN_HEIGHT} to {@link #MAX_HEIGHT} px below the line (gaps allowed), and no surface, moving
     * or not ({@code movers}, each the whole area it passes through), closer than the minimum under the body; and no burst,
     * on the route or anywhere along any dive it could make, over a standing place. Null when it is fine, else what is wrong.
     */
    public static String problem(float x, float y, float w, float h, float offNeg, float offPos, List<Rectangle> floors,
                                 List<Rectangle> movers, List<Rectangle> blockers, Rectangle room, float cameraTop,
                                 Map<String, Rectangle> standing) {
        if (!(w > 0) || !(h > 0)) return "needs positive bounds";
        if (!Float.isFinite(offNeg) || !Float.isFinite(offPos) || offNeg < 0 || offPos < 0) return "needs finite, non-negative offNeg and offPos";
        float left = x - offNeg * TILE_SIZE, right = x + offPos * TILE_SIZE;
        if (left < room.x || right + FRAME_WIDTH > room.x + room.width) return "has its range leaving the room";
        Rectangle[] climbed = climbed(floors, x, y, left, right);
        float line = bottomAt(y);
        Rectangle body = new Rectangle();
        for (float step = 0; step < right - left + CHECK_ROUTE_STEP; step += CHECK_ROUTE_STEP) {
            float at = Math.min(right, left + step), bottom = routeBottom(at, line, climbed);
            body.set(at + BODY_X, bottom - BODY_HEIGHT, BODY_WIDTH, BODY_HEIGHT);
            if (body.y < room.y || bottom > room.y + room.height) return "has its range leaving the room";
            if (bottom - BODY_HEIGHT - BODY_Y < cameraTop) return "flies above the camera's view of its room";
            for (Rectangle blocker : blockers) if (body.overlaps(blocker)) return "has something solid, one-way or moving on its flight line";
        }
        List<Rectangle> unclimbed = new ArrayList<>(floors);
        for (Rectangle box : climbed) unclimbed.remove(box);
        // Sampled strictly inside the sweep: a floor or platform that only touches one of its ends does not lie under the line.
        float sweepLeft = left + BODY_X, sweepWidth = right - left + BODY_WIDTH;
        for (float step = 0; step < sweepWidth + LINE_STEP; step += LINE_STEP) {
            float column = sweepLeft + Math.min(sweepWidth - HALF, step + HALF);
            // The lowest the body's bottom gets over this column: where the route is nearest to what lies under it.
            float lowest = lowestRouteOver(column, left, right, line, climbed);
            if (Math.min(surfaceAt(floors, column, lowest), surfaceAt(movers, column, lowest)) - lowest < MIN_HEIGHT)
                return "flies less than " + (int) MIN_HEIGHT + " px above the surface under x " + (int) column;
            float surface = surfaceAt(unclimbed, column, line);
            if (surface != Float.MAX_VALUE && surface - line > MAX_HEIGHT)
                return "flies more than " + (int) MAX_HEIGHT + " px above the surface under x " + (int) column;
        }
        float middle = BODY_X + BODY_WIDTH * HALF;
        float half = BeetleFx.BLAST_WIDTH * HALF;
        Rectangle inFlight = new Rectangle();
        for (float step = 0; step < right - left + CHECK_STEP; step += CHECK_STEP) {
            float at = Math.min(right, left + step), middleY = routeBottom(at, line, climbed) - BODY_HEIGHT * HALF;
            inFlight.set(at + middle - half, middleY - BeetleFx.BLAST_AIR_HALF_HEIGHT, BeetleFx.BLAST_WIDTH,
                BeetleFx.BLAST_AIR_HALF_HEIGHT * SIDES);
            for (Map.Entry<String, Rectangle> place : standing.entrySet())
                if (inFlight.overlaps(place.getValue())) return "can burst over the room " + place.getKey();
        }
        return diveProblem(left + middle, right + middle, line, climbed, floors, movers, standing);
    }

    /** The largest route bottom among the places on the line from which the body covers {@code column}. */
    private static float lowestRouteOver(float column, float left, float right, float line, Rectangle[] climbed) {
        float from = MathUtils.clamp(column - BODY_X - BODY_WIDTH + HALF, left, right);
        float to = MathUtils.clamp(column - BODY_X - HALF, left, right);
        float lowest = routeBottom(to, line, climbed);
        for (float at = from; at < to; at += CHECK_ROUTE_STEP) lowest = Math.max(lowest, routeBottom(at, line, climbed));
        return lowest;
    }

    /**
     * No dive reaches a standing place: for every spot a dive could lock on, from every point of the route within its commit
     * reach, on every surface under it that dive could go down to, the burst box anywhere along the straight line (a sword or
     * something moving in can set it off mid-dive) and at its end.
     */
    private static String diveProblem(float middleLeft, float middleRight, float line, Rectangle[] climbed, List<Rectangle> floors,
                                      List<Rectangle> movers, Map<String, Rectangle> standing) {
        Rectangle box = new Rectangle();
        List<Float> surfaces = new ArrayList<>();
        float firstTarget = middleLeft - COMMIT_REACH, lastTarget = middleRight + COMMIT_REACH;
        for (float step = 0; step < lastTarget - firstTarget + CHECK_STEP; step += CHECK_STEP) {
            float target = Math.min(lastTarget, firstTarget + step);
            float from = Math.max(middleLeft, target - COMMIT_REACH), to = Math.min(middleRight, target + COMMIT_REACH);
            for (float along = 0; along < to - from + CHECK_STEP; along += CHECK_STEP) {
                float start = Math.min(to, from + along);
                float bottom = routeBottom(start - BODY_X - BODY_WIDTH * HALF, line, climbed), middleY = bottom - BODY_HEIGHT * HALF;
                surfaces.clear();
                for (Rectangle f : floors) if (spans(f, target) && f.y >= bottom - 1 && f.y <= line + MAX_HEIGHT) surfaces.add(f.y);
                // A moving object's top can be anywhere in the area it passes through.
                for (Rectangle m : movers)
                    if (spans(m, target)) for (float top = Math.max(m.y, bottom); top <= Math.min(m.y + m.height, line + MAX_HEIGHT); top += CHECK_STEP)
                        surfaces.add(top);
                for (float surface : surfaces) {
                    for (int i = 0; i <= CHECK_DIVE_SHARES; i++) {
                        float share = i / (float) CHECK_DIVE_SHARES;
                        float cx = start + (target - start) * share;
                        float cy = middleY + (surface - BeetleFx.BODY_CENTRE_ABOVE_FLOOR - middleY) * share;
                        float top = cy - BeetleFx.BLAST_AIR_HALF_HEIGHT;
                        box.set(cx - BeetleFx.BLAST_WIDTH * HALF, top, BeetleFx.BLAST_WIDTH,
                            Math.min(cy + BeetleFx.BLAST_AIR_HALF_HEIGHT, surface) - top);
                        for (Map.Entry<String, Rectangle> place : standing.entrySet())
                            if (box.overlaps(place.getValue())) return "can dive on the room " + place.getKey();
                    }
                }
            }
        }
        return null;
    }

    private static boolean spans(Rectangle area, float x) { return x >= area.x && x <= area.x + area.width; }

    /** The highest surface at {@code x} no higher than {@code from}, or {@link Float#MAX_VALUE} over open air. */
    private static float surfaceAt(List<Rectangle> floors, float x, float from) {
        float surface = Float.MAX_VALUE;
        for (Rectangle f : floors) if (spans(f, x) && f.y >= from - 1 && f.y < surface) surface = f.y;
        return surface;
    }

    @Override public Hitbox hitbox() { return hitbox; }
    @Override public boolean isInQuickSand() { return inQuickSand; }
    @Override public void setInQuickSand(boolean value) { inQuickSand = value; }
    @Override public Escalator currentEscalator() { return currentEscalator; }
    @Override public void setCurrentEscalator(Escalator escalator) { currentEscalator = escalator; }
}
