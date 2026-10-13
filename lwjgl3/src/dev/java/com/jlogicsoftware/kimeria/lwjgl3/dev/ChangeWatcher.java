package com.jlogicsoftware.kimeria.lwjgl3.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Polls the core sources and the assets directory once a second (plain {@code lastModified}
 * polling: macOS's WatchService is itself a slow poller) and reports what changed. Source
 * changes are compiled here, off the render thread; the result is handed on as a {@link Change}.
 */
final class ChangeWatcher implements Runnable {
    private static final long POLL_MS = 1000;
    /** A file modified more recently than this may still be being written; look again next poll. */
    private static final long SETTLE_MS = 300;

    /** @param classes new or changed classes by binary name; @param assets changed paths relative to the assets dir */
    record Change(Map<String, byte[]> classes, Set<String> assets) {
    }

    private final Path sourceRoot;
    private final Path assetsRoot;
    private final SourceCompiler compiler;
    private final Consumer<Change> sink;

    ChangeWatcher(Path sourceRoot, Path assetsRoot, SourceCompiler compiler, Consumer<Change> sink) {
        this.sourceRoot = sourceRoot;
        this.assetsRoot = assetsRoot;
        this.compiler = compiler;
        this.sink = sink;
    }

    void start() {
        Thread thread = new Thread(this, "kimeria-dev-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        Map<Path, Long> seen = scan();
        while (true) {
            try {
                Thread.sleep(POLL_MS);
                seen = poll(seen);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                System.err.println("[dev] watcher: " + e);
            }
        }
    }

    private Map<Path, Long> poll(Map<Path, Long> seen) throws IOException {
        Map<Path, Long> now = scan();
        Map<Path, Long> accepted = new HashMap<>(now);
        boolean sourcesChanged = false;
        Set<String> assets = new TreeSet<>();
        long cutoff = System.currentTimeMillis() - SETTLE_MS;

        for (Map.Entry<Path, Long> e : now.entrySet()) {
            Path path = e.getKey();
            if (Objects.equals(seen.get(path), e.getValue())) continue;
            if (e.getValue() > cutoff) { // still settling: pretend we have not seen it yet
                if (seen.containsKey(path)) accepted.put(path, seen.get(path));
                else accepted.remove(path);
                continue;
            }
            if (path.startsWith(sourceRoot)) sourcesChanged = true;
            else assets.add(assetsRoot.relativize(path).toString().replace('\\', '/'));
        }
        for (Path gone : seen.keySet()) {
            if (!now.containsKey(gone) && gone.startsWith(sourceRoot)) sourcesChanged = true;
        }

        Map<String, byte[]> classes = Map.of();
        if (sourcesChanged) {
            Map<String, byte[]> compiled = compiler.compile();
            if (compiled == null) System.err.println("[dev] compile failed, the running code is unchanged");
            else classes = compiled;
        }
        if (!classes.isEmpty() || !assets.isEmpty()) sink.accept(new Change(classes, assets));
        return accepted;
    }

    private Map<Path, Long> scan() {
        Map<Path, Long> result = new HashMap<>();
        for (Path root : new Path[]{sourceRoot, assetsRoot}) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(p -> {
                        try {
                            result.put(p, Files.getLastModifiedTime(p).toMillis());
                        } catch (IOException ignored) {
                            // vanished between listing and stat: the next poll sees it gone
                        }
                    });
            } catch (IOException ignored) {
                // directory briefly missing, e.g. while a tool rewrites it
            }
        }
        return result;
    }
}
