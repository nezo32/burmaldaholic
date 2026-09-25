package dev.nezo.burmaldaholic.chaos.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.chaos.logic.ChaosFxMath;
import dev.nezo.burmaldaholic.chaos.logic.GoldenHourScore;
import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CasinoParticle;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.client.fx.FxText;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.fx.CoreParticles;
import dev.nezo.burmaldaholic.core.network.PlayerStatusPayload;
import dev.nezo.burmaldaholic.core.text.Texts;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Golden Hour on the client (global.md §4.5, lane J-L3): the server-wide gold vignette (30 % swell, then a steady
 * 12 %), 24 golden motes rising around the player, the countdown plaque (sun + multiplier + m:ss from the server's
 * ticks; the last ten seconds count down big in the middle of the screen), and the lounge loop on the AMBIENT
 * category ({@link GoldenHourScore}). Starts from the server's {@code GOLDEN_HOUR_START}; a player who joins mid-hour
 * (no start payload) sees the steady state at once; the end payload (or the server's timer reaching 0) fades out.
 * The timer never ends early: it is the server's value minus the ticks since it arrived, held at 0:01 until the
 * server says it is over.
 */
public final class GoldenHourFx {
	private static final Identifier PLAQUE = Burmaldaholic.id("chaos/gh_plaque");
	private static final Identifier SUN = Burmaldaholic.id("chaos/gh_sun");
	private static final int PLAQUE_IN_MS = 400;

	private static long startMs = Long.MIN_VALUE;
	private static long endMs = Long.MIN_VALUE;
	private static double endFrom;
	private static PlayerStatusPayload lastStatus;
	private static long syncTick;
	private static long serverTicks;
	private static long lastCountdownSecond = -1;
	private static long step;
	// cached text
	private static String clockText = "";
	private static Component clockComponent = Component.empty();

	private GoldenHourFx() {}

	static void onStart(int durationTicks) {
		startMs = AnimClock.localMs();
		endMs = Long.MIN_VALUE;
		serverTicks = Math.max(serverTicks, durationTicks);
		syncTick = ClientCasinoState.clientTicks();
		FxSounds.playRaw(Identifier.withDefaultNamespace("block.beacon.activate"), SoundSource.AMBIENT, 0.5f, 1.4f, 0, null);
		motes();
	}

	static void onEnd() {
		if (endMs != Long.MIN_VALUE) return;
		endFrom = vignetteNow(AnimClock.localMs());
		endMs = AnimClock.localMs();
		startMs = Long.MIN_VALUE;
		FxSounds.play("golden_hour_end", 1f);
		FxSounds.playRaw(Identifier.withDefaultNamespace("block.beacon.deactivate"), SoundSource.AMBIENT, 0.6f, 1.2f, 0, null);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable("hud.burmaldaholic.golden_hour.over"));
	}

	static void reset() {
		startMs = Long.MIN_VALUE;
		endMs = Long.MIN_VALUE;
		lastStatus = null;
		serverTicks = 0;
		lastCountdownSecond = -1;
	}

	/** Remaining ticks as the client shows them (0 = no Golden Hour). */
	public static long remaining() {
		return ChaosFxMath.remainingTicks(serverTicks, ClientCasinoState.clientTicks() - syncTick);
	}

	public static boolean active() {
		return ClientCasinoState.hasStatus() && remaining() > 0;
	}

	// ---- tick: timer sync, music, countdown ticks ----------------------------------------------------------------------

	static void tick(Minecraft mc) {
		PlayerStatusPayload s = ClientCasinoState.hasStatus() ? ClientCasinoState.status() : null;
		if (s != lastStatus) {
			lastStatus = s;
			long before = serverTicks;
			serverTicks = s == null ? 0 : s.goldenHourTicks();
			syncTick = ClientCasinoState.clientTicks();
			if (before > 0 && serverTicks <= 0) onEnd(); // missed END payload: the server's timer is the truth
		}
		if (!active() || mc.level == null) {
			step = 0;
			return;
		}
		long t = mc.level.getGameTime();
		if (GoldenHourScore.onStep(t) && FxSettings.volume() > 0) {
			for (GoldenHourScore.Note n : GoldenHourScore.notes(t / GoldenHourScore.STEP_TICKS)) {
				FxSounds.playRaw(Burmaldaholic.id("golden_hour_music." + n.voice()), SoundSource.AMBIENT, n.volume(), GoldenHourScore.pitch(n.semitone()), 0,
					null);
			}
		}
		long left = remaining();
		long sec = (left + 19) / 20;
		if (left <= 200 && sec != lastCountdownSecond) {
			lastCountdownSecond = sec;
			FxSounds.playRaw(Identifier.withDefaultNamespace("block.note_block.hat"), SoundSource.AMBIENT, 0.6f, sec <= 3 ? 1.4f : 1.1f, 0, null);
		}
	}

	private static void motes() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		SimpleParticleType mote = CoreParticles.get("golden_mote");
		int n = FxSettings.reduceMotion() ? 8 : ChaosFxMath.GH_MOTES;
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix(mc.player.getId(), (int) mc.level.getGameTime()));
		for (int i = 0; i < n; i++) {
			double a = rng.nextDouble() * Math.PI * 2;
			double r = 1.2 + rng.nextDouble() * 1.8;
			long delayTicks = ChaosFxMath.moteSpawnMs(i * ChaosFxMath.GH_MOTES / n) / 50;
			ChaosFx.later(delayTicks, () -> {
				if (mc.player == null || mc.level == null || CasinoParticle.burstAllowance(mc.level, 1, false) < 1) return;
				Vec3 p = mc.player.position();
				mc.level.addParticle(mote, p.x + Math.cos(a) * r, p.y + 0.2, p.z + Math.sin(a) * r, 0, 0.06, 0);
			});
		}
	}

	// ---- HUD -----------------------------------------------------------------------------------------------------------

	private static double vignetteNow(long now) {
		boolean flashes = FxSettings.flashes();
		if (endMs != Long.MIN_VALUE) return ChaosFxMath.goldenVignetteEnd(now - endMs, endFrom);
		if (!active()) return 0;
		if (startMs != Long.MIN_VALUE) return ChaosFxMath.goldenVignette(now - startMs, flashes);
		return flashes ? ChaosFxMath.GH_STEADY : 0; // joined mid-hour: the steady state
	}

	/** Gold edge vignette (after the vanilla vignette). */
	static void extractVignette(GuiGraphicsExtractor g, DeltaTracker delta) {
		long now = AnimClock.localMs();
		double a = vignetteNow(now);
		if (endMs != Long.MIN_VALUE && now - endMs > ChaosFxMath.GH_END_MS) endMs = Long.MIN_VALUE;
		if (a > 0.004) ChaosFx.vignette(g, g.guiWidth(), g.guiHeight(), CasinoPalette.GOLD, (float) a);
	}

	/** Countdown plaque (top right) and the last-ten-seconds big count. */
	static void extractCountdown(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!active()) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		long now = AnimClock.localMs();
		long left = remaining();
		String clock = ChaosFxMath.clock(left);
		if (!clock.equals(clockText)) {
			clockText = clock;
			clockComponent = Component.translatable("hud.burmaldaholic.golden_hour", Texts.raw(multiplier() + "  " + clock)); // literal-ok: numbers
		}
		int w = g.guiWidth();
		int textW = font.width(clockComponent);
		int pw = textW + 30;
		int ph = 18;
		double in = startMs == Long.MIN_VALUE ? 1 : Ease.OUT_CUBIC.apply((now - startMs) / (double) PLAQUE_IN_MS);
		if (FxSettings.reduceMotion()) in = 1;
		int x = (int) Math.round(w - 6 - pw + (1 - in) * (pw + 10));
		int y = 6;
		// "ending in" pulse: the plaque grows once when a minute is left (and at 10 s)
		float pulse = 1f;
		if (!FxSettings.reduceMotion() && (left <= 1200 && left > 1190 || left <= 200 && left > 190)) pulse = 1.1f;
		g.pose().pushMatrix();
		g.pose().translate(x + pw / 2f, y + ph / 2f);
		g.pose().scale(pulse, pulse);
		g.pose().translate(-pw / 2f, -ph / 2f);
		if (!FxSprites.blit(g, PLAQUE, 0, 0, pw, ph, 0xFFFFFFFF)) {
			g.fill(0, 0, pw, ph, CasinoPalette.BG_DEEP);
			g.outline(0, 0, pw, ph, CasinoPalette.GOLD);
		}
		if (!FxSprites.blit(g, SUN, 4, 1, 16, 16, 0xFFFFFFFF)) g.fill(8, 5, 16, 13, CasinoPalette.GOLD);
		g.text(font, clockComponent, 23, 5, CasinoPalette.GOLD, true);
		g.pose().popMatrix();

		if (left <= 200) {
			long sec = (left + 19) / 20;
			double into = ((200 - left) % 20) * 50.0 + delta.getGameTimeDeltaPartialTick(false) * 50.0;
			float s = (float) ChaosFxMath.countdownScale(left, into, FxSettings.reduceMotion());
			Component big = Texts.number(sec);
			int ink = CasinoPalette.INK;
			FxText.outlinedCentered(g, font, big, w / 2, g.guiHeight() / 5, 3f * s, CasinoPalette.GOLD, ink);
		}
	}

	private static String multiplier() {
		double m;
		try {
			m = CasinoConfig.chaos().goldenHour.multiplier;
		} catch (RuntimeException e) {
			m = 2;
		}
		String n = m == Math.rint(m) ? Long.toString((long) m) : Double.toString(Math.round(m * 100) / 100.0);
		return "×" + n; // literal-ok: multiplier symbol
	}
}
