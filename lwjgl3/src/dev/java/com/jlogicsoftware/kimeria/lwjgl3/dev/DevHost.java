package com.jlogicsoftware.kimeria.lwjgl3.dev;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector2;

import java.lang.instrument.ClassDefinition;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Hosts the real game inside a window that outlives it, and applies source/asset changes to it
 * the way Flutter does: the state-preserving "hot reload" wherever that is possible, and a
 * "hot restart" (a brand new game from freshly compiled classes) where it is not.
 *
 * <p>Hot reload, which keeps the run exactly as it is:
 * <ul>
 *   <li>method-body edits are swapped into the live classes via the JVM's class redefinition;</li>
 *   <li>edited images are re-uploaded into the existing {@link Texture} objects;</li>
 *   <li>edited sounds and shaders are dropped from their caches and reloaded on next use
 *       (a shader that does not compile is rejected and the old one keeps running);</li>
 *   <li>an edited level file rebuilds the level, with the player left where they stood.</li>
 * </ul>
 * Hot restart, which resets to the main menu: any structural code change (a field, method or
 * lambda added or removed, which the JVM refuses to redefine), edited fonts or music, or a game
 * that crashed. F7 forces one.
 *
 * <p>The game is touched only through reflection, so nothing in {@code core} knows this exists.
 * Everything here runs on the render thread.
 */
final class DevHost implements ApplicationListener {
    private static final String GAME_CLASS = "com.jlogicsoftware.kimeria.KimeriaGame";
    private static final String SHADERS_CLASS = "com.jlogicsoftware.kimeria.effects.Shaders";

    private final Path classesDir;
    private final Queue<ChangeWatcher.Change> inbox = new ConcurrentLinkedQueue<>();

    private URLClassLoader loader;
    private ApplicationListener game;
    /** The game threw; it stays frozen on a red screen until the next change or F7. */
    private boolean faulted;
    private int width, height;

    DevHost(Path classesDir) {
        this.classesDir = classesDir;
    }

    void post(ChangeWatcher.Change change) {
        inbox.add(change);
    }

    private static void log(String message) {
        System.out.println("[dev] " + message);
    }

    // ---- ApplicationListener ----

