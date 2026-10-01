package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.jlogicsoftware.kimeria.entity.Entity;

/**
 * Port of Dart's coin-pickup {@code RippleEffect}: a ring expands from the coin and the
 * scene around it is pushed radially in and out, like a wave passing through the level.
 * <p>
 * The effect draws nothing itself. It only keeps the ripple's clock and parameters; while
 * one is live, {@code KimeriaGame} renders the world into an offscreen buffer and blits it
 * through {@code screen_effects.frag}, which reads this ripple through {@link #apply}.
 * Distances are in pixels and go to the shader as fractions of the view width, as both
 * reference implementations do.
 */
public class RippleEffect extends Entity {
    private final float duration;
    private final float maxRadius;
    private final float strength;
    private final float frequency;
    private final float decay;
    private float elapsed = 0f;

    public RippleEffect(float centerX, float centerY, float duration, float maxRadius, float strength, float frequency, float decay) {
        super(centerX, centerY, 0f, 0f);
        this.duration = duration;
        this.maxRadius = maxRadius;
        this.strength = strength;
        this.frequency = frequency;
        this.decay = decay;
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
        if (elapsed >= duration) removeFromParent();
    }

    /**
     * Sets the ripple uniforms of {@code screen_effects.frag}.
     *
     * @param viewX the world position of the view's top-left corner
     * @param viewY (the camera is Y-down, so this is the top edge)
     * @param viewW the view's size in world units
     */
    public void apply(ShaderProgram shader, float viewX, float viewY, float viewW, float viewH) {
        float progress = MathUtils.clamp(elapsed / duration, 0f, 1f);
        // The offscreen buffer's texture is bottom-up (see KimeriaGame.renderPostProcessedWorld),
        // so the Y-down world position is flipped into the texture's V.
        shader.setUniformf("uRippleCenter", (position.x - viewX) / viewW, 1f - (position.y - viewY) / viewH);
        shader.setUniformf("uRippleAspect", viewW / viewH);
        shader.setUniformf("uRippleTime", elapsed);
        shader.setUniformf("uRippleProgress", progress);
        shader.setUniformf("uRippleMaxRadius", maxRadius / viewW);
        shader.setUniformf("uRippleStrength", strength * (1f - progress) / viewW); // fades out over the ripple's life
        shader.setUniformf("uRippleFrequency", frequency);
        shader.setUniformf("uRippleDecay", decay);
    }

    /** Turns the ripple off in {@code screen_effects.frag} (the shader is shared, so this must be set explicitly). */
    public static void disable(ShaderProgram shader) {
        shader.setUniformf("uRippleStrength", 0f);
    }
}
