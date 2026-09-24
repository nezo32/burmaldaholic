package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.core.anim.CelebrationPlan;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * The shared celebration kit (global.md §2.6): banner / overlay, roll-up with tier-upgrade beats, chip burst,
 * coin shower, rays, vignette, skip. One instance per client, drawn by {@link ClientFx} on the HUD (no screen
 * open) or on top of the open screen (screens implementing {@link Host} draw it themselves, e.g. the J-L2
 * {@code CasinoScreen} at its own stratum). All timing is {@link CelebrationPlan} (pure, unit-tested); this class
 * only draws, plays sounds and owns the GUI particles.
 *
 * <p>Games never draw their own big-win sequence: they call {@link #play} with their {@link CelebrationRequest}
 * (slots pass {@code SlotTiers.WORDS} + {@code WinTierTable.SLOTS}), or the server sends the {@code fx} payload
 * ({@code ServerFx.celebrate}) and {@link ClientFx} builds the request from {@link CelebrationStyles}.
 * Steady state is allocation-free apart from re-building the amount text when the shown value changes.
 */
public final class CelebrationOverlay {
	/** A screen that draws the overlay itself (its own stratum); the automatic screen hook then skips it. */
	public interface Host {}

	private static final CelebrationOverlay INSTANCE = new CelebrationOverlay();
	private static final int QUEUE_MAX = 4;
	private static final int[] CHIP_DENOMS = {1, 5, 25, 100, 500};
	private static final int[] CHIP_COLORS = {0xFFF4ECF8, 0xFFD83440, 0xFF3CB44B, 0xFF222222, 0xFF783CBE};
	private static final int SPRITE_COIN = 5;
	private static final int SPRITE_SPARKLE = 6;
	private static final int SPRITE_CONFETTI = 7;
	private static final int[] CONFETTI_COLORS = {0xFFFFD640, 0xFF80FF40, 0xFFD83440, 0xFFBE5AFF, 0xFF5CE8E0, 0xFFF4ECF8};

	private final GuiParticlePool particles = new GuiParticlePool();
	private final Deque<CelebrationRequest> queue = new ArrayDeque<>();
	private CelebrationRequest active;
	private CelebrationPlan plan;
	private long startMs;
	private long lastFrameMs;
	private SeedMix.FxRng rng = new SeedMix.FxRng(1);

	// per-celebration caches
	private WinTier shownWord;
	private Component wordText;
	private FormattedCharSequence wordSeq;
	private int wordWidth;
	private boolean wordHasAmount;
	private long shownAmount = Long.MIN_VALUE;
	private FormattedCharSequence amountSeq;
	private int amountWidth;
	private int lastTick;
	private int upgradesPlayed;
	private int coinsSpawned;
	private boolean burstDone;

	public static CelebrationOverlay get() {
		return INSTANCE;
	}

	/** Starts a celebration (replaces a running one: the old one jumps to its end, overrun rule). */
	public void play(CelebrationRequest request) {
		queue.clear();
		start(request);
	}

	/** Plays after the running celebration (several jackpots in tape order, slots.md §4.11). */
	public void enqueue(CelebrationRequest request) {
		if (active == null) start(request);
		else if (queue.size() < QUEUE_MAX) queue.add(request);
	}

	private void start(CelebrationRequest request) {
		this.active = request;
		this.plan = CelebrationPlan.of(request.tier(), request.ret(), request.stake(), request.table(), FxSettings.localProfile(),
			FxSettings.celebrations() == FxSettings.Celebrations.OFF);
		this.startMs = AnimClock.localMs();
		this.lastFrameMs = startMs;
		this.rng = new SeedMix.FxRng(request.seed());
		this.shownWord = null;
		this.shownAmount = Long.MIN_VALUE;
		this.lastTick = -1;
		this.upgradesPlayed = 0;
		this.coinsSpawned = 0;
		this.burstDone = false;
		particles.clear();
		String stem = request.stems().of(plan.style());
		if (stem != null) FxSounds.play(stem, 1f);
		if (plan.style() == WinTier.EPIC && request.stems() == CelebrationRequest.TierStems.CORE) FxSounds.play("win_big", 1f);
	}

	/** Click / Space / Enter / Esc: jump to the final frame (hold 300 ms, then exit). */
	public void skip() {
		if (plan != null) plan = plan.withSkipAt((int) (AnimClock.localMs() - startMs));
	}

	/** A running celebration longer than 1 s that has not been skipped yet (global.md §1.2). */
	public boolean skippable() {
		return plan != null && !plan.skipped() && plan.endMs() > 1000 && !plan.done(AnimClock.localMs() - startMs);
	}

	public boolean isActive() {
		return active != null;
	}

	public CelebrationPlan plan() {
		return plan;
	}

	/** Amount shown at {@code nowMs} (exact server amount at the end or after a skip). */
	public long shownAmount(long nowMs) {
		return plan == null ? 0 : plan.amountAt(nowMs - startMs);
	}

	/** Tier word currently shown (upgrades as the rolling amount passes the caller's thresholds). */
	public WinTier shownTier(long nowMs) {
		return plan == null ? WinTier.LOSS : plan.wordAt(nowMs - startMs);
	}

	public void clear() {
		active = null;
		plan = null;
		queue.clear();
		particles.clear();
	}

	// ---- drawing ------------------------------------------------------------------------------

	/** Draws the current frame without blur (safe on any screen). */
	public void extract(GuiGraphicsExtractor g) {
		extract(g, false);
	}

	/**
	 * Draws the current frame (call once per frame; also advances sounds and particles). {@code allowBlur}: MEGA+
	 * may blur what is behind (vanilla allows ONE blur per frame, so pass true only where nothing else blurs:
	 * the HUD pass with no screen open, or a screen that does not blur its own background).
	 */
	public void extract(GuiGraphicsExtractor g, boolean allowBlur) {
		if (active == null) return;
		long now = AnimClock.localMs();
		double t = now - startMs;
		if (plan.done(t)) {
			CelebrationRequest next = queue.poll();
			active = null;
			plan = null;
			particles.clear();
			if (next != null) start(next);
			return;
		}
		float dt = Math.max(0, Math.min(100, now - lastFrameMs));
		lastFrameMs = now;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cx = w / 2;
		float alpha = plan.alpha(t);
		boolean overlay = plan.overlay();
		boolean motion = !plan.reduced();

		sounds(t);

		if (overlay) {
			float dim = plan.backdrop(t);
			if (motion && plan.style().ordinal() >= WinTier.MEGA.ordinal()) {
				g.nextStratum();
				if (allowBlur) {
					try {
						g.blurBeforeThisStratum();
					} catch (IllegalStateException alreadyBlurred) {
						// another element blurred this frame: the dim backdrop alone carries the beat
					}
				}
			}
			if (dim > 0) g.fill(0, 0, w, h, CasinoPalette.withAlpha(CasinoPalette.BG_DARKEST, dim));
			if (motion && plan.style().ordinal() >= WinTier.MEGA.ordinal()) rays(g, cx, h / 3, Math.min(w, h), t, alpha);
			if (plan.style().ordinal() >= WinTier.EPIC.ordinal()) vignette(g, w, h, t, alpha);
		}

		// particles (never under reduce motion)
		if (motion) {
			spawn(t, cx, overlay ? h / 3 : h / 4, w);
			particles.step(now, dt);
			drawParticles(g, now, alpha);
		}

		if (overlay) drawOverlayText(g, font, t, cx, h, w, alpha);
		else drawBanner(g, font, t, cx, h, w, alpha);
	}

	private void sounds(double t) {
		CelebrationRequest r = active;
		int n = plan.upgradeCount();
		while (upgradesPlayed < n && plan.upgradeTime(upgradesPlayed) <= t && !plan.skipped()) {
			if (r.stems().big() != null) FxSounds.play(r.stems().big(), 1f);
			upgradesPlayed++;
		}
		int tick = plan.tickIndex(t);
		if (tick > lastTick) {
			if (r.stems().tick() != null) FxSounds.play(r.stems().tick(), 1f, plan.tickPitch(tick));
			lastTick = tick;
		}
	}

	private void rays(GuiGraphicsExtractor g, int cx, int cy, int size, double t, float alpha) {
		float deg = plan.raysDegrees(t);
		int s = Math.max(64, (int) (size * 0.9f));
		int color = CasinoPalette.withAlpha(CasinoPalette.GOLD, 0.35f * alpha);
		layer(g, cx, cy, s, deg, color);
		if (plan.style().ordinal() >= WinTier.EPIC.ordinal()) layer(g, cx, cy, (int) (s * 0.8f), -deg * 1.3f + 15, CasinoPalette.withAlpha(CasinoPalette.LILAC, 0.25f * alpha));
	}

	private void layer(GuiGraphicsExtractor g, int cx, int cy, int s, float deg, int color) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Math.toRadians(deg));
		if (!FxSprites.blit(g, FxSprites.RAYS, -s / 2, -s / 2, s, s, color)) {
			// fallback: 12 thin tapered beams
			for (int i = 0; i < 12; i++) {
				g.pose().pushMatrix();
				g.pose().rotate((float) (i * Math.PI / 6));
				int len = s / 2;
				g.fill(8, -2, len / 2, 2, color);
				g.fill(len / 2, -1, len, 1, CasinoPalette.withAlpha(color, ((color >>> 24) / 255f) * 0.6f));
				g.pose().popMatrix();
			}
		}
		g.pose().popMatrix();
	}

	private void vignette(GuiGraphicsExtractor g, int w, int h, double t, float alpha) {
		float a;
		if (FxSettings.flashes()) a = (float) (0.15 + 0.15 * (0.5 + 0.5 * Math.sin(t / 1000.0 * 2 * Math.PI))); // ≤ 30 %, 1 Hz
		else a = 0.15f; // static tint
		int edge = CasinoPalette.withAlpha(CasinoPalette.GOLD, a * alpha);
		int clear = CasinoPalette.withAlpha(CasinoPalette.GOLD, 0);
		int band = Math.max(12, h / 8);
		g.fillGradient(0, 0, w, band, edge, clear);
		g.fillGradient(0, h - band, w, h, clear, edge);
	}

	private void spawn(double t, int cx, int cy, int w) {
		WinTier style = plan.style();
		if (!burstDone) {
			burstDone = true;
			int chips = switch (style) {
				case BIG -> 16;
				case MEGA -> 24;
				case EPIC, JACKPOT -> 32;
				case NICE -> 8;
				default -> 0;
			};
			long now = startMs;
			for (int i = 0; i < chips; i++) {
				double ang = -Math.PI * (0.1 + 0.8 * rng.nextDouble());
				double sp = 0.15 + 0.25 * rng.nextDouble();
				particles.spawn(rng.nextInt(CHIP_DENOMS.length), cx + (float) (rng.nextDouble() * 40 - 20), cy, (float) (Math.cos(ang) * sp),
					(float) (Math.sin(ang) * sp), 0.00035f, now, 900);
			}
			if (style.ordinal() >= WinTier.MEGA.ordinal()) {
				for (int i = 0; i < 12; i++) {
					particles.spawn(SPRITE_SPARKLE, cx + (float) (rng.nextDouble() * 120 - 60), cy + (float) (rng.nextDouble() * 40 - 20), 0, -0.01f,
						0, now + rng.nextInt(400), 600);
				}
			}
			if (style.ordinal() >= WinTier.EPIC.ordinal()) {
				for (int i = 0; i < 16; i++) {
					double ang = -Math.PI * rng.nextDouble();
					particles.spawn(SPRITE_CONFETTI + rng.nextInt(CONFETTI_COLORS.length) * 16, cx, cy, (float) (Math.cos(ang) * 0.25),
						(float) (Math.sin(ang) * 0.3), 0.0002f, now, 1600);
				}
			}
		}
		if (style == WinTier.JACKPOT) {
			int due = (int) Math.min(40, Math.floor(40 * Math.min(1, t / 1500.0)));
			for (; coinsSpawned < due; coinsSpawned++) {
				particles.spawn(SPRITE_COIN, (float) (rng.nextDouble() * w), -12, 0, (float) (0.06 + 0.06 * rng.nextDouble()), 0.00015f,
					startMs + (long) t, 2200);
			}
		}
	}

	private void drawParticles(GuiGraphicsExtractor g, long now, float alpha) {
		for (int i = 0; i < particles.count(); i++) {
			float age = particles.age(i, now);
			if (age < 0) continue;
			float a = alpha * (age > 0.7f ? (1 - age) / 0.3f : 1);
			int x = (int) particles.x(i);
			int y = (int) particles.y(i);
			int sprite = particles.sprite(i);
			if (sprite < CHIP_DENOMS.length) {
				if (!FxSprites.blit(g, FxSprites.chip(CHIP_DENOMS[sprite]), x - 4, y - 4, 8, 8, CasinoPalette.withAlpha(0xFFFFFFFF, a))) {
					g.fill(x - 3, y - 3, x + 3, y + 3, CasinoPalette.withAlpha(CHIP_COLORS[sprite], a));
					g.fill(x - 1, y - 1, x + 1, y + 1, CasinoPalette.withAlpha(CasinoPalette.BONE, a));
				}
			} else if (sprite == SPRITE_COIN) {
				if (!FxSprites.blit(g, FxSprites.COIN_SPIN, x - 8, y - 8, 16, 16, CasinoPalette.withAlpha(0xFFFFFFFF, a))) {
					int half = Math.max(1, (int) Math.abs(Math.cos((now + i * 97) / 120.0) * 5));
					g.fill(x - half, y - 5, x + half, y + 5, CasinoPalette.withAlpha(CasinoPalette.GOLD, a));
					g.fill(x - half, y + 3, x + half, y + 5, CasinoPalette.withAlpha(CasinoPalette.GOLD_SHADE, a));
				}
			} else if (sprite == SPRITE_SPARKLE) {
				float tw = (float) Math.sin(age * Math.PI);
				if (!FxSprites.blit(g, FxSprites.SPARKLE, x - 3, y - 3, 7, 7, CasinoPalette.withAlpha(0xFFFFFFFF, a * tw))) {
					int c = CasinoPalette.withAlpha(CasinoPalette.LILAC, a * tw);
					g.fill(x - 3, y, x + 4, y + 1, c);
					g.fill(x, y - 3, x + 1, y + 4, c);
				}
			} else {
				int color = CONFETTI_COLORS[((sprite - SPRITE_CONFETTI) / 16) % CONFETTI_COLORS.length];
				boolean flip = ((now / 120) + i) % 2 == 0;
				g.fill(x, y, x + (flip ? 3 : 1), y + (flip ? 2 : 3), CasinoPalette.withAlpha(color, a));
			}
		}
	}

	private void updateWord(WinTier word) {
		if (word == shownWord) return;
		shownWord = word;
		String key = active.words().key(word);
		if (key == null) key = active.words().key(WinTier.WIN);
		Object arg = word == WinTier.JACKPOT
			? (active.jackpotName() != null ? Component.translatable(active.jackpotName()) : Component.empty())
			: Texts.number(active.ret());
		wordText = Component.translatable(key, arg);
		wordHasAmount = word != WinTier.JACKPOT && Language.getInstance().getOrDefault(key).contains("%");
		wordSeq = wordText.getVisualOrderText();
		wordWidth = Minecraft.getInstance().font.width(wordSeq);
	}

	private void updateAmount(long amount, Font font) {
		if (amount == shownAmount) return;
		shownAmount = amount;
		Component c = Component.translatable("gui.burmaldaholic.fx.amount", Texts.number(amount));
		amountSeq = c.getVisualOrderText();
		amountWidth = font.width(amountSeq);
	}

	private int wordColor(WinTier word, float alpha) {
		int base = switch (word) {
			case LOSS -> CasinoPalette.CHIP_RED;
			case PUSH, RETURN -> CasinoPalette.BONE_SHADE;
			case WIN, NICE -> CasinoPalette.BONUS;
			default -> CasinoPalette.GOLD;
		};
		return CasinoPalette.withAlpha(base, alpha);
	}

	private void drawOverlayText(GuiGraphicsExtractor g, Font font, double t, int cx, int h, int w, float alpha) {
		WinTier word = plan.wordAt(t);
		updateWord(word);
		int maxW = Math.max(40, w - 32);
		int scale = FxText.fitScale(font, wordText, 3, maxW);
		float s = scale * plan.wordScale(t);
		int y = h / 3 - (int) (font.lineHeight * scale / 2f) + (int) plan.bannerOffset(t);
		int ink = CasinoPalette.withAlpha(CasinoPalette.INK, alpha);
		if (word == WinTier.JACKPOT && !plan.reduced()) jackpotWave(g, font, t, cx, y, s, alpha, ink);
		else FxText.outlinedCentered(g, font, wordSeq, wordWidth, cx, y, s, wordColor(word, alpha), ink);
		int ay = y + font.lineHeight * scale + 6;
		if (!wordHasAmount) {
			updateAmount(plan.amountAt(t), font);
			int as = amountWidth * 2 <= maxW ? 2 : 1;
			FxText.outlinedCentered(g, font, amountSeq, amountWidth, cx, ay, as, CasinoPalette.withAlpha(CasinoPalette.BONUS, alpha), ink);
			ay += font.lineHeight * as + 4;
		}
		if (plan.style() == WinTier.JACKPOT && active.stake() > 0) {
			Component m = Component.translatable("gui.burmaldaholic.fx.multiplier", Texts.decimal(formatMultiple(plan.multiple())));
			FxText.outlinedCentered(g, font, m, cx, ay, 1f, CasinoPalette.withAlpha(CasinoPalette.BONE, alpha), ink);
			ay += font.lineHeight + 4;
		}
		if (active.maxWin() && active.words().maxWin() != null) {
			Component m = Component.translatable(active.words().maxWin());
			int ms = FxText.fitScale(font, m, 2, maxW);
			FxText.outlinedCentered(g, font, m, cx, ay, ms, CasinoPalette.withAlpha(CasinoPalette.CHIP_RED_LIGHT, alpha), ink);
			ay += font.lineHeight * ms + 4;
		}
		if (skippable()) {
			Component hint = Component.translatable("gui.burmaldaholic.fx.skip");
			FxText.outlinedCentered(g, font, hint, cx, Math.min(h - 14, ay + 8), 1f, CasinoPalette.withAlpha(CasinoPalette.BONE_SHADE, alpha * 0.8f), ink);
		}
	}

	private void jackpotWave(GuiGraphicsExtractor g, Font font, double t, int cx, int y, float s, float alpha, int ink) {
		String str = wordText.getString();
		int total = font.width(str);
		float x = cx - total * s / 2f;
		int color = wordColor(WinTier.JACKPOT, alpha);
		int offset = 0;
		for (int i = 0; i < str.length(); ) {
			int cp = str.codePointAt(i);
			String ch = new String(Character.toChars(cp));
			int cw = font.width(ch);
			float dy = (float) (2 * Math.sin(t / 1000.0 * 8 + offset));
			g.pose().pushMatrix();
			g.pose().translate(x + cw * s / 2f, y + dy * s + font.lineHeight * s / 2f);
			g.pose().scale(s, s);
			int lx = -cw / 2;
			int ly = -font.lineHeight / 2;
			g.text(font, ch, lx - 1, ly, ink, false);
			g.text(font, ch, lx + 1, ly, ink, false);
			g.text(font, ch, lx, ly - 1, ink, false);
			g.text(font, ch, lx, ly + 1, ink, false);
			g.text(font, ch, lx, ly, color, false);
			g.pose().popMatrix();
			x += cw * s;
			i += Character.charCount(cp);
			offset++;
		}
	}

	/** In-screen banner (WIN, NICE, PUSH, RETURN, LOSS, and every tier when celebrations are off). */
	private void drawBanner(GuiGraphicsExtractor g, Font font, double t, int cx, int h, int w, float alpha) {
		WinTier word = plan.wordAt(t);
		updateWord(word);
		int maxW = Math.max(40, w - 32);
		int scale = FxText.fitScale(font, wordText, 2, maxW);
		float s = scale * plan.wordScale(t);
		boolean withAmount = !wordHasAmount && active.tier().isWin();
		if (withAmount) updateAmount(plan.amountAt(t), font);
		int textW = Math.max((int) (wordWidth * scale), withAmount ? amountWidth : 0);
		int boxH = font.lineHeight * scale + (withAmount ? font.lineHeight + 3 : 0) + 8;
		int top = h / 4 - boxH / 2 + (int) plan.bannerOffset(t);
		int left = cx - textW / 2 - 8;
		int right = cx + textW / 2 + 8;
		if (!FxSprites.blit(g, FxSprites.PANEL, left, top, right - left, boxH, CasinoPalette.withAlpha(0xFFFFFFFF, alpha))) {
			g.fill(left, top, right, top + boxH, CasinoPalette.withAlpha(CasinoPalette.BG_DEEP, 0.9f * alpha));
			g.outline(left, top, right - left, boxH, CasinoPalette.withAlpha(CasinoPalette.FRAME, alpha));
		}
		int ink = CasinoPalette.withAlpha(CasinoPalette.INK, alpha);
		FxText.outlinedCentered(g, font, wordSeq, wordWidth, cx, top + 4, s, wordColor(word, alpha), ink);
		if (withAmount) {
			FxText.outlinedCentered(g, font, amountSeq, amountWidth, cx, top + 4 + font.lineHeight * scale + 3, 1f,
				CasinoPalette.withAlpha(CasinoPalette.BONUS, alpha), ink);
		}
	}

	private static String formatMultiple(double m) {
		long tenths = Math.round(m * 10);
		return tenths % 10 == 0 ? Long.toString(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
	}

	// ---- input ----------------------------------------------------------------------------------

	/** Key handling for the screen hook: Space / Enter / Esc skip a skippable celebration (consumed). */
	public boolean onKey(int key) {
		if (!skippable()) return false;
		if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
			skip();
			return true;
		}
		return false;
	}

	/** Mouse click for the screen hook: skips a skippable celebration (consumed). */
	public boolean onClick() {
		if (!skippable()) return false;
		skip();
		return true;
	}
}
