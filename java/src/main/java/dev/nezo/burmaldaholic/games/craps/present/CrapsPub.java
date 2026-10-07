package dev.nezo.burmaldaholic.games.craps.present;

import java.util.ArrayList;
import java.util.List;

/**
 * The public part of a craps table for the in-world board (ui-v2.md §7.2, §7.6): every stack on the layout by seat
 * (never anything hidden: craps has no secrets), and each seat's net of the last roll. Rides in the block entity's
 * update tag next to {@code CrapsSync} (the dice / puck), encoded as one int array. Pure; encode / decode unit-tested.
 *
 * @param seq    changes whenever the content changes (the renderer caches its text by it)
 * @param stacks the bets on the layout (≤ {@link #MAX_STACKS}, the largest first when there are more)
 * @param nets   net of the last roll per seat index (seat → net; seats without a resolved bet are absent)
 */
public record CrapsPub(int seq, List<Stack> stacks, List<Net> nets) {
	public static final int VERSION = 2;
	public static final int MAX_STACKS = 12;
	/** Bet kinds by wire ordinal (same order as {@code BetKind}). */
	public static final String[] KINDS = {"pass", "dont_pass", "come", "dont_come", "field"};

	public CrapsPub {
		stacks = List.copyOf(stacks.size() > MAX_STACKS ? stacks.subList(0, MAX_STACKS) : stacks);
		nets = List.copyOf(nets);
	}

	/** One bet: seat index of its owner, kind, its point (come / don't come that travelled), flat and odds. */
	public record Stack(int seat, String kind, int point, long flat, long odds) {}

	public record Net(int seat, long net) {}

	public static final CrapsPub EMPTY = new CrapsPub(0, List.of(), List.of());

	/** Chips of {@code seat} on the layout. */
	public long onTable(int seat) {
		long t = 0;
		for (Stack s : stacks) {
			if (s.seat() == seat) t += s.flat() + s.odds();
		}
		return t;
	}

	/** Net of the last roll of {@code seat}, or null. */
	public Long net(int seat) {
		for (Net n : nets) {
			if (n.seat() == seat) return n.net();
		}
		return null;
	}

	private static int kindOrdinal(String kind) {
		for (int i = 0; i < KINDS.length; i++) {
			if (KINDS[i].equals(kind)) return i;
		}
		return 0;
	}

	public int[] encode() {
		List<Integer> a = new ArrayList<>();
		a.add(VERSION);
		a.add(seq);
		a.add(stacks.size());
		for (Stack s : stacks) {
			a.add(s.seat());
			a.add(kindOrdinal(s.kind()));
			a.add(s.point());
			a.add(clampInt(s.flat()));
			a.add(clampInt(s.odds()));
		}
		a.add(nets.size());
		for (Net n : nets) {
			a.add(n.seat());
			a.add(clampInt(n.net()));
		}
		return a.stream().mapToInt(Integer::intValue).toArray();
	}

	private static int clampInt(long v) {
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
	}

	/** Decodes {@link #encode}; throws on an unknown or malformed array. */
	public static CrapsPub decode(int[] a) {
		if (a.length < 4 || a[0] != VERSION) throw new IllegalArgumentException("craps pub v" + (a.length > 0 ? a[0] : -1));
		int i = 1;
		int seq = a[i++];
		int n = a[i++];
		if (n < 0 || n > MAX_STACKS || a.length < i + n * 5 + 1) throw new IllegalArgumentException("craps pub stacks " + n);
		List<Stack> stacks = new ArrayList<>();
		for (int k = 0; k < n; k++) {
			int kind = a[i + 1];
			stacks.add(new Stack(a[i], KINDS[Math.floorMod(kind, KINDS.length)], a[i + 2], a[i + 3], a[i + 4]));
			i += 5;
		}
		int m = a[i++];
		if (m < 0 || a.length != i + m * 2) throw new IllegalArgumentException("craps pub nets " + m);
		List<Net> nets = new ArrayList<>();
		for (int k = 0; k < m; k++) {
			nets.add(new Net(a[i], a[i + 1]));
			i += 2;
		}
		return new CrapsPub(seq, stacks, nets);
	}
}
