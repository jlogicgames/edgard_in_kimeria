package com.jlogicsoftware.kimeria.ui;

import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import java.util.List;

/** Dialogue timeline; input edges and rendering are supplied by the host. */
public final class HealerDialogue {
    public enum Choice { RETURN, BREW, FIGHT, CONTINUE }
    /** The screen's opening (D78): the frame unfolds and the thread draws in; letters start after it, gameplay waits as before. */
    public static final float OPEN_SECONDS = 0.3f;
    /** The line that ties the silver thread (#495) draws it from Edgard's wrist to the healer over this long. */
    public static final float THREAD_TIE_SECONDS = 1.2f;
    private static final float LETTERS_PER_SECOND = 38f;
    // Constant lists: the view asks for the choices every frame.
    private static final List<Choice> NO_CHOICES = List.of(), FIGHT_ONLY = List.of(Choice.FIGHT),
        RETURN_OR_FIGHT = List.of(Choice.RETURN, Choice.FIGHT);
    private final List<Msg> lines;
    private final boolean eligible, paid;
    /** The index of the line that ties the thread (the start tale only), or -1: the thread is there from the opening. */
    private final int threadLine;
    private int line, selected;
    private float characters;
    /** Seconds since the dialogue opened (capped at the opening) and since the current line's letters began; paused time does not count. */
    private float opened, lineTime, time;
    private boolean choosing, finished;
    /** Instant text (#342): every line is whole from its first frame; read live, so the setting applies at once. */
    private boolean instantText;
    private final boolean postVictory;
    /** Scripted scenes (no checklist, no choices) finish with {@link Choice#CONTINUE}. */
    private final Kind kind;

    /** Which conversation this is; the scripted ones are never offered choices. */
    public enum Kind { PRE_BOSS, VICTORY, TUTORIAL_START, TUTORIAL_END, FIRST_DEATH }

    private HealerDialogue(Kind kind, List<Msg> lines) {
        eligible = paid = false;
        this.kind = kind;
        postVictory = kind == Kind.VICTORY;
        this.lines = lines;
        threadLine = lines.indexOf(Msg.HEALER_TALE_THREAD);
    }

    public static HealerDialogue afterVictory() {
        return new HealerDialogue(Kind.VICTORY, List.of(Msg.HEALER_VICTORY, Msg.EDGARD_DARKNESS, Msg.HEALER_GUIDE));
    }

    /** The fairy-tale preview of the forest-0 lessons (D41). */
    public static HealerDialogue tutorialStart() {
        return new HealerDialogue(Kind.TUTORIAL_START, List.of(Msg.HEALER_TALE_ROAD, Msg.EDGARD_TALE_NO_TIME,
            Msg.HEALER_TALE_FAIRIES, Msg.HEALER_TALE_LIFT, Msg.HEALER_TALE_WITCH,
            Msg.HEALER_TALE_THREAD, Msg.HEALER_TALE_FIREGIFT, Msg.HEALER_TALE_FAIRY, Msg.EDGARD_TALE_UNDERSTOOD));
    }

    /** The praise and parting blessing at the end of forest-0 (D41). */
    public static HealerDialogue tutorialEnd() {
        return new HealerDialogue(Kind.TUTORIAL_END, List.of(Msg.HEALER_TALE_PRAISE, Msg.EDGARD_TALE_READY,
            Msg.HEALER_TALE_HEROES, Msg.HEALER_TALE_BLESSING));
    }

    /** The healer's one line after the first death of a New Game run: he drew the silver thread (#341, D81). */
    public static HealerDialogue firstDeath() {
        return new HealerDialogue(Kind.FIRST_DEATH, List.of(Msg.HEALER_FIRST_DEATH));
    }

    public boolean postVictory() { return postVictory; }
    public Kind kind() { return kind; }
    /** True for every conversation that is a fixed script ending in CONTINUE. */
    public boolean scripted() { return kind != Kind.PRE_BOSS; }
    /** The recipe checklist belongs to the pre-boss meeting only. */
    public boolean showsChecklist() { return !scripted(); }

    public HealerDialogue(boolean bat, boolean mushroom, boolean allCoins, boolean eligible, boolean paid) {
        this(bat, mushroom, allCoins, eligible, paid, false);
    }

