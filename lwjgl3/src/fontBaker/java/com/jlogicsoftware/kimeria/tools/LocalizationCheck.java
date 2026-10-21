package com.jlogicsoftware.kimeria.tools;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.localization.Catalog;
import com.jlogicsoftware.kimeria.localization.CatalogValidation;
import com.jlogicsoftware.kimeria.localization.FontCharacters;
import com.jlogicsoftware.kimeria.localization.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Offline release gate: strict catalogs, source font and every shipped bitmap size/page. */
public final class LocalizationCheck {
    private LocalizationCheck() { }

    public static void main(String[] args) {
        GdxNativesLoader.load();
        Gdx.files = new HeadlessFiles();
        catalogs();
        String characters = FontCharacters.required(LocalizationCheck::source);
        sourceGlyphs(characters);
        bitmapGlyphs(characters);
        System.out.println("Localization validated: " + Language.values().length + " languages, "
            + com.jlogicsoftware.kimeria.localization.Msg.values().length + " keys, " + characters.length()
            + " required glyphs at every shipped size.");
    }

    static String source(Language language) {
        return Gdx.files.internal("localization/" + language.code() + ".json").readString("UTF-8");
    }

    static void catalogs() {
        List<String> errors = new ArrayList<>();
        Map<String, String> english = Catalog.parse("en", source(Language.ENGLISH));
        for (Language language : Language.values()) {
            try {
                errors.addAll(CatalogValidation.errors(language, english, Catalog.parse(language.code(), source(language))));
            } catch (RuntimeException malformed) { errors.add(language.code() + " [<resource>]: " + malformed.getMessage()); }
        }
        require(errors);
    }

    static void sourceGlyphs(String characters) {
        FreeTypeFontGenerator font = new FreeTypeFontGenerator(Gdx.files.internal("fonts/QuestSquare.ttf"));
        try {
            List<String> errors = new ArrayList<>();
            for (int i = 0; i < characters.length(); i++) if (!font.hasGlyph(characters.charAt(i)))
                errors.add("QuestSquare.ttf: missing " + describe(characters.charAt(i)) + " " + context(characters.charAt(i)));
            require(errors);
        } finally { font.dispose(); }
    }

    static void bitmapGlyphs(String characters) {
        List<String> errors = new ArrayList<>();
        for (int size : FontCharacters.SIZES) {
            String path = "fonts/generated/QuestSquare-" + size + ".fnt";
            BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(Gdx.files.internal(path), false);
            List<Pixmap> pages = new ArrayList<>();
            try {
                for (String image : data.imagePaths) pages.add(new Pixmap(Gdx.files.internal(image)));
                for (int i = 0; i < characters.length(); i++) {
                    char c = characters.charAt(i);
                    BitmapFont.Glyph glyph = data.getGlyph(c);
                    String label = path + ": " + describe(c) + " " + context(c);
                    if (glyph == null || glyph.id != c || glyph == data.missingGlyph) {
                        errors.add(label + " missing glyph; run ./gradlew :lwjgl3:bakeFonts"); continue;
                    }
                    if (glyph.page < 0 || glyph.page >= pages.size()) { errors.add(label + " invalid texture page"); continue; }
                    Pixmap page = pages.get(glyph.page);
                    if (glyph.srcX < 0 || glyph.srcY < 0 || glyph.srcX + glyph.width > page.getWidth()
                        || glyph.srcY + glyph.height > page.getHeight()) { errors.add(label + " outside texture page"); continue; }
                    if (c == ' ') continue;
                    boolean ink = false;
                    for (int x = 0; x < glyph.width && !ink; x++) for (int y = 0; y < glyph.height && !ink; y++)
                        ink = (page.getPixel(glyph.srcX + x, glyph.srcY + y) & 255) != 0;
                    if (!ink) errors.add(label + " blank texture glyph");
                }
            } finally { pages.forEach(Pixmap::dispose); }
        }
        require(errors);
    }

    private static String describe(char c) { return "'" + c + "' U+" + String.format("%04X", (int) c); }

    private static String context(char c) {
        List<String> locations = new ArrayList<>();
        for (Language language : Language.values()) {
            if ((language.nativeName() + FontCharacters.uppercase(language.nativeName())).indexOf(c) >= 0)
                locations.add(language.code() + " [nativeName]");
            for (var entry : Catalog.parse(language.code(), source(language)).entrySet())
                if ((entry.getValue() + FontCharacters.uppercase(entry.getValue())).indexOf(c) >= 0) {
                    locations.add(language.code() + " [" + entry.getKey() + "]"); break;
                }
        }
        return locations.isEmpty() ? "[reserved alphabet/UI symbol]" : String.join(", ", locations);
    }

    private static void require(List<String> errors) {
        if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
    }
}
