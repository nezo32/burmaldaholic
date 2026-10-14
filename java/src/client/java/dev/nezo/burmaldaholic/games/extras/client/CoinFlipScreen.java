package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.games.extras.logic.CoinFlip;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.anim.CoinAnim;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasFlow;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasKeys;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasLayout;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasView;
import dev.nezo.burmaldaholic.games.extras.present.LocalBet;
import dev.nezo.burmaldaholic.games.extras.present.LocalBets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Coin Flip, table UI v2 (REDESIGN.md §7.7 "Coin Flip: coin centred, Heads / Tails as two big toggle buttons"; lane E):
 * the kit's TopBar and Dock (BetBar; primary <b>Flip</b> {@code Space}; pawn stakes swap the BetBar for −/+) and the
 * parlour scene: the coin on its pad in the middle of the green fill, the options column (Your call: Heads / Tails
 * toggles — keys {@code H} / {@code T} —, the stake kind, Last flips pips, the tally, the payout). The toss is the
 * {@link CoinAnim} storyboard onto the server's face; click the coin or Enter skips. Hardcore Soul Wager: the red button
 * leads to the warning card, the type-to-confirm word and the 5-second hold (server-enforced).
 *
 * <p>Hit map ids: {@code felt.heads}, {@code felt.tails}, {@code felt.coin} (skip), {@code felt.stake},
 * {@code felt.soul}, plus the kit's ids.
 */
final class CoinFlipScreen extends ExtrasSceneScreen<ExtrasView> implements dev.nezo.burmaldaholic.client.fx.ClientFx.CelebrationGate {
	private static final Identifier SPIN = Kit.sheet("extras/coin_spin");
	private static final Identifier GLINT = Kit.sheet("extras/coin_glint");
	private static final Identifier HEAT = Kit.sheet("extras/coin_heat");
	private static final Identifier PIPS = Kit.sheet("extras/chain_pips");
	private static final Identifier MINI = Kit.sheet("extras/coin_mini");
	/** This machine's bet in the client session (per position, R-CDE m5). */
	private final LocalBet bet;
	private static final BetControl PAWN = new BetControl(true, 1);
	private static CoinFlip.Side call = CoinFlip.Side.HEADS;
	/** Last flips of this session: {heads 0/1, won 0/1}, oldest first. */
	private static final List<int[]> HISTORY = new ArrayList<>();

	private enum Mode {
		NORMAL, WARN, TYPE, HOLD
	}

	private Mode mode = Mode.NORMAL;
	private CoinFlip.Side soulSide = CoinFlip.Side.HEADS;
	private EditBox word;
	private String typed = "";
	private boolean holding;
	private long holdStartMs;
	private int shownSeq;
	private long animStart = -1;
	private boolean landedDone = true;
	private int landSounds;
	private boolean whooshed;
	private ExtrasLayout.Scene scene;

	CoinFlipScreen(CompoundTag state) {
		super("coin", Component.translatable("gui.burmaldaholic.extras.coin.title"), state);
		this.bet = LocalBets.of("coin", LocalBets.HAND);
		shownSeq = state.getCompoundOrEmpty("result").getIntOr("seq", -1);
		if (bet.amount() <= 0) bet.add(Math.max(1, state.getLongOr("min", 1)), state.getLongOr("max", 0), state.getLongOr("balance", 0));
		chrome.keys(KeyMap.standard().bind(KeyMap.H, KeyMap.Ids.felt("heads")).bind(KeyMap.T, KeyMap.Ids.felt("tails")));
	}

	// ---- the pure parts ------------------------------------------------------------------------------------------------

	private boolean chips() {
		return PAWN.kind().equals(BetControl.CHIPS);
	}

	@Override
	protected ExtrasView view(CompoundTag s) {
		PAWN.validate(s);
		boolean busy = flipping();
		String block = mode != Mode.NORMAL ? ExtrasKeys.REASON_SOUL : null;
		if (!chips()) {
			return ExtrasView.pawn(busy, block, PAWN.kind().equals(BetControl.ITEM) ? s.getLongOr("held_value", 0) : PAWN.amount(), PAWN.max(s));
		}
		bet.clamp(s.getLongOr("max", 0), s.getLongOr("balance", 0));
		double payout = s.getDoubleOr("payout", CoinFlip.DEFAULT_PAYOUT);
		long upTo = bet.amount() + Payouts.floorPay(bet.amount(), payout);
		return ExtrasView.chips(bet.view(s.getLongOr("min", 1), s.getLongOr("max", 0), shownBalance(), busy), busy, block, upTo);
	}

