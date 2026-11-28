package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.BeetleFx;
import com.jlogicsoftware.kimeria.effects.BossFx;
import com.jlogicsoftware.kimeria.entity.objects.EmberFissure;
import com.jlogicsoftware.kimeria.entity.objects.FallingStone;
import com.jlogicsoftware.kimeria.entity.objects.FallingTrap;
import com.jlogicsoftware.kimeria.entity.objects.LanceRoot;
import com.jlogicsoftware.kimeria.world.Level;

/**
 * The forest boss's three special attacks (#625, D157; the card is in docs/BOSS.md): falling rocks, roots from the ground and
 * the blow. Each warns for its own {@link TrapWarnings} time, sampled once at its start, longer with the assist.
 * <ul>
 * <li><b>Rocks:</b> {@value #ROCKS} Forest stones, a new one every {@value #ROCK_INTERVAL} s, each over the hero's centre as
 * its shadow appears; it falls from the ceiling to the floor, through one-way platforms, and kills while it falls and as it
 * lands.</li>
 * <li><b>Roots:</b> {@value #ROOTS} lance-root columns, {@value #ROOT_SPACING} px apart round the hero's centre at the start;
 * the lethal part is exactly the drawn column.</li>
 * <li><b>Blow:</b> specks are drawn in from the rim of the beetle's dome (D149), then it kills there for one instant.</li>
 * </ul>
 * No lethal area overlaps the arena's protected spans ({@link BossArena}). Fixed storage: nothing is allocated after the
 * constructor. The clocks advance only in {@link #update}, so pause freezes them. World Y grows downward.
 */
public final class BossAttacks {
    public enum Kind { NONE, ROCKS, ROOTS, BLOW }

    public static final int ROCKS = 3, ROOTS = 5;
    public static final float ROCK_INTERVAL = .45f, ROCK_SIZE = 16, ROCK_SPENT_SECONDS = .5f;
    public static final float ROOT_SPACING = 32, ROOT_HEIGHT = 48, ROOT_JET_SECONDS = .6f;
    /** The blow's wind-up specks per second at its start and at its end, as the beetle's. */
    private static final float SPECKS_START = 50, SPECKS_END = 150;
    private static final float HALF = .5f;
    private static final float ROCK_HALF = ROCK_SIZE * HALF, ROOT_HALF = EmberFissure.JET_WIDTH * HALF;
    /** The root's lips, where its soil hops from: this far over the floor line. */
    private static final float LIP_RISE = LanceRoot.LIP_RISE_PX;
    private static final int SHADOW_FRAMES = 3, STONE_FRAMES = 2, STONE_WHOLE = 0, STONE_CRACKED = 1;
    private static final String ROCKS_SOUND = "boss_call", ROCK_IMPACT_SOUND = "stone_impact", ROOTS_SOUND = "root_creak",
        ROOTS_ACT_SOUND = "root_thrust", BLOW_SOUND = "boss_blow_charge", BLOW_LONG_SOUND = "boss_blow_charge_long",
        BLOW_ACT_SOUND = "boss_blow";

    private final GameContext game;
    private final Level level;
    private final BossArena arena;
    private final FallingStone.Skin skin;
    private final TextureRegion[] stoneFrames = new TextureRegion[STONE_FRAMES];
    private final TextureRegion[] shadowFrames = new TextureRegion[SHADOW_FRAMES];
    private final TextureRegion[] rootFrames = new TextureRegion[EmberFissure.FRAMES];
    private final EmberFissure.Column column;

    private Kind kind = Kind.NONE;
    /** Seconds since the attack started, and the warning sampled at that moment. */
    private float clock, warning;
    // Rocks: launched in order; a rock's age counts from the moment its shadow appears.
    private int launched;
    private final float[] rockLeft = new float[ROCKS], rockTop = new float[ROCKS], rockY = new float[ROCKS],
        rockSweptFrom = new float[ROCKS], rockAge = new float[ROCKS], rockSpent = new float[ROCKS];
    private final boolean[] rockLanded = new boolean[ROCKS], rockSweeping = new boolean[ROCKS];
    // Roots.
    private final float[] rootX = new float[ROOTS];
    private final boolean[] rootStands = new boolean[ROOTS];
    private float rootTop, soilDue;
    private boolean thrust, sinking;
    // Blow.
    private float speckDue;
    private boolean blowDue;
    /** The hero fell: what is under way finishes its motion, and no further rock is thrown. */
    private boolean settling;
    private final Rectangle scratch = new Rectangle();

