package dev.nezo.burmaldaholic.core.ui.v2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The pure contract of the v2 kit (docs/architecture/ui-v2.md §3, §5): bet model, shared gates, the "never stuck" check
 * over an exhaustive sample flow, free-slot and tag placement, the ack watchdog, keys, stamps and the strings.
 */
class KitContractTest {
	private static BetView.Builder base() {
		return BetView.builder().seated(true).canSit(true).phase(BetView.Phase.BETTING).bettingOpen(true).limits(10, 1000).balance(5000);
	}

	@Test
	void chipsAreFilteredByMaxAndBalanceAndKeepTheSmallest() {
		assertArrayEquals(new long[] {5, 25, 100, 500, 1000}, base().build().chips());
		assertArrayEquals(new long[] {1, 5, 25}, base().balance(30).build().chips());
		assertArrayEquals(new long[] {1, 5, 25, 100}, base().limits(1, 100).build().chips());
		assertArrayEquals(new long[] {1, 5, 25, 100, 500}, base().limits(1, 0).balance(100_000).build().chips());
		assertArrayEquals(new long[] {25, 100, 500, 1000, 5000}, base().limits(100, 0).balance(100_000).build().chips());
		assertArrayEquals(new long[0], base().balance(0).build().chips());
	}

	@Test
	void sharedGatesExplainEveryRefusal() {
		BetView standing = base().seated(false).build();
		assertEquals(V2Keys.REASON_SIT_TO_BET, BetGates.chip(5, standing).reasonKey());
		assertTrue(BetGates.primary("deal", V2Keys.PRIMARY_DEAL, standing).enabled(), "not seated: the primary is Sit down");
		assertEquals(BetGates.SIT, BetGates.primary("deal", V2Keys.PRIMARY_DEAL, standing).action());
		assertEquals(V2Keys.REASON_TABLE_FULL, BetGates.sit(base().seated(false).canSit(false).build()).reasonKey());
		assertEquals(V2Keys.REASON_NOT_ENOUGH_MIN, BetGates.sit(base().seated(false).balance(3).build()).reasonKey());
		assertEquals(V2Keys.REASON_PLACE_BET, BetGates.primary("deal", V2Keys.PRIMARY_DEAL, base().build()).reasonKey());
		assertEquals(V2Keys.REASON_BELOW_MIN, BetGates.primary("deal", V2Keys.PRIMARY_DEAL, base().myTotal(5).build()).reasonKey());
		assertTrue(BetGates.primary("deal", V2Keys.PRIMARY_DEAL, base().myTotal(50).build()).enabled());
		assertEquals(V2Keys.REASON_BETS_OPEN_IN,
			BetGates.primary("deal", V2Keys.PRIMARY_DEAL, base().bettingOpen(false).phase(BetView.Phase.IDLE).secondsLeft(4).build()).reasonKey());
		assertEquals(V2Keys.REASON_WAITING_FOR,
			BetGates.primary("deal", V2Keys.PRIMARY_DEAL, base().bettingOpen(false).phase(BetView.Phase.ACTING).waitingFor("Alex").build()).reasonKey());
		assertEquals(V2Keys.REASON_NOTHING_TO_UNDO, BetGates.tool(BetGates.UNDO, base().build()).reasonKey());
		assertTrue(BetGates.tool(BetGates.UNDO, base().canUndo(true).myTotal(5).build()).enabled());
		assertEquals(V2Keys.REASON_ABOVE_MAX, BetGates.chip(500, base().myTotal(800).build()).reasonKey());
		Gate rebet = BetGates.betOrRebet("deal", V2Keys.PRIMARY_DEAL, base().rebet(true, 50).build());
		assertTrue(rebet.enabled());
		assertEquals(V2Keys.PRIMARY_REBET, rebet.labelKey());
		assertEquals(V2Keys.REASON_NOT_ENOUGH, BetGates.tool(BetGates.REBET, base().rebet(true, 50).balance(20).build()).reasonKey());
	}

