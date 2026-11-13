package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.objects.FalseHoneyFungus;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Not a check: writes a still contact sheet of the spore cloud to the file named by the environment variable
 * {@code SPORE_PREVIEW_OUT} (docs/references/forest-traps/false-honey-fungus-cloud-preview.png), deterministic, with no GL context.
 * Run: {@code SPORE_PREVIEW_OUT=docs/references/forest-traps/false-honey-fungus-cloud-preview.png ./gradlew :core:test --tests '*SporeCloudPreview'}.
 * <p>
 * The real fungus is stepped for the particles (their true positions, sizes, alphas, colours and draw order); the dot is the
 * {@link SoftDot} falloff and the haze is a CPU port of {@code spore_cloud.frag} (same constants, same maths). It is an approximation of
 * the GPU image (no bilinear filtering, no 8-bit blending), good for judging shape, palette and the lethal-rectangle containment; the
 * running game is the real look. Each panel is the rectangle plus an 8 px margin, 10x; the red frame is the lethal rectangle.
 */
@EnabledIfEnvironmentVariable(named = "SPORE_PREVIEW_OUT", matches = ".+")
class SporeCloudPreview {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int SCALE = 10, MARGIN = 8, GAP = 6;
    private static final float[] TIMES = {0.05f, 0.15f, 0.3f, 0.5f, 0.7f, 0.9f, 0.98f};
    private static final float FLOOR = 320, X = 480, W = 24, H = 16, ZONE = 64;
    private static final float[] SKY = {0.11f, 0.17f, 0.12f}, GROUND = {0.22f, 0.16f, 0.1f}, LETHAL_FRAME = {0.9f, 0.2f, 0.2f}, MUSHROOM = {0.55f, 0.45f, 0.3f};
    private static final float[] DARK = {0.373f, 0.627f, 0.165f}, MID = {0.651f, 0.878f, 0.290f}, LIGHT = {0.902f, 0.949f, 0.416f};

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { Shaders.dispose(); SoftDot.dispose(); harness.restoreGlobals(); }

    @Test void writesTheContactSheet() throws IOException {
        Rectangle hero = new Rectangle(X, FLOOR - 22, 11, 22);
        FalseHoneyFungus f = new FalseHoneyFungus(harness.assets(), harness.game().trapWarnings(), X, FLOOR - H, W, H, ZONE, ZONE, () -> hero);
        Rectangle lethal = f.cloudBounds();
        int panel = (int) (lethal.width + 2 * MARGIN) * SCALE;
        BufferedImage sheet = new BufferedImage(TIMES.length * (panel + GAP) - GAP, panel, BufferedImage.TYPE_INT_RGB);
        int next = 0;
        int frames = 0;
        while (next < TIMES.length && frames++ < 1000) {
            f.update(STEP);
            if (!f.cloudLive()) continue;
            if (f.stateTime() >= TIMES[next]) paint(sheet, next++ * (panel + GAP), f, lethal);
        }
        File out = new File(System.getenv("SPORE_PREVIEW_OUT"));
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
    }

