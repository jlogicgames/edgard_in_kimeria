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
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jlogicsoftware.kimeria.effects.FlightFx;
import com.jlogicsoftware.kimeria.effects.RippleEffect;
import com.jlogicsoftware.kimeria.effects.RoomRippleFx;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.effects.ShockwaveEffect;
import com.jlogicsoftware.kimeria.effects.SoftDot;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.input.IntroInput;
import com.jlogicsoftware.kimeria.ui.ControlsView;
import com.jlogicsoftware.kimeria.ui.FontSizes;
import com.jlogicsoftware.kimeria.ui.FpsLabel;
import com.jlogicsoftware.kimeria.ui.IntroScreen;
import com.jlogicsoftware.kimeria.ui.KimeriaArms;
import com.jlogicsoftware.kimeria.ui.HealerDialogue;
import com.jlogicsoftware.kimeria.ui.HealerDialogueView;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Localization;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.ui.Hud;
import com.jlogicsoftware.kimeria.ui.PlayfieldViewport;
import com.jlogicsoftware.kimeria.ui.TouchCoach;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import com.jlogicsoftware.kimeria.ui.TouchGlyph;
import com.jlogicsoftware.kimeria.ui.TouchLayout;
import com.jlogicsoftware.kimeria.ui.Menu;
import com.jlogicsoftware.kimeria.ui.MenuBackdrop;
import com.jlogicsoftware.kimeria.ui.MenuItem;
import com.jlogicsoftware.kimeria.ui.Overlay;
import com.jlogicsoftware.kimeria.world.Level;
import com.jlogicsoftware.kimeria.world.RoomFade;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private enum UiState {WEB_START, MAIN_MENU, CONFIRM_NEW_GAME, INTRO, ENDING, ABOUT, OPTIONS, CONTROLS, ASSIST, LOADING, PLAYING, HEALER, PAUSED}

    private static final float LOGICAL_W = 640f, LOGICAL_H = 360f;
    /** Controls screen (D135): title, then {@link ControlsView} from y 52 to 228, then the two buttons. */
    private static final float CONTROLS_TITLE_Y = 10f, CONTROLS_BUTTONS_Y = 236f;
    /** Options (#494): the title's top, and the band between the title and the menu hint that its button list is centred in. */
    static final float OPTIONS_TITLE_Y = 14f;
    static final float OPTIONS_BAND_TOP = 46f, OPTIONS_BAND_BOTTOM = 332f;
    /**
     * Sprites the batch holds before it flushes: the most libGDX allows (4 vertices each, vertex indices are shorts, so at most 32767 / 4 = 8191).
     * The parallax layers draw about 4 200 tile regions a frame (#236), which a 1000-sprite batch flushed five times.
     */
    private static final int BATCH_SPRITES = 8191;
    private static final int RGB_BLUE = 2;
    private static final float[] BLACK_CLEAR = {0f, 0f, 0f};
    private static final float[] BACKDROP_CLEAR = {0.16f, 0.16f, 0.19f};
    /** Behind the world while no level is loaded (D92); a loaded level's own {@code background} replaces it. */
    private static final float[] WORLD_CLEAR = new float[3];
    static { Level.parseBackground(Level.DEFAULT_BACKGROUND, WORLD_CLEAR); }
    /** Portrait phones: the strip under the picture that holds the touch controls (#226). */
    private static final float[] STRIP_CLEAR = {0.06f, 0.06f, 0.08f};
    /** Black tint drawn over the menu backdrop on every menu screen (start, main, submenus). */
    private static final float MENU_TINT_ALPHA = 0.2f;
    private static final List<String> LEVEL_NAMES = List.of("forest-0", "forest-1", "forest-2");
    /** Play order of the shipped levels; read by {@code RoomCardsTest} (D72). */
    static List<String> levelNames() { return LEVEL_NAMES; }
    private static final float MUSIC_FADE_IN = 2f, MUSIC_FADE_OUT = 1f;

    private Assets assets;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;
    private Viewport viewport;
    private final Vector2 cameraTarget = new Vector2();
    private final Rectangle reusableVisibleRect = new Rectangle();
    private final Rectangle reusableShakeView = new Rectangle();

    private OrthographicCamera screenCamera;
    private Viewport uiViewport;

    private Player player;
    private Level level;
    private final boolean platformTouch;
    private final DisplayControl pageFullscreen;
    private final boolean debugMode;
    private final boolean prefersReducedMotion;
    private boolean screenShake = Settings.DEFAULT_SCREEN_SHAKE;
    private boolean instantText = Settings.DEFAULT_INSTANT_TEXT;

    public KimeriaGame() { this(false); }

    public KimeriaGame(boolean platformTouch) { this(platformTouch, false); }

    public KimeriaGame(boolean platformTouch, boolean debugMode) { this(platformTouch, debugMode, null); }

    /** @param pageFullscreen the browser's page fullscreen where it exists (Android Chrome, not iPhone Safari); null elsewhere */
    public KimeriaGame(boolean platformTouch, boolean debugMode, DisplayControl pageFullscreen) {
        this(platformTouch, debugMode, pageFullscreen, false);
    }

    /** @param prefersReducedMotion the browser's {@code prefers-reduced-motion: reduce} on the web; false elsewhere. It only sets the Screen shake default while the player has not chosen. */
    public KimeriaGame(boolean platformTouch, boolean debugMode, DisplayControl pageFullscreen, boolean prefersReducedMotion) {
        this.prefersReducedMotion = prefersReducedMotion;
        this.pageFullscreen = pageFullscreen;
        this.platformTouch = platformTouch;
        this.debugMode = debugMode;
    }

    private Hud hud;
    /** Pickup flights to the HUD slots (D132, #557): screen space, cosmetic; cleared with every rollback, room change and level load. */
    private static final long FLIGHT_SEED = 0xF11641FL;
    private final FlightFx flights = new FlightFx(FLIGHT_SEED);
    private final EnumMap<ItemType, TextureRegion> flightSprites = new EnumMap<>(ItemType.class);
    private final Vector2 flightSlot = new Vector2();
    private static final float POP_SECONDS = .12f, POP_RISE = 6, POP_FROM = 16, ITEM_ART = 16, PICKUP_FLIGHT_SLOTS = 2,  POP_TO = 20, PICKUP_FLIGHT_SECONDS = .40f, PICKUP_ARC = 24,
        PICKUP_END_SIZE = 10, PICKUP_FADE = .65f, PICKUP_TRAIL_R = 1f, PICKUP_TRAIL_G = .957f, PICKUP_TRAIL_B = .667f;
    private TouchControls touchControls;
    private final TouchLayout touchLayout = new TouchLayout(LOGICAL_W, LOGICAL_H);
    private Overlay overlay;
    private MenuBackdrop menuBackdrop;
    private final GamepadInput gamepad = new GamepadInput();

    /** Everything needed to begin a run; Continue fills this from saved progress. */
    private record StartData(int levelIndex, int coins, boolean newRun) {
        static StartData newGame() {
            return new StartData(0, 0, true);
        }
    }

    /** The tutorial level whose start and end carry the healer's tales (D41). */
    private static final String TUTORIAL_LEVEL = "forest-0";
    /**
     * One-shot tutorial dialogues (D41). Run state only, never saved: a save exists only after a
     * level is completed, so Continue never re-enters forest-0 and nothing needs persisting.
     * The start tale is pending only for a New Game run; each is consumed the moment it opens, so
     * death, Restart Level and retries never repeat it.
     */
    private boolean tutorialIntroPending, tutorialOutroShown;
    /**
     * The healer's first-death line (#341, D81): once per New Game run. Unlike the tales it is saved the moment it
     * opens ({@link SavedProgress#setFirstDeathTold}), because a death can come after Continue; New Game clears it and
     * a save without the key counts as told. Never on a later death, Restart Level or a collection return.
     */
    private boolean firstDeathTold = true;

    private SavedProgress savedProgress;
    private UiState uiState;
    private int currentLevelIndex = 0;
    private int coinsCollected = 0;
    /** Menu callbacks held in fields: a method reference or capturing lambda built in {@code renderOverlay} would allocate every frame. */
    private final Runnable hoverSound = this::playHoverSound, clickSound = () -> playSound("button_click");
    private static final Runnable NO_ACTION = () -> { };
    private final FpsLabel fpsLabel = new FpsLabel();
    private static final float WEB_START_BUTTONS_Y = 170f;
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private final RangedStock rangedStock = new RangedStock();
    /** Coin count when the level began; Restart Level goes back to it. */
    private int levelStartCoins = 0;
    private int roomStartCoins = 0;
    private final RoomFade roomFade = new RoomFade();
    /** The death fade under the silver thread's pull (#341, D81): the room is rebuilt at its black midpoint. */
    private final RoomFade deathFade = new RoomFade();
    private final Runnable rebuildUnderBlack = () -> rebuildRoom(true);
    private boolean gameStarted = false;
    private boolean pendingFullscreenToggle = false;

    private float timeScale = 1f;
    /** Set by a fall into a pit; the room is rebuilt at the start of the next frame (see {@link #rebuildRoom}). */
    private boolean roomResetPending = false;
    private final Vector2 panFrom = new Vector2(), panTo = new Vector2();
    private float panDuration = 0f, panElapsed = 0f;
    private boolean cameraPanning = false;
    /** The Options switches (#494): sound effects and music, loaded in {@link #create} and applied at once. */
    private boolean playSounds = Settings.DEFAULT_SOUND, playMusic = Settings.DEFAULT_MUSIC;
    /** Name of the near-silent loop that keeps the audio output awake (#316); see {@link #updateAudioKeepAlive}. */
    static final String KEEPALIVE_SOUND = "keepalive";
    private static final String KEEPALIVE_PATH = "audio/keepalive.wav";
    /** Volume of the keep-alive loop. The file itself is at -65 dBFS RMS; this is not scaled by the player's volume. */
    static final float KEEPALIVE_VOLUME = 1f;
    /** Desktop-only switch (set by the LWJGL3 launcher): a sleeping output device swallows the start of a sound. */
    private boolean audioKeepAlive = false;
    private final Set<String> firstPlayLogged = new HashSet<>();
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
    /** The Kimeria arms whose sun lights up on the ending page (#416, D95) and that mark the selected menu button (#415); cut once at load. */
    private KimeriaArms arms;
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
    private ControlsView controlsView;
    private Menu webStartMenu, mainMenu, aboutMenu, optionsMenu, controlsMenu, assistMenu, pauseMenu, confirmNewGameMenu;

    /** Offscreen copy of the world, filled only while a post effect (pause glitch, coin ripple) is live. */
    private FrameBuffer sceneBuffer;
    private float glitchTime = 0f;

    @Override
    public void create() {
        Localization.current();
        assets = new Assets();
        assets.preloadSounds();
        batch = new SpriteBatch(BATCH_SPRITES);
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera();
        camera.setToOrtho(true, LOGICAL_W, LOGICAL_H);
        viewport = new PlayfieldViewport(LOGICAL_W, LOGICAL_H, camera);

        screenCamera = new OrthographicCamera();
        screenCamera.setToOrtho(true, LOGICAL_W, LOGICAL_H);
        screenCamera.update();
        uiViewport = new PlayfieldViewport(LOGICAL_W, LOGICAL_H, screenCamera);

        hud = new Hud(assets, this, LOGICAL_W, LOGICAL_H);
        touchControls = new TouchControls(assets, this, uiViewport, LOGICAL_W, LOGICAL_H, platformTouch);
        touchControls.setLayout(touchLayout);
        arms = new KimeriaArms(assets);
        overlay = new Overlay(assets, LOGICAL_W, LOGICAL_H, arms);
        controlsView = new ControlsView(assets, overlay);
        healerView = new HealerDialogueView(assets);
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
        screenShake = settings.get(Settings.SCREEN_SHAKE, !prefersReducedMotion);
        instantText = settings.get(Settings.INSTANT_TEXT);
        loadAudioSwitches();
        savedProgress = new SavedProgress(settings.preferences(), LEVEL_NAMES.size());
        buildMenus();
        // Browsers block audio until the page gets a user gesture, so the web build opens on a
        // one-button screen whose click/keypress unlocks it; other platforms go straight to the menu.
        uiState = isWeb() ? UiState.WEB_START : UiState.MAIN_MENU;
        // resize() may have run before the touch controls existed, so lay out once more now that they do.
        applyLayout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
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
            new MenuItem(() -> Msg.ABOUT.t(language), () -> { aboutRules = false; uiState = UiState.ABOUT; }),
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
            new MenuItem(() -> (aboutRules ? Msg.ABOUT_GAME : Msg.RULES).t(language), () -> aboutRules = !aboutRules),
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
        if (pageFullscreen != null && pageFullscreen.available()) {
            // Runs inside the click that selects the item: browsers only allow fullscreen from a user gesture.
            optionItems.add(new MenuItem(
                () -> Msg.DISPLAY_LABEL.t(language) + ": "
                    + (pageFullscreen.active() ? Msg.FULLSCREEN : Msg.WINDOWED).t(language),
                pageFullscreen::toggle));
        }
        optionItems.add(new MenuItem(
            () -> Msg.SOUND_LABEL.t(language) + ": " + (playSounds ? Msg.ON : Msg.OFF).t(language),
            this::toggleSound));
        optionItems.add(new MenuItem(
            () -> Msg.MUSIC_LABEL.t(language) + ": " + (playMusic ? Msg.ON : Msg.OFF).t(language),
            this::toggleMusic));
        optionItems.add(new MenuItem(() -> Msg.CONTROLS.t(language), () -> {
            controlsMenu.selected = 0;
            uiState = UiState.CONTROLS;
        }));
        optionItems.add(new MenuItem(() -> Msg.ASSIST.t(language), () -> uiState = UiState.ASSIST));
        optionItems.add(new MenuItem(() -> Msg.BACK.t(language), this::backToMainMenu));
        optionsMenu = new Menu(() -> Msg.OPTIONS.t(language), null, optionItems, this::backToMainMenu,
            Overlay.BUTTON_W, Overlay.COMPACT_BUTTON_H, Overlay.COMPACT_BUTTON_GAP);

        // D135: Left-handed lives on the Controls screen, which shows both hands so the switch explains itself.
        controlsMenu = new Menu(() -> Msg.CONTROLS.t(language), null, List.of(
            new MenuItem(
                () -> Msg.LEFT_HANDED_LABEL.t(language) + ": " + (leftHanded ? Msg.ON : Msg.OFF).t(language),
                this::toggleLeftHanded),
            new MenuItem(() -> Msg.BACK.t(language), () -> uiState = UiState.OPTIONS)
        ), () -> uiState = UiState.OPTIONS);

        assistMenu = new Menu(() -> Msg.ASSIST.t(language), null, List.of(
            new MenuItem(
                () -> Msg.LONGER_TRAP_WARNINGS.t(language) + ": "
                    + (trapWarnings.longer() ? Msg.ON : Msg.OFF).t(language),
                () -> trapWarnings.toggle(settings)),
            new MenuItem(
                () -> Msg.SCREEN_SHAKE.t(language) + ": " + (screenShake ? Msg.ON : Msg.OFF).t(language),
                () -> { screenShake = !screenShake; settings.set(Settings.SCREEN_SHAKE, screenShake); }),
            new MenuItem(
                () -> Msg.INSTANT_TEXT.t(language) + ": " + (instantText ? Msg.ON : Msg.OFF).t(language),
                () -> { instantText = !instantText; settings.set(Settings.INSTANT_TEXT, instantText); }),
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

    /** The saved Sound and Music switches (#494), applied before anything plays. */
    private void loadAudioSwitches() {
        playSounds = settings.get(Settings.SOUND);
        playMusic = settings.get(Settings.MUSIC);
    }

    /** Switches the sound effects and remembers the choice (#494). Off also ends every running loop. */
    private void toggleSound() {
        playSounds = !playSounds;
        settings.set(Settings.SOUND, playSounds);
        if (!playSounds) while (!loops.isEmpty()) stopSound(loops.keySet().iterator().next());
    }

    /** Switches the music and remembers the choice (#494); the next {@link #updateMenuMusic} stops or starts the track. */
    private void toggleMusic() {
        playMusic = !playMusic;
        settings.set(Settings.MUSIC, playMusic);
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

    /** Instant text (#342): the paged intro (#336) reads this to show a whole page at once. */
    public boolean instantText() {
        return instantText;
    }

    @Override
    public boolean screenShakeDisabled() {
        return !screenShake;
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
        flights.clear();
        artifacts.restartLevel();
        rangedStock.restartLevel();
        // A farewell cut off by Restart Level is replayed when the tutorial is finished again.
        if (TUTORIAL_LEVEL.equals(LEVEL_NAMES.get(currentLevelIndex))) tutorialOutroShown = false;
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
        flights.clear();
        healer = null;
        tutorialIntroPending = tutorialOutroShown = false;
        pausedHealer = false;
        artifacts.clear();
        rangedStock.reset();
        roomFade.cancel();
        deathFade.cancel();
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
        if (savedProgress.exists()) startGame(new StartData(savedProgress.nextLevelIndex(), 0, false));
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
        startStory(IntroScreen.openBook(introLeaves(), overlay, assets, LOGICAL_W, instantText()), UiState.INTRO);
    }

    private boolean storyScreenActive() { return uiState == UiState.INTRO || uiState == UiState.ENDING; }

    /** The ending on black pages (#336) with the arms (#416, D95); the opening is {@link #startStory} on its book (D133). Both share the fade, music and fresh-input rules. */
    private void openStory(Msg message, UiState state) {
        startStory(IntroScreen.open(message.t(language), overlay, assets, LOGICAL_W, instantText(), state == UiState.ENDING ? arms : null), state);
    }

    /** The twelve leaves of the opening on its book (#535, #536, #615, D133), in the player's language. */
    private String[] introLeaves() {
        return new String[] {Msg.INTRO_LEAF_1.t(language), Msg.INTRO_LEAF_2.t(language), Msg.INTRO_LEAF_3.t(language),
            Msg.INTRO_LEAF_4.t(language), Msg.INTRO_LEAF_5.t(language), Msg.INTRO_LEAF_6.t(language), Msg.INTRO_LEAF_7.t(language),
            Msg.INTRO_LEAF_8.t(language), Msg.INTRO_LEAF_9.t(language), Msg.INTRO_LEAF_10.t(language), Msg.INTRO_LEAF_11.t(language),
            Msg.INTRO_LEAF_12.t(language)};
    }

    private void startStory(IntroScreen screen, UiState state) {
        intro = screen;
        float hintRight = LOGICAL_W - IntroScreen.SKIP_HINT_MARGIN;
        intro.setSkipHints(overlay.hintBlock(Msg.INTRO_SKIP_KEYBOARD.t(language), hintRight, IntroScreen.SKIP_HINT_TOP),
            overlay.hintBlock(Msg.INTRO_SKIP_GAMEPAD.t(language), hintRight, IntroScreen.SKIP_HINT_TOP));
        introInput.reset(Gdx.input);
        uiState = state;
    }

    private void updateStory(float dt) {
        // Escape, the system Back key or the pad's Back/Start leaves the story; any other fresh press turns the page.
        boolean pressed = introInput.skipPressed(Gdx.input);
        // Touch names no key (its skip button is the hint); a connected pad names Start; otherwise the keyboard's Esc.
        boolean touch = touchControls.isAvailable();
        intro.skipHint(IntroScreen.skipHintFor(touch, !touch && gamepad.isConnected())); // a touch device never polls the pad here
        touchControls.updateSkip(); // a tap on the skip button leaves; it never also turns a page
        if (touchControls.skipPressed() || introInput.keyPressed(Input.Keys.ESCAPE) || introInput.keyPressed(Input.Keys.BACK) || gamepad.back() || gamepad.pausePressed()) intro.skip();
        else if (pressed || gamepad.anyButtonPressed()) intro.turn();
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
        rangedStock.reset();
        currentLevelIndex = Math.floorMod(data.levelIndex(), LEVEL_NAMES.size());
        tutorialIntroPending = data.newRun() && TUTORIAL_LEVEL.equals(LEVEL_NAMES.get(currentLevelIndex));
        tutorialOutroShown = false;
        if (data.newRun() && savedProgress != null) savedProgress.setFirstDeathTold(false);
        firstDeathTold = savedProgress != null ? savedProgress.firstDeathTold() : !data.newRun();
        coinsCollected = data.coins();
        player = new Player(assets, this, 0, 0);
        startLoadingLevel();
    }

    private void startLoadingLevel() {
        flights.clear();
        healer = null;
        pausedHealer = false;
        level = null; // the previous level is released while the loading screen shows
        gameStarted = false;
        // Death's slow motion must not leak into the next level, and a
        // reset still waiting for its frame (a death just before Restart or Exit) is moot now.
        timeScale = 1f;
        roomResetPending = false;
        roomFade.cancel();
        deathFade.cancel();
        cameraPanning = false;
        levelLoadDelay = 1f; // mirrors the original's Future.delayed(1s) before (re)loading a level
        uiState = UiState.LOADING;
    }

    private void loadLevel() {
        artifacts.beginLevel();
        rangedStock.beginLevel();
        levelStartCoins = coinsCollected;
        roomStartCoins = coinsCollected;
        Rectangle visible = new Rectangle();
        level = new Level(assets, this, player, LEVEL_NAMES.get(currentLevelIndex), () -> visibleWorldRect(visible), rangedStock);
    }

    /** Hands the freshly built level to the player, after the one-time tutorial tale when it is due. */
    private void beginLevelPlay() {
        gameStarted = true;
        uiState = UiState.PLAYING;
        if (tutorialIntroPending && TUTORIAL_LEVEL.equals(level.name)) {
            tutorialIntroPending = false;
            openScriptedHealer(HealerDialogue.tutorialStart());
        }
    }

    private void openScriptedHealer(HealerDialogue dialogue) {
        healer = dialogue;
        healer.setInstantText(instantText); // before the first render, not one frame later
        dialogueInput.reset(Gdx.input);
        gameStarted = false;
        uiState = UiState.HEALER;
    }

    /**
     * Rebuilds only the active room without reloading the map: at a frame boundary after a pit, or at the black of
     * the death fade after the thread's pull, where the camera snaps to the start instead of panning (#341, D81).
     * The first death of a run then opens the healer's line.
     */
    private void rebuildRoom(boolean underBlack) {
        flights.clear();
        roomResetPending = false;
        coinsCollected = roomStartCoins;
        timeScale = 1f;
        player = new Player(assets, this, 0, 0);
        level.resetCurrentRoom(player);
        if (underBlack) {
            player.arriveUnderThread();
            cameraTarget.set(player.roomCameraTarget());
            cameraPanning = false;
        } else {
            player.arriveAfterReset();
        }
        if (!firstDeathTold) {
            firstDeathTold = true; // consumed on opening, like the tales
            if (savedProgress != null) savedProgress.setFirstDeathTold(true);
            openScriptedHealer(HealerDialogue.firstDeath());
        }
    }

    private void enterNextRoom() {
        flights.clear();
        if (level.healerAhead()) {
            roomFade.cancel();
            level.cancelLeaving();
            level.prepareHealer();
            roomStartCoins = coinsCollected;
            openHealer(false, level.beginPreBossHealerMeeting());
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

    private void openHealer(boolean paid, boolean returning) {
        healer = new HealerDialogue(artifacts.hasIngredient(level.name, ItemType.BAT),
            artifacts.hasIngredient(level.name, ItemType.MUSHROOM),
            level.forestCoinTotal() > 0 && level.forestCoinsSecured() == level.forestCoinTotal(),
            level.canBrew(), paid, returning);
        healer.setInstantText(instantText); // before the first render, not one frame later
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
            case BREW -> { if (level.brewElixir()) { roomStartCoins = coinsCollected; openHealer(true, false); } }
            case CONTINUE -> {
                if (healer == null) return;
                switch (healer.kind()) {
                    case VICTORY, TUTORIAL_END -> completeLevel();
                    case TUTORIAL_START, FIRST_DEATH -> {
                        // The healer's gift arrives at his shoulder with a twinkle once, as the start tale closes (D82);
                        // closing the first-death line leaves her as she is.
                        if (healer.kind() == HealerDialogue.Kind.TUTORIAL_START) level.fairy().arrive();
                        healer = null;
                        player.resetJumpInput();
                        gamepad.suppressJumpUntilRelease();
                        gameStarted = true;
                        uiState = UiState.PLAYING;
                    }
                    case PRE_BOSS -> { }
                }
            }
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
        applyLayout(width, height);
    }

    private void applyLayout(int width, int height) {
        // Portrait phones: picture pinned to the top, the strip below it belongs to the touch controls (#226).
        touchLayout.setPixelRatio(pageFullscreen != null ? pageFullscreen.pixelRatio() : 1f);
        touchLayout.update(width, height, touchControls != null && touchControls.isAvailable());
        // D137 (#491): text takes its large tier whenever the picture is shown smaller than it was drawn for, phone or not.
        if (assets != null) assets.setLargeText(FontSizes.large(touchLayout.cssPerUnit()));
        for (Viewport v : new Viewport[]{viewport, uiViewport}) {
            ((PlayfieldViewport) v).setTopAligned(touchLayout.portrait());
            ((PlayfieldViewport) v).setMaxScale(touchLayout.pictureScale());
            v.update(width, height, false);
        }
    }

    @Override
    public void render() {
        updateAudioKeepAlive();
        // Cleared before applying: the mode switch may re-enter render() on desktop.
        if (pendingFullscreenToggle) {
            pendingFullscreenToggle = false;
            applyFullscreenToggle();
        }
        float dt = Gdx.graphics.getDeltaTime();
        gamepad.update(leftHanded);
        UiState stateAtFrameStart = uiState;
        touchControls.update(stateAtFrameStart == UiState.PLAYING, stateAtFrameStart == UiState.PLAYING || stateAtFrameStart == UiState.HEALER);
        if (stateAtFrameStart != UiState.PLAYING && gamepad.confirm()) {
            gamepad.suppressJumpUntilRelease();
        }

        if (levelLoadDelay > 0) {
            levelLoadDelay -= dt;
            if (levelLoadDelay <= 0) {
                loadLevel();
                beginLevelPlay();
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

        updatePlay(dt, stateAtFrameStart, levelVisible);

        // A victory exit can open dialogue inside Level.update on this frame.
        levelVisible = uiState == UiState.PLAYING || (uiState == UiState.PAUSED && !pausedHealer);
        menuBackdropActive = !levelVisible && !storyScreenActive() && uiState != UiState.HEALER
            && !(uiState == UiState.PAUSED && pausedHealer);
        menuBackdrop.update(dt);
        updateMenuMusic(dt, musicWanted);
        handleDevHotkeys();

        // Development hotkeys can replace the player and clear the level here.
        // Re-evaluate presentation ownership before rendering this same frame.
        levelVisible = uiState == UiState.PLAYING || (uiState == UiState.PAUSED && !pausedHealer);
        menuBackdropActive = !levelVisible && !storyScreenActive() && uiState != UiState.HEALER
            && !(uiState == UiState.PAUSED && pausedHealer);

        float shakeX = 0, shakeY = 0;
        if (level != null) {
            Vector2 shake = level.cameraShakeOffset(visibleWorldRect(reusableShakeView)); // read at once, never kept
            shakeX = shake.x;
            shakeY = shake.y;
        }
        camera.position.set(cameraTarget.x + LOGICAL_W / 2f + shakeX, cameraTarget.y + LOGICAL_H / 2f + shakeY, 0);
        camera.update();

        // The menu backdrop is a dark scene so the fog and fireflies stand out; gameplay shows the level's background (D92).
        float[] clear;
        if (storyScreenActive() || uiState == UiState.HEALER || pausedHealer) {
            clear = BLACK_CLEAR;
        } else if (menuBackdropActive) {
            clear = BACKDROP_CLEAR;
        } else {
            clear = worldClear();
        }
        boolean framed = touchLayout.portrait() || touchLayout.sideBars();
        if (framed) {
            // Dark strip or side bars for the controls, the scene colour only behind the picture.
            clearScreen(STRIP_CLEAR);
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            HdpiUtils.glScissor(uiViewport.getScreenX(), uiViewport.getScreenY(), uiViewport.getScreenWidth(), uiViewport.getScreenHeight());
        }
        clearScreen(clear);
        if (framed) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

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

        // The hint anchor is placed through camera.project: give it the unshaken camera so the label does not wobble.
        if (shakeX != 0 || shakeY != 0) {
            camera.position.set(cameraTarget.x + LOGICAL_W / 2f, cameraTarget.y + LOGICAL_H / 2f, 0);
            camera.update();
        }
        // HUD + overlays draw in fixed logical screen space (not affected by world camera).
        batch.setProjectionMatrix(screenCamera.combined);
        batch.begin();
        String fpsText = debugMode && !roomFade.active() && !storyScreenActive()
            && uiState != UiState.HEALER && !pausedHealer ? fpsLabel.text(Gdx.graphics.getFramesPerSecond()) : "";
        if (levelVisible) {
            hud.render(batch, level != null ? level.boss() : null,
                level != null && artifacts.bossWeaponDamageMultiplier(level.name) > 1f, player, fpsText);
            flights.render(batch); // over the HUD, under the hint and the touch controls
            if (uiState == UiState.PLAYING && !roomFade.active() && !deathFade.active() && player != null) hud.renderInteractionHint(batch, player, camera, dt);
            if (uiState == UiState.PLAYING) touchControls.render(batch);
        }
        if (menuBackdropActive) {
            menuBackdrop.render(batch);
        }
        // Portrait phones: the strip under the picture always carries the large rotate prompt (D140; in play TouchControls.render draws it with the controls).
        if (touchLayout.portrait() && !(levelVisible && uiState == UiState.PLAYING)) touchControls.renderStrip(batch, false);
        if (levelVisible && roomFade.active()) overlay.panel(batch, 0, 0, LOGICAL_W, LOGICAL_H, roomFade.alpha());
        if (levelVisible && deathFade.active()) overlay.panel(batch, 0, 0, LOGICAL_W, LOGICAL_H, deathFade.alpha());
        overlay.beginFrame();
        renderOverlay(uiState == stateAtFrameStart);
        if (!fpsText.isEmpty()) overlay.topRight(batch, fpsText, LOGICAL_W - Hud.FPS_RIGHT_MARGIN, Hud.FPS_TOP_MARGIN);
        overlay.flushText(batch);
        // The healer panel is opaque, so its pause button is drawn on top of it (the world HUD draws its own).
        if (uiState == UiState.HEALER) touchControls.renderPauseButton(batch);
        if (storyScreenActive()) touchControls.renderSkipButton(batch);
        // Overlay text is queued in a Y-up pass; the fade must cover it after that pass.
        if (storyScreenActive()) overlay.panel(batch, 0, 0, LOGICAL_W, LOGICAL_H, intro.alpha());
        batch.end();
    }

    /**
     * One frame of gameplay, as render() runs it before drawing: effects age, a pending pit reset rebuilds the room,
     * then the death fade, the room fade or the level advances. Separate so the death-to-control test drives this very
     * code (KimeriaGameDeathTimingTest, #341).
     */
    private void updatePlay(float dt, UiState stateAtFrameStart, boolean levelVisible) {
        // Age existing effects before combat/reset creates new ones: a slow frame must not
        // consume a newly landed hit's entire flash before it has been rendered once.
        if (levelVisible && level != null && gameStarted && !roomFade.active() && !deathFade.dark()
            && stateAtFrameStart != UiState.HEALER) level.updatePresentation(dt);
        if (roomResetPending && uiState == UiState.PLAYING) {
            rebuildRoom(false);
        }
        if (levelVisible && level != null && gameStarted && stateAtFrameStart != UiState.HEALER) {
            if (deathFade.dark()) {
                // After black the rebuilt room waits, as in a room transition, while the hero lands.
                if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || gamepad.pausePressed() || touchControls.pausePressed()) togglePause();
                else {
                    player.advanceLanding(dt);
                    deathFade.update(dt, rebuildUnderBlack);
                }
            } else if (deathFade.active()) {
                // Before black the world keeps its slow motion, so the cause of death and the thread stay live.
                level.update(dt * timeScale);
                if (gameStarted) deathFade.update(dt, rebuildUnderBlack);
            } else if (roomFade.active()) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || gamepad.pausePressed() || touchControls.pausePressed()) togglePause();
                else {
                    level.updateRoomChange(dt); // the portal and the ripple play while the rest of the world is held (#553)
                    roomFade.update(dt, this::enterNextRoom);
                }
            } else {
                level.update(dt * timeScale);
                flights.update(dt); // real time, not the assist's time scale; a paused game never reaches this line
                if (gameStarted) advanceCameraPan(dt);
                if (gameStarted && !roomResetPending && level.atRoomExit()) {
                    player.resetJumpInput();
                    level.clearCameraEffects();
                    level.beginLeaving();
                    flights.clear();
                    roomFade.start(RoomRippleFx.LEAVE_HOLD_SECONDS); // a clear pause so the ripple can play; input is ignored, held keys are kept
                }
            }
        }
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
        if (dialogueInput.keyPressed(Input.Keys.ESCAPE) || touchControls.pausePressed()) {
            pausedHealer = true;
            uiState = UiState.PAUSED;
            return;
        }
        healer.setInstantText(instantText);
        healer.update(dt);
        healerView.update(dt, healer); // the flights of this screen (D132): cosmetic, real time, frozen with the pause
        String flightSound = healerView.takeSound();
        if (flightSound != null && playSounds) playSound(flightSound);
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

    /** The flat colour behind the world (D92): the loaded level's map {@code background}, or the default before one loads. */
    private float[] worldClear() { return level != null ? level.backgroundRgb() : WORLD_CLEAR; }

    private static void clearScreen(float[] rgb) {
        Gdx.gl.glClearColor(rgb[0], rgb[1], rgb[RGB_BLUE], 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
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
        float[] world = pausedHealer ? BLACK_CLEAR : worldClear();
        Gdx.gl.glClearColor(world[0], world[1], world[RGB_BLUE], 1f);
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
        float volume = playMusic ? soundVolume * intro.volume() : 0f;
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
        musicVolumeTarget = (want && playMusic) ? soundVolume : 0f;
        if (!playMusic) musicVolume = 0f; // a switched-off channel is silent at once, with no fade (#494)
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
        if (!debugMode) return;
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
            intro.render(batch, overlay);
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
            case CONTROLS -> controlsMenu;
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
        Runnable onHover = hoverSound, onActivate = clickSound;

        float cx = LOGICAL_W / 2f;
        // Every menu screen shares this full-screen tint over the fog/firefly backdrop -- no solid card.
        overlay.panel(batch, 0, 0, LOGICAL_W, LOGICAL_H, MENU_TINT_ALPHA);
        if (webStart) {
            overlay.title(batch, Msg.TITLE.t(language), cx, 90);
            overlay.buttons(batch, menu, cx, WEB_START_BUTTONS_Y, mouse, clicked, confirm, back, NO_ACTION, onActivate);
            return; // no menu hint: it mentions gamepad/Esc controls that do nothing here
        }
        if (uiState == UiState.MAIN_MENU) {
            float titleY = 14f, titleBottom = 54f, hintTop = LOGICAL_H - 24f;
            overlay.title(batch, Msg.TITLE.t(language), cx, titleY);

            // Centre the button list in the space between the title and the hint line.
            float startY = titleBottom + (hintTop - titleBottom - Overlay.buttonsHeight(menu)) / 2f;
            overlay.buttons(batch, menu, cx, startY, mouse, clicked, confirm, back, onHover, onActivate);
        } else if (uiState == UiState.OPTIONS) {
            // Seven rows (#494) leave no room for the panel layout below: the list takes the whole band between title and hint.
            overlay.title(batch, menu.titleText(), cx, OPTIONS_TITLE_Y);
            overlay.buttons(batch, menu, cx, optionsButtonsY(menu), mouse, clicked, confirm, back, onHover, onActivate);
        } else if (uiState == UiState.CONTROLS) {
            overlay.title(batch, menu.titleText(), cx, CONTROLS_TITLE_Y);
            controlsView.draw(batch, ControlsView.modeFor(touchControls.isAvailable(), gamepad.isConnected()), language, leftHanded);
            overlay.buttons(batch, menu, cx, CONTROLS_BUTTONS_Y, mouse, clicked, confirm, back, onHover, onActivate);
        } else {
            float panelW = uiState == UiState.ABOUT ? 610f : 320f;
            String aboutBody = uiState == UiState.ABOUT ? aboutText() : menu.bodyText();
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

        overlay.hint(batch, Msg.MENU_HINT.t(language), cx, LOGICAL_H - Overlay.HINT_BOTTOM_MARGIN);
    }

    /** Top of the Options list: centred in the band between the title and the menu hint. */
    static float optionsButtonsY(Menu menu) {
        return OPTIONS_BAND_TOP + (OPTIONS_BAND_BOTTOM - OPTIONS_BAND_TOP - Overlay.buttonsHeight(menu)) / 2f;
    }

    /** The About page shown: the game blurb (three paragraphs, D135) or the rules. */
    private String aboutText() {
        if (aboutRules) return Msg.ABOUT_RULES.t(language) + "\n\n" + Msg.ABOUT_RULES_CARRY.t(language);
        return Msg.ABOUT_GAME_STORY.t(language) + "\n\n" + Msg.ABOUT_GAME_PLAY.t(language) + "\n\n" + Msg.ABOUT_GAME_MORE.t(language);
    }

    @Override
    public void dispose() {
        stopSound(KEEPALIVE_SOUND);
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
    public TouchGlyph rangedGlyph() { return player == null || level == null ? TouchGlyph.FIREBALL : player.rangedGlyph(); }

    @Override
    public String rangedPrompt() { return player == null || level == null ? "" : player.rangedPrompt(); }

    @Override
    public void coachSignals(TouchCoach.Signals out) { if (player != null && level != null) player.coachSignals(out); }

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
    public void playSound(String name) { playSound(name, 1f); }

    @Override
    public void playSound(String name, float volumeScale) {
        if (!playSounds) return;
        Sound sound = assets.sound("audio/" + name + ".wav");
        if (debugMode && firstPlayLogged.add(name)) {
            long start = System.nanoTime();
            long id = sound.play(soundVolume * volumeScale);
            Gdx.app.log("Audio", "first play of " + name + ": play() returned id " + id + " after "
                + (System.nanoTime() - start) / NANOS_PER_MILLI + " ms, keep-alive "
                + (audioKeepAlive ? (loops.containsKey(KEEPALIVE_SOUND) ? "running" : "stopped") : "disabled"));
            return;
        }
        sound.play(soundVolume * volumeScale);
    }

    private static final float NANOS_PER_MILLI = 1_000_000f;

    /**
     * Desktop only: keeps a near-silent, non-silent loop running while sounds are on, so an output device that idles after a
     * second of silence (Bluetooth, some macOS outputs) is already awake when a real sound starts (#316). Called every frame:
     * a map lookup, no allocation. It starts when sounds are on, stops when they are off, and starts again when the handle is
     * gone; pause and room changes do not touch it.
     */
    private void updateAudioKeepAlive() {
        if (!audioKeepAlive) return;
        boolean running = loops.containsKey(KEEPALIVE_SOUND);
        if (playSounds && !running) {
            loops.put(KEEPALIVE_SOUND, assets.sound(KEEPALIVE_PATH).loop(KEEPALIVE_VOLUME));
        } else if (!playSounds && running) {
            stopSound(KEEPALIVE_SOUND);
        }
    }

    /** Enables the audio keep-alive (desktop launcher only, before the game starts). */
    public void setAudioKeepAlive(boolean enabled) { audioKeepAlive = enabled; }

    /** Looping handles by sound name; a sound is looped with {@code Sound.loop} and stopped by its id (desktop and web Howler). */
    private final Map<String, Long> loops = new HashMap<>();

    @Override
    public void loopSound(String name) {
        stopSound(name);
        if (!playSounds) return;
        loops.put(name, assets.sound("audio/" + name + ".wav").loop(soundVolume));
    }

    @Override
    public void stopSound(String name) {
        Long id = loops.remove(name);
        if (id != null) assets.sound("audio/" + name + ".wav").stop(id);
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
        if (healer != null && healer.scripted()) return;
        if (level != null && level.hasBossEncounter() && level.victoryExitComplete()) {
            openScriptedHealer(HealerDialogue.afterVictory());
            return;
        }
        if (level != null && TUTORIAL_LEVEL.equals(level.name) && !tutorialOutroShown) {
            tutorialOutroShown = true;
            openScriptedHealer(HealerDialogue.tutorialEnd());
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
    public void startPickupFlight(ItemType type, float worldX, float worldY) {
        if (hud == null || flights.freeSlots() < PICKUP_FLIGHT_SLOTS || !hud.slotCenter(type, flightSlot)) return; // the pop and the flight both fit, or neither starts
        TextureRegion sprite = flightSprites.get(type);
        if (sprite == null) {
            sprite = assets.region(type.sheet, type.spriteX, type.spriteY, ITEM_ART, ITEM_ART);
            flightSprites.put(type, sprite);
        }
        // World and screen share one 640x360 frame; the world camera's centre is its position (Y down), so its top left is half a frame back.
        float x = worldX - (camera.position.x - LOGICAL_W / 2f), y = worldY - (camera.position.y - LOGICAL_H / 2f);
        flights.start(sprite, x, y, x, y - POP_RISE, 0, POP_FROM, POP_TO, 0, POP_SECONDS, FlightFx.EASE_OUT, 1f,
            PICKUP_TRAIL_R, PICKUP_TRAIL_G, PICKUP_TRAIL_B, false, false);
        flights.start(sprite, x, y - POP_RISE, flightSlot.x, flightSlot.y, PICKUP_ARC, POP_TO, PICKUP_END_SIZE, POP_SECONDS,
            PICKUP_FLIGHT_SECONDS, FlightFx.EASE_IN, PICKUP_FADE, PICKUP_TRAIL_R, PICKUP_TRAIL_G, PICKUP_TRAIL_B, true, true);
    }

    @Override
    public void resetRoom() {
        roomResetPending = true;
    }

    @Override
    public void beginDeathFade() {
        flights.clear(); // a flight in the air is dropped cleanly: the room rolls the pickup back (#557)
        deathFade.start();
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
