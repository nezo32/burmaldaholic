package dev.nezo.burmaldaholic.client.input;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Mouse buttons and key codes resolved at RUNTIME from {@link InputConstants}.
 *
 * <p>Minecraft 26.3 moved input to SDL3 and renumbered every mouse button (left 0 → 1, right 1 → 3) and every key code.
 * The release jar is compiled against 26.2, and javac inlines {@code static final} constants, so any literal or
 * {@code InputConstants.MOUSE_BUTTON_LEFT} / {@code GLFW_KEY_*} comparison bakes in the 26.2 numbers and silently fails
 * on 26.3 (v0.1.3: chips in blackjack / UTH never took a click). These fields are NOT compile-time constants: they are
 * read once from the running game. Never compare raw button or key numbers anywhere else in the mod.
 */
public final class Inputs {
	private Inputs() {}

	public static final int MOUSE_LEFT = constant("MOUSE_BUTTON_LEFT");
	public static final int MOUSE_RIGHT = constant("MOUSE_BUTTON_RIGHT");
	public static final int MOUSE_MIDDLE = constant("MOUSE_BUTTON_MIDDLE");

	public static boolean isLeft(int button) {
		return button == MOUSE_LEFT;
	}

	public static boolean isRight(int button) {
		return button == MOUSE_RIGHT;
	}

	public static boolean isMiddle(int button) {
		return button == MOUSE_MIDDLE;
	}

	/** Modifier bits of the running version ({@code InputConstants.MOD_*}; GLFW on 26.2, SDL3 on 26.3). */
	public static final int MOD_SHIFT = constant("MOD_SHIFT");
	public static final int MOD_CONTROL = constant("MOD_CONTROL");

	/** True when {@code mods} (a key / mouse event's modifiers) has Shift held. */
	public static boolean shift(int mods) {
		return (mods & MOD_SHIFT) != 0;
	}

	/** True when {@code mods} has Control held. */
	public static boolean ctrl(int mods) {
		return (mods & MOD_CONTROL) != 0;
	}

