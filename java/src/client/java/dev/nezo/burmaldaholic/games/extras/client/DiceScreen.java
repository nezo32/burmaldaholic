package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.TableKit;
import dev.nezo.burmaldaholic.client.table.fx.TableTheme;
import dev.nezo.burmaldaholic.client.ui.v2.BetPort;
import dev.nezo.burmaldaholic.client.ui.v2.ChipStack;
import dev.nezo.burmaldaholic.client.ui.v2.SeatPlate;
import dev.nezo.burmaldaholic.client.ui.v2.V2Button;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Sprites;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.dice.DiceThrowPath;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Flow;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import dev.nezo.burmaldaholic.core.ui.v2.TableGrid;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasFlow;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasKeys;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasLayout;
import dev.nezo.burmaldaholic.games.extras.present.ExtrasView;
import dev.nezo.burmaldaholic.games.extras.present.LocalBet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Dice Duel, table UI v2 (REDESIGN.md §7.7 "Dice Duel: arena full width, plates above each lane"; lane E): the kit's
 * TopBar and Dock (BetBar or −/+ for pawn stakes; primary <b>Roll</b> {@code Space}) and the tavern scene: the arena over
 * the full width (your lane left, the dealer's or the opponent's right, a plate above each), and above it the PvP strip
 * (opponent picker + Challenge, the challenge sent, an incoming challenge card with Accept {@code Y} / Decline {@code N}).
 * The cups shake, tip and throw both pairs on the shared {@link DiceThrowPath}; the totals pop, the higher plaque turns
 * gold, the house seal slams on a tie on 7, the chips cross the table; PvP shows every round. Click or Space skips.
 *
 * <p>Hit map ids: {@code felt.arena} (skip), {@code felt.target}, {@code felt.challenge}, {@code felt.accept},
 * {@code felt.decline}, {@code felt.stake}, plus the kit's ids.
 */
final class DiceScreen extends ExtrasSceneScreen<ExtrasView> {
	private static final Identifier DICE = TableGfx.sheet("extras/dice_duel_dice_big");
	private static final Identifier TUMBLE = TableGfx.sheet("extras/dice_duel_tumble_big");
	private static final LocalBet BET = new LocalBet(0);
	private static final BetControl PAWN = new BetControl(true, 1);

	private String target = "";
	private int shownSeq;
	private long resultAt = -1;
	private long rollPressedAt = -1;
	private boolean skipped;
	private boolean bannerShown;
	private TableTheme theme = TableTheme.VILLAGE;
	private List<int[][]> rounds = List.of();
	private final List<DiceThrowPath[]> paths = new ArrayList<>();
	private int cueRound = -1;
	private int cueIndexYou;
	private int cueIndexThem;
	private int beatsPlayed;
	private int ticks;
	private final DiceThrowPath.Sample sample = new DiceThrowPath.Sample();
	private ExtrasLayout.Scene scene;

	DiceScreen(CompoundTag state) {
		super("dice", Component.translatable("gui.burmaldaholic.extras.dice.title"), state);
		this.target = state.getStringOr("target", "");
		this.shownSeq = state.getCompoundOrEmpty("result").getIntOr("seq", -1);
		this.theme = TableKit.theme(dev.nezo.burmaldaholic.client.ui.CasinoTheme.byId(state.getStringOr("theme", "village")));
		if (shownSeq >= 0) {
			loadRounds(state.getCompoundOrEmpty("result"));
			resultAt = Util.getMillis() - 60_000;
			bannerShown = true;
		}
		if (BET.amount() <= 0) BET.add(Math.max(1, state.getLongOr("min", 1)), state.getLongOr("max", 0), state.getLongOr("balance", 0));
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
		BET.clamp(s.getLongOr("max", 0), s.getLongOr("balance", 0));
		return ExtrasView.chips(BET.view(s.getLongOr("min", 1), s.getLongOr("max", 0), shownBalance(), busy), busy, null, 2 * BET.amount());
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
				BET.add(denom, state().getLongOr("max", 0), shownBalance());
				local();
			}

			@Override
			public boolean setsAmount() {
				return true;
			}

