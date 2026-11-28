package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.KimeriaGame;
import com.jlogicsoftware.kimeria.entity.Actor;
import com.badlogic.gdx.graphics.g2d.Animation;
import java.util.IdentityHashMap;
import java.util.Map;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Device polling through production Player/Level dispatch; no physical-device claims. */
class DirectionalActionsTest {
    private static final float REAL_STEP = 1f / 60f;

    private final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private final Vector2 lastCameraTarget = new Vector2();
    private float timeScale = 1f;
    private int coins, completions, resets;
    private boolean soundsEnabled;
    private final List<String> playedSounds = new ArrayList<>();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private TouchControls touch;
    private boolean leftHanded, touchActive = true, backendReportsTouch = true;
    private final boolean[] buttons = new boolean[20], fingers = new boolean[5];
    private final int[] pointerX = new int[5], pointerY = new int[5];
    private final float[] axes = new float[4];
    private final com.badlogic.gdx.controllers.ControllerMapping mapping = new com.badlogic.gdx.controllers.ControllerMapping(
        0, 1, 2, 3, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15) {};
    private final com.badlogic.gdx.controllers.Controller controller = (com.badlogic.gdx.controllers.Controller) Proxy.newProxyInstance(
        com.badlogic.gdx.controllers.Controller.class.getClassLoader(), new Class<?>[]{com.badlogic.gdx.controllers.Controller.class},
        (proxy, method, args) -> switch (method.getName()) {
            case "getMapping" -> mapping;
            case "getButton" -> buttons[(int) args[0]];
            case "getAxis" -> axes[(int) args[0]];
            case "getMinButtonIndex" -> 0;
            case "getMaxButtonIndex" -> 19;
            default -> method.getReturnType() == boolean.class ? false : null;
        });
    private final GamepadInput pad = new GamepadInput() {
        @Override protected com.badlogic.gdx.controllers.Controller controller() { return controller; }
    };
    private final TrapWarnings warnings = new TrapWarnings(); // assist stays off
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, com.badlogic.gdx.graphics.Texture.TextureFilter filter) { return new TextureRegion(); }
        @Override public TextureRegion region(String path, float x, float y, float w, float h) {
            return new TextureRegion();
        }
        @Override public BitmapFont font(int size) { return null; } // no rendering
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "logicalResolution" -> new Vector2(640, 360);
            case "trapWarnings" -> warnings;
            case "gamepad" -> pad;
            case "touch" -> touch;
            case "moveCameraTo", "panCameraTo" -> { lastCameraTarget.set((Vector2) args[0]); yield null; }
            case "isGameStarted" -> true;
            case "leftHanded" -> leftHanded;
            case "setNormalTime" -> { timeScale = 1f; yield null; }
            case "setTimeScale" -> { timeScale = (float) args[0]; yield null; }
            case "addCoin" -> { coins++; yield null; }
            case "coinsCollected" -> coins;
            case "artifacts" -> artifacts;
            case "playSounds" -> soundsEnabled;
            case "playSound" -> { playedSounds.add((String) args[0]); yield null; }
            case "loadNextLevel" -> { completions++; yield null; }
            case "resetRoom" -> { resets++; yield null; }
            // Invulnerability and sound are off; camera callbacks record the clamped target only.
            default -> method.getReturnType() == boolean.class ? false : null;
        });

    private Application previousApp;
    private Graphics previousGraphics;
    private Input previousInput;
    private Files previousFiles;

    /**
     * Test resource {@code tiles/practice-test.tmx}: the D24 practice stone, carryable torch, practice lever and torch
     * and the ladder that forest-2's first room held before #376 (same ids and positions), inside a whole-room control
     * zone, then a hall and a boss room so a collection return works. The shipped map dropped them (D72: forest-0 now
     * teaches all of them); the carry, lever and ladder hint behaviour they exercise is general, so these tests keep it
     * on the fixture.
     */
    private static final String PRACTICE_MAP = "practice-test";

    @BeforeEach void setUp() {
        previousApp = Gdx.app; previousGraphics = Gdx.graphics;
        previousInput = Gdx.input; previousFiles = Gdx.files;
        GdxNativesLoader.load();
        Gdx.files = new HeadlessFiles();
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isPeripheralAvailable" -> backendReportsTouch && args[0] == Input.Peripheral.MultitouchScreen;
            case "isTouched" -> fingers[args == null || args.length == 0 ? 0 : (int) args[0]];
            case "getX" -> pointerX[args == null || args.length == 0 ? 0 : (int) args[0]];
            case "getY" -> pointerY[args == null || args.length == 0 ? 0 : (int) args[0]];
            case "isKeyPressed" -> held.contains((int) args[0]);
            case "isKeyJustPressed" -> pressed.contains((int) args[0]);
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getDeltaTime" -> REAL_STEP;
                case "getWidth" -> 640;
                case "getHeight" -> 360;
                default -> null;
            });
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> method.getName().equals("getType") ? Application.ApplicationType.HeadlessDesktop : null);
        var camera = new com.badlogic.gdx.graphics.OrthographicCamera();
        camera.setToOrtho(true, 640, 360);
        var viewport = new com.badlogic.gdx.utils.viewport.FitViewport(640, 360, camera);
        viewport.setScreenBounds(0, 0, 640, 360);
        touch = new TouchControls(assets, game, viewport, 640, 360);
    }

    @AfterEach void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics;
        Gdx.input = previousInput; Gdx.files = previousFiles;
    }

    private Player player() { return new Player(assets, game, 0, 0); }

    private Level level;
    private CountingSwitch first, second;
    private static final class CountingSwitch extends com.jlogicsoftware.kimeria.entity.Entity implements com.jlogicsoftware.kimeria.entity.items.Actionable {
        private final String target;
        int count;
        CountingSwitch(String target) { super(0, 0, 1, 1); this.target = target; }
        public String getTargetId() { return target; }
        public void performAction() { count++; }
        public void update(float dt) { }
    }
    private void scene(boolean left) {
        leftHanded = left;
        level = new Level(assets, game, player(), "room-test", Rectangle::new);
        first = new CountingSwitch("first"); second = new CountingSwitch("second");
        level.objects.add(first); level.objects.add(second);
        // Exact overlap, reverse list order: the lower authored ID must win.
        level.triggers.add(new com.jlogicsoftware.kimeria.entity.items.Trigger(9, 32, 272, 64, 48, "second"));
        level.triggers.add(new com.jlogicsoftware.kimeria.entity.items.Trigger(7, 32, 272, 64, 48, "first"));
        level.objects.add(new com.jlogicsoftware.kimeria.entity.items.Carryable(level, 21,
            com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.TORCH, 60, 308));
        level.objects.add(new com.jlogicsoftware.kimeria.entity.items.Carryable(level, 20,
            com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE, 60, 308));
        frames(2);
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aFreshUpOnALeverInReachActivatesItOnceAndActionNeverDoes(boolean left) {
        scene(left); frames(10);
        var stones = level.objects.stream()
            .filter(com.jlogicsoftware.kimeria.entity.items.Carryable.class::isInstance).toList();
        level.objects.removeAll(stones); frames(2);
        held.add(action()); frames(2);
        assertEquals(0, first.count, "Action alone is the sword and never interacts");
        held.remove(action()); frames(2);
        held.add(up()); frames(2);
        assertEquals(1, first.count);
        frames(90); assertEquals(1, first.count, "Holding Up never repeats the interaction");
        held.add(action()); frames(2); assertEquals(1, first.count);
    }

    /** Presses Up with the hero lifted out of reach, then returns him: Up is held but not fresh, so nothing interacts. */
    private void holdUpIntoReach() {
        var p = level.player; float y = p.position.y;
        p.position.y = y - 100; held.add(up()); frames(2);
        p.position.y = y; p.velocity.setZero(); frames(1);
    }

    /** D82: the fairy shows exactly the target the old outline framed, every frame. */
    private void assertFairyShows() {
        assertSame(level.player.interactionFeedbackTarget(), level.fairy().selected(), "the fairy shows the interaction target");
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void theFairyGlowIsInstantAndShowsTheOneSelectedTarget(boolean left) {
        scene(left); level.triggers.clear(); frames(30);
        var fairy = level.fairy();
        assertEquals(com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.IDLE, fairy.mode());
        assertNull(fairy.selected());
        assertEquals(2, fairy.passiveTargets().size, "both carryables in reach are marked passive while nothing is selected");
        // A fresh Up would pick the stone up at once; press it out of reach, then step into reach with Up held.
        holdUpIntoReach();
        var target = level.player.interactionFeedbackTarget();
        assertNotNull(target); assertFairyShows();
        // The selection reads on its first frame: full glow while she is still on her way (D82).
        assertEquals(1, fairy.glowAlpha(target));
        assertEquals(com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.FLY, fairy.mode());
        assertTrue(fairy.distanceToGoal() > 2, "she has not arrived on the selection frame");
        assertFalse(fairy.passiveTargets().contains(target, true));
        assertEquals(1, fairy.passiveTargets().size, "the other carryable stays passive; only one target is selected");
        assertEquals(.3f, fairy.glowAlpha(fairy.passiveTargets().get(0)), 1e-6);
        frames(29); // 0.5 s
        assertFairyShows();
        assertEquals(com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.POINT, fairy.mode());
        // Beside the target, never over it, and under the label anchored above it (world Y grows down).
        float fairyTop = fairy.y() - 8, fairyBottom = fairy.y() + 8, fairyLeft = fairy.x() - 8, fairyRight = fairy.x() + 8;
        assertTrue(fairyRight <= target.getX() || fairyLeft >= target.getX() + target.getWidth(), "she does not cover the target");
        assertTrue(fairyBottom <= target.getY() + 1, "she points from beside its upper corner");
        Vector2 anchor = new Vector2(); level.player.contextHintPosition(anchor);
        assertTrue(fairyTop > anchor.y, "she stays under the label's anchor");
        // She is on the side away from the hero.
        assertEquals(level.player.centerX() < target.centerX(), fairy.x() > target.centerX());
        held.clear(); frames(1);
        assertNull(fairy.selected()); assertEquals(.3f, fairy.glowAlpha(target), 1e-6);
        frames(60);
        assertEquals(com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.IDLE, fairy.mode(), "back at his shoulder");
        assertEquals(level.player.facingRight, fairy.x() < level.player.centerX(), "behind him, away from where he faces");
    }

    @Test void theFairyMarksTheTargetOnTheFrameTheHeroWalksIntoReach() {
        // Pins D82's call order: she is updated after the hero moves, so on the step he comes into reach with Up held
        // she already shows what the label and his next Up press see (Player.update read the selection before moving).
        scene(false); level.triggers.clear(); frames(30);
        var stone = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c
            && c.kind == com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE).findFirst().orElseThrow();
        level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable && o != stone);
        level.player.position.x = ((com.jlogicsoftware.kimeria.entity.Entity) stone).getX() + 80; frames(2);
        assertNull(level.player.interactionFeedbackTarget());
        held.add(up()); held.add(Input.Keys.A);
        boolean reached = false;
        for (int i = 0; i < 120 && !reached; i++) {
            frames(1);
            assertFairyShows();
            reached = level.fairy().selected() == stone;
        }
        assertTrue(reached, "the hero walked into reach with Up held");
    }

    @Test void theFairyKeepsFollowingTheHeroOnEveryUpdate() {
        // A frozen fairy (an update path that skips her) would stay where the hero left her.
        scene(false); level.triggers.clear();
        level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable); frames(30);
        var fairy = level.fairy();
        float before = fairy.x();
        level.player.position.x += 200; frames(60);
        assertEquals(before + 200, fairy.x(), 3, "she followed him 200 px");
        assertTrue(fairy.distanceToGoal() < 3, "and settled at his shoulder");
    }

    @Test void theFairyRestartsHerPointWhenTheTargetChangesWhereSheAlreadyHovers() {
        // Two targets on the same box: the hover point does not move, so only the target tells her to point again.
        CountingSwitch a = new CountingSwitch("a"), b = new CountingSwitch("b");
        a.position.set(200, 300); a.size.set(16, 16); b.position.set(200, 300); b.size.set(16, 16);
        com.jlogicsoftware.kimeria.entity.Entity[] target = {a};
        Player hero = new Player(assets, game, 0, 0) {
            @Override public com.jlogicsoftware.kimeria.entity.Entity interactionFeedbackTarget() { return target[0]; }
            @Override public void collectPassiveTargets(com.badlogic.gdx.utils.Array<com.jlogicsoftware.kimeria.entity.Entity> out,
                com.jlogicsoftware.kimeria.entity.Entity selected) { out.clear(); }
        };
        hero.position.set(150, 300);
        var fairy = new com.jlogicsoftware.kimeria.ui.FairyGuide(assets);
        for (int i = 0; i < 120; i++) fairy.update(REAL_STEP, hero);
        assertEquals(com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.POINT, fairy.mode());
        int held = fairy.frame(); // the last of the three point frames, held
        target[0] = b; fairy.update(REAL_STEP, hero);
        assertEquals(com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.POINT, fairy.mode());
        assertEquals(held - 2, fairy.frame(), "a new target restarts the point clip at its first frame");
    }

    @Test void theFairyHidesWhenTheHeroCannotActAndArrivesWithATwinkle() {
        scene(false); frames(30);
        var fairy = level.fairy();
        fairy.arrive(); frames(1);
        assertEquals(com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.APPEAR, fairy.mode());
        assertEquals(fairy.goalX(), fairy.x(), 1e-4);
        frames(24); // the two 0.2 s twinkle frames
        assertEquals(com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.IDLE, fairy.mode());
        held.add(up()); frames(1); assertNotNull(fairy.selected());
        level.player.collidedWithActor(true); frames(10);
        assertFalse(level.player.canFight());
        assertEquals(com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.HIDDEN, fairy.mode());
        assertNull(fairy.selected()); assertEquals(0, fairy.passiveTargets().size);
    }

    private void frames(int count) {
        for (int i = 0; i < count; i++) {
            pad.update(leftHanded);
            touch.update(touchActive);
            level.update(REAL_STEP);
            pressed.clear();
        }
    }
    private int action() { return leftHanded ? Input.Keys.X : Input.Keys.K; }
    private int ranged() { return leftHanded ? Input.Keys.C : Input.Keys.L; }
    private int up() { return leftHanded ? Input.Keys.UP : Input.Keys.W; }
    private int down() { return leftHanded ? Input.Keys.DOWN : Input.Keys.S; }
    private void tap(int key) { held.add(key); pressed.add(key); frames(1); held.remove(key); frames(1); }
    private void pickup() {
        var mechanisms = new ArrayList<com.jlogicsoftware.kimeria.entity.items.Trigger>();
        for (var trigger : level.triggers) mechanisms.add(trigger);
        level.triggers.clear();
        tap(up());
        for (var trigger : mechanisms) level.triggers.add(trigger);
    }
    private void putDown() { tap(down()); }

    private void standByEntranceStone() {
        var stone = level.objects.stream()
            .filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c
                && c.kind == com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        standByCarryable(stone);
    }

    private void standByCarryable(com.jlogicsoftware.kimeria.entity.items.Carryable object) {
        // The shared Tiled map is editable; derive reach from its authored object, not old coordinates.
        level.player.position.set(object.centerX() - level.player.hitbox.offsetX - level.player.hitbox.width / 2,
            object.getY() - level.player.size.y);
        frames(30);
    }

    @Test void heldStoneRendersAfterTheHeroAndThrownStoneReturnsToTheWorldPass() {
        scene(false);
        pickup();
        var stone = level.player.carriedObject();
        List<Object[]> draws = new ArrayList<>();
        var batch = (com.badlogic.gdx.graphics.g2d.Batch) Proxy.newProxyInstance(
            com.badlogic.gdx.graphics.g2d.Batch.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.graphics.g2d.Batch.class}, (proxy, method, args) -> {
                if (method.getName().equals("draw")) draws.add(args);
                return null;
            });
        stone.render(batch); // The world's object pass must not leave a stone behind the hero.
        assertTrue(draws.isEmpty());
        level.player.render(batch);
        assertEquals(2, draws.size(), "The held stone must render after the hero, never behind him");
        assertEquals(8f, draws.get(1)[3]);
        assertEquals(8f, draws.get(1)[4]);
        draws.clear();
        tap(ranged());
        stone.render(batch);
        assertEquals(1, draws.size());
        assertEquals(8f, draws.get(0)[3]);
        assertEquals(8f, draws.get(0)[4]);
    }

    @Test void torchTracksTheAuthoredFrontPalmWhenTurningAndJumping() {
        scene(false);
        level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c
            && c.kind == com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE);
        pickup(); frames(1);
        var torch = level.player.carriedObject();
        // Carrying draws the carry clips (#343 part 2, D79): the near fist, measured on the carry idle frame.
        assertEquals(32, torch.getX() + 5 - level.player.getX(), .001);
        assertTrue(torch.getY() + 8 - level.player.getY() >= 32);
        assertTrue(torch.getY() + 8 - level.player.getY() <= 35);
        level.player.facingRight = false;
        torch.followHolder();
        assertEquals(15, torch.getX() + 5 - level.player.getX(), .001);
        tap(Input.Keys.J); // The jump frame's near fist is raised, far above the waist (facing left: the empty left fist, #343 D79).
        assertEquals(Player.State.JUMPING, level.player.getState());
        assertEquals(16, torch.getX() + 5 - level.player.getX(), .001);
        assertEquals(22, torch.getY() + 8 - level.player.getY(), .001);
        // It is omitted from the world pass; Player.render owns its foreground pass.
        var batch = (com.badlogic.gdx.graphics.g2d.Batch) Proxy.newProxyInstance(
            com.badlogic.gdx.graphics.g2d.Batch.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.graphics.g2d.Batch.class}, (proxy, method, args) -> {
                fail("Held torch must not render behind the hero"); return null;
            });
        torch.render(batch);
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void keyboardSelectsSwordSingleTargetPickupPlaceAndUpDownTogether(boolean left) {
        scene(left);
        assertEquals(7, level.player.selectedMechanism().id);
        assertEquals(20, level.player.selectedCarryable().id);
        tap(action());
        assertEquals(Player.State.ATTACKING, level.player.getState());
        assertEquals(0, first.count); assertNull(level.player.carriedObject());
        frames(50);
        // The stone and the lever are both in reach; the nearer one, the stone, takes this press.
        tap(up());
        assertEquals(20, level.player.carriedObject().id);
        assertEquals(0, first.count); assertEquals(0, second.count);
        // Carrying, only mechanisms are Up targets.
        tap(up());
        assertEquals(1, first.count); assertEquals(0, second.count);
        assertEquals(20, level.player.carriedObject().id);
        // Up and Down pressed together interact with nothing: no lever, no put down.
        held.add(up()); held.add(down()); frames(2); held.clear(); frames(1);
        assertEquals(1, first.count); assertEquals(20, level.player.carriedObject().id);
        held.add(up()); frames(1); assertEquals(2, first.count);
        frames(60); // Holding never repeats an interaction.
        assertEquals(2, first.count); assertEquals(20, level.player.carriedObject().id);
        held.clear(); frames(1);
        tap(action()); assertEquals(Player.State.ATTACKING, level.player.getState());
        assertNotNull(level.player.carriedObject()); assertEquals(2, first.count, "Action never interacts");
        frames(50);
        tap(down());
        assertNull(level.player.carriedObject());
        assertEquals(3, level.player.ammunition());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void throwingConsumesOnlyRangedPressAndFireballsConsumeOneCharge(boolean left) {
        scene(left); pickup();
        assertNotNull(level.player.carriedObject());
        assertEquals(com.jlogicsoftware.kimeria.ui.TouchGlyph.THROW, level.player.rangedGlyph(), "carrying: the stone on its arc");
        held.add(ranged()); frames(1);
        assertNull(level.player.carriedObject());
        assertEquals(com.jlogicsoftware.kimeria.ui.TouchGlyph.FIREBALL, level.player.rangedGlyph(), "empty-handed with ammunition: the flame");
        assertEquals(3, level.player.ammunition());
        frames(30); assertEquals(3, level.player.ammunition());
        assertFalse(level.objects.stream().anyMatch(com.jlogicsoftware.kimeria.entity.items.Fireball.class::isInstance));
        held.clear(); frames(1);
        tap(ranged()); assertEquals(2, level.player.ammunition());
        assertTrue(level.objects.stream().anyMatch(com.jlogicsoftware.kimeria.entity.items.Fireball.class::isInstance));
        tap(ranged()); tap(ranged()); tap(ranged());
        assertEquals(0, level.player.ammunition());
        assertEquals(com.jlogicsoftware.kimeria.localization.Msg.NO_AMMO, level.player.rangedLabel());
        assertEquals(com.jlogicsoftware.kimeria.ui.TouchGlyph.EMPTY, level.player.rangedGlyph(), "empty-handed, no ammunition: the hollow flame");
    }

    /** #492 (D138): every state of the Ranged label has its own pictogram, and the three states are three different ones. */
    @Test void everyRangedStateHasItsOwnPictogram() {
        scene(false); pickup();
        var seen = java.util.EnumSet.noneOf(com.jlogicsoftware.kimeria.ui.TouchGlyph.class);
        seen.add(level.player.rangedGlyph());
        held.add(ranged()); frames(1); held.clear(); frames(1); // throw it
        seen.add(level.player.rangedGlyph());
        tap(ranged()); tap(ranged()); tap(ranged());
        seen.add(level.player.rangedGlyph());
        assertEquals(java.util.EnumSet.of(com.jlogicsoftware.kimeria.ui.TouchGlyph.THROW, com.jlogicsoftware.kimeria.ui.TouchGlyph.FIREBALL,
            com.jlogicsoftware.kimeria.ui.TouchGlyph.EMPTY), seen);
    }

    /** #492 (D138): on touch the carried-stone label drops its binding ("Throw"); on the keyboard it keeps it ("L: Throw"). */
    @Test void theRangedPromptNamesNoButtonOnTouchAndTheKeyOnTheKeyboard() {
        scene(false);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        level.triggers.clear();
        standByEntranceStone();
        pickup(); frames(1);
        assertNotNull(level.player.carriedObject());
        assertTrue(level.player.contextHint().contains("S: Put down\nL: Throw"), level.player.contextHint());
        finger(0, 80, 262); frames(1); // the stick pushed up: touch becomes the device in use
        assertEquals("Put down\nThrow", level.player.contextHint());
        java.util.Arrays.fill(fingers, false); frames(1);
    }

    @Test void roomRetryRestoresAmmoObjectsAndClearsQueuedProjectilesButExitSecuresSpend() {
        scene(false); pickup(); tap(ranged()); tap(ranged());
        assertEquals(2, level.player.ammunition());
        level.resetCurrentRoom(player()); frames(2);
        assertEquals(3, level.player.ammunition());
        assertNull(level.player.carriedObject());
        assertFalse(level.objects.stream().anyMatch(com.jlogicsoftware.kimeria.entity.items.Fireball.class::isInstance));
        tap(ranged());
        level.enterNextRoom(player()); frames(2);
        assertEquals(2, level.player.ammunition());
        tap(ranged()); assertEquals(1, level.player.ammunition());
        level.resetCurrentRoom(player()); frames(2);
        assertEquals(2, level.player.ammunition());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void heldEntryResumeDeathAndLayoutChangesRequireRelease(boolean left) {
        leftHanded = left;
        held.add(action()); held.add(ranged()); held.add(down()); held.add(up());
        scene(left); frames(3);
        assertNull(level.player.carriedObject()); assertEquals(3, level.player.ammunition());
        held.clear(); frames(1); pickup(); assertNotNull(level.player.carriedObject());
        held.add(ranged()); level.player.resetJumpInput(); frames(2);
        assertNotNull(level.player.carriedObject()); assertEquals(3, level.player.ammunition());
        held.clear(); frames(1); tap(ranged()); assertNull(level.player.carriedObject());
        leftHanded = !leftHanded;
        held.add(ranged()); held.add(action()); frames(2);
        assertEquals(3, level.player.ammunition());
        assertNotEquals(Player.State.ATTACKING, level.player.getState());
        held.clear(); frames(1); tap(ranged()); assertEquals(2, level.player.ammunition());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void gamepadUsesMovementVerticalRatherThanMirroredActionDpad(boolean left) {
        scene(left);
        int actionButton = left ? mapping.buttonDpadLeft : mapping.buttonX;
        int rangedButton = left ? mapping.buttonDpadUp : mapping.buttonY;
        int verticalAxis = left ? mapping.axisRightY : mapping.axisLeftY;
        axes[verticalAxis] = 1;
        frames(1); assertEquals(1, level.player.verticalMovement());
        assertNull(level.player.carriedObject(), "Down with an empty hand does nothing");
        axes[verticalAxis] = 0; frames(1);
        axes[verticalAxis] = -1;
        frames(1); assertEquals(-1, level.player.verticalMovement(), "Up is still the climb direction");
        assertNotNull(level.player.carriedObject(), "A fresh stick up takes hold of the nearest carryable");
        axes[verticalAxis] = 0; frames(1);
        buttons[actionButton] = true; frames(1);
        assertEquals(Player.State.ATTACKING, level.player.getState(), "Action alone swings the sword");
        assertNotNull(level.player.carriedObject());
        buttons[actionButton] = false; frames(50);
        buttons[rangedButton] = true; frames(1);
        assertNull(level.player.carriedObject()); assertEquals(3, level.player.ammunition());
        buttons[rangedButton] = false; frames(1);
        buttons[rangedButton] = true; frames(1);
        assertEquals(2, level.player.ammunition());
        buttons[rangedButton] = false; frames(1);
        // With a stone or torch in reach those are nearer than the lever, so leave only the mechanism.
        level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable);
        axes[verticalAxis] = -1; frames(1);
        assertEquals(1, first.count); assertEquals(0, second.count);
    }

    private void finger(int p, float x, int y) { fingers[p] = true; pointerX[p] = (int) x; pointerY[p] = y; }
    private float mirrored(float x) { return leftHanded ? 640 - x : x; }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void touchPicksUpACarryableWhileTheMovementStickIsHeld(boolean left) {
        scene(left); frames(10);
        var stone = level.objects.stream()
            .filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c && c.id == 20)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        standByCarryable(stone);
        finger(0, mirrored(80), 245); frames(1);
        assertNotNull(level.player.carriedObject(), "A steep stick up takes hold at once");
        finger(1, mirrored(520), 318); frames(2);
        assertNotNull(level.player.carriedObject(), "The Action button is the sword and does not drop it");
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void touchStickInteractsOnlyWhenSteeplyUpAndALeaningStickJustClimbs(boolean left) {
        scene(left); frames(10);
        var stone = level.objects.stream()
            .filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c && c.id == 20)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        standByCarryable(stone);
        finger(0, mirrored(120), 255); frames(1); // 40 px sideways, 25 px up: about 32 degrees
        assertEquals(-1, level.player.verticalMovement(), "A leaning stick still counts as Up for a ladder");
        assertFalse(touch.interactUpHeld()); assertNull(level.player.carriedObject());
        fingers[0] = false; standByCarryable(stone); finger(0, mirrored(120), 255); frames(1);
        pointerX[0] = (int) mirrored(90); pointerY[0] = 245; frames(1); // 10 px sideways, 35 px up: about 74 degrees
        assertTrue(touch.interactUpHeld());
        assertNotNull(level.player.carriedObject(), "Steepening the stick without lifting the finger is a fresh press");
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void touchClaimsTwoPointersDragsVerticallyAndSuppressesMenuHold(boolean left) {
        scene(left);
        var stone = level.objects.stream()
            .filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c && c.id == 20)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        standByCarryable(stone);
        finger(0, mirrored(80), 245); frames(1);
        assertEquals(-1, level.player.verticalMovement());
        finger(1, mirrored(520), 318); frames(1);
        assertNotNull(level.player.carriedObject(), "The stick up took hold of the stone");
        finger(1, mirrored(520), 318); frames(1);
        assertNotNull(level.player.carriedObject()); assertEquals(0, first.count, "The Action button never interacts");
        frames(30); assertNotNull(level.player.carriedObject());
        fingers[1] = false; frames(50);
        fingers[0] = false; frames(1);
        finger(0, mirrored(80), 245); frames(1); // a fresh steep push, the stone in hand: the lever
        assertEquals(1, first.count); assertEquals(0, second.count);
        assertEquals(-1, level.player.verticalMovement());
        java.util.Arrays.fill(fingers, false); frames(1);
        finger(1, mirrored(536), 244); frames(1);
        assertNull(level.player.carriedObject()); assertEquals(3, level.player.ammunition());
        touchActive = false; frames(1);
        level.player.resetJumpInput(); touchActive = true; frames(1);
        assertEquals(3, level.player.ammunition());
        fingers[1] = false; frames(1);
        finger(1, mirrored(536), 244); frames(1);
        assertEquals(2, level.player.ammunition());
    }

    @Test void blockedPlacementKeepsTheObjectAndNeverSpendsACharge() {
        scene(false); pickup();
        var block = com.jlogicsoftware.kimeria.entity.environment.CollisionBlock.plain(63, 300, 16, 20);
        level.collisionBlocks.add(block);
        tap(down());
        assertNotNull(level.player.carriedObject(), "A blocked put down keeps the object");
        assertEquals(3, level.player.ammunition());
        level.collisionBlocks.remove(block);
        tap(down());
        assertNull(level.player.carriedObject(), "With the way clear a fresh Down puts it down");
        assertEquals(3, level.player.ammunition());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aBlockedPlacementSaysBlockedOnTheDownLine(boolean left) {
        scene(left);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        level.triggers.clear();
        standByEntranceStone();
        pickup(); frames(1);
        assertNotNull(level.player.carriedObject());
        assertTrue(level.player.contextHint().contains(Input.Keys.toString(down()) + ": Put down"));
        assertFalse(level.player.contextHint().contains("Blocked"), "clear ground: no Blocked");
        var body = level.player.hitboxBounds();
        level.collisionBlocks.add(com.jlogicsoftware.kimeria.entity.environment.CollisionBlock.plain(body.x - 40, body.y, body.width + 80, body.height));
        frames(1);
        assertTrue(level.player.contextHint().contains(Input.Keys.toString(down()) + ": Put down - Blocked"), level.player.contextHint());
        tap(down());
        assertNotNull(level.player.carriedObject(), "a blocked Down keeps the object");
    }

    @Test void torchThrowAlsoKeepsAmmoAndTheEmptyHandDoesNotFire() {
        scene(false);
        level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c && c.id == 20);
        pickup();
        assertEquals(com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.TORCH, level.player.carriedObject().kind);
        tap(ranged());
        assertEquals(3, level.player.ammunition());
        assertFalse(level.objects.stream().anyMatch(com.jlogicsoftware.kimeria.entity.items.Fireball.class::isInstance));
    }

    @Test void projectilesHitOrdinaryEnemiesAndDamageBossForTwoEnergy() {
        scene(false);
        var mob = new com.jlogicsoftware.kimeria.entity.enemy.YellowMob(assets, game, 100, 288, 48, 32, 0, 0);
        mob.setPlayer(level.player); mob.setLevel(level); level.objects.add(mob);
        tap(ranged()); frames(20);
        assertTrue(mob.isDefeated());
        level = new Level(assets, game, player(), "forest-2", Rectangle::new);
        for (int room = 0; room < 8; room++) level.enterNextRoom(player()); // entry, walls, fissure, glade, roost, hollow, ridge, canopy, then the boss room (#331, #330, #645, #525)
        frames(2);
        var boss = level.boss();
        var fireball = new com.jlogicsoftware.kimeria.entity.items.Fireball(level, boss.hitboxBounds().x, boss.hitboxBounds().y, true);
        level.objects.add(fireball); fireball.update(REAL_STEP);
        assertTrue(fireball.isRemoved()); assertEquals(10, boss.energy());
    }

    private com.jlogicsoftware.kimeria.entity.enemy.YellowMob blood_mob(float x) {
        var mob = new com.jlogicsoftware.kimeria.entity.enemy.YellowMob(assets, game, x, 288, 48, 32, 0, 0);
        mob.setPlayer(level.player); mob.setLevel(level); level.objects.add(mob);
        return mob;
    }
    /** Hero beside the mob (side -1: hero on its left, facing right; +1: hero on its right, facing left), ready to swing. */
    private void swordBeside(com.jlogicsoftware.kimeria.entity.enemy.YellowMob mob, int side) {
        Rectangle body = mob.hitboxBounds();
        level.player.position.set(side < 0 ? body.x - 40 : body.x + body.width - 8, 272);
        level.player.facingRight = side < 0;
        level.player.setOnGround(true);
    }
    private void swing() { pressed.add(action()); held.add(action()); frames(1); held.remove(action()); }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints = {-1, 1})
    void aSwordKillSpraysOneLightBurstAwayFromTheHeroAndOnlyOnce(int side) {
        scene(false); level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable);
        var mob = blood_mob(100); frames(2);
        swordBeside(mob, side);
        var blood = level.bloodParticles();
        assertEquals(0, blood.count());
        swing();
        assertTrue(mob.isDefeated(), "the sword hit is the kill");
        assertEquals(com.jlogicsoftware.kimeria.effects.KimeriaBloodSystem.LIGHT_DROPLETS, blood.count());
        for (int i = 0; i < blood.count(); i++) assertFalse(blood.empowered(i), "a mob never gets the boss's empowered burst");
        Rectangle body = mob.hitboxBounds();
        // Drops 1.. fly away from the hero (every fifth, starting with 0, sprays back); world Y: the floor is the body's bottom edge.
        assertEquals(-side, Math.signum(blood.vx(1)), "away from the hero");
        frames(20);
        assertTrue(blood.count() > 0);
        for (int i = 0; i < blood.count(); i++) assertTrue(blood.y(i) <= body.y + body.height, "drops never fall below the mob's feet");
        frames(12); // the rest of the swing keeps overlapping the corpse
        assertTrue(blood.count() <= com.jlogicsoftware.kimeria.effects.KimeriaBloodSystem.LIGHT_DROPLETS, "one burst per kill");
    }

    @Test void aFireballKillSpraysNothing() {
        scene(false); level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable);
        var mob = blood_mob(100);
        tap(ranged()); frames(20);
        assertTrue(mob.isDefeated());
        assertEquals(0, level.bloodParticles().count());
    }

    @Test void aSwordMissAnAlreadyDefeatedMobAndARetryResetSprayNothing() {
        scene(false); level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable);
        var mob = blood_mob(100); frames(2);
        level.player.position.set(400, 272); swing(); frames(20);
        assertFalse(mob.isDefeated()); assertEquals(0, level.bloodParticles().count(), "a swing that touches nothing");
        swordBeside(mob, -1); frames(1); swing();
        int burst = level.bloodParticles().count();
        assertEquals(com.jlogicsoftware.kimeria.effects.KimeriaBloodSystem.LIGHT_DROPLETS, burst);
        mob.hitBySword(level); mob.hitBySword(level);
        assertEquals(burst, level.bloodParticles().count(), "a defeated mob bleeds no more");
        level.resetCurrentRoom(player()); frames(1);
        assertEquals(0, level.bloodParticles().count(), "a retry clears the blood");
    }

    @Test void bloodOnlyMovesWhenTheLevelIsUpdatedSoAPauseFreezesIt() {
        scene(false); level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable);
        var mob = blood_mob(100); frames(2); swordBeside(mob, -1); swing();
        var blood = level.bloodParticles();
        float x = blood.x(1), y = blood.y(1), age = blood.age(1);
        // A paused game does not call Level.update, the only thing that ages and moves the drops.
        assertEquals(x, blood.x(1)); assertEquals(y, blood.y(1)); assertEquals(age, blood.age(1));
        frames(3);
        assertNotEquals(x, blood.x(1));
    }

    @Test void rangedStockCrossesLevelsAndRestartRestoresLevelEntryRatherThanRefilling() {
        var stock = new com.jlogicsoftware.kimeria.RangedStock();
        stock.spend(); stock.beginLevel(); stock.spend();
        assertEquals(1, stock.count());
        stock.restartLevel(); assertEquals(2, stock.count());
        var nextLevel = new Level(assets, game, player(), "room-test", Rectangle::new, stock);
        assertEquals(2, nextLevel.player.ammunition());
        stock.reset(); assertEquals(3, stock.count());
    }

    @Test void fullRefillUsesTheLocalHintOutsideTutorialAndAutomaticallyCollectsWhenSpaceOpens() {
        scene(false);
        level = new Level(assets, game, player(), "forest-2", Rectangle::new);
        level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); frames(2); // the canopy, after the glade, the roost, the hollow and the ridge (#331, #330, #645, #525)
        var refill = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.FireballRefill.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.FireballRefill.class::cast).findFirst().orElseThrow();
        level.player.position.set(refill.getX() - level.player.hitbox.offsetX,
            refill.getY() - level.player.hitbox.offsetY);
        frames(1);
        assertFalse(level.controlHintsActive());
        assertEquals("Fireball inventory full", level.player.contextHint());
        Vector2 anchor = new Vector2(); level.player.contextHintPosition(anchor);
        assertEquals(refill.centerX(), anchor.x);
        assertTrue(anchor.y < refill.getY());
        assertEquals(3, level.player.ammunition());
        assertFalse(refill.isRemoved());
        assertFalse(level.rangedStock.refill(), "Full stock rejects another charge");
        level.rangedStock.spend(); frames(1);
        assertEquals(3, level.player.ammunition());
        assertTrue(refill.isRemoved());
        assertTrue(level.player.contextHint().isEmpty());
        frames(3);
        assertEquals(3, level.player.ammunition());
    }

    @Test void swordRemainsGroundedAndJumpPlusSwordKeepsTheBufferedJump() {
        scene(false);
        level.player.setOnGround(false);
        level.player.velocity.y = -100;
        tap(action()); assertNotEquals(Player.State.ATTACKING, level.player.getState());
        frames(60);
        assertTrue(level.player.isOnGround());
        held.add(Input.Keys.J); held.add(action()); frames(1);
        assertNotEquals(Player.State.ATTACKING, level.player.getState());
        assertTrue(level.player.velocity.y < 0);
    }

    @Test void webCapabilityEnablesTouchWhenTheBackendCannotDetectIt() {
        scene(false);
        backendReportsTouch = false;
        var camera = new com.badlogic.gdx.graphics.OrthographicCamera(); camera.setToOrtho(true, 640, 360);
        var viewport = new com.badlogic.gdx.utils.viewport.FitViewport(640, 360, camera);
        viewport.setScreenBounds(0, 0, 640, 360);
        touch = new TouchControls(assets, game, viewport, 640, 360, true);
        var stone = level.objects.stream()
            .filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c && c.id == 20)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        standByCarryable(stone);
        finger(0, 80, 245); frames(1);
        finger(1, 520, 318); frames(1);
        assertNotNull(level.player.carriedObject());
    }

    @Test void nearestTargetWinsBeforeIdTieBreakAndReloadDropsOldHolder() {
        scene(false);
        var closer = level.triggers.stream().filter(t -> t.id == 9).findFirst().orElseThrow();
        closer.position.set(24, 288);
        assertEquals(9, level.player.selectedMechanism().id);
        var torch = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c && c.id == 21)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        torch.position.set(49, 303);
        assertEquals(21, level.player.selectedCarryable().id);
        pickup(); assertEquals(torch, level.player.carriedObject());
        level.resetCurrentRoom(level.player);
        assertNull(level.player.carriedObject());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void practiceLeverFramesItsOwnSpriteAndSwitchesTheTorchBothWays(boolean left) {
        scene(left);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        var trigger = level.triggers.stream().filter(t -> t.targetId.equals("practice-light")).findFirst().orElseThrow();
        level.player.position.set(trigger.centerX() - level.player.hitbox.offsetX - level.player.hitbox.width / 2,
            trigger.centerY() - level.player.hitbox.offsetY - level.player.hitbox.height / 2);
        frames(2);
        assertSame(trigger, level.player.selectedMechanism());
        assertNull(level.player.interactionFeedbackTarget(), "Nothing glows until Up is held");
        var lever = assertInstanceOf(com.jlogicsoftware.kimeria.entity.items.Lever.class, level.mechanismFeedbackTarget(trigger));
        assertSame(trigger, lever.trigger);
        assertEquals(trigger.centerX(), lever.centerX(), .001);
        assertEquals(trigger.getY() + trigger.getHeight(), lever.getY() + lever.getHeight(), .001);
        assertEquals(16, lever.getWidth()); assertEquals(16, lever.getHeight());
        var torch = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.effects.Torch t
            && t.getTargetId().equals("practice-light"))
            .map(com.jlogicsoftware.kimeria.effects.Torch.class::cast).findFirst().orElseThrow();
        assertFalse(torch.isBurning()); assertFalse(lever.isUp());
        assertTrue(level.player.contextHint().contains("Turn on"));
        assertTrue(level.player.contextHint().endsWith("Lever"));
        List<Object> drawn = new ArrayList<>();
        var batch = (com.badlogic.gdx.graphics.g2d.Batch) Proxy.newProxyInstance(
            com.badlogic.gdx.graphics.g2d.Batch.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.graphics.g2d.Batch.class}, (proxy, method, args) -> {
                if (method.getName().equals("draw")) drawn.add(args[0]); return null;
            });
        lever.render(batch);
        held.add(up()); frames(1); // the fresh press switches it
        assertTrue(torch.isBurning()); assertTrue(lever.isUp());
        assertSame(lever, level.player.interactionFeedbackTarget());
        assertFairyShows();
        assertEquals(1, level.fairy().glowAlpha(lever), "the selected lever glows at full strength (D82)");
        assertTrue(level.fairy().passiveTargets().contains(torch, true), "the torch the lever drives is marked passive");
        assertTrue(level.player.contextHint().contains("Turn off"));
        lever.render(batch); assertNotSame(drawn.get(0), drawn.get(1));
        frames(30); assertTrue(lever.isUp(), "A held Up must not repeatedly toggle the lever");
        held.remove(up()); frames(1); tap(up());
        assertFalse(torch.isBurning()); assertFalse(lever.isUp());
        lever.render(batch); assertSame(drawn.get(0), drawn.get(2));
        torch.toggleFire(true); assertTrue(lever.isUp(), "The torch remains the source of truth");
        torch.toggleFire(false);
        held.clear();
        level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c
            && c.kind == com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast)
            .forEach(com.jlogicsoftware.kimeria.entity.items.Carryable::removeFromParent);
        var portableTorch = level.objects.stream()
            .filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c
                && c.kind == com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.TORCH)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        standByCarryable(portableTorch);
        holdUpIntoReach();
        var pickup = level.player.interactionFeedbackTarget();
        assertFairyShows();
        assertSame(level.player.selectedCarryable(), pickup);
        assertEquals(com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.TORCH,
            ((com.jlogicsoftware.kimeria.entity.items.Carryable) pickup).kind);
        assertNotSame(level.player, pickup);
        held.remove(up()); held.add(down()); frames(1); assertNull(level.player.interactionFeedbackTarget()); assertFairyShows();
        held.clear(); frames(1); assertNull(level.player.interactionFeedbackTarget()); assertFairyShows();
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void pickupRequiresContactAndDoesNotTeachOutsideTheTrainingRoom(boolean left) {
        scene(left);
        level.triggers.clear();
        var stone = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c && c.id == 20)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c && c.id == 21);
        Rectangle body = level.player.hitboxBounds();
        stone.position.set(body.x + body.width + 12, 308);
        held.add(up()); frames(1);
        assertNull(level.player.selectedCarryable());
        assertEquals("", level.player.contextHint());
        assertNull(level.player.interactionFeedbackTarget());
        assertFairyShows();
        assertNull(level.player.carriedObject(), "A fresh Up out of reach takes nothing");
        held.clear(); frames(1);
        held.add(left ? Input.Keys.RIGHT : Input.Keys.D); frames(6); held.clear(); frames(1);
        assertSame(stone, level.player.selectedCarryable());
        assertEquals("", level.player.contextHint());
        tap(action()); assertNull(level.player.carriedObject(), "Action never picks up"); frames(50);
        pickup(); frames(1); assertSame(stone, level.player.carriedObject());
        assertEquals("", level.player.contextHint());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void trainingRoomRepeatsPickupPlaceAndThrow(boolean left) {
        scene(left);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        // Isolate carrying reminders here; the combined lever lesson/anchor has its own regression below.
        level.triggers.clear();
        standByEntranceStone();
        assertTrue(level.player.contextHint().contains(Input.Keys.toString(up()) + ": Pick up"));
        pickup(); frames(1);
        assertNotNull(level.player.carriedObject());
        assertTrue(level.player.contextHint().contains(left ? "C: Throw" : "L: Throw"));
        assertTrue(level.player.contextHint().contains(Input.Keys.toString(down()) + ": Put down"));
        Vector2 anchor = new Vector2(); level.player.contextHintPosition(anchor);
        frames(120);
        held.add(left ? Input.Keys.RIGHT : Input.Keys.D); frames(35); held.clear();
        assertTrue(level.player.contextHint().contains("Put down"), "The old 50 px limit must not suppress in-zone practice");
        assertTrue(level.player.contextHint().contains("Throw"));
        Vector2 movedAnchor = new Vector2(); level.player.contextHintPosition(movedAnchor);
        assertEquals(anchor, movedAnchor);
        tap(down()); frames(1);
        assertNull(level.player.carriedObject());
        assertTrue(level.player.contextHint().contains("Pick up"));
        pickup(); frames(1);
        assertTrue(level.player.contextHint().contains("Put down"));
        assertTrue(level.player.contextHint().contains("Throw"));
        level.resetCurrentRoom(level.player);
        standByEntranceStone(); pickup(); frames(1);
        assertTrue(level.player.contextHint().contains("Put down"), "Retrying in the area still teaches");
    }

    @Test void trainingRoomTeachesThroughoutItsWidthAndHeight() {
        scene(false);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        standByEntranceStone();
        var stone = level.player.selectedCarryable();
        assertNotNull(stone);
        Rectangle room = level.currentRoomBounds();
        float[][] probes = {{room.x + 8, room.y + 8},
            {room.x + room.width - 8, room.y + 8},
            {room.x + 8, room.y + room.height - 8},
            {room.x + room.width - 8, room.y + room.height - 8},
            {room.x + room.width / 2, room.y + room.height / 2}};
        for (float[] probe : probes) {
            level.player.position.set(probe[0] - level.player.hitbox.offsetX - level.player.hitbox.width / 2,
                probe[1] - level.player.hitbox.offsetY - level.player.hitbox.height / 2);
            stone.position.set(probe[0] - 6, probe[1] - 6);
            assertSame(stone, level.player.selectedCarryable());
            assertTrue(level.controlHintsActive(), "The entire first room teaches, including beyond the old rectangle");
            assertTrue(level.player.contextHint().contains("Pick up"));
        }
    }

    @Test void trainingIsAbsentInLaterRoomsAndAvailableWhenReturningToTheFirstRoom() {
        scene(false);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        assertTrue(level.controlHintsActive());
        level.enterNextRoom(player());
        assertFalse(level.controlHintsActive());
        level.returnForCollection(player());
        standByEntranceStone();
        assertNotNull(level.player.selectedCarryable());
        assertTrue(level.controlHintsActive());
        assertTrue(level.player.contextHint().contains("Pick up"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false, STONE", "true, STONE", "false, TORCH", "true, TORCH"})
    void carryingStillTeachesTheNearbyLeverAtItsOwnAnchor(boolean left,
            com.jlogicsoftware.kimeria.entity.items.Carryable.Kind kind) {
        scene(left);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        var item = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c
            && c.kind == kind).map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        standByCarryable(item); pickup();
        assertSame(item, level.player.carriedObject());
        Vector2 pickupAnchor = new Vector2(); level.player.contextHintPosition(pickupAnchor);
        var trigger = level.triggers.stream().filter(t -> t.targetId.equals("practice-light")).findFirst().orElseThrow();
        level.player.position.set(trigger.centerX() - level.player.hitbox.offsetX - level.player.hitbox.width / 2,
            trigger.centerY() - level.player.hitbox.offsetY - level.player.hitbox.height / 2);
        frames(2);
        assertSame(trigger, level.player.selectedMechanism());
        String hint = level.player.contextHint();
        assertTrue(hint.startsWith(Input.Keys.toString(up()) + ": Turn on - Lever"));
        assertTrue(hint.contains("Put down")); assertTrue(hint.contains("Throw"));
        assertEquals(3, hint.lines().count());
        Vector2 mechanismAnchor = new Vector2(); level.player.contextHintPosition(mechanismAnchor);
        assertEquals(level.mechanismFeedbackTarget(trigger).centerX(), mechanismAnchor.x, .001);
        assertNotEquals(pickupAnchor, mechanismAnchor, "The mechanism lesson belongs by the lever, not the old pickup");
        tap(up()); frames(1);
        assertSame(item, level.player.carriedObject(), "Operating the lever must not place or consume the held item");
        assertTrue(level.player.contextHint().contains("Turn off - Lever"));
        tap(up()); frames(1);
        assertTrue(level.player.contextHint().contains("Turn on - Lever"));
        level.player.position.x = level.currentRoomBounds().x + 700;
        frames(1);
        assertNull(level.player.selectedMechanism());
        assertEquals("", level.player.contextHint(), "Camera scrolling consumes this pickup's carrying reminder");
        level.player.position.set(trigger.centerX() - level.player.hitbox.offsetX - level.player.hitbox.width / 2,
            trigger.centerY() - level.player.hitbox.offsetY - level.player.hitbox.height / 2);
        frames(2);
        assertTrue(level.player.contextHint().contains("Turn on - Lever"), "Mechanism teaching remains repeatable after carrying reminders expire");
        assertFalse(level.player.contextHint().contains("Put down"));
        level.enterNextRoom(player());
        assertFalse(level.controlHintsActive());
        assertEquals("", level.player.contextHint());
    }

    private static final int CAMERA_SETTLE_FRAMES = 90;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void carryingReminderEndsOnCameraScrollAndRearmsOnEachPickup(boolean left) {
        scene(left);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        level.triggers.clear();
        var stone = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c
            && c.kind == com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        // Clear sky inside the teaching room avoids terrain/enemies changing this camera-only setup.
        // Held there until the camera has eased up to its resting height (D154), so only the horizontal move below can scroll.
        for (int i = 0; i < CAMERA_SETTLE_FRAMES; i++) { level.player.position.set(700, 40); level.player.velocity.setZero(); frames(1); }
        Rectangle body = level.player.hitboxBounds();
        stone.position.set(body.x + body.width / 2 - 6, body.y + body.height / 2 - 6);
        pickup();
        assertSame(stone, level.player.carriedObject());
        assertTrue(level.player.contextHint().contains("Put down"));
        Vector2 cameraAtPickup = new Vector2(lastCameraTarget);
        // Teleporting right left the focus at the zone's trailing edge; step back into the zone.
        level.player.position.x -= 3; frames(1);
        assertEquals(cameraAtPickup, lastCameraTarget, "Movement within the dead zone does not scroll");
        assertTrue(level.player.contextHint().contains("Throw"));
        level.player.position.x += 60; frames(1);
        assertNotEquals(cameraAtPickup, lastCameraTarget);
        assertSame(stone, level.player.carriedObject());
        assertEquals("", level.player.contextHint());
        level.player.position.x -= 60; frames(2);
        assertEquals("", level.player.contextHint(), "Returning/stopping must not revive the same pickup's reminder");
        putDown(); assertNull(level.player.carriedObject());
        pickup(); assertSame(stone, level.player.carriedObject());
        assertTrue(level.player.contextHint().contains("Put down"), "Every new pickup in the first room teaches again");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aJumpFromTheFloorKeepsTheCarryingReminder(boolean left) {
        scene(left);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        level.triggers.clear();
        var stone = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c
            && c.kind == com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        frames(CAMERA_SETTLE_FRAMES);
        Rectangle body = level.player.hitboxBounds();
        stone.position.set(body.x + body.width / 2 - 6, body.y + body.height - 12);
        pickup();
        assertSame(stone, level.player.carriedObject());
        float floor = level.player.position.y, highest = floor;
        tap(jump());
        for (int i = 0; i < CAMERA_SETTLE_FRAMES; i++) { frames(1); highest = Math.min(highest, level.player.position.y); }
        assertTrue(highest < floor - 48, "high enough to have scrolled without the slack (D154): " + highest);
        assertTrue(level.player.contextHint().contains("Put down"), "no scroll, so the reminder stays");
    }

    // -- Vertical ladders (#99) --

    private static final float LADDER_X = 104, LEDGE_Y = 240, FLOOR_Y = 320;
    private int jump() { return leftHanded ? Input.Keys.Z : Input.Keys.J; }

    private void ladderScene(boolean left) {
        leftHanded = left; held.clear(); pressed.clear();
        level = new Level(assets, game, player(), "ladder-test", Rectangle::new);
        first = new CountingSwitch("first");
        level.objects.add(first);
        frames(2);
    }
    private void standAt(float centerX, float feetY) {
        var p = level.player;
        p.position.set(centerX - p.hitbox.offsetX - p.hitbox.width / 2, feetY - p.hitbox.offsetY - p.hitbox.height);
        p.velocity.setZero(); frames(10);
    }
    private float feet() { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }
    private float bodyCentre() { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }
    /** A mechanism zone covering the whole ladder column, so a fresh Up has a real target everywhere on it. */
    private void mechanismAlongLadder() {
        level.triggers.add(new com.jlogicsoftware.kimeria.entity.items.Trigger(5, 96, 240, 16, 80, "first"));
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void upClimbsToTheTopAndStepsOntoTheLedgeWhereUpDoesNotRegrab(boolean left) {
        ladderScene(left); standAt(LADDER_X - 6, FLOOR_Y);
        held.add(up()); frames(2);
        assertTrue(level.player.onLadder());
        assertEquals(Player.State.LADDER, level.player.getState());
        assertEquals(LADDER_X, bodyCentre(), .01, "Taking hold centres the hero on the rungs");
        float before = feet(); frames(30);
        assertTrue(feet() < before - 20, "Climbing moves up at a steady speed");
        frames(90);
        assertFalse(level.player.onLadder());
        assertEquals(LEDGE_Y, feet(), .05, "Stepping off the top stands on the ledge surface");
        frames(40);
        assertFalse(level.player.onLadder(), "Held Up on a ledge must not grab the ladder again");
        assertEquals(LEDGE_Y, feet(), .05);
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void downFromTheLedgeDescendsAndStepsOffAtTheBottom(boolean left) {
        ladderScene(left); standAt(LADDER_X, LEDGE_Y);
        assertEquals(LEDGE_Y, feet(), .05);
        held.add(down()); frames(2);
        assertTrue(level.player.onLadder());
        assertTrue(feet() > LEDGE_Y);
        frames(150);
        assertFalse(level.player.onLadder());
        assertEquals(FLOOR_Y, feet(), .05);
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void upAtTheTopSurfaceAndDownAtTheBottomDoNothing(boolean left) {
        ladderScene(left); standAt(LADDER_X, LEDGE_Y);
        held.add(up()); frames(20);
        assertFalse(level.player.onLadder());
        held.clear(); standAt(LADDER_X - 6, FLOOR_Y);
        held.add(down()); frames(20);
        assertFalse(level.player.onLadder());
        held.clear(); standAt(LADDER_X + 40, FLOOR_Y);
        held.add(up()); frames(20);
        assertFalse(level.player.onLadder(), "A ladder is only taken from beside its rungs");
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aFreshUpOnAMechanismOperatesItAndDoesNotClimbUntilReleasedAndPressedAgain(boolean left) {
        ladderScene(left); mechanismAlongLadder(); standAt(LADDER_X - 6, FLOOR_Y);
        held.add(up()); frames(20);
        assertEquals(1, first.count);
        assertFalse(level.player.onLadder());
        assertEquals(FLOOR_Y, feet(), .05);
        frames(30);
        assertFalse(level.player.onLadder(), "That Up press was spent on the lever; it must be pressed again to climb");
        assertEquals(1, first.count, "Holding Up never repeats the interaction");
        level.triggers.clear(); held.clear(); frames(2); held.add(up()); frames(2);
        assertTrue(level.player.onLadder());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void upOnTheLadderNeverInteractsAndKeepsClimbing(boolean left) {
        ladderScene(left); standAt(LADDER_X - 6, FLOOR_Y);
        held.add(up()); frames(30);
        assertTrue(level.player.onLadder());
        mechanismAlongLadder(); held.clear(); frames(2);
        float height = feet();
        held.add(up()); frames(10); // a fresh press, on the rungs, with a lever in reach
        assertEquals(0, first.count, "Interaction does nothing while on a ladder");
        assertTrue(level.player.onLadder()); assertTrue(feet() < height, "The press climbs instead");
        held.add(action()); pressed.add(action()); frames(1); pressed.clear(); frames(10);
        assertEquals(0, first.count, "Action never interacts");
        assertNotEquals(Player.State.ATTACKING, level.player.getState());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aStoneLyingOnTheLadderIsPickedUpFirstAndTheNextFreshUpClimbsWithIt(boolean left) {
        ladderScene(left); standAt(LADDER_X - 6, FLOOR_Y);
        level.objects.add(new com.jlogicsoftware.kimeria.entity.items.Carryable(level, 40,
            com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE, bodyCentre() - 4, feet() - 8));
        frames(1);
        held.add(up()); frames(15);
        assertNotNull(level.player.carriedObject(), "The first fresh Up takes the stone");
        assertFalse(level.player.onLadder(), "and that press is spent: it does not also climb");
        held.clear(); frames(2); held.add(up()); frames(5);
        assertTrue(level.player.onLadder(), "The next fresh Up climbs");
        assertNotNull(level.player.carriedObject(), "carrying the stone");
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void atTheLadderTopAFreshDownDescendsKeepingTheObjectAndTheLadderWinsOverPutDown(boolean left) {
        ladderScene(left); standAt(LADDER_X, LEDGE_Y);
        level.objects.add(new com.jlogicsoftware.kimeria.entity.items.Carryable(level, 41,
            com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE, bodyCentre() - 4, feet() - 8));
        frames(1); tap(up());
        assertNotNull(level.player.carriedObject());
        assertFalse(level.player.onLadder());
        held.add(down()); frames(3);
        assertTrue(level.player.onLadder(), "Down at the top climbs down");
        assertNotNull(level.player.carriedObject(), "and the object is kept, not put down");
    }

    @Test void aKeyTapShorterThanAFrameStillInteracts() {
        scene(false); level.triggers.clear(); frames(5);
        pressed.add(up()); frames(1); // the web delivers a press and release between two frames: only "just pressed" is seen
        assertNotNull(level.player.carriedObject(), "a sub-frame Up tap picks up");
        pressed.add(down()); frames(1);
        assertNull(level.player.carriedObject(), "and a sub-frame Down tap puts down");
    }

    /** A finger resting on the 60 degree line must not flip between interaction and climb (each flip would be a fresh press). */
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aTouchStickRestingOnTheAngleLineDoesNotChatter(boolean left) {
        scene(left); frames(10);
        int[] around = {36, 33, 36, 33, 35, 32}; // dy above the 60 degree line (34.6 for dx 20) and just under it, above the 55 degree release (28.6)
        finger(0, mirrored(100), 280 - 36); frames(1);
        assertTrue(touch.interactUpHeld(), "past the line it interacts");
        for (int dy : around) { finger(0, mirrored(100), 280 - dy); frames(1); assertTrue(touch.interactUpHeld(), "still held at dy " + dy); }
        finger(0, mirrored(100), 280 - 27); frames(1);
        assertFalse(touch.interactUpHeld(), "below the release angle it lets go");
        for (int dy : around) { finger(0, mirrored(100), 280 - Math.min(dy, 34)); frames(1); assertFalse(touch.interactUpHeld(), "and does not re-enter until the line: dy " + dy); }
        assertEquals(-1, level.player.verticalMovement(), "it keeps climbing throughout");
    }

    /** The pad twin of the touch test: a stick resting between the 0.3 release and the 0.4 press must not toggle the lever again and again. */
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aPadStickRestingBetweenThePressAndReleaseLevelsDoesNotChatter(boolean left) {
        scene(left);
        level.objects.removeAll(level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Carryable.class::isInstance).toList());
        frames(10);
        int verticalAxis = left ? mapping.axisRightY : mapping.axisLeftY;
        axes[verticalAxis] = -0.45f; frames(1);
        assertEquals(1, first.count, "past 0.4 the stick is one fresh Up");
        for (int i = 0; i < 20; i++) { axes[verticalAxis] = i % 2 == 0 ? -0.35f : -0.45f; frames(1); }
        assertEquals(1, first.count, "wobbling between 0.35 and 0.45 never presses again");
        assertEquals(-1, level.player.verticalMovement(), "and still climbs");
        axes[verticalAxis] = -0.25f; frames(2);
        assertEquals(0, level.player.verticalMovement(), "below 0.3 it lets go");
        for (int i = 0; i < 20; i++) { axes[verticalAxis] = i % 2 == 0 ? -0.35f : -0.25f; frames(1); }
        assertEquals(1, first.count, "and stays released until it passes 0.4 again");
        axes[verticalAxis] = -0.5f; frames(1);
        assertEquals(2, first.count, "a real new push is a new press");
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aFreshDownRightAfterSteppingOffALadderTopStillKeepsTheObject(boolean left) {
        ladderScene(left); standAt(LADDER_X, LEDGE_Y);
        level.objects.add(new com.jlogicsoftware.kimeria.entity.items.Carryable(level, 41,
            com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE, bodyCentre() - 4, feet() - 8));
        frames(1); tap(up());
        assertNotNull(level.player.carriedObject());
        held.add(down()); frames(3); held.clear(); frames(1);
        assertTrue(level.player.onLadder());
        held.add(up()); for (int i = 0; i < 400 && level.player.onLadder(); i++) frames(1);
        held.clear();
        assertFalse(level.player.onLadder(), "stepped off at the top");
        tap(down()); // inside the short regrab pause: the ladder still wins over put-down
        assertNotNull(level.player.carriedObject(), "the object is not put down at the ladder top");
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void upAndDownTogetherNeverInteractNorClimb(boolean left) {
        ladderScene(left); mechanismAlongLadder(); standAt(LADDER_X - 6, FLOOR_Y);
        held.add(up()); held.add(down()); frames(20);
        assertEquals(0, first.count); assertFalse(level.player.onLadder());
        assertEquals(0, level.player.verticalMovement());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void swordAndPickUpAreUnavailableOnALadderButRangedWorksAndKeepsTheGrip(boolean left) {
        ladderScene(left); standAt(LADDER_X - 6, FLOOR_Y);
        held.add(up()); frames(30); held.clear(); frames(1);
        assertTrue(level.player.onLadder());
        var stone = new com.jlogicsoftware.kimeria.entity.items.Carryable(level, 40,
            com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE, bodyCentre() - 4, feet() - 8);
        level.objects.add(stone); frames(1);
        float height = feet();
        tap(action());
        assertNotEquals(Player.State.ATTACKING, level.player.getState(), "The sword needs footing");
        tap(up());
        assertNull(level.player.carriedObject(), "Pick up needs footing too");
        assertTrue(level.player.onLadder());
        assertEquals(height, feet(), 1.5);
        held.clear(); frames(30);
        assertTrue(level.player.onLadder());
        tap(ranged());
        assertEquals(2, level.player.ammunition(), "A fireball can be fired from a ladder");
        assertTrue(level.player.onLadder(), "Firing does not let go");
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void jumpLetsGoWithAHopAndTheDirectionHeldDrivesItAwayFromTheLadder(boolean left) {
        ladderScene(left); standAt(LADDER_X - 6, FLOOR_Y);
        held.add(up()); frames(10); // Low on the ladder, so the hop cannot reach the ledge above.
        assertTrue(level.player.onLadder());
        held.add(left ? Input.Keys.RIGHT : Input.Keys.D); tap(jump());
        assertFalse(level.player.onLadder());
        assertTrue(level.player.velocity.y < 0, "Jumping off hops upward");
        frames(10);
        assertTrue(bodyCentre() > LADDER_X + 4, "The held direction carries the hero off the ladder");
        assertFalse(level.player.onLadder(), "Held Up must not snatch the ladder straight back");
        held.clear(); frames(200); // Let go of Up as well, or it would take hold again after the short delay.
        assertEquals(FLOOR_Y, feet(), .05, "The hero comes down onto the floor");
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void gamepadStickClimbsAndStepsOff(boolean left) {
        ladderScene(left); standAt(LADDER_X - 6, FLOOR_Y);
        int axis = left ? mapping.axisRightY : mapping.axisLeftY;
        axes[axis] = -1; frames(2);
        assertTrue(level.player.onLadder());
        frames(120);
        assertFalse(level.player.onLadder());
        assertEquals(LEDGE_Y, feet(), .05);
        axes[axis] = 1; frames(2);
        assertTrue(level.player.onLadder(), "Stick down from the top descends");
        axes[axis] = 0;
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void touchStickClimbsAndAnActionTouchNeitherOperatesNorStopsIt(boolean left) {
        ladderScene(left); standAt(LADDER_X - 6, FLOOR_Y);
        finger(0, mirrored(80), 245); frames(2);
        assertTrue(level.player.onLadder(), "Dragging the stick up takes hold");
        frames(5);
        float height = feet();
        finger(1, mirrored(520), 318); frames(1);
        assertNotEquals(Player.State.ATTACKING, level.player.getState(), "The sword needs footing");
        frames(20);
        assertTrue(feet() < height - 5, "The Action touch does not stop the climb");
        java.util.Arrays.fill(fingers, false); frames(1);
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void aLeaningTouchStickClimbsPastAMechanismAndASteepOneOperatesIt(boolean left) {
        ladderScene(left); mechanismAlongLadder(); standAt(LADDER_X, FLOOR_Y);
        finger(0, mirrored(92), 264); frames(2); // 12 px sideways (inside the dead zone), 16 up: about 53 degrees, climbs but is too shallow to interact
        assertTrue(level.player.onLadder());
        assertEquals(0, first.count);
        java.util.Arrays.fill(fingers, false); frames(1);
        ladderScene(left); mechanismAlongLadder(); standAt(LADDER_X, FLOOR_Y);
        finger(0, mirrored(80), 245); frames(3); // straight up
        assertEquals(1, first.count); assertFalse(level.player.onLadder(), "A steep push operates the lever and is spent");
        java.util.Arrays.fill(fingers, false); frames(1);
    }

    /** D126: hand-operated objects (a Trigger, a Stone) may not stand within 32 px of a ladder; a thrown-only trigger may. */
    @Test void aHandOperatedObjectNearALadderIsRejectedAtLoadButAThrowActivatedTriggerIsNot() {
        var trigger = assertThrows(IllegalArgumentException.class, () -> new Level(assets, game, player(), "ladder-near-trigger", Rectangle::new));
        assertTrue(trigger.getMessage().contains("Trigger") && trigger.getMessage().contains("D126"), trigger.getMessage());
        var stone = assertThrows(IllegalArgumentException.class, () -> new Level(assets, game, player(), "ladder-near-stone", Rectangle::new));
        assertTrue(stone.getMessage().contains("Stone") && stone.getMessage().contains("D126"), stone.getMessage());
        assertDoesNotThrow(() -> new Level(assets, game, player(), "ladder-near-throw", Rectangle::new));
        assertDoesNotThrow(() -> new Level(assets, game, player(), "ladder-far-trigger", Rectangle::new));
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void hintsNameTheStickOfTheDeviceInUseForUpAndDown(boolean left) {
        scene(left);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        level.triggers.clear();
        standByEntranceStone();
        int axis = left ? mapping.axisRightY : mapping.axisLeftY;
        axes[axis] = 1; frames(1); // Down on the stick: nothing to put down, so only the device changes
        assertNull(level.player.carriedObject());
        assertTrue(level.player.contextHint().contains((left ? "Right stick" : "Left stick") + " up: Pick up"), level.player.contextHint());
        axes[axis] = 0; frames(2);
        finger(0, mirrored(80), 315); frames(1); // stick down on the touch pad
        // D138 (#492): on touch the label names no stick, it says only what happens.
        assertTrue(level.player.contextHint().startsWith("Pick up"), level.player.contextHint());
        assertFalse(level.player.contextHint().contains("Stick"), level.player.contextHint());
        java.util.Arrays.fill(fingers, false); frames(1);
    }

    @Test void theFirstForestRoomTeachesTheLadderOnlyThere() {
        scene(false);
        level = new Level(assets, game, player(), PRACTICE_MAP, Rectangle::new);
        frames(2);
        var ladder = level.ladders.get(0);
        standAt(ladder.centerX(), ladder.bottomY());
        assertEquals("W: Climb", level.player.contextHint());
        held.add(up()); frames(2);
        assertTrue(level.player.onLadder());
        assertEquals("", level.player.contextHint(), "Once attached nothing more needs teaching");
        held.clear(); held.add(jump()); frames(1); held.clear();
        standAt(ladder.centerX(), ladder.topY());
        assertEquals("S: Climb down", level.player.contextHint());
        standAt(ladder.centerX() + 120, ladder.bottomY());
        assertEquals("", level.player.contextHint());
        ladderScene(false);
        standAt(LADDER_X - 6, FLOOR_Y);
        assertEquals("", level.player.contextHint(), "Rooms outside the first teaching room do not repeat the lesson");
    }

    // ---- forest-0: map-authored hints and the walkable tutorial skeleton (#195) ----

    private Level forestZero() {
        held.clear();
        level = new Level(assets, game, player(), "forest-0", Rectangle::new);
        frames(2);
        return level;
    }

    private void standAt(float bodyCenterX) {
        level.player.position.set(bodyCenterX - level.player.hitbox.offsetX - level.player.hitbox.width / 2,
            level.player.position.y);
        frames(2);
    }

    /**
     * The zone message sits 45% down its zone (#206, restored after #211 had raised it to 30%): near the middle
     * of the screen, never at the top. Pinned for every authored message zone of forest-0, so it cannot drift again.
     */
    @Test void everyForestZeroMessageAnchorIsFortyFivePercentDownItsZone() {
        forestZero();
        Vector2 anchor = new Vector2();
        int checked = 0;
        for (var zone : level.hintZones()) {
            if (zone.message() == null) continue;
            level.player.position.set(zone.bounds().x + zone.bounds().width / 2 - level.player.hitbox.offsetX - level.player.hitbox.width / 2,
                zone.bounds().y + zone.bounds().height - 80);
            frames(2);
            if (level.player.zoneHint().isEmpty()) continue;
            level.player.zoneHintPosition(anchor);
            float down = (anchor.y - zone.bounds().y) / zone.bounds().height;
            assertEquals(.45f, down, .001, "45% down the zone at x=" + zone.bounds().x);
            // A one-screen room (640x360, camera on the room) shows the anchor at least ~40% down the screen.
            if (zone.bounds().height <= 360.5f)
                assertTrue(anchor.y - zone.bounds().y >= .40f * zone.bounds().height, "message at/below 40% from the top");
            checked++;
        }
        assertTrue(checked >= 3, "forest-0 authors several message zones: " + checked);
    }

    @Test void authoredMessageHintShowsAtItsZoneAndRepeatsAfterRetryAndReturn() {
        forestZero();
        var zone = level.hintZones().get(0);
        float inside = zone.bounds().x + zone.bounds().width / 2;
        standAt(inside);
        // This scene is a touch device whose hero no key has moved yet: the first room says its touch variant all through (D139, #496).
        assertEquals("Drag the stick right to find the way out", level.player.zoneHint());
        assertEquals("", level.player.contextHint(), "Zone messages are their own channel (#211)");
        Vector2 anchor = new Vector2(); level.player.zoneHintPosition(anchor);
        assertEquals(zone.bounds().x + zone.bounds().width / 2, anchor.x, .001);
        // #206: drawn mid-screen (not at the top edge), and clear of the standing hero's head (y 272) even
        // for a two-line, 40 px label.
        assertEquals(zone.bounds().y + zone.bounds().height * .45f, anchor.y, .001);
        // The two-line label (40 px) grows upward from the anchor, so its top edge is anchor.y - 40 and its
        // bottom edge anchor.y must clear the hero's head at y 272.
        assertTrue(anchor.y - 40 > 60 && anchor.y < 272, "mid-screen, above the hero: " + anchor.y);
        // The hero's body centre is tested, so the hint must survive a jump: the zone spans the room.
        var room = level.currentRoomBounds();
        assertEquals(room.y, zone.bounds().y, .001); assertEquals(room.height, zone.bounds().height, .001);
        level.player.position.y -= 120; frames(1);
        assertEquals("Drag the stick right to find the way out", level.player.zoneHint(), "Still shown at jump height");
        level.player.position.y += 120; frames(30);
        standAt(zone.bounds().x + zone.bounds().width + 64);
        assertEquals(com.jlogicsoftware.kimeria.localization.Msg.HINT_LEVER_TORCH.t(game.language()), level.player.zoneHint(), "The next zone speaks next");
        level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); level.enterNextRoom(player()); frames(2);
        assertEquals("A cracked platform falls soon after you land. Cross quickly", level.player.zoneHint(), "Hints belong to their room's zones, not the whole level: the traps room speaks its own");
        level.resetCurrentRoom(player()); level.reloadAt(new Vector2(inside, 272)); frames(2);
        // Retry after death, restart, and a return to the room all rebuild the same authored hint.
        level.resetCurrentRoom(player()); frames(2);
        standAt(inside);
        assertEquals("Drag the stick right to find the way out", level.player.zoneHint());
        Level restarted = new Level(assets, game, player(), "forest-0", Rectangle::new);
        level = restarted; frames(2); standAt(inside);
        assertEquals("Drag the stick right to find the way out", level.player.zoneHint());
    }

    @Test void onlyTheForestZeroLessonRoomsTeachControls() {
        forestZero();
        standAt(level.hintZones().get(0).bounds().x + 40);
        assertTrue(level.controlHintsActive(), "The lever room (#186) teaches controls; later rooms' tickets add theirs");
        level.enterNextRoom(player()); frames(2);
        assertTrue(level.controlHintsActive(), "The ladder room (#196) teaches its own controls");
        level.enterNextRoom(player()); frames(2);
        assertTrue(level.controlHintsActive(), "The wall room (#197) teaches its own controls");
        level.enterNextRoom(player()); frames(2);
        assertTrue(level.controlHintsActive(), "The platform room (#198) teaches its own controls");
        level.enterNextRoom(player()); frames(2);
        assertTrue(level.controlHintsActive(), "The stone room (#199) teaches its own controls");
        level.enterNextRoom(player()); frames(2);
        assertFalse(level.controlHintsActive(), "The traps room (#200) has no lever, so no control zone");
        forestZero();
        level = new Level(assets, game, player(), "forest-2", Rectangle::new); frames(2);
        assertFalse(level.controlHintsActive(), "forest-2 has no control zone since #376: forest-0 teaches the controls");
        assertTrue(level.hintZones().isEmpty());
        level.enterNextRoom(player()); frames(2);
        assertFalse(level.controlHintsActive());
        assertNull(level.messageHint());
    }

    @Test void forestZeroIsWalkableRoomByRoomWithoutEnemiesAndEndsAtItsLevelExit() {
        forestZero();
        int rooms = level.roomLayout.rooms.size();
        assertEquals(6, rooms);
        for (int room = 0; room < rooms; room++) {
            assertTrue(level.objects.stream().noneMatch(o -> o instanceof Enemy<?> || o instanceof Boss),
                "forest-0 has no monsters in room " + room);
            held.clear(); held.add(Input.Keys.D);
            int budget = 60 * 40;
            while (budget-- > 0 && !level.atRoomExit() && completions == 0) frames(1);
            held.clear();
            if (room == 1) {
                assertFalse(level.atRoomExit(), "The ladder room's exit is on a ledge: walking right alone does not reach it");
                level.enterNextRoom(player()); frames(2); // The route itself is covered by LadderLessonTest.
            } else if (room == 2) {
                assertFalse(level.atRoomExit(), "The wall room's exit is behind a lowered wall: walking right alone does not reach it");
                level.enterNextRoom(player()); frames(2); // The route itself is covered by WallLessonTest.
            } else if (room == 3) {
                assertFalse(level.atRoomExit(), "The platform room's exit is on a ledge above a stopped platform: walking right alone does not reach it");
                level.enterNextRoom(player()); frames(2); // The route itself is covered by PlatformLessonTest.
            } else if (room == 4) {
                assertFalse(level.atRoomExit(), "The stone room's exit is across a chasm: walking right alone does not reach it");
                level.enterNextRoom(player()); frames(2); // The route itself is covered by StoneLessonTest.
            } else if (room == 5) {
                // Walking right alone drops into the pit; the route itself (jumps, bomb) is covered by TrapsLessonTest.
                assertFalse(level.atRoomExit(), "The last room has no exit, only the level's Checkpoint");
                level.resetCurrentRoom(player()); frames(2);
                int jumpBudget = 60 * 40;
                while (jumpBudget-- > 0 && completions == 0) {
                    var body = level.player.hitboxBounds();
                    float r = body.x + body.width;
                    held.clear(); held.add(Input.Keys.D);
                    if (level.player.isOnGround() && ((r >= 4030 && r < 4040) || (r >= 4126 && r < 4146))) { held.add(jump()); pressed.add(jump()); }
                    frames(1);
                }
                held.clear();
                assertEquals(1, completions, "The last room's Checkpoint is the level exit");
            } else if (room < rooms - 1) {
                assertTrue(level.atRoomExit(), "Room " + room + " exit is reachable by walking right");
                level.enterNextRoom(player()); frames(2);
            }
        }
    }
}
