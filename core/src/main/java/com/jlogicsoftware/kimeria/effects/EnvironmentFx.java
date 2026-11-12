package com.jlogicsoftware.kimeria.effects;

import java.util.function.BooleanSupplier;

/**
 * The environment effects of one {@code Level} (#239). Today it holds the screen shake; the dust and grit
 * particle systems join it in later steps. Everything here is cosmetic: no collision, timing or route reads it.
 */
public final class EnvironmentFx {
    private final ScreenShake shake;

    /** @param shakeEnabled false while the player's Screen shake option is off */
    public EnvironmentFx(BooleanSupplier shakeEnabled) { shake = new ScreenShake(shakeEnabled); }

    public ScreenShake shake() { return shake; }

    public void update(float dt) { shake.update(dt); }

    /** Death, retry and room change. */
    public void clear() { shake.clear(); }
}
