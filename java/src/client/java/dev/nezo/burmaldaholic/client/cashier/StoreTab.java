package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.core.cashier.ExchangeMath;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterLayout;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.store.StoreCatalog;
import dev.nezo.burmaldaholic.core.store.net.StoreActionPayload;
import dev.nezo.burmaldaholic.core.store.net.StoreSyncPayload.OfferView;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Store tab (EC-SPEC §3): category rail with a search box, a 6 × 4 tile grid (4 columns when narrow), and the detail
 * panel (a bottom strip when narrow) with ×1 / ×16 / ×64 / Max, the total and Buy. A Max over 5,000 chips needs a second
 * click within 3 s ("Confirm N?"). Shift+click on a quantity button buys ×64 at once.
 * Keys: arrows move the selection, Enter ×1, Shift+Enter ×64, Ctrl+Enter Max, {@code /} focuses the search.
 *
 * <p>Targets: {@code store.search}, {@code store.cat.<category>}, {@code store.tile.<offer id>},
 * {@code store.qty.<1|16|64|max>}, {@code store.buy}.
 */
public final class StoreTab implements CounterTab {
	public static final String ID = "store";
	public static final String ALL = "all";
	public static final long CONFIRM_OVER = 5000;
	public static final long CONFIRM_MS = 3000;
	private static final int[] QTY = {1, 16, 64, 0};
	private static final String[] QTY_ID = {"1", "16", "64", "max"};

	private final CounterShell shell;
	private String category = ALL;
	private String search = "";
	private boolean searchFocused;
	private int selected;
	private int scrollRow;
	private int qtyIdx;
	private long confirmUntil;
	private CounterLayout.@Nullable Store layout;
	private CounterLayout.@Nullable Detail detail;
	private List<OfferView> shown = List.of();

	public StoreTab(CounterShell shell) {
		this.shell = shell;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("gui.burmaldaholic.cashier.tab.store");
	}

	@Override
	public void opened() {
		ClientStore.send(StoreActionPayload.sync());
	}

	// ---- state for tests -------------------------------------------------------------------------------------------

	public List<OfferView> shown() {
		return shown;
	}

	public @Nullable OfferView selectedOffer() {
		return selected >= 0 && selected < shown.size() ? shown.get(selected) : null;
	}

	public int qty() {
		return QTY[qtyIdx];
	}

	public boolean searchFocused() {
		return searchFocused;
	}

	public String search() {
		return search;
	}

	public boolean confirming() {
		return Util.getMillis() < confirmUntil;
	}

	// ---- layout ----------------------------------------------------------------------------------------------------

	private List<String> categories() {
		List<String> c = new ArrayList<>();
		c.add(ALL);
		c.addAll(StoreCatalog.CATEGORIES);
		return c;
	}

