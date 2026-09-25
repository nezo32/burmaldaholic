package dev.nezo.burmaldaholic.games.extras.client.pvp.wheel;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.client.pvp.kit.Faces;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpDraw;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMath;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.client.WheelDraw;
import dev.nezo.burmaldaholic.games.extras.client.pvp.coin.MatchView;
import dev.nezo.burmaldaholic.games.extras.client.pvp.coin.PvpPanel;
import dev.nezo.burmaldaholic.games.extras.client.pvp.coin.TauntScreen;
import dev.nezo.burmaldaholic.games.extras.logic.anim.WheelAnim;
import dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelMath;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Wheel Party (PVP.md §6.5; visual/extras.md §4.3, extras-pvp.md §4.2): the carnival wheel block on the arena (rim,
 * bulbs, hub, flapper, stand) with a face painted from the stake shares in the joiners' dye colours and their heads
 * on the arcs (8 px on arcs ≥ 6°, 12 px on shares ≥ 20 %), turning slowly while bets are open; the legend as lobby
 * rows (swatch, head, name, share); the pot. The spin lands on the server's {@code angle1000} with the same curve as
 * the solo wheel (pull-back, spin, settle), the flapper ticking on slice boundaries and a decorative peg every 10°,
 * the owner under the pointer named with their head; "By a hair" (only when the server flags it) crawls the last
 * second over the real boundary. Then the winning slice is outlined and the others dim, the winner's head takes the
 * hub with the crown, and UNDERDOG / by-a-hair lines follow the server's result step.
 */
public final class WheelPartyScreen extends PvpPanel implements PvpScreens.ModeScreen {
	private static final int WX = 22;
	private static final int WY = 32;
	private static final int RX = 212;

	private MatchView view;
	private int steps;
	private long stepAt = Util.getMillis();
	private int syncTick;
	private EditBox topUpBox;
	private long topUp;
	private double idle;
	private long idleAt = Util.getMillis();
	private double spinFrom;
	private long lastPeg = Long.MIN_VALUE;
	private long lastTickMs;

