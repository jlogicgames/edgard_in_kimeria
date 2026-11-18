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
 * The forest spider (#328, D102): it skitters inside its authored range, hears the hero, notices in body language (it rises
 * and drums a foreleg), chases, and once the hero is in its pounce box it locks onto where the hero stands, winds up for the
 * shared bite warning ({@link TrapWarnings#BITE_WARNING_SECONDS}, longer with the assist) while silk glints run to the landing
 * spot, leaps there and bites, hit or miss, then lies flat and harmless while it recovers. Its body never hurts: only the fang
 * box kills, during the leap and the bite. A bite that reaches the hero is resolved at the start of the spider's next frame,
 * so a kill that lands on the same frame (the sword, a stomp, a fireball or a stone) wins. Every weapon kills it in any state,
 * quietly: it leaves its body (D101, {@link Remains}), on its back with its legs curled up. A sword kill sprays the light blood
 * of D65. Its effects are {@link SpiderFx} and, for the body's end, {@link RemainsFx}; its sprite is one strip of nine frames
 * ({@link SpriteSheets#MONSTER_SPIDER}). World Y grows downward: the floor line is the body's bottom edge.
 */
public class Spider extends Enemy<Spider.State> implements CollideBody, Remains {
    /** {@code DEAD}: killed, the body lying (D101); {@code BURN}: the body burning up. */
    public enum State { CRAWL, NOTICE, CHASE, WINDUP, LEAP, BITE, RECOVER, DEAD, BURN }

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
    /** Left and right: the mirror spans twice the pivot (Actor.render), a reach counts on both sides. */
    private static final int SIDES = 2;
    private static final int CRAWL1 = 0, CRAWL2 = 1, RISE = 2, TAP = 3, CROUCH = 4, LEAP_FRAME = 5, BITE_FRAME = 6, RECOVER_FRAME = 7,
        DEAD_FRAME = 8, FRAMES = 9;

    /** The skitter: this fast for {@link #SKITTER_MOVE} s, then still for {@link #SKITTER_STILL} s (24 px/s on average). */
    public static final float SKITTER_SPEED = 48, SKITTER_MOVE = .3f, SKITTER_STILL = .3f;
    /** The chase (px/s); the hero runs 100, so running away always works. */
    public static final float CHASE_SPEED = 85;
    private static final float SKITTER_FRAME_SECONDS = .1f, CHASE_FRAME_SECONDS = .05f;
    /** Hearing: the hero's centre within this many px either side, inside a band this tall above the floor line. */
    public static final float HEAR_RADIUS = 112, HEAR_BAND = 40;
    /** It loses the hero beyond this distance, or after this long out of the band. */
    public static final float LOSE_RADIUS = 160, LOSE_SECONDS = 1f;
    /** The notice in body language: it rises, drums a raised foreleg twice and settles (0.5 s, not scaled by the assist). */
    private static final int[] NOTICE_FRAMES = {RISE, TAP, RISE, TAP, RISE, CRAWL1};
    private static final float[] NOTICE_SECONDS = {.1f, .08f, .08f, .08f, .08f, .08f};
    public static final float NOTICE_TOTAL;
    static {
        float total = 0;
        for (float s : NOTICE_SECONDS) total += s;
        NOTICE_TOTAL = total;
    }
    /** The wind-up: drawn this many px back, away from the hero; silk glints per second, running to the landing spot. */
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

    static final String NOTICE_SOUND = "spider_notice", WINDUP_SOUND = "spider_windup", WINDUP_LONG_SOUND = "spider_windup_long",
        LEAP_SOUND = "spider_leap", BITE_SOUND = "spider_bite", DEATH_SOUND = "bounce";
    /** D101: shared by every monster's body; the rocking is silent. */
    public static final String CRUMBLE_SOUND = "remains_crumble", BURN_SOUND = "remains_burn";

    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    private final TextureRegion bodyFrame;
    private final float spawnX, spawnY;
    private final Rectangle pounceScratch = new Rectangle(), fangScratch = new Rectangle(), heroScratch = new Rectangle(),
        nearScratch = new Rectangle();
    private final RemainsSwing swing = new RemainsSwing();
    private Level level;
    private boolean clambering, inQuickSand;
    private Escalator currentEscalator;
    private float stateClock, frameClock, outOfBand, silkDue, warningTotal, flameDue, deadClock;
    /** The leap: from here to there, on the floor line it took off from; the fangs' x last frame (the swept bite). */
    private float leapFromX, leapToX, leapFloorY, lastFangX, lineFlown;
    private boolean crawlFrame, heroNear, poofDone, bitePending;
    /**
     * One bite per leap: set when the fangs reach the hero, cleared when the next leap starts. A hero the bite cannot kill (the
     * developer's invulnerability, the victory walk) is not bitten again and again, and the spider always goes on to recover.
     */
    private boolean bitThisLeap;
    private int noticeStep;

    public Spider(Assets assets, GameContext game, float x, float y, float w, float h, float offNeg, float offPos) {
        super(assets, game, "spider", x, y, w, h, offNeg, offPos, State.class);
        spawnX = x;
        spawnY = y;
        hitbox = Hitbox.rect(BODY_X, BODY_Y, BODY_WIDTH, BODY_HEIGHT);
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.MONSTER_SPIDER.frame(assets, i, 0);
        bodyFrame = SpriteSheets.MONSTER_SPIDER_BODY.frame(assets, 0, 0);
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
    public boolean isCommitted() { return current == State.WINDUP || current == State.LEAP || current == State.BITE; }
    /** Where the leap will put {@code position.x} (the locked target), valid from the commit until it lands. */
    public float leapTarget() { return leapToX; }
    /** The body's swing now, in degrees (0 at rest; positive: its top leans right). */
    public float rockAngle() { return swing.angle(); }

    /** The floor line the spider stands on: the bottom of its body. */
    public float floorY() { return position.y + hitbox.offsetY + hitbox.height; }
    /** The middle of the body, horizontally. */
    public float centreX() { return hitboxBoundsInto(bodyScratch).x + hitbox.width / 2f; }
    /** The fangs' middle, horizontally. */
    public float fangX() { return position.x + (facingRight ? FANG_RIGHT : FANG_LEFT); }

    /** The pounce box (the hero's body touching it makes the spider commit), written into {@code out}. */
    public Rectangle pounceBoxInto(Rectangle out) { return boundsInto(POUNCE, out); }

    /** The fang box now (null outside the leap and the bite), written into {@code out}. */
    public Rectangle fangBoxInto(Rectangle out) {
        if (current == State.LEAP) return boundsInto(LEAP_FANGS, out);
        if (current == State.BITE) return boundsInto(BITE_FANGS, out);
        return null;
    }

    @Override
    public void revisit(Player nextPlayer) {
        super.revisit(nextPlayer);
        // A spider left behind returns calm at its spawn: surviving enemies take their authored state (D3, D6).
        position.set(spawnX, spawnY);
        velocity.setZero();
        moveDirection = -1;
        facingRight = false;
        bitePending = bitThisLeap = false;
        enter(State.CRAWL);
    }

    private void enter(State state) {
        current = state;
        stateClock = frameClock = outOfBand = silkDue = flameDue = 0;
        noticeStep = 0;
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
        if (bitePending) { // last frame's bite: nothing killed the spider since, so it lands
            bitePending = false;
            if (heroPresent()) player.collidedWithActor(false);
        }
        stateClock += dt;
        velocity.x = 0;
        switch (current) {
            case CRAWL -> crawl(dt);
            case NOTICE -> notice();
            case CHASE -> chase(dt);
            case WINDUP -> windup(dt);
            case LEAP -> { leap(); return; } // a flight on its own arc: no gravity, no floor test until it lands
            case BITE -> bite();
            case RECOVER -> recover();
            default -> { }
        }
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
        if (stateClock % (SKITTER_MOVE + SKITTER_STILL) >= SKITTER_MOVE) return; // the still part of the skitter
        velocity.x = moveDirection * SKITTER_SPEED;
        animateCrawl(dt, SKITTER_FRAME_SECONDS);
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
        float direction = directionToPlayer(facingRight ? 1 : -1);
        facingRight = direction > 0;
        if (inPounceBox()) { commit(); return; }
        boolean atEdge = (direction < 0 && position.x <= rangeNeg) || (direction > 0 && position.x >= rangePos);
        if (atEdge) return; // it waits at its range's end, facing the hero
        velocity.x = direction * CHASE_SPEED;
        animateCrawl(dt, CHASE_FRAME_SECONDS);
    }

    private void animateCrawl(float dt, float frameSeconds) {
        frameClock += dt;
        while (frameClock >= frameSeconds) { frameClock -= frameSeconds; crawlFrame = !crawlFrame; }
    }

    private boolean inPounceBox() {
        return pounceBoxInto(pounceScratch).overlaps(player.hitboxBoundsInto(heroScratch));
    }

    /**
     * It commits on the spot and locks its target: the hero's centre now, where its fangs will land, kept inside its range and
     * never behind it. Then the shared bite warning, sampled once.
     */
    private void commit() {
        enter(State.WINDUP);
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        float target = hero.x + hero.width / 2f - (facingRight ? FANG_RIGHT : FANG_LEFT);
        target = facingRight ? Math.max(position.x, target) : Math.min(position.x, target);
        leapToX = Math.max(rangeNeg, Math.min(rangePos, target));
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

    /** Killed (by anything): it flips onto its back and stays as a body (D101); the poof follows a moment later. */
    private void die() {
        enter(State.DEAD); // killed in the air, the body falls from where it was (fall)
        pendingRemoval = true;
        bitePending = false;
        poofDone = heroNear = false;
        swing.reset();
        deadClock = 0;
        velocity.setZero();
        if (game.playSounds()) game.playSound(DEATH_SOUND);
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

    /** The frame index drawn now. */
    int frameIndex() {
        return switch (current) {
            case CRAWL, CHASE -> crawlFrame ? CRAWL2 : CRAWL1;
            case NOTICE -> NOTICE_FRAMES[noticeStep];
            case WINDUP -> CROUCH;
            case LEAP -> LEAP_FRAME;
            case BITE -> BITE_FRAME;
            case RECOVER -> RECOVER_FRAME;
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
        else batch.draw(frame, x + SIDES * pivotX(), y, -FRAME_WIDTH, FRAME_HEIGHT);
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

    /**
     * The load-time checks (D102), on the spider as the game builds it whatever size the Tiled object has (the 32x16 frame, its
     * {@code y} the frame's top): the frame's sweep over the range inside the room, a floor without gaps under the body's whole
     * sweep, and no standing place (the entrance body, the room exit, a checkpoint) inside the pounce box at any point of the
     * range, facing either way (from {@code position.x} - 46 to {@code position.x} + 70), so the hero never enters or stands
     * inside a pounce. Null when the spider is fine, else what is wrong.
     */
    public static String problem(float x, float y, float w, float h, float offNeg, float offPos, List<Rectangle> floors,
                                 Rectangle room, Map<String, Rectangle> standing) {
        if (!(w > 0) || !(h > 0)) return "needs positive bounds";
        if (!Float.isFinite(offNeg) || !Float.isFinite(offPos) || offNeg < 0 || offPos < 0) return "needs finite, non-negative offNeg and offPos";
        float leftX = x - offNeg * TILE_SIZE, rightX = x + offPos * TILE_SIZE, floor = y + BODY_Y + BODY_HEIGHT;
        float pivot = BODY_X + BODY_WIDTH / 2, pounceRight = POUNCE.offsetX + POUNCE.width, pounceLeft = SIDES * pivot - pounceRight;
        float top = y + POUNCE.offsetY;
        Rectangle reach = new Rectangle(leftX + pounceLeft, top, rightX + pounceRight - (leftX + pounceLeft), floor - top);
        if (leftX < room.x || rightX + FRAME_WIDTH > room.x + room.width || top < room.y || floor > room.y + room.height)
            return "has its range leaving the room";
        float left = leftX + BODY_X, right = rightX + BODY_X + BODY_WIDTH, covered = left;
        List<Rectangle> spans = new ArrayList<>();
        for (Rectangle f : floors) if (Math.abs(f.y - floor) < 1f) spans.add(f);
        spans.sort((a, b) -> Float.compare(a.x, b.x));
        for (Rectangle f : spans) if (f.x <= covered + 1f) covered = Math.max(covered, f.x + f.width);
        if (covered < right) return "must crawl on floor without gaps under its whole range";
        for (Map.Entry<String, Rectangle> place : standing.entrySet())
            if (reach.overlaps(place.getValue())) return "can pounce at the room " + place.getKey() + " (its reach is " + (int) POUNCE_REACH + " px)";
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
