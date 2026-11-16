package com.jlogicsoftware.kimeria.input;

import com.badlogic.gdx.Input;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntroInputTest {
    private final boolean[] down = new boolean[Input.Keys.MAX_KEYCODE + 1];
    private final boolean[] justPressed = new boolean[down.length];
    private boolean touched, justTouched;
    private final Input input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
        new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isKeyPressed" -> down[(int) args[0]];
            case "isKeyJustPressed" -> justPressed[(int) args[0]];
            case "isTouched" -> touched;
            case "justTouched" -> justTouched;
            default -> throw new UnsupportedOperationException(method.getName());
        });

    @Test void menuHoldAndKeyRepeatAreIgnoredButReleaseThenPressSkips() {
        IntroInput intro = new IntroInput();
        down[Input.Keys.ENTER] = true;
        intro.reset(input);
        justPressed[Input.Keys.ENTER] = true;
        assertFalse(intro.skipPressed(input));
        down[Input.Keys.ENTER] = false;
        justPressed[Input.Keys.ENTER] = false;
        assertFalse(intro.skipPressed(input));
        down[Input.Keys.ENTER] = true;
        assertTrue(intro.skipPressed(input));
    }

    @Test void anotherKeyCanSkipWhileMenuKeyIsStillHeldAndQuickTapsCount() {
        IntroInput intro = new IntroInput();
        down[Input.Keys.SPACE] = true;
        intro.reset(input);
        justPressed[Input.Keys.Q] = true;
        assertTrue(intro.skipPressed(input));
    }

    @Test void menuTouchIsIgnoredUntilReleasedThenNewTapSkips() {
        IntroInput intro = new IntroInput();
        touched = true;
        justTouched = true;
        intro.reset(input);
        assertFalse(intro.skipPressed(input));
        touched = false;
        justTouched = false;
        assertFalse(intro.skipPressed(input));
        justTouched = true;
        assertTrue(intro.skipPressed(input));
    }
    @Test void aHeldConfirmKeyAdvancesInstantDialogueOneLineNotMany() {
        IntroInput dialogueInput = new IntroInput();
        com.jlogicsoftware.kimeria.localization.Language language = com.jlogicsoftware.kimeria.localization.Language.ENGLISH;
        var dialogue = com.jlogicsoftware.kimeria.ui.HealerDialogue.tutorialStart();
        dialogue.setInstantText(true);
        dialogueInput.reset(input);
        down[Input.Keys.ENTER] = true;
        int advanced = 0;
        for (int frame = 0; frame < 30; frame++) { // held for 30 frames, the OS repeating the key from frame 10
            justPressed[Input.Keys.ENTER] = frame == 0 || frame >= 10;
            if (dialogueInput.skipPressed(input) && dialogueInput.keyPressed(Input.Keys.ENTER)) { dialogue.advance(language); advanced++; }
        }
        assertEquals(1, advanced);
        assertEquals(com.jlogicsoftware.kimeria.localization.Msg.EDGARD_TALE_NO_TIME.t(language), dialogue.completeText(language));
        down[Input.Keys.ENTER] = false; justPressed[Input.Keys.ENTER] = false;
        dialogueInput.skipPressed(input);
        down[Input.Keys.ENTER] = true; justPressed[Input.Keys.ENTER] = true; // released and pressed again: one more line
        assertTrue(dialogueInput.skipPressed(input) && dialogueInput.keyPressed(Input.Keys.ENTER));
        dialogue.advance(language);
        assertEquals(com.jlogicsoftware.kimeria.localization.Msg.HEALER_TALE_FAIRIES.t(language), dialogue.completeText(language));
    }

    @Test void dialogueConfirmationEdgesIgnoreHeldGameplayButtonsAndResumeInput() {
        IntroInput dialogue = new IntroInput();
        for (int key : new int[]{Input.Keys.J, Input.Keys.K, Input.Keys.L, Input.Keys.Z, Input.Keys.X, Input.Keys.C}) {
            java.util.Arrays.fill(down, false); java.util.Arrays.fill(justPressed, false);
            down[key] = true; justPressed[key] = true;
            dialogue.reset(input);
            assertFalse(dialogue.skipPressed(input));
            assertFalse(dialogue.keyPressed(key));
            down[key] = false; justPressed[key] = false;
            dialogue.skipPressed(input);
            down[key] = true; justPressed[key] = true;
            assertTrue(dialogue.skipPressed(input));
            assertTrue(dialogue.keyPressed(key));
            // OS repeats and a held key cannot select another choice.
            assertFalse(dialogue.skipPressed(input));
            assertFalse(dialogue.keyPressed(key));
        }
    }

}
