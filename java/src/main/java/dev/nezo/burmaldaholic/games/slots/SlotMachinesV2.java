package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.SlotsConfig;
import dev.nezo.burmaldaholic.core.config.sections.SlotsV2Config;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDefaults;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotRtpV2;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SymbolRole;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Slots v2 machines built from the live config ({@code slots.<m>.*}, SLOTS.md §12): engine definitions (invalid
 * strips / pays keep the defaults, logged loudly), RTP reports ({@code slots.validateRtp}), house edges for cashback,
 * streak RTP. Cached until the config changes ({@link #invalidate()}).
 */
public final class SlotMachinesV2 {
	private static final Map<Machine, MachineDef> DEFS = new ConcurrentHashMap<>();
	private static final Map<Machine, SlotRtpV2.Report> REPORTS = new ConcurrentHashMap<>();
	private static final AtomicReference<List<SlotRtpV2.Warning>> WARNINGS = new AtomicReference<>(List.of());

	/** SLOTS.md §7.1 (default machines; verified by {@code SlotRtpV2Test}). */
	static final double[] DEFAULT_RTP = {0.950735093002, 0.953943832565, 0.965344189995};
	static final double[] DEFAULT_OWNED_RTP = {0.940483002869, 0.938091430577, 0.937566412217};
	/** Mean free-spin win of a 3-scatter trigger (× bet): what a buy gets (SLOTS.md §6.3). */
	static final double[] DEFAULT_FS3 = {12.858949740342, 17.198135247722, 102.466891985663};
	static final double[] DEFAULT_CONTRIBUTION = {0.010, 0.015, 0.025};

	private SlotMachinesV2() {}

	public static void invalidate() {
		DEFS.clear();
		REPORTS.clear();
	}

	/** The machine of a (unchanged) block tier. */
	public static Machine machine(Tier tier) {
		return switch (tier) {
			case COPPER -> Machine.OVERWORLD;
			case GOLD -> Machine.NETHER;
			case NETHERITE -> Machine.END;
		};
	}

	public static SlotsV2Config.Machine cfg(Machine m) {
		SlotsConfig c = CasinoConfig.slots();
		return switch (m) {
			case OVERWORLD -> c.overworld;
			case NETHER -> c.nether;
			case END -> c.end;
		};
	}

	public static MachineDef def(Machine m) {
		return DEFS.computeIfAbsent(m, k -> {
			try {
				return build(k, cfg(k));
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("!!! slots.{}: invalid machine config ({}) — using the SLOTS.md defaults", k.id, e.getMessage());
				return SlotDefaults.def(k);
			}
		});
	}

	/** Engine definition from a config object; throws {@link IllegalArgumentException} with the reason when invalid. */
	public static MachineDef build(Machine m, SlotsV2Config.Machine c) {
		MachineDef d = SlotDefaults.def(m);
		String[] codes = SlotDefaults.codes(m);
		int[][] strips = new int[5][];
		for (int r = 0; r < 5; r++) strips[r] = SlotDefaults.parseStrip(codes, c.strips[r]);
		List<String> errors = SlotDefaults.validateStrips(m, strips, d.bonusReelsMask());
		if (!errors.isEmpty()) throw new IllegalArgumentException("strips: " + errors);
		String[] ids = SlotDefaults.symbolIds(m);
		int[][] pays = new int[ids.length][3];
		for (int s = 0; s < ids.length; s++) {
			if (d.roles()[s] != SymbolRole.PAY) continue;
			double[] p = c.pays.get(ids[s]);
			if (p == null || p.length != 3) throw new IllegalArgumentException("pays." + ids[s]);
			for (int k = 0; k < 3; k++) pays[s][k] = fifths(p[k], "pays." + ids[s]);
		}
		int[] scat = new int[3];
		for (int k = 0; k < 3; k++) scat[k] = fifths(c.scatterPays[k], "scatterPays");
		if (m == Machine.NETHER) scat = new int[3];
		SlotsV2Config.FreeSpins fs = c.freeSpins;
		int[] ladder = d.ladder();
		int[] ladderFree = d.ladderFree();
		int buy = 0;
		MachineDef.Features f = d.features();
		int[] pickW = f.pickWeights();
		int board = f.pickBoard();
		int holdTrigger = f.holdTrigger();
		int holdRespins = f.holdRespins();
		int holdPpm = f.holdCoinPpm();
		int[] holdW = f.holdWeights();
		int[][] rings = f.wheelRings();
		switch (c) {
			case SlotsV2Config.Overworld o -> {
				board = o.pick.board;
				pickW = weights(o.pick.weights, SlotDefaults.PICK_KEYS, "pick.weights");
			}
			case SlotsV2Config.Nether n -> {
				ladder = n.tumble.ladder.clone();
				ladderFree = n.tumble.ladderFree.clone();
				holdTrigger = n.hold.trigger;
				holdRespins = n.hold.respins;
				holdPpm = (int) Math.round(n.hold.coinChance * 1_000_000);
				holdW = weights(n.hold.coinWeights, SlotDefaults.HOLD_KEYS, "hold.coinWeights");
				buy = fifths(n.buy.price, "buy.price");
			}
			case SlotsV2Config.End e -> {
				rings = new int[][] {SlotDefaults.wheelTokens(e.wheel.outer), SlotDefaults.wheelTokens(e.wheel.middle),
					SlotDefaults.wheelTokens(e.wheel.core)};
				for (int v : rings[2]) if (v == 0) throw new IllegalArgumentException("wheel.core: UP is not allowed");
				buy = fifths(e.buy.price, "buy.price");
			}
			default -> {
			}
		}
		SlotsV2Config.Jackpot j = c.jackpot;
		int[] seeds = new int[4];
		int[] ppm = new int[4];
		int[] owned = new int[4];
		for (int t = 0; t < 4; t++) {
			String key = SlotDefaults.TIER_KEYS[t];
			seeds[t] = j.seed.getOrDefault(key, 0);
			ppm[t] = (int) Math.round(j.contribution.getOrDefault(key, 0.0) * 1_000_000);
			owned[t] = j.owned.getOrDefault(key, 0);
		}
		MachineDef.Features nf = new MachineDef.Features(board, f.pickValues(), pickW, holdTrigger, holdRespins, holdPpm, f.holdValues(), holdW, rings,
			j.refBet, seeds, ppm, owned);
		return new MachineDef(m, codes, d.roles(), strips, pays, scat, d.bonusReelsMask(), fs.awards.clone(), fs.retrigger, fs.cap,
			m == Machine.OVERWORLD ? fs.multiplier : 1, ladder, ladderFree, c.maxWinMultiple, buy, nf);
	}

	private static int fifths(double x, String what) {
		double f = x * 5;
		if (Math.abs(f - Math.rint(f)) > 1e-9 || f < 0) throw new IllegalArgumentException(what + ": multiples of 0.2 expected");
		return (int) Math.rint(f);
	}

	private static int[] weights(Map<String, Integer> map, String[] keys, String what) {
		int[] w = new int[keys.length];
		long sum = 0;
		for (int i = 0; i < keys.length; i++) {
			w[i] = map.getOrDefault(keys[i], 0);
			sum += w[i];
		}
		if (sum <= 0 || sum > (1 << 20)) throw new IllegalArgumentException(what + ": total weight must be 1…1 048 576");
		return w;
	}

	/** Whether the machine runs the SLOTS.md defaults (known RTP, no enumeration needed). */
	public static boolean isDefault(Machine m) {
		return def(m).equals(SlotDefaults.def(m));
	}

	/** The RTP report if computed (non-default configs are computed by {@link #validate}). */
	public static SlotRtpV2.@Nullable Report report(Machine m) {
		return REPORTS.get(m);
	}

	private static double contribution(MachineDef d) {
		double c = 0;
		for (int ppm : d.features().contributionPpm()) c += ppm / 1_000_000.0;
		return c;
	}

	/**
	 * The machine's paying tables are the defaults: only the buy price and / or the contributions differ, so the exact
	 * §7.1 numbers apply with the contributions added live and the buy RTP recomputed exactly from the price.
	 */
	public static boolean tablesDefault(Machine m) {
		MachineDef d = def(m);
		MachineDef s = SlotDefaults.def(m);
		MachineDef.Features f = d.features();
		MachineDef.Features sf = s.features();
		MachineDef.Features nf = new MachineDef.Features(f.pickBoard(), f.pickValues(), f.pickWeights(), f.holdTrigger(), f.holdRespins(),
			f.holdCoinPpm(), f.holdValues(), f.holdWeights(), f.wheelRings(), f.jackpotRef(), f.jackpotSeedMult(), sf.contributionPpm(), f.ownedMult());
		MachineDef n = new MachineDef(d.machine(), d.codes(), d.roles(), d.strips(), d.paysFifths(), d.scatterFifths(), d.bonusReelsMask(),
			d.freeSpins(), d.retrigger(), d.fsCap(), d.fsMultiplier(), d.ladder(), d.ladderFree(), d.capMultiple(), s.buyPriceFifths(), nf);
		return n.equals(s);
	}

	/** House RTP for the streak cap and the paytable line. */
	public static double houseRtp(Machine m) {
		SlotRtpV2.Report r = REPORTS.get(m);
		if (r != null) return r.total();
		return DEFAULT_RTP[m.ordinal()] - DEFAULT_CONTRIBUTION[m.ordinal()] + (tablesDefault(m) ? contribution(def(m)) : DEFAULT_CONTRIBUTION[m.ordinal()]);
	}

	public static double ownedRtp(Machine m) {
		SlotRtpV2.Report r = REPORTS.get(m);
		return r != null ? r.totalOwned() : DEFAULT_OWNED_RTP[m.ordinal()];
	}

	/** Buy-feature RTP incl. contribution: {@code EV(3-scatter free spins) / price + contribution} (NaN without a buy). */
	public static double buyRtp(Machine m) {
		SlotRtpV2.Report r = REPORTS.get(m);
		if (r != null) return r.buyRtp();
		MachineDef d = def(m);
		if (d.buyPriceFifths() <= 0) return Double.NaN;
		return DEFAULT_FS3[m.ordinal()] / (d.buyPriceFifths() / 5.0) + contribution(d);
	}

	/** Cashback edge (SLOTS.md §7.1): 1 − house RTP; for a buy, 1 − its buy RTP. */
	public static double houseEdge(Machine m, boolean buy, boolean owned) {
		double rtp = buy ? buyRtp(m) : owned ? ownedRtp(m) : houseRtp(m);
		return Double.isNaN(rtp) ? 1 - houseRtp(m) : Math.max(0, 1 - rtp);
	}

	/**
	 * {@code slots.validateRtp}: recompute §7.1 for every machine whose config differs from the defaults (exact
	 * enumeration, a few seconds; call off the server thread) and warn loudly when RTP &gt; 0.99 or a buy RTP exceeds its
	 * machine RTP. Never auto-fixes.
	 */
	public static List<SlotRtpV2.Warning> validate() {
		List<SlotRtpV2.Warning> out = new ArrayList<>();
		Map<Machine, SlotRtpV2.Report> fresh = new EnumMap<>(Machine.class);
		for (Machine m : Machine.values()) {
			if (isDefault(m)) continue;
			// any change is re-validated EXACTLY (full enumeration + chains), incl. the buy RTP when only the price changed
			SlotRtpV2.Report r = SlotRtpV2.compute(def(m));
			fresh.put(m, r);
			for (SlotRtpV2.Warning w : SlotRtpV2.warnings(r)) {
				out.add(w);
				Burmaldaholic.LOGGER.warn("!!! slots.{}{}: {} RTP {} {} — check slots.{}.* in the config (never auto-fixed)", m.id,
					w.owned() ? " (owned)" : "", w.buy() ? "buy-feature" : "", w.percent(),
					w.buy() ? "is above the machine RTP or more than 0.5 % below it" : "exceeds 99 %", m.id);
			}
			Burmaldaholic.LOGGER.info("slots.{}: RTP {} % (owned {} %)", m.id, String.format(java.util.Locale.ROOT, "%.4f", r.total() * 100),
				String.format(java.util.Locale.ROOT, "%.4f", r.totalOwned() * 100));
		}
		REPORTS.putAll(fresh);
		WARNINGS.set(List.copyOf(out));
		return out;
	}

	/** Warnings of the last {@link #validate} run (admin page). */
	public static List<SlotRtpV2.Warning> lastWarnings() {
		return WARNINGS.get();
	}

	/** Runs {@link #validate} on a background thread (enumeration must never block the server thread). */
	public static void validateAsync() {
		Thread t = new Thread(() -> {
			try {
				validate();
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("slots.validateRtp failed", e);
			}
		}, "burmaldaholic-slots-rtp");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}
}
