package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.RemainsFx;
import com.jlogicsoftware.kimeria.effects.SpiderFx;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The forest spider (#328, D102; climbing and the silk drop #483, D103). It lives on a {@link Route}: floors, the walls between
 * them, and a silk thread hanging from a lip at either end of the route. It skitters along the route, hears the hero in a
 * circle (blocked by nothing), notices in body language (it rises and drums a foreleg), and chases to the point of its route
 * nearest the hero. It strikes in two ways only, both after the shared bite warning ({@link TrapWarnings#BITE_WARNING_SECONDS},
 * longer with the assist) with silk glints running along the strike line: on a floor it locks onto where the hero stands, leaps
 * there and bites (D102); at a lip it hangs head down over the edge, drops down its thread and bites at the thread's end (D103),
 * then hangs harmless and reels back up. On a wall it never strikes. Its body never hurts: only the fang box kills, during the
 * leap, the drop and the bites. A bite that reaches the hero is resolved at the start of the spider's next frame, so a kill
 * that lands on the same frame (the sword, a stomp, a fireball or a stone) wins. Every weapon kills it in any state, quietly: it
 * leaves its body (D101, {@link Remains}), on its back with its legs curled up, on the floor below wherever it died; a thread it
 * hung from snaps. A sword kill sprays the light blood of D65. Its effects are {@link SpiderFx} and, for the body's end,
 * {@link RemainsFx}; its sprites are the floor strip ({@link SpriteSheets#MONSTER_SPIDER}, with the hanging frames and the thread
 * tile) and the wall strip ({@link SpriteSheets#MONSTER_SPIDER_WALL}). World Y grows downward: the floor line is the body's
 * bottom edge.
 */
public class Spider extends Enemy<Spider.State> implements CollideBody, Remains {
    /**
     * {@code CRAWL}, {@code NOTICE} and {@code CHASE} happen on floors and walls; {@code WINDUP} to {@code RECOVER} are the floor
     * pounce (D102); {@code SET} (the drop's wind-up at the lip), {@code DROP}, {@code DANGLE} (the bite at the thread's end),
     * {@code HANG} and {@code REEL} are the silk drop (D103); {@code DEAD}: killed, the body lying (D101); {@code BURN}: burning up.
     */
    public enum State { CRAWL, NOTICE, CHASE, WINDUP, LEAP, BITE, RECOVER, SET, DROP, DANGLE, HANG, REEL, DEAD, BURN }

    public static final float FRAME_WIDTH = 32, FRAME_HEIGHT = 16;
    /** The body hitbox in the 32x16 frame: the drawn body is x 3..21, y 3..15 (abdomen, head and fangs); the legs reach past it. */
    private static final float BODY_X = 2, BODY_Y = 3, BODY_WIDTH = 20, BODY_HEIGHT = 13;
    /** The fang box, inside the drawn head and fangs: in the leap frame (head up) and on the floor (bite). */
    private static final Hitbox LEAP_FANGS = Hitbox.rect(17, 3, 6, 8), BITE_FANGS = Hitbox.rect(17, 7, 6, 9);
    /** The pounce box: from the body's middle to {@link #POUNCE_REACH} px past its front, from the floor line up to its top + 8. */
    public static final float POUNCE_REACH = 48;
    private static final float POUNCE_ABOVE = 8;
    private static final Hitbox POUNCE = Hitbox.rect(BODY_X + BODY_WIDTH / 2, BODY_Y - POUNCE_ABOVE,
        BODY_WIDTH / 2 + POUNCE_REACH, BODY_HEIGHT + POUNCE_ABOVE);
    /** Where the fangs' middle sits, from {@code position.x}, facing right and facing left (mirrored about the body's middle). */
    private static final float FANG_RIGHT = 20, FANG_LEFT = 2 * (BODY_X + BODY_WIDTH / 2) - FANG_RIGHT;
    /** The furthest the fangs can reach past a range end: the leap's reach, the fangs' middle and half their width (#497). */
    private static final float FANG_EXTENT = POUNCE_REACH + FANG_RIGHT + BITE_FANGS.width / 2;
    /** Left and right: the mirror spans twice the pivot (Actor.render), a reach counts on both sides. */
    private static final int SIDES = 2;
    private static final int CRAWL1 = 0, CRAWL2 = 1, RISE = 2, TAP = 3, CROUCH = 4, LEAP_FRAME = 5, BITE_FRAME = 6, RECOVER_FRAME = 7,
        DEAD_FRAME = 8, HANG_FRAME = 9, HANG_WIND_FRAME = 10, SILK_FRAME = 11, FRAMES = 12;

    /** The wall strip (D103): 16x32, head up, the wall on its right; the body inside it, and the frames. */
    public static final float WALL_WIDTH = 16, WALL_HEIGHT = 32;
    private static final float WALL_BODY_X = 7, WALL_BODY_Y = 7, WALL_BODY_WIDTH = 8, WALL_BODY_HEIGHT = 21;
    private static final int WALL1 = 0, WALL2 = 1, WALL_TAP = 2, WALL_FRAMES = 3;
    /** On a wall it rises this far off the face for the notice's {@code rise} beats. */
    private static final float WALL_RISE = 1;

    /**
     * Hanging on its thread (D103): the 32x16 hang frames are drawn upright, head down, mirrored about the thread, which leaves
     * the spinnerets at x {@link #HANG_THREAD_X} of the frame's top row. The body and the fang box inside that frame.
     */
    private static final float HANG_THREAD_X = 15f, HANG_BODY_X = 10f, HANG_BODY_Y = 0, HANG_BODY_WIDTH = 12f, HANG_BODY_HEIGHT = 14;
    private static final float HANG_FANG_X = 12, HANG_FANG_Y = 10, HANG_FANG_WIDTH = 8, HANG_FANG_HEIGHT = 6;
    /** The fangs' middle: half a pixel right of the thread's column, between the mirrored frame's columns 15 and 16. */
    private static final float THREAD_MIDDLE = .5f;
    /**
     * The hanging body clears the ledge's face by this many px on either side of a lip (D103). The thread leaves the frame left
     * of its mirror axis, so it hangs {@link #threadOut} px out: 6 from a lip on the right of its floor, 8 from one on the left.
     */
    public static final float HANG_CLEARANCE = 1f;

    /** How far out from its lip a thread hangs, so the hanging body clears the ledge's face by {@link #HANG_CLEARANCE} either side. */
    public static float threadOut(boolean lipOnRight) {
        return HANG_CLEARANCE + (lipOnRight ? HANG_THREAD_X - HANG_BODY_X : HANG_BODY_X + HANG_BODY_WIDTH - HANG_THREAD_X);
    }
    /** The thread's end leaves the hanging frame's bottom (the fang tips) this far above the floor below. */
    public static final float HANG_GAP = 4f;
    /** The longest drop column, from the lip to the floor below. */
    public static final float THREAD_MAX = 112;
    /** The hero needs this much floor beside the drop column, on each side, to step out of it (D103 load check). */
    public static final float DROP_ESCAPE_FLOOR = 16;
    /** The drop: this fast (px/s); the reel back up: this fast; a 3 px bob on the silk's stretch when it arrives. */
    public static final float DROP_SPEED = 384, REEL_SPEED = 64;
    private static final float BOB = 3, BOB_SECONDS = .2f, SHED_STEP = 6;
    /** A snapped thread's stub fades over this long (D103). */
    public static final float SNAP_SECONDS = .4f;

    /** The skitter: this fast for {@link #SKITTER_MOVE} s, then still for {@link #SKITTER_STILL} s (24 px/s on average). */
    public static final float SKITTER_SPEED = 48, SKITTER_MOVE = .3f, SKITTER_STILL = .3f;
    /** The chase (px/s); the hero runs 100, so running away always works. Up a wall it climbs slower (D103). */
    public static final float CHASE_SPEED = 85, CLIMB_SPEED = 64;
    private static final float SKITTER_FRAME_SECONDS = .1f, CHASE_FRAME_SECONDS = .05f;
    /** Hearing (D103): the hero's centre within this circle around the body's centre, in the spider's room; nothing blocks it. */
    public static final float HEAR_RADIUS = 144;
    /** It loses the hero after this long beyond this distance. */
    public static final float LOSE_RADIUS = 192, LOSE_SECONDS = 1f;
    /** The notice in body language: it rises, drums a raised foreleg twice and settles (0.5 s, not scaled by the assist). */
    private static final int[] NOTICE_FRAMES = {RISE, TAP, RISE, TAP, RISE, CRAWL1};
    private static final float[] NOTICE_SECONDS = {.1f, .08f, .08f, .08f, .08f, .08f};
    public static final float NOTICE_TOTAL;
    static {
        float total = 0;
        for (float s : NOTICE_SECONDS) total += s;
        NOTICE_TOTAL = total;
    }
    /** The wind-up: drawn this many px back, away from the hero (up, at the lip); silk glints per second, running to the spot. */
    private static final float DRAW_BACK = 1, SILK_PER_SECOND = 60;
    /** The leap: this long, on a low arc this high (the body really moves); one silk-line dot per {@code LINE_STEP} px flown. */
    public static final float LEAP_SECONDS = .25f, LEAP_ARC = 6, LINE_STEP = 2;
    /** Where the silk line leaves the body: the abdomen's tip, facing right, and its height in the frame. */
    private static final float LINE_X = 3, LINE_Y = 8;
    /** The bite frame holds this long with the fangs lethal; then the recovery, flat and harmless, not scaled by the assist. */
    public static final float BITE_SECONDS = .1f, RECOVER_SECONDS = .6f;
    /** The death's poof starts this long after the hit, once a fireball's own flash is spent (as the beetle's, D101). */
    public static final float POOF_DELAY = .08f;
    private static final float FLIP_LIFT = 4, FLIP_SECONDS = .2f;
    /** The poof's middle: this far above the floor line. */
    private static final float BODY_CENTRE_ABOVE_FLOOR = 7;
    /** The active {@code dead} frame holds this long, then cross-fades into the decor body over the second (D76, D101). */
    public static final float BODY_FADE_DELAY = .3f, BODY_FADE_SECONDS = .6f;
    /** The body rocks when the hero's body enters its box grown this many px left and right (D101). */
    public static final float NEAR_REACH = 6;
    public static final float KICK_DEGREES = RemainsSwing.KICK_DEGREES;
    /** The pivot, in the 32x16 frame facing right: the middle of the bottom of the upturned belly. */
    private static final float PIVOT_X = 11, PIVOT_Y = FRAME_HEIGHT;
    /** The burn: the body darkens to this tint over the burn, with flames at this rate, then a wisp; it is gone at the end. */
    public static final float BURN_SECONDS = .3f;
    private static final float BURN_FLAMES_PER_SECOND = 70, CHAR_TINT = .15f;
    /** A hero falls onto the body when its feet were this close above the body's top on the previous frame (as a stomp). */
    private static final float LANDING_TOLERANCE = 2;
    /** The leap's reach is tested in steps of this many px, and floor under the body every {@code FLOOR_PROBE} px of it (#497). */
    private static final float REACH_STEP = 2, FLOOR_PROBE = 4;
    /** A position counts as the end of a leg within this many px. */
    private static final float AT_END = .01f;

    static final String NOTICE_SOUND = "spider_notice", WINDUP_SOUND = "spider_windup", WINDUP_LONG_SOUND = "spider_windup_long",
        LEAP_SOUND = "spider_leap", BITE_SOUND = "spider_bite", DEATH_SOUND = "bounce";
    /** D103: the drop, the reel back up and a thread snapping when its spider is killed. */
    static final String DROP_SOUND = "spider_drop", REEL_SOUND = "spider_reel", SNAP_SOUND = "spider_snap";
    /** D101: shared by every monster's body; the rocking is silent. */
    public static final String CRUMBLE_SOUND = "remains_crumble", BURN_SOUND = "remains_burn";

    private final TextureRegion[] frames = new TextureRegion[FRAMES], wallFrames = new TextureRegion[WALL_FRAMES];
    private final TextureRegion bodyFrame, silkTile, silkTexel;
    private final Route route;
    private final Rectangle pounceScratch = new Rectangle(), fangScratch = new Rectangle(), heroScratch = new Rectangle(),
        nearScratch = new Rectangle(), dropScratch = new Rectangle(), selfScratch = new Rectangle();
    private final RemainsSwing swing = new RemainsSwing();
    private Level level;
    private boolean clambering, inQuickSand;
    private Escalator currentEscalator;
    private float stateClock, frameClock, outOfReach, silkDue, warningTotal, flameDue, deadClock;
    /** The leap: from here to there, on the floor line it took off from; the fangs' x last frame (the swept bite). */
    private float leapFromX, leapToX, leapFloorY, lastFangX, lineFlown;
    private boolean stoppedBySolid;
    private float solidStop;
    private boolean crawlFrame, heroNear, poofDone, bitePending;
    /**
     * One bite per leap or drop: set when the fangs reach the hero, cleared when the next strike starts. A hero the bite cannot
     * kill (the developer's invulnerability, the victory walk) is not bitten again and again, and the spider always goes on.
     */
    private boolean bitThisLeap;
    private int noticeStep;
    /** Where it is on its route: the leg, which way it patrols (+1 towards the route's end), and its head on a wall. */
    private int leg, patrolDir, heading;
    private boolean headUp;
    /** The chase's goal on its route this frame: the leg and the coordinate on it. */
    private int targetLeg;
    private float targetU;
    /** The thread in use (0 at the route's start, 1 at its end), the drop's fangs' top last frame, and the shed specks. */
    private int dropSide;
    private float lastFangY, shedFlown;
    /** A snapped thread: where it hung, and how long ago it snapped (it fades over {@link #SNAP_SECONDS}); -1 for none. */
    private float snapX, snapTop, snapBottom, snapClock = -1;

    public Spider(Assets assets, GameContext game, float x, float y, float w, float h, float offNeg, float offPos) {
        this(assets, game, x, y, w, h, Route.floor(x, y, offNeg, offPos));
    }

    /** A spider on a traced route (D103): it spawns at the route's start (the first point of its first floor or wall), heading along it. */
    public Spider(Assets assets, GameContext game, Route route) {
        this(assets, game, 0, 0, FRAME_WIDTH, FRAME_HEIGHT, route); // spawn() puts it on the route
    }

    private Spider(Assets assets, GameContext game, float x, float y, float w, float h, Route route) {
        super(assets, game, "spider", x, y, w, h, 0, 0, State.class);
        this.route = route;
        hitbox = Hitbox.rect(BODY_X, BODY_Y, BODY_WIDTH, BODY_HEIGHT);
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.MONSTER_SPIDER.frame(assets, i, 0);
        for (int i = 0; i < WALL_FRAMES; i++) wallFrames[i] = SpriteSheets.MONSTER_SPIDER_WALL.frame(assets, i, 0);
        bodyFrame = SpriteSheets.MONSTER_SPIDER_BODY.frame(assets, 0, 0);
        float silkX = SILK_FRAME * FRAME_WIDTH;
        silkTile = assets.region(SpriteSheets.MONSTER_SPIDER.path(), silkX, 0, 1, Route.SILK_TILE);
        silkTexel = assets.region(SpriteSheets.MONSTER_SPIDER.path(), silkX, 0, 1, 1);
        spawn();
    }

    /** Back at the spawn, calm: the route's start, or the authored spot of a floor-only spider, facing left as D102's. */
    private void spawn() {
        enterLeg(0, true);
        if (route.legacy) position.x = route.spawnU;
        velocity.setZero();
        patrolDir = route.legacy ? -1 : 1;
        moveDirection = -1;
        facingRight = false;
        headUp = true;
        current = State.CRAWL;
    }

    public void setLevel(Level level) { this.level = level; }

    public float rangeNeg() { return rangeNeg; }
    public float rangePos() { return rangePos; }
    public State state() { return current; }
    public Route route() { return route; }
    /** The leg of its route it is on; true while it is on a wall (crawling, noticing or chasing there). */
    public int leg() { return leg; }
    public boolean onWall() { return isWallPose(); }
    public boolean headUp() { return headUp; }
    public float warningTotal() { return warningTotal; }
    /** Seconds of the wind-up left (0 outside it): the pounce's or the drop's. */
    public float warningRemaining() {
        return current == State.WINDUP || current == State.SET ? Math.max(0, warningTotal - stateClock) : 0;
    }
    public boolean isCommitted() {
        return current == State.WINDUP || current == State.LEAP || current == State.BITE
            || current == State.SET || current == State.DROP || current == State.DANGLE;
    }
    /** Where the leap will put {@code position.x} (the locked target), valid from the commit until it lands. */
    public float leapTarget() { return leapToX; }
    /** The body's swing now, in degrees (0 at rest; positive: its top leans right). */
    public float rockAngle() { return swing.angle(); }
    /** The thread's x, and where its spinnerets are now (the thread's lower end), while it hangs on its thread. */
    public float threadX() { return route.threadX[dropSide]; }
    public float spinneretY() { return position.y; }
    /** True while a snapped thread is still fading (D103). */
    public boolean threadSnapping() { return snapClock >= 0 && snapClock < SNAP_SECONDS; }

    /** The floor line the spider stands on: the bottom of its body (on a floor). */
    public float floorY() { return position.y + hitbox.offsetY + hitbox.height; }
    /** The middle of the body, horizontally, in whatever pose it is in. */
    public float centreX() { Rectangle body = hitboxBoundsInto(bodyScratch); return body.x + body.width / 2f; }
    private float centreY() { Rectangle body = hitboxBoundsInto(bodyScratch); return body.y + body.height / 2f; }
    /** The fangs' middle, horizontally (on a floor). */
    public float fangX() { return position.x + (facingRight ? FANG_RIGHT : FANG_LEFT); }

    /** The pounce box (the hero's body touching it makes the spider commit), written into {@code out}. */
    public Rectangle pounceBoxInto(Rectangle out) { return boundsInto(POUNCE, out); }

    /** The fang box now (null outside the leap, the bite, the drop and the bite on the thread), written into {@code out}. */
    public Rectangle fangBoxInto(Rectangle out) {
        if (current == State.LEAP) return boundsInto(LEAP_FANGS, out);
        if (current == State.BITE) return boundsInto(BITE_FANGS, out);
        if (current == State.DROP || current == State.DANGLE) return hangFangsInto(out);
        return null;
    }

    /** The drop box of the thread on this side (0: the route's start, 1: its end): the column its fangs sweep, lip to thread's end. */
    public Rectangle dropBoxInto(int side, Rectangle out) { return route.dropBoxInto(side, out); }

    private Rectangle hangFangsInto(Rectangle out) {
        return out.set(position.x + HANG_FANG_X, position.y + HANG_FANG_Y, HANG_FANG_WIDTH, HANG_FANG_HEIGHT);
    }

    private boolean isThreadPose() {
        return current == State.SET || current == State.DROP || current == State.DANGLE || current == State.HANG || current == State.REEL;
    }

    private boolean isWallPose() {
        return route.wall[leg] && (current == State.CRAWL || current == State.NOTICE || current == State.CHASE);
    }

    /** The body box follows the pose: on a floor the authored hitbox; on a wall and on the thread, the turned and hanging bodies. */
    @Override
    public Rectangle boundsInto(Hitbox box, Rectangle out) {
        if (box != hitbox) return super.boundsInto(box, out);
        if (isThreadPose()) return out.set(position.x + HANG_BODY_X, position.y + HANG_BODY_Y, HANG_BODY_WIDTH, HANG_BODY_HEIGHT);
        if (!isWallPose()) return super.boundsInto(box, out);
        float x = route.solidRight[leg] ? WALL_BODY_X : WALL_WIDTH - WALL_BODY_X - WALL_BODY_WIDTH;
        float y = headUp ? WALL_BODY_Y : WALL_HEIGHT - WALL_BODY_Y - WALL_BODY_HEIGHT;
        return out.set(position.x + x, position.y + y, WALL_BODY_WIDTH, WALL_BODY_HEIGHT);
    }

    @Override
    public void revisit(Player nextPlayer) {
        super.revisit(nextPlayer);
        // A spider left behind returns calm at its spawn: surviving enemies take their authored state (D3, D6).
        bitePending = bitThisLeap = false;
        snapClock = -1;
        enter(State.CRAWL);
        spawn();
    }

    private void enter(State state) {
        current = state;
        stateClock = frameClock = outOfReach = silkDue = flameDue = 0;
        noticeStep = 0;
    }

    @Override
    public void update(float dt) {
        if (!game.isGameStarted() || isRemoved()) return;
        if (current == State.DEAD || current == State.BURN) {
            deadClock += dt;
            if (snapClock >= 0) snapClock += dt;
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
        if (bitePending) { // last frame's bite: nothing killed the spider since, so it lands
            bitePending = false;
            if (heroPresent()) player.collidedWithActor(false);
        }
        stateClock += dt;
        velocity.x = 0;
        heading = 0;
        switch (current) {
            case CRAWL -> crawl(dt);
            case NOTICE -> notice();
            case CHASE -> chase(dt);
            case WINDUP -> windup(dt);
            case LEAP -> { leap(); return; } // a flight on its own arc: no gravity, no floor test until it lands
            case BITE -> bite();
            case RECOVER -> recover();
            case SET -> set(dt);
            case DROP -> drop(dt);
            case DANGLE -> dangle();
            case HANG -> hang();
            case REEL -> reel(dt);
            default -> { }
        }
        if (isThreadPose() || isWallPose()) return; // on its checked route: no gravity, no collisions (D103)
        float was = position.x;
        position.x += velocity.x * dt;
        clampToRange(was);
        checkHorizontalCollisions(level);
        applyGravity(dt);
        checkVerticalCollisions(level, dt);
        if (heading != 0 && atEnd(heading) && hasNext(heading)) enterLeg(leg + heading, heading > 0); // round a corner onto a wall
    }

    private boolean heroPresent() { return player != null && !player.isRemoved() && player.canLeaveRoom(); }

    // ---- the route ---------------------------------------------------------------------------------------------------

    /** +1 when going towards the route's end ({@code dir} +1) increases this leg's coordinate, else -1. */
    private int duSign(int dir) { return (dir > 0) == route.aLow[leg] ? 1 : -1; }

    private float coordinate() { return route.wall[leg] ? position.y : position.x; }

    private boolean atEnd(int dir) {
        float u = coordinate();
        return duSign(dir) > 0 ? u >= route.hi[leg] - AT_END : u <= route.lo[leg] + AT_END;
    }

    private boolean hasNext(int dir) { return dir > 0 ? leg + 1 < route.legs : leg > 0; }

    /** Onto leg {@code i} at its a end (true, coming from the previous leg) or its b end: a quarter-turn snap round the corner. */
    private void enterLeg(int i, boolean atA) {
        leg = i;
        float u = atA == route.aLow[i] ? route.lo[i] : route.hi[i];
        if (route.wall[i]) {
            position.set(route.solidRight[i] ? route.line[i] - WALL_WIDTH : route.line[i], u);
        } else {
            position.set(u, route.line[i] - FRAME_HEIGHT);
            rangeNeg = route.lo[i];
            rangePos = route.hi[i];
        }
        velocity.setZero();
    }

    /**
     * One step along the route, towards its end ({@code dir} +1) or its start: on a floor through the velocity (the physics moves
     * it, then it rounds a corner); on a wall directly, rounding the corner at the wall's end.
     */
    private void travel(int dir, float upSpeed, float downSpeed, float dt) {
        int du = duSign(dir);
        if (!route.wall[leg]) {
            velocity.x = du * downSpeed;
            facingRight = du > 0;
            moveDirection = du;
            heading = dir;
            return;
        }
        headUp = du < 0;
        position.y += du * (headUp ? upSpeed : downSpeed) * dt;
        if (du > 0 ? position.y >= route.hi[leg] : position.y <= route.lo[leg]) {
            position.y = du > 0 ? route.hi[leg] : route.lo[leg];
            if (hasNext(dir)) enterLeg(leg + dir, dir > 0);
        }
    }

    /** The leg and coordinate of the route point nearest the hero's centre (the current leg wins a tie), into the target fields. */
    private void aim() {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float hx = hero.x + hero.width / 2f, hy = hero.y + hero.height / 2f, best = Float.MAX_VALUE;
        for (int i = 0; i < route.legs; i++) {
            float u, cx, cy;
            if (route.wall[i]) {
                u = MathUtils.clamp(hy - WALL_HEIGHT / 2f, route.lo[i], route.hi[i]);
                cx = route.solidRight[i] ? route.line[i] - WALL_WIDTH + WALL_BODY_X + WALL_BODY_WIDTH / 2f
                    : route.line[i] + WALL_WIDTH - WALL_BODY_X - WALL_BODY_WIDTH / 2f;
                cy = u + WALL_HEIGHT / 2f;
            } else {
                u = MathUtils.clamp(hx - BODY_X - BODY_WIDTH / 2f, route.lo[i], route.hi[i]);
                cx = u + BODY_X + BODY_WIDTH / 2f;
                cy = route.line[i] - BODY_HEIGHT / 2f;
            }
            float d = (cx - hx) * (cx - hx) + (cy - hy) * (cy - hy);
            boolean better = i == leg ? d <= best + 1f : d < best - 1f; // the leg it is on wins a tie
            if (better) { best = d; targetLeg = i; targetU = u; }
        }
    }

    // ---- floor and wall: patrol, notice, chase ----------------------------------------------------------------------

    private void crawl(float dt) {
        if (heroPresent() && hears()) {
            faceHero();
            enter(State.NOTICE);
            if (game.playSounds()) game.playSound(NOTICE_SOUND);
            return;
        }
        if (atEnd(patrolDir) && !hasNext(patrolDir)) patrolDir = -patrolDir;
        int du = duSign(patrolDir);
        if (route.wall[leg]) headUp = du < 0;
        else { moveDirection = du; facingRight = du > 0; }
        if (stateClock % (SKITTER_MOVE + SKITTER_STILL) >= SKITTER_MOVE) return; // the still part of the skitter
        travel(patrolDir, SKITTER_SPEED, SKITTER_SPEED, dt);
        animateCrawl(dt, SKITTER_FRAME_SECONDS);
    }

    private void faceHero() {
        if (route.wall[leg]) {
            Rectangle hero = player.hitboxBoundsInto(heroScratch);
            headUp = hero.y + hero.height / 2f < centreY();
        } else {
            facingRight = directionToPlayer(facingRight ? 1 : -1) > 0;
        }
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
        if (!heroPresent() || lostHero(dt)) { enter(State.CRAWL); return; }
        aim();
        int side = heroDropSide();
        if (side >= 0 && atLip(side)) { setUp(side); return; }
        if (side >= 0) { travel(side == 0 ? -1 : 1, CLIMB_SPEED, CHASE_SPEED, dt); animateCrawl(dt, CHASE_FRAME_SECONDS); return; }
        if (targetLeg != leg) {
            travel(targetLeg > leg ? 1 : -1, CLIMB_SPEED, CHASE_SPEED, dt);
            animateCrawl(dt, CHASE_FRAME_SECONDS);
            return;
        }
        if (route.wall[leg]) { climbTo(dt); return; }
        float direction = directionToPlayer(facingRight ? 1 : -1);
        facingRight = direction > 0;
        if (inPounceBox() && commit()) return; // it commits only when its landing can put the fangs on the hero (#497)
        float dx = targetU - position.x; // the target is already inside its range: under the hero, or the range's end
        if (Math.abs(dx) < AT_END) return; // it holds there, facing the hero (D102: no overshoot, no turning back)
        float step = CHASE_SPEED * dt;
        velocity.x = Math.abs(dx) <= step ? dx / dt : Math.signum(dx) * CHASE_SPEED; // the last step lands on the target
        animateCrawl(dt, CHASE_FRAME_SECONDS);
    }

    /** On a wall, to the height nearest the hero; there it waits, its head towards the hero. It never strikes from a wall. */
    private void climbTo(float dt) {
        float dy = targetU - position.y;
        if (Math.abs(dy) < AT_END) { faceHero(); return; }
        headUp = dy < 0;
        float step = (headUp ? CLIMB_SPEED : CHASE_SPEED) * dt;
        position.y += Math.abs(dy) <= step ? dy : Math.signum(dy) * step;
        animateCrawl(dt, CHASE_FRAME_SECONDS);
    }

    private void animateCrawl(float dt, float frameSeconds) {
        frameClock += dt;
        while (frameClock >= frameSeconds) { frameClock -= frameSeconds; crawlFrame = !crawlFrame; }
    }

    private boolean inPounceBox() {
        return pounceBoxInto(pounceScratch).overlaps(player.hitboxBoundsInto(heroScratch));
    }

    /** Hearing (D103): a circle around the body's centre, through walls and floors. */
    private boolean hears() {
        return heroDistanceSquared() <= HEAR_RADIUS * HEAR_RADIUS;
    }

    private float heroDistanceSquared() {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        Rectangle self = hitboxBoundsInto(selfScratch);
        float dx = hero.x + hero.width / 2f - (self.x + self.width / 2f), dy = hero.y + hero.height / 2f - (self.y + self.height / 2f);
        return dx * dx + dy * dy;
    }

    private boolean lostHero(float dt) {
        outOfReach = heroDistanceSquared() > LOSE_RADIUS * LOSE_RADIUS ? outOfReach + dt : 0;
        return outOfReach >= LOSE_SECONDS;
    }

    /** It stays in its range, but a spider the leap left past an end (#497) may crawl back in, and is never snapped back. */
    private void clampToRange(float was) {
        float low = Math.min(rangeNeg, was), high = Math.max(rangePos, was);
        if (position.x < low) position.x = low;
        else if (position.x > high) position.x = high;
    }

    // ---- the floor pounce (D102) --------------------------------------------------------------------------------------

    /**
     * It commits on the spot and locks its target: the hero's centre now, where its fangs will land, never behind it. The leap may
     * leave the range by up to {@link #POUNCE_REACH} (#497), but never leaves the continuous floor under the body at its floor
     * line, and a solid stops it. It commits only when that landing, or the flight to it, puts the fangs on the hero (in x and in height, a solid cutting the flight short); else it
     * holds, facing him, rather than leaping in place for ever. Then the shared bite warning, sampled once. True when it committed.
     */
    private boolean commit() {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float target = hero.x + hero.width / 2f - (facingRight ? FANG_RIGHT : FANG_LEFT);
        target = facingRight ? Math.max(position.x, Math.min(rangePos + POUNCE_REACH, target))
            : Math.min(position.x, Math.max(rangeNeg - POUNCE_REACH, target));
        float landing = reachableLanding(target);
        float fang = facingRight ? FANG_RIGHT : FANG_LEFT, end = stoppedBySolid ? solidStop : landing; // a solid cuts the flight short
        float from = Math.min(fangX(), end + fang) - BITE_FANGS.width / 2f, to = Math.max(fangX(), end + fang) + BITE_FANGS.width / 2f;
        float fangTop = position.y + BITE_FANGS.offsetY;
        boolean reaches = hero.x + hero.width > from && hero.x < to && hero.y + hero.height > fangTop && hero.y < fangTop + BITE_FANGS.height;
        if (!reaches) return false;
        enter(State.WINDUP);
        leapToX = stoppedBySolid ? solidStop : landing; // the runtime flies to the stop the gate predicted: no leap in place
        startWarning();
        return true;
    }

    /**
     * The furthest x towards {@code target}, in 2 px steps from here, with floor under the whole body at its floor line: a leap
     * never leaves the floor (#497). The first solid in the body's way is noted in {@code solidStop} (the leap lands against it at
     * runtime, as D102 says). Without a level the target stands.
     */
    private float reachableLanding(float target) {
        stoppedBySolid = false;
        solidStop = position.x;
        if (level == null) return target;
        float step = target > position.x ? REACH_STEP : -REACH_STEP, x = position.x, good = x;
        while (x != target) {
            x = Math.abs(target - x) <= REACH_STEP ? target : x + step;
            if (!floorAt(x)) break;
            if (!stoppedBySolid && solidAt(x)) stoppedBySolid = true;
            else if (!stoppedBySolid) solidStop = x;
            good = x;
        }
        return good;
    }

    /** True when a solid is in the body's way at {@code x}, the body raised a pixel off its floor line. */
    private boolean solidAt(float x) {
        Rectangle body = leapBodyInto(fangScratch);
        body.x += x - position.x;
        return level.blocksObject(body, false);
    }

    /** True when the body at {@code x} on this floor line has authored floor under every 4 px of it (not a ladder's top step, #497). */
    private boolean floorAt(float x) {
        float floor = floorY(), left = x + BODY_X, right = left + BODY_WIDTH;
        for (float column = left; column <= right + FLOOR_PROBE - 1; column += FLOOR_PROBE) {
            if (!level.spiderFloorAt(fangScratch.set(Math.min(column, right - 1), floor + 1, 1, 1))) return false;
        }
        return true;
    }

    private void startWarning() {
        TrapWarnings warnings = game.trapWarnings();
        warningTotal = warnings != null ? warnings.duration(TrapWarnings.BITE_WARNING_SECONDS) : TrapWarnings.BITE_WARNING_SECONDS;
        if (game.playSounds()) game.playSound(warningTotal > TrapWarnings.BITE_WARNING_SECONDS ? WINDUP_LONG_SOUND : WINDUP_SOUND);
    }

    private void windup(float dt) {
        silkDue += SILK_PER_SECOND * dt;
        float fang = fangX(), spot = leapToX + (facingRight ? FANG_RIGHT : FANG_LEFT), floor = floorY();
        while (silkDue >= 1) { silkDue -= 1; if (level != null) level.spiderFx().silk(fang, spot, floor); }
        if (stateClock >= warningTotal) {
            leapFromX = position.x;
            leapFloorY = position.y;
            lastFangX = fangX();
            lineFlown = 0;
            bitThisLeap = false;
            enter(State.LEAP);
            if (game.playSounds()) game.playSound(LEAP_SOUND);
        }
    }

    /**
     * The flight: straight to the locked spot on a low arc; the fangs are lethal all the way, swept from last frame's x. A solid in
     * the way (a wall, a crate, a block or a platform's side, as {@link Level#blocksObject} sees them) stops it: it lands against
     * it, short of the spot, and bites there (D102).
     */
    private void leap() {
        float t = Math.min(1f, stateClock / LEAP_SECONDS), was = position.x, wasY = position.y;
        position.x = leapFromX + (leapToX - leapFromX) * t;
        position.y = leapFloorY - LEAP_ARC * MathUtils.sin(MathUtils.PI * t);
        velocity.setZero();
        if (level != null && level.blocksObject(leapBodyInto(fangScratch), false)) {
            position.x = was;
            position.y = level.blocksObject(leapBodyInto(fangScratch), false) ? wasY : leapFloorY;
            land();
            return;
        }
        lineFlown += Math.abs(position.x - was);
        float tip = facingRight ? LINE_X : SIDES * pivotX() - LINE_X;
        while (lineFlown >= LINE_STEP) {   // the dots trail back from the abdomen's tip, one every LINE_STEP px flown
            lineFlown -= LINE_STEP;
            float back = facingRight ? -lineFlown : lineFlown;
            if (level != null) level.spiderFx().line(position.x + tip + back, position.y + LINE_Y);
        }
        checkBite();
        if (t >= 1) {
            position.y = leapFloorY;
            land();
        }
    }

    /** The body's box raised a pixel off its floor line, so the floor it stands on is not a solid in its way. */
    private Rectangle leapBodyInto(Rectangle out) {
        Rectangle body = hitboxBoundsInto(out);
        return body.set(body.x, body.y, body.width, body.height - 1);
    }

    /** The end of the leap, at the spot or against a solid: the bite, seen and heard hit or miss (D93). */
    private void land() {
        enter(State.BITE);
        if (level != null) level.spiderFx().bite(fangX(), floorY());
        if (game.playSounds()) game.playSound(BITE_SOUND);
        checkBite();
    }

    /** The bite frame; a bite that reached the hero on its last frame lands before the recovery starts (it stays in the window). */
    private void bite() {
        checkBite();
        if (stateClock >= BITE_SECONDS && !bitePending) enter(State.RECOVER);
    }

    private void recover() {
        if (stateClock < RECOVER_SECONDS) return;
        if (!heroPresent()) { enter(State.CRAWL); return; }
        facingRight = directionToPlayer(facingRight ? 1 : -1) > 0;
        enter(State.CHASE);
    }

    /**
     * The fangs reach the hero: the bite lands at the start of the next frame unless the spider is killed first (the hit wins a
     * same-frame tie, D102). The fang box is swept from last frame's fangs, so a fast frame cannot skip over the hero.
     */
    private void checkBite() {
        if (!heroPresent() || bitThisLeap || player.isGotHit()) return;
        Rectangle fangs = fangBoxInto(fangScratch); // centred on fangX(): grown back to cover where the fangs were last frame
        float fangNow = fangX(), from = Math.min(lastFangX, fangNow) - fangs.width / 2f, to = Math.max(lastFangX, fangNow) + fangs.width / 2f;
        lastFangX = fangNow;
        fangs.set(from, fangs.y, to - from, fangs.height);
        if (fangs.overlaps(player.hitboxBoundsInto(heroScratch))) bitePending = bitThisLeap = true;
    }

    // ---- the silk drop (D103) ---------------------------------------------------------------------------------------

    /** The thread whose drop box the hero touches on this floor leg (0 at the route's start, 1 at its end), or -1. */
    private int heroDropSide() {
        if (route.wall[leg]) return -1;
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        if (leg == 0 && route.thread[0] && route.dropBoxInto(0, dropScratch).overlaps(hero)) return 0;
        if (leg == route.legs - 1 && route.thread[1] && route.dropBoxInto(1, dropScratch).overlaps(hero)) return 1;
        return -1;
    }

    private boolean atLip(int side) { return atEnd(side == 0 ? -1 : 1); }

    /** At the lip: it steps over the edge and hangs head down against it, drawn back up a pixel, and the shared bite warning runs. */
    private void setUp(int side) {
        dropSide = side;
        enter(State.SET);
        position.set(route.threadX[side] - HANG_THREAD_X, route.anchorY[side] - DRAW_BACK);
        velocity.setZero();
        startWarning();
    }

    /** The drop's wind-up: silk glints run down the column from its fangs to the thread's end; then it lets go. */
    private void set(float dt) {
        float x = route.threadX[dropSide] + THREAD_MIDDLE, end = route.endY[dropSide] + FRAME_HEIGHT;
        silkDue += SILK_PER_SECOND * dt;
        while (silkDue >= 1) { silkDue -= 1; if (level != null) level.spiderFx().silkDown(x, position.y + FRAME_HEIGHT, end); }
        if (stateClock < warningTotal) return;
        position.y = route.anchorY[dropSide];
        lastFangY = position.y + HANG_FANG_Y;
        shedFlown = 0;
        bitThisLeap = false;
        enter(State.DROP);
        if (game.playSounds()) game.playSound(DROP_SOUND);
    }

    /** The drop: straight down the thread, fast; the fangs are lethal all the way, swept from last frame's. */
    private void drop(float dt) {
        float was = position.y, end = route.endY[dropSide];
        position.y = Math.min(end, position.y + DROP_SPEED * dt);
        shedFlown += position.y - was;
        while (shedFlown >= SHED_STEP) { // the thread sheds a speck every few px it pays out
            shedFlown -= SHED_STEP;
            if (level != null) level.spiderFx().shed(route.threadX[dropSide], position.y - shedFlown);
        }
        checkDropBite();
        if (position.y < end) return;
        enter(State.DANGLE);
        if (level != null) level.spiderFx().bite(route.threadX[dropSide] + THREAD_MIDDLE, end + FRAME_HEIGHT + HANG_GAP);
        if (game.playSounds()) game.playSound(BITE_SOUND);
        checkDropBite();
    }

    /** The bite at the thread's end, hit or miss (D93); one bite per drop, and a bite due on its last frame lands inside it. */
    private void dangle() {
        checkDropBite();
        if (stateClock >= BITE_SECONDS && !bitePending) enter(State.HANG);
    }

    /** Harmless on its thread, as the pounce's recovery; then it reels up. */
    private void hang() {
        if (stateClock < RECOVER_SECONDS) return;
        enter(State.REEL);
        if (game.playSounds()) game.playSound(REEL_SOUND);
    }

    /** Back up the thread, harmless and head down; at the lip it steps back onto its floor and goes on (a new drop needs a full wind-up). */
    private void reel(float dt) {
        position.y -= REEL_SPEED * dt;
        if (position.y > route.anchorY[dropSide]) return;
        boolean start = dropSide == 0;
        enterLeg(start ? 0 : route.legs - 1, start);
        facingRight = duSign(start ? -1 : 1) > 0; // facing over the lip
        enter(heroPresent() ? State.CHASE : State.CRAWL);
    }

    /** As {@link #checkBite}, down the thread: the fang box grown up to where it was last frame. */
    private void checkDropBite() {
        if (!heroPresent() || bitThisLeap || player.isGotHit()) return;
        Rectangle fangs = hangFangsInto(fangScratch);
        float top = Math.min(lastFangY, fangs.y), bottom = fangs.y + fangs.height;
        lastFangY = fangs.y;
        fangs.set(fangs.x, top, fangs.width, bottom - top);
        if (fangs.overlaps(player.hitboxBoundsInto(heroScratch))) bitePending = bitThisLeap = true;
    }

    // ---- answers and the body (D101) ----------------------------------------------------------------------------------

    /** A fireball, a thrown stone or torch ({@code gotHit}) kills it; body contact is a stomp (it kills it too) or nothing. */
    @Override
    public void collidedWithActor(boolean gotHit) {
        if (isDefeated()) return;
        if (gotHit) { die(); return; }
        if (!stompedByPlayer()) return; // touch is harmless: only the fangs kill (D102)
        player.velocity.y = -BOUNCE_HEIGHT;
        die();
    }

    /** The sword kills it in any state with the light blood of D65 (the base class); on the body, it crumbles it (D101). */
    @Override
    public void hitBySword(Level level) {
        if (isRemains()) crumble();
        else super.hitBySword(level);
    }

    /**
     * Killed (by anything): it flips onto its back and stays as a body (D101); the poof follows a moment later. Killed on a wall
     * or its thread, the body is set on the open side, where it was, and falls to the floor below; a thread snaps (D103).
     */
    private void die() {
        boolean wall = isWallPose(), thread = isThreadPose();
        Rectangle was = hitboxBoundsInto(nearScratch);
        float cx = was.x + was.width / 2f, cy = was.y + was.height / 2f;
        if (thread) snap();
        enter(State.DEAD); // killed in the air, the body falls from where it was (fall)
        if (wall || thread) {
            float x = cx - BODY_X - BODY_WIDTH / 2f;
            if (wall) x = route.solidRight[leg] ? Math.min(x, route.line[leg] - BODY_X - BODY_WIDTH) : Math.max(x, route.line[leg] - BODY_X);
            else if (route.threadX[dropSide] > route.lipX[dropSide]) x = Math.max(x, route.lipX[dropSide] - BODY_X);
            else x = Math.min(x, route.lipX[dropSide] - BODY_X - BODY_WIDTH);
            position.set(x, cy - BODY_Y - BODY_HEIGHT / 2f);
        }
        pendingRemoval = true;
        bitePending = false;
        poofDone = heroNear = false;
        swing.reset();
        deadClock = 0;
        velocity.setZero();
        if (game.playSounds()) game.playSound(DEATH_SOUND);
    }

    /**
     * The thread snaps: specks drift down from it, and its stub fades. During the drop's wind-up the spinnerets are still at the
     * lip and no thread is drawn yet, so nothing snaps.
     */
    private void snap() {
        if (position.y <= route.anchorY[dropSide]) return;
        snapX = route.threadX[dropSide];
        snapTop = route.anchorY[dropSide];
        snapBottom = position.y;
        snapClock = 0;
        if (level != null) level.spiderFx().snap(snapX, snapTop, snapBottom);
        if (game.playSounds()) game.playSound(SNAP_SOUND);
    }

    @Override
    protected void setDefeated() { die(); }

    private void poof() {
        poofDone = true;
        float floor = floorY();
        if (level != null) level.spiderFx().poof(centreX(), floor - BODY_CENTRE_ABOVE_FLOOR, floor);
    }

    /** A body falls and settles on the floor under it; one that falls below the room, over a pit, is gone (D101). */
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
        swing.push(player.velocity.x != 0 ? Math.signum(player.velocity.x) : hero.x + hero.width / 2f < centreX() ? 1 : -1);
    }

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
        if (!poofDone) poof();
        if (level != null) level.remainsFx().crumble(centreX(), floorY(), SpiderFx.SPIDER_CHIPS);
        if (game.playSounds()) game.playSound(CRUMBLE_SOUND);
        removeFromParent();
    }

    @Override
    public void burnUp() {
        if (!isRemains()) return;
        if (!poofDone) poof();
        enter(State.BURN);
        if (level != null) level.remainsFx().burnGlow(centreX(), floorY(), BURN_SECONDS);
        if (game.playSounds()) game.playSound(BURN_SOUND);
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    /** The frame index drawn now: of the floor strip, or of the wall strip while {@link #onWall()}. */
    int frameIndex() {
        if (isWallPose()) {
            if (current == State.NOTICE && NOTICE_FRAMES[noticeStep] == TAP) return WALL_TAP;
            return current != State.NOTICE && crawlFrame ? WALL2 : WALL1;
        }
        return switch (current) {
            case CRAWL, CHASE -> crawlFrame ? CRAWL2 : CRAWL1;
            case NOTICE -> NOTICE_FRAMES[noticeStep];
            case WINDUP -> CROUCH;
            case LEAP -> LEAP_FRAME;
            case BITE -> BITE_FRAME;
            case RECOVER -> RECOVER_FRAME;
            case SET, DROP -> HANG_WIND_FRAME;
            case DANGLE, HANG, REEL -> HANG_FRAME;
            case DEAD, BURN -> DEAD_FRAME;
        };
    }

    /** How far the body has faded from the active {@code dead} frame into the decor body: 0 at the kill, 1 once settled. */
    public float bodyFade() {
        if (current != State.DEAD && current != State.BURN) return 0;
        return Math.min(1f, Math.max(0f, (deadClock - BODY_FADE_DELAY) / BODY_FADE_SECONDS));
    }

    /** The 3 px bob on the silk's stretch when it arrives at the thread's end (drawing only). */
    private float bob() {
        if (current != State.DANGLE && current != State.HANG) return 0;
        float t = current == State.DANGLE ? stateClock : stateClock + BITE_SECONDS;
        return t < BOB_SECONDS ? Math.round(BOB * MathUtils.sin(MathUtils.PI * t / BOB_SECONDS)) : 0;
    }

    @Override
    public void render(Batch batch) {
        if (current == State.DEAD || current == State.BURN) { renderSnap(batch); renderBody(batch); return; }
        if (isThreadPose()) {
            float y = position.y + bob();
            renderThread(batch, route.threadX[dropSide], route.anchorY[dropSide], y);
            batch.draw(frames[frameIndex()], position.x, y, FRAME_WIDTH, FRAME_HEIGHT);
            return;
        }
        if (isWallPose()) { renderWall(batch); return; }
        float dx = current == State.WINDUP ? (facingRight ? -DRAW_BACK : DRAW_BACK) : 0;
        TextureRegion frame = frames[frameIndex()];
        float x = position.x + dx, y = position.y;
        if (facingRight) batch.draw(frame, x, y, FRAME_WIDTH, FRAME_HEIGHT);
        else batch.draw(frame, x + SIDES * pivotX(), y, -FRAME_WIDTH, FRAME_HEIGHT);
    }

    /** On a wall: the strip is drawn with the wall on its right; mirrored for a wall on its left, flipped for a head pointing down. */
    private void renderWall(Batch batch) {
        boolean right = route.solidRight[leg];
        float rise = current == State.NOTICE && NOTICE_FRAMES[noticeStep] == RISE ? (right ? -WALL_RISE : WALL_RISE) : 0;
        float x = position.x + rise + (right ? 0 : WALL_WIDTH), y = position.y + (headUp ? 0 : WALL_HEIGHT);
        batch.draw(wallFrames[frameIndex()], x, y, right ? WALL_WIDTH : -WALL_WIDTH, headUp ? WALL_HEIGHT : -WALL_HEIGHT);
    }

    /** The thread from the lip down to {@code bottom}: the 1x4 silk tile repeated, the last few pixels one texel at a time. */
    private void renderThread(Batch batch, float x, float top, float bottom) {
        float y = top;
        while (y + Route.SILK_TILE <= bottom) { batch.draw(silkTile, x, y, 1, Route.SILK_TILE); y += Route.SILK_TILE; }
        while (y + 1 <= bottom) { batch.draw(silkTexel, x, y, 1, 1); y += 1; }
    }

    /** A snapped thread's stub, fading. */
    private void renderSnap(Batch batch) {
        if (!threadSnapping()) return;
        float saved = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f - snapClock / SNAP_SECONDS);
        renderThread(batch, snapX, snapTop, snapBottom);
        batch.setPackedColor(saved);
    }

    /** The body: the flip, the cross-fade into the decor body, the swing about the belly, and while burning a darkening tint. */
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

    // ---- authoring ----------------------------------------------------------------------------------------------------

    /**
     * The spider's route (D103), checked at load. Legs alternate between floors and walls. A floor leg's coordinate is
     * {@code position.x} (range {@code lo}..{@code hi}, its floor line {@code line}); a wall leg's is {@code position.y}, the top of the
     * 16x32 wall frame (range {@code lo}..{@code hi}, the face at x {@code line}, the solid on its right when {@code solidRight}).
     * {@code aLow}: the leg's a end (towards the route's start) is at {@code lo}. At most one silk thread hangs at each end of the
     * route, from a lip at the end of a floor: index 0 at the start, 1 at the end.
     */
    public static final class Route {
        /** The thread tile: 1x4 px at the silk frame's top-left. */
        static final int SILK_TILE = 4;
        /** Two ends a thread may hang from: the route's start and its end; a polyline point is two numbers, at least two points. */
        static final int ENDS = 2, START = 0, END = 1, XY = 2, MIN_POINTS = 2;
        final int legs;
        final boolean[] wall, aLow, solidRight;
        final float[] lo, hi, line;
        final boolean[] thread = new boolean[ENDS];
        /** Per thread: its x, the lip's x, the floor line it hangs from, the spinnerets' y at its end, and the floor below. */
        final float[] threadX = new float[ENDS], lipX = new float[ENDS], anchorY = new float[ENDS], endY = new float[ENDS], floorBelow = new float[ENDS];
        final float spawnU;
        /** A floor-only spider authored as a box with offNeg and offPos (D102): it spawns where it was placed, facing left. */
        final boolean legacy;

        private Route(int legs, boolean legacy, float spawnU) {
            this.legs = legs;
            this.legacy = legacy;
            this.spawnU = spawnU;
            wall = new boolean[legs];
            aLow = new boolean[legs];
            solidRight = new boolean[legs];
            lo = new float[legs];
            hi = new float[legs];
            line = new float[legs];
        }

        public int legs() { return legs; }
        public boolean isWall(int i) { return wall[i]; }
        /** On a wall leg: the solid is on its right (the spider on the face's left). */
        public boolean solidRight(int i) { return solidRight[i]; }
        public boolean hasThread(int side) { return thread[side]; }
        public float lo(int i) { return lo[i]; }
        public float hi(int i) { return hi[i]; }
        public float line(int i) { return line[i]; }
        public float threadX(int side) { return threadX[side]; }
        public float anchorY(int side) { return anchorY[side]; }
        /** Where the spinnerets stop at the thread's end. */
        public float endY(int side) { return endY[side]; }

        /** The drop box of a thread: the column its fangs sweep, from the lip to the fang tips at the thread's end. */
        public Rectangle dropBoxInto(int side, Rectangle out) {
            float x = threadX[side] - HANG_THREAD_X + HANG_FANG_X;
            return out.set(x, anchorY[side], HANG_FANG_WIDTH, endY[side] + FRAME_HEIGHT - anchorY[side]);
        }

        /** D102's floor-only spider: one floor leg, {@code position.x} from {@code x - offNeg} tiles to {@code x + offPos} tiles. */
        public static Route floor(float x, float y, float offNeg, float offPos) {
            Route r = new Route(1, true, x);
            r.lo[0] = x - offNeg * TILE_SIZE;
            r.hi[0] = x + offPos * TILE_SIZE;
            r.line[0] = y + BODY_Y + BODY_HEIGHT;
            r.aLow[0] = true;
            return r;
        }

        /**
         * A route traced by a Tiled polyline over the room's static solids (untyped and {@code Wall} collision boxes). A
         * horizontal piece is a floor (its line on the floor tops); a vertical piece along one face of a solid is a wall; a vertical
         * piece over open air at either end of the polyline, below a floor's end, is a thread down to the floor below. Throws
         * with the reason when the polyline is not a route.
         */
        public static Route trace(float[] points, List<Rectangle> solids) {
            if (points == null || points.length < XY * MIN_POINTS || points.length % XY != 0)
                throw new IllegalArgumentException("needs a polyline of at least two points");
            int segments = points.length / XY - 1;
            for (float p : points) if (!Float.isFinite(p)) throw new IllegalArgumentException("has a broken point");
            boolean[] isThread = new boolean[segments], isWall = new boolean[segments], right = new boolean[segments];
            for (int s = 0; s < segments; s++) {
                float ax = x(points, s), ay = y(points, s), bx = x(points, s + 1), by = y(points, s + 1);
                if (ax != bx && ay != by) throw new IllegalArgumentException("has a piece that is neither level nor upright (point " + (s + 1) + ")");
                if (ax == bx && ay == by) throw new IllegalArgumentException("has two points in the same place (point " + (s + 1) + ")");
                if (ay == by) continue;
                float top = Math.min(ay, by), bottom = Math.max(ay, by);
                boolean solidOnRight = covers(solids, ax, top, bottom, true), solidOnLeft = covers(solids, ax, top, bottom, false);
                if (solidOnRight && solidOnLeft) throw new IllegalArgumentException("climbs inside a solid at x " + (int) ax);
                if (solidOnRight || solidOnLeft) { isWall[s] = true; right[s] = solidOnRight; continue; }
                // a thread: the first piece, rising to the lip at the start of a floor, or the last, falling from the end of one
                boolean belowFloor = segments > 1 && ((s == 0 && ay > by && y(points, s + 1) == y(points, s + 1 + 1))
                    || (s == segments - 1 && by > ay && y(points, s - 1) == y(points, s)));
                if (!belowFloor) throw new IllegalArgumentException("has an upright piece at x " + (int) ax
                    + " that is neither a wall (along one face of a solid) nor a thread (hanging from a floor's end at the route's end)");
                isThread[s] = true;
            }
            int first = isThread[0] ? 1 : 0, last = segments - 1 - (isThread[segments - 1] ? 1 : 0);
            if (last < first) throw new IllegalArgumentException("has no floor or wall to walk on");
            Route r = new Route(last - first + 1, false, 0);
            for (int s = first; s <= last; s++) {
                int i = s - first;
                float ax = x(points, s), ay = y(points, s), bx = x(points, s + 1), by = y(points, s + 1);
                if (i > 0 && isWall[s] == r.wall[i - 1]) throw new IllegalArgumentException("has two " + (isWall[s] ? "walls" : "floors")
                    + " in a row at point " + (s + 1) + " (merge them into one piece)");
                r.wall[i] = isWall[s];
                if (isWall[s]) {
                    r.solidRight[i] = right[s];
                    r.line[i] = ax;
                    r.lo[i] = Math.min(ay, by);
                    r.hi[i] = Math.max(ay, by) - WALL_HEIGHT;
                    r.aLow[i] = ay < by;
                    if (r.hi[i] < r.lo[i]) throw new IllegalArgumentException("has a wall shorter than the spider (" + (int) WALL_HEIGHT + " px) at x " + (int) ax);
                } else {
                    r.line[i] = ay;
                    r.lo[i] = Math.min(ax, bx) - BODY_X;
                    r.hi[i] = Math.max(ax, bx) - BODY_X - BODY_WIDTH;
                    r.aLow[i] = ax < bx;
                    if (r.hi[i] < r.lo[i]) throw new IllegalArgumentException("has a floor shorter than the spider's body (" + (int) BODY_WIDTH + " px) at y " + (int) ay);
                }
                if (i > 0) corner(r, i - 1, i, ax, ay);
            }
            // the start thread runs from its foot (point 0) up to the lip (point 1); the end thread from the lip down to its foot
            if (isThread[0]) hangThread(r, START, x(points, 1), y(points, 1), y(points, 0), 0);
            if (isThread[segments - 1]) hangThread(r, END, x(points, segments - 1), y(points, segments - 1), y(points, segments), r.legs - 1);
            return r;
        }

        private static float x(float[] points, int i) { return points[XY * i]; }
        private static float y(float[] points, int i) { return points[XY * i + 1]; }

        /** The corner between legs {@code a} and {@code b} at point ({@code x}, {@code y}): a floor at a wall's foot, or over its top. */
        private static void corner(Route r, int a, int b, float x, float y) {
            int floor = r.wall[a] ? b : a, wall = r.wall[a] ? a : b;
            float other = r.lo[floor] + BODY_X == x ? r.hi[floor] + BODY_X + BODY_WIDTH : r.lo[floor] + BODY_X;
            boolean beyondRight = other > x, top = y == r.lo[wall], foot = y == r.hi[wall] + WALL_HEIGHT;
            // at the foot the floor runs away from the solid (on the open side); over the top it runs along the solid
            boolean ok = (foot && beyondRight != r.solidRight[wall]) || (top && beyondRight == r.solidRight[wall]);
            if (r.line[floor] != y || !ok) throw new IllegalArgumentException("has a corner at (" + (int) x + ", " + (int) y
                + ") where the floor does not meet the wall's foot or top");
        }

        /** A thread at {@code side} from the lip ({@code lipX}, {@code lipY}) of floor leg {@code leg} down to the floor at {@code floorY}. */
        private static void hangThread(Route r, int side, float lipX, float lipY, float floorY, int leg) {
            if (r.wall[leg]) throw new IllegalArgumentException("hangs a thread from a wall: a thread hangs from a floor's end");
            float other = r.lo[leg] + BODY_X == lipX ? r.hi[leg] + BODY_X + BODY_WIDTH : r.lo[leg] + BODY_X;
            float out = other < lipX ? 1 : -1;
            r.thread[side] = true;
            r.lipX[side] = lipX;
            r.threadX[side] = lipX + out * threadOut(out > 0);
            r.anchorY[side] = lipY;
            r.floorBelow[side] = floorY;
            r.endY[side] = floorY - HANG_GAP - FRAME_HEIGHT;
            if (floorY - lipY > THREAD_MAX) throw new IllegalArgumentException("has a thread longer than " + (int) THREAD_MAX + " px at x " + (int) lipX);
            if (r.endY[side] <= lipY) throw new IllegalArgumentException("has a thread too short to drop at x " + (int) lipX);
        }

        /** True when solids flush with x (on its right, or its left) cover top..bottom without a gap. */
        private static boolean covers(List<Rectangle> solids, float x, float top, float bottom, boolean onRight) {
            float covered = top;
            boolean grew = true;
            while (grew && covered < bottom) {
                grew = false;
                for (Rectangle s : solids) {
                    boolean flush = onRight ? s.x == x : s.x + s.width == x;
                    if (flush && s.y <= covered && s.y + s.height > covered) { covered = s.y + s.height; grew = true; }
                }
            }
            return covered >= bottom;
        }
    }

    /**
     * The load-time checks of a floor-only spider authored as a box (D102), on the spider as the game builds it whatever size the
     * Tiled object has (the 32x16 frame, its {@code y} the frame's top): the frame's sweep over the range inside the room, a floor
     * without gaps under the body's whole sweep, and no standing place (the entrance body, the room exit, a checkpoint) inside the
     * pounce box at any point of the range, facing either way (from {@code position.x} - 46 to {@code position.x} + 70), so the
     * hero never enters or stands inside a pounce. Null when the spider is fine, else what is wrong.
     */
    public static String problem(float x, float y, float w, float h, float offNeg, float offPos, List<Rectangle> floors,
                                 Rectangle room, Map<String, Rectangle> standing) {
        if (!(w > 0) || !(h > 0)) return "needs positive bounds";
        if (!Float.isFinite(offNeg) || !Float.isFinite(offPos) || offNeg < 0 || offPos < 0) return "needs finite, non-negative offNeg and offPos";
        return floorProblem(x - offNeg * TILE_SIZE, x + offPos * TILE_SIZE, y + BODY_Y + BODY_HEIGHT, floors, room, standing);
    }

    /** D102's checks for one floor leg: {@code position.x} from {@code leftX} to {@code rightX}, standing on {@code floor}. */
    private static String floorProblem(float leftX, float rightX, float floor, List<Rectangle> floors, Rectangle room,
                                       Map<String, Rectangle> standing) {
        float pivot = BODY_X + BODY_WIDTH / 2, pounceRight = Math.max(POUNCE.offsetX + POUNCE.width, FANG_EXTENT), pounceLeft = SIDES * pivot - pounceRight;
        float y = floor - BODY_Y - BODY_HEIGHT, top = y + POUNCE.offsetY;
        Rectangle reach = new Rectangle(leftX + pounceLeft, top, rightX + pounceRight - (leftX + pounceLeft), floor - top);
        if (leftX < room.x || rightX + FRAME_WIDTH > room.x + room.width || top < room.y || floor > room.y + room.height)
            return "has its range leaving the room";
        float left = leftX + BODY_X, right = rightX + BODY_X + BODY_WIDTH;
        if (!gapless(floors, left, right, floor)) return "must crawl on floor without gaps under its whole range";
        for (Map.Entry<String, Rectangle> place : standing.entrySet())
            if (reach.overlaps(place.getValue())) return "can pounce at the room " + place.getKey() + " (its reach is " + (int) POUNCE_REACH + " px)";
        return null;
    }

    /** Floor tops on {@code floor} cover {@code left}..{@code right} without a gap. */
    private static boolean gapless(List<Rectangle> floors, float left, float right, float floor) {
        float covered = left;
        List<Rectangle> spans = new ArrayList<>();
        for (Rectangle f : floors) if (Math.abs(f.y - floor) < 1f) spans.add(f);
        spans.sort((a, b) -> Float.compare(a.x, b.x));
        for (Rectangle f : spans) if (f.x <= covered + 1f) covered = Math.max(covered, f.x + f.width);
        return covered >= right;
    }

    /**
     * The load-time checks of a traced route (D103). Every floor leg passes D102's checks. Every wall leg and every thread lies
     * inside the room and inside its camera view ({@code viewTop} down). A thread's column is clear of every solid and of
     * {@code blockers} (one-way platforms and objects that move), ends on a floor, keeps {@link #DROP_ESCAPE_FLOOR} px of that
     * floor beside it on each side, and lies over nothing in {@code noDrop} (climbables, quicksand); no standing place is inside a
     * drop box. Null when the route is fine, else what is wrong.
     */
    public static String routeProblem(float[] points, List<Rectangle> floors, List<Rectangle> solids, List<Rectangle> blockers,
                                      List<Rectangle> noDrop, Rectangle room, float viewTop, Map<String, Rectangle> standing) {
        Route r;
        try {
            r = Route.trace(points, solids);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        for (int i = 0; i < r.legs; i++) {
            if (r.wall[i]) {
                float x = r.solidRight[i] ? r.line[i] - WALL_WIDTH : r.line[i];
                if (x < room.x || x + WALL_WIDTH > room.x + room.width || r.lo[i] < room.y || r.hi[i] + WALL_HEIGHT > room.y + room.height)
                    return "has a wall leaving the room";
                if (r.lo[i] < viewTop) return "climbs above the camera's view of its room (y " + (int) viewTop + ")";
                continue;
            }
            String floor = floorProblem(r.lo[i], r.hi[i], r.line[i], floors, room, standing);
            if (floor != null) return floor;
            if (r.line[i] - FRAME_HEIGHT < viewTop) return "walks above the camera's view of its room (y " + (int) viewTop + ")";
        }
        Rectangle box = new Rectangle();
        for (int side = 0; side < Route.ENDS; side++) {
            if (!r.thread[side]) continue;
            r.dropBoxInto(side, box);
            Rectangle hanging = new Rectangle(r.threadX[side] - HANG_THREAD_X, r.anchorY[side] - DRAW_BACK, FRAME_WIDTH,
                r.endY[side] + FRAME_HEIGHT + DRAW_BACK - r.anchorY[side]);
            Rectangle column = new Rectangle(r.threadX[side] - HANG_THREAD_X + HANG_BODY_X, r.anchorY[side],
                HANG_BODY_WIDTH, r.floorBelow[side] - r.anchorY[side]);
            if (!room.contains(hanging)) return "hangs its thread outside the room";
            for (Rectangle s : solids) if (s.overlaps(column)) return "drops through a solid at x " + (int) r.threadX[side];
            for (Rectangle b : blockers) if (b.overlaps(column)) return "drops through a platform or a moving object at x " + (int) r.threadX[side];
            if (!gapless(floors, box.x - DROP_ESCAPE_FLOOR, box.x + box.width + DROP_ESCAPE_FLOOR, r.floorBelow[side]))
                return "needs floor under its thread and " + (int) DROP_ESCAPE_FLOOR + " px of it on each side to step out of the drop";
            for (Rectangle d : noDrop) if (d.overlaps(box)) return "drops onto a climbable or quicksand, where the hero cannot step aside";
            for (Map.Entry<String, Rectangle> place : standing.entrySet())
                if (box.overlaps(place.getValue())) return "can drop on the room " + place.getKey();
        }
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
