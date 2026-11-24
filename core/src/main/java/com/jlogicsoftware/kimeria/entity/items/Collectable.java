package com.jlogicsoftware.kimeria.entity.items;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.effects.SoftGlowFx;
import com.jlogicsoftware.kimeria.entity.Entity;

/**
 * Animated coins and artifacts, sharing the pickup sound, the spark burst and ring (Level.pickupSparks) and the pickup cue (#345, playtest F19): a 2 px bob, a
 * warm-white halo pulsing with it and a sparkle, so a pickup never reads as an enemy (enemies have none of it). The
 * coin has no halo ({@link #hasHalo}).
 */
public class Collectable extends Entity {
    private static final int GLOW_SEED_X = 31;
    private static final float STEP_TIME = 0.3f;
    private static final int FRAME_COUNT = 2;
    private static final int FRAME_SIZE = 16;
    /** The shared pickup cue (#345, playtest F19): the drawing rises this many whole pixels and settles back. */
    static final int BOB_HEIGHT = 2;
    /** Seconds per bob, rest to top and back to rest. */
    static final float BOB_PERIOD = 1.2f;
    /** The pickup's light (#544, D124 amending D76): a {@link SoftGlowFx} on the item's oval of this radius; additive, so it only brightens (checked by check_contrast.py). */
    static final float GLOW_RADIUS = 8f;
    /** One sparkle (three frames) every period; items start it at different times, by their x. */
    static final float SPARKLE_PERIOD = 2.4f, SPARKLE_FRAME_TIME = 0.1f, SPARKLE_STAGGER_PER_TILE = 0.4f;
    static final int SPARKLE_SIZE = 5, SPARKLE_FRAMES = 3;
    /** The sparkle's top-left from the item's horizontal centre and drawn top edge: right of centre, 2 px above. */
    static final float SPARKLE_DX = 3, SPARKLE_DY = -2;
    private static final String SPARKLE_SHEET = "images/objects/pickup-sparkle.png";

    private final ItemType type;
    private final GameContext game;
    private final Animation<TextureRegion> animation;
    private float stateTime = 0f;
    private boolean collected = false;
    private final Runnable onPickup;
    /** The soft light; null for the coin ({@link #hasHalo}). */
    private final SoftGlowFx glow;
    private final TextureRegion[] sparkle = new TextureRegion[SPARKLE_FRAMES];
    private final float sparkleOffset;

    public Collectable(Assets assets, GameContext game, String name, float x, float y, float w, float h) {
        this(assets, game, name, x, y, w, h, () -> {});
    }

    public Collectable(Assets assets, GameContext game, String name, float x, float y, float w, float h, Runnable onPickup) {
        super(x, y, w, h);
        this.onPickup = onPickup;
        this.type = ItemType.fromMapName(name);
        this.game = game;
        animation = assets.animation(type.sheet, FRAME_COUNT, STEP_TIME, FRAME_SIZE, FRAME_SIZE, type.spriteX, type.spriteY);
        animation.setPlayMode(Animation.PlayMode.LOOP);
        glow = hasHalo(type) ? new SoftGlowFx(SoftGlowFx.PICKUP, (long) (x * GLOW_SEED_X + y)) : null;
        if (glow != null) glow.place(position.x + size.x / 2f, position.y + size.y / 2f, GLOW_RADIUS, GLOW_RADIUS); // lit from its first draw, through a room's fade-in too
        for (int n = 0; n < SPARKLE_FRAMES; n++) sparkle[n] = assets.region(SPARKLE_SHEET, n * SPARKLE_SIZE, 0, SPARKLE_SIZE, SPARKLE_SIZE);
        sparkleOffset = (x / FRAME_SIZE * SPARKLE_STAGGER_PER_TILE) % SPARKLE_PERIOD;
    }

    public boolean isCollected() {
        return collected;
    }

    public ItemType type() { return type; }

    /** The soft light, for tests; null on the coin. */
    SoftGlowFx glow() { return glow; }

    public void collideWithPlayer() {
        if (collected) return;
        collected = true;
        onPickup.run();
        if (game.playSounds()) game.playSound("collect");

        if (type == ItemType.COIN) {
            game.addCoin();
        }
        // The picture of it arriving (D132, #557): after every state change above, which no flight reads or waits for.
        game.startPickupFlight(type, position.x + size.x / 2f, position.y - bobLift(stateTime) + size.y / 2f);
        removeFromParent();
    }

    @Override
    public void update(float dt) {
        stateTime += dt;
        if (glow != null) {
            glow.place(position.x + size.x / 2f, position.y - bobLift(stateTime) + size.y / 2f, GLOW_RADIUS, GLOW_RADIUS);
            glow.update(dt);
        }
    }

    /**
     * Every collectable but the coin draws the halo. The coin's white rim washes out against added light: with the
     * halo its outline fell to 0.123 against the D76 floor of 0.28 (forest-2 x 336); the rim already does a halo's job.
     */
    static boolean hasHalo(ItemType type) {
        return type != ItemType.COIN;
    }

    /** 0 at rest, 1 at the top of the bob. */
    static float bobPhase(float time) {
        return (1f - MathUtils.cos(MathUtils.PI2 * time / BOB_PERIOD)) / 2;
    }

    /** Whole pixels the drawing is lifted at {@code time}: 0 at rest, {@link #BOB_HEIGHT} at the top. World Y grows down, so lifting subtracts. */
    static int bobLift(float time) {
        return MathUtils.round(BOB_HEIGHT * bobPhase(time));
    }

    /** The sparkle frame shown at {@code time} (this item's offset included), or -1 between sparkles. */
    static int sparkleFrame(float time) {
        float t = time % SPARKLE_PERIOD;
        return t < SPARKLE_FRAME_TIME * SPARKLE_FRAMES ? (int) (t / SPARKLE_FRAME_TIME) : -1;
    }

    @Override
    public void render(Batch batch) {
        // Only the drawing moves; the pickup box stays where the map put it.
        float phase = bobPhase(stateTime);
        float top = position.y - bobLift(stateTime);
        float centerX = position.x + size.x / 2f, centerY = top + size.y / 2f;
        if (glow != null) glow.render(batch, 1f);
        batch.draw(animation.getKeyFrame(stateTime), position.x, top, size.x, size.y);
        int spark = sparkleFrame(stateTime + sparkleOffset);
        if (spark >= 0) batch.draw(sparkle[spark], centerX + SPARKLE_DX, top + SPARKLE_DY, SPARKLE_SIZE, SPARKLE_SIZE);
    }
}
