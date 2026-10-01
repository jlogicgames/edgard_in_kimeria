package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jlogicsoftware.kimeria.effects.RippleEffect;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.effects.ShockwaveEffect;
import com.jlogicsoftware.kimeria.effects.SoftDot;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.ui.Hud;
import com.jlogicsoftware.kimeria.ui.Menu;
import com.jlogicsoftware.kimeria.ui.MenuBackdrop;
import com.jlogicsoftware.kimeria.ui.MenuItem;
import com.jlogicsoftware.kimeria.ui.Overlay;
import com.jlogicsoftware.kimeria.world.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole game in one place, playing the role Dart split across
 * {@code EdgardInKimeria} (the {@code FlameGame}) plus its overlay widgets,
 * and the role Rust split across {@code ui.rs}/{@code audio.rs}/{@code dev.rs}.
 * The world renders through a Y-DOWN camera on purpose: every position in
 * the original Flame/Bevy sources, and every coordinate in the raw .tmx
 * level files, is authored with (0,0) at the top-left and Y growing
 * downward. Matching that convention here means the ported physics,
 * collision and spawn code can be translated near line-for-line instead of
 * flipping signs throughout.
 */
public class KimeriaGame extends ApplicationAdapter implements GameContext {
    private enum UiState {WEB_START, MAIN_MENU, ABOUT, OPTIONS, LOADING, PLAYING, PAUSED, GAME_OVER}

    private static final float LOGICAL_W = 640f, LOGICAL_H = 360f;
    /** Black tint drawn over the menu backdrop on every menu screen (start, main, submenus). */
    private static final float MENU_TINT_ALPHA = 0.2f;
    private static final List<String> LEVEL_NAMES = List.of("forest-1", "forest");
    private static final float MUSIC_FADE_IN = 2f, MUSIC_FADE_OUT = 1f;

    private Assets assets;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;
    private Viewport viewport;
    private final Vector2 cameraTarget = new Vector2();
    private final Rectangle reusableVisibleRect = new Rectangle();

    private OrthographicCamera screenCamera;
    private Viewport uiViewport;

    private Player player;
    private Level level;
    private Hud hud;
    private Overlay overlay;
    private MenuBackdrop menuBackdrop;
    private final GamepadInput gamepad = new GamepadInput();

    /** Everything needed to begin a run; a save/load system would fill this from the chosen save. */
    private record StartData(int levelIndex, int coins) {
        static StartData newGame() {
            return new StartData(0, 0);
        }
    }

    private UiState uiState;
    private int currentLevelIndex = 0;
    private int coinsCollected = 0;
    private boolean gameStarted = false;

    private float timeScale = 1f;
    private boolean playSounds = true;
    private float soundVolume = 1f;
    private boolean debugDraw = false;
    private boolean invulnerable = false;
    private Language language = Language.ENGLISH;

    private float levelLoadDelay = -1f;

    private Music menuMusic;
    private float musicVolume = 0f;
    private float musicVolumeTarget = 0f;
    // Our own record of whether play() has been issued. Music.isPlaying() can stay false on the web
    // backend until the AudioContext is unlocked, so polling it would queue a new copy every frame.
    private boolean menuMusicPlaying = false;

    private Menu webStartMenu, mainMenu, aboutMenu, optionsMenu, pauseMenu, gameOverMenu;

    private FrameBuffer glitchBuffer;
    private float glitchTime = 0f;

    @Override
    public void create() {
        assets = new Assets();
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera();
        camera.setToOrtho(true, LOGICAL_W, LOGICAL_H);
        viewport = new FitViewport(LOGICAL_W, LOGICAL_H, camera);

        screenCamera = new OrthographicCamera();
        screenCamera.setToOrtho(true, LOGICAL_W, LOGICAL_H);
        screenCamera.update();
        uiViewport = new FitViewport(LOGICAL_W, LOGICAL_H, screenCamera);

        hud = new Hud(assets, this, LOGICAL_W, LOGICAL_H);
        overlay = new Overlay(assets, LOGICAL_W, LOGICAL_H);
        menuBackdrop = new MenuBackdrop();

        menuMusic = Gdx.audio.newMusic(Gdx.files.internal("audio/main_menu.mp3"));
        menuMusic.setLooping(true);
        menuMusic.setVolume(0f);

        buildMenus();
        // Browsers block audio until the page gets a user gesture, so the web build opens on a
        // one-button screen whose click/keypress unlocks it; other platforms go straight to the menu.
        uiState = isWeb() ? UiState.WEB_START : UiState.MAIN_MENU;
        // No player or level yet: they are built by startGame() once the player picks what to play.
    }

