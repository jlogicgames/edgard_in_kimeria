package com.jlogicsoftware.kimeria.ui;

import com.jlogicsoftware.kimeria.localization.FontCharacters;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** D70 (#335): every size the views use is baked, no baked size is unused, and the floor is not lowered silently. */
class FontSizesTest {
    private static final int[] USED = {FontSizes.LABEL, FontSizes.BODY, FontSizes.DIALOGUE, FontSizes.ROTATE_PROMPT,
        FontSizes.ZONE, FontSizes.BUTTON, FontSizes.COUNT, FontSizes.TITLE, FontSizes.STORY,
        FontSizes.LABEL_LARGE, FontSizes.BODY_LARGE, FontSizes.DIALOGUE_LARGE};

    @Test void everyUsedSizeIsBakedAndEveryBakedSizeIsUsed() {
        for (int size : USED) assertTrue(Arrays.stream(FontCharacters.SIZES).anyMatch(s -> s == size), "not baked: " + size);
        for (int size : FontCharacters.SIZES) assertTrue(Arrays.stream(USED).anyMatch(s -> s == size), "baked but unused: " + size);
    }

    /** D140: the rotate prompt was the one size under 16 (13 px); it is a large size now, so no baked size is under 16. */
    @Test void readableTextIsAtLeastSixteen() {
        assertTrue(FontSizes.LABEL >= 16 && FontSizes.BODY >= 16 && FontSizes.DIALOGUE >= 18);
        for (int size : USED) assertTrue(size >= 16, "size " + size);
        for (int size : FontCharacters.SIZES) assertTrue(size >= 16, "baked size " + size);
        assertTrue(FontSizes.ROTATE_PROMPT >= FontSizes.TITLE, "the prompt is as large as the title: it has to be impossible to miss");
    }

    /** D137: the large tier of a role is never smaller than its nominal size and no larger than the biggest the layouts hold (20). */
    @Test void theLargeTierGrowsEachSmallRoleWithinWhatTheLayoutsHold() {
        assertTrue(FontSizes.LABEL_LARGE >= FontSizes.LABEL && FontSizes.BODY_LARGE >= FontSizes.BODY && FontSizes.DIALOGUE_LARGE >= FontSizes.DIALOGUE);
        for (int size : new int[]{FontSizes.LABEL_LARGE, FontSizes.BODY_LARGE, FontSizes.DIALOGUE_LARGE}) assertTrue(size <= FontSizes.ZONE, "no larger than the zone size: " + size);
        assertEquals(FontSizes.LABEL, FontSizes.label(false));
        assertEquals(FontSizes.LABEL_LARGE, FontSizes.label(true));
        assertEquals(FontSizes.BODY, FontSizes.body(false));
        assertEquals(FontSizes.BODY_LARGE, FontSizes.body(true));
        assertEquals(FontSizes.DIALOGUE, FontSizes.dialogue(false));
        assertEquals(FontSizes.DIALOGUE_LARGE, FontSizes.dialogue(true));
    }
}