	/** A sample single-spot flow (what a game lane writes) checked over every combination (§5.3). */
	@Test
	void sampleFlowIsNeverStuck() {
		Flow<BetView> flow = new Flow<>() {
			@Override
			public Gate primary(BetView v) {
				return BetGates.betOrRebet("deal", V2Keys.PRIMARY_DEAL, v);
			}

			@Override
			public BetView bet(BetView v) {
				return v;
			}
		};
		List<String> problems = new ArrayList<>();
		int n = 0;
		for (BetView.Phase phase : BetView.Phase.values()) {
			for (boolean seated : new boolean[] {false, true}) {
				for (boolean canSit : new boolean[] {false, true}) {
					for (long balance : new long[] {0, 5, 10, 1000, 5000}) {
						for (long bet : new long[] {0, 5, 50, 1000}) {
							for (boolean open : new boolean[] {false, true}) {
								for (int secs : new int[] {-1, 3}) {
									BetView v = BetView.builder().seated(seated).canSit(canSit).phase(phase).bettingOpen(open).limits(10, 1000)
										.balance(balance).myTotal(seated ? bet : 0).canUndo(bet > 0).canClear(bet > 0).secondsLeft(secs).build();
									for (String p : FlowCheck.problems(flow, v)) problems.add(v + ": " + p);
									n++;
								}
							}
						}
					}
				}
			}
		}
		assertTrue(n > 1000);
		// the only legitimately stuck views: seated, betting open, broke — they must still say why (not enough chips)
		problems.removeIf(p -> p.contains("balance=0") && p.contains("not a waiting reason 'gui.burmaldaholic.core.v2.reason.not_enough_min'"));
		assertEquals(List.of(), problems.stream().limit(5).toList());
	}

