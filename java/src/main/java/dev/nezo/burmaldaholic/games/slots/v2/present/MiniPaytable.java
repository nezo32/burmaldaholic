package dev.nezo.burmaldaholic.games.slots.v2.present;

import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SymbolRole;
import java.util.Arrays;

/**
 * The win panel's mini paytable (lane J-L9b; PURE): the (up to) three PAY symbols with the best 5-of-a-kind pay in
 * the machine's REAL config (the server-sent {@link MachineDef}, never hard-coded), best first, and their pay at a
 * bet. The order is cached per def so the screen does no list / sort / boxing per frame.
 */
public final class MiniPaytable {
	public static final int ROWS = 3;

	private static MachineDef cachedDef;
	private static int[] cached = new int[0];

	private MiniPaytable() {}

	/** Symbol indices of the best-paying PAY symbols (5 of a kind), best first; ties keep the paytable order. */
	public static synchronized int[] top(MachineDef def) {
		if (def == cachedDef) return cached;
		int[] best = new int[ROWS];
		int n = 0;
		for (int i = 0; i < def.roles().length; i++) {
			if (def.roles()[i] != SymbolRole.PAY) continue;
			int pay = def.paysFifths()[i][2];
			int k = n;
			while (k > 0 && def.paysFifths()[best[k - 1]][2] < pay) k--;
			if (k >= ROWS) continue;
			System.arraycopy(best, k, best, k + 1, Math.min(n, ROWS - 1) - k);
			best[k] = i;
			n = Math.min(ROWS, n + 1);
		}
		cached = Arrays.copyOf(best, n);
		cachedDef = def;
		return cached;
	}

	/** Pay of five {@code sym} on one way at {@code bet} (chips; the paytable is in fifths of the bet). */
	public static long pay(MachineDef def, int sym, long bet) {
		return (long) def.paysFifths()[sym][2] * bet / 5;
	}
}
