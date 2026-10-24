package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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
    private static final float HINT_WIDTH = 200, HINT_SIDE_MARGIN = 8, HINT_BOTTOM_MARGIN = 40, HINT_TOP_MARGIN = 52;
    private static final float TEXT_SHADOW_OFFSET = 1;
    private static final float ICON_X = 10, ICON_Y = 10, ICON_SIZE = 32;

    private final GameContext game;
    private final BossHud bossHud;
    private final TextureRegion coinIcon;
    private final TextureRegion fireballIcon;
    private final Map<ItemType, TextureRegion> artifactIcons = new EnumMap<>(ItemType.class);
    private final BitmapFont font, hintFont;
    private final Matrix4 textProjection;
    private final float logicalWidth, logicalHeight;
    private final Vector2 hintPosition = new Vector2();
    private final Vector3 hintScreen = new Vector3();
    private final HintTransition hintTransition = new HintTransition();
    private final Color hintColor = new Color(Color.WHITE), hintShadow = new Color(Color.BLACK);
    private Player hintOwner;

    public Hud(Assets assets, GameContext game, float logicalWidth, float logicalHeight) {
        this.game = game;
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.coinIcon = assets.region("images/HUD/coin.png", com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        this.fireballIcon = assets.region("images/HUD/fireball.png", com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        for (ItemType type : new ItemType[]{ItemType.CHICKEN, ItemType.KEY}) {
            artifactIcons.put(type, assets.region("images/HUD/" + type.name().toLowerCase(java.util.Locale.ROOT) + ".png",
                com.badlogic.gdx.graphics.Texture.TextureFilter.Linear));
        }
        this.font = assets.font(COUNT_FONT_SIZE);
        this.hintFont = assets.font(HINT_FONT_SIZE);

        OrthographicCamera textCamera = new OrthographicCamera();
        textCamera.setToOrtho(false, logicalWidth, logicalHeight);
        textCamera.update();
        textProjection = textCamera.combined.cpy();
        bossHud = new BossHud(assets, game, textProjection, logicalWidth, logicalHeight);
    }

    public void render(SpriteBatch batch, Boss boss, boolean empowered, Player player, String fpsText) {
        batch.draw(coinIcon, ICON_X, ICON_Y, ICON_SIZE, ICON_SIZE);
        GlyphLayout ammunition = player == null ? null : new GlyphLayout(font, String.valueOf(player.ammunition()));
        float rightEdge = logicalWidth - ICON_X;
        if (!fpsText.isEmpty()) {
            // Reserve a stable three-digit counter column; wider values can still push the HUD left.
            float fpsWidth = Math.max(new GlyphLayout(hintFont, FPS_WIDTH_SAMPLE).width, new GlyphLayout(hintFont, fpsText).width);
            rightEdge = logicalWidth - FPS_RIGHT_MARGIN - fpsWidth - FPS_STOCK_GAP;
        }
        float ammunitionX = ammunition == null ? rightEdge : rightEdge - ammunition.width;
        if (player != null) batch.draw(fireballIcon, ammunitionX - ICON_TEXT_GAP - ICON_SIZE, ICON_Y, ICON_SIZE, ICON_SIZE);

        String text = String.valueOf(game.coinsCollected());
        GlyphLayout layout = new GlyphLayout(font, text);
        float x = ICON_X + ICON_SIZE + ICON_TEXT_GAP;
        float artifactX = x + layout.width + INVENTORY_SECTION_GAP;
        for (var pickup : game.artifacts().items()) {
            batch.draw(artifactIcons.get(pickup.type()), artifactX, ICON_Y, ICON_SIZE, ICON_SIZE);
            artifactX += ICON_SIZE + INVENTORY_GAP;
        }
        // Vertically center the label on the icon's own center, both
        // measured "distance from top" before converting to the Y-up
        // text projection's bottom-left-origin y.
        float yFromTop = ICON_Y + (ICON_SIZE - layout.height) / 2f;

        // A copy: setProjectionMatrix() overwrites the batch's own matrix in place, so a bare reference
        // would already hold the text projection when it is "restored" below.
        Matrix4 previous = batch.getProjectionMatrix().cpy();
        batch.setProjectionMatrix(textProjection);
        font.setColor(Color.WHITE);
        font.draw(batch, layout, x, logicalHeight - yFromTop);
        if (ammunition != null) {
            float ammunitionTop = ICON_Y + (ICON_SIZE - ammunition.height) / 2f;
            font.draw(batch, ammunition, ammunitionX, logicalHeight - ammunitionTop);
        }
        batch.setProjectionMatrix(previous);
        bossHud.render(batch, boss, empowered);
    }

    /** First-room contextual lessons, smoothly faded at their own world position. */
    public void renderInteractionHint(SpriteBatch batch, Player player, OrthographicCamera camera, float dt) {
        if (hintOwner != player) { hintTransition.clear(); hintOwner = player; }
        String text = player.contextHint();
        if (!text.isEmpty()) player.contextHintPosition(hintPosition);
        hintTransition.update(text, hintPosition, dt);
        if (hintTransition.alpha() == 0) return;
        text = hintTransition.text();
        hintTransition.position(hintPosition);
        hintScreen.set(hintPosition.x, hintPosition.y, 0);
        camera.project(hintScreen, 0, 0, logicalWidth, logicalHeight);
        float width = HINT_WIDTH;
        float x = Math.max(HINT_SIDE_MARGIN, Math.min(logicalWidth - width - HINT_SIDE_MARGIN, hintScreen.x - width / 2));
        float y = Math.max(HINT_BOTTOM_MARGIN, Math.min(logicalHeight - HINT_TOP_MARGIN, hintScreen.y));
        hintColor.a = hintShadow.a = hintTransition.alpha();
        GlyphLayout layout = new GlyphLayout(hintFont, text, hintColor, width, Align.center, true);
        // Grow upward: a second instruction must not cover the hero's head below the label.
        y = Math.min(logicalHeight - HINT_TOP_MARGIN, y + Math.max(0, layout.height - hintFont.getCapHeight()));
        y -= hintTransition.offsetY(); // Text projection is Y-up, slide offset is Y-down.
        Matrix4 previous = batch.getProjectionMatrix().cpy();
        batch.setProjectionMatrix(textProjection);
        GlyphLayout shadow = new GlyphLayout(hintFont, text, hintShadow, width, Align.center, true);
        hintFont.draw(batch, shadow, x + TEXT_SHADOW_OFFSET, y - TEXT_SHADOW_OFFSET);
        hintFont.setColor(Color.WHITE);
        hintFont.draw(batch, layout, x, y);
        batch.setProjectionMatrix(previous);
    }
}
