package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.TimeUtils;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.items.Actionable;

/**
 * Port of Dart's {@code Torch}. The original spawns dozens of independent
 * {@code Future.delayed} particle schedulers driving Skia canvas draws with
 * blur mask filters; here that's replaced with a small allocation-free
 * {@link KimeriaParticleSystem} (fixed arrays, one per torch, nothing allocated after the
 * constructor) that keeps the same visual language -- a flickering core flame, rising
 * embers, drifting smoke, and green magic sparkles -- and the same
 * {@code toggleFire}/{@code performAction} behaviour used by triggers and falling platforms.
 */
public class Torch extends Entity implements Actionable {
    /** Measured peak live count (TorchParticleCapacityTest) plus headroom; a full pool only thins the flame. */
    static final int PARTICLE_CAPACITY = 96;
    // Particle tuning, unchanged from the pre-migration torch (pinned by ParticleGoldenTest).
    private static final float CORE_RISE = 12f, EMBER_RISE = 40f, SPARKLE_RISE = 30f, SMOKE_RISE = 50f;
    private static final float CORE_SIZE = 8f, EMBER_SIZE = 2.5f, SPARKLE_SIZE = 1.6f, SMOKE_SIZE = 20f;
    private static final float SMOKE_GRAY = 0.4f;
    private static final float FLICKER_MIN = 0.12f, FLICKER_SPAN = 0.22f;
    private static final float CORE_EVERY_MIN = 0.04f, CORE_EVERY_SPAN = 0.12f;
    private static final float EMBER_EVERY_MIN = 0.15f, EMBER_EVERY_SPAN = 0.2f;
    private static final float SPARKLE_EVERY_MIN = 0.08f, SPARKLE_EVERY_SPAN = 0.18f;
    private static final float SMOKE_EVERY_MIN = 0.3f, SMOKE_EVERY_SPAN = 0.5f;
    private static final float CORE_LIFE_MIN = 0.14f, CORE_LIFE_SPAN = 0.18f;
    private static final float EMBER_LIFE_MIN = 0.35f, EMBER_LIFE_SPAN = 0.45f;
    private static final float SPARKLE_LIFE_MIN = 0.4f, SPARKLE_LIFE_SPAN = 0.8f;
    private static final float SMOKE_LIFE_MIN = 2f, SMOKE_LIFE_SPAN = 2f;
    private static final float CORE_SPREAD_X = 4f, CORE_SPREAD_Y = 3f;
    private static final float EMBER_SPREAD_X = 8f, EMBER_SPREAD_Y = 6f, EMBER_DRIFT = 28f, EMBER_RISE_SPAN = 30f;
    private static final float SPARKLE_SPREAD_X = 10f, SPARKLE_SPREAD_Y = 8f, SPARKLE_DRIFT = 18f, SPARKLE_RISE_SPAN = 60f;
    private static final float SMOKE_SPREAD_X = 6f, SMOKE_DRIFT = 8f, SMOKE_RISE_SPAN = 40f, SMOKE_START_LIFT = 2f;
    private static final int EMBER_BURST_EXTRA = 3, SPARKLE_BURST_EXTRA = 3, SPARKLE_BURST_MIN = 2, SPARKLE_BURST_MAX = 6;
    private static final float SPARKLE_BURST_PER_INTENSITY = 0.02f;
    private static final float FLAME_R = 1f, FLAME_G = 0.85f, FLAME_B = 0.2f;
    private static final float EMBER_R = 0.6f, EMBER_G = 1f, EMBER_B = 0.4f;
    private static final float SPARKLE_R = 0.35f, SPARKLE_G = 0.85f, SPARKLE_B = 0.3f;
    private static final float GLOW_FLICKER_MIN = 0.85f, GLOW_FLICKER_SPAN = 0.3f;
    private static final float GLOW_SCALE = 1.4f, GLOW_ALPHA_SCALE = 0.6f;
    private static final float GLOW_R = 1f, GLOW_G = 0.9f, GLOW_B = 0.4f;
    /** Odd 64-bit constant that spreads consecutive torch counters over the seed space. */
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;
    private static final float HALF = 0.5f, TWICE = 2f;
    private static int seedCounter;

