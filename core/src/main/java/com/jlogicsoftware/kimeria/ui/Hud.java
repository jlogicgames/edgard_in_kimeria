package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.entity.items.ItemType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Port of Dart's {@code Hud}: coin icon + count, drawn in fixed logical
 * (640x360) space. The count is drawn through a separate Y-up projection --
 * see {@link Overlay}'s class doc for why a {@link BitmapFont} needs that
 * instead of the Y-down-camera-plus-flipped-region trick used elsewhere.
 */
public class Hud {
    private static final float ICON_X = 10, ICON_Y = 10, ICON_SIZE = 32;

    private final GameContext game;
    private final TextureRegion coinIcon;
    private final Map<ItemType, TextureRegion> artifactIcons = new EnumMap<>(ItemType.class);
    private final BitmapFont font;
    private final Matrix4 textProjection;
    private final float logicalHeight;

    public Hud(Assets assets, GameContext game, float logicalWidth, float logicalHeight) {
        this.game = game;
        this.logicalHeight = logicalHeight;
        this.coinIcon = assets.region("images/Items.png", 0, 0, 16, 16);
        for (ItemType type : new ItemType[]{ItemType.CHICKEN, ItemType.KEY}) {
            artifactIcons.put(type, assets.region("images/Items.png", type.spriteX, type.spriteY, 16, 16));
        }
        this.font = assets.font(20);

        OrthographicCamera textCamera = new OrthographicCamera();
        textCamera.setToOrtho(false, logicalWidth, logicalHeight);
        textCamera.update();
        textProjection = textCamera.combined.cpy();
    }

    public void render(SpriteBatch batch) {
        batch.draw(coinIcon, ICON_X, ICON_Y, ICON_SIZE, ICON_SIZE);

        String text = String.valueOf(game.coinsCollected());
        GlyphLayout layout = new GlyphLayout(font, text);
        float x = ICON_X + ICON_SIZE + 8f;
        float artifactX = x + layout.width + 16f;
        for (var pickup : game.artifacts().items()) {
            batch.draw(artifactIcons.get(pickup.type()), artifactX, ICON_Y, ICON_SIZE, ICON_SIZE);
            artifactX += ICON_SIZE + 8f;
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
        batch.setProjectionMatrix(previous);
    }
}
