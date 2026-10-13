package com.jlogicsoftware.kimeria.lwjgl3.dev;

import java.lang.instrument.Instrumentation;

/**
 * Java agent entry point (see {@code devAgentJar} in lwjgl3/build.gradle.kts). It exists only to
 * hand the JVM's {@link Instrumentation} to {@link DevHost}, which uses it to swap method bodies
 * of already-loaded classes: the "hot reload" half of the dev loop.
 */
public final class DevAgent {
    private static volatile Instrumentation instrumentation;

    private DevAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
    }

    /** Null when the JVM was started without {@code -javaagent}. */
    static Instrumentation instrumentation() {
        return instrumentation;
    }
}
