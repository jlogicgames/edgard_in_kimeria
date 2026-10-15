package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.ui.TouchControls;

/**
 * Everything an entity needs from the running game, mirroring what Dart
 * entities reached via {@code HasGameReference<EdgardInKimeria>}.
 */
public interface GameContext {
    Assets assets();

    GamepadInput gamepad();

    TouchControls touch();

    Language language();

    /** Left-handed mode: mirrored touch layout and the left-hand keyboard bindings. */
    boolean leftHanded();

    /** Shared warning timing for every timed trap. */
    TrapWarnings trapWarnings();

    /** True while the player overlaps a trigger zone, i.e. the action button interacts instead of attacking. */
    boolean interactAvailable();

    /** Debug aid (F2): ignores lethal damage so a level can be walked end to end. */
    boolean invulnerable();

    void setInvulnerable(boolean value);

    /** Debug aid (F1): draw hitbox/collision-block gizmos. */
    boolean debugDraw();

    void setDebugDraw(boolean value);

    boolean playSounds();

    float soundVolume();

    /** name is one of: jump, bounce, collect, hit, disappear */
    void playSound(String name);

    boolean isGameStarted();

    void setGameStarted(boolean started);

    void addCoin();

    int coinsCollected();

    ArtifactInventory artifacts();

    void loadNextLevel();

    /**
     * Asks for the current room to be rebuilt from scratch with a fresh player (death, D3). Takes
     * effect at the start of the next frame, never mid-update, and skips the loading screen.
     * Passed rooms retain their state; maps without room markers use the whole level.
     */
    void resetRoom();

    void togglePause();

    boolean isSlowTime();

    void setSlowTime();

    void setNormalTime();

    /** Sets the world's time scale (1 = normal); the death slow-motion uses it. */
    void setTimeScale(float scale);

    /** Smoothly moves the world camera toward {@code target} at {@code speed} px/s. */
    void moveCameraTo(Vector2 target, float speed);

    /**
     * Pans the camera to {@code target} in exactly {@code seconds} of real time, easing in and out,
     * whatever the distance. Chase moves are ignored until it lands.
     */
    void panCameraTo(Vector2 target, float seconds);

    boolean isCameraPanning();

    Vector2 logicalResolution();
}
