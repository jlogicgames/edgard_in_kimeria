package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The lethal spore cloud of the false honey fungus as particles plus a soft shader (#296, D63): a lime haze that swells out of the cap
 * tuft, drifts up and thins over {@code lifeSeconds}, and is gone when the hitbox is. The shader layer sits under the particles, which alone are a complete look if it cannot be built; both inside the lethal rectangle:
 * <ol>
 * <li>a swirling glow ({@code spore_cloud.frag}, skipped when the context cannot compile it) on a quad that is exactly the rectangle and fades to nothing at its border;</li>
 * <li>{@link #HAZE_COUNT} soft {@link KimeriaParticleSystem#KIND_SMOKE} puffs (normal blending, growing) and {@link #SPECK_COUNT}
 * {@link KimeriaParticleSystem#KIND_GLOW} specks (additive, shrinking) on a fixed pool.</li>
 * </ol>
 * Every particle's start and end are kept {@code drawSize / 2} (at its largest) inside the rectangle, and positions are a straight
 * lerp between them, so no particle quad ever leaves the rectangle (D5: what is drawn is no wider than what kills). The palette is
 * the {@code trap-spores} one, so the cloud stays clearly lime next to the golden {@link KimeriaParticleSystem#KIND_GLOW} burst of the
 * harmless fungus. World Y grows downward: the haze rises, so its end Y is smaller than its start Y.
 * <p>
 * Nothing allocates after construction. {@link #update} only runs while the game runs (pause freezes it); the caller clears it with
 * the hitbox. If the shader cannot be built the particles alone are drawn.
 */
public final class SporeCloud {
    public static final int HAZE_COUNT = 34, SPECK_COUNT = 12, PARTICLE_CAPACITY = HAZE_COUNT + SPECK_COUNT + 2;
    /** Debug runs (-Dkimeria.debug=true) log once, on the first burst, whether the shader layer loaded (never per frame). */
    private static final boolean DEBUG_LOG = Boolean.getBoolean("kimeria.debug");
    private static boolean logged;
    public static final String SHADER = "spore_cloud.frag";
    /** The longest particle outlives the cloud by this much so the pool is never empty while the hitbox is live; it is cleared with it. */
    public static final float LIFE_MARGIN = 0.05f;
    /** Palette of {@code trap-spores}: a6e04a, 5fa02a, e6f26a. */
    private static final float DARK_R = 0.373f, DARK_G = 0.627f, DARK_B = 0.165f;
    private static final float MID_R = 0.651f, MID_G = 0.878f, MID_B = 0.290f;
    private static final float LIGHT_R = 0.902f, LIGHT_G = 0.949f, LIGHT_B = 0.416f;
    private static final float HAZE_SIZE_MIN = 6f, HAZE_SIZE_MAX = 8f, SPECK_SIZE_MIN = 2.5f, SPECK_SIZE_MAX = 3.5f;
    private static final float HAZE_LIFE_MIN_FRACTION = 0.7f, SPECK_LIFE_MIN_FRACTION = 0.5f;
    private static final float TUFT_SPREAD_PX = 6f;
    /** Haze end points are biased upward: the end Y fraction is {@code random^HAZE_RISE_BIAS}-like (squared), 0 = top of the rectangle. */
    private static final float HALF = 0.5f, TWO = 2f, SMOOTH_A = 3f, SMOOTH_B = 2f;
    /** Timeline of the shader layer as fractions of the cloud's life: swell in, the share of strength lost by thinning, the fade-out tail. */
    private static final float SWELL_END = 0.25f, THIN_SHARE = 0.55f, TAIL_START = 0.9f, SHADER_PEAK = 0.9f, REACH_SWELL_END = 0.5f;

    private final KimeriaParticleSystem particles;
    private final float left, top, size, tuftX, tuftY, lifeSeconds;

    /**
     * @param left,top,size the lethal rectangle (a square)
     * @param tuftY where the cap tuft is, in world Y
     */
    public SporeCloud(float left, float top, float size, float tuftY, float lifeSeconds, long seed) {
        this.left = left;
        this.top = top;
        this.size = size;
        this.tuftX = left + size / TWO;
        this.tuftY = tuftY;
        this.lifeSeconds = lifeSeconds;
        particles = new KimeriaParticleSystem(PARTICLE_CAPACITY, seed);
    }

    public KimeriaParticleSystem particles() { return particles; }

    /** Releases the cloud: haze puffs and specks from the tuft towards points inside the rectangle. Called once, when the swell ends. */
    public void burst() {
        if (DEBUG_LOG && !logged) logShaderState();
        RandomXS128 random = particles.random();
        for (int n = 0; n < HAZE_COUNT; n++) {
            float s = HAZE_SIZE_MIN + random.nextFloat() * (HAZE_SIZE_MAX - HAZE_SIZE_MIN);
            float mix = random.nextFloat();
            spawn(KimeriaParticleSystem.KIND_SMOKE, s, s, HAZE_LIFE_MIN_FRACTION, true,
                DARK_R + (MID_R - DARK_R) * mix, DARK_G + (MID_G - DARK_G) * mix, DARK_B + (MID_B - DARK_B) * mix);
        }
        for (int n = 0; n < SPECK_COUNT; n++) {
            float s = SPECK_SIZE_MIN + random.nextFloat() * (SPECK_SIZE_MAX - SPECK_SIZE_MIN);
            spawn(KimeriaParticleSystem.KIND_GLOW, s, s / TWO, SPECK_LIFE_MIN_FRACTION, false, LIGHT_R, LIGHT_G, LIGHT_B);
        }
    }

    /** Once per run, debug only: says whether the shader layer compiled or fell back. */
    private static void logShaderState() {
        logged = true;
        String state = Shaders.tryLoad(SHADER) != null ? "loaded, drawn under the particles" : "FAILED to build, falling back to particles only (see the Shaders error above)";
        if (Gdx.app != null) Gdx.app.log("SporeCloud", "first burst: shader layer " + state);
    }

    /** One particle; {@code inset} is its largest half extent, kept between its centre and the rectangle's edge. */
    private void spawn(byte kind, float baseSize, float inset, float lifeMinFraction, boolean rise, float r, float g, float b) {
        RandomXS128 random = particles.random();
        float span = size - TWO * inset;
        float startX = clamp(tuftX + (random.nextFloat() - HALF) * TWO * TUFT_SPREAD_PX, left + inset, left + inset + span);
        float startY = clamp(tuftY + (random.nextFloat() - HALF) * TWO * TUFT_SPREAD_PX, top + inset, top + inset + span);
        float endX = left + inset + random.nextFloat() * span;
        float rand = random.nextFloat();
        float endY = top + inset + (rise ? rand * rand : rand) * span;
        float life = lifeSeconds * (lifeMinFraction + random.nextFloat() * (1f - lifeMinFraction)) + LIFE_MARGIN;
        particles.spawn(kind, startX, startY, endX, endY, life, baseSize, r, g, b);
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }

    public void update(float dt) { particles.update(dt); }

    public void clear() { particles.clear(); }

    /** The shader layer's strength at {@code progress} (0..1 of the life): swells in, thins, and is exactly 0 when the hitbox ends. */
    public static float strength(float progress) {
        float p = MathUtils.clamp(progress, 0f, 1f);
        float in = smooth(0f, SWELL_END, p);
        float thin = 1f - THIN_SHARE * p;
        float tail = 1f - smooth(TAIL_START, 1f, p);
        return SHADER_PEAK * in * thin * tail;
    }

    /** How far the haze has reached out of the tuft (0..1) at {@code progress}: quick at first, then settled. */
    public static float swell(float progress) {
        float t = MathUtils.clamp(progress / REACH_SWELL_END, 0f, 1f);
        return 1f - (1f - t) * (1f - t);
    }

    /** GLSL smoothstep. */
    public static float smooth(float edge0, float edge1, float x) {
        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (SMOOTH_A - SMOOTH_B * t);
    }

    /** Draws the shader haze then the particles; {@code elapsed} is the seconds since the burst. Restores the batch's shader, colour and blending. */
    public void render(Batch batch, float elapsed) {
        float progress = elapsed / lifeSeconds;
        ShaderProgram shader = Shaders.tryLoad(SHADER);
        if (shader != null) {
            ShaderProgram previous = batch.getShader();
            batch.setShader(shader);
            shader.setUniformf("uTime", elapsed);
            shader.setUniformf("uSwell", swell(progress));
            shader.setUniformf("uAlpha", strength(progress));
            shader.setUniformf("uOrigin", HALF, (tuftY - top) / size);
            batch.draw(Shaders.whiteQuad(), left, top, size, size);
            batch.setShader(previous);
        }
        if (particles.count() > 0) particles.render(batch, SoftDot.region());
    }
}
