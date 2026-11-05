package com.jlogicsoftware.kimeria.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HintLayoutTest {
    @Test void aLabelTopGrowsUpwardFromItsAnchorAndStaysOnScreen() {
        assertEquals(100 + 30 - 9, HintLayout.top(100, 30, 9, 360), .001, "two lines grow upward");
        assertEquals(100, HintLayout.top(100, 9, 9, 360), .001, "one line sits on the anchor");
        assertEquals(HintLayout.BOTTOM_MARGIN, HintLayout.top(-50, 9, 9, 360), .001);
        assertEquals(360 - HintLayout.TOP_MARGIN, HintLayout.top(999, 40, 9, 360), .001);
    }

    @Test void theContextualLabelIsLeftAloneWhenItIsApartOrNoMessageIsPresent() {
        assertEquals(100, HintLayout.contextTop(100, 40, true, 220), .001);
        assertEquals(290, HintLayout.contextTop(290, 40, false, 0), .001);
    }

    @Test void theContextualLabelIsLoweredUnderTheMessageNeverTheOtherWayRound() {
        float zoneBottom = 180;
        float lowered = HintLayout.contextTop(200, 40, true, zoneBottom);
        assertEquals(zoneBottom - HintLayout.GAP, lowered, .001);
        // It stays on screen: never below its own height.
        assertEquals(40, HintLayout.contextTop(290, 40, true, 60), .001);
    }

    @Test void thePlacementUsesOnlyAnchorsAndHeightsSoOpacityCannotMoveIt() {
        float a = HintLayout.contextTop(150, 30, true, 250, 20, 9, 360);
        float b = HintLayout.contextTop(150, 30, true, 250, 20, 9, 360);
        assertEquals(a, b, 0);
        assertTrue(a <= HintLayout.top(250, 20, 9, 360) - 20 - HintLayout.GAP + .001);
    }

    @Test void theGapExceedsTheMessagesFaintSlideIn() {
        assertTrue(HintLayout.GAP > 22, "ATTENTION_SLIDE is 22 px, from below the resting place at zero opacity");
    }
}
