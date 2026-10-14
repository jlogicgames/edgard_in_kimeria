package com.jlogicsoftware.kimeria.ui;

import java.util.List;
import java.util.function.Supplier;

/**
 * Keyboard/gamepad/mouse-navigable list of buttons, matching the Rust
 * version's menu nav (arrows/tab move, enter/space/A confirm, esc/B back).
 * Owns only selection state; {@link Overlay#menu} draws it and reports
 * clicks, {@link com.jlogicsoftware.kimeria.KimeriaGame} drives navigation.
 *
 * <p>Title/body are suppliers (not plain strings) so a menu built once at
 * startup still reflects the current UI language when re-rendered after
 * the player changes it in Options.
 */
public class Menu {
    public final Supplier<String> title;
    public final Supplier<String> body;
    public final List<MenuItem> items;
    public final Runnable onBack;
    public final float buttonWidth;
    public int selected = 0;

    public Menu(Supplier<String> title, Supplier<String> body, List<MenuItem> items, Runnable onBack) {
        this(title, body, items, onBack, Overlay.BUTTON_W);
    }

    public Menu(Supplier<String> title, Supplier<String> body, List<MenuItem> items, Runnable onBack,
                float buttonWidth) {
        this.buttonWidth = buttonWidth;
        this.title = title;
        this.body = body;
        this.items = items;
        this.onBack = onBack;
    }

    public String titleText() {
        return title == null ? null : title.get();
    }

    public String bodyText() {
        return body == null ? null : body.get();
    }

    public void moveSelection(int delta) {
        int n = items.size();
        selected = ((selected + delta) % n + n) % n;
    }

    public void confirmSelected() {
        items.get(selected).action.run();
    }

    public void back() {
        if (onBack != null) onBack.run();
    }
}
