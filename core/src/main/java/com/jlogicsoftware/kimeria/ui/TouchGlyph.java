package com.jlogicsoftware.kimeria.ui;

/**
 * The pictogram on a touch Action or Ranged button (#492, D138). A state of the button is one constant; its art is the pair
 * {@code images/HUD/touch/<artName>.png} (idle, cream face) and {@code <artName>-down.png} (pressed, amber face), baked 64x64 from
 * {@code art/hud/touch/<artName>.grid}. The glyphs are never mirrored: left-handed mode moves the buttons, not their drawings.
 *
 * <p>The Action button is always {@link #SWORD} (D126 moved use, pick up and put down to the stick). The Ranged button shows
 * {@link #THROW} while the hero carries something, else {@link #FIREBALL} with ammunition and {@link #EMPTY} without.
 */
public enum TouchGlyph {
    /** A diagonal blade with a crossguard and a grip: the Action button. */
    SWORD("sword"),
    /** A solid teardrop flame trailing left with two speed ticks: empty-handed with ammunition. */
    FIREBALL("fireball"),
    /** A round stone on a dotted arc ending in an arrowhead: carrying something. */
    THROW("throw"),
    /** A hollow, dashed flame outline in slate: empty-handed, no ammunition. */
    EMPTY("empty");

    private final String artName;

    TouchGlyph(String artName) { this.artName = artName; }

    /** The file stem of the idle art; the pressed art is {@code artName() + "-down"}. */
    public String artName() { return artName; }
}
