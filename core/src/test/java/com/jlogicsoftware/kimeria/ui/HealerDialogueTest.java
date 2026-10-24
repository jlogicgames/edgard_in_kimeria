package com.jlogicsoftware.kimeria.ui;

import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealerDialogueTest {
    private Files previousFiles;
    @BeforeEach void catalogs() {
        previousFiles = Gdx.files;
        Gdx.files = new HeadlessFiles();
    }
    @AfterEach void restoreFiles() { Gdx.files = previousFiles; }

    private void toChoices(HealerDialogue dialogue, Language language) {
        for (int i = 0; i < 20 && !dialogue.choosing(); i++) dialogue.advance(language);
        assertTrue(dialogue.choosing());
    }

    @Test void victoryReusesRevealRulesWithNoChoicesAndCompletesOnlyOnceInBothLanguages() {
        for (Language language : Language.values()) {
            HealerDialogue dialogue = HealerDialogue.afterVictory();
            assertTrue(dialogue.postVictory());
            for (Msg line : java.util.List.of(Msg.HEALER_VICTORY, Msg.EDGARD_DARKNESS, Msg.HEALER_GUIDE)) {
                assertEquals(line.t(language), dialogue.completeText(language));
                assertEquals(line == Msg.EDGARD_DARKNESS, dialogue.edgardSpeaking());
                assertEquals("", dialogue.text(language));
                assertNull(dialogue.interact(language, false, 0, false, -1));
                assertNull(dialogue.advance(language));
                assertEquals(line.t(language), dialogue.text(language));
                assertEquals(line == Msg.HEALER_GUIDE ? HealerDialogue.Choice.CONTINUE : null,
                    dialogue.advance(language));
                assertFalse(dialogue.choosing()); assertTrue(dialogue.choices().isEmpty());
            }
            assertNull(dialogue.advance(language));
        }
    }

    @Test void firstPressRevealsAndNextAdvancesInBothLanguagesWithoutChoosing() {
        for (Language language : Language.values()) {
            HealerDialogue dialogue = new HealerDialogue(false, false, false, false, false);
            assertEquals("", dialogue.text(language));
            dialogue.update(.1f);
            assertTrue(dialogue.text(language).length() > 0);
            assertTrue(dialogue.text(language).length() < dialogue.completeText(language).length());
            dialogue.advance(language);
            assertEquals(Msg.HEALER_GREETING.t(language), dialogue.text(language));
            dialogue.advance(language);
            assertEquals("", dialogue.text(language));
            assertTrue(dialogue.edgardSpeaking());
            toChoices(dialogue, language);
            assertEquals(2, dialogue.choices().size());
            assertFalse(dialogue.choices().contains(HealerDialogue.Choice.BREW));
        }
    }

    @Test void navigationCannotConfirmEvenWhenMirroredGamepadActionIsSimultaneous() {
        HealerDialogue dialogue = new HealerDialogue(false, true, true, false, false);
        toChoices(dialogue, Language.ENGLISH);
        assertNull(dialogue.interact(Language.ENGLISH, true, 1, true, -1));
        assertEquals(HealerDialogue.Choice.FIGHT, dialogue.choices().get(dialogue.selected()));
        assertEquals(HealerDialogue.Choice.FIGHT, dialogue.interact(Language.ENGLISH, false, 0, true, -1));
        assertEquals(HealerDialogue.Choice.RETURN, dialogue.interact(Language.ENGLISH, false, 0, false, 0));
    }

    @Test void fullRecipeKeepsIntroductionThenBrewsWithoutReturnOrUnempoweredFightChoice() {
        for (Language language : Language.values()) {
            HealerDialogue dialogue = new HealerDialogue(true, true, true, true, false);
            for (Msg message : java.util.List.of(Msg.HEALER_GREETING, Msg.EDGARD_REPLY)) {
                assertEquals(message.t(language), dialogue.completeText(language));
                assertEquals(message == Msg.EDGARD_REPLY, dialogue.edgardSpeaking());
                assertNull(dialogue.interact(language, true, 0, true, 0), "Reveal introduction without brewing");
                assertEquals(message.t(language), dialogue.text(language));
                assertNull(dialogue.interact(language, true, 0, true, 0), "Advance introduction without brewing");
            }
            assertEquals(Msg.HEALER_READY.t(language), dialogue.completeText(language));
            assertFalse(dialogue.edgardSpeaking());
            assertTrue(dialogue.choices().isEmpty());
            assertNull(dialogue.interact(language, true, 0, true, 0), "First fresh press only reveals the line");
            assertEquals(Msg.HEALER_READY.t(language), dialogue.text(language));
            assertFalse(dialogue.choosing());
            assertNull(dialogue.interact(language, false, 0, false, -1), "Holding cannot brew");
            assertEquals(HealerDialogue.Choice.BREW, dialogue.interact(language, true, 0, false, -1));
            HealerDialogue paid = new HealerDialogue(false, false, true, false, true);
            toChoices(paid, language);
            assertEquals(java.util.List.of(HealerDialogue.Choice.FIGHT), paid.choices());
        }
    }

    @Test void completingLastLineDoesNotActivateAChoiceWithTheSamePressOrTap() {
        HealerDialogue dialogue = new HealerDialogue(true, true, true, true, true);
        dialogue.advance(Language.ENGLISH); dialogue.advance(Language.ENGLISH); dialogue.advance(Language.ENGLISH);
        assertFalse(dialogue.choosing());
        assertNull(dialogue.interact(Language.ENGLISH, true, 0, true, 0));
        assertTrue(dialogue.choosing());
        assertEquals(java.util.List.of(HealerDialogue.Choice.FIGHT), dialogue.choices());
    }

    @Test void refusalDistinguishesIngredientsPaymentAndBoth() {
        for (Language language : Language.values()) {
            boolean[][] cases = {{false, true, true}, {true, false, true}, {true, true, false}, {false, false, false}};
            Msg[] expected = {Msg.HEALER_INGREDIENTS, Msg.HEALER_INGREDIENTS, Msg.HEALER_PAYMENT, Msg.HEALER_BOTH};
            for (int i = 0; i < cases.length; i++) {
                var c = cases[i];
                HealerDialogue dialogue = new HealerDialogue(c[0], c[1], c[2], false, false);
                for (int step = 0; step < 5; step++) dialogue.advance(language);
                assertEquals(expected[i].t(language), dialogue.text(language));
            }
        }
    }

    @Test void touchTargetsAreDistinctAndLargeEnoughInLogicalPixels() {
        HealerDialogueView view = new HealerDialogueView(new com.jlogicsoftware.kimeria.Assets() {
            @Override public com.badlogic.gdx.graphics.g2d.TextureRegion region(String path, com.badlogic.gdx.graphics.Texture.TextureFilter filter) {
                return new com.badlogic.gdx.graphics.g2d.TextureRegion();
            }
        });
        assertEquals(0, view.choiceAt(new Vector2(320, 260), 3));
        assertEquals(1, view.choiceAt(new Vector2(320, 290), 3));
        assertEquals(2, view.choiceAt(new Vector2(320, 320), 3));
        assertEquals(-1, view.choiceAt(new Vector2(320, 320), 2));
        assertEquals(-1, view.choiceAt(new Vector2(320, 150), 3));
        assertEquals(-1, view.choiceAt(new Vector2(590, 18), 2));
    }

    @Test void speakerRegionsOverlapByOneThirdAndEveryLocalizedLineFitsTheBakedFontBox() {
        var edgard = HealerDialogueView.dialogueRegion(true);
        var healer = HealerDialogueView.dialogueRegion(false);
        assertEquals(42f, edgard.x(), .001f);
        assertEquals(42f + 556f / 3f, healer.x(), .001f);
        assertEquals(556f * 2f / 3f, edgard.width(), .001f);
        assertEquals(edgard.width(), healer.width(), .001f);
        assertEquals(598f, edgard.x() + edgard.width(), .001f);
        assertEquals(598f, healer.x() + healer.width(), .001f);
        assertEquals(556f / 3f, edgard.x() + edgard.width() - healer.x(), .001f);

        var fontData = new BitmapFont.BitmapFontData(Gdx.files.internal("fonts/generated/QuestSquare-15.fnt"), false);
        var font = new BitmapFont(fontData, new TextureRegion(new MetricsTexture()), false);
        Msg[] healerLines = {Msg.HEALER_GREETING, Msg.HEALER_INGREDIENTS, Msg.HEALER_PAYMENT,
            Msg.HEALER_BOTH, Msg.HEALER_READY, Msg.HEALER_OFFER, Msg.HEALER_PAID,
            Msg.HEALER_LOCK, Msg.HEALER_VICTORY, Msg.HEALER_GUIDE};
        Msg[] edgardLines = {Msg.EDGARD_REPLY, Msg.EDGARD_DARKNESS};
        for (Language language : Language.values()) {
            for (Msg message : healerLines) assertLineFits(font, message.t(language), healer.width(), language, message);
            for (Msg message : edgardLines) assertLineFits(font, message.t(language), edgard.width(), language, message);
        }
        font.dispose();
    }

    private static void assertLineFits(BitmapFont font, String text, float width, Language language, Msg message) {
        GlyphLayout layout = new GlyphLayout(font, text, Color.WHITE, width,
            message == Msg.EDGARD_REPLY || message == Msg.EDGARD_DARKNESS ? Align.left : Align.right, true);
        assertTrue(layout.width <= width + 1f,
            () -> language + " " + message + " exceeds speaker width: " + layout.width + " > " + width);
        assertTrue(layout.height <= HealerDialogueView.dialogueTextHeight(),
            () -> language + " " + message + " exceeds dialogue height: " + layout.height + " > " + HealerDialogueView.dialogueTextHeight());
    }

    private static final class MetricsTexture extends Texture {
        MetricsTexture() { super(); }
        @Override public int getWidth() { return 1024; }
        @Override public int getHeight() { return 1024; }
        @Override public int getDepth() { return 0; }
        @Override public boolean isManaged() { return false; }
        @Override protected void reload() {}
    }
}
