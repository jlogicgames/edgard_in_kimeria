package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.entity.Entity;

/** Selection UI only. The selected object's artwork is always a separate sprite. */
public final class InteractionHighlight {
    private InteractionHighlight() { }

    public static void draw(Batch batch, Entity target) {
        float x = target.getX() - 3, y = target.getY() - 3;
        float w = target.getWidth() + 6, h = target.getHeight() + 6;
        batch.setColor(0, 0, 0, .9f);
        corners(batch, x - 1, y - 1, w + 2, h + 2, 3, 7);
        batch.setColor(1, .82f, .3f, 1);
        corners(batch, x, y, w, h, 1, 5);
        batch.setColor(Color.WHITE);
    }

    private static void corners(Batch batch, float x, float y, float w, float h, float thickness, float arm) {
        var pixel = Shaders.whiteQuad();
        for (float cx : new float[]{x, x + w - arm}) {
            batch.draw(pixel, cx, y, arm, thickness);
            batch.draw(pixel, cx, y + h - thickness, arm, thickness);
        }
        for (float cy : new float[]{y, y + h - arm}) {
            batch.draw(pixel, x, cy, thickness, arm);
            batch.draw(pixel, x + w - thickness, cy, thickness, arm);
        }
    }
}
