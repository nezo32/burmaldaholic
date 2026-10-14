package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The edge between real input and the v2 kit: a raw mouse button becomes a {@link Press}, a raw key code becomes a
 * {@link KeyMap} key name, both through the runtime-resolved {@link Inputs} (Minecraft 26.3 / SDL3 renumbered every mouse
 * button and key code; numbers compiled in on 26.2 would be wrong there).
 */
public final class V2Input {
	private V2Input() {}

	private static final Map<Integer, String> NAME_BY_CODE = new HashMap<>();
	private static final Map<String, Integer> CODE_BY_NAME = new HashMap<>();

	static {
		put(KeyMap.SPACE, Inputs.KEY_SPACE);
		put(KeyMap.ENTER, Inputs.KEY_RETURN);
		put(KeyMap.TAB, Inputs.KEY_TAB);
		put(KeyMap.BACKSPACE, Inputs.KEY_BACKSPACE);
		put(KeyMap.DELETE, Inputs.KEY_DELETE);
		put(KeyMap.ESCAPE, Inputs.KEY_ESCAPE);
		for (char c = '0'; c <= '9'; c++) put(String.valueOf(c), field("KEY_" + c));
		for (char c = 'a'; c <= 'z'; c++) put(String.valueOf(c), field("KEY_" + Character.toUpperCase(c)));
		NAME_BY_CODE.putIfAbsent(Inputs.KEY_NUMPADENTER, KeyMap.ENTER); // keypad Enter acts as Enter (not shown on plates)
	}

	private static void put(String name, int code) {
		NAME_BY_CODE.put(code, name);
		CODE_BY_NAME.put(name, code);
	}

	private static int field(String name) {
		try {
			return Inputs.class.getField(name).getInt(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Inputs." + name + " is missing", e);
		}
	}

	/** The kit's meaning of a mouse button (left / right); null for any other button. */
	public static @Nullable Press press(int button) {
		if (Inputs.isLeft(button)) return Press.LEFT;
		if (Inputs.isRight(button)) return Press.RIGHT;
		return null;
	}

	/** The {@link KeyMap} name of a key code of the running version, or null when the kit does not name it. */
	public static @Nullable String keyName(int code) {
		return NAME_BY_CODE.get(code);
	}

	/** The running version's key code of a {@link KeyMap} name (tests press keys with it). */
	public static int keyCode(String name) {
		Integer c = CODE_BY_NAME.get(name.toLowerCase(Locale.ROOT));
		if (c == null) throw new IllegalArgumentException("unknown key name '" + name + "'");
		return c;
	}
}
