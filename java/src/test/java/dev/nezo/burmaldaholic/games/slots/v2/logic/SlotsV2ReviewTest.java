package dev.nezo.burmaldaholic.games.slots.v2.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Review J-L8 (adversarial): an independent replay of every tape against SLOTS.md §1.3 (the cap ends every feature at
 * once), §3 feature rules and §5 pool maths, with tiny caps so the cap lands inside every feature; owned and house; paid
 * spins and buys. Nothing here reuses the draw's accumulator.
 */
class SlotsV2ReviewTest {
	private static MachineDef withCap(MachineDef d, int cap) {
		return new MachineDef(d.machine(), d.codes(), d.roles(), d.strips(), d.paysFifths(), d.scatterFifths(), d.bonusReelsMask(), d.freeSpins(),
			d.retrigger(), d.fsCap(), d.fsMultiplier(), d.ladder(), d.ladderFree(), cap, d.buyPriceFifths(), d.features());
	}

	/** Independent capped running total (SLOTS.md §1.3). */
	private static final class Run {
		final long cap;
		long total;
		boolean capped;

		Run(long cap) {
			this.cap = cap;
		}

		void add(long f) {
			if (capped) return;
			total += f;
			if (total >= cap) {
				total = cap;
				capped = true;
			}
		}
	}

	/** Replays a tape; returns the jackpot tiers that must have been awarded, in order. */
	private static List<Integer> replay(MachineDef d, SpinTape t, boolean owned, Run run) {
		List<Integer> tiers = new java.util.ArrayList<>();
		MachineDef.Features f = d.features();
		java.util.function.IntConsumer prize = v -> {
			if (run.capped) return;
			if (v > 0) run.add(v * 5L);
			else if (v < 0) {
				tiers.add(-v);
				if (owned) run.add(f.ownedMult()[-v - 1] * 5L);
			}
		};
		if (!t.bought()) {
			SpinEval base = SpinEval.of(d, t.stops(), false, 0);
			run.add(base.payFifths());
			if (t.hunt() != null) {
				assertTrue(base.bonus(), "hunt only on a trigger");
				for (int e : t.hunt().entries()) {
					if (run.capped || e == 0) break;
					prize.accept(e);
				}
			}
			if (t.hoard() != null) {
				SpinTape.Hoard h = t.hoard();
				assertEquals(base.coins(), h.initialCells().length, "one value per initial coin");
				assertTrue(base.coins() >= f.holdTrigger());
				// respin rule: any new coin resets to `respins`; the feature ends at 0 left or 15 coins
				int left = f.holdRespins();
				int n = h.initialCells().length;
				for (int i = 0; i < h.respinCells().size(); i++) {
					assertTrue(left > 0 && n < 15, "respin played after the end");
					int k = h.respinCells().get(i).length;
					n += k;
					left = k > 0 ? f.holdRespins() : left - 1;
				}
				assertTrue(left == 0 || n >= 15, "hoard ended early");
				for (int v : h.initialValues()) prize.accept(v);
				for (int[] vs : h.respinValues()) for (int v : vs) prize.accept(v);
				if (!run.capped && n >= 15) prize.accept(-Jackpots.GRAND);
			}
			if (t.wheel() != null) {
				assertTrue(base.bonus());
				int[] segs = t.wheel().segments();
				for (int i = 0; i < segs.length - 1; i++) assertEquals(0, f.wheelRings()[i][segs[i]], "only UP leads inward");
				int last = f.wheelRings()[segs.length - 1][segs[segs.length - 1]];
				assertTrue(last != 0 || segs.length == f.wheelRings().length, "the wheel stopped on UP");
				prize.accept(last);
			}
		}
		if (t.freeSpins() != null) {
			int given = Math.min(t.bought() ? d.freeSpins()[0] : d.freeSpins()[Math.min(SpinEval.of(d, t.stops(), false, 0).scatters(), 5) - 3],
				d.fsCap());
			assertEquals(given, t.freeSpins().awarded());
			int sticky = 0;
			int played = 0;
			for (SpinTape.FreeSpin s : t.freeSpins().spins()) {
				assertFalse(run.capped, "a free spin was played after the max-win cap");
				SpinEval e = SpinEval.of(d, s.stops(), true, sticky);
				assertEquals(e.payFifths() * d.fsMultiplier(), s.payFifths());
				assertEquals(e.scatters() >= 3, s.retrigger());
				assertEquals(e.stickyAfter(), s.stickyMaskAfter());
				sticky = e.stickyAfter();
				if (s.retrigger()) given += Math.min(d.retrigger(), d.fsCap() - given);
				played++;
				run.add(s.payFifths());
			}
			assertTrue(played <= given);
			if (!run.capped) assertEquals(given, played, "free spins forfeited without the cap");
		} else if (!t.bought()) {
			int sc = SpinEval.of(d, t.stops(), false, 0).scatters();
			assertTrue(sc < 3 || run.capped, "a trigger without free spins");
		}
		return tiers;
	}

