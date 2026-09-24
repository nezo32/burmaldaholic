package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.table.fx.ChipButton;
import dev.nezo.burmaldaholic.client.table.fx.TableButton;
import dev.nezo.burmaldaholic.client.table.fx.TableKit;
import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.table.fx.TableChrome;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.TableTheme;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.dice.ChipStacks;
import dev.nezo.burmaldaholic.core.anim.dice.DiceThrowPath;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Dice Duel (UI.md §9; docs/design/visual/tables.md §5–§6; animation/tables.md §3.3): the full-screen arena. Your lane
 * on the left, the dealer's (or the opponent's) on the right; the cups shake, tip and throw both pairs on the shared
 * no-wall {@link DiceThrowPath} (real faces only from 82 %), the other pair lands 350 ms later, the totals pop, the
 * higher plaque turns gold while the other cracks, the "vs" plaque tilts toward the loser, the house seal slams on a
 * tie on 7, and the chips cross the table. PvP shows every round (ties re-roll). Stake: the chip tray adds chips (Clear
 * resets), a stake-kind button for pawn stakes; PvP challenge and pending challenges on the top bar / arena.
 */
final class DiceScreen extends ExtrasScreen {
	private static final int W = 427;
	private static final int H = 240;
	private static long lastAmount = 25;

	/** The stake: chips added from the tray, or a pawn stake kind (§4.3); the server validates everything. */
	private String stakeKind = BetSelector.CHIPS;
	private long stakeAmount;
	private String target = "";
	private int shownSeq;
	private long resultAt = -1;
	private long rollPressedAt = -1;
	private boolean skipped;
	private boolean showRules;
	private int lastAdded = 25;
	private TableTheme theme = TableTheme.VILLAGE;
	private int ox;
	private int oy;
	private @Nullable Component err;
	private int errTicks;
	private List<int[][]> rounds = List.of();
	private final List<DiceThrowPath[]> paths = new ArrayList<>();
	private int cueRound = -1;
	private int cueIndexYou;
	private int cueIndexThem;
	private int beatsPlayed;
	private final DiceThrowPath.Sample sample = new DiceThrowPath.Sample();

	DiceScreen(CompoundTag state) {
		super("dice", Component.translatable("gui.burmaldaholic.extras.dice.title"), state, W, H);
		this.stakeAmount = lastAmount;
		this.target = state.getStringOr("target", "");
		this.shownSeq = state.getCompoundOrEmpty("result").getIntOr("seq", -1);
		this.theme = TableKit.theme(dev.nezo.burmaldaholic.client.ui.CasinoTheme.byId(state.getStringOr("theme", "village")));
		if (shownSeq >= 0) {
			loadRounds(state.getCompoundOrEmpty("result"));
			resultAt = Util.getMillis() - 60_000; // opened on an old result: settled
		}
	}

	private int ax() {
		return ox + 33;
	}

	private int ay() {
		return oy + 34;
	}

	@Override
	protected void onStateChanged(CompoundTag oldState, CompoundTag newState) {
		theme = TableKit.theme(dev.nezo.burmaldaholic.client.ui.CasinoTheme.byId(newState.getStringOr("theme", "village")));
		CompoundTag r = newState.getCompoundOrEmpty("result");
		int seq = r.getIntOr("seq", -1);
		if (seq >= 0 && seq != shownSeq) {
			shownSeq = seq;
			loadRounds(r);
			// the shake lasts at least 300 ms from the click: the timeline starts when the answer arrives
			resultAt = Util.getMillis();
			rollPressedAt = -1;
			skipped = false;
			cueRound = -1;
			beatsPlayed = 0;
		}
		String t = newState.getStringOr("target", "");
		if (!t.isEmpty()) {
			target = t;
		}
	}

