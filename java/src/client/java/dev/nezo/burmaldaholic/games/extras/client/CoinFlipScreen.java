package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.CoinFlip;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.anim.CoinAnim;
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
 * Coin Flip (UI.md §9; visual/extras.md §3, mockup {@code extras_coin_flip.png}; extras-pvp.md §1.2): the card-room
 * scene, "Your call" (Heads / Tails with the mini coins) and the bet well on the left, the last flips (won / lost
 * pips), tally, streak and pay preview on the right, and the Lucky Coin in the middle over its baize pad. A result
 * starts the toss of {@link CoinAnim}: anticipation, flight (spinning with ghost frames, shadow shrinking), two
 * vertical bounces on the server's face, the glint on a win and the face name popping; then the shared celebration.
 * Skip (click on the coin, Space, Enter) or reduce motion jump to / play the same terminal pose. The chip counter and
 * the HUD delta are held until the landing.
 *
 * <p>Hardcore Soul Wager (Stakes): the red button leads to the warning, the type-to-confirm word and the 5-second hold
 * (the server enforces it); the coin is tinted blood-red with the heat rim and the hold bar is an ember progress bar.
 */
final class CoinFlipScreen extends SceneScreen implements dev.nezo.burmaldaholic.client.fx.ClientFx.CelebrationGate {
	private static final Identifier SPIN = Kit.sheet("extras/coin_spin");
	private static final Identifier GLINT = Kit.sheet("extras/coin_glint");
	private static final Identifier HEAT = Kit.sheet("extras/coin_heat");
	private static final Identifier PIPS = Kit.sheet("extras/chain_pips");
	private static final Identifier MINI = Kit.sheet("extras/coin_mini");
	private static final int CX = 200;
	private static final int CY = 116;
	private static long lastAmount = 1;
	private static CoinFlip.Side call = CoinFlip.Side.HEADS;
	/** Last flips of this session: {heads 0/1, won 0/1}, oldest first (visual/extras.md §3.2 "Last flips"). */
	private static final List<int[]> HISTORY = new ArrayList<>();

	private enum Mode {
		NORMAL, WARN, TYPE, HOLD
	}

	private final BetControl bet;
	private Mode mode = Mode.NORMAL;
	private CoinFlip.Side soulSide = CoinFlip.Side.HEADS;
	private EditBox word;
	private String typed = "";
	private boolean holding;
	private long holdStartMs;
	private int shownSeq;
	private long animStart = -1;
	private boolean skipped;
	private boolean landedDone = true;
	private int landSounds;
	private boolean whooshed;
	private boolean flicked;

	CoinFlipScreen(CompoundTag state) {
		super("coin", Component.translatable("gui.burmaldaholic.extras.coin.title"), state, Scene.COIN);
		this.bet = new BetControl(true, lastAmount);
		shownSeq = state.getCompoundOrEmpty("result").getIntOr("seq", -1);
	}