	public WheelPartyScreen(JsonObject state) {
		super(Component.translatable("gui.burmaldaholic.pvp.wheel.title"), 256, 220);
		this.view = new MatchView(state);
		this.steps = view.steps().size();
		this.topUp = 10;
		if (view.step("spin") != null) stepAt = 0; // opened late: at rest
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
			if (next.step("spin") != null && view.step("spin") == null) spinFrom = idleAngle();
			stepAt = Util.getMillis();
		}
		steps = n;
		syncTick = ticks;
		topUp = parse(topUpBox, topUp);
		view = next;
		rebuild();
	}

	/** Display name of a slice owner: a player's name, or a bot's name with its tag. */
	static Component name(String name, boolean bot, String level) {
		if (!bot) return Texts.raw(name);
		BotDifficulty d = BotDifficulty.byId(level, BotDifficulty.NORMAL);
		return Component.translatable("gui.burmaldaholic.bots.display_level", Component.translatable(name), Component.translatable(d.styleKey()));
	}

	private long[] stakes() {
		List<MatchView.Seat> seats = view.seats();
		long[] out = new long[seats.size()];
		for (int i = 0; i < out.length; i++) out[i] = seats.get(i).stake();
		return out;
	}

	private boolean youIn() {
		return view.you() >= 0;
	}

	private boolean isHost() {
		MatchView.Seat me = view.seat(view.you());
		return me != null && me.host();
	}

	@Override
	protected int layout() {
		if (view.lobby() && youIn()) {
			topUpBox = amountBox(left + RX, top + 164, 60, topUp);
			KitButton add = KitButton.of(left + RX + 64, top + 164, 112, Component.translatable("gui.burmaldaholic.pvp.wheel.add"), KitButton.Style.SECONDARY,
				b -> {
					topUp = parse(topUpBox, topUp);
					CompoundTag a = new CompoundTag();
					a.putLong("extra", topUp);
					WheelPartyClient.send("top_up", a);
				});
			addRenderableWidget(add);
			if (isHost() && view.seats().size() >= 2) {
				addRenderableWidget(KitButton.of(left + RX, top + 187, 88, Component.translatable("gui.burmaldaholic.pvp.wheel.spin_now"),
					KitButton.Style.PRIMARY, b -> WheelPartyClient.send("spin", new CompoundTag())).breathe(true));
			}
			addRenderableWidget(KitButton.of(left + RX + 92, top + 187, 84, Component.translatable("gui.burmaldaholic.pvp.lobby.leave"),
				KitButton.Style.SECONDARY, b -> WheelPartyClient.send("leave", new CompoundTag())));
		} else if (view.settled() && youIn()) {
			addRenderableWidget(KitButton.of(left + RX, top + 187, 176, Component.translatable("gui.burmaldaholic.pvp.rematch"), KitButton.Style.PRIMARY, b -> {
				CompoundTag a = new CompoundTag();
				a.putString("id", view.id());
				WheelPartyClient.send("rematch", a);
				b.active = false;
			}));
		}
		if (youIn()) {
			addRenderableWidget(KitButton.of(left + RX, top + 210, 86, Component.translatable("gui.burmaldaholic.pvp.taunt.button"), KitButton.Style.SECONDARY,
				b -> {
					if (minecraft != null) {
						minecraft.gui.setScreen(new TauntScreen(this, line -> {
							CompoundTag a = new CompoundTag();
							a.putInt("line", line);
							WheelPartyClient.send("taunt", a);
						}));
					}
				}));
		}
		addRenderableWidget(KitButton.of(left + RX + 90, top + 210, 86, Component.translatable("gui.burmaldaholic.common.close"), KitButton.Style.SECONDARY,
			b -> onClose()));
		return top + 232;
	}

	// ---- angles ------------------------------------------------------------------------------------------------

	private double idleAngle() {
		if (Kit.reduceMotion()) return idle;
		long now = Util.getMillis();
		idle += (now - idleAt) * 6 / 1000.0;
		idleAt = now;
		return idle;
	}

	/** Pointer degrees from slice 0's start at rest (the server's {@code angle1000}). */
	private double pointerDeg() {
		MatchView.StepView spin = view.step("spin");
		return spin == null ? 0 : MatchView.lng(spin.data(), "angle1000", 0) / 1000.0;
	}

	private double rotation() {
		MatchView.StepView spin = view.step("spin");
		if (spin == null) return idleAngle();
		double rest = -pointerDeg();
		int total = Math.max(1, spin.ticks()) * 50;
		double ms = stepAt == 0 ? total : Util.getMillis() - stepAt;
		if (view.steps().getLast() != spin) ms = total;
		if (Kit.reduceMotion()) return WheelAnim.reduced(spinFrom, rest, ms);
		int hair = MatchView.integer(spin.data(), "hair", -1);
		double behind = sliceEndDeg(winner()) - pointerDeg();
		if (hair >= 0) return WheelAnim.angleHair(spinFrom, rest, total, ms, WheelAnim.hairCrawl(behind));
		return WheelAnim.angle(spinFrom, rest, total, ms, behind);
	}

	private boolean spinning() {
		MatchView.StepView spin = view.step("spin");
		return spin != null && stepAt > 0 && view.steps().getLast() == spin && Util.getMillis() - stepAt < spin.ticks() * 50L;
	}

	private boolean stopped() {
		return view.step("spin") != null && !spinning();
	}

	private int winner() {
		MatchView.StepView spin = view.step("spin");
		return spin == null ? -1 : MatchView.integer(spin.data(), "winner", -1);
	}

	/** Clockwise degrees (from the top) where seat i's slice starts / ends. */
	private double sliceStartDeg(int seat) {
		long[] s = stakes();
		long pot = Math.max(1, PvpMath.pot(s));
		long c = 0;
		for (int i = 0; i < seat && i < s.length; i++) c += s[i];
		return 360.0 * c / pot;
	}

	private double sliceEndDeg(int seat) {
		long[] s = stakes();
		long pot = Math.max(1, PvpMath.pot(s));
		long c = 0;
		for (int i = 0; i <= seat && i < s.length; i++) c += s[i];
		return 360.0 * c / pot;
	}

	private int ownerAt(double theta) {
		long[] s = stakes();
		long pot = PvpMath.pot(s);
		if (pot <= 0) return -1;
		double a = ((-theta % 360) + 360) % 360;
		long u = Math.min(pot - 1, (long) Math.floor(a / 360.0 * pot));
		return WheelMath.winner(s, u);
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	protected void playArea(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int wx = left + WX;
		int wy = top + WY;
		long[] stakes = stakes();
		long pot = PvpMath.pot(stakes);
		double theta = rotation();
		ticks(theta);
		WheelDraw.stand(g, wx, wy);
		int cx = wx + WheelDraw.C;
		int cy = wy + WheelDraw.C;
		Kit.disc(g, cx, cy, WheelDraw.R_FACE, 0xFF2A1238);
		boolean stop = stopped();
		int win = winner();
		// slices, 2° strips in the owners' dye colours
		for (int deg = 0; deg < 360; deg += 2) {
			if (pot <= 0) break;
			long u = Math.min(pot - 1, (long) Math.floor((deg + 1) / 360.0 * pot));
			int owner = WheelMath.winner(stakes, u);
			int r = stop && owner == win ? WheelDraw.R_FACE + 3 : WheelDraw.R_FACE - 1;
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate((float) Math.toRadians(theta + deg + 1));
			int hw = (int) Math.ceil(r * Math.sin(Math.toRadians(1.3))) + 1;
			int color = WheelArt.color(owner);
			if (stop && owner != win) color = Kit.lerp(color, 0xFF303030, 0.6);
			g.fill(-hw, -r, hw, -22, color);
			g.pose().popMatrix();
		}
		// seams and heads on the arcs
		List<PvpSeat> seats = view.seatsFull();
		for (int i = 0; i < stakes.length && pot > 0; i++) {
			double a0 = sliceStartDeg(i);
			double a1 = sliceEndDeg(i);
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate((float) Math.toRadians(theta + a0));
			g.fill(0, -WheelDraw.R_FACE, 1, -22, 0xFF180A28);
			g.pose().popMatrix();
			double arc = a1 - a0;
			int size = arc / 360.0 >= 0.2 ? 12 : arc >= 6 ? 8 : 0;
			if (size == 0) continue;
			double mid = Math.toRadians(theta + (a0 + a1) / 2);
			int hx = (int) Math.round(cx + Math.sin(mid) * 52) - size / 2;
			int hy = (int) Math.round(cy - Math.cos(mid) * 52) - size / 2;
			PvpSeat s = i < seats.size() ? seats.get(i) : null;
			if (s == null) continue;
			g.fill(hx - 2, hy - 2, hx + size + 2, hy + size + 2, 0xFF180A28);
			g.fill(hx - 1, hy - 1, hx + size + 1, hy + size + 1, Kit.GOLD);
			Faces.draw(g, s.key(), s.bot(), s.name(), hx, hy, size);
		}
		if (stop && win >= 0 && !Kit.reduceMotion()) {
			double pulse = 0.5 + 0.5 * Math.sin(Util.getMillis() / 1000.0 * Math.PI * 2);
			double a0 = sliceStartDeg(win);
			double a1 = sliceEndDeg(win);
			for (double a : new double[] {a0, a1}) {
				g.pose().pushMatrix();
				g.pose().translate(cx, cy);
				g.pose().rotate((float) Math.toRadians(theta + a));
				g.fill(0, -WheelDraw.R_FACE - 3, 1, -22, Kit.alpha(0xFFFFFFFF, 0.5 + 0.5 * pulse));
				g.pose().popMatrix();
			}
		}
		Kit.disc(g, cx, cy, 22, 0xFF2A1238);
		WheelDraw.rim(g, wx, wy);
		boolean still = Kit.reduceMotion();
		long now = Util.getMillis();
		WheelDraw.bulbs(g, wx, wy, i -> spinning() && !still ? ((i + (int) Math.floor(theta / 15)) % 3 == 0 ? 1 : 0)
			: stop ? ((int) (now / 150) + i) % 2 == 0 ? 2 : 1 : (i + now / 1500) % 2 == 0 ? 1 : 0);
		WheelDraw.hub(g, wx, wy);
		WheelDraw.flapper(g, wx, wy, spinning() && !still ? WheelAnim.flapper(theta, 10) : 0);
		// the winner takes the hub with the crown
		if (stop && win >= 0 && win < seats.size()) {
			double p = still ? 1 : Math.min(1, (now - stepAt - (view.step("spin").ticks() * 50L)) / 400.0);
			if (stepAt == 0) p = 1;
			if (p > 0) {
				int size = (int) Math.round(12 + 12 * p);
				PvpSeat s = seats.get(win);
				g.fill(cx - size / 2 - 2, cy - size / 2 - 2, cx + size / 2 + 2, cy + size / 2 + 2, 0xFF180A28);
				g.fill(cx - size / 2 - 1, cy - size / 2 - 1, cx + size / 2 + 1, cy + size / 2 + 1, Kit.GOLD);
				Faces.draw(g, s.key(), s.bot(), s.name(), cx - size / 2, cy - size / 2, size);
				if (p >= 1) Kit.sprite(g, Kit.pvp("crown"), cx - 8, cy - size / 2 - 11, 16, 12);
			}
		}
	}

	private void ticks(double theta) {
		if (!spinning()) {
			lastPeg = Long.MIN_VALUE;
			return;
		}
		long peg = WheelAnim.pegCount(theta, 10);
		if (lastPeg == Long.MIN_VALUE) lastPeg = peg;
		if (peg == lastPeg) return;
		long passed = Math.abs(peg - lastPeg);
		lastPeg = peg;
		long now = Util.getMillis();
		if (now - lastTickMs < 50) return;
		lastTickMs = now;
		FxSounds.play("wheel_tick", 0.5f, WheelAnim.tickPitch(passed * 10, 30));
	}

	@Override
	protected void content(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<PvpSeat> seats = view.seatsFull();
		long pot = Math.max(1, view.pot());
		MatchView.StepView result = view.step("result");
		int underdog = result != null && MatchView.bool(result.data(), "underdog", false) ? MatchView.integer(result.data(), "winner", -1) : -1;
		int y = top + 30;
		for (int i = 0; i < seats.size() && i < 8; i++) {
			PvpSeat s = seats.get(i);
			int x = left + RX;
			Kit.sprite(g, Kit.pvp(s.host() ? "lobby_row_host" : "lobby_row"), x, y, 176, 15);
			g.fill(x + 4, y + 4, x + 11, y + 11, WheelArt.color(i));
			Faces.draw(g, s.key(), s.bot(), s.name(), x + 14, y + 2, 11);
			Component share = WheelArt.share(s.stake(), pot);
			Component stake = Texts.number(s.stake());
			int sw = font.width(share);
			int kw = font.width(stake);
			Component nm = s.plateName();
			Kit.fit(g, font, nm, x + 28, y + 4, 176 - 28 - sw - kw - 14, i == winner() && stopped() ? Kit.GOLD : s.you() ? Kit.GOLD : Kit.BONE, false);
			g.text(font, stake, x + 176 - 8 - sw - 6 - kw, y + 4, Kit.BONE_SHADE, false);
			g.text(font, share, x + 176 - 6 - sw, y + 4, Kit.BONE, false);
			if (s.index() == underdog) {
				Component tag = Component.translatable("gui.burmaldaholic.pvp.wheel.underdog_tag");
				Kit.sprite(g, Kit.pvp("mode_banner"), x + 30, y - 3, font.width(tag) + 12, 12);
				g.text(font, tag, x + 36, y - 1, Kit.LILAC, false);
			}
			y += 16;
		}
		// pot / cut, countdown or the landing lines
		int ly = top + 30 + Math.min(8, seats.size()) * 16 + 2;
		Component potLine = Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.chips(view.pot())).append(" · ")
			.append(Component.translatable("gui.burmaldaholic.pvp.lobby.rake",
				dev.nezo.burmaldaholic.games.extras.client.pvp.coin.CoinDuelSetupScreen.percent(view.rakeBasisPoints())));
		Kit.fit(g, font, potLine, left + RX, ly, 176, Kit.GOLD, true);
		Component status = null;
		int color = Kit.BONE_SHADE;
		if (view.lobby() && view.ticksLeft() >= 0) {
			status = Component.translatable("gui.burmaldaholic.pvp.wheel.spins_in", seconds(view.ticksLeft() - (ticks - syncTick)));
		} else if (spinning()) {
			int owner = ownerAt(rotation());
			if (owner >= 0 && owner < seats.size()) {
				PvpSeat s = seats.get(owner);
				// the owner under the pointer, in their colour
				int wx = left + WX + WheelDraw.C;
				Component nm = s.plateName();
				int w = font.width(nm) + 22;
				Kit.sprite(g, Kit.pvp("mode_banner"), wx - w / 2, top + WY + 26, w, 16);
				Faces.draw(g, s.key(), s.bot(), s.name(), wx - w / 2 + 4, top + WY + 28, 12);
				g.text(font, nm, wx - w / 2 + 18, top + WY + 30, WheelArt.color(owner) | 0xFF000000, true);
			}
		} else if (stopped()) {
			int winner = winner();
			status = Component.translatable("msg.burmaldaholic.pvp.wheel.lands", view.name(winner));
			color = Kit.GOLD;
			MatchView.StepView spin = view.step("spin");
			int hair = spin == null ? -1 : MatchView.integer(spin.data(), "hair", -1);
			if (hair >= 0) {
				Kit.fit(g, font, Component.translatable("msg.burmaldaholic.pvp.wheel.by_a_hair", view.name(hair)), left + RX, ly + 22, 176, Kit.BONE_SHADE, true);
			}
		}
		if (status != null) Kit.fit(g, font, status, left + RX, ly + 11, 176, color, true);
	}

	@Override
	protected void overlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		// taunt bubbles point at the sender's legend row
		com.google.gson.JsonArray taunts = MatchView.arr(view.json, "taunts");
		double extra = dev.nezo.burmaldaholic.client.pvp.kit.RevealState.ticksSinceState();
		for (int k = 0; k < taunts.size(); k++) {
			JsonObject t = taunts.get(k).getAsJsonObject();
			int seat = MatchView.integer(t, "seat", -1);
			if (seat < 0 || seat >= 8) continue;
			PvpDraw.bubble(g, font, MatchView.integer(t, "line", 0), left + RX + 40, top + 30 + seat * 16, 170,
				MatchView.lng(t, "age", 0) + extra);
		}
	}

	@Override
	public void tick() {
		super.tick();
		MatchView.StepView spin = view.step("spin");
		if (spin != null && stepAt > 0 && Math.abs(Util.getMillis() - stepAt - spin.ticks() * 50L) < 50) rebuild();
	}
}
