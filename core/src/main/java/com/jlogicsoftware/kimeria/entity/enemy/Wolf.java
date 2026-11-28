package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.WolfFx;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The forest wolf (#331, D115): the fastest and most dangerous Forest monster, and the first one that cannot be outrun. It trots
 * inside its authored range, sniffing at each end. It sees far ahead ({@link #SIGHT_RANGE}, a band cut by solids) and hears a
 * close circle ({@link #HEAR_RADIUS}); noticed, it stops, pricks its ears, bristles and scrapes a paw (0.5 s, not scaled by the
 * assist) and calls, then sprints at {@link #CHASE_SPEED}, faster than the hero runs, bound to its range. When the hero's body
 * touches its lunge box (from its muzzle {@link #COMMIT_REACH} px ahead, {@link #COMMIT_HEIGHT} px high) it commits where it
 * stands: the direction locks, and it crouches for the shared bite warning ({@link TrapWarnings#BITE_WARNING_SECONDS}, longer
 * with the assist) with pale breath streaks drawn along the whole line its jaws will cover. Then it lunges {@link #LUNGE_REACH} px
 * along that line in {@link #LUNGE_SECONDS}, low over the floor, and bites, seen and heard hit or miss (D93); the jaws kill, the
 * body never does. After the bite it recovers for {@link #RECOVER_SECONDS}, harmless, not scaled by the assist, and chases again,
 * each lunge after a full new wind-up.
 *
 * <p>It never leaves its floor: the lunge stops at the end of the floor under it and against a solid, and may pass its range end
 * only over floor. Anything whose top is more than {@link #COMMIT_HEIGHT} px above its floor is out of its reach: it runs to the
 * nearest point it can reach, stops facing the hero and bays; after {@link #BAY_SECONDS} of that it gives up and trots back, and will
 * not notice that hero again until he has been out of its sight for {@link #COOL_SECONDS}. A jump always answers the lunge (its
 * jaws run low and the load check keeps {@link #HEADROOM} px of headroom over its reach).
 *
 * <p>Every weapon kills it in any state; it leaves its body (D101, {@link Remains}): lying on its side along the floor, which
 * keeps its live palette (D117), twitches (a big body does not rock) when anyone comes near, is crumbled by the sword
 * or a stomp and burnt by a fireball or a torch; a stone flies through it (D119). Killed in its lunge, the body slides on to the lunge's end. A bite that
 * reaches the hero lands at the start of the wolf's next frame, so a hit that kills it on the same frame wins (as the spider's).
 * Its effects are {@link WolfFx}; its sprite is one strip ({@link SpriteSheets#MONSTER_WOLF}). World Y grows downward: the floor
 * line is the body's bottom edge, the jaws run {@link #JAW_ABOVE_FLOOR_LOW} to {@link #JAW_ABOVE_FLOOR_HIGH} px above it (a
 * smaller Y).
 *
 * <p>The develop (#525, D158) gives it one deterministic answer per habit. A stomp kills only a committed wolf (wind-up, lunge,
 * bite, recovery, or bayed under a perched hero); any other stomp bounces the hero off. From its chase it reads a jump (a
 * {@link State#READ} tell, then it runs to where he will land) or, when the airborne hero is in its pounce box, bites him in the air
 * ({@link State#POUNCE_COIL}, {@link State#POUNCE}), one answer per jump. A swing at its wind-up makes it hop back
 * ({@link State#FLINCH}, {@link State#HOP}); the next such swing it waits out ({@link State#HOLD}) and winds up again; and so on in
 * turn. The longer-warnings assist turns those three off. On a route (a Tiled polyline, {@link Route}) it leaps steps and gaps
 * between its floor legs ({@link State#LEAP_COIL}, {@link State#LEAP}); it never strikes across one.
 */
public class Wolf extends Enemy<Wolf.State> implements CollideBody, Remains {
    /**
     * {@code SNIFF}: at a range end before it turns; {@code BAY}: stopped at the nearest point it can reach, hero out of reach;
     * {@code READ}: reading a jump; {@code POUNCE_COIL}, {@code POUNCE}: the jump bite; {@code FLINCH}, {@code HOP}: the jump back
     * from a swing; {@code HOLD}: waiting a swing out; {@code LEAP_COIL}, {@code LEAP}: a leap between the legs of its route.
     */
    public enum State { TROT, SNIFF, NOTICE, CHASE, SKID, READ, POUNCE_COIL, POUNCE, WINDUP, LUNGE, BITE, RECOVER, FLINCH, HOP, HOLD,
        LEAP_COIL, LEAP, BAY, DEAD, BURN }

    public static final float FRAME_WIDTH = 48, FRAME_HEIGHT = 24;
    /** The body hitbox in the 48x24 frame: x 6..40, y 6..24 (D10); the feet are on the floor line, row 23. */
    private static final float BODY_X = 6, BODY_Y = 6, BODY_WIDTH = 34, BODY_HEIGHT = 18;
    /** The jaw box, inside the drawn muzzle (7x7, 8 to 15 px above the floor line): lethal during the lunge and the bite. */
    private static final Hitbox JAWS = Hitbox.rect(40, 9, 7, 7);
    /** The jaw box's front edge in the frame facing right: the muzzle, the reference of every reach (D115). */
    private static final float MUZZLE_X = 47;
    public static final float JAW_ABOVE_FLOOR_LOW = 8, JAW_ABOVE_FLOOR_HIGH = 15;
    private static final float PIVOT_X = BODY_X + BODY_WIDTH / 2f;
    private static final int SIDES = 2;
    private static final int STAND = 0, TROT1 = 1, TROT2 = 2, ALERT = 3, HACKLE = 4, SCRAPE = 5, RUN1 = 6, RUN_FRAMES = 4, SKID_FRAME = 10,
        CROUCH = 11, LUNGE_FRAME = 12, BITE_FRAME = 13, SHAKE = 14, BAY_FRAME = 15, DEAD_FRAME = 16, READ_FRAME = 17, COIL_FRAME = 18,
        LEAP_FRAME = 19, FRAMES = 20;

    /** It trots this fast (px/s) and stops this long at each end before it turns; the trot frames alternate this often. */
    public static final float PATROL_SPEED = 40, SNIFF_SECONDS = .6f;
    private static final float TROT_FRAME_SECONDS = .2f;
    /** Sight, ahead only: from its muzzle this far, from its floor line up this high, cut at the first solid at its eye height. */
    public static final float SIGHT_RANGE = 256, SIGHT_BAND = 48, EYE_HEIGHT = 16;
    /** Hearing: a circle around its body's centre, through anything. */
    public static final float HEAR_RADIUS = 64;
    private static final float SIGHT_STEP = 4, SIGHT_PROBE = 4;
    /** The notice in body language: ears up, bristled, a paw scraped, bristled again (0.5 s, not scaled by the assist). */
    private static final int[] NOTICE_FRAMES = {ALERT, HACKLE, SCRAPE, HACKLE};
    private static final float[] NOTICE_SECONDS = {.15f, .15f, .1f, .1f};
    public static final float NOTICE_TOTAL;
    static {
        float total = 0;
        for (float s : NOTICE_SECONDS) total += s;
        NOTICE_TOTAL = total;
    }
    /** The chase: top speed (px/s), reached in 0.25 s (the acceleration), and the braking of a stop or a turn (a skid of about 10 px). */
    public static final float CHASE_SPEED = 160, ACCELERATION = 640, BRAKING = 1280;
    /** The gallop's four frames each run this long at full speed (slower while it is still speeding up). */
    private static final float GALLOP_FRAME_SECONDS = .06f;
    /** The lunge box: from the muzzle this far ahead, from the floor line up this high. A hero above it is out of its reach. */
    public static final float COMMIT_REACH = 72, COMMIT_HEIGHT = 24;
    /**
     * The lunge: this far along its floor in this long (a hero who runs from the commit gains {@code 100 * (wind-up + lunge)} px
     * on it, so without the assist the jaws at {@link #LUNGE_REACH} must outreach {@link #COMMIT_REACH} + 75, and with the assist
     * {@code COMMIT_REACH + 100} must outreach them: D115).
     */
    public static final float LUNGE_REACH = 160, LUNGE_SECONDS = .25f;
    public static final float LUNGE_SPEED = LUNGE_REACH / LUNGE_SECONDS;
    /** The bite frame holds this long; the recovery that follows is harmless and not scaled by the assist. */
    public static final float BITE_SECONDS = .1f, RECOVER_SECONDS = .5f;
    /** A hero out of its reach for this long, or out of sight and hearing for {@link #LOSE_SECONDS}, is given up on. */
    public static final float BAY_SECONDS = 1.5f, LOSE_SECONDS = 1.5f;
    /** After giving up it ignores that hero until he has been out of its sight this long. */
    public static final float COOL_SECONDS = 1f;
    /** The load check keeps this much headroom above its floor over its reach, and a range at least this long (px). */
    public static final float HEADROOM = 64, MIN_RANGE = 160;
    /** A breath streak is born this many times a second along the wind-up's line. */
    public static final float BREATH_PER_SECOND = 60;
    /** Landing read (D158): it runs until its muzzle is this far short of where the hero will land. */
    public static final float READ_STAND_OFF = 36;
    /** The jump bite's box: this far either side of its body's centre, up to {@link #POUNCE_HIGH} above its floor (its low edge is the lunge box's top). */
    public static final float POUNCE_HALF_WIDTH = 48, POUNCE_HIGH = 64, POUNCE_SECONDS = .2f;
    /** The jump back: this far in this long, drawn lifted, then a recovery long enough to step in and swing. */
    public static final float HOP_REACH = 40, HOP_SECONDS = .2f, HOP_RECOVER_SECONDS = 1.4f;
    /** A route's links: a step this high at most, a gap this wide at most; the leap's fixed time and arc. */
    public static final float LEAP_STEP_MAX = 16, LEAP_GAP_MAX = 48, LEAP_SECONDS = .3f;
    private static final float LEAP_LIFT = 16, HOP_LIFT = 6;
    /** Scales {@code s * (1 - s)} so a leap's arc peaks at {@link #LEAP_LIFT}. */
    private static final float PARABOLA = 4;
    /** A hero whose feet fall no faster than this stands (a walk off a ledge passes it on the second frame). */
    private static final float STANDING_FALL = 25 * Player.PROPORTION;
    /** Another leg wins over the one the hero was last on only by this much, so a hero at a link does not make it dither. */
    private static final float LEG_HYSTERESIS = 4;
    private static final float ARC_PER_SECOND = 60, MARK_PER_SECOND = 40, PATH_STEPS = 8;
    private static final float JAW_CENTRE_X = 43.5f, JAW_CENTRE_Y = 12.5f;
    private static final float SKID_DUST_SECONDS = .04f, DRAW_BACK = 1;
    private static final float ARRIVE = .5f, TURN_DEAD_ZONE = 2, STOPPED_SPEED = 20;
    /** The lunge's reach is traced over floor in steps this long. */
    private static final float REACH_STEP = 2, FLOOR_PROBE = 4;
    /** The jaws are tested at places at most this far apart along their path in a frame (less than the jaw box is wide). */
    private static final float SWEEP_STEP = 4;
    /** Float slack so the reach's far end (a whole number of steps) is included, and a box hanging this far above the floor line is not on it. */
    private static final float REACH_SLACK = .001f, FLOOR_TOUCH = .5f;

    /** The death: the poof starts this long after the hit; the body drops in a hop this high and long. */
    public static final float POOF_DELAY = .08f;
    private static final float FLIP_LIFT = 3, FLIP_SECONDS = .2f;
    /** The active {@code dead} frame holds this long, then cross-fades into the body sheet over the second (D101; the same live palette since D117). */
    public static final float BODY_FADE_DELAY = .3f, BODY_FADE_SECONDS = .6f;
    /** The body twitches when the hero's body enters its box grown this many px left and right (D101): a 1 px shudder for this long. */
    public static final float NEAR_REACH = 6, TWITCH_SECONDS = .3f;
    private static final float TWITCH_STEP = .05f, TWITCH_PIXELS = 1;
    public static final float BURN_SECONDS = .3f;
    private static final float BURN_FLAMES_PER_SECOND = 70, CHAR_TINT = .15f;
    private static final float LANDING_TOLERANCE = 2;
    private static final float BODY_CENTRE_ABOVE_FLOOR = BODY_HEIGHT / 2f;

    static final String NOTICE_SOUND = "wolf_notice", WINDUP_SOUND = "wolf_windup", WINDUP_LONG_SOUND = "wolf_windup_long",
        LUNGE_SOUND = "wolf_lunge", BITE_SOUND = "wolf_bite", BAY_SOUND = "wolf_bay", RUN_SOUND = "wolf_run", DEATH_SOUND = "bounce",
        READ_SOUND = "wolf_read", POUNCE_SOUND = "wolf_pounce", FLINCH_SOUND = "wolf_flinch", LEAP_SOUND = "wolf_leap";
    public static final String CRUMBLE_SOUND = "remains_crumble", BURN_SOUND = "remains_burn";

    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    private final TextureRegion bodyFrame;
    private final Route route;
    private final float spawnX;
    private final Rectangle heroScratch = new Rectangle(), boxScratch = new Rectangle(), nearScratch = new Rectangle(), moverScratch = new Rectangle(),
        sightScratch = new Rectangle(), jawScratch = new Rectangle(), commitScratch = new Rectangle(), pounceScratch = new Rectangle();
    private Level level;
    private boolean inQuickSand;
    private Escalator currentEscalator;
    private float stateClock, frameClock, senseClock, bayClock, coolClock, breathDue, warningTotal, speed, gallopClock, dustDue, flameDue,
        deadClock, twitchClock = TWITCH_SECONDS;
    private boolean trotFrame, cooling, heroNear, moverNear, poofDone, bitePending, bitThisLunge, bayed;
    private int noticeStep, lastGallopFrame;
    // The lunge: from where, to where (the line the wind-up drew), and the jaw box's place last frame, for the sweep.
    private float lungeFromX, lungeToX, lastJawX, lastJawY, slideLeft;
    // The develop (D158): the leg it stands on and the hero stood by, what this jump and this engagement have already had answered.
    private int leg, heroLeg, swingSeen, swingCount;
    private boolean atBay, airAnswered, readPending, leapChasing;
    private float recoverFor = RECOVER_SECONDS, tellTotal, markDue, arcDue, readX, readSpotX;
    private float fromX, fromY, toX, toY;
    private int leapStep;

    /** A wolf on one stretch of floor: the Tiled object's {@code x}, {@code y} (the frame's top) and {@code offNeg}/{@code offPos}. */
    public Wolf(Assets assets, GameContext game, float x, float y, float w, float h, float offNeg, float offPos) {
        this(assets, game, x, y, w, h, Route.range(x, y, offNeg, offPos), false);
    }

    /** A wolf on a route (a Tiled polyline, D158): it starts at the polyline's first point, facing along it. */
    public Wolf(Assets assets, GameContext game, Route route) {
        this(assets, game, route.startX(), route.floorY(route.startLeg()) - FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT, route,
            !route.startsAtRight());
    }

    private Wolf(Assets assets, GameContext game, float x, float y, float w, float h, Route route, boolean startsRight) {
        super(assets, game, "wolf", x, y, w, h, 0, 0, State.class);
        this.route = route;
        spawnX = x;
        hitbox = Hitbox.rect(BODY_X, BODY_Y, BODY_WIDTH, BODY_HEIGHT);
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.MONSTER_WOLF.frame(assets, i, 0);
        bodyFrame = SpriteSheets.MONSTER_WOLF_BODY.frame(assets, 0, 0);
        setLeg(route.startLeg());
        heroLeg = leg;
        moveDirection = startsRight ? 1 : -1;
        facingRight = startsRight;
        current = State.TROT;
    }

    private void setLeg(int next) {
        leg = next;
        rangeNeg = route.lo[next];
        rangePos = route.hi[next];
    }

    public void setLevel(Level level) { this.level = level; }

    public float rangeNeg() { return rangeNeg; }
    public float rangePos() { return rangePos; }
    public State state() { return current; }
    public float warningTotal() { return warningTotal; }
    /** Seconds of the wind-up left (0 outside it). */
    public float warningRemaining() { return current == State.WINDUP ? Math.max(0, warningTotal - stateClock) : 0; }
    /** True from the commit to the end of the bite: the jaws are coming or here. */
    public boolean isCommitted() { return current == State.WINDUP || current == State.LUNGE || current == State.BITE; }
    /** True when a stomp kills it (D158): committed, recovering, or bayed (its steps under a perched hero included). */
    public boolean stompKills() {
        return switch (current) {
            case WINDUP, LUNGE, BITE, RECOVER, BAY -> true;
            case CHASE, SKID -> atBay;
            default -> false;
        };
    }
    /** The route leg it stands on, from the left (0 on a wolf without a route). */
    public int leg() { return leg; }
    /** Where a read jump sends it (its {@code position.x}), while it runs there and waits. */
    public boolean isReading() { return current == State.READ || readPending; }
    public float readTargetX() { return readX; }
    /** Swings met at its wind-up in this engagement (D158): odd ones make it hop back, even ones it waits out. */
    public int swingsMet() { return swingCount; }
    public float speed() { return speed; }
    /** True after it gave up on a hero and until he has been out of its sight for {@link #COOL_SECONDS}. */
    public boolean isCooling() { return cooling; }
    /** Where the wind-up's line ends (the body's x then): valid from the commit to the end of the lunge. */
    public float lungeTargetX() { return lungeToX; }
    /** The twitch of the body, in px: 0 at rest and outside its {@link #TWITCH_SECONDS}. */
    public float twitch() { return current == State.DEAD && twitchClock < TWITCH_SECONDS ? twitchOffset() : 0; }

    /** The floor line the wolf stands on: the bottom of its body. */
    public float floorY() { return position.y + hitbox.offsetY + hitbox.height; }
    public float centreX() { return position.x + PIVOT_X; }
    /** The jaw box's front edge, facing the way it faces: the muzzle. */
    public float jawFrontX() { return facingRight ? position.x + MUZZLE_X : position.x + SIDES * PIVOT_X - MUZZLE_X; }

    /** The jaw box in world space, written into {@code out} (returned). */
    public Rectangle jawBoxInto(Rectangle out) { return boundsInto(JAWS, out); }

    /** The lunge box that makes it commit, written into {@code out} (returned): from the jaws' back, through the muzzle, ahead. */
    public Rectangle commitBoxInto(Rectangle out) {
        Rectangle jaws = jawBoxInto(jawScratch);
        float top = floorY() - COMMIT_HEIGHT;
        if (facingRight) return out.set(jaws.x, top, jaws.width + COMMIT_REACH, COMMIT_HEIGHT);
        return out.set(jaws.x - COMMIT_REACH, top, jaws.width + COMMIT_REACH, COMMIT_HEIGHT);
    }

    @Override
    public void revisit(Player nextPlayer) {
        super.revisit(nextPlayer);
        // A wolf left behind returns calm at its spawn, facing its authored way (D3, D6).
        setLeg(route.startLeg());
        position.set(spawnX, route.floorY(leg) - FRAME_HEIGHT);
        velocity.setZero();
        facingRight = !route.single() && !route.startsAtRight();
        moveDirection = facingRight ? 1 : -1;
        bitePending = bitThisLunge = cooling = heroNear = poofDone = trotFrame = atBay = airAnswered = readPending = false;
        speed = slideLeft = gallopClock = deadClock = senseClock = bayClock = coolClock = lungeFromX = lungeToX = lastJawX = lastJawY = 0;
        lastGallopFrame = swingCount = 0;
        heroLeg = leg;
        recoverFor = RECOVER_SECONDS;
        twitchClock = TWITCH_SECONDS;
        enter(State.TROT);
    }

    private void enter(State state) {
        current = state;
        stateClock = frameClock = breathDue = dustDue = flameDue = markDue = arcDue = 0;
        noticeStep = 0;
    }

    @Override
    public void update(float dt) {
        if (!game.isGameStarted() || isRemoved()) return;
        if (current == State.DEAD || current == State.BURN) {
            deadClock += dt;
            if (!poofDone && deadClock >= POOF_DELAY) poof();
            slide(dt);
            if (fall(dt)) return;
            if (current == State.DEAD) lie(dt);
            else burn(dt);
            return;
        }
        updateEnemy(dt);
    }

    @Override
    public void updateEnemy(float dt) {
        if (bitePending) { // last frame's bite: nothing killed the wolf since, so it lands
            bitePending = false;
            if (heroPresent()) player.collidedWithActor(false);
        }
        stateClock += dt;
        velocity.x = 0;
        trackHero();
        if (current == State.CHASE || current == State.SKID) tryAirMoves();
        switch (current) {
            case TROT -> trot(dt);
            case SNIFF -> sniff(dt);
            case NOTICE -> notice();
            case CHASE -> chase(dt);
            case SKID -> skid(dt);
            case READ -> read(dt);
            case POUNCE_COIL -> pounceCoil(dt);
            case POUNCE -> { pounce(); return; } // along the arc locked at the tell: no gravity until it bites
            case WINDUP -> windup(dt);
            case LUNGE -> { lunge(); return; } // a rush along the line the wind-up drew: no gravity, no floor test until it lands
            case BITE -> bite();
            case RECOVER -> recover();
            case FLINCH -> flinch();
            case HOP -> hop();
            case HOLD -> hold(dt);
            case LEAP_COIL -> leapCoil();
            case LEAP -> { leap(); return; } // a fixed arc between two legs, checked clear at load: no gravity, no collision
            case BAY -> bay(dt);
            default -> { }
        }
        if (isRemoved() || current == State.LEAP) return;
        float was = position.x;
        position.x += velocity.x * dt;
        clampToRange(was);
        checkHorizontalCollisions(level);
        applyGravity(dt);
        checkVerticalCollisions(level, dt);
    }

    private boolean heroPresent() { return player != null && !player.isRemoved() && player.canLeaveRoom(); }

    /** It stays in its range, but a wolf the lunge left past an end may trot back in, and is never snapped back. */
    private void clampToRange(float was) {
        float low = Math.min(rangeNeg, was), high = Math.max(rangePos, was);
        if (position.x < low) position.x = low;
        else if (position.x > high) position.x = high;
    }

    // ---- patrol ----------------------------------------------------------------------------------------------------

    private void trot(float dt) {
        if (notices(dt)) { startNotice(); return; }
        boolean atEnd = (moveDirection < 0 && position.x <= rangeNeg) || (moveDirection > 0 && position.x >= rangePos);
        if (atEnd && route.hasLeg(leg + (int) moveDirection)) { startLeap((int) moveDirection, false); return; }
        if (atEnd) { enter(State.SNIFF); return; }
        facingRight = moveDirection > 0;
        velocity.x = moveDirection * PATROL_SPEED;
        frameClock += dt;
        while (frameClock >= TROT_FRAME_SECONDS) { frameClock -= TROT_FRAME_SECONDS; trotFrame = !trotFrame; }
    }

    /** At a range end: it stops, head down, then turns round; it may still notice the hero. */
    private void sniff(float dt) {
        if (notices(dt)) { startNotice(); return; }
        if (stateClock < SNIFF_SECONDS) return;
        moveDirection = -moveDirection;
        facingRight = moveDirection > 0;
        enter(State.TROT);
    }

    /**
     * True when it notices the hero now: it hears him, or sees him ahead. After a give-up it ignores him until he has been out of
     * its sight (and hearing) for {@link #COOL_SECONDS}: the clock runs here, while it patrols.
     */
    private boolean notices(float dt) {
        if (!heroPresent()) { if (cooling) coolClock += dt; return false; }
        boolean sensed = hears() || sees(facingRight);
        if (!cooling) return sensed;
        coolClock = sensed ? 0 : coolClock + dt;
        if (coolClock >= COOL_SECONDS) cooling = false;
        return false;
    }

    /** It notices: it faces the hero, stops and shows it (D100), with its own call. */
    private void startNotice() {
        faceHero();
        enter(State.NOTICE);
        speed = 0;
        senseClock = bayClock = 0;
        bayed = atBay = airAnswered = readPending = false;
        swingCount = 0;
        if (game.playSounds()) game.playSound(NOTICE_SOUND);
    }

    private void faceHero() { facingRight = directionToPlayer(facingRight ? 1 : -1) > 0; }

    private void notice() {
        float elapsed = 0;
        noticeStep = NOTICE_FRAMES.length - 1;
        for (int i = 0; i < NOTICE_SECONDS.length; i++) {
            elapsed += NOTICE_SECONDS[i];
            if (stateClock < elapsed) { noticeStep = i; break; }
        }
        if (stateClock >= NOTICE_TOTAL) enter(State.CHASE);
    }

    // ---- the chase -------------------------------------------------------------------------------------------------

    /**
     * It runs to a target inside its range, under the hero where it can and else the range end, and commits when the hero's body
     * touches the lunge box on the way. At the target, with the hero out of its reach, it stops facing him and bays. Turning costs
     * a skid: it brakes to a stop before it starts the other way.
     */
    private void chase(float dt) {
        if (!heroPresent()) { giveUp(); return; }
        senseClock = senses() ? 0 : senseClock + dt;
        if (senseClock >= LOSE_SECONDS) { giveUp(); return; }
        int across = readPending ? 0 : Integer.signum(heroLeg - leg);
        float target = readPending ? readX : across != 0 ? (across > 0 ? rangePos : rangeNeg) : targetX(), dx = target - position.x;
        if (Math.abs(dx) > TURN_DEAD_ZONE && (dx > 0) != facingRight) {
            if (speed > STOPPED_SPEED) { enter(State.SKID); return; }
            facingRight = dx > 0;
            speed = 0;
        }
        if (inCommitBox() && commit()) return;
        if (Math.abs(dx) <= ARRIVE && speed <= STOPPED_SPEED) {
            if (across != 0) { position.x = target; startLeap(across, true); }
            else arrive(target);
            return;
        }
        float distance = Math.abs(dx);
        float wanted = Math.min(CHASE_SPEED, (float) Math.sqrt(SIDES * BRAKING * distance));
        speed = speed < wanted ? Math.min(wanted, speed + ACCELERATION * dt) : Math.max(wanted, speed - BRAKING * dt);
        float step = speed * dt;
        velocity.x = (facingRight ? 1 : -1) * (step >= distance ? distance / dt : speed);
        gallop(dt);
    }

    /**
     * At its target: it holds, faces the hero (or the spot a read jump will land on), and, the hero standing out of its reach,
     * bays. It never bays under a hero in the air (D158): he has not taken a refuge yet.
     */
    private void arrive(float target) {
        speed = 0;
        position.x = target;
        if (readPending) facingRight = readSpotX > centreX();
        else faceHero();
        if (inCommitBox() && commit()) return;
        if (readPending || heroAirborne()) return;
        enter(State.BAY);
        atBay = true;
        if (!bayed) { if (game.playSounds()) game.playSound(BAY_SOUND); bayed = true; }   // once per stop, not each time it follows him along a ledge
    }

    /** Bayed: it holds facing the hero; he steps into its reach and it commits, he moves along and it follows, or it gives up. */
    private void bay(float dt) {
        if (!heroPresent()) { giveUp(); return; }
        senseClock = senses() ? 0 : senseClock + dt;
        bayClock += dt;
        if (senseClock >= LOSE_SECONDS || bayClock >= BAY_SECONDS) { giveUp(); return; }
        faceHero();
        if (inCommitBox() && commit()) return;
        if (heroLeg != leg || Math.abs(targetX() - position.x) > ARRIVE + TURN_DEAD_ZONE) enter(State.CHASE);
    }

    /** Where it wants its body: its x under the hero's centre, kept inside its range. */
    private float targetX() {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        return MathUtils.clamp(hero.x + hero.width / 2f - PIVOT_X, rangeNeg, rangePos);
    }

    /** It still has the hero: it sees him ahead or behind, or hears him. */
    private boolean senses() { return hears() || sees(true) || sees(false); }

    private void skid(float dt) {
        speed = Math.max(0, speed - BRAKING * dt);
        velocity.x = (facingRight ? 1 : -1) * speed;
        dustDue += dt;
        while (dustDue >= SKID_DUST_SECONDS) {
            dustDue -= SKID_DUST_SECONDS;
            if (level != null) level.wolfFx().skid(centreX(), floorY(), facingRight ? 1 : -1);
        }
        if (speed <= 0) { facingRight = !facingRight; enter(State.CHASE); }
    }

    /** The gallop frames, a little slower while it is still speeding up; the paw patter once a cycle, in view only. */
    private void gallop(float dt) {
        gallopClock += dt * speed / CHASE_SPEED;
        int frame = ((int) (gallopClock / GALLOP_FRAME_SECONDS)) % RUN_FRAMES;
        if (frame == 0 && lastGallopFrame != 0 && game.playSounds() && level != null && level.inCameraView(bodyBoundsInto())) game.playSound(RUN_SOUND);
        lastGallopFrame = frame;
    }

    private Rectangle bodyBoundsInto() { return hitboxBoundsInto(nearScratch); }

    private void giveUp() {
        cooling = true;
        coolClock = 0;
        speed = 0;
        atBay = readPending = false;
        swingCount = 0;
        moveDirection = position.x > (route.lo[0] + route.hi[route.legs - 1]) / 2f ? -1 : 1;
        facingRight = moveDirection > 0;
        enter(State.TROT);
    }

    // ---- senses ----------------------------------------------------------------------------------------------------

    /** Hearing: a circle around the body's centre, through anything. */
    private boolean hears() {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        Rectangle self = hitboxBoundsInto(nearScratch);
        float dx = hero.x + hero.width / 2f - (self.x + self.width / 2f), dy = hero.y + hero.height / 2f - (self.y + self.height / 2f);
        return dx * dx + dy * dy <= HEAR_RADIUS * HEAR_RADIUS;
    }

    /** Sight that way: a box from the muzzle (or, behind it, the body's back) {@link #SIGHT_RANGE} px out, cut at the first solid. */
    private boolean sees(boolean right) {
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float floor = floorY();
        if (hero.y >= floor || hero.y + hero.height <= floor - SIGHT_BAND) return false;
        float start;
        if (right == facingRight) start = jawFrontX();
        else {
            Rectangle body = hitboxBoundsInto(sightScratch);
            start = right ? body.x + body.width : body.x;
        }
        float near = right ? hero.x : hero.x + hero.width;
        if (right ? near > start + SIGHT_RANGE || hero.x + hero.width < start : near < start - SIGHT_RANGE || hero.x > start) return false;
        float end = sightEnd(start, right);
        sightScratch.set(Math.min(start, end), floor - SIGHT_BAND, Math.abs(end - start), SIGHT_BAND);
        return sightScratch.overlaps(hero);
    }

    /** The x where sight from {@code start} is cut: the first solid at eye height, or {@link #SIGHT_RANGE} px out. */
    private float sightEnd(float start, boolean right) {
        float dir = right ? 1 : -1;
        if (level == null) return start + dir * SIGHT_RANGE;
        float y = floorY() - EYE_HEIGHT - SIGHT_PROBE / 2f;
        for (float d = 0; d < SIGHT_RANGE; d += SIGHT_STEP) {
            float x = start + dir * d;
            if (level.blocksObject(boxScratch.set(right ? x : x - SIGHT_PROBE, y, SIGHT_PROBE, SIGHT_PROBE), false)) return x;
        }
        return start + dir * SIGHT_RANGE;
    }

    /** True when the hero touches the lunge box, on its own leg: it never strikes across a step or a gap (D158). */
    private boolean inCommitBox() {
        return !player.isGotHit() && heroLeg == leg && commitBoxInto(commitScratch).overlaps(player.hitboxBoundsInto(heroScratch));
    }

    // ---- the lunge (D115) --------------------------------------------------------------------------------------------

    /**
     * It commits where it stands and locks its direction: the line the jaws will cover is traced now over the floor under it, a
     * solid or the floor's end cutting it short, and the wind-up draws exactly that line. The shared bite warning is sampled once.
     */
    private boolean commit() {
        speed = 0;
        atBay = readPending = false;
        swingSeen = player.swingNumber();
        lungeFromX = position.x;
        lungeToX = reachableEnd();
        enter(State.WINDUP);
        if (level != null) level.wolfFx().skid(centreX(), floorY(), facingRight ? 1 : -1);
        TrapWarnings warnings = game.trapWarnings();
        warningTotal = warnings != null ? warnings.duration(TrapWarnings.BITE_WARNING_SECONDS) : TrapWarnings.BITE_WARNING_SECONDS;
        if (game.playSounds()) game.playSound(warningTotal > TrapWarnings.BITE_WARNING_SECONDS ? WINDUP_LONG_SOUND : WINDUP_SOUND);
        bayClock = 0;
        bayed = false;
        return true;
    }

    /**
     * The furthest x along its facing, in {@link #REACH_STEP} steps from here up to {@link #LUNGE_REACH} px, with floor under the
     * whole body at its floor line and no solid in its way: a lunge never leaves its floor and never goes through a wall (#497's
     * rule). Without a level the full reach stands.
     */
    private float reachableEnd() {
        float dir = facingRight ? 1 : -1;
        if (level == null) return position.x + dir * LUNGE_REACH;
        float good = position.x;
        for (float d = REACH_STEP; d <= LUNGE_REACH + REACH_SLACK; d += REACH_STEP) {
            float x = position.x + dir * d;
            if (!floorAt(x) || solidAt(x)) break;
            good = x;
        }
        return good;
    }

    /** True when the body at {@code x} on this floor line has authored floor under every 4 px of it (not a ladder's top step). */
    private boolean floorAt(float x) {
        float floor = floorY(), left = x + BODY_X, right = left + BODY_WIDTH;
        for (float column = left; column <= right + FLOOR_PROBE - 1; column += FLOOR_PROBE) {
            if (!level.spiderFloorAt(boxScratch.set(Math.min(column, right - 1), floor + 1, 1, 1))) return false;
        }
        return true;
    }

    /** True when a solid is in the body's way at {@code x}, the body raised a pixel off its floor line. */
    private boolean solidAt(float x) { return solidAt(x, position.y); }

    private boolean solidAt(float x, float y) {
        Rectangle body = hitboxBoundsInto(boxScratch);
        body.set(body.x + x - position.x, body.y + y - position.y, body.width, body.height - 1);
        return level.blocksObject(body, false);
    }

    private void windup(float dt) {
        breathe(dt);
        if (metSwing()) return;
        if (stateClock >= warningTotal) {
            startSweep();
            enter(State.LUNGE);
            if (game.playSounds()) game.playSound(LUNGE_SOUND);
        }
    }

    private void breathe(float dt) {
        breathDue += BREATH_PER_SECOND * dt;
        float jaw = jawFrontX(), end = lungeToX + (jaw - position.x), floor = floorY();
        while (breathDue >= 1) { breathDue -= 1; if (level != null) level.wolfFx().breath(jaw, end, floor); }
    }

    /** The jaws start to sweep here: one bite at most until the next start. */
    private void startSweep() {
        Rectangle jaws = jawBoxInto(jawScratch);
        lastJawX = jaws.x;
        lastJawY = jaws.y;
        bitThisLunge = false;
    }

    /**
     * The rush: straight along the line the wind-up drew, in {@link #LUNGE_SECONDS} (a shorter line at the same speed); the jaws
     * are lethal all the way, swept from last frame's front. A solid in the way stops it where it is, and it bites there.
     */
    private void lunge() {
        float was = position.x, line = Math.abs(lungeToX - lungeFromX), flown = Math.min(line, LUNGE_SPEED * stateClock);
        position.x = lungeFromX + (facingRight ? flown : -flown);
        velocity.setZero();
        // The line was traced at the commit; a floor that has gone since (a support that crumbled) or a solid that came stops it at the last
        // good place: it never flies over air.
        if (level != null && (solidAt(position.x) || !floorAt(position.x))) { position.x = was; land(); return; }
        checkBite();
        if (flown >= line) land();
    }

    /** The end of the lunge or the pounce, at its end or against a solid: the bite, seen and heard hit or miss (D93). */
    private void land() {
        enter(State.BITE);
        recoverFor = RECOVER_SECONDS;
        if (level != null) {
            if (floorY() < route.floorY(leg) - LANDING_TOLERANCE) level.wolfFx().snap(jawFrontX(), floorY()); // in the air: no dust
            else level.wolfFx().bite(jawFrontX(), floorY());
        }
        if (game.playSounds()) game.playSound(BITE_SOUND);
        checkBite();
    }

    /** The bite frame; a bite that reached the hero on its last frame lands before the recovery starts. */
    private void bite() {
        checkBite();
        if (stateClock >= BITE_SECONDS && !bitePending) enter(State.RECOVER);
    }

    /** Flat-footed and harmless; after a pounce the time counts from its landing. */
    private void recover() {
        if (velocity.y > 0) { stateClock = 0; return; } // still falling from a jump bite
        if (stateClock < recoverFor) return;
        if (!heroPresent()) { giveUp(); return; }
        faceHero();   // a standing turn, if the hero is behind it
        speed = 0;
        senseClock = 0;
        enter(State.CHASE);
    }

    /**
     * The jaws reach the hero: the bite lands at the start of the next frame unless the wolf is killed first (the hit wins a
     * same-frame tie, as the spider's). The jaw box is swept from its last place along its path, so a fast frame cannot skip over the hero.
     */
    private void checkBite() {
        if (!heroPresent() || bitThisLunge || player.isGotHit()) return;
        Rectangle jaws = jawBoxInto(jawScratch), hero = player.hitboxBoundsInto(heroScratch);
        float toX = jaws.x, toY = jaws.y, dx = toX - lastJawX, dy = toY - lastJawY;
        int steps = Math.max(1, MathUtils.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / SWEEP_STEP));
        for (int i = 0; i <= steps && !bitThisLunge; i++) {
            jaws.setPosition(lastJawX + dx * i / steps, lastJawY + dy * i / steps);
            if (jaws.overlaps(hero)) bitePending = bitThisLunge = true;
        }
        lastJawX = toX;
        lastJawY = toY;
    }

    // ---- the answers to the hero's habits (D158) ----------------------------------------------------------------------

    /** The longer-warnings assist turns the landing read, the jump bite, the jump back and the held lunge off. */
    private boolean movesOn() {
        TrapWarnings warnings = game.trapWarnings();
        return warnings == null || !warnings.longer();
    }

    private boolean heroStanding() { return player.onLadder() || player.isOnGround() && player.velocity.y <= STANDING_FALL; }
    private boolean heroAirborne() { return heroPresent() && !player.isOnGround() && !player.onLadder(); }

    /** What a standing hero settles: the leg he is on, a new jump to answer, a read jump over, and a bay ended in its reach. */
    private void trackHero() {
        if (!heroPresent() || !heroStanding()) return;
        airAnswered = readPending = false;
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float feet = hero.y + hero.height;
        if (!route.single()) heroLeg = legFor(hero.x + hero.width / 2f, feet);
        if (atBay && feet > route.floorY(heroLeg) - COMMIT_HEIGHT && heroLeg == leg) {
            float cx = hero.x + hero.width / 2f;
            if (cx >= rangeNeg - COMMIT_REACH && cx <= rangePos + FRAME_WIDTH + COMMIT_REACH) atBay = false;
        }
    }

    /** The leg a hero standing at ({@code x}, {@code feet}) is on or nearest to; the last one wins a near tie. */
    private int legFor(float x, float feet) {
        int best = heroLeg;
        float bestScore = legDistance(heroLeg, x, feet) - LEG_HYSTERESIS;
        for (int i = 0; i < route.legs; i++) {
            float score = legDistance(i, x, feet);
            if (score < bestScore) { best = i; bestScore = score; }
        }
        return best;
    }

    private float legDistance(int i, float x, float feet) {
        float left = route.lo[i] + BODY_X, right = route.hi[i] + BODY_X + BODY_WIDTH;
        return Math.max(0, Math.max(left - x, x - right)) + Math.abs(route.floorY(i) - feet);
    }

    /**
     * From the chase or its skid, once per jump: an airborne hero with his feet above the lunge box is bitten in the air when he is
     * in the pounce box, else his landing is read. A bayed wolf starts nothing.
     */
    private void tryAirMoves() {
        if (atBay || airAnswered || !heroAirborne() || player.isGotHit() || !movesOn()) return;
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float floor = floorY(), feetAbove = floor - (hero.y + hero.height);
        if (feetAbove < COMMIT_HEIGHT || floor < route.floorY(leg) - LANDING_TOLERANCE) return;
        pounceScratch.set(centreX() - POUNCE_HALF_WIDTH, floor - POUNCE_HIGH, SIDES * POUNCE_HALF_WIDTH, POUNCE_HIGH - COMMIT_HEIGHT);
        if (pounceScratch.overlaps(hero)) startPounce(hero);
        else startRead(hero, feetAbove);
    }

    /** The landing read: where his feet come back to its floor at this speed, and where it wants its body for that. */
    private void startRead(Rectangle hero, float feetAbove) {
        float g = player.gravityPerSecond(), vy = player.velocity.y;
        float t = (-vy + (float) Math.sqrt(vy * vy + SIDES * g * feetAbove)) / g;
        readSpotX = hero.x + hero.width / 2f + player.velocity.x * t;
        float way = readSpotX >= centreX() ? 1 : -1;
        float muzzle = readSpotX - way * (hero.width / 2f + READ_STAND_OFF);
        float muzzleOffset = way > 0 ? MUZZLE_X : SIDES * PIVOT_X - MUZZLE_X;
        readX = MathUtils.clamp(muzzle - muzzleOffset, rangeNeg, rangePos);
        airAnswered = true;
        tellTotal = tell(TrapWarnings.WOLF_READ_SECONDS);
        enter(State.READ);
        if (game.playSounds()) game.playSound(READ_SOUND);
    }

    private float tell(float base) {
        TrapWarnings warnings = game.trapWarnings();
        return warnings != null ? warnings.duration(base) : base;
    }

    /** It brakes and lifts its head after him, marks on the floor where he will land; then it runs there. */
    private void read(float dt) {
        if (!heroPresent()) { giveUp(); return; }
        speed = Math.max(0, speed - BRAKING * dt);
        velocity.x = (facingRight ? 1 : -1) * speed;
        if (speed <= 0) facingRight = readSpotX > centreX();
        markDue += MARK_PER_SECOND * dt;
        while (markDue >= 1) { markDue -= 1; if (level != null) level.wolfFx().mark(readSpotX, route.floorY(leg)); }
        if (stateClock < tellTotal) return;
        readPending = !heroStanding();
        senseClock = 0;
        enter(State.CHASE);
    }

    /**
     * The jump bite: the spot is locked now, where his body will be when the leap arrives; it is not started when the arc would
     * meet a solid, land off its floor or leave its range by more than its reach.
     */
    private void startPounce(Rectangle hero) {
        float total = tell(TrapWarnings.WOLF_POUNCE_SECONDS), t = total + POUNCE_SECONDS, g = player.gravityPerSecond(), floor = floorY();
        float x = hero.x + hero.width / 2f + player.velocity.x * t;
        float feet = Math.min(floor, hero.y + hero.height + player.velocity.y * t + g * t * t / SIDES);
        float aim = MathUtils.clamp(feet - hero.height / 2f, floor - POUNCE_HIGH, floor - (FRAME_HEIGHT - JAW_CENTRE_Y));
        boolean right = x >= centreX();
        float jawOffset = right ? JAW_CENTRE_X : SIDES * PIVOT_X - JAW_CENTRE_X;
        float endX = x - jawOffset, endY = Math.min(position.y, aim - JAW_CENTRE_Y);
        if (!clearPath(endX, endY)) return;
        facingRight = right;
        speed = 0;
        fromX = position.x;
        fromY = position.y;
        toX = endX;
        toY = endY;
        airAnswered = true;
        tellTotal = total;
        enter(State.POUNCE_COIL);
        if (game.playSounds()) game.playSound(POUNCE_SOUND);
    }

    /**
     * True when the way to {@code x} stays on this floor ({@link #clearGround}) and the arc from here up to ({@code x}, {@code y})
     * meets no solid: a jump bite never lands on a block or crosses a gap or a step.
     */
    private boolean clearPath(float x, float y) {
        if (!clearGround(x)) return false;
        if (level == null) return true;
        for (int i = 1; i <= PATH_STEPS; i++) {
            float s = i / PATH_STEPS;
            if (solidAt(MathUtils.lerp(position.x, x, s), position.y + (y - position.y) * rise(s))) return false;
        }
        return true;
    }

    /** The pounce's rise: fast off the ground, slowing into the spot. */
    private static float rise(float s) { return 1 - (1 - s) * (1 - s); }

    private void pounceCoil(float dt) {
        arcDue += ARC_PER_SECOND * dt;
        while (arcDue >= 1) {
            arcDue -= 1;
            if (level == null) continue;
            float s = level.wolfFx().arcPoint(), jaw = jawFrontX() - position.x;
            level.wolfFx().arc(MathUtils.lerp(fromX, toX, s) + jaw, fromY + (toY - fromY) * rise(s) + JAW_CENTRE_Y);
        }
        if (stateClock >= tellTotal) {
            startSweep();
            enter(State.POUNCE);
            if (game.playSounds()) game.playSound(LUNGE_SOUND);
        }
    }

    /** The leap at the locked spot, jaws lethal all the way; it bites there (or against what came in its way) and falls. */
    private void pounce() {
        float s = Math.min(1, stateClock / POUNCE_SECONDS);
        float x = MathUtils.lerp(fromX, toX, s), y = fromY + (toY - fromY) * rise(s);
        velocity.setZero();
        if (level != null && solidAt(x, y)) { land(); return; }
        position.set(x, y);
        checkBite();
        if (s >= 1) land();
    }

    /** A new swing at its wind-up, facing it, the blade not on it yet: the jump back and the wait take turns. True when it acts. */
    private boolean metSwing() {
        if (!movesOn() || !heroPresent() || player.swingNumber() == swingSeen) return false;
        swingSeen = player.swingNumber();
        if (!player.isSwinging() || player.swordReaches(hitboxBoundsInto(nearScratch))) return false;
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        boolean heroLeft = hero.x + hero.width / 2f < centreX();
        if (player.facingRight != heroLeft) return false;
        if ((swingCount & 1) != 0) {
            swingCount++;
            enter(State.HOLD);
            return true;
        }
        float end = position.x + (heroLeft ? HOP_REACH : -HOP_REACH);
        if (!clearGround(end)) return false;
        swingCount++;
        facingRight = !heroLeft;
        fromX = position.x;
        toX = end;
        tellTotal = tell(TrapWarnings.WOLF_FLINCH_SECONDS);
        enter(State.FLINCH);
        if (game.playSounds()) game.playSound(FLINCH_SOUND);
        return true;
    }

    /** True when it can move along its floor to {@code end}: floor and no solid all the way, within its range plus its reach. */
    private boolean clearGround(float end) {
        if (end < rangeNeg - LUNGE_REACH || end > rangePos + LUNGE_REACH) return false;
        if (level == null) return true;
        float way = end > position.x ? 1 : -1, distance = Math.abs(end - position.x);
        for (float d = Math.min(REACH_STEP, distance); ; d = Math.min(d + REACH_STEP, distance)) {
            float x = position.x + way * d;
            if (!floorAt(x) || solidAt(x)) return false;
            if (d >= distance) return true;
        }
    }

    private void flinch() {
        if (stateClock >= tellTotal) enter(State.HOP);
    }

    private void hop() {
        float x = MathUtils.lerp(fromX, toX, Math.min(1, stateClock / HOP_SECONDS));
        boolean blocked = level != null && (solidAt(x) || !floorAt(x));
        if (!blocked) position.x = x;
        if (blocked || stateClock >= HOP_SECONDS) {
            recoverFor = HOP_RECOVER_SECONDS;
            enter(State.RECOVER);
        }
    }

    /** It stays crouched until the blade is down, then winds up in full again; another swing meanwhile is met in its turn. */
    private void hold(float dt) {
        if (!heroPresent()) { giveUp(); return; }
        breathe(dt);
        if (metSwing()) return;
        if (player.isSwinging()) return;
        faceHero();
        commit();
    }

    // ---- the route's leaps (D158) -------------------------------------------------------------------------------------

    /** It crouches at the end of its leg, {@code step} the way of the next one; {@code chasing}: it goes on chasing there. */
    private void startLeap(int step, boolean chasing) {
        leapStep = step;
        leapChasing = chasing;
        speed = 0;
        facingRight = step > 0;
        fromX = position.x;
        toX = step > 0 ? route.lo[leg + step] : route.hi[leg + step];
        toY = route.floorY(leg + step) - FRAME_HEIGHT;
        enter(State.LEAP_COIL);
        if (game.playSounds() && level != null && level.inCameraView(bodyBoundsInto())) game.playSound(LEAP_SOUND);
    }

    private void leapCoil() {
        if (stateClock < TrapWarnings.WOLF_LEAP_SECONDS) return; // movement: not scaled by the assist
        fromY = position.y;
        if (level != null) level.wolfFx().skid(centreX(), floorY(), leapStep);
        enter(State.LEAP);
    }

    /** The fixed arc to the next leg: harmless, a stomp bounces off it. */
    private void leap() {
        float s = Math.min(1, stateClock / LEAP_SECONDS);
        // Up on to a step it rises before it travels, and off one it travels before it drops, so its body clears the step's corner.
        float along = toY < fromY ? s * s : toY > fromY ? rise(s) : s;
        position.set(MathUtils.lerp(fromX, toX, along), MathUtils.lerp(fromY, toY, s) - LEAP_LIFT * PARABOLA * s * (1 - s));
        velocity.setZero();
        if (s < 1) return;
        setLeg(leg + leapStep);
        moveDirection = leapStep;
        if (level != null) level.wolfFx().skid(centreX(), floorY(), leapStep);
        enter(leapChasing ? State.CHASE : State.TROT);
    }

    // ---- being killed and the body (D101) ------------------------------------------------------------------------------

    /**
     * A fireball, a thrown stone or torch ({@code gotHit}) kills it in any state. A stomp always bounces the hero, and kills only a
     * committed or bayed wolf ({@link #stompKills}, D158); the bounce counts as that jump's answer. Touch is harmless.
     */
    @Override
    public void collidedWithActor(boolean gotHit) {
        if (isDefeated()) return;
        if (gotHit) { die(); return; }
        if (!stompedByPlayer()) return; // touch is harmless: only the jaws kill (D115)
        player.velocity.y = -BOUNCE_HEIGHT;
        if (stompKills()) { die(); return; }
        airAnswered = true;
        if (game.playSounds()) game.playSound(DEATH_SOUND);
    }

    /** The sword kills it in any state with the light spray of D65 (the base class); on the body, it crumbles it (D101). */
    @Override
    public void hitBySword(Level level) {
        if (isRemains()) crumble();
        else super.hitBySword(level);
    }

    /** Killed: it drops onto its side and stays as a body (D101); killed in the lunge, the body slides on to the line's end. */
    private void die() {
        slideLeft = current == State.LUNGE ? Math.abs(lungeToX - position.x) : 0;
        enter(State.DEAD);
        atBay = readPending = false;
        velocity.y = 0;
        pendingRemoval = true;
        bitePending = false;
        poofDone = heroNear = moverNear = false;
        twitchClock = TWITCH_SECONDS;
        deadClock = 0;
        speed = 0;
        velocity.setZero();
        if (game.playSounds()) game.playSound(DEATH_SOUND);
    }

    @Override
    protected void setDefeated() { die(); }

    private void poof() {
        poofDone = true;
        float floor = floorY();
        if (level != null) level.wolfFx().poof(centreX(), floor - BODY_CENTRE_ABOVE_FLOOR, floor);
    }

    /** The body slides on to where the lunge would have ended, over the floor, stopping at a solid. */
    private void slide(float dt) {
        if (slideLeft <= 0) return;
        float move = Math.min(slideLeft, LUNGE_SPEED * dt), was = position.x;
        position.x += facingRight ? move : -move;
        slideLeft -= move;
        if (level != null && solidAt(position.x)) { position.x = was; slideLeft = 0; }
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

    /** The body lying (D101): a stomp, the hero coming near (a twitch), and its clock. */
    private void lie(float dt) {
        stateClock += dt;
        if (heroPresent() && stompedOn()) { player.velocity.y = -BOUNCE_HEIGHT; crumble(); return; }
        boolean near = heroPresent() && heroNear();
        if (near && !heroNear) twitchClock = 0;
        heroNear = near;
        // D119: anyone moving through the body makes it twitch, not only the hero.
        Enemy<?> mover = Remains.moverNear(level, this, grownBox(), moverScratch);
        if (mover != null && !moverNear) twitchClock = 0;
        moverNear = mover != null;
        if (twitchClock < TWITCH_SECONDS) twitchClock += dt;
    }

    private float twitchOffset() { return (((int) (twitchClock / TWITCH_STEP)) & 1) == 0 ? TWITCH_PIXELS : -TWITCH_PIXELS; }

    /** The body's box grown {@link #NEAR_REACH} left and right, in {@code nearScratch}. */
    private Rectangle grownBox() {
        Rectangle body = hitboxBoundsInto(nearScratch);
        body.set(body.x - NEAR_REACH, body.y, body.width + SIDES * NEAR_REACH, body.height);
        return body;
    }

    private boolean heroNear() {
        return grownBox().overlaps(player.hitboxBoundsInto(heroScratch));
    }

    private boolean stompedOn() {
        Rectangle body = hitboxBoundsInto(nearScratch);
        return body.overlaps(player.hitboxBoundsInto(heroScratch)) && player.velocity.y > 0
            && player.previousFeetY() <= body.y + LANDING_TOLERANCE;
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
        if (level != null) level.remainsFx().crumble(centreX(), floorY(), WolfFx.WOLF_CHIPS);
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

    // ---- drawing -------------------------------------------------------------------------------------------------------

    int frameIndex() {
        return switch (current) {
            case TROT -> trotFrame ? TROT2 : TROT1;
            case SNIFF -> TROT1;
            case NOTICE -> NOTICE_FRAMES[noticeStep];
            case CHASE -> speed > STOPPED_SPEED ? RUN1 + ((int) (gallopClock / GALLOP_FRAME_SECONDS)) % RUN_FRAMES : STAND;
            case SKID -> SKID_FRAME;
            case WINDUP -> CROUCH;
            case LUNGE -> LUNGE_FRAME;
            // In the air after a jump bite it keeps the leap's shape (the shut-jawed bite loses its outline against the sky, D76); the
            // glints and the sound show the bite.
            case BITE -> floorY() < route.floorY(leg) - LANDING_TOLERANCE ? LEAP_FRAME : BITE_FRAME;
            case RECOVER -> SHAKE;
            case BAY -> BAY_FRAME;
            case READ -> speed > STOPPED_SPEED ? SKID_FRAME : READ_FRAME;
            case POUNCE_COIL, FLINCH, LEAP_COIL -> COIL_FRAME;
            case POUNCE, LEAP -> LEAP_FRAME;
            case HOP -> RUN1 + 1;
            case HOLD -> CROUCH;
            case DEAD, BURN -> DEAD_FRAME;
        };
    }

    /** How far the body has faded from the active {@code dead} frame into the body sheet: 0 at the kill, 1 once settled. */
    public float bodyFade() {
        if (current != State.DEAD && current != State.BURN) return 0;
        return Math.min(1f, Math.max(0f, (deadClock - BODY_FADE_DELAY) / BODY_FADE_SECONDS));
    }

    @Override
    public void render(Batch batch) {
        if (current == State.DEAD || current == State.BURN) { renderBody(batch); return; }
        float dx = current == State.WINDUP || current == State.HOLD ? (facingRight ? -DRAW_BACK : DRAW_BACK) : 0;
        TextureRegion frame = frames[frameIndex()];
        float lift = current == State.HOP ? HOP_LIFT * MathUtils.sin(MathUtils.PI * Math.min(1, stateClock / HOP_SECONDS)) : 0;
        float x = position.x + dx, y = position.y - lift;
        if (facingRight) batch.draw(frame, x, y, FRAME_WIDTH, FRAME_HEIGHT);
        else batch.draw(frame, x + SIDES * PIVOT_X, y, -FRAME_WIDTH, FRAME_HEIGHT);
    }

    /** The body: the drop, the cross-fade into the body sheet, the twitch, and while burning a darkening tint. */
    private void renderBody(Batch batch) {
        float lift = deadClock < FLIP_SECONDS ? FLIP_LIFT * MathUtils.sin(MathUtils.PI * deadClock / FLIP_SECONDS) : 0;
        float shift = twitch(), y = position.y - lift;
        float drawX = facingRight ? position.x + shift : position.x + SIDES * PIVOT_X + shift;
        float width = facingRight ? FRAME_WIDTH : -FRAME_WIDTH;
        float saved = batch.getPackedColor();
        float fade = bodyFade();
        float tint = current == State.BURN ? 1f - (1f - CHAR_TINT) * Math.min(1f, stateClock / BURN_SECONDS) : 1f;
        if (fade < 1) {
            batch.setColor(tint, tint, tint, 1f - fade);
            batch.draw(frames[DEAD_FRAME], drawX, y, width, FRAME_HEIGHT);
        }
        if (fade > 0) {
            batch.setColor(tint, tint, tint, fade);
            batch.draw(bodyFrame, drawX, y, width, FRAME_HEIGHT);
        }
        batch.setPackedColor(saved);
    }

    // ---- load-time checks (D115, D158) ---------------------------------------------------------------------------------

    /**
     * The load-time checks (D115), on the 48x24 wolf whatever the Tiled object's size ({@code y} is the frame's top): a range at
     * least {@link #MIN_RANGE} px long inside the room, on floor without gaps under its whole range; no standing place (the
     * entrance body, the room exit, a checkpoint) inside its sight or its reach at any point of the range, facing either way; and
     * {@link #HEADROOM} px of headroom above its floor over its whole reach (no {@code solids} box hangs into that strip). Null
     * when the wolf is fine, else what is wrong. {@code floors}: every collision box a monster may stand on; {@code solids}: those
     * that block it in level flight (not one-way platforms).
     */
    public static String problem(float x, float y, float w, float h, float offNeg, float offPos, List<Rectangle> floors,
                                 List<Rectangle> solids, Rectangle room, Map<String, Rectangle> standing) {
        if (!(w > 0) || !(h > 0)) return "needs positive bounds";
        if (!Float.isFinite(offNeg) || !Float.isFinite(offPos) || offNeg < 0 || offPos < 0) return "needs finite, non-negative offNeg and offPos";
        float low = x - offNeg * TILE_SIZE, high = x + offPos * TILE_SIZE;
        if (high - low < MIN_RANGE) return "needs a range of at least " + (int) MIN_RANGE + " px";
        return legProblem(low, high, y + FRAME_HEIGHT, floors, solids, room, standing);
    }

    /**
     * The load checks of a wolf on a route (D158): the route itself ({@link Route#trace}), at least {@link #MIN_RANGE} px from end
     * to end, every leg held to a range wolf's checks, and over every link, from the higher floor up {@link #HEADROOM} px, no solid.
     */
    public static String routeProblem(float[] points, List<Rectangle> floors, List<Rectangle> solids, Rectangle room,
                                      Map<String, Rectangle> standing) {
        Route r;
        try {
            r = Route.trace(points);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        if (r.hi[r.legs - 1] - r.lo[0] < MIN_RANGE) return "needs a route of at least " + (int) MIN_RANGE + " px from end to end";
        for (int i = 0; i < r.legs; i++) {
            String leg = legProblem(r.lo[i], r.hi[i], r.line[i], floors, solids, room, standing);
            if (leg != null) return leg;
        }
        Rectangle arc = new Rectangle();
        for (int i = 0; i + 1 < r.legs; i++) {
            float top = Math.min(r.line[i], r.line[i + 1]);
            float left = r.hi[i] + BODY_X, right = r.lo[i + 1] + BODY_X + BODY_WIDTH;
            arc.set(left, top - HEADROOM, right - left, HEADROOM - FLOOR_TOUCH);
            for (Rectangle s : solids) if (s.overlaps(arc)) return "has a solid in the way of its leap at x " + (int) s.x;
        }
        return null;
    }

    private static String legProblem(float low, float high, float floor, List<Rectangle> floors, List<Rectangle> solids, Rectangle room,
                                     Map<String, Rectangle> standing) {
        if (low < room.x || high + FRAME_WIDTH > room.x + room.width || floor - FRAME_HEIGHT < room.y || floor > room.y + room.height)
            return "has its range leaving the room";
        float left = low + BODY_X, right = high + BODY_X + BODY_WIDTH;
        float covered = left;
        List<Rectangle> spans = new ArrayList<>();
        for (Rectangle f : floors) if (Math.abs(f.y - floor) < 1f) spans.add(f);
        spans.sort((a, b) -> Float.compare(a.x, b.x));
        for (Rectangle f : spans) if (f.x <= covered + 1f) covered = Math.max(covered, f.x + f.width);
        if (covered < right) return "must trot on floor without gaps under its whole range";
        float pivot = PIVOT_X, muzzleRight = MUZZLE_X, muzzleLeft = SIDES * pivot - MUZZLE_X;
        float senseLeft = low + muzzleLeft - Math.max(SIGHT_RANGE, COMMIT_REACH + LUNGE_REACH), senseRight = high + muzzleRight + Math.max(SIGHT_RANGE, COMMIT_REACH + LUNGE_REACH);
        Rectangle reach = new Rectangle(senseLeft, floor - SIGHT_BAND, senseRight - senseLeft, SIGHT_BAND);
        for (Map.Entry<String, Rectangle> place : standing.entrySet())
            if (reach.overlaps(place.getValue())) return "has the room " + place.getKey() + " in its sight or reach (" + (int) SIGHT_RANGE + " px)";
        float reachLeft = low + muzzleLeft - LUNGE_REACH, reachRight = high + muzzleRight + LUNGE_REACH;
        Rectangle strip = new Rectangle(reachLeft, floor - HEADROOM, reachRight - reachLeft, HEADROOM - 1);
        for (Rectangle s : solids)
            if (s.overlaps(strip) && s.y + s.height < floor - FLOOR_TOUCH) return "has less than " + (int) HEADROOM + " px of headroom over its reach";
        return null;
    }

    /**
     * Where a wolf may trot: floor legs from left to right, each a range of {@code position.x} on its floor line. A range wolf has
     * one. A Tiled polyline (D158) names each leg by the two ends of the floor the body keeps to, so it has an even number of points,
     * its x always moving one way; between one leg and the next is a link it leaps: a step of at most {@link #LEAP_STEP_MAX} px or a
     * gap of at most {@link #LEAP_GAP_MAX} px. The wolf starts at the first point.
     */
    public static final class Route {
        private static final int XY = 2, LEG_POINTS = 2;
        final float[] lo, hi, line;
        final int legs;
        private final float start;
        private final int startLeg;
        private final boolean startsAtRight;

        private Route(int legs, float start, int startLeg, boolean startsAtRight) {
            this.legs = legs;
            lo = new float[legs];
            hi = new float[legs];
            line = new float[legs];
            this.start = start;
            this.startLeg = startLeg;
            this.startsAtRight = startsAtRight;
        }

        static Route range(float x, float y, float offNeg, float offPos) {
            Route r = new Route(1, x, 0, true);
            r.lo[0] = x - offNeg * TILE_SIZE;
            r.hi[0] = x + offPos * TILE_SIZE;
            r.line[0] = y + FRAME_HEIGHT;
            return r;
        }

        /** The route of a polyline; throws with the reason when the polyline is not one. */
        public static Route trace(float[] points) {
            if (points == null || points.length < XY * LEG_POINTS || points.length % (XY * LEG_POINTS) != 0)
                throw new IllegalArgumentException("needs a polyline of floor legs: two points each, an even number in all");
            for (float p : points) if (!Float.isFinite(p)) throw new IllegalArgumentException("has a broken point");
            int count = points.length / XY, legs = count / LEG_POINTS;
            boolean leftward = points[XY] < points[0];
            float[] x = new float[count], y = new float[count];
            for (int i = 0; i < count; i++) { // read from the left end whichever way it was drawn
                int from = leftward ? count - 1 - i : i;
                x[i] = points[XY * from];
                y[i] = points[XY * from + 1];
            }
            Route r = new Route(legs, 0, leftward ? legs - 1 : 0, leftward);
            for (int i = 0; i < legs; i++) {
                int a = LEG_POINTS * i, b = a + 1;
                if (y[a] != y[b]) throw new IllegalArgumentException("has a floor leg that is not level at x " + (int) x[a]);
                if (x[b] - x[a] < BODY_WIDTH)
                    throw new IllegalArgumentException("has a floor leg shorter than its body (" + (int) BODY_WIDTH + " px) or running back at x " + (int) x[a]);
                r.lo[i] = x[a] - BODY_X;
                r.hi[i] = x[b] - BODY_X - BODY_WIDTH;
                r.line[i] = y[a];
                if (i == 0) continue;
                float gap = x[a] - x[a - 1], step = Math.abs(y[a] - y[a - 1]);
                if (gap < 0) throw new IllegalArgumentException("turns back at x " + (int) x[a]);
                if (gap > LEAP_GAP_MAX) throw new IllegalArgumentException("has a gap wider than " + (int) LEAP_GAP_MAX + " px at x " + (int) x[a]);
                if (step > LEAP_STEP_MAX) throw new IllegalArgumentException("has a step higher than " + (int) LEAP_STEP_MAX + " px at x " + (int) x[a]);
                if (gap == 0 && step == 0) throw new IllegalArgumentException("has two legs on one floor at x " + (int) x[a] + " (merge them)");
            }
            return new Route(r, leftward ? r.hi[legs - 1] : r.lo[0]);
        }

        private Route(Route from, float start) {
            legs = from.legs;
            lo = from.lo;
            hi = from.hi;
            line = from.line;
            this.start = start;
            startLeg = from.startLeg;
            startsAtRight = from.startsAtRight;
        }

        public int legs() { return legs; }
        public float lo(int leg) { return lo[leg]; }
        public float hi(int leg) { return hi[leg]; }
        /** The floor line of leg {@code leg} (the bottom of the body). */
        public float floorY(int leg) { return line[leg]; }
        boolean single() { return legs == 1; }
        boolean hasLeg(int leg) { return leg >= 0 && leg < legs; }
        float startX() { return start; }
        int startLeg() { return startLeg; }
        boolean startsAtRight() { return startsAtRight; }
    }

    @Override public Hitbox hitbox() { return hitbox; }
    @Override public boolean isInQuickSand() { return inQuickSand; }
    @Override public void setInQuickSand(boolean value) { inQuickSand = value; }
    @Override public Escalator currentEscalator() { return currentEscalator; }
    @Override public void setCurrentEscalator(Escalator escalator) { currentEscalator = escalator; }
}
