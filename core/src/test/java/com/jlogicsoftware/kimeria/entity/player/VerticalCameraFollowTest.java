package com.jlogicsoftware.kimeria.entity.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The vertical follow camera (D154, #678): the upward slack and the ease. World Y grows downward. */
class VerticalCameraFollowTest {
    /** A shipped room: the camera top lies between 0 and 8. */
    private static final float MIN = 0, MAX = 8, FLOOR_Y = 256;
    /** The peak of the spring over those 8 px is 8 x STIFFNESS / e = 23.5 px/s. */
    private static final float PEAK_STEP_AT_60 = .4f;

    private static VerticalCameraFollow restingAt(float top) {
        VerticalCameraFollow follow = new VerticalCameraFollow();
        follow.settle(FLOOR_Y, top);
        return follow;
    }

    @Test void theFocusStaysUnderARiseWithinTheSlackAndFollowsBeyondIt() {
        VerticalCameraFollow follow = restingAt(MAX);
        float slack = VerticalCameraFollow.RISE_SLACK;
        assertEquals(FLOOR_Y, follow.focus(FLOOR_Y - slack), .001f);
        assertEquals(FLOOR_Y - 10, follow.focus(FLOOR_Y - slack - 10), .001f);
    }

    @Test void theFocusComesDownWithTheHeroAtOnce() {
        VerticalCameraFollow follow = restingAt(MAX);
        follow.focus(100);
        assertEquals(FLOOR_Y, follow.focus(FLOOR_Y), .001f, "a hero back on the floor is framed as the room was authored");
    }

    @Test void theCameraStartsGentlyNeverOvershootsAndLandsOnItsTarget() {
        VerticalCameraFollow follow = restingAt(MAX);
        float previous = MAX;
        for (int frame = 0; frame < 90; frame++) {
            float top = follow.ease(MIN, 1f / 60f);
            assertTrue(top <= previous && top >= MIN, "frame " + frame + ": " + top);
            assertTrue(previous - top <= PEAK_STEP_AT_60, "frame " + frame + " stepped " + (previous - top));
            if (frame == 0) assertTrue(previous - top < .1f, "it eases in");
            previous = top;
        }
        assertEquals(MIN, previous, 0f);
    }

    @Test void thePathDoesNotDependOnTheFrameRate() {
        VerticalCameraFollow at60 = restingAt(MAX), at144 = restingAt(MAX);
        for (int sixth = 1; sixth <= 5; sixth++) { // every sixth of a second, 10 frames at 60 Hz and 24 at 144 Hz
            float slow = 0, fast = 0;
            for (int i = 0; i < 10; i++) slow = at60.ease(MIN, 1f / 60f);
            for (int i = 0; i < 24; i++) fast = at144.ease(MIN, 1f / 144f);
            assertEquals(slow, fast, .001f, "after " + sixth + "/6 s");
        }
    }

    @Test void targetsThatKeepTurningBackNeverCarryTheCameraOutOfTheirRange() {
        VerticalCameraFollow follow = restingAt(MAX);
        for (int i = 0; i < 600; i++) { // the target jumps between the room's limits at uneven intervals
            float top = follow.ease((i / 7 + i / 31) % 2 == 0 ? MIN : MAX, i % 3 == 0 ? .05f : 1f / 120f);
            assertTrue(top >= MIN && top <= MAX, "frame " + i + ": " + top);
        }
    }

    @Test void noTimeMovesNothing() {
        VerticalCameraFollow follow = restingAt(MAX);
        assertEquals(MAX, follow.ease(MIN, 0), 0f);
    }
}