	@Override
	protected void onStateChanged(CompoundTag oldState, CompoundTag newState) {
		CompoundTag r = newState.getCompoundOrEmpty("result");
		int seq = r.getIntOr("seq", -1);
		if (seq != shownSeq && seq >= 0) {
			shownSeq = seq;
			if (!landedDone) finishToss(false); // overrun: the previous toss ends at once
			animStart = Util.getMillis();
			skipped = false;
			landedDone = false;
			landSounds = 0;
			whooshed = false;
			flicked = false;
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

	/** Toss time in the unscaled storyboard (ms), or −1 when no toss is running. */
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

	@Override
	protected boolean skip() {
		if (!flipping()) return false;
		finishToss(true);
		return true;
	}

	/** The landing: history pip, balance released, celebration for the server tier. */
	private void finishToss(boolean viaSkip) {
		landedDone = true;
		skipped = viaSkip;
		releaseBalance();
		HISTORY.add(new int[] {heads() ? 1 : 0, won() ? 1 : 0});
		while (HISTORY.size() > 10) HISTORY.removeFirst();
		// the celebration is the server's (tier computed there, ExtrasGames.celebrate), held until this landing
		dev.nezo.burmaldaholic.client.fx.ClientFx.releaseCelebration();
		if (!won()) {
			FxSounds.play("lose", 0.6f, 1f);
		}
		if (minecraft != null) rebuildWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		double ms = tossMs();
		if (ms < 0) return;
		boolean rm = Kit.reduceMotion();
		if (!rm && !flicked && ms >= CoinAnim.ANTICIPATION_MS) {
			flicked = true;
		}
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

	// ---- layout ------------------------------------------------------------------------------------------------

	@Override
	protected void layout() {
		CompoundTag s = state();
		bet.validate(s);
		switch (mode) {
			case NORMAL -> {
				boolean busy = flipping();
				KitButton.Icon headsIcon = new KitButton.Icon(MINI, 28, 14, 0, 0, 14, 14);
				KitButton.Icon tailsIcon = new KitButton.Icon(MINI, 28, 14, 14, 0, 14, 14);
				button(22, 58, 96, 20, Component.translatable(CoinFlip.Side.HEADS.key()), call == CoinFlip.Side.HEADS ? KitButton.Style.PRIMARY
					: KitButton.Style.SECONDARY, b -> choose(CoinFlip.Side.HEADS)).selected(call == CoinFlip.Side.HEADS).icon(headsIcon);
				button(22, 82, 96, 20, Component.translatable(CoinFlip.Side.TAILS.key()), call == CoinFlip.Side.TAILS ? KitButton.Style.PRIMARY
					: KitButton.Style.SECONDARY, b -> choose(CoinFlip.Side.TAILS)).selected(call == CoinFlip.Side.TAILS).icon(tailsIcon);
				if (bet.kinds(s).size() > 1) {
					button(22, 104, 96, 14, bet.kindLabel(), KitButton.Style.SECONDARY, b -> {
						bet.nextKind(state());
						rebuildWidgets();
					});
				}
				boolean steps = !bet.kind().equals(BetControl.ITEM);
				button(22, 142, 26, 20, Component.translatable("gui.burmaldaholic.extras.minus"), KitButton.Style.SECONDARY, b -> {
					bet.step(state(), -1);
					rebuildWidgets();
				}).active(steps && !busy);
				button(51, 142, 26, 20, Component.translatable("gui.burmaldaholic.extras.plus"), KitButton.Style.SECONDARY, b -> {
					bet.step(state(), 1);
					rebuildWidgets();
				}).active(steps && !busy);
				button(80, 142, 38, 20, Component.translatable("gui.burmaldaholic.common.max"), KitButton.Style.SECONDARY, b -> {
					bet.max(state(), true);
					rebuildWidgets();
				}).active(steps && !busy);
				Component flip = Component.translatable(shownSeq >= 0 ? "gui.burmaldaholic.extras.coin.again" : "gui.burmaldaholic.extras.coin.flip");
				button(150, 206, 100, 20, flip, KitButton.Style.PRIMARY, b -> flip(call)).active(!busy);
				button(324, 206, 60, 20, Component.translatable("gui.burmaldaholic.extras.leave"), KitButton.Style.SECONDARY, b -> onClose());
				if (s.getBooleanOr("soul", false)) {
					button(268, 170, 106, 18, Component.translatable("gui.burmaldaholic.extras.soul.button").withStyle(ChatFormatting.BOLD),
						KitButton.Style.DANGER, b -> {
							mode = Mode.WARN;
							rebuildWidgets();
						}).active(!busy);
				}
			}
			case WARN -> {
				button(110, 150, 86, 20, Component.translatable("gui.burmaldaholic.common.confirm"), KitButton.Style.DANGER, b -> {
					mode = Mode.TYPE;
					typed = "";
					rebuildWidgets();
				});
				button(204, 150, 86, 20, Component.translatable("gui.burmaldaholic.common.cancel"), KitButton.Style.SECONDARY, b -> cancelSoul());
			}
			case TYPE -> {
				word = new EditBox(font, px + 110, py + 120, 180, 20, Component.translatable("gui.burmaldaholic.extras.soul_confirm_word"));
				word.setMaxLength(24);
				word.setValue(typed);
				word.setResponder(v -> typed = v);
				addRenderableWidget(word);
				setInitialFocus(word);
				button(110, 150, 86, 20, Component.translatable("gui.burmaldaholic.common.confirm"), KitButton.Style.DANGER, b -> {
					if (wordMatches(typed)) {
						mode = Mode.HOLD;
						rebuildWidgets();
					} else {
						b.shake();
						showError(Component.translatable("gui.burmaldaholic.extras.soul.confirm_prompt",
							Component.translatable("gui.burmaldaholic.extras.soul_confirm_word")));
					}
				});
				button(204, 150, 86, 20, Component.translatable("gui.burmaldaholic.common.cancel"), KitButton.Style.SECONDARY, b -> cancelSoul());
			}
			case HOLD -> {
				for (CoinFlip.Side side : CoinFlip.Side.values()) {
					int x = side == CoinFlip.Side.HEADS ? 110 : 204;
					button(x, 70, 86, 20, Component.translatable(side.key()), side == soulSide ? KitButton.Style.PRIMARY : KitButton.Style.SECONDARY,
						b -> {
							soulSide = side;
							rebuildWidgets();
						}).selected(side == soulSide);
				}
				button(157, 180, 86, 20, Component.translatable("gui.burmaldaholic.common.cancel"), KitButton.Style.SECONDARY, b -> cancelSoul());
			}
		}
	}

	private void choose(CoinFlip.Side side) {
		call = side;
		rebuildWidgets();
	}

	static boolean wordMatches(String typed) {
		String t = typed.trim().toLowerCase(Locale.ROOT);
		return !t.isEmpty() && (t.equals(I18n.get("gui.burmaldaholic.extras.soul_confirm_word").toLowerCase(Locale.ROOT))
			|| t.equals("deal") || t.equals("сделка"));
	}

	private void cancelSoul() {
		holding = false;
		mode = Mode.NORMAL;
		send("soul_cancel", new CompoundTag());
		rebuildWidgets();
	}

	private void flip(CoinFlip.Side side) {
		lastAmount = Math.max(1, bet.amount());
		CompoundTag args = bet.args();
		args.putString("side", side.id());
		send("flip", args);
	}

	@Override
	public void onClose() {
		if (flipping()) finishToss(true);
		super.onClose();
	}

	// ---- soul hold -----------------------------------------------------------------------------------------------

	private static final int HOLD_X = 110;
	private static final int HOLD_Y = 120;
	private static final int HOLD_W = 180;

	private double holdProgress() {
		return holding ? Math.min(1, (Util.getMillis() - holdStartMs) / 5000.0) : 0;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (mode == Mode.HOLD && event.button() == 0 && inHold(event.x(), event.y())) {
			holding = true;
			holdStartMs = Util.getMillis();
			send("soul_arm", new CompoundTag());
			return true;
		}
		if (mode == Mode.NORMAL && flipping() && event.button() == 0 && Math.abs(event.x() - (px + CX)) < 40 && Math.abs(event.y() - (py + CY)) < 60) {
			return skip();
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

	private boolean inHold(double x, double y) {
		return x >= px + HOLD_X && x < px + HOLD_X + HOLD_W && y >= py + HOLD_Y && y < py + HOLD_Y + 20;
	}

	private int lastHeartbeat = -1;

	private void holdTick() {
		if (mode != Mode.HOLD || !holding) return;
		long ms = Util.getMillis() - holdStartMs;
		// heartbeat once per second, twice per second in the last 2 s (pitch 1.0 → 1.25)
		int beat = ms < 3000 ? (int) (ms / 1000) : 3 + (int) ((ms - 3000) / 500);
		if (beat != lastHeartbeat) {
			lastHeartbeat = beat;
			FxSounds.play("heartbeat", 1f, (float) (1 + 0.25 * Math.min(1, ms / 5000.0)));
		}
		if (ms >= 5000) {
			holding = false;
			CompoundTag args = new CompoundTag();
			args.putString("side", soulSide.id());
			send("soul", args);
			mode = Mode.NORMAL;
			rebuildWidgets();
		}
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	protected void extractPlayArea(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		holdTick();
		boolean soul = mode == Mode.HOLD || (soulResult() && animStart >= 0);
		if (soul) g.fill(px + 12, py + 24, px + Scene.W - 12, py + Scene.H - 12, 0x40600000);
		Kit.sprite(g, Kit.extras("coin_pad"), px + 128, py + 148, 144, 40, soul ? 0xFF6A3A2A : 0xFFFFFFFF);
		CoinAnim.Pose pose = pose();
		int sw = (int) Math.round(28 * pose.shadow() + 8);
		Kit.sprite(g, Kit.extras("coin_shadow"), px + CX - sw / 2, py + 162, sw, Math.max(3, sw / 4), Kit.fade(0.55 * pose.shadowAlpha()));
		if (pose.airborne() && !Kit.reduceMotion()) {
			double ms = tossMs();
			CoinAnim.Pose g1 = CoinAnim.toss(heads(), won(), Math.max(CoinAnim.ANTICIPATION_MS, ms - 60), false);
			CoinAnim.Pose g2 = CoinAnim.toss(heads(), won(), Math.max(CoinAnim.ANTICIPATION_MS, ms - 120), false);
			coin(g, g2, 0.14, soul);
			coin(g, g1, 0.28, soul);
		}
		coin(g, pose, 1, soul);
		// sparkles around a coin in the air (cosmetic)
		if (pose.airborne() && !Kit.reduceMotion()) {
			long t = Util.getMillis() / 90;
			int[][] pts = {{-40, -68}, {38, -54}, {30, -82}, {-30, -24}};
			for (int i = 0; i < pts.length; i++) {
				Kit.sprite(g, Kit.core("fx/sparkle"), px + CX + pts[i][0] - 3, py + CY + pts[i][1] - 3, 7, 7, Kit.fade(0.5 + 0.5 * Math.sin(t + i)));
			}
		}
	}

	private CoinAnim.Pose pose() {
		if (animStart < 0) return state().getCompoundOrEmpty("result").getIntOr("seq", -1) >= 0 ? CoinAnim.terminal(heads()) : CoinAnim.IDLE;
		if (landedDone) return CoinAnim.terminal(heads());
		return CoinAnim.toss(heads(), won(), tossMs(), Kit.reduceMotion());
	}

	private void coin(GuiGraphicsExtractor g, CoinAnim.Pose p, double alpha, boolean soul) {
		float x = px + CX;
		float y = (float) (py + CY + p.dy());
		g.pose().pushMatrix();
		g.pose().translate(x, y);
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

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		CompoundTag s = state();
		switch (mode) {
			case NORMAL -> normal(g, s);
			case WARN -> {
				soulCard(g);
				Kit.big(g, font, Component.translatable("gui.burmaldaholic.extras.soul.warning_title").withStyle(ChatFormatting.BOLD), px + 200, py + 50,
					2, Kit.RED_LIGHT, Kit.INK);
				Kit.wrapCentered(g, font, Component.translatable("gui.burmaldaholic.extras.soul.warning", Texts.chips(s.getLongOr("soul_value", 0))),
					px + 200, py + 78, 240, 6, Kit.BONE, true);
			}
			case TYPE -> {
				soulCard(g);
				Kit.wrapCentered(g, font, Component.translatable("gui.burmaldaholic.extras.soul.confirm_prompt",
					Component.translatable("gui.burmaldaholic.extras.soul_confirm_word").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)),
					px + 200, py + 60, 240, 4, Kit.BONE, true);
			}
			case HOLD -> {
				soulCard(g);
				int x = px + HOLD_X;
				int y = py + HOLD_Y;
				Kit.sprite(g, Kit.core("menu/progress"), x, y, HOLD_W, 20);
				int fill = (int) Math.round((HOLD_W - 8) * holdProgress());
				if (fill > 0) {
					Kit.sprite(g, Kit.core("menu/progress_fill_red"), x + 4, y + 4, fill, 12);
					if (!Kit.reduceMotion()) {
						double pulse = 0.5 + 0.5 * Math.sin(Util.getMillis() / 120.0);
						g.fill(x + 3 + fill, y + 3, x + 6 + fill, y + 17, Kit.alpha(0xFFFF6020, 0.6 + 0.4 * pulse));
					}
				}
				Kit.centeredFit(g, font, Component.translatable("gui.burmaldaholic.extras.soul.hold"), px + 200, y + 26, 240, Kit.BONE, true);
			}
		}
	}

	private void soulCard(GuiGraphicsExtractor g) {
		g.fill(px + 90, py + 36, px + 310, py + 200, 0xE0200810);
		Kit.region(g, Kit.sheet("core/fx/vignette_red"), 256, 256, 0, 0, 256, 256, px + 90, py + 36, 220, 164, 0xFFFFFFFF);
		Kit.frameRect(g, px + 90, py + 36, 220, 164, Kit.RED);
	}

	private void normal(GuiGraphicsExtractor g, CompoundTag s) {
		// left: your call + bet
		Scene.inset(g, px + 18, py + 38, 104, 128);
		Kit.fit(g, font, Component.translatable("gui.burmaldaholic.extras.coin.your_call"), px + 22, py + 44, 96, Kit.GOLD, true);
		if (bet.kinds(s).size() <= 1) Kit.text(g, font, Component.translatable("gui.burmaldaholic.extras.bet"), px + 22, py + 108, Kit.BONE_SHADE);
		Scene.inset(g, px + 22, py + 120, 96, 18);
		Kit.sprite(g, Kit.core("fx/chip_" + BetControl.chipDenom(bet.amount())), px + 26, py + 125, 8, 8);
		Kit.fit(g, font, bet.shown(s), px + 38, py + 125, 76, Kit.GOLD, true);
		// right: last flips
		Scene.inset(g, px + 268, py + 38, 106, 128);
		Kit.fit(g, font, Component.translatable("gui.burmaldaholic.extras.coin.history"), px + 272, py + 44, 98, Kit.GOLD, true);
		List<int[]> hist = new ArrayList<>(HISTORY);
		if (flipping()) {
			if (hist.size() >= 10) hist.removeFirst();
			hist.add(null);
		}
		int heads = 0;
		int tails = 0;
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
			Kit.frame(g, PIPS, 96, 16, 16, 16, pip, px + 272 + (i % 5) * 19, py + 58 + (i / 5) * 19, 0xFFFFFFFF);
		}
		Kit.fit(g, font, Component.translatable("gui.burmaldaholic.extras.coin.tally", Texts.number(heads), Texts.number(tails)), px + 272, py + 104,
			98, Kit.BONE, true);
		int streak = ClientCasinoState.streak();
		if (streak != 0) {
			boolean lucky = streak > 0;
			Kit.sprite(g, Kit.core(lucky ? "hud/flame_1" : "hud/cloud_1"), px + 272, py + 118, 8, 8);
			Kit.fit(g, font, Component.translatable(lucky ? "hud.burmaldaholic.streak.lucky" : "hud.burmaldaholic.streak.unlucky",
				Texts.number(Math.abs(streak))), px + 283, py + 118, 87, lucky ? Kit.BONUS : Kit.COOL, true);
		}
		double payout = s.getDoubleOr("payout", CoinFlip.DEFAULT_PAYOUT);
		Kit.fit(g, font, Component.translatable("gui.burmaldaholic.extras.coin.pays", Texts.decimal(Payouts.formatMultiplier(1 + payout))), px + 272,
			py + 134, 98, Kit.BONE_SHADE, true);
		long value = bet.value(s);
		if (value > 0) {
			long win = Payouts.floorPay(value, payout);
			Kit.fit(g, font, Component.translatable("gui.burmaldaholic.extras.coin.win_preview", Texts.number(value + win)), px + 272, py + 146, 98,
				Kit.BONE_SHADE, true);
		}
		// status line, face name
		CompoundTag r = s.getCompoundOrEmpty("result");
		CoinAnim.Pose pose = pose();
		if (flipping() && !pose.landed()) {
			Kit.centeredFit(g, font, Component.translatable("gui.burmaldaholic.extras.coin.flipping"), px + 200, py + 194, 220, Kit.LILAC, true);
		} else if (r.getIntOr("seq", -1) >= 0 && pose.landed()) {
			Component side = Component.translatable("gui.burmaldaholic.extras.coin." + (heads() ? "heads" : "tails"));
			double pop = landedDone ? 1 : pose.name();
			if (pop >= 0) {
				double sc = Kit.reduceMotion() ? 1 : dev.nezo.burmaldaholic.core.anim.Ease.OUT_BACK.apply(Math.min(1, pop));
				Kit.big(g, font, side, px + 200, py + 170, (float) (2 * sc), won() ? Kit.GOLD : Kit.BONE, Kit.INK);
			}
			if (landedDone) {
				Component line = won()
					? Component.translatable("gui.burmaldaholic.extras.coin.result_win", side, Texts.chips(r.getLongOr("net", 0)))
					: Component.translatable("gui.burmaldaholic.extras.coin.result_lose", side);
				Kit.centeredFit(g, font, line, px + 200, py + 194, 230, won() ? Kit.BONUS : Kit.RED_LIGHT, true);
			}
		}
	}
}
