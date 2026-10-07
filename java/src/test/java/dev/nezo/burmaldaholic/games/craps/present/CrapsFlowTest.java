package dev.nezo.burmaldaholic.games.craps.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.games.craps.present.CrapsFlow.Click;
import dev.nezo.burmaldaholic.games.craps.present.CrapsView.BetPub;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/**
 * The craps v2 bet flow, exhaustively (ui-v2.md §5.3): over seated × seat free × balance × point × shooter × line bet ×
 * window × rolling, (a) something can be done or the screen names who / what it waits for, (b) a disabled gate always
 * has a reason, (c) seated with chips ≥ min and an open bet ⇒ the chips are enabled; every felt click either sends an
 * action or names a reason that exists in EN and RU.
 */
class CrapsFlowTest {
	private static final long[] BALANCES = {0, 5, 10, 1000};

	private static CrapsView view(boolean seated, boolean seatFree, long balance, int point, int shooterKind, boolean line, boolean window,
			boolean rolling, boolean undo) {
		boolean youShoot = shooterKind == 1;
		String shooter = shooterKind == 0 ? "" : youShoot ? "Me" : "Alex";
		List<BetPub> bets = new ArrayList<>();
		if (line) bets.add(new BetPub(1, "pass", 10, 0, 0, true, point == 0 ? 1 : 2, 30, point == 0 ? 0 : 30, false));
		bets.add(new BetPub(-1, "field", 5, 0, 0, false, 1, 0, 0, false));
		List<String> allowed = new ArrayList<>();
		if (point == 0) {
			if (!line) allowed.add("pass");
			allowed.add("dont_pass");
		} else {
			allowed.add("come");
			allowed.add("dont_come");
		}
		allowed.add("field");
		boolean comeOut = point == 0;
		boolean canRoll = youShoot && (!comeOut || line) && !window && !rolling;
		boolean needLine = shooterKind != 0 && comeOut && !line && youShoot;
		return new CrapsView(seated, seatFree, balance, 10, 500, true, point, youShoot, canRoll, needLine, window, window ? 7 : -1, canRoll ? 12 : -1,
			shooter, undo, allowed, bets, rolling, seated ? 2 : 1);
	}

	@Test
	void neverStuck() {
		int n = 0;
		for (boolean seated : new boolean[] {false, true}) {
			for (boolean free : new boolean[] {false, true}) {
				for (long bal : BALANCES) {
					for (int point : new int[] {0, 8}) {
						for (int sh = 0; sh < 3; sh++) {
							for (boolean line : new boolean[] {false, true}) {
								for (boolean window : new boolean[] {false, true}) {
									for (boolean rolling : new boolean[] {false, true}) {
										for (boolean undo : new boolean[] {false, true}) {
											CrapsView v = view(seated, free, bal, point, sh, line, window, rolling, undo);
											check(v);
											n++;
										}
									}
								}
							}
						}
					}
				}
			}
		}
		assertTrue(n > 1000);
	}

	private static void check(CrapsView v) {
		Gate primary = CrapsFlow.primary(v);
		Gate chips = CrapsFlow.chips(v);
		List<Gate> all = List.of(primary, chips, CrapsFlow.tool("undo", v), CrapsFlow.tool("clear", v));
		for (Gate g : all) {
			assertTrue(g.enabled() || (g.reasonKey() != null && !g.reasonKey().isEmpty()), "reason for " + g.labelKey() + " in " + v);
			KEYS.add(g.labelKey());
			if (g.reasonKey() != null) KEYS.add(g.reasonKey());
		}
		boolean something = all.stream().anyMatch(Gate::enabled);
		boolean waits = !v.shooter().isEmpty() || v.windowSeconds() >= 0 || v.rolling() || !v.seatFree() || v.balance() < v.min();
		assertTrue(something || waits, "stuck: " + v);
		if (v.seated() && v.balance() >= v.min() && !v.allowed().isEmpty()) {
			assertTrue(chips.enabled(), "chips must be enabled: " + v);
		}
		if (!v.seated() && v.seatFree() && v.balance() >= v.min()) {
			assertTrue(primary.enabled() && primary.action().equals("sit"), "Sit down: " + v);
		}
		if (v.youShoot() && v.canRoll() && v.seated() && !v.rolling()) {
			assertTrue(primary.enabled() && primary.action().equals("roll"), "Roll: " + v);
		}
		var st = CrapsFlow.status(v);
		KEYS.add(st.line1().key());
		KEYS.add(st.line2().key());
		for (String spot : CrapsLayout.SPOT_IDS) {
			Click c = CrapsFlow.click(v, spot, 5);
			assertTrue(c.sends() || (c.errorKey() != null && !c.errorKey().isEmpty()), "click " + spot + " does nothing: " + v);
			if (!c.sends()) KEYS.add(c.errorKey());
			KEYS.add(CrapsFlow.hover(spot, v).key());
		}
	}

	private static final Set<String> KEYS = new HashSet<>();

