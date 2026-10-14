package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.motion.MotionClock;
import dev.nezo.burmaldaholic.client.fx.motion.MotionFx;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.table.fx.TableKit;
import dev.nezo.burmaldaholic.client.table.fx.TableTheme;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.SeatPlate;
import dev.nezo.burmaldaholic.client.ui.v2.V2Button;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Sprites;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.anim.motion.SpeedPolicy;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.games.extras.present.DiceStage;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasFlow;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasKeys;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasView;
import dev.nezo.burmaldaholic.games.extras.present.LocalBet;
import dev.nezo.burmaldaholic.games.extras.present.LocalBets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Dice Duel, table UI v2 (REDESIGN.md §7.7; DI §3–§9, PHASE2B §4 DCb): the kit's TopBar and Dock (BetBar or −/+ for
 * pawn stakes; primary <b>Roll</b> {@code Space}), the PvP strip (opponent picker + Challenge, the challenge sent, an
 * incoming challenge card with Accept {@code Y} / Decline {@code N}) and under it the arena at a crisp art scale
 * ({@link DiceStage}); the motion is {@link DiceArena}.
 *
 * <p>Clocks (DI §3.3–§3.4): house rolls run on the local clock from the Roll press (the cups shake at once; the flick
 * waits for the result and lands on a shake reversal), scaled by the animation speed ({@link SpeedPolicy#LOCAL}: SLOW and
 * TURBO both apply, nothing on the server waits on a house roll). PvP duels run on the SHARED clock
 * {@code levelMs − start·50} of the duel's game tick ({@link SpeedPolicy#SHARED}: never slowed or sped up, the server
 * posts each round's chat lines at its COMPARE beat), so both screens and the world stage show the same beats; rolls
 * are keyed by player side, so A's dice move alike on A's screen (left) and B's screen (right, mirrored). Click or
 * Space skips to the end (events fire silently).
 *
 * <p>Hit map ids: {@code felt.arena} (skip), {@code felt.target}, {@code felt.challenge}, {@code felt.accept},
 * {@code felt.decline}, {@code felt.stake}, plus the kit's ids.
 */
final class DiceScreen extends ExtrasSceneScreen<ExtrasView> {
	/** This machine's bet in the client session (per position, R-CDE m5). */
	private final LocalBet bet;
	private static final BetControl PAWN = new BetControl(true, 1);

	private String target = "";
	private int shownSeq;
	private TableTheme theme = TableTheme.VILLAGE;
	private final DiceArena arena = new DiceArena();
	private DiceArena.Show show;
	/** The previous result's dice, gathered back into the cups when the next roll starts. */
	private DiceArena.Show prev;
	private long prevSweepAt = -1;
	/** House: local ms of the Roll press (−1 = none); the shake clock origin. */
	private long pressAt = -1;
	/** House: animation speed of the current roll (LOCAL policy), fixed at the press. */
	private double speed = 1;
	/** A result is on screen without a press (screen reopened): show its end state. */
	private boolean settled;
	private boolean skipped;
	private boolean bannerShown;
	private int ticks;
	private DiceStage stage;

	DiceScreen(CompoundTag state) {
		super("dice", Component.translatable("gui.burmaldaholic.extras.dice.title"), state);
		this.bet = LocalBets.of("dice", LocalBets.HAND);
		this.target = state.getStringOr("target", "");
		CompoundTag r = state.getCompoundOrEmpty("result");
		this.shownSeq = r.getIntOr("seq", -1);
		this.theme = TableKit.theme(dev.nezo.burmaldaholic.client.ui.CasinoTheme.byId(state.getStringOr("theme", "village")));
		if (shownSeq >= 0) {
			show = build(r);
			bannerShown = true;
			// a PvP duel still running keeps its shared clock; anything else shows its end state
			settled = !show.pvp || duelMs(0) >= DuelTimeline.endMs(show.roundCount());
			if (!show.pvp) show.offset = DuelTimeline.SHAKE_LEAD;
		}
		if (bet.amount() <= 0) bet.add(Math.max(1, state.getLongOr("min", 1)), state.getLongOr("max", 0), state.getLongOr("balance", 0));
		chrome.keys(KeyMap.standard().bind(KeyMap.Y, KeyMap.Ids.felt("accept")).bind(KeyMap.N, KeyMap.Ids.felt("decline")));
	}

	// ---- the pure parts ------------------------------------------------------------------------------------------------

	private boolean chips() {
		return PAWN.kind().equals(BetControl.CHIPS);
	}

	@Override
	protected ExtrasView view(CompoundTag s) {
		PAWN.validate(s);
		boolean busy = rolling();
		if (!chips()) {
			return ExtrasView.pawn(busy, null, PAWN.kind().equals(BetControl.ITEM) ? s.getLongOr("held_value", 0) : PAWN.amount(), PAWN.max(s));
		}
		bet.clamp(s.getLongOr("max", 0), s.getLongOr("balance", 0));
		return ExtrasView.chips(bet.view(s.getLongOr("min", 1), s.getLongOr("max", 0), shownBalance(), busy), busy, null, 2 * bet.amount());
	}

	@Override
	protected Flow<ExtrasView> flow() {
		return ExtrasFlow.DICE;
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

	private CompoundTag stakeArgs() {
		if (!chips()) return PAWN.args();
		CompoundTag t = new CompoundTag();
		t.putString("stake", "chips");
		t.putLong("amount", bet.amount());
		return t;
	}

	@Override
	public void onGate(String id, Gate gate) {
		switch (gate.action()) {
			case "roll" -> {
				if (chips()) bet.sent();
				press();
				send("roll", stakeArgs());
				local();
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

	/** Roll pressed: the cups start shaking now; the last result's dice are gathered back into them. */
	void press() {
		long now = MotionClock.localMs();
		if (show != null) {
			prev = show;
			prevSweepAt = now;
		}
		show = null;
		pressAt = now;
		speed = SpeedPolicy.LOCAL.factor(SpeedPolicy.speedOf(FxSettings.speed().pct), true);
		skipped = false;
		settled = false;
		arena.silence();
		if (FxSettings.reduceMotion()) FxSounds.play("dice_cup", 0.6f, 1.8f);
	}

	@Override
	public void showError(Component message) {
		super.showError(message);
		pressAt = -1;
		FxSounds.play("ui_deny", 1f);
		local();
	}

	// ---- state ---------------------------------------------------------------------------------------------------

	private DiceArena.Show build(CompoundTag r) {
		List<int[][]> rounds = new ArrayList<>();
		ListTag list = r.getListOrEmpty("rounds");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag rt = list.getCompoundOrEmpty(i);
			rounds.add(new int[][] {rt.getIntArray("you").orElse(new int[] {1, 1}), rt.getIntArray("them").orElse(new int[] {1, 1})});
		}
		if (rounds.isEmpty()) rounds.add(new int[][] {r.getIntArray("you").orElse(new int[] {1, 1}), r.getIntArray("them").orElse(new int[] {1, 1})});
		boolean pvp = "pvp".equals(r.getStringOr("mode", "house"));
		int me = "b".equals(r.getStringOr("side", "a")) ? 1 : 0;
		return new DiceArena.Show(r.getIntOr("seq", 0), r.getIntOr("seed", r.getIntOr("seq", 0)), pvp, r.getLongOr("start", 0), me, rounds,
			r.getStringOr("outcome", ""), r.getLongOr("net", 0), r.getBooleanOr("pawn", false), arena.cache());
	}

	@Override
	protected void onStateChanged(CompoundTag oldState, CompoundTag newState) {
		theme = TableKit.theme(dev.nezo.burmaldaholic.client.ui.CasinoTheme.byId(newState.getStringOr("theme", "village")));
		CompoundTag r = newState.getCompoundOrEmpty("result");
		int seq = r.getIntOr("seq", -1);
		if (seq >= 0 && seq != shownSeq) {
			shownSeq = seq;
			if ("house".equals(r.getStringOr("mode", ""))) bet.ack(); // the server played the amount: it becomes Rebet
			DiceArena.Show next = build(r);
			long now = MotionClock.localMs();
			if (show != null && show != next) {
				prev = show;
				prevSweepAt = now;
			}
			if (!next.pvp) {
				// the flick waits for the first shake reversal after the result (local shift, DI §3.3)
				if (pressAt < 0) {
					pressAt = now;
					speed = SpeedPolicy.LOCAL.factor(SpeedPolicy.speedOf(FxSettings.speed().pct), true);
				}
				next.offset = DuelTimeline.shakeOffset(sincePress());
			} else {
				pressAt = -1;
			}
			show = next;
			skipped = false;
			settled = false;
			bannerShown = false;
			holdBalance(oldState.getLongOr("balance", newState.getLongOr("balance", 0)));
		}
		String t = newState.getStringOr("target", "");
		if (!t.isEmpty()) target = t;
	}

	/** House: animation ms since the Roll press (the shake clock). */
	private double sincePress() {
		return pressAt < 0 ? 0 : (MotionClock.localMs() - pressAt) * speed;
	}

	/** PvP: ms since the duel's start tick on the shared clock. */
	private double duelMs(float partial) {
		return show == null ? 0 : MotionClock.levelMs(partial) - show.start * 50.0;
	}

	/** Duel ms of the shown result ({@code +∞}-like end when skipped / settled; NaN without a result). */
	double t(float partial) {
		if (show == null) return Double.NaN;
		double end = DuelTimeline.endMs(show.roundCount()) + 1;
		if (skipped || settled) return end;
		return show.pvp ? duelMs(partial) : sincePress() - show.offset;
	}

	private double t() {
		return t(0);
	}

	/** House: shake ms while waiting for the result (NaN when not waiting). */
	private double waiting() {
		return pressAt >= 0 && show == null ? sincePress() : Double.NaN;
	}

	private boolean rolling() {
		double t = t();
		return pressAt >= 0 && show == null || show != null && t < DuelTimeline.endMs(show.roundCount());
	}

	@Override
	public void tick() {
		super.tick();
		ticks++;
		if (ticks % 20 == 0 && !state().getListOrEmpty("incoming").isEmpty()) sendRaw("refresh", new CompoundTag());
		// the end of a result: release the balance, the banner, the next roll
		if (show != null && !bannerShown && t() >= DuelTimeline.endMs(show.roundCount())) {
			bannerShown = true;
			releaseBalance();
			chrome.status.hint(outcomeLine(show.outcome, show.net));
			if (!show.pawn) chrome.banner.show(show.net);
			local();
		}
	}

	private Component outcomeLine(String outcome, long net) {
		return switch (outcome) {
			case "win" -> Component.translatable("gui.burmaldaholic.extras.dice.win", Texts.chips(net));
			case "lose" -> Component.translatable("gui.burmaldaholic.extras.dice.lose", Texts.chipsAcc(-net));
			case "house_tie" -> Component.translatable("gui.burmaldaholic.extras.dice.tie_seven");
			case "refund" -> Component.translatable("msg.burmaldaholic.extras.dice.pvp_refund");
			default -> Component.translatable("gui.burmaldaholic.extras.dice.tie");
		};
	}

	/** Skips to the end of the shown result (click / Space); passed events stay silent. */
	private boolean skip() {
		if (show == null || t() >= DuelTimeline.endMs(show.roundCount())) return false;
		skipped = true;
		arena.silence();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == Inputs.KEY_SPACE && skip()) return true;
		return super.keyPressed(event);
	}

	// ---- layout -------------------------------------------------------------------------------------------------------

	/** The PvP strip above the arena. */
	private Box strip() {
		return stage.strip();
	}

	private int row() {
		return stage.row();
	}

	private Box stakeBox() {
		Box s = strip();
		return new Box(s.right() - 90, s.y(), 90, row());
	}

	private Box targetBox() {
		Box s = strip();
		return new Box(s.x(), s.y(), Math.min(150, (s.w() - 100) / 2), row());
	}

	private Box challengeBox() {
		Box t = targetBox();
		return new Box(t.right() + 4, t.y(), t.w(), row());
	}

	/** The incoming challenge card: text left, Accept / Decline right. */
	private Box inviteBox() {
		Box s = strip();
		return new Box(s.x(), s.y() + row() + 4, s.w(), Math.min(row() + 4, s.bottom() - s.y() - row() - 4));
	}

	private Box acceptBox() {
		Box c = inviteBox();
		return new Box(c.right() - 2 * 64 - 6, c.y() + 2, 64, c.h() - 4);
	}

	private Box declineBox() {
		Box a = acceptBox();
		return new Box(a.right() + 4, a.y(), 64, a.h());
	}

	/** The stage (tests). */
	DiceStage stage() {
		return stage;
	}

	@Override
	protected void layoutPlay(TableGrid grid, ExtrasView v, HitMap hits) {
		stage = DiceStage.of(grid);
		Box o = stage.arena();
		chrome.banner.regions(List.of(stage.box(90, 40, 180, 40)), List.of());
		hits.add(KeyMap.Ids.felt("arena"), o, true);
		CompoundTag s = state();
		if (PAWN.kinds(s).size() > 1) hits.add(KeyMap.Ids.felt("stake"), stakeBox(), !v.busy());
		if (s.getBooleanOr("pvp", false)) {
			ListTag nearby = s.getListOrEmpty("nearby");
			if (s.getCompoundOrEmpty("outgoing").isEmpty() && !nearby.isEmpty()) {
				hits.add(KeyMap.Ids.felt("target"), targetBox(), nearby.size() > 1);
				hits.add(KeyMap.Ids.felt("challenge"), challengeBox(), chips() && bet.amount() > 0);
			}
			if (!s.getListOrEmpty("incoming").isEmpty()) {
				hits.add(KeyMap.Ids.felt("accept"), acceptBox(), true);
				hits.add(KeyMap.Ids.felt("decline"), declineBox(), true);
			}
		}
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press button) {
		CompoundTag s = state();
		switch (id) {
			case "felt.arena" -> {
				return skip();
			}
			case "felt.stake" -> {
				if (!enabled) {
					chrome.refuse(Gate.off("stake", ExtrasKeys.STAKE, dev.nezo.burmaldaholic.core.ui.v2.V2Keys.REASON_ROLLING));
					return true;
				}
				PAWN.nextKind(s);
				local();
				return true;
			}
			case "felt.target" -> {
				ListTag nearby = s.getListOrEmpty("nearby");
				if (nearby.isEmpty()) return false;
				int idx = Math.max(0, indexOf(nearby, target));
				target = nearby.getCompoundOrEmpty((idx + 1) % nearby.size()).getStringOr("uuid", "");
				local();
				return true;
			}
			case "felt.challenge" -> {
				if (!enabled) {
					chrome.refuse(Gate.off("challenge", ExtrasKeys.CHALLENGE, dev.nezo.burmaldaholic.core.ui.v2.V2Keys.REASON_PLACE_BET));
					return true;
				}
				CompoundTag args = new CompoundTag();
				args.putString("target", currentTarget());
				args.putLong("amount", Math.max(1, bet.amount()));
				send("challenge", args);
				return true;
			}
			case "felt.accept", "felt.decline" -> {
				ListTag incoming = s.getListOrEmpty("incoming");
				if (incoming.isEmpty()) return false;
				CompoundTag args = new CompoundTag();
				args.putInt("id", incoming.getCompoundOrEmpty(0).getIntOr("id", -1));
				send(id.equals("felt.accept") ? "accept" : "decline", args);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private String currentTarget() {
		ListTag nearby = state().getListOrEmpty("nearby");
		int idx = indexOf(nearby, target);
		return idx >= 0 ? target : nearby.isEmpty() ? "" : nearby.getCompoundOrEmpty(0).getStringOr("uuid", "");
	}

	private static int indexOf(ListTag list, String uuid) {
		for (int i = 0; i < list.size(); i++) if (list.getCompoundOrEmpty(i).getStringOr("uuid", "").equals(uuid)) return i;
		return -1;
	}

	// ---- drawing ------------------------------------------------------------------------------------------------------

	@Override
	protected void renderPlay(GuiGraphicsExtractor g, TableGrid grid, ExtrasView v, int mouseX, int mouseY, float partial) {
		if (stage == null) stage = DiceStage.of(grid);
		SceneKit.fill(g, grid.play, "dice");
		boolean reduced = MotionFx.reduced();
		double t = t(partial);
		double wait = waiting();
		double sweep = Double.NaN;
		if (prev != null && prevSweepAt >= 0) {
			double u = (MotionClock.localMs() - prevSweepAt) / (reduced ? 200.0 : 250.0);
			if (u >= 1) prev = null;
			else sweep = Math.max(0, u);
		}
		g.pose().pushMatrix();
		g.pose().translate((float) stage.ox(), (float) stage.oy());
		g.pose().scale((float) stage.scale(), (float) stage.scale());
		arena.draw(g, new DiceArena.Frame(stage, font, reduced, MotionClock.localMs()), show, t, wait, prev, sweep, theme.id);
		g.pose().popMatrix();
		plates(g, t, reduced);
		arena.sounds(show, t, wait, show, reduced);
		strip(g, state(), v, mouseX, mouseY);
	}

	/** Name plates over the top rail of each lane; ROOMY adds the pair's total once it is counted. */
	private void plates(GuiGraphicsExtractor g, double t, boolean reduced) {
		boolean pvp = show != null ? show.pvp : false;
		CompoundTag r = state().getCompoundOrEmpty("result");
		Component them = pvp ? Texts.raw(r.getStringOr("opponent", "")) : Component.translatable("gui.burmaldaholic.extras.dice.dealer");
		Component you = Component.translatable("gui.burmaldaholic.common.you");
		int laneL = (DiceArena.LANE_X0 + DiceArena.LANE_X1) / 2;
		for (int order = 0; order < 2; order++) {
			Component name = order == 0 ? you : them;
			Component line2 = null;
			if (stage.roomy() && show != null && !Double.isNaN(t)) {
				int round = DuelTimeline.roundAt(t, show.roundCount());
				double rt = t - DuelTimeline.roundStart(round);
				int[] f = show.faces(round, order);
				if (DiceArena.pipsLit(show, round, order, rt, reduced) >= f[0] + f[1])
					line2 = Component.translatable("gui.burmaldaholic.extras.dice.motion.total", Texts.number(f[0] + f[1]));
			}
			int h = stage.roomy() ? SeatPlate.ROOMY_H : 18;
			int w = Math.min(120, Math.max(font.width(name), line2 == null ? 0 : font.width(line2)) + 26);
			int cx = order == 0 ? laneL : DiceStage.W - laneL;
			SeatPlate.draw(g, font, stage.plate(cx, w, h), name, line2, order == 0 ? SeatPlate.Kind.YOU : SeatPlate.Kind.OTHER, false);
		}
	}

	/** The PvP strip and the stake kind toggle above the arena. */
	private void strip(GuiGraphicsExtractor g, CompoundTag s, ExtrasView v, int mx, int my) {
		if (PAWN.kinds(s).size() > 1) SceneKit.toggle(g, font, stakeBox(), PAWN.kindLabel(), !chips(), !v.busy(), mx, my);
		if (!s.getBooleanOr("pvp", false)) return;
		ListTag nearby = s.getListOrEmpty("nearby");
		CompoundTag outgoing = s.getCompoundOrEmpty("outgoing");
		if (!outgoing.isEmpty()) {
			Component o = Component.translatable("msg.burmaldaholic.extras.dice.challenge_sent", Texts.raw(outgoing.getStringOr("name", "")),
				Texts.chips(outgoing.getLongOr("stake", 0)));
			V2Draw.left(g, font, o, new Box(strip().x(), strip().y(), strip().w() - 96, row()), 0, 1, CasinoPalette.V2_MUTED, true);
		} else if (!nearby.isEmpty()) {
			int idx = Math.max(0, indexOf(nearby, currentTarget()));
			String name = nearby.getCompoundOrEmpty(idx).getStringOr("name", "");
			Component who = Component.translatable("gui.burmaldaholic.extras.labeled", Component.translatable("gui.burmaldaholic.extras.dice.target"),
				Texts.raw(name));
			V2Button.draw(g, font, targetBox(), who, null, V2Sprites.ButtonStyle.NORMAL, nearby.size() > 1, targetBox().contains(mx, my), false, false);
			V2Button.draw(g, font, challengeBox(), Component.translatable("gui.burmaldaholic.extras.dice.challenge_amount",
				Texts.chipsAcc(Math.max(1, bet.amount()))), null, V2Sprites.ButtonStyle.NORMAL, chips() && bet.amount() > 0, challengeBox().contains(mx, my),
				false, false);
		}
		ListTag incoming = s.getListOrEmpty("incoming");
		if (!incoming.isEmpty()) {
			CompoundTag c = incoming.getCompoundOrEmpty(0);
			Box card = inviteBox();
			V2Draw.pill(g, card, CasinoPalette.V2_PANEL_HI, CasinoPalette.V2_GOLD);
			Component text = Component.translatable("gui.burmaldaholic.extras.dice.invite", Texts.raw(c.getStringOr("name", "")),
				Texts.chips(c.getLongOr("stake", 0)));
			V2Draw.left(g, font, text, new Box(card.x(), card.y(), acceptBox().x() - card.x() - 4, card.h()), 6, 1, CasinoPalette.V2_GOLD, true);
			V2Button.draw(g, font, acceptBox(), Component.translatable("gui.burmaldaholic.extras.dice.accept"), V2Button.keyName(KeyMap.Y),
				V2Sprites.ButtonStyle.PRIMARY, true, acceptBox().contains(mx, my), false, false);
			V2Button.draw(g, font, declineBox(), Component.translatable("gui.burmaldaholic.extras.dice.decline"), V2Button.keyName(KeyMap.N),
				V2Sprites.ButtonStyle.NORMAL, true, declineBox().contains(mx, my), false, false);
		}
	}
}
