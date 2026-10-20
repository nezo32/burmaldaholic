package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.core.ui.v2.AckWatchdog;
import dev.nezo.burmaldaholic.core.ui.v2.BetAmount;
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

		/**
		 * Called LAST in {@link #relayout}, after the Dock and the TopBar registered their targets: targets that must sit
		 * on top of everything (z-order = registration order) or depend on the Dock's boxes, and extra banner obstacles
		 * ({@code chrome().banner.obstacles(..)}). Default nothing.
		 */
		default void layoutOverlay(DockLayout.Dock dock, HitMap hits) {}
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
	/** The {@code − value +} slot of the ACTIONS Dock (hidden until {@link Stepper#set}). */
	public final Stepper stepper = new Stepper();
	public final StatusLine status = new StatusLine();
	public final ResultBanner banner = new ResultBanner();
	public final V2Toast toasts = new V2Toast();
	/** The typed-amount strip of the betting Dock (lane HR). */
	public final BetEntry entry = new BetEntry();
	/** Whose limits bind the viewer (the state's {@code cap_source}; lane HR). */
	private dev.nezo.burmaldaholic.core.wager.BetCap cap = dev.nezo.burmaldaholic.core.wager.BetCap.NONE;
	private final AckWatchdog watchdog = new AckWatchdog();
	private @Nullable Gate secondary;
	private Box secondaryBox = Box.EMPTY;
	private DockLayout.Dock dock;
	private int focus = -1;
	private final Map<String, Supplier<Component>> narration = new HashMap<>();
	private String lastPressed = "";
	private boolean resynced;
	private boolean deltaShown;
	private String shakeId = "";
	private long shakeAt = -1;

	public V2Chrome(Host host) {
		this.host = host;
		actions.shake = this::shakeDx;
		bet.shake = this::shakeDx;
		stepper.shake = this::shakeDx;
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
		held.clear();
		this.viewport = vp;
		this.grid = new TableGrid(vp);
		relayout();
	}

	/**
	 * Applies the game's flow to the components: primary, actions, bet bar (when the flow has a bet model and no actions),
	 * status reason and hint. {@code port} may be null for games without a bet bar.
	 */
	public <V> void apply(Flow<V> flow, V view, @Nullable BetPort port) {
		BetView bv = flow.bet(view);
		Gate p = explain(flow.primary(view), bv);
		primary.set(p, keys.keyOf(KeyMap.Ids.PRIMARY));
		List<Gate> acts = new ArrayList<>();
		for (Gate a : flow.actions(view)) acts.add(explain(a, bv));
		actions.set(acts, keys);
		BetView b = flow.bet(view);
		boolean showBet = b != null && acts.isEmpty() && port != null;
		bet.visible(showBet);
		if (b != null && port != null) {
			bet.bind(b, port);
			long[] chips = bet.chips();
			Gate[] cg = new Gate[chips.length];
			for (int i = 0; i < chips.length; i++) cg[i] = explain(flow.chip(chips[i], view), b);
			String[] tools = bet.toolIds();
			Gate[] tg = new Gate[tools.length];
			for (int i = 0; i < tools.length; i++) tg[i] = explain(flow.tool(tools[i], view), b);
			bet.gates(cg, tg);
			status.timer.auto(b.secondsLeft());
		} else {
			status.timer.clear(); // games without a bet model this phase set their own timer after apply
		}
		if (!showBet || !b.canBet()) entry.close();
		status.reason(p);
		Optional<String> hint = flow.hintKey(view);
		status.hint(entry.isOpen() ? V2Draw.tr(BetEntry.KEY_HINT)
			: hint.map(k -> V2Draw.tr(k, flow.hintArgs(view))).orElse(fallbackHint(showBet ? b : null)));
		relayout();
	}

	/** The cap the last state carried (lane HR). */
	public dev.nezo.burmaldaholic.core.wager.BetCap cap() {
		return cap;
	}

	/**
	 * A disabled gate's reason in the words of WHOSE limit it is (owner playtest B5): the generic "The maximum bet is N" of
	 * any game's flow becomes the VIP tier / table / owner sentence of the state's {@code cap_source}, and "Not enough
	 * chips" says the balance. Enabled gates and other reasons pass unchanged.
	 */
	public Gate explain(Gate g, @Nullable BetView v) {
		if (g.enabled() || g.reasonKey() == null) return g;
		dev.nezo.burmaldaholic.core.wager.BetCap c = v != null && v.cap().source() != dev.nezo.burmaldaholic.core.wager.BetCap.Source.NONE ? v.cap() : cap;
		if (g.reasonKey().equals(V2Keys.REASON_ABOVE_MAX) && c.source() != dev.nezo.burmaldaholic.core.wager.BetCap.Source.NONE) {
			Object[] a = g.reasonArgs();
			dev.nezo.burmaldaholic.core.wager.BetCap at = a.length > 0 && a[0] instanceof Number n && n.longValue() > 0 ? c.withMax(n.longValue()) : c;
			return new Gate(g.action(), g.labelKey(), g.labelArgs(), false, at.reasonKey(), at.reasonArgs());
		}
		if (g.reasonKey().equals(V2Keys.REASON_BELOW_MIN) && c.ownerMin()) {
			return new Gate(g.action(), g.labelKey(), g.labelArgs(), false, dev.nezo.burmaldaholic.core.wager.BetCap.KEY_OWNER_MIN, g.reasonArgs());
		}
		if (g.reasonKey().equals(V2Keys.REASON_NOT_ENOUGH) && v != null) {
			return new Gate(g.action(), g.labelKey(), g.labelArgs(), false, dev.nezo.burmaldaholic.core.wager.BetCap.KEY_BALANCE,
				new Object[] {Math.max(0, v.balance())});
		}
		return g;
	}

	/** The bet view the entry clamps with: the flow's, with the state's cap when the flow did not set one. */
	private BetView entryView() {
		BetView v = bet.view();
		return v.cap().source() == dev.nezo.burmaldaholic.core.wager.BetCap.Source.NONE && cap.source() != dev.nezo.burmaldaholic.core.wager.BetCap.Source.NONE
			? v.toBuilder().cap(cap).build() : v;
	}

	/** Opens the bet-entry strip (readout click, E); when bets are closed, says why. */
	private boolean openEntry() {
		if (!bet.visible()) return false;
		if (entry.isOpen()) {
			entry.close();
			relayout();
			return true;
		}
		BetView v = entryView();
		Gate closed = BetGates.closedReason(BetGates.CHIP, V2Keys.CHIP, v);
		if (closed != null) {
			refuse(explain(closed, v));
			return true;
		}
		BetPort port = bet.port();
		BetAmount.Mode mode = bet.isMultiSpot() || port == null || !port.setsAmount() ? BetAmount.Mode.UNIT : BetAmount.Mode.BET;
		BetAmount.Cost cost = mode == BetAmount.Mode.BET ? port.entryCost() : BetAmount.ONE;
		entry.open(mode, cost, BetAmount.initial(v, mode, Math.max(0, bet.selected()), cost));
		entryHint();
		relayout();
		return true;
	}

	/** A press on the open strip (½, ×2, Max, OK, the field). */
	private boolean pressEntry(String id) {
		if (!entry.isOpen()) return id.equals(KeyMap.Ids.ENTRY) && openEntry();
		BetView v = entryView();
		BetAmount.Mode mode = entry.mode();
		BetAmount.Clamp c = switch (id) {
			case BetEntry.HALF -> BetAmount.halved(entry.value(), v, mode, entry.cost());
			case BetEntry.DOUBLE -> BetAmount.doubled(entry.value(), v, mode, entry.cost());
			case BetEntry.MAX -> BetAmount.max(v, mode, entry.cost());
			default -> null;
		};
		if (id.equals(BetEntry.OK)) {
			entry.pressed(id);
			commitEntry();
			return true;
		}
		if (id.equals(KeyMap.Ids.ENTRY)) return openEntry();
		if (c == null) return true;
		entry.pressed(id);
		entry.set(c.value());
		entryHint();
		if (c.clamped()) flash(id, c.reasonKey(), c.reasonArgs());
		relayout();
		return true;
	}

	/** Line 2 while the strip is open: how to type, or the whole stake when the amount is only part of it (UTH: Ante). */
	private void entryHint() {
		BetAmount.Cost cost = entry.cost();
		status.hint(cost.identity() || entry.value() <= 0 ? V2Draw.tr(BetEntry.KEY_HINT) : V2Draw.tr(BetEntry.KEY_COST, cost.of(entry.value())));
	}

	/** OK / Enter: the amount (clamped, whose cap said) becomes the bet, or the felt unit. */
	private void commitEntry() {
		BetView v = entryView();
		long raw = entry.value();
		BetAmount.Mode mode = entry.mode();
		BetAmount.Cost cost = entry.cost();
		entry.close();
		if (raw > 0) {
			BetAmount.Clamp c = BetAmount.clamp(raw, v, mode, cost);
			if (c.clamped()) flash(BetEntry.OK, c.reasonKey(), c.reasonArgs());
			BetPort port = bet.port();
			if (c.value() > 0 && port != null) {
				if (mode == BetAmount.Mode.BET && port.amount(c.value())) {
					bet.unit(0);
				} else {
					bet.unit(c.value());
					status.hint(V2Draw.tr(BetEntry.KEY_UNIT, c.value()));
				}
			}
		}
		relayout();
	}

	/** A key while the strip is open: digits / k / m type, Backspace deletes, Enter = OK, Esc closes; every key is taken. */
	private boolean keyEntry(String name) {
		switch (name) {
			case KeyMap.ESCAPE -> entry.close();
			case KeyMap.ENTER -> commitEntry();
			case KeyMap.BACKSPACE, KeyMap.DELETE -> entry.backspace();
			default -> {
				if (name.length() == 1) entry.type(name.charAt(0));
			}
		}
		if (entry.isOpen()) entryHint();
		relayout();
		return true;
	}

	/** Test hook: the entry types {@code text} as keys would. */
	public void typeEntry(String text) {
		for (char c : text.toCharArray()) keyEntry(String.valueOf(c));
	}

	/**
	 * The kit's line-2 hint when the game gives none, by phase (never a betting hint outside betting — C2: "Click the
	 * layout" showed on the roulette result): bets open and seated → how chips work (multi-spot: click the layout;
	 * single: chips add to the bet); a countdown to the next round → "Next round in N s"; else nothing (the primary's
	 * reason or the game's line 1 speaks).
	 */
	private @Nullable Component fallbackHint(@Nullable BetView b) {
		if (b == null) return null;
		if (b.seated() && b.bettingOpen()) {
			return bet.isMultiSpot() ? V2Draw.tr(V2Keys.STATUS_CLICK_LAYOUT, Math.max(0, bet.selected())) : V2Draw.tr(V2Keys.STATUS_CHIPS_ADD);
		}
		if (!b.bettingOpen() && b.secondsLeft() >= 0 && (b.phase() == BetView.Phase.BETTING || b.phase() == BetView.Phase.IDLE)) {
			return V2Draw.tr(V2Keys.STATUS_NEXT_ROUND_IN, b.secondsLeft());
		}
		return null;
	}

	/** Rebuilds the hit map: the host's play targets, the Dock, the TopBar (z-order = registration order). */
	public void relayout() {
		hits.clear();
		banner.beginLayout();
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
			spec = DockLayout.Spec.actions(actions.measure(font), actions.measureCompact(font), stepper.measure(font, grid.tier), pw, pc);
		}
		// the status asks for the width of its longer line (capped at 40 % of the Dock): actions go compact first, then line 2
		// moves to the hint strip above the Dock (review R-KW B2, DES 3)
		spec = spec.withStatus(Math.min(grid.dock.w() * 2 / 5, status.measure(font)));
		dock = DockLayout.layout(grid, spec, bet.selectedIndex());
		bet.place(grid.tier, dock.chips(), dock.firstChip(), dock.tools(), dock.readout(), dock.compactReadout());
		if (bet.visible()) bet.hits(hits);
		actions.place(dock.actions(), dock.compactActions());
		stepper.place(dock.stepper());
		stepper.hits(hits);
		for (int i = 0; i < Math.min(actions.gates().size(), dock.actions().size()); i++) {
			Gate a = actions.gates().get(i);
			hits.add(ActionRow.id(a), dock.actions().get(i), a.enabled());
		}
		secondaryBox = secondary == null ? Box.EMPTY : dock.secondary(); // betting Docks only (DockLayout drops it first)
		if (secondary != null) hits.add(KeyMap.Ids.SECONDARY, secondaryBox, secondary.enabled());
		primary.place(dock.primary(), dock.compactPrimary());
		hits.add(KeyMap.Ids.PRIMARY, dock.primary(), primary.gate().enabled());
		status.place(dock.status(), dock.hintStrip());
		top.layout(grid, font, hits);
		narrateKit();
		banner.tier(grid.tier);
		banner.kitObstacles(dock.hintStrip().isEmpty() ? List.of() : List.of(dock.hintStrip()));
		toasts.place(grid.play);
		host.layoutOverlay(dock, hits);
		if (entry.isOpen()) { // the typed-amount strip sits on top of everything (lane HR)
			int anchor = !dock.readout().isEmpty() ? dock.readout().x() : !dock.chips().isEmpty() ? dock.chips().get(0).x() : grid.dock.x();
			entry.layout(grid, font, anchor);
			entry.hits(hits);
		}
	}

	/** Narrations of the kit's own targets (Tab focus): label, and the reason when disabled. */
	private void narrateKit() {
		java.util.function.Function<Gate, Component> say = g -> {
			Component r = V2Draw.reason(g);
			return r == null ? V2Draw.label(g) : V2Draw.label(g).copy().append(". ").append(r); // literal-ok: punctuation
		};
		narration.put(KeyMap.Ids.PRIMARY, () -> say.apply(primary.gate()));
		for (Gate a : actions.gates()) narration.put(ActionRow.id(a), () -> say.apply(a));
		if (stepper.visible()) {
			narration.put(Stepper.MINUS, () -> say.apply(stepper.minus()));
			narration.put(Stepper.PLUS, () -> say.apply(stepper.plus()));
			narration.put(Stepper.VALUE, stepper::value);
		}
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
		narration.put(KeyMap.Ids.READOUT, () -> Component.translatable(BetEntry.KEY_OPEN));
		narration.put(BetEntry.HALF, () -> Component.translatable(BetEntry.KEY_HALF));
		narration.put(BetEntry.DOUBLE, () -> Component.translatable(BetEntry.KEY_DOUBLE));
		narration.put(BetEntry.MAX, () -> Component.translatable(BetEntry.KEY_MAX));
		narration.put(BetEntry.OK, () -> Component.translatable(BetEntry.KEY_OK));
		narration.put(BetEntry.FIELD, () -> entry.text().isEmpty() ? Component.translatable(BetEntry.KEY_OPEN) : V2Draw.tr(V2Keys.BET, entry.value()));
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
		cap = dev.nezo.burmaldaholic.core.wager.BetCap.read(state);
		long before = watchdog.lastAck();
		long ack = state.getLongOr("ack", -1);
		watchdog.state(state.getIntOr("ack_epoch", 0), ack);
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
		if (entry.isOpen() && (t.isEmpty() || !BetEntry.owns(t.get().id()) && !t.get().id().equals(KeyMap.Ids.READOUT))) {
			entry.close(); // a click elsewhere closes the strip (and still does what it says)
			relayout();
			t = hits.at(x, y);
		}
		if (t.isEmpty()) return false;
		focus = -1;
		return press(t.get().id(), t.get().enabled(), p);
	}

	/** Whether a click at (x, y) lands on an enabled kit or play target (it then acts even while a celebration plays). */
	public boolean hitsEnabled(double x, double y) {
		return hits.at(x, y).map(HitMap.Target::enabled).orElse(false);
	}

	/** Whether a key press reaches an enabled target (a bound kit key or the stepper's). */
	public boolean keyHitsEnabled(int code) {
		String name = V2Input.keyName(code);
		if (name == null) return false;
		if (entry.isOpen()) return true;
		String step = stepper.idForKey(name);
		if (step != null) {
			Gate g = stepper.gate(step);
			return g != null && g.enabled();
		}
		Optional<String> id = keys.idFor(name);
		return id.isPresent() && hits.targets().stream().anyMatch(t -> t.id().equals(id.get()) && t.enabled());
	}

		/** A key press (the raw event key code, resolved by {@link V2Input}); true when consumed. */
	public boolean key(int code) {
		return key(V2Input.keyName(code));
	}

	/**
	 * Keys held down now (by name): the OS auto-repeat of a held key must not place a chip per repeat or press the primary
	 * again (review R-KW M5). Cleared by {@link #keyReleased} and on resize.
	 */
	private final java.util.Set<String> held = new java.util.HashSet<>();

	/** A key release (raw code); true when the kit tracked it as held. */
	public boolean keyReleased(int code) {
		String name = V2Input.keyName(code);
		return name != null && held.remove(name);
	}

	/** Whether a press of {@code id} repeats while its key is held (no: chips, primary, secondary, actions, the stepper, tools). */
	private static boolean once(String id) {
		return id.startsWith("chip.") || id.startsWith("tool.") || id.startsWith("action.") || id.startsWith("stepper.") || id.equals(KeyMap.Ids.PRIMARY)
			|| id.equals(KeyMap.Ids.SECONDARY);
	}

	/** A key press by {@link KeyMap} name (null = unnamed key); true when consumed. Tab walks the targets, Enter presses the focused one. */
	public boolean key(@Nullable String name) {
		if (name == null) return false;
		if (entry.isOpen()) return keyEntry(name);
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
		String step = stepper.idForKey(name);
		if (step != null) return !held.add(name) || press(step, true, Press.LEFT);
		Optional<String> id = keys.idFor(name);
		if (id.isEmpty()) return false;
		if (once(id.get()) && !held.add(name)) return true; // an auto-repeat of a held key: swallowed
		Optional<HitMap.Target> t = hits.targets().stream().filter(x -> x.id().equals(id.get())).findFirst();
		return press(id.get(), t.map(HitMap.Target::enabled).orElse(true), Press.LEFT);
	}

	/** Scroll over a target: routed to the host as {@link Press#SCROLL_UP} (+) / {@link Press#SCROLL_DOWN} (−) presses of that target. */
	public boolean scroll(double x, double y, double dy) {
		if (dy == 0) return false;
		Optional<HitMap.Target> t = hits.at(x, y);
		if (t.isPresent() && bet.visible() && (t.get().id().equals(KeyMap.Ids.READOUT) || t.get().id().equals(BetEntry.FIELD))) {
			if (!entry.isOpen() && !openEntry()) return false;
			if (!entry.isOpen()) return true; // bets closed: the reason flashed
			BetAmount.Clamp c = BetAmount.wheel(entry.value(), dy > 0, entryView(), entry.mode(), entry.cost());
			entry.set(c.value());
			entryHint();
			if (c.clamped()) flash(BetEntry.FIELD, c.reasonKey(), c.reasonArgs());
			relayout();
			return true;
		}
		if (t.isPresent() && t.get().id().equals(Stepper.VALUE)) {
			String id = dy > 0 ? Stepper.PLUS : Stepper.MINUS;
			Gate g = stepper.gate(id);
			return g != null && press(id, g.enabled(), Press.LEFT);
		}
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
		if (id.startsWith("stepper.")) {
			if (id.equals(Stepper.VALUE)) return true;
			Gate g = stepper.gate(id);
			if (g == null) return false;
			if (!g.enabled()) flash(id, g);
			else {
				stepper.pressed(id);
				host.onGate(id, g);
			}
			return true;
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
		if (id.equals(KeyMap.Ids.READOUT)) return openEntry() || true;
		if (BetEntry.owns(id)) return pressEntry(id);
		return host.onTarget(id, hitEnabled, button);
	}

	/** A press on a disabled gate: its reason in the status line (never nothing). */
	public void refuse(Gate g) {
		Component r = V2Draw.reason(g);
		if (r != null) status.flash(r);
	}

	/**
	 * "Not now, because …" for any target: the reason ({@code reasonKey} with {@code args}, numbers formatted) flashes in
	 * the status line and the target ({@code primary}, {@code chip.N}, {@code action.<a>}, {@code stepper.*}, a felt id
	 * the host draws with {@link #shakeDx}) shakes 3 px. E.g. a queued Rebet the balance no longer covers.
	 */
	public void flash(String targetId, String reasonKey, Object... args) {
		status.flash(V2Draw.tr(reasonKey, args));
		shake(targetId);
	}

	private void flash(String targetId, Gate disabled) {
		refuse(disabled);
		shake(targetId);
	}

	/** Shakes a target (3 px, {@link PrimaryButton#SHAKE_MS}; reduce motion: none). */
	public void shake(String targetId) {
		if (KeyMap.Ids.PRIMARY.equals(targetId)) primary.shake();
		shakeId = targetId;
		shakeAt = net.minecraft.util.Util.getMillis();
	}

	/** The x offset a shaking target is drawn at now (0 = still); hosts use it for their own felt targets. */
	public int shakeDx(String targetId) {
		if (shakeAt < 0 || !targetId.equals(shakeId) || dev.nezo.burmaldaholic.client.fx.FxSettings.reduceMotion()) return 0;
		long age = net.minecraft.util.Util.getMillis() - shakeAt;
		if (age >= PrimaryButton.SHAKE_MS) return 0;
		double t = age / (double) PrimaryButton.SHAKE_MS;
		return (int) Math.round(Math.sin(t * Math.PI * 6) * 3 * (1 - t));
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------

	/** The felt, full bleed over the whole screen (under the TopBar and Dock, which cover it). */
	public void renderBackground(GuiGraphicsExtractor g, dev.nezo.burmaldaholic.client.ui.CasinoTheme theme) {
		if (viewport.framed()) { // outside the frame: the menu background dimmed (the blurred world shows through)
			int x0 = -viewport.ox(), y0 = -viewport.oy(), x1 = viewport.w() - viewport.ox(), y1 = viewport.h() - viewport.oy();
			int fw = viewport.frameW(), fh = viewport.frameH();
			int dim = CasinoPalette.withAlpha(CasinoPalette.V2_INK, 0.86f);
			g.fill(x0, y0, x1, 0, dim);
			g.fill(x0, fh, x1, y1, dim);
			g.fill(x0, 0, 0, fh, dim);
			g.fill(fw, 0, x1, fh, dim);
		}
		V2Felt.draw(g, grid.play, theme);
	}

	/** Outset of the frame edge around a framed screen (layout px). */
	public static final int FRAME_EDGE = 8;

	/** The frame edge (nine-slice with its shadow) around a framed screen, drawn over the frame's outer pixels' surround. */
	private void frameEdge(GuiGraphicsExtractor g) {
		if (!viewport.framed()) return;
		Box e = grid.screen.inset(-FRAME_EDGE);
		if (V2Draw.sprite(g, V2Sprites.FRAME_EDGE, e, 0xFFFFFFFF)) return;
		for (int i = 0; i < FRAME_EDGE - 2; i++) V2Draw.outline(g, e.inset(i), CasinoPalette.withAlpha(0xFF000000, 0.08f + 0.05f * i));
		V2Draw.outline(g, grid.screen.inset(-2), CasinoPalette.V2_RAIL_DARK);
		V2Draw.outline(g, grid.screen.inset(-1), CasinoPalette.V2_GOLD_SHADE);
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
		stepper.draw(g, font, mx, my, f);
		status.draw(g, font);
		if (secondary != null && !secondaryBox.isEmpty()) {
			V2Button.draw(g, font, secondaryBox, V2Draw.label(secondary), null, V2Sprites.ButtonStyle.NORMAL, secondary.enabled(),
				secondaryBox.contains(mx, my), false, KeyMap.Ids.SECONDARY.equals(f));
		}
		primary.draw(g, font, mx, my, KeyMap.Ids.PRIMARY.equals(f));
		entry.draw(g, font, mx, my, bet.view());
		banner.draw(g, font);
		toasts.draw(g, font);
		frameEdge(g);
		if (focus >= 0 && !f.isEmpty() && !f.startsWith("chip.") && !f.startsWith("tool.") && !f.startsWith("action.") && !f.startsWith("top.")
			&& !f.startsWith("stepper.")
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