	@Override
	protected Flow<ExtrasView> flow() {
		return ExtrasFlow.COIN;
	}

	@Override
	protected void refresh() {
		super.refresh();
		if (view != null && !chips() && !view.busy()) chrome.status.hint(Component.translatable(ExtrasKeys.HINT_STAKE, PAWN.shown(state())));
	}

	@Override
	protected BetPort betPort() {
		return new BetPort() {
			@Override
			public void add(long denom) {
				bet.add(denom, state().getLongOr("max", 0), shownBalance());
				local();
			}

			@Override
			public void remove(long denom) {
				bet.undo();
				local();
			}

			@Override
			public void tool(String id) {
				long max = state().getLongOr("max", 0);
				long bal = shownBalance();
				switch (id) {
					case BetGates.UNDO -> bet.undo();
					case BetGates.CLEAR -> bet.clear();
					case BetGates.REBET -> bet.rebet(max, bal);
					case BetGates.DOUBLE -> bet.dbl(max, bal);
					default -> {}
				}
				local();
			}
		};
	}

	@Override
	public void onGate(String id, Gate gate) {
		switch (gate.action()) {
			case "flip" -> {
				CompoundTag args;
				if (chips()) {
					args = new CompoundTag();
					args.putString("stake", "chips");
					args.putLong("amount", bet.amount());
					bet.sent();
				} else {
					args = PAWN.args();
				}
				args.putString("side", call.id());
				send("flip", args);
			}
			case ExtrasFlow.MINUS -> {
				PAWN.step(state(), -1);
				local();
			}
			case ExtrasFlow.PLUS -> {
				PAWN.step(state(), 1);
				local();
			}
			default -> super.onGate(id, gate);
		}
	}

	// ---- the toss ------------------------------------------------------------------------------------------------------

	@Override
	protected void onStateChanged(CompoundTag oldState, CompoundTag newState) {
		CompoundTag r = newState.getCompoundOrEmpty("result");
		int seq = r.getIntOr("seq", -1);
		if (seq != shownSeq && seq >= 0) {
			shownSeq = seq;
			bet.ack(); // the server played the amount: it becomes Rebet
			if (!landedDone) finishToss(false);
			animStart = Util.getMillis();
			landedDone = false;
			landSounds = 0;
			whooshed = false;
			holdBalance(oldState.getLongOr("balance", newState.getLongOr("balance", 0)));
			ClientCasinoState.holdBalanceDelta((int) (durationMs() + 200));
		}
	}

	private boolean heads() {
		return "heads".equals(state().getCompoundOrEmpty("result").getStringOr("landed", "heads"));
	}

	private boolean won() {
		return state().getCompoundOrEmpty("result").getBooleanOr("win", false);
	}

	private boolean soulResult() {
		return state().getCompoundOrEmpty("result").getBooleanOr("soul", false);
	}

	private double durationMs() {
		return Kit.reduceMotion() ? CoinAnim.REDUCED_MS : CoinAnim.TOTAL_MS * 100.0 / Kit.speedPct();
	}

	private double tossMs() {
		if (animStart < 0 || landedDone) return -1;
		double ms = Util.getMillis() - animStart;
		return Kit.reduceMotion() ? ms : ms * Kit.speedPct() / 100.0;
	}

	private boolean flipping() {
		return animStart >= 0 && !landedDone;
	}

	@Override
	public boolean holdsCelebration(String game) {
		return dev.nezo.burmaldaholic.games.extras.server.ExtrasGames.COIN.equals(game) && flipping();
	}

	private boolean skip() {
		if (!flipping()) return false;
		finishToss(true);
		return true;
	}

	private void finishToss(boolean viaSkip) {
		landedDone = true;
		releaseBalance();
		HISTORY.add(new int[] {heads() ? 1 : 0, won() ? 1 : 0});
		while (HISTORY.size() > 10) HISTORY.removeFirst();
		dev.nezo.burmaldaholic.client.fx.ClientFx.releaseCelebration();
		if (!won()) FxSounds.play("lose", 0.6f, 1f);
		CompoundTag r = state().getCompoundOrEmpty("result");
		Component side = Component.translatable("gui.burmaldaholic.extras.coin." + (heads() ? "heads" : "tails"));
		chrome.status.hint(won() ? Component.translatable("gui.burmaldaholic.extras.coin.result_win", side, Texts.chips(r.getLongOr("net", 0)))
			: Component.translatable("gui.burmaldaholic.extras.coin.result_lose", side));
		if (!r.getBooleanOr("pawn", false) && !soulResult()) chrome.banner.show(r.getLongOr("net", 0));
		local();
	}

