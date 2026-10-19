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

/** Full player/boss integration with the authored room and real movement/input. */
class BossCombatTest {
    private static final float REAL_STEP = 1f / 60f;

    private final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private float timeScale = 1f;
    private int coins, completions, resets;
    private boolean soundsEnabled;
    private final List<String> playedSounds = new ArrayList<>();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private TouchControls touch;
    private final GamepadInput pad = new GamepadInput();
    private final TrapWarnings warnings = new TrapWarnings(); // assist stays off
    private final Assets assets = new Assets() {
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
            case "isGameStarted" -> true;
            case "setNormalTime" -> { timeScale = 1f; yield null; }
            case "setTimeScale" -> { timeScale = (float) args[0]; yield null; }
            case "addCoin" -> { coins++; yield null; }
            case "coinsCollected" -> coins;
            case "artifacts" -> artifacts;
            case "playSounds" -> soundsEnabled;
            case "playSound" -> { playedSounds.add((String) args[0]); yield null; }
            case "loadNextLevel" -> { completions++; yield null; }
            case "resetRoom" -> { resets++; yield null; }
            // Invulnerability and sound are off; camera callbacks do not affect physics.
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
        touch = new TouchControls(assets, game, null, 640, 360);
    }

    @AfterEach void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics;
        Gdx.input = previousInput; Gdx.files = previousFiles;
    }

    private Player player() { return new Player(assets, game, 0, 0); }

    private Level level;
    private Boss boss;

    private void encounter() {
        level = new Level(assets, game, player(), "forest", Rectangle::new);
        level.enterNextRoom(player());
        level.enterNextRoom(player());
        boss = level.objects.stream().filter(Boss.class::isInstance).map(Boss.class::cast).findFirst().orElseThrow();
        frames(20);
        assertEquals(Boss.State.THRONE, boss.getState());
    }

    private void frames(int count) {
        for (int i = 0; i < count; i++) { level.update(REAL_STEP); pressed.clear(); }
    }

    private void untilState(Boss.State state) {
        for (int frame = 0; frame < 180 && boss.getState() != state; frame++) frames(1);
        assertEquals(state, boss.getState(), "Boss failed to reach " + state);
    }

    private void outsideSwing() { level.player.position.set(boss.getX() - 90, 272); }
    // Facing left boss: body at x+7..25, mirrored weapon x-8..8. Sword hits from safe spacing.
    private void inSwordRange() {
        level.player.position.set(boss.getX() - 36, 272);
        level.player.facingRight = true;
        level.player.setOnGround(true);
    }
    private void startNormal() {
        inSwordRange(); frames(2);
        assertEquals(Boss.State.NORMAL, boss.getState());
    }
    private void sword() { pressed.add(Input.Keys.K); frames(1); }
    private Vector2 shake() { return level.cameraShakeOffset(new Rectangle(1856, 0, 640, 360)); }

    private static void gameField(KimeriaGame target, String name, Object value) throws Exception {
        var field = KimeriaGame.class.getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void gameState(KimeriaGame target, String name) throws Exception {
        var field = KimeriaGame.class.getDeclaredField("uiState"); field.setAccessible(true);
        field.set(target, Enum.valueOf((Class) field.getType(), name));
    }

    @Test void f6UsesTheRoomFadeAndStopsForHealerBeforeTheBoss() throws Exception {
        level = new Level(assets, game, player(), "forest", Rectangle::new);
        KimeriaGame running = new KimeriaGame();
        gameField(running, "assets", assets); gameField(running, "level", level);
        gameField(running, "player", level.player); running.setGameStarted(true);
        var hotkeys = KimeriaGame.class.getDeclaredMethod("handleDevHotkeys"); hotkeys.setAccessible(true);
        var enter = KimeriaGame.class.getDeclaredMethod("enterNextRoom"); enter.setAccessible(true);
        var fadeField = KimeriaGame.class.getDeclaredField("roomFade"); fadeField.setAccessible(true);
        RoomFade fade = (RoomFade) fadeField.get(running);
        pressed.add(Input.Keys.F6);
        gameState(running, "PAUSED"); hotkeys.invoke(running);
        assertFalse(fade.active(), "Pause must ignore the shortcut");
        gameState(running, "PLAYING");
        for (int room = 1; room <= 2; room++) {
            hotkeys.invoke(running); assertTrue(fade.active());
            hotkeys.invoke(running); // The same/held event must not restart an active transition.
            for (int frame = 0; frame < 4; frame++) fade.update(.25f, () -> {
                try { enter.invoke(running); } catch (Exception e) { throw new RuntimeException(e); }
            });
            assertFalse(fade.active());
        }
        assertEquals(1280, level.currentRoomBounds().x);
        assertNull(level.boss(), "Healer opens before entering the boss room");
        assertFalse(running.isGameStarted());
        var state = KimeriaGame.class.getDeclaredField("uiState"); state.setAccessible(true);
        assertEquals("HEALER", state.get(running).toString());
        var choose = KimeriaGame.class.getDeclaredMethod("chooseHealer", com.jlogicsoftware.kimeria.ui.HealerDialogue.Choice.class);
        choose.setAccessible(true);
        choose.invoke(running, com.jlogicsoftware.kimeria.ui.HealerDialogue.Choice.FIGHT);
        for (int frame = 0; frame < 4; frame++) fade.update(.25f, () -> {
            try { enter.invoke(running); } catch (Exception e) { throw new RuntimeException(e); }
        });
        assertEquals(1952, level.currentRoomBounds().x);
        assertEquals(new Vector2(2032, 272), level.player.position);
        assertNotNull(level.boss());
        assertFalse(level.healerAhead());
        hotkeys.invoke(running);
        assertFalse(fade.active(), "F6 at the last room must be a no-op");
        assertEquals(0, completions);
        pressed.clear();
    }

    @Test void nextLevelAfterBossVictoryGetsAFreshControllablePlayer() throws Exception {
        encounter();
        artifacts.drinkBossElixir("forest");
        boss.collidedWithActor(true); boss.collidedWithActor(true);
        frames(550);
        assertEquals(1, completions);
        Player exiting = level.player;
        assertFalse(exiting.canFight());

        KimeriaGame running = new KimeriaGame();
        gameField(running, "assets", assets); gameField(running, "level", level);
        gameField(running, "player", exiting); gameField(running, "currentLevelIndex", 0);
        gameField(running, "touchControls", touch); gameField(running, "playSounds", false);
        int[] saveWrites = {0};
        var preferences = (com.badlogic.gdx.Preferences) Proxy.newProxyInstance(
            com.badlogic.gdx.Preferences.class.getClassLoader(), new Class<?>[]{com.badlogic.gdx.Preferences.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "contains" -> false;
                case "putInteger" -> proxy;
                case "flush" -> { saveWrites[0]++; yield null; }
                default -> null;
            });
        var progress = new com.jlogicsoftware.kimeria.SavedProgress(preferences, 2);
        gameField(running, "savedProgress", progress);
        held.add(Input.Keys.K); held.add(Input.Keys.D);
        running.loadNextLevel();
        var healerField = KimeriaGame.class.getDeclaredField("healer"); healerField.setAccessible(true);
        var dialogue = (com.jlogicsoftware.kimeria.ui.HealerDialogue) healerField.get(running);
        assertTrue(dialogue.postVictory());
        assertFalse(progress.exists(), "Dialogue entry must not write a save");
        assertEquals(0, saveWrites[0]);
        assertSame(exiting, getGamePlayer(running));
        running.loadNextLevel();
        assertSame(dialogue, healerField.get(running), "Duplicate completion cannot reopen the scene");
        gameField(running, "uiViewport", new com.badlogic.gdx.utils.viewport.FitViewport(640, 360));
        gameField(running, "healerView", new com.jlogicsoftware.kimeria.ui.HealerDialogueView());
        var update = KimeriaGame.class.getDeclaredMethod("updateHealer", float.class); update.setAccessible(true);
        update.invoke(running, 0f);
        assertEquals("", dialogue.text(com.jlogicsoftware.kimeria.localization.Language.ENGLISH),
            "Held fight and movement inputs cannot reveal the opening line");
        held.clear(); update.invoke(running, 0f);
        for (int i = 0; i < 6; i++) {
            held.add(Input.Keys.K); update.invoke(running, 0f);
            held.clear();
            if (i < 5) update.invoke(running, 0f);
        }
        assertNull(healerField.get(running));
        assertEquals(1, progress.nextLevelIndex(), "First-level boss must lead to the single-room second level");
        var choose = KimeriaGame.class.getDeclaredMethod("chooseHealer", com.jlogicsoftware.kimeria.ui.HealerDialogue.Choice.class);
        choose.setAccessible(true);
        choose.invoke(running, com.jlogicsoftware.kimeria.ui.HealerDialogue.Choice.CONTINUE);
        assertEquals(1, progress.nextLevelIndex(), "Repeated final advance cannot complete another level");
        assertEquals(1, saveWrites[0], "The entire post-boss flow writes exactly one save");
        assertFalse(running.isGameStarted(), "The loading screen still owns the transition");
        var playerField = KimeriaGame.class.getDeclaredField("player"); playerField.setAccessible(true);
        Player arriving = (Player) playerField.get(running);
        assertNotSame(exiting, arriving, "Scripted victory state must not survive into the new level");
        // Same shipped single-room second-level map; the alias only omits GL-only decorative sky in headless tests.
        Level next = new Level(assets, running, arriving, "replay-forest-1", Rectangle::new);
        gameField(running, "level", next);
        assertTrue(next.player.canFight());
        running.setGameStarted(true);
        for (int frame = 0; frame < 30; frame++) next.update(REAL_STEP);
        float start = next.player.getX();
        held.add(Input.Keys.D);
        for (int frame = 0; frame < 3; frame++) next.update(REAL_STEP);
        held.clear();
        assertTrue(next.player.getX() > start, "Movement input must work after victory");
        assertEquals(Player.State.RUNNING, next.player.getState());
        pressed.add(Input.Keys.K); next.update(REAL_STEP); pressed.clear();
        assertEquals(Player.State.ATTACKING, next.player.getState());
        for (int frame = 0; frame < 50; frame++) next.update(REAL_STEP);
        held.add(Input.Keys.J); next.update(REAL_STEP); held.clear();
        assertTrue(next.player.velocity.y < 0, "Jump input must work after victory");
    }

    private Player getGamePlayer(KimeriaGame running) throws Exception {
        var field = KimeriaGame.class.getDeclaredField("player"); field.setAccessible(true);
        return (Player) field.get(running);
    }

    @Test void realBossExitOpensGuideOnlyAfterDeathAndWalk() throws Exception {
        KimeriaGame running = new KimeriaGame();
        gameField(running, "assets", assets); gameField(running, "touchControls", touch);
        gameField(running, "currentLevelIndex", 0); gameField(running, "playSounds", false);
        gameField(running, "player", new Player(assets, running, 0, 0));
        var progress = new com.jlogicsoftware.kimeria.SavedProgress(null, 2);
        gameField(running, "savedProgress", progress);
        var load = KimeriaGame.class.getDeclaredMethod("loadLevel"); load.setAccessible(true); load.invoke(running);
        var field = KimeriaGame.class.getDeclaredField("level"); field.setAccessible(true);
        Level world = (Level) field.get(running);
        world.enterNextRoom(new Player(assets, running, 0, 0));
        world.enterNextRoom(new Player(assets, running, 0, 0));
        gameField(running, "player", world.player);
        running.setGameStarted(true); gameState(running, "PLAYING");
        assertFalse(world.victoryExitComplete());
        world.player.collidedWithActor(false);
        assertFalse(world.victoryExitComplete(), "Damage cannot open the guide scene");
        Player retry = new Player(assets, running, 0, 0);
        world.resetCurrentRoom(retry); gameField(running, "player", retry);
        running.artifacts().drinkBossElixir("forest");
        world.boss().collidedWithActor(true); world.boss().collidedWithActor(true);
        for (int i = 0; i < 30; i++) world.update(REAL_STEP);
        assertFalse(world.victoryExitComplete(), "Death presentation must finish first");
        assertTrue(running.isGameStarted());
        for (int i = 0; i < 550; i++) world.update(REAL_STEP);
        assertTrue(world.victoryExitComplete());
        var healerField = KimeriaGame.class.getDeclaredField("healer"); healerField.setAccessible(true);
        var dialogue = (com.jlogicsoftware.kimeria.ui.HealerDialogue) healerField.get(running);
        assertTrue(dialogue.postVictory()); assertFalse(running.isGameStarted());
        assertFalse(progress.exists());
        for (int i = 0; i < 100; i++) world.update(REAL_STEP);
        assertSame(dialogue, healerField.get(running), "The completed exit sends only one callback");
    }

    @Test void normalUsesSeventhArtRowAndEveryCounterPoseUsesThirdRow() throws Exception {
        Map<TextureRegion, Integer> rows = new IdentityHashMap<>();
        Assets tagged = new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) {
                TextureRegion region = new TextureRegion();
                rows.put(region, (int) y / 32 + 1); return region;
            }
        };
        Boss actor = new Boss(tagged, game, null, 0, 0);
        var field = Actor.class.getDeclaredField("animations"); field.setAccessible(true);
        Map<?, ?> animations = (Map<?, ?>) field.get(actor);
        Animation<?> normal = (Animation<?>) animations.get(Boss.State.NORMAL);
        assertEquals(1.4f / 2.25f, normal.getAnimationDuration(), .00001f);
        for (Object frame : normal.getKeyFrames()) assertEquals(7, rows.get(frame));
        for (Boss.State state : new Boss.State[]{Boss.State.WARNING, Boss.State.COUNTER, Boss.State.COUNTER_RECOVERY}) {
            Animation<?> animation = (Animation<?>) animations.get(state);
            for (Object frame : animation.getKeyFrames()) assertEquals(3, rows.get(frame));
        }
        Animation<?> counter = (Animation<?>) animations.get(Boss.State.COUNTER);
        assertEquals(.3f / 2.25f, counter.getAnimationDuration(), .00001f);
    }

    @Test void nextRoomShortcutCanReachTheBossAndCannotAdvancePastTheLastRoom() {
        level = new Level(assets, game, player(), "forest", Rectangle::new);
        assertTrue(level.hasNextRoom());
        level.enterNextRoom(player());
        assertTrue(level.hasNextRoom());
        level.enterNextRoom(player());
        assertNotNull(level.boss());
        assertEquals(new Vector2(2032, 272), level.player.position);
        assertFalse(level.hasNextRoom());
        assertEquals(0, completions, "Room skipping must not write level completion");
    }

    @Test void damageAndRetryFeedbackPreserveCombatAndArrivalTiming() {
        soundsEnabled = true;
        encounter();
        assertFalse(playedSounds.contains("boss_restore"), "Entering a fresh room is not a retry");
        startNormal(); sword();
        assertEquals(1, playedSounds.stream().filter("boss_hit"::equals).count());
        assertEquals(1, level.objects.stream().filter(BloodBurst.class::isInstance).count());
        assertEquals(1f, boss.feedback().lostSegmentAlpha(11));
        outsideSwing(); frames(30);
        assertEquals(1, playedSounds.stream().filter("boss_hit"::equals).count());
        level.player.collidedWithActor(false);
        level.resetCurrentRoom(player());
        level.player.arriveAfterReset();
        Boss retry = level.boss();
        assertEquals(12, retry.energy(), "Gameplay health resets immediately");
        assertEquals(11f, retry.feedback().displayedEnergy(retry.energy()));
        assertEquals(1, playedSounds.stream().filter("boss_restore"::equals).count());
        level.updatePresentation(.125f);
        assertEquals(11.5f, retry.feedback().displayedEnergy(retry.energy()));
        level.updatePresentation(.125f);
        assertEquals(12f, retry.feedback().displayedEnergy(retry.energy()));
        assertEquals(Player.State.APPEARING, level.player.getState());
        frames(26);
        assertTrue(level.player.canFight(), "Feedback never extends the existing arrival");
        assertEquals(Boss.State.THRONE, retry.getState());
    }

    @Test void counterHitsHaveFeedbackWithoutInterruptingOrRestartingTheCounter() {
        soundsEnabled = true;
        encounter(); startNormal(); sword(); outsideSwing(); frames(83);
        assertEquals(Boss.State.COUNTER, boss.getState());
        level.updatePresentation(1);
        boss.collidedWithActor(true);
        assertEquals(Boss.State.COUNTER, boss.getState());
        frames(1);
        assertEquals(1, level.objects.stream().filter(BloodBurst.class::isInstance).count());
        assertEquals(1f, boss.feedback().lostSegmentAlpha(10));
        assertEquals(2, playedSounds.stream().filter("boss_hit"::equals).count());
        frames(20);
        assertEquals(Boss.State.COUNTER_RECOVERY, boss.getState());
    }

    @Test void throneWakeAndPursuitNeverReseatAndRetryRestoresFullBossWithElixir() {
        encounter();
        level.player.position.x = boss.getX() + 16 - 23.5f - Boss.WAKE_DISTANCE - 1;
        frames(1); assertFalse(boss.awakened());
        level.player.position.x += 3;
        frames(2); assertTrue(boss.awakened());
        float x = boss.getX(); outsideSwing(); frames(10);
        assertTrue(boss.getX() < x);
        assertNotEquals(Boss.State.THRONE, boss.getState());
        artifacts.drinkBossElixir("forest"); boss.collidedWithActor(true);
        assertEquals(2, boss.energy());
        level.player.collidedWithActor(false);
        assertTrue(level.player.isGotHit()); assertEquals(Vector2.Zero, shake());
        level.resetCurrentRoom(player());
        Boss retry = (Boss) level.objects.stream().filter(Boss.class::isInstance).findFirst().orElseThrow();
        assertEquals(12, retry.energy()); assertEquals(Boss.State.THRONE, retry.getState());
        assertEquals(new Vector2(2032, 272), level.player.position);
        assertEquals(10f, artifacts.bossWeaponDamageMultiplier("forest"));
        level.player.arriveAfterReset(); frames(30);
        assertEquals(Boss.State.THRONE, retry.getState());
    }

    @Test void aSwordSwingDealsOneHitAndNormalAndElixirNeedTwelveOrTwoSwings() {
        encounter(); inSwordRange(); sword();
        assertEquals(11, boss.energy()); frames(35);
        assertEquals(11, boss.energy(), "Overlap frames cannot repeat a sword hit");
        outsideSwing(); frames(90);
        for (int i = 0; i < 10; i++) boss.collidedWithActor(true);
        assertEquals(1, boss.energy()); boss.collidedWithActor(true);
        assertTrue(boss.isDefeated());
        level.resetCurrentRoom(player());
        boss = (Boss) level.objects.stream().filter(Boss.class::isInstance).findFirst().orElseThrow();
        artifacts.drinkBossElixir("forest");
        assertEquals(12, boss.energy());
        boss.collidedWithActor(true);
        assertEquals(2, boss.energy()); boss.collidedWithActor(true);
        assertTrue(boss.isDefeated());
    }

    @Test void hitAndRetreatCanInterruptAgainWhenTheNextHitArrivesDuringApproach() {
        encounter(); startNormal(); sword();
        assertEquals(Boss.State.HURT, boss.getState());
        outsideSwing(); frames(125);
        assertEquals(Boss.State.WALK, boss.getState());
        // The sword reaches slightly farther than the boss's attack trigger. The hit lands
        // during WALK; on the following frame the old AI starts NORMAL inside the same
        // already-counted player swing, so no second collision can cancel that strike.
        level.player.position.set(boss.getX() - 38, 272);
        level.player.facingRight = true;
        level.player.setOnGround(true);
        sword();
        assertEquals(10, boss.energy());
        assertEquals(Boss.State.HURT, boss.getState(), "A later ordinary hit must not be ignored during approach");
        assertFalse(boss.isAttackActive());
        assertFalse(level.player.isGotHit());
    }

    @Test void interruptionOnWindupStrikeAndCompletionFrameCancelsOldDamageAndShake() {
        for (int delay : new int[]{0, (int) (Boss.NORMAL_FRAME * 2 / REAL_STEP) + 1,
            (int) Math.ceil(Boss.NORMAL_DURATION / REAL_STEP) - 1}) {
            encounter(); startNormal(); outsideSwing(); frames(delay);
            inSwordRange(); sword();
            assertEquals(Boss.State.HURT, boss.getState(), "Delay=" + delay);
            assertFalse(level.player.isGotHit()); assertFalse(boss.isAttackActive());
            outsideSwing(); frames(65);
            assertEquals(Vector2.Zero, shake());
            frames(60); assertFalse(level.player.isGotHit());
            assertEquals(Vector2.Zero, shake());
        }
    }

    @Test void counterCannotBeInterruptedButStillTakesDamageAndCannotRecurse() {
        encounter(); startNormal(); sword(); outsideSwing(); frames(20);
        assertEquals(Boss.State.WARNING, boss.getState());
        boss.collidedWithActor(true); assertEquals(10, boss.energy());
        assertEquals(Boss.State.WARNING, boss.getState());
        frames(63); assertEquals(Boss.State.COUNTER, boss.getState());
        boss.collidedWithActor(true); assertEquals(9, boss.energy());
        assertEquals(Boss.State.COUNTER, boss.getState());
        frames(40); assertEquals(Boss.State.WALK, boss.getState());
        assertFalse(level.player.isGotHit());
    }

    @Test void everyLaterNormalAttackCanBeInterruptedAfterTheCounterHasRecovered() {
        encounter(); startNormal();
        for (int round = 0; round < 3; round++) {
            assertEquals(Boss.State.NORMAL, boss.getState());
            sword();
            assertEquals(Boss.State.HURT, boss.getState(), "Interruption round " + round);
            assertEquals(11 - round, boss.energy());
            outsideSwing(); frames(125);
            assertEquals(Boss.State.WALK, boss.getState());
            assertFalse(boss.isCounterSequence());
            inSwordRange(); frames(2);
        }
        assertFalse(level.player.isGotHit());
    }

    @Test void hitsDuringCounterRecoveryDoNotQueueAnotherCounter() {
        encounter(); startNormal(); sword(); outsideSwing(); frames(102);
        assertEquals(Boss.State.COUNTER_RECOVERY, boss.getState());
        boss.collidedWithActor(true);
        assertEquals(10, boss.energy());
        assertEquals(Boss.State.COUNTER_RECOVERY, boss.getState());
        frames(22);
        assertEquals(Boss.State.WALK, boss.getState());
        inSwordRange(); frames(2); sword();
        assertEquals(Boss.State.HURT, boss.getState());
    }

    @Test void ordinaryRecoveryCanBeInterruptedInsteadOfStartingAnUncancelledStrikeInTheSameSwing() {
        encounter(); startNormal(); outsideSwing(); untilState(Boss.State.RECOVERY);
        assertEquals(Boss.State.RECOVERY, boss.getState());
        inSwordRange(); sword();
        assertEquals(Boss.State.HURT, boss.getState());
        assertFalse(boss.isAttackActive());
    }

    @Test void jumpAwayAfterFullSwordRecoveryEscapesCounterAtThirtySixtyAndOneTwentyFps() {
        for (int fps : new int[]{30, 60, 120}) {
            encounter(); startNormal(); inSwordRange(); sword();
            // Finish the entire existing 0.7-second attack before asking for a jump.
            frames(43);
            assertFalse(level.player.isGotHit());
            held.add(Input.Keys.J); held.add(Input.Keys.A);
            for (int frame = 0; frame < fps * 2; frame++) {
                level.update(1f / fps); pressed.clear();
                if (frame == 0) held.remove(Input.Keys.J);
                assertFalse(level.player.isGotHit(), "Escape at " + fps + " fps, frame " + frame);
            }
            held.clear();
        }
    }

    @Test void stompBouncesAndBodyBlocksWithoutDamageEvenWhileSwinging() {
        encounter();
        level.player.position.set(boss.getX() - 10, boss.hitboxBounds().y - 48 - 1);
        level.player.velocity.y = 150;
        frames(1);
        assertTrue(level.player.velocity.y < 0); assertEquals(12, boss.energy());
        assertFalse(level.player.isGotHit());
        level.player.position.set(boss.getX() - 9, 272);
        level.player.setOnGround(true); sword();
        assertFalse(level.player.isGotHit());
        assertFalse(boss.hitboxOverlaps(level.player.hitboxBounds()));
        assertEquals(11, boss.energy());
    }

    @Test void solidBodyStopsHeldMovementFromEitherSideWithoutContactDamageOrBlood() {
        encounter();
        for (boolean fromLeft : new boolean[]{true, false}) {
            boss.collidedWithActor(true); // Warned counter keeps the boss stationary during this check.
            frames(1);
            Rectangle body = boss.hitboxBounds();
            level.player.position.set((fromLeft ? body.x - level.player.hitbox.width - 1 : body.x + body.width + 1)
                - level.player.hitbox.offsetX, 272);
            level.player.velocity.setZero();
            level.player.setOnGround(true);
            held.add(fromLeft ? Input.Keys.D : Input.Keys.A);
            long bursts = level.objects.stream().filter(BloodBurst.class::isInstance).count();
            for (int frame = 0; frame < 20; frame++) {
                frames(1);
                assertFalse(level.player.isGotHit());
                assertFalse(boss.hitboxOverlaps(level.player.hitboxBounds()));
                if (fromLeft) assertTrue(level.player.hitboxBounds().x < body.x);
                else assertTrue(level.player.hitboxBounds().x > body.x);
            }
            held.clear();
            assertEquals(bursts, level.objects.stream().filter(BloodBurst.class::isInstance).count(),
                "Solid contact must not emit blood");
        }
    }

    @Test void completedMissShakesOnceAndClearingOrDeathLeavesNoResidualEffect() {
        encounter(); startNormal(); outsideSwing(); untilState(Boss.State.RECOVERY);
        assertEquals(Boss.State.RECOVERY, boss.getState());
        frames(1); assertTrue(shake().len() > 0);
        assertEquals(0, shake().x, "Small room keeps its centred horizontal framing");
        assertTrue(shake().y >= 0 && shake().y <= 1.25f);
        frames(15); assertEquals(Vector2.Zero, shake()); frames(30); assertEquals(Vector2.Zero, shake());
        level.shakeAfterBossAttack(); level.clearCameraEffects(); assertEquals(Vector2.Zero, shake());
        level.shakeAfterBossAttack(); frames(1); level.player.collidedWithActor(false);
        assertEquals(Vector2.Zero, shake()); frames(30); assertEquals(Vector2.Zero, shake());
    }

    @Test void victoryKeepsFirefliesFireAndHazardsRunningWithoutHarmingTheHero() throws Exception {
        encounter();
        var ambienceField = Level.class.getDeclaredField("ambience"); ambienceField.setAccessible(true);
        List<?> ambience = (List<?>) ambienceField.get(level);
        assertFalse(ambience.stream().anyMatch(com.jlogicsoftware.kimeria.effects.FogEffect.class::isInstance));
        Object firefly = ambience.stream().filter(com.jlogicsoftware.kimeria.effects.Firefly.class::isInstance)
            .findFirst().orElseThrow();
        var timer = com.jlogicsoftware.kimeria.effects.Firefly.class.getDeclaredField("timer");
        timer.setAccessible(true);
        float before = timer.getFloat(firefly);
        int[] fireUpdates = {0};
        level.objects.add(new com.jlogicsoftware.kimeria.effects.Torch(2100, 300, 16, 16, 300, "") {
            @Override public void update(float dt) { fireUpdates[0]++; super.update(dt); }
        });
        Bomb bomb = new Bomb(assets, game, level::queueSpawn, blast -> {
            if (blast.overlaps(level.player.hitboxBounds())) level.player.collidedWithActor(false);
        }, level.player.getX() + 16, 288, 16, 16);
        level.objects.add(bomb); bomb.collideWithPlayer();
        artifacts.drinkBossElixir("forest");
        boss.collidedWithActor(true); boss.collidedWithActor(true);
        assertTrue(boss.isDefeated());
        float start = level.player.getX();
        frames(30);
        assertEquals(start, level.player.getX(), "Wait for the existing death presentation");
        assertTrue(timer.getFloat(firefly) > before);
        assertEquals(30, fireUpdates[0]);
        float pausedFirefly = timer.getFloat(firefly);
        pressed.add(Input.Keys.ESCAPE); frames(1);
        assertEquals(pausedFirefly, timer.getFloat(firefly), "The real pause action still freezes the world");
        assertEquals(30, fireUpdates[0]);
        frames(40);
        assertTrue(level.player.getX() > start);
        assertEquals(70, fireUpdates[0]);
        assertTrue(bomb.isRemoved(), "Hazards continue updating during the exit");
        assertFalse(level.player.isGotHit(), "Even a live hazard cannot kill the scripted hero");
        assertEquals(0, resets);
    }

    @Test void lethalCounterHitCancelsDamageAndExitCrossesWallsWithExactlyOneCompletion() {
        encounter(); artifacts.drinkBossElixir("forest");
        startNormal(); sword(); outsideSwing(); frames(83);
        assertEquals(Boss.State.COUNTER, boss.getState());
        assertEquals(2, boss.energy());
        inSwordRange(); boss.collidedWithActor(true);
        assertTrue(boss.isDefeated()); assertFalse(boss.isAttackActive());
        assertEquals(Vector2.Zero, shake());
        float start = level.player.getX();
        held.add(Input.Keys.A); held.add(Input.Keys.J); pressed.add(Input.Keys.K);
        frames(30); assertEquals(start, level.player.getX(), "Death presentation finishes before walking");
        assertFalse(boss.isRemoved(), "The death animation must finish before removal");
        assertTrue(level.objects.contains(boss));
        frames(20);
        assertTrue(boss.isRemoved(), "The final death frame must not remain on the map");
        assertFalse(level.objects.contains(boss));
        assertTrue(level.player.getX() > start, "Removing the boss must not stop the victory walk");
        level.player.debugTriggerCheckpoint(); assertEquals(0, completions);
        frames(500);
        assertEquals(1, completions); assertEquals(0, resets);
        assertFalse(level.player.isGotHit()); assertTrue(level.player.getX() > 2496);
        frames(100); assertEquals(1, completions);
    }
    @Test void elixirThickensBloodAndShakesOnlyOnLandedEmpoweredSwordHits() {
        encounter();
        boss.collidedWithActor(true);
        frames(0); level.update(0);
        BloodBurst normal = level.objects.stream().filter(BloodBurst.class::isInstance).map(BloodBurst.class::cast).findFirst().orElseThrow();
        assertEquals(32, normal.dropletCount());
        assertEquals(Vector2.Zero, shake(), "Ordinary hit has no empowered impact shake");
        artifacts.drinkBossElixir("forest");
        boss.collidedWithActor(true); level.update(.01f);
        assertEquals(1, boss.energy());
        assertTrue(level.objects.stream().filter(BloodBurst.class::isInstance).map(BloodBurst.class::cast)
            .anyMatch(b -> b.dropletCount() == 48));
        assertTrue(shake().len() > 0);
        level.clearCameraEffects();
        boss.collidedWithActor(false);
        assertEquals(Vector2.Zero, shake(), "Body contact cannot trigger sword-impact shake");
        boss.collidedWithActor(true); level.update(.01f);
        assertTrue(boss.isDefeated());
        assertEquals(1, artifacts.bossWeaponDamageMultiplier("forest"));
        assertTrue(shake().len() > 0, "The killing empowered hit still has its own impact shake");
        frames(20);
        assertEquals(Vector2.Zero, shake());
    }

    @Test void healerReturnDeathAndPaymentUseRealGameCoinBoundariesAndRestartClearsEffect() throws Exception {
        KimeriaGame running = new KimeriaGame();
        gameField(running, "playSounds", false);
        gameField(running, "assets", assets);
        gameField(running, "touchControls", touch);
        gameField(running, "currentLevelIndex", 0);
        gameField(running, "coinsCollected", 19);
        gameField(running, "player", new Player(assets, running, 0, 0));
        var load = KimeriaGame.class.getDeclaredMethod("loadLevel"); load.setAccessible(true);
        var enter = KimeriaGame.class.getDeclaredMethod("enterNextRoom"); enter.setAccessible(true);
        var rebuild = KimeriaGame.class.getDeclaredMethod("rebuildRoom"); rebuild.setAccessible(true);
        var levelField = KimeriaGame.class.getDeclaredField("level"); levelField.setAccessible(true);
        var choose = KimeriaGame.class.getDeclaredMethod("chooseHealer", com.jlogicsoftware.kimeria.ui.HealerDialogue.Choice.class);
        choose.setAccessible(true);
        load.invoke(running);
        Level world = (Level) levelField.get(running);
        Collectable securedCoin = world.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast)
            .filter(c -> c.type() == ItemType.COIN).findFirst().orElseThrow();
        securedCoin.collideWithPlayer();
        world.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast)
            .filter(c -> c.type() == ItemType.CHICKEN).forEach(Collectable::collideWithPlayer);
        enter.invoke(running);
        world.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast).forEach(Collectable::collideWithPlayer);
        enter.invoke(running); // Healer secures the canopy before opening.
        assertEquals(23, running.coinsCollected());
        choose.invoke(running, com.jlogicsoftware.kimeria.ui.HealerDialogue.Choice.RETURN);
        world.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast).forEach(Collectable::collideWithPlayer);
        assertEquals(25, running.coinsCollected());
        rebuild.invoke(running);
        assertEquals(23, running.coinsCollected(), "Only this revisit's two provisional coins roll back");
        assertEquals(2, world.objects.stream().filter(Collectable.class::isInstance).count());
        assertTrue(running.artifacts().hasFullSet("forest"));
        world.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast).forEach(Collectable::collideWithPlayer);
        enter.invoke(running); enter.invoke(running);
        choose.invoke(running, com.jlogicsoftware.kimeria.ui.HealerDialogue.Choice.BREW);
        assertEquals(19, running.coinsCollected());
        assertEquals(10, running.artifacts().bossWeaponDamageMultiplier("forest"));
        choose.invoke(running, com.jlogicsoftware.kimeria.ui.HealerDialogue.Choice.FIGHT);
        enter.invoke(running);
        rebuild.invoke(running);
        assertEquals(19, running.coinsCollected(), "Boss death must not refund payment");
        assertEquals(10, running.artifacts().bossWeaponDamageMultiplier("forest"));
        assertFalse(world.healerAhead());
        gameField(running, "pauseMenu", new com.jlogicsoftware.kimeria.ui.Menu(null, null,
            List.of(new com.jlogicsoftware.kimeria.ui.MenuItem(() -> "Resume", () -> {})), null));
        var restart = KimeriaGame.class.getDeclaredMethod("restartLevel"); restart.setAccessible(true);
        restart.invoke(running); load.invoke(running);
        assertEquals(19, running.coinsCollected());
        assertEquals(1, running.artifacts().bossWeaponDamageMultiplier("forest"));
        assertTrue(running.artifacts().items().isEmpty());
        Level restarted = (Level) levelField.get(running);
        assertEquals(0, restarted.forestCoinsSecured());
        assertEquals(4, restarted.objects.stream().filter(Collectable.class::isInstance).count());
    }

    @Test void escapePausesTheDialogueWithoutAdvancingTextAndResumeKeepsItsState() throws Exception {
        for (int scene = 0; scene < 3; scene++) {
            boolean choices = scene == 1;
            KimeriaGame running = new KimeriaGame();
            var dialogue = scene == 2 ? com.jlogicsoftware.kimeria.ui.HealerDialogue.afterVictory()
                : new com.jlogicsoftware.kimeria.ui.HealerDialogue(false, false, false, false, false);
            if (choices) {
                for (int i = 0; i < 8; i++) dialogue.advance(com.jlogicsoftware.kimeria.localization.Language.ENGLISH);
                dialogue.select(1);
            } else dialogue.update(.2f);
            gameField(running, "healer", dialogue);
            gameState(running, "HEALER");
            String text = dialogue.text(com.jlogicsoftware.kimeria.localization.Language.ENGLISH);
            int selected = dialogue.selected();
            pressed.add(Input.Keys.ESCAPE);
            var update = KimeriaGame.class.getDeclaredMethod("updateHealer", float.class); update.setAccessible(true);
            update.invoke(running, 1f);
            var origin = KimeriaGame.class.getDeclaredField("pausedHealer"); origin.setAccessible(true);
            var state = KimeriaGame.class.getDeclaredField("uiState"); state.setAccessible(true);
            assertEquals("PAUSED", state.get(running).toString());
            assertTrue(origin.getBoolean(running), "The pause background must be dialogue, not the level");
            assertEquals(text, dialogue.text(com.jlogicsoftware.kimeria.localization.Language.ENGLISH));
            assertEquals(selected, dialogue.selected());
            var resume = KimeriaGame.class.getDeclaredMethod("resumeGame"); resume.setAccessible(true);
            resume.invoke(running);
            assertEquals("HEALER", state.get(running).toString());
            assertFalse(running.isGameStarted(), "Resume dialogue must not start the world simulation");
            assertFalse(origin.getBoolean(running));
            assertEquals(text, dialogue.text(com.jlogicsoftware.kimeria.localization.Language.ENGLISH));
            assertEquals(selected, dialogue.selected());
            pressed.clear();
        }
    }

    @Test void batProximityKeepsNormalSpeedAndDeathStillSlowsTime() {
        level = new Level(assets, game, player(), "forest", Rectangle::new);
        var bat = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.enemy.Bat.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.enemy.Bat.class::cast).findFirst().orElseThrow();
        level.player.position.set(bat.getX() - 60, bat.getY() - 26);
        level.player.update(REAL_STEP);
        assertFalse(level.player.isGotHit());
        assertEquals(1f, timeScale, "A nearby bat must not change gameplay speed");
        level.player.collidedWithActor(false);
        assertEquals(.2f, timeScale, "Death still has its intentional slow-motion presentation");
        level.player.update(REAL_STEP);
        assertEquals(.2f, timeScale);
    }

}
