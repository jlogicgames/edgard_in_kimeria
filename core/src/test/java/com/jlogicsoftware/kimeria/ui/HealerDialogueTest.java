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

    @Test void tutorialTalesAreScriptedWithoutChoicesChecklistOrRepeatedCompletion() {
        Object[][] scripts = {
            {HealerDialogue.Kind.TUTORIAL_START, java.util.List.of(Msg.HEALER_TALE_ROAD, Msg.EDGARD_TALE_NO_TIME,
                Msg.HEALER_TALE_FAIRIES, Msg.HEALER_TALE_LIFT, Msg.HEALER_TALE_WITCH,
                Msg.HEALER_TALE_THREAD, Msg.HEALER_TALE_FIREGIFT, Msg.EDGARD_TALE_UNDERSTOOD)},
            {HealerDialogue.Kind.TUTORIAL_END, java.util.List.of(Msg.HEALER_TALE_PRAISE, Msg.EDGARD_TALE_READY,
                Msg.HEALER_TALE_HEROES, Msg.HEALER_TALE_BLESSING)}};
        for (Language language : Language.values()) {
            for (Object[] script : scripts) {
                HealerDialogue dialogue = script[0] == HealerDialogue.Kind.TUTORIAL_START
                    ? HealerDialogue.tutorialStart() : HealerDialogue.tutorialEnd();
                @SuppressWarnings("unchecked") java.util.List<Msg> lines = (java.util.List<Msg>) script[1];
                assertEquals(script[0], dialogue.kind());
                assertTrue(dialogue.scripted()); assertFalse(dialogue.postVictory()); assertFalse(dialogue.showsChecklist());
                for (int i = 0; i < lines.size(); i++) {
                    Msg line = lines.get(i);
                    assertEquals(line.t(language), dialogue.completeText(language));
                    assertEquals(line.name().startsWith("EDGARD_"), dialogue.edgardSpeaking());
                    assertNull(dialogue.advance(language), "first press reveals");
                    assertEquals(line.t(language), dialogue.text(language));
                    assertEquals(i == lines.size() - 1 ? HealerDialogue.Choice.CONTINUE : null, dialogue.advance(language));
                    assertFalse(dialogue.choosing()); assertTrue(dialogue.choices().isEmpty());
                }
                assertNull(dialogue.advance(language), "completion fires once");
            }
        }
    }

    /** The repository root, found by walking up from the working directory (Gradle runs in core/, an IDE may not). */
    private static java.nio.file.Path repositoryRoot() {
        java.nio.file.Path p = java.nio.file.Path.of("").toAbsolutePath();
        while (p != null && !java.nio.file.Files.isRegularFile(p.resolve("tools/text_style.json"))) p = p.getParent();
        assertNotNull(p, "tools/text_style.json not found above " + java.nio.file.Path.of("").toAbsolutePath());
        return p;
    }

    private static com.badlogic.gdx.utils.JsonValue register() throws java.io.IOException {
        String json = java.nio.file.Files.readString(repositoryRoot().resolve("tools/text_style.json"));
        return new com.badlogic.gdx.utils.JsonReader().parse(json).get("register");
    }

    /**
     * The register groups of docs/TEXT_STYLE.md for one language, shared with tools/check_text_style.py (T2):
     * stems match at the start of a word, words only as whole words, case-insensitive.
     */
    private static java.util.Map<String, java.util.List<java.util.regex.Pattern>> registerGroups(Language language) throws java.io.IOException {
        var groups = new java.util.LinkedHashMap<String, java.util.List<java.util.regex.Pattern>>();
        int flags = java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE
            | java.util.regex.Pattern.UNICODE_CHARACTER_CLASS;
        for (com.badlogic.gdx.utils.JsonValue group : register()) {
            var patterns = new java.util.ArrayList<java.util.regex.Pattern>();
            com.badlogic.gdx.utils.JsonValue terms = group.get(language.code());
            if (terms != null) {
                for (String stem : terms.get("stems").asStringArray())
                    patterns.add(java.util.regex.Pattern.compile("(?<!\\w)" + java.util.regex.Pattern.quote(stem), flags));
                for (String word : terms.get("words").asStringArray())
                    patterns.add(java.util.regex.Pattern.compile("(?<!\\w)" + java.util.regex.Pattern.quote(word) + "(?!\\w)", flags));
            }
            groups.put(group.name, patterns);
        }
        return groups;
    }

    /**
     * Every word this test pinned before the list moved to tools/text_style.json, with the group and language that
     * must hold it: the data may grow, never drop or move them. (Before D74 each word was checked in every language as
     * a substring; now each is checked in its own language, at a word start, so "alive" no longer trips "live".)
     */
    @Test void sharedRegisterListKeepsEveryWordThisTestUsedToPin() throws java.io.IOException {
        String[][] floor = {
            {"controls", "en", "lever", "button", "press", "jump", "key", "stick"},
            {"controls", "de", "hebel", "taste", "drück", "spring", "knopf"},
            {"controls", "pl", "dźwign", "przycisk", "naciśn", "skocz", "klawisz", "drążek"},
            {"controls", "uk", "важіл", "кнопк", "натисн", "стрибк", "клавіш"},
            {"controls", "be", "рычаг", "націсн", "скачк", "клавіш"},
            {"lives", "en", "live", "lives"}, {"lives", "de", "leben"}, {"lives", "pl", "życi"},
            {"lives", "uk", "життя", "життів"}, {"lives", "be", "жыцц"},
            {"respawn", "en", "respawn"}, {"respawn", "de", "wiederbele"}, {"respawn", "pl", "odrodz"},
            {"respawn", "uk", "відродж"}, {"respawn", "be", "адраджэн"},
            {"retry", "en", "retry", "restart"}, {"retry", "de", "neustart"}, {"retry", "pl", "ponown", "restart"},
            {"retry", "uk", "повтор", "рестарт"}, {"retry", "be", "паўтор", "рэстарт"},
            {"checkpoint", "en", "checkpoint"}, {"checkpoint", "de", "kontrollpunkt", "speicherpunkt"},
            {"checkpoint", "pl", "punkt kontrol"}, {"checkpoint", "uk", "контрольн"}, {"checkpoint", "be", "кантрольн"}};
        com.badlogic.gdx.utils.JsonValue register = register();
        for (String[] row : floor) {
            com.badlogic.gdx.utils.JsonValue terms = register.get(row[0]) == null ? null : register.get(row[0]).get(row[1]);
            assertNotNull(terms, "tools/text_style.json lost the group " + row[0] + "/" + row[1]);
            var held = new java.util.HashSet<String>(java.util.List.of(terms.get("stems").asStringArray()));
            held.addAll(java.util.List.of(terms.get("words").asStringArray()));
            for (int i = 2; i < row.length; i++)
                assertTrue(held.contains(row[i]), "tools/text_style.json lost " + row[i] + " from " + row[0] + "/" + row[1]);
        }
    }

    @Test void tutorialTalesStayInFairyTaleRegisterWithoutNamingControlsOrMechanics() throws java.io.IOException {
        // #228: the thread explains the returns in images; no control and no game term in any language.
        for (Language language : Language.values()) {
            var groups = registerGroups(language);
            for (HealerDialogue d : new HealerDialogue[]{HealerDialogue.tutorialStart(), HealerDialogue.tutorialEnd()}) {
                String text = "";
                for (int i = 0; i < 12; i++) { text += d.completeText(language) + " "; if (d.advance(language) == null) d.advance(language); }
                for (var group : groups.entrySet()) for (var pattern : group.getValue())
                    assertFalse(pattern.matcher(text).find(), language + " names " + group.getKey() + ": " + pattern);
            }
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

    @Test void returningGreetingChecksCurrentRecipeStateForEveryLanguage() {
        for (Language language : Language.values()) {
            boolean[][] cases = {{false, true, true}, {true, false, true}, {true, true, false}, {false, true, false}};
            Msg[] expected = {Msg.HEALER_INGREDIENTS, Msg.HEALER_INGREDIENTS, Msg.HEALER_PAYMENT, Msg.HEALER_BOTH};
            for (int i = 0; i < cases.length; i++) {
                boolean[] c = cases[i];
                HealerDialogue dialogue = new HealerDialogue(c[0], c[1], c[2], false, false, true);
                assertEquals(Msg.HEALER_RETURN_GREETING.t(language), dialogue.completeText(language));
                assertFalse(dialogue.edgardSpeaking());
                dialogue.advance(language); dialogue.advance(language);
                assertEquals(Msg.HEALER_RETURN_CHECK.t(language), dialogue.completeText(language));
                dialogue.advance(language); dialogue.advance(language);
                assertEquals(expected[i].t(language), dialogue.completeText(language));
                dialogue.advance(language); dialogue.advance(language);
                assertEquals(Msg.HEALER_OFFER.t(language), dialogue.completeText(language));
                toChoices(dialogue, language);
                assertEquals(java.util.List.of(HealerDialogue.Choice.RETURN, HealerDialogue.Choice.FIGHT), dialogue.choices());
                assertEquals(0, dialogue.selected(), "Return remains the initial selection after repeat visits");
            }

            HealerDialogue newlyComplete = new HealerDialogue(true, true, true, true, false, true);
            for (Msg line : java.util.List.of(Msg.HEALER_RETURN_GREETING, Msg.HEALER_RETURN_CHECK, Msg.HEALER_READY)) {
                assertEquals(line.t(language), newlyComplete.completeText(language));
                newlyComplete.advance(language);
                if (line != Msg.HEALER_READY) newlyComplete.advance(language);
            }
            assertEquals(HealerDialogue.Choice.BREW, newlyComplete.advance(language));

            HealerDialogue paid = new HealerDialogue(false, false, false, false, true, true);
            assertEquals(Msg.HEALER_PAID.t(language), paid.completeText(language));
            assertFalse(paid.completeText(language).equals(Msg.HEALER_RETURN_GREETING.t(language)));
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
        assertEquals(42f + 556f * 2f / 3f, edgard.x() + edgard.width(), .001f);
        assertEquals(598f, healer.x() + healer.width(), .001f);
        assertEquals(556f / 3f, edgard.x() + edgard.width() - healer.x(), .001f);

        var fontData = new BitmapFont.BitmapFontData(Gdx.files.internal("fonts/generated/QuestSquare-" + FontSizes.DIALOGUE + ".fnt"), false);
        var font = new BitmapFont(fontData, new TextureRegion(new MetricsTexture()), false);
        int spokenLineCount = 0;
        for (Language language : Language.values()) {
            for (Msg message : Msg.values()) {
                if (!isSpokenDialogueLine(message)) continue;
                spokenLineCount++;
                boolean edgardSpeaking = message.name().startsWith("EDGARD_");
                float width = edgardSpeaking ? edgard.width() : healer.width();
                assertLineFits(font, message.t(language), width, language, message);
            }
        }
        assertTrue(spokenLineCount > 0, "Typed healer/player dialogue keys must be covered");
        font.dispose();
    }

    private static boolean isSpokenDialogueLine(Msg message) {
        if (message == Msg.HEALER_RETURN || message == Msg.HEALER_BREW || message == Msg.HEALER_FIGHT
            || message == Msg.HEALER_HINT) return false;
        return message.name().startsWith("HEALER_") || message.name().startsWith("EDGARD_");
    }

    private static void assertLineFits(BitmapFont font, String text, float width, Language language, Msg message) {
        int alignment = message.name().startsWith("EDGARD_") ? Align.left : Align.right;
        GlyphLayout layout = new GlyphLayout(font, text, Color.WHITE, width,
            alignment, true);
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
