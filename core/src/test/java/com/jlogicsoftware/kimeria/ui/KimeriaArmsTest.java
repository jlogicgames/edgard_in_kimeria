package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The shared arms (#416, D95; reused by the main menu, #415): drawing leaves the caller's batch colour as it was. */
class KimeriaArmsTest {
    @Test void drawingRestoresTheCallersTint() {
        KimeriaArms arms = new KimeriaArms(new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        });
        NoopBatch batch = new NoopBatch();
        Color tint = new Color(0.2f, 0.4f, 0.6f, 0.8f);
        batch.setColor(tint);
        float before = batch.getPackedColor();
        for (int frame = 0; frame < KimeriaArms.FRAME_COUNT; frame++) arms.draw(batch, frame, 320f, 40f, 3);
        assertEquals(before, batch.getPackedColor(), "the tint survives the arms");
    }
}