	@Override
	public void layout(Box body, HitMap hits) {
		String q = search.toLowerCase(Locale.ROOT);
		List<OfferView> f = new ArrayList<>();
		for (OfferView o : ClientStore.offers()) {
			if (!category.equals(ALL) && !o.category().equals(category)) continue;
			if (!q.isEmpty() && !o.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
			f.add(o);
		}
		shown = List.copyOf(f);
		selected = shown.isEmpty() ? -1 : Math.max(0, Math.min(selected, shown.size() - 1));
		List<String> cats = categories();
		CounterLayout.Store l = CounterLayout.store(body, cats.size());
		layout = l;
		detail = CounterLayout.detail(l);
		if (selected >= 0) {
			int row = selected / l.cols();
			if (row < scrollRow) scrollRow = row;
			if (row >= scrollRow + l.rows()) scrollRow = row - l.rows() + 1;
		} else {
			scrollRow = 0;
		}
		hits.add("store.search", l.search(), true);
		for (int i = 0; i < l.categories().size() && i < cats.size(); i++) hits.add("store.cat." + cats.get(i), l.categories().get(i), true);
		int first = scrollRow * l.cols();
		for (int v = 0; v < l.cols() * l.rows() && first + v < shown.size(); v++) {
			hits.add("store.tile." + shown.get(first + v).id(), l.tile(v), true);
		}
		OfferView sel = selectedOffer();
		boolean can = sel != null && sel.lockReason().isEmpty();
		for (int i = 0; i < 4; i++) hits.add("store.qty." + QTY_ID[i], detail.qty().get(i), can);
		hits.add("store.buy", detail.buy(), can);
	}

	// ---- input -----------------------------------------------------------------------------------------------------

	@Override
	public boolean onTarget(String id, boolean enabled, Press press, int mods) {
		if (!press.left()) return false;
		if (id.equals("store.search")) {
			searchFocused = true;
			return true;
		}
		searchFocused = false;
		if (id.startsWith("store.cat.")) {
			category = id.substring("store.cat.".length());
			selected = 0;
			scrollRow = 0;
		} else if (id.startsWith("store.tile.")) {
			String oid = id.substring("store.tile.".length());
			for (int i = 0; i < shown.size(); i++) if (shown.get(i).id().equals(oid)) selected = i;
			confirmUntil = 0;
		} else if (id.startsWith("store.qty.")) {
			if (!enabled) return true;
			if (Inputs.shift(mods)) {
				buy(64, false); // EC-SPEC §1: Shift+click on any quantity button sends ×64
			} else {
				String q = id.substring("store.qty.".length());
				for (int i = 0; i < QTY_ID.length; i++) if (QTY_ID[i].equals(q)) qtyIdx = i;
				confirmUntil = 0;
			}
		} else if (id.equals("store.buy")) {
			if (enabled) buySelected();
		} else {
			return false;
		}
		shell.relayout();
		return true;
	}

	/** Buys with the selected quantity; Max over {@link #CONFIRM_OVER} asks for a second press within 3 s. */
	private void buySelected() {
		if (QTY[qtyIdx] == 0) {
			if (maxTotal() > CONFIRM_OVER && !confirming()) {
				confirmUntil = Util.getMillis() + CONFIRM_MS;
				return;
			}
			confirmUntil = 0;
			buy(0, true);
		} else {
			buy(QTY[qtyIdx], false);
		}
	}

	private void buy(int qty, boolean all) {
		OfferView o = selectedOffer();
		if (o == null) return;
		ClientStore.send(StoreActionPayload.buy(o.id(), qty, all));
	}

	/** Client estimate of a Max buy (the server decides): {@code min(balance / price, left, MAX_UNITS) × price}. */
	public long maxTotal() {
		OfferView o = selectedOffer();
		if (o == null || o.price() <= 0) return 0;
		long n = Math.min(shell.host().balance() / o.price(), ExchangeMath.MAX_UNITS);
		if (o.limitLeft() >= 0) n = Math.min(n, o.limitLeft());
		return n * o.price();
	}

	@Override
	public boolean key(int keyCode, int mods) {
		if (searchFocused) {
			if (keyCode == Inputs.KEY_BACKSPACE) {
				if (!search.isEmpty()) search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
			} else if (keyCode == Inputs.KEY_RETURN || keyCode == Inputs.KEY_NUMPADENTER || keyCode == Inputs.KEY_TAB) {
				searchFocused = false;
			} else if (keyCode == Inputs.KEY_ESCAPE) {
				return false;
			}
			shell.relayout();
			return true; // typing never switches tabs or closes the screen
		}
		if (keyCode == Inputs.KEY_SLASH) {
			searchFocused = true;
			shell.pressed("store.search");
			return true;
		}
		CounterLayout.Store l = layout;
		int cols = l == null ? 6 : l.cols();
		int move = keyCode == Inputs.KEY_LEFT ? -1 : keyCode == Inputs.KEY_RIGHT ? 1 : keyCode == Inputs.KEY_UP ? -cols : keyCode == Inputs.KEY_DOWN ? cols : 0;
		if (move != 0) {
			if (!shown.isEmpty()) selected = Math.max(0, Math.min(shown.size() - 1, selected + move));
			confirmUntil = 0;
			shell.relayout();
			return true;
		}
		if (keyCode == Inputs.KEY_RETURN || keyCode == Inputs.KEY_NUMPADENTER) {
			OfferView o = selectedOffer();
			if (o == null || !o.lockReason().isEmpty()) return true;
			if (Inputs.ctrl(mods)) {
				qtyIdx = 3;
				shell.pressed("store.qty.max");
				buySelected();
			} else {
				int q = Inputs.shift(mods) ? 64 : 1;
				shell.pressed("store.qty." + q);
				buy(q, false);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(int codePoint) {
		if (!searchFocused || Character.isISOControl(codePoint) || search.length() >= 24) return searchFocused;
		search += new String(Character.toChars(codePoint));
		selected = 0;
		shell.relayout();
		return true;
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------

	@Override
	public void render(GuiGraphicsExtractor g, Box body, int mouseX, int mouseY, float partial) {
		CounterLayout.Store l = layout;
		CounterLayout.Detail d = detail;
		if (l == null || d == null) return;
		Font font = shell.font();
		// search
		V2Draw.fill(g, l.search(), searchFocused ? 0xFF000000 : 0xC0000000);
		V2Draw.outline(g, l.search(), searchFocused ? CasinoPalette.V2_GOLD : CasinoPalette.V2_PANEL_HI);
		Component sText = search.isEmpty() && !searchFocused ? Component.translatable("gui.burmaldaholic.cashier.store.search")
			: Texts.raw(search + (searchFocused && (Util.getMillis() / 500) % 2 == 0 ? "_" : "")); // literal-ok: caret
		V2Draw.left(g, font, sText, l.search(), 3, 1, search.isEmpty() && !searchFocused ? CasinoPalette.V2_MUTED : CasinoPalette.V2_TEXT, false);
		List<String> cats = categories();
		for (int i = 0; i < l.categories().size() && i < cats.size(); i++) {
			String c = cats.get(i);
			Component label = Component.translatable(c.equals(ALL) ? "gui.burmaldaholic.cashier.store.category.all"
				: "gui.burmaldaholic.cashier.store.category." + c);
			CounterDraw.button(g, font, l.categories().get(i), label, c.equals(category), true, mouseX, mouseY, false);
		}
		// grid
		CounterDraw.panel(g, l.grid().inset(-2));
		int first = scrollRow * l.cols();
		OfferView hover = null;
		for (int v = 0; v < l.cols() * l.rows() && first + v < shown.size(); v++) {
			OfferView o = shown.get(first + v);
			Box t = l.tile(v);
			boolean sel = first + v == selected;
			V2Draw.fill(g, t, sel ? 0xFF4E287C : 0xFF22122F);
			V2Draw.outline(g, t, sel ? CasinoPalette.V2_GOLD : t.contains(mouseX, mouseY) ? CasinoPalette.V2_EDGE : CasinoPalette.V2_PANEL_HI);
			g.fakeItem(o.stack(), t.x() + (t.w() - 16) / 2, t.y() + 3);
			boolean locked = !o.lockReason().isEmpty();
			V2Draw.centred(g, font, Texts.raw(CounterDraw.compact(o.price())), new Box(t.x(), t.bottom() - 11, t.w(), 10), 1,
				locked ? CasinoPalette.V2_MUTED : CasinoPalette.V2_GOLD, false);
			if (o.lockReason().equals("vip") || o.lockReason().equals("limit")) {
				V2Draw.fill(g, new Box(t.x() + 1, t.y() + 1, 7, 7), CasinoPalette.V2_LOSE_FILL); // lock badge
			}
			if (o.limitPerDay() > 0) {
				Component badge = Texts.raw(Math.max(0, o.limitLeft()) + "/" + o.limitPerDay()); // literal-ok: n/day badge
				V2Draw.left(g, font, badge, new Box(t.x() + 1, t.y() + 18, t.w() - 2, 8), 0, 1, CasinoPalette.V2_MUTED, false);
			}
			if (t.contains(mouseX, mouseY)) hover = o;
		}
		if (shown.isEmpty()) V2Draw.centred(g, font, Component.translatable("gui.burmaldaholic.cashier.store.empty"), l.grid(), 1, CasinoPalette.V2_MUTED, false);
		// detail
		CounterDraw.panel(g, l.detail());
		OfferView o = selectedOffer();
		if (o != null) {
			g.fakeItem(o.stack(), d.icon().x(), d.icon().y());
			V2Draw.left(g, font, o.stack().getHoverName(), d.name(), 0, 1, CasinoPalette.V2_TEXT, false);
			V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.cashier.store.price", Texts.chips(o.price())), d.line2(), 0, 1, CasinoPalette.V2_GOLD,
				false);
			Component l3 = lockLine(o);
			if (l3 != null && !d.line3().isEmpty()) V2Draw.left(g, font, l3, d.line3(), 0, 1, CasinoPalette.V2_MUTED, false);
			String[] labels = {"gui.burmaldaholic.cashier.qty.x1", "gui.burmaldaholic.cashier.qty.x16", "gui.burmaldaholic.cashier.qty.x64",
				"gui.burmaldaholic.cashier.exchange.max"};
			boolean can = o.lockReason().isEmpty();
			for (int i = 0; i < 4; i++) CounterDraw.button(g, font, d.qty().get(i), Component.translatable(labels[i]), i == qtyIdx, can, mouseX, mouseY, false);
			long total = QTY[qtyIdx] == 0 ? maxTotal() : QTY[qtyIdx] * o.price();
			V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.cashier.store.total", Texts.chips(total)), d.total(), 0, 1, CasinoPalette.V2_TEXT, false);
			Component buy = confirming() ? Component.translatable("gui.burmaldaholic.cashier.store.confirm", Texts.number(total))
				: Component.translatable("gui.burmaldaholic.cashier.store.buy");
			CounterDraw.button(g, font, d.buy(), buy, true, can, mouseX, mouseY, false);
		}
		if (hover != null) g.setTooltipForNextFrame(font, hover.stack(), mouseX, mouseY);
	}

	private static @Nullable Component lockLine(OfferView o) {
		return switch (o.lockReason()) {
			case "vip" -> Component.translatable("gui.burmaldaholic.common.requires_vip", VipTiers.name(o.minTier()));
			case "limit" -> o.limitEveryDays() > 1 ? Component.translatable("gui.burmaldaholic.cashier.store.limit_days", Texts.number(o.limitEveryDays()))
				: Component.translatable("gui.burmaldaholic.cashier.store.limit_reached");
			default -> o.limitPerDay() > 0 ? Component.translatable("gui.burmaldaholic.cashier.store.limit_day", Texts.number(Math.max(0, o.limitLeft())),
				Texts.number(o.limitPerDay())) : null;
		};
	}
}
