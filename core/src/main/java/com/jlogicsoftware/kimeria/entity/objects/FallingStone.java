package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.effects.Shaders;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Falling stone trap (D45), reusable across worlds through a {@link Skin}. The stone is NOT the hero's
 * throwable {@code Carryable} stone: it cannot be picked up, thrown, pushed or stood on and no tool of the
 * hero touches it. At rest its start point shows the loose overhang (#360, D71 at-rest cue): a cracked rock lip with
 * the stone's crown wedged in it and a grit trickle from the lip's two cracks. While the hero is in its zone the trickle
 * stops and a growing ground shadow shows where it will land; then it appears and falls and the lip shows the empty
 * socket; on impact it shows the cracked frame and stays as harmless scenery until the room is rebuilt.
 */
public class FallingStone extends FallingTrap {
    /** Stone, shadow and lip (the at-rest cue, #360) art for one world; add an entry to {@link #SKINS} for a new world. */
    public record Skin(SpriteSheets.Sheet stone, SpriteSheets.Sheet shadow, SpriteSheets.Sheet lip) { }

    public static final String DEFAULT_SKIN = "forest";
    // tools/check_tiled_links.py reads the skin names from the string keys of this map: keep them literals.
    private static final java.util.Map<String, Skin> SKINS = java.util.Map.of(
        "forest", new Skin(SpriteSheets.TRAP_STONE, SpriteSheets.TRAP_SHADOW, SpriteSheets.TRAP_STONE_LIP));

    /** Names accepted by the {@code skin} property. */
    public static java.util.Set<String> skinNames() { return SKINS.keySet(); }
    /** The shadow starts at this fraction of its full width and grows linearly to full by the end of the warning. */
    public static final float SHADOW_START_SCALE = 0.35f;
    private static final int SHADOW_FRAMES = 3;
    private static final int LAST_SHADOW_FRAME = SHADOW_FRAMES - 1;
    private static final int STONE_FRAMES = 2;
    /** Lip frames (#360): 0 loose (the crown wedged in, at rest and through the warning), 1 broken (the empty socket). */
    public static final int LIP_LOOSE = 0, LIP_BROKEN = 1;
    private static final int LIP_FRAMES = 2;

    // The grit trickle (#360): every TRICKLE_PERIOD s, TRICKLE_SPECKS specks TRICKLE_SPACING s apart from one crack, alternating.
    public static final float TRICKLE_PERIOD = 1.5f, TRICKLE_SPACING = 0.1f, TRICKLE_LIFE = 0.8f;
    public static final int TRICKLE_SPECKS = 4, GRIT_CAPACITY = 8;
    /** The cracks sit this far left and right of the stone's centre (lip columns 6 and 18 of 24); specks start this far below its top. */
    public static final float CRACK_OFFSET_PX = 6f, GRIT_START_PX = 5f;
    /** Specks fall TRICKLE_FALL_PX, give or take FALL_JITTER_PX, and leave the crack up to CRACK_JITTER_PX to either side. */
    public static final float TRICKLE_FALL_PX = 48f, FALL_JITTER_PX = 6f, CRACK_JITTER_PX = 1f;
    private static final int SMALL_SPECK_ONE_IN = 3;
    private static final float SMALL_SPECK_PX = 1f, LARGE_SPECK_PX = 2f, TWO = 2f;
    // stone.grid's d 34314a and h d8d6e8: the dark specks read on sky, the light ones on dirt.
    private static final float DARK_R = 0.204f, DARK_G = 0.192f, DARK_B = 0.290f;
    private static final float LIGHT_R = 0.847f, LIGHT_G = 0.839f, LIGHT_B = 0.910f;
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;

    /** Resolves a map {@code skin} property; an unknown name fails loudly. */
    public static Skin skin(String name) {
        Skin skin = SKINS.get(name.toLowerCase(Locale.ROOT));
        if (skin == null) throw new IllegalArgumentException("Unknown falling stone skin '" + name + "', known: " + SKINS.keySet());
        return skin;
    }

    private final Skin skin;
    private final TextureRegion[] stoneFrames = new TextureRegion[STONE_FRAMES];
    private final TextureRegion[] shadowFrames = new TextureRegion[SHADOW_FRAMES];
    private final TextureRegion[] lipFrames = new TextureRegion[LIP_FRAMES];
    private final KimeriaParticleSystem grit;
    /**
     * The lip's top (world Y grows downward), from {@code FallingTrapLayout}: the map's {@code lipTop} property, else the stone's
     * start lowered to the camera's top edge while the hero stands on the floor (rooms 8 px taller than the view hide their top rows).
     */
    private final float lipTop;
    private float trickleClock;
    private int trickleEmitted, trickles;
    private Runnable onImpact = () -> { };

    /** @param lipTop the world y of the lip's top edge (validated on screen by {@code FallingTrapLayout}) */
    public FallingStone(Assets assets, Skin skin, Rectangle body, Rectangle zone, float landingBottom, float lipTop,
                        TrapWarnings warnings, Supplier<Rectangle> hero, BooleanSupplier heroGrounded, Runnable kill) {
        super(body, zone, landingBottom, warnings, hero, heroGrounded, kill);
        this.skin = skin;
        this.lipTop = lipTop;
        for (int i = 0; i < STONE_FRAMES; i++) stoneFrames[i] = skin.stone().frame(assets, i, 0);
        for (int i = 0; i < SHADOW_FRAMES; i++) shadowFrames[i] = skin.shadow().frame(assets, i, 0);
        for (int i = 0; i < LIP_FRAMES; i++) lipFrames[i] = skin.lip().frame(assets, i, 0);
        grit = new KimeriaParticleSystem(GRIT_CAPACITY, (Float.floatToIntBits(body.x) * SEED_MIX) ^ Float.floatToIntBits(body.y));
    }

    /** Where the lip is drawn (for tests). */
    public Rectangle lipBounds() {
        return new Rectangle(centerX() - skin.lip().width() / 2f, lipTop, skin.lip().width(), skin.lip().height());
    }

    /** The trickle's particles (for tests). */
    public KimeriaParticleSystem grit() { return grit; }

    /** The lip frame drawn now: loose until the stone falls, then broken. */
    public int lipFrame() { return state() == State.FALLING || state() == State.SPENT ? LIP_BROKEN : LIP_LOOSE; }

    @Override
    public void update(float dt) {
        super.update(dt);
        if (state() == State.ARMED) updateTrickle(dt);
        grit.update(dt);
    }

    /**
     * Emits the specks that are due: the first of a trickle at once, then one every TRICKLE_SPACING s; a new trickle every
     * TRICKLE_PERIOD s from the other crack. Only while armed: once the hero triggers it the warning is the shadow alone.
     */
    private void updateTrickle(float dt) {
        trickleClock += dt;
        if (trickleClock >= TRICKLE_PERIOD) {
            trickleClock -= TRICKLE_PERIOD;
            trickleEmitted = 0;
            trickles++;
        }
        RandomXS128 random = grit.random();
        float crackX = centerX() + ((trickles & 1) == 0 ? -CRACK_OFFSET_PX : CRACK_OFFSET_PX);
        float startY = lipTop + GRIT_START_PX;
        while (trickleEmitted < TRICKLE_SPECKS && trickleClock >= trickleEmitted * TRICKLE_SPACING) {
            boolean dark = (trickleEmitted & 1) == 0;
            trickleEmitted++;
            float x = crackX + (random.nextFloat() * TWO - 1f) * CRACK_JITTER_PX;
            float fall = TRICKLE_FALL_PX + (random.nextFloat() * TWO - 1f) * FALL_JITTER_PX;
            float size = random.nextInt(SMALL_SPECK_ONE_IN) == 0 ? SMALL_SPECK_PX : LARGE_SPECK_PX;
            grit.spawn(KimeriaParticleSystem.KIND_GRIT, x, startY, x, startY + fall, TRICKLE_LIFE, size,
                dark ? DARK_R : LIGHT_R, dark ? DARK_G : LIGHT_G, dark ? DARK_B : LIGHT_B);
        }
    }

    /** Called once when the stone lands (#239 step 5: the hanging vines sway). Cosmetic: nothing in the trap reads it. */
    public void onImpact(Runnable listener) { onImpact = listener; }

    @Override
    protected void landed() { onImpact.run(); }

    /** True while the ground shadow is drawn: through the warning and the fall, until the stone lands. */
    public boolean shadowVisible() { return state() == State.WARNING || state() == State.FALLING; }

    /** Shadow width as a fraction of the sheet's frame width: grows through the warning, full while falling. */
    public float shadowScale() {
        return SHADOW_START_SCALE + (1f - SHADOW_START_SCALE) * warningProgress();
    }

    /** Shadow frame: 0 small, 1 medium, 2 large by thirds of the warning. */
    public int shadowFrame() { return Math.min(LAST_SHADOW_FRAME, (int) (warningProgress() * SHADOW_FRAMES)); }

    /** The stone stays hidden above the screen of the room while only the shadow warns. */
    public boolean stoneVisible() { return state() == State.FALLING || state() == State.SPENT; }

    /** 0 stone, 1 cracked (impact). */
    public int stoneFrame() { return state() == State.SPENT ? 1 : 0; }

    @Override
    public void render(Batch batch) {
        SpriteSheets.Sheet lip = skin.lip();
        batch.draw(lipFrames[lipFrame()], centerX() - lip.width() / 2f, lipTop, lip.width(), lip.height());
        if (grit.count() > 0) grit.render(batch, Shaders.whiteQuad());
        if (shadowVisible()) {
            SpriteSheets.Sheet sheet = skin.shadow();
            float w = sheet.width() * shadowScale(), h = sheet.height();
            batch.draw(shadowFrames[shadowFrame()], centerX() - w / 2f, landingBottom() - h, w, h);
        }
        if (stoneVisible()) batch.draw(stoneFrames[stoneFrame()], position.x, position.y, size.x, size.y);
    }
}
