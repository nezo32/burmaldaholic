package dev.nezo.burmaldaholic.games.slots.client.features;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.SlotSounds;
import dev.nezo.burmaldaholic.games.slots.client.SlotStage;
import dev.nezo.burmaldaholic.games.slots.client.fx.ScreenParticles;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.client.panels.CabinetArt;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.present.FeatureMotion;
import dev.nezo.burmaldaholic.games.slots.v2.present.HuntBoard;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotBeats;
import dev.nezo.burmaldaholic.games.slots.v2.present.SymbolStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Treasure Hunt (slots.md §4.8; JS8): the reels become 15 chests that drop in; hovering wobbles a chest, pressing it
 * squashes it and it RATTLES until the server confirms the reveal (D6: the i-th pick shows entry i, sent only on the
 * i-th pick, F7); then the lid opens with light rays and the prize pops (coin pile by size + "Chest: 50"), a gem flies
 * to its jackpot meter, or a Creeper swells, flickers and puffs (no explosion). At the end the remaining chests open
 * dimmed with the remaining real entries. The timeline pauses at the end of the hunt intro while the server waits for
 * the picks: every viewer holds there and reveals the entries the server publishes (the spinning player also picks);
 * a complete tape without a live server (preview spectator, replay) reveals its entries on the timeline.
 */
public final class HuntView {
	private static final ItemStack CHEST = new ItemStack(Items.CHEST);
	private static final ItemStack CREEPER = new ItemStack(Items.CREEPER_HEAD);
	private static final ItemStack NUGGET = new ItemStack(Items.GOLD_NUGGET);
	private static final ItemStack INGOT = new ItemStack(Items.GOLD_INGOT);
	private static final ItemStack BLOCK = new ItemStack(Items.GOLD_BLOCK);

	private HuntBoard board = new HuntBoard();
	private Beat intro;
	private Beat end;
	private boolean interactive;
	/** The server is still waiting for picks (the tape's entries arrive one by one). */
	private boolean live;
	private boolean held;
	private boolean restShown;
	private double endedAt = -1;
	private final double[] pt = new double[2];
	private final double[] revealT = new double[HuntBoard.CHESTS];
	private final boolean[] puffed = new boolean[HuntBoard.CHESTS];

	public void reset(SlotStage s) {
		board = new HuntBoard();
		intro = null;
		end = null;
		interactive = false;
		held = false;
		restShown = false;
		endedAt = -1;
		java.util.Arrays.fill(puffed, false);
		for (Beat b : s.beats(SlotTimeline.BONUS_INTRO)) if (b.arg(0) == SlotBeats.FEATURE_HUNT) intro = b;
		for (Beat b : s.beats(SlotTimeline.HUNT_END)) end = b;
		SpinTape.Hunt h = s.tape() == null ? null : s.tape().hunt();
		live = h != null && s.tape().totalFifths() < 0;
		if (live) {
			// late join / reopened screen: the entries opened so far
			int[] e = h.entries();
			for (int i = 0; i < Math.min(h.opened(), e.length); i++) {
				int chest = board.reveal(e[i], s.now());
				if (chest >= 0) revealT[chest] = s.now();
			}
		}
	}

	/** Hold the clock at the end of the intro: for the picking player, and for everyone while the server waits. */
	public void registerHold(SlotStage s, boolean interactive) {
		if (intro == null || s.tape() == null || s.tape().hunt() == null) return;
		this.interactive = interactive;
		if (interactive || live) {
			held = true;
			s.clock().holdAt(intro.end());
		}
	}

	public HuntBoard board() {
		return board;
	}

	private double coverStart() {
		return intro == null ? Double.POSITIVE_INFINITY : intro.at();
	}

	private double coverEnd(SlotStage s) {
		if (intro == null) return Double.NEGATIVE_INFINITY;
		if (end != null) return end.end();
		double last = intro.end();
		for (Beat b : s.beats(SlotTimeline.HUNT_OPEN)) last = Math.max(last, b.end());
		SpinTape.Hunt h = s.tape() == null ? null : s.tape().hunt();
		int remaining = h == null ? 0 : h.entries().length - h.opened();
		return last + 80.0 * remaining + 900;
	}