    public BossAttacks(Assets assets, GameContext game, Level level, BossArena arena) {
        this.game = game;
        this.level = level;
        this.arena = arena;
        skin = FallingStone.skin(FallingStone.DEFAULT_SKIN);
        for (int i = 0; i < STONE_FRAMES; i++) stoneFrames[i] = skin.stone().frame(assets, i, 0);
        for (int i = 0; i < SHADOW_FRAMES; i++) shadowFrames[i] = skin.shadow().frame(assets, i, 0);
        for (int i = 0; i < EmberFissure.FRAMES; i++) rootFrames[i] = SpriteSheets.TRAP_LANCE_ROOT.frame(assets, i, 0);
        column = new EmberFissure.Column(assets, SpriteSheets.TRAP_LANCE_SHAFT);
    }

    public BossArena arena() { return arena; }
    public Kind kind() { return kind; }
    /** The warning sampled when the running attack started. */
    public float warningSeconds() { return warning; }

    /** True while the attack still holds the boss: until the last rock lands, the roots have sunk or the blow goes off. */
    public boolean holdsBoss() {
        return switch (kind) {
            case NONE -> false;
            case ROCKS -> launched < ROCKS || !rockLanded[ROCKS - 1];
            case ROOTS -> clock < warning + ROOT_JET_SECONDS;
            case BLOW -> clock < warning;
        };
    }

    /**
     * Starts {@code wanted} against a hero whose centre is at {@code heroX}, from a boss whose middle is at {@code bossX};
     * false, with nothing started, when no part of it can be placed off the protected spans.
     */
    public boolean start(Kind wanted, float heroX, float bossX) {
        if (wanted == Kind.BLOW && !arena.clear(bossX, BeetleFx.BLAST_WIDTH * HALF)) return false;
        if (wanted == Kind.ROOTS && !anyRootStands(heroX)) return false;
        if (wanted == Kind.ROCKS && Float.isNaN(arena.nearestClear(heroX, ROCK_HALF))) return false;
        clear();
        kind = wanted;
        switch (wanted) {
            case ROCKS -> {
                warning = warningFor(TrapWarnings.BOSS_ROCKS_WARNING_SECONDS);
                launch(heroX);
                play(ROCKS_SOUND);
            }
            case ROOTS -> {
                warning = warningFor(TrapWarnings.BOSS_ROOTS_WARNING_SECONDS);
                for (int i = 0; i < ROOTS; i++) {
                    rootX[i] = rootCentre(heroX, i);
                    rootStands[i] = arena.clear(rootX[i], ROOT_HALF);
                }
                rootTop = arena.floor();
                play(ROOTS_SOUND);
            }
            case BLOW -> {
                warning = warningFor(TrapWarnings.BOSS_BLOW_WARNING_SECONDS);
                play(warning > TrapWarnings.BOSS_BLOW_WARNING_SECONDS ? BLOW_LONG_SOUND : BLOW_SOUND);
            }
            case NONE -> { }
        }
        return true;
    }

    private float warningFor(float base) {
        TrapWarnings warnings = game.trapWarnings();
        return warnings != null ? warnings.duration(base) : base;
    }

    private static float rootCentre(float heroX, int i) { return heroX + (i - ROOTS / 2) * ROOT_SPACING; }

    private boolean anyRootStands(float heroX) {
        for (int i = 0; i < ROOTS; i++) if (arena.clear(rootCentre(heroX, i), ROOT_HALF)) return true;
        return false;
    }

    private void launch(float heroX) {
        int i = launched++;
        float centre = arena.nearestClear(heroX, ROCK_HALF);
        rockLeft[i] = centre - ROCK_HALF;
        rockTop[i] = rockY[i] = rockSweptFrom[i] = arena.ceilingOver(rockLeft[i], rockLeft[i] + ROCK_SIZE);
        rockAge[i] = rockSpent[i] = 0;
        rockLanded[i] = rockSweeping[i] = false;
    }

