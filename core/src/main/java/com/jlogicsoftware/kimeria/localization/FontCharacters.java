package com.jlogicsoftware.kimeria.localization;

import java.util.Locale;
import java.util.function.Function;

/** Single charset contract for the font baker and shipped-font validation. */
public final class FontCharacters {
    public static final int[] SIZES = {13, 15, 20, 30};
    // Retain the complete alphabets even when a current translation does not use every letter.
    private static final String ALPHABETS = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻäöüÄÖÜß"
        + "АБВГДЕЁЖЗІЙКЛМНОПРСТУЎФХЦЧШЫЬЭЮЯабвгдеёжзійклмнопрстуўфхцчшыьэюя";

    private FontCharacters() { }

    /** Neither bundled TTF has capital sharp S. Uppercase German uses SS intentionally. */
    public static String uppercase(String text) {
        return text.replace("ẞ", "SS").replace("ß", "ss").toUpperCase(Locale.ROOT);
    }

    public static String required(Function<Language, String> source) {
        boolean[] included = new boolean[65536];
        // Digits, key names, selection/checklist markers, x10 and debug punctuation.
        for (int c = 32; c < 127; c++) included[c] = true;
        include(included, ALPHABETS);
        for (Language language : Language.values()) {
            include(included, language.nativeName());
            include(included, uppercase(language.nativeName()));
            for (String value : Catalog.parse(language.code(), source.apply(language)).values()) {
                include(included, value);
                include(included, uppercase(value));
            }
        }
        StringBuilder result = new StringBuilder();
        for (int c = 32; c < included.length; c++) if (included[c]) result.append((char) c);
        return result.toString();
    }

    private static void include(boolean[] included, String text) {
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) >= 32) included[text.charAt(i)] = true;
    }
}
