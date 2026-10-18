package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.jlogicsoftware.kimeria.entity.WorldObject;

/** A finite, cosmetic spray: crimson droplets, fast streaks and short-lived floor splashes. */
public final class BloodBurst implements WorldObject {
    public static final int DROPLETS = 32;
    public static final float DURATION = .9f;
    private static final float GRAVITY = 420f;

    private static final class Drop {
        float x, y, vx, vy, size, trailLength, life, red, green;
        boolean landed;
    }

    private final Drop[] drops;
    public int dropletCount() { return drops.length; }
    private final float floorY;
    private final boolean empowered;
    private float age;

    public BloodBurst(float x, float y, float direction, float floorY) {
        this(x, y, direction, floorY, false);
    }

    public BloodBurst(float x, float y, float direction, float floorY, boolean empowered) {
        drops = new Drop[empowered ? 48 : DROPLETS];
        this.floorY = floorY;
        this.empowered = empowered;
        for (int i = 0; i < drops.length; i++) {
            Drop p = new Drop();
            p.x = x + MathUtils.random(-2f, 2f);
            p.y = y + MathUtils.random(-3f, 3f);
            // A strong fan away from the sword, with a few drops spraying back toward it.
            p.vx = direction * MathUtils.random(65f, 190f) * (i % 5 == 0 ? -.4f : 1f);
            p.vy = MathUtils.random(-175f, -35f);
            p.size = i % DROPLETS < 8 ? MathUtils.random(1f, 1.5f) : MathUtils.random(.4f, .9f);
            p.size *= empowered ? 1.8f : 1f;
            p.trailLength = Math.min(4.5f, 1f + Math.abs(p.vx) * .0175f) * (empowered ? 4f : 1f);
            p.life = MathUtils.random(.6f, DURATION);
            p.red = MathUtils.random(.5f, .68f) * (empowered ? 1.2f : 1f);
            p.green = MathUtils.random(.1f, .15f);
            drops[i] = p;
        }
    }

    @Override public void update(float dt) {
        if (isRemoved()) return;
        age += dt;
        for (Drop p : drops) {
            if (age >= p.life || p.landed) continue;
            p.x += p.vx * dt;
            p.y += p.vy * dt + .5f * GRAVITY * dt * dt;
            p.vy += GRAVITY * dt;
            if (p.y >= floorY) {
                p.y = floorY;
                p.landed = true;
            }
        }
    }

    @Override public boolean isRemoved() { return age >= DURATION; }

    @Override public void render(Batch batch) {
        if (isRemoved()) return;
        Color before = new Color(batch.getColor());
        var dot = Shaders.whiteQuad();
        for (Drop p : drops) {
            if (age >= p.life) continue;
            float alpha = Math.min(1, (p.life - age) / .2f) * before.a;
            batch.setColor(p.red, p.green, .12f, alpha);
            if (p.landed) {
                float spread = p.size * (empowered ? 6f : 3f);
                batch.draw(dot, p.x - spread / 2, p.y - (empowered ? 1f : .5f),
                    spread, empowered ? 1.5f : .75f);
            } else {
                float angle = MathUtils.atan2(p.vy, p.vx) * MathUtils.radiansToDegrees + 180;
                batch.draw(dot, p.x, p.y - p.size / 2, 0, p.size / 2,
                    p.trailLength, p.size, 1, 1, angle);
                batch.setColor(.7f, .19f, .18f, alpha);
                batch.draw(dot, p.x - p.size / 2, p.y - p.size / 2, p.size, p.size);
            }
        }
        batch.setColor(before);
    }
}
