package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.player.Player;

import java.util.EnumMap;
import java.util.Map;

/**
 * Port of Dart's {@code Hud}: coin icon + count, drawn in fixed logical
 * (640x360) space. The count is drawn through a separate Y-up projection --
 * see {@link Overlay}'s class doc for why a {@link BitmapFont} needs that
 * instead of the Y-down-camera-plus-flipped-region trick used elsewhere.
 */
public class Hud {
    public static final float FPS_RIGHT_MARGIN = 6, FPS_TOP_MARGIN = 6;
    private static final float FPS_STOCK_GAP = 12, ICON_TEXT_GAP = 8, INVENTORY_GAP = 8, INVENTORY_SECTION_GAP = 16;
    private static final String FPS_WIDTH_SAMPLE = "999 FPS";
    private static final int COUNT_FONT_SIZE = 20, HINT_FONT_SIZE = 13;
    /**
     * #254 (D50): the zone message is 20 px, 400 px wide; the label above the hero 13 px, 200 px wide (D49 had 39/608
     * and 20/300). Public so the layout tests measure exactly what is drawn.
     */
    public static final int ZONE_FONT_SIZE = 20, CONTEXT_FONT_SIZE = 13;
    public static final float ZONE_WIDTH = 400, CONTEXT_WIDTH = 200;
    private static final float TEXT_SHADOW_OFFSET = 1;
    private static final float ICON_X = 10, ICON_Y = 10, ICON_SIZE = 32;
    private static final float FIREBALL_GAP = 4, EMPTY_FIREBALL_BRIGHTNESS = 0.25f;

    private final GameContext game;
    private final BossHud bossHud;
    private final TextureRegion coinIcon;
    private final TextureRegion fireballIcon;
    private final Map<ItemType, TextureRegion> artifactIcons = new EnumMap<>(ItemType.class);
    private final BitmapFont font, hintFont, zoneFont, contextFont;
    private final Matrix4 textProjection;
    private final float logicalWidth, logicalHeight;
    private final Vector2 hintPosition = new Vector2();
    private final Vector3 hintScreen = new Vector3();
    /** Immediate contextual label and the delayed, lingering zone message: separate channels (#211). */
    private final HintTransition hintTransition = new HintTransition(), zoneTransition = new HintTransition();
    private final Vector2 zonePosition = new Vector2(), contextPlace = new Vector2(), zonePlace = new Vector2();
    private final Color hintColor = new Color(Color.WHITE), hintShadow = new Color(Color.BLACK);
    private Player hintOwner;
    /** Per-frame scratch, so no frame allocates a layout or a matrix: the layouts are re-laid out in place with {@code setText}. */
    private final GlyphLayout measureLayout = new GlyphLayout(), shadowLayout = new GlyphLayout(), textLayout = new GlyphLayout(),
        fpsLayout = new GlyphLayout(), coinLayout = new GlyphLayout();
    private final Matrix4 previousProjection = new Matrix4();
    /** The width of {@link #FPS_WIDTH_SAMPLE}, measured once on first use (NaN until then). */
    private float fpsSampleWidth = Float.NaN;
    /** The coin count as text, rebuilt only when the count changes (the text and its layout are an event, not a frame). */
    private int shownCoins = -1;

