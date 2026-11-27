package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.Hitbox;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.physics.CollideBody;
import com.jlogicsoftware.kimeria.world.Level;

/**
 * A monster whose death clip ends in the body it leaves (D101, #478): the Bat, the RedMob and the YellowMob. Killed, it
 * falls while the clip plays and lies on the surface below; over a pit it is gone. The body keeps the live palette (D117),
 * is harmless, counts as defeated and reacts to any actor passing (D119): a small one rocks, a big one twitches, silently.
 * The sword and a stomp (with the normal bounce) crumble it, a fireball or a thrown torch burns it.
 */
public abstract class FallenEnemy<S extends Enum<S>> extends Enemy<S> implements CollideBody, Remains {
    /** The body reacts when an actor enters its box grown this many px left and right (D101). */
    public static final float NEAR_REACH = Beetle.NEAR_REACH;
    /** A big body's twitch: a 1 px shudder of the same frame for this long, as the wolf's. */
    public static final float TWITCH_SECONDS = Wolf.TWITCH_SECONDS;
    private static final float TWITCH_STEP = .05f, TWITCH_PIXELS = 1;
    /** The burn: the body darkens to this tint over the burn, with flames at this rate, then a wisp; it is gone at the end. */
    public static final float BURN_SECONDS = Beetle.BURN_SECONDS;
    private static final float BURN_FLAMES_PER_SECOND = 70, CHAR_TINT = .15f;
    /** A hero falls onto the body when its feet were this close above the body's top on the previous frame (as a stomp). */
    private static final float LANDING_TOLERANCE = 2;
    private static final int SIDES = 2;
    private static final float HALF = .5f;

    private enum Body { NONE, LYING, BURNING }

    protected Level level;
    private final S deathState;
    /** The lying body in the unflipped frame: what a weapon or a stomp reaches, its bottom edge the floor line. */
    private final Hitbox bodyBox;
    private final float[][] chips;
    private final boolean twitches;
    private final RemainsSwing swing = new RemainsSwing();
    private final Rectangle nearScratch = new Rectangle(), heroScratch = new Rectangle(), moverScratch = new Rectangle();
    private Body body = Body.NONE;
    private float burnClock, flameDue, twitchClock;
    private boolean heroNear, moverNear;
    private boolean inQuickSand;
    private Escalator currentEscalator;

    /**
     * @param deathState the state whose one-shot clip plays at the kill and holds its last frame
     * @param bodyBox    the lying body's box in the unflipped frame
     * @param chips      the crumble chips, rgb triples of the monster's live palette
     * @param twitches   true for a big body, which twitches; a small one rocks
     */
    protected FallenEnemy(Assets assets, GameContext game, String spriteName, float x, float y, float w, float h,
                          float offNeg, float offPos, Class<S> stateType, S deathState, Hitbox bodyBox, float[][] chips,
                          boolean twitches) {
        super(assets, game, spriteName, x, y, w, h, offNeg, offPos, stateType);
        this.deathState = deathState;
        this.bodyBox = bodyBox;
        this.chips = chips;
        this.twitches = twitches;
    }

    public void setLevel(Level level) { this.level = level; }

    /** The frame the body rests on once the death clip is over and it lies; null keeps the clip's last frame. */
    protected TextureRegion restFrame() { return null; }

    /** The body's swing now, in degrees (0 at rest, and always for a big body; positive: its top leans right). */
    public float rockAngle() { return swing.angle(); }

    /** A big body's twitch now, in px: 0 at rest, and always for a small body. */
    public float twitch() {
        if (!twitches || body != Body.LYING || twitchClock >= TWITCH_SECONDS) return 0;
        return (((int) (twitchClock / TWITCH_STEP)) & 1) == 0 ? TWITCH_PIXELS : -TWITCH_PIXELS;
    }

    /** The frame drawn now, once killed. */
    public TextureRegion bodyFrame() {
        TextureRegion rest = restFrame();
        return rest != null && isOnGround() && isCurrentAnimationFinished() ? rest : animations.get(current).getKeyFrame(stateTime);
    }

    @Override
    protected void setDefeated() {
        setState(deathState);
        body = Body.LYING;
        heroNear = moverNear = false;
        swing.reset();
        twitchClock = TWITCH_SECONDS;
        velocity.setZero();
        setOnGround(false);
    }

    @Override
    public void update(float dt) {
        if (body == Body.NONE) { super.update(dt); return; }
        if (!game.isGameStarted() || isRemoved()) return;
        tickAnimation(dt);
        if (fall(dt)) return;
        if (body == Body.LYING) lie(dt);
        else burn(dt);
    }

    /** It falls and settles on the surface under it, like any enemy; one that falls below the room, over a pit, is gone. */
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