	@Override
	public void tick() {
		super.tick();
		holdTick();
		double ms = tossMs();
		if (ms < 0) return;
		boolean rm = Kit.reduceMotion();
		if (!rm && !whooshed && ms >= CoinAnim.ANTICIPATION_MS + 86) {
			whooshed = true;
			FxSounds.play("coin_whoosh", 0.3f, 1f);
		}
		if (!rm) {
			while (landSounds < 2 && ms >= CoinAnim.FLIGHT_END_MS + CoinAnim.CONTACTS_MS[landSounds]) {
				FxSounds.play("coin_land", landSounds == 0 ? 1f : 0.4f, 1f);
				landSounds++;
			}
		}
		if (ms >= (rm ? CoinAnim.REDUCED_MS : CoinAnim.TOTAL_MS)) finishToss(false);
	}

	@Override
	public void onClose() {
		if (flipping()) finishToss(true);
		super.onClose();
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		int k = event.key();
		if ((k == KeyMap.ENTER || k == 335 || k == KeyMap.SPACE) && flipping() && skip()) return true;
		if (mode == Mode.TYPE && word != null && word.isFocused() && k != KeyMap.ESCAPE) return word.keyPressed(event);
		return super.keyPressed(event);
	}

	// ---- layout ---------------------------------------------------------------------------------------------------------

	private Box sideBox(int i) {
		return scene.row(14, i);
	}

	private Box stakeBox() {
		return sideBox(2);
	}

	private Box soulBox() {
		Box side = scene.side();
		return new Box(side.x(), side.bottom() - scene.row(), side.w(), scene.row());
	}

	private int coinX() {
		return scene.object().cx();
	}

	private int coinY() {
		return scene.object().cy() - 14;
	}

	/** The soul wager card over the scene. */
	private Box card() {
		Box o = scene.object();
		int w = Math.min(240, scene.side().x() - 12);
		return Box.centredAt(o.cx(), o.cy(), w, Math.min(164, o.h() + 10));
	}

	@Override
	protected void layoutPlay(TableGrid grid, ExtrasView v, HitMap hits) {
		scene = ExtrasLayout.coin(grid);
		chrome.banner.regions(List.of(new Box(scene.object().x() - 40, coinY() + 36, scene.object().w() + 80, 40)), List.of());
		clearWidgets();
		if (mode != Mode.NORMAL) {
			soulWidgets();
			return;
		}
		hits.add(KeyMap.Ids.felt("coin"), Box.centredAt(coinX(), coinY(), 70, 90), true);
		hits.add(KeyMap.Ids.felt("heads"), sideBox(0), !v.busy());
		hits.add(KeyMap.Ids.felt("tails"), sideBox(1), !v.busy());
		if (PAWN.kinds(state()).size() > 1) hits.add(KeyMap.Ids.felt("stake"), stakeBox(), !v.busy());
		if (state().getBooleanOr("soul", false)) hits.add(KeyMap.Ids.felt("soul"), soulBox(), !v.busy());
	}

	@Override
	public boolean onTarget(String id, boolean enabled, int button) {
		if (id.equals(KeyMap.Ids.felt("coin"))) return skip();
		if (!enabled && id.startsWith("felt.")) {
			chrome.refuse(Gate.off(id, ExtrasKeys.YOUR_CALL, ExtrasKeys.REASON_FLIPPING));
			return true;
		}
		switch (id) {
			case "felt.heads" -> call = CoinFlip.Side.HEADS;
			case "felt.tails" -> call = CoinFlip.Side.TAILS;
			case "felt.stake" -> PAWN.nextKind(state());
			case "felt.soul" -> mode = Mode.WARN;
			default -> {
				return false;
			}
		}
		Kit.vanilla("block.stone_button.click_on", 0.3f, 1.2f);
		local();
		return true;
	}

