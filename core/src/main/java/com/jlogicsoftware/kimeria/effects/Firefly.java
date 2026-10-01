package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.WorldObject;

/**
 * Port of Dart's {@code Firefly}: a black dot that flies a short bezier curve, then hides and repicks a spot.
 * {@link #glowing} builds the menu variant instead: a larger, brighter light that is hidden only briefly
 * and is meant to be drawn with additive blending (see {@link #render}).
 */
public class Firefly implements WorldObject {
    private final Vector2 area;
    private float timer = 0f, hideTime = 0f, flyTime = 0f;
    private boolean flying = false;
    private final Vector2 start = new Vector2();
    private final Vector2 end = new Vector2();
    private final Vector2 control = new Vector2();
    private float radius = 2f;
    private final Color color;
    private final boolean glow;

    public Firefly(Vector2 area) {
        this(area, Color.BLACK, false);
    }

    /** The menu backdrop's "MenuFirefly": a bright {@code color} light rather than the gameplay black dot. */
    public static Firefly glowing(Vector2 area, Color color) {
        return new Firefly(area, color, true);
    }

    private Firefly(Vector2 area, Color color, boolean glow) {
        this.area = area;
        this.color = color;
        this.glow = glow;
        startHide();
    }

    @Override
    public void update(float dt) {
        timer += dt;
        if (flying && timer > flyTime) {
            startHide();
        } else if (!flying && timer > hideTime) {
            startFly();
        }
    }

    private void startFly() {
        timer = 0;
        flying = true;
        flyTime = 3f + MathUtils.random(4f);
        start.set(MathUtils.random(area.x), MathUtils.random(area.y));
        control.set(start.x + MathUtils.random(-30f, 30f), start.y + MathUtils.random(-30f, 30f));
        end.set(start.x + MathUtils.random(-20f, 20f), start.y + MathUtils.random(-20f, 20f));
        radius = glow ? 4f + MathUtils.random(2f) : 2f + MathUtils.random();
    }

    private void startHide() {
        timer = 0;
        flying = false;
        hideTime = glow ? 0.2f + MathUtils.random(0.4f) : 1f + MathUtils.random();
    }

    /** The glow variant is tuned for additive blending ({@code GL_SRC_ALPHA, GL_ONE}); the caller sets that up. */
    @Override
    public void render(Batch batch) {
        if (!flying) return;
        float t = MathUtils.clamp(timer / flyTime, 0f, 1f);
        float mt = 1f - t;
        float x = mt * mt * start.x + 2 * mt * t * control.x + t * t * end.x;
        float y = mt * mt * start.y + 2 * mt * t * control.y + t * t * end.y;
        // Gameplay fireflies fade as a triangle; the menu ones hold a steady glow between short fades.
        float alpha = glow ? 0.65f * Math.min(1f, Math.min(t, 1f - t) * 4f) : (t < 0.5f ? t * 2f : (1f - t) * 2f);

        TextureRegion dot = glow ? SoftDot.glowRegion() : SoftDot.region();
        batch.setColor(color.r, color.g, color.b, alpha);
        batch.draw(dot, x - radius, y - radius, radius * 2, radius * 2);
        batch.setColor(Color.WHITE);
    }
}
