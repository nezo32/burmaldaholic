package dev.nezo.burmaldaholic.core.wager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Lane HR (owner playtest B5): whose cap a maximum is, the same key on the client's gates and in the state tag. */
class BetCapTest {
	@Test
	void inferSource() {
		assertEquals(BetCap.Source.OWNER, BetCap.infer(1000, 1, 5000, 1000, 0, 1000, 1, 4000).source(), "owner wins a tie with the VIP max");
		assertEquals(BetCap.Source.TABLE, BetCap.infer(5000, 1, 5000, 0, 0, 20_000, 3, 50_000).source());
		BetCap vip = BetCap.infer(500, 1, 0, 0, 0, 500, 1, 2000);
		assertEquals(BetCap.Source.VIP, vip.source());
		assertEquals(2, vip.nextTier());
		assertEquals(2000, vip.nextMax());
		assertEquals(BetCap.KEY_VIP, vip.reasonKey());
		BetCap hr = BetCap.infer(1000, 1, 0, 0, 0, 500, 1, 2000);
		assertEquals(4000, hr.nextMax(), "a High-Roller ×2 maximum scales with the next tier");
		BetCap capped = BetCap.infer(500, 1, 0, 1500, 0, 500, 1, 2000);
		assertEquals(1500, capped.nextMax(), "the next tier is still capped by the owner");
		BetCap top = BetCap.infer(50_000, 1, 0, 0, 0, 50_000, 5, 0);
		assertEquals(BetCap.KEY_VIP_TOP, top.reasonKey(), "the top tier names no next tier");
		assertEquals(BetCap.Source.NONE, BetCap.infer(0, 1, 0, 0, 0, 500, 1, 2000).source());
		assertTrue(BetCap.infer(500, 50, 0, 0, 50, 500, 1, 0).ownerMin());
	}

	/** T-HR D3: a game's own maximum (Plinko: a fifth of the VIP max) scales the next tier's number too. */
	@Test
	void withMaxScalesTheNextTier() {
		BetCap c = BetCap.infer(1000, 1, 0, 0, 0, 1000, 1, 4000);
		BetCap fifth = c.withMax(200);
		assertEquals(200, fifth.max());
		assertEquals(800, fifth.nextMax());
		assertEquals(2, fifth.nextTier());
		assertEquals(c, c.withMax(1000));
	}

	@Test
	void stateRoundTrip() {
		BetCap c = BetCap.infer(500, 25, 0, 0, 0, 500, 1, 2000);
		CompoundTag t = new CompoundTag();
		c.write(t);
		assertEquals("vip", t.getStringOr("cap_source", ""));
		assertEquals(c, BetCap.read(t));
		assertEquals(BetCap.NONE, BetCap.read(new CompoundTag()));
	}

	@Test
	void gatesUseTheCapSentence() {
		BetView.Builder b = BetView.builder().seated(true).phase(BetView.Phase.BETTING).bettingOpen(true).limits(1, 500).balance(10_000).myTotal(400);
		assertEquals(V2Keys.REASON_ABOVE_MAX, BetGates.chip(500, b.build()).reasonKey(), "unknown cap: the generic sentence");
		assertEquals(BetCap.KEY_VIP, BetGates.chip(500, b.cap(BetCap.infer(500, 1, 0, 0, 0, 500, 1, 2000)).build()).reasonKey());
		assertEquals(BetCap.KEY_OWNER, BetGates.chip(500, b.cap(BetCap.infer(500, 1, 0, 500, 0, 2000, 2, 5000)).build()).reasonKey());
		assertEquals(BetCap.KEY_TABLE, BetGates.tool(BetGates.DOUBLE, b.canDouble(true).cap(BetCap.infer(500, 1, 500, 0, 0, 2000, 2, 5000)).build())
			.reasonKey());
	}

	@Test
	void serverRefusalsUseTheSameKeys() {
		BetCap c = BetCap.infer(500, 25, 0, 0, 0, 500, 1, 2000);
		assertEquals(BetCap.KEY_VIP, key(BetLimits.refusal(BetLimits.Violation.TIER_MAX, c, 10_000)));
		assertEquals(V2Keys.REASON_BELOW_MIN, key(BetLimits.refusal(BetLimits.Violation.TOO_LOW, c, 10_000)));
		assertEquals(BetCap.KEY_BALANCE, key(BetLimits.refusal(BetLimits.Violation.INSUFFICIENT_FUNDS, c, 10)));
	}

	private static String key(net.minecraft.network.chat.Component c) {
		return ((net.minecraft.network.chat.contents.TranslatableContents) c.getContents()).getKey();
	}
}
