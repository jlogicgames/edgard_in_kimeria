package com.jlogicsoftware.kimeria.localization;

import com.badlogic.gdx.Gdx;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Loads common packaged UTF-8 catalogs once. Missing/invalid translations fall back to English. */
public final class Localization {
    private static Localization current;
    private final Map<Language, Map<String, String>> catalogs = new EnumMap<>(Language.class);
    private final Set<String> reported = new HashSet<>();
    private final Consumer<String> diagnostic;

    public Localization(Function<Language, String> source, Consumer<String> diagnostic) {
        this.diagnostic = diagnostic;
        for (Language language : Language.values()) {
            try {
                catalogs.put(language, Catalog.parse(language.code(), source.apply(language)));
            } catch (RuntimeException error) {
                catalogs.put(language, new java.util.LinkedHashMap<>());
                diagnostic.accept(language.code() + " [<resource>]: " + error.getMessage());
            }
        }
    }

    public static Localization current() {
        if (current == null) current = new Localization(
            language -> Gdx.files.internal("localization/" + language.code() + ".json").readString("UTF-8"),
            message -> System.err.println("Localization: " + message));
        return current;
    }

    public String text(Language language, String key) {
        if (language == null) language = Language.ENGLISH;
        String value = catalogs.get(language).get(key);
        if (value != null && !value.trim().isEmpty()) return value;
        if (reported.add(language.code() + ":" + key)) diagnostic.accept(language.code() + " [" + key + "]: using English fallback");
        value = catalogs.get(Language.ENGLISH).get(key);
        if (value != null && !value.trim().isEmpty()) return value;
        if (reported.add("en:missing:" + key)) diagnostic.accept("en [" + key + "]: English fallback also missing");
        return "[" + key + "]";
    }
}
