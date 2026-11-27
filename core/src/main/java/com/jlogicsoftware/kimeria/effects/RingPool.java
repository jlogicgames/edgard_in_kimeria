package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;

/**
 * A fixed pool of expanding rings through the existing {@code shockwave.frag} (the uniforms {@link ShockwaveEffect} sets), shared
 * by {@link FireballFx} (impact rings), {@link PickupSparksFx} (the pickup ring) and {@link SporeCloud} (the false honey fungus's
 * burst, D152): nothing is created per ring, there is no
 * screen distortion and no shader was edited. Each slot is live until its own ring's life ends; a new ring takes the first free slot
 * and only when every slot is live does it reuse the oldest one. World Y grows downward; the ring is centred on its point.
 */
final class RingPool {
    private static final String SHADER = "shockwave.frag";
    /** {@code shockwave.frag}'s ring radius in quad UV (the ring fills the quad), as {@link ShockwaveEffect} sets it. */
    private static final float UV_RADIUS = .5f;
    /**
     * {@code shockwave.frag} grows the ring with {@code smoothstep(0.02, 1, progress)}, which crawls at the start and also gates its
     * fade, so a linear progress shows the ring late. The ring is fed {@code START + (END - START) * (1 - (1 - t)^2)} instead: it
     * starts at about 30 % of its radius, visible from the first frame, and expands fast. It stops at {@code END}: the shader's last
     * stretch adds so little light that the ring reads grey over the blue sky, so the ring ends while it still has its colour.
     */
    private static final float START = .3f, END = .88f;
    private static final int GREEN = 1, BLUE = 2;

    private final int capacity;
    private final float[] x, y, age, life, radius, width, color;
    private final boolean[] live;
    private final long[] started;
    private int count, next;
    private long startCount;

    /** {@code color} is the intensity {@code shockwave.frag} multiplies by its fade and adds to what is behind (three floats: r, g, b; copied). */
    RingPool(int capacity, float[] ringColor) {
        this.capacity = capacity;
        x = new float[capacity]; y = new float[capacity]; age = new float[capacity]; life = new float[capacity];
        radius = new float[capacity]; width = new float[capacity]; live = new boolean[capacity]; started = new long[capacity];
        color = ringColor.clone();
    }

    /** Live rings. */
    public int count() { return count; }

    /** The radius the {@code i}-th live ring grows to, oldest first ({@code i} below {@link #count}). */
    public float radius(int i) { return radius[slot(i)]; }
    /** The centre of the {@code i}-th live ring, oldest first. */
    public float centreX(int i) { return x[slot(i)]; }
    public float centreY(int i) { return y[slot(i)]; }

    /** The slot of the {@code i}-th live ring, oldest first; for the accessors, which only tests read. */
    private int slot(int i) {
        long after = Long.MIN_VALUE;
        for (int n = 0; n <= i; n++) {
            int found = -1;
            for (int s = 0; s < capacity; s++)
                if (live[s] && started[s] > after && (found < 0 || started[s] < started[found])) found = s;
            if (found < 0) throw new IndexOutOfBoundsException("ring " + i);
            if (n == i) return found;
            after = started[found];
        }
        throw new IndexOutOfBoundsException("ring " + i);
    }

    /** Starts a ring in the first free slot from {@code next} on; only when every slot is live does it reuse the oldest ring. */
    public void start(float cx, float cy, float lifeSeconds, float ringRadius, float ringWidth) {
        int i = -1;
        for (int n = 0; n < capacity && i < 0; n++) if (!live[(next + n) % capacity]) i = (next + n) % capacity;
        if (i < 0) {
            i = 0;
            for (int s = 1; s < capacity; s++) if (started[s] < started[i]) i = s;
        } else {
            live[i] = true;
            count++;
        }
        next = (i + 1) % capacity;
        started[i] = ++startCount;
        x[i] = cx; y[i] = cy; age[i] = 0; life[i] = lifeSeconds; radius[i] = ringRadius; width[i] = ringWidth;
    }

    /** Each ring is freed when its own life ends: a short ring started after a long one ends first. */
    public void update(float dt) {
        for (int i = 0; i < capacity; i++) {
            if (!live[i]) continue;
            age[i] += dt;
            if (age[i] >= life[i]) { live[i] = false; count--; }
        }
    }

    /** Each live ring through {@code shockwave.frag}, added; the shader and normal blending are restored. */
    public void render(Batch batch) {
        if (count == 0) return;
        // Decorative: a GPU (or the headless tests) that cannot build the shader simply shows no ring.
        ShaderProgram shader = Shaders.tryLoad(SHADER);
        if (shader == null) return;
        ShaderProgram previous = batch.getShader();
        batch.setShader(shader);
        // shockwave.frag writes premultiplied colour: added, it lights the sky; under normal blending its faint edge darkens it.
        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
        for (int i = 0; i < capacity; i++) {
            if (!live[i]) continue;
            float diameter = radius[i] * 2;
            shader.setUniformf("uSize", diameter, diameter);
            shader.setUniformf("uTime", age[i]);
            float t = MathUtils.clamp(age[i] / life[i], 0, 1), rest = 1 - t;
            shader.setUniformf("uProgress", START + (END - START) * (1 - rest * rest));
            shader.setUniformf("uMaxRadius", UV_RADIUS);
            shader.setUniformf("uWidth", width[i] / diameter);
            shader.setUniformf("uColor", color[0], color[GREEN], color[BLUE]);
            batch.draw(Shaders.whiteQuad(), x[i] - radius[i], y[i] - radius[i], diameter, diameter);
            batch.flush(); // the uniforms change per ring: draw this one before the next sets them
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setShader(previous);
    }

    public void clear() {
        count = 0;
        for (int i = 0; i < capacity; i++) live[i] = false;
    }
}
