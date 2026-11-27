package com.jlogicsoftware.kimeria.ui;

import java.util.function.Supplier;

/**
 * A "Label: value" menu label, joined again only when either part changes, so a menu can ask for it every frame without
 * concatenating anything (#464).
 */
public final class ValueLabel implements Supplier<String> {
    private final Supplier<String> name, value;
    private String shownName, shownValue, text;

    public ValueLabel(Supplier<String> name, Supplier<String> value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String get() {
        String currentName = name.get(), currentValue = value.get();
        if (!currentName.equals(shownName) || !currentValue.equals(shownValue)) {
            shownName = currentName;
            shownValue = currentValue;
            text = currentName + ": " + currentValue;
        }
        return text;
    }
}