    /** Advances the running attack; {@code heroX} is the hero's centre now, {@code bossX} and {@code bossY} the boss's middle. */
    public void update(float dt, float heroX, float bossX, float bossY) {
        if (kind == Kind.NONE) return;
        clock += dt;
        switch (kind) {
            case ROCKS -> updateRocks(dt, heroX);
            case ROOTS -> updateRoots(dt);
            case BLOW -> updateBlow(dt, bossX, bossY);
            case NONE -> { }
        }
    }

    private void updateRocks(float dt, float heroX) {
        for (int i = 0; i < launched; i++) {
            rockSweeping[i] = false;
            if (rockLanded[i]) { rockSpent[i] += dt; continue; }
            rockAge[i] += dt;
            float falling = rockAge[i] - warning;
            if (falling <= 0) continue;
            rockSweptFrom[i] = rockY[i];
            rockSweeping[i] = true;
            rockY[i] = rockTop[i] + FallingTrap.GRAVITY * falling * falling * HALF;
            if (rockY[i] + ROCK_SIZE >= arena.floor()) {
                rockY[i] = arena.floor() - ROCK_SIZE;
                rockLanded[i] = true;
                level.bossRockImpact(rockLeft[i] + ROCK_HALF, arena.floor(), ROCK_SIZE);
                play(ROCK_IMPACT_SOUND);
            }
        }
        if (!settling && launched < ROCKS && clock >= launched * ROCK_INTERVAL) launch(heroX);
        else if ((settling || launched == ROCKS) && rockLanded[launched - 1] && rockSpent[launched - 1] >= ROCK_SPENT_SECONDS)
            kind = Kind.NONE;
    }

    private void updateRoots(float dt) {
        float since = clock - warning;
        BossFx fx = level.bossFx();
        if (since < 0) {
            soilDue += dt;
            while (soilDue >= LanceRoot.HOP_PERIOD) {
                soilDue -= LanceRoot.HOP_PERIOD;
                for (int i = 0; i < ROOTS; i++) if (rootStands[i]) fx.soil(rootX[i], arena.floor() - LIP_RISE);
            }
            return;
        }
        if (!thrust) { thrust = true; play(ROOTS_ACT_SOUND); }
        rootTop = arena.floor() - ROOT_HEIGHT * rise(since);
        if (!sinking && since >= ROOT_JET_SECONDS - EmberFissure.SINK_SECONDS) {
            sinking = true;
            for (int i = 0; i < ROOTS; i++)
                if (rootStands[i]) fx.bark(rootX[i], arena.floor(), ROOT_HEIGHT, EmberFissure.JET_WIDTH, LanceRoot.CHIPS);
        }
        if (since >= ROOT_JET_SECONDS + EmberFissure.SPENT_SECONDS) kind = Kind.NONE;
    }

    /** The share of the column's height {@code s} seconds into the thrust: up, held, down, as the clocked column's (D91). */
    private static float rise(float s) {
        if (s < 0 || s >= ROOT_JET_SECONDS) return 0;
        if (s < EmberFissure.RISE_SECONDS) return s / EmberFissure.RISE_SECONDS;
        if (s < ROOT_JET_SECONDS - EmberFissure.SINK_SECONDS) return 1;
        return 1 - (s - (ROOT_JET_SECONDS - EmberFissure.SINK_SECONDS)) / EmberFissure.SINK_SECONDS;
    }

    private void updateBlow(float dt, float bossX, float bossY) {
        if (blowDue) return;
        if (clock >= warning) { blowDue = true; return; }
        speckDue += (SPECKS_START + (SPECKS_END - SPECKS_START) * clock / warning) * dt;
        while (speckDue >= 1) { speckDue -= 1; level.bossFx().speck(bossX, bossY, arena.floor()); }
    }

    /** True when a falling or landing rock, or a root's column, touches {@code heroBody} this frame. */
    public boolean strikes(Rectangle heroBody) {
        if (kind == Kind.ROCKS) {
            for (int i = 0; i < launched; i++)
                if (rockSweeping[i] && scratch.set(rockLeft[i], rockSweptFrom[i], ROCK_SIZE, rockY[i] + ROCK_SIZE - rockSweptFrom[i])
                    .overlaps(heroBody)) return true;
        } else if (kind == Kind.ROOTS && rootTop < arena.floor()) {
            for (int i = 0; i < ROOTS; i++)
                if (rootStands[i] && scratch.set(rootX[i] - ROOT_HALF, rootTop, EmberFissure.JET_WIDTH, arena.floor() - rootTop)
                    .overlaps(heroBody)) return true;
        }
        return false;
    }

