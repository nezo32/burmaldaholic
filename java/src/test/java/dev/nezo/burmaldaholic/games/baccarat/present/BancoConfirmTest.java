package dev.nezo.burmaldaholic.games.baccarat.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.ui.v2.DockLayout;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.LayoutAudit;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/**
 * T-A2 R4: a big Banco needs a second press within 3 s; a small one goes at once. Client logic without a server or a
 * Minecraft client (the clock is a counter); plus the "Confirm Banco N?" label fits the primary at 427 × 240 in RU.
 */
class BancoConfirmTest {
	private final AtomicLong now = new AtomicLong(10_000);
	private final BancoConfirm c = new BancoConfirm(BaccaratFlow.INSTANCE, now::get);

	/** A chemmy punter with nothing drafted facing an open bank of {@code open}. */
	private static BaccaratView view(long open, long balance, long draft) {
		return new BaccaratView(true, true, true, "betting", 1, 50_000, 0, balance, new long[0], false, false, true, 20, false, draft, false,
			false, false, true, open, 0, false, 20, "Bot", false);
	}

	@Test
	void threshold() {
		assertFalse(BancoConfirm.needsConfirm(100, 10_000), "1 % of the balance");
		assertFalse(BancoConfirm.needsConfirm(1_000, 10_000), "exactly 10 %, exactly 1000");
		assertTrue(BancoConfirm.needsConfirm(1_001, 1_000_000), "over 1000 chips");
		assertTrue(BancoConfirm.needsConfirm(101, 1_000), "over 10 % of the balance");
	}

	@Test
	void smallBancoGoesAtOnce() {
		BaccaratView v = view(100, 10_000, 0);
		Gate g = c.primary(v);
		assertEquals("banco", g.action());
		assertEquals(BaccaratFlow.BAC2 + "banco", g.labelKey());
		assertTrue(c.press(g, v.balance()), "1 % of the balance: one press sends");
		assertFalse(c.armed());
	}

	@Test
	void bigBancoNeedsASecondPressWithinTheWindow() {
		BaccaratView v = view(2_000, 5_000, 0);
		Gate first = c.primary(v);
		assertFalse(c.press(first, v.balance()), "first press only arms");
		Gate armed = c.primary(v);
		assertEquals("banco", armed.action());
		assertEquals(BancoConfirm.CONFIRM_KEY, armed.labelKey());
		assertEquals(2_000L, armed.labelArgs()[0]);
		now.addAndGet(BancoConfirm.WINDOW_MS - 1);
		assertFalse(c.expired());
		assertTrue(c.press(c.primary(v), v.balance()), "second press inside 3 s sends");
		assertEquals(BaccaratFlow.BAC2 + "banco", c.primary(v).labelKey(), "sent: back to the plain label");
		assertFalse(c.press(c.primary(v), v.balance()), "the next big Banco asks again");
	}

	@Test
	void armedBancoExpiresAndAnyChangeDisarms() {
		BaccaratView v = view(2_000, 5_000, 0);
		assertFalse(c.press(c.primary(v), v.balance()));
		now.addAndGet(BancoConfirm.WINDOW_MS);
		assertTrue(c.expired(), "3 s passed: expired once");
		assertFalse(c.expired());
		assertEquals(BaccaratFlow.BAC2 + "banco", c.primary(v).labelKey());
		assertFalse(c.press(c.primary(v), v.balance()), "after expiry the press arms again, it never sends");

		// the open bank changed (another punter, a new coup): ask again
		BaccaratView bigger = view(3_000, 5_000, 0);
		assertEquals(BaccaratFlow.BAC2 + "banco", c.primary(bigger).labelKey());
		assertFalse(c.press(c.primary(bigger), bigger.balance()), "a different stake re-arms instead of sending");

		// a chip drafted: the primary is the punt, the Banco is disarmed
		BaccaratView drafted = view(3_000, 5_000, 25);
		Gate punt = c.primary(drafted);
		assertEquals("punt", punt.action());
		assertFalse(c.armed());
		assertTrue(c.press(punt, drafted.balance()), "a punt is never held");
		assertFalse(c.press(c.primary(bigger), bigger.balance()), "back to nothing drafted: Banco asks from scratch");
	}

	@Test
	void otherGatesDelegate() {
		BaccaratView v = view(2_000, 5_000, 0);
		assertEquals(BaccaratFlow.INSTANCE.actions(v), c.actions(v));
		assertEquals(BaccaratFlow.INSTANCE.chip(25, v).enabled(), c.chip(25, v).enabled());
		assertEquals(BaccaratFlow.INSTANCE.tool("undo", v).reasonKey(), c.tool("undo", v).reasonKey());
		assertEquals(BaccaratFlow.INSTANCE.bet(v), c.bet(v));
	}

	/** A vanilla-like advance width (same as the kit's StatusFitTest). */
	private static final ToIntFunction<String> FONT = s -> {
		int w = 0;
		for (int i = 0; i < s.length(); i = s.offsetByCodePoints(i, 1)) {
			int ch = s.codePointAt(i);
			w += ch == ' ' ? 4 : "i!.,:;|'".indexOf(ch) >= 0 ? 2 : "l`".indexOf(ch) >= 0 ? 3 : "tI[]".indexOf(ch) >= 0 ? 4 : "fk<>\"()*{}".indexOf(ch) >= 0 ? 5 : 6;
		}
		return w;
	};

	@Test
	void confirmLabelFitsThePrimaryAt427x240() throws Exception {
		Path dir = Path.of(System.getProperty("burmaldaholic.projectDir", "."), "src/main/lang/baccarat");
		for (String lang : new String[] {"ru_ru", "en_us"}) {
			JsonObject j = JsonParser.parseString(Files.readString(dir.resolve(lang + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
			assertTrue(j.has(BancoConfirm.CONFIRM_KEY), lang + " lacks " + BancoConfirm.CONFIRM_KEY);
			String label = j.get(BancoConfirm.CONFIRM_KEY).getAsString().replace("%1$s", "99 999 999");
			TableGrid g = TableGrid.of(427, 240);
			int readout = Math.max(FONT.applyAsInt("Ставка"), 2 * FONT.applyAsInt("12 500"));
			int readoutC = Math.max(FONT.applyAsInt("Ставка"), FONT.applyAsInt("12 500"));
			int compact = FONT.applyAsInt(label) + 16;
			int full = compact + 5 + FONT.applyAsInt("Пробел") + 6;
			int want = FONT.applyAsInt("Сначала сделайте ставку") + 2;
			DockLayout.Dock d = DockLayout.layout(g, DockLayout.Spec.betting(BaccaratView.DENOMS.length, 4, readout, readoutC, 0, full, compact)
				.withStatus(want), 2);
			String where = lang + " \"" + label + "\" at 427x240";
			assertEquals(0, d.overflow(), where + ": the Dock overflows");
			assertTrue(d.primary().w() >= (d.compactPrimary() ? compact : full), where + ": primary " + d.primary().w() + " px");
			LayoutAudit a = new LayoutAudit(g.dock).add("status", d.status()).add("primary", d.primary());
			if (d.readout() != null && d.readout().w() > 0) a.add("readout", d.readout());
			for (int i = 0; i < d.tools().size(); i++) a.add("tool" + i, d.tools().get(i));
			assertEquals(List.of(), a.problems(), where);
		}
	}
}
