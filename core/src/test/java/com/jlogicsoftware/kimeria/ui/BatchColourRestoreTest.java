package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@code Boss.render} and {@code BossHud.render} borrow the batch and font colour and must hand them back exactly (#285 step 4). */
class BatchColourRestoreTest {
    private static final float R = .3f, G = .6f, B = .9f, A = .5f;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private static void assertColour(Color c, String what) {
        assertEquals(R, c.r, 0f, what); assertEquals(G, c.g, 0f, what);
        assertEquals(B, c.b, 0f, what); assertEquals(A, c.a, 0f, what);
    }

    private Boss bossRoom() {
        Level level = harness.roomOf("forest-2", 2);
        return level.boss();
    }

    @Test void bossRenderRestoresTheBatchColour() {
        Boss boss = bossRoom();
        batch.setColor(R, G, B, A);
        boss.render(batch);
        assertColour(batch.getColor(), "plain draw");
        boss.collidedWithActor(true); // starts the orange counter sequence, which tints the batch while drawing
        assertTrue(boss.isCounterSequence(), "the tinted branch must be the one under test");
        boss.render(batch);
        assertColour(batch.getColor(), "counter-sequence draw");
    }

    @Test void bossHudRestoresTheBatchAndFontColour() {
        BitmapFont font = new BitmapFont();
        harness.font = font;
        BossHud hud = new BossHud(harness.assets(), harness.game(), new Matrix4(), 640, 360);
        Boss boss = bossRoom();
        batch.setColor(R, G, B, A);
        font.setColor(B, A, R, G);
        for (boolean empowered : new boolean[]{false, true}) {
            hud.render(batch, boss, empowered);
            assertColour(batch.getColor(), "batch colour, empowered=" + empowered);
            Color f = font.getColor();
            assertEquals(B, f.r, 0f); assertEquals(A, f.g, 0f); assertEquals(R, f.b, 0f); assertEquals(G, f.a, 0f);
        }
        font.dispose();
    }
}
