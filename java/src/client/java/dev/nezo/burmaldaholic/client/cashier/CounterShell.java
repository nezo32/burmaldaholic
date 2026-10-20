package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.ui.v2.V2Button;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Input;
import dev.nezo.burmaldaholic.client.ui.v2.V2Sprites;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterLayout;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSource;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The counter screen's v2 shell (EC-SPEC §3): panel, top bar (tabs, balance / withdrawable, close), the active
 * {@link CounterTab}'s body, the result line, one {@link HitMap} for clicks and the keyboard rules (1 / 2 / 3 switch tab,
 * Esc closes through the host; every other key goes to the active tab). Two thin hosts own one shell each:
 * {@link CounterScreen} (cashier block, a table menu) and {@link CounterSceneScreen} (merchant NPC, {@code /casino store}).
 *
 * <p>Shell target ids: {@code tab.<id>}, {@code top.close}. Tab ids are prefixed by the tab ({@link CounterTab}).
 */
public final class CounterShell {
	/** What the shell needs from its screen. */
	public interface Host {
		Font font();

		/** Closes the screen (the cashier host also closes its menu). */
		void closeCounter();

		CounterSource source();

		/** Dimension code of the source ({@code O / N / E / ?}). */
		String dimension();

		/** The cashier's table state ({@code {}} for menu-less sources). */
		CompoundTag state();

		/** Sends a cashier table action (no-op for menu-less sources). */
		void sendTable(String action, CompoundTag args);

		/** Current balance (state on the cashier, synced status elsewhere). */
		long balance();

		/** Withdrawable chips (balance − debt); -1 = not shown. */
		long withdrawable();
	}

	/** Implemented by both counter screens (tests and payload receivers reach the shell). */
	public interface Holder {
		CounterShell shell();
	}

	private final Host host;
	private List<CounterTab> tabs = List.of();
	private int active;
	private final HitMap hits = new HitMap();
	private Viewport viewport = new Viewport(480, 270, 1, 1f);
	private CounterLayout.Shell layout = CounterLayout.shell(480, 270, new int[0]);
	private @Nullable Component result;
	private boolean resultOk = true;
	private int resultTicks;
	private String lastPressed = "";

	public CounterShell(Host host) {
		this.host = host;
	}

	/** The shell of the open screen, if it is a counter. */
	public static Optional<CounterShell> current() {
		return Minecraft.getInstance().gui.screen() instanceof Holder h ? Optional.of(h.shell()) : Optional.empty();
	}

	// ---- accessors -------------------------------------------------------------------------------------------------

	public Host host() {
		return host;
	}

	public Font font() {
		return host.font();
	}

	public HitMap hits() {
		return hits;
	}

	public Viewport viewport() {
		return viewport;
	}

	public CounterLayout.Shell layout() {
		return layout;
	}

	public List<CounterTab> tabs() {
		return tabs;
	}

	public @Nullable CounterTab activeTab() {
		return tabs.isEmpty() ? null : tabs.get(active);
	}

	/** The tab with {@code id}, if this screen has it. */
	public Optional<CounterTab> tab(String id) {
		return tabs.stream().filter(t -> t.id().equals(id)).findFirst();
	}

	/** The id of the last target pressed by click or key (tests). */
	public String lastPressed() {
		return lastPressed;
	}

	/** Marks {@code id} as pressed (tabs pressing by keyboard call this so tests see the same as a click). */
	public void pressed(String id) {
		lastPressed = id;
	}

	public @Nullable Component result() {
		return result;
	}

	// ---- life cycle ------------------------------------------------------------------------------------------------

