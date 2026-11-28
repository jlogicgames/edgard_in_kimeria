package com.jlogicsoftware.kimeria.testsupport;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Interns every string literal of a class, so that no measured window pays for them. When HotSpot queues an optimizing (C2 or
 * Graal) compile of a method, the thread that asked for it first creates the String objects the method's class still lacks for
 * its literals. For a frame lambda of {@code AllocationBudgetTest} that was about 21 KB once, on the measured thread, whenever
 * the compile was queued inside the window (D161). A literal that is already interned is found, not created.
 */
final class StringLiterals {
    private static final List<String> HELD = new ArrayList<>(); // the JVM's intern table holds its strings weakly

    private StringLiterals() { }

    /** Reads {@code type}'s class file and interns its string constants; fails, never skips, when the file cannot be read. */
    static synchronized void intern(Class<?> type) {
        String file = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getResourceAsStream(file)) {
            if (stream == null) throw new AssertionError("No class file " + file);
            DataInputStream in = new DataInputStream(stream);
            in.skipNBytes(8); // magic and version
            String[] utf = new String[in.readUnsignedShort()];
            List<Integer> literals = new ArrayList<>();
            for (int i = 1; i < utf.length; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) { // JVMS 4.4, the constant pool
                    case 1 -> utf[i] = in.readUTF();
                    case 8 -> literals.add(in.readUnsignedShort());
                    case 7, 16, 19, 20 -> in.skipNBytes(2);
                    case 15 -> in.skipNBytes(3);
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                    case 5, 6 -> { in.skipNBytes(8); i++; } // long and double take two slots
                    default -> throw new AssertionError("Unknown constant pool tag " + tag + " in " + file);
                }
            }
            for (int index : literals) HELD.add(utf[index].intern());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