    private void paint(BufferedImage image, int ox, FalseHoneyFungus f, Rectangle lethal) {
        SporeCloud cloud = f.sporeCloud();
        KimeriaParticleSystem p = cloud.particles();
        float progress = f.stateTime() / FalseHoneyFungus.CLOUD_SECONDS;
        float strength = SporeCloud.strength(progress), swell = SporeCloud.swell(progress);
        int size = (int) (lethal.width + 2 * MARGIN) * SCALE;
        float[][] px = new float[size * size][];
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float wx = lethal.x - MARGIN + (i + 0.5f) / SCALE, wy = lethal.y - MARGIN + (j + 0.5f) / SCALE;
                float[] c = wy >= FLOOR ? GROUND.clone() : SKY.clone();
                if (wy >= FLOOR - 16 + 4 && wy < FLOOR && Math.abs(wx - (lethal.x + lethal.width / 2)) < 9) c = MUSHROOM.clone();
                px[j * size + i] = c;
                if (SporeCloud.SHADER_LAYER && wx >= lethal.x && wx < lethal.x + lethal.width && wy >= lethal.y && wy < lethal.y + lethal.height)
                    haze(c, (wx - lethal.x) / lethal.width, (wy - lethal.y) / lethal.height, f.stateTime(), swell, strength,
                        (f.getY() + 4 - lethal.y) / lethal.height);
            }
        }
        for (int pass = 0; pass < 2; pass++) { // normal blending first, then additive, like KimeriaParticleSystem.render
            for (int n = 0; n < p.count(); n++) {
                boolean additive = p.kind(n) == KimeriaParticleSystem.KIND_GLOW;
                if (additive != (pass == 1)) continue;
                float radius = p.drawSize(n) / 2f, alpha = p.alpha(n);
                for (int j = 0; j < size; j++) for (int i = 0; i < size; i++) {
                    float wx = lethal.x - MARGIN + (i + 0.5f) / SCALE, wy = lethal.y - MARGIN + (j + 0.5f) / SCALE;
                    float d = (float) Math.hypot(wx - p.x(n), wy - p.y(n)) / radius;
                    if (d >= 1f) continue;
                    float a = Math.min(1f, (1f - d) * (1f - d)) * alpha;
                    float[] c = px[j * size + i];
                    float[] src = {p.red(n), p.green(n), p.blue(n)};
                    for (int k = 0; k < 3; k++) c[k] = additive ? Math.min(1f, c[k] + src[k] * a) : c[k] * (1 - a) + src[k] * a;
                }
            }
        }
        for (int j = 0; j < size; j++) for (int i = 0; i < size; i++) {
            float wx = lethal.x - MARGIN + (i + 0.5f) / SCALE, wy = lethal.y - MARGIN + (j + 0.5f) / SCALE;
            float[] c = px[j * size + i];
            boolean frame = Math.abs(wx - lethal.x) < 0.5f / SCALE * 2 && wy >= lethal.y && wy <= lethal.y + lethal.height
                || Math.abs(wx - (lethal.x + lethal.width)) < 0.5f / SCALE * 2 && wy >= lethal.y && wy <= lethal.y + lethal.height
                || Math.abs(wy - lethal.y) < 0.5f / SCALE * 2 && wx >= lethal.x && wx <= lethal.x + lethal.width;
            if (frame) c = LETHAL_FRAME;
            image.setRGB(ox + i, j, rgb(c));
        }
    }

    /** CPU port of spore_cloud.frag: blends the haze over {@code c}. */
    private static void haze(float[] c, float u, float v, float time, float swell, float strength, float originV) {
        float px = u - 0.5f, py = (v - originV) * 0.8f;
        float r = (float) Math.hypot(px, py);
        float angle = time * 2.4f + r * 9f;
        float cs = (float) Math.cos(angle), sn = (float) Math.sin(angle);
        float qx = cs * px - sn * py, qy = sn * px + cs * py;
        float wave = 0.5f + 0.5f * (float) Math.sin(qx * 16f + 2f * Math.sin(qy * 13f + time * 4f));
        float density = SporeCloud.smooth(0.15f, 0.85f, wave);
        float reach = 0.16f + (0.72f - 0.16f) * swell;
        float body = 1f - SporeCloud.smooth(0.3f * reach, reach, r + (density - 0.5f) * 0.14f);
        float edge = SporeCloud.smooth(0f, 0.12f, u) * SporeCloud.smooth(0f, 0.12f, 1f - u)
            * SporeCloud.smooth(0f, 0.12f, v) * SporeCloud.smooth(0f, 0.04f, 1f - v);
        float core = 1f - SporeCloud.smooth(0f, 0.5f * reach, r);
        float alpha = body * edge * strength * (0.2f + 0.8f * density);
        for (int k = 0; k < 3; k++) {
            float col = DARK[k] + (MID[k] - DARK[k]) * density;
            col = col + (LIGHT[k] - col) * (core * core * 0.7f + density * density * 0.3f);
            c[k] = c[k] * (1 - alpha) + col * alpha;
        }
    }

    private static int rgb(float[] c) {
        return (Math.round(Math.min(1f, c[0]) * 255) << 16) | (Math.round(Math.min(1f, c[1]) * 255) << 8) | Math.round(Math.min(1f, c[2]) * 255);
    }
}
