package dev.nezo.burmaldaholic.games.slots.v2.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Compact, versioned text encoding of {@link SpinTape} for persistence (Java block entity, Bedrock dynamic
 * property; worst case End + 40 free spins &lt; 1 200 characters, SLOTS.md §8.1). The SAME string format in both
 * editions (twin of {@code bedrock/src/games/slots/v2/logic/tape-codec.ts}; vectors in {@code slots_engine.json}).
 *
 * <p>Grammar (numbers in lower-case base 36, negative with a leading {@code -}; fields separated by {@code ;}):
 * <pre>
 *   2;&lt;m&gt;;&lt;bet&gt;;&lt;flags&gt;;&lt;total&gt;;&lt;stops&gt;;&lt;fs&gt;;&lt;hunt&gt;;&lt;hoard&gt;;&lt;wheel&gt;;&lt;jackpots&gt;
 *   m        o | n | e                         flags   'b' bought, 'c' cap hit (in that order), or empty
 *   stops    s.s.s.s.s  (empty when bought)
 *   fs       &lt;awarded&gt;:&lt;spin&gt;/&lt;spin&gt;…           spin    s.s.s.s.s,&lt;stickyMaskAfter&gt;,&lt;retrigger 0|1&gt;,&lt;pay&gt;
 *   hunt     &lt;entry&gt;.&lt;entry&gt;…:&lt;opened&gt;
 *   hoard    &lt;cells&gt;,&lt;values&gt;/&lt;cells&gt;,&lt;values&gt;… (first pair = locked coins, then one pair per respin;
 *            cells/values '.'-joined, empty for a respin without a new coin)
 *   wheel    seg.seg.seg
 *   jackpots &lt;tier&gt;.&lt;chips&gt;.&lt;owned 0|1&gt;/…
 * </pre>
 * Empty optional sections are empty strings. The free-spin total is not stored (Σ of the spins). Decoding validates the
 * shape and throws {@link IllegalArgumentException} on anything else.
 */
public final class TapeCodec {
	private static final Pattern NUMBER = Pattern.compile("-?[0-9a-z]+");
	private static final Pattern FLAGS = Pattern.compile("b?c?");

	private TapeCodec() {}

	private static String n(long v) {
		return Long.toString(v, 36);
	}

	private static long p(String s) {
		if (!NUMBER.matcher(s).matches()) throw new IllegalArgumentException("tape: bad number '" + s + "'");
		long v = Long.parseLong(s, 36);
		if (Math.abs(v) > (1L << 53) - 1) throw new IllegalArgumentException("tape: number out of range '" + s + "'");
		return v;
	}

