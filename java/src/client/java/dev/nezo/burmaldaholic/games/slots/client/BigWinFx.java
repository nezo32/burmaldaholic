package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.CelebrationRequest;
import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.RollUp;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.anim.WinTierTable;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.client.fx.ScreenParticles;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.client.fx.SlotSprites;
import dev.nezo.burmaldaholic.games.slots.client.panels.CabinetArt;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTiers;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.present.CelebrationPlan;
import dev.nezo.burmaldaholic.games.slots.v2.present.SoundPlan;
import dev.nezo.burmaldaholic.games.slots.v2.present.WinShowPlan;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The slot win sequence (slots.md §4.12–§4.13, SLOTS.md §10.1; JS12): Returned (muted grey line, no fanfare — no loss
 * disguised as a win, F9), Win (panel roll-up with ticks, ≤ 15/s, pitch +1 %), Nice (in-panel banner pop + 20 coins),
 * and Big / Mega / Epic: the overlay starts at the NICE word and UPGRADES as the rolling amount passes 15×, 40× and
 * 100× the bet — punch, ray colour, coin rate, the tier stem; one 30 % flash at Mega, a 4 px shake at Epic — and
 * always ends on the SERVER tier and the exact amount (F5). Max Win: a steel plate slams in first. Skippable: a skip
 * jumps to the final value; after the 800 ms hold any key dismisses (autoplay after 1 500 ms).
 *
 * <p>The slot tier words / thresholds are also handed to the shared {@link CelebrationOverlay} (D4, lead decision
 * L2) as a {@link CelebrationRequest}. Until lane J-L1 draws that overlay (J-M1) this class draws the sequence
 * itself; {@link #DELEGATE_TO_SHARED_OVERLAY} switches to the shared one without touching the maths.
 */
public final class BigWinFx {
	/**
	 * The shared CelebrationOverlay draws the Big / Mega / Epic sequence (J-M1): the slot screen only feeds it the request
	 * and keeps the in-panel parts (Returned, Win, Nice, Max Win plate, panel amount).
	 */
	public static final boolean DELEGATE_TO_SHARED_OVERLAY = true;

	private static final int[] WORD_COLORS = {0xFFFFD640, 0xFFFFD640, 0xFFFF8A1A, 0xFFFF40C0};

	private Beat rollup;
	private WinTier shownWord = WinTier.LOSS;
	private double upgradedAt = -1e9;
	private double megaFlashAt = -1e9;
	private double epicShakeAt = -1e9;
	private final SoundPlan.Gate ticks = new SoundPlan.Gate(CelebrationPlan.TICK_GAP_MS);
	private int tickCount;
	private boolean ended;
	private long dismissAt = -1;
	private boolean dismissed;
	private boolean started;
	private double coinCarry;

	public void reset(SlotStage s) {
		rollup = null;
		shownWord = WinTier.LOSS;
		upgradedAt = -1e9;
		megaFlashAt = -1e9;
		epicShakeAt = -1e9;
		ticks.reset();
		tickCount = 0;
		ended = false;
		dismissAt = -1;
		dismissed = false;
		started = false;
		coinCarry = 0;
		var l = s.beats(SlotTimeline.ROLLUP);
		rollup = l.isEmpty() ? null : l.get(0);
	}

	private WinTier tier() {
		return rollup == null ? WinTier.LOSS : WinTier.values()[Math.max(0, Math.min(WinTier.values().length - 1, rollup.arg(1)))];
	}

	private long chips() {
		return rollup == null ? 0 : Integer.toUnsignedLong(rollup.arg(0));
	}

	private long bet(SlotStage s) {
		return rollup == null ? s.bet() : Math.max(1, rollup.arg(2));
	}

	private double rollMs(SlotStage s) {
		return Math.max(1, rollup.dur() - s.localProfile().scale(CelebrationPlan.HOLD_MS));
	}

	/** Progress of the roll-up (0..1). */
	private double u(SlotStage s) {
		if (rollup == null) return 0;
		return Math.max(0, Math.min(1, (s.t() - rollup.at()) / rollMs(s)));
	}

	/** Amount shown on the win panel now (monotonic, exact at the end). */
	public long panelAmount(SlotStage s) {
		if (rollup == null || s.t() < rollup.at()) return 0;
		return RollUp.valueAt(chips(), u(s));
	}

	/** Server tier of the spin (for the panel colours), LOSS before the roll-up. */
	public WinTier panelTier(SlotStage s) {
		return rollup == null || s.t() < rollup.at() ? WinTier.LOSS : tier();
	}

	public boolean overlayShowing(SlotStage s) {
		return rollup != null && tier().isOverlay() && s.t() >= rollup.at() && !dismissed && !DELEGATE_TO_SHARED_OVERLAY;
	}

	/** Skip: before the end, jump to the final value (the clock skip does it); at the hold, dismiss. */
	public boolean skip(SlotStage s) {
		if (rollup == null || s.t() < rollup.at()) return false;
		if (u(s) >= 1 && overlayShowing(s)) {
			dismissed = true;
			return true;
		}
		return false;
	}

	public void cue(SlotStage s, Beat b) {
		if (b.kind().equals(SlotTimeline.MAX_WIN)) {
			SlotSounds.maxWin();
			return;
		}
		if (!b.kind().equals(SlotTimeline.ROLLUP)) return;
		rollup = b;
		started = true;
		WinTier t = tier();
		CelebrationRequest req = new CelebrationRequest(t, chips(), bet(s), WinTierTable.SLOTS, SlotTiers.WORDS, SlotStems.STEMS, 0,
			s.tape() != null && s.tape().capHit(), s.seed());
		if (DELEGATE_TO_SHARED_OVERLAY && t.isOverlay()) CelebrationOverlay.get().play(req);
		switch (t) {
			case RETURN -> SlotSounds.play("slots.returned", 1f, 1f);
			case NICE -> {
				SlotSounds.winNice();
				s.particles().burst(ScreenParticles.COIN, s.wx() + s.windowW() / 2f, s.wy() + s.windowH() - 10, 20, 0.14f, 0.0005f, 1100, s.now());
			}
			default -> {
				if (t.isOverlay() && !DELEGATE_TO_SHARED_OVERLAY) {
					shownWord = WinTier.NICE;
					upgradedAt = s.t();
					SlotSounds.winNice();
				}
			}
		}
	}

	public void update(SlotStage s, boolean jump) {
		if (rollup == null || !started || s.t() < rollup.at()) return;
		WinTier t = tier();
		double u = u(s);
		boolean shared = DELEGATE_TO_SHARED_OVERLAY && t.isOverlay(); // the shared overlay plays its own ticks and stems
		// ticks: ≤ 15/s, pitch +1 % per tick (cap 1.4); Returned is silent after its one muted tick
		if (t.isWin() && !shared && u < 1 && !jump && ticks.tryFire(s.now())) SlotSounds.play("slots.rollup_tick", RollUp.tickPitch(tickCount++), 0.5f);
		if (t.isOverlay() && !shared) {
			WinTier w = CelebrationPlan.wordAt(u, chips(), bet(s), WinTierTable.SLOTS, t);
			if (w.ordinal() > shownWord.ordinal()) {
				shownWord = w;
				upgradedAt = s.t();
				if (!jump) {
					switch (w) {
						case BIG -> SlotSounds.bigWin();
						case MEGA -> {
							SlotSounds.megaWin();
							megaFlashAt = s.t();
						}
						case EPIC -> {
							SlotSounds.epicWin();
							epicShakeAt = s.t();
							s.particles().burst(ScreenParticles.STAR, s.wx() + s.windowW() / 2f, s.wy(), 12, 0.15f, 0.0002f, 900, s.now());
						}
						default -> {
						}
					}
				}
			}
			// coins at 20 / 40 / 60 per second by word, confetti from Mega
			if (u < 1 && !s.reduceMotion()) {
				coinCarry += CelebrationPlan.COIN_RATE[CelebrationPlan.wordIndex(shownWord)] / 60.0;
				int n = (int) coinCarry;
				coinCarry -= n;
				if (n > 0) s.particles().fountain(ScreenParticles.COIN, s.wx() + s.windowW() / 2f, s.wy() + s.windowH() + 10, s.windowW(), n, s.now());
				if (shownWord.ordinal() >= WinTier.MEGA.ordinal() && s.now() % 5 == 0) {
					s.particles().rain(ScreenParticles.CONFETTI, s.wx() - 80, s.wx() + s.windowW() + 80, s.wy() - 40, 2, s.now());
				}
			}
		}
		if (u >= 1 && !ended) {
			ended = true;
			shownWord = t;
			if (t == WinTier.WIN) SlotSounds.winSmall(1f, 1f);
			if (t.isWin() && !shared) SlotSounds.play("slots.rollup_end", 1f, 1f);
			dismissAt = s.now() + 1500 + (long) s.localProfile().scale(CelebrationPlan.HOLD_MS);
		}
		if (ended && dismissAt > 0 && s.now() >= dismissAt && s.finished()) dismissed = true;
	}

	/** Nice: the in-panel banner pops over the reels' lower edge. */
	private void drawNice(SlotStage s, GuiGraphicsExtractor g) {
		double ms = s.t() - rollup.at();
		if (ms > WinShowPlan.LOOP_LIMIT_MS) return;
		double pop = s.reduceMotion() ? 1 : WinShowPlan.bannerPop(ms);
		Component word = Component.translatable(SlotTiers.WORDS.key(WinTier.NICE));
		int sc = SlotDraw.fitScale(s.font(), word, 2, s.windowW() - 20);
		int w = s.font().width(word) * sc + 20;
		float cx = s.wx() + s.windowW() / 2f;
		float cy = s.wy() + s.windowH() - 4;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale((float) pop, (float) pop);
		if (CabinetArt.ART) SlotSprites.blit(g, SlotSprites.machine(s.machine(), "banner_small"), -w / 2, -8 - 4 * sc, w, 16 + 8 * sc - 8);
		else SlotDraw.plate(g, -w / 2, -8 - 4 * sc, w, 16 + 8 * sc - 8, 0xFF8A3AAA, 0xFF3A1450, 0xFFFFD640);
		SlotDraw.outlined(g, s.font(), word, 0, -4 * sc + 4, sc, 0xFFFFD640, 0xFF180A28);
		g.pose().popMatrix();
	}

	public void draw(SlotStage s, GuiGraphicsExtractor g, int sw, int sh) {
		drawMaxWin(s, g);
		if (rollup == null || s.t() < rollup.at()) return;
		WinTier t = tier();
		if (t == WinTier.NICE) {
			drawNice(s, g);
			return;
		}
		if (!overlayShowing(s)) return;
		double u = u(s);
		double ms = s.t() - rollup.at();
		boolean rm = s.reduceMotion();
		double fadeIn = Math.min(1, ms / 200.0);
		int wi = CelebrationPlan.wordIndex(shownWord);
		double shake = CelebrationPlan.shake(s.t() - epicShakeAt, rm, s.seed());
		g.pose().pushMatrix();
		g.pose().translate((float) shake, (float) (shake * 0.5));
		g.fill(0, 0, sw, sh, SlotDraw.withAlpha(0xFF06040C, 0.55 * fadeIn));
		float cx = s.wx() + s.windowW() / 2f;
		float cy = s.wy() + s.windowH() / 2f - 6;
		if (!rm) {
			double ang = ms / 1000.0 * Math.toRadians(25);
			SlotDraw.rays(g, cx, cy, 170, 16, ang, SlotDraw.withAlpha(CelebrationPlan.RAY_COLORS[wi], 0.55 * fadeIn));
			if (wi >= 2) SlotDraw.rays(g, cx, cy, 130, 10, -ang * 1.4, SlotDraw.withAlpha(0xFFFFFFFF, 0.25 * fadeIn));
		}
		double flash = CelebrationPlan.flash(s.t() - megaFlashAt, s.flashes() && !rm, 0.3);
		if (flash > 0) g.fill(0, 0, sw, sh, SlotDraw.withAlpha(0xFFFFE680, flash));
		Component word = Component.translatable(SlotTiers.WORDS.key(rm ? t : shownWord));
		int sc = SlotDraw.fitScale(s.font(), word, 3, sw - 24);
		double punch = rm ? 1 : CelebrationPlan.punch(s.t() - upgradedAt);
		double enter = rm ? 1 : Ease.OUT_BACK.apply(Math.min(1, ms / 300.0));
		SlotDraw.outlined(g, s.font(), word, cx, cy - 10, (float) (sc * punch * enter), WORD_COLORS[wi], 0xFF180A28);
		long shown = RollUp.valueAt(chips(), u);
		SlotDraw.outlined(g, s.font(), Texts.chips(shown), cx, cy + 12 + 2 * sc, 2f, 0xFFFFFFFF, 0xFF180A28);
		if (u >= 1 && s.t() - (rollup.at() + rollMs(s)) > s.localProfile().scale(CelebrationPlan.HOLD_MS)) {
			SlotDraw.outlined(g, s.font(), Component.translatable("gui.burmaldaholic.slots.skip"), cx, cy + 34 + 2 * sc, 1f, 0xA0F4ECF8, 0x80180A28);
		}
		g.pose().popMatrix();
	}

	/** Max Win: the steel plate slams from 2× to 1× in 200 ms with a 4 px shake, then stays on the win panel. */
	private static void drawMaxWin(SlotStage s, GuiGraphicsExtractor g) {
		var l = s.beats(SlotTimeline.MAX_WIN);
		if (l.isEmpty()) return;
		Beat b = l.get(0);
		double ms = s.t() - b.at();
		if (ms < 0) return;
		int[] c = ms < 1500 ? new int[] {s.wx() + s.windowW() / 2, s.wy() + s.windowH() / 2} : s.host().winPanelCenter();
		double slam = s.reduceMotion() ? 1 : CelebrationPlan.plateSlam(ms);
		double shake = CelebrationPlan.shake(ms - 200, s.reduceMotion(), s.seed());
		Component text = Component.translatable(SlotTiers.WORDS.maxWin());
		int w = s.font().width(text) + 16;
		g.pose().pushMatrix();
		g.pose().translate((float) (c[0] + shake), c[1] + (ms < 1500 ? 0 : 22));
		float sc = (float) (slam * (ms < 1500 ? 1.5 : 0.75));
		g.pose().scale(sc, sc);
		if (CabinetArt.ART) SlotSprites.blit(g, SlotSprites.MAXWIN_PLATE, -Math.max(w, 64) / 2 - 4, -12, Math.max(w, 64) + 8, 24);
		else SlotDraw.plate(g, -w / 2, -9, w, 18, 0xFFD8D8E0, 0xFF6A6A78, 0xFF3A3A48);
		g.centeredText(s.font(), text, 0, -4, 0xFF180A28);
		g.pose().popMatrix();
	}

	/** Slot fanfare stems for the shared request (catalog ids). */
	public static final class SlotStems {
		public static final CelebrationRequest.TierStems STEMS = new CelebrationRequest.TierStems("slots.win_small", "slots.win_nice", "slots.big_win",
			"slots.mega_win", "slots.epic_win", "jackpot", "slots.returned", "slots.rollup_tick");

		private SlotStems() {}
	}
}
