package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.RandomXS128;

/**
 * Allocation-free particle system on fixed struct-of-arrays storage (#285, docs/DEVELOPMENT.md "Particle system plan").
 * Capacity is fixed at construction; {@link #spawn} writes into the next free slot and, when the pool is full, drops the
 * oldest particle, so nothing allocates after the constructor. Only arrays and float maths are used, so the TeaVM web
 * build needs nothing special.
 *
 * <p>Every particle flies in a straight line from its start to its end over its life ({@code lerp}), with a size and
 * alpha curve picked by its kind: {@link #KIND_GLOW} fades with the square of its remaining life, shrinks and is drawn
 * additively; {@link #KIND_SMOKE} fades linearly, grows and is drawn with normal blending; {@link #KIND_GRIT} fades
 * linearly, keeps its size and is drawn with normal blending. World Y grows downward, so a
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

    private static final float PEAK_ALPHA = 0.7f;
    private static final float GLOW_SHRINK = 0.4f;
    private static final float SMOKE_GROWTH = 1f;
    private static final float FULL = 1f;

    private final int capacity;
    private final RandomXS128 random;
    private final float[] x, y, fromX, fromY, toX, toY, life, maxLife, size, r, g, b;
    private final byte[] kind;
    private int count;

    public KimeriaParticleSystem(int capacity, long seed) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be at least 1");
        this.capacity = capacity;
        this.random = new RandomXS128(seed);
        x = new float[capacity]; y = new float[capacity];
        fromX = new float[capacity]; fromY = new float[capacity];
        toX = new float[capacity]; toY = new float[capacity];
        life = new float[capacity]; maxLife = new float[capacity]; size = new float[capacity];
        r = new float[capacity]; g = new float[capacity]; b = new float[capacity];
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
    }

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
            // Vector2.lerp's form, kept so positions match the pre-migration torch to the float.
            float rest = FULL - t;
            x[live] = fromX[live] * rest + toX[live] * t;
            y[live] = fromY[live] * rest + toY[live] * t;
            live++;
        }
        count = live;
    }

    public void clear() { count = 0; }

    /** Position, size and alpha as {@link #render} uses them; for tests. */
    public float x(int i) { return x[i]; }
    public float y(int i) { return y[i]; }
    public byte kind(int i) { return kind[i]; }
    public float life(int i) { return life[i]; }
    public float maxLife(int i) { return maxLife[i]; }
    public float drawSize(int i) {
        float t = FULL - life[i] / maxLife[i];
        if (kind[i] == KIND_GRIT) return size[i];
        return size[i] * (kind[i] == KIND_SMOKE ? (FULL + SMOKE_GROWTH * t) : (FULL - GLOW_SHRINK * t));
    }
    public float alpha(int i) {
        float t = FULL - life[i] / maxLife[i];
        return (kind[i] == KIND_GLOW ? (FULL - t) * (FULL - t) : (FULL - t)) * PEAK_ALPHA;
    }

    /**
     * Draws the normal-blended particles (smoke and grit, behind), then the additive ones, and restores white and normal blending.
     * One blend switch per pass, and none for a pass with nothing to draw.
     */
    public void render(Batch batch, TextureRegion region) {
        if (count == 0) return;
        draw(batch, region, true);
        draw(batch, region, false);
        batch.setColor(FULL, FULL, FULL, FULL);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void draw(Batch batch, TextureRegion region, boolean normalPass) {
        boolean switched = false;
        for (int i = 0; i < count; i++) {
            if ((kind[i] != KIND_GLOW) != normalPass) continue;
            if (!switched) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, normalPass ? GL20.GL_ONE_MINUS_SRC_ALPHA : GL20.GL_ONE);
                switched = true;
            }
            float s = drawSize(i);
            batch.setColor(r[i], g[i], b[i], alpha(i));
            batch.draw(region, x[i] - s / 2f, y[i] - s / 2f, s, s);
        }
    }
}
