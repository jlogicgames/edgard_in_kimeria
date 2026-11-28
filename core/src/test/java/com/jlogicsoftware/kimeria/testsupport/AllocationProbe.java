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

    /**
     * Runs the warm-up frames, then the measured frames, and returns the average allocated bytes per measured frame. The string
     * literals of the class that made {@code frame} are interned first ({@link StringLiterals}, D161).
     */
    public static double bytesPerFrame(Frame frame) {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("ThreadMXBean allocated-bytes is not supported on this JVM");
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        StringLiterals.intern(frame.getClass().getNestHost());
        int index = 0;
        for (; index < WARMUP_FRAMES; index++) frame.run(index);
        long before = bean.getThreadAllocatedBytes(thread);
        if (before < 0) throw new AssertionError("ThreadMXBean.getThreadAllocatedBytes is unsupported or disabled for this thread");
        for (int i = 0; i < MEASURED_FRAMES; i++, index++) frame.run(index);
        long after = bean.getThreadAllocatedBytes(thread);
        return (after - before) / (double) MEASURED_FRAMES;
    }

    /**
     * The median, over {@code pairs} pairs of adjacent windows, of (bytes per frame of {@code a}) minus (bytes per frame of {@code b}).
     * For two paths that share a large noisy cost (the harness stubs and escape analysis, which the JIT removes at an unpredictable
     * moment, so a single window of one path can sit in another JIT state than a single window of the other), windows are taken
     * alternately, A then B, B then A, so a JIT change lands in one pair at a time and the median ignores it, while an allocation
     * that the paths really differ by shows in every pair. {@code enterA} and {@code enterB} put the scene in each state (not measured).
     */
    public static double medianPairedDifference(int pairs, int framesPerWindow, Runnable enterA, Frame a, Runnable enterB, Frame b) {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("ThreadMXBean allocated-bytes is not supported on this JVM");
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        StringLiterals.intern(a.getClass().getNestHost());
        StringLiterals.intern(b.getClass().getNestHost());
        int index = 0;
        for (int w = 0; w < 2; w++) {
            enterA.run(); for (int i = 0; i < WARMUP_FRAMES; i++) a.run(index++);
            enterB.run(); for (int i = 0; i < WARMUP_FRAMES; i++) b.run(index++);
        }
        double[] differences = new double[pairs];
        for (int p = 0; p < pairs; p++) {
            boolean aFirst = p % 2 == 0;
            double first = window(bean, thread, aFirst ? enterA : enterB, aFirst ? a : b, framesPerWindow, index);
            double second = window(bean, thread, aFirst ? enterB : enterA, aFirst ? b : a, framesPerWindow, index);
            differences[p] = aFirst ? first - second : second - first;
        }
        java.util.Arrays.sort(differences);
        return differences[pairs / 2];
    }

    private static double window(com.sun.management.ThreadMXBean bean, long thread, Runnable enter, Frame frame, int frames, int index) {
        enter.run();
        long before = bean.getThreadAllocatedBytes(thread);
        if (before < 0) throw new AssertionError("ThreadMXBean.getThreadAllocatedBytes is unsupported or disabled for this thread");
        for (int i = 0; i < frames; i++) frame.run(index + i);
        return (bean.getThreadAllocatedBytes(thread) - before) / (double) frames;
    }
}
