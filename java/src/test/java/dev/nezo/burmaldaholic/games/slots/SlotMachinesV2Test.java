package dev.nezo.burmaldaholic.games.slots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.config.sections.SlotsV2Config;
import dev.nezo.burmaldaholic.games.slots.logic.SlotTable;
import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDefaults;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotRtpV2;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotRtpV2Test;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Tumble;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Config → engine definitions (SLOTS.md §12), exact buy RTP, tumble bound, LegacySlots (review items of lane B-L8). */
class SlotMachinesV2Test {
	private static SlotsV2Config.Machine config(Machine m) {
		return switch (m) {
			case OVERWORLD -> new SlotsV2Config.Overworld();
			case NETHER -> new SlotsV2Config.Nether();
			case END -> new SlotsV2Config.End();
		};
	}

	@Test
	void defaultConfigBuildsTheSlotsMdMachines() {
		for (Machine m : Machine.values()) assertEquals(SlotDefaults.def(m), SlotMachinesV2.build(m, config(m)), m.id);
	}

	@Test
	void validCustomStripsAndWheelAreAccepted() {
		SlotsV2Config.Overworld o = new SlotsV2Config.Overworld();
		String[] cells = o.strips[0].split(" ");
		String rotated = String.join(" ", java.util.stream.Stream.concat(java.util.Arrays.stream(cells).skip(1), java.util.stream.Stream.of(cells[0])).toList());
		o.strips = o.strips.clone();
		o.strips[0] = rotated;
		MachineDef d = SlotMachinesV2.build(Machine.OVERWORLD, o);
		assertNotEquals(SlotDefaults.def(Machine.OVERWORLD), d);
		assertEquals(SlotDefaults.def(Machine.OVERWORLD).strips()[0][1], d.strips()[0][0]);
		SlotsV2Config.End e = new SlotsV2Config.End();
		e.wheel.outer = new String[] {"10", "UP", "12", "MINI", "20", "UP"};
		assertEquals(6, SlotMachinesV2.build(Machine.END, e).features().wheelRings()[0].length);
	}

	@Test
	void invalidTablesAreRejected() {
		SlotsV2Config.Overworld o = new SlotsV2Config.Overworld();
		o.strips = o.strips.clone();
		o.strips[0] = "WD " + o.strips[0].substring(3); // Wild on reel 1
		assertThrows(IllegalArgumentException.class, () -> SlotMachinesV2.build(Machine.OVERWORLD, o));
		SlotsV2Config.End e = new SlotsV2Config.End();
		e.wheel.core = new String[] {"150", "UP", "250", "GRAND"};
		assertThrows(IllegalArgumentException.class, () -> SlotMachinesV2.build(Machine.END, e));
		SlotsV2Config.Nether n = new SlotsV2Config.Nether();
		n.pays = Map.of("wither_skull", new double[] {0.3, 2, 8});
		assertThrows(IllegalArgumentException.class, () -> SlotMachinesV2.build(Machine.NETHER, n));
	}

	/** The exact constants used before a background validation finishes equal the enumeration (§6.3, §7.1). */
	@Test
	void buyRtpIsExactForAnyPrice() {
		for (Machine m : Machine.values()) {
			SlotRtpV2.Report r = SlotRtpV2Test.report(m);
			assertEquals(SlotMachinesV2.DEFAULT_FS3[m.ordinal()], r.fsPerTrigger()[0], 1e-9);
			assertEquals(SlotMachinesV2.DEFAULT_RTP[m.ordinal()], r.total(), 1e-11);
			assertEquals(SlotMachinesV2.DEFAULT_OWNED_RTP[m.ordinal()], r.totalOwned(), 1e-11);
		}
		assertEquals(0.94968, SlotRtpV2Test.report(Machine.NETHER).buyRtp(), 5e-6);
		assertEquals(0.96506, SlotRtpV2Test.report(Machine.END).buyRtp(), 5e-6);
		// an underpriced Nether buy (15 × bet) is flagged: buy RTP above the machine RTP
		double buy = SlotMachinesV2.DEFAULT_FS3[1] / 15 + 0.015;
		SlotRtpV2.Report n = SlotRtpV2Test.report(Machine.NETHER);
		SlotRtpV2.Report cheap = new SlotRtpV2.Report(n.machine(), n.baseWays(), n.scatterPay(), n.freeSpins(), n.bonus(), n.jackpotSeed(),
			n.jackpotContribution(), n.total(), n.totalOwned(), n.hitPay(), n.hitAny(), n.pFreeSpins(), n.pBonus(), n.fsPerTrigger(),
			n.fsExpectedSpins(), n.bonusPerTrigger(), n.jackpotHits(), buy);
		assertTrue(buy > n.total());
		assertTrue(SlotRtpV2.warnings(cheap).stream().anyMatch(SlotRtpV2.Warning::buy));
	}

	/** A configured strip set can tumble forever: the chain stops after {@link Tumble#MAX_STEPS} evaluations. */
	@Test
	void tumbleChainsAreBounded() {
		MachineDef d = SlotDefaults.def(Machine.NETHER);
		int[][] strips = new int[5][32]; // every cell a Wither Skull (index 3): every evaluation wins
		for (int[] s : strips) java.util.Arrays.fill(s, 3);
		MachineDef endless = new MachineDef(d.machine(), d.codes(), d.roles(), strips, d.paysFifths(), d.scatterFifths(), d.bonusReelsMask(),
			d.freeSpins(), d.retrigger(), d.fsCap(), d.fsMultiplier(), d.ladder(), d.ladderFree(), d.capMultiple(), d.buyPriceFifths(), d.features());
		Tumble.Chain c = Tumble.run(endless, new int[5], d.ladder());
		assertEquals(Tumble.MAX_STEPS, c.steps().size());
		assertEquals(Tumble.MAX_STEPS - 1, c.tumbles());
		assertTrue(c.steps().getLast().payFifths() > 0, "the last evaluation still pays");
	}

	/** SLOTS.md §15 test 13 (v1 part): a v1 record fixture settles at its stored payout with the kept v1 evaluator. */
	@Test
	void legacyRecordSettlesWithTheV1Evaluator() {
		SlotTable table = SlotTable.of(Map.of("berries", 24, "apple", 20, "diamond", 8), Map.of("berries", 10.0, "apple", 10.0, "diamond", 60.0),
			new double[] {2, 3}, 1, false);
		CompoundTag rec = new CompoundTag();
		int d = Symbol.DIAMOND.ordinal();
		int a = Symbol.APPLE.ordinal();
		rec.putIntArray("grid", new int[] {a, a, a, d, d, d, a, a, a});
		rec.putLong("line_bet", 2);
		rec.putString("tier", "copper");
		Long payout = LegacySlots.payout(rec, table);
		assertEquals(120L, payout); // middle row: three diamonds × 60 × 2
		assertEquals(dev.nezo.burmaldaholic.games.slots.logic.Tier.COPPER, LegacySlots.tier(rec));
		rec.putIntArray("grid", new int[] {1, 2});
		assertEquals(null, LegacySlots.payout(rec, table));
	}
}
