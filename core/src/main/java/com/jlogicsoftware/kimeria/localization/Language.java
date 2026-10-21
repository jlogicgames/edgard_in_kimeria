package com.jlogicsoftware.kimeria.localization;

/** Enum names are persisted; never rename existing values when adding a language. */
public enum Language {
    ENGLISH("en", "English"), UKRAINIAN("uk", "Українська"),
    POLISH("pl", "Polski"), GERMAN("de", "Deutsch"), BELARUSIAN("be", "Беларуская");

    private final String code;
    private final String nativeName;

    Language(String code, String nativeName) {
        this.code = code;
        this.nativeName = nativeName;
    }

    public String code() { return code; }

    /** The language's own name, in its own language -- used as the label of the button that selects it. */
    public String nativeName() {
        return nativeName;
    }

    public Language next() {
        Language[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
