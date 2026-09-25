package dev.nezo.burmaldaholic.games.extras.client.pvp.coin;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.PlateRow;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpDraw;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.anim.CoinAnim;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinChain;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelNet;
import dev.nezo.burmaldaholic.pvp.logic.PvpMotion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Coin Flip Duel (PVP.md §4.5; visual/extras.md §3.3, extras-pvp.md §2.2) on the arena: the VS intro (your plate and
 * the rival's slide in, the VS badge pops), the pot over the Lucky Coin on its pad, the Double-or-nothing chain
 * tracker, the countdown digits with the ring, the open-ended toss (the face is not known until the {@code land}
 * step), the landing that decelerates onto the true face, bounces and slides towards the winner, whose plate turns
 * gold (the loser's dims), and the heat per chain link (warm glint, red-hot rim, flames, soul fire). Decisions (Double
 * or nothing Heads / Tails, Walk away; Let it ride, Take the money), Rematch, Taunt… and Close are casino buttons; they
 * go to the server as {@code decide} actions. ALL SQUARE shows its banner.
 */
public final class CoinDuelScreen extends PvpPanel implements PvpScreens.ModeScreen {
	private static final Identifier SPIN = Kit.sheet("extras/coin_spin");
	private static final Identifier HEAT = Kit.sheet("extras/coin_heat");
	private static final Identifier PIPS = Kit.sheet("extras/chain_pips");
	private static final Identifier MINI = Kit.sheet("extras/coin_mini");
	private static final int CY = 140;
	/** Chain results seen this session: chain id → {heads, you won} per link. */
	private static final Map<String, List<int[]>> CHAINS = new HashMap<>();

	private MatchView view;
	private int steps;
	private long stepAt = Util.getMillis();
	private String sentDecision = "";
	private double spinH;
	private double spinDy;
	private boolean landSound;
	private long bannerAt = -1;

	public CoinDuelScreen(JsonObject state) {
		super(Component.translatable("gui.burmaldaholic.pvp.coin.title"), 256, 180);
		this.view = new MatchView(state);
		this.steps = view.steps().size();
		// opened late on a landed flip: no replay
		if (view.step("land") != null) stepAt = 0;
	}

	@Override
	public Screen screen() {
		return this;
	}

	@Override
	protected boolean grudge() {
		return MatchView.bool(view.json, "grudge", false);
	}

	@Override
	public void update(JsonObject state) {
		MatchView next = new MatchView(state);
		int n = next.steps().size();
		if (n != steps || !next.id().equals(view.id())) {
			if (n > 0 && "land".equals(next.steps().getLast().kind())) {
				// freeze the loop where it is; the landing decelerates from here onto the true face
				double ms = Util.getMillis() - stepAt;
				spinH = CoinAnim.duelSpinH(ms);
				spinDy = CoinAnim.duelSpinDy(ms);
				landSound = false;
				remember(next);
			}
			stepAt = Util.getMillis();
		}
		boolean decisionChanged = !MatchView.str(next.decision(), "id", "").equals(MatchView.str(view.decision(), "id", ""))
			|| !next.id().equals(view.id());
		if (decisionChanged) sentDecision = "";
		if (MatchView.str(next.json, "event", "").equals("all_square") || MatchView.bool(next.chain(), "allSquare", false)) bannerAt = Util.getMillis();
		steps = n;
		view = next;
		rebuild();
	}

	private static void remember(MatchView v) {
		MatchView.StepView land = v.step("land");
		if (land == null) return;
		String chain = MatchView.str(v.json, "chainOf", "");
		if (chain.isEmpty()) chain = v.id();
		List<int[]> list = CHAINS.computeIfAbsent(chain, k -> new ArrayList<>());
		int link = MatchView.integer(v.json, "link", 0);
		while (list.size() > link) list.removeLast();
		list.add(new int[] {MatchView.bool(land.data(), "heads", true) ? 1 : 0, MatchView.integer(land.data(), "winner", -1) == v.you() ? 1 : 0});
	}

	private boolean heads0() {
		return MatchView.bool(view.params(), "heads", true);
	}

	private MatchView.@Nullable StepView lastStep() {
		var all = view.steps();
		return all.isEmpty() ? null : all.getLast();
	}

	private static void send(String action, CompoundTag args) {
		ClientPlayNetworking.send(new CoinDuelNet.Action(action, args));
	}

	private void decide(String decision, long option) {
		CompoundTag a = new CompoundTag();
		a.putString("decision", decision);
		a.putLong("option", option);
		JsonObject d = view.decision();
		if (d != null) {
			a.putString("match", MatchView.str(d, "match", view.id()));
			a.putLong("seq", MatchView.lng(d, "seq", -1));
		}
		send("decide", a);
		sentDecision = decision;
		rebuild();
	}

	@Override
	protected int layout() {
		Flow flow = new Flow(left + 20, top + 207, 360);
		JsonObject d = view.decision();
		String id = d == null ? "" : MatchView.str(d, "id", "");
		if (d != null && !id.equals(sentDecision)) {
			if (id.equals(CoinChain.DON_OFFER)) {
				if (MatchView.str(d, "blocked", "").isEmpty()) {
					flow.button(Component.translatable("gui.burmaldaholic.pvp.coin.don_heads"), 80, KitButton.Style.PRIMARY,
						b -> decide(CoinChain.DON_OFFER, CoinChain.DON_HEADS)).breathe(true);
					flow.button(Component.translatable("gui.burmaldaholic.pvp.coin.don_tails"), 80, KitButton.Style.PRIMARY,
						b -> decide(CoinChain.DON_OFFER, CoinChain.DON_TAILS)).breathe(true);
				}
				flow.button(Component.translatable("gui.burmaldaholic.pvp.coin.walk_away"), 60, b -> decide(CoinChain.DON_OFFER, CoinChain.WALK_AWAY));
			} else if (id.equals(CoinChain.LET_IT_RIDE)) {
				flow.button(Component.translatable("gui.burmaldaholic.pvp.coin.let_it_ride"), 70, KitButton.Style.PRIMARY,
					b -> decide(CoinChain.LET_IT_RIDE, CoinChain.RIDE)).selected(true);
				flow.button(Component.translatable("gui.burmaldaholic.pvp.coin.take_money"), 70, b -> decide(CoinChain.LET_IT_RIDE, CoinChain.TAKE));
			}
		} else if (view.settled()) {
			flow.button(Component.translatable("gui.burmaldaholic.pvp.rematch"), 70, KitButton.Style.PRIMARY, b -> {
				CompoundTag a = new CompoundTag();
				a.putString("id", view.id());
				send("rematch", a);
				b.active = false;
			});
		}
		flow.button(Component.translatable("gui.burmaldaholic.pvp.taunt.button"), 56, b -> {
			if (minecraft != null) {
				minecraft.gui.setScreen(new TauntScreen(this, line -> {
					CompoundTag a = new CompoundTag();
					a.putInt("line", line);
					send("taunt", a);
				}));
			}
		});
		flow.button(Component.translatable("gui.burmaldaholic.common.close"), 56, b -> onClose());
		return Math.min(flow.bottom(), top + 232);
	}

	/** One line under the coin: landed / takes, the chain, the decision (the most recent that applies). */
	private @Nullable Component infoLine() {
		JsonObject d = view.decision();
		if (d != null && !MatchView.str(d, "id", "").equals(sentDecision)) {
			long dd = MatchView.lng(d, "deficit", 0);
			String blocked = MatchView.str(d, "blocked", "");
			if (MatchView.str(d, "id", "").equals(CoinChain.DON_OFFER)) {
				if (blocked.equals(CoinChain.DON_UNAFFORDABLE)) return Component.translatable(CoinChain.DON_UNAFFORDABLE, Texts.chips(MatchView.lng(d, "blockedArg", dd)));
				if (!blocked.isEmpty()) return Component.translatable(CoinChain.DON_LIMIT);
				return Component.translatable("gui.burmaldaholic.pvp.coin.don_explain", Texts.chips(dd), Texts.chips(2 * dd));
			}
			int other = view.you() == 0 ? 1 : 0;
			Component called = side(MatchView.integer(d, "called", 1) != CoinChain.DON_TAILS);
			return Component.translatable("gui.burmaldaholic.pvp.coin.don_request", view.name(other), Texts.chips(dd), called, Texts.chips(dd));
		}
		MatchView.StepView land = view.step("land");
		if (land != null && landed()) {
			int winner = MatchView.integer(land.data(), "winner", -1);
			long pay = view.payout(winner);
			if (pay < 0) pay = view.pot() - PvpMath.rake(view.pot(), view.rakeBasisPoints());
			return Component.translatable("gui.burmaldaholic.pvp.coin.takes", view.name(winner), Texts.chipsAcc(pay));
		}
		JsonObject chain = view.chain();
		int loser = MatchView.integer(chain, "loser", -1);
		long deficit = MatchView.lng(chain, "deficit", 0);
		if (loser >= 0 && deficit > 0) return Component.translatable("gui.burmaldaholic.pvp.coin.chain", view.name(loser), Texts.chips(deficit));
		return null;
	}

	private static Component side(boolean heads) {
		return Component.translatable(heads ? "gui.burmaldaholic.extras.coin.heads" : "gui.burmaldaholic.extras.coin.tails");
	}

	private boolean landed() {
		MatchView.StepView last = lastStep();
		return view.step("land") != null && (last == null || !"land".equals(last.kind()) || Util.getMillis() - stepAt >= 700);
	}

	/** Chain link 1…5 (heat). */
	private int link() {
		return Math.max(1, Math.min(5, MatchView.integer(view.json, "link", 0) + 1));
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	protected void playArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int k = link();
		boolean rm = Kit.reduceMotion();
		if (k >= 2) {
			double v = 0.10 + 0.05 * (k - 2);
			if (k == 5 && !rm) v += 0.05 * Math.sin(Util.getMillis() / 1000.0 * Math.PI);
			Kit.region(g, Kit.sheet("core/fx/vignette_red"), 256, 256, 0, 0, 256, 256, left, top, 400, 240, Kit.alpha(0xFF000000, v * 3));
		}
		Kit.sprite(g, Kit.extras("coin_pad"), left + 128, top + 158, 144, 40);
	}

	@Override
	protected void content(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		long now = Util.getMillis();
		boolean rm = Kit.reduceMotion();
		double intro = now - openedAt;
		List<PvpSeat> seats = PlateRow.ordered(view.seatsFull());
		int[] winners = view.settled() || view.step("land") != null && landed() ? winnerArray() : null;
		// plates slide in from the edges (VS intro)
		double slide = PvpMotion.plateSlide(intro, rm);
		for (int i = 0; i < seats.size() && i < 2; i++) {
			PvpSeat s = seats.get(i);
			boolean left0 = i == 0;
			int x = left0 ? left + 16 - (int) Math.round(slide * 160) : left + 234 + (int) Math.round(slide * 160);
			PvpDraw.PlateKind kind = winners == null ? PvpDraw.kindOf(s, grudge()) : contains(winners, s.index()) ? PvpDraw.PlateKind.WINNER
				: PvpDraw.PlateKind.LOSER;
			boolean heads = s.index() == 0 ? heads0() : !heads0();
			Component line = Component.translatable("gui.burmaldaholic.pvp.coin.plate_line", side(heads), Texts.number(s.stake()));
			int dy = kind == PvpDraw.PlateKind.LOSER ? 2 : 0;
			PvpDraw.plate(g, font, s, kind, x, top + 30 + dy, 150, line);
			Kit.region(g, MINI, 28, 14, heads ? 0 : 14, 0, 14, 14, x + 150 - 20, top + 30 + dy + 3);
			if (s.pressed()) PvpDraw.readyTick(g, x + 124, top + 18);
		}
		PvpDraw.vs(g, font, left + 200, top + 45, PvpMotion.vsPop(intro, rm));
		// the pot and the chain tracker
		PvpDraw.pot(g, font, view.pot(), Math.max(1, view.seatsFull().isEmpty() ? 1 : view.seatsFull().get(0).stake()), left + 188, top + 64);
		chainPips(g);
		coin(g, now, rm);
		Component info = infoLine();
		if (info != null) Kit.wrapCentered(g, font, info, left + 200, top + 186, 360, 2, Kit.BONE, true);
		if (bannerAt >= 0) {
			PvpDraw.modeBanner(g, font, Component.translatable("gui.burmaldaholic.pvp.coin.all_square_title"), left + 200, top + 96, 300, now - bannerAt);
		}
	}

	private int[] winnerArray() {
		MatchView.StepView land = view.step("land");
		int w = land == null ? -1 : MatchView.integer(land.data(), "winner", -1);
		return w < 0 ? new int[0] : new int[] {w};
	}

	private static boolean contains(int[] a, int v) {
		for (int x : a) if (x == v) return true;
		return false;
	}

	private void chainPips(GuiGraphicsExtractor g) {
		String chain = MatchView.str(view.json, "chainOf", "");
		if (chain.isEmpty()) chain = view.id();
		List<int[]> past = CHAINS.getOrDefault(chain, List.of());
		int link = MatchView.integer(view.json, "link", 0);
		if (link == 0 && view.decision() == null && past.size() <= 1) return; // a single flip: no chain yet
		int x0 = left + 200 - (5 * 18) / 2;
		for (int i = 0; i < 5; i++) {
			int pip = 0;
			if (i < past.size() && i != link) {
				int[] r = past.get(i);
				pip = r[0] == 1 ? (r[1] == 1 ? 2 : 3) : (r[1] == 1 ? 4 : 5);
			} else if (i == link) {
				pip = 1;
			}
			Kit.frame(g, PIPS, 96, 16, 16, 16, pip, x0 + i * 18, top + 104, 0xFFFFFFFF);
		}
	}

	private void coin(GuiGraphicsExtractor g, long now, boolean rm) {
		MatchView.StepView last = lastStep();
		String kind = last == null ? "" : last.kind();
		double ms = now - stepAt;
		int cx = left + 200;
		int frame = CoinAnim.IDLE_FRAME;
		double dy = 0;
		double dx = 0;
		boolean squash = false;
		MatchView.StepView land = view.step("land");
		if (kind.equals("countdown")) {
			double wob = rm ? 0 : 4 * Math.sin(ms / 1000.0 * Math.PI * 2);
			g.pose().pushMatrix();
			g.pose().translate(cx, top + CY);
			g.pose().rotate((float) Math.toRadians(wob));
			Kit.region(g, SPIN, 768, 64, frame * 64, 0, 64, 64, -32, -32);
			g.pose().popMatrix();
			PvpDraw.countdown(g, font, cx, top + CY - 4, last.ticks() - ms / 50.0, last.ticks());
			return;
		}
		if (kind.equals("spin")) {
			frame = CoinAnim.frame(CoinAnim.duelSpinH(ms));
			dy = rm ? -CoinAnim.DUEL_APEX : CoinAnim.duelSpinDy(ms);
		} else if (land != null) {
			boolean heads = MatchView.bool(land.data(), "heads", true);
			int winner = MatchView.integer(land.data(), "winner", -1);
			List<PvpSeat> order = PlateRow.ordered(view.seatsFull());
			int dir = order.isEmpty() ? 0 : order.get(0).index() == winner ? -1 : 1;
			double t = kind.equals("land") && stepAt > 0 && !rm ? ms : 5000;
			double[] p = CoinAnim.duelLand(spinH, spinDy, heads, t, dir);
			dx = p[0];
			dy = p[1];
			frame = (int) p[2];
			squash = p[4] > 0;
			if (p[3] > 0 && !landSound && t < 5000) {
				landSound = true;
				FxSounds.play("coin_land", 1f, 1f);
			}
		}
		int heat = link();
		g.pose().pushMatrix();
		g.pose().translate((float) (cx + dx), (float) (top + CY + dy));
		if (squash) g.pose().scale(1.12f, 0.88f);
		Kit.region(g, SPIN, 768, 64, frame * 64, 0, 64, 64, -32, -32);
		if (heat >= 3) Kit.region(g, HEAT, 128, 64, heat == 5 ? 64 : 0, 0, 64, 64, -32, -32, 64, 64, Kit.fade(heat == 3 ? 0.5 : 0.8));
		if (heat >= 4 && !rm) {
			String flame = heat == 5 ? "coin_flame_soul" : "coin_flame";
			for (int r = 0; r < 4; r++) {
				g.pose().pushMatrix();
				g.pose().rotate((float) Math.toRadians(r * 90));
				Kit.sprite(g, Kit.extras(flame), -8, -44, 16, 24);
				g.pose().popMatrix();
			}
		}
		g.pose().popMatrix();
	}

	@Override
	protected void overlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<PvpSeat> seats = view.seatsFull();
		PlateRow.bubblesAt(g, font, view.json, seats, left, top + 30, new int[] {16, 234});
	}

	@Override
	public void tick() {
		super.tick();
		if (ticks % 20 == 0 && view.decision() != null) rebuild();
	}
}