    private boolean isWeb() {
        return Gdx.app.getType() == Application.ApplicationType.WebGL;
    }

    private void buildMenus() {
        // No back action: there is nothing behind this screen to return to.
        webStartMenu = new Menu(null, null, List.of(
            new MenuItem(() -> Msg.PLAY.t(language), this::backToMainMenu)
        ), null);

        // Esc/B on the root menu has nowhere to go back to; park the selection on Exit
        // (a second Enter confirms) rather than quitting on a stray keypress. A browser tab
        // can't be closed by the page, so the web build has no Exit at all.
        List<MenuItem> mainItems = new ArrayList<>(List.of(
            new MenuItem(() -> Msg.PLAY.t(language), () -> startGame(StartData.newGame())),
            new MenuItem(() -> Msg.ABOUT.t(language), () -> uiState = UiState.ABOUT),
            new MenuItem(() -> Msg.OPTIONS.t(language), () -> uiState = UiState.OPTIONS)
        ));
        if (!isWeb()) {
            mainItems.add(new MenuItem(() -> Msg.EXIT.t(language), () -> Gdx.app.exit()));
        }
        mainMenu = new Menu(null, null, mainItems, () -> {
            if (!isWeb()) mainMenu.selected = mainMenu.items.size() - 1;
        });

        aboutMenu = new Menu(() -> Msg.ABOUT.t(language), null, List.of(
            new MenuItem(() -> Msg.BACK.t(language), this::backToMainMenu)
        ), this::backToMainMenu);

        // Only the desktop window can be switched; the web canvas and mobile screens
        // don't offer a windowed mode to opt into.
        List<MenuItem> optionItems = new ArrayList<>();
        optionItems.add(new MenuItem(() -> Msg.LANGUAGE_LABEL.t(language) + ": " + language.nativeName(), () -> language = language.next()));
        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            optionItems.add(new MenuItem(
                () -> Msg.DISPLAY_LABEL.t(language) + ": "
                    + (Gdx.graphics.isFullscreen() ? Msg.FULLSCREEN : Msg.WINDOWED).t(language),
                this::toggleFullscreen));
        }
        optionItems.add(new MenuItem(() -> Msg.BACK.t(language), this::backToMainMenu));
        optionsMenu = new Menu(() -> Msg.OPTIONS.t(language), null, optionItems, this::backToMainMenu);

        pauseMenu = new Menu(() -> Msg.PAUSE_MENU.t(language), null, List.of(
            new MenuItem(() -> Msg.RESUME.t(language), this::resumeGame),
            new MenuItem(() -> Msg.EXIT_TO_MENU.t(language), this::exitToMainMenu)
        ), this::resumeGame);

