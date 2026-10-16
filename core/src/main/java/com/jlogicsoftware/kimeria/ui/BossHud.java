package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.localization.Msg;

/** Replaceable PNG art; all sprite sizes and layout measurements live together below. */
public final class BossHud {
    private static final String ART = "images/ui/boss/";
    private static final int FRAME_WIDTH = 224, FRAME_HEIGHT = 26;
    private static final int SEGMENT_WIDTH = 14, SEGMENT_HEIGHT = 16, SEGMENT_GAP = 3;
    private static final int INSET_X = 12, INSET_Y = 5;
    private static final float TOP = 50, LABEL_TOP = 32, ELIXIR_SIZE = 16;
    private final TextureRegion frame, filled, empty, elixir;
    private final BitmapFont font;
    private final GameContext game;
    private final Matrix4 textProjection;
    private final float logicalWidth, logicalHeight;

    public BossHud(Assets assets, GameContext game, Matrix4 textProjection, float width, float height) {
        this.game = game;
        this.textProjection = textProjection;
        logicalWidth = width;
        logicalHeight = height;
        frame = assets.region(ART + "frame.png", 0, 0, FRAME_WIDTH, FRAME_HEIGHT);
        filled = assets.region(ART + "segment-filled.png", 0, 0, SEGMENT_WIDTH, SEGMENT_HEIGHT);
        empty = assets.region(ART + "segment-empty.png", 0, 0, SEGMENT_WIDTH, SEGMENT_HEIGHT);
        elixir = assets.region("images/Items.png", 80, 0, 16, 16);
        font = assets.font(13);
    }

    private static void glow(Batch batch, TextureRegion art, float x, float y, float width, float height) {
        int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
        int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE, srcAlpha, dstAlpha);
        batch.draw(art, x, y, width, height);
        batch.setBlendFunctionSeparate(src, dst, srcAlpha, dstAlpha);
    }

    public void render(Batch batch, Boss boss, boolean empowered) {
        if (boss == null) return;
        Color before = new Color(batch.getColor());
        float x = (logicalWidth - FRAME_WIDTH) / 2f;
        var feedback = boss.feedback();
        float energy = feedback.displayedEnergy(boss.energy());
        batch.setColor(Color.WHITE);
        batch.draw(frame, x, TOP, FRAME_WIDTH, FRAME_HEIGHT);
        for (int i = 0; i < Boss.MAX_ENERGY; i++) {
            float sx = x + INSET_X + i * (SEGMENT_WIDTH + SEGMENT_GAP);
            float sy = TOP + INSET_Y;
            batch.draw(empty, sx, sy, SEGMENT_WIDTH, SEGMENT_HEIGHT);
            // Alpha filling keeps replacement sprites intact rather than slicing their bevels.
            float fill = Math.max(0, Math.min(1, energy - i));
            if (fill > 0) {
                batch.setColor(1, 1, 1, fill);
                batch.draw(filled, sx, sy, SEGMENT_WIDTH, SEGMENT_HEIGHT);
            }
            float lost = feedback.lostSegmentAlpha(i);
            if (lost > 0) {
                batch.setColor(1f, .8f, .35f, lost);
                glow(batch, filled, sx, sy, SEGMENT_WIDTH, SEGMENT_HEIGHT);
            }
            batch.setColor(Color.WHITE);
        }
        if (feedback.restoreAlpha() > 0) {
            batch.setColor(1, 1, .65f, feedback.restoreAlpha() * .65f);
            glow(batch, frame, x, TOP, FRAME_WIDTH, FRAME_HEIGHT);
            batch.setColor(Color.WHITE);
        }
        if (empowered) batch.draw(elixir, x + FRAME_WIDTH - 48, LABEL_TOP - 2, ELIXIR_SIZE, ELIXIR_SIZE);

        Matrix4 previous = batch.getProjectionMatrix().cpy();
        Color previousFont = new Color(font.getColor());
        batch.setProjectionMatrix(textProjection);
        font.setColor(Color.WHITE);
        font.draw(batch, Msg.BOSS.t(game.language()), x, logicalHeight - LABEL_TOP);
        if (empowered) font.draw(batch, "x10", x + FRAME_WIDTH - 28, logicalHeight - LABEL_TOP);
        font.setColor(previousFont);
        batch.setProjectionMatrix(previous);
        batch.setColor(before);
    }
}
