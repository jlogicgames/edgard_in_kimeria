package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Catalog;
import com.jlogicsoftware.kimeria.testsupport.MetricsFont;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The page split of the opening and the ending (#336), measured with the real body font's metrics in every language. */
class IntroPagesTest {
    private static final float WRAP = 640f - IntroScreen.TEXT_MARGIN * 2f;
    private static final float SCREEN_HEIGHT = 360f;

    @BeforeAll static void natives() { GdxNativesLoader.load(); }

    private static final String[] STORIES = {"INTRO_STORY", "ENDING_STORY"};

    /** The shipped catalog text, read straight from the file so the process-wide localization cache cannot hide a bad load. */
    private static String story(String key, Language language) {
        String source = new HeadlessFiles().internal("localization/" + language.code() + ".json").readString("UTF-8");
        String text = Catalog.parse(language.code(), source).get(key);
        assertTrue(text.length() > 60, key + " " + language.code() + " must be the real story text");
        return text;
    }

    /** The ending (#416, D95) is split under the Kimeria arms, exactly as {@code IntroScreen.open} does in the game. */
    private static boolean withArms(String story) { return story.equals("ENDING_STORY"); }

    private static IntroPages split(String story, String text, Overlay overlay) {
        return IntroScreen.split(text, p -> overlay.bodyHeight(p, WRAP), withArms(story));
    }

    /**
     * The ending as #372 will ship it (the healer tells it): the author-approved EN, and a German written long on purpose
     * (337 chars, the longest language) until #372 brings the real translations. The page must hold both under the arms.
     */
    private static final String HEALER_ENDING_EN = "Hear how my tale ends. From that forest Edgard walked a long road, and I guided him as I"
        + " had promised. He defeated every enemy, and not one monster was left in Kimeria.\n\nThen the sun shone again, and the"
        + " sleeping kingdom woke to the light. So ends my tale of Kimeria.\n\nTHE END";
    private static final String HEALER_ENDING_DE_LONG = "Höre, wie meine Geschichte endet. Von jenem Wald aus ging Edgard einen langen Weg,"
        + " und ich führte ihn, wie ich es ihm versprochen hatte. Er besiegte alle Feinde, und in Kimeria blieb kein einziges Ungeheuer"
        + " übrig.\n\nDa schien die Sonne wieder, und das schlafende Königreich erwachte zum Licht. So endet meine Geschichte von"
        + " Kimeria.\n\nENDE";

    private static Overlay overlay() {
        Assets assets = new Assets() {
            @Override public BitmapFont font(int size) { return new MetricsFont(size); }
        };
        return new Overlay(assets, 640, 360);
    }

    private static String rebuilt(IntroPages pages) {
        StringBuilder text = new StringBuilder();
        for (int page = 0; page < pages.pageCount(); page++)
            for (int i = pages.first(page); i < pages.end(page); i++) text.append(text.length() == 0 ? "" : "\n\n").append(pages.paragraph(i));
        return text.toString();
    }

    @Test void everyPageHoldsWholeParagraphsAndTogetherTheyAreTheWholeTextInEveryLanguage() {
        Overlay overlay = overlay();
        for (String story : STORIES) for (Language language : Language.values()) {
            String text = story(story, language);
            IntroPages pages = split(story, text, overlay);
            String where = story + " " + language.code();
            assertEquals(text, rebuilt(pages), where + ": the pages must carry the text unchanged");
            assertTrue(pages.pageCount() >= 1, where);
            int expected = 0;
            for (int page = 0; page < pages.pageCount(); page++) {
                assertEquals(expected, pages.first(page), where + ": no paragraph skipped or repeated");
                assertTrue(pages.end(page) > pages.first(page), where + ": no empty page");
                expected = pages.end(page);
                for (int i = pages.first(page); i < pages.end(page); i++) System.out.printf("  para %d h=%.1f chars=%d%n", i, pages.height(i), pages.paragraph(i).length());
                System.out.printf("PAGES %s page %d: paragraphs %d-%d, bottom %.1f of %.1f%n", where, page + 1,
                    pages.first(page) + 1, pages.end(page), pages.bottom(page), IntroPages.TEXT_TOP + IntroPages.MAX_HEIGHT);
            }
            assertEquals(pages.paragraphCount(), expected, where);
        }
    }

    @Test void noPageOverflowsTheScreenInAnyLanguageAndParagraphsReadTopToBottom() {
        Overlay overlay = overlay();
        for (String story : STORIES) for (Language language : Language.values()) {
            IntroPages pages = split(story, story(story, language), overlay);
            for (int page = 0; page < pages.pageCount(); page++) {
                String where = story + " " + language.code() + " page " + (page + 1);
                assertEquals(withArms(story) ? IntroScreen.ENDING_TEXT_TOP : IntroPages.TEXT_TOP, pages.top(pages.first(page)), where);
                assertTrue(pages.bottom(page) <= IntroPages.PAGE_BOTTOM, where + " bottom " + pages.bottom(page));
                assertTrue(pages.bottom(page) < SCREEN_HEIGHT, where);
                for (int i = pages.first(page) + 1; i < pages.end(page); i++)
                    assertEquals(pages.top(i - 1) + pages.height(i - 1) + IntroPages.PARAGRAPH_GAP, pages.top(i), 0.001f, where);
            }
        }
    }

    /**
     * #416 (D95): the ending is one page under the arms in every language, today's text and the healer's ending of #372
     * alike, and its text starts below the arms. Measured with the real body font: today's ending ends at 244 in every
     * language, #372's EN at 237 and the long DE at 253, of {@link IntroPages#PAGE_BOTTOM} (304).
     */
    @Test void theEndingIsOnePageUnderTheArmsWithTodaysTextAndTheHealersEnding() {
        Overlay overlay = overlay();
        float armsBottom = IntroScreen.ARMS_TOP + KimeriaArms.HEIGHT * IntroScreen.ARMS_SCALE;
        assertTrue(armsBottom < IntroScreen.ENDING_TEXT_TOP, "the arms sit above the ending's text");
        java.util.Map<String, String> endings = new java.util.LinkedHashMap<>();
        for (Language language : Language.values()) endings.put(language.code(), story("ENDING_STORY", language));
        endings.put("#372 en", HEALER_ENDING_EN);
        endings.put("#372 de (long)", HEALER_ENDING_DE_LONG);
        for (java.util.Map.Entry<String, String> ending : endings.entrySet()) {
            IntroPages pages = split("ENDING_STORY", ending.getValue(), overlay);
            System.out.printf("ENDING under the arms %s: bottom %.1f of %.1f%n", ending.getKey(), pages.bottom(0), IntroPages.PAGE_BOTTOM);
            assertEquals(1, pages.pageCount(), ending.getKey() + ": the ending must stay one page");
            assertEquals(IntroScreen.ENDING_TEXT_TOP, pages.top(0), ending.getKey());
            assertTrue(pages.bottom(0) <= IntroPages.PAGE_BOTTOM, ending.getKey() + " bottom " + pages.bottom(0));
        }
    }

    /** Word stems that bring the light back, per language: light or sun (lower case; "światł" is light, "świat" is world). */
    private static final java.util.Map<String, String[]> LIGHT_WORDS = java.util.Map.of(
        "en", new String[]{"light", "sun"}, "uk", new String[]{"світл", "сонц"}, "pl", new String[]{"światł", "słońc"},
        "de", new String[]{"licht", "sonne"}, "be", new String[]{"святл", "сонц"});

    private static boolean speaksOfLight(String paragraph, String language) {
        String text = paragraph.toLowerCase(java.util.Locale.ROOT);
        for (String stem : LIGHT_WORDS.get(language)) if (text.contains(stem)) return true;
        return false;
    }

    /**
     * The ending's structure (#416, D95), which the sun's timing relies on: it closes with exactly one closing line in
     * capitals (THE END), the paragraph before it is the first to speak of light or the sun, and every language has the
     * same paragraphs as English. A translation or #372 that merges, adds or moves a paragraph fails here, not in the game.
     */
    private static void assertEndingStructure(String where, String text, String language, int paragraphs) {
        String[] parts = text.split("\n\n");
        assertEquals(paragraphs, parts.length, where + ": the same paragraphs as English");
        assertTrue(IntroScreen.isClosingLine(parts[parts.length - 1]), where + ": the last paragraph is the closing line");
        for (int i = 0; i < parts.length - 1; i++)
            assertFalse(IntroScreen.isClosingLine(parts[i]), where + ": only one closing line, at the end: " + parts[i]);
        IntroPages pages = IntroPages.split(text, p -> 7f);
        int light = IntroScreen.lightParagraph(pages);
        assertEquals(parts.length - 2, light, where + ": the sun lights with the paragraph before the closing line");
        assertTrue(speaksOfLight(parts[light], language), where + ": the light paragraph brings the light back: " + parts[light]);
        for (int i = 0; i < light; i++)
            assertFalse(speaksOfLight(parts[i], language), where + ": the light must not come back before the sun lights: " + parts[i]);
    }

    @Test void theSunLightsWithTheParagraphThatBringsTheLightBackInEveryShippedLanguage() {
        int paragraphs = story("ENDING_STORY", Language.ENGLISH).split("\n\n").length;
        for (Language language : Language.values())
            assertEndingStructure("ENDING_STORY " + language.code(), story("ENDING_STORY", language), language.code(), paragraphs);
        assertTrue(story("ENDING_STORY", Language.ENGLISH).split("\n\n")[paragraphs - 2].startsWith("Light has returned"));
    }

    @Test void theHealersEndingOf372KeepsTheSameStructure() {
        assertEndingStructure("#372 en", HEALER_ENDING_EN, "en", 3);
        assertEndingStructure("#372 de (long)", HEALER_ENDING_DE_LONG, "de", 3);
        IntroPages healer = IntroPages.split(HEALER_ENDING_EN, p -> 7f);
        assertTrue(healer.paragraph(IntroScreen.lightParagraph(healer)).startsWith("Then the sun shone again"));
    }

    @Test void theLightParagraphIsFoundFromTheTextNotByACount() {
        assertEquals(1, IntroScreen.lightParagraph(IntroPages.split("a\n\nsun\n\nTHE END", p -> 7f)));
        assertEquals(1, IntroScreen.lightParagraph(IntroPages.split("a\n\nsun\n\nTHE END\n\nFIN", p -> 7f)), "two closing lines");
        assertEquals(2, IntroScreen.lightParagraph(IntroPages.split("a\n\nb\n\nsun", p -> 7f)), "no closing line");
        assertTrue(IntroScreen.isClosingLine("КІНЕЦЬ"));
        assertFalse(IntroScreen.isClosingLine("Світло повернулося у світ."));
        assertFalse(IntroScreen.isClosingLine("..."));
    }

    @Test void aShiftedTopKeepsThePageBottomAndShrinksTheRoom() {
        float top = IntroScreen.ENDING_TEXT_TOP, room = IntroPages.PAGE_BOTTOM - top;
        IntroPages pages = IntroPages.split("a\n\nb", p -> room, top);
        assertEquals(2, pages.pageCount(), "a paragraph that fills the room leaves none for the next");
        assertEquals(top, pages.top(1));
        assertEquals(IntroPages.PAGE_BOTTOM, pages.bottom(0), 0.001f);
        assertThrows(IllegalStateException.class, () -> IntroPages.split("x", p -> room + 1f, top));
    }

    @Test void aParagraphTallerThanAPageIsAnErrorNotASilentOverflow() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> IntroPages.split("short\n\ntoo tall", p -> p.startsWith("too") ? IntroPages.MAX_HEIGHT + 1f : 40f));
        assertTrue(error.getMessage().contains("too tall"));
    }

    @Test void packingIsGreedyAndStartsANewPageExactlyWhenTheNextParagraphWouldNotFit() {
        float h = (IntroPages.MAX_HEIGHT - IntroPages.PARAGRAPH_GAP) / 2f; // two paragraphs fill a page exactly
        IntroPages pages = IntroPages.split("a\n\nb\n\nc", p -> h);
        assertEquals(2, pages.pageCount());
        assertEquals(2, pages.end(0));
        assertEquals(3, pages.end(1));
        assertEquals(IntroPages.TEXT_TOP, pages.top(2));
        assertEquals(IntroPages.TEXT_TOP + IntroPages.MAX_HEIGHT, pages.bottom(0), 0.001f);
    }
}
