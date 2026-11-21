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
 * cross-fades into a decor-tier palette, twitches (a big body does not rock) when the hero comes near, is crumbled by the sword,
 * a stone or a stomp and burnt by a fireball or a torch. Killed in its lunge, the body slides on to the lunge's end. A bite that
 * reaches the hero lands at the start of the wolf's next frame, so a hit that kills it on the same frame wins (as the spider's).
 * Its effects are {@link WolfFx}; its sprite is one strip ({@link SpriteSheets#MONSTER_WOLF}). World Y grows downward: the floor
 * line is the body's bottom edge, the jaws run {@link #JAW_ABOVE_FLOOR_LOW} to {@link #JAW_ABOVE_FLOOR_HIGH} px above it (a
 * smaller Y).
 */
public class Wolf extends Enemy<Wolf.State> implements CollideBody, Remains {
    /** {@code SNIFF}: at a range end before it turns; {@code BAY}: stopped at the nearest point it can reach, hero out of reach. */
    public enum State { TROT, SNIFF, NOTICE, CHASE, SKID, WINDUP, LUNGE, BITE, RECOVER, BAY, DEAD, BURN }

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
        CROUCH = 11, LUNGE_FRAME = 12, BITE_FRAME = 13, SHAKE = 14, BAY_FRAME = 15, DEAD_FRAME = 16, FRAMES = 17;

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
    private static final float SKID_DUST_SECONDS = .04f, DRAW_BACK = 1;
    private static final float ARRIVE = .5f, TURN_DEAD_ZONE = 2, STOPPED_SPEED = 20;
    /** The lunge's reach is traced over floor in steps this long. */
    private static final float REACH_STEP = 2, FLOOR_PROBE = 4;
    /** Float slack so the reach's far end (a whole number of steps) is included, and a box hanging this far above the floor line is not on it. */
    private static final float REACH_SLACK = .001f, FLOOR_TOUCH = .5f;

    /** The death: the poof starts this long after the hit; the body drops in a hop this high and long. */
    public static final float POOF_DELAY = .08f;
    private static final float FLIP_LIFT = 3, FLIP_SECONDS = .2f;
    /** The active {@code dead} frame holds this long, then cross-fades into the decor body over the second (D76, D101). */
    public static final float BODY_FADE_DELAY = .3f, BODY_FADE_SECONDS = .6f;
    /** The body twitches when the hero's body enters its box grown this many px left and right (D101): a 1 px shudder for this long. */
    public static final float NEAR_REACH = 6, TWITCH_SECONDS = .3f;
    private static final float TWITCH_STEP = .05f, TWITCH_PIXELS = 1;
    public static final float BURN_SECONDS = .3f;
    private static final float BURN_FLAMES_PER_SECOND = 70, CHAR_TINT = .15f;
    private static final float LANDING_TOLERANCE = 2;
    private static final float BODY_CENTRE_ABOVE_FLOOR = BODY_HEIGHT / 2f;

    static final String NOTICE_SOUND = "wolf_notice", WINDUP_SOUND = "wolf_windup", WINDUP_LONG_SOUND = "wolf_windup_long",
        LUNGE_SOUND = "wolf_lunge", BITE_SOUND = "wolf_bite", BAY_SOUND = "wolf_bay", RUN_SOUND = "wolf_run", DEATH_SOUND = "bounce";
    public static final String CRUMBLE_SOUND = "remains_crumble", BURN_SOUND = "remains_burn";

    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    private final TextureRegion bodyFrame;
    private final float spawnX, spawnY;
    private final Rectangle heroScratch = new Rectangle(), boxScratch = new Rectangle(), nearScratch = new Rectangle(),
        sightScratch = new Rectangle(), jawScratch = new Rectangle(), commitScratch = new Rectangle();
    private Level level;
    private boolean clambering, inQuickSand;
    private Escalator currentEscalator;
    private float stateClock, frameClock, senseClock, bayClock, coolClock, breathDue, warningTotal, speed, gallopClock, dustDue, flameDue,
        deadClock, twitchClock = TWITCH_SECONDS;
    private boolean trotFrame, cooling, heroNear, poofDone, bitePending, bitThisLunge, bayed;
    private int noticeStep, lastGallopFrame;
    // The lunge: from where, to where (the line the wind-up drew), and the jaw front last frame, for the sweep.
    private float lungeFromX, lungeToX, lastJawX, slideLeft;

    public Wolf(Assets assets, GameContext game, float x, float y, float w, float h, float offNeg, float offPos) {
        super(assets, game, "wolf", x, y, w, h, offNeg, offPos, State.class);
        spawnX = x;
        spawnY = y;
        hitbox = Hitbox.rect(BODY_X, BODY_Y, BODY_WIDTH, BODY_HEIGHT);
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.MONSTER_WOLF.frame(assets, i, 0);
        bodyFrame = SpriteSheets.MONSTER_WOLF_BODY.frame(assets, 0, 0);
        rangeNeg = x - offNeg * TILE_SIZE;
        rangePos = x + offPos * TILE_SIZE;
        moveDirection = -1;
        facingRight = false;
        current = State.TROT;
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
        position.set(spawnX, spawnY);
        velocity.setZero();
        moveDirection = -1;
        facingRight = false;
        bitePending = bitThisLunge = cooling = heroNear = poofDone = trotFrame = false;
        speed = slideLeft = gallopClock = deadClock = senseClock = bayClock = coolClock = lungeFromX = lungeToX = lastJawX = 0;
        lastGallopFrame = 0;
        twitchClock = TWITCH_SECONDS;
        enter(State.TROT);
    }

    private void enter(State state) {
        current = state;
        stateClock = frameClock = breathDue = dustDue = flameDue = 0;
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
        switch (current) {
            case TROT -> trot(dt);
            case SNIFF -> sniff(dt);
            case NOTICE -> notice();
            case CHASE -> chase(dt);
            case SKID -> skid(dt);
            case WINDUP -> windup(dt);
            case LUNGE -> { lunge(); return; } // a rush along the line the wind-up drew: no gravity, no floor test until it lands
            case BITE -> bite();
            case RECOVER -> recover();
            case BAY -> bay(dt);
            default -> { }
        }
        if (isRemoved()) return;
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
        bayed = false;
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
        float target = targetX(), dx = target - position.x;
        if (Math.abs(dx) > TURN_DEAD_ZONE && (dx > 0) != facingRight) {
            if (speed > STOPPED_SPEED) { enter(State.SKID); return; }
            facingRight = dx > 0;
            speed = 0;
        }
        if (inCommitBox() && commit()) return;
        if (Math.abs(dx) <= ARRIVE && speed <= STOPPED_SPEED) { arrive(target); return; }
        float distance = Math.abs(dx);
        float wanted = Math.min(CHASE_SPEED, (float) Math.sqrt(SIDES * BRAKING * distance));
        speed = speed < wanted ? Math.min(wanted, speed + ACCELERATION * dt) : Math.max(wanted, speed - BRAKING * dt);
        float step = speed * dt;
        velocity.x = (facingRight ? 1 : -1) * (step >= distance ? distance / dt : speed);
        gallop(dt);
    }

    /** At its target: it holds, faces the hero, and, the hero not within its reach, bays. */
    private void arrive(float target) {
        speed = 0;
        position.x = target;
        faceHero();
        if (inCommitBox() && commit()) return;
        enter(State.BAY);
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
        if (Math.abs(targetX() - position.x) > ARRIVE + TURN_DEAD_ZONE) enter(State.CHASE);
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
        moveDirection = position.x > (rangeNeg + rangePos) / 2f ? -1 : 1;
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

    private boolean inCommitBox() {
        return !player.isGotHit() && commitBoxInto(commitScratch).overlaps(player.hitboxBoundsInto(heroScratch));
    }

    // ---- the lunge (D115) --------------------------------------------------------------------------------------------

    /**
     * It commits where it stands and locks its direction: the line the jaws will cover is traced now over the floor under it, a
     * solid or the floor's end cutting it short, and the wind-up draws exactly that line. The shared bite warning is sampled once.
     */
    private boolean commit() {
        speed = 0;
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
    private boolean solidAt(float x) {
        Rectangle body = hitboxBoundsInto(boxScratch);
        body.set(body.x + x - position.x, body.y, body.width, body.height - 1);
        return level.blocksObject(body, false);
    }

    private void windup(float dt) {
        breathDue += BREATH_PER_SECOND * dt;
        float jaw = jawFrontX(), end = lungeToX + (jaw - position.x), floor = floorY();
        while (breathDue >= 1) { breathDue -= 1; if (level != null) level.wolfFx().breath(jaw, end, floor); }
        if (stateClock >= warningTotal) {
            lastJawX = jawFrontX();
            bitThisLunge = false;
            enter(State.LUNGE);
            if (game.playSounds()) game.playSound(LUNGE_SOUND);
        }
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

    /** The end of the lunge, at the line's end or against a solid: the bite, seen and heard hit or miss (D93). */
    private void land() {
        enter(State.BITE);
        if (level != null) level.wolfFx().bite(jawFrontX(), floorY());
        if (game.playSounds()) game.playSound(BITE_SOUND);
        checkBite();
    }

    /** The bite frame; a bite that reached the hero on its last frame lands before the recovery starts. */
    private void bite() {
        checkBite();
        if (stateClock >= BITE_SECONDS && !bitePending) enter(State.RECOVER);
    }

    private void recover() {
        if (stateClock < RECOVER_SECONDS) return;
        if (!heroPresent()) { giveUp(); return; }
        faceHero();   // a standing turn, if the hero is behind it
        speed = 0;
        senseClock = 0;
        enter(State.CHASE);
    }

    /**
     * The jaws reach the hero: the bite lands at the start of the next frame unless the wolf is killed first (the hit wins a
     * same-frame tie, as the spider's). The jaw box is swept from last frame's front, so a fast frame cannot skip over the hero.
     */
    private void checkBite() {
        if (!heroPresent() || bitThisLunge || player.isGotHit()) return;
        Rectangle jaws = jawBoxInto(jawScratch);
        float front = jawFrontX(), travelled = Math.abs(front - lastJawX);
        lastJawX = front;
        // Grown back to where the front was last frame: left of the box when it runs right, right of it when it runs left.
        jaws.set(facingRight ? jaws.x - travelled : jaws.x, jaws.y, jaws.width + travelled, jaws.height);
        if (jaws.overlaps(player.hitboxBoundsInto(heroScratch))) bitePending = bitThisLunge = true;
    }

    // ---- being killed and the body (D101) ------------------------------------------------------------------------------

    /** A fireball, a thrown stone or torch ({@code gotHit}) kills it in any state; a stomp kills it with the bounce; touch is harmless. */
    @Override
    public void collidedWithActor(boolean gotHit) {
        if (isDefeated()) return;
        if (gotHit) { die(); return; }
        if (!stompedByPlayer()) return; // touch is harmless: only the jaws kill (D115)
        player.velocity.y = -BOUNCE_HEIGHT;
        die();
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
        pendingRemoval = true;
        bitePending = false;
        poofDone = heroNear = false;
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
        if (twitchClock < TWITCH_SECONDS) twitchClock += dt;
    }

    private float twitchOffset() { return (((int) (twitchClock / TWITCH_STEP)) & 1) == 0 ? TWITCH_PIXELS : -TWITCH_PIXELS; }

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
            case BITE -> BITE_FRAME;
            case RECOVER -> SHAKE;
            case BAY -> BAY_FRAME;
            case DEAD, BURN -> DEAD_FRAME;
        };
    }

    /** How far the body has faded from the active {@code dead} frame into the decor body: 0 at the kill, 1 once settled. */
    public float bodyFade() {
        if (current != State.DEAD && current != State.BURN) return 0;
        return Math.min(1f, Math.max(0f, (deadClock - BODY_FADE_DELAY) / BODY_FADE_SECONDS));
    }

    @Override
    public void render(Batch batch) {
        if (current == State.DEAD || current == State.BURN) { renderBody(batch); return; }
        float dx = current == State.WINDUP ? (facingRight ? -DRAW_BACK : DRAW_BACK) : 0;
        TextureRegion frame = frames[frameIndex()];
        float x = position.x + dx, y = position.y;
        if (facingRight) batch.draw(frame, x, y, FRAME_WIDTH, FRAME_HEIGHT);
        else batch.draw(frame, x + SIDES * PIVOT_X, y, -FRAME_WIDTH, FRAME_HEIGHT);
    }

    /** The body: the drop, the cross-fade into the decor body, the twitch, and while burning a darkening tint. */
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

    // ---- load-time checks (D115) ---------------------------------------------------------------------------------------

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
        float floor = y + FRAME_HEIGHT, low = x - offNeg * TILE_SIZE, high = x + offPos * TILE_SIZE;
        if (high - low < MIN_RANGE) return "needs a range of at least " + (int) MIN_RANGE + " px";
        if (low < room.x || high + FRAME_WIDTH > room.x + room.width || y < room.y || floor > room.y + room.height)
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

    @Override public Hitbox hitbox() { return hitbox; }
    @Override public boolean isClambering() { return clambering; }
    @Override public void setClambering(boolean value) { clambering = value; }
    @Override public boolean isInQuickSand() { return inQuickSand; }
    @Override public void setInQuickSand(boolean value) { inQuickSand = value; }
    @Override public Escalator currentEscalator() { return currentEscalator; }
    @Override public void setCurrentEscalator(Escalator escalator) { currentEscalator = escalator; }
}
