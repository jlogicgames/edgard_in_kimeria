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

    @Test void instantTextShowsTheWholeLineOnTheFirstFrameAndTheFirstPressAdvances() {
        for (Language language : Language.values()) {
            HealerDialogue dialogue = HealerDialogue.afterVictory();
            dialogue.setInstantText(true);
            assertEquals(Msg.HEALER_VICTORY.t(language), dialogue.text(language), "whole line before any update");
            assertTrue(dialogue.fullyRevealed(language));
            dialogue.advance(language); // no "skip the reveal" press first
            assertEquals(Msg.EDGARD_DARKNESS.t(language), dialogue.completeText(language));
            assertEquals(Msg.EDGARD_DARKNESS.t(language), dialogue.text(language));
        }
    }

    @Test void instantTextOnAtOpeningShowsEveryKindOfDialogueWholeBeforeAnyUpdate() {
        Language language = Language.ENGLISH;
        for (HealerDialogue dialogue : new HealerDialogue[]{HealerDialogue.tutorialStart(), HealerDialogue.tutorialEnd(),
            HealerDialogue.afterVictory(), new HealerDialogue(true, true, true, true, false), new HealerDialogue(false, false, false, false, false, true)}) {
            dialogue.setInstantText(true);
            assertEquals(dialogue.completeText(language), dialogue.text(language));
            assertEquals(dialogue.completeText(language).length(), dialogue.visibleCharacters(language));
            assertTrue(dialogue.fullyRevealed(language), "the press hint may show on the first frame");
        }
    }

    @Test void instantTextNPressesAdvanceExactlyNLinesAndTheLastPressFinishesOnce() {
        Language language = Language.ENGLISH;
        HealerDialogue dialogue = HealerDialogue.tutorialStart(); // 9 lines (#338 added the fairy gift)
        dialogue.setInstantText(true);
        for (int press = 1; press <= 8; press++) {
            assertNull(dialogue.advance(language));
            assertEquals(java.util.List.of(Msg.HEALER_TALE_ROAD, Msg.EDGARD_TALE_NO_TIME, Msg.HEALER_TALE_FAIRIES, Msg.HEALER_TALE_LIFT,
                Msg.HEALER_TALE_WITCH, Msg.HEALER_TALE_THREAD, Msg.HEALER_TALE_FIREGIFT, Msg.HEALER_TALE_FAIRY, Msg.EDGARD_TALE_UNDERSTOOD).get(press).t(language),
                dialogue.completeText(language), "after press " + press);
        }
        assertEquals(HealerDialogue.Choice.CONTINUE, dialogue.advance(language)); // the 9th press leaves the last line
        assertNull(dialogue.advance(language), "a press after the end does nothing");
        assertEquals(Msg.EDGARD_TALE_UNDERSTOOD.t(language), dialogue.completeText(language));
    }

    @Test void instantTextOffNeedsTwoPressesPerLineAndOnNeedsOne() {
        Language language = Language.ENGLISH;
        HealerDialogue slow = HealerDialogue.afterVictory();
        slow.advance(language);
        assertEquals(Msg.HEALER_VICTORY.t(language), slow.completeText(language), "first press only completes the line");
        slow.advance(language);
        assertEquals(Msg.EDGARD_DARKNESS.t(language), slow.completeText(language));
        HealerDialogue fast = HealerDialogue.afterVictory();
        fast.setInstantText(true);
        fast.advance(language);
        assertEquals(Msg.EDGARD_DARKNESS.t(language), fast.completeText(language));
    }

    @Test void togglingMidLineCompletesOrResumesTheReveal() {
        Language language = Language.ENGLISH;
        HealerDialogue dialogue = HealerDialogue.afterVictory();
        dialogue.update(HealerDialogue.OPEN_SECONDS); // the screen opens first (D78); letters start after it
        dialogue.update(0.1f);
        int partial = dialogue.visibleCharacters(language);
        assertTrue(partial > 0 && partial < dialogue.completeText(language).length());
        dialogue.setInstantText(true); // switching on shows the whole line at once
        assertEquals(dialogue.completeText(language).length(), dialogue.visibleCharacters(language));
        dialogue.setInstantText(false); // off again: the reveal position it had is kept, not reset to blank
        assertEquals(partial, dialogue.visibleCharacters(language));
        dialogue.advance(language);
        assertEquals(Msg.HEALER_VICTORY.t(language), dialogue.completeText(language));
        assertTrue(dialogue.fullyRevealed(language));
    }

    @Test void instantTextOffKeepsTheLetterByLetterReveal() {
        HealerDialogue dialogue = HealerDialogue.afterVictory();
        dialogue.setInstantText(false);
        assertEquals("", dialogue.text(Language.ENGLISH));
        assertFalse(dialogue.fullyRevealed(Language.ENGLISH));
        dialogue.advance(Language.ENGLISH); // first press only completes the line
        assertEquals(Msg.HEALER_VICTORY.t(Language.ENGLISH), dialogue.completeText(Language.ENGLISH));
        assertTrue(dialogue.fullyRevealed(Language.ENGLISH));
    }

    @Test void victoryReusesRevealRulesWithNoChoicesAndCompletesOnlyOnceInBothLanguages() {
        for (Language language : Language.values()) {
            HealerDialogue dialogue = HealerDialogue.afterVictory();
            dialogue.update(HealerDialogue.OPEN_SECONDS); // the screen is open (D78); input before that is ignored
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
                Msg.HEALER_TALE_THREAD, Msg.HEALER_TALE_FIREGIFT, Msg.HEALER_TALE_FAIRY, Msg.EDGARD_TALE_UNDERSTOOD)},
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
            dialogue.update(HealerDialogue.OPEN_SECONDS);
            assertEquals("", dialogue.text(language), "no letter while the screen opens");
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
        dialogue.update(HealerDialogue.OPEN_SECONDS); // the screen is open (D78); input before that is ignored
        toChoices(dialogue, Language.ENGLISH);
        assertNull(dialogue.interact(Language.ENGLISH, true, 1, true, -1));
        assertEquals(HealerDialogue.Choice.FIGHT, dialogue.choices().get(dialogue.selected()));
        assertEquals(HealerDialogue.Choice.FIGHT, dialogue.interact(Language.ENGLISH, false, 0, true, -1));
        assertEquals(HealerDialogue.Choice.RETURN, dialogue.interact(Language.ENGLISH, false, 0, false, 0));
    }

    @Test void fullRecipeKeepsIntroductionThenBrewsWithoutReturnOrUnempoweredFightChoice() {
        for (Language language : Language.values()) {
            HealerDialogue dialogue = new HealerDialogue(true, true, true, true, false);
            dialogue.update(HealerDialogue.OPEN_SECONDS); // the screen is open (D78); input before that is ignored
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
        dialogue.update(HealerDialogue.OPEN_SECONDS); // the screen is open (D78); input before that is ignored
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
        // D78: the choices sit in the middle column, 26 px tall, 28 px apart, from y 84.
        assertEquals(0, view.choiceAt(new Vector2(320, 90), 2));
        assertEquals(1, view.choiceAt(new Vector2(320, 120), 2));
        assertEquals(-1, view.choiceAt(new Vector2(320, 120), 1));
        assertEquals(-1, view.choiceAt(new Vector2(320, 111), 2), "the 2 px gap between buttons hits nothing");
        assertEquals(-1, view.choiceAt(new Vector2(320, 260), 2), "the text box is not a button");
        assertEquals(-1, view.choiceAt(new Vector2(100, 90), 2), "the portrait window is not a button");
        assertTrue(HealerDialogueView.CHOICE_H >= 26 && HealerDialogueView.MIDDLE_W >= 200, "touch targets stay large");
    }

    /**
     * D78: every spoken line, in all five languages, fits the 556 px text box at the 18 px baked font in at most three
     * lines, so one of the box's four lines stays spare for the first-death line (#341).
     */
    @Test void everyLocalizedLineFitsTheTextBoxWithOneLineSpare() {
        assertEquals(556f, HealerDialogueView.TEXT_W, .001f);
        assertEquals(4 * 18f, HealerDialogueView.TEXT_H, .001f);
        var fontData = new BitmapFont.BitmapFontData(Gdx.files.internal("fonts/generated/QuestSquare-" + FontSizes.DIALOGUE + ".fnt"), false);
        var font = new BitmapFont(fontData, new TextureRegion(new MetricsTexture()), false);
        int spokenLineCount = 0, mostLines = 0;
        for (Language language : Language.values()) {
            for (Msg message : Msg.values()) {
                if (!isSpokenDialogueLine(message)) continue;
                spokenLineCount++;
                mostLines = Math.max(mostLines, assertLineFits(font, message.t(language), language, message));
            }
        }
        assertTrue(spokenLineCount > 0, "Typed healer/player dialogue keys must be covered");
        assertEquals(3, mostLines, "the longest line (de HEALER_TALE_FIREGIFT) wraps to three lines");
        font.dispose();
    }

    /**
     * D134 (#538): the fourteen pre-boss, checklist and victory lines of the tale-voice pass take at most two of the text
     * box's four lines in every language, at the 18 px baked font in the 556 px box.
     */
    @Test void theTalePassLinesTakeAtMostTwoLinesInEveryLanguage() {
        var fontData = new BitmapFont.BitmapFontData(Gdx.files.internal("fonts/generated/QuestSquare-" + FontSizes.DIALOGUE + ".fnt"), false);
        var font = new BitmapFont(fontData, new TextureRegion(new MetricsTexture()), false);
        Msg[] passLines = {Msg.HEALER_GREETING, Msg.EDGARD_REPLY, Msg.HEALER_RETURN_GREETING, Msg.HEALER_RETURN_CHECK,
            Msg.HEALER_INGREDIENTS, Msg.HEALER_BOTH, Msg.HEALER_PAYMENT, Msg.HEALER_READY, Msg.HEALER_OFFER, Msg.HEALER_PAID,
            Msg.HEALER_LOCK, Msg.HEALER_VICTORY, Msg.EDGARD_DARKNESS, Msg.HEALER_GUIDE};
        assertEquals(14, passLines.length);
        for (Language language : Language.values()) {
            for (Msg message : passLines) {
                int lines = assertLineFits(font, message.t(language), language, message);
                assertTrue(lines <= 2, () -> language + " " + message + " takes " + lines + " lines, D134 says at most two");
            }
        }
        font.dispose();
    }

    /**
     * D81 (#341): the healer's first-death line takes exactly two of the text box's four lines in every language, at the
     * 18 px baked font in the 556 px box, right-aligned as the healer's lines are. Measured 2026-11-16: two in all five.
     */
    @Test void theFirstDeathLineTakesTwoOfTheFourLinesInEveryLanguage() {
        var fontData = new BitmapFont.BitmapFontData(Gdx.files.internal("fonts/generated/QuestSquare-" + FontSizes.DIALOGUE + ".fnt"), false);
        var font = new BitmapFont(fontData, new TextureRegion(new MetricsTexture()), false);
        assertEquals(4, HealerDialogueView.TEXT_LINES);
        for (Language language : Language.values()) {
            assertEquals(2, assertLineFits(font, Msg.HEALER_FIRST_DEATH.t(language), language, Msg.HEALER_FIRST_DEATH),
                language + " HEALER_FIRST_DEATH");
        }
        font.dispose();
    }

    private static BitmapFont dialogueFont(int size) {
        var fontData = new BitmapFont.BitmapFontData(Gdx.files.internal("fonts/generated/QuestSquare-" + size + ".fnt"), false);
        return new BitmapFont(fontData, new TextureRegion(new MetricsTexture()), false);
    }

    /**
     * D137 (#491): on a small picture the dialogue is the 20 px baked font. Every spoken line in five languages still fits the 556 px
     * box in at most three of its four lines (one stays spare, as at 18 px; measured 2026-11-24: the longest line takes three), and
     * four lines of 20 px (80) sit inside the taller box ({@code BOX_H_LARGE} 90, ink to y 313 of 316).
     */
    @Test void atTheLargeSizeEveryLocalizedLineStillFitsTheTextBoxWithOneLineSpare() {
        assertEquals(4 * 20f, HealerDialogueView.TEXT_H_LARGE, .001f);
        var font = dialogueFont(FontSizes.DIALOGUE_LARGE);
        int mostLines = 0;
        for (Language language : Language.values()) for (Msg message : Msg.values()) {
            if (!isSpokenDialogueLine(message)) continue;
            mostLines = Math.max(mostLines, assertLineFits(font, message.t(language), language, message));
        }
        assertEquals(3, mostLines, "the longest line wraps to three lines at 20 px too");
        assertTrue(HealerDialogueView.TEXT_Y + HealerDialogueView.TEXT_H_LARGE <= HealerDialogueView.BOX_Y + HealerDialogueView.BOX_H_LARGE - 2f, "four 20 px lines inside the ink");
        assertTrue(HealerDialogueView.HINT_Y_LARGE + FontSizes.DIALOGUE_LARGE <= HealerDialogueView.SCREEN_H - 6f, "the prompt clears the page border");
        assertTrue(HealerDialogueView.BOX_Y + HealerDialogueView.BOX_H_LARGE + HealerDialogueView.BOX_BAND_OUT < HealerDialogueView.HINT_Y_LARGE, "the prompt sits under the box");
        font.dispose();
    }

    /**
     * D134 at the large size: the fourteen tale-pass lines take at most two lines at 18 px; at 20 px one of them, the German offer,
     * takes three (still one of four lines spare). The exception is pinned, so no other line can grow into it unseen.
     */
    @Test void atTheLargeSizeOnlyTheGermanOfferOfTheTalePassTakesThreeLines() {
        var font = dialogueFont(FontSizes.DIALOGUE_LARGE);
        Msg[] passLines = {Msg.HEALER_GREETING, Msg.EDGARD_REPLY, Msg.HEALER_RETURN_GREETING, Msg.HEALER_RETURN_CHECK,
            Msg.HEALER_INGREDIENTS, Msg.HEALER_BOTH, Msg.HEALER_PAYMENT, Msg.HEALER_READY, Msg.HEALER_OFFER, Msg.HEALER_PAID,
            Msg.HEALER_LOCK, Msg.HEALER_VICTORY, Msg.EDGARD_DARKNESS, Msg.HEALER_GUIDE};
        for (Language language : Language.values()) for (Msg message : passLines) {
            int lines = assertLineFits(font, message.t(language), language, message);
            boolean exception = language == Language.GERMAN && message == Msg.HEALER_OFFER;
            assertEquals(exception ? 3 : 2, Math.max(lines, 2) , language + " " + message + " takes " + lines + " lines at 20 px");
        }
        font.dispose();
    }

    /** D81 at the large size: the first-death line still takes two of the four lines in every language (measured 2026-11-24). */
    @Test void atTheLargeSizeTheFirstDeathLineStillTakesTwoLines() {
        var font = dialogueFont(FontSizes.DIALOGUE_LARGE);
        for (Language language : Language.values())
            assertEquals(2, assertLineFits(font, Msg.HEALER_FIRST_DEATH.t(language), language, Msg.HEALER_FIRST_DEATH), language + " HEALER_FIRST_DEATH");
        font.dispose();
    }

    /**
     * The typewriter's glyph map covers every glyph of every spoken line in all five languages, laid out as the view lays it
     * out: the whole line reveals at its last letter, none shows before the first. Its starting room is above the longest
     * real line, so it never grows in play; a glyph that is not in the text fails loudly instead of hiding the rest.
     */
    @Test void theGlyphMapCoversEveryLineAndFailsLoudly() {
        var fontData = new BitmapFont.BitmapFontData(Gdx.files.internal("fonts/generated/QuestSquare-" + FontSizes.DIALOGUE + ".fnt"), false);
        var font = new BitmapFont(fontData, new TextureRegion(new MetricsTexture()), false);
        com.badlogic.gdx.utils.IntArray map = new com.badlogic.gdx.utils.IntArray();
        int longestGlyphs = 0, longestLetters = 0;
        for (Language language : Language.values()) for (Msg message : Msg.values()) {
            if (!isSpokenDialogueLine(message)) continue;
            String text = message.t(language);
            boolean edgard = message.name().startsWith("EDGARD_");
            GlyphLayout layout = new GlyphLayout(font, text, Color.WHITE, HealerDialogueView.TEXT_W, edgard ? Align.left : Align.right, true);
            HealerDialogueView.mapGlyphs(layout, text, map);
            int glyphs = 0;
            for (int r = 0; r < layout.runs.size; r++) glyphs += layout.runs.get(r).glyphs.size;
            assertEquals(glyphs, map.size, language + " " + message + ": every glyph is mapped");
            assertEquals(glyphs, HealerDialogueView.glyphsFor(map, text.length()), language + " " + message + ": the whole line reveals");
            assertEquals(0, HealerDialogueView.glyphsFor(map, 0));
            longestGlyphs = Math.max(longestGlyphs, glyphs);
            longestLetters = Math.max(longestLetters, text.length());
        }
        assertEquals(257, longestLetters, "the longest spoken line (de HEALER_TALE_FIREGIFT); update LINE_GLYPHS' note if it changes");
        assertTrue(HealerDialogueView.LINE_GLYPHS >= longestGlyphs, "the glyph map never grows in play: " + longestGlyphs);
        GlyphLayout other = new GlyphLayout(font, "abc");
        assertThrows(IllegalStateException.class, () -> HealerDialogueView.mapGlyphs(other, "xyz", map));
        font.dispose();
    }

    /** The speaker follows the script line by line: the lit window, the notch and the text side all name the same speaker. */
    @Test void theSpeakerFollowsEveryScript() {
        assertSpeakers(HealerDialogue.tutorialStart(), "HEHHHHHHE"); // #338: the fairy gift is the healer's
        assertSpeakers(HealerDialogue.tutorialEnd(), "HEHH");
        assertSpeakers(HealerDialogue.afterVictory(), "HEH");
        assertSpeakers(new HealerDialogue(false, false, false, false, false), "HEHH");
        assertSpeakers(new HealerDialogue(true, true, true, true, false), "HEH");
        assertSpeakers(new HealerDialogue(true, true, true, true, false, true), "HHH");
        assertSpeakers(new HealerDialogue(false, false, false, false, true), "HH");
    }

    private static void assertSpeakers(HealerDialogue dialogue, String expected) {
        Language language = Language.ENGLISH;
        dialogue.setInstantText(true);
        for (int i = 0; i < expected.length(); i++) {
            boolean edgard = expected.charAt(i) == 'E';
            assertEquals(edgard, dialogue.edgardSpeaking(), "line " + i + " " + dialogue.currentLine());
            assertEquals(edgard, dialogue.currentLine().name().startsWith("EDGARD_"), "line " + i + " key");
            float notch = HealerDialogueView.speakerCentreX(dialogue.edgardSpeaking());
            float window = edgard ? HealerDialogueView.EDGARD_WINDOW_X : HealerDialogueView.HEALER_WINDOW_X;
            assertTrue(notch > window && notch < window + HealerDialogueView.WINDOW, "the notch points at the speaker, line " + i);
            if (i + 1 < expected.length()) dialogue.advance(language);
        }
        Msg last = dialogue.currentLine();
        dialogue.advance(language);
        assertSame(last, dialogue.currentLine(), "the script has exactly " + expected.length() + " lines");
    }

    /** D78: the screen opens in at most 0.3 s, letters wait for it, and paused time (no update) does not open it. */
    @Test void theScreenOpensInAtMostAThirdOfASecondBeforeAnyLetter() {
        assertTrue(HealerDialogue.OPEN_SECONDS <= .3f);
        HealerDialogue dialogue = HealerDialogue.tutorialStart();
        assertEquals(0f, dialogue.openProgress());
        dialogue.update(HealerDialogue.OPEN_SECONDS / 2f);
        assertEquals(.5f, dialogue.openProgress(), .001f);
        assertEquals(0, dialogue.visibleCharacters(Language.ENGLISH));
        assertEquals(0f, dialogue.lineTime());
        dialogue.update(HealerDialogue.OPEN_SECONDS); // overshoots the opening by half of it: that half reveals letters
        assertEquals(1f, dialogue.openProgress(), .001f);
        assertEquals(HealerDialogue.OPEN_SECONDS / 2f, dialogue.lineTime(), .001f);
        assertTrue(dialogue.visibleCharacters(Language.ENGLISH) > 0);
        dialogue.advance(Language.ENGLISH);
        dialogue.advance(Language.ENGLISH);
        assertEquals(0f, dialogue.lineTime(), "a new line restarts the glint");
        assertEquals(1f, dialogue.openProgress(), .001f, "the screen opens once, not per line");
    }

    /**
     * D78 review: the view draws no text until the screen is open, so presses and taps during the opening act on nothing:
     * with instant text off a press does not skip the typewriter; with it on, quick presses do not advance unseen lines, and
     * a tap on a choice does nothing either. After the opening the same input works as before.
     */
    @Test void inputDuringTheOpeningIsIgnored() {
        for (boolean instant : new boolean[]{false, true}) {
            Language language = Language.ENGLISH;
            HealerDialogue tale = HealerDialogue.tutorialStart();
            tale.setInstantText(instant);
            tale.update(HealerDialogue.OPEN_SECONDS / 3f);
            assertNull(tale.interact(language, true, 0, true, -1));
            assertNull(tale.interact(language, true, 1, true, 0));
            assertEquals(Msg.HEALER_TALE_ROAD, tale.currentLine(), "instant text " + instant + ": no line skipped unseen");
            assertEquals(instant ? tale.completeText(language).length() : 0, tale.visibleCharacters(language),
                "instant text " + instant + ": the typewriter is not skipped");
            tale.update(HealerDialogue.OPEN_SECONDS);
            assertTrue(tale.openProgress() >= 1f);
            tale.interact(language, true, 0, true, -1);
            if (!instant) {
                assertTrue(tale.fullyRevealed(language), "after the opening the first press reveals the line");
                tale.interact(language, true, 0, true, -1);
            }
            assertEquals(Msg.EDGARD_TALE_NO_TIME, tale.currentLine(), "after the opening a press advances");

            HealerDialogue stop = new HealerDialogue(false, false, false, false, true);
            stop.setInstantText(instant);
            for (int i = 0; i < 10 && !stop.choosing(); i++) stop.advance(language);
            assertTrue(stop.choosing());
            assertNull(stop.interact(language, false, 0, true, 0), "no choice is taken before the screen is open");
            stop.update(HealerDialogue.OPEN_SECONDS);
            assertEquals(HealerDialogue.Choice.FIGHT, stop.interact(language, false, 0, true, 0));
        }
    }

    /** The speaker's bob is 1 px at most and goes up (Y down: a lift is subtracted). */
    @Test void theSpeakerBobsOnePixel() {
        int lifted = 0;
        for (int i = 0; i < 240; i++) {
            int lift = HealerDialogueView.bobLift(i / 60f);
            assertTrue(lift == 0 || lift == 1);
            lifted += lift;
        }
        assertTrue(lifted > 60 && lifted < 180, "lifted about half the time: " + lifted);
        assertTrue(HealerDialogueView.PORTRAIT_INSET - HealerDialogueView.BAND >= 1f, "1 px of air above the portrait for the lift");
    }

    /** The screen's parts stay on screen and apart (logical px, Y down). */
    @Test void theScreenPartsDoNotOverlap() {
        float choicesBottom = HealerDialogueView.CHOICE_Y + 2 * HealerDialogueView.CHOICE_STEP - (HealerDialogueView.CHOICE_STEP - HealerDialogueView.CHOICE_H);
        assertTrue(choicesBottom < HealerDialogueView.THREAD_Y, "the choices clear the thread art");
        assertTrue(HealerDialogueView.WINDOW_Y + HealerDialogueView.WINDOW < HealerDialogueView.PLATE_Y, "plates under the windows");
        assertTrue(HealerDialogueView.THREAD_Y + HealerDialogueView.THREAD_H <= HealerDialogueView.PLATE_Y, "the thread hangs above the plates");
        assertTrue(HealerDialogueView.PLATE_Y + HealerDialogueView.PLATE_H <= HealerDialogueView.NOTCH_Y, "the notch sits under the plate");
        assertTrue(HealerDialogueView.TEXT_Y + HealerDialogueView.TEXT_H <= HealerDialogueView.BOX_Y + HealerDialogueView.BOX_H - 2f, "four lines inside the ink");
        assertTrue(HealerDialogueView.HINT_Y + FontSizes.DIALOGUE <= HealerDialogueView.SCREEN_H - 6f, "the prompt clears the page border");
        assertTrue(HealerDialogueView.CREST_Y >= 6f, "crests clear the page border");
        assertEquals(HealerDialogueView.THREAD_Y0, HealerDialogueView.threadY(HealerDialogueView.THREAD_X0), .001f);
        assertEquals(HealerDialogueView.THREAD_Y1, HealerDialogueView.threadY(HealerDialogueView.THREAD_X1), .001f);
        assertTrue(HealerDialogueView.THREAD_X0 >= HealerDialogueView.EDGARD_WINDOW_X + HealerDialogueView.WINDOW, "the span starts at Edgard's window");
        assertTrue(HealerDialogueView.THREAD_X1 <= HealerDialogueView.HEALER_WINDOW_X, "and ends at the healer's");
    }

    private static boolean isSpokenDialogueLine(Msg message) {
        if (message == Msg.HEALER_RETURN || message == Msg.HEALER_BREW || message == Msg.HEALER_FIGHT
            || message == Msg.HEALER_HINT) return false;
        return message.name().startsWith("HEALER_") || message.name().startsWith("EDGARD_");
    }

    private static int assertLineFits(BitmapFont font, String text, Language language, Msg message) {
        int alignment = message.name().startsWith("EDGARD_") ? Align.left : Align.right;
        float width = HealerDialogueView.TEXT_W;
        GlyphLayout layout = new GlyphLayout(font, text, Color.WHITE, width, alignment, true);
        assertTrue(layout.width <= width + 1f,
            () -> language + " " + message + " exceeds the text box: " + layout.width + " > " + width);
        int lines = layout.runs.size == 0 ? 0 : 1;
        for (int i = 1; i < layout.runs.size; i++) if (layout.runs.get(i).y != layout.runs.get(i - 1).y) lines++;
        int count = lines;
        assertTrue(count <= HealerDialogueView.TEXT_LINES - 1,
            () -> language + " " + message + " takes " + count + " lines; one of the " + HealerDialogueView.TEXT_LINES + " stays spare for #341");
        return count;
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
