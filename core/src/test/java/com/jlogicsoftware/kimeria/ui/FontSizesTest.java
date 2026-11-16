package com.jlogicsoftware.kimeria.ui;

import com.jlogicsoftware.kimeria.localization.FontCharacters;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** D70 (#335): every size the views use is baked, no baked size is unused, and the floor is not lowered silently. */
class FontSizesTest {
    private static final int[] USED = {FontSizes.LABEL, FontSizes.BODY, FontSizes.DIALOGUE, FontSizes.TOUCH_LABEL,
        FontSizes.ZONE, FontSizes.BUTTON, FontSizes.COUNT, FontSizes.TITLE};

    @Test void everyUsedSizeIsBakedAndEveryBakedSizeIsUsed() {
        for (int size : USED) assertTrue(Arrays.stream(FontCharacters.SIZES).anyMatch(s -> s == size), "not baked: " + size);
        for (int size : FontCharacters.SIZES) assertTrue(Arrays.stream(USED).anyMatch(s -> s == size), "baked but unused: " + size);
    }

    @Test void readableTextIsAtLeastSixteenAndOnlyTouchLabelsAreSmaller() {
        assertTrue(FontSizes.LABEL >= 16 && FontSizes.BODY >= 16 && FontSizes.DIALOGUE >= 18);
        for (int size : USED) if (size < 16) assertEquals(FontSizes.TOUCH_LABEL, size);
    }
}