    /** True on the frame the blow's wind-up has ended and it has not gone off yet. */
    public boolean blowDue() { return blowDue; }

    /** The blow goes off round {@code bossX}, hit or miss; true when {@code heroBody} is in its dome. */
    public boolean detonate(float bossX, Rectangle heroBody) {
        blowDue = false;
        kind = Kind.NONE;
        level.bossFx().burst(bossX, arena.floor());
        play(BLOW_ACT_SOUND);
        return Beetle.reaches(heroBody, bossX, arena.floor());
    }

    /**
     * The hero fell: the attack plays on to its end, so what killed the hero stays seen (a root bites with its first pixel and
     * then rises), but no new rock is thrown. The boss asks for no more harm after this.
     */
    public void settle() { settling = true; }

    /** Removes every attack (a new one, or the boss's defeat). */
    public void clear() {
        kind = Kind.NONE;
        clock = warning = soilDue = speckDue = 0;
        launched = 0;
        thrust = sinking = blowDue = settling = false;
        rootTop = arena.floor();
    }

    /** The number of rocks whose shadow has appeared, for tests. */
    public int rocksLaunched() { return kind == Kind.ROCKS ? launched : 0; }
    /** Rock {@code i}'s box now, for tests. */
    public Rectangle rockBounds(int i) { return new Rectangle(rockLeft[i], rockY[i], ROCK_SIZE, ROCK_SIZE); }
    public boolean rockLanded(int i) { return rockLanded[i]; }
    /** The centre of root {@code i}, for tests. */
    public float rootX(int i) { return rootX[i]; }
    public boolean rootStands(int i) { return kind == Kind.ROOTS && rootStands[i]; }
    /** The roots' top now (the floor line while they are down). */
    public float rootTop() { return rootTop; }

    private void play(String sound) {
        if (game.playSounds()) game.playSound(sound);
    }

    /** The ground shadows, drawn behind the boss. */
    public void renderUnder(Batch batch) {
        if (kind != Kind.ROCKS) return;
        SpriteSheets.Sheet sheet = skin.shadow();
        for (int i = 0; i < launched; i++) {
            if (rockLanded[i]) continue;
            float progress = Math.min(1f, rockAge[i] / warning);
            float width = sheet.width() * (FallingStone.SHADOW_START_SCALE + (1f - FallingStone.SHADOW_START_SCALE) * progress);
            int frame = Math.min(SHADOW_FRAMES - 1, (int) (progress * SHADOW_FRAMES));
            batch.draw(shadowFrames[frame], rockLeft[i] + ROCK_HALF - width * HALF, arena.floor() - sheet.height(), width, sheet.height());
        }
    }

    /** The rocks and the roots, drawn in front of the boss. */
    public void renderOver(Batch batch) {
        if (kind == Kind.ROCKS) {
            for (int i = 0; i < launched; i++) {
                if (!rockLanded[i]) {
                    if (rockAge[i] > warning) batch.draw(stoneFrames[STONE_WHOLE], rockLeft[i], rockY[i], ROCK_SIZE, ROCK_SIZE);
                } else if (rockSpent[i] < ROCK_SPENT_SECONDS) batch.draw(stoneFrames[STONE_CRACKED], rockLeft[i], rockY[i], ROCK_SIZE, ROCK_SIZE);
            }
        } else if (kind == Kind.ROOTS) {
            float since = clock - warning;
            int frame = since < 0 ? (clock < warning * HALF ? EmberFissure.WARN1 : EmberFissure.WARN2)
                : since < ROOT_JET_SECONDS ? EmberFissure.VENT : EmberFissure.SPENT;
            for (int i = 0; i < ROOTS; i++) {
                if (!rootStands[i]) continue;
                if (rootTop < arena.floor()) column.render(batch, rootX[i], arena.floor(), rootTop, clock);
                batch.draw(rootFrames[frame], rootX[i] - EmberFissure.WIDTH * HALF, arena.floor() - EmberFissure.FLOOR_OFFSET,
                    EmberFissure.WIDTH, EmberFissure.HEIGHT);
            }
        }
    }
}
