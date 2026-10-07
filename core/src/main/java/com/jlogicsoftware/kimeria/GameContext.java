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

    void loadNextLevel();

    void triggerGameOver();

    void togglePause();

    boolean isSlowTime();

    void setSlowTime();

    void setNormalTime();

    /** Smoothly moves the world camera toward {@code target} at {@code speed} px/s. */
    void moveCameraTo(Vector2 target, float speed);

    Vector2 logicalResolution();
}
