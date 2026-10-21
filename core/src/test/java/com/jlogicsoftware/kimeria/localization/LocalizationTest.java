package com.jlogicsoftware.kimeria.localization;

import com.badlogic.gdx.backends.headless.HeadlessFiles;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalizationTest {
    private String source(Language language) {
        return new HeadlessFiles().internal("localization/" + language.code() + ".json").readString("UTF-8");
    }

    @Test void everyShippedCatalogIsCompleteAndValid() {
        Map<String, String> english = Catalog.parse("en", source(Language.ENGLISH));
        for (Language language : Language.values()) {
            var catalog = Catalog.parse(language.code(), source(language));
            assertEquals(Msg.values().length, catalog.size(), language.code());
            assertEquals(java.util.List.of(), CatalogValidation.errors(language, english, catalog), language.code());
            assertTrue(catalog.get("INTRO_STORY").contains("\n\n"), language.code());
        }
    }

    @Test void strictReaderPreservesUnicodeEscapesAndMultilineText() {
        assertEquals("Żółć\n\nЎІЁ \" / \\", Catalog.parse("pl", "{\"TITLE\":\"Żółć\\n\\n\\u040EІЁ \\\" \\/ \\\\\"}").get("TITLE"));
        assertEquals(Map.of(), Catalog.parse("en", " \n {} \t"));
    }

    @Test void malformedAndDuplicateResourcesReportLanguageAndKey() {
        for (String invalid : new String[]{"{\"TITLE\":\"a\",\"TITLE\":\"b\"}",
            "{\"TITLE\":42}", "{\"TITLE\":null}", "{\"TITLE\":\"bad\\q\"}", "{\"TITLE\":\"bad\\u0xyz\"}",
            "{\"TITLE\":\"unterminated}", "{\"TITLE\":\"raw\nnewline\"}", "{\"TITLE\":\"ok\"} trailing"}) {
            String error = assertThrows(IllegalArgumentException.class, () -> Catalog.parse("de", invalid)).getMessage();
            assertTrue(error.contains("de [TITLE]"), error);
            assertTrue(error.contains("at "), error);
        }
        for (String invalid : new String[]{"[]", "{TITLE:\"a\"}", "{\"TITLE\":\"a\",}", "//comment\n{}"})
            assertThrows(IllegalArgumentException.class, () -> Catalog.parse("be", invalid), invalid);
    }

    @Test void validatorFindsMissingUnknownEmptyAndMismatchedOrBrokenPlaceholders() {
        Map<String, String> english = Catalog.parse("en", source(Language.ENGLISH));
        Map<String, String> broken = new LinkedHashMap<>(english);
        broken.remove("TITLE"); broken.put("UNKNOWN_KEY", "something"); broken.put("PLAY", "  \n");
        broken.put("FOREST_COINS_COUNT", "{count}/{wrong}"); broken.put("HEALER", "{oops");
        String errors = String.join("\n", CatalogValidation.errors(Language.BELARUSIAN, english, broken));
        for (String expected : new String[]{"be [TITLE]: missing", "be [UNKNOWN_KEY]: unknown", "be [PLAY]: empty",
            "be [FOREST_COINS_COUNT]: placeholder mismatch", "be [HEALER]: Unclosed"}) assertTrue(errors.contains(expected), errors);
        broken.put("HEALER", "\uFFFD");
        assertTrue(String.join("\n", CatalogValidation.errors(Language.BELARUSIAN, english, broken)).contains("U+FFFD"));
        assertNotEquals(Catalog.placeholders("{count}"), Catalog.placeholders("{count} {count}"));
    }

    @Test void runtimeFallsBackForMissingEmptyAndBrokenCatalogsAndReportsOnce() {
        ArrayList<String> errors = new ArrayList<>();
        Localization translations = new Localization(language -> switch (language) {
            case ENGLISH -> "{\"TITLE\":\"English title\",\"PLAY\":\"Play\"}";
            case UKRAINIAN -> "{\"TITLE\":\"Українська назва\",\"PLAY\":\" \"}";
            case GERMAN -> "{broken";
            case POLISH, BELARUSIAN -> "{}";
        }, errors::add);
        assertEquals("Українська назва", translations.text(Language.UKRAINIAN, "TITLE"));
        assertEquals("Play", translations.text(Language.UKRAINIAN, "PLAY"));
        assertEquals("English title", translations.text(Language.GERMAN, "TITLE"));
        assertEquals("English title", translations.text(Language.POLISH, "TITLE"));
        int diagnostics = errors.size();
        assertEquals("English title", translations.text(Language.POLISH, "TITLE"));
        assertEquals(diagnostics, errors.size());
        assertEquals("English title", translations.text(null, "TITLE"));
        assertEquals("[UNKNOWN]", translations.text(Language.ENGLISH, "UNKNOWN"));
        assertTrue(errors.stream().anyMatch(error -> error.contains("de [<resource>]")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("uk [PLAY]")));
    }

    @Test void namedFormattingAllowsTranslationReorderingWithoutRecursiveReplacement() {
        assertEquals("6 insgesamt, 3 gefunden", Catalog.format("{total} insgesamt, {count} gefunden", "count", "3", "total", "6"));
        assertEquals("{total}/6", Catalog.format("{count}/{total}", "count", "{total}", "total", "6"));
        assertThrows(IllegalArgumentException.class, () -> Catalog.format("{count}", "wrong", "2"));
        assertThrows(IllegalArgumentException.class, () -> Catalog.format("{count}", "count"));
        assertThrows(IllegalArgumentException.class, () -> Catalog.format("{count}", "count", "2", "count", "3"));
    }

    @Test void charsetIncludesCurrentTextUppercaseNativeNamesAndReservedAlphabets() {
        String characters = FontCharacters.required(this::source);
        for (char c : "ąćęłńóśźżĄĆĘŁŃÓŚŹŻäöüÄÖÜßІіЎўЁё".toCharArray()) assertTrue(characters.indexOf(c) >= 0, "Missing " + c);
        assertEquals("STRASSE GROSS SS", FontCharacters.uppercase("Straße groß ẞ"));
        assertFalse(characters.contains("ẞ"));
        assertEquals(' ', characters.charAt(0));
    }
}