    private final KimeriaParticleSystem particles;
    private int intensity;
    private final String targetId;
    private static final float SPRITE_OFFSET_Y = -3;
    private final TextureRegion sprite;
    private boolean burning;

    private float coreTimer, emberTimer, sparkleTimer, smokeTimer, flickerTimer;
    private float lightRadius, lightAlpha;

    public Torch(float centerX, float centerY, float w, float h, int intensity, String targetId) {
        this(null, centerX, centerY, w, h, intensity, targetId);
    }

    public Torch(Assets assets, float centerX, float centerY, float w, float h, int intensity, String targetId) {
        this(assets, centerX, centerY, w, h, intensity, targetId, TimeUtils.nanoTime() ^ (SEED_MIX * ++seedCounter));
    }

    /** Fixed seed for the particle generator, so tests can reproduce a run. */
    Torch(Assets assets, float centerX, float centerY, float w, float h, int intensity, String targetId, long seed) {
        super(centerX - w / 2f, centerY - h / 2f, w, h);
        particles = new KimeriaParticleSystem(PARTICLE_CAPACITY, seed);
        sprite = assets == null ? null : SpriteSheets.TORCH.frame(assets);
        this.intensity = intensity;
        this.targetId = targetId == null ? "" : targetId;
        burning = intensity > 0;
        computeLightBase();
    }

    private void computeLightBase() {
        lightRadius = MathUtils.clamp(4f + intensity * 0.06f, 3f, 40f);
        lightAlpha = MathUtils.clamp(18f + intensity * 0.22f, 8f, 220f) / 255f;
    }

    public void toggleFire(boolean on) {
        intensity = on ? 200 : 0;
        burning = on;
        computeLightBase();
        if (!on) {
            particles.clear();
        }
    }

    public boolean isBurning() { return burning; }

    public int intensity() { return intensity; }

    /** Rebuild logical fire/light state without carrying transient particles between room visits. */
    public void restoreState(int intensity, boolean burning) {
        this.intensity = Math.max(0, intensity);
        this.burning = burning;
        computeLightBase();
        particles.clear();
        coreTimer = emberTimer = sparkleTimer = smokeTimer = flickerTimer = 0f;
    }

    @Override
    public String getTargetId() {
        return targetId;
    }

    @Override
    public void performAction() {
        toggleFire(intensity == 0);
    }

    @Override
    public void update(float dt) {
        if (!burning) return;

        RandomXS128 random = particles.random();
        flickerTimer -= dt;
        if (flickerTimer <= 0) {
            flickerTimer = FLICKER_MIN + random.nextFloat() * FLICKER_SPAN;
        }

        coreTimer -= dt;
        if (coreTimer <= 0) {
            spawnCore(random);
            coreTimer = CORE_EVERY_MIN + random.nextFloat() * CORE_EVERY_SPAN;
        }
        emberTimer -= dt;
        if (emberTimer <= 0) {
            spawnEmbers(random);
            emberTimer = EMBER_EVERY_MIN + random.nextFloat() * EMBER_EVERY_SPAN;
        }
        sparkleTimer -= dt;
        if (sparkleTimer <= 0) {
            spawnSparkles(random);
            sparkleTimer = SPARKLE_EVERY_MIN + random.nextFloat() * SPARKLE_EVERY_SPAN;
        }
        smokeTimer -= dt;
        if (smokeTimer <= 0) {
            spawnSmoke(random);
            smokeTimer = SMOKE_EVERY_MIN + random.nextFloat() * SMOKE_EVERY_SPAN;
        }
        particles.update(dt);
    }

    // The random calls below stay in the order the original code made them (life, start x, start y, end x, end rise).
    private void spawnCore(RandomXS128 random) {
        float life = CORE_LIFE_MIN + random.nextFloat() * CORE_LIFE_SPAN;
        float fromX = centerX() + rnd(random, CORE_SPREAD_X), fromY = centerY() + rnd(random, CORE_SPREAD_Y);
        particles.spawn(KimeriaParticleSystem.KIND_GLOW, fromX, fromY, fromX, fromY - CORE_RISE, life, CORE_SIZE, FLAME_R, FLAME_G, FLAME_B);
    }