	public static final int KEY_0 = constant("KEY_0");
	public static final int KEY_1 = constant("KEY_1");
	public static final int KEY_2 = constant("KEY_2");
	public static final int KEY_3 = constant("KEY_3");
	public static final int KEY_4 = constant("KEY_4");
	public static final int KEY_5 = constant("KEY_5");
	public static final int KEY_6 = constant("KEY_6");
	public static final int KEY_7 = constant("KEY_7");
	public static final int KEY_8 = constant("KEY_8");
	public static final int KEY_9 = constant("KEY_9");
	public static final int KEY_A = constant("KEY_A");
	public static final int KEY_ADD = constant("KEY_ADD");
	public static final int KEY_APOSTROPHE = constant("KEY_APOSTROPHE");
	public static final int KEY_B = constant("KEY_B");
	public static final int KEY_BACKSLASH = constant("KEY_BACKSLASH");
	public static final int KEY_BACKSPACE = constant("KEY_BACKSPACE");
	public static final int KEY_C = constant("KEY_C");
	public static final int KEY_CAPSLOCK = constant("KEY_CAPSLOCK");
	public static final int KEY_COMMA = constant("KEY_COMMA");
	public static final int KEY_D = constant("KEY_D");
	public static final int KEY_DELETE = constant("KEY_DELETE");
	public static final int KEY_DOWN = constant("KEY_DOWN");
	public static final int KEY_E = constant("KEY_E");
	public static final int KEY_END = constant("KEY_END");
	public static final int KEY_EQUALS = constant("KEY_EQUALS");
	public static final int KEY_ESCAPE = constant("KEY_ESCAPE");
	public static final int KEY_F = constant("KEY_F");
	public static final int KEY_F1 = constant("KEY_F1");
	public static final int KEY_F10 = constant("KEY_F10");
	public static final int KEY_F11 = constant("KEY_F11");
	public static final int KEY_F12 = constant("KEY_F12");
	public static final int KEY_F13 = constant("KEY_F13");
	public static final int KEY_F14 = constant("KEY_F14");
	public static final int KEY_F15 = constant("KEY_F15");
	public static final int KEY_F16 = constant("KEY_F16");
	public static final int KEY_F17 = constant("KEY_F17");
	public static final int KEY_F18 = constant("KEY_F18");
	public static final int KEY_F19 = constant("KEY_F19");
	public static final int KEY_F2 = constant("KEY_F2");
	public static final int KEY_F20 = constant("KEY_F20");
	public static final int KEY_F21 = constant("KEY_F21");
	public static final int KEY_F22 = constant("KEY_F22");
	public static final int KEY_F23 = constant("KEY_F23");
	public static final int KEY_F24 = constant("KEY_F24");
	public static final int KEY_F3 = constant("KEY_F3");
	public static final int KEY_F4 = constant("KEY_F4");
	public static final int KEY_F5 = constant("KEY_F5");
	public static final int KEY_F6 = constant("KEY_F6");
	public static final int KEY_F7 = constant("KEY_F7");
	public static final int KEY_F8 = constant("KEY_F8");
	public static final int KEY_F9 = constant("KEY_F9");
	public static final int KEY_G = constant("KEY_G");
	public static final int KEY_GRAVE = constant("KEY_GRAVE");
	public static final int KEY_H = constant("KEY_H");
	public static final int KEY_HOME = constant("KEY_HOME");
	public static final int KEY_I = constant("KEY_I");
	public static final int KEY_INSERT = constant("KEY_INSERT");
	public static final int KEY_J = constant("KEY_J");
	public static final int KEY_K = constant("KEY_K");
	public static final int KEY_L = constant("KEY_L");
	public static final int KEY_LALT = constant("KEY_LALT");
	public static final int KEY_LBRACKET = constant("KEY_LBRACKET");
	public static final int KEY_LCONTROL = constant("KEY_LCONTROL");
	public static final int KEY_LEFT = constant("KEY_LEFT");
	public static final int KEY_LSHIFT = constant("KEY_LSHIFT");
	public static final int KEY_M = constant("KEY_M");
	public static final int KEY_MINUS = constant("KEY_MINUS");
	public static final int KEY_MULTIPLY = constant("KEY_MULTIPLY");
	public static final int KEY_N = constant("KEY_N");
	public static final int KEY_NUMLOCK = constant("KEY_NUMLOCK");
	public static final int KEY_NUMPAD0 = constant("KEY_NUMPAD0");
	public static final int KEY_NUMPAD1 = constant("KEY_NUMPAD1");
	public static final int KEY_NUMPAD2 = constant("KEY_NUMPAD2");
	public static final int KEY_NUMPAD3 = constant("KEY_NUMPAD3");
	public static final int KEY_NUMPAD4 = constant("KEY_NUMPAD4");
	public static final int KEY_NUMPAD5 = constant("KEY_NUMPAD5");
	public static final int KEY_NUMPAD6 = constant("KEY_NUMPAD6");
	public static final int KEY_NUMPAD7 = constant("KEY_NUMPAD7");
	public static final int KEY_NUMPAD8 = constant("KEY_NUMPAD8");
	public static final int KEY_NUMPAD9 = constant("KEY_NUMPAD9");
	public static final int KEY_NUMPADCOMMA = constant("KEY_NUMPADCOMMA");
	public static final int KEY_NUMPADENTER = constant("KEY_NUMPADENTER");
	public static final int KEY_NUMPADEQUALS = constant("KEY_NUMPADEQUALS");
	public static final int KEY_O = constant("KEY_O");
	public static final int KEY_P = constant("KEY_P");
	public static final int KEY_PAGEDOWN = constant("KEY_PAGEDOWN");
	public static final int KEY_PAGEUP = constant("KEY_PAGEUP");
	public static final int KEY_PAUSE = constant("KEY_PAUSE");
	public static final int KEY_PERIOD = constant("KEY_PERIOD");
	public static final int KEY_PRINTSCREEN = constant("KEY_PRINTSCREEN");
	public static final int KEY_Q = constant("KEY_Q");
	public static final int KEY_R = constant("KEY_R");
	public static final int KEY_RALT = constant("KEY_RALT");
	public static final int KEY_RBRACKET = constant("KEY_RBRACKET");
	public static final int KEY_RCONTROL = constant("KEY_RCONTROL");
	public static final int KEY_RETURN = constant("KEY_RETURN");
	public static final int KEY_RIGHT = constant("KEY_RIGHT");
	public static final int KEY_RSHIFT = constant("KEY_RSHIFT");
	public static final int KEY_S = constant("KEY_S");
	public static final int KEY_SCROLLLOCK = constant("KEY_SCROLLLOCK");
	public static final int KEY_SEMICOLON = constant("KEY_SEMICOLON");
	public static final int KEY_SLASH = constant("KEY_SLASH");
	public static final int KEY_SPACE = constant("KEY_SPACE");
	public static final int KEY_T = constant("KEY_T");
	public static final int KEY_TAB = constant("KEY_TAB");
	public static final int KEY_U = constant("KEY_U");
	public static final int KEY_UP = constant("KEY_UP");
	public static final int KEY_V = constant("KEY_V");
	public static final int KEY_W = constant("KEY_W");
	public static final int KEY_X = constant("KEY_X");
	public static final int KEY_Y = constant("KEY_Y");
	public static final int KEY_Z = constant("KEY_Z");

	/** Reads {@code InputConstants.<name>} reflectively so the value comes from the running Minecraft version. */
	private static int constant(String name) {
		try {
			return InputConstants.class.getField(name).getInt(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("InputConstants." + name + " is missing in this Minecraft version", e);
		}
	}
}