    public HealerDialogue(boolean bat, boolean mushroom, boolean allCoins, boolean eligible, boolean paid, boolean returning) {
        postVictory = false;
        kind = Kind.PRE_BOSS;
        this.eligible = eligible;
        this.paid = paid;
        Msg greeting = returning ? Msg.HEALER_RETURN_GREETING : Msg.HEALER_GREETING;
        Msg response = returning ? Msg.HEALER_RETURN_CHECK : Msg.EDGARD_REPLY;
        if (paid) lines = List.of(Msg.HEALER_PAID, Msg.HEALER_LOCK);
        else if (eligible) lines = List.of(greeting, response, Msg.HEALER_READY);
        else lines = List.of(greeting, response,
            !bat || !mushroom ? (allCoins ? Msg.HEALER_INGREDIENTS : Msg.HEALER_BOTH) : !allCoins ? Msg.HEALER_PAYMENT : Msg.HEALER_READY,
            Msg.HEALER_OFFER);
        threadLine = -1; // met after the tale: the thread is already tied
    }

    public void update(float dt) {
        float step = Math.max(0, dt);
        time += step;
        if (opened < OPEN_SECONDS) {
            float rest = opened + step - OPEN_SECONDS;
            opened = Math.min(OPEN_SECONDS, opened + step);
            step = Math.max(0, rest);
        }
        lineTime += step;
        if (!choosing) characters += step * LETTERS_PER_SECOND;
    }
    /** 0 when the screen opens, 1 once the frame, the thread and the box are whole (after {@link #OPEN_SECONDS}). */
    public float openProgress() { return opened / OPEN_SECONDS; }
    /** Seconds the current line has been on screen after the opening. */
    public float lineTime() { return lineTime; }
    /**
     * How much of the silver thread is tied, 0 to 1 from Edgard's wrist (#495): in the start tale there is none before
     * {@code HEALER_TALE_THREAD}, that line draws it over {@link #THREAD_TIE_SECONDS} (at once with instant text) and it
     * stays; every other conversation has it whole.
     */
    public float threadTied() {
        if (line > threadLine) return 1f;
        if (line < threadLine) return 0f;
        return instantText ? 1f : Math.min(1f, lineTime / THREAD_TIE_SECONDS);
    }
    /** Seconds since the thread glint of the current line set off: {@link #lineTime}, but after the draw-in on the tying line (negative until then). */
    public float glintTime() { return line == threadLine && !instantText ? lineTime - THREAD_TIE_SECONDS : lineTime; }
    /** Seconds the dialogue has run, paused time excluded (drives the fairy's flight and hover and the paid hand-over). */
    public float time() { return time; }
    /** The line on screen now. */
    public Msg currentLine() { return lines.get(line); }
    public void setInstantText(boolean instant) { instantText = instant; }
    public boolean instantText() { return instantText; }
    /** How many letters of the line show now; the per-frame renderer asks this instead of {@link #text}, which builds a substring. */
    public int visibleCharacters(Language language) {
        int length = completeText(language).length();
        return instantText ? length : Math.min(length, (int) characters);
    }
    public String text(Language language) { return completeText(language).substring(0, visibleCharacters(language)); }
    public boolean fullyRevealed(Language language) { return instantText || characters >= completeText(language).length(); }
    public String completeText(Language language) { return lines.get(line).t(language); }
    public boolean edgardSpeaking() { return lines.get(line).name().startsWith("EDGARD_"); }
    public boolean choosing() { return choosing; }
    public boolean paid() { return paid; }
    public int selected() { return selected; }
    public void select(int index) { if (index >= 0 && index < choices().size()) selected = index; }
    public List<Choice> choices() {
        if (scripted()) return NO_CHOICES;
        if (paid) return FIGHT_ONLY;
        return eligible ? NO_CHOICES : RETURN_OR_FIGHT;
    }
    public void move(int direction) { selected = Math.floorMod(selected + direction, choices().size()); }
    /**
     * Navigation consumes the event; opening choices never also activates one. Nothing is taken while the screen opens
     * (D78): the view shows no text before {@link #OPEN_SECONDS}, so a press then would act on a line nobody has seen.
     */
    public Choice interact(Language language, boolean freshAdvance, int navigation, boolean confirm, int clicked) {
        if (opened < OPEN_SECONDS) return null;
        if (!choosing) {
            return freshAdvance ? advance(language) : null;
        }
        if (navigation != 0) { move(navigation); return null; }
        if (clicked >= 0 && clicked < choices().size()) { select(clicked); return choices().get(selected); }
        return confirm ? choices().get(selected) : null;
    }

    public Choice advance(Language language) {
        if (choosing || finished) return null;
        int length = completeText(language).length();
        if (!instantText && characters < length) { characters = length; return null; }
        if (line + 1 < lines.size()) { line++; characters = 0; lineTime = 0; }
        else if (scripted()) { finished = true; return Choice.CONTINUE; }
        else if (eligible && !paid) return Choice.BREW;
        else choosing = true;
        return null;
    }
}