	/** The rounds of a result (PvP: every round; house: one) and their shared throw paths. */
	private void loadRounds(CompoundTag r) {
		List<int[][]> out = new ArrayList<>();
		ListTag list = r.getListOrEmpty("rounds");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag rt = list.getCompoundOrEmpty(i);
			out.add(new int[][] {rt.getIntArray("you").orElse(new int[] {1, 1}), rt.getIntArray("them").orElse(new int[] {1, 1})});
		}
		if (out.isEmpty()) {
			out.add(new int[][] {r.getIntArray("you").orElse(new int[] {1, 1}), r.getIntArray("them").orElse(new int[] {1, 1})});
		}
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
		if (resultAt < 0) {
			return -1;
		}
		double t = Util.getMillis() - resultAt;
		if (skipped) {
			return DuelTimeline.endMs(rounds.size()) + 1;
		}
		return t;
	}

	@Override
	void showError(Component message) {
		super.showError(message);
		err = message;
		errTicks = 60;
		rollPressedAt = -1;
		FxSounds.play("ui_deny", 1f);
	}

	@Override
	public void tick() {
		super.tick();
		if (errTicks > 0 && --errTicks == 0) {
			err = null;
		}
		if (ticks % 20 == 0 && !state().getListOrEmpty("incoming").isEmpty()) {
			send("refresh", new CompoundTag());
		}
	}

	private boolean busy() {
		double t = t();
		return rollPressedAt >= 0 || (t >= 0 && t < DuelTimeline.endMs(rounds.size()));
	}

	// ---- widgets --------------------------------------------------------------------------------------------------

	@Override
	protected void layout() {
		ox = (width - W) / 2;
		oy = (height - H) / 2;
		CompoundTag s = state();
		for (int i = 0; i < ChipStacks.DENOMS.length; i++) {
			int d = ChipStacks.DENOMS[i];
			ChipButton b = addRenderableWidget(new ChipButton(ox + 8 + i * 27, oy + 212, d, theme, true, () -> lastAdded, v -> {
				lastAdded = v;
				long max = Math.max(0, Math.min(s.getLongOr("max", 0), s.getLongOr("balance", 0)));
				stakeAmount = Math.min(stakeAmount + v, Math.max(1, max));
				rebuildWidgets();
			}));
			b.active = BetSelector.CHIPS.equals(stakeKind);
		}
		addRenderableWidget(TableButton.icon(ox + 146, oy + 214, "clear", Component.translatable("gui.burmaldaholic.common.clear"), theme, b -> {
			stakeAmount = 0;
			rebuildWidgets();
		}));
		addRenderableWidget(TableButton.icon(ox + 168, oy + 214, "rules", Component.translatable("gui.burmaldaholic.common.rules"), theme,
			b -> showRules = !showRules));
		addRenderableWidget(TableButton.icon(ox + 190, oy + 214, "leave", Component.translatable("gui.burmaldaholic.common.leave"), theme, b -> onClose()));
		TableButton roll = addRenderableWidget(TableButton.action(ox + 373, oy + 189, TableButton.Kind.ROLL,
			Component.translatable("gui.burmaldaholic.extras.dice.roll"), b -> {
				lastAmount = Math.max(1, stakeAmount);
				rollPressedAt = Util.getMillis();
				skipped = true; // a new roll skips the previous result's beats
				FxSounds.play("dice_cup", 1f);
				send("roll", stakeArgs());
			}));
		roll.active = !busy() && (stakeAmount > 0 || !BetSelector.CHIPS.equals(stakeKind));
		roll.pulse(roll.active);
		// stake kind (pawn stakes) next to the stake line
		List<String> kinds = new ArrayList<>();
		kinds.add(BetSelector.CHIPS);
		for (String k : List.of(BetSelector.ITEM, BetSelector.XP, BetSelector.HEARTS)) {
			if (s.getBooleanOr("pawn_" + k, false)) {
				kinds.add(k);
			}
		}
		if (kinds.size() > 1) {
			if (!kinds.contains(stakeKind)) {
				stakeKind = BetSelector.CHIPS;
			}
			addRenderableWidget(TableKit.button(ox + 214, oy + 185, 40, Component.translatable("gui.burmaldaholic.extras.stake_kind", kindLabel(stakeKind)),
				theme, b -> {
					stakeKind = kinds.get((kinds.indexOf(stakeKind) + 1) % kinds.size());
					stakeAmount = BetSelector.CHIPS.equals(stakeKind) ? Math.max(1, s.getLongOr("min", 1)) : BetSelector.ITEM.equals(stakeKind) ? 0 : 1;
					rebuildWidgets();
				}));
			if (BetSelector.XP.equals(stakeKind) || BetSelector.HEARTS.equals(stakeKind)) {
				long max = BetSelector.XP.equals(stakeKind) ? Math.min(s.getIntOr("xp_level", 0), 30) : s.getIntOr("hearts_max", 3);
				addRenderableWidget(TableKit.button(ox + 214 + 90, oy + 185, 20, Component.translatable("gui.burmaldaholic.extras.add", Texts.number(1)), theme,
					b -> {
						stakeAmount = Math.min(stakeAmount + 1, Math.max(1, max));
						rebuildWidgets();
					}));
			}
		} else {
			stakeKind = BetSelector.CHIPS;
		}
		// PvP: opponent + challenge on the top bar
		if (s.getBooleanOr("pvp", false)) {
			ListTag nearby = s.getListOrEmpty("nearby");
			CompoundTag outgoing = s.getCompoundOrEmpty("outgoing");
			if (outgoing.isEmpty() && !nearby.isEmpty()) {
				int idx = indexOf(nearby, target);
				if (idx < 0) {
					idx = 0;
					target = nearby.getCompoundOrEmpty(0).getStringOr("uuid", "");
				}
				String name = nearby.getCompoundOrEmpty(idx).getStringOr("name", "");
				int next = (idx + 1) % nearby.size();
				CasinoButton who = addRenderableWidget(TableKit.button(ox + 110, oy + 1, 50,
					Component.translatable("gui.burmaldaholic.extras.labeled", Component.translatable("gui.burmaldaholic.extras.dice.target"), Texts.raw(name)),
					theme, b -> {
						target = nearby.getCompoundOrEmpty(next).getStringOr("uuid", "");
						rebuildWidgets();
					}));
				addRenderableWidget(TableKit.button(who.getX() + who.getWidth() + 4, oy + 1, 50,
					Component.translatable("gui.burmaldaholic.extras.dice.challenge_amount", Texts.chipsAcc(Math.max(1, stakeAmount))), theme, b -> {
						CompoundTag args = new CompoundTag();
						args.putString("target", target);
						args.putLong("amount", Math.max(1, stakeAmount));
						send("challenge", args);
					}));
			}
			ListTag incoming = s.getListOrEmpty("incoming");
			if (!incoming.isEmpty()) {
				CompoundTag c = incoming.getCompoundOrEmpty(0);
				int id = c.getIntOr("id", -1);
				CasinoButton acc = addRenderableWidget(TableKit.button(ax() + 116, ay() + 18, 56, Component.translatable("gui.burmaldaholic.extras.dice.accept"),
					theme, b -> answer("accept", id)).style(CasinoButton.Style.PRIMARY));
				addRenderableWidget(TableKit.button(acc.getX() + acc.getWidth() + 6, ay() + 18, 56, Component.translatable("gui.burmaldaholic.extras.dice.decline"),
					theme, b -> answer("decline", id)));
			}
		}
	}

	private CompoundTag stakeArgs() {
		CompoundTag t = new CompoundTag();
		t.putString("stake", stakeKind);
		t.putLong("amount", stakeAmount);
		return t;
	}

	/** "Bet: 25 chips" / "Bet: 3 levels" / "Bet: held item (20 chips)". */
	private Component stakeLine(CompoundTag state) {
		Component what = switch (stakeKind) {
			case BetSelector.XP -> Texts.plural("unit.burmaldaholic.level", stakeAmount);
			case BetSelector.HEARTS -> Texts.plural("unit.burmaldaholic.heart", stakeAmount);
			case BetSelector.ITEM -> Component.translatable("gui.burmaldaholic.extras.stake_item_value", Texts.chips(state.getLongOr("held_value", 0)));
			default -> Texts.chips(stakeAmount);
		};
		return Component.translatable("gui.burmaldaholic.common.bet_amount", what);
	}

	private static Component kindLabel(String kind) {
		return Component.translatable(switch (kind) {
			case BetSelector.ITEM -> "gui.burmaldaholic.common.stake_item";
			case BetSelector.XP -> "gui.burmaldaholic.common.stake_xp";
			case BetSelector.HEARTS -> "gui.burmaldaholic.common.stake_hearts";
			default -> "gui.burmaldaholic.common.stake_chips";
		});
	}

	private static int indexOf(ListTag list, String uuid) {
		for (int i = 0; i < list.size(); i++) {
			if (list.getCompoundOrEmpty(i).getStringOr("uuid", "").equals(uuid)) {
				return i;
			}
		}
		return -1;
	}

	private void answer(String action, int id) {
		CompoundTag args = new CompoundTag();
		args.putInt("id", id);
		send(action, args);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() == 0) {
			if (showRules) {
				showRules = false;
				return true;
			}
			double t = t();
			if (t >= DuelTimeline.reveal(rounds.size() - 1) && t < DuelTimeline.endMs(rounds.size())) {
				skipped = true; // skip the local chip beats
				rebuildWidgets();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 32 && t() >= 0 && t() < DuelTimeline.endMs(rounds.size())) {
			skipped = true;
			rebuildWidgets();
			return true;
		}
		return super.keyPressed(event);
	}

	// ---- rendering ------------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		TableChrome.backdrop(g, theme, width, height);
		TableGfx.region(g, TableGfx.sheet("extras/dice_duel_arena_" + theme.id), 360, 140, 0, 0, 360, 140, ax(), ay(), 0xFFFFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// the ExtrasScreen chrome (plain title / balance / error) is replaced by the arena's own
		extractContent(g, mouseX, mouseY, a);
		for (var child : children()) {
			if (child instanceof Renderable r) {
				r.extractRenderState(g, mouseX, mouseY, a);
			}
		}
		if (showRules) {
			g.nextStratum();
			int x = ax() + 30;
			int y = ay() + 20;
			TableGfx.blit(g, "core/plate_" + theme.id, x, y, 300, 70);
			int ty = y + 8;
			for (FormattedCharSequence seq : font.split(Component.translatable("gui.burmaldaholic.extras.dice.rules"), 284)) {
				g.text(font, seq, x + 8, ty, TableChrome.BONE, true);
				ty += 10;
			}
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		CompoundTag s = state();
		// top bar
		Component title = Component.translatable("gui.burmaldaholic.extras.dice.title");
		TableChrome.title(g, font, theme, TableChrome.FULL, ox, oy, title, 100);
		TableChrome.balance(g, font, theme, TableChrome.FULL, ox, oy, s.getLongOr("balance", 0));
		CompoundTag r = s.getCompoundOrEmpty("result");
		boolean pvp = "pvp".equals(r.getStringOr("mode", "house"));
		Component themName = pvp ? Texts.raw(r.getStringOr("opponent", "")) : Component.translatable("gui.burmaldaholic.extras.dice.dealer");
		plate(g, Component.translatable("gui.burmaldaholic.common.you"), "you", ax() + 96, TableChrome.seatTint(0));
		plate(g, themName, "other", ax() + 250, TableChrome.seatTint(1) == 0 ? 0 : 0xFFFF6E6A);
		double t = t();
		arena(g, t, r);
		// incoming challenge plaque (buttons are widgets)
		ListTag incoming = s.getListOrEmpty("incoming");
		if (s.getBooleanOr("pvp", false) && !incoming.isEmpty()) {
			CompoundTag c = incoming.getCompoundOrEmpty(0);
			Component text = Component.translatable("gui.burmaldaholic.extras.dice.invite", Texts.raw(c.getStringOr("name", "")),
				Texts.chips(c.getLongOr("stake", 0)));
			int w = Math.min(340, font.width(text) + 16);
			TableGfx.blit(g, "core/plate_gold_" + theme.id, ax() + 180 - w / 2, ay() + 2, w, 14);
			g.text(font, TableChrome.fit(font, text, w - 8), ax() + 180 - Math.min(font.width(text), w - 8) / 2, ay() + 5, TableChrome.GOLD, true);
		}
		CompoundTag outgoing = s.getCompoundOrEmpty("outgoing");
		if (!outgoing.isEmpty()) {
			Component o = Component.translatable("msg.burmaldaholic.extras.dice.challenge_sent", Texts.raw(outgoing.getStringOr("name", "")),
				Texts.chips(outgoing.getLongOr("stake", 0)));
			var seq = TableChrome.fit(font, o, 220);
			g.text(font, seq, ox + 214 - font.width(seq) / 2, oy + 7, TableChrome.MUTED, true);
		}
		// bottom bar: stake line + status
		Component stake = stakeLine(s);
		g.text(font, TableChrome.fit(font, stake, 150), ox + 216, oy + 218, TableChrome.BONE, true);
		if (err != null) {
			var seq = TableChrome.fit(font, err, 300);
			g.text(font, seq, ox + 214 - font.width(seq) / 2, oy + 202, TableChrome.RED, true);
		}
	}

	private void plate(GuiGraphicsExtractor g, Component name, String kind, int cx, int tint) {
		int w = font.width(name) + 20;
		int x = cx - w / 2;
		int y = ay() + 16;
		TableGfx.blit(g, "core/seat_" + kind + "_" + theme.id, x, y, w, 15);
		TableGfx.blit(g, "core/seat_stripe", x + 3, y + 2, 9, 10, tint);
		g.text(font, name, x + 16, y + 4, TableChrome.BONE, true);
	}

	private static final Identifier DICE = TableGfx.sheet("extras/dice_duel_dice_big");
	private static final Identifier TUMBLE = TableGfx.sheet("extras/dice_duel_tumble_big");

	/** Everything that moves in the arena at {@code t} ms of the result (or the waiting shake). */
	private void arena(GuiGraphicsExtractor g, double t, CompoundTag r) {
		boolean reduced = FxSettings.reduceMotion();
		long now = Util.getMillis();
		int n = rounds.size();
		boolean hasResult = t >= 0 && r.getIntOr("seq", -1) >= 0 && n > 0;
		boolean waiting = rollPressedAt >= 0;
		int round = hasResult ? DuelTimeline.roundAt(t, n) : 0;
		double rt = hasResult ? t - DuelTimeline.roundStart(round) : -1;
		// cups
		int cupFrame;
		if (waiting && !reduced) {
			cupFrame = DuelTimeline.waitingCupFrame(now - rollPressedAt);
			if ((now - rollPressedAt) % 180 < 20) {
				FxSounds.play("dice_cup", 0.6f, 1f);
			}
		} else {
			cupFrame = hasResult ? DuelTimeline.cupFrame(rt, reduced) : 0;
		}
		int shake = cupFrame >= 1 && cupFrame <= 3 && !reduced ? (cupFrame == 1 ? -2 : cupFrame == 2 ? 2 : 0) : 0;
		var cup = TableGfx.spriteFile("extras/dice_duel_cup");
		TableGfx.region(g, cup, 40, 240, 0, cupFrame * 40, 40, 40, ax() + 8 + shake, ay() + 64, 0xFFFFFFFF);
		int themFrame = waiting && !reduced ? 1 + (cupFrame % 3) : cupFrame;
		TableGfx.regionMirrored(g, cup, 40, 240, 0, themFrame * 40, 40, 40, ax() + 312 - shake, ay() + 64);
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
		// dice
		for (int side = 0; side < 2; side++) {
			double dt = rt - (DuelTimeline.throwAt(0, side));
			if (dt < 0) {
				continue;
			}
			double sweep = !last && rt >= DuelTimeline.SWEEP_AT ? Math.min(1, (rt - DuelTimeline.SWEEP_AT) / DuelTimeline.SWEEP_MS) : 0;
			if (sweep >= 1) {
				continue;
			}
			boolean winner = last && rt >= DuelTimeline.COMPARE && ("win".equals(outcome) ? side == 0 : "lose".equals(outcome) ? side == 1 : false);
			for (int d = 0; d < 2; d++) {
				pp[side].sample(d, reduced ? pp[side].durationMs() : dt, sample);
				double alpha = reduced ? Math.min(1, dt / 200.0) : 1;
				int x = ax() + (int) Math.round(sample.x) - 20;
				int y = ay() + (int) Math.round(sample.y - sample.z) - 20;
				if (sweep > 0) {
					int cx = side == 0 ? ax() + 8 : ax() + 312;
					double e = Ease.IN_CUBIC.apply(sweep);
					x = (int) Math.round(x + (cx - x) * e);
					y = (int) Math.round(y + (ay() + 64 - y) * e);
					alpha *= 1 - sweep;
				}
				if (sample.frame >= 0) {
					TableGfx.region(g, TUMBLE, 336, 42, sample.frame * 42, 0, 42, 42, x - 1, y - 1, TableGfx.fade(alpha));
				} else {
					int col = 0;
					double land = dt - pp[side].durationMs();
					if (!reduced && land >= 0 && land < 120) {
						col = land < 60 ? 1 : 2; // land squash + bright rim
					} else if (winner) {
						col = FxSettings.flashes() && !reduced ? 3 + (int) ((now / 100) % 6) : 3;
					} else if (!reduced && FxSettings.flashes() && land > 400 && ((now / 90) % 40) < 4) {
						col = 9 + (int) ((now / 90) % 4); // idle shine sweep
					}
					TableGfx.region(g, DICE, 520, 240, col * 40, (sample.face - 1) * 40, 40, 40, x, y, TableGfx.fade(alpha));
				}
			}
		}
		// plaques: totals pop when each pair rests
		boolean compared = rt >= DuelTimeline.COMPARE;
		plaque(g, ax() + 80, rt - DuelTimeline.YOU_LAND, youTotal, compared && last ? "win".equals(outcome) ? 1 : "lose".equals(outcome) || "house_tie".equals(outcome) ? -1 : 0
			: 0, compared && tie, rt - DuelTimeline.COMPARE, reduced);
		plaque(g, ax() + 244, rt - DuelTimeline.THEM_LAND, themTotal, compared && last ? "lose".equals(outcome) || "house_tie".equals(outcome) ? 1
			: "win".equals(outcome) ? -1 : 0 : 0, compared && tie, rt - DuelTimeline.COMPARE, reduced);
		vs(g, compared && last ? "win".equals(outcome) ? 2 : "lose".equals(outcome) || "house_tie".equals(outcome) ? 1 : 0 : 0);
		// house tie on 7: the red seal slams onto the dealer's side
		if (last && "house_tie".equals(outcome) && compared) {
			double k = reduced ? 1 : Math.min(1, (rt - DuelTimeline.COMPARE) / 250.0);
			int drop = (int) Math.round(18 * (1 - Ease.OUT_BOUNCE.apply(k)));
			TableGfx.blit(g, "extras/dice_duel_house_stamp", ax() + 239, ay() + 30 - drop, 42, 42);
		}
		// tie in a PvP round: "Tie! Re-roll k of 3"
		if (!last && compared && rt < DuelTimeline.COMPARE + DuelTimeline.TIE_BANNER_MS) {
			Component tieText = Component.translatable("gui.burmaldaholic.extras.dice.fx.tie_reroll", Texts.number(round + 2), Texts.number(n));
			TableChrome.banner(g, font, "banner_" + theme.id, ax() + 100, ay() + 104, 160, 20, tieText, TableChrome.GOLD, null, 0, 1);
		}
		cues(round, rt, last, outcome, compared);
		if (last) {
			chips(g, rt, outcome, r, reduced);
		}
	}

	private void vs(GuiGraphicsExtractor g, int frame) {
		TableGfx.frame(g, "extras/dice_duel_vs", 32, 32, 3, frame, ax() + 164, ay() + 54, 0xFFFFFFFF);
	}

	/** A total plaque: {@code state} 1 winner (gold, lifted), -1 loser (cracked, dimmed), 0 neutral; tie: grey + shake. */
	private void plaque(GuiGraphicsExtractor g, int x, double since, int total, int state, boolean tie, double sinceCompare, boolean reduced) {
		if (since < 0) {
			return;
		}
		double pop = reduced ? 1 : Ease.OUT_BACK.apply(Math.min(1, since / DuelTimeline.POP_MS));
		int dy = (int) Math.round(6 * (1 - pop));
		int dx = 0;
		if (tie && !reduced && sinceCompare >= 0 && sinceCompare < 300) {
			dx = (int) Math.round(Math.sin(sinceCompare / 300.0 * Math.PI * 4));
		}
		String sprite = state > 0 ? "win" : state < 0 ? "lose" : "neutral";
		int lift = state > 0 ? 2 : 0;
		int tint = state < 0 ? 0xC0FFFFFF : 0xFFFFFFFF;
		TableGfx.blit(g, "extras/dice_duel_plaque_" + sprite, x + dx, y(dy) - lift, 32, 24, tint);
		Component n = Texts.number(total);
		int color = state > 0 ? TableChrome.GOLD : state < 0 ? 0xFF8C7CA8 : tie ? 0xFFB8B0C0 : TableChrome.BONE;
		g.pose().pushMatrix();
		g.pose().translate(x + dx + 16 - font.width(n), y(dy) - lift + 4);
		g.pose().scale(2f, 2f);
		g.text(font, n, 0, 0, color, true);
		g.pose().popMatrix();
		if (state < 0 && sinceCompare >= 0) {
			int f = reduced || sinceCompare > 120 ? 1 : 0;
			TableGfx.frame(g, "extras/dice_duel_crack", 32, 24, 2, f, x + dx, y(dy), 0xFFFFFFFF);
		}
	}

	private int y(int dy) {
		return ay() + 40 + dy;
	}

	private void cues(int round, double rt, boolean last, String outcome, boolean compared) {
		if (cueRound != round) {
			cueRound = round;
			cueIndexYou = 0;
			cueIndexThem = 0;
			beatsPlayed = 0;
		}
		if (rt > DuelTimeline.endMs(1) + 500) {
			return; // an old result
		}
		for (int side = 0; side < 2; side++) {
			double dt = rt - DuelTimeline.throwAt(0, side);
			var list = paths.get(round)[side].cues();
			int idx = side == 0 ? cueIndexYou : cueIndexThem;
			while (idx < list.size() && list.get(idx).atMs() <= dt) {
				var c = list.get(idx++);
				FxSounds.play(c.sound(), side == 0 ? 1f : 0.8f, c.pitch());
			}
			if (side == 0) {
				cueIndexYou = idx;
			} else {
				cueIndexThem = idx;
			}
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
			if (pvp && "win".equals(outcome)) {
				FxSounds.play("pvp_victory", 0.8f, 1f);
			}
		}
	}

	/** Chips across the table and the outcome banner (the last round). */
	private void chips(GuiGraphicsExtractor g, double rt, String outcome, CompoundTag r, boolean reduced) {
		long net = r.getLongOr("net", 0);
		long stake = Math.max(1, Math.abs(net));
		if (rt < DuelTimeline.CHIPS) {
			TableChrome.stack(g, stake, ax() + 150, ay() + 124, 0, 0xFFFFFFFF);
			return;
		}
		double k = reduced ? 1 : Math.min(1, (rt - DuelTimeline.CHIPS) / 400.0);
		double fly = reduced ? 1 : Math.max(0, Math.min(1, (rt - DuelTimeline.CHIPS - 400) / 300.0));
		int bx = ox + 400;
		int by = oy + 10;
		switch (outcome) {
			case "win" -> {
				int x0 = ax() + 150;
				int theirs = (int) Math.round(ax() + 200 + (ax() + 164 - (ax() + 200)) * Ease.OUT_CUBIC.apply(k));
				if (fly < 1) {
					int fx = (int) Math.round(x0 + (bx - x0) * Ease.IN_CUBIC.apply(fly));
					int fy = (int) Math.round(ay() + 124 + (by - ay() - 124) * Ease.IN_CUBIC.apply(fly));
					TableChrome.stack(g, stake, fx, fy, 0, 0xFFFFFFFF);
					TableChrome.stack(g, stake, (int) Math.round(theirs + (bx - theirs) * Ease.IN_CUBIC.apply(fly)), fy, 0, 0xFFFFFFFF);
				}
				Component plus = Component.empty().append(Texts.raw("+")).append(Texts.number(net));
				g.text(font, plus, ax() + 150 - font.width(plus) / 2 + 10, ay() + 100, TableChrome.GOLD, true);
			}
			case "lose", "house_tie" -> {
				int x = (int) Math.round(ax() + 150 + (ax() + 210 - (ax() + 150)) * Ease.IN_CUBIC.apply(k));
				if (k < 1) {
					TableChrome.stack(g, stake, x, ay() + 124, 0, TableGfx.fade(1 - k * 0.8));
				}
			}
			default -> {
				if (k < 1) {
					TableChrome.stack(g, stake, ax() + 150, ay() + 124 + (int) Math.round(30 * k), 0, TableGfx.fade(1 - k));
				}
			}
		}
		// outcome banner
		Component line1;
		Component line2;
		String sprite;
		int c1 = TableChrome.GOLD;
		switch (outcome) {
			case "win" -> {
				line1 = Component.translatable("gui.burmaldaholic.extras.dice.fx.victory");
				line2 = Component.translatable("gui.burmaldaholic.extras.dice.win", Texts.chips(net));
				sprite = "banner_win";
			}
			case "lose" -> {
				line1 = Component.translatable("gui.burmaldaholic.extras.dice.fx.defeat");
				line2 = Component.translatable("gui.burmaldaholic.extras.dice.lose", Texts.chipsAcc(-net));
				sprite = "banner_lose";
				c1 = 0xFFE08080;
			}
			case "house_tie" -> {
				line1 = Component.translatable("gui.burmaldaholic.extras.dice.fx.defeat");
				line2 = Component.translatable("gui.burmaldaholic.extras.dice.tie_seven");
				sprite = "banner_lose";
				c1 = 0xFFE08080;
			}
			case "refund" -> {
				line1 = Component.translatable("gui.burmaldaholic.extras.dice.tie");
				line2 = Component.translatable("msg.burmaldaholic.extras.dice.pvp_refund");
				sprite = "banner_" + theme.id;
				c1 = TableChrome.MUTED;
			}
			default -> {
				line1 = Component.translatable("gui.burmaldaholic.extras.dice.tie");
				line2 = null;
				sprite = "banner_" + theme.id;
				c1 = TableChrome.MUTED;
			}
		}
		double bk = reduced ? 1 : Math.min(1, (rt - DuelTimeline.CHIPS) / 250.0);
		int dy = (int) Math.round(8 * (1 - Ease.OUT_BACK.apply(bk)));
		TableChrome.banner(g, font, sprite, ox + 130, oy + 178 + dy, 168, 24, line1, c1, line2, TableChrome.BONE, bk);
	}
}
