package dev.nezo.burmaldaholic.games.roulette.client;

import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.client.table.fx.ChipButton;
import dev.nezo.burmaldaholic.client.table.fx.TableButton;
import dev.nezo.burmaldaholic.client.table.fx.TableChrome;
import dev.nezo.burmaldaholic.client.table.fx.TableClock;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.TableKit;
import dev.nezo.burmaldaholic.client.table.fx.TableTheme;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.dice.ChipStacks;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteTableBlockEntity;
import dev.nezo.burmaldaholic.games.roulette.logic.BetType;
import dev.nezo.burmaldaholic.games.roulette.logic.Layout;
import dev.nezo.burmaldaholic.games.roulette.logic.LayoutGrid;
import dev.nezo.burmaldaholic.games.roulette.logic.Racetrack;
import dev.nezo.burmaldaholic.games.roulette.logic.RouletteBallPath;
import dev.nezo.burmaldaholic.games.roulette.logic.Spot;
import dev.nezo.burmaldaholic.games.roulette.logic.Wheel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * European roulette (UI.md §7; docs/design/visual/tables.md §3, §6; animation/tables.md §1.4). The themed table:
 * mini wheel on the last result + history pills, the felt layout and the racetrack (call bets), stacked chips with a
 * ghost chip on the exact edge / intersection, the chip tray, icon buttons and the big Spin chip. The spin: the layout
 * dims in place and the pre-rendered wheel rises over it; the ball follows the shared {@link RouletteBallPath} from
 * the server's {@code (result, seed, spin_start)} and settles in the drawn pocket for every viewer. The result: the
 * dolly on the winning stack, gold outlines, the sweep, payout discs with the server's per-bet returns, the banner
 * and the shared celebration (NICE and up).
 *
 * <p>Shared time (the spin) ignores speed / skip; the local result beats respect {@code anim.speed} and skip on click
 * or Space. Late joiners past 85 % see the settled state. Reduced motion: no flights, a slow wheel, the ball fades in
 * its pocket. Only renders server state and sends actions.
 */
public class RouletteScreen extends CasinoTableScreen {
	private static final int[] CHIPS = ChipStacks.DENOMS;
	private static final int NMB_MS = 1000;
	private static final int RESULT_MS = 3000;

	private TableChrome.Frame frame = TableChrome.FULL;
	private int ox;
	private int oy;
	private TableTheme theme = TableTheme.VILLAGE;
	private final TableClock clock = new TableClock();
	private int selectedChip = 25;
	private boolean racetrackOn;
	private boolean rulesOn;
	private float partial;
	// hover
	private @Nullable Spot hover;
	private int hoverNumber = -1;
	private Racetrack.@Nullable Section hoverSection;
	// animation state
	private @Nullable RouletteBallPath path;
	private int pathSeq = -1;
	private int cueIndex;
	private long bellFor = Long.MIN_VALUE;
	private long resultFor = Long.MIN_VALUE;
	private long dollyPlayed = Long.MIN_VALUE;
	private long sweepPlayed = Long.MIN_VALUE;
	private boolean resultSkipped;
	private long resultLocalStart;
	private long localStartFor = Long.MIN_VALUE;
	private final List<Flight> flights = new ArrayList<>();
	private @Nullable Spot invalidSpot;
	private long invalidAt;
	private @Nullable Spot lastClick;
	private @Nullable Component error;
	private int errorTicks;
	private @Nullable TableButton spinButton;

	/** A chip flying from the tray to its drop point (local, 180 ms). */
	private record Flight(int denom, int x0, int y0, int x1, int y1, long start) {}

