package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.WorldObject;

import java.util.function.Supplier;

/** Menu-only full-viewport drifting fog overlay. Gameplay levels do not create this effect. */
public class FogEffect implements WorldObject {
    private final Supplier<Rectangle> visibleWorldRect;
    private float time = 0f;

    public FogEffect(Supplier<Rectangle> visibleWorldRect) {
        this.visibleWorldRect = visibleWorldRect;
    }

    @Override
    public void update(float dt) {
        time += dt;
    }

    @Override
    public void render(Batch batch) {
        Rectangle rect = visibleWorldRect.get();
        ShaderProgram shader = Shaders.load("fog.frag");
        ShaderProgram previous = batch.getShader();
        batch.setShader(shader);
        shader.setUniformf("uSize", rect.width, rect.height);
        shader.setUniformf("uGroundPos", 0f);
        shader.setUniformf("uGroundAdd", 0f);
        shader.setUniformf("uFade", 1f);
        shader.setUniformf("uTime", time);
        batch.draw(Shaders.whiteQuad(), rect.x, rect.y, rect.width, rect.height);
        batch.setShader(previous);
    }
}
