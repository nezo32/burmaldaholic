package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.core.ui.v2.AckWatchdog;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.DockLayout;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import dev.nezo.burmaldaholic.core.ui.v2.Viewport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The frame of every v2 screen (docs/architecture/ui-v2.md §4.1, D5): it owns the {@link Viewport}, the
 * {@link TableGrid}, the {@link HitMap} and {@link KeyMap}, the components (TopBar, BetBar, PrimaryButton, ActionRow,
 * StatusLine, ResultBanner, toasts) and the acknowledgement watchdog. Hosts ({@link V2TableScreen}, {@link V2SceneScreen})
 * route input here and draw the play area between {@link #renderBackground} and {@link #renderChrome}.
 *
 * <p>Coordinates: v2 screens fill the GUI (D3) and the GUI is the virtual one (the mouse mixin divides by the factor), so
 * every coordinate the host receives or draws with is layout px — one coordinate system.
 *
 * <p>Life cycle: {@link #resize} on init; {@link #apply} on every state change (gates from the game's Flow), then
 * {@link #relayout} (the host's play targets first, then the Dock and the TopBar: z-order = registration order);
 * per frame drawing only; {@link #tick} per client tick (watchdog).
 */
public final class V2Chrome {
	/** What the chrome needs from its screen. */
	public interface Host {
		Font font();

		/** Sends a table action as is (the chrome's {@link #send} adds the watchdog). */
		void sendRaw(String action, CompoundTag args);

		/** An enabled gate of the primary ({@code primary}), the secondary or an action ({@code action.<a>}) was pressed. */
		void onGate(String id, Gate gate);

		/** A press on one of the host's own targets (felt spots, seat boxes, …): left / right click, key, or scroll. */
		default boolean onTarget(String id, boolean enabled, Press press) {
			return false;
		}

		/** A TopBar icon ({@code top.rules}, {@code top.settings}, {@code top.leave}). */
		void onTopIcon(String id);

		/** Registers the PlayArea targets of the current state (called by {@link #relayout}, before the Dock's). */
		void layoutPlay(TableGrid grid, HitMap hits);
	}

	/** Implemented by v2 screens: what client GameTests ({@code RealFlow}) and the gallery reach. */
	public interface Holder {
		V2Chrome chrome();
	}

	/**
	 * Test hook (docs/architecture/ui-v2.md §11.2 "a dropped packet"): while &gt; 0, tracked sends are swallowed (counted
	 * down) as if the packet were lost, so the watchdog's reason + resync can be asserted. Never set in production.
	 */
	public static volatile int dropNextSends;

	private final Host host;
	private Viewport viewport = new Viewport(480, 270, 1, 1f);
	private TableGrid grid = new TableGrid(viewport);
	private final HitMap hits = new HitMap();
	private KeyMap keys = KeyMap.standard();
	public final TopBar top = new TopBar();
	public final BetBar bet = new BetBar();
	public final PrimaryButton primary = new PrimaryButton();
	public final ActionRow actions = new ActionRow();
	public final StatusLine status = new StatusLine();
	public final ResultBanner banner = new ResultBanner();
	public final V2Toast toasts = new V2Toast();
	private final AckWatchdog watchdog = new AckWatchdog();
	private @Nullable Gate secondary;
	private Box secondaryBox = Box.EMPTY;
	private DockLayout.Dock dock;
	private int focus = -1;
	private final Map<String, Supplier<Component>> narration = new HashMap<>();
	private String lastPressed = "";
	private boolean resynced;
	private boolean deltaShown;

	public V2Chrome(Host host) {
		this.host = host;
	}

	// ---- accessors -------------------------------------------------------------------------------------------------

	public Viewport viewport() {
		return viewport;
	}

	public TableGrid grid() {
		return grid;
	}

	public HitMap hits() {
		return hits;
	}

	public KeyMap keys() {
		return keys;
	}

	/** Replaces the key map (start from {@link KeyMap#standard()} and add the game's keys). */
	public void keys(KeyMap keys) {
		this.keys = keys;
	}

	public AckWatchdog watchdog() {
		return watchdog;
	}

	/** The id of the last target pressed by click or key (tests). */
	public String lastPressed() {
		return lastPressed;
	}

	/** The Dock of the last layout. */
	public DockLayout.@Nullable Dock dock() {
		return dock;
	}

	/** A secondary normal button left of the primary (e.g. Leave on the result), id {@code secondary}; null = none. */
	public void secondary(@Nullable Gate gate) {
		this.secondary = gate;
	}

	/** Registers a narration for a target id (Tab focus reads it). */
	public void narrate(String id, Supplier<Component> text) {
		narration.put(id, text);
	}

	// ---- life cycle ------------------------------------------------------------------------------------------------

	public void resize(Viewport vp) {
		this.viewport = vp;
		this.grid = new TableGrid(vp);
		relayout();
	}

	/**
	 * Applies the game's flow to the components: primary, actions, bet bar (when the flow has a bet model and no actions),
	 * status reason and hint. {@code port} may be null for games without a bet bar.
	 */
	public <V> void apply(Flow<V> flow, V view, @Nullable BetPort port) {
		Gate p = flow.primary(view);
		primary.set(p, keys.keyOf(KeyMap.Ids.PRIMARY));
		List<Gate> acts = flow.actions(view);
		actions.set(acts, keys);
		BetView b = flow.bet(view);
		boolean showBet = b != null && acts.isEmpty() && port != null;
		bet.visible(showBet);
		if (b != null && port != null) {
			bet.bind(b, port);
			long[] chips = bet.chips();
			Gate[] cg = new Gate[chips.length];
			for (int i = 0; i < chips.length; i++) cg[i] = flow.chip(chips[i], view);
			String[] tools = bet.toolIds();
			Gate[] tg = new Gate[tools.length];
			for (int i = 0; i < tools.length; i++) tg[i] = flow.tool(tools[i], view);
			bet.gates(cg, tg);
			status.timer.auto(b.secondsLeft());
		} else {
			status.timer.clear(); // games without a bet model this phase set their own timer after apply
		}
		status.reason(p);
		Optional<String> hint = flow.hintKey(view);
		status.hint(hint.map(k -> V2Draw.tr(k, flow.hintArgs(view))).orElse(showBet && bet.isMultiSpot() && bet.armed()
			? V2Draw.tr(V2Keys.STATUS_CLICK_LAYOUT, bet.selected()) : null));
		relayout();
	}

	/** Rebuilds the hit map: the host's play targets, the Dock, the TopBar (z-order = registration order). */
	public void relayout() {
		hits.clear();
		Font font = host.font();
		host.layoutPlay(grid, hits);
		int pw = primary.measure(font);
		int pc = primary.measureCompact(font);
		DockLayout.Spec spec;
		if (bet.visible()) {
			int[] ro = bet.measureReadout(font);
			int sw = secondary == null ? 0 : V2Button.width(font, V2Draw.label(secondary), null);
			spec = DockLayout.Spec.betting(bet.chips().length, bet.toolIds().length, ro[0], ro[1], sw, pw, pc);
		} else {
			spec = DockLayout.Spec.actions(actions.measure(font), actions.measureCompact(font), pw, pc);
		}
		dock = DockLayout.layout(grid, spec, bet.selectedIndex());
		bet.place(grid.tier, dock.chips(), dock.firstChip(), dock.tools(), dock.readout(), dock.compactReadout());
		if (bet.visible()) bet.hits(hits);
		actions.place(dock.actions(), dock.compactActions());
		for (int i = 0; i < Math.min(actions.gates().size(), dock.actions().size()); i++) {
			Gate a = actions.gates().get(i);
			hits.add(ActionRow.id(a), dock.actions().get(i), a.enabled());
		}
		secondaryBox = secondary == null ? Box.EMPTY : dock.secondary(); // betting Docks only (DockLayout drops it first)
		if (secondary != null) hits.add(KeyMap.Ids.SECONDARY, secondaryBox, secondary.enabled());
		primary.place(dock.primary(), dock.compactPrimary());
		hits.add(KeyMap.Ids.PRIMARY, dock.primary(), primary.gate().enabled());
		status.place(dock.status());
		top.layout(grid, font, hits);
		narrateKit();
		banner.tier(grid.tier);
		toasts.place(grid.play);
	}

	/** Narrations of the kit's own targets (Tab focus): label, and the reason when disabled. */
	private void narrateKit() {
		java.util.function.Function<Gate, Component> say = g -> {
			Component r = V2Draw.reason(g);
			return r == null ? V2Draw.label(g) : V2Draw.label(g).copy().append(". ").append(r); // literal-ok: punctuation
		};
		narration.put(KeyMap.Ids.PRIMARY, () -> say.apply(primary.gate()));
		for (Gate a : actions.gates()) narration.put(ActionRow.id(a), () -> say.apply(a));
		long[] chips = bet.chips();
		for (int i = 0; i < chips.length; i++) {
			int idx = i;
			narration.put(KeyMap.Ids.chip(i), () -> {
				Gate g = bet.chipGate(idx);
				Component label = V2Draw.tr(V2Keys.CHIP, chips[idx]);
				Component r = V2Draw.reason(g);
				return r == null ? label : label.copy().append(". ").append(r); // literal-ok: punctuation
			});
		}
		for (String t : bet.toolIds()) narration.put(KeyMap.Ids.tool(t), () -> say.apply(bet.toolGate(t)));
		narration.put(KeyMap.Ids.TOP_RULES, () -> Component.translatable(V2Keys.TOP_RULES));
		narration.put(KeyMap.Ids.TOP_SETTINGS, () -> Component.translatable(V2Keys.TOP_SETTINGS));
		narration.put(KeyMap.Ids.TOP_LEAVE, () -> Component.translatable(V2Keys.TOP_LEAVE));
	}

	/** One client tick: the watchdog; when an action timed out, the reason shows and a {@code resync} is sent. */
	public void tick() {
		if (watchdog.tick()) {
			Component why = Component.translatable(V2Keys.REASON_NO_ANSWER);
			status.watchdog(why);
			toasts.show(V2Toast.Kind.ERROR, why);
			resynced = true;
			host.sendRaw("resync", new CompoundTag());
		}
	}

	/** True once after the watchdog fired (tests / the screen's own reaction). */
	public boolean consumeResynced() {
		boolean r = resynced;
		resynced = false;
		return r;
	}

	/** A new state arrived (reads the server's {@code ack}). */
	public void acceptState(CompoundTag state) {
		long before = watchdog.lastAck();
		long ack = state.getLongOr("ack", -1);
		watchdog.state(ack);
		if (ack > before && before >= 0) status.clearFlash(); // an answered action makes an old "why not" stale
	}

	/** A server error: toast on the felt, status line, the pending chip dropped. */
	public void error(Component message) {
		watchdog.error();
		status.error(message);
		toasts.show(V2Toast.Kind.ERROR, message);
	}

	/**
	 * Sends a table action under the watchdog: {@code spot} / {@code amount} describe the pending chip drawn until the
	 * echo ("" / 0 = none). Every kit-sent action goes through here.
	 */
	public void send(String action, CompoundTag args, String spot, long amount) {
		watchdog.sent(action, spot, amount);
		if (dropNextSends > 0) {
			dropNextSends--;
			return;
		}
		host.sendRaw(action, args);
	}

	public void send(String action) {
		send(action, new CompoundTag(), "", 0);
	}

	/** The pending action for drawing (null = none). */
	public BetBar.@Nullable AckState pending() {
		return watchdog.pending() ? new BetBar.AckState(watchdog.pendingAction(), watchdog.pendingSpot(), watchdog.pendingAmount()) : null;
	}

	// ---- input -----------------------------------------------------------------------------------------------------

	/** A mouse click in layout px ({@code button}: the raw event button, resolved by {@link V2Input}); true when consumed. */
	public boolean click(double x, double y, int button) {
		Press p = V2Input.press(button);
		if (p == null) return false;
		Optional<HitMap.Target> t = hits.at(x, y);
		if (t.isEmpty()) return false;
		focus = -1;
		return press(t.get().id(), t.get().enabled(), p);
	}

	/** A key press (the raw event key code, resolved by {@link V2Input}); true when consumed. */
	public boolean key(int code) {
		return key(V2Input.keyName(code));
	}

	/** A key press by {@link KeyMap} name (null = unnamed key); true when consumed. Tab walks the targets, Enter presses the focused one. */
	public boolean key(@Nullable String name) {
		if (name == null) return false;
		if (name.equals(KeyMap.TAB)) {
			List<HitMap.Target> ts = focusable();
			if (ts.isEmpty()) return false;
			focus = (focus + 1) % ts.size();
			Supplier<Component> n = narration.get(ts.get(focus).id());
			if (n != null) CasinoUi.say(n.get());
			return true;
		}
		if (name.equals(KeyMap.ENTER) && focus >= 0) {
			List<HitMap.Target> ts = focusable();
			if (focus < ts.size()) return press(ts.get(focus).id(), ts.get(focus).enabled(), Press.LEFT);
		}
		Optional<String> id = keys.idFor(name);
		if (id.isEmpty()) return false;
		Optional<HitMap.Target> t = hits.targets().stream().filter(x -> x.id().equals(id.get())).findFirst();
		return press(id.get(), t.map(HitMap.Target::enabled).orElse(true), Press.LEFT);
	}

	/** Scroll over a target: routed to the host as {@link Press#SCROLL_UP} (+) / {@link Press#SCROLL_DOWN} (−) presses of that target. */
	public boolean scroll(double x, double y, double dy) {
		if (dy == 0) return false;
		Optional<HitMap.Target> t = hits.at(x, y);
		return t.isPresent() && host.onTarget(t.get().id(), t.get().enabled(), dy > 0 ? Press.SCROLL_UP : Press.SCROLL_DOWN);
	}

	private List<HitMap.Target> focusable() {
		List<HitMap.Target> out = new ArrayList<>();
		for (HitMap.Target t : hits.targets()) if (!t.id().equals(KeyMap.Ids.READOUT)) out.add(t);
		return out;
	}

	/** The focused target's id, or "". */
	public String focusedId() {
		List<HitMap.Target> ts = focusable();
		return focus >= 0 && focus < ts.size() ? ts.get(focus).id() : "";
	}

	/** Presses a target by id (click / key / test). */
	public boolean press(String id, boolean hitEnabled, Press button) {
		lastPressed = id;
		if (id.equals(KeyMap.Ids.PRIMARY)) {
			Gate g = primary.gate();
			if (!g.enabled()) {
				primary.shake();
				refuse(g);
				return true;
			}
			primary.pressed();
			host.onGate(id, g);
			return true;
		}
		if (id.equals(KeyMap.Ids.SECONDARY) && secondary != null) {
			if (!secondary.enabled()) refuse(secondary);
			else host.onGate(id, secondary);
			return true;
		}
		if (id.startsWith("action.")) {
			for (Gate g : actions.gates()) {
				if (!ActionRow.id(g).equals(id)) continue;
				if (!g.enabled()) refuse(g);
				else {
					actions.pressed(id);
					host.onGate(id, g);
				}
				return true;
			}
			return false;
		}
		if (id.startsWith("chip.")) {
			if (!bet.visible()) return false;
			int i;
			try {
				i = Integer.parseInt(id.substring(5));
			} catch (NumberFormatException e) {
				return false;
			}
			long[] chips = bet.chips();
			if (i < 0 || i >= chips.length) return false;
			Gate g = bet.chipGate(i);
			if (!g.enabled() && !button.right()) {
				refuse(g);
				return true;
			}
			BetPort port = bet.port();
			if (port == null) return false;
			long d = chips[i];
			if (button.right()) {
				port.remove(d);
			} else if (bet.isMultiSpot()) {
				bet.select(d, true);
				status.hint(V2Draw.tr(V2Keys.STATUS_CLICK_LAYOUT, d));
				relayout();
			} else {
				bet.select(d, false);
				port.add(d);
			}
			return true;
		}
		if (id.startsWith("tool.")) {
			if (!bet.visible()) return false;
			String tool = id.substring(5);
			Gate g = bet.toolGate(tool);
			if (!g.enabled()) {
				refuse(g);
				return true;
			}
			BetPort port = bet.port();
			if (port != null) port.tool(tool);
			return true;
		}
		if (id.startsWith("top.")) {
			host.onTopIcon(id);
			return true;
		}
		if (id.equals(KeyMap.Ids.READOUT)) return true;
		return host.onTarget(id, hitEnabled, button);
	}

	/** A press on a disabled gate: its reason in the status line (never nothing). */
	public void refuse(Gate g) {
		Component r = V2Draw.reason(g);
		if (r != null) status.flash(r);
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------

	/** The felt, full bleed over the whole screen (under the TopBar and Dock, which cover it). */
	public void renderBackground(GuiGraphicsExtractor g, dev.nezo.burmaldaholic.client.ui.CasinoTheme theme) {
		V2Felt.draw(g, grid.play, theme);
	}

	/** TopBar, Dock (BetBar or ActionRow, status, primary), then the banner and the toasts. */
	public void renderChrome(GuiGraphicsExtractor g, int mx, int my) {
		Font font = host.font();
		boolean delta = top.balance.deltaVisible();
		if (delta != deltaShown) { // the TopBar makes room for the delta pill while it shows
			deltaShown = delta;
			relayout();
		}
		Box d = grid.dock;
		if (!V2Draw.sprite(g, V2Sprites.DOCK, d, 0xFFFFFFFF)) {
			V2Draw.fill(g, d, CasinoPalette.V2_INK);
			g.fill(d.x(), d.y(), d.right(), d.y() + 1, CasinoPalette.V2_RAIL_DARK);
			g.fill(d.x(), d.y() + 1, d.right(), d.y() + 2, CasinoPalette.V2_GOLD_SHADE);
		}
		String f = focusedId();
		top.draw(g, font, mx, my, f);
		bet.draw(g, font, mx, my, f, pending());
		actions.draw(g, font, mx, my, f);
		status.draw(g, font);
		if (secondary != null && !secondaryBox.isEmpty()) {
			V2Button.draw(g, font, secondaryBox, V2Draw.label(secondary), null, V2Sprites.ButtonStyle.NORMAL, secondary.enabled(),
				secondaryBox.contains(mx, my), false, KeyMap.Ids.SECONDARY.equals(f));
		}
		primary.draw(g, font, mx, my, KeyMap.Ids.PRIMARY.equals(f));
		banner.draw(g, font);
		toasts.draw(g, font);
		if (focus >= 0 && !f.isEmpty() && !f.startsWith("chip.") && !f.startsWith("tool.") && !f.startsWith("action.") && !f.startsWith("top.")
			&& !f.equals(KeyMap.Ids.PRIMARY)) {
			hits.box(f).ifPresent(b -> V2Draw.outline(g, b.inset(-1), CasinoPalette.V2_GOLD));
		}
		hits.at(mx, my).ifPresent(t -> {
			if (!t.id().startsWith("tool.") && !t.id().startsWith("top.")) return;
			Supplier<Component> n = narration.get(t.id());
			if (n != null) iconTip(g, font, t.box(), n.get());
		});
		if (debugOverlay) {
			for (HitMap.Target t : hits.targets()) V2Draw.outline(g, t.box(), t.enabled() ? 0x8000FFFF : 0x80FF4040);
		}
	}

	/** The label of an icon-only button on hover: a pill above it (below it for TopBar icons), kept on screen. */
	private void iconTip(GuiGraphicsExtractor g, Font font, Box icon, Component text) {
		int w = Math.min(grid.screen.w() - 4, font.width(text) + 8);
		int h = 13;
		boolean below = icon.y() < grid.play.y();
		int x = Math.max(2, Math.min(grid.screen.w() - w - 2, icon.cx() - w / 2));
		int y = below ? icon.bottom() + 3 : icon.y() - h - 3;
		Box b = new Box(x, y, w, h);
		g.nextStratum();
		V2Draw.pill(g, b, CasinoPalette.V2_PANEL_HI, CasinoPalette.V2_INK);
		V2Draw.centred(g, font, text, b, 1, CasinoPalette.V2_TEXT, true);
	}

	/** Draws every hit target's outline (gallery / debugging). */
	public static boolean debugOverlay;

	/** The convenience of BetGates for hosts without a flow-provided tool set. */
	public static Gate defaultTool(String id, BetView v) {
		return BetGates.tool(id, v);
	}
}