	private void soulWidgets() {
		Box c = card();
		int cx = c.cx();
		switch (mode) {
			case WARN -> {
				add(new KitButton(cx - 90, c.bottom() - 30, 86, 20, Component.translatable("gui.burmaldaholic.common.confirm"), KitButton.Style.DANGER, b -> {
					mode = Mode.TYPE;
					typed = "";
					local();
				}));
				add(new KitButton(cx + 4, c.bottom() - 30, 86, 20, Component.translatable("gui.burmaldaholic.common.cancel"), KitButton.Style.SECONDARY,
					b -> cancelSoul()));
			}
			case TYPE -> {
				word = new EditBox(font, cx - 90, c.y() + 70, 180, 20, Component.translatable("gui.burmaldaholic.extras.soul_confirm_word"));
				word.setMaxLength(24);
				word.setValue(typed);
				word.setResponder(t -> typed = t);
				addRenderableWidget(word);
				setInitialFocus(word);
				add(new KitButton(cx - 90, c.bottom() - 30, 86, 20, Component.translatable("gui.burmaldaholic.common.confirm"), KitButton.Style.DANGER, b -> {
					if (wordMatches(typed)) {
						mode = Mode.HOLD;
						local();
					} else {
						b.shake();
						showError(Component.translatable("gui.burmaldaholic.extras.soul.confirm_prompt",
							Component.translatable("gui.burmaldaholic.extras.soul_confirm_word")));
					}
				}));
				add(new KitButton(cx + 4, c.bottom() - 30, 86, 20, Component.translatable("gui.burmaldaholic.common.cancel"), KitButton.Style.SECONDARY,
					b -> cancelSoul()));
			}
			case HOLD -> {
				for (CoinFlip.Side side : CoinFlip.Side.values()) {
					int x = side == CoinFlip.Side.HEADS ? cx - 90 : cx + 4;
					add(new KitButton(x, c.y() + 30, 86, 20, Component.translatable(side.key()), side == soulSide ? KitButton.Style.PRIMARY
						: KitButton.Style.SECONDARY, b -> {
							soulSide = side;
							local();
						})).selected(side == soulSide);
				}
				add(new KitButton(cx - 43, c.bottom() - 26, 86, 20, Component.translatable("gui.burmaldaholic.common.cancel"), KitButton.Style.SECONDARY,
					b -> cancelSoul()));
			}
			default -> {}
		}
	}

	private KitButton add(KitButton b) {
		addRenderableWidget(b);
		return b;
	}

	static boolean wordMatches(String typed) {
		String t = typed.trim().toLowerCase(Locale.ROOT);
		return !t.isEmpty() && (t.equals(I18n.get("gui.burmaldaholic.extras.soul_confirm_word").toLowerCase(Locale.ROOT))
			|| t.equals("deal") || t.equals("сделка"));
	}

	private void cancelSoul() {
		holding = false;
		mode = Mode.NORMAL;
		sendRaw("soul_cancel", new CompoundTag());
		local();
	}

	// ---- soul hold -----------------------------------------------------------------------------------------------

	private Box holdBar() {
		Box c = card();
		return new Box(c.cx() - 90, c.y() + 70, 180, 20);
	}