	public boolean covers(SlotStage s) {
		double t = s.t();
		return intro != null && t >= coverStart() && t < coverEnd(s);
	}

	public double coverAlpha(SlotStage s) {
		if (!covers(s)) return 0;
		double t = s.t();
		double a = Math.min(1, (t - coverStart()) / 300.0);
		return Math.min(a, Math.max(0, (coverEnd(s) - t) / 300.0));
	}

	/** The board waits for a pick (the stage clock holds). */
	public boolean awaitingPick(SlotStage s) {
		return interactive && covers(s) && s.clock().holding() && !board.ended() && board.pending() < 0;
	}

	public void cue(SlotStage s, Beat b) {
		if (b.kind().equals(SlotTimeline.BONUS_INTRO) && b.arg(0) == SlotBeats.FEATURE_HUNT) {
			for (int i = 0; i < 3; i++) SlotSounds.bonusLand(s.machine(), 0.3f + 0.1f * i);
		} else if (b.kind().equals(SlotTimeline.HUNT_OPEN) && !interactive) {
			revealSound(s, b.arg(0));
			int chest = board.reveal(b.arg(0), s.now());
			if (chest >= 0) revealT[chest] = s.now();
		}
	}

	public void update(SlotStage s) {
		if (intro == null) return;
		SpinTape.Hunt tapeHunt = s.tape() == null ? null : s.tape().hunt();
		if (!held && tapeHunt != null && s.t() >= intro.end() && !board.ended()) {
			// complete tape, nobody waits: reveal the opened entries on the timeline, then the rest
			double span = end == null ? 1500 : Math.max(300, end.dur() - 700);
			int n = Math.max(1, tapeHunt.opened());
			int due = (int) Math.min(n, Math.floor((s.t() - intro.end()) / Math.min(300.0, span / n)) + 1);
			int[] e = tapeHunt.entries();
			while (board.opened() < due && board.opened() < e.length && !board.ended()) {
				int v = e[board.opened()];
				revealSound(s, v);
				int chest = board.reveal(v, s.now());
				if (chest >= 0) revealT[chest] = s.now();
			}
			if (board.opened() >= n && !board.ended()) {
				board.revealRest(java.util.Arrays.copyOfRange(e, Math.min(board.opened(), e.length), e.length), s.now());
			}
		}
		// end of the hunt: dim the rest once the last prize has been shown, then let the timeline continue
		if (board.ended() && endedAt < 0) endedAt = s.now();
		if (board.ended() && !restShown && s.now() - endedAt > 700) {
			restShown = true;
			SpinTape.Hunt h = s.tape() == null ? null : s.tape().hunt();
			if (h != null && !held && !interactive) {
				int[] rest = java.util.Arrays.copyOfRange(h.entries(), Math.min(board.opened(), h.entries().length), h.entries().length);
				board.revealRest(rest, s.now());
			}
			if (held) {
				double last = intro.end();
				for (Beat b : s.beats(SlotTimeline.HUNT_OPEN)) last = Math.max(last, b.end());
				s.clock().release(last);
			}
		}
	}

	/** The server's answer to the pending pick (the next entry of the tape). */
	public void reveal(SlotStage s, int value) {
		revealSound(s, value);
		int chest = board.reveal(value, s.now());
		if (chest >= 0) revealT[chest] = s.now();
	}

	public void revealRest(SlotStage s, int[] remaining) {
		board.revealRest(remaining, s.now());
	}

	private static void revealSound(SlotStage s, int value) {
		SlotSounds.play("slots.chest_open", 1f, 1f);
		if (value == 0) {
			SlotSounds.play("slots.creeper_hiss", 1f, 1f);
		} else if (value < 0) {
			SlotSounds.winNice();
			SlotSounds.jackpot(1.3f, 0.5f);
		} else {
			int size = HuntBoard.pileSize(value);
			for (int i = 0; i <= size; i++) SlotSounds.coinLand(1f + 0.08f * i);
		}
	}