    /** The body (D101): a stomp, the pushes of whoever passes, and its swing or twitch. */
    private void lie(float dt) {
        boolean hero = player != null && !player.isRemoved() && player.canLeaveRoom();
        if (hero && stompedOn()) { player.velocity.y = -BOUNCE_HEIGHT; crumble(); return; } // the normal bounce, and it crumbles
        // Nothing reacts in the air, nor while the death clip still shows it falling over: the body answers once it lies.
        if (!isOnGround() || !isCurrentAnimationFinished()) return;
        boolean near = hero && grownBox().overlaps(player.hitboxBoundsInto(heroScratch));
        if (near && !heroNear) react(heroDirection());
        heroNear = near;
        // D119: anyone moving through the body moves it, not only the hero (monsters, the boss).
        Enemy<?> mover = Remains.moverNear(level, this, grownBox(), moverScratch);
        if (mover != null && !moverNear) react(Math.signum(mover.velocity.x));
        moverNear = mover != null;
        if (!twitches) swing.update(dt);
        else if (twitchClock < TWITCH_SECONDS) twitchClock += dt;
    }

    private void react(float direction) {
        if (twitches) twitchClock = 0;
        else swing.push(direction);
    }

    /** The way the hero moves, or away from it when it stands still. */
    private float heroDirection() {
        if (player.velocity.x != 0) return Math.signum(player.velocity.x);
        Rectangle hero = player.hitboxBoundsInto(heroScratch);
        return hero.x + hero.width * HALF < centreX() ? 1 : -1;
    }

    /** The body's box grown {@link #NEAR_REACH} left and right, in {@code nearScratch}. */
    private Rectangle grownBox() {
        Rectangle box = remainsInto(nearScratch);
        box.set(box.x - NEAR_REACH, box.y, box.width + SIDES * NEAR_REACH, box.height);
        return box;
    }

    /** A stomp on the body: the hero's body touches it while falling, its feet above the body's top on the previous frame. */
    private boolean stompedOn() {
        Rectangle box = remainsInto(nearScratch);
        return box.overlaps(player.hitboxBoundsInto(heroScratch)) && player.velocity.y > 0
            && player.previousFeetY() <= box.y + LANDING_TOLERANCE;
    }

    /** The middle of the lying body, horizontally. */
    private float centreX() { return remainsInto(nearScratch).x + bodyBox.width * HALF; }
    /** The floor line the body lies on: the bottom of its box. */
    public float floorY() { return position.y + bodyBox.offsetY + bodyBox.height; }

    /** The body burning: flames at a steady rate while it darkens, then a wisp, then it is gone. */
    private void burn(float dt) {
        burnClock += dt;
        float cx = centreX(), floor = floorY();
        flameDue += BURN_FLAMES_PER_SECOND * dt;
        while (flameDue >= 1) { flameDue -= 1; if (level != null) level.remainsFx().burnFlame(cx, floor); }
        if (burnClock >= BURN_SECONDS) {
            if (level != null) level.remainsFx().burnOut(cx, floor);
            removeFromParent();
        }
    }

    @Override public boolean isRemains() { return body == Body.LYING && !isRemoved(); }
    @Override public Rectangle remainsInto(Rectangle out) { return boundsInto(bodyBox, out); }

    @Override
    public void crumble() {
        if (!isRemains()) return;
        if (level != null) level.remainsFx().crumble(centreX(), floorY(), chips);
        if (game.playSounds()) game.playSound(Beetle.CRUMBLE_SOUND);
        removeFromParent();
    }

    @Override
    public void burnUp() {
        if (!isRemains()) return;
        body = Body.BURNING;
        burnClock = flameDue = 0;
        if (level != null) level.remainsFx().burnGlow(centreX(), floorY(), BURN_SECONDS);
        if (game.playSounds()) game.playSound(Beetle.BURN_SOUND);
    }

    /** The sword kills it with the light spray of D65 (the base class); on the body, it crumbles it (D101). */
    @Override
    public void hitBySword(Level level) {
        if (isRemains()) crumble();
        else super.hitBySword(level);
    }

    /**
     * Alive, drawn as any actor. Killed, its death clip or resting frame, swung about the bottom of the body or shifted by the
     * twitch, and darkening while it burns. Nothing allocates; the batch colour is put back.
     */
    @Override
    public void render(Batch batch) {
        if (body == Body.NONE) { super.render(batch); return; }
        float saved = batch.getPackedColor();
        if (body == Body.BURNING) {
            float tint = 1f - (1f - CHAR_TINT) * Math.min(1f, burnClock / BURN_SECONDS);
            batch.setColor(tint, tint, tint, 1);
        }
        // Mirrored by a negative scale about the pivot, which is where Actor mirrors a live frame.
        batch.draw(bodyFrame(), position.x + twitch(), position.y, pivotX(), bodyBox.offsetY + bodyBox.height, size.x, size.y,
            facingRight ? 1 : -1, 1, swing.angle());
        batch.setPackedColor(saved);
    }

    @Override public Hitbox hitbox() { return hitbox; }
    @Override public boolean isInQuickSand() { return inQuickSand; }
    @Override public void setInQuickSand(boolean value) { inQuickSand = value; }
    @Override public Escalator currentEscalator() { return currentEscalator; }
    @Override public void setCurrentEscalator(Escalator escalator) { currentEscalator = escalator; }
}