	private static String list(int[] a) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < a.length; i++) {
			if (i > 0) sb.append('.');
			sb.append(n(a[i]));
		}
		return sb.toString();
	}

	private static int[] plist(String s) {
		if (s.isEmpty()) return new int[0];
		String[] parts = s.split("\\.", -1);
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) out[i] = Math.toIntExact(p(parts[i]));
		return out;
	}

	private static String machine(Machine m) {
		return switch (m) {
			case OVERWORLD -> "o";
			case NETHER -> "n";
			case END -> "e";
		};
	}

	public static String encode(SpinTape t) {
		String flags = (t.bought() ? "b" : "") + (t.capHit() ? "c" : "");
		StringBuilder fs = new StringBuilder();
		if (t.freeSpins() != null) {
			fs.append(n(t.freeSpins().awarded())).append(':');
			List<SpinTape.FreeSpin> spins = t.freeSpins().spins();
			for (int i = 0; i < spins.size(); i++) {
				SpinTape.FreeSpin s = spins.get(i);
				if (i > 0) fs.append('/');
				fs.append(list(s.stops())).append(',').append(n(s.stickyMaskAfter())).append(',').append(s.retrigger() ? '1' : '0').append(',')
					.append(n(s.payFifths()));
			}
		}
		String hunt = t.hunt() == null ? "" : list(t.hunt().entries()) + ":" + n(t.hunt().opened());
		StringBuilder hoard = new StringBuilder();
		if (t.hoard() != null) {
			SpinTape.Hoard h = t.hoard();
			hoard.append(list(h.initialCells())).append(',').append(list(h.initialValues()));
			for (int i = 0; i < h.respinCells().size(); i++) {
				hoard.append('/').append(list(h.respinCells().get(i))).append(',').append(list(h.respinValues().get(i)));
			}
		}
		String wheel = t.wheel() == null ? "" : list(t.wheel().segments());
		StringBuilder jp = new StringBuilder();
		for (int i = 0; i < t.jackpots().size(); i++) {
			SpinTape.JackpotAward j = t.jackpots().get(i);
			if (i > 0) jp.append('/');
			jp.append(n(j.tier())).append('.').append(n(j.chips())).append('.').append(j.owned() ? '1' : '0');
		}
		return String.join(";", Integer.toString(SpinTape.VERSION), machine(t.machine()), n(t.bet()), flags, n(t.totalFifths()), list(t.stops()),
			fs, hunt, hoard, wheel, jp);
	}

	/** Decodes; throws {@link IllegalArgumentException} for anything malformed or of another version. */
	public static SpinTape decode(String s) {
		try {
			String[] f = s.split(";", -1);
			if (f.length != 11 || !f[0].equals(Integer.toString(SpinTape.VERSION))) throw new IllegalArgumentException("tape: not a v2 tape");
			Machine m = switch (f[1]) {
				case "o" -> Machine.OVERWORLD;
				case "n" -> Machine.NETHER;
				case "e" -> Machine.END;
				default -> throw new IllegalArgumentException("tape: unknown machine '" + f[1] + "'");
			};
			if (!FLAGS.matcher(f[3]).matches()) throw new IllegalArgumentException("tape: bad flags '" + f[3] + "'");
			boolean bought = f[3].contains("b");
			long bet = p(f[2]);
			long total = p(f[4]);
			int[] stops = plist(f[5]);
			if (!bought && stops.length != 5) throw new IllegalArgumentException("tape: 5 stops expected");
			SpinTape.FreeSpins fs = null;
			if (!f[6].isEmpty()) {
				int colon = f[6].indexOf(':');
				if (colon < 0) throw new IllegalArgumentException("tape: bad free spins");
				String body = f[6].substring(colon + 1);
				List<SpinTape.FreeSpin> spins = new ArrayList<>();
				long pay = 0;
				if (!body.isEmpty()) {
					for (String x : body.split("/", -1)) {
						String[] a = x.split(",", -1);
						if (a.length != 4 || !(a[2].equals("0") || a[2].equals("1"))) throw new IllegalArgumentException("tape: bad free spin");
						int[] st = plist(a[0]);
						if (st.length != 5) throw new IllegalArgumentException("tape: 5 free-spin stops expected");
						long sp = p(a[3]);
						spins.add(new SpinTape.FreeSpin(st, Math.toIntExact(p(a[1])), a[2].equals("1"), sp));
						pay += sp;
					}
				}
				fs = new SpinTape.FreeSpins(Math.toIntExact(p(f[6].substring(0, colon))), spins, pay);
			}
			SpinTape.Hunt hunt = null;
			if (!f[7].isEmpty()) {
				String[] a = f[7].split(":", -1);
				if (a.length != 2) throw new IllegalArgumentException("tape: bad hunt");
				hunt = new SpinTape.Hunt(plist(a[0]), Math.toIntExact(p(a[1])));
			}
			SpinTape.Hoard hoard = null;
			if (!f[8].isEmpty()) {
				String[] parts = f[8].split("/", -1);
				int[][] first = pair(parts[0]);
				List<int[]> rc = new ArrayList<>();
				List<int[]> rv = new ArrayList<>();
				for (int i = 1; i < parts.length; i++) {
					int[][] x = pair(parts[i]);
					rc.add(x[0]);
					rv.add(x[1]);
				}
				hoard = new SpinTape.Hoard(first[0], first[1], rc, rv);
			}
			SpinTape.Wheel wheel = f[9].isEmpty() ? null : new SpinTape.Wheel(plist(f[9]));
			List<SpinTape.JackpotAward> jps = new ArrayList<>();
			if (!f[10].isEmpty()) {
				for (String x : f[10].split("/", -1)) {
					String[] a = x.split("\\.", -1);
					if (a.length != 3) throw new IllegalArgumentException("tape: bad jackpot");
					int tier = Math.toIntExact(p(a[0]));
					if (tier < 1 || tier > 4 || !(a[2].equals("0") || a[2].equals("1"))) throw new IllegalArgumentException("tape: bad jackpot");
					jps.add(new SpinTape.JackpotAward(tier, p(a[1]), a[2].equals("1")));
				}
			}
			return new SpinTape(m, bet, bought, stops, fs, hunt, hoard, wheel, jps, total, f[3].contains("c"));
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("tape: " + e.getMessage(), e);
		}
	}

	private static int[][] pair(String g) {
		String[] a = g.split(",", -1);
		if (a.length != 2) throw new IllegalArgumentException("tape: bad hoard");
		int[] cells = plist(a[0]);
		int[] values = plist(a[1]);
		if (cells.length != values.length) throw new IllegalArgumentException("tape: hoard cells/values mismatch");
		return new int[][] {cells, values};
	}

	/** Debug form for logs. */
	public static String describe(SpinTape t) {
		return t.machine().id + " bet " + t.bet() + " total " + t.totalFifths() + "/5 stops " + Arrays.toString(t.stops());
	}
}