    @Override
    public void create() {
        startGame();
    }

    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        if (game != null && !faulted) game.resize(width, height);
    }

    @Override
    public void render() {
        ChangeWatcher.Change change;
        while ((change = inbox.poll()) != null) apply(change);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F7)) restart("F7");

        if (game == null || faulted) {
            Gdx.gl.glClearColor(0.25f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            return;
        }
        try {
            game.render();
        } catch (Throwable t) {
            fault("render", t);
        }
    }

    @Override
    public void pause() {
        if (game != null && !faulted) game.pause();
    }

    @Override
    public void resume() {
        if (game != null && !faulted) game.resume();
    }

    @Override
    public void dispose() {
        stopGame();
    }

    // ---- game lifecycle ----

    private void startGame() {
        try {
            // The parent has libGDX but not core, so every game class comes from classesDir and
            // dies with this loader: that is what makes a restart pick up structural changes.
            loader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, DevHost.class.getClassLoader());
            // DevHost bypasses Lwjgl3Launcher, so it must select the debug game explicitly on every restart.
            game = (ApplicationListener) loader.loadClass(GAME_CLASS)
                .getDeclaredConstructor(boolean.class, boolean.class).newInstance(false, true);
            game.create();
            if (width > 0) game.resize(width, height);
            faulted = false;
        } catch (Throwable t) {
            fault("startup", t);
        }
    }

    private void stopGame() {
        if (game != null) {
            try {
                game.dispose();
            } catch (Throwable t) {
                log("dispose threw: " + t);
            }
        }
        game = null;
        Gdx.input.setInputProcessor(null);
        if (loader != null) {
            try {
                loader.close();
            } catch (Exception ignored) {
                // nothing left to do with a loader we are dropping anyway
            }
            loader = null;
        }
    }

    private void restart(String reason) {
        log("hot restart: " + reason);
        stopGame();
        startGame();
    }

    private void fault(String phase, Throwable t) {
        faulted = true;
        System.err.println("[dev] the game crashed in " + phase + "; save a fix, or press F7 to restart");
        t.printStackTrace();
    }

    // ---- applying a change ----

    private void apply(ChangeWatcher.Change change) {
        if (game == null || faulted) {
            restart("changed while the game was not running");
            return;
        }
        long started = System.nanoTime();
        List<String> done = new ArrayList<>();
        String restartReason = reloadCode(change.classes(), done);
        if (restartReason == null) restartReason = reloadAssets(change.assets(), done);
        if (restartReason != null) {
            restart(restartReason);
        } else if (!done.isEmpty()) {
            log("hot reload: " + String.join(", ", done) + " (" + (System.nanoTime() - started) / 1_000_000 + " ms)");
        }
    }

    /** Swaps method bodies into the already-loaded classes. Returns why a restart is needed, or null. */
    private String reloadCode(Map<String, byte[]> classes, List<String> done) {
        if (classes.isEmpty()) return null;
        var instrumentation = DevAgent.instrumentation();
        if (instrumentation == null) return "code changed and the JVM has no agent (start it with ./gradlew runDev)";

        Map<String, Class<?>> loaded = new HashMap<>();
        for (Class<?> c : instrumentation.getAllLoadedClasses()) {
            if (c.getClassLoader() == loader) loaded.put(c.getName(), c);
        }
        List<ClassDefinition> definitions = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            Class<?> c = loaded.get(e.getKey());
            if (c != null) definitions.add(new ClassDefinition(c, e.getValue()));
            // A class that is not loaded yet needs nothing: it loads fresh from classesDir.
        }
        if (definitions.isEmpty()) return null;
        try {
            instrumentation.redefineClasses(definitions.toArray(new ClassDefinition[0]));
        } catch (UnsupportedOperationException e) {
            return "structural code change (" + e.getMessage() + ")";
        } catch (Exception | LinkageError e) {
            return "code could not be redefined (" + e + ")";
        }
        done.add(definitions.size() + (definitions.size() == 1 ? " class" : " classes"));
        return null;
    }

    /** Applies changed asset files. Returns why a restart is needed, or null. */
    private String reloadAssets(Set<String> changed, List<String> done) {
        if (changed.isEmpty()) return null;
        try {
            Object assets = get(game, "assets");
            Map<String, Texture> textures = get(assets, "textures");
            Map<String, Sound> sounds = get(assets, "sounds");
            int textureCount = 0, soundCount = 0;
            Set<String> shaderFiles = new TreeSet<>();
            Set<String> tileFiles = new TreeSet<>();

            for (String path : changed) {
                String ext = path.substring(path.lastIndexOf('.') + 1);
                // Fonts and the menu music are held directly by the game, with no cache to invalidate.
                if (path.startsWith("fonts/generated/") || (path.startsWith("audio/") && !ext.equals("wav"))) {
                    return "asset that is held directly changed (" + path + ")";
                }
                switch (ext) {
                    case "png", "jpg" -> {
                        Texture texture = textures.get(path);
                        if (texture != null && reloadTexture(texture, path)) textureCount++;
                    }
                    case "wav" -> {
                        Sound sound = sounds.remove(path);
                        if (sound != null) {
                            sound.dispose();
                            soundCount++;
                        }
                    }
                    case "frag", "vert" -> shaderFiles.add(path);
                    case "tmx", "tsx" -> tileFiles.add(path);
                    default -> {
                        // not something the game loads
                    }
                }
            }
            if (textureCount > 0) done.add(textureCount + (textureCount == 1 ? " texture" : " textures"));
            if (soundCount > 0) done.add(soundCount + (soundCount == 1 ? " sound" : " sounds"));
            if (!shaderFiles.isEmpty() && reloadShaders(shaderFiles)) done.add("shaders");
            if (!tileFiles.isEmpty()) reloadLevel(tileFiles, done);
            return null;
        } catch (ReflectiveOperationException e) {
            return "the dev hooks no longer match the game (" + e + ")";
        }
    }

    /** Re-uploads the file into the same Texture, so every region cut from it picks it up. */
    private boolean reloadTexture(Texture texture, String path) {
        try {
            texture.load(new FileTextureData(Gdx.files.internal(path), null, null, false));
            return true;
        } catch (Exception e) {
            log("could not reload " + path + ", keeping the old image: " + e.getMessage());
            return false;
        }
    }

    /** Drops the compiled shaders so they rebuild on next use, unless one of the sources is broken. */
    private boolean reloadShaders(Set<String> files) throws ReflectiveOperationException {
        Class<?> shaders = loader.loadClass(SHADERS_CLASS);
        Map<String, ShaderProgram> cache = getStatic(shaders, "cache");

        Set<String> toCheck = new TreeSet<>();
        for (String f : files) {
            if (f.endsWith(".frag")) toCheck.add(f.substring("shaders/".length()));
            else toCheck.addAll(cache.keySet()); // the shared vertex shader: every loaded effect
        }
        ShaderProgram.pedantic = false;
        for (String frag : toCheck) {
            ShaderProgram probe = new ShaderProgram(Gdx.files.internal("shaders/quad.vert"), Gdx.files.internal("shaders/" + frag));
            boolean ok = probe.isCompiled();
            String compileLog = probe.getLog();
            probe.dispose();
            if (!ok) {
                System.err.println("[dev] shader " + frag + " does not compile, keeping the old one:\n" + compileLog);
                return false;
            }
        }
        cache.values().forEach(ShaderProgram::dispose);
        cache.clear();
        return true;
    }

    /** Rebuilds the current level from disk and puts the player back where they were. */
    private void reloadLevel(Set<String> files, List<String> done) throws ReflectiveOperationException {
        Object level = get(game, "level");
        Object player = get(game, "player");
        if (level == null || player == null) return; // not in a level: the next load reads the new file
        String name = get(level, "name");
        boolean relevant = files.stream().anyMatch(f -> f.endsWith(".tsx") || f.equals("tiles/" + name + ".tmx"));
        if (!relevant) return;

        Vector2 position = (Vector2) player.getClass().getField("position").get(player);
        Vector2 saved = new Vector2(position);
        Method loadLevel = game.getClass().getDeclaredMethod("loadLevel");
        loadLevel.setAccessible(true);
        try {
            loadLevel.invoke(game);
        } catch (InvocationTargetException e) {
            // The old level is still in place: the constructor threw before it was assigned.
            System.err.println("[dev] level " + name + " does not load, keeping the old one: " + e.getCause());
            return;
        }
        Object reloadedLevel = get(game, "level");
        reloadedLevel.getClass().getMethod("reloadAt", Vector2.class).invoke(reloadedLevel, saved);
        done.add("level " + name);
    }

    // ---- reflection helpers ----

    @SuppressWarnings("unchecked")
    private static <T> T get(Object target, String field) throws ReflectiveOperationException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return (T) f.get(target);
            } catch (NoSuchFieldException ignored) {
                // try the superclass
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + field);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getStatic(Class<?> owner, String field) throws ReflectiveOperationException {
        Field f = owner.getDeclaredField(field);
        f.setAccessible(true);
        return (T) f.get(null);
    }
}
