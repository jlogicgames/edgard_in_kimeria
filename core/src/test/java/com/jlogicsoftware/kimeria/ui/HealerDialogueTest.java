package com.jlogicsoftware.kimeria.ui;

import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealerDialogueTest {
    private void toChoices(HealerDialogue dialogue, Language language) {
        for (int i = 0; i < 20 && !dialogue.choosing(); i++) dialogue.advance(language);
        assertTrue(dialogue.choosing());
    }

    @Test void firstPressRevealsAndNextAdvancesInBothLanguagesWithoutChoosing() {
        for (Language language : Language.values()) {
            HealerDialogue dialogue = new HealerDialogue(false, false, false, false, false);
            assertEquals("", dialogue.text(language));
            dialogue.update(.1f);
            assertTrue(dialogue.text(language).length() > 0);
            assertTrue(dialogue.text(language).length() < dialogue.completeText(language).length());
            dialogue.advance(language);
            assertEquals(Msg.HEALER_GREETING.t(language), dialogue.text(language));
            dialogue.advance(language);
            assertEquals("", dialogue.text(language));
            assertTrue(dialogue.edgardSpeaking());
            toChoices(dialogue, language);
            assertEquals(2, dialogue.choices().size());
            assertFalse(dialogue.choices().contains(HealerDialogue.Choice.BREW));
        }
    }

    @Test void navigationCannotConfirmEvenWhenMirroredGamepadActionIsSimultaneous() {
        HealerDialogue dialogue = new HealerDialogue(false, true, true, false, false);
        toChoices(dialogue, Language.ENGLISH);
        assertNull(dialogue.interact(Language.ENGLISH, true, 1, true, -1));
        assertEquals(HealerDialogue.Choice.FIGHT, dialogue.choices().get(dialogue.selected()));
        assertEquals(HealerDialogue.Choice.FIGHT, dialogue.interact(Language.ENGLISH, false, 0, true, -1));
        assertEquals(HealerDialogue.Choice.RETURN, dialogue.interact(Language.ENGLISH, false, 0, false, 0));
    }

    @Test void fullRecipeKeepsIntroductionThenBrewsWithoutReturnOrUnempoweredFightChoice() {
        for (Language language : Language.values()) {
            HealerDialogue dialogue = new HealerDialogue(true, true, true, true, false);
            for (Msg message : java.util.List.of(Msg.HEALER_GREETING, Msg.EDGARD_REPLY)) {
                assertEquals(message.t(language), dialogue.completeText(language));
                assertEquals(message == Msg.EDGARD_REPLY, dialogue.edgardSpeaking());
                assertNull(dialogue.interact(language, true, 0, true, 0), "Reveal introduction without brewing");
                assertEquals(message.t(language), dialogue.text(language));
                assertNull(dialogue.interact(language, true, 0, true, 0), "Advance introduction without brewing");
            }
            assertEquals(Msg.HEALER_READY.t(language), dialogue.completeText(language));
            assertFalse(dialogue.edgardSpeaking());
            assertTrue(dialogue.choices().isEmpty());
            assertNull(dialogue.interact(language, true, 0, true, 0), "First fresh press only reveals the line");
            assertEquals(Msg.HEALER_READY.t(language), dialogue.text(language));
            assertFalse(dialogue.choosing());
            assertNull(dialogue.interact(language, false, 0, false, -1), "Holding cannot brew");
            assertEquals(HealerDialogue.Choice.BREW, dialogue.interact(language, true, 0, false, -1));
            HealerDialogue paid = new HealerDialogue(false, false, true, false, true);
            toChoices(paid, language);
            assertEquals(java.util.List.of(HealerDialogue.Choice.FIGHT), paid.choices());
        }
    }

    @Test void completingLastLineDoesNotActivateAChoiceWithTheSamePressOrTap() {
        HealerDialogue dialogue = new HealerDialogue(true, true, true, true, true);
        dialogue.advance(Language.ENGLISH); dialogue.advance(Language.ENGLISH); dialogue.advance(Language.ENGLISH);
        assertFalse(dialogue.choosing());
        assertNull(dialogue.interact(Language.ENGLISH, true, 0, true, 0));
        assertTrue(dialogue.choosing());
        assertEquals(java.util.List.of(HealerDialogue.Choice.FIGHT), dialogue.choices());
    }

    @Test void refusalDistinguishesIngredientsPaymentAndBoth() {
        for (Language language : Language.values()) {
            boolean[][] cases = {{false, true, true}, {true, false, true}, {true, true, false}, {false, false, false}};
            Msg[] expected = {Msg.HEALER_INGREDIENTS, Msg.HEALER_INGREDIENTS, Msg.HEALER_PAYMENT, Msg.HEALER_BOTH};
            for (int i = 0; i < cases.length; i++) {
                var c = cases[i];
                HealerDialogue dialogue = new HealerDialogue(c[0], c[1], c[2], false, false);
                for (int step = 0; step < 5; step++) dialogue.advance(language);
                assertEquals(expected[i].t(language), dialogue.text(language));
            }
        }
    }

    @Test void touchTargetsAreDistinctAndLargeEnoughInLogicalPixels() {
        HealerDialogueView view = new HealerDialogueView();
        assertEquals(0, view.choiceAt(new Vector2(320, 260), 3));
        assertEquals(1, view.choiceAt(new Vector2(320, 290), 3));
        assertEquals(2, view.choiceAt(new Vector2(320, 320), 3));
        assertEquals(-1, view.choiceAt(new Vector2(320, 320), 2));
        assertEquals(-1, view.choiceAt(new Vector2(320, 150), 3));
        assertEquals(-1, view.choiceAt(new Vector2(590, 18), 2));
    }
}
