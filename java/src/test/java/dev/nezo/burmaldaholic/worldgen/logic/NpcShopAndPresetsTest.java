package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class NpcShopAndPresetsTest {
	@Test
	void presetsPerCasinoAndTable() {
		assertEquals(Optional.of(TablePresets.PARLOR_POKER), TablePresets.presetId(CasinoKind.PIGLIN_PARLOR, Layouts.POKER));
		assertEquals(Optional.empty(), TablePresets.presetId(CasinoKind.PIGLIN_PARLOR, Layouts.BLACKJACK));
		assertEquals(Optional.of(TablePresets.HIGH_ROLLER_BLACKJACK), TablePresets.presetId(CasinoKind.HIGH_ROLLER, Layouts.BLACKJACK_HIGH_ROLLER));
		assertEquals(Optional.of(TablePresets.HIGH_ROLLER_BLACKJACK), TablePresets.presetId(CasinoKind.HIGH_ROLLER, Layouts.BLACKJACK));
		assertEquals(Optional.of(TablePresets.HIGH_ROLLER_ROULETTE), TablePresets.presetId(CasinoKind.HIGH_ROLLER, Layouts.ROULETTE_HIGH_ROLLER));
		assertEquals(Optional.of(TablePresets.HIGH_ROLLER_POKER), TablePresets.presetId(CasinoKind.HIGH_ROLLER, Layouts.POKER));
		assertEquals(Optional.of(TablePresets.HIGH_ROLLER_CRAPS), TablePresets.presetId(CasinoKind.HIGH_ROLLER, Layouts.CRAPS));
		assertEquals(Optional.empty(), TablePresets.presetId(CasinoKind.HIGH_ROLLER, Layouts.SLOTS_NETHERITE));
		assertEquals(Optional.empty(), TablePresets.presetId(CasinoKind.HIGH_ROLLER, Layouts.CASHIER));
		assertEquals(Optional.of(TablePresets.HIGH_ROLLER_BACCARAT), TablePresets.presetId(CasinoKind.HIGH_ROLLER, Layouts.BACCARAT_HIGH_ROLLER));
		assertEquals(Optional.of(TablePresets.HIGH_ROLLER_UTH), TablePresets.presetId(CasinoKind.HIGH_ROLLER, Layouts.UTH_HIGH_ROLLER));
		// R-CB M11: the v2 Parlor's baccarat is out of the baccarat module's neighbour search radius → direct preset
		assertEquals(Optional.of(TablePresets.PARLOR_BACCARAT), TablePresets.presetId(CasinoKind.PIGLIN_PARLOR, Layouts.BACCARAT));
		assertEquals(Optional.empty(), TablePresets.presetId(CasinoKind.PIGLIN_PARLOR, Layouts.CRAPS));
		assertEquals(Optional.empty(), TablePresets.presetId(CasinoKind.CASINO, Layouts.BACCARAT));
		assertEquals(Optional.empty(), TablePresets.presetId(CasinoKind.VILLAGE_CASINO, Layouts.BLACKJACK));
		assertEquals(Optional.empty(), TablePresets.presetId(null, Layouts.POKER));
	}

	@Test
	void presetTablesOfTheLayoutsAreCovered() {
		// every poker / baccarat table in the Parlor and every card / dice table in the Lounge gets its preset
		for (Layout l : Layouts.all().values()) {
			for (int x = 0; x < l.size().x(); x++) {
				for (int y = 0; y < l.size().y(); y++) {
					for (int z = 0; z < l.size().z(); z++) {
						BlockSpec b = l.grid().get(x, y, z);
						if (b == null) {
							continue;
						}
						String id = b.name();
						boolean expected = (l.kind() == CasinoKind.PIGLIN_PARLOR && (id.equals(Layouts.POKER) || id.equals(Layouts.BACCARAT)))
							|| (l.kind() == CasinoKind.HIGH_ROLLER && (id.startsWith(Layouts.BLACKJACK) || id.startsWith(Layouts.ROULETTE)
								|| id.startsWith(Layouts.BACCARAT) || id.startsWith(Layouts.UTH) || id.equals(Layouts.CRAPS)
								|| id.equals(Layouts.POKER)));
						assertEquals(expected, TablePresets.presetId(l.kind(), id).isPresent(), l.id() + " " + id);
					}
				}
			}
		}
	}

	@Test
	void theV2ParlorAndLoungeGetTheirPresets() {
		// R-CB M11 on the v0.2 single templates themselves (casino/parlor, casino/lounge)
		java.util.Map<String, java.util.Set<String>> want = java.util.Map.of(
			"casino/parlor", java.util.Set.of(TablePresets.PARLOR_POKER, TablePresets.PARLOR_BACCARAT),
			"casino/lounge", java.util.Set.of(TablePresets.HIGH_ROLLER_BLACKJACK, TablePresets.HIGH_ROLLER_ROULETTE,
				TablePresets.HIGH_ROLLER_BACCARAT, TablePresets.HIGH_ROLLER_UTH, TablePresets.HIGH_ROLLER_CRAPS, TablePresets.HIGH_ROLLER_POKER));
		for (var e : want.entrySet()) {
			Layout l = Layouts.byId(e.getKey()).orElseThrow(() -> new AssertionError(e.getKey() + " missing"));
			java.util.Set<String> got = new java.util.TreeSet<>();
			for (int x = 0; x < l.size().x(); x++) {
				for (int y = 0; y < l.size().y(); y++) {
					for (int z = 0; z < l.size().z(); z++) {
						BlockSpec b = l.grid().get(x, y, z);
						if (b != null) {
							TablePresets.presetId(l.kind(), b.name()).ifPresent(got::add);
						}
					}
				}
			}
			assertEquals(new java.util.TreeSet<>(e.getValue()), got, e.getKey());
		}
	}

	@Test
	void shopsMatchTheSpec() {
		assertEquals(3, NpcShop.of(NpcRole.CROUPIER).size());
		assertEquals(25, NpcShop.CROUPIER.stream().filter(o -> o.id().equals("lucky_coin")).findFirst().orElseThrow().price());
		assertEquals(1, NpcShop.of(NpcRole.SHULKER_CROUPIER).size());
		assertEquals("scratch_card_gold", NpcShop.SHULKER_CROUPIER.getFirst().id());
		assertTrue(NpcShop.of(NpcRole.PIGLIN_DEALER).isEmpty());
		assertEquals(NpcShop.Check.VIP, NpcShop.check(100, 1, 1_000, 0));
		assertEquals(NpcShop.Check.FUNDS, NpcShop.check(100, 1, 99, 1));
		assertEquals(NpcShop.Check.OK, NpcShop.check(100, 1, 100, 1));
	}

	@Test
	void greetingKeysStayInRange() {
		assertEquals("dialog.burmaldaholic.croupier.greeting.3", NpcShop.greetingKey(NpcRole.CROUPIER, 3));
		assertEquals("dialog.burmaldaholic.shulker_croupier.greeting.2", NpcShop.greetingKey(NpcRole.SHULKER_CROUPIER, 9));
		assertEquals("dialog.burmaldaholic.piglin_dealer.greeting.1", NpcShop.greetingKey(NpcRole.PIGLIN_DEALER, 0));
	}
}
