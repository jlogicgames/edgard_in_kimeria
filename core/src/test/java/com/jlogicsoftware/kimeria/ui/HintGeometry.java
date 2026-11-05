package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;

/**
 * Test-side hint placement with the real shipped 13 px font metrics (no GPU), using the same
 * {@link HintLayout} arithmetic as the HUD. World y is Y-down; the 640x360 view shows world y
 * {@code cameraY .. cameraY+360}, so screen y (Y-up) is {@code 360 - (worldY - cameraY)}.
 * Needs {@code Gdx.files} to be a {@link HeadlessFiles}.
 */
public final class HintGeometry {
    public static final float SCREEN_H = 360;
    private static final float WIDTH = 200;

    private static class MetricsFont extends BitmapFont {
        MetricsFont() {
            super(new BitmapFontData(new HeadlessFiles().internal("fonts/generated/QuestSquare-13.fnt"), false), new TextureRegion(), true);
        }
        @Override protected void load(BitmapFontData data) { /* Metrics require no texture UVs. */ }
    }

    private final BitmapFont font = new MetricsFont();

    public float height(String text) { return new GlyphLayout(font, text, Color.WHITE, WIDTH, Align.center, true).height; }

    public float screenY(Vector2 worldAnchor, float cameraY) { return SCREEN_H - (worldAnchor.y - cameraY); }

    /** Top edge of the zone message at its anchor. */
    public float zoneTop(Vector2 zoneAnchor, String zone, float cameraY) {
        return HintLayout.top(screenY(zoneAnchor, cameraY), height(zone), font.getCapHeight(), SCREEN_H);
    }

    /** Top edge of the contextual label as the HUD places it, with a zone message present or not. */
    public float contextTop(Vector2 contextAnchor, String context, boolean zonePresent, Vector2 zoneAnchor, String zone, float cameraY) {
        return HintLayout.contextTop(screenY(contextAnchor, cameraY), height(context), zonePresent,
            zonePresent ? screenY(zoneAnchor, cameraY) : 0, zonePresent ? height(zone) : 0, font.getCapHeight(), SCREEN_H);
    }

    /** Clear space between the zone message's bottom and the contextual label's top (negative: they overlap). */
    public float gap(Vector2 contextAnchor, String context, Vector2 zoneAnchor, String zone, float cameraY) {
        float zoneBottom = zoneTop(zoneAnchor, zone, cameraY) - height(zone);
        return zoneBottom - contextTop(contextAnchor, context, true, zoneAnchor, zone, cameraY);
    }
}