        gameOverMenu = new Menu(() -> Msg.GAME_OVER.t(language), null, List.of(
            new MenuItem(() -> Msg.PLAY_AGAIN.t(language), () -> startGame(StartData.newGame())),
            new MenuItem(() -> Msg.EXIT_TO_MENU.t(language), this::exitToMainMenu)
        ), this::exitToMainMenu);
    }

    /** Switches between fullscreen and the windowed size, and remembers the choice for the next launch. */
    private void toggleFullscreen() {
        boolean fullscreen = !Gdx.graphics.isFullscreen();
        if (fullscreen) {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        } else {
            Gdx.graphics.setWindowedMode(Settings.WINDOWED_WIDTH, Settings.WINDOWED_HEIGHT);
        }
        Preferences prefs = Gdx.app.getPreferences(Settings.PREFS_NAME);
        prefs.putBoolean(Settings.KEY_FULLSCREEN, fullscreen);
        prefs.flush();
    }

    private void resumeGame() {
        gameStarted = true;
        uiState = UiState.PLAYING;
    }

    private void exitToMainMenu() {
        releaseGame();
        uiState = UiState.MAIN_MENU;
    }

    /** Drops the level and player so nothing stays alive behind the menus. */
    private void releaseGame() {
        gameStarted = false;
        levelLoadDelay = -1f;
        level = null;
        player = null;
        coinsCollected = 0;
        currentLevelIndex = 0;
    }

    private void backToMainMenu() {
        uiState = UiState.MAIN_MENU;
    }

    /** Builds the player and level from {@code data}, behind the loading screen. */
    private void startGame(StartData data) {
        currentLevelIndex = Math.floorMod(data.levelIndex(), LEVEL_NAMES.size());
        coinsCollected = data.coins();
        player = new Player(assets, this, 0, 0);
        startLoadingLevel();
    }

    private void startLoadingLevel() {
        level = null; // the previous level is released while the loading screen shows
        gameStarted = false;
        levelLoadDelay = 1f; // mirrors the original's Future.delayed(1s) before (re)loading a level
        uiState = UiState.LOADING;
    }

    private void loadLevel() {
        Rectangle visible = new Rectangle();
        level = new Level(assets, this, player, LEVEL_NAMES.get(currentLevelIndex), () -> visibleWorldRect(visible));
    }

    private Rectangle visibleWorldRect(Rectangle out) {
        out.set(cameraTarget.x, cameraTarget.y, LOGICAL_W, LOGICAL_H);
        return out;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, false);
        uiViewport.update(width, height, false);
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        gamepad.update();
        UiState stateAtFrameStart = uiState;
        if (stateAtFrameStart != UiState.PLAYING && gamepad.confirm()) {
            gamepad.suppressJumpUntilRelease();
        }

        if (levelLoadDelay > 0) {
            levelLoadDelay -= dt;
            if (levelLoadDelay <= 0) {
                loadLevel();
                gameStarted = true;
                uiState = UiState.PLAYING;
            }
        }

        // The level (with the player, enemies, etc.) only runs/shows while
        // actually playing/paused/game-over -- the main menu and its About/
        // Options sub-screens show only the decorative fog+firefly backdrop
        // behind them, matching the Rust version, not a live view of the
        // level loading behind the scenes.
        boolean levelVisible = uiState == UiState.PLAYING || uiState == UiState.PAUSED || uiState == UiState.GAME_OVER;
        boolean menuBackdropActive = !levelVisible;
        // Stay silent until the start screen has been dismissed: the click that dismisses it is
        // what lets the browser start the music. Loading is the start of a run, so fade it out.
        boolean musicWanted = menuBackdropActive && uiState != UiState.WEB_START && uiState != UiState.LOADING;

        if (levelVisible && level != null) {
            level.update(dt * timeScale);
        }

        menuBackdrop.update(dt);
        updateMenuMusic(dt, musicWanted);
        handleDevHotkeys();

        camera.position.set(cameraTarget.x + LOGICAL_W / 2f, cameraTarget.y + LOGICAL_H / 2f, 0);
        camera.update();

        // The menu backdrop is a dark scene so the fog and fireflies stand out; gameplay keeps the sky.
        if (menuBackdropActive) {
            Gdx.gl.glClearColor(0.16f, 0.16f, 0.19f, 1f);
        } else {
            Gdx.gl.glClearColor(0.53f, 0.8f, 0.92f, 1f);
        }
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        boolean glitched = uiState == UiState.PAUSED && level != null;
        if (glitched) {
            glitchTime += dt;
            renderGlitchedWorld();
        } else {
            glitchTime = 0f;
            if (levelVisible && level != null) {
                renderWorld();
            }
        }

        // HUD + overlays draw in fixed logical screen space (not affected by world camera).
        batch.setProjectionMatrix(screenCamera.combined);
        batch.begin();
        if (uiState == UiState.PLAYING || uiState == UiState.PAUSED) {
            hud.render(batch);
        }
        if (menuBackdropActive) {
            menuBackdrop.render(batch);
        }
        overlay.beginFrame();
        renderOverlay(uiState == stateAtFrameStart);
        overlay.topRight(batch, Gdx.graphics.getFramesPerSecond() + " FPS", LOGICAL_W - 6, 6);
        overlay.flushText(batch);
        batch.end();
    }

    private void renderWorld() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        level.render(batch, visibleWorldRect(reusableVisibleRect));
        batch.end();

        if (debugDraw) {
            shapeRenderer.setProjectionMatrix(camera.combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            level.renderDebug(shapeRenderer);
            shapeRenderer.end();
        }
    }

    /**
     * While paused, the frozen world is drawn into an offscreen buffer and blitted
     * to the screen through the chromatic-aberration shader. The HUD and pause menu
     * draw afterwards in the regular UI pass, so they stay crisp. Only the paused
     * path pays for the extra pass; normal play renders straight to the screen.
     */
    private void renderGlitchedWorld() {
        ensureGlitchBuffer();
        glitchBuffer.begin();
        Gdx.gl.glClearColor(0.53f, 0.8f, 0.92f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        renderWorld();
        glitchBuffer.end(); // resets the GL viewport to the whole window
        uiViewport.apply();

        ShaderProgram shader = Shaders.load("chroma_glitch.frag");
        batch.setProjectionMatrix(screenCamera.combined);
        batch.disableBlending(); // straight copy: the buffer's alpha is not meaningful
        batch.begin();
        batch.setShader(shader);
        shader.setUniformf("uTime", glitchTime);
        shader.setUniformf("uIntensity", 1f);
        // The buffer is filled by the same Y-down camera as the screen, so its
        // (bottom-up) texture already lands the right way up here -- no flip.
        batch.draw(glitchBuffer.getColorBufferTexture(), 0, 0, LOGICAL_W, LOGICAL_H);
        batch.setShader(null);
        batch.end();
        batch.enableBlending();
    }

    /** (Re)creates the offscreen buffer to match the on-screen game area, in backbuffer pixels. */
    private void ensureGlitchBuffer() {
        float hdpi = Gdx.graphics.getBackBufferWidth() / (float) Math.max(1, Gdx.graphics.getWidth());
        int w = Math.max(1, Math.round(uiViewport.getScreenWidth() * hdpi));
        int h = Math.max(1, Math.round(uiViewport.getScreenHeight() * hdpi));
        if (glitchBuffer != null && glitchBuffer.getWidth() == w && glitchBuffer.getHeight() == h) return;
        if (glitchBuffer != null) glitchBuffer.dispose();
        glitchBuffer = new PixmapBackedFrameBuffer(w, h);
    }

    /**
     * A {@link FrameBuffer} whose color texture is allocated from a blank {@link Pixmap}.
     * The stock buffer allocates it with {@code glTexImage2D(..., null)}, which the
     * gdx-teavm web backend's dev (non-obfuscated) build crashes on
     * ({@code ArrayBufferView is not defined}); uploading a pixmap takes the same path
     * as every other texture and works on all backends.
     */
    private static final class PixmapBackedFrameBuffer extends FrameBuffer {
        PixmapBackedFrameBuffer(int width, int height) {
            super(Pixmap.Format.RGBA8888, width, height, false);
        }

        @Override
        protected Texture createTexture(FrameBufferTextureAttachmentSpec attachmentSpec) {
            // Runs from the super constructor, so read the size from the builder, not from fields.
            Pixmap blank = new Pixmap(bufferBuilder.width, bufferBuilder.height, Pixmap.Format.RGBA8888);
            Texture texture = new Texture(new PixmapTextureData(blank, null, false, true));
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
            return texture;
        }
    }

    private void updateMenuMusic(float dt, boolean want) {
        musicVolumeTarget = (want && playSounds) ? soundVolume : 0f;
        if (musicVolume < musicVolumeTarget) {
            musicVolume = Math.min(musicVolumeTarget, musicVolume + (1f / MUSIC_FADE_IN) * dt);
        } else if (musicVolume > musicVolumeTarget) {
            musicVolume = Math.max(musicVolumeTarget, musicVolume - (1f / MUSIC_FADE_OUT) * dt);
        }
        if (musicVolume > 0f && !menuMusicPlaying) {
            menuMusic.play();
            menuMusicPlaying = true;
        }
        if (musicVolume <= 0f && menuMusicPlaying) {
            menuMusic.stop();
            menuMusicPlaying = false;
        }
        menuMusic.setVolume(musicVolume);
    }

    /** F1 debug gizmos, F2 invulnerability, F3 shockwave+ripple, F4 next level, F5 checkpoint -- see Rust's dev.rs. */
    private void handleDevHotkeys() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) debugDraw = !debugDraw;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) invulnerable = !invulnerable;
        if (uiState != UiState.PLAYING || level == null) return;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            float cx = player.centerX(), cy = player.centerY();
            level.queueSpawn(new ShockwaveEffect(cx, cy, 0.6f, 64f, 8f));
            level.queueSpawn(new RippleEffect(cx, cy, 0.75f, 120f, 12f, 60f, 30f));
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
            loadNextLevel();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            player.debugTriggerCheckpoint();
        }
    }

    private Vector2 mouseLogical() {
        Vector2 v = new Vector2(Gdx.input.getX(), Gdx.input.getY());
        uiViewport.unproject(v);
        return v;
    }

    /**
     * @param acceptActions false on the frame the UI state changed under us (e.g. Esc paused
     *                      the game in {@code Player}), so that same keypress can't also
     *                      activate the pause menu's Back/Resume
     */
    private void renderOverlay(boolean acceptActions) {
        Vector2 mouse = mouseLogical();
        boolean clicked = Gdx.input.justTouched();
        // Only up/down (and Tab/Shift+Tab) move through these vertical button
        // lists -- left/right are left free for a possible horizontal
        // control (e.g. the Options language toggle) rather than doubling
        // as up/down, which reads backwards in a vertical list.
        boolean webStart = uiState == UiState.WEB_START;
        boolean navUp = Gdx.input.isKeyJustPressed(Input.Keys.UP)
            || (Gdx.input.isKeyJustPressed(Input.Keys.TAB) && Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT))
            || gamepad.menuUp();
        boolean navDown = Gdx.input.isKeyJustPressed(Input.Keys.DOWN)
            || (Gdx.input.isKeyJustPressed(Input.Keys.TAB) && !Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT))
            || gamepad.menuDown();
        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || gamepad.confirm();
        boolean back = Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || gamepad.back()
            || (uiState == UiState.PAUSED && gamepad.pausePressed());
        if (webStart) {
            // Only mouse/touch/keyboard count as a user gesture for browser autoplay rules; a
            // gamepad button press does not, so it must not dismiss the start screen.
            confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
            back = false;
            navUp = false;
            navDown = false;
        }
        if (!acceptActions) {
            confirm = false;
            back = false;
        }

        Menu menu = switch (uiState) {
            case WEB_START -> webStartMenu;
            case MAIN_MENU -> mainMenu;
            case ABOUT -> aboutMenu;
            case OPTIONS -> optionsMenu;
            case PAUSED -> pauseMenu;
            case GAME_OVER -> gameOverMenu;
            case LOADING, PLAYING -> null;
        };
        if (uiState == UiState.LOADING) {
            overlay.panel(batch, 0, 0, LOGICAL_W, LOGICAL_H, MENU_TINT_ALPHA);
            overlay.title(batch, Msg.LOADING.t(language), LOGICAL_W / 2f, LOGICAL_H / 2f - 15f);
            return;
        }
        if (menu == null) return;

        if (navUp || navDown) {
            if (navUp) menu.moveSelection(-1);
            if (navDown) menu.moveSelection(1);
            playHoverSound();
        }
        Runnable onHover = this::playHoverSound;
        Runnable onActivate = () -> playSound("button_click");

        float cx = LOGICAL_W / 2f;
        // Every menu screen shares this full-screen tint over the fog/firefly backdrop -- no solid card.
        overlay.panel(batch, 0, 0, LOGICAL_W, LOGICAL_H, MENU_TINT_ALPHA);
        if (webStart) {
            overlay.title(batch, Msg.TITLE.t(language), cx, 90);
            overlay.buttons(batch, menu, cx, 170f, mouse, clicked, confirm, back, () -> {}, onActivate);
            return; // no menu hint: it mentions gamepad/Esc controls that do nothing here
        }
        if (uiState == UiState.MAIN_MENU) {
            overlay.title(batch, Msg.TITLE.t(language), cx, 14);

            float startY = 70f;
            overlay.buttons(batch, menu, cx, startY, mouse, clicked, confirm, back, onHover, onActivate);
            float afterButtons = startY + Overlay.buttonsHeight(menu) + 14f;
            overlay.body(batch, Msg.CONTROLS_HELP.t(language), cx, afterButtons, 480f);
        } else {
            float panelW = 320f;
            String aboutBody = uiState == UiState.ABOUT ? Msg.ABOUT_BODY.t(language) : null;
            float bodyH = aboutBody != null ? overlay.bodyHeight(aboutBody, panelW - 30f) + 20f : 0f;
            float contentH = (menu.titleText() != null ? 44f : 10f) + bodyH + Overlay.buttonsHeight(menu) + 40f;
            float panelH = Math.max(160f, contentH);
            float panelX = cx - panelW / 2f;
            float panelY = LOGICAL_H / 2f - panelH / 2f;

            float y = panelY + 30f;
            if (menu.titleText() != null) {
                overlay.title(batch, menu.titleText(), cx, y);
                y += 40f;
            }
            if (aboutBody != null) {
                overlay.body(batch, aboutBody, cx, y, panelW - 30f);
                y += bodyH;
            }
            overlay.buttons(batch, menu, cx, y, mouse, clicked, confirm, back, onHover, onActivate);
        }

        overlay.hint(batch, Msg.MENU_HINT.t(language), cx, LOGICAL_H - 12);
    }

    @Override
    public void dispose() {
        assets.dispose();
        batch.dispose();
        shapeRenderer.dispose();
        menuMusic.dispose();
        if (glitchBuffer != null) glitchBuffer.dispose();
        Shaders.dispose();
        SoftDot.dispose();
    }

    // ---- GameContext ----

    @Override
    public Assets assets() {
        return assets;
    }

    @Override
    public GamepadInput gamepad() {
        return gamepad;
    }

    @Override
    public boolean invulnerable() {
        return invulnerable;
    }

    @Override
    public void setInvulnerable(boolean value) {
        invulnerable = value;
    }

    @Override
    public boolean debugDraw() {
        return debugDraw;
    }

    @Override
    public void setDebugDraw(boolean value) {
        debugDraw = value;
    }

    @Override
    public boolean playSounds() {
        return playSounds;
    }

    @Override
    public float soundVolume() {
        return soundVolume;
    }

    @Override
    public void playSound(String name) {
        if (!playSounds) return;
        Sound sound = assets.sound("audio/" + name + ".wav");
        sound.play(soundVolume);
    }

    /** Quiet hover/focus blip, distinct from {@code button_click}'s louder press sound -- see Rust's ui.rs. */
    private void playHoverSound() {
        if (!playSounds) return;
        assets.sound("audio/button_click.wav").play(soundVolume * 0.35f);
    }

    @Override
    public boolean isGameStarted() {
        return gameStarted;
    }

    @Override
    public void setGameStarted(boolean started) {
        gameStarted = started;
    }

    @Override
    public void addCoin() {
        coinsCollected++;
    }

    @Override
    public int coinsCollected() {
        return coinsCollected;
    }

    @Override
    public void loadNextLevel() {
        if (currentLevelIndex < LEVEL_NAMES.size() - 1) {
            currentLevelIndex++;
        } else {
            currentLevelIndex = 0;
        }
        startLoadingLevel();
    }

    @Override
    public void triggerGameOver() {
        uiState = UiState.GAME_OVER;
    }

    @Override
    public void pause() {
        gameStarted = !gameStarted;
        uiState = gameStarted ? UiState.PLAYING : UiState.PAUSED;
    }

    @Override
    public boolean isSlowTime() {
        return timeScale < 1f;
    }

    @Override
    public void setSlowTime() {
        timeScale = 0.5f;
    }

    @Override
    public void setNormalTime() {
        timeScale = 1f;
    }

    @Override
    public void moveCameraTo(Vector2 target, float speed) {
        float dt = Gdx.graphics.getDeltaTime();
        float maxStep = speed * dt;
        float dx = target.x - cameraTarget.x;
        float dy = target.y - cameraTarget.y;
        cameraTarget.x += clampAbs(dx, maxStep);
        cameraTarget.y += clampAbs(dy, maxStep);
    }

    private static float clampAbs(float value, float max) {
        if (value > max) return max;
        if (value < -max) return -max;
        return value;
    }

    @Override
    public Vector2 logicalResolution() {
        return new Vector2(LOGICAL_W, LOGICAL_H);
    }
}
