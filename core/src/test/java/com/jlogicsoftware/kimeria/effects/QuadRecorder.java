package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * A batch that records where each region draw puts its quad, as {@code SpriteBatch} would: {@code (x, y, width, height)}, or the
 * rotated form {@code (x, y, originX, originY, width, height, scaleX, scaleY, rotation)}, turned about {@code origin} (scale 1 only).
 */
final class QuadRecorder {
    static final int PLAIN = 4, ROTATED = 9;
    final List<float[]> quads = new ArrayList<>();
    final Batch batch = (Batch) Proxy.newProxyInstance(QuadRecorder.class.getClassLoader(), new Class<?>[]{Batch.class},
        (proxy, method, args) -> {
            if (method.getName().equals("draw")) {
                float[] quad = new float[args.length - 1];
                for (int i = 0; i < quad.length; i++) quad[i] = (Float) args[i + 1];
                quads.add(quad);
            }
            return null;
        });

    void clear() { quads.clear(); }

    /** World X of corner {@code c} (0..3): bit 0 picks the right side, bit 1 the lower one. */
    static float cornerX(float[] q, int c) {
        if (q.length == PLAIN) return q[0] + (c & 1) * q[2];
        float dx = (c & 1) * q[4] - q[2], dy = (c >> 1) * q[5] - q[3];
        return q[0] + q[2] + dx * MathUtils.cosDeg(q[8]) - dy * MathUtils.sinDeg(q[8]);
    }

    static float cornerY(float[] q, int c) {
        if (q.length == PLAIN) return q[1] + (c >> 1) * q[3];
        float dx = (c & 1) * q[4] - q[2], dy = (c >> 1) * q[5] - q[3];
        return q[1] + q[3] + dx * MathUtils.sinDeg(q[8]) + dy * MathUtils.cosDeg(q[8]);
    }
}
