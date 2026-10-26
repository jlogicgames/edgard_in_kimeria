package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.entity.Entity;

/** Selection UI only. The selected object's artwork is always a separate sprite. */
public final class InteractionHighlight {
    private static final float TARGET_PADDING = 3, OUTLINE_OFFSET = 1;
    private static final float LINE_THICKNESS = 1, ARM_LENGTH = 5;
    private static final Color OUTLINE_COLOR = new Color(0, 0, 0, .9f);
    private static final Color TARGET_COLOR = new Color(1, .82f, .3f, 1);
    private static final Color PASSIVE_TARGET_COLOR = new Color(.72f, .75f, .8f, .72f);

    private InteractionHighlight() { }

    public static void draw(Batch batch, Entity target) {
        float x = target.getX() - TARGET_PADDING, y = target.getY() - TARGET_PADDING;
        float w = target.getWidth() + TARGET_PADDING * 2, h = target.getHeight() + TARGET_PADDING * 2;
        batch.setColor(OUTLINE_COLOR);
        corners(batch, x - OUTLINE_OFFSET, y - OUTLINE_OFFSET,
            w + OUTLINE_OFFSET * 2, h + OUTLINE_OFFSET * 2,
            LINE_THICKNESS + OUTLINE_OFFSET * 2, ARM_LENGTH + OUTLINE_OFFSET * 2);
        batch.setColor(TARGET_COLOR);
        corners(batch, x, y, w, h, LINE_THICKNESS, ARM_LENGTH);
        batch.setColor(Color.WHITE);
    }

    public static void drawPassive(Batch batch, Entity target) {
        float x = target.getX() - TARGET_PADDING, y = target.getY() - TARGET_PADDING;
        float w = target.getWidth() + TARGET_PADDING * 2, h = target.getHeight() + TARGET_PADDING * 2;
        batch.setColor(PASSIVE_TARGET_COLOR);
        corners(batch, x, y, w, h, LINE_THICKNESS, ARM_LENGTH);
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