    public Hud(Assets assets, GameContext game, float logicalWidth, float logicalHeight) {
        this.game = game;
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.coinIcon = assets.region("images/HUD/coin.png", com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        this.fireballIcon = assets.region("images/HUD/fireball.png", com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        for (ItemType type : new ItemType[]{ItemType.BAT, ItemType.MUSHROOM}) {
            artifactIcons.put(type, assets.region("images/HUD/" + type.iconName() + ".png",
                com.badlogic.gdx.graphics.Texture.TextureFilter.Linear));
        }
        this.font = assets.font(COUNT_FONT_SIZE);
        this.hintFont = assets.font(HINT_FONT_SIZE);
        this.zoneFont = assets.font(ZONE_FONT_SIZE);
        this.contextFont = assets.font(CONTEXT_FONT_SIZE);

        OrthographicCamera textCamera = new OrthographicCamera();
        textCamera.setToOrtho(false, logicalWidth, logicalHeight);
        textCamera.update();
        textProjection = textCamera.combined.cpy();
        bossHud = new BossHud(assets, game, textProjection, logicalWidth, logicalHeight);
    }

    public void render(Batch batch, Boss boss, boolean empowered, Player player, String fpsText) {
        batch.draw(coinIcon, ICON_X, ICON_Y, ICON_SIZE, ICON_SIZE);
        float rightEdge = logicalWidth - ICON_X;
        if (!fpsText.isEmpty()) {
            // Reserve a stable three-digit counter column; wider values can still push the HUD left.
            if (Float.isNaN(fpsSampleWidth)) { fpsLayout.setText(hintFont, FPS_WIDTH_SAMPLE); fpsSampleWidth = fpsLayout.width; }
            fpsLayout.setText(hintFont, fpsText);
            float fpsWidth = Math.max(fpsSampleWidth, fpsLayout.width);
            rightEdge = logicalWidth - FPS_RIGHT_MARGIN - fpsWidth - FPS_STOCK_GAP;
        }
        if (player != null) {
            float previousColor = batch.getPackedColor();
            float stockX = rightEdge - com.jlogicsoftware.kimeria.RangedStock.CAPACITY * ICON_SIZE
                - (com.jlogicsoftware.kimeria.RangedStock.CAPACITY - 1) * FIREBALL_GAP;
            for (int slot = 0; slot < com.jlogicsoftware.kimeria.RangedStock.CAPACITY; slot++) {
                float brightness = slot < player.ammunition() ? 1 : EMPTY_FIREBALL_BRIGHTNESS;
                batch.setColor(brightness, brightness, brightness, 1);
                batch.draw(fireballIcon, stockX + slot * (ICON_SIZE + FIREBALL_GAP), ICON_Y, ICON_SIZE, ICON_SIZE);
            }
            batch.setPackedColor(previousColor);
        }

        int coins = game.coinsCollected();
        if (coins != shownCoins) { shownCoins = coins; coinLayout.setText(font, Integer.toString(coins)); }
        GlyphLayout layout = coinLayout;
        float x = ICON_X + ICON_SIZE + ICON_TEXT_GAP;
        float artifactX = x + layout.width + INVENTORY_SECTION_GAP;
        var pickups = game.artifacts().items();
        for (int i = 0; i < pickups.size(); i++) {
            batch.draw(artifactIcons.get(pickups.get(i).type()), artifactX, ICON_Y, ICON_SIZE, ICON_SIZE);
            artifactX += ICON_SIZE + INVENTORY_GAP;
        }
        // Vertically center the label on the icon's own center, both
        // measured "distance from top" before converting to the Y-up
        // text projection's bottom-left-origin y.
        float yFromTop = ICON_Y + (ICON_SIZE - layout.height) / 2f;

        // A copy: setProjectionMatrix() overwrites the batch's own matrix in place, so a bare reference
        // would already hold the text projection when it is "restored" below.
        previousProjection.set(batch.getProjectionMatrix());
        batch.setProjectionMatrix(textProjection);
        font.setColor(Color.WHITE);
        font.draw(batch, layout, x, logicalHeight - yFromTop);
        batch.setProjectionMatrix(previousProjection);
        bossHud.render(batch, boss, empowered);
    }

    /**
     * Two independent hint channels (#211): the map-authored zone message (delayed, animated, lingering) and
     * the immediate contextual label (pickup, lever, mechanism, ladder, controls, fireball-full). Neither
     * hides, replaces or resets the other; {@link HintLayout} keeps them apart.
     */
    public void renderInteractionHint(Batch batch, Player player, OrthographicCamera camera, float dt) {
        if (hintOwner != player) { hintTransition.clear(); zoneTransition.clear(); hintOwner = player; }
        if (!player.canFight()) { // Death, checkpoint and victory end a lingering tutorial message.
            hintTransition.clear(); zoneTransition.clear();
        }
        float step = HintTransition.clampFrame(dt);
        String text = player.contextHint();
        if (!text.isEmpty()) player.contextHintPosition(hintPosition);
        hintTransition.update(text, hintPosition, step);
        String zone = player.zoneHint();
        if (!zone.isEmpty()) player.zoneHintPosition(zonePosition);
        zoneTransition.update(zone, zonePosition, step, true);

        boolean contextShown = hintTransition.alpha() > 0, zoneShown = zoneTransition.alpha() > 0;
        // The zone message is present while requested, delayed or lingering. Reserving its place from that,
        // not from its opacity, keeps the contextual label still while the message fades or crossfades.
        boolean zonePresent = !zone.isEmpty() || !zoneTransition.text().isEmpty();
        if (!contextShown && !zoneShown) return;
        float zoneHeight = 0, zoneAnchorY = 0;
        if (zonePresent) {
            boolean held = !zoneTransition.text().isEmpty();
            measureLayout.setText(zoneFont, held ? zoneTransition.text() : zone, hintColor, ZONE_WIDTH, Align.center, true);
            zoneHeight = measureLayout.height;
            zoneAnchorY = place(held ? zoneTransition.anchor() : zonePosition, zoneHeight, zoneFont, ZONE_WIDTH, camera, zonePlace);
        }
        if (contextShown) {
            measureLayout.setText(contextFont, hintTransition.text(), hintColor, CONTEXT_WIDTH, Align.center, true);
            float height = measureLayout.height;
            float anchorY = place(hintTransition.anchor(), height, contextFont, CONTEXT_WIDTH, camera, contextPlace);
            contextPlace.y = HintLayout.contextTop(anchorY, height, zonePresent, zoneAnchorY, zoneHeight,
                contextFont.getCapHeight(), zoneFont.getCapHeight(), logicalHeight);
        }

        previousProjection.set(batch.getProjectionMatrix());
        batch.setProjectionMatrix(textProjection);
        if (zoneShown) draw(batch, zoneTransition, zonePlace, zoneFont, ZONE_WIDTH);
        if (contextShown) draw(batch, hintTransition, contextPlace, contextFont, CONTEXT_WIDTH);
        batch.setProjectionMatrix(previousProjection);
    }

    /** Sets the label's left x and top edge at its world anchor; returns the anchor's screen y. */
    private float place(Vector2 anchor, float height, BitmapFont font, float width, OrthographicCamera camera, Vector2 out) {
        hintScreen.set(anchor.x, anchor.y, 0);
        camera.project(hintScreen, 0, 0, logicalWidth, logicalHeight);
        out.set(Math.max(HintLayout.SIDE_MARGIN, Math.min(logicalWidth - width - HintLayout.SIDE_MARGIN, hintScreen.x - width / 2)),
            HintLayout.top(hintScreen.y, height, font.getCapHeight(), logicalHeight));
        return hintScreen.y;
    }

    private void draw(Batch batch, HintTransition transition, Vector2 place, BitmapFont font, float width) {
        hintColor.a = hintShadow.a = transition.alpha();
        float y = place.y - transition.offsetY(); // Text projection is Y-up, slide offset is Y-down.
        // Glyph colours are fixed when a layout is built, so build them after the alpha is set.
        shadowLayout.setText(font, transition.text(), hintShadow, width, Align.center, true);
        textLayout.setText(font, transition.text(), hintColor, width, Align.center, true);
        font.draw(batch, shadowLayout, place.x + TEXT_SHADOW_OFFSET, y - TEXT_SHADOW_OFFSET);
        font.setColor(Color.WHITE);
        font.draw(batch, textLayout, place.x, y);
    }
}