	public RouletteScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable(menu.tableType() == RouletteModule.HIGH_ROLLER_TABLE
			? "gui.burmaldaholic.roulette.title_high_roller" : "gui.burmaldaholic.roulette.title"), 427, 240);
		this.titleLabelY = -10_000;
	}

	// ---- geometry --------------------------------------------------------------------------------------------------

	private boolean compact() {
		return frame.compact();
	}

	private LayoutGrid grid() {
		return compact() ? LayoutGrid.COMPACT : LayoutGrid.BIG;
	}

	private int layoutX() {
		return ox + (compact() ? 64 : 110);
	}

	private int layoutY() {
		return oy + (compact() ? 32 : 36);
	}

	private boolean showTrack() {
		return !compact() || racetrackOn;
	}

	private boolean showLayout() {
		return !compact() || !racetrackOn;
	}

	private int trackX() {
		return ox + (compact() ? 3 : 110);
	}

	private int trackY() {
		return oy + (compact() ? 50 : 142);
	}

	private int wheelCx() {
		return ox + (compact() ? 142 : 213);
	}

	private int wheelCy() {
		return oy + (compact() ? 75 : 124);
	}

	// ---- lifecycle -------------------------------------------------------------------------------------------------

	@Override
	protected void init() {
		frame = TableChrome.frameFor(width, height);
		super.init();
		ox = (width - frame.w()) / 2;
		oy = (height - frame.h()) / 2;
		leftPos = ox;
		topPos = oy;
		rebuild();
	}

	@Override
	protected void onStateChanged(CompoundTag s) {
		clock.sync(s.getLongOr("game_time", 0));
		theme = TableKit.theme(theme());
		int seq = s.getIntOr("spin_seq", -1);
		int result = s.getIntOr("result", -1);
		if (result >= 0 && seq != pathSeq && ("spin".equals(phaseOf(s)) || "result".equals(phaseOf(s)))) {
			path = RouletteBallPath.of(result, s.getIntOr("seed", 0), Math.max(1, s.getIntOr("spin_ticks", 100)) * 50);
			pathSeq = seq;
			cueIndex = 0;
		}
		if (minecraft != null) {
			rebuild();
		}
	}

	private static String phaseOf(CompoundTag s) {
		return s.getStringOr("phase", "betting");
	}

	@Override
	public void showError(Component message) {
		super.showError(message);
		this.error = message;
		this.errorTicks = 60;
		if (lastClick != null && Util.getMillis() - invalidAt > 400) {
			invalidSpot = lastClick;
			invalidAt = Util.getMillis();
			FxSounds.play("ui_deny", 1f);
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (errorTicks > 0 && --errorTicks == 0) {
			error = null;
		}
	}

	// ---- state helpers ---------------------------------------------------------------------------------------------

	private boolean betting() {
		return "betting".equals(phase()) && state().getBooleanOr("enabled", true);
	}

	private long total() {
		return state().getLongOr("total", 0);
	}

	private int result() {
		return state().getIntOr("result", -1);
	}

	private int[] history() {
		return state().getIntArray("history").orElse(new int[0]);
	}

	private boolean multiplayer() {
		return state().getIntOr("bettors", 0) > 1 || state().getListOrEmpty("seats").size() > 1;
	}

	private double phaseMs() {
		return clock.msSince(state().getLongOr("phase_start", 0), partial);
	}

	private double spinMs() {
		return clock.msSince(state().getLongOr("spin_start", 0), partial);
	}

	/** Local time of the RESULT beats (anim.speed; skip jumps to the end). */
	private double resultMs() {
		long start = state().getLongOr("phase_start", 0);
		double shared = phaseMs();
		if (localStartFor != start) {
			localStartFor = start;
			resultSkipped = shared >= 0.85 * RESULT_MS; // late join: settled
			resultLocalStart = Util.getMillis() - (long) Math.max(0, shared);
		}
		if (resultSkipped) {
			return RESULT_MS * 2;
		}
		double speed = FxSettings.speed().pct / 100.0;
		return (Util.getMillis() - resultLocalStart) * speed;
	}

	private record Stack(Spot spot, long amount, long ret) {}

	private List<Stack> myStacks() {
		List<Stack> out = new ArrayList<>();
		ListTag list = state().getListOrEmpty("bets");
		long[] rets = state().getLongArray("bet_returns").orElse(null);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag b = list.getCompoundOrEmpty(i);
			int idx = i;
			Spot.parseKey(b.getStringOr("spot", "")).ifPresent(s -> out.add(new Stack(s, b.getLongOr("amount", 0),
				rets != null && idx < rets.length ? rets[idx] : -1)));
		}
		return out;
	}

	private Map<Spot, Long> spots(String key) {
		Map<Spot, Long> out = new LinkedHashMap<>();
		CompoundTag t = state().getCompoundOrEmpty(key);
		for (String k : t.keySet()) {
			Spot.parseKey(k).ifPresent(s -> out.put(s, t.getLongOr(k, 0)));
		}
		return out;
	}

	private static Component decode(@Nullable Tag tag) {
		if (tag == null) {
			return Component.empty();
		}
		var level = Minecraft.getInstance().level;
		var ops = level != null ? level.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
		return ComponentSerialization.CODEC.parse(ops, tag).result().orElse(Component.empty());
	}

	private int otherTint() {
		ListTag seats = state().getListOrEmpty("seats");
		for (int i = 0; i < seats.size(); i++) {
			CompoundTag s = seats.getCompoundOrEmpty(i);
			if (!s.getBooleanOr("you", false)) {
				return TableChrome.seatTint(s.getIntOr("index", 1));
			}
		}
		return TableChrome.seatTint(1);
	}

	// ---- widgets ---------------------------------------------------------------------------------------------------

	private void rebuild() {
		clearWidgets();
		long max = maxBet();
		if (selectedChip < minBet() || (max > 0 && selectedChip > max)) {
			for (int c : CHIPS) {
				if (c >= minBet() && (max <= 0 || c <= max)) {
					selectedChip = c;
					break;
				}
			}
		}
		boolean bet = betting();
		for (int i = 0; i < CHIPS.length; i++) {
			int d = CHIPS[i];
			int x = compact() ? ox + 4 + i * 25 : ox + 8 + i * 27;
			int y = compact() ? oy + 136 : oy + 212;
			ChipButton b = addRenderableWidget(new ChipButton(x, y, d, theme, !compact(), () -> selectedChip, v -> selectedChip = v));
			b.active = d >= minBet() && (max <= 0 || d <= max);
		}
		int ix = compact() ? ox + 132 : ox + 146;
		int iy = compact() ? oy + 138 : oy + 214;
		addRenderableWidget(TableButton.icon(ix, iy, "undo", Component.translatable("gui.burmaldaholic.roulette.button.undo"), theme,
			b -> sendAction("undo"))).active = bet && total() > 0;
		ix += 22;
		addRenderableWidget(TableButton.icon(ix, iy, "clear", Component.translatable("gui.burmaldaholic.roulette.clear_bets"), theme,
			b -> sendAction("clear"))).active = bet && total() > 0;
		ix += 22;
		if (compact()) {
			addRenderableWidget(TableButton.icon(ix, iy, "racetrack", Component.translatable("gui.burmaldaholic.roulette.racetrack.toggle"), theme,
				b -> racetrackOn = !racetrackOn));
		} else {
			addRenderableWidget(TableButton.icon(ix, iy, "rebet", Component.translatable("gui.burmaldaholic.common.rebet"), theme,
				b -> sendAction("rebet"))).active = bet && state().getBooleanOr("can_rebet", false);
			ix += 22;
			addRenderableWidget(TableButton.icon(ix, iy, "double", Component.translatable("gui.burmaldaholic.roulette.button.double"), theme,
				b -> sendAction("double"))).active = bet && total() > 0;
		}
		// top bar: rules + leave, left of the balance plaque
		int bw = TableChrome.balanceWidth(font, balance());
		int tx = ox + frame.w() - (compact() ? 2 : 4) - bw - 46;
		int ty = oy + (compact() ? -1 : 1);
		if (!compact()) {
			addRenderableWidget(TableButton.icon(tx, ty, "rules", Component.translatable("gui.burmaldaholic.common.rules"), theme, b -> rulesOn = !rulesOn));
			addRenderableWidget(TableButton.icon(tx + 22, ty, "leave", Component.translatable("gui.burmaldaholic.common.leave"), theme, b -> {
				sendAction("leave");
				onClose();
			})).active = mySeat() >= 0;
		}
		boolean ready = state().getBooleanOr("ready", false);
		Component spinLabel = Component.translatable(multiplayer() ? "gui.burmaldaholic.common.ready" : "gui.burmaldaholic.common.spin");
		int sx = compact() ? ox + 236 : ox + 373;
		int sy = compact() ? oy + 112 : oy + 189;
		spinButton = addRenderableWidget(TableButton.action(sx, sy, TableButton.Kind.SPIN, spinLabel, b -> sendAction("spin")));
		spinButton.active = bet && total() > 0 && !ready;
		spinButton.pulse(spinButton.active);
	}

	// ---- input -----------------------------------------------------------------------------------------------------

	private Optional<Spot> spotAt(double mx, double my) {
		if (!betting() || rulesOn || !showLayout()) {
			return Optional.empty();
		}
		return grid().hit(mx - layoutX(), my - layoutY());
	}

	private void updateHover(double mx, double my) {
		hover = spotAt(mx, my).orElse(null);
		hoverNumber = -1;
		hoverSection = null;
		if (betting() && !rulesOn && showTrack()) {
			hoverNumber = Racetrack.numberAt(mx - trackX(), my - trackY()).orElse(-1);
			hoverSection = Racetrack.sectionAt(mx - trackX(), my - trackY()).orElse(null);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			if ("result".equals(phase()) && !resultSkipped && resultMs() < RESULT_MS) {
				resultSkipped = true; // skip the local result beats (never the spin)
			}
			if (rulesOn && !super.mouseClicked(event, doubleClick)) {
				rulesOn = false;
				return true;
			}
			updateHover(event.x(), event.y());
			if (hover != null) {
				CompoundTag args = new CompoundTag();
				args.putString("type", hover.type().id());
				args.putIntArray("nums", hover.numbers().stream().mapToInt(Integer::intValue).toArray());
				args.putLong("amount", selectedChip);
				sendAction("bet", args);
				launchFlight(hover);
				return true;
			}
			if (hoverNumber >= 0) {
				CompoundTag args = new CompoundTag();
				args.putInt("n", hoverNumber);
				args.putLong("amount", selectedChip);
				sendAction("neighbours", args);
				launchFlight(Spot.of(BetType.STRAIGHT, hoverNumber));
				return true;
			}
			if (hoverSection != null) {
				CompoundTag args = new CompoundTag();
				args.putString("section", hoverSection.id());
				args.putLong("amount", selectedChip);
				sendAction("call", args);
				FxSounds.play("chip_place", 1f, 1f);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 32 /* GLFW space; lwjgl is not on the 26.3 compile path */ && "result".equals(phase())) {
			resultSkipped = true;
			return true;
		}
		return super.keyPressed(event);
	}

	private void launchFlight(Spot s) {
		lastClick = s;
		FxSounds.play("chip_place", 1f, (float) (0.9 + 0.1 * Math.log(selectedChip) / Math.log(5)));
		if (FxSettings.reduceMotion()) {
			return;
		}
		int[] c = grid().center(s);
		int i = 0;
		for (int k = 0; k < CHIPS.length; k++) {
			if (CHIPS[k] == selectedChip) {
				i = k;
			}
		}
		int x0 = compact() ? ox + 4 + i * 25 + 12 : ox + 8 + i * 27 + 12;
		int y0 = compact() ? oy + 146 : oy + 222;
		flights.add(new Flight(selectedChip, x0, y0, layoutX() + c[0], layoutY() + c[1] + 5, Util.getMillis()));
	}

	// ---- rendering: background -------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		partial = a;
		updateHover(mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, a);
		overlays(g);
		if (hover != null) {
			g.setComponentTooltipForNextFrame(font, tooltip(hover), mouseX, mouseY);
		} else if (hoverNumber >= 0) {
			g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.burmaldaholic.roulette.racetrack.neighbours", Texts.number(hoverNumber)),
				Component.translatable("gui.burmaldaholic.roulette.bet.straight").withColor(TableChrome.MUTED)), mouseX, mouseY);
		} else if (hoverSection != null) {
			g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.burmaldaholic.roulette.racetrack." + hoverSection.id())), mouseX,
				mouseY);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		partial = a;
		TableChrome.backdrop(g, theme, width, height);
		TableChrome.table(g, theme, frame, ox, oy);
		topBar(g);
		String ph = phase();
		int[] hist = history();
		double rMs = "result".equals(ph) ? resultMs() : RESULT_MS * 2;
		boolean newResultShown = !"result".equals(ph) || rMs >= 400;
		// mini wheel + history (the new result appears when the big wheel has sunk)
		int[] shownHist = hist;
		if ("result".equals(ph) && !newResultShown && hist.length > 0) {
			shownHist = java.util.Arrays.copyOfRange(hist, 1, hist.length);
		}
		if (!compact()) {
			RouletteWheelView.mini(g, theme, ox + 22, oy + 34, shownHist.length > 0 ? shownHist[0] : -1);
		}
		pills(g, shownHist, "result".equals(ph) ? rMs - 400 : 10_000);
		if (showLayout()) {
			TableGfx.region(g, TableGfx.sheet("roulette/layout" + (compact() ? "_compact_" : "_") + theme.id), grid().width(), grid().height(), 0, 0,
				grid().width(), grid().height(), layoutX(), layoutY(), 0xFFFFFFFF);
			layoutLabels(g);
		}
		if (showTrack()) {
			TableGfx.region(g, TableGfx.sheet("roulette/racetrack_" + theme.id), Racetrack.W, Racetrack.H, 0, 0, Racetrack.W, Racetrack.H, trackX(),
				trackY(), 0xFFFFFFFF);
			for (Racetrack.Section s : Racetrack.Section.values()) {
				Component l = Component.translatable("gui.burmaldaholic.roulette.racetrack." + s.id());
				int w = font.width(l);
				g.text(font, l, trackX() + s.labelX() - w / 2, trackY() + 18, theme.line, true);
			}
		}
		highlights(g, ph, rMs);
		chips(g, ph, rMs);
		flights(g);
		if ("result".equals(ph) && result() >= 0) {
			dolly(g, rMs);
			resultBanner(g, rMs);
		}
	}

	private void topBar(GuiGraphicsExtractor g) {
		int bx = TableChrome.balanceWidth(font, balance());
		int right = frame.w() - (compact() ? 2 : 4) - bx - (compact() ? 4 : 50);
		int x = TableChrome.title(g, font, theme, frame, ox, oy, title, compact() ? Math.min(140, right - 2) : 170); // 140: «Европейская рулетка» fits, never into the balance
		if (!compact()) {
			List<TableChrome.Seat> seats = new ArrayList<>();
			ListTag list = state().getListOrEmpty("seats");
			for (int i = 0; i < list.size(); i++) {
				CompoundTag s = list.getCompoundOrEmpty(i);
				int idx = s.getIntOr("index", i);
				seats.add(new TableChrome.Seat(Texts.raw(s.getStringOr("name", "")), s.getBooleanOr("you", false) ? "you" : "other", TableChrome.seatTint(idx)));
			}
			ListTag bots = state().getListOrEmpty("bot_names");
			for (int i = 0; i < bots.size(); i++) {
				seats.add(new TableChrome.Seat(decode(bots.get(i)), "bot", 0));
			}
			TableChrome.seats(g, font, theme, ox, oy, x + 6, seats, right);
		}
		TableChrome.balance(g, font, theme, frame, ox, oy, balance());
	}

	private void pills(GuiGraphicsExtractor g, int[] hist, double insertMs) {
		int n = compact() ? 6 : 8;
		double slide = FxSettings.reduceMotion() ? 1 : Math.max(0, Math.min(1, insertMs / 250.0));
		for (int i = 0; i < Math.min(n, hist.length); i++) {
			int v = hist[i];
			String col = Wheel.color(v).id();
			int x = compact() ? ox + 20 : ox + 22 + (i % 2) * 26;
			int y = compact() ? oy + 30 + i * 13 : oy + 112 + (i / 2) * 13;
			int alpha = 0xFFFFFFFF;
			if (i == 0 && slide < 1) {
				x -= (int) Math.round((1 - Ease.OUT_CUBIC.apply(slide)) * 16);
				alpha = TableGfx.fade(slide);
			}
			TableGfx.blit(g, "roulette/pill_" + col + (i == 0 ? "_new" : ""), x, y, 22, 11, alpha);
			g.text(font, Texts.number(v), x + 10, y + 2, TableChrome.BONE, true);
		}
	}

	private void layoutLabels(GuiGraphicsExtractor g) {
		LayoutGrid gr = grid();
		int y = layoutY() + gr.evenY() + (gr.outH() - 8) / 2 + 1;
		Component even = Component.translatable("gui.burmaldaholic.roulette.layout.even");
		Component odd = Component.translatable("gui.burmaldaholic.roulette.layout.odd");
		int cw = 2 * gr.cw();
		int ex = layoutX() + gr.zw() + cw + cw / 2;
		int oddX = layoutX() + gr.zw() + 4 * cw + cw / 2;
		g.text(font, TableChrome.fit(font, even, cw - 2), ex - Math.min(font.width(even), cw - 2) / 2, y, theme.line, true);
		g.text(font, TableChrome.fit(font, odd, cw - 2), oddX - Math.min(font.width(odd), cw - 2) / 2, y, theme.line, true);
	}

	private void cellSprite(GuiGraphicsExtractor g, String sprite, Layout.Rect r, int argb) {
		TableGfx.blit(g, sprite, layoutX() + r.x(), layoutY() + r.y(), r.w() + 1, r.h() + 1, argb);
	}

	private Layout.Rect boxOf(Spot s) {
		return s.type().inside() ? grid().cell(s.numbers().get(0)) : grid().outsideBox(s);
	}

	private void highlights(GuiGraphicsExtractor g, String ph, double rMs) {
		LayoutGrid gr = grid();
		if (showLayout() && hover != null) {
			if (hover.type().inside()) {
				for (int n : hover.numbers()) {
					cellSprite(g, "roulette/cell_hover", gr.cell(n), 0xFFFFFFFF);
				}
			} else {
				cellSprite(g, "roulette/cell_hover", gr.outsideBox(hover), 0xFFFFFFFF);
			}
		}
		Set<Integer> neighbour = hoverNumber >= 0 ? Set.copyOf(Racetrack.neighbours(hoverNumber, 2))
			: hoverSection != null ? Racetrack.covered(hoverSection) : Set.of();
		if (showLayout()) {
			for (int n : neighbour) {
				cellSprite(g, "roulette/cell_neighbour", gr.cell(n), 0xFFFFFFFF);
			}
		}
		if ("result".equals(ph) && result() >= 0 && rMs >= 400 && showLayout()) {
			int r = result();
			int k = 0;
			boolean flashes = FxSettings.flashes();
			List<Layout.Rect> boxes = new ArrayList<>();
			boxes.add(gr.cell(r));
			if (r != 0) {
				for (BetType t : List.of(BetType.DOZEN, BetType.COLUMN, BetType.RED, BetType.BLACK, BetType.EVEN, BetType.ODD, BetType.LOW, BetType.HIGH)) {
					for (Spot s : Spot.all(t)) {
						if (s.covers(r)) {
							boxes.add(gr.outsideBox(s));
						}
					}
				}
			}
			for (Layout.Rect b : boxes) {
				if (rMs >= 400 + 60 * k++) {
					if (flashes && !FxSettings.reduceMotion()) {
						cellSprite(g, "roulette/cell_win", b, 0xFFFFFFFF);
					} else {
						g.fill(layoutX() + b.x(), layoutY() + b.y(), layoutX() + b.x() + b.w() + 1, layoutY() + b.y() + 2, TableChrome.GOLD);
						g.fill(layoutX() + b.x(), layoutY() + b.y() + b.h() - 1, layoutX() + b.x() + b.w() + 1, layoutY() + b.y() + b.h() + 1, TableChrome.GOLD);
						g.fill(layoutX() + b.x(), layoutY() + b.y(), layoutX() + b.x() + 2, layoutY() + b.y() + b.h() + 1, TableChrome.GOLD);
						g.fill(layoutX() + b.x() + b.w() - 1, layoutY() + b.y(), layoutX() + b.x() + b.w() + 1, layoutY() + b.y() + b.h() + 1, TableChrome.GOLD);
					}
				}
			}
		}
	}

	/** Base point of a spot's stack in screen coordinates (the chip centre sits 5 px below the drop point). */
	private int[] stackPoint(Spot s) {
		int[] c = grid().center(s);
		return new int[] {layoutX() + c[0], layoutY() + c[1] + 5};
	}

	private void chips(GuiGraphicsExtractor g, String ph, double rMs) {
		if (!showLayout()) {
			return;
		}
		boolean result = "result".equals(ph) && result() >= 0;
		int r = result();
		int feltTop = oy + frame.feltY();
		// bots' virtual chips (steel, faded: never mistaken for real chips)
		for (Map.Entry<Spot, Long> e : spots("bot_chips").entrySet()) {
			int[] p = stackPoint(e.getKey());
			TableChrome.stack(g, e.getValue(), p[0] - 5, p[1], 0xFF90A4AE, 0x99FFFFFF);
		}
		int tint = otherTint();
		for (Map.Entry<Spot, Long> e : spots("others").entrySet()) {
			int[] p = stackPoint(e.getKey());
			double a = 1;
			int dy = 0;
			if (result && !e.getKey().covers(r)) {
				double k = Math.max(0, Math.min(1, (rMs - 650) / 260.0));
				dy = -(int) Math.round((p[1] - feltTop) * Ease.IN_CUBIC.apply(k));
				a = k > 0.7 ? 1 - (k - 0.7) / 0.3 : 1;
			}
			if (a > 0.02) {
				TableChrome.stack(g, e.getValue(), p[0] + 5, p[1] + dy, tint, TableGfx.fade(a));
			}
		}
		// the viewer's chips; during RESULT: sweep the losers, pay the winners, fly them to the balance
		int k = 0;
		long now = Util.getMillis();
		for (Stack s : myStacks()) {
			int[] p = stackPoint(s.spot());
			if (!result) {
				int dx = 0;
				TableChrome.stack(g, s.amount(), p[0] + dx, p[1], 0, 0xFFFFFFFF);
				TableChrome.stackValue(g, font, s.amount(), p[0] + dx, p[1], 0xFFFFFFFF);
				continue;
			}
			boolean win = s.ret() > 0 || (s.ret() < 0 && s.spot().covers(r));
			if (!win) {
				double start = 650 + 30 * Math.min(12, k++);
				double kk = Math.max(0, Math.min(1, (rMs - start) / 260.0));
				if (FxSettings.reduceMotion()) {
					kk = rMs >= start ? Math.min(1, (rMs - start) / 120.0) * 1.0001 : 0;
					if (kk < 1) {
						TableChrome.stack(g, s.amount(), p[0], p[1], 0, TableGfx.fade(1 - kk));
					}
					continue;
				}
				int dy = -(int) Math.round((p[1] - feltTop) * Ease.IN_CUBIC.apply(kk));
				double a = kk > 0.7 ? 1 - (kk - 0.7) / 0.3 : 1;
				if (kk < 1) {
					TableChrome.stack(g, s.amount(), p[0], p[1] + dy, 0, TableGfx.fade(a));
				}
				continue;
			}
			long ret = Math.max(0, s.ret());
			long payout = Math.max(0, ret - s.amount());
			double fly = Math.max(0, Math.min(1, (rMs - 1500) / 420.0));
			int bx = ox + frame.w() - 30;
			int by = oy + 10;
			int x = p[0];
			int y = p[1];
			if (fly > 0 && !FxSettings.reduceMotion()) {
				double e = Ease.IN_CUBIC.apply(fly);
				x = (int) Math.round(x + (bx - x) * e);
				y = (int) Math.round(y + (by - y) * e);
			}
			double a = fly >= 1 ? 0 : FxSettings.reduceMotion() && fly > 0 ? 1 - fly : 1;
			if (a <= 0) {
				continue;
			}
			TableChrome.stack(g, s.amount(), x, y, 0, TableGfx.fade(a));
			// payout discs pop in beside the bet: 1 per 40 ms from 1000 ms
			if (rMs >= 1000 && payout > 0) {
				int shown = FxSettings.reduceMotion() ? 5 : (int) Math.min(5, Math.max(0, (rMs - 1000) / 40 + 1));
				int[] discs = new int[ChipStacks.MAX_DISCS];
				int n = Math.min(shown, ChipStacks.discs(payout, discs));
				for (int d = 0; d < n; d++) {
					TableChrome.disc(g, discs[d], x + 8, y - 3 * d, TableGfx.fade(a));
				}
				if (fly <= 0) {
					double pop = FxSettings.reduceMotion() ? 1 : Math.min(1, (rMs - 1000) / 250.0);
					int ly = y - TableChrome.stackHeight(Math.max(s.amount(), payout)) - 16 - (int) Math.round(4 * (1 - Ease.OUT_BACK.apply(pop)));
					TableChrome.label(g, font, theme, Component.empty().append(Texts.raw("+")).append(Texts.number(payout)), x + 4, ly, pop);
				}
			}
		}
		if (result && rMs >= 650 && sweepPlayed != state().getLongOr("phase_start", 0)) {
			sweepPlayed = state().getLongOr("phase_start", 0);
			if (!resultSkipped) {
				FxSounds.play("chip_sweep", 1f);
			}
		}
		// ghost chip on the exact drop point; the invalid shake
		if (hover != null && betting()) {
			int[] p = stackPoint(hover);
			TableChrome.disc(g, ChipStacks.denomOf(selectedChip), p[0], p[1], 0x8CFFFFFF);
		}
		if (invalidSpot != null && now - invalidAt < 220) {
			int[] p = stackPoint(invalidSpot);
			int[] shake = {3, -3, 2, 0};
			int dx = shake[(int) Math.min(3, (now - invalidAt) / 55)];
			TableGfx.blit(g, "core/stack/chip_invalid", p[0] - 6 + dx, p[1] - 8, 12, 11);
		}
	}

	private void flights(GuiGraphicsExtractor g) {
		long now = Util.getMillis();
		flights.removeIf(f -> now - f.start() > 400);
		for (Flight f : flights) {
			double t = (now - f.start()) / 180.0;
			if (t >= 1) {
				continue; // landed: the state's stack takes over
			}
			double e = Ease.OUT_CUBIC.apply(t);
			double mx = (f.x0() + f.x1()) / 2.0;
			double my = (f.y0() + f.y1()) / 2.0 - 18;
			double x = (1 - e) * (1 - e) * f.x0() + 2 * (1 - e) * e * mx + e * e * f.x1();
			double y = (1 - e) * (1 - e) * f.y0() + 2 * (1 - e) * e * my + e * e * f.y1();
			TableChrome.disc(g, ChipStacks.denomOf(f.denom()), (int) Math.round(x), (int) Math.round(y), 0xFFFFFFFF);
		}
	}

	private void dolly(GuiGraphicsExtractor g, double rMs) {
		if (!showLayout() || rMs < 400) {
			return;
		}
		int r = result();
		Layout.Rect c = grid().cell(r);
		int cx = layoutX() + (r == 0 ? c.x() + 6 : c.cx());
		int cy = layoutY() + c.cy();
		double k = FxSettings.reduceMotion() ? 1 : Math.min(1, (rMs - 400) / 250.0);
		int drop = (int) Math.round(24 * (1 - Ease.OUT_BOUNCE.apply(k)));
		long ps = state().getLongOr("phase_start", 0);
		if (dollyPlayed != ps) {
			dollyPlayed = ps;
			if (!resultSkipped) {
				FxSounds.play("roulette_dolly", 1f);
			}
		}
		TableGfx.blit(g, "roulette/dolly_shadow", cx - 9, cy + 3, 12, 4, TableGfx.fade(0.3 + 0.7 * k));
		if (FxSettings.reduceMotion() || !FxSettings.flashes()) {
			TableGfx.frame(g, "roulette/dolly", 12, 14, 4, 0, cx - 10, cy - 12 - drop, 0xFFFFFFFF);
		} else {
			TableGfx.blit(g, "roulette/dolly", cx - 10, cy - 12 - drop, 12, 14);
		}
	}

	private void resultBanner(GuiGraphicsExtractor g, double rMs) {
		if (rMs < 1000) {
			return;
		}
		int r = result();
		double k = FxSettings.reduceMotion() ? 1 : Math.min(1, (rMs - 1000) / 250.0);
		long staked = state().getLongOr("out_staked", 0);
		long ret = state().getLongOr("out_return", 0);
		WinTier tier = WinTier.valueOf(state().getStringOr("tier", staked <= 0 ? "LOSS" : ret > staked ? "WIN" : ret > 0 ? "RETURN" : "LOSS"));
		Component line1 = Component.translatable("gui.burmaldaholic.roulette.result", RouletteTableBlockEntity.pocket(r),
			Component.translatable("gui.burmaldaholic.roulette.color." + Wheel.color(r).id()));
		Component line2 = null;
		int c2 = TableChrome.GOLD;
		String sprite = "banner_" + theme.id;
		if (staked > 0) {
			if (ret > staked) {
				boolean straight = false;
				for (Stack s : myStacks()) {
					straight |= s.spot().type() == BetType.STRAIGHT && s.ret() > 0;
				}
				line2 = straight ? Component.translatable("gui.burmaldaholic.roulette.fx.straight_up_win", Texts.number(ret - staked))
					: Component.translatable("gui.burmaldaholic.common.result.win", Texts.number(ret - staked));
				sprite = "banner_win";
				c2 = tierColor(tier);
			} else if (ret > 0) {
				line2 = Component.translatable("gui.burmaldaholic.fx.returned", Texts.number(ret));
				c2 = TableChrome.MUTED;
			} else {
				line2 = Component.translatable("gui.burmaldaholic.common.result.loss", Texts.number(staked));
				sprite = "banner_lose";
				c2 = 0xFFE08080;
			}
		}
		int w = compact() ? 150 : 150;
		int h = 26;
		int x = compact() ? ox + 67 : ox + 184;
		int y = compact() ? oy + 104 : oy + 148;
		int dy = (int) Math.round(10 * (1 - Ease.OUT_BACK.apply(k)));
		TableChrome.banner(g, font, sprite, x, y + dy, w, h, line1, TableChrome.BONE, line2, c2, k);
		// the shared celebration once the payout reached the balance (NICE and up)
		long ps = state().getLongOr("phase_start", 0);
		if (rMs >= 1500 && resultFor != ps && staked > 0) {
			resultFor = ps;
			if (tier.ordinal() >= WinTier.NICE.ordinal() && FxSettings.celebrations() != FxSettings.Celebrations.OFF) {
				CelebrationOverlay.get().play(CelebrationRequest.core(tier, ret, staked, state().getIntOr("seed", 0)));
			} else if (ret > staked) {
				FxSounds.play("win_small", 1f);
			} else if (ret > 0) {
				FxSounds.play("push", 1f);
			} else {
				FxSounds.play("lose", 1f);
			}
		}
	}

	private static int tierColor(WinTier t) {
		return switch (t) {
			case NICE -> 0xFF80FF40;
			case BIG, MEGA, EPIC, JACKPOT -> 0xFFFFD640;
			default -> 0xFFFFF1A0;
		};
	}

	private List<Component> tooltip(Spot spot) {
		List<Component> lines = new ArrayList<>();
		Component desc = describe(spot);
		Component payout = Component.translatable("gui.burmaldaholic.roulette.bet." + spot.type().id());
		lines.add(desc);
		if (!desc.getString().equals(payout.getString())) {
			lines.add(payout.copy().withColor(TableChrome.MUTED));
		}
		for (Stack s : myStacks()) {
			if (s.spot().equals(spot)) {
				lines.add(Component.translatable("gui.burmaldaholic.common.bet_amount", Texts.chips(s.amount())).withColor(TableChrome.GOLD));
			}
		}
		return lines;
	}

	/** "Split 17-20", "2nd dozen (13–24)", "Red (1:1)". */
	static Component describe(Spot s) {
		return switch (s.type()) {
			case STRAIGHT, SPLIT, STREET, TRIO, CORNER, SIX_LINE ->
				Component.translatable("gui.burmaldaholic.roulette.desc." + s.type().id(), Texts.raw(s.label()));
			case DOZEN, COLUMN -> Component.translatable("gui.burmaldaholic.roulette.desc." + s.type().id() + "." + s.outsideIndex());
			default -> Component.translatable("gui.burmaldaholic.roulette.bet." + s.type().id());
		};
	}

	// ---- rendering: over the widgets (the dim, the wheel, the banner, the status) ------------------------------------

	private void overlays(GuiGraphicsExtractor g) {
		String ph = phase();
		double pm = phaseMs();
		boolean spinView = "no_more_bets".equals(ph) || "spin".equals(ph) || ("result".equals(ph) && resultMs() < 400);
		if (spinView) {
			g.nextStratum();
			double dim;
			double wipe;
			if ("no_more_bets".equals(ph)) {
				wipe = FxSettings.reduceMotion() ? 1 : Math.min(1, pm / 400.0);
				dim = 1;
				if (bellFor != state().getLongOr("phase_start", 0)) {
					bellFor = state().getLongOr("phase_start", 0);
					if (pm < 500) {
						FxSounds.play("roulette_bell", 1f);
					}
				}
			} else if ("spin".equals(ph)) {
				wipe = 1;
				dim = 1;
			} else {
				wipe = 1;
				dim = 1 - Ease.IN_OUT_CUBIC.apply(resultMs() / 400.0);
			}
			int fx = ox + frame.feltX();
			int fw = (int) Math.round(frame.feltW() * wipe);
			g.fill(fx, oy + frame.feltY(), fx + fw, oy + frame.feltY() + frame.feltH(), TableGfx.alpha(0x78080410, dim));
			if (fw < frame.feltW() && fw > 0) {
				for (int i = 0; i < 12 && fw + i < frame.feltW(); i++) {
					g.fill(fx + fw + i, oy + frame.feltY(), fx + fw + i + 1, oy + frame.feltY() + frame.feltH(), TableGfx.alpha(0x78080410, dim * (1 - i / 12.0)));
				}
			}
			g.fill(ox, oy + frame.barY() - 1, ox + frame.w(), oy + frame.h(), TableGfx.alpha(0x5A080410, dim));
			// the wheel rises (600–1000 ms of NO_MORE_BETS) and sinks (0–400 ms of RESULT)
			double rise;
			if ("no_more_bets".equals(ph)) {
				rise = FxSettings.reduceMotion() ? (pm >= 600 ? 1 : 0) : Ease.IN_OUT_CUBIC.apply((pm - 600) / 400.0);
			} else if ("spin".equals(ph)) {
				rise = 1;
			} else {
				rise = 1 - Ease.IN_OUT_CUBIC.apply(resultMs() / 400.0);
			}
			RouletteBallPath p = path;
			if (p != null && rise > 0.01 && result() >= 0) {
				double t = "no_more_bets".equals(ph) ? 0 : "spin".equals(ph) ? spinMs() : p.spinMs + 1;
				if ("spin".equals(ph) && t >= 0.85 * p.spinMs && pathSeqJoinedLate()) {
					t = Math.max(t, p.spinMs);
				}
				if ("spin".equals(ph)) {
					cues(p, t);
				}
				if (compact()) {
					RouletteWheelView.compact(g, font, theme, p, t, wheelCx(), wheelCy(), rise, lastBefore());
				} else {
					RouletteWheelView.big(g, font, theme, p, t, wheelCx(), wheelCy(), rise, (int) Math.round(8 * (1 - rise)), lastBefore());
				}
			} else if (rise > 0.01 && !compact()) {
				// NO_MORE_BETS: the number is not drawn yet — the wheel rises at rest, the ball in the last pocket
				RouletteWheelView.big(g, font, theme, null, 0, wheelCx(), wheelCy(), rise, (int) Math.round(8 * (1 - rise)), lastBefore());
			}
			// "No more bets" banner slides down from the top of the panel
			double bk = "no_more_bets".equals(ph) ? (FxSettings.reduceMotion() ? 1 : Math.min(1, pm / 250.0)) : "spin".equals(ph) ? 1 : 1 - resultMs() / 400.0;
			if (bk > 0.02) {
				Component nmb = Component.translatable("gui.burmaldaholic.roulette.no_more_bets");
				int bw = Math.max(100, font.width(nmb) + 24);
				int by = oy + (compact() ? 14 : 24) - (int) Math.round(20 * (1 - Ease.OUT_BACK.apply(bk)));
				TableChrome.banner(g, font, "banner_" + theme.id, wheelCx() - bw / 2, by, bw, 20, nmb, TableChrome.GOLD, null, 0, Math.min(1, bk));
			}
		}
		status(g, ph);
		if (rulesOn) {
			rules(g);
		}
	}

	/** The number the wheel rested on before this spin (the history's head until RESULT adds the new one). */
	private int lastBefore() {
		int[] h = history();
		if ("result".equals(phase())) {
			return h.length > 1 ? h[1] : -1;
		}
		return h.length > 0 ? h[0] : -1;
	}

	private boolean joinedLate;
	private int lateFor = -2;

	/** A viewer who opened the screen after 85 % of the spin sees the settled wheel (§0.1). */
	private boolean pathSeqJoinedLate() {
		if (lateFor != pathSeq) {
			lateFor = pathSeq;
			joinedLate = path != null && spinMs() >= 0.85 * path.spinMs;
		}
		return joinedLate;
	}

	/** Plays the spin's sound cues crossed since the last frame (own screen: personal playback). */
	private void cues(RouletteBallPath p, double t) {
		List<RouletteBallPath.Cue> cues = p.cues();
		if (cueIndex == 0 && t > 400) {
			// opened mid-spin: skip the cues already past
			while (cueIndex < cues.size() && cues.get(cueIndex).atMs() < t - 100) {
				cueIndex++;
			}
		}
		while (cueIndex < cues.size() && cues.get(cueIndex).atMs() <= t) {
			RouletteBallPath.Cue c = cues.get(cueIndex++);
			FxSounds.play(c.sound(), 1f, c.pitch());
		}
	}

	private void status(GuiGraphicsExtractor g, String ph) {
		Component line1;
		Component line2 = null;
		int c1 = TableChrome.GOLD;
		switch (ph) {
			case "no_more_bets" -> line1 = Component.translatable("gui.burmaldaholic.roulette.no_more_bets");
			case "spin" -> {
				line1 = Component.translatable("gui.burmaldaholic.roulette.no_more_bets");
				line2 = Component.translatable("msg.burmaldaholic.roulette.spinning");
			}
			case "result" -> {
				line1 = null;
			}
			default -> {
				if (!state().getBooleanOr("enabled", true)) {
					line1 = Component.translatable("gui.burmaldaholic.error.disabled");
					c1 = TableChrome.RED;
				} else {
					line1 = Component.translatable("gui.burmaldaholic.roulette.place_bets");
				}
				line2 = total() > 0 ? Component.translatable("gui.burmaldaholic.common.total_bet", Texts.number(total())) : limitsLine();
				if (multiplayer() && state().getIntOr("bettors", 0) > 0 && total() > 0) {
					line2 = Component.translatable("gui.burmaldaholic.roulette.ready_count", Texts.number(state().getIntOr("ready_count", 0)),
						Texts.number(state().getIntOr("bettors", 0)));
				}
			}
		}
		if (error != null) {
			line1 = error;
			c1 = TableChrome.RED;
		}
		if (compact()) {
			if (line1 != null && !"spin".equals(ph)) {
				var seq = TableChrome.fit(font, line1, total() > 0 ? 150 : 250);
				int cx = total() > 0 ? ox + 90 : ox + 142;
				g.text(font, seq, cx - font.width(seq) / 2, oy + 112, c1, true);
			}
			if (total() > 0) {
				// right end of the status row (the bottom bar is full: chips, icons, Spin)
				Component bet = Component.translatable("gui.burmaldaholic.common.bet_amount", Texts.number(total()));
				var seq = TableChrome.fit(font, bet, 80);
				g.text(font, seq, ox + 226 - font.width(seq), oy + 112, TableChrome.GOLD, true);
			}
		} else {
			TableChrome.status(g, font, ox + 296, oy + 211, 150, line1, line2, c1);
			long left = state().getLongOr("ticks_left", -1);
			if ("betting".equals(ph) && left >= 0 && multiplayer()) {
				double leftSec = Math.max(0, left / 20.0 - clock.msSince(state().getLongOr("game_time", 0), partial) / 1000.0);
				TableChrome.timer(g, ox + 352, oy + 213, leftSec, Math.max(1, state().getIntOr("phase_ticks", 500)) / 20.0, !FxSettings.reduceMotion());
			}
		}
		// bots' virtual bets line, small, above the bottom bar
		Tag line = state().get("bot_line");
		if (line != null && !compact() && "betting".equals(ph)) {
			var seq = TableChrome.fit(font, decode(line), 250);
			g.text(font, seq, ox + 110, oy + 190, TableChrome.MUTED, true);
		}
	}

	private void rules(GuiGraphicsExtractor g) {
		g.nextStratum();
		int x = ox + frame.feltX() + 20;
		int y = oy + frame.feltY() + 12;
		int w = frame.feltW() - 40;
		TableGfx.blit(g, "core/plate_" + theme.id, x, y, w, 70);
		int ty = y + 8;
		for (int i = 1; i <= 2; i++) {
			for (var seq : font.split(Component.translatable("gui.burmaldaholic.roulette.rules." + i), w - 16)) {
				g.text(font, seq, x + 8, ty, TableChrome.BONE, true);
				ty += 10;
			}
			ty += 4;
		}
		var seq = TableChrome.fit(font, limitsLine(), w - 16);
		g.text(font, seq, x + 8, ty, TableChrome.GOLD, true);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		// the chrome draws the title, status and error line (CasinoTableScreen's plain labels are hidden)
	}
}
