package com.jlogicsoftware.kimeria.testsupport;

import com.jlogicsoftware.kimeria.TrapWarnings;

/**
 * Expected trap warning lengths, derived from {@link TrapWarnings} so that a tuned warning time (D7, #300)
 * never needs a second edit in a test. The literal values are pinned once, in {@code TrapWarningsTest}.
 */
public final class ExpectedWarnings {
    private ExpectedWarnings() {}

    /** The default warning (stump fuse, swell, thorn root, stone shadow...) with or without the longer-warnings assist. */
    public static float standard(boolean longer) {
        return of(TrapWarnings.DEFAULT_WARNING_SECONDS, longer);
    }

    /** The falling platform's warning with or without the assist. */
    public static float platform(boolean longer) {
        return of(TrapWarnings.FALLING_PLATFORM_SECONDS, longer);
    }

    public static float of(float baseSeconds, boolean longer) {
        return longer ? TrapWarnings.longestDuration(baseSeconds) : baseSeconds;
    }
}
