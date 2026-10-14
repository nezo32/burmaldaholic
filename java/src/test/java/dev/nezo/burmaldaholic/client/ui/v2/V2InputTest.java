package dev.nezo.burmaldaholic.client.ui.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The v2 kit's input edge resolves real buttons / key codes of the Minecraft version on the classpath (the build runs
 * this on 26.2 and 26.3, whose numbers differ): left / right → {@link Press}, every key the kit binds → its name and back.
 */
class V2InputTest {
	@Test
	void mouseButtons() {
		assertEquals(Press.LEFT, V2Input.press(Inputs.MOUSE_LEFT));
		assertEquals(Press.RIGHT, V2Input.press(Inputs.MOUSE_RIGHT));
		assertNull(V2Input.press(Inputs.MOUSE_MIDDLE), "a middle click is not a kit press (it used to read as scroll + on 26.2)");
	}

	@Test
	void kitKeysRoundTrip() {
		assertEquals(KeyMap.SPACE, V2Input.keyName(Inputs.KEY_SPACE));
		assertEquals(KeyMap.ENTER, V2Input.keyName(Inputs.KEY_RETURN));
		assertEquals(KeyMap.ENTER, V2Input.keyName(Inputs.KEY_NUMPADENTER));
		assertEquals(KeyMap.TAB, V2Input.keyName(Inputs.KEY_TAB));
		assertEquals(KeyMap.KEY_1, V2Input.keyName(Inputs.KEY_1));
		assertEquals(KeyMap.KEY_5, V2Input.keyName(Inputs.KEY_5));
		assertEquals(KeyMap.H, V2Input.keyName(Inputs.KEY_H));
		assertEquals(Inputs.KEY_SPACE, V2Input.keyCode(KeyMap.SPACE));
		assertEquals(Inputs.KEY_2, V2Input.keyCode(KeyMap.KEY_2));
		assertEquals(Inputs.KEY_X, V2Input.keyCode(KeyMap.X));
		for (Map.Entry<String, String> e : KeyMap.standard().entries()) {
			assertEquals(e.getKey(), V2Input.keyName(V2Input.keyCode(e.getKey())), "standard key " + e.getKey() + " -> " + e.getValue());
		}
		for (char c = 'a'; c <= 'z'; c++) assertEquals(String.valueOf(c), V2Input.keyName(V2Input.keyCode(String.valueOf(c))));
		for (int d = 0; d <= 9; d++) assertEquals(KeyMap.digit(d), V2Input.keyName(V2Input.keyCode(KeyMap.digit(d))));
	}
}