			@Override
			public boolean amount(long amount) { // the bet-entry control's OK (lane HR)
				BET.set(amount, state().getLongOr("max", 0), shownBalance());
				local();
				return true;
			}

			@Override
			public void remove(long denom) {
				BET.undo();
				local();
			}

			@Override
			public void tool(String id) {
				long max = state().getLongOr("max", 0);
				long bal = shownBalance();
				switch (id) {
					case BetGates.UNDO -> BET.undo();
					case BetGates.CLEAR -> BET.clear();
					case BetGates.REBET -> BET.rebet(max, bal);
					case BetGates.DOUBLE -> BET.dbl(max, bal);
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
		t.putLong("amount", BET.amount());
		return t;
	}

	@Override
	public void onGate(String id, Gate gate) {
		switch (gate.action()) {
			case "roll" -> {
				if (chips()) BET.played();
				rollPressedAt = Util.getMillis();
				skipped = true; // a new roll skips the previous result's beats
				FxSounds.play("dice_cup", 1f);
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

	@Override
	public void showError(Component message) {
		super.showError(message);
		rollPressedAt = -1;
		FxSounds.play("ui_deny", 1f);
		local();
	}

	// ---- state ---------------------------------------------------------------------------------------------------

	@Override
	protected void onStateChanged(CompoundTag oldState, CompoundTag newState) {
		theme = TableKit.theme(dev.nezo.burmaldaholic.client.ui.CasinoTheme.byId(newState.getStringOr("theme", "village")));
		CompoundTag r = newState.getCompoundOrEmpty("result");
		int seq = r.getIntOr("seq", -1);
		if (seq >= 0 && seq != shownSeq) {
			shownSeq = seq;
			loadRounds(r);
			resultAt = Util.getMillis();
			rollPressedAt = -1;
			skipped = false;
			bannerShown = false;
			cueRound = -1;
			beatsPlayed = 0;
			holdBalance(oldState.getLongOr("balance", newState.getLongOr("balance", 0)));
		}
		String t = newState.getStringOr("target", "");
		if (!t.isEmpty()) target = t;
	}

	private void loadRounds(CompoundTag r) {
		List<int[][]> out = new ArrayList<>();
		ListTag list = r.getListOrEmpty("rounds");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag rt = list.getCompoundOrEmpty(i);
			out.add(new int[][] {rt.getIntArray("you").orElse(new int[] {1, 1}), rt.getIntArray("them").orElse(new int[] {1, 1})});
		}
		if (out.isEmpty()) out.add(new int[][] {r.getIntArray("you").orElse(new int[] {1, 1}), r.getIntArray("them").orElse(new int[] {1, 1})});
		rounds = out;
		paths.clear();
		int seed = r.getIntOr("seed", r.getIntOr("seq", 0));
		for (int i = 0; i < rounds.size(); i++) {
			int[][] rd = rounds.get(i);
			DiceThrowPath you = DiceThrowPath.of(seed + i * 2, clampFace(rd[0][0]), clampFace(rd[0][1]), DiceThrowPath.Params.duel(40, 84, 80, 84, 84, 88, 36));
			DiceThrowPath them = DiceThrowPath.of(seed + i * 2 + 1, clampFace(rd[1][0]), clampFace(rd[1][1]),
				DiceThrowPath.Params.duel(320, 84, 242, 84, 246, 88, 36));
			paths.add(new DiceThrowPath[] {you, them});
		}
	}

	private static int clampFace(int f) {
		return Math.max(1, Math.min(6, f));
	}

	private double t() {
		if (resultAt < 0) return -1;
		if (skipped) return DuelTimeline.endMs(rounds.size()) + 1;
		return Util.getMillis() - resultAt;
	}

	private boolean rolling() {
		double t = t();
		return rollPressedAt >= 0 || (t >= 0 && t < DuelTimeline.endMs(rounds.size()));
	}

	@Override
	public void tick() {
		super.tick();
		ticks++;
		if (ticks % 20 == 0 && !state().getListOrEmpty("incoming").isEmpty()) sendRaw("refresh", new CompoundTag());
		// the end of a result: release the balance, the banner, the next roll
		double t = t();
		if (resultAt >= 0 && !bannerShown && t >= DuelTimeline.endMs(rounds.size())) {
			bannerShown = true;
			releaseBalance();
			CompoundTag r = state().getCompoundOrEmpty("result");
			String outcome = r.getStringOr("outcome", "");
			chrome.status.hint(outcomeLine(outcome, r.getLongOr("net", 0)));
			if (!r.getBooleanOr("pawn", false)) chrome.banner.show(r.getLongOr("net", 0));
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

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == Inputs.KEY_SPACE && t() >= 0 && t() < DuelTimeline.endMs(rounds.size())) {
			skipped = true;
			return true;
		}
		return super.keyPressed(event);
	}

	// ---- layout -------------------------------------------------------------------------------------------------------

	private int ax() {
		return scene.object().x();
	}

	private int ay() {
		return scene.object().y();
	}

	/** The PvP strip above the arena (the scene's options area). */
	private Box strip() {
		return scene.side();
	}

	private Box stakeBox() {
		Box s = strip();
		return new Box(s.right() - 90, s.y(), 90, scene.row());
	}

	private Box targetBox() {
		Box s = strip();
		return new Box(s.x(), s.y(), Math.min(150, (s.w() - 100) / 2), scene.row());
	}

	private Box challengeBox() {
		Box t = targetBox();
		return new Box(t.right() + 4, t.y(), t.w(), scene.row());
	}

	/** The incoming challenge card: text left, Accept / Decline right. */
	private Box inviteBox() {
		Box s = strip();
		return new Box(s.x(), s.y() + scene.row() + 4, s.w(), Math.min(scene.row() + 4, s.bottom() - s.y() - scene.row() - 4));
	}

	private Box acceptBox() {
		Box c = inviteBox();
		return new Box(c.right() - 2 * 64 - 6, c.y() + 2, 64, c.h() - 4);
	}

	private Box declineBox() {
		Box a = acceptBox();
		return new Box(a.right() + 4, a.y(), 64, a.h());
	}

	@Override
	protected void layoutPlay(TableGrid grid, ExtrasView v, HitMap hits) {
		scene = ExtrasLayout.dice(grid);
		Box o = scene.object();
		chrome.banner.regions(List.of(new Box(o.x() + 90, o.y() + 96, o.w() - 180, 40)), List.of());
		hits.add(KeyMap.Ids.felt("arena"), o, true);
		CompoundTag s = state();
		if (PAWN.kinds(s).size() > 1) hits.add(KeyMap.Ids.felt("stake"), stakeBox(), !v.busy());
		if (s.getBooleanOr("pvp", false)) {
			ListTag nearby = s.getListOrEmpty("nearby");
			if (s.getCompoundOrEmpty("outgoing").isEmpty() && !nearby.isEmpty()) {
				hits.add(KeyMap.Ids.felt("target"), targetBox(), nearby.size() > 1);
				hits.add(KeyMap.Ids.felt("challenge"), challengeBox(), chips() && BET.amount() > 0);
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
				double t = t();
				if (t >= 0 && t < DuelTimeline.endMs(rounds.size())) {
					skipped = true;
					return true;
				}
				return false;
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
				args.putLong("amount", Math.max(1, BET.amount()));
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
		if (scene == null) scene = ExtrasLayout.dice(grid);
		SceneKit.fill(g, grid.play, "dice");
		CompoundTag s = state();
		Box o = scene.object();
		g.pose().pushMatrix();
		g.pose().translate(o.x(), o.y());
		g.pose().scale(scene.scale(), scene.scale());
		TableGfx.region(g, TableGfx.sheet("extras/dice_duel_arena_" + theme.id), 360, 140, 0, 0, 360, 140, 0, 0, 0xFFFFFFFF);
		g.pose().popMatrix();
		CompoundTag r = s.getCompoundOrEmpty("result");
		boolean pvp = "pvp".equals(r.getStringOr("mode", "house"));
		Component themName = pvp ? Texts.raw(r.getStringOr("opponent", "")) : Component.translatable("gui.burmaldaholic.extras.dice.dealer");
		plate(g, Component.translatable("gui.burmaldaholic.common.you"), SeatPlate.Kind.YOU, 96);
		plate(g, themName, SeatPlate.Kind.OTHER, 264);
		g.pose().pushMatrix();
		g.pose().translate(o.x(), o.y());
		g.pose().scale(scene.scale(), scene.scale());
		arena(g, t(), r);
		g.pose().popMatrix();
		strip(g, s, v, mouseX, mouseY);
	}

	private void plate(GuiGraphicsExtractor g, Component name, SeatPlate.Kind kind, int cx) {
		int w = Math.min(120, font.width(name) + 26);
		int x = scene.object().x() + Math.round(cx * scene.scale()) - w / 2;
		SeatPlate.draw(g, font, new Box(x, scene.object().y() + 6, w, 18), name, kind);
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
			V2Draw.left(g, font, o, new Box(strip().x(), strip().y(), strip().w() - 96, scene.row()), 0, 1, CasinoPalette.V2_MUTED, true);
		} else if (!nearby.isEmpty()) {
			int idx = Math.max(0, indexOf(nearby, currentTarget()));
			String name = nearby.getCompoundOrEmpty(idx).getStringOr("name", "");
			Component who = Component.translatable("gui.burmaldaholic.extras.labeled", Component.translatable("gui.burmaldaholic.extras.dice.target"),
				Texts.raw(name));
			V2Button.draw(g, font, targetBox(), who, null, V2Sprites.ButtonStyle.NORMAL, nearby.size() > 1, targetBox().contains(mx, my), false, false);
			V2Button.draw(g, font, challengeBox(), Component.translatable("gui.burmaldaholic.extras.dice.challenge_amount",
				Texts.chipsAcc(Math.max(1, BET.amount()))), null, V2Sprites.ButtonStyle.NORMAL, chips() && BET.amount() > 0, challengeBox().contains(mx, my),
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

	/** Everything that moves in the arena at {@code t} ms of the result (or the waiting shake), arena-local. */
	private void arena(GuiGraphicsExtractor g, double t, CompoundTag r) {
		boolean reduced = FxSettings.reduceMotion();
		long now = Util.getMillis();
		int n = rounds.size();
		boolean hasResult = t >= 0 && r.getIntOr("seq", -1) >= 0 && n > 0;
		boolean waiting = rollPressedAt >= 0;
		int round = hasResult ? DuelTimeline.roundAt(t, n) : 0;
		double rt = hasResult ? t - DuelTimeline.roundStart(round) : -1;
		int cupFrame;
		if (waiting && !reduced) {
			cupFrame = DuelTimeline.waitingCupFrame(now - rollPressedAt);
			if ((now - rollPressedAt) % 180 < 20) FxSounds.play("dice_cup", 0.6f, 1f);
		} else {
			cupFrame = hasResult ? DuelTimeline.cupFrame(rt, reduced) : 0;
		}
		int shake = cupFrame >= 1 && cupFrame <= 3 && !reduced ? (cupFrame == 1 ? -2 : cupFrame == 2 ? 2 : 0) : 0;
		var cup = TableGfx.spriteFile("extras/dice_duel_cup");
		TableGfx.region(g, cup, 40, 240, 0, cupFrame * 40, 40, 40, 8 + shake, 64, 0xFFFFFFFF);
		int themFrame = waiting && !reduced ? 1 + (cupFrame % 3) : cupFrame;
		TableGfx.regionMirrored(g, cup, 40, 240, 0, themFrame * 40, 40, 40, 312 - shake, 64);
		if (!hasResult) {
			vs(g, 0);
			return;
		}
		int[][] rd = rounds.get(round);
		DiceThrowPath[] pp = paths.get(round);
		boolean last = round == n - 1;
		String outcome = r.getStringOr("outcome", "");
		int youTotal = rd[0][0] + rd[0][1];
		int themTotal = rd[1][0] + rd[1][1];
		boolean tie = youTotal == themTotal;
		for (int side = 0; side < 2; side++) {
			double dt = rt - DuelTimeline.throwAt(0, side);
			if (dt < 0) continue;
			double sweep = !last && rt >= DuelTimeline.SWEEP_AT ? Math.min(1, (rt - DuelTimeline.SWEEP_AT) / DuelTimeline.SWEEP_MS) : 0;
			if (sweep >= 1) continue;
			boolean winner = last && rt >= DuelTimeline.COMPARE && ("win".equals(outcome) ? side == 0 : "lose".equals(outcome) && side == 1);
			for (int d = 0; d < 2; d++) {
				pp[side].sample(d, reduced ? pp[side].durationMs() : dt, sample);
				double alpha = reduced ? Math.min(1, dt / 200.0) : 1;
				int x = (int) Math.round(sample.x) - 20;
				int y = (int) Math.round(sample.y - sample.z) - 20;
				if (sweep > 0) {
					int cx = side == 0 ? 8 : 312;
					double e = Ease.IN_CUBIC.apply(sweep);
					x = (int) Math.round(x + (cx - x) * e);
					y = (int) Math.round(y + (64 - y) * e);
					alpha *= 1 - sweep;
				}
				if (sample.frame >= 0) {
					TableGfx.region(g, TUMBLE, 336, 42, sample.frame * 42, 0, 42, 42, x - 1, y - 1, TableGfx.fade(alpha));
				} else {
					int col = 0;
					double land = dt - pp[side].durationMs();
					if (!reduced && land >= 0 && land < 120) col = land < 60 ? 1 : 2;
					else if (winner) col = FxSettings.flashes() && !reduced ? 3 + (int) ((now / 100) % 6) : 3;
					else if (!reduced && FxSettings.flashes() && land > 400 && ((now / 90) % 40) < 4) col = 9 + (int) ((now / 90) % 4);
					TableGfx.region(g, DICE, 520, 240, col * 40, (sample.face - 1) * 40, 40, 40, x, y, TableGfx.fade(alpha));
				}
			}
		}
		boolean compared = rt >= DuelTimeline.COMPARE;
		plaque(g, 80, rt - DuelTimeline.YOU_LAND, youTotal, compared && last ? "win".equals(outcome) ? 1 : "lose".equals(outcome) || "house_tie".equals(outcome)
			? -1 : 0 : 0, compared && tie, rt - DuelTimeline.COMPARE, reduced);
		plaque(g, 248, rt - DuelTimeline.THEM_LAND, themTotal, compared && last ? "lose".equals(outcome) || "house_tie".equals(outcome) ? 1
			: "win".equals(outcome) ? -1 : 0 : 0, compared && tie, rt - DuelTimeline.COMPARE, reduced);
		vs(g, compared && last ? "win".equals(outcome) ? 2 : "lose".equals(outcome) || "house_tie".equals(outcome) ? 1 : 0 : 0);
		if (last && "house_tie".equals(outcome) && compared) {
			double k = reduced ? 1 : Math.min(1, (rt - DuelTimeline.COMPARE) / 250.0);
			int drop = (int) Math.round(18 * (1 - Ease.OUT_BOUNCE.apply(k)));
			TableGfx.blit(g, "extras/dice_duel_house_stamp", 243, 30 - drop, 42, 42);
		}
		if (!last && compared && rt < DuelTimeline.COMPARE + DuelTimeline.TIE_BANNER_MS) {
			Component tieText = Component.translatable("gui.burmaldaholic.extras.dice.fx.tie_reroll", Texts.number(round + 2), Texts.number(n));
			Box b = new Box(100, 104, 160, 20);
			V2Draw.pill(g, b, CasinoPalette.V2_PANEL, CasinoPalette.V2_GOLD);
			V2Draw.centred(g, font, tieText, b, 1, CasinoPalette.V2_GOLD, true);
		}
		cues(round, rt, last, outcome, compared);
		if (last) chips(g, rt, outcome, r, reduced);
	}

	private void vs(GuiGraphicsExtractor g, int frame) {
		TableGfx.frame(g, "extras/dice_duel_vs", 32, 32, 3, frame, 164, 54, 0xFFFFFFFF);
	}

	private void plaque(GuiGraphicsExtractor g, int x, double since, int total, int state, boolean tie, double sinceCompare, boolean reduced) {
		if (since < 0) return;
		double pop = reduced ? 1 : Ease.OUT_BACK.apply(Math.min(1, since / DuelTimeline.POP_MS));
		int dy = (int) Math.round(6 * (1 - pop));
		int dx = tie && !reduced && sinceCompare >= 0 && sinceCompare < 300 ? (int) Math.round(Math.sin(sinceCompare / 300.0 * Math.PI * 4)) : 0;
		String sprite = state > 0 ? "win" : state < 0 ? "lose" : "neutral";
		int lift = state > 0 ? 2 : 0;
		int y = 40 + dy;
		TableGfx.blit(g, "extras/dice_duel_plaque_" + sprite, x + dx, y - lift, 32, 24, state < 0 ? 0xC0FFFFFF : 0xFFFFFFFF);
		Component num = Texts.number(total);
		int color = state > 0 ? CasinoPalette.V2_GOLD : state < 0 ? 0xFF8C7CA8 : tie ? 0xFFB8B0C0 : CasinoPalette.V2_TEXT;
		V2Draw.text(g, font, num, x + dx + 16 - font.width(num), y - lift + 4, 2, color, true);
		if (state < 0 && sinceCompare >= 0) TableGfx.frame(g, "extras/dice_duel_crack", 32, 24, 2, reduced || sinceCompare > 120 ? 1 : 0, x + dx, y, 0xFFFFFFFF);
	}

	private void cues(int round, double rt, boolean last, String outcome, boolean compared) {
		if (cueRound != round) {
			cueRound = round;
			cueIndexYou = 0;
			cueIndexThem = 0;
			beatsPlayed = 0;
		}
		if (rt > DuelTimeline.endMs(1) + 500) return;
		for (int side = 0; side < 2; side++) {
			double dt = rt - DuelTimeline.throwAt(0, side);
			var list = paths.get(round)[side].cues();
			int idx = side == 0 ? cueIndexYou : cueIndexThem;
			while (idx < list.size() && list.get(idx).atMs() <= dt) {
				var c = list.get(idx++);
				FxSounds.play(c.sound(), side == 0 ? 1f : 0.8f, c.pitch());
			}
			if (side == 0) cueIndexYou = idx;
			else cueIndexThem = idx;
		}
		if (beatsPlayed == 0 && rt >= DuelTimeline.YOU_LAND) {
			beatsPlayed = 1;
			FxSounds.play("pvp_drumroll", 0.3f, 1f);
		}
		if (beatsPlayed == 1 && last && compared && rt >= DuelTimeline.CHIPS) {
			beatsPlayed = 2;
			boolean pvp = "pvp".equals(state().getCompoundOrEmpty("result").getStringOr("mode", "house"));
			switch (outcome) {
				case "win" -> FxSounds.play(pvp ? "win_nice" : "win_small", 1f);
				case "lose", "house_tie" -> FxSounds.play("lose", 1f);
				default -> FxSounds.play("push", 1f);
			}
			if (pvp && "win".equals(outcome)) FxSounds.play("pvp_victory", 0.8f, 1f);
		}
	}

	/** The pot in the middle, then it crosses the table (arena-local). */
	private void chips(GuiGraphicsExtractor g, double rt, String outcome, CompoundTag r, boolean reduced) {
		long net = r.getLongOr("net", 0);
		long stake = Math.max(1, Math.abs(net));
		int cx = 180;
		int cy = 124;
		if (rt < DuelTimeline.CHIPS) {
			ChipStack.draw(g, Box.centredAt(cx, cy, 16, 16), stake);
			return;
		}
		double k = reduced ? 1 : Math.min(1, (rt - DuelTimeline.CHIPS) / 400.0);
		switch (outcome) {
			case "win" -> {
				int x = (int) Math.round(cx + (60 - cx) * Ease.IN_CUBIC.apply(k));
				if (k < 1) ChipStack.draw(g, Box.centredAt(x, cy, 16, 16), 2 * stake, (float) (1 - 0.8 * k));
			}
			case "lose", "house_tie" -> {
				int x = (int) Math.round(cx + (300 - cx) * Ease.IN_CUBIC.apply(k));
				if (k < 1) ChipStack.draw(g, Box.centredAt(x, cy, 16, 16), stake, (float) (1 - 0.8 * k));
			}
			default -> {
				if (k < 1) ChipStack.draw(g, Box.centredAt(cx, cy + (int) Math.round(20 * k), 16, 16), stake, (float) (1 - k));
			}
		}
	}
}
