package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.entity.items.Actionable;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of Dart's {@code Torch}. The original spawns dozens of independent
 * {@code Future.delayed} particle schedulers driving Skia canvas draws with
 * blur mask filters; here that's replaced with a small pooled-particle
 * system (a standard technique on a synchronous update loop) that keeps the
 * same visual language -- a flickering core flame, rising embers, drifting
 * smoke, and green magic sparkles -- and the same {@code toggleFire}/
 * {@code performAction} behaviour used by triggers and falling platforms.
 */
public class Torch extends Entity implements Actionable {
    private enum Kind {CORE, EMBER, SMOKE, SPARKLE}

    private static final class Particle {
        Kind kind;
        Vector2 pos = new Vector2();
        Vector2 from = new Vector2();
        Vector2 to = new Vector2();
        float life, maxLife, size;
        Color color = new Color();
    }

    private final List<Particle> particles = new ArrayList<>();
    private int intensity;
    private final String targetId;
    private final TextureRegion sprite;
    private boolean burning;

    private float coreTimer, emberTimer, sparkleTimer, smokeTimer, flickerTimer;
    private float lightRadius, lightAlpha;

    public Torch(float centerX, float centerY, float w, float h, int intensity, String targetId) {
        this(null, centerX, centerY, w, h, intensity, targetId);
    }

    public Torch(Assets assets, float centerX, float centerY, float w, float h, int intensity, String targetId) {
        super(centerX - w / 2f, centerY - h / 2f, w, h);
        sprite = assets == null ? null : assets.region("images/objects/torch.png", 0, 0, 8, 16);
        this.intensity = intensity;
        this.targetId = targetId == null ? "" : targetId;
        burning = intensity > 0;
        computeLightBase();
    }

    private void computeLightBase() {
        lightRadius = MathUtils.clamp(4f + intensity * 0.06f, 3f, 40f);
        lightAlpha = MathUtils.clamp(18f + intensity * 0.22f, 8f, 220f) / 255f;
    }

    public void toggleFire(boolean on) {
        intensity = on ? 200 : 0;
        burning = on;
        computeLightBase();
        if (!on) {
            particles.clear();
        }
    }

    public boolean isBurning() { return burning; }

    @Override
    public String getTargetId() {
        return targetId;
    }

    @Override
    public void performAction() {
        toggleFire(intensity == 0);
    }

    @Override
    public void update(float dt) {
        if (!burning) return;

        flickerTimer -= dt;
        if (flickerTimer <= 0) {
            flickerTimer = 0.12f + MathUtils.random(0.22f);
        }

        coreTimer -= dt;
        if (coreTimer <= 0) {
            spawnCore();
            coreTimer = 0.04f + MathUtils.random(0.12f);
        }
        emberTimer -= dt;
        if (emberTimer <= 0) {
            spawnEmbers();
            emberTimer = 0.15f + MathUtils.random(0.2f);
        }
        sparkleTimer -= dt;
        if (sparkleTimer <= 0) {
            spawnSparkles();
            sparkleTimer = 0.08f + MathUtils.random(0.18f);
        }
        smokeTimer -= dt;
        if (smokeTimer <= 0) {
            spawnSmoke();
            smokeTimer = 0.3f + MathUtils.random(0.5f);
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                particles.remove(i);
                continue;
            }
            float t = 1f - p.life / p.maxLife;
            p.pos.set(p.from).lerp(p.to, t);
        }
    }

    private void spawnCore() {
        Particle p = new Particle();
        p.kind = Kind.CORE;
        p.maxLife = p.life = 0.14f + MathUtils.random(0.18f);
        p.from.set(centerX() + rnd(4), centerY() + rnd(3));
        p.to.set(p.from.x, p.from.y - 12f);
        p.size = 8f;
        p.color.set(1f, 0.85f, 0.2f, 1f);
        particles.add(p);
    }

    private void spawnEmbers() {
        int burst = 1 + MathUtils.random(2);
        for (int i = 0; i < burst; i++) {
            Particle p = new Particle();
            p.kind = Kind.EMBER;
            p.maxLife = p.life = 0.35f + MathUtils.random(0.45f);
            p.from.set(centerX() + rnd(8), centerY() + rnd(6));
            p.to.set(p.from.x + rnd(28), p.from.y - 40f - MathUtils.random(30f));
            p.size = 2.5f;
            p.color.set(0.6f, 1f, 0.4f, 1f);
            particles.add(p);
        }
    }

    private void spawnSparkles() {
        // Was up to 40 particles per burst (every ~0.1-0.3s): stacking that
        // many additive-blended dots this close together saturates straight
        // to white/cyan instead of reading as distinct green sparks.
        int burst = MathUtils.clamp(Math.round(intensity * 0.02f) + MathUtils.random(2), 2, 6);
        for (int i = 0; i < burst; i++) {
            Particle p = new Particle();
            p.kind = Kind.SPARKLE;
            p.maxLife = p.life = 0.4f + MathUtils.random(0.8f);
            p.from.set(centerX() + rnd(10), centerY() + rnd(8));
            p.to.set(p.from.x + rnd(18), p.from.y - 30f - MathUtils.random(60f));
            p.size = 1.6f;
            p.color.set(0.35f, 0.85f, 0.3f, 1f);
            particles.add(p);
        }
    }

    private void spawnSmoke() {
        Particle p = new Particle();
        p.kind = Kind.SMOKE;
        p.maxLife = p.life = 2f + MathUtils.random(2f);
        p.from.set(centerX() + rnd(6), centerY() - 2f);
        p.to.set(p.from.x + rnd(8), p.from.y - 50f - MathUtils.random(40f));
        p.size = 20f;
        p.color.set(0.4f, 0.4f, 0.4f, 1f);
        particles.add(p);
    }

    private static float rnd(float range) {
        return (MathUtils.random() - 0.5f) * range * 2f;
    }

    @Override
    public void render(Batch batch) {
        if (sprite != null) batch.draw(sprite, centerX() - 4, centerY() - 3, 8, 16);
        TextureRegion dot = SoftDot.region();
        if (burning) {
            float flicker = 0.85f + MathUtils.random(0.3f);
            float radius = lightRadius * flicker * 1.4f;
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            batch.setColor(1f, 0.9f, 0.4f, lightAlpha * 0.6f);
            batch.draw(dot, centerX() - radius / 2f, centerY() - radius / 2f, radius, radius);
        }

        for (Particle p : particles) {
            float t = 1f - p.life / p.maxLife;
            // Additive particles fade in the second half of their life too
            // (not just linearly from full alpha), so a fresh burst doesn't
            // all sit near maximum brightness at once and wash out to white.
            float alpha = (p.kind == Kind.SMOKE ? (1f - t) : (1f - t) * (1f - t)) * 0.7f;
            float size = p.size * (p.kind == Kind.SMOKE ? (1f + t) : (1f - 0.4f * t));
            boolean additive = p.kind != Kind.SMOKE;
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, additive ? GL20.GL_ONE : GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setColor(p.color.r, p.color.g, p.color.b, alpha);
            batch.draw(dot, p.pos.x - size / 2f, p.pos.y - size / 2f, size, size);
        }
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

}
