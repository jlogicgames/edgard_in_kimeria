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
import com.jlogicsoftware.kimeria.effects.BloodBurst;
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

    @BeforeEach void setUp() {
        previousApp = Gdx.app; previousGraphics = Gdx.graphics;
        previousInput = Gdx.input; previousFiles = Gdx.files;
        GdxNativesLoader.load();
        HeadlessFiles files = new HeadlessFiles();
        Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(), new Class<?>[]{Files.class}, (proxy, method, args) -> {
            // Read the exact shipped map under an alias, omitting only forest-1's decorative sky/rain.
            // All room markers, collision objects, hazards, enemies, and player physics remain intact.
            if (method.getName().equals("internal") && args[0].equals("tiles/replay-forest-1.tmx"))
                return files.internal("tiles/forest-1.tmx");
            return method.invoke(files, args);
        });
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
    void standingLookUsesStaticPosesAndDelayedCameraInBothLayouts(boolean left) {
        scene(left); frames(10);
        level.triggers.clear();
        level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable);
        float neutral = lastCameraTarget.y;
        held.add(up()); frames(1);
        assertEquals(Player.State.LOOK_UP, level.player.getState());
        frames(10); assertEquals(neutral, lastCameraTarget.y, .001);
        frames(12); assertTrue(lastCameraTarget.y < neutral, "Without an interaction, inspection starts after 0.3 s");
        frames(140); assertEquals(neutral - 100, lastCameraTarget.y, .01);
        held.clear(); frames(125);
        assertEquals(Player.State.IDLE, level.player.getState());
        assertEquals(neutral, lastCameraTarget.y, .01);
        held.add(down()); frames(181);
        assertEquals(Player.State.LOOK_DOWN, level.player.getState());
        assertEquals(neutral + 150, lastCameraTarget.y, .01);
        held.add(up()); frames(125);
        assertEquals(Player.State.IDLE, level.player.getState());
        assertEquals(neutral, lastCameraTarget.y, .01);
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void pickupAndMechanismEachDelayInspectionForOneSecond(boolean left) {
        for (boolean mechanism : new boolean[]{false, true}) {
            held.clear(); scene(left); frames(10);
            if (mechanism) level.objects.removeIf(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable);
            else level.triggers.clear();
            assertNotNull(mechanism ? level.player.selectedMechanism() : level.player.selectedCarryable());
            float neutral = lastCameraTarget.y;
            held.add(mechanism ? up() : down()); frames(54);
            assertEquals(mechanism ? Player.State.LOOK_UP : Player.State.LOOK_DOWN, level.player.getState());
            assertEquals(neutral, lastCameraTarget.y, .001);
            frames(12);
            assertTrue(mechanism ? lastCameraTarget.y < neutral : lastCameraTarget.y > neutral);
            frames(60);
            assertEquals(neutral + (mechanism ? -100 : 150), lastCameraTarget.y, .01);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void actionMovementJumpAndUiBoundariesTakePriorityOverLooking(boolean left) {
        scene(left); frames(10);
        held.add(up()); frames(181);
        level.player.resetJumpInput(); frames(181);
        assertEquals(Player.State.IDLE, level.player.getState(), "Held direction cannot leak across a UI boundary");
        held.clear(); frames(1); held.add(up()); frames(2);
        assertEquals(Player.State.LOOK_UP, level.player.getState());
        held.add(action()); frames(2);
        assertEquals(1, first.count); assertNotEquals(Player.State.LOOK_UP, level.player.getState());
        frames(90); assertEquals(1, first.count);
        held.clear(); frames(2); held.add(up());
        held.add(left ? Input.Keys.RIGHT : Input.Keys.D); frames(2);
        assertEquals(Player.State.RUNNING, level.player.getState());
        held.clear(); frames(2); held.add(up());
        held.add(left ? Input.Keys.Z : Input.Keys.J); frames(2);
        assertNotEquals(Player.State.LOOK_UP, level.player.getState());
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
    private void pickup() { held.add(down()); tap(action()); held.remove(down()); }

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
        assertEquals(31, torch.getX() + 5 - level.player.getX(), .001);
        assertTrue(torch.getY() + 8 - level.player.getY() >= 32);
        assertTrue(torch.getY() + 8 - level.player.getY() <= 35);
        level.player.facingRight = false;
        torch.followHolder();
        assertEquals(16, torch.getX() + 5 - level.player.getX(), .001);
        tap(Input.Keys.J); // Native jump frame's free palm is raised, far above the waist.
        assertEquals(Player.State.JUMPING, level.player.getState());
        assertEquals(26, torch.getX() + 5 - level.player.getX(), .001);
        assertEquals(17, torch.getY() + 8 - level.player.getY(), .001);
        // It is omitted from the world pass; Player.render owns its foreground pass.
        var batch = (com.badlogic.gdx.graphics.g2d.Batch) Proxy.newProxyInstance(
            com.badlogic.gdx.graphics.g2d.Batch.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.graphics.g2d.Batch.class}, (proxy, method, args) -> {
                fail("Held torch must not render behind the hero"); return null;
            });
        torch.render(batch);
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void keyboardSelectsSwordSingleTargetPickupPlaceAndConflicts(boolean left) {
        scene(left);
        assertEquals(7, level.player.selectedMechanism().id);
        assertEquals(20, level.player.selectedCarryable().id);
        tap(action());
        assertEquals(Player.State.ATTACKING, level.player.getState());
        assertEquals(0, first.count); assertNull(level.player.carriedObject());
        frames(50);
        held.add(up()); tap(action()); held.remove(up());
        assertEquals(1, first.count); assertEquals(0, second.count);
        held.add(up()); held.add(down()); tap(action()); held.clear();
        assertEquals(1, first.count); assertNull(level.player.carriedObject());
        held.add(down()); held.add(action()); frames(1);
        assertEquals(20, level.player.carriedObject().id);
        frames(60); // Holding never turns pickup into putting down.
        assertEquals(20, level.player.carriedObject().id);
        held.clear(); frames(1);
        tap(action()); assertEquals(Player.State.ATTACKING, level.player.getState());
        assertNotNull(level.player.carriedObject());
        frames(50);
        held.add(down()); tap(action()); held.clear();
        assertNull(level.player.carriedObject());
        assertEquals(3, level.player.ammunition());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void throwingConsumesOnlyRangedPressAndFireballsConsumeOneCharge(boolean left) {
        scene(left); pickup();
        assertNotNull(level.player.carriedObject());
        held.add(ranged()); frames(1);
        assertNull(level.player.carriedObject());
        assertEquals(3, level.player.ammunition());
        frames(30); assertEquals(3, level.player.ammunition());
        assertFalse(level.objects.stream().anyMatch(com.jlogicsoftware.kimeria.entity.items.Fireball.class::isInstance));
        held.clear(); frames(1);
        tap(ranged()); assertEquals(2, level.player.ammunition());
        assertTrue(level.objects.stream().anyMatch(com.jlogicsoftware.kimeria.entity.items.Fireball.class::isInstance));
        tap(ranged()); tap(ranged()); tap(ranged());
        assertEquals(0, level.player.ammunition());
        assertEquals(com.jlogicsoftware.kimeria.localization.Msg.NO_AMMO, level.player.rangedLabel());
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
        held.add(action()); held.add(ranged()); held.add(down());
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
        buttons[actionButton] = true; frames(1);
        assertNotNull(level.player.carriedObject()); assertEquals(0, level.player.verticalMovement());
        assertTrue(level.player.actionPrompt().contains(left ? "D-pad" : "X/B"));
        buttons[actionButton] = false; axes[verticalAxis] = 0; frames(1);
        buttons[actionButton] = true; frames(1);
        assertEquals(Player.State.ATTACKING, level.player.getState());
        assertNotNull(level.player.carriedObject());
        buttons[actionButton] = false; frames(50);
        buttons[rangedButton] = true; frames(1);
        assertNull(level.player.carriedObject()); assertEquals(3, level.player.ammunition());
        buttons[rangedButton] = false; frames(1);
        buttons[rangedButton] = true; frames(1);
        assertEquals(2, level.player.ammunition());
        buttons[rangedButton] = false; frames(1);
        axes[verticalAxis] = -1; buttons[actionButton] = true; frames(1);
        assertEquals(1, first.count); assertEquals(0, second.count);
    }

    private void finger(int p, float x, int y) { fingers[p] = true; pointerX[p] = (int) x; pointerY[p] = y; }
    private float mirrored(float x) { return leftHanded ? 640 - x : x; }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void gamepadAndTouchLookThroughTheirActiveMovementStick(boolean left) {
        scene(left); frames(10);
        float neutral = lastCameraTarget.y;
        int axis = left ? mapping.axisRightY : mapping.axisLeftY;
        axes[axis] = -1; frames(181);
        assertEquals(Player.State.LOOK_UP, level.player.getState());
        assertTrue(lastCameraTarget.y < neutral);
        axes[axis] = 0; frames(125);
        finger(0, mirrored(80), 315); frames(181);
        assertEquals(Player.State.LOOK_DOWN, level.player.getState());
        assertEquals(neutral + 150, lastCameraTarget.y, .01);
        finger(1, mirrored(520), 318); frames(2);
        assertNotNull(level.player.carriedObject());
        assertNotEquals(Player.State.LOOK_DOWN, level.player.getState());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void touchClaimsTwoPointersDragsVerticallyAndSuppressesMenuHold(boolean left) {
        scene(left);
        finger(0, mirrored(80), 315); frames(1);
        assertEquals(1, level.player.verticalMovement());
        finger(1, mirrored(520), 318); frames(1);
        assertNotNull(level.player.carriedObject());
        assertEquals(com.jlogicsoftware.kimeria.localization.Msg.PUT_DOWN, level.player.actionLabel());
        frames(30); assertNotNull(level.player.carriedObject());
        fingers[1] = false; frames(1);
        pointerY[0] = 245; // Same claimed stick, dragged upward.
        finger(1, mirrored(520), 318); frames(1);
        assertEquals(1, first.count); assertEquals(0, second.count);
        assertEquals(0, level.player.verticalMovement());
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
        level.collisionBlocks.add(com.jlogicsoftware.kimeria.entity.environment.CollisionBlock.plain(63, 300, 16, 20));
        held.add(down()); tap(action());
        assertNotNull(level.player.carriedObject());
        assertEquals(com.jlogicsoftware.kimeria.localization.Msg.PLACE_BLOCKED, level.player.actionLabel());
        assertEquals(3, level.player.ammunition());
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

    @Test void projectilesHitOrdinaryEnemiesButCannotDamageTheSwordOnlyBoss() {
        scene(false);
        var mob = new com.jlogicsoftware.kimeria.entity.enemy.YellowMob(assets, game, 100, 288, 48, 32, 0, 0);
        mob.setPlayer(level.player); mob.setLevel(level); level.objects.add(mob);
        tap(ranged()); frames(20);
        assertTrue(mob.isDefeated());
        level = new Level(assets, game, player(), "forest", Rectangle::new);
        level.enterNextRoom(player()); level.enterNextRoom(player()); frames(2);
        var boss = level.boss();
        int energy = boss.energy();
        var fireball = new com.jlogicsoftware.kimeria.entity.items.Fireball(level, boss.hitboxBounds().x, boss.hitboxBounds().y, true);
        level.objects.add(fireball); fireball.update(REAL_STEP);
        assertTrue(fireball.isRemoved()); assertEquals(energy, boss.energy());
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
        finger(0, 80, 315); frames(1);
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
        level = new Level(assets, game, player(), "forest", Rectangle::new);
        var trigger = level.triggers.stream().filter(t -> t.targetId.equals("practice-light")).findFirst().orElseThrow();
        level.player.position.set(trigger.centerX() - level.player.hitbox.offsetX - level.player.hitbox.width / 2,
            trigger.centerY() - level.player.hitbox.offsetY - level.player.hitbox.height / 2);
        frames(2);
        held.add(up()); frames(1);
        var mechanism = level.player.interactionFeedbackTarget();
        var lever = assertInstanceOf(com.jlogicsoftware.kimeria.entity.items.Lever.class, mechanism);
        assertSame(trigger, lever.trigger);
        assertEquals(trigger.centerX(), lever.centerX(), .001);
        assertEquals(trigger.getY() + trigger.getHeight(), lever.getY() + lever.getHeight(), .001);
        assertEquals(16, lever.getWidth()); assertEquals(16, lever.getHeight());
        var torch = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.effects.Torch t
            && t.getTargetId().equals("practice-light"))
            .map(com.jlogicsoftware.kimeria.effects.Torch.class::cast).findFirst().orElseThrow();
        assertFalse(torch.isBurning()); assertFalse(lever.isUp());
        assertTrue(level.player.actionPrompt().contains("Turn on"));
        assertTrue(level.player.actionPrompt().endsWith("Lever"));
        List<Object> drawn = new ArrayList<>();
        var batch = (com.badlogic.gdx.graphics.g2d.Batch) Proxy.newProxyInstance(
            com.badlogic.gdx.graphics.g2d.Batch.class.getClassLoader(),
            new Class<?>[]{com.badlogic.gdx.graphics.g2d.Batch.class}, (proxy, method, args) -> {
                if (method.getName().equals("draw")) drawn.add(args[0]); return null;
            });
        lever.render(batch);
        held.add(action()); frames(1);
        assertTrue(torch.isBurning()); assertTrue(lever.isUp());
        assertTrue(level.player.actionPrompt().contains("Turn off"));
        lever.render(batch); assertNotSame(drawn.get(0), drawn.get(1));
        frames(30); assertTrue(lever.isUp(), "A held action must not repeatedly toggle the lever");
        held.remove(action()); frames(1); tap(action());
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
        held.add(down()); frames(1);
        var pickup = level.player.interactionFeedbackTarget();
        assertSame(level.player.selectedCarryable(), pickup);
        assertEquals(com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.TORCH,
            ((com.jlogicsoftware.kimeria.entity.items.Carryable) pickup).kind);
        assertNotSame(level.player, pickup);
        held.add(up()); frames(1); assertNull(level.player.interactionFeedbackTarget());
        held.clear(); frames(1); assertNull(level.player.interactionFeedbackTarget());
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
        held.add(down()); frames(1);
        assertNull(level.player.selectedCarryable());
        assertEquals("", level.player.contextHint());
        assertNull(level.player.interactionFeedbackTarget());
        tap(action()); assertNull(level.player.carriedObject());
        assertNotEquals(Player.State.ATTACKING, level.player.getState());
        held.clear();
        held.add(left ? Input.Keys.RIGHT : Input.Keys.D); frames(6); held.clear(); frames(1);
        assertSame(stone, level.player.selectedCarryable());
        assertEquals("", level.player.contextHint());
        pickup(); frames(1); assertSame(stone, level.player.carriedObject());
        assertEquals("", level.player.contextHint());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void trainingRoomRepeatsPickupPlaceAndThrow(boolean left) {
        scene(left);
        level = new Level(assets, game, player(), "forest", Rectangle::new);
        // Isolate carrying reminders here; the combined lever lesson/anchor has its own regression below.
        level.triggers.clear();
        standByEntranceStone();
        assertTrue(level.player.contextHint().contains(left ? "Down + X" : "S + K"));
        pickup(); frames(1);
        assertNotNull(level.player.carriedObject());
        assertTrue(level.player.contextHint().contains(left ? "C: Throw" : "L: Throw"));
        assertTrue(level.player.contextHint().contains(left ? "Down + X: Put down" : "S + K: Put down"));
        Vector2 anchor = new Vector2(); level.player.contextHintPosition(anchor);
        frames(120);
        held.add(left ? Input.Keys.RIGHT : Input.Keys.D); frames(35); held.clear();
        assertTrue(level.player.contextHint().contains("Put down"), "The old 50 px limit must not suppress in-zone practice");
        assertTrue(level.player.contextHint().contains("Throw"));
        Vector2 movedAnchor = new Vector2(); level.player.contextHintPosition(movedAnchor);
        assertEquals(anchor, movedAnchor);
        held.add(down()); tap(action()); held.clear(); frames(1);
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
        level = new Level(assets, game, player(), "forest", Rectangle::new);
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
            assertTrue(level.tutorialHintsActive(), "The entire first room teaches, including beyond the old rectangle");
            assertTrue(level.player.contextHint().contains("Pick up"));
        }
    }

    @Test void trainingIsAbsentInLaterRoomsAndAvailableWhenReturningToTheFirstRoom() {
        scene(false);
        level = new Level(assets, game, player(), "forest", Rectangle::new);
        assertTrue(level.tutorialHintsActive());
        level.enterNextRoom(player());
        assertFalse(level.tutorialHintsActive());
        level.returnForCollection(player());
        standByEntranceStone();
        assertNotNull(level.player.selectedCarryable());
        assertTrue(level.tutorialHintsActive());
        assertTrue(level.player.contextHint().contains("Pick up"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false, STONE", "true, STONE", "false, TORCH", "true, TORCH"})
    void carryingStillTeachesTheNearbyLeverAtItsOwnAnchor(boolean left,
            com.jlogicsoftware.kimeria.entity.items.Carryable.Kind kind) {
        scene(left);
        level = new Level(assets, game, player(), "forest", Rectangle::new);
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
        assertTrue(hint.startsWith(left ? "Up + X: Turn on - Lever" : "W + K: Turn on - Lever"));
        assertTrue(hint.contains("Put down")); assertTrue(hint.contains("Throw"));
        assertEquals(3, hint.lines().count());
        Vector2 mechanismAnchor = new Vector2(); level.player.contextHintPosition(mechanismAnchor);
        assertEquals(level.mechanismFeedbackTarget(trigger).centerX(), mechanismAnchor.x, .001);
        assertNotEquals(pickupAnchor, mechanismAnchor, "The mechanism lesson belongs by the lever, not the old pickup");
        held.add(up()); tap(action()); held.clear(); frames(1);
        assertSame(item, level.player.carriedObject(), "Operating the lever must not place or consume the held item");
        assertTrue(level.player.contextHint().contains("Turn off - Lever"));
        held.add(up()); tap(action()); held.clear(); frames(1);
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
        assertFalse(level.tutorialHintsActive());
        assertEquals("", level.player.contextHint());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void carryingReminderEndsOnCameraScrollAndRearmsOnEachPickup(boolean left) {
        scene(left);
        level = new Level(assets, game, player(), "forest", Rectangle::new);
        level.triggers.clear();
        var stone = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Carryable c
            && c.kind == com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.STONE)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        // Clear sky inside the teaching room avoids terrain/enemies changing this camera-only setup.
        level.player.position.set(700, 40); frames(1);
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
        pickup(); assertNull(level.player.carriedObject()); // place
        pickup(); assertSame(stone, level.player.carriedObject());
        assertTrue(level.player.contextHint().contains("Put down"), "Every new pickup in the first room teaches again");
    }

}
