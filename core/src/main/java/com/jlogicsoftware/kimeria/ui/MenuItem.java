package com.jlogicsoftware.kimeria.ui;

import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

/** One button in a {@link Menu}. */
public class MenuItem {
    public final Supplier<String> label;
    public final Runnable action;
    public final BooleanSupplier visible;

    public MenuItem(Supplier<String> label, Runnable action) {
        this(label, action, () -> true);
    }

    public MenuItem(Supplier<String> label, Runnable action, BooleanSupplier visible) {
        this.label = label;
        this.action = action;
        this.visible = visible;
    }

    public MenuItem(String label, Runnable action) {
        this(() -> label, action);
    }
}
