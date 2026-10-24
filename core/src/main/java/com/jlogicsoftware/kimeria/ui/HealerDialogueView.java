package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.world.Level;

/** Shared image portraits; their textures are owned by the game's Assets cache. */
public final class HealerDialogueView {
    private static final Color HEALER_GREEN = new Color(.42f, .88f, .48f, 1f);
    private static final float FRAME_X = 24f, FRAME_Y = 138f, FRAME_WIDTH = 592f, FRAME_HEIGHT = 104f;
    private static final float TEXT_HORIZONTAL_INSET = 18f, TEXT_VERTICAL_INSET = 16f;
    private static final float TEXT_INNER_X = FRAME_X + TEXT_HORIZONTAL_INSET;
    private static final float TEXT_INNER_WIDTH = FRAME_WIDTH - TEXT_HORIZONTAL_INSET * 2f;
    private static final float TEXT_INNER_Y = FRAME_Y + TEXT_VERTICAL_INSET;
    private static final float TEXT_INNER_HEIGHT = FRAME_HEIGHT - TEXT_VERTICAL_INSET * 2f;
    private final TextureRegion edgard, healer;

    record DialogueRegion(float x, float width) {}

    static DialogueRegion dialogueRegion(boolean edgardSpeaking) {
        float speakerWidth = TEXT_INNER_WIDTH * 2f / 3f;
        float x = edgardSpeaking ? TEXT_INNER_X : TEXT_INNER_X + TEXT_INNER_WIDTH / 3f;
        return new DialogueRegion(x, speakerWidth);
    }

    static float dialogueTextHeight() { return TEXT_INNER_HEIGHT; }

    public HealerDialogueView(Assets assets) {
        edgard = assets.region("images/portraits/edgard.png", Texture.TextureFilter.Linear);
        healer = assets.region("images/portraits/healer.png", Texture.TextureFilter.Linear);
    }

    public int choiceAt(Vector2 p, int count) {
        if (p.x < 40 || p.x > 600) return -1;
        for (int i = 0; i < count; i++) if (p.y >= 252 + i * 30 && p.y < 278 + i * 30) return i;
        return -1;
    }
    public void render(SpriteBatch batch, Overlay overlay, HealerDialogue dialogue, Language lang,
                       Level level, ArtifactInventory inventory) {
        overlay.panel(batch, 0, 0, 640, 360, 1);
        frame(batch, 28, 32, 88, 88, dialogue.edgardSpeaking());
        frame(batch, 524, 32, 88, 88, !dialogue.edgardSpeaking());
        portrait(batch, edgard, 40, 40);
        portrait(batch, healer, 536, 40);
        overlay.body(batch, Msg.EDGARD.t(lang), 72, 108, 100, Color.WHITE);
        overlay.body(batch, Msg.HEALER.t(lang), 568, 108, 100, HEALER_GREEN);
        String checklist = dialogue.paid() ? Msg.PAID.t(lang)
            : mark(inventory.hasIngredient(level.name, ItemType.CHICKEN)) + Msg.CHICKEN.t(lang) + "\n"
                + mark(inventory.hasIngredient(level.name, ItemType.KEY)) + Msg.KEY.t(lang) + "\n"
                + mark(level.forestCoinTotal() > 0 && level.forestCoinsSecured() == level.forestCoinTotal())
                + Msg.FOREST_COINS_COUNT.format(lang, "count", Integer.toString(level.forestCoinsSecured()),
                    "total", Integer.toString(level.forestCoinTotal()));
        if (!dialogue.postVictory()) overlay.body(batch, checklist, 320, 48, 360);
        frame(batch, FRAME_X, FRAME_Y, FRAME_WIDTH, FRAME_HEIGHT, false);
        Color speakerColor = dialogue.edgardSpeaking() ? Color.WHITE : HEALER_GREEN;
        int alignment = dialogue.edgardSpeaking() ? Align.left : Align.right;
        DialogueRegion region = dialogueRegion(dialogue.edgardSpeaking());
        overlay.dialogue(batch, dialogue.completeText(lang), dialogue.text(lang).length(),
            region.x(), TEXT_INNER_Y, region.width(), speakerColor, alignment);
        if (dialogue.choosing()) {
            for (int i = 0; i < dialogue.choices().size(); i++) {
                frame(batch, 40, 252 + i * 30, 560, 26, i == dialogue.selected());
                Msg label = switch (dialogue.choices().get(i)) {
                    case RETURN -> Msg.HEALER_RETURN;
                    case BREW -> Msg.HEALER_BREW;
                    case FIGHT -> Msg.HEALER_FIGHT;
                    case CONTINUE -> throw new IllegalStateException("Continuation has no choice button");
                };
                overlay.choiceLabel(batch, label.t(lang), 40, 252 + i * 30, 560, 26, i == dialogue.selected());
            }
        } else if (dialogue.fullyRevealed(lang)) {
            String prompt = Msg.HEALER_HINT.t(lang);
            overlay.dialogue(batch, prompt, prompt.length(), 42, 258, 556, speakerColor, Align.right);
        }
    }
    private static String mark(boolean present) { return present ? "[+] " : "[-] "; }
    private static void frame(SpriteBatch batch, float x, float y, float w, float h, boolean selected) {
        batch.setColor(selected ? 1f : .55f, selected ? .8f : .6f, selected ? .3f : .7f, 1);
        batch.draw(Shaders.whiteQuad(), x, y, w, h);
        batch.setColor(.035f, .045f, .075f, 1);
        batch.draw(Shaders.whiteQuad(), x + 2, y + 2, w - 4, h - 4);
        batch.setColor(1, 1, 1, 1);
    }
    private static void portrait(SpriteBatch batch, TextureRegion image, float x, float y) {
        // Fit the entire canvas inside the existing 64x64 slot, including non-square replacements.
        float scale = Math.min(64f / image.getRegionWidth(), 64f / image.getRegionHeight());
        float width = image.getRegionWidth() * scale, height = image.getRegionHeight() * scale;
        batch.setColor(Color.WHITE);
        batch.draw(image, x + (64 - width) / 2f, y + (64 - height) / 2f, width, height);
    }
}
