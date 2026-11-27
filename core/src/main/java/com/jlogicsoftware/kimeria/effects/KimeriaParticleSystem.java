package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * Allocation-free particle system on fixed struct-of-arrays storage (#285, docs/DEVELOPMENT.md "Particles and the allocation guard").
 * Capacity is fixed at construction; {@link #spawn} writes into the next free slot and, when the pool is full, drops the
 * oldest particle, so nothing allocates after the constructor. Only arrays and float maths are used, so the TeaVM web
 * build needs nothing special.
 *
 * <p>Every particle flies in a straight line from its start to its end over its life ({@code lerp}; {@link #KIND_SPARK} eases it
 * out), with a size and
 * alpha curve picked by its kind: {@link #KIND_GLOW} fades with the square of its remaining life, shrinks and is drawn
 * additively; {@link #KIND_SMOKE} fades linearly, grows and is drawn with normal blending; {@link #KIND_GRIT} fades
 * linearly, keeps its size and is drawn with normal blending; {@link #KIND_FLAME} stays opaque, shrinks to nothing and is
 * drawn with normal blending, so it dies out without an alpha fade (a fading warm colour over the blue sky turns olive or
 * magenta, #397). World Y grows downward, so a
 * particle that rises has an end Y smaller than its start Y.
 *
 * <p>Live particles are kept dense and in spawn order (index 0 is the oldest). Each system owns one
 * {@link RandomXS128}, the algorithm behind {@code MathUtils.random}, seeded in the constructor so a seed reproduces a run.
 */
public final class KimeriaParticleSystem {
    /** Additive, shrinking, alpha (1 - t)^2: flames, embers and sparks. */
    public static final byte KIND_GLOW = 0;
    /** Normal blending, growing, alpha 1 - t: smoke. */
    public static final byte KIND_SMOKE = 1;
    /** Normal blending, constant size, alpha 1 - t: falling grit drawn as hard squares (#239). */
    public static final byte KIND_GRIT = 2;
    /** Normal blending, fully opaque, size {@code size * (1 - t)}: a hard flame pixel that shrinks out instead of fading (#397). */
    public static final byte KIND_FLAME = 3;

    /**
     * Normal blending, a falling leaf (#473): drifts down its line while swaying sideways on a sine, flips like a thin plate (its width runs from edge-on to full) and swings with the sway, fades in and out over the first and last {@code LEAF_EDGE} of its life, opaque between.
     * Spawn it with {@link #spawnLeaf}.
     */
    public static final byte KIND_LEAF = 4;

    /**
     * Additive, constant size: a point riding an ellipse (pixelfx portal, ported; see THIRD_PARTY_NOTICES.md). It sits at
     * {@code centre + (cos a * rx * rho, sin a * ry * rho)} with the angle {@code a} turning from its start by its sweep over its
     * life and the radius factor {@code rho} running from its start to its end value. Alpha ramps in and out over the first and last
     * {@code ORBIT_EDGE} of its life and is full between, so a stream of overlapping orbits reads as steady light. A sweep of 0 and
     * equal rho values make a light that only breathes in place. Spawn it with {@link #spawnOrbit}; fixed storage, no allocation.
     */
    public static final byte KIND_ORBIT = 5;

    /**
     * Additive, the upper half of a glow: its flat edge lies on the particle's y, so a flash on a floor never reaches under it
     * (D147). Size and alpha as {@link #KIND_GLOW}; as tall as half its width.
     */
    public static final byte KIND_DOME = 6;

    /**
     * Normal blending, a spark that flies out and brakes (D152): its place on the line is {@code 1 - (1 - t)^4}, so it covers
     * most of the way early and then almost hangs. A square head of size {@code size * (1 - 0.4 t)} drags a streak back towards
     * its start, {@code SPARK_TAIL_PX * (1 - t)^3} long and never longer than the way already flown, so it shrinks into a dot.
     * Opaque until {@code SPARK_FADE_FROM}, then it dims to {@code SPARK_END_ALPHA} and is still seen when its life ends.
     */
    public static final byte KIND_SPARK = 7;

    private static final float SPARK_SHRINK = 0.4f, SPARK_TAIL_PX = 5f, SPARK_TAIL_WIDTH = 0.7f;
    private static final float SPARK_FADE_FROM = 0.7f, SPARK_END_ALPHA = 0.45f;

    private static final float ORBIT_EDGE = 0.25f;
    private static final float LEAF_EDGE = 0.12f, LEAF_PEAK_ALPHA = 0.85f;
    private static final float LEAF_SWAY_SECONDS = 3.4f, LEAF_TUMBLE_SECONDS = 2.2f, LEAF_FLAT_RATIO = 0.4f, LEAF_EDGE_ON = 0.15f;
    private static final float LEAF_TILT_DEG = 50f, LEAF_TILT_LAG = 0.25f;
    private static final float PEAK_ALPHA = 0.7f;
    private static final float GLOW_SHRINK = 0.4f;
    private static final float SMOKE_GROWTH = 1f;
    private static final float FULL = 1f, DEGREES = 360f, HALF = .5f;

    private final int capacity;
    private final RandomXS128 random;
    private final float[] x, y, fromX, fromY, toX, toY, life, maxLife, size, r, g, b, swayAmp, swayPhase, rhoFrom, rhoTo;
    private final byte[] kind;
    private int count;
    /** The alpha multiplier of the render in progress; {@link #FULL} outside {@link #render}. */
    private float scale = FULL;

    public KimeriaParticleSystem(int capacity, long seed) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be at least 1");
        this.capacity = capacity;
        this.random = new RandomXS128(seed);
        x = new float[capacity]; y = new float[capacity];
        fromX = new float[capacity]; fromY = new float[capacity];
        toX = new float[capacity]; toY = new float[capacity];
        life = new float[capacity]; maxLife = new float[capacity]; size = new float[capacity];
        r = new float[capacity]; g = new float[capacity]; b = new float[capacity];
        swayAmp = new float[capacity]; swayPhase = new float[capacity];
        rhoFrom = new float[capacity]; rhoTo = new float[capacity];
        kind = new byte[capacity];
    }

    public RandomXS128 random() { return random; }
    public int capacity() { return capacity; }
    /** Number of live particles. */
    public int count() { return count; }

    /** Adds a particle that lives {@code lifeSeconds}; when the pool is full the oldest particle is dropped. */
    public void spawn(byte kindOf, float startX, float startY, float endX, float endY, float lifeSeconds, float baseSize,
                      float red, float green, float blue) {
        if (count == capacity) removeOldest();
        int i = count++;
        kind[i] = kindOf;
        x[i] = fromX[i] = startX;
        y[i] = fromY[i] = startY;
        toX[i] = endX;
        toY[i] = endY;
        life[i] = maxLife[i] = lifeSeconds;
        size[i] = baseSize;
        r[i] = red; g[i] = green; b[i] = blue;
        swayAmp[i] = 0; swayPhase[i] = 0;
    }

    /**
     * Adds a {@link #KIND_LEAF}: a straight fall from start to end plus a sideways sway of {@code amplitude} px, started
     * at {@code phase} (0 to 1 of a cycle). {@code size} is the leaf's long side.
     */
    public void spawnLeaf(float startX, float startY, float endX, float endY, float lifeSeconds, float baseSize, float amplitude,
                          float phase, float red, float green, float blue) {
        spawn(KIND_LEAF, startX, startY, endX, endY, lifeSeconds, baseSize, red, green, blue);
        swayAmp[count - 1] = amplitude;
        swayPhase[count - 1] = phase;
    }

    /**
     * Adds a {@link #KIND_ORBIT}: centred on ({@code centreX}, {@code centreY}) with radii {@code radiusX} and {@code radiusY} at
     * {@code rho} 1, starting at {@code startTurn} (0 to 1 of a full turn, 0 pointing right, turning towards larger Y) and turning
     * {@code sweepTurns} over its life while {@code rho} runs from {@code rhoStart} to {@code rhoEnd}. {@code size} is its side.
     */
    public void spawnOrbit(float centreX, float centreY, float radiusX, float radiusY, float startTurn, float sweepTurns,
                           float rhoStart, float rhoEnd, float lifeSeconds, float baseSize, float red, float green, float blue) {
        spawn(KIND_ORBIT, centreX, centreY, radiusX, radiusY, lifeSeconds, baseSize, red, green, blue);
        int i = count - 1;
        swayPhase[i] = startTurn; swayAmp[i] = sweepTurns; rhoFrom[i] = rhoStart; rhoTo[i] = rhoEnd;
        placeOrbit(i, 0f);
    }

    /** Moves the particle just spawned {@code seconds} into its life (less than its life), so an effect that starts mid-stream shows its first frame already running. */
    public void ageLast(float seconds) {
        int i = count - 1;
        life[i] = Math.max(life[i] - seconds, 0f);
        float t = FULL - life[i] / maxLife[i];
        if (kind[i] == KIND_ORBIT) placeOrbit(i, t);
    }

    private void placeOrbit(int i, float t) {
        float turn = swayPhase[i] + swayAmp[i] * t, rho = rhoFrom[i] + (rhoTo[i] - rhoFrom[i]) * t;
        x[i] = fromX[i] + MathUtils.cosDeg(DEGREES * turn) * toX[i] * rho;
        y[i] = fromY[i] + MathUtils.sinDeg(DEGREES * turn) * toY[i] * rho;
    }

    private static boolean additive(byte k) { return k == KIND_GLOW || k == KIND_ORBIT || k == KIND_DOME; }

    private void removeOldest() {
        for (int i = 1; i < count; i++) copy(i, i - 1);
        count--;
    }

    private void copy(int from, int to) {
        x[to] = x[from]; y[to] = y[from];
        fromX[to] = fromX[from]; fromY[to] = fromY[from];
        toX[to] = toX[from]; toY[to] = toY[from];
        life[to] = life[from]; maxLife[to] = maxLife[from]; size[to] = size[from];
        r[to] = r[from]; g[to] = g[from]; b[to] = b[from];
        swayAmp[to] = swayAmp[from]; swayPhase[to] = swayPhase[from];
        rhoFrom[to] = rhoFrom[from]; rhoTo[to] = rhoTo[from];
        kind[to] = kind[from];
    }

    /** Ages every particle by {@code dt}, moves the live ones and drops the dead, keeping spawn order. */
    public void update(float dt) {
        int live = 0;
        for (int i = 0; i < count; i++) {
            float remaining = life[i] - dt;
            if (remaining <= 0) continue;
            if (live != i) copy(i, live);
            life[live] = remaining;
            float t = FULL - remaining / maxLife[live];
            if (kind[live] == KIND_ORBIT) { placeOrbit(live, t); live++; continue; }
            if (kind[live] == KIND_SPARK) t = brake(t);
            // Vector2.lerp's form, kept so positions match the pre-migration torch to the float.
            float rest = FULL - t;
            x[live] = fromX[live] * rest + toX[live] * t;
            y[live] = fromY[live] * rest + toY[live] * t;
            if (kind[live] == KIND_LEAF) x[live] += swayAmp[live] * MathUtils.sinDeg(DEGREES * swayCycle(live, t));
            live++;
        }
        count = live;
    }

    /** Quartic ease-out: 0 at 0, 1 at 1, fastest at the start. */
    private static float brake(float t) {
        float rest = FULL - t;
        return FULL - rest * rest * rest * rest;
    }

    private float swayCycle(int i, float t) { return swayPhase[i] + t * maxLife[i] / LEAF_SWAY_SECONDS; }

    public void clear() { count = 0; }

    /** Position, size and alpha as {@link #render} uses them; for tests. */
    public float x(int i) { return x[i]; }
    /** Where the particle appeared (its path's start). */
    public float fromX(int i) { return fromX[i]; }
    public float y(int i) { return y[i]; }
    public byte kind(int i) { return kind[i]; }
    public float life(int i) { return life[i]; }
    public float red(int i) { return r[i]; }
    public float green(int i) { return g[i]; }
    public float blue(int i) { return b[i]; }
    public float maxLife(int i) { return maxLife[i]; }
    /** Width of the drawn rectangle: a leaf is a plate that flips about its long axis, so its width runs from edge-on to full. */
    public float drawWidth(int i) {
        if (kind[i] != KIND_LEAF) return drawSize(i);
        float t = FULL - life[i] / maxLife[i];
        float flip = Math.abs(MathUtils.cosDeg(DEGREES * (swayPhase[i] + t * maxLife[i] / LEAF_TUMBLE_SECONDS)));
        return Math.max(1f, size[i] * (LEAF_EDGE_ON + (FULL - LEAF_EDGE_ON) * flip));
    }
    /** Height of the drawn rectangle: a leaf's short side, constant; half the width for a dome; the width for the rest. */
    public float drawHeight(int i) {
        if (kind[i] == KIND_LEAF) return size[i] * LEAF_FLAT_RATIO;
        return kind[i] == KIND_DOME ? drawSize(i) * HALF : drawSize(i);
    }
    /** Length of a spark's streak behind its head, towards its start; 0 for the rest. */
    public float tailLength(int i) {
        if (kind[i] != KIND_SPARK) return 0;
        float rest = life[i] / maxLife[i];
        float dx = x[i] - fromX[i], dy = y[i] - fromY[i];
        return Math.min((float) Math.sqrt(dx * dx + dy * dy), SPARK_TAIL_PX * rest * rest * rest);
    }
    /** Width of a spark's streak; 0 for the rest. */
    public float tailWidth(int i) { return kind[i] == KIND_SPARK ? drawSize(i) * SPARK_TAIL_WIDTH : 0; }
    /**
     * Rotation in degrees: a leaf's rectangle swings like a pendulum with its sway; a spark's streak lies along its line of
     * flight (0 pointing right, turning towards larger Y); 0 for the rest.
     */
    public float tilt(int i) {
        if (kind[i] == KIND_SPARK) return MathUtils.atan2Deg(toY[i] - fromY[i], toX[i] - fromX[i]);
        if (kind[i] != KIND_LEAF) return 0;
        float t = FULL - life[i] / maxLife[i];
        return LEAF_TILT_DEG * MathUtils.sinDeg(DEGREES * (swayCycle(i, t) + LEAF_TILT_LAG));
    }
    public float drawSize(int i) {
        float t = FULL - life[i] / maxLife[i];
        if (kind[i] == KIND_GRIT || kind[i] == KIND_LEAF || kind[i] == KIND_ORBIT) return size[i];
        if (kind[i] == KIND_FLAME) return size[i] * (FULL - t);
        if (kind[i] == KIND_SPARK) return size[i] * (FULL - SPARK_SHRINK * t);
        return size[i] * (kind[i] == KIND_SMOKE ? (FULL + SMOKE_GROWTH * t) : (FULL - GLOW_SHRINK * t));
    }
    public float alpha(int i) {
        if (kind[i] == KIND_FLAME) return FULL;
        float t = FULL - life[i] / maxLife[i];
        if (kind[i] == KIND_SPARK)
            return FULL - (FULL - SPARK_END_ALPHA) * Math.max(0f, t - SPARK_FADE_FROM) / (FULL - SPARK_FADE_FROM);
        if (kind[i] == KIND_ORBIT) return Math.min(FULL, Math.min(t, FULL - t) / ORBIT_EDGE);
        if (kind[i] == KIND_LEAF) return LEAF_PEAK_ALPHA * Math.min(FULL, Math.min(t, FULL - t) / LEAF_EDGE);
        return (kind[i] == KIND_GLOW || kind[i] == KIND_DOME ? (FULL - t) * (FULL - t) : (FULL - t)) * PEAK_ALPHA;
    }

    /**
     * Draws the normal-blended particles (smoke, grit, flame, leaves and sparks, behind), then the additive ones, and restores white and normal blending.
     * One blend switch per pass, and none for a pass with nothing to draw.
     */
    public void render(Batch batch, TextureRegion region) { render(batch, region, FULL); }

    /** As {@link #render(Batch, TextureRegion)} with every alpha multiplied by {@code intensity} (0 to 1): a whole effect dims or brightens at once, with no respawn. */
    public void render(Batch batch, TextureRegion region, float intensity) {
        if (count == 0 || intensity <= 0f) return;
        scale = intensity;
        draw(batch, region, true);
        draw(batch, region, false);
        scale = FULL;
        batch.setColor(FULL, FULL, FULL, FULL);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void draw(Batch batch, TextureRegion region, boolean normalPass) {
        boolean switched = false;
        for (int i = 0; i < count; i++) {
            if (additive(kind[i]) == normalPass) continue;
            if (!switched) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, normalPass ? GL20.GL_ONE_MINUS_SRC_ALPHA : GL20.GL_ONE);
                switched = true;
            }
            batch.setColor(r[i], g[i], b[i], alpha(i) * scale);
            if (kind[i] == KIND_LEAF) {
                float w = drawWidth(i), h = drawHeight(i);
                batch.draw(region, x[i] - w / 2f, y[i] - h / 2f, w / 2f, h / 2f, w, h, FULL, FULL, tilt(i));
            } else if (kind[i] == KIND_DOME) {
                // The region's upper half of V: its dense middle row falls on the rectangle's lower edge, the particle's y.
                float s = drawSize(i), middle = (region.getV() + region.getV2()) * HALF;
                batch.draw(region.getTexture(), x[i] - s * HALF, y[i] - s * HALF, s, s * HALF, region.getU(), region.getV(), region.getU2(), middle);
            } else if (kind[i] == KIND_SPARK) {
                float s = drawSize(i), tail = tailLength(i), w = tailWidth(i);
                // The streak ends in the head's centre and turns about it, so it points back along the line of flight.
                if (tail > 0) batch.draw(region, x[i] - tail, y[i] - w * HALF, tail, w * HALF, tail, w, FULL, FULL, tilt(i));
                batch.draw(region, x[i] - s * HALF, y[i] - s * HALF, s, s);
            } else { // the other kinds do not rotate: the plain draw, as before #473
                float s = drawSize(i);
                batch.draw(region, x[i] - s / 2f, y[i] - s / 2f, s, s);
            }
        }
    }
}