	@Test
	void theCapEndsEveryFeatureAndTheTotalIsReplayable() {
		for (Machine m : Machine.values()) {
			MachineDef base = SlotDefaults.def(m);
			for (int cap : new int[] {3, 12, 60, base.capMultiple()}) {
				MachineDef d = withCap(base, cap);
				SlotRng rng = SlotRng.seeded(cap * 7919 + m.ordinal());
				int features = 0;
				int capped = 0;
				for (int i = 0; i < 25_000; i++) {
					boolean owned = i % 3 == 0;
					boolean buy = d.buyPriceFifths() > 0 && i % 40 == 0;
					long bet = new long[] {5, 10, 50, 100, 500, 5000}[i % 6];
					long[] pools = owned ? null : new long[] {0, 1_000 + i, 3_000, 20_000, 99_999};
					SpinTape t = SlotDraw.draw(new SlotDraw.Request(d, bet, buy, owned, pools), rng);
					Run run = new Run(cap * 5L);
					List<Integer> tiers = replay(d, t, owned, run);
					assertEquals(run.total, t.totalFifths(), "total " + TapeCodec.encode(t));
					assertEquals(run.capped, t.capHit(), "cap flag " + TapeCodec.encode(t));
					assertEquals(tiers, t.jackpots().stream().map(SpinTape.JackpotAward::tier).toList(), "jackpots " + TapeCodec.encode(t));
					for (SpinTape.JackpotAward j : t.jackpots()) assertEquals(owned, j.owned());
					if (owned) assertEquals(t.totalChips(), t.payoutChips(), "owned: everything inside the cap");
					assertTrue(t.totalChips() <= SlotDraw.reservation(d, bet), "payout above the owned reservation");
					if (t.hunt() != null) {
						int opens = SlotDraw.huntOpens(d, t);
						assertTrue(opens >= 1 && opens <= t.hunt().picks());
					}
					if (t.featureTriggered()) features++;
					if (t.capHit()) capped++;
				}
				assertTrue(features > 50, m.id + " cap " + cap + ": features seen " + features);
				if (cap <= 12) assertTrue(capped > 100, m.id + " cap " + cap + ": caps seen " + capped);
			}
		}
	}

	/** §5.1/§5.2: contributions are exact to the millionth; each award mints the seed share within one chip. */
	@Test
	void poolLedgerIsExact() {
		for (Machine m : Machine.values()) {
			MachineDef d = SlotDefaults.def(m);
			MachineDef.Features f = d.features();
			long ref = f.jackpotRef();
			SlotRng rng = SlotRng.seeded(4242 + m.ordinal());
			long[] inc = new long[5];
			long[] rem = new long[5];
			long[] contributedMicros = new long[5];
			long[] awarded = new long[5];
			long[] minted = new long[5];
			long[] incFromContrib = new long[5];
			int hits = 0;
			long[] ladder = {5, 10, 20, 50, 100, 250, 500, 1000, 2500, 5000};
			for (int i = 0; i < 200_000; i++) {
				long bet = ladder[i % ladder.length];
				boolean buy = d.buyPriceFifths() > 0 && i % 97 == 0;
				long stake = buy ? d.buyPrice(bet) : bet;
				for (int t = 1; t <= 4; t++) {
					long[] r = Jackpots.contribute(rem[t], stake, f.contributionPpm()[t - 1]);
					inc[t] += r[0];
					incFromContrib[t] += r[0];
					rem[t] = r[1];
					contributedMicros[t] += stake * f.contributionPpm()[t - 1];
				}
				long[] pools = new long[5];
				for (int t = 1; t <= 4; t++) pools[t] = f.seedChips(t) + inc[t];
				SpinTape tape = SlotDraw.draw(new SlotDraw.Request(d, bet, buy, false, pools), rng);
				for (SpinTape.JackpotAward j : tape.jackpots()) {
					int t = j.tier();
					long seed = f.seedChips(t);
					long before = inc[t];
					long after = Jackpots.incrementAfter(before, bet, ref);
					assertTrue(j.chips() <= seed + before, "award above the pool");
					long mint = j.chips() - (before - after);
					double share = (double) seed * Math.min(bet, ref) / ref;
					assertTrue(Math.abs(mint - share) <= 1.0, "mint " + mint + " vs seed share " + share);
					awarded[t] += j.chips();
					minted[t] += mint;
					inc[t] = after;
					hits++;
				}
				long[] check = new long[5];
				for (int t = 1; t <= 4; t++) check[t] = f.seedChips(t) + inc[t];
				long[] replay = pools.clone();
				SlotDraw.applyAwards(d, tape, replay);
				assertEquals(java.util.Arrays.toString(check), java.util.Arrays.toString(replay), "module debit = draw debit");
			}
			for (int t = 1; t <= 4; t++) {
				assertEquals(contributedMicros[t], incFromContrib[t] * Jackpots.MICROS + rem[t], "contribution micros");
				// conservation: what went in (contributions + minted seed shares) = what came out + what is left
				assertEquals(incFromContrib[t] + minted[t], awarded[t] + inc[t], m.id + " tier " + t);
			}
			assertTrue(hits > 20, m.id + " jackpot hits " + hits);
		}
	}

	/** The streak re-draw candidate test counts pool money (§8.2 "a spin incl. jackpots is one outcome"). */
	@Test
	void losingIncludesProgressiveAwards() {
		SpinTape t = new SpinTape(Machine.OVERWORLD, 10, false, new int[5], null, null, null, null,
			List.of(new SpinTape.JackpotAward(1, 50, false)), 0, false);
		assertFalse(SlotDraw.losing(t));
		assertEquals(50, t.payoutChips());
		assertEquals(0, t.totalChips(), "pool money is outside the wager's payout (Golden Hour net excludes it, §8.3)");
	}
}
