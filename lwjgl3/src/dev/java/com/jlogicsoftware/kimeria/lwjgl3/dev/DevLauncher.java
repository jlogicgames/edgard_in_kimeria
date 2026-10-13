package com.jlogicsoftware.kimeria.lwjgl3.dev;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Desktop launcher for development only: {@code ./gradlew runDev}. It lives in its own source set
 * and is never part of the game jar, the installers or CI, so a release build cannot contain it
 * (see lwjgl3/build.gradle.kts). See {@link DevHost} for what it does with changes.
 */
public final class DevLauncher {
    private DevLauncher() {
    }

    public static void main(String[] args) throws IOException {
        // Set by the runDev task: the repository root, since the working directory is assets/.
        Path root = Path.of(System.getProperty("kimeria.dev.root", "..")).toAbsolutePath().normalize();
        Path sources = root.resolve("core/src/main/java");
        Path assets = root.resolve("assets");
        Path classes = root.resolve("core/build/dev-classes");

        SourceCompiler.deleteRecursively(classes);
        SourceCompiler compiler = new SourceCompiler(
            sources, classes, root.resolve("core/build/dev-classes-staging"), System.getProperty("java.class.path"));
        System.out.println("[dev] compiling core ...");
        if (compiler.compile() == null) {
            System.err.println("[dev] core does not compile; fix the errors above and run again");
            System.exit(1);
        }

        DevHost host = new DevHost(classes);
        new ChangeWatcher(sources, assets, compiler, host::post).start();

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Edgard in Kimeria [dev]");
        config.useVsync(true);
        config.setForegroundFPS(60);
        // Windowed regardless of the player's fullscreen preference: the editor must stay in reach.
        config.setWindowedMode(1280, 720);
        config.setResizable(true);
        System.out.println("[dev] ready: edits to core sources and assets/ apply live; F7 restarts the game");
        new Lwjgl3Application(host, config);
    }
}