	@Test
	void clicksPlaceFlatBetsOrOdds() {
		CrapsView comeOut = view(true, true, 1000, 0, 1, false, false, false, false);
		Click pass = CrapsFlow.click(comeOut, CrapsLayout.PASS, 25);
		assertEquals("bet", pass.action());
		assertEquals("pass", pass.kind());
		assertEquals(25, pass.amount());
		// the odds lane on the come-out without a Pass bet places the Pass bet (never nothing)
		assertEquals("pass", CrapsFlow.click(comeOut, CrapsLayout.PASS_ODDS, 5).kind());
		// a chip under the table minimum is raised to the minimum
		assertEquals(10, CrapsFlow.click(comeOut, CrapsLayout.FIELD, 1).amount());
		Click come = CrapsFlow.click(comeOut, CrapsLayout.COME, 5);
		assertFalse(come.sends());
		assertEquals(CrapsFlow.K + "reason.come_needs_point", come.errorKey());

		CrapsView point = view(true, true, 1000, 8, 1, true, false, false, false);
		Click odds = CrapsFlow.click(point, CrapsLayout.PASS_ODDS, 25);
		assertEquals("odds", odds.action());
		assertEquals(1, odds.betId());
		assertEquals(24, odds.amount(), "snapped to the unit of 2");
		// odds of one chip below the unit: one unit
		assertEquals(2, CrapsFlow.click(point, CrapsLayout.PASS, 1).amount());
		assertEquals("come", CrapsFlow.click(point, CrapsLayout.COME, 5).kind());
		Click p = CrapsFlow.click(point, CrapsLayout.pointId(6), 5);
		assertEquals(CrapsFlow.K + "reason.point_box", p.errorKey());
	}

	@Test
	void comeOddsOnTheirPointBox() {
		List<BetPub> bets = List.of(new BetPub(7, "come", 10, 0, 6, true, 5, 50, 50, false));
		CrapsView v = new CrapsView(true, true, 1000, 10, 500, true, 8, false, false, false, false, -1, -1, "Alex", false, List.of("come", "dont_come", "field"),
			bets, false, 2);
		Click c = CrapsFlow.click(v, CrapsLayout.pointId(6), 25);
		assertEquals("odds", c.action());
		assertEquals(7, c.betId());
		assertEquals(25, c.amount());
		Gate primary = CrapsFlow.primary(v);
		assertFalse(primary.enabled());
		assertEquals(CrapsFlow.K + "reason.waiting_for", primary.reasonKey());
		assertEquals("Alex", primary.reasonArgs()[0]);
	}

	@Test
	void readsTheServerState() {
		CompoundTag s = new CompoundTag();
		s.putInt("seat", 0);
		s.putInt("seat_count", 6);
		ListTag seats = new ListTag();
		seats.add(new CompoundTag());
		s.put("seats", seats);
		s.putLong("balance", 140);
		s.putLong("min", 1);
		s.putLong("max", 200);
		s.putInt("point", 8);
		s.putBoolean("you_shoot", true);
		s.putBoolean("can_roll", true);
		s.putString("shooter", "Me");
		CompoundTag allowed = new CompoundTag();
		allowed.putBoolean("come", true);
		allowed.putBoolean("pass", false);
		s.put("allowed", allowed);
		ListTag bets = new ListTag();
		CompoundTag b = new CompoundTag();
		b.putInt("id", 3);
		b.putString("kind", "pass");
		b.putLong("flat", 50);
		b.putLong("odds", 75);
		b.putBoolean("mine", true);
		b.putLong("room", 175);
		b.putInt("unit", 5);
		bets.add(b);
		s.put("bets", bets);
		CrapsView v = CrapsView.read(s, 9, 18, false);
		assertTrue(v.seated() && v.seatFree() && v.youShoot() && v.canRoll());
		assertEquals(125, v.myTotal());
		assertEquals(List.of("come"), v.allowed());
		assertEquals(-1, v.windowSeconds(), "no window flag: no window seconds");
		assertEquals(18, v.rollSeconds());
		assertEquals(1, v.oddsTargets().size());
		Gate primary = CrapsFlow.primary(v);
		assertTrue(primary.enabled());
		assertEquals("roll", primary.action());
	}

	@Test
	void everyKeyExistsInEnglishAndRussian() throws Exception {
		neverStuck();
		Path dir = langDir();
		String en = Files.readString(dir.resolve("craps/en_us.json")) + Files.readString(dir.resolve("core/en_us.json"));
		String ru = Files.readString(dir.resolve("craps/ru_ru.json")) + Files.readString(dir.resolve("core/ru_ru.json"));
		assertFalse(KEYS.isEmpty());
		for (String k : KEYS) {
			assertTrue(en.contains("\"" + k + "\""), "EN missing " + k);
			assertTrue(ru.contains("\"" + k + "\""), "RU missing " + k);
		}
	}

	private static Path langDir() {
		String root = System.getProperty("burmaldaholic.projectDir", ".");
		for (Path p : new Path[] {Path.of(root, "src/main/lang"), Path.of("src/main/lang"), Path.of("java/src/main/lang")}) {
			if (Files.isDirectory(p)) return p;
		}
		throw new IllegalStateException("src/main/lang not found");
	}

	@Test
	void allGatesHaveLabels() {
		CrapsView v = view(true, true, 1000, 0, 1, true, false, false, true);
		assertNotNull(CrapsFlow.primary(v).labelKey());
	}
}
