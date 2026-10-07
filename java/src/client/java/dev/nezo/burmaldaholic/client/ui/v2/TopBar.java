package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The slim bar on top of every v2 screen (REDESIGN §4): left the title pill (gold), the limits pill and the seats pill
 * (muted); right the icons Settings / Rules / Leave (ids {@code top.settings}, {@code top.rules}, {@code top.leave}) and
 * the {@link BalancePill} with its delta pill. Pills that do not fit are dropped from the right of the left group
 * (seats, then limits); the title is ellipsised; icons and balance always stay.
 */
public final class TopBar {
	public final BalancePill balance = new BalancePill();
	private Component title = Component.empty();
	private @Nullable Component limits, seats;
	private boolean rules = true, settings = true, leave = true;
	private Box titleBox = Box.EMPTY, limitsBox = Box.EMPTY, seatsBox = Box.EMPTY;
	private final Map<String, Box> icons = new LinkedHashMap<>();
	private Box bar = Box.EMPTY;
	private int balanceScale = 1;

	/** Title, limits ({@code max} 0 = no limits pill) and seats ({@code seats} 0 = no seats pill). */
	public void set(Component title, long min, long max, int seated, int seats) {
		this.title = title;
		this.limits = max > 0 ? Component.translatable(V2Keys.LIMITS, Texts.number(min), Texts.number(max)) : null;
		this.seats = seats > 0 ? Component.translatable(V2Keys.SEATS, Texts.number(seated), Texts.number(seats)) : null;
	}

	/** Which icons the screen offers (default all three; Leave is always there while seated at a table). */
	public void icons(boolean rules, boolean settings, boolean leave) {
		this.rules = rules;
		this.settings = settings;
		this.leave = leave;
	}

	public Component title() {
		return title;
	}

	/** Lays the bar out and registers the icon targets. */
	void layout(TableGrid grid, Font font, HitMap hits) {
		bar = grid.topBar;
		Tier t = grid.tier;
		int m = t.margin;
		int ph = t == Tier.T3 ? 18 : 14;
		int py = bar.y() + (bar.h() - ph) / 2;
		balanceScale = t == Tier.T3 ? 2 : 1;
		int bh = t == Tier.T3 ? 22 : ph;
		int bw = balance.measure(font, balanceScale);
		Box bal = new Box(bar.right() - m - bw, bar.y() + (bar.h() - bh) / 2, bw, bh);
		int x = bal.x() - 4;
		int dw = balance.measureDelta(font);
		Box delta = Box.EMPTY;
		if (dw > 0) {
			delta = new Box(x - dw, py, dw, ph);
			x = delta.x() - 4;
		}
		balance.place(bal, delta);
		icons.clear();
		int is = ph;
		int pitch = is + 2;
		String[] ids = {KeyMap.Ids.TOP_SETTINGS, KeyMap.Ids.TOP_RULES, KeyMap.Ids.TOP_LEAVE};
		boolean[] on = {settings, rules, leave};
		int count = 0;
		for (boolean b : on) if (b) count++;
		int ix = x - count * pitch + 2;
		for (int i = 0; i < ids.length; i++) {
			if (!on[i]) continue;
			Box b = new Box(ix, py, is, is);
			icons.put(ids[i], b);
			ix += pitch;
		}
		int iconsLeft = count == 0 ? x : x - count * pitch + 2;
		int lx = m;
		int right = iconsLeft - 6;
		int tw = font.width(title) + 10;
		int lw = limits == null ? 0 : font.width(limits) + 8;
		int sw = seats == null ? 0 : font.width(seats) + 8;
		boolean showSeats = sw > 0 && lx + tw + 4 + lw + 4 + sw <= right;
		boolean showLimits = lw > 0 && lx + tw + 4 + lw <= right;
		int titleW = Math.max(0, Math.min(tw, right - lx));
		titleBox = new Box(lx, py, titleW, ph);
		lx = titleBox.right() + 4;
		limitsBox = showLimits ? new Box(lx, py, lw, ph) : Box.EMPTY;
		if (showLimits) lx = limitsBox.right() + 4;
		seatsBox = showSeats ? new Box(lx, py, sw, ph) : Box.EMPTY;
		for (Map.Entry<String, Box> e : icons.entrySet()) hits.add(e.getKey(), e.getValue(), true);
	}

	public @Nullable Box icon(String id) {
		return icons.get(id);
	}

	public void draw(GuiGraphicsExtractor g, Font font, int mx, int my, String focusedId) {
		if (bar.isEmpty()) return;
		if (!V2Draw.sprite(g, V2Sprites.TOPBAR, bar, 0xFFFFFFFF)) {
			V2Draw.fill(g, bar, CasinoPalette.V2_INK);
			g.fill(bar.x(), bar.bottom() - 2, bar.right(), bar.bottom() - 1, CasinoPalette.V2_GOLD_SHADE);
			g.fill(bar.x(), bar.bottom() - 1, bar.right(), bar.bottom(), CasinoPalette.V2_RAIL_DARK);
		}
		if (!titleBox.isEmpty()) {
			V2Draw.sprite(g, V2Sprites.TITLE_PILL, titleBox, CasinoPalette.V2_PANEL, CasinoPalette.V2_PANEL_HI);
			V2Draw.centred(g, font, title, titleBox.inset(1), 1, CasinoPalette.V2_GOLD, true);
		}
		for (var e : new Object[][] {{limitsBox, limits}, {seatsBox, seats}}) {
			Box b = (Box) e[0];
			if (b.isEmpty() || e[1] == null) continue;
			V2Draw.sprite(g, V2Sprites.INFO_PILL, b, CasinoPalette.V2_PANEL, CasinoPalette.V2_PANEL_HI);
			V2Draw.centred(g, font, (Component) e[1], b, 1, CasinoPalette.V2_MUTED, true);
		}
		for (Map.Entry<String, Box> e : icons.entrySet()) {
			Box b = e.getValue();
			boolean hover = b.contains(mx, my);
			String name = e.getKey().substring(4);
			if (!V2Draw.sprite(g, V2Sprites.ButtonStyle.ICON.sprite(true, hover, false), b, 0xFFFFFFFF)) {
				V2Button.flat(g, b, V2Sprites.ButtonStyle.ICON, true, hover);
			}
			Box gl = b.centred(b.w() - 4, b.h() - 4);
			if (!V2Draw.sprite(g, V2Sprites.icon(name), gl, 0xFFFFFFFF)) Glyphs.draw(g, name, gl, CasinoPalette.V2_TEXT);
			if (e.getKey().equals(focusedId)) V2Draw.outline(g, b.inset(-1), CasinoPalette.V2_GOLD);
		}
		balance.draw(g, font, balanceScale);
	}
}
