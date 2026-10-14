package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.jlogicsoftware.kimeria.entity.Entity;

/** Port of Dart's {@code ShockwaveEffect}: an expanding thin ring, used by the F3 debug hotkey. */
public class ShockwaveEffect extends Entity {
    private final float duration;
    private float elapsed = 0f;
    private final Color color;
    private final float maxRadiusPx;
    private final float ringWidthPx;

    public ShockwaveEffect(float centerX, float centerY, float duration, float maxRadiusPx, float ringWidthPx) {
        this(centerX, centerY, duration, maxRadiusPx, ringWidthPx, Color.valueOf("fff2b2"));
    }

    public ShockwaveEffect(float centerX, float centerY, float duration, float maxRadiusPx, float ringWidthPx, Color color) {
        super(centerX - maxRadiusPx, centerY - maxRadiusPx, maxRadiusPx * 2, maxRadiusPx * 2);
        this.duration = duration;
        this.maxRadiusPx = maxRadiusPx;
        this.ringWidthPx = ringWidthPx;
        this.color = color;
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
        if (elapsed >= duration) removeFromParent();
    }

    @Override
    public void render(Batch batch) {
        float progress = MathUtils.clamp(elapsed / duration, 0f, 1f);
        ShaderProgram shader = Shaders.load("shockwave.frag");
        ShaderProgram previous = batch.getShader();
        batch.setShader(shader);
        shader.setUniformf("uSize", size.x, size.y);
        shader.setUniformf("uTime", elapsed);
        shader.setUniformf("uProgress", progress);
        shader.setUniformf("uMaxRadius", 0.5f);
        shader.setUniformf("uWidth", ringWidthPx / (maxRadiusPx * 2f));
        shader.setUniformf("uColor", color.r, color.g, color.b);
        batch.draw(Shaders.whiteQuad(), position.x, position.y, size.x, size.y);
        batch.setShader(previous);
    }
}