	private int chestX(SlotStage s, int chest) {
		return s.cellX(chest % 5);
	}

	private int chestY(SlotStage s, int chest) {
		return s.cellY(chest / 5);
	}

	public boolean click(SlotStage s, double mx, double my) {
		if (!awaitingPick(s)) return false;
		for (int c = 0; c < HuntBoard.CHESTS; c++) {
			int x = chestX(s, c);
			int y = chestY(s, c);
			if (mx >= x && mx < x + s.cell() && my >= y && my < y + s.cell()) return press(s, c);
		}
		return false;
	}

	public boolean pickNext(SlotStage s) {
		if (!awaitingPick(s)) return false;
		return press(s, board.nextClosed());
	}

	private boolean press(SlotStage s, int chest) {
		if (!board.press(chest, s.now())) return false;
		SlotSounds.click();
		s.host().pickChest(chest);
		return true;
	}

	public void draw(SlotStage s, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		double a = coverAlpha(s);
		if (a <= 0) return;
		double t = s.t();
		int cell = s.cell();
		long now = s.now();
		SymbolStyle.Theme theme = SymbolStyle.theme(s.machine());
		g.fill(s.wx(), s.wy(), s.wx() + s.windowW(), s.wy() + s.windowH(), SlotDraw.withAlpha(0xFF2A1A0C, a));
		g.fillGradient(s.wx(), s.wy(), s.wx() + s.windowW(), s.wy() + s.windowH(), SlotDraw.withAlpha(theme.bonusAccent(), a * 0.35),
			SlotDraw.withAlpha(0xFF100804, a * 0.6));
		if (a < 0.95) return;
		double introMs = t - intro.at();
		for (int c = 0; c < HuntBoard.CHESTS; c++) {
			int x = chestX(s, c);
			int y = chestY(s, c) - (int) Math.round(s.reduceMotion() ? 0 : HuntBoard.dropOffset(c, introMs, intro.dur()));
			HuntBoard.State st = board.state(c);
			boolean hover = st == HuntBoard.State.CLOSED && awaitingPick(s) && mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell;
			float cx = x + cell / 2f;
			float cy = y + cell / 2f;
			double since = now - board.stamp(c);
			switch (st) {
				case CLOSED, PENDING -> {
					g.pose().pushMatrix();
					g.pose().translate(cx, y + cell - 4);
					if (hover && !s.reduceMotion()) g.pose().rotate((float) Math.toRadians(HuntBoard.wobble(now)));
					if (st == HuntBoard.State.PENDING) {
						g.pose().translate(s.reduceMotion() ? 0 : HuntBoard.rattle(since), 0);
						if (since < 60) g.pose().scale(1, 0.92f);
					}
					if (CabinetArt.ART) {
						int size = cell < 40 ? 29 : 40;
						int frame = st == HuntBoard.State.PENDING ? 1 : 0; // hover wobbles only: frame 1 reads as "opening"
						SlotSprites.frame(g, SlotSprites.CHEST, 40, 40, 6, frame, -size / 2, -size + 2, size, size, 0xFFFFFFFF);
					} else {
						float sc = (cell - 12) / 16f;
						g.pose().scale(sc, sc);
						g.item(CHEST, -8, -16);
					}
					g.pose().popMatrix();
					if (hover) SlotDraw.frame(g, x + 2, y + 2, cell - 4, cell - 4, 1, 0xCCFFD640);
				}
				case OPEN, DIMMED -> drawOpened(s, g, c, x, y, since, st == HuntBoard.State.DIMMED);
			}
		}
		Component hint = board.ended() ? Component.translatable("gui.burmaldaholic.slots.bonus.total", Texts.chips(board.totalTimesBet() * s.bet()))
			: Component.translatable("gui.burmaldaholic.slots.pick.hint");
		g.fill(s.wx(), s.wy() + s.windowH() - 11, s.wx() + s.windowW(), s.wy() + s.windowH(), 0xB0100804);
		SlotDraw.centeredFit(g, s.font(), hint, s.wx() + s.windowW() / 2, s.wy() + s.windowH() - 10, s.windowW() - 4, board.ended() ? 0xFFFFD640 : 0xFFF4ECD8);
	}

