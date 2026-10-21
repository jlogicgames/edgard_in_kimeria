package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.world.Level;

/** Original pixel-drawn placeholder portraits; no external art or asset loading. */
public final class HealerDialogueView {
    private static final Color HEALER_GREEN = new Color(.42f, .88f, .48f, 1f);
    private static final String[] HERO = {
        "................", ".....hhhhhh.....", "....hhhhhhhh....", "...hhsssssshh...",
        "...hssessessh...", "....ssssssss....", ".....ssssss.....", "......ssss......",
        "....rrrrrrrr....", "...rrbbbbbbrr...", "..sssbbbbbbsss..", "..sssbbbbbbsss..",
        ".....bbbbbb.....", ".....bbbbbb.....", "....bbbbbbbb....", "....bbbbbbbb...."
    };
    private static final String[] GUIDE = {
        "......gggg......", ".....gggggg.....", "....gggggggg....", "...ggssssssgg...",
        "...gssessessg...", "....ssssssss....", "....wwssssww....", "....wwwwwwww....",
        ".....wwwwww.....", "...gggwwwwggg...", "..gggggwwggggg..", "..ssggggggggss..",
        "..ssggggggggss..", "....gggggggg....", "...gggggggggg...", "...gggggggggg..."
    };

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
        portrait(batch, HERO, 40, 40);
        portrait(batch, GUIDE, 536, 40);
        overlay.body(batch, Msg.EDGARD.t(lang), 72, 108, 100, Color.WHITE);
        overlay.body(batch, Msg.HEALER.t(lang), 568, 108, 100, HEALER_GREEN);
        String checklist = dialogue.paid() ? Msg.PAID.t(lang)
            : mark(inventory.hasIngredient(level.name, ItemType.CHICKEN)) + Msg.CHICKEN.t(lang) + "\n"
                + mark(inventory.hasIngredient(level.name, ItemType.KEY)) + Msg.KEY.t(lang) + "\n"
                + mark(level.forestCoinTotal() > 0 && level.forestCoinsSecured() == level.forestCoinTotal())
                + Msg.FOREST_COINS_COUNT.format(lang, "count", Integer.toString(level.forestCoinsSecured()),
                    "total", Integer.toString(level.forestCoinTotal()));
        if (!dialogue.postVictory()) overlay.body(batch, checklist, 320, 48, 360);
        frame(batch, 24, 138, 592, 104, false);
        Color speakerColor = dialogue.edgardSpeaking() ? Color.WHITE : HEALER_GREEN;
        int alignment = dialogue.edgardSpeaking() ? Align.left : Align.right;
        overlay.dialogue(batch, dialogue.completeText(lang), dialogue.text(lang).length(),
            42, 154, 556, speakerColor, alignment);
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
    private static void portrait(SpriteBatch batch, String[] pixels, float x, float y) {
        for (int row = 0; row < pixels.length; row++) for (int col = 0; col < pixels[row].length(); col++) {
            switch (pixels[row].charAt(col)) {
                case 'h' -> batch.setColor(.28f, .16f, .09f, 1);
                case 's' -> batch.setColor(.92f, .68f, .42f, 1);
                case 'e' -> batch.setColor(.1f, .1f, .16f, 1);
                case 'r' -> batch.setColor(.8f, .18f, .14f, 1);
                case 'b' -> batch.setColor(.2f, .35f, .65f, 1);
                case 'g' -> batch.setColor(.22f, .48f, .32f, 1);
                case 'w' -> batch.setColor(.8f, .83f, .76f, 1);
                default -> { continue; }
            }
            batch.draw(Shaders.whiteQuad(), x + col * 4, y + row * 4, 4, 4);
        }
        batch.setColor(1, 1, 1, 1);
    }
}
