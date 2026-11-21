package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.physics.ProjectileStep;
import com.jlogicsoftware.kimeria.world.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The forest snail (#329, D106): the first monster that hurts from a distance and the first that only looks ahead. It crawls
 * slowly inside its authored range and pauses at each end before it turns. It sees only in front of it, and a solid cuts its
 * sight; noticed, it stops, shoots its stalks up twice (0.5 s, not scaled by the assist) and calls, then holds still and faces
 * the hero. It never chases. When the hero's body touches its firing line (a 4 px band at shin height, cut at the first solid)
 * and its reload is done, it rears and winds up for the shared D7 warning ({@link TrapWarnings}, longer with the assist), its
 * specks drawn in along the line, and spits one gob that flies straight and level. Only the gob kills; touching the snail is
 * harmless. After the spit it reloads for {@link #RELOAD_SECONDS}, harmless, not scaled by the assist.
 *
 * <p>The gob is owned here, one per snail, with no allocation (D106): it splats on the hero, on the first solid face, or after
 * {@link #GOB_RANGE} px falls to the floor and splats there, seen and heard hit or miss (D93). It passes through one-way
 * platforms, monsters, bodies and pickups. Every weapon breaks it and flies on ({@link Breakable}): the sword's live box and a
 * thrown stone break it apart, a fireball and a thrown torch burn it away. A gob's hit on the hero lands at the start of the
 * snail's next frame, so a blade that cuts it on the same frame wins. A gob in flight flies on when its snail is killed; a snail
 * killed during its wind-up fires nothing.
 *
 * <p>The sword, a fireball, a thrown stone or torch and a stomp kill the snail in any state; it leaves its body (D101,
 * {@link Remains}): the empty shell on its back, which keeps its live palette (D117), rocks when the hero passes, is
 * crumbled by the sword, a stone or a stomp and burnt by a fireball or a torch. The shell blocks nothing. Its effects are
 * {@link com.jlogicsoftware.kimeria.effects.SnailFx}; its sprite is one strip ({@link SpriteSheets#MONSTER_SNAIL}). World Y grows
 * downward: the floor line is the body's bottom edge, and the gob flies {@link #GOB_HEIGHT} px above it (a smaller Y).
 */
public class Snail extends Enemy<Snail.State> implements CollideBody, Remains, Breakable {
    /** {@code PAUSE}: at a range end before it turns; {@code WATCH}: aware, ready; {@code DEAD}: the body; {@code BURN}: it burns. */
    public enum State { CRAWL, PAUSE, NOTICE, WATCH, WINDUP, SPIT, RELOAD, DEAD, BURN }

    public static final float FRAME_WIDTH = 24, FRAME_HEIGHT = 16;
    /** The body hitbox in the 24x16 frame: the shell, the foot and the neck are x 1..20, y 3..15 (D10); the stalks stay out. */
    private static final float BODY_X = 1f, BODY_Y = 3, BODY_WIDTH = 20f, BODY_HEIGHT = 13;
    private static final int SIDES = 2;
    private static final int CRAWL1 = 0, CRAWL2 = 1, LOOK = 2, BLINK = 3, DRAW = 4, SPIT_FRAME = 5, REST = 6, DEAD_FRAME = 7,
        GOB_CELL = 8, FRAMES = 8, GOB_FRAMES = 2;

    /** It crawls this slowly (px/s), the slowest thing in the game, and stops this long at each end before it turns. */
    public static final float PATROL_SPEED = 12, PAUSE_SECONDS = .8f;
    private static final float CRAWL_FRAME_SECONDS = .25f;
    /** Sight, ahead only: from its front edge this far ahead, from its floor line up to this high, cut at the first solid. */
    public static final float SIGHT_RANGE = 176, SIGHT_BAND = 48;
    /** Aware, it loses the hero after this long out of sight; a hero behind it is followed with a turn after this long. */
    public static final float LOSE_SECONDS = 1.5f, TURN_SECONDS = .4f;
    /** The notice in body language: stalks up, pulled in, up, pulled in (0.5 s, not scaled by the assist). */
    private static final int[] NOTICE_FRAMES = {LOOK, BLINK, LOOK, BLINK};
    private static final float[] NOTICE_SECONDS = {.15f, .1f, .15f, .1f};
    public static final float NOTICE_TOTAL;
    static {
        float total = 0;
        for (float s : NOTICE_SECONDS) total += s;
        NOTICE_TOTAL = total;
    }
    /** The spit frame holds this long; the reload runs from the spit, harmless and not scaled by the assist. */
    public static final float SPIT_SECONDS = .15f, RELOAD_SECONDS = 2.4f;
    /** While it reloads, the rest and blink frames alternate this often: a slow swallow. */
    private static final float SWALLOW_SECONDS = .4f;

    /** The gob: its core's centre flies this high above the floor line, this fast, this far before it drops. */
    public static final float GOB_HEIGHT = 7f, GOB_SPEED = 120, GOB_RANGE = 176f;
    /** The lethal core, inside the drawn gob (D10); the firing line is this band, the core's height. */
    public static final float GOB_CORE = 4f;
    /** The drawn gob is an 8x8 cell centred on the core; its two wobble frames alternate this often. */
    private static final float GOB_DRAW = 8, GOB_WOBBLE_SECONDS = .1f;
    /**
     * A weapon breaks what it visibly touches: the drawn gob, 8 wide and 6 tall (its art fills rows 1 to 6 of the cell), not
     * only its lethal core. A fireball flies at the hero's chest (y 3 to 13 below his top) and meets the gob's top edge.
     */
    public static final float GOB_BREAK_WIDTH = 8, GOB_BREAK_HEIGHT = 6;
    /** Past its range the gob falls at the hero's gravity (px/s^2) and splats on the floor. */
    public static final float GOB_GRAVITY = 588;
    /** The gob sheds a drip every this many px it flies. */
    private static final float DRIP_SPACING = 12;
    /** The mouth, in the 24x16 frame facing right: the gob is born here, and the specks run into it. */
    private static final float MOUTH_X = 21f;
    /** The firing line is traced in steps this long at load time and for the specks (px). */
    private static final float TRACE_STEP = 2;
    /** The load check's drop past the range is traced at this rate (steps a second), for at most this many steps (2 s). */
    private static final float DROP_TRACE_RATE = 240;
    private static final int DROP_TRACE_STEPS = 480;
    /** The load check walks the range in whole steps; this much float slack keeps its far end in. */
    private static final float RANGE_SLACK = .01f;
    /** Wind-up specks per second, and the swelling glow's rate (one short glow each). */
    public static final float SPECKS_PER_SECOND = 40, SWELLS_PER_SECOND = 30;

    /** The death: the poof starts this long after the hit; the empty shell rolls over in a hop this high and long. */
    public static final float POOF_DELAY = .08f;
    private static final float FLIP_LIFT = 3, FLIP_SECONDS = .2f;
    /** The active {@code dead} frame holds this long, then cross-fades into the body sheet over the second (D101; the same live palette since D117). */
    public static final float BODY_FADE_DELAY = .3f, BODY_FADE_SECONDS = .6f;
    /** The body rocks when the hero's body enters its box grown this many px left and right (D101). */
    public static final float NEAR_REACH = 6;
    /** The pivot, in the 24x16 frame facing right: the middle of the bottom of the upturned shell. */
    private static final float PIVOT_X = 12, PIVOT_Y = FRAME_HEIGHT;
    public static final float BURN_SECONDS = .3f;
    private static final float BURN_FLAMES_PER_SECOND = 70, CHAR_TINT = .15f;
    private static final float LANDING_TOLERANCE = 2;
    private static final float BODY_CENTRE_ABOVE_FLOOR = 6;

    static final String NOTICE_SOUND = "snail_notice", WINDUP_SOUND = "snail_windup", WINDUP_LONG_SOUND = "snail_windup_long",
        SPIT_SOUND = "snail_spit", SPLAT_SOUND = "snail_splat", DEATH_SOUND = "bounce";
    public static final String CRUMBLE_SOUND = "remains_crumble", BURN_SOUND = "remains_burn";

    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    private final TextureRegion[] gobFrames = new TextureRegion[GOB_FRAMES];
    private final TextureRegion bodyFrame;
    private final float spawnX, spawnY;
    private final Rectangle heroScratch = new Rectangle(), boxScratch = new Rectangle(), nearScratch = new Rectangle(),
        lineScratch = new Rectangle(), gobScratch = new Rectangle();
    private Level level;
    private boolean clambering, inQuickSand;
    private Escalator currentEscalator;
    private final RemainsSwing swing = new RemainsSwing();
    private float flameDue, deadClock;
    private boolean heroNear, poofDone, gone;
    private float stateClock, frameClock, lostClock, behindClock, reloadLeft, speckDue, swellDue, warningTotal;
    private boolean crawlFrame;
    private int noticeStep;

    // The gob, one per snail (D106): plain fields, no allocation.
    private boolean gobActive, gobFalling, gobHitPending, gobRight;
    private float gobX, gobY, gobVy, gobFlown, gobFloor, gobClock, gobDripDue;

    public Snail(Assets assets, GameContext game, float x, float y, float w, float h, float offNeg, float offPos) {
        super(assets, game, "snail", x, y, w, h, offNeg, offPos, State.class);
        spawnX = x;
        spawnY = y;
        hitbox = Hitbox.rect(BODY_X, BODY_Y, BODY_WIDTH, BODY_HEIGHT);
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.MONSTER_SNAIL.frame(assets, i, 0);
        for (int i = 0; i < GOB_FRAMES; i++)
            gobFrames[i] = assets.region(SpriteSheets.MONSTER_SNAIL.path(), (int) (GOB_CELL * FRAME_WIDTH + i * GOB_DRAW), 0, (int) GOB_DRAW, (int) GOB_DRAW);
        bodyFrame = SpriteSheets.MONSTER_SNAIL_BODY.frame(assets, 0, 0);
        rangeNeg = x - offNeg * TILE_SIZE;
        rangePos = x + offPos * TILE_SIZE;
        moveDirection = -1;
        facingRight = false;
        current = State.CRAWL;
    }

    public void setLevel(Level level) { this.level = level; }

    public float rangeNeg() { return rangeNeg; }
    public float rangePos() { return rangePos; }
    public State state() { return current; }
    public float warningTotal() { return warningTotal; }
    /** Seconds of the wind-up left (0 outside it). */
    public float warningRemaining() { return current == State.WINDUP ? Math.max(0, warningTotal - stateClock) : 0; }
    /** Seconds of reload left (0 when it is ready to wind up again). */
    public float reloadLeft() { return reloadLeft; }
    public float rockAngle() { return swing.angle(); }
    public boolean gobActive() { return gobActive; }
    /** The gob's core centre (valid while {@link #gobActive()}). */
    public float gobX() { return gobX; }
    public float gobY() { return gobY; }

    /** The floor line the snail stands on: the bottom of its body. */
    public float floorY() { return position.y + hitbox.offsetY + hitbox.height; }
    public float centreX() { return hitboxBoundsInto(bodyScratch).x + hitbox.width / 2f; }
    /** Where the gob is born: its mouth, mirrored about the body's centre when it faces left. */
    public float mouthX() { return facingRight ? position.x + MOUTH_X : position.x + SIDES * pivotX() - MOUTH_X; }
    /** The firing line's middle, {@link #GOB_HEIGHT} px above the floor line (Y down: a smaller Y). */
    public float lineY() { return floorY() - GOB_HEIGHT; }

    @Override
    public void revisit(Player nextPlayer) {
        super.revisit(nextPlayer);
        // A snail left behind returns calm at its spawn, with no gob in the air (D3, D6).
        position.set(spawnX, spawnY);
        velocity.setZero();
        moveDirection = -1;
        facingRight = false;
        gobActive = gobHitPending = gone = false;
        reloadLeft = 0;
        enter(State.CRAWL);
    }

    private void enter(State state) {
        current = state;
        stateClock = frameClock = speckDue = swellDue = flameDue = 0;
        noticeStep = 0;
    }

    @Override
    public void update(float dt) {
        if (!game.isGameStarted() || isRemoved()) return;
        updateGob(dt);
        if (isRemoved()) return;
        if (gone) { if (!gobActive && !gobHitPending) removeFromParent(); return; }
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
        reloadLeft = Math.max(0, reloadLeft - dt);
        velocity.x = 0;
        switch (current) {
            case CRAWL -> crawl(dt);
            case PAUSE -> pause();
            case NOTICE -> notice();
            case WATCH -> watch(dt);
            case WINDUP -> windup(dt);
            case SPIT -> { if (track(dt) && stateClock >= SPIT_SECONDS) enter(State.RELOAD); }
            case RELOAD -> reload(dt);
            default -> { }
        }
        if (isRemoved()) return;
        position.x += velocity.x * dt;
        if (position.x < rangeNeg) position.x = rangeNeg;
        else if (position.x > rangePos) position.x = rangePos;
        checkHorizontalCollisions(level);
        applyGravity(dt);
        checkVerticalCollisions(level, dt);
    }

    private boolean heroPresent() { return player != null && !player.isRemoved() && player.canLeaveRoom(); }

    private void crawl(float dt) {
        if (heroPresent() && sees()) { startNotice(); return; }
        boolean atEnd = (moveDirection < 0 && position.x <= rangeNeg) || (moveDirection > 0 && position.x >= rangePos);
        if (atEnd && rangePos > rangeNeg) { enter(State.PAUSE); return; }
        facingRight = moveDirection > 0;
        velocity.x = moveDirection * PATROL_SPEED;
        frameClock += dt;
        while (frameClock >= CRAWL_FRAME_SECONDS) { frameClock -= CRAWL_FRAME_SECONDS; crawlFrame = !crawlFrame; }
    }

    /** At a range end: it stops, then turns round; it may still notice the hero ahead. */
    private void pause() {
        if (heroPresent() && sees()) { startNotice(); return; }
        if (stateClock < PAUSE_SECONDS) return;
        moveDirection = -moveDirection;
        facingRight = moveDirection > 0;
        enter(State.CRAWL);
    }

    /** It sees the hero: the notice starts, with its own call (D100). */
    private void startNotice() {
        enter(State.NOTICE);
        lostClock = behindClock = 0;   // awareness starts: the clocks of tracking run on through the spit and the reload
        if (game.playSounds()) game.playSound(NOTICE_SOUND);
    }

    private void notice() {
        float elapsed = 0;
        noticeStep = NOTICE_FRAMES.length - 1;
        for (int i = 0; i < NOTICE_SECONDS.length; i++) {
            elapsed += NOTICE_SECONDS[i];
            if (stateClock < elapsed) { noticeStep = i; break; }
        }
        if (stateClock >= NOTICE_TOTAL) enter(State.WATCH);
    }

    /** Aware and ready: it follows the hero round and fires once the hero's body is in its line. */
    private void watch(float dt) {
        if (!track(dt)) return;
        if (reloadLeft <= 0 && heroInLine()) commit();
    }

    private void reload(float dt) {
        if (!track(dt)) return;
        if (reloadLeft <= 0) enter(State.WATCH);
    }

    /**
     * Aware: a hero seen ahead keeps it watching; one behind it is followed with a turn after {@link #TURN_SECONDS}; one out of
     * sight for {@link #LOSE_SECONDS} is lost and it crawls on. False when it went back to its crawl.
     */
    private boolean track(float dt) {
        if (!heroPresent()) { lose(); return false; }
        if (sees()) { lostClock = behindClock = 0; return true; }
        if (seesBehind()) {
            behindClock += dt;
            lostClock = 0;
            if (behindClock >= TURN_SECONDS) { facingRight = !facingRight; behindClock = 0; }
            return true;
        }
        behindClock = 0;
        lostClock += dt;
        if (lostClock >= LOSE_SECONDS) { lose(); return false; }
        return true;
    }

    private void lose() {
        moveDirection = facingRight ? 1 : -1;
        enter(State.CRAWL);
    }

    /** It commits: the shared warning, sampled once, its facing locked; the spit follows whatever the hero does. */
    private void commit() {
        enter(State.WINDUP);
        TrapWarnings warnings = game.trapWarnings();
        warningTotal = warnings != null ? warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS) : TrapWarnings.DEFAULT_WARNING_SECONDS;
        if (game.playSounds()) game.playSound(warningTotal > TrapWarnings.DEFAULT_WARNING_SECONDS ? WINDUP_LONG_SOUND : WINDUP_SOUND);
    }

    private void windup(float dt) {
        float progress = Math.min(1f, stateClock / warningTotal);
        if (level != null) {
            float mouth = mouthX(), y = lineY();
            float far = lineEnd(facingRight);
            speckDue += SPECKS_PER_SECOND * dt;
            while (speckDue >= 1) { speckDue -= 1; level.snailFx().speck(mouth, far, y); }
            swellDue += SWELLS_PER_SECOND * dt;
            while (swellDue >= 1) { swellDue -= 1; level.snailFx().swell(mouth, y, progress); }
        }
        if (stateClock >= warningTotal) spit();
    }

    /** The act (D93): the gob leaves the mouth, straight and level, and the reload starts. */
    private void spit() {
        gobActive = true;
        gobFalling = gobHitPending = false;
        gobRight = facingRight;
        gobX = mouthX();
        gobY = lineY();
        gobFloor = floorY();
        gobVy = gobFlown = gobClock = gobDripDue = 0;
        reloadLeft = RELOAD_SECONDS;
        enter(State.SPIT);
        if (game.playSounds()) game.playSound(SPIT_SOUND);
    }

    // ---- sight and the firing line -------------------------------------------------------------------------------------

    /** The x where the firing line ends facing that way: the first solid at the gob's height, or {@link #GOB_RANGE} px out. */
    private float lineEnd(boolean right) {
        float start = mouthX(), dir = right ? 1 : -1, y = lineY() - GOB_CORE / 2f;
        if (level == null) return start + dir * GOB_RANGE;
        for (float d = 0; d < GOB_RANGE; d += TRACE_STEP) {
            float x = start + dir * d;
            if (level.blocksObject(boxScratch.set(right ? x : x - GOB_CORE, y, GOB_CORE, GOB_CORE), false)) return x;
        }
        return start + dir * GOB_RANGE;
    }

    /** The sight box ahead that way, from the front edge to the line's end, from the floor line up {@link #SIGHT_BAND} px. */
    private boolean seesTowards(boolean right) {
        Rectangle body = hitboxBoundsInto(lineScratch);
        float front = right ? body.x + body.width : body.x;
        float end = lineEndFrom(front, right);
        float floor = floorY();
        lineScratch.set(Math.min(front, end), floor - SIGHT_BAND, Math.abs(end - front), SIGHT_BAND);
        return lineScratch.overlaps(player.hitboxBoundsInto(heroScratch));
    }

    /** As {@link #lineEnd} but from {@code from} (the body's edge), the way it faces or behind it. */
    private float lineEndFrom(float from, boolean right) {
        float dir = right ? 1 : -1, y = lineY() - GOB_CORE / 2f;
        if (level == null) return from + dir * SIGHT_RANGE;
        for (float d = 0; d < SIGHT_RANGE; d += TRACE_STEP) {
            float x = from + dir * d;
            if (level.blocksObject(boxScratch.set(right ? x : x - GOB_CORE, y, GOB_CORE, GOB_CORE), false)) return x;
        }
        return from + dir * SIGHT_RANGE;
    }

    private boolean sees() { return seesTowards(facingRight); }
    private boolean seesBehind() { return seesTowards(!facingRight); }

    /** The firing line ahead, as a band {@link #GOB_CORE} px tall around {@link #lineY()}, written into {@code out}. */
    public Rectangle lineInto(Rectangle out) {
        float mouth = mouthX(), end = lineEnd(facingRight);
        return out.set(Math.min(mouth, end), lineY() - GOB_CORE / 2f, Math.abs(end - mouth), GOB_CORE);
    }

    private boolean heroInLine() { return lineInto(lineScratch).overlaps(player.hitboxBoundsInto(heroScratch)); }

    // ---- the gob ---------------------------------------------------------------------------------------------------

    /** The gob in flight: a pending hit lands first (the hit of last frame; a blade since would have cut it), then it moves. */
    private void updateGob(float dt) {
        if (gobHitPending) {
            gobHitPending = false;
            if (gobActive) {
                if (heroPresent()) player.collidedWithActor(false);
                endGob(gobRight ? -1 : 1, false);
            }
        }
        if (!gobActive || level == null) return;
        gobClock += dt;
        int steps = ProjectileStep.count(dt);
        float step = ProjectileStep.frameSeconds(dt) / steps, dir = gobRight ? 1 : -1;
        for (int i = 0; i < steps && gobActive; i++) {
            float fromX = gobX, fromY = gobY, dx = dir * GOB_SPEED * step;
            gobX += dx;
            gobFlown += Math.abs(dx);
            if (gobFalling) { gobVy += GOB_GRAVITY * step; gobY += gobVy * step; }
            for (gobDripDue += Math.abs(dx); gobDripDue >= DRIP_SPACING; gobDripDue -= DRIP_SPACING) level.snailFx().drip(gobX - dir * GOB_CORE, gobY, gobFloor);
            float half = GOB_CORE / 2f, left = Math.min(fromX, gobX) - half;
            Rectangle swept = gobScratch.set(left, gobY - half, Math.abs(gobX - fromX) + GOB_CORE, GOB_CORE);
            if (level.blocksObject(swept, gobFalling)) {
                // A wall face stops it when the level move alone is blocked (one-way platforms never are); else it fell onto floor.
                boolean wall = !gobFalling || level.blocksObject(gobScratch.set(left, fromY - half, Math.abs(gobX - fromX) + GOB_CORE, GOB_CORE), false);
                if (wall) { gobX = fromX; gobY = fromY; endGob(-dir, true); }
                else endGob(-dir, false);
                return;
            }
            if (heroPresent() && !player.isGotHit() && swept.overlaps(player.hitboxBoundsInto(heroScratch))) { gobHitPending = true; return; }
            if (!gobFalling && gobFlown >= GOB_RANGE) { gobFalling = true; gobVy = 0; }
            Rectangle room = level.currentRoomBounds();
            // The load check keeps every drop on floor inside the room. Should a future map let one fall out, leaving the room counts
            // as blocked above (Level.blocksObject), so it splats at the bottom edge; this is the backstop, never a silent end (D93).
            if (gobY > room.y + room.height) { gobY = room.y + room.height; endGob(-dir, false); return; }
        }
    }

    /** The gob ends where it is, hit or miss (D93): its splat, thrown back the way {@code away} points, and its sound. */
    private void endGob(float away, boolean onWall) {
        gobActive = gobHitPending = false;
        if (level != null) level.snailFx().splat(onWall ? gobX + (away < 0 ? GOB_CORE / 2f : -GOB_CORE / 2f) : gobX, gobY + (onWall ? 0 : GOB_CORE / 2f), away, onWall);
        if (game.playSounds()) game.playSound(SPLAT_SOUND);
    }

    @Override public boolean isBreakable() { return gobActive; }

    @Override public Rectangle breakableInto(Rectangle out) {
        return out.set(gobX - GOB_BREAK_WIDTH / 2f, gobY - GOB_BREAK_HEIGHT / 2f, GOB_BREAK_WIDTH, GOB_BREAK_HEIGHT);
    }

    /** A weapon reaches the gob: the blade or a stone breaks it apart, fire steams it away; the weapon flies on (D106). */
    @Override public void breakApart(boolean fire) {
        if (!gobActive) return;
        gobActive = gobHitPending = false;
        float away = gobRight ? -1 : 1;
        if (level != null) {
            if (fire) level.snailFx().steam(gobX, gobY, away);
            else level.snailFx().splat(gobX, gobY, away, false);
        }
        if (game.playSounds()) game.playSound(SPLAT_SOUND);
    }

    // ---- death and the body (D101) -------------------------------------------------------------------------------------

    private void poof() {
        poofDone = true;
        float floor = floorY();
        if (level != null) level.snailFx().poof(centreX(), floor - BODY_CENTRE_ABOVE_FLOOR, floor);
    }

    private boolean fall(float dt) {
        if (level == null) return false;
        velocity.x = 0;
        applyGravity(dt);
        checkVerticalCollisions(level, dt);
        Rectangle room = level.currentRoomBounds();
        if (position.y <= room.y + room.height) return false;
        finish();
        return true;
    }

    /** The body is gone: at once, or, while its gob still flies, once the gob has ended (the gob flies on, D106). */
    private void finish() {
        if (gobActive || gobHitPending) gone = true;
        else removeFromParent();
    }

    private void lie(float dt) {
        stateClock += dt;
        if (heroPresent() && stompedOn()) { player.velocity.y = -BOUNCE_HEIGHT; crumble(); return; }
        boolean near = heroPresent() && heroNear();
        if (near && !heroNear) push();
        heroNear = near;
        swing.update(dt);
    }

    private boolean heroNear() {
        Rectangle body = hitboxBoundsInto(nearScratch);
        body.set(body.x - NEAR_REACH, body.y, body.width + SIDES * NEAR_REACH, body.height);
        return body.overlaps(player.hitboxBoundsInto(heroScratch));
    }

    private boolean stompedOn() {
        Rectangle body = hitboxBoundsInto(nearScratch);
        return body.overlaps(player.hitboxBoundsInto(heroScratch)) && player.velocity.y > 0
            && player.previousFeetY() <= body.y + LANDING_TOLERANCE;
    }

    private void push() {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float direction = player.velocity.x != 0 ? Math.signum(player.velocity.x) : hero.x + hero.width / 2f < centreX() ? 1 : -1;
        swing.push(direction);
    }

    private void burn(float dt) {
        stateClock += dt;
        float cx = centreX(), floor = floorY();
        flameDue += BURN_FLAMES_PER_SECOND * dt;
        while (flameDue >= 1) { flameDue -= 1; if (level != null) level.remainsFx().burnFlame(cx, floor); }
        if (stateClock >= BURN_SECONDS) {
            if (level != null) level.remainsFx().burnOut(cx, floor);
            finish();
        }
    }

    @Override public boolean isRemains() { return current == State.DEAD && !gone && !isRemoved(); }
    @Override public Rectangle remainsInto(Rectangle out) { return hitboxBoundsInto(out); }

    @Override
    public void crumble() {
        if (!isRemains()) return;
        if (!poofDone) poof();
        if (level != null) level.remainsFx().crumble(centreX(), floorY(), com.jlogicsoftware.kimeria.effects.SnailFx.SNAIL_CHIPS);
        if (game.playSounds()) game.playSound(CRUMBLE_SOUND);
        finish();
    }

    @Override
    public void burnUp() {
        if (!isRemains()) return;
        if (!poofDone) poof();
        enter(State.BURN);
        if (level != null) level.remainsFx().burnGlow(centreX(), floorY(), BURN_SECONDS);
        if (game.playSounds()) game.playSound(BURN_SOUND);
    }

    /** A fireball, a thrown stone or torch ({@code gotHit}) kills it in any state; a stomp kills it with the bounce; touch is harmless. */
    @Override
    public void collidedWithActor(boolean gotHit) {
        if (isDefeated()) return;
        if (gotHit) { die(); return; }
        if (!stompedByPlayer()) return; // touch is harmless (D106)
        player.velocity.y = -BOUNCE_HEIGHT;
        die();
    }

    /** The sword kills it in any state, with the light spray of D65 (through {@link Enemy#hitBySword}); on the body, it crumbles it. */
    @Override
    public void hitBySword(Level level) {
        if (isRemains()) { crumble(); return; }
        super.hitBySword(level);
    }

    /** Killed: it rolls onto its back and stays as the empty shell (D101). Killed winding up, its gob pops on its lips. */
    private void die() {
        if (current == State.WINDUP) {
            if (level != null) level.snailFx().splat(mouthX(), lineY(), facingRight ? -1 : 1, false);
            if (game.playSounds()) game.playSound(SPLAT_SOUND);
        }
        enter(State.DEAD);
        pendingRemoval = true;
        poofDone = heroNear = false;
        swing.reset();
        deadClock = 0;
        velocity.setZero();
        if (game.playSounds()) game.playSound(DEATH_SOUND);
    }

    @Override
    protected void setDefeated() { die(); }

    // ---- drawing -------------------------------------------------------------------------------------------------

    int frameIndex() {
        return switch (current) {
            case CRAWL -> crawlFrame ? CRAWL2 : CRAWL1;
            case PAUSE -> BLINK;
            case NOTICE -> NOTICE_FRAMES[noticeStep];
            case WATCH -> LOOK;
            case WINDUP -> DRAW;
            case SPIT -> SPIT_FRAME;
            case RELOAD -> ((int) (stateClock / SWALLOW_SECONDS)) % SIDES == 0 ? REST : BLINK;
            case DEAD, BURN -> DEAD_FRAME;
        };
    }

    public float bodyFade() {
        if (current != State.DEAD && current != State.BURN) return 0;
        return Math.min(1f, Math.max(0f, (deadClock - BODY_FADE_DELAY) / BODY_FADE_SECONDS));
    }

    @Override
    public void render(Batch batch) {
        if (!gone) {
            if (current == State.DEAD || current == State.BURN) renderBody(batch);
            else {
                TextureRegion frame = frames[frameIndex()];
                if (facingRight) batch.draw(frame, position.x, position.y, FRAME_WIDTH, FRAME_HEIGHT);
                else batch.draw(frame, position.x + SIDES * pivotX(), position.y, -FRAME_WIDTH, FRAME_HEIGHT);
            }
        }
        if (gobActive) {
            TextureRegion gob = gobFrames[((int) (gobClock / GOB_WOBBLE_SECONDS)) % GOB_FRAMES];
            float x = gobX - GOB_DRAW / 2f, y = gobY - GOB_DRAW / 2f;
            if (gobRight) batch.draw(gob, x, y, GOB_DRAW, GOB_DRAW);
            else batch.draw(gob, x + GOB_DRAW, y, -GOB_DRAW, GOB_DRAW);
        }
    }

    private void renderBody(Batch batch) {
        float lift = deadClock < FLIP_SECONDS ? FLIP_LIFT * MathUtils.sin(MathUtils.PI * deadClock / FLIP_SECONDS) : 0;
        float drawX = facingRight ? position.x : position.x + SIDES * (pivotX() - PIVOT_X), y = position.y - lift;
        float scaleX = facingRight ? 1 : -1;
        float saved = batch.getPackedColor();
        float fade = bodyFade();
        float tint = current == State.BURN ? 1f - (1f - CHAR_TINT) * Math.min(1f, stateClock / BURN_SECONDS) : 1f;
        if (fade < 1) {
            batch.setColor(tint, tint, tint, 1f - fade);
            batch.draw(frames[DEAD_FRAME], drawX, y, PIVOT_X, PIVOT_Y, FRAME_WIDTH, FRAME_HEIGHT, scaleX, 1, swing.angle());
        }
        if (fade > 0) {
            batch.setColor(tint, tint, tint, fade);
            batch.draw(bodyFrame, drawX, y, PIVOT_X, PIVOT_Y, FRAME_WIDTH, FRAME_HEIGHT, scaleX, 1, swing.angle());
        }
        batch.setPackedColor(saved);
    }

    // ---- load-time checks (D106) -----------------------------------------------------------------------------------

    /**
     * The load-time checks (D106), on the 24x16 snail whatever the Tiled object's size: its range inside the room on floor without
     * gaps; and every firing line, from every point of its range facing either way, ends on a solid face or on floor inside the
     * room and below the camera's top ({@code cameraTop}), and crosses no standing place (the entrance body, the room exit, a
     * checkpoint). {@code floors}: every collision box a gob can land on; {@code solids}: those that stop it in level flight (not
     * one-way platforms). Null when the snail is fine, else what is wrong.
     */
    public static String problem(float x, float y, float w, float h, float offNeg, float offPos, List<Rectangle> floors,
                                 List<Rectangle> solids, Rectangle room, float cameraTop, Map<String, Rectangle> standing) {
        if (!(w > 0) || !(h > 0)) return "needs positive bounds";
        if (!Float.isFinite(offNeg) || !Float.isFinite(offPos) || offNeg < 0 || offPos < 0) return "needs finite, non-negative offNeg and offPos";
        float floor = y + h, low = x - offNeg * TILE_SIZE, high = x + offPos * TILE_SIZE;
        if (low < room.x || high + FRAME_WIDTH > room.x + room.width || floor - FRAME_HEIGHT < room.y || floor > room.y + room.height)
            return "has its range leaving the room";
        float covered = low;
        List<Rectangle> spans = new ArrayList<>();
        for (Rectangle f : floors) if (Math.abs(f.y - floor) < 1f) spans.add(f);
        spans.sort((a, b) -> Float.compare(a.x, b.x));
        for (Rectangle f : spans) if (f.x <= covered + 1f) covered = Math.max(covered, f.x + f.width);
        if (covered < high + FRAME_WIDTH) return "must crawl on floor without gaps under its whole range";
        float lineTop = floor - GOB_HEIGHT - GOB_CORE / 2f;
        if (lineTop < cameraTop) return "has its firing line above the camera's view of its room";
        float pivot = BODY_X + BODY_WIDTH / 2f;
        Rectangle box = new Rectangle(), path = new Rectangle();
        for (float px = low; px <= high + RANGE_SLACK; px += GOB_CORE) {
            for (int side = 0; side < SIDES; side++) {
                boolean right = side == 0;
                float dir = right ? 1 : -1, mouth = right ? px + MOUTH_X : px + SIDES * pivot - MOUTH_X;
                String end = traceLine(mouth, dir, lineTop, floors, solids, room, box, path, standing);
                if (end != null) return "fires " + (right ? "right" : "left") + " from x " + (int) px + " and " + end;
            }
        }
        return null;
    }

    /** One firing line from {@code mouth}: its level flight to a solid or {@link #GOB_RANGE}, then its drop to the floor. */
    private static String traceLine(float mouth, float dir, float lineTop, List<Rectangle> floors, List<Rectangle> solids,
                                    Rectangle room, Rectangle box, Rectangle path, Map<String, Rectangle> standing) {
        float flown = 0, x = mouth;
        boolean stopped = false;
        while (flown < GOB_RANGE) {
            x = mouth + dir * flown;
            box.set(dir > 0 ? x : x - GOB_CORE, lineTop, GOB_CORE, GOB_CORE);
            if (hits(box, solids)) { stopped = true; break; }
            if (box.x < room.x || box.x + box.width > room.x + room.width) return "its gob leaves the room";
            flown += TRACE_STEP;
        }
        path.set(Math.min(mouth, x), lineTop, Math.abs(x - mouth), GOB_CORE);
        String crossed = crosses(path, standing);
        if (crossed != null) return "its gob crosses the room " + crossed;
        if (stopped) return null;
        // The drop: level speed on, falling at GOB_GRAVITY, until the core touches floor.
        float gy = lineTop, vy = 0, startX = x, dt = 1f / DROP_TRACE_RATE;
        for (int i = 0; i < DROP_TRACE_STEPS; i++) {
            vy += GOB_GRAVITY * dt;
            gy += vy * dt;
            x += dir * GOB_SPEED * dt;
            box.set(dir > 0 ? x : x - GOB_CORE, gy, GOB_CORE, GOB_CORE);
            if (box.x < room.x || box.x + box.width > room.x + room.width || gy + GOB_CORE > room.y + room.height)
                return "its gob falls out of the room";
            if (hits(box, solids) || hits(box, floors)) {
                path.set(Math.min(startX, x), lineTop, Math.abs(x - startX) + GOB_CORE, gy + GOB_CORE - lineTop);
                crossed = crosses(path, standing);
                return crossed != null ? "its gob falls on the room " + crossed : null;
            }
        }
        return "its gob never reaches a floor";
    }

    private static boolean hits(Rectangle box, List<Rectangle> rects) {
        for (Rectangle r : rects) if (r.overlaps(box)) return true;
        return false;
    }

    private static String crosses(Rectangle path, Map<String, Rectangle> standing) {
        for (Map.Entry<String, Rectangle> place : standing.entrySet()) if (path.overlaps(place.getValue())) return place.getKey();
        return null;
    }

    @Override public Hitbox hitbox() { return hitbox; }
    @Override public boolean isClambering() { return clambering; }
    @Override public void setClambering(boolean value) { clambering = value; }
    @Override public boolean isInQuickSand() { return inQuickSand; }
    @Override public void setInQuickSand(boolean value) { inQuickSand = value; }
    @Override public Escalator currentEscalator() { return currentEscalator; }
    @Override public void setCurrentEscalator(Escalator escalator) { currentEscalator = escalator; }
}
