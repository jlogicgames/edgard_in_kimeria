package com.jlogicsoftware.kimeria.testsupport;

import java.lang.management.ManagementFactory;

/**
 * Measures the bytes the current thread allocates per frame of a frame loop (docs/DEVELOPMENT.md, "Particle system
 * plan", section 13). HotSpot only: it says nothing about TeaVM, GC pauses or frame time, and escape analysis can
 * hide some allocations a browser build would still pay. It fails, never skips, when the JVM cannot report them.
 */
public final class AllocationProbe {
    public static final int WARMUP_FRAMES = 300, MEASURED_FRAMES = 1200;

    /** One frame of the scene under test; called with the frame index (0-based, warm-up included). */
    public interface Frame { void run(int index); }

    private AllocationProbe() { }

    /** Runs the warm-up frames, then the measured frames, and returns the average allocated bytes per measured frame. */
    public static double bytesPerFrame(Frame frame) {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("ThreadMXBean allocated-bytes is not supported on this JVM");
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        int index = 0;
        for (; index < WARMUP_FRAMES; index++) frame.run(index);
        long before = bean.getThreadAllocatedBytes(thread);
        if (before < 0) throw new AssertionError("ThreadMXBean.getThreadAllocatedBytes is unsupported or disabled for this thread");
        for (int i = 0; i < MEASURED_FRAMES; i++, index++) frame.run(index);
        long after = bean.getThreadAllocatedBytes(thread);
        return (after - before) / (double) MEASURED_FRAMES;
    }
}