	private void drawOpened(SlotStage s, GuiGraphicsExtractor g, int c, int x, int y, double since, boolean dimmed) {
		int cell = s.cell();
		int value = board.entry(c);
		float cx = x + cell / 2f;
		float cy = y + cell / 2f;
		if (since < 0) {
			// dimmed reveal not reached yet (80 ms stagger): still the closed chest, in the same grid slot
			int size = cell < 40 ? 29 : 40;
			if (CabinetArt.ART) {
				SlotSprites.frame(g, SlotSprites.CHEST, 40, 40, 6, 0, (int) cx - size / 2, y + cell - 4 - size + 2, size, size, 0xFFFFFFFF);
			} else {
				g.pose().pushMatrix();
				g.pose().translate(cx, y + cell - 4);
				float sc = (cell - 12) / 16f;
				g.pose().scale(sc, sc);
				g.item(CHEST, -8, -16);
				g.pose().popMatrix();
			}
			return;
		}
		// open chest: the lid flipbook, light spills out
		int frame = HuntBoard.openFrame(since);
		int size = cell < 40 ? 29 : 40;
		if (CabinetArt.ART) {
			if (dimmed) SlotSprites.blit(g, SlotSprites.CHEST_DIM, (int) cx - size / 2, (int) cy - size / 2, size, size);
			else SlotSprites.frame(g, SlotSprites.CHEST, 40, 40, 6, Math.min(5, frame), (int) cx - size / 2, (int) cy - size / 2, size, size, 0xFFFFFFFF);
		} else {
			g.fill(x + 6, y + cell - 14, x + cell - 6, y + cell - 4, 0xFF6B4423);
			g.fill(x + 6, y + cell - 14, x + cell - 6, y + cell - 12, 0xFF8B5A2B);
		}
		if (!dimmed && frame >= 3 && since < 1200 && !s.reduceMotion()) {
			for (int i = 0; i < 4; i++) {
				double ang = -Math.PI / 2 + (i - 1.5) * 0.35;
				double len = 12 + 6 * Math.sin(since / 90.0 + i);
				SlotDraw.line(g, cx, y + cell - 13, cx + Math.cos(ang) * len, y + cell - 13 + Math.sin(ang) * len, 1, 0xB0FFE680);
			}
		}
		double pop = s.reduceMotion() ? 1 : HuntBoard.prizePop(since);
		if (pop > 0) {
			g.pose().pushMatrix();
			g.pose().translate(cx, cy - 4);
			g.pose().scale((float) pop, (float) pop);
			if (value > 0 && CabinetArt.ART) {
				int ps = cell < 40 ? 24 : 32;
				SlotSprites.blit(g, SlotSprites.coinPile(HuntBoard.pileSize(value)), -ps / 2, -ps / 2, ps, ps);
			} else if (value > 0) {
				int pileSize = HuntBoard.pileSize(value);
				ItemStack pile = pileSize == 2 ? BLOCK : pileSize == 1 ? INGOT : NUGGET;
				float sc = 1.0f + 0.25f * pileSize;
				g.pose().scale(sc, sc);
				g.item(pile, -8, -8);
			} else if (value < 0) {
				int tier = -value;
				if (!dimmed && since < HuntBoard.OPEN_MS + 800) {
					SlotDraw.gem(g, 0, 0, 7, SymbolStyle.JACKPOT_COLORS[tier - 1]);
				} else if (dimmed) {
					SlotDraw.gem(g, 0, 0, 6, SymbolStyle.JACKPOT_COLORS[tier - 1]);
				}
			} else {
				double swell = dimmed || s.reduceMotion() ? 1 : HuntBoard.creeperSwell(since);
				g.pose().scale((float) swell, (float) swell);
				if (CabinetArt.ART) {
					int cs = cell < 40 ? 24 : 32;
					int cf = dimmed ? 3 : Math.min(3, (int) Math.max(0, (since - HuntBoard.OPEN_MS) / 150));
					SlotSprites.frame(g, SlotSprites.CREEPER, 40, 40, 4, cf, -cs / 2, -cs / 2, cs, cs, 0xFFFFFFFF);
				} else {
					g.item(CREEPER, -8, -8);
				}
			}
			g.pose().popMatrix();
		}
		if (!dimmed && value == 0) drawCreeperFx(s, g, c, x, y, since);
		if (!dimmed && value < 0) drawGemFlight(s, g, c, cx, cy, since, -value);
		if (!dimmed && value > 0 && since > HuntBoard.OPEN_MS && since < HuntBoard.OPEN_MS + 1200) {
			double ms = since - HuntBoard.OPEN_MS;
			Component prize = Component.translatable("gui.burmaldaholic.slots.pick.prize", Texts.chips(value * s.bet()));
			SlotDraw.outlined(g, s.font(), prize, cx, (float) (y + 6 - FeatureMotion.floatRise(ms) * 0.75), 1f, SlotDraw.withAlpha(0xFFFFD640, FeatureMotion.floatAlpha(ms * 0.6)),
				SlotDraw.withAlpha(0xFF180A28, FeatureMotion.floatAlpha(ms * 0.6)));
		}
		if (dimmed) {
			g.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, 0x80000000);
			if (value > 0) SlotDraw.centeredFit(g, s.font(), Texts.chips(value * s.bet()), (int) cx, y + cell - 11, cell - 2, 0xA0FFFFFF);
		}
	}

	private void drawCreeperFx(SlotStage s, GuiGraphicsExtractor g, int c, int x, int y, double since) {
		double ms = since - HuntBoard.OPEN_MS;
		if (ms < 0) return;
		int cell = s.cell();
		if (ms < 600) {
			// white overlay flickering at 3 Hz (≤ 30 %); with flashes off a static tint that grows
			double a = s.flashes() ? (Math.sin(ms / 1000.0 * Math.PI * 6) > 0 ? 0.3 : 0.05) : 0.3 * ms / 600.0;
			if (!s.reduceMotion()) g.fill(x + 4, y + 4, x + cell - 4, y + cell - 4, SlotDraw.withAlpha(0xFFFFFFFF, a));
		} else if (!puffed[c]) {
			puffed[c] = true;
			s.particles().burst(ScreenParticles.SMOKE, x + cell / 2f, y + cell / 2f, 16, 0.06f, -0.00002f, 800, s.now());
			SlotSounds.vanilla(SoundEvents.GENERIC_EXTINGUISH_FIRE, 1.4f, 0.4f);
		}
		if (ms > 600 && ms < 2600) {
			Component text = Component.translatable("gui.burmaldaholic.slots.pick.creeper");
			SlotDraw.outlined(g, s.font(), text, s.wx() + s.windowW() / 2f, s.wy() + 10, 1f, 0xFF80FF40, 0xFF102008);
		}
	}

	private void drawGemFlight(SlotStage s, GuiGraphicsExtractor g, int c, float cx, float cy, double since, int tier) {
		double ms = since - HuntBoard.OPEN_MS - 300;
		if (ms < 0 || ms > 500 || s.reduceMotion()) return;
		int[] m = s.host().meterCenter(tier);
		FeatureMotion.arc(cx, cy, m[0], m[1], 40, Ease.IN_OUT_SINE.apply(ms / 500.0), pt);
		SlotDraw.gem(g, (int) pt[0], (int) pt[1], 8, SymbolStyle.JACKPOT_COLORS[tier - 1]);
	}
}
