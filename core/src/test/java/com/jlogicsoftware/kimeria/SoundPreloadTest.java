package com.jlogicsoftware.kimeria;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The first play of a sound after start-up or a pause was lost on the web (a lazily created Howler instance, an audio
 * context made after the unlocking key press). Every effect is therefore created at start-up; the audio layer itself
 * needs a browser, so this pins the list against the sources and the files.
 */
class SoundPreloadTest {
    private static Path root() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("assets/audio"))) p = p.getParent();
        return p;
    }

    @Test void everySoundTheGamePlaysIsPreloadedAndExists() throws IOException {
        Path root = root();
        Set<String> listed = new HashSet<>(List.of(Assets.SOUND_EFFECTS));
        assertEquals(Assets.SOUND_EFFECTS.length, listed.size(), "no duplicates");
        for (String name : listed) assertTrue(Files.isRegularFile(root.resolve("assets/audio/" + name + ".wav")), name);
        Set<String> used = new TreeSet<>();
        Pattern literal = Pattern.compile("(?:playSound|loopSound|stopSound)\\(\\s*\"([a-z_]+)\""), constant = Pattern.compile("_SOUND\\s*=\\s*\"([a-z_]+)\"");
        try (Stream<Path> files = Files.walk(root.resolve("core/src/main/java"))) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(f);
                for (Pattern p : List.of(literal, constant)) { Matcher m = p.matcher(text); while (m.find()) used.add(m.group(1)); }
                Matcher m = Pattern.compile("\"audio/([a-z_]+)\\.wav\"").matcher(text);
                while (m.find()) used.add(m.group(1));
            }
        }
        used.remove("intro"); // streamed Music, not a Sound
        assertTrue(used.size() >= 10, used.toString());
        for (String name : used) assertTrue(listed.contains(name), name + " is played but not in Assets.SOUND_EFFECTS");
    }
}
