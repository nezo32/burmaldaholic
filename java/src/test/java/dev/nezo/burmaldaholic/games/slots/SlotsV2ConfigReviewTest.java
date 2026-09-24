package dev.nezo.burmaldaholic.games.slots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.config.ConfigBinder;
import dev.nezo.burmaldaholic.core.config.ConfigIssue;
import dev.nezo.burmaldaholic.core.config.sections.SlotsConfig;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDefaults;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotRtpV2;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Review J-L8, parity with the Bedrock review findings: string lists (strips, wheel wedges) survive the config binder;
 * a price-only change is re-validated exactly and an over-paying buy is flagged; bad values are rejected.
 */
class SlotsV2ConfigReviewTest {
	private final ConfigBinder binder = new ConfigBinder(new Gson());

	@Test
	void defaultsRoundTripThroughTheBinderWithoutIssues() {
		Gson gson = new Gson();
		JsonObject json = gson.toJsonTree(new SlotsConfig()).getAsJsonObject();
		List<ConfigIssue> issues = new ArrayList<>();
		SlotsConfig c = binder.bind(new SlotsConfig(), json, "slots", issues);
		assertTrue(issues.isEmpty(), issues.toString());
		for (Machine m : Machine.values()) {
			var cfg = switch (m) {
				case OVERWORLD -> c.overworld;
				case NETHER -> c.nether;
				case END -> c.end;
			};
			assertEquals(SlotDefaults.def(m), SlotMachinesV2.build(m, cfg), m.id);
		}
	}

	@Test
	void customStringListsAreAcceptedAndBadOnesRejected() {
		List<ConfigIssue> issues = new ArrayList<>();
		String end0 = String.join(" ", SlotDefaults.strips(Machine.END)[0].split(" "));
		SlotsConfig c = binder.bind(new SlotsConfig(), JsonParser.parseString("""
			{"end": {"wheel": {"outer": ["10", "UP", "12", "MINI", "20", "UP"], "core": ["150", "MAJOR", "250", "GRAND"]},
			         "strips": ["%s", "%s", "%s", "%s", "%s"]}}""".formatted(end0, SlotDefaults.strips(Machine.END)[1],
			SlotDefaults.strips(Machine.END)[2], SlotDefaults.strips(Machine.END)[3], SlotDefaults.strips(Machine.END)[4])).getAsJsonObject(),
			"slots", issues);
		assertTrue(issues.isEmpty(), issues.toString());
		MachineDef d = SlotMachinesV2.build(Machine.END, c.end);
		assertEquals(6, d.features().wheelRings()[0].length);
		assertEquals(4, d.features().wheelRings()[2].length);
		// a list of the wrong size falls back to the default (reported), never a crash
		issues.clear();
		SlotsConfig bad = binder.bind(new SlotsConfig(), JsonParser.parseString("""
			{"end": {"strips": ["ES ES ES"]}, "nether": {"bets": [10, 20, 33]}}""").getAsJsonObject(), "slots", issues);
		assertEquals(5, bad.end.strips.length);
		assertFalse(issues.isEmpty());
		assertEquals(10, bad.nether.bets[0]);
		assertEquals(0, java.util.Arrays.stream(bad.nether.bets).filter(b -> b % 5 != 0).count(), "a bet not a multiple of 5 rejects the list");
	}

	/** Bedrock review: the buy RTP was not rechecked when only the price changed. Java recomputes exactly. */
	@Test
	void anUnderpricedBuyIsFlaggedAfterAPriceOnlyChange() {
		SlotsConfig c = new SlotsConfig();
		c.nether.buy.price = 15;
		MachineDef d = SlotMachinesV2.build(Machine.NETHER, c.nether);
		assertEquals(75, d.buyPriceFifths());
		assertTrue(SlotRtpV2.warnings(SlotRtpV2.compute(d)).stream().anyMatch(SlotRtpV2.Warning::buy), "15 × bet pays more than the machine");
		c.nether.buy.price = 18.4;
		assertTrue(SlotRtpV2.warnings(SlotRtpV2.compute(SlotMachinesV2.build(Machine.NETHER, c.nether))).isEmpty(), "default price is fine");
	}
}
