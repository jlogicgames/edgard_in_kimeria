package com.jlogicsoftware.kimeria.testsupport;

import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** A shipped font with its real glyph metrics and no texture, so text lays out (and draws through a no-op batch) without a GPU. */
public final class MetricsFont extends BitmapFont {
    public MetricsFont(int size) {
        super(new BitmapFontData(new HeadlessFiles().internal("fonts/generated/QuestSquare-" + size + ".fnt"), false), new TextureRegion(), true);
    }
    @Override protected void load(BitmapFontData data) { /* Metrics require no texture UVs. */ }
}
