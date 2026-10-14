package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.fx.TableClock;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.TableKit;
import dev.nezo.burmaldaholic.client.table.fx.TableTheme;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.client.ui.v2.ResultBanner;
import dev.nezo.burmaldaholic.client.ui.v2.V2TableScreen;
import dev.nezo.burmaldaholic.client.ui.v2.ValuePill;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Racetrack;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteBallPath;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteFlow;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteLayout;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * European roulette, table UI v2 (REDESIGN.md §7.5; docs/architecture/ui-v2.md; mockups {@code v2_roulette_*}). The
 * felt fills the screen: the wheel column on the left (the wheel, the ball on the shared {@link RouletteBallPath},
 * "Last numbers"), the betting layout on the right with every inside bet reachable by precise hit-testing (cell =
 * straight, line = split, outer edge = street / six line, intersection = corner, zero edges = trio / first four), the
 * call bets (Tier / Orphelins / Voisins / Zero) under it; the kit's TopBar and Dock (chips, Undo / Clear / Rebet / ×2,
 * the bet readout, the status, the gold primary: Sit down / Spin / Ready / Rebet). The spin keeps the layout on screen
 * (dimmed) with every bet visible; the result frames the winning cell in gold, sweeps the losing stacks, pays the winning
 * ones with the server's per-bet returns and shows the net in the banner + the TopBar delta.
 *
 * <p>Only renders server state and sends actions ({@code bet}, {@code call}, {@code undo}, {@code clear}, {@code rebet},
 * {@code double}, {@code spin}, {@code sit}); every action goes through the kit's acknowledgement watchdog.
 */
public class RouletteScreen extends V2TableScreen<RouletteView> {
	private static final int RESULT_MS = 3000;
	/** HitMap id of the whole betting layout. */
	static final String FELT_ALL = "felt.layout";

	private final TableClock clock = new TableClock();
	private TableTheme theme = TableTheme.VILLAGE;
	private @Nullable RouletteLayout layout;
	private float partial;
	// the spin
	private @Nullable RouletteBallPath path;
	private int pathSeq = -1;
	private int cueIndex;
	private long bellFor = Long.MIN_VALUE;
	private long resultFor = Long.MIN_VALUE;
	private long localStartFor = Long.MIN_VALUE;
	private long resultLocalStart;
	private boolean resultSkipped;
	private boolean joinedLate;
	private int lateFor = -2;
	// the bet just placed: its value tag shows for 1.5 s (§2.2)
	private @Nullable Spot lastPlaced;
	private long lastPlacedAt;
	/** the layout point of the click being routed (precise hit-testing of the felt), null for keyboard presses */
	private double @Nullable [] clickAt;
	/** what the status hint currently describes (hover readout), "" = the flow's hint */
	private String hoverShown = "";
	/** Repeat pressed during the result (DES roulette P1): the last slip is sent the moment betting opens */
	private boolean rebetQueued;
	/** own stacks that carried a value pill in the last frame (real-flow tests) */
	private int valuePills;
	/** above this many own stacks the value pills show on hover only (DES roulette P1) */
	static final int PILLS_ALWAYS_MAX = 8;

