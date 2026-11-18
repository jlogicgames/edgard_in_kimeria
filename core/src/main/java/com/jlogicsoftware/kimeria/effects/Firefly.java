package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.WorldObject;

/**
 * A firefly: a small light that flies a short bezier curve, then hides and repicks a spot. Port of the idea of Dart's
 * {@code Firefly}, drawn as a glow only: {@link #glowing} is the menu backdrop's light, {@link #smallGlow} the Forest's
 * gameplay light (#472). Both are drawn additively (see {@link #render}).
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
    private final float radiusMin, radiusSpread;
    /** Radius ranges (px): the menu glow (unchanged) and the Forest's small gameplay light (#472). */
    private static final float MENU_GLOW_RADIUS_MIN = 4f, MENU_GLOW_RADIUS_SPREAD = 2f;
    private static final float SMALL_GLOW_RADIUS_MIN = 1.75f, SMALL_GLOW_RADIUS_SPREAD = 0.5f;
    /** Peak alpha of the glow, and the share of a flight spent fading in and out at each end. */
    private static final float PEAK_ALPHA = 0.65f, FADE_FRACTION = 0.25f;
    /** Seconds hidden between flights (the glow's own range). */
    private static final float HIDE_MIN = 0.2f, HIDE_SPREAD = 0.4f;

    /** The menu backdrop's "MenuFirefly": a bright {@code color} light, 4 to 6 px in radius. */
    public static Firefly glowing(Vector2 area, Color color) {
        return new Firefly(area, color, MENU_GLOW_RADIUS_MIN, MENU_GLOW_RADIUS_SPREAD);
    }

    /** The Forest's gameplay light (#472): the glow of {@link #glowing} at about 4 px across, so it stays ambient. */
    public static Firefly smallGlow(Vector2 area, Color color) {
        return new Firefly(area, color, SMALL_GLOW_RADIUS_MIN, SMALL_GLOW_RADIUS_SPREAD);
    }

    private Firefly(Vector2 area, Color color, float radiusMin, float radiusSpread) {
        this.area = area;
        this.color = color;
        this.radiusMin = radiusMin;
        this.radiusSpread = radiusSpread;
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
        radius = radiusMin + MathUtils.random(radiusSpread);
    }

    private void startHide() {
        timer = 0;
        flying = false;
        hideTime = HIDE_MIN + MathUtils.random(HIDE_SPREAD);
    }

    /** Tuned for additive blending ({@code GL_SRC_ALPHA, GL_ONE}); the caller sets that up. */
    @Override
    public void render(Batch batch) {
        if (!flying) return;
        float t = MathUtils.clamp(timer / flyTime, 0f, 1f);
        float mt = 1f - t;
        float x = mt * mt * start.x + 2 * mt * t * control.x + t * t * end.x;
        float y = mt * mt * start.y + 2 * mt * t * control.y + t * t * end.y;
        // A steady glow between short fades in and out.
        float alpha = PEAK_ALPHA * Math.min(1f, Math.min(t, 1f - t) / FADE_FRACTION);

        batch.setColor(color.r, color.g, color.b, alpha);
        batch.draw(SoftDot.glowRegion(), x - radius, y - radius, radius * 2, radius * 2);
        batch.setColor(Color.WHITE);
    }
}
