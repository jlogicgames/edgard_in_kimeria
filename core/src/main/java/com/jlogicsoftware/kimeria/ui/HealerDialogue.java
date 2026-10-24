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

    private HealerDialogue() {
        eligible = paid = false;
        postVictory = true;
        lines = List.of(Msg.HEALER_VICTORY, Msg.EDGARD_DARKNESS, Msg.HEALER_GUIDE);
    }

    public static HealerDialogue afterVictory() { return new HealerDialogue(); }
    public boolean postVictory() { return postVictory; }

    public HealerDialogue(boolean chicken, boolean key, boolean allCoins, boolean eligible, boolean paid) {
        this(chicken, key, allCoins, eligible, paid, false);
    }

    public HealerDialogue(boolean chicken, boolean key, boolean allCoins, boolean eligible, boolean paid, boolean returning) {
        postVictory = false;
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
    public boolean edgardSpeaking() { return lines.get(line) == Msg.EDGARD_REPLY || lines.get(line) == Msg.EDGARD_DARKNESS; }
    public boolean choosing() { return choosing; }
    public boolean paid() { return paid; }
    public int selected() { return selected; }
    public void select(int index) { if (index >= 0 && index < choices().size()) selected = index; }
    public List<Choice> choices() {
        if (postVictory) return List.of();
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
        else if (postVictory) { finished = true; return Choice.CONTINUE; }
        else if (eligible && !paid) return Choice.BREW;
        else choosing = true;
        return null;
    }
}