    private void spawnEmbers(RandomXS128 random) {
        int burst = 1 + random.nextInt(EMBER_BURST_EXTRA);
        for (int i = 0; i < burst; i++) {
            float life = EMBER_LIFE_MIN + random.nextFloat() * EMBER_LIFE_SPAN;
            float fromX = centerX() + rnd(random, EMBER_SPREAD_X), fromY = centerY() + rnd(random, EMBER_SPREAD_Y);
            float toX = fromX + rnd(random, EMBER_DRIFT), toY = fromY - EMBER_RISE - random.nextFloat() * EMBER_RISE_SPAN;
            particles.spawn(KimeriaParticleSystem.KIND_GLOW, fromX, fromY, toX, toY, life, EMBER_SIZE, EMBER_R, EMBER_G, EMBER_B);
        }
    }

    private void spawnSparkles(RandomXS128 random) {
        // Was up to 40 particles per burst (every ~0.1-0.3s): stacking that
        // many additive-blended dots this close together saturates straight
        // to white/cyan instead of reading as distinct green sparks.
        int burst = MathUtils.clamp(Math.round(intensity * SPARKLE_BURST_PER_INTENSITY) + random.nextInt(SPARKLE_BURST_EXTRA),
            SPARKLE_BURST_MIN, SPARKLE_BURST_MAX);
        for (int i = 0; i < burst; i++) {
            float life = SPARKLE_LIFE_MIN + random.nextFloat() * SPARKLE_LIFE_SPAN;
            float fromX = centerX() + rnd(random, SPARKLE_SPREAD_X), fromY = centerY() + rnd(random, SPARKLE_SPREAD_Y);
            float toX = fromX + rnd(random, SPARKLE_DRIFT), toY = fromY - SPARKLE_RISE - random.nextFloat() * SPARKLE_RISE_SPAN;
            particles.spawn(KimeriaParticleSystem.KIND_GLOW, fromX, fromY, toX, toY, life, SPARKLE_SIZE, SPARKLE_R, SPARKLE_G, SPARKLE_B);
        }
    }

    private void spawnSmoke(RandomXS128 random) {
        float life = SMOKE_LIFE_MIN + random.nextFloat() * SMOKE_LIFE_SPAN;
        float fromX = centerX() + rnd(random, SMOKE_SPREAD_X), fromY = centerY() - SMOKE_START_LIFT;
        float toX = fromX + rnd(random, SMOKE_DRIFT), toY = fromY - SMOKE_RISE - random.nextFloat() * SMOKE_RISE_SPAN;
        particles.spawn(KimeriaParticleSystem.KIND_SMOKE, fromX, fromY, toX, toY, life, SMOKE_SIZE, SMOKE_GRAY, SMOKE_GRAY, SMOKE_GRAY);
    }

    private static float rnd(RandomXS128 random, float range) {
        return (random.nextFloat() - HALF) * range * TWICE;
    }

    /** The torch's particles, for tests. */
    KimeriaParticleSystem particles() { return particles; }

    @Override
    public void render(Batch batch) {
        if (sprite != null) batch.draw(sprite, centerX() - SpriteSheets.TORCH.width() / 2f, centerY() + SPRITE_OFFSET_Y,
            SpriteSheets.TORCH.width(), SpriteSheets.TORCH.height());
        TextureRegion dot = SoftDot.region();
        // Smoke (normal blending) is drawn first inside the system so it stays behind the additive flame.
        particles.render(batch, dot);
        if (burning) {
            float flicker = GLOW_FLICKER_MIN + particles.random().nextFloat() * GLOW_FLICKER_SPAN;
            float radius = lightRadius * flicker * GLOW_SCALE;
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            batch.setColor(GLOW_R, GLOW_G, GLOW_B, lightAlpha * GLOW_ALPHA_SCALE);
            batch.draw(dot, centerX() - radius / 2f, centerY() - radius / 2f, radius, radius);
            batch.setColor(Color.WHITE);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

}
