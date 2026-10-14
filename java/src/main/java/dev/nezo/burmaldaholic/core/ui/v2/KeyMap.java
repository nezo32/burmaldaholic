package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Keyboard parity of a v2 screen (docs/architecture/ui-v2.md §5.2-6): key → {@link HitMap} target id. A key press acts
 * exactly like a click on that target (a disabled target shows its reason). The key hint plate of a button shows the key
 * bound to its id ({@link #keyOf}).
 *
 * <p>Keys are LOGICAL names ({@code "space"}, {@code "1"}, {@code "h"} — the suffix of vanilla's
 * {@code key.keyboard.<name>}), never key codes: Minecraft 26.3 (SDL3) renumbered every key code and javac inlines
 * numeric constants, so this common layer holds no numbers. The client resolves a real key event to a name at runtime
 * ({@code client/ui/v2/V2Input}).
 */
public final class KeyMap {
	public static final String SPACE = "space", ENTER = "enter", TAB = "tab", BACKSPACE = "backspace", DELETE = "delete", ESCAPE = "escape";
	public static final String KEY_0 = "0", KEY_1 = "1", KEY_2 = "2", KEY_3 = "3", KEY_4 = "4", KEY_5 = "5";
	public static final String A = "a", B = "b", C = "c", D = "d", E = "e", F = "f", G = "g", H = "h", I = "i", J = "j", K = "k", L = "l", M = "m",
		N = "n", O = "o", P = "p", Q = "q", R = "r", S = "s", T = "t", U = "u", V = "v", W = "w", X = "x", Y = "y", Z = "z";

	/** Every name the kit knows (the client can resolve each of them to the running version's key code). */
	public static final List<String> NAMED = List.of(SPACE, ENTER, TAB, BACKSPACE, DELETE, ESCAPE);

	private final Map<String, String> byKey = new LinkedHashMap<>();
	private final Map<String, String> byId = new LinkedHashMap<>();

	/** The digit key {@code d} (0–9). */
	public static String digit(int d) {
		if (d < 0 || d > 9) throw new IllegalArgumentException("digit " + d);
		return String.valueOf((char) ('0' + d));
	}

	/** The kit's standard keys: Space = primary, 1–5 = chips, Backspace = undo, Delete = clear, R = rebet, X = ×2. */
	public static KeyMap standard() {
		KeyMap k = new KeyMap();
		k.bind(SPACE, Ids.PRIMARY);
		for (int i = 0; i < 5; i++) k.bind(digit(1 + i), Ids.chip(i));
		k.bind(BACKSPACE, Ids.tool(BetGates.UNDO));
		k.bind(DELETE, Ids.tool(BetGates.CLEAR));
		k.bind(R, Ids.tool(BetGates.REBET));
		k.bind(X, Ids.tool(BetGates.DOUBLE));
		return k;
	}

	/** Binds {@code key} to {@code id} (replaces an earlier binding of either). */
	public KeyMap bind(String key, String id) {
		if (!known(key)) throw new IllegalArgumentException("unknown key name '" + key + "'");
		String old = byKey.remove(key);
		if (old != null) byId.remove(old);
		String oldKey = byId.remove(id);
		if (oldKey != null) byKey.remove(oldKey);
		byKey.put(key, id);
		byId.put(id, key);
		return this;
	}

	public KeyMap unbind(String key) {
		String id = byKey.remove(key);
		if (id != null) byId.remove(id);
		return this;
	}

	/** The target bound to key {@code key} (a logical name; null = a key the kit does not name). */
	public Optional<String> idFor(@Nullable String key) {
		return key == null ? Optional.empty() : Optional.ofNullable(byKey.get(key));
	}

	/** The key bound to {@code id}, or null. */
	public @Nullable String keyOf(String id) {
		return byId.get(id);
	}

	public List<Map.Entry<String, String>> entries() {
		return new ArrayList<>(byKey.entrySet());
	}

	/** Whether {@code key} is a name the kit knows: a letter, a digit or one of {@link #NAMED}. */
	public static boolean known(@Nullable String key) {
		if (key == null) return false;
		if (key.length() == 1) {
			char c = key.charAt(0);
			return c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
		}
		return NAMED.contains(key);
	}

	/**
	 * The vanilla translation key of a key's display name ({@code key.keyboard.space} → "Space" / "Пробел"), or null for
	 * keys the kit does not name. The same on every supported Minecraft version.
	 */
	public static @Nullable String vanillaNameKey(@Nullable String key) {
		return known(key) ? "key.keyboard." + key : null;
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