	/** Sets the tabs (ids in order); keeps the active tab when it is still there. */
	public void setTabs(List<String> ids) {
		String was = activeTab() == null ? "" : activeTab().id();
		tabs = CounterTabs.create(ids, this);
		active = 0;
		for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id().equals(was)) active = i;
		CounterTab t = activeTab();
		if (t != null) t.opened();
	}

	public void resize(Viewport vp) {
		this.viewport = vp;
		relayout();
	}

	/** Re-computes the layout and every target (open, resize, data change, tab switch). */
	public void relayout() {
		Font font = host.font();
		if (font == null) return;
		int[] widths = new int[tabs.size()];
		for (int i = 0; i < tabs.size(); i++) widths[i] = font.width(tabs.get(i).title()) + 2 * V2Button.PAD;
		layout = CounterLayout.shell(viewport.w(), viewport.h(), widths);
		hits.clear();
		CounterTab t = activeTab();
		if (t != null) t.layout(layout.body(), hits);
		for (int i = 0; i < tabs.size(); i++) hits.add("tab." + tabs.get(i).id(), layout.tabs().get(i), true);
		hits.add("top.close", layout.close(), true);
	}

	public void select(int index) {
		if (index < 0 || index >= tabs.size() || index == active) return;
		active = index;
		tabs.get(active).opened();
		relayout();
	}

	public void select(String id) {
		for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id().equals(id)) select(i);
	}

	public void tick() {
		for (CounterTab t : tabs) t.tick();
		if (resultTicks > 0 && --resultTicks == 0) result = null;
	}

	/** The cashier's state changed. */
	public void stateChanged() {
		for (CounterTab t : tabs) t.stateChanged();
		relayout();
	}

	/** A server result line ({@code CounterResultPayload}, table errors). Shown 80 ticks. */
	public void result(boolean ok, Component message) {
		result = message;
		resultOk = ok;
		resultTicks = 80;
	}

	// ---- input -----------------------------------------------------------------------------------------------------

	/** A mouse press at layout px; true when consumed. */
	public boolean click(double x, double y, int button, int mods) {
		Press press = V2Input.press(button);
		if (press == null) return false;
		Optional<HitMap.Target> t = hits.at(x, y);
		if (t.isEmpty()) return false;
		String id = t.get().id();
		lastPressed = id;
		if (!press.left() && (id.startsWith("tab.") || id.equals("top.close"))) return true;
		if (id.startsWith("tab.")) {
			select(id.substring(4));
			return true;
		}
		if (id.equals("top.close")) {
			host.closeCounter();
			return true;
		}
		CounterTab tab = activeTab();
		return tab != null && tab.onTarget(id, t.get().enabled(), press, mods);
	}

	/** A key press; true when consumed (Esc is left to the host). */
	public boolean key(int keyCode, int mods) {
		CounterTab tab = activeTab();
		if (tab != null && tab.key(keyCode, mods)) return true; // a focused search box eats digits first
		int idx = keyCode == Inputs.KEY_1 ? 0 : keyCode == Inputs.KEY_2 ? 1 : keyCode == Inputs.KEY_3 ? 2 : -1;
		if (idx >= 0 && idx < tabs.size()) {
			lastPressed = "tab." + tabs.get(idx).id();
			select(idx);
			return true;
		}
		return false;
	}

	public boolean charTyped(int codePoint) {
		CounterTab tab = activeTab();
		return tab != null && tab.charTyped(codePoint);
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------

	public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		Font font = host.font();
		CounterLayout.Shell l = layout;
		V2Draw.fill(g, l.panel(), 0xF0140A20);
		V2Draw.outline(g, l.panel(), CasinoPalette.V2_EDGE);
		V2Draw.fill(g, l.top().inset(1), CasinoPalette.V2_PANEL);
		g.fill(l.top().x() + 1, l.top().bottom() - 1, l.top().right() - 1, l.top().bottom(), CasinoPalette.V2_EDGE);
		for (int i = 0; i < tabs.size(); i++) {
			Box b = l.tabs().get(i);
			boolean hover = b.contains(mouseX, mouseY);
			V2Button.draw(g, font, b, tabs.get(i).title(), null, i == active ? V2Sprites.ButtonStyle.PRIMARY : V2Sprites.ButtonStyle.NORMAL, true, hover,
				false, false);
		}
		if (!l.readout().isEmpty()) {
			long wd = host.withdrawable();
			Component line = wd >= 0
				? Component.translatable("gui.burmaldaholic.cashier.readout", Texts.number(host.balance()), Texts.number(wd))
				: Component.translatable("gui.burmaldaholic.cashier.readout_balance", Texts.number(host.balance()));
			V2Draw.left(g, font, line, l.readout(), 0, 1, CasinoPalette.V2_GOLD, true);
		}
		V2Button.draw(g, font, l.close(), CLOSE_GLYPH, null, V2Sprites.ButtonStyle.NORMAL, true, l.close().contains(mouseX, mouseY), false, false);
		CounterTab t = activeTab();
		if (t != null) t.render(g, l.body(), mouseX, mouseY, partial);
		if (result != null) V2Draw.left(g, font, result, l.result(), 0, 1, resultOk ? CasinoPalette.V2_WIN : CasinoPalette.V2_LOSE, true);
	}

	private static final Component CLOSE_GLYPH = Component.literal("×"); // literal-ok: glyph
}
