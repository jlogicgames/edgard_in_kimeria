package com.jlogicsoftware.kimeria.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MenuTest {
    @Test
    void hiddenItemsAreSkippedInBothDirectionsAndLayout() {
        Menu menu = new Menu(null, null, List.of(
            new MenuItem(() -> "Continue", () -> fail("Hidden item activated"), () -> false),
            new MenuItem("New Game", () -> {}),
            new MenuItem(() -> "Hidden", () -> fail("Hidden item activated"), () -> false),
            new MenuItem("Options", () -> {})
        ), null);
        assertEquals(1, menu.selected);
        assertEquals(88f, Overlay.buttonsHeight(menu));
        menu.moveSelection(-1);
        assertEquals(3, menu.selected);
        menu.moveSelection(1);
        assertEquals(1, menu.selected);
        menu.moveSelection(3);
        assertEquals(3, menu.selected);
    }

    @Test
    void changingVisibilityRepairsSelectionBeforeConfirmation() {
        AtomicBoolean visible = new AtomicBoolean(true);
        AtomicInteger activated = new AtomicInteger();
        Menu menu = new Menu(null, null, List.of(
            new MenuItem(() -> "Continue", () -> fail("Hidden item activated"), visible::get),
            new MenuItem("New Game", activated::incrementAndGet)
        ), null);
        assertEquals(0, menu.selected);
        visible.set(false);
        menu.confirmSelected();
        assertEquals(1, activated.get());
        assertEquals(1, menu.selected);
        visible.set(true);
        menu.ensureVisibleSelection();
        assertEquals(1, menu.selected); // Preserve a still-visible selection.
        menu.moveSelection(-1);
        assertEquals(0, menu.selected);
    }

    @Test
    void emptyAndFullyHiddenMenusHaveNoSelectionOrNegativeHeight() {
        for (List<MenuItem> items : List.of(List.<MenuItem>of(), List.of(
                new MenuItem(() -> "Hidden", () -> fail("Hidden item activated"), () -> false)))) {
            Menu menu = new Menu(null, null, items, null);
            menu.moveSelection(-1);
            menu.confirmSelected();
            assertEquals(-1, menu.selected);
            assertEquals(0f, Overlay.buttonsHeight(menu));
        }
    }
}
