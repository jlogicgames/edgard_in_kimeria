package com.jlogicsoftware.kimeria.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Strict release validation; runtime fallback must never hide incomplete shipped catalogs. */
public final class CatalogValidation {
    private CatalogValidation() { }

    public static List<String> errors(Language language, Map<String, String> english, Map<String, String> catalog) {
        List<String> errors = new ArrayList<>();
        for (String key : catalog.keySet()) {
            try { Msg.valueOf(key); }
            catch (IllegalArgumentException unknown) { errors.add(language.code() + " [" + key + "]: unknown key"); }
        }
        for (Msg message : Msg.values()) {
            String key = message.name(), value = catalog.get(key), reference = english.get(key);
            String prefix = language.code() + " [" + key + "]: ";
            if (value == null) { errors.add(prefix + "missing key"); continue; }
            if (value.trim().isEmpty()) errors.add(prefix + "empty translation");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                // All rendering uses bitmap fonts with BMP glyph IDs. Reject broken UTF-8,
                // surrogate pairs and unsupported controls before font generation can mask them.
                if (c == '\uFFFD' || Character.isSurrogate(c) || c < 32 && c != '\n')
                    errors.add(prefix + "unsupported character U+" + Integer.toHexString(c).toUpperCase(java.util.Locale.ROOT));
            }
            try {
                Map<String, Integer> placeholders = Catalog.placeholders(value);
                if (reference != null && !placeholders.equals(Catalog.placeholders(reference)))
                    errors.add(prefix + "placeholder mismatch; expected " + Catalog.placeholders(reference) + ", got " + placeholders);
            } catch (IllegalArgumentException malformed) { errors.add(prefix + malformed.getMessage()); }
        }
        return errors;
    }
}
