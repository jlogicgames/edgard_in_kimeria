package com.jlogicsoftware.kimeria.lwjgl3.dev;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Compiles all of {@code core} with the JDK's in-process compiler, independently of Gradle, so a
 * save-to-running-game round trip does not pay for a Gradle invocation. Classes are compiled into
 * a staging directory first and then moved into the live output directory one file at a time, so
 * the running game's class loader never reads a half-written class file.
 */
final class SourceCompiler {
    private final Path sourceRoot;
    private final Path outDir;
    private final Path stagingDir;
    private final String classpath;
    /** The bytes of every class file currently installed in {@link #outDir}, by binary name. */
    private final Map<String, byte[]> installed = new HashMap<>();

    SourceCompiler(Path sourceRoot, Path outDir, Path stagingDir, String classpath) {
        this.sourceRoot = sourceRoot;
        this.outDir = outDir;
        this.stagingDir = stagingDir;
        this.classpath = classpath;
    }

    /**
     * Compiles everything and installs the class files that differ from the previous run.
     *
     * @return the classes that are new or changed (binary name to bytes), or null if the sources
     *         do not compile, in which case nothing is installed
     */
    Map<String, byte[]> compile() throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new IllegalStateException("No system Java compiler: run the dev build on a JDK, not a JRE");
        }
        deleteRecursively(stagingDir);
        Files.createDirectories(stagingDir);

        List<Path> sources;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }

        boolean[] failed = {false};
        DiagnosticListener<JavaFileObject> listener = d -> {
            if (d.getKind() == Diagnostic.Kind.ERROR) failed[0] = true;
            if (d.getKind() == Diagnostic.Kind.ERROR || d.getKind() == Diagnostic.Kind.WARNING) {
                String file = d.getSource() == null ? "" : sourceRoot.relativize(Path.of(d.getSource().toUri())) + ":" + d.getLineNumber() + ": ";
                System.err.println("[dev] " + file + d.getMessage(null));
            }
        };
        boolean ok;
        try (StandardJavaFileManager fm = javac.getStandardFileManager(listener, null, StandardCharsets.UTF_8)) {
            List<String> options = List.of(
                "-d", stagingDir.toString(), "-cp", classpath,
                "--release", "17", "-encoding", "UTF-8", "-g", "-proc:none", "-Xlint:none");
            ok = javac.getTask(null, fm, listener, options, null, fm.getJavaFileObjectsFromPaths(sources)).call();
        }
        if (!ok || failed[0]) return null;

        Map<String, byte[]> changed = new LinkedHashMap<>();
        List<Path> classFiles;
        try (Stream<Path> walk = Files.walk(stagingDir)) {
            classFiles = walk.filter(p -> p.toString().endsWith(".class")).toList();
        }
        for (Path file : classFiles) {
            String relative = stagingDir.relativize(file).toString().replace('\\', '/');
            String name = relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
            byte[] bytes = Files.readAllBytes(file);
            if (Arrays.equals(installed.get(name), bytes)) continue;
            Path target = outDir.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.move(file, target, StandardCopyOption.ATOMIC_MOVE);
            installed.put(name, bytes);
            changed.put(name, bytes);
        }
        return changed;
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(dir)) {
            paths = new ArrayList<>(walk.sorted(Comparator.reverseOrder()).toList());
        }
        for (Path p : paths) Files.delete(p);
    }
}
