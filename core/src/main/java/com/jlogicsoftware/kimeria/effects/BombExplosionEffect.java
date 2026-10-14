package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.jlogicsoftware.kimeria.entity.Entity;

/** Original upward plume, anchored to the bomb's ground point near the quad's bottom. */
public class BombExplosionEffect extends Entity {
    private static final float ORIGIN_Y = 0.85f;
    private final float duration;
    private float elapsed = 0f;

    public BombExplosionEffect(float centerX, float groundY, float size, float duration) {
        super(centerX - size / 2f, groundY - size * ORIGIN_Y, size, size);
        this.duration = duration;
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
        if (elapsed >= duration) removeFromParent();
    }

    @Override
    public void render(Batch batch) {
        float progress = MathUtils.clamp(elapsed / duration, 0f, 1f);
        ShaderProgram shader = Shaders.load("bomb_explosion.frag");
        ShaderProgram previous = batch.getShader();
        batch.setShader(shader);
        shader.setUniformf("uSize", size.x, size.y);
        shader.setUniformf("uTime", elapsed);
        shader.setUniformf("uProgress", progress);
        shader.setUniformf("uOriginY", ORIGIN_Y);
        batch.draw(Shaders.whiteQuad(), position.x, position.y, size.x, size.y);
        batch.setShader(previous);
    }
}
