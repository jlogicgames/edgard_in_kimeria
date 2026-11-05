package com.jlogicsoftware.kimeria.ui;

import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import java.util.List;

/** Dialogue timeline; input edges and rendering are supplied by the host. */
public final class HealerDialogue {
    public enum Choice { RETURN, BREW, FIGHT, CONTINUE }
    private final List<Msg> lines;
    private final boolean eligible, paid;
    private int line, selected;
    private float characters;
    private boolean choosing, finished;
    private final boolean postVictory;
    /** Scripted scenes (no checklist, no choices) finish with {@link Choice#CONTINUE}. */
    private final Kind kind;

    /** Which conversation this is; the three scripted ones are never offered choices. */
    public enum Kind { PRE_BOSS, VICTORY, TUTORIAL_START, TUTORIAL_END }

    private HealerDialogue(Kind kind, List<Msg> lines) {
        eligible = paid = false;
        this.kind = kind;
        postVictory = kind == Kind.VICTORY;
        this.lines = lines;
    }

    public static HealerDialogue afterVictory() {
        return new HealerDialogue(Kind.VICTORY, List.of(Msg.HEALER_VICTORY, Msg.EDGARD_DARKNESS, Msg.HEALER_GUIDE));
    }

    /** The fairy-tale preview of the forest-0 lessons (D41). */
    public static HealerDialogue tutorialStart() {
        return new HealerDialogue(Kind.TUTORIAL_START, List.of(Msg.HEALER_TALE_ROAD, Msg.EDGARD_TALE_NO_TIME,
            Msg.HEALER_TALE_FAIRIES, Msg.HEALER_TALE_LIFT, Msg.HEALER_TALE_WITCH));
    }

    /** The praise and parting blessing at the end of forest-0 (D41). */
    public static HealerDialogue tutorialEnd() {
        return new HealerDialogue(Kind.TUTORIAL_END, List.of(Msg.HEALER_TALE_PRAISE, Msg.EDGARD_TALE_READY,
            Msg.HEALER_TALE_HEROES, Msg.HEALER_TALE_BLESSING));
    }

    public boolean postVictory() { return postVictory; }
    public Kind kind() { return kind; }
    /** True for every conversation that is a fixed script ending in CONTINUE. */
    public boolean scripted() { return kind != Kind.PRE_BOSS; }
    /** The recipe checklist belongs to the pre-boss meeting only. */
    public boolean showsChecklist() { return !scripted(); }

    public HealerDialogue(boolean chicken, boolean key, boolean allCoins, boolean eligible, boolean paid) {
        this(chicken, key, allCoins, eligible, paid, false);
    }

    public HealerDialogue(boolean chicken, boolean key, boolean allCoins, boolean eligible, boolean paid, boolean returning) {
        postVictory = false;
        kind = Kind.PRE_BOSS;
        this.eligible = eligible;
        this.paid = paid;
        Msg greeting = returning ? Msg.HEALER_RETURN_GREETING : Msg.HEALER_GREETING;
        Msg response = returning ? Msg.HEALER_RETURN_CHECK : Msg.EDGARD_REPLY;
        if (paid) lines = List.of(Msg.HEALER_PAID, Msg.HEALER_LOCK);
        else if (eligible) lines = List.of(greeting, response, Msg.HEALER_READY);
        else lines = List.of(greeting, response,
            !chicken || !key ? (allCoins ? Msg.HEALER_INGREDIENTS : Msg.HEALER_BOTH) : !allCoins ? Msg.HEALER_PAYMENT : Msg.HEALER_READY,
            Msg.HEALER_OFFER);
    }

    public void update(float dt) { if (!choosing) characters += Math.max(0, dt) * 38; }
    public String text(Language language) {
        String text = lines.get(line).t(language);
        return text.substring(0, Math.min(text.length(), (int) characters));
    }
    public boolean fullyRevealed(Language language) { return characters >= completeText(language).length(); }
    public String completeText(Language language) { return lines.get(line).t(language); }
    public boolean edgardSpeaking() { return lines.get(line).name().startsWith("EDGARD_"); }
    public boolean choosing() { return choosing; }
    public boolean paid() { return paid; }
    public int selected() { return selected; }
    public void select(int index) { if (index >= 0 && index < choices().size()) selected = index; }
    public List<Choice> choices() {
        if (scripted()) return List.of();
        if (paid) return List.of(Choice.FIGHT);
        return eligible ? List.of() : List.of(Choice.RETURN, Choice.FIGHT);
    }
    public void move(int direction) { selected = Math.floorMod(selected + direction, choices().size()); }
    /** Navigation consumes the event; opening choices never also activates one. */
    public Choice interact(Language language, boolean freshAdvance, int navigation, boolean confirm, int clicked) {
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
        if (characters < length) { characters = length; return null; }
        if (line + 1 < lines.size()) { line++; characters = 0; }
        else if (scripted()) { finished = true; return Choice.CONTINUE; }
        else if (eligible && !paid) return Choice.BREW;
        else choosing = true;
        return null;
    }
}
