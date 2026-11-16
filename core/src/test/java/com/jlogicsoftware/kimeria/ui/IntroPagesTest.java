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
            IntroPages pages = IntroPages.split(text, p -> overlay.bodyHeight(p, WRAP));
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
            IntroPages pages = IntroPages.split(story(story, language), p -> overlay.bodyHeight(p, WRAP));
            for (int page = 0; page < pages.pageCount(); page++) {
                String where = story + " " + language.code() + " page " + (page + 1);
                assertEquals(IntroPages.TEXT_TOP, pages.top(pages.first(page)), where);
                assertTrue(pages.bottom(page) <= IntroPages.TEXT_TOP + IntroPages.MAX_HEIGHT, where + " bottom " + pages.bottom(page));
                assertTrue(pages.bottom(page) < SCREEN_HEIGHT, where);
                for (int i = pages.first(page) + 1; i < pages.end(page); i++)
                    assertEquals(pages.top(i - 1) + pages.height(i - 1) + IntroPages.PARAGRAPH_GAP, pages.top(i), 0.001f, where);
            }
        }
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
