package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
import com.jlogicsoftware.kimeria.input.IntroInput;
import com.jlogicsoftware.kimeria.ui.IntroScreen;
import com.jlogicsoftware.kimeria.ui.HealerDialogue;
import com.jlogicsoftware.kimeria.ui.HealerDialogueView;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Localization;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.ui.Hud;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import com.jlogicsoftware.kimeria.ui.Menu;
import com.jlogicsoftware.kimeria.ui.MenuBackdrop;
import com.jlogicsoftware.kimeria.ui.MenuItem;
import com.jlogicsoftware.kimeria.ui.Overlay;
import com.jlogicsoftware.kimeria.world.Level;
import com.jlogicsoftware.kimeria.world.RoomFade;

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
    private enum UiState {WEB_START, MAIN_MENU, CONFIRM_NEW_GAME, INTRO, ENDING, ABOUT, OPTIONS, ASSIST, LOADING, PLAYING, HEALER, PAUSED}

    private static final float LOGICAL_W = 640f, LOGICAL_H = 360f;
    /** Black tint drawn over the menu backdrop on every menu screen (start, main, submenus). */
    private static final float MENU_TINT_ALPHA = 0.2f;
    private static final List<String> LEVEL_NAMES = List.of("forest", "forest-1");
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
    private TouchControls touchControls;
    private Overlay overlay;
    private MenuBackdrop menuBackdrop;
    private final GamepadInput gamepad = new GamepadInput();

    /** Everything needed to begin a run; Continue fills this from saved progress. */
    private record StartData(int levelIndex, int coins) {
        static StartData newGame() {
            return new StartData(0, 0);
        }
    }

    private SavedProgress savedProgress;
    private UiState uiState;
    private int currentLevelIndex = 0;
    private int coinsCollected = 0;
    private final ArtifactInventory artifacts = new ArtifactInventory();
    /** Coin count when the level began; Restart Level goes back to it. */
    private int levelStartCoins = 0;
    private int roomStartCoins = 0;
    private final RoomFade roomFade = new RoomFade();
    private boolean gameStarted = false;
    private boolean pendingFullscreenToggle = false;

    private float timeScale = 1f;
    /** Set by a death; the room is rebuilt at the start of the next frame (see {@link #rebuildRoom()}). */
    private boolean roomResetPending = false;
    private final Vector2 panFrom = new Vector2(), panTo = new Vector2();
    private float panDuration = 0f, panElapsed = 0f;
    private boolean cameraPanning = false;
    private boolean playSounds = true;
    private float soundVolume = 1f;
    private boolean debugDraw = false;
    private boolean invulnerable = false;
    private Settings settings;
    private Language language = Settings.DEFAULT_LANGUAGE;
    private final TrapWarnings trapWarnings = new TrapWarnings();
    private boolean leftHanded = Settings.DEFAULT_LEFT_HANDED;

    private float levelLoadDelay = -1f;

    private HealerDialogue healer;
    private HealerDialogueView healerView;
    private final IntroInput dialogueInput = new IntroInput();
    private boolean pausedHealer;
    private IntroScreen intro;
    private final IntroInput introInput = new IntroInput();
    private Music introMusic;
    private boolean introMusicPlaying;

    private Music menuMusic;
    private float musicVolume = 0f;
    private float musicVolumeTarget = 0f;
    // Our own record of whether play() has been issued. Music.isPlaying() can stay false on the web
    // backend until the AudioContext is unlocked, so polling it would queue a new copy every frame.
    private boolean menuMusicPlaying = false;

    private boolean aboutRules;
    private Menu webStartMenu, mainMenu, aboutMenu, optionsMenu, assistMenu, pauseMenu, confirmNewGameMenu;

    /** Offscreen copy of the world, filled only while a post effect (pause glitch, coin ripple) is live. */
    private FrameBuffer sceneBuffer;
    private float glitchTime = 0f;

    @Override
    public void create() {
        Localization.current();
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
        touchControls = new TouchControls(assets, this, uiViewport, LOGICAL_W, LOGICAL_H);
        overlay = new Overlay(assets, LOGICAL_W, LOGICAL_H);
        healerView = new HealerDialogueView();
        menuBackdrop = new MenuBackdrop();

        menuMusic = Gdx.audio.newMusic(Gdx.files.internal("audio/main_menu.mp3"));
        menuMusic.setLooping(true);
        menuMusic.setVolume(0f);
        // Preload before New Game so the first intro frame never loads text, fonts or audio.
        introMusic = Gdx.audio.newMusic(Gdx.files.internal("audio/intro.wav"));
        introMusic.setLooping(true);
        introMusic.setVolume(0f);
        Shaders.whiteQuad();

        settings = Settings.load(() -> Gdx.app.getPreferences(Settings.PREFS_NAME));
        language = settings.get(Settings.LANGUAGE);
        leftHanded = settings.get(Settings.LEFT_HANDED);
        trapWarnings.load(settings);
        savedProgress = new SavedProgress(settings.preferences(), LEVEL_NAMES.size());
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
            new MenuItem(() -> Msg.CONTINUE.t(language), this::continueGame, savedProgress::exists),
            new MenuItem(() -> Msg.NEW_GAME.t(language), this::requestNewGame),
            new MenuItem(() -> Msg.ABOUT.t(language), () -> uiState = UiState.ABOUT),
            new MenuItem(() -> Msg.OPTIONS.t(language), () -> uiState = UiState.OPTIONS)
        ));
        if (!isWeb()) {
            mainItems.add(new MenuItem(() -> Msg.EXIT.t(language), () -> Gdx.app.exit()));
        }
        mainMenu = new Menu(null, null, mainItems, () -> {
            if (!isWeb()) mainMenu.selected = mainMenu.items.size() - 1;
        });

        confirmNewGameMenu = new Menu(() -> Msg.NEW_GAME.t(language),
            () -> Msg.NEW_GAME_CONFIRMATION.t(language), List.of(
                new MenuItem(() -> Msg.CONFIRM.t(language), this::newGame),
                new MenuItem(() -> Msg.CANCEL.t(language), this::backToMainMenu)
            ), this::backToMainMenu);

        aboutMenu = new Menu(() -> Msg.ABOUT.t(language), null, List.of(
            new MenuItem(() -> (aboutRules ? Msg.CONTROLS : Msg.RULES).t(language), () -> aboutRules = !aboutRules),
            new MenuItem(() -> Msg.BACK.t(language), this::backToMainMenu)
        ), this::backToMainMenu);

        // Only the desktop window can be switched; the web canvas and mobile screens
        // don't offer a windowed mode to opt into.
        List<MenuItem> optionItems = new ArrayList<>();
        optionItems.add(new MenuItem(() -> Msg.LANGUAGE_LABEL.t(language) + ": " + language.nativeName(), this::toggleLanguage));
        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            optionItems.add(new MenuItem(
                () -> Msg.DISPLAY_LABEL.t(language) + ": "
                    + (Gdx.graphics.isFullscreen() ? Msg.FULLSCREEN : Msg.WINDOWED).t(language),
                this::toggleFullscreen));
        }
        optionItems.add(new MenuItem(
            () -> Msg.LEFT_HANDED_LABEL.t(language) + ": " + (leftHanded ? Msg.ON : Msg.OFF).t(language),
            this::toggleLeftHanded));
        optionItems.add(new MenuItem(() -> Msg.ASSIST.t(language), () -> uiState = UiState.ASSIST));
        optionItems.add(new MenuItem(() -> Msg.BACK.t(language), this::backToMainMenu));
        optionsMenu = new Menu(() -> Msg.OPTIONS.t(language), null, optionItems, this::backToMainMenu);

        assistMenu = new Menu(() -> Msg.ASSIST.t(language), null, List.of(
            new MenuItem(
                () -> Msg.LONGER_TRAP_WARNINGS.t(language) + ": "
                    + (trapWarnings.longer() ? Msg.ON : Msg.OFF).t(language),
                () -> trapWarnings.toggle(settings)),
            new MenuItem(() -> Msg.BACK.t(language), () -> uiState = UiState.OPTIONS)
        ), () -> uiState = UiState.OPTIONS, 440f);

        pauseMenu = new Menu(() -> Msg.PAUSE_MENU.t(language), null, List.of(
            new MenuItem(() -> Msg.RESUME.t(language), this::resumeGame),
            new MenuItem(() -> Msg.RESTART_LEVEL.t(language), this::restartLevel),
            new MenuItem(() -> Msg.EXIT_TO_MENU.t(language), this::exitToMainMenu)
        ), this::resumeGame);
    }

    /**
     * Asks for the window mode to be switched. The switch itself is deferred to the start of the
     * next {@link #render()}: the menu fires from inside a render pass, and on LWJGL3 changing the
     * window mode refreshes the window by re-entering {@code render()} while the batch is open.
     */
    private void toggleFullscreen() {
        pendingFullscreenToggle = true;
    }

    /** Switches between fullscreen and the windowed size, and remembers the choice for the next launch. */
    private void applyFullscreenToggle() {
        boolean fullscreen = !Gdx.graphics.isFullscreen();
        if (fullscreen) {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        } else {
            Gdx.graphics.setWindowedMode(Settings.WINDOWED_WIDTH, Settings.WINDOWED_HEIGHT);
        }
        settings.set(Settings.FULLSCREEN, Gdx.graphics.isFullscreen());
    }

    private void toggleLanguage() {
        language = language.next();
        settings.set(Settings.LANGUAGE, language);
    }

    /** Flips left-handed mode and remembers the choice; touch layout and key bindings read it live. */
    private void toggleLeftHanded() {
        leftHanded = !leftHanded;
        settings.set(Settings.LEFT_HANDED, leftHanded);
    }

    @Override
    public boolean leftHanded() {
        return leftHanded;
    }

    @Override
    public TrapWarnings trapWarnings() {
        return trapWarnings;
    }

    private void resumeGame() {
        if (player != null) player.resetJumpInput();
        if (pausedHealer) {
            pausedHealer = false;
            dialogueInput.reset(Gdx.input);
            uiState = UiState.HEALER;
            gameStarted = false;
        } else {
            gameStarted = true;
            uiState = UiState.PLAYING;
        }
    }

    /**
     * Starts the current level over from its beginning, as a fresh attempt (D3): a new player, a
     * rebuilt level and the coin count from when the level began. The save is not touched.
     */
    private void restartLevel() {
        artifacts.restartLevel();
        coinsCollected = levelStartCoins;
        pauseMenu.selected = 0; // so the next pause does not open on Restart Level
        player = new Player(assets, this, 0, 0);
        startLoadingLevel();
    }

    private void exitToMainMenu() {
        releaseGame();
        backToMainMenu();
    }

    /** Drops the level and player so nothing stays alive behind the menus. */
    private void releaseGame() {
        healer = null;
        pausedHealer = false;
        artifacts.clear();
        roomFade.cancel();
        gameStarted = false;
        levelLoadDelay = -1f;
        level = null;
        player = null;
        coinsCollected = 0;
        currentLevelIndex = 0;
    }

    private void backToMainMenu() {
        savedProgress.reload();
        mainMenu.selected = 0;
        mainMenu.ensureVisibleSelection();
        uiState = UiState.MAIN_MENU;
    }

    private void continueGame() {
        if (savedProgress.exists()) startGame(new StartData(savedProgress.nextLevelIndex(), 0));
    }

    private void requestNewGame() {
        if (savedProgress.exists()) {
            confirmNewGameMenu.selected = 1; // Cancel is the safe default.
            uiState = UiState.CONFIRM_NEW_GAME;
        } else {
            newGame();
        }
    }

    private void newGame() {
        savedProgress.clear();
        openStory(Msg.INTRO_STORY, UiState.INTRO);
    }

    private boolean storyScreenActive() { return uiState == UiState.INTRO || uiState == UiState.ENDING; }

    /** Opening and ending share the same text scroll, fade, music and fresh-input rules. */
    private void openStory(Msg message, UiState state) {
        String text = message.t(language);
        intro = new IntroScreen(text,
            overlay.bodyHeight(text, LOGICAL_W - IntroScreen.TEXT_MARGIN * 2f), LOGICAL_H);
        introInput.reset(Gdx.input);
        uiState = state;
    }

    private void updateStory(float dt) {
        if (introInput.skipPressed(Gdx.input) || gamepad.anyButtonPressed()) intro.skip();
        // Let the menu track fade to silence before starting the story's envelope.
        if (musicVolume <= 0f || intro.state() == IntroScreen.State.FADE_OUT) intro.update(dt);
        updateIntroMusic();
        if (intro.done()) {
            intro = null;
            if (uiState == UiState.ENDING) {
                savedProgress.clear();
                backToMainMenu();
            } else startGame(StartData.newGame());
        }
    }

    /** Builds the player and level from {@code data}, behind the loading screen. */
    private void startGame(StartData data) {
        artifacts.clear();
        currentLevelIndex = Math.floorMod(data.levelIndex(), LEVEL_NAMES.size());
        coinsCollected = data.coins();
        player = new Player(assets, this, 0, 0);
        startLoadingLevel();
    }

    private void startLoadingLevel() {
        healer = null;
        pausedHealer = false;
        level = null; // the previous level is released while the loading screen shows
        gameStarted = false;
        // Death's slow motion must not leak into the next level, and a
        // reset still waiting for its frame (a death just before Restart or Exit) is moot now.
        timeScale = 1f;
        roomResetPending = false;
        roomFade.cancel();
        cameraPanning = false;
        levelLoadDelay = 1f; // mirrors the original's Future.delayed(1s) before (re)loading a level
        uiState = UiState.LOADING;
    }

    private void loadLevel() {
        artifacts.beginLevel();
        levelStartCoins = coinsCollected;
        roomStartCoins = coinsCollected;
        Rectangle visible = new Rectangle();
        level = new Level(assets, this, player, LEVEL_NAMES.get(currentLevelIndex), () -> visibleWorldRect(visible));
    }

    /** Rebuilds only the active room, at a frame boundary without reloading the map. */
    private void rebuildRoom() {
        roomResetPending = false;
        coinsCollected = roomStartCoins;
        timeScale = 1f;
        player = new Player(assets, this, 0, 0);
        level.resetCurrentRoom(player);
        player.arriveAfterReset();
    }

    private void enterNextRoom() {
        if (level.healerAhead()) {
            roomFade.cancel();
            level.prepareHealer();
            roomStartCoins = coinsCollected;
            openHealer(false);
            return;
        }
        roomStartCoins = coinsCollected;
        timeScale = 1f;
        player = new Player(assets, this, 0, 0);
        level.enterNextRoom(player);
        cameraTarget.set(player.roomCameraTarget());
        cameraPanning = false;
        gamepad.suppressJumpUntilRelease();
    }

    private void openHealer(boolean paid) {
        healer = new HealerDialogue(artifacts.hasIngredient(level.name, ItemType.CHICKEN),
            artifacts.hasIngredient(level.name, ItemType.KEY),
            level.forestCoinTotal() > 0 && level.forestCoinsSecured() == level.forestCoinTotal(),
            level.canBrew(), paid);
        dialogueInput.reset(Gdx.input);
        gameStarted = false;
        uiState = UiState.HEALER;
    }

    private void chooseHealer(HealerDialogue.Choice choice) {
        switch (choice) {
            case RETURN -> {
                player = new Player(assets, this, 0, 0);
                level.returnForCollection(player);
                roomStartCoins = coinsCollected;
                timeScale = 1;
                cameraTarget.set(player.roomCameraTarget());
                cameraPanning = false;
                healer = null;
                gamepad.suppressJumpUntilRelease();
                gameStarted = true;
                uiState = UiState.PLAYING;
            }
            case BREW -> { if (level.brewElixir()) { roomStartCoins = coinsCollected; openHealer(true); } }
            case CONTINUE -> { if (healer != null && healer.postVictory()) completeLevel(); }
            case FIGHT -> {
                level.commitBossEncounter();
                healer = null;
                gamepad.suppressJumpUntilRelease();
                gameStarted = true;
                uiState = UiState.PLAYING;
                roomFade.start();
            }
        }
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
        // Cleared before applying: the mode switch may re-enter render() on desktop.
        if (pendingFullscreenToggle) {
            pendingFullscreenToggle = false;
            applyFullscreenToggle();
        }
        float dt = Gdx.graphics.getDeltaTime();
        gamepad.update(leftHanded);
        UiState stateAtFrameStart = uiState;
        touchControls.update(stateAtFrameStart == UiState.PLAYING);
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

        if (storyScreenActive() && uiState == stateAtFrameStart) updateStory(dt);

        if (uiState == UiState.HEALER && stateAtFrameStart == UiState.HEALER) updateHealer(dt);

        // The level (with the player, enemies, etc.) only runs/shows while
        // actually playing/paused -- the main menu and its About/
        // Options sub-screens show only the decorative fog+firefly backdrop
        // behind them, matching the Rust version, not a live view of the
        // level loading behind the scenes.
        boolean levelVisible = uiState == UiState.PLAYING || (uiState == UiState.PAUSED && !pausedHealer);
        boolean menuBackdropActive = !levelVisible && !storyScreenActive() && uiState != UiState.HEALER
            && !(uiState == UiState.PAUSED && pausedHealer);
        // Stay silent until the start screen has been dismissed: the click that dismisses it is
        // what lets the browser start the music. Loading is the start of a run, so fade it out.
        boolean musicWanted = menuBackdropActive && uiState != UiState.WEB_START && uiState != UiState.LOADING;

        // Age existing effects before combat/reset creates new ones: a slow frame must not
        // consume a newly landed hit's entire flash before it has been rendered once.
        if (levelVisible && level != null && gameStarted && !roomFade.active()
            && stateAtFrameStart != UiState.HEALER) level.updatePresentation(dt);
        if (roomResetPending && uiState == UiState.PLAYING) {
            rebuildRoom();
        }
        if (levelVisible && level != null && gameStarted && stateAtFrameStart != UiState.HEALER) {
            if (roomFade.active()) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || gamepad.pausePressed()) togglePause();
                else roomFade.update(dt, this::enterNextRoom);
            } else {
                level.update(dt * timeScale);
                if (gameStarted) advanceCameraPan(dt);
                if (gameStarted && !roomResetPending && level.atRoomExit()) {
                    player.resetJumpInput();
                    level.clearCameraEffects();
                    roomFade.start();
                }
            }
        }

        // A victory exit can open dialogue inside Level.update on this frame.
        levelVisible = uiState == UiState.PLAYING || (uiState == UiState.PAUSED && !pausedHealer);
        menuBackdropActive = !levelVisible && !storyScreenActive() && uiState != UiState.HEALER
            && !(uiState == UiState.PAUSED && pausedHealer);
        menuBackdrop.update(dt);
        updateMenuMusic(dt, musicWanted);
        handleDevHotkeys();

        Vector2 shake = level != null ? level.cameraShakeOffset(visibleWorldRect(new Rectangle())) : new Vector2();
        camera.position.set(cameraTarget.x + LOGICAL_W / 2f + shake.x, cameraTarget.y + LOGICAL_H / 2f + shake.y, 0);
        camera.update();

        // The menu backdrop is a dark scene so the fog and fireflies stand out; gameplay keeps the sky.
        if (storyScreenActive() || uiState == UiState.HEALER || pausedHealer) {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        } else if (menuBackdropActive) {
            Gdx.gl.glClearColor(0.16f, 0.16f, 0.19f, 1f);
        } else {
            Gdx.gl.glClearColor(0.53f, 0.8f, 0.92f, 1f);
        }
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        boolean glitched = uiState == UiState.PAUSED && (pausedHealer || level != null);
        RippleEffect ripple = levelVisible && level != null ? level.activeRipple() : null;
        if (glitched) {
            glitchTime += dt;
        } else {
            glitchTime = 0f;
        }
        if (glitched || ripple != null) {
            renderPostProcessedScene(glitched, ripple);
        } else if (levelVisible && level != null) {
            renderWorld();
        }

        // HUD + overlays draw in fixed logical screen space (not affected by world camera).
        batch.setProjectionMatrix(screenCamera.combined);
        batch.begin();
        if (levelVisible) {
            hud.render(batch, level != null ? level.boss() : null,
                level != null && artifacts.bossWeaponDamageMultiplier(level.name) > 1f);
            if (uiState == UiState.PLAYING) touchControls.render(batch);
        }
        if (menuBackdropActive) {
            menuBackdrop.render(batch);
        }
        if (levelVisible && roomFade.active()) overlay.panel(batch, 0, 0, LOGICAL_W, LOGICAL_H, roomFade.alpha());
        overlay.beginFrame();
        renderOverlay(uiState == stateAtFrameStart);
        if (!roomFade.active() && !storyScreenActive() && uiState != UiState.HEALER && !pausedHealer) overlay.topRight(batch, Gdx.graphics.getFramesPerSecond() + " FPS", LOGICAL_W - 6, 6);
        overlay.flushText(batch);
        // Overlay text is queued in a Y-up pass; the fade must cover it after that pass.
        if (storyScreenActive()) overlay.panel(batch, 0, 0, LOGICAL_W, LOGICAL_H, intro.alpha());
        batch.end();
    }

    /** Render only: the paused background must not poll input or advance its text. */
    private void renderHealerScene() {
        batch.setProjectionMatrix(screenCamera.combined);
        batch.begin();
        overlay.beginFrame();
        healerView.render(batch, overlay, healer, language, level, artifacts);
        overlay.flushText(batch);
        batch.end();
    }

    private void updateHealer(float dt) {
        boolean fresh = dialogueInput.skipPressed(Gdx.input);
        if (dialogueInput.keyPressed(Input.Keys.ESCAPE)) {
            pausedHealer = true;
            uiState = UiState.PAUSED;
            return;
        }
        healer.update(dt);
        boolean up = dialogueInput.keyPressed(Input.Keys.UP) || dialogueInput.keyPressed(Input.Keys.W)
            || dialogueInput.keyPressed(Input.Keys.A) || dialogueInput.keyPressed(Input.Keys.LEFT)
            || gamepad.menuUp() || gamepad.menuLeft();
        boolean down = dialogueInput.keyPressed(Input.Keys.DOWN) || dialogueInput.keyPressed(Input.Keys.S)
            || dialogueInput.keyPressed(Input.Keys.D) || dialogueInput.keyPressed(Input.Keys.RIGHT)
            || gamepad.menuDown() || gamepad.menuRight();
        boolean confirm = dialogueInput.keyPressed(Input.Keys.ENTER) || dialogueInput.keyPressed(Input.Keys.SPACE)
            || dialogueInput.keyPressed(leftHanded ? Input.Keys.Z : Input.Keys.J)
            || dialogueInput.keyPressed(leftHanded ? Input.Keys.X : Input.Keys.K)
            || dialogueInput.keyPressed(leftHanded ? Input.Keys.C : Input.Keys.L)
            || gamepad.confirm() || gamepad.actionPressed() || gamepad.shootPressed();
        Vector2 mouse = mouseLogical();
        HealerDialogue.Choice choice = healer.interact(language,
            fresh || gamepad.anyButtonPressed() || up || down,
            up ? -1 : down ? 1 : 0, confirm,
            dialogueInput.touchPressed() ? healerView.choiceAt(mouse, healer.choices().size()) : -1);
        if (choice != null) chooseHealer(choice);
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
     * Draws the current background (world or frozen healer dialogue) into an offscreen buffer
     * and blits it to the screen through
     * {@code screen_effects.frag}, which applies the pause glitch and/or the coin ripple's
     * scene distortion in one pass (ripple first, then the chroma shift). The HUD and pause
     * menu draw afterwards in the regular UI pass, so they stay crisp. Only frames with a
     * live effect pay for the extra pass; otherwise the world renders straight to the screen.
     *
     * @param ripple the ripple to distort the scene with, or null for none
     */
    private void renderPostProcessedScene(boolean glitched, RippleEffect ripple) {
        ensureSceneBuffer();
        sceneBuffer.begin();
        if (pausedHealer) Gdx.gl.glClearColor(0, 0, 0, 1);
        else Gdx.gl.glClearColor(0.53f, 0.8f, 0.92f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (pausedHealer) renderHealerScene();
        else renderWorld();
        sceneBuffer.end(); // resets the GL viewport to the whole window
        uiViewport.apply();

        ShaderProgram shader = Shaders.load("screen_effects.frag");
        batch.setProjectionMatrix(screenCamera.combined);
        batch.disableBlending(); // straight copy: the buffer's alpha is not meaningful
        batch.begin();
        batch.setShader(shader);
        // The shader is shared by both effects, so every uniform is set on every frame.
        shader.setUniformf("uTime", glitchTime);
        shader.setUniformf("uIntensity", glitched ? 1f : 0f);
        if (ripple != null) {
            ripple.apply(shader, cameraTarget.x, cameraTarget.y, LOGICAL_W, LOGICAL_H);
        } else {
            RippleEffect.disable(shader);
        }
        // The buffer is filled by the same Y-down camera as the screen, so its
        // (bottom-up) texture already lands the right way up here -- no flip.
        batch.draw(sceneBuffer.getColorBufferTexture(), 0, 0, LOGICAL_W, LOGICAL_H);
        batch.setShader(null);
        batch.end();
        batch.enableBlending();
    }

    /** (Re)creates the offscreen buffer to match the on-screen game area, in backbuffer pixels. */
    private void ensureSceneBuffer() {
        float hdpi = Gdx.graphics.getBackBufferWidth() / (float) Math.max(1, Gdx.graphics.getWidth());
        int w = Math.max(1, Math.round(uiViewport.getScreenWidth() * hdpi));
        int h = Math.max(1, Math.round(uiViewport.getScreenHeight() * hdpi));
        if (sceneBuffer != null && sceneBuffer.getWidth() == w && sceneBuffer.getHeight() == h) return;
        if (sceneBuffer != null) sceneBuffer.dispose();
        sceneBuffer = new PixmapBackedFrameBuffer(w, h);
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

    private void updateIntroMusic() {
        float volume = playSounds ? soundVolume * intro.volume() : 0f;
        introMusic.setVolume(volume);
        if (volume > 0f && !introMusicPlaying) {
            introMusic.play();
            introMusicPlaying = true;
        } else if (volume <= 0f && introMusicPlaying) {
            introMusic.stop();
            introMusicPlaying = false;
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

    /** F1 debug gizmos, F2 invulnerability, F3 shockwave+ripple, F4 next level, F5 checkpoint, F6 next room. */
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
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5) && player != null) {
            player.debugTriggerCheckpoint();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F6) && level != null && level.hasNextRoom()
            && !roomFade.active() && player.canFight()) {
            player.resetJumpInput();
            level.clearCameraEffects();
            roomFade.start();
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
        if (uiState == UiState.HEALER) {
            healerView.render(batch, overlay, healer, language, level, artifacts);
            return;
        }
        if (storyScreenActive()) {
            overlay.body(batch, intro.text(), LOGICAL_W / 2f, intro.textTop(),
                LOGICAL_W - IntroScreen.TEXT_MARGIN * 2f);
            return;
        }
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
            case CONFIRM_NEW_GAME -> confirmNewGameMenu;
            case ABOUT -> aboutMenu;
            case OPTIONS -> optionsMenu;
            case ASSIST -> assistMenu;
            case PAUSED -> pauseMenu;
            case INTRO, ENDING, LOADING, PLAYING, HEALER -> null;
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
            float titleY = 14f, titleBottom = 54f, hintTop = LOGICAL_H - 24f;
            overlay.title(batch, Msg.TITLE.t(language), cx, titleY);

            // Centre the button list in the space between the title and the hint line.
            float startY = titleBottom + (hintTop - titleBottom - Overlay.buttonsHeight(menu)) / 2f;
            overlay.buttons(batch, menu, cx, startY, mouse, clicked, confirm, back, onHover, onActivate);
        } else {
            float panelW = uiState == UiState.ABOUT ? 610f : 320f;
            String aboutBody = uiState == UiState.ABOUT ? (aboutRules ? Msg.ABOUT_RULES.t(language) : aboutText()) : menu.bodyText();
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

    /** About text for the current handedness; the gamepad line appears only while a controller is connected. */
    private String aboutText() {
        String body = (leftHanded ? Msg.ABOUT_BODY_LEFT_HANDED : Msg.ABOUT_BODY).t(language);
        if (!gamepad.isConnected()) return body;
        return body + "\n\n" + (leftHanded ? Msg.ABOUT_GAMEPAD_LEFT_HANDED : Msg.ABOUT_GAMEPAD).t(language);
    }

    @Override
    public void dispose() {
        assets.dispose();
        batch.dispose();
        shapeRenderer.dispose();
        menuMusic.dispose();
        introMusic.dispose();
        if (sceneBuffer != null) sceneBuffer.dispose();
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
    public TouchControls touch() {
        return touchControls;
    }

    @Override
    public Language language() {
        return language;
    }

    @Override
    public boolean interactAvailable() {
        return player != null && player.canInteract();
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
    public void spendCoins(int amount) {
        if (amount < 0 || amount > coinsCollected - levelStartCoins)
            throw new IllegalArgumentException("Invalid current-level payment");
        coinsCollected -= amount;
    }

    @Override
    public int coinsCollected() {
        return coinsCollected;
    }

    @Override
    public ArtifactInventory artifacts() { return artifacts; }

    @Override
    public void loadNextLevel() {
        if (uiState == UiState.ENDING) return;
        if (healer != null && healer.postVictory()) return;
        if (level != null && level.name.equals("forest") && level.victoryExitComplete()) {
            healer = HealerDialogue.afterVictory();
            dialogueInput.reset(Gdx.input);
            gameStarted = false;
            uiState = UiState.HEALER;
            return;
        }
        completeLevel();
    }

    private void completeLevel() {
        if (level != null) level.keepPickups();
        if (currentLevelIndex == LEVEL_NAMES.size() - 1) {
            releaseGame();
            openStory(Msg.ENDING_STORY, UiState.ENDING);
            return;
        }
        currentLevelIndex++;
        savedProgress.save(currentLevelIndex);
        // The outgoing hero may still be in the protected boss exit or checkpoint animation.
        player = new Player(assets, this, 0, 0);
        startLoadingLevel();
    }

    @Override
    public void resetRoom() {
        roomResetPending = true;
    }

    // Not named pause(): that is ApplicationListener.pause(), which libGDX calls when the window
    // is minimized, and toggling the game from there left the main menu replaced by an empty level.
    @Override
    public void togglePause() {
        if (level != null) level.clearCameraEffects();
        if (player != null) player.resetJumpInput();
        gameStarted = !gameStarted;
        uiState = gameStarted ? UiState.PLAYING : UiState.PAUSED;
    }

    @Override
    public void setNormalTime() {
        timeScale = 1f;
    }

    @Override
    public void setTimeScale(float scale) {
        timeScale = scale;
    }

    @Override
    public void panCameraTo(Vector2 target, float seconds) {
        panFrom.set(cameraTarget);
        panTo.set(target);
        panDuration = seconds;
        panElapsed = 0f;
        cameraPanning = true;
    }

    @Override
    public boolean isCameraPanning() {
        return cameraPanning;
    }

    /** Real time, so the slow motion of a death does not stretch the pan that follows it. */
    private void advanceCameraPan(float dt) {
        if (!cameraPanning) return;
        panElapsed += dt;
        float t = Math.min(1f, panElapsed / panDuration);
        float eased = t * t * (3f - 2f * t); // smoothstep: eases in and out
        cameraTarget.set(panFrom).lerp(panTo, eased);
        if (t >= 1f) cameraPanning = false;
    }

    @Override
    public void moveCameraTo(Vector2 target, float speed) {
        if (cameraPanning) return;
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
