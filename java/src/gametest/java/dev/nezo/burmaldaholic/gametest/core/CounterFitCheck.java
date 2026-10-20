package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.cashier.CounterShell;
import dev.nezo.burmaldaholic.client.cashier.StoreTab;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterLayout;
import dev.nezo.burmaldaholic.core.store.net.StoreSyncPayload.OfferView;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * T-EC1: deterministic layout asserts for the counter (no screenshots): every hit target inside the panel and the
 * viewport, no two targets overlapping, and the labels drawn in them fit WITHOUT ellipsis (RU included).
 */
final class CounterFitCheck {
	private CounterFitCheck() {}

	/** Problems found on the shell's active tab (empty = all fits). Call on the client thread. */
	static List<String> check(Minecraft mc, CounterShell shell) {
		List<String> bad = new ArrayList<>();
		Font font = mc.font;
		CounterLayout.Shell l = shell.layout();
		var vp = shell.viewport();
		Box screen = new Box(0, 0, vp.w(), vp.h());
		if (!inside(l.panel(), screen)) bad.add("panel " + l.panel() + " outside viewport " + screen);
		List<HitMap.Target> ts = shell.hits().targets();
		for (HitMap.Target t : ts) {
			if (t.box().w() <= 0 || t.box().h() <= 0) continue; // hidden targets (e.g. no tray when narrow)
			if (!inside(t.box(), l.panel())) bad.add(t.id() + " " + t.box() + " outside panel " + l.panel());
		}
		for (int i = 0; i < ts.size(); i++) {
			for (int j = i + 1; j < ts.size(); j++) {
				Box a = ts.get(i).box(), b = ts.get(j).box();
				if (a.w() <= 0 || b.w() <= 0) continue;
				if (overlap(a, b)) bad.add("overlap " + ts.get(i).id() + " " + a + " / " + ts.get(j).id() + " " + b);
			}
		}
		// labels
		for (int i = 0; i < shell.tabs().size(); i++) fit(bad, font, "tab " + shell.tabs().get(i).id(), shell.tabs().get(i).title(), l.tabs().get(i), 4);
		fit(bad, font, "readout", Component.translatable("gui.burmaldaholic.cashier.readout", Texts.number(1_000_000), Texts.number(1_000_000)), l.readout(), 0);
		String active = shell.activeTab() == null ? "" : shell.activeTab().id();
		if (active.equals("exchange")) {
			label(bad, font, shell, "exchange.deposit_all", "gui.burmaldaholic.cashier.deposit_all");
			label(bad, font, shell, "exchange.deposit_held", "gui.burmaldaholic.cashier.deposit_held");
			label(bad, font, shell, "exchange.max", "gui.burmaldaholic.common.max");
			label(bad, font, shell, "exchange.clear", "gui.burmaldaholic.cashier.clear");
			label(bad, font, shell, "exchange.withdraw", "gui.burmaldaholic.cashier.withdraw");
			for (HitMap.Target t : ts) {
				String id = t.id();
				if (!id.startsWith("exchange.row.")) continue;
				String key = id.endsWith(".1") ? "gui.burmaldaholic.cashier.qty.x1" : id.endsWith(".64") ? "gui.burmaldaholic.cashier.qty.x64"
					: id.endsWith(".all") ? "gui.burmaldaholic.cashier.exchange.all" : "gui.burmaldaholic.cashier.exchange.max";
				fit(bad, font, id, Component.translatable(key), t.box(), 2);
				if (id.endsWith(".in.1") || id.endsWith(".out.1")) { // the rate cell left of x1
					Box rate = new Box(t.box().x() - CounterLayout.GAP - CounterLayout.RATE_W, t.box().y(), CounterLayout.RATE_W, t.box().h());
					String k = id.endsWith(".in.1") ? "gui.burmaldaholic.cashier.exchange.in" : "gui.burmaldaholic.cashier.exchange.out";
					fit(bad, font, id + " rate", Component.translatable(k, Texts.number(99)), rate, 0);
				}
			}
		} else if (active.equals("store")) {
			StoreTab st = (StoreTab) shell.activeTab();
			int cats = (int) ts.stream().filter(t -> t.id().startsWith("store.cat.")).count();
			CounterLayout.Store sl = CounterLayout.store(l.body(), cats);
			CounterLayout.Detail d = CounterLayout.detail(sl);
			for (HitMap.Target t : ts) {
				if (t.id().startsWith("store.cat.")) {
					String c = t.id().substring("store.cat.".length());
					fit(bad, font, t.id(), Component.translatable("gui.burmaldaholic.cashier.store.category." + c), t.box(), 4);
				}
			}
			fit(bad, font, "search placeholder", Component.translatable("gui.burmaldaholic.cashier.store.search"), sl.search(), 3);
			String[] q = {"1", "16", "64", "max"};
			String[] qk = {"qty.x1", "qty.x16", "qty.x64", "exchange.max"};
			for (int i = 0; i < 4; i++) label(bad, font, shell, "store.qty." + q[i], "gui.burmaldaholic.cashier." + qk[i]);
			label(bad, font, shell, "store.buy", "gui.burmaldaholic.cashier.store.buy");
			Optional<Box> buy = shell.hits().box("store.buy");
			buy.ifPresent(b -> fit(bad, font, "confirm 99,999", Component.translatable("gui.burmaldaholic.cashier.store.confirm", Texts.number(99_999)), b, 2));
			OfferView o = st.selectedOffer();
			if (o != null) {
				fit(bad, font, "price line", Component.translatable("gui.burmaldaholic.cashier.store.price", Texts.chips(o.price())), d.line2(), 0);
				fit(bad, font, "total line", Component.translatable("gui.burmaldaholic.cashier.store.total", Texts.chips(99_999)), d.total(), 0);
				if (!d.line3().isEmpty()) {
					fit(bad, font, "limit line", Component.translatable("gui.burmaldaholic.cashier.store.limit_day", Texts.number(16), Texts.number(16)), d.line3(), 0);
					fit(bad, font, "limit_days line", Component.translatable("gui.burmaldaholic.cashier.store.limit_days", Texts.number(7)), d.line3(), 0);
				}
			}
			for (HitMap.Target t : ts) {
				if (!t.id().startsWith("store.tile.")) continue;
				fit(bad, font, t.id() + " badge", Component.translatable("gui.burmaldaholic.cashier.store.limit_day", Texts.number(16), Texts.number(16)),
					new Box(t.box().x() + 1, t.box().y(), t.box().w() - 2, 8), 0);
				break; // same width for every tile
			}
		}
		return bad;
	}

	private static void label(List<String> bad, Font font, CounterShell shell, String id, String key) {
		shell.hits().box(id).ifPresent(b -> fit(bad, font, id, Component.translatable(key), b, 2));
	}

	private static void fit(List<String> bad, Font font, String what, Component c, Box b, int pad) {
		if (b.w() <= 0) return;
		int w = font.width(c);
		if (w > b.w() - 2 * pad) bad.add(what + " '" + c.getString() + "' " + w + "px > " + (b.w() - 2 * pad) + "px");
	}

	private static boolean inside(Box a, Box outer) {
		return a.x() >= outer.x() && a.y() >= outer.y() && a.x() + a.w() <= outer.x() + outer.w() && a.y() + a.h() <= outer.y() + outer.h();
	}

	private static boolean overlap(Box a, Box b) {
		return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
	}
}