	public RouletteScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(menu.tableType() == RouletteModule.HIGH_ROLLER_TABLE
			? "gui.burmaldaholic.roulette.title_high_roller" : "gui.burmaldaholic.roulette.title"));
		chrome.bet.multiSpot(true);
	}

	// ---- the pure parts ------------------------------------------------------------------------------------------------

	@Override
	protected RouletteView view(CompoundTag s) {
		clock.sync(s.getLongOr("game_time", 0));
		theme = TableKit.theme(theme());
		boolean seated = s.getIntOr("seat", -1) >= 0;
		int occupied = s.getListOrEmpty("seats").size();
		int seatCount = s.getIntOr("seat_count", 0);
		RouletteFlow.Phase phase = RouletteFlow.Phase.of(s.getStringOr("phase", "betting"));
		Map<Spot, Long> mine = mineOf(s);
		long total = s.getLongOr("total", 0);
		long rebet = rebetAmount(s);
		long ticks = s.getLongOr("ticks_left", -1);
		boolean multi = s.getIntOr("bettors", 0) > 1 || occupied > 1;
		int secs = ticks < 0 || (phase == RouletteFlow.Phase.BETTING && !multi) ? -1 : (int) Math.ceil(ticks / 20.0);
		RouletteFlow.View v = new RouletteFlow.View(seated, !seated && seatCount > 0 && occupied >= seatCount, s.getBooleanOr("enabled", true), phase,
			s.getLongOr("min", 1), s.getLongOr("max", 0), s.getLongOr("min_total", 0), s.getLongOr("balance", 0), total,
			s.getBooleanOr("can_rebet", false), rebet, s.getBooleanOr("ready", false), multi, s.getIntOr("ready_count", 0), s.getIntOr("bettors", 0), secs,
			rebetQueued && phase == RouletteFlow.Phase.RESULT);
		// the spin's ball path (rebuilt only for a new spin)
		int seq = s.getIntOr("spin_seq", -1);
		int result = s.getIntOr("result", -1);
		if (result >= 0 && seq != pathSeq && (phase == RouletteFlow.Phase.SPIN || phase == RouletteFlow.Phase.RESULT)) {
			path = RouletteBallPath.of(result, s.getIntOr("seed", 0), Math.max(1, s.getIntOr("spin_ticks", 100)) * 50);
			pathSeq = seq;
			cueIndex = 0;
		}
		return RouletteView.of(v, mine);
	}

	private static Map<Spot, Long> mineOf(CompoundTag s) {
		Map<Spot, Long> out = new LinkedHashMap<>();
		ListTag list = s.getListOrEmpty("bets");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompoundOrEmpty(i);
			Spot.parseKey(b.getStringOr("spot", "")).ifPresent(sp -> out.merge(sp, b.getLongOr("amount", 0), Long::sum));
		}
		return out;
	}

	/** The total of the server's last slip: what Rebet places. */
	private static long rebetAmount(CompoundTag s) {
		return s.getLongOr("rebet_total", 0);
	}

	@Override
	protected Flow<RouletteView> flow() {
		return RouletteFlow.FLOW;
	}

	@Override
	protected @Nullable BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				// multi-spot: a chip only arms; placing happens on the felt (onTarget)
			}

			@Override
			public void tool(String toolId) {
				sendTracked(toolId, new CompoundTag(), "", 0);
			}
		};
	}

	// ---- layout + input -----------------------------------------------------------------------------------------------

	@Override
	protected void layoutPlay(TableGrid grid, RouletteView v, HitMap hits) {
		RouletteLayout l = RouletteLayout.of(grid);
		layout = l;
		boolean open = RouletteFlow.betBlock(v.flow()).isEmpty();
		// the whole layout first (a click anywhere is hit-tested precisely), then every spot's own box on top (keyboard,
		// narration, real-flow tests: the centre of each box is that spot)
		hits.add(FELT_ALL, l.layout, open);
		for (Spot s : RouletteLayout.allSpots()) hits.add(RouletteLayout.spotId(s), l.target(s), open);
		for (Racetrack.Section s : Racetrack.Section.values()) hits.add(RouletteLayout.callId(s), l.calls.get(s.ordinal()), open);
		// the banner never covers a number cell (the winning one stays visible), the wheel or the history (R-CDE M4)
		chrome.banner.regions(l.bannerRegions(), List.of());
		chrome.banner.obstacles(l.bannerObstacles());
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press button) {
		RouletteView v = view;
		RouletteLayout l = layout;
		if (v == null || l == null || !id.startsWith("felt.")) return false;
		if (button.left() && "result".equals(phase()) && !resultSkipped) {
			resultSkipped = true; // a click skips the local result beats (never the shared spin)
		}
		Optional<Gate> block = RouletteFlow.betBlock(v.flow());
		if (block.isPresent()) {
			chrome.refuse(block.get());
			return true;
		}
		if (!button.left()) return true;
		long d = chrome.bet.armOrDefault();
		if (d <= 0) {
			chrome.refuse(Gate.off("chip", RouletteFlow.K + "bet", "gui.burmaldaholic.core.v2.reason.not_enough", v.flow().min()));
			return true;
		}
		Gate chip = RouletteFlow.chip(v.flow(), d);
		if (!chip.enabled()) {
			chrome.refuse(chip);
			return true;
		}
		String rest = id.substring(5);
		if (rest.equals("layout") && (clickAt == null || l.hit(clickAt[0], clickAt[1]).isEmpty())) return true;
		if (rest.startsWith("call.")) {
			Optional<Racetrack.Section> sec = Racetrack.Section.byId(rest.substring(5));
			if (sec.isEmpty()) return false;
			CompoundTag args = new CompoundTag();
			args.putString("section", sec.get().id());
			args.putLong("amount", d);
			sendTracked("call", args, "", d);
			FxSounds.play("chip_place", 1f, 1f);
			return true;
		}
		double[] at = clickAt;
		Optional<Spot> spot = at != null ? l.hit(at[0], at[1]) : rest.equals("layout") ? Optional.empty() : Spot.parseKey(rest);
		if (spot.isEmpty()) return rest.equals("layout");
		CompoundTag args = new CompoundTag();
		args.putString("type", spot.get().type().id());
		args.putIntArray("nums", spot.get().numbers().stream().mapToInt(Integer::intValue).toArray());
		args.putLong("amount", d);
		sendTracked("bet", args, spot.get().key(), d);
		lastPlaced = spot.get();
		lastPlacedAt = Util.getMillis();
		FxSounds.play("chip_place", 1f, (float) (0.9 + 0.1 * Math.log(d) / Math.log(5)));
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		clickAt = new double[] {event.x(), event.y()};
		try {
			return super.mouseClicked(event, doubleClick);
		} finally {
			clickAt = null;
		}
	}

	@Override
	public void onGate(String id, Gate gate) {
		if ("spin".equals(gate.action())) {
			sendTracked("spin");
			return;
		}
		if (BetGates.REBET.equals(gate.action()) && "result".equals(phase())) {
			// the server takes bets only while betting is open: queue the slip, skip the local result beats
			rebetQueued = true;
			resultSkipped = true;
			if (view != null) view = view(state());
			refresh();
			return;
		}
		super.onGate(id, gate);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (!rebetQueued) return;
		String ph = phase();
		if ("betting".equals(ph)) {
			rebetQueued = false;
			if (state().getLongOr("total", 0) == 0 && state().getBooleanOr("can_rebet", false)) sendTracked(BetGates.REBET);
		} else if (!"result".equals(ph)) {
			rebetQueued = false;
		}
	}

	/** Own stacks that carried a value pill in the last frame (real-flow tests). */
	public int valuePillsDrawn() {
		return valuePills;
	}

	/** Whether a Repeat pressed during the result waits for betting to open. */
	public boolean rebetQueued() {
		return rebetQueued;
	}

	// ---- state helpers -------------------------------------------------------------------------------------------------

	private int result() {
		return state().getIntOr("result", -1);
	}

	private int[] history() {
		return state().getIntArray("history").orElse(new int[0]);
	}

	private double phaseMs() {
		return clock.msSince(state().getLongOr("phase_start", 0), partial);
	}

	private double spinMs() {
		return clock.msSince(state().getLongOr("spin_start", 0), partial);
	}

	private double resultMs() {
		long start = state().getLongOr("phase_start", 0);
		double shared = phaseMs();
		if (localStartFor != start) {
			localStartFor = start;
			resultSkipped = shared >= 0.85 * RESULT_MS;
			resultLocalStart = Util.getMillis() - (long) Math.max(0, shared);
		}
		if (resultSkipped) return RESULT_MS * 2;
		return (Util.getMillis() - resultLocalStart) * (FxSettings.speed().pct / 100.0);
	}

	private Map<Spot, Long> spots(String key) {
		Map<Spot, Long> out = new LinkedHashMap<>();
		CompoundTag t = state().getCompoundOrEmpty(key);
		for (String k : t.keySet()) Spot.parseKey(k).ifPresent(s -> out.put(s, t.getLongOr(k, 0)));
		return out;
	}

	private int lastBefore() {
		int[] h = history();
		if ("result".equals(phase())) return h.length > 1 ? h[1] : -1;
		return h.length > 0 ? h[0] : -1;
	}

	// ---- drawing -------------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, RouletteView v, int mouseX, int mouseY, float a) {
		partial = a;
		RouletteLayout l = layout;
		if (l == null || l.grid.play.w() != grid.play.w() || l.grid.play.h() != grid.play.h()) {
			l = RouletteLayout.of(grid);
			layout = l;
		}
		String ph = phase();
		boolean betting = RouletteFlow.betBlock(v.flow()).isEmpty();
		double rMs = "result".equals(ph) ? resultMs() : RESULT_MS * 2;
		// hover: the spot / call under the mouse lights its numbers
		Spot hover = betting ? l.hit(mouseX, mouseY).orElse(null) : null;
		Racetrack.Section hoverCall = betting ? l.callAt(mouseX, mouseY).orElse(null) : null;
		Set<Integer> lit = new HashSet<>();
		Spot litOutside = null;
		if (hover != null) {
			if (hover.type().inside()) lit.addAll(hover.numbers());
			else litOutside = hover;
		} else if (hoverCall != null) {
			lit.addAll(Racetrack.covered(hoverCall));
		}
		boolean spinView = "no_more_bets".equals(ph) || "spin".equals(ph);
		int win = "result".equals(ph) && rMs >= 400 ? result() : spinView && path != null && path.settled(spinMs()) ? result() : -1;
		RouletteFeltView.layout(g, font, l, spinView ? 1 : 0, lit, litOutside, win);
		RouletteFeltView.calls(g, font, l, hoverCall, betting);
		// the wheel column
		int[] hist = history();
		boolean newShown = !"result".equals(ph) || rMs >= 400;
		int[] shownHist = "result".equals(ph) && !newShown && hist.length > 0 ? java.util.Arrays.copyOfRange(hist, 1, hist.length) : hist;
		wheel(g, l, ph);
		RouletteFeltView.history(g, font, l, shownHist, "result".equals(ph) ? Math.max(0, Math.min(1, (rMs - 400) / 250.0)) : 1);
		stacks(g, l, v, ph, rMs, win);
		// ghost chip + hover readout
		String hoverKey = "";
		if (hover != null) {
			long d = chrome.bet.selected();
			int[] p = l.anchor(hover);
			if (d > 0) ChipStack.drawPending(g, new Box(p[0] - l.chip / 2, p[1] - l.chip / 2, l.chip, l.chip), d);
			long on = v.mine().getOrDefault(hover, 0L);
			hoverKey = hover.key() + "/" + on;
			if (!hoverKey.equals(hoverShown)) {
				Component desc = describe(hover);
				chrome.status.hint(on > 0 ? Component.translatable("gui.burmaldaholic.roulette.v2.hover", desc, Texts.number(on),
					Texts.number(hover.type().payout())) : Component.translatable("gui.burmaldaholic.roulette.v2.hover_empty", desc,
					Texts.number(hover.type().payout())));
			}
		} else if (hoverCall != null) {
			long d = Math.max(0, chrome.bet.selected());
			hoverKey = "call/" + hoverCall.id() + "/" + d;
			if (!hoverKey.equals(hoverShown)) {
				long all = d * Racetrack.spots(hoverCall).size();
				chrome.status.hint(Component.translatable("gui.burmaldaholic.roulette.v2.call_hover",
					Component.translatable("gui.burmaldaholic.roulette.racetrack." + hoverCall.id()), Texts.number(d), Texts.number(all)));
			}
		}
		if (hoverKey.isEmpty() && !hoverShown.isEmpty()) refresh(); // back to the flow's own hint
		hoverShown = hoverKey;
		hoverSpot = hover;
		if ("result".equals(ph) && result() >= 0) resultBeats(rMs);
	}

	private void wheel(GuiGraphicsExtractor g, RouletteLayout l, String ph) {
		int cx = l.wheel.cx();
		int cy = l.wheel.cy();
		RouletteBallPath p = path;
		boolean spinning = ("spin".equals(ph) || "result".equals(ph)) && p != null && result() >= 0;
		if ("no_more_bets".equals(ph) && bellFor != state().getLongOr("phase_start", 0)) {
			bellFor = state().getLongOr("phase_start", 0);
			if (phaseMs() < 500) FxSounds.play("roulette_bell", 1f);
		}
		if (!spinning) {
			RouletteWheelView.medium(g, font, theme, null, 0, cx, cy, l.wheelD, lastBefore());
			return;
		}
		double t = "spin".equals(ph) ? spinMs() : p.spinMs + 1;
		if ("spin".equals(ph)) {
			if (lateFor != pathSeq) {
				lateFor = pathSeq;
				joinedLate = t >= 0.85 * p.spinMs;
			}
			if (joinedLate) t = Math.max(t, p.spinMs);
			cues(p, t);
		}
		RouletteWheelView.medium(g, font, theme, p, t, cx, cy, l.wheelD, lastBefore());
	}

	private void cues(RouletteBallPath p, double t) {
		List<RouletteBallPath.Cue> cues = p.cues();
		if (cueIndex == 0 && t > 400) {
			while (cueIndex < cues.size() && cues.get(cueIndex).atMs() < t - 100) cueIndex++;
		}
		while (cueIndex < cues.size() && cues.get(cueIndex).atMs() <= t) {
			RouletteBallPath.Cue c = cues.get(cueIndex++);
			FxSounds.play(c.sound(), 1f, c.pitch());
		}
	}

	/** Everybody's chips: bots (steel, faded), other players (their seat colour), the viewer's (with value tags). */
	private void stacks(GuiGraphicsExtractor g, RouletteLayout l, RouletteView v, String ph, double rMs, int win) {
		boolean result = "result".equals(ph) && result() >= 0;
		int r = result();
		for (Map.Entry<Spot, Long> e : spots("bot_chips").entrySet()) RouletteFeltView.stack(g, l, e.getKey(), e.getValue(), 0xFF90A4AE, 0.6);
		for (Map.Entry<Spot, Long> e : spots("others").entrySet()) {
			double a = result && !e.getKey().covers(r) ? 1 - Math.max(0, Math.min(1, (rMs - 650) / 260.0)) : 1;
			RouletteFeltView.stack(g, l, e.getKey(), e.getValue(), 0xFFFFB74D, a);
		}
		ListTag list = state().getListOrEmpty("bets");
		long[] rets = state().getLongArray("bet_returns").orElse(null);
		List<Box> placedTags = new ArrayList<>();
		Map<Spot, Long> mine = v.mine();
		var pending = chrome.pending();
		// value pills on every own stack while betting (hover / just placed only when crowded)
		boolean pillsAll = !result && list.size() <= PILLS_ALWAYS_MAX;
		int pills = 0;
		for (int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompoundOrEmpty(i);
			Optional<Spot> sp = Spot.parseKey(b.getStringOr("spot", ""));
			if (sp.isEmpty()) continue;
			Spot s = sp.get();
			long amount = b.getLongOr("amount", 0);
			long ret = rets != null && i < rets.length ? rets[i] : -1;
			int n = Math.min(RouletteLayout.MAX_DISCS, ChipStack.discs(amount).size());
			Box box = l.stack(s, n);
			Box disc = new Box(box.x(), box.y(), l.chip, box.h());
			if (!result) {
				ChipStack.draw(g, disc, amount);
				boolean showTag = pillsAll || s.equals(lastPlaced) && Util.getMillis() - lastPlacedAt < 1500 || isHover(l, s);
				if (showTag && tag(g, l, s, n, Texts.number(amount), ValuePill.Style.YOU, placedTags)) pills++;
				continue;
			}
			boolean wins = ret > 0 || (ret < 0 && s.covers(r));
			if (!wins) {
				double k = Math.max(0, Math.min(1, (rMs - 650) / 260.0));
				if (k < 1) ChipStack.draw(g, disc, amount, (float) (1 - k));
				continue;
			}
			ChipStack.draw(g, disc, amount);
			// the winning stack glows; its payout tag pops at 1000 ms
			g.fill(disc.x() - 1, disc.y() - 1, disc.right() + 1, disc.y(), 0xC0FFD640);
			if (rMs >= 1000 && ret > 0) tag(g, l, s, n, Texts.raw("+").append(Texts.number(ret - amount)), ValuePill.Style.WIN, placedTags);
		}
		valuePills = pills;
		if (pending != null && !pending.spot().isEmpty()) {
			Spot.parseKey(pending.spot()).ifPresent(s -> ChipStack.drawPending(g, l.stack(s, 1), pending.amount()));
		}
	}

	private @Nullable Spot hoverSpot;

	private boolean isHover(RouletteLayout l, Spot s) {
		return s.equals(hoverSpot);
	}

	private boolean tag(GuiGraphicsExtractor g, RouletteLayout l, Spot s, int discs, Component text, ValuePill.Style style, List<Box> placed) {
		int w = ValuePill.width(font, text);
		Optional<Box> b = l.tag(s, discs, w, 11, placed);
		if (b.isEmpty()) return false;
		placed.add(b.get());
		ValuePill.draw(g, font, b.get(), text, style);
		return true;
	}

	/**
	 * The result beats: the banner + TopBar delta + the celebration (NICE and up) once the payout landed; a viewer with
	 * no bet on this spin sees the number ("17 RED") in the banner instead (R-CDE M4).
	 */
	private void resultBeats(double rMs) {
		long ps = state().getLongOr("phase_start", 0);
		long staked = state().getLongOr("out_staked", 0);
		long ret = state().getLongOr("out_return", 0);
		if (resultFor == ps) return;
		if (staked <= 0) {
			if (rMs < 400) return;
			resultFor = ps;
			chrome.banner.show(numberWord(result()), 0, ResultBanner.SHOW_MS);
			return;
		}
		if (rMs < 1000) return;
		resultFor = ps;
		long net = ret - staked;
		chrome.banner.show(net);
		chrome.top.balance.delta(net);
		WinTier tier = WinTier.valueOf(state().getStringOr("tier", net > 0 ? "WIN" : ret > 0 ? "RETURN" : "LOSS"));
		if (tier.ordinal() >= WinTier.NICE.ordinal() && FxSettings.celebrations() != FxSettings.Celebrations.OFF) {
			CelebrationOverlay.get().play(CelebrationRequest.core(tier, ret, staked, state().getIntOr("seed", 0)));
		} else if (net > 0) {
			FxSounds.play("win_small", 1f);
		} else if (ret > 0) {
			FxSounds.play("push", 1f);
		} else {
			FxSounds.play("lose", 1f);
		}
	}

	/** "17 RED", "0 ZERO": the banner word of a spin for a viewer without a bet. */
	public static Component numberWord(int n) {
		return Component.translatable("gui.burmaldaholic.roulette.v2.result", Texts.number(n),
			Component.translatable("gui.burmaldaholic.roulette.v2.board.color." + Wheel.color(n).id()));
	}

	/** "Split 17-20", "2nd dozen (13–24)", "Red (1:1)". */
	static Component describe(Spot s) {
		return switch (s.type()) {
			case FIRST_FOUR -> Component.translatable("gui.burmaldaholic.roulette.bet.first_four");
			case STRAIGHT, SPLIT, STREET, TRIO, CORNER, SIX_LINE ->Component.translatable("gui.burmaldaholic.roulette.desc." + s.type().id(), Texts.raw(s.label()));
			case DOZEN, COLUMN -> Component.translatable("gui.burmaldaholic.roulette.desc." + s.type().id() + "." + s.outsideIndex());
			case RED -> Component.translatable("gui.burmaldaholic.roulette.color.red");
			case BLACK -> Component.translatable("gui.burmaldaholic.roulette.color.black");
			case ODD -> Component.translatable("gui.burmaldaholic.roulette.short.odd");
			case EVEN -> Component.translatable("gui.burmaldaholic.roulette.short.even");
			case LOW -> Texts.raw("1–18");
			case HIGH -> Texts.raw("19–36");
		};
	}
}