	private double holdProgress() {
		return holding ? Math.min(1, (Util.getMillis() - holdStartMs) / 5000.0) : 0;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (mode == Mode.HOLD && event.button() == 0 && holdBar().contains(event.x(), event.y())) {
			holding = true;
			holdStartMs = Util.getMillis();
			sendRaw("soul_arm", new CompoundTag());
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (holding) {
			holding = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	private int lastHeartbeat = -1;

	private void holdTick() {
		if (mode != Mode.HOLD || !holding) return;
		long ms = Util.getMillis() - holdStartMs;
		int beat = ms < 3000 ? (int) (ms / 1000) : 3 + (int) ((ms - 3000) / 500);
		if (beat != lastHeartbeat) {
			lastHeartbeat = beat;
			FxSounds.play("heartbeat", 1f, (float) (1 + 0.25 * Math.min(1, ms / 5000.0)));
		}
		if (ms >= 5000) {
			holding = false;
			CompoundTag args = new CompoundTag();
			args.putString("side", soulSide.id());
			mode = Mode.NORMAL;
			send("soul", args);
			local();
		}
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, ExtrasView v, int mouseX, int mouseY, float partial) {
		if (scene == null) scene = ExtrasLayout.coin(grid);
		SceneKit.fill(g, grid.play, "coin");
		boolean soul = mode == Mode.HOLD || (soulResult() && animStart >= 0);
		if (soul) V2Draw.fill(g, grid.play, 0x40600000);
		int cx = coinX();
		int cy = coinY();
		Kit.sprite(g, Kit.extras("coin_pad"), cx - 72, cy + 32, 144, 40, soul ? 0xFF6A3A2A : 0xFFFFFFFF);
		CoinAnim.Pose pose = pose();
		int sw = (int) Math.round(28 * pose.shadow() + 8);
		Kit.sprite(g, Kit.extras("coin_shadow"), cx - sw / 2, cy + 46, sw, Math.max(3, sw / 4), Kit.fade(0.55 * pose.shadowAlpha()));
		if (pose.airborne() && !Kit.reduceMotion()) {
			double ms = tossMs();
			coin(g, CoinAnim.toss(heads(), won(), Math.max(CoinAnim.ANTICIPATION_MS, ms - 120), false), 0.14, soul);
			coin(g, CoinAnim.toss(heads(), won(), Math.max(CoinAnim.ANTICIPATION_MS, ms - 60), false), 0.28, soul);
		}
		coin(g, pose, 1, soul);
		if (pose.airborne() && !Kit.reduceMotion()) {
			long t = Util.getMillis() / 90;
			int[][] pts = {{-40, -68}, {38, -54}, {30, -82}, {-30, -24}};
			for (int i = 0; i < pts.length; i++) {
				Kit.sprite(g, Kit.core("fx/sparkle"), cx + pts[i][0] - 3, cy + pts[i][1] - 3, 7, 7, Kit.fade(0.5 + 0.5 * Math.sin(t + i)));
			}
		}
		CompoundTag r = state().getCompoundOrEmpty("result");
		if (r.getIntOr("seq", -1) >= 0 && pose.landed()) {
			Component side = Component.translatable("gui.burmaldaholic.extras.coin." + (heads() ? "heads" : "tails"));
			double pop = landedDone ? 1 : pose.name();
			if (pop >= 0) {
				double sc = Kit.reduceMotion() ? 1 : dev.nezo.burmaldaholic.core.anim.Ease.OUT_BACK.apply(Math.min(1, pop));
				Kit.big(g, font, side, cx, cy + 74, (float) (2 * sc), won() ? Kit.GOLD : Kit.BONE, Kit.INK);
			}
		}
		if (mode == Mode.NORMAL) side(g, v, mouseX, mouseY);
		else soulCard(g);
	}

	private CoinAnim.Pose pose() {
		if (animStart < 0) return state().getCompoundOrEmpty("result").getIntOr("seq", -1) >= 0 ? CoinAnim.terminal(heads()) : CoinAnim.IDLE;
		if (landedDone) return CoinAnim.terminal(heads());
		return CoinAnim.toss(heads(), won(), tossMs(), Kit.reduceMotion());
	}

	private void coin(GuiGraphicsExtractor g, CoinAnim.Pose p, double alpha, boolean soul) {
		g.pose().pushMatrix();
		g.pose().translate(coinX(), (float) (coinY() + p.dy()));
		g.pose().scale((float) p.scaleX(), (float) p.scaleY());
		int tint = soul ? 0xFFB05050 : 0xFFFFFFFF;
		Kit.region(g, SPIN, 768, 64, p.frame() * 64, 0, 64, 64, -32, -32, 64, 64, Kit.alpha(tint, alpha));
		if (soul && alpha >= 1) Kit.region(g, HEAT, 128, 64, 0, 0, 64, 64, -32, -32, 64, 64, Kit.fade(0.8));
		if (alpha >= 1 && p.glint() >= 0 && p.glint() < 1 && (p.frame() == 0 || p.frame() == 11)) {
			int f = Math.min(3, (int) (p.glint() * 4));
			Kit.region(g, GLINT, 256, 64, f * 64, 0, 64, 64, -32, -32);
		}
		g.pose().popMatrix();
	}

	private void side(GuiGraphicsExtractor g, ExtrasView v, int mx, int my) {
		Box side = scene.side();
		CompoundTag s = state();
		SceneKit.caption(g, font, Component.translatable(ExtrasKeys.YOUR_CALL), side, side.y() + 2);
		for (CoinFlip.Side c : CoinFlip.Side.values()) {
			Box b = sideBox(c == CoinFlip.Side.HEADS ? 0 : 1);
			SceneKit.toggle(g, font, b, Component.translatable(c.key()), c == call, !v.busy(), mx, my);
			Kit.region(g, MINI, 28, 14, c == CoinFlip.Side.HEADS ? 0 : 14, 0, 14, 14, b.x() + 4, b.y() + (b.h() - 14) / 2);
		}
		int y = side.y() + 14 + 2 * (scene.row() + 2) + 4;
		if (PAWN.kinds(s).size() > 1) {
			SceneKit.toggle(g, font, stakeBox(), PAWN.kindLabel(), !chips(), !v.busy(), mx, my);
			y += scene.row() + 2;
		}
		SceneKit.caption(g, font, Component.translatable(ExtrasKeys.LAST_FLIPS), side, y);
		List<int[]> hist = new ArrayList<>(HISTORY);
		if (flipping()) {
			if (hist.size() >= 10) hist.removeFirst();
			hist.add(null);
		}
		int heads = 0;
		int tails = 0;
		int pitch = Math.min(19, (side.w() + 3) / 5);
		for (int i = 0; i < 10; i++) {
			int pip = 0;
			if (i < hist.size()) {
				int[] h = hist.get(i);
				if (h == null) pip = 1;
				else {
					pip = h[0] == 1 ? (h[1] == 1 ? 2 : 3) : (h[1] == 1 ? 4 : 5);
					if (h[0] == 1) heads++;
					else tails++;
				}
			}
			Kit.frame(g, PIPS, 96, 16, 16, 16, pip, side.x() + (i % 5) * pitch, y + 12 + (i / 5) * 18, 0xFFFFFFFF);
		}
		y += 12 + 36 + 2;
		V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.extras.coin.tally", Texts.number(heads), Texts.number(tails)),
			new Box(side.x(), y, side.w(), 10), 0, 1, CasinoPalette.V2_TEXT, true);
		double payout = s.getDoubleOr("payout", CoinFlip.DEFAULT_PAYOUT);
		if (y + 24 <= soulBox().y() || !s.getBooleanOr("soul", false)) {
			V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.extras.coin.pays", Texts.decimal(Payouts.formatMultiplier(1 + payout))),
				new Box(side.x(), y + 12, side.w(), 10), 0, 1, CasinoPalette.V2_MUTED, true);
		}
		if (s.getBooleanOr("soul", false)) {
			dev.nezo.burmaldaholic.client.ui.v2.V2Button.draw(g, font, soulBox(),
				Component.translatable("gui.burmaldaholic.extras.soul.button").withStyle(ChatFormatting.BOLD), null,
				dev.nezo.burmaldaholic.client.ui.v2.V2Sprites.ButtonStyle.DANGER, !v.busy(), soulBox().contains(mx, my), false, false);
		}
	}

	private void soulCard(GuiGraphicsExtractor g) {
		Box c = card();
		g.fill(c.x(), c.y(), c.right(), c.bottom(), 0xE0200810);
		Kit.region(g, Kit.sheet("core/fx/vignette_red"), 256, 256, 0, 0, 256, 256, c.x(), c.y(), c.w(), c.h(), 0xFFFFFFFF);
		Kit.frameRect(g, c.x(), c.y(), c.w(), c.h(), Kit.RED);
		int cx = c.cx();
		switch (mode) {
			case WARN -> {
				Kit.big(g, font, Component.translatable("gui.burmaldaholic.extras.soul.warning_title").withStyle(ChatFormatting.BOLD), cx, c.y() + 12, 2,
					Kit.RED_LIGHT, Kit.INK);
				Kit.wrapCentered(g, font, Component.translatable("gui.burmaldaholic.extras.soul.warning", Texts.chips(state().getLongOr("soul_value", 0))),
					cx, c.y() + 40, c.w() - 20, 6, Kit.BONE, true);
			}
			case TYPE -> Kit.wrapCentered(g, font, Component.translatable("gui.burmaldaholic.extras.soul.confirm_prompt",
				Component.translatable("gui.burmaldaholic.extras.soul_confirm_word").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)), cx, c.y() + 20,
				c.w() - 20, 4, Kit.BONE, true);
			case HOLD -> {
				Box hb = holdBar();
				Kit.sprite(g, Kit.core("menu/progress"), hb.x(), hb.y(), hb.w(), 20);
				int fill = (int) Math.round((hb.w() - 8) * holdProgress());
				if (fill > 0) {
					Kit.sprite(g, Kit.core("menu/progress_fill_red"), hb.x() + 4, hb.y() + 4, fill, 12);
					if (!Kit.reduceMotion()) {
						double pulse = 0.5 + 0.5 * Math.sin(Util.getMillis() / 120.0);
						g.fill(hb.x() + 3 + fill, hb.y() + 3, hb.x() + 6 + fill, hb.y() + 17, Kit.alpha(0xFFFF6020, 0.6 + 0.4 * pulse));
					}
				}
				Kit.centeredFit(g, font, Component.translatable("gui.burmaldaholic.extras.soul.hold"), cx, hb.bottom() + 6, c.w() - 20, Kit.BONE, true);
			}
			default -> {}
		}
	}
}
