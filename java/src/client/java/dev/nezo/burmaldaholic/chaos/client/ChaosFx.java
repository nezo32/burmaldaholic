package dev.nezo.burmaldaholic.chaos.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.chaos.ChaosFxNet;
import dev.nezo.burmaldaholic.chaos.logic.ChaosEvent;
import dev.nezo.burmaldaholic.chaos.logic.ChaosFxMath;
import dev.nezo.burmaldaholic.chaos.net.ChaosFxPayload;
import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CasinoParticle;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.fx.CoreParticles;
import dev.nezo.burmaldaholic.core.network.FxPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Client chaos presentation (global.md §4.6, lane J-L3). The intro arrives on the shared {@code fx} channel
 * ({@code CHAOS}, arg = event): the kind tone (sound), the chaos card flipping in above the vanilla title, a bad
 * event's shake, the curse vignette and the teleport veil. World decoration arrives as {@link ChaosFxPayload} at the
 * server's real spots: chip pops, diamond glint columns, summon runes, teleport rings, buff rings, curse spirals.
 * Honours reduce motion (no flip / shake / veil, fewer particles), flashes (static tints) and the casino particle
 * budget ({@link CasinoParticle#burstAllowance}).
 */
public final class ChaosFx {
	private static final int VEIL = 0xFFF0E0FF;

	/** A world spawn scheduled for a later client tick (columns, spirals, rings that build up over time). */
	private record Pending(long tick, Runnable run) {}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static ChaosEvent card;
	private static long cardStartMs = Long.MIN_VALUE;
	private static long ticks;

	private ChaosFx() {}

	// ---- intro ---------------------------------------------------------------------------------------------------------

	static void onIntro(FxPayload p) {
		ChaosEvent[] all = ChaosEvent.values();
		if (p.arg() < 0 || p.arg() >= all.length) return;
		ChaosEvent e = all[p.arg()];
		ChaosFxMath.Tone tone = ChaosFxMath.Tone.of(e.kind());
		card = e;
		cardStartMs = AnimClock.localMs();
		switch (e) {
			case WEATHER_CHANGE -> play("entity.lightning_bolt.thunder", 0.35f, 1.3f, 0);
			default -> FxSounds.play(tone.sound, 1f);
		}
		// composite layers (global §2.7): good = second chime + orb; bad = cave rumble
		if (tone == ChaosFxMath.Tone.GOOD) {
			play("block.amethyst_block.chime", 1f, 1.8f, 2);
			play("entity.experience_orb.pickup", 0.7f, 1.6f, 3);
		} else if (tone == ChaosFxMath.Tone.BAD) {
			play("ambient.cave", 0.3f, 1f, 0);
		} else if (e == ChaosEvent.RANDOM_TELEPORT) {
			play("block.portal.trigger", 0.15f, 2f, 0);
		}
		if (e == ChaosEvent.DIAMOND_RAIN) ambientGlints();
	}

	/** 8 ambient cyan glints within 3 blocks over 2 s (never a diamond sprite: they cannot read as loot). */
	private static void ambientGlints() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || FxSettings.reduceMotion()) return;
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix((int) ticks, mc.player.getId()));
		SimpleParticleType glint = CoreParticles.get("diamond_glint");
		for (int i = 0; i < 8; i++) {
			double a = rng.nextDouble() * Math.PI * 2;
			double r = 1 + rng.nextDouble() * 2;
			double y = 0.5 + rng.nextDouble() * 2;
			later(i * 5L, () -> {
				ClientLevel level = mc.level;
				if (level == null || mc.player == null || CasinoParticle.burstAllowance(level, 1, false) < 1) return;
				Vec3 at = mc.player.position();
				level.addParticle(glint, at.x + Math.cos(a) * r, at.y + y, at.z + Math.sin(a) * r, 0, -0.01, 0);
			});
		}
	}

	// ---- world decoration ----------------------------------------------------------------------------------------------

	static void onPoints(ChaosFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return;
		boolean reduced = FxSettings.reduceMotion();
		SeedMix.FxRng rng = new SeedMix.FxRng(p.seed());
		switch (p.fx()) {
			case ChaosFxPayload.CHIP_POP -> {
				for (Vec3 v : p.points()) {
					// one pop per real pile: a chip jumps 0.6 blocks and falls back, with a glint
					add(level, CoreParticles.get("chip_pop"), v.x, v.y + 0.2, v.z, (rng.nextDouble() - 0.5) * 0.04, 0.3, (rng.nextDouble() - 0.5) * 0.04);
					if (!reduced) add(level, CoreParticles.get("chip_glint"), v.x, v.y + 0.5, v.z, 0, 0.02, 0);
				}
			}
			case ChaosFxPayload.BUFF_RING, ChaosFxPayload.GOLD_RING -> {
				boolean gold = ChaosFxPayload.GOLD_RING.equals(p.fx());
				Vec3 c = p.points().getFirst();
				int n = reduced ? 8 : 16;
				for (int i = 0; i < n; i++) {
					double[] o = ChaosFxMath.ring(i, n, 0.9, rng.nextDouble() * 0.3);
					int k = i;
					later(reduced ? 0 : i / 4, () -> {
						add(level, CoreParticles.get(gold ? "chip_glint" : "sparkle"), c.x + o[0], c.y + 0.15, c.z + o[1], 0, 0.09, 0);
						if (!gold && k % 3 == 0) add(level, ParticleTypes.HAPPY_VILLAGER, c.x + o[0], c.y + 0.4, c.z + o[1], 0, 0.05, 0);
					});
				}
			}
			case ChaosFxPayload.CURSE_SPIRAL -> {
				Vec3 c = p.points().getFirst();
				int n = 6;
				int steps = reduced ? 3 : 12;
				for (int s = 0; s < steps; s++) {
					double t = s / (double) steps;
					double t2 = (s + 1) / (double) steps;
					later(s * 2L, () -> {
						for (int i = 0; i < n; i++) {
							double[] a = ChaosFxMath.spiral(i, n, t);
							double[] b = ChaosFxMath.spiral(i, n, t2);
							add(level, CoreParticles.get("curse_wisp"), c.x + a[0], c.y + a[1], c.z + a[2], (b[0] - a[0]) / 2, (b[1] - a[1]) / 2, (b[2] - a[2]) / 2);
						}
					});
				}
			}
			case ChaosFxPayload.DIAMOND_COLUMN -> {
				Vec3 at = p.points().getFirst();
				int idx = p.arg();
				for (int k = 0; k < ChaosFxMath.COLUMN_TICKS; k++) {
					double h = ChaosFxMath.columnHeight(k * 50.0);
					later(k, () -> {
						add(level, CoreParticles.get("diamond_glint"), at.x, at.y + h, at.z, 0, -0.05, 0);
						if (!reduced) add(level, CoreParticles.get("diamond_glint"), at.x, at.y + h + 0.35, at.z, 0, -0.03, 0);
					});
				}
				later(ChaosFxMath.COLUMN_TICKS, () -> {
					CasinoParticle.burst(level, CoreParticles.get("diamond_glint"), at.x, at.y + 0.2, at.z, reduced ? 3 : 6, 0.06, false, p.seed());
					FxSounds.playRaw(Identifier.withDefaultNamespace("block.amethyst_block.chime"), SoundSource.PLAYERS, 0.8f, 1.0f + 0.08f * Math.min(12, idx), 0, at);
				});
			}
			case ChaosFxPayload.XP_SPARKLE -> {
				Vec3 c = p.points().getFirst();
				CasinoParticle.burst(level, CoreParticles.get("sparkle"), c.x, c.y + 1.2, c.z, reduced ? 4 : 8, 0.07, false, p.seed());
				add(level, ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1.4, c.z, 0, 0.05, 0);
			}
			case ChaosFxPayload.MOB_RUNE -> {
				SimpleParticleType rune = ChaosFxNet.rune();
				for (Vec3 v : p.points()) {
					add(level, rune != null ? rune : CoreParticles.get("summon_rune"), v.x, v.y, v.z, 0, 0, 0);
					if (!reduced) {
						for (int k = 0; k < 3; k++) later(4L + k * 4, () -> add(level, CoreParticles.get("summon_rune"), v.x, v.y + 0.1, v.z, 0, 0.04, 0));
					}
				}
			}
			case ChaosFxPayload.TELEPORT_RING -> {
				Vec3 c = p.points().getFirst();
				int n = reduced ? 8 : 14;
				for (int s = 0; s < (reduced ? 1 : 3); s++) {
					double r = ChaosFxMath.teleportRingRadius(s * 100.0);
					later(s * 2L, () -> {
						for (int i = 0; i < n; i++) {
							double[] o = ChaosFxMath.ring(i, n, r, 0);
							add(level, CoreParticles.get("teleport_ring"), c.x + o[0], c.y + 0.2, c.z + o[1], o[0] * 0.03, 0.01, o[1] * 0.03);
						}
					});
				}
				add(level, ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1, c.z, 0, 0.05, 0);
			}
			default -> {
			}
		}
	}

	/** Adds one world particle under the casino budget. */
	private static void add(ClientLevel level, ParticleOptions type, double x, double y, double z, double dx, double dy, double dz) {
		if (type == null || level == null) return;
		if (type instanceof SimpleParticleType s && CoreParticles.all().containsValue(s) && CasinoParticle.burstAllowance(level, 1, false) < 1) return;
		level.addParticle(type, x, y, z, dx, dy, dz);
	}

	static void later(long ticksFromNow, Runnable run) {
		if (ticksFromNow <= 0) run.run();
		else PENDING.add(new Pending(ticks + ticksFromNow, run));
	}

	private static void play(String vanilla, float volume, float pitch, int delayTicks) {
		FxSounds.playRaw(Identifier.withDefaultNamespace(vanilla), SoundSource.PLAYERS, volume, pitch, delayTicks, null);
	}

	static void tick(Minecraft mc) {
		ticks++;
		if (PENDING.isEmpty()) return;
		if (mc.level == null) {
			PENDING.clear();
			return;
		}
		List<Pending> due = new ArrayList<>();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (p.tick() <= ticks) {
				due.add(p);
				it.remove();
			}
		}
		for (Pending p : due) p.run().run();
	}

	static void reset() {
		PENDING.clear();
		card = null;
		cardStartMs = Long.MIN_VALUE;
	}

	// ---- HUD: card, shake, curse vignette, teleport veil ---------------------------------------------------------------

	/** Full-screen layers (after the vanilla vignette): curse vignette, teleport veil. */
	static void extractVeil(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (card == null) return;
		double ms = AnimClock.localMs() - cardStartMs;
		boolean flashes = FxSettings.flashes();
		int w = g.guiWidth();
		int h = g.guiHeight();
		if (card == ChaosEvent.CURSE) {
			double a = ChaosFxMath.curseVignette(ms, flashes);
			if (a > 0.004) vignette(g, w, h, CasinoPalette.CURSE_BG, (float) a);
		}
		if (card == ChaosEvent.RANDOM_TELEPORT) {
			double a = ChaosFxMath.teleportVeil(ms, flashes);
			if (a > 0.004) g.fill(0, 0, w, h, CasinoPalette.withAlpha(VEIL, (float) a));
		}
	}

	/** The chaos card above the vanilla title (flip in, hold, flip out; bad events shake it). */
	static void extractCard(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (card == null) return;
		double ms = AnimClock.localMs() - cardStartMs;
		if (ms >= ChaosFxMath.CARD_TOTAL_MS) {
			if (ms > ChaosFxMath.CARD_TOTAL_MS + 1000) card = null;
			return;
		}
		boolean reduced = FxSettings.reduceMotion();
		double sx = ChaosFxMath.cardScaleX(ms, reduced);
		double alpha = ChaosFxMath.cardAlpha(ms);
		if (sx <= 0.01 || alpha <= 0.01) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int size = 64;
		double shake = card.kind() == ChaosEvent.Kind.BAD ? ChaosFxMath.shakePx(ms, reduced) : 0;
		int cx = w / 2 + (int) Math.round(shake);
		int top = Math.max(4, h / 2 - 50 - size);
		Identifier sprite = Burmaldaholic.id("chaos/card_" + card.id().toLowerCase(Locale.ROOT));
		g.pose().pushMatrix();
		g.pose().translate(cx, top + size / 2f);
		g.pose().scale((float) sx, 1f);
		int argb = CasinoPalette.withAlpha(0xFFFFFFFF, (float) alpha);
		if (!FxSprites.blit(g, sprite, -size / 2, -size / 2, size, size, argb)) {
			int tone = ChaosFxMath.Tone.of(card.kind()).color;
			g.fill(-size / 2, -size / 2, size / 2, size / 2, CasinoPalette.withAlpha(CasinoPalette.BG_DEEP, (float) alpha));
			g.outline(-size / 2, -size / 2, size, size, CasinoPalette.withAlpha(tone, (float) alpha));
		}
		g.pose().popMatrix();
	}

	/** Tinted vignette (white sprite × colour), gradient bands when the sprite is missing. */
	static void vignette(GuiGraphicsExtractor g, int w, int h, int rgb, float alpha) {
		int argb = CasinoPalette.withAlpha(rgb, alpha);
		if (FxSprites.blit(g, Burmaldaholic.id("chaos/vignette"), 0, 0, w, h, argb)) return;
		int clear = CasinoPalette.withAlpha(rgb, 0);
		int band = Math.max(12, h / 6);
		g.fillGradient(0, 0, w, band, argb, clear);
		g.fillGradient(0, h - band, w, h, clear, argb);
	}

	/** Test hook: the card being shown (null none). */
	public static ChaosEvent card() {
		return card;
	}
}
