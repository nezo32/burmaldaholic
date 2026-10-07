package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keyboard parity of a v2 screen (docs/architecture/ui-v2.md §5.2-6): key code → {@link HitMap} target id. A key press
 * acts exactly like a click on that target (a disabled target shows its reason). The key hint plate of a button shows
 * the key bound to its id ({@link #keyOf}). Key codes are GLFW's (constants below; the pure layer has no GLFW import).
 */
public final class KeyMap {
	// GLFW key codes
	public static final int SPACE = 32, ENTER = 257, TAB = 258, BACKSPACE = 259, DELETE = 261, ESCAPE = 256;
	public static final int KEY_0 = 48, KEY_1 = 49, KEY_2 = 50, KEY_3 = 51, KEY_4 = 52, KEY_5 = 53;
	public static final int A = 65, B = 66, C = 67, D = 68, E = 69, F = 70, G = 71, H = 72, I = 73, J = 74, K = 75, L = 76, M = 77, N = 78,
		O = 79, P = 80, Q = 81, R = 82, S = 83, T = 84, U = 85, V = 86, W = 87, X = 88, Y = 89, Z = 90;

	private final Map<Integer, String> byKey = new LinkedHashMap<>();
	private final Map<String, Integer> byId = new LinkedHashMap<>();

	/** The kit's standard keys: Space = primary, 1–5 = chips, Backspace = undo, Delete = clear, R = rebet, X = ×2. */
	public static KeyMap standard() {
		KeyMap k = new KeyMap();
		k.bind(SPACE, Ids.PRIMARY);
		for (int i = 0; i < 5; i++) k.bind(KEY_1 + i, Ids.chip(i));
		k.bind(BACKSPACE, Ids.tool(BetGates.UNDO));
		k.bind(DELETE, Ids.tool(BetGates.CLEAR));
		k.bind(R, Ids.tool(BetGates.REBET));
		k.bind(X, Ids.tool(BetGates.DOUBLE));
		return k;
	}

	/** Binds {@code key} to {@code id} (replaces an earlier binding of either). */
	public KeyMap bind(int key, String id) {
		String old = byKey.remove(key);
		if (old != null) byId.remove(old);
		Integer oldKey = byId.remove(id);
		if (oldKey != null) byKey.remove(oldKey);
		byKey.put(key, id);
		byId.put(id, key);
		return this;
	}

	public KeyMap unbind(int key) {
		String id = byKey.remove(key);
		if (id != null) byId.remove(id);
		return this;
	}

	public Optional<String> idFor(int key) {
		return Optional.ofNullable(byKey.get(key));
	}

	/** The key bound to {@code id}, or -1. */
	public int keyOf(String id) {
		return byId.getOrDefault(id, -1);
	}

	public List<Map.Entry<Integer, String>> entries() {
		return new ArrayList<>(byKey.entrySet());
	}

	/** The HitMap ids the kit uses (docs/architecture/ui-v2.md §4.2). */
	public static final class Ids {
		private Ids() {}

		public static final String PRIMARY = "primary";
		public static final String SECONDARY = "secondary";
		public static final String READOUT = "readout";
		public static final String TOP_RULES = "top.rules", TOP_SETTINGS = "top.settings", TOP_LEAVE = "top.leave";

		public static String chip(int index) {
			return "chip." + index;
		}

		public static String tool(String tool) {
			return "tool." + tool;
		}

		public static String action(String action) {
			return "action." + action;
		}

		/** A felt target registered by the game (spot, seat box, …): {@code felt.<id>}. */
		public static String felt(String id) {
			return "felt." + id;
		}

		/** An empty seat box (click = sit there): {@code seat.<index>}. */
		public static String seat(int index) {
			return "seat." + index;
		}
	}
}
