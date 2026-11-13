package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.RandomXS128;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.effects.SoftDot;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.function.Supplier;

/**
 * The harmless honey fungus (D63, #295). Tiled type {@code HoneyFungus}. Its burst is a puff of warm golden spores on its own
 * {@link KimeriaParticleSystem}: no hitbox, no collision, nothing reads it. After the burst the cap stays deflated.
 * Particles advance in {@link #update} only (pause freezes them) and die with the room. Its random stream is seeded from its
 * position, so a run is reproducible. World Y grows downward: the spores rise, so their end Y is smaller than their start Y.
 */
public final class HoneyFungus extends FungusTrap {
    public static final int PARTICLE_CAPACITY = 24, SPORES_PER_BURST = 16;
    /** Seconds a spore lives; the burst state lasts as long as the longest. */
    public static final float SPORE_LIFE_MIN = 0.6f, SPORE_LIFE_MAX = 1.0f;
    private static final float SPORE_SIZE_PX = 3f, SPAWN_SPREAD_PX = 8f, DRIFT_PX = 18f, RISE_MIN_PX = 14f, RISE_MAX_PX = 34f;
    private static final float SPORE_R = 0.96f, SPORE_G = 0.74f, SPORE_B = 0.28f;
    private static final float SPAWN_FROM_TOP_PX = 4f, HALF = 0.5f;
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;

    private final KimeriaParticleSystem spores;

    public HoneyFungus(Assets assets, TrapWarnings warnings, float x, float y, float w, float h,
                       float zoneLeft, float zoneRight, Supplier<Rectangle> heroBody) {
        super(assets, SpriteSheets.TRAP_HONEY_FUNGUS, warnings, x, y, w, h, zoneLeft, zoneRight, heroBody);
        spores = new KimeriaParticleSystem(PARTICLE_CAPACITY, (Float.floatToIntBits(x) * SEED_MIX) ^ Float.floatToIntBits(y));
    }

    public static HoneyFungus fromMap(Assets assets, TrapWarnings warnings, TiledObject o, Supplier<Rectangle> heroBody) {
        float[] zone = zoneOf(o);
        return new HoneyFungus(assets, warnings, o.x, o.y, o.width, o.height, zone[0], zone[1], heroBody);
    }

    public KimeriaParticleSystem spores() { return spores; }

    @Override
    protected float burstSeconds() { return SPORE_LIFE_MAX; }

    @Override
    protected void burst() {
        RandomXS128 random = spores.random();
        float top = position.y + SPAWN_FROM_TOP_PX;
        for (int n = 0; n < SPORES_PER_BURST; n++) {
            float startX = centerX() + (random.nextFloat() - HALF) * 2f * SPAWN_SPREAD_PX;
            float life = SPORE_LIFE_MIN + random.nextFloat() * (SPORE_LIFE_MAX - SPORE_LIFE_MIN);
            float endX = startX + (random.nextFloat() - HALF) * 2f * DRIFT_PX;
            float endY = top - (RISE_MIN_PX + random.nextFloat() * (RISE_MAX_PX - RISE_MIN_PX));
            spores.spawn(KimeriaParticleSystem.KIND_GLOW, startX, top, endX, endY, life, SPORE_SIZE_PX, SPORE_R, SPORE_G, SPORE_B);
        }
    }

    @Override
    protected void updateBurst(float dt) { spores.update(dt); }

    @Override
    protected void renderBurst(Batch batch) {
        if (spores.count() > 0) spores.render(batch, SoftDot.region());
    }
}
