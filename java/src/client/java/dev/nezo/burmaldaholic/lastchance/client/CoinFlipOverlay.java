package dev.nezo.burmaldaholic.lastchance.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.client.fx.FxText;
import dev.nezo.burmaldaholic.client.fx.GuiParticlePool;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.lastchance.logic.CoinFlipTimeline;
import dev.nezo.burmaldaholic.lastchance.net.CoinFlipPayload;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Last Chance cinematic (global.md §4.8, lane J-L3): red-black vignette and heartbeat, "Last Chance…", the coin
 * launched and spinning (12-frame sheet at 2×), decelerating onto the face the server already decided — then the
 * result: heads = gold sparkle burst and a gold cross-fade (the server's totem + sound land on the same tick), tails =
 * the coin cracks and the vignette turns red. Every frame comes from {@link CoinFlipTimeline} (pure, tested: the last
 * frames only approach the payload face). Drawn above the HUD, so it also shows over the death screen on tails.
 * Reduced motion: no launch or vignette, face frames swap, then the result. Flashes off: vignette ≤ 25 %, no colour
 * change on landing.
 */
final class CoinFlipOverlay {
	private static final Identifier SHEET = Burmaldaholic.id("textures/gui/lastchance/coin_spin.png");
	private static final Identifier[] CRACK = {Burmaldaholic.id("textures/gui/lastchance/coin_crack_0.png"),
		Burmaldaholic.id("textures/gui/lastchance/coin_crack_1.png")};
	private static final Identifier VIGNETTE = Burmaldaholic.id("lastchance/vignette");
	private static final int COIN = 64;
	private static final int SPARKLE = 1;

	private static CoinFlipPayload current;
	private static long startMs;
	private static long lastMs;
	private static boolean heartbeat2;
	private static boolean landed;
	private static final GuiParticlePool PARTICLES = new GuiParticlePool();

	private CoinFlipOverlay() {}

	static void start(CoinFlipPayload payload) {
		current = payload;
		startMs = AnimClock.localMs();
		lastMs = startMs;
		heartbeat2 = false;
		landed = false;
		PARTICLES.clear();
		FxSounds.play("heartbeat", 1f, 1.0f);
	}

	static void reset() {
		current = null;
		PARTICLES.clear();
	}

	static boolean active() {
		return current != null && CoinFlipTimeline.active(AnimClock.localMs() - startMs);
	}

	/** Test hook: the frame shown now (null when idle). */
	static CoinFlipTimeline.Frame frameNow() {
		if (current == null) return null;
		return CoinFlipTimeline.frame(AnimClock.localMs() - startMs, current.heads(), FxSettings.reduceMotion(), FxSettings.flashes());
	}

	static CoinFlipPayload current() {
		return current;
	}

	static void tick() {
		if (current != null && !active()) reset();
	}

	private static void beats(double ms) {
		if (!heartbeat2 && ms >= CoinFlipTimeline.HEARTBEAT_2_MS) {
			heartbeat2 = true;
			FxSounds.play("heartbeat", 1f, 1.12f);
		}
		boolean reduced = FxSettings.reduceMotion();
		if (!landed && ms >= CoinFlipTimeline.landMs(reduced)) {
			landed = true;
			FxSounds.play("coin_land", 1f);
			if (current.heads()) {
				if (!reduced) burst();
			} else {
				FxSounds.play("lose", 1f, 0.6f);
				FxSounds.playRaw(Identifier.withDefaultNamespace("block.chain.break"), SoundSource.PLAYERS, 0.5f, 1.6f, 2, null);
			}
		}
	}

	private static void burst() {
		Minecraft mc = Minecraft.getInstance();
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		float cx = w / 2f;
		float cy = coinCenterY(h);
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix((int) startMs, 7));
		long now = AnimClock.localMs();
		for (int i = 0; i < CoinFlipTimeline.SPARKLES; i++) {
			double a = i * Math.PI * 2 / CoinFlipTimeline.SPARKLES + rng.nextDouble() * 0.3;
			double sp = 0.09 + rng.nextDouble() * 0.08;
			PARTICLES.spawn(SPARKLE, cx, cy, (float) (Math.cos(a) * sp), (float) (Math.sin(a) * sp - 0.03), 0.00012f, now, 700 + rng.nextInt(300));
		}
	}

	private static int titleY(int h) {
		return h / 5;
	}

	private static float coinCenterY(int h) {
		return titleY(h) + 22 + CoinFlipTimeline.LAUNCH_PX * 2 + COIN / 2f;
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!active()) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		long now = AnimClock.localMs();
		double ms = now - startMs;
		beats(ms);
		boolean reduced = FxSettings.reduceMotion();
		CoinFlipTimeline.Frame f = CoinFlipTimeline.frame(ms, current.heads(), reduced, FxSettings.flashes());
		float alpha = (float) f.alpha();
		if (alpha <= 0.02f) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cx = w / 2;

		// the world desaturates (tint) and the edges close in
		if (f.tint() > 0.003) g.fill(0, 0, w, h, CasinoPalette.withAlpha(CasinoPalette.BG_DARKEST, (float) f.tint() * alpha));
		if (f.vignette() > 0.003) {
			int argb = CasinoPalette.withAlpha(f.vignetteColor(), (float) f.vignette() * alpha);
			if (!FxSprites.blit(g, VIGNETTE, 0, 0, w, h, argb)) {
				int clear = CasinoPalette.withAlpha(f.vignetteColor(), 0);
				int band = Math.max(16, h / 5);
				g.fillGradient(0, 0, w, band, argb, clear);
				g.fillGradient(0, h - band, w, h, clear, argb);
			}
		}

		// title: "Last Chance…" then HEADS! / TAILS…
		int maxW = Math.max(80, w - 32);
		Component title = Component.translatable(f.title() == 1 ? "msg.burmaldaholic.lastchance.flip_title"
			: current.heads() ? "msg.burmaldaholic.lastchance.heads_title" : "msg.burmaldaholic.lastchance.tails_title");
		int color = f.title() == 1 ? CasinoPalette.BONE : current.heads() ? CasinoPalette.GOLD : CasinoPalette.CHIP_RED_LIGHT;
		int scale = FxText.fitScale(font, title, 2, maxW);
		float ta = alpha * (float) f.titleAlpha();
		FxText.outlinedCentered(g, font, title, cx, titleY(h), (float) (scale * f.titleScale()), CasinoPalette.withAlpha(color, ta),
			CasinoPalette.withAlpha(CasinoPalette.INK, ta));

		// the coin (2× the 32 px sheet; the frame and height come from the timeline)
		float coinTop = coinCenterY(h) - COIN / 2f + (float) f.coinY() * 2;
		int x0 = cx - COIN / 2;
		int y0 = Math.round(coinTop);
		if (alpha > 0.5f) {
			// shadow on the "table line" grows as the coin comes down
			float lift = (float) (-f.coinY() / CoinFlipTimeline.LAUNCH_PX);
			int sw = (int) (COIN * (0.7f - 0.25f * lift));
			g.fill(cx - sw / 2, Math.round(coinCenterY(h) + COIN / 2f + 2), cx + sw / 2, Math.round(coinCenterY(h) + COIN / 2f + 5),
				CasinoPalette.withAlpha(CasinoPalette.INK, 0.45f * alpha));
			Identifier tex = f.crack() >= 0 ? CRACK[f.crack()] : SHEET;
			if (f.crack() >= 0) g.blit(tex, x0, y0, x0 + COIN, y0 + COIN, 0, 1, 0, 1);
			else {
				float v0 = f.coinFrame() / (float) CoinFlipTimeline.FRAMES;
				float v1 = (f.coinFrame() + 1) / (float) CoinFlipTimeline.FRAMES;
				g.blit(tex, x0, y0, x0 + COIN, y0 + COIN, 0, 1, v0, v1);
			}
		}

		// sparkles (heads)
		float dt = Math.max(0, Math.min(100, now - lastMs));
		lastMs = now;
		PARTICLES.step(now, dt);
		for (int i = 0; i < PARTICLES.count(); i++) {
			float age = PARTICLES.age(i, now);
			float a = alpha * (float) Math.sin(Math.PI * Math.min(1, age));
			int px = (int) PARTICLES.x(i);
			int py = (int) PARTICLES.y(i);
			if (!FxSprites.blit(g, FxSprites.SPARKLE, px - 3, py - 3, 7, 7, CasinoPalette.withAlpha(0xFFFFE070, a))) {
				int c = CasinoPalette.withAlpha(CasinoPalette.GOLD, a);
				g.fill(px - 2, py, px + 3, py + 1, c);
				g.fill(px, py - 2, px + 1, py + 3, c);
			}
		}

		// result subtitle (existing keys; high stakes in red)
		if (f.landed()) {
			Component sub = Component.translatable(!current.heads() ? "msg.burmaldaholic.lastchance.tails_subtitle"
				: current.highStakes() ? "msg.burmaldaholic.lastchance.hardcore.title" : "msg.burmaldaholic.lastchance.heads_subtitle");
			int subColor = current.heads() && current.highStakes() ? CasinoPalette.CHIP_RED_LIGHT : CasinoPalette.BONE;
			int y = Math.round(coinCenterY(h) + COIN / 2f + 10);
			List<FormattedCharSequence> lines = font.split(sub, maxW);
			for (FormattedCharSequence line : lines) {
				FxText.outlinedCentered(g, font, line, font.width(line), cx, y, 1f, CasinoPalette.withAlpha(subColor, alpha),
					CasinoPalette.withAlpha(CasinoPalette.INK, alpha));
				y += font.lineHeight + 1;
			}
		}
	}
}
