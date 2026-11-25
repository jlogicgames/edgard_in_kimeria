package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;

/**
 * Test-side hint placement with the real shipped font metrics (zone message {@link FontSizes#ZONE} px, contextual label {@link FontSizes#LABEL} px, and its 18 px large tier, D137, #249 and #254) (no GPU), using the same
 * {@link HintLayout} arithmetic as the HUD. World y is Y-down; the 640x360 view shows world y
 * {@code cameraY .. cameraY+360}, so screen y (Y-up) is {@code 360 - (worldY - cameraY)}.
 * Needs {@code Gdx.files} to be a {@link HeadlessFiles}.
 */
public final class HintGeometry {
    public static final float SCREEN_H = 360;

    private static class MetricsFont extends BitmapFont {
        MetricsFont(int size) {
            super(new BitmapFontData(new HeadlessFiles().internal("fonts/generated/QuestSquare-" + size + ".fnt"), false), new TextureRegion(), true);
        }
        @Override protected void load(BitmapFontData data) { /* Metrics require no texture UVs. */ }
    }

    private final BitmapFont zoneFont = new MetricsFont(FontSizes.ZONE);
    /** The label above the hero in both text tiers (D137): the nominal 16 px and the 18 px of a small picture. */
    private final BitmapFont[] contextFonts = {new MetricsFont(FontSizes.label(false)), new MetricsFont(FontSizes.label(true))};

    public float zoneHeight(String text) { return new GlyphLayout(zoneFont, text, Color.WHITE, Hud.ZONE_WIDTH, Align.center, true).height; }

    private static float contextHeight(BitmapFont font, String text) { return new GlyphLayout(font, text, Color.WHITE, Hud.CONTEXT_WIDTH, Align.center, true).height; }

    public float screenY(Vector2 worldAnchor, float cameraY) { return SCREEN_H - (worldAnchor.y - cameraY); }

    /** Top edge of the zone message at its anchor. */
    public float zoneTop(Vector2 zoneAnchor, String zone, float cameraY) {
        return HintLayout.top(screenY(zoneAnchor, cameraY), zoneHeight(zone), zoneFont.getCapHeight(), SCREEN_H);
    }

    private float contextTop(BitmapFont font, Vector2 contextAnchor, String context, boolean zonePresent, Vector2 zoneAnchor, String zone, float cameraY) {
        return HintLayout.contextTop(screenY(contextAnchor, cameraY), contextHeight(font, context), zonePresent,
            zonePresent ? screenY(zoneAnchor, cameraY) : 0, zonePresent ? zoneHeight(zone) : 0,
            font.getCapHeight(), zoneFont.getCapHeight(), SCREEN_H);
    }

    /**
     * Clear space between the zone message's bottom and the contextual label's top (negative: they overlap), the smaller of the two
     * text tiers: a test that needs this at least {@link HintLayout#GAP} holds in both.
     */
    public float gap(Vector2 contextAnchor, String context, Vector2 zoneAnchor, String zone, float cameraY) {
        float worst = Float.POSITIVE_INFINITY;
        for (BitmapFont font : contextFonts) {
            float zoneBottom = zoneTop(zoneAnchor, zone, cameraY) - zoneHeight(zone);
            worst = Math.min(worst, zoneBottom - contextTop(font, contextAnchor, context, true, zoneAnchor, zone, cameraY));
        }
        return worst;
    }

    /** {@link #heroOverlap} with no zone message on screen: the label at its own anchor; the larger overlap of the two text tiers. */
    public float heroOverlapAlone(Vector2 contextAnchor, String context, float cameraY,
                                  float heroLeft, float heroRight, float heroTop, float heroBottom) {
        float worst = Float.NEGATIVE_INFINITY;
        for (BitmapFont font : contextFonts)
            worst = Math.max(worst, overlap(font, contextAnchor, context, false, contextAnchor, "", cameraY, heroLeft, heroRight, heroTop, heroBottom));
        return worst;
    }

    /**
     * Vertical overlap in px (positive: crossing) between the contextual label as the HUD places it, with a zone
     * message present, and the hero's hitbox ({@code heroTop}/{@code heroBottom} in world y, Y-down), horizontally
     * only when the label's text overlaps the hero's {@code heroLeft..heroRight} in world x. The larger overlap of the two text tiers.
     */
    public float heroOverlap(Vector2 contextAnchor, String context, Vector2 zoneAnchor, String zone, float cameraY,
                             float heroLeft, float heroRight, float heroTop, float heroBottom) {
        float worst = Float.NEGATIVE_INFINITY;
        for (BitmapFont font : contextFonts)
            worst = Math.max(worst, overlap(font, contextAnchor, context, true, zoneAnchor, zone, cameraY, heroLeft, heroRight, heroTop, heroBottom));
        return worst;
    }

    private float overlap(BitmapFont font, Vector2 contextAnchor, String context, boolean zonePresent, Vector2 zoneAnchor, String zone, float cameraY,
                          float heroLeft, float heroRight, float heroTop, float heroBottom) {
        GlyphLayout layout = new GlyphLayout(font, context, Color.WHITE, Hud.CONTEXT_WIDTH, Align.center, true);
        float half = layout.width / 2;
        if (contextAnchor.x + half <= heroLeft || contextAnchor.x - half >= heroRight) return Float.NEGATIVE_INFINITY;
        float labelTop = contextTop(font, contextAnchor, context, zonePresent, zoneAnchor, zone, cameraY);
        float labelBottom = labelTop - layout.height;
        float heroScreenTop = SCREEN_H - (heroTop - cameraY), heroScreenBottom = SCREEN_H - (heroBottom - cameraY);
        return Math.min(labelTop, heroScreenTop) - Math.max(labelBottom, heroScreenBottom);
    }
}
