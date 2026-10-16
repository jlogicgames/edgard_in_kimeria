package com.jlogicsoftware.kimeria.effects;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossFeedbackTest {
    @Test void retryAnimatesFromTheLastHealthToFullInAQuarterSecond() {
        BossFeedback feedback = new BossFeedback();
        feedback.restored(2, 12);
        assertEquals(2f, feedback.displayedEnergy(12));
        feedback.update(.125f);
        assertEquals(7f, feedback.displayedEnergy(12));
        feedback.update(.125f);
        assertEquals(12f, feedback.displayedEnergy(12));
        assertEquals(0f, feedback.restoreAlpha());
    }

    @Test void elixirDamageHighlightsAllTenLostSegmentsAndThenClears() {
        BossFeedback feedback = new BossFeedback();
        feedback.damaged(12, 2);
        assertEquals(2f, feedback.displayedEnergy(2));
        assertEquals(0f, feedback.lostSegmentAlpha(1));
        assertEquals(1f, feedback.lostSegmentAlpha(2));
        assertEquals(1f, feedback.lostSegmentAlpha(11));
        feedback.update(.125f);
        assertEquals(.5f, feedback.lostSegmentAlpha(2));
        feedback.update(.125f);
        assertEquals(0f, feedback.lostSegmentAlpha(11));
    }

    @Test void aFreshRetryCannotCarryDamageFlashesFromTheFailedAttempt() {
        BossFeedback feedback = new BossFeedback();
        feedback.damaged(12, 11);
        feedback.restored(11, 12);
        assertEquals(0f, feedback.lostSegmentAlpha(11));
        feedback.update(0);
        assertEquals(11f, feedback.displayedEnergy(12));
        feedback.update(1);
        assertEquals(12f, feedback.displayedEnergy(12));
    }
}