	@Test
	void flowCheckCatchesTheV011Bug() {
		// v0.1.1: seated, betting open, chips in pocket, but no chip could be clicked
		Flow<BetView> broken = new Flow<>() {
			@Override
			public Gate primary(BetView v) {
				return Gate.off("deal", V2Keys.PRIMARY_DEAL, V2Keys.REASON_PLACE_BET);
			}

			@Override
			public BetView bet(BetView v) {
				return v;
			}

			@Override
			public Gate chip(long denom, BetView v) {
				return Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_BETS_CLOSED);
			}

			@Override
			public Gate tool(String id, BetView v) {
				return Gate.off(id, V2Keys.TOOL_UNDO, V2Keys.REASON_NOTHING_TO_UNDO);
			}
		};
		List<String> p = FlowCheck.problems(broken, base().build());
		assertFalse(p.isEmpty());
		assertTrue(p.stream().anyMatch(s -> s.contains("no chip is enabled")), p.toString());
	}

	@Test
	void freeSlotPrefersTheCentreAndSkipsWhenFull() {
		Box region = new Box(0, 0, 200, 60);
		assertEquals(Optional.of(new Box(50, 20, 100, 20)), FreeSlot.first(List.of(region), 100, 20, List.of()));
		Optional<Box> around = FreeSlot.first(List.of(region), 100, 20, List.of(new Box(90, 25, 20, 10)));
		assertTrue(around.isPresent());
		assertFalse(around.get().intersects(new Box(90, 25, 20, 10)));
		assertTrue(around.get().inside(region));
		assertEquals(Optional.empty(), FreeSlot.first(List.of(region), 100, 20, List.of(region)));
		assertEquals(Optional.empty(), FreeSlot.first(List.of(region), 300, 20, List.of()));
		Box second = new Box(0, 100, 200, 40);
		assertEquals(second.y() + 10, FreeSlot.first(List.of(region, second), 100, 20, List.of(region)).orElseThrow().y());
	}

	@Test
	void tagPlacerNeverCoversHardBoxesInStrictMode() {
		Box bounds = new Box(0, 0, 100, 100);
		List<Box> hard = List.of(new Box(0, 0, 100, 40), new Box(0, 60, 100, 40));
		List<Box> tags = TagPlacer.place(hard, List.of(), bounds, List.of(new TagPlacer.Request(30, 10, 10, 10), new TagPlacer.Request(30, 10, 10, 10)));
		assertFalse(tags.get(0).isEmpty());
		for (Box h : hard) assertFalse(tags.get(0).intersects(h));
		assertFalse(tags.get(1).isEmpty());
		assertFalse(tags.get(1).intersects(tags.get(0)));
		List<Box> full = TagPlacer.place(List.of(bounds), List.of(), bounds, List.of(new TagPlacer.Request(30, 10, 10, 10)));
		assertTrue(full.get(0).isEmpty(), "nothing free: no tag (the status line carries it)");
		List<Box> best = TagPlacer.place(List.of(bounds), List.of(), bounds, List.of(new TagPlacer.Request(30, 10, 10, 10)), TagPlacer.Mode.BEST_EFFORT);
		assertEquals(new Box(10, 10, 30, 10), best.get(0));
	}

	@Test
	void watchdogFiresOnceWithoutAnEcho() {
		AckWatchdog w = new AckWatchdog();
		w.state(3);
		w.sent("bet", "", 25);
		assertTrue(w.pending());
		w.state(3); // a state from before the action (e.g. a timer sync)
		assertTrue(w.pending());
		w.state(4);
		assertFalse(w.pending(), "the echo clears the pending chip");
		w.sent("bet", "red", 5);
		int fired = 0;
		for (int i = 0; i < AckWatchdog.TIMEOUT_TICKS + 10; i++) if (w.tick()) fired++;
		assertEquals(1, fired);
		assertFalse(w.pending());
		w.sent("bet", "", 5);
		w.error();
		assertFalse(w.pending());
		AckWatchdog legacy = new AckWatchdog();
		legacy.sent("spin", "", 0);
		legacy.state(-1);
		assertFalse(legacy.pending(), "a state without ack answers anything");
	}

	@Test
	void standardKeys() {
		KeyMap k = KeyMap.standard();
		assertEquals(Optional.of("primary"), k.idFor(KeyMap.SPACE));
		assertEquals(Optional.of("chip.0"), k.idFor(KeyMap.KEY_1));
		assertEquals(Optional.of("chip.4"), k.idFor(KeyMap.KEY_5));
		assertEquals(Optional.of("tool.undo"), k.idFor(KeyMap.BACKSPACE));
		assertEquals(Optional.of("tool.clear"), k.idFor(KeyMap.DELETE));
		assertEquals(Optional.of("tool.rebet"), k.idFor(KeyMap.R));
		assertEquals(Optional.of("tool.double"), k.idFor(KeyMap.X));
		k.bind(KeyMap.H, "action.hit").bind(KeyMap.R, "action.raise");
		assertEquals(KeyMap.H, k.keyOf("action.hit"));
		assertEquals(-1, k.keyOf("tool.rebet"), "a game key replaces a kit key");
	}

	@Test
	void stampLevels() {
		assertArrayEquals(new int[] {2 * 40 + 12, 48}, Stamps.levels(40, true));
		assertArrayEquals(new int[] {48}, Stamps.levels(40, false));
		assertEquals(2, Stamps.scaleOf(0, true));
		assertEquals(1, Stamps.scaleOf(1, true));
		assertEquals(0, Stamps.scaleOf(2, true));
		assertEquals(0, Stamps.scaleOf(1, false));
		assertEquals(Stamps.Kind.GOLD, Stamps.Outcome.BLACKJACK.kind);
		assertEquals(Stamps.Outcome.LOSE, Stamps.Outcome.ofNet(-5));
	}

	@Test
	void everyKitKeyExistsInEnglishAndRussian() throws Exception {
		Path dir = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/main/lang/core");
		for (String lang : new String[] {"en_us", "ru_ru"}) {
			String json = Files.readString(dir.resolve(lang + ".json"), StandardCharsets.UTF_8);
			for (String key : V2Keys.ALL) assertTrue(json.contains("\"" + key + "\""), lang + " lacks " + key);
		}
	}
}
