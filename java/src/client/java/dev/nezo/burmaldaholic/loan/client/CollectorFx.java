package dev.nezo.burmaldaholic.loan.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CasinoParticle;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.core.fx.CoreParticles;
import dev.nezo.burmaldaholic.loan.logic.ArrivalPlan;
import dev.nezo.burmaldaholic.loan.net.LoanFxPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Debt Collectors' arrival (global.md §4.9, lane J-L3), from {@link LoanFxPayload}: three door knocks (personal
 * for the debtor, positional at the debtor for spectators), the red-steel "fist" card above the vanilla "Knock knock"
 * title (it jolts on each knock), two red vignette pulses (flashes off: one static tint) and a smoke column at every
 * real squad member with {@code collector_arrive} once. All timing is {@link ArrivalPlan}.
 */
final class CollectorFx {
	private static final Identifier CARD = Burmaldaholic.id("loan/collector_card");
	private static final Identifier VIGNETTE = Burmaldaholic.id("loan/vignette");

	private record Pending(long atMs, Runnable run) {}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static long startMs = Long.MIN_VALUE;

	private CollectorFx() {}

	static void onPayload(LoanFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		long now = AnimClock.localMs();
		boolean mine = p.debtor().equals(mc.player.getUUID());
		if (mine) startMs = now;
		Vec3 knockAt = mine ? null : p.at();
		for (int k = 0; k < ArrivalPlan.KNOCK_MS.length; k++) {
			float pitch = ArrivalPlan.KNOCK_PITCH[k];
			PENDING.add(new Pending(now + ArrivalPlan.KNOCK_MS[k], () -> FxSounds.playRaw(Identifier.withDefaultNamespace("block.wooden_door.close"),
				knockAt == null ? SoundSource.PLAYERS : SoundSource.HOSTILE, 0.9f, pitch, 0, knockAt)));
		}
		boolean reduced = FxSettings.reduceMotion();
		SimpleParticleType smoke = CoreParticles.get("collector_smoke");
		for (int m = 0; m < p.members().size(); m++) {
			Vec3 v = p.members().get(m);
			int member = m;
			for (int i = 0; i < ArrivalPlan.SMOKE_PER_MEMBER; i += reduced ? 3 : 1) {
				double[] s = ArrivalPlan.smoke(i, member);
				PENDING.add(new Pending(now + (long) s[3], () -> {
					ClientLevel level = Minecraft.getInstance().level;
					if (level == null || CasinoParticle.burstAllowance(level, 1, false) < 1) return;
					level.addParticle(smoke, v.x + s[0], v.y + s[1], v.z + s[2], 0, 0.07, 0);
				}));
			}
			PENDING.add(new Pending(now, () -> {
				ClientLevel level = Minecraft.getInstance().level;
				if (level != null) level.addParticle(ParticleTypes.POOF, v.x, v.y + 0.5, v.z, 0, 0.02, 0);
			}));
		}
		if (!p.members().isEmpty()) {
			Vec3 first = p.members().getFirst();
			PENDING.add(new Pending(now + 120, () -> FxSounds.playAt("collector_arrive", first.x, first.y, first.z, 1f, 1f)));
		}
	}

	static void tick() {
		if (PENDING.isEmpty()) return;
		long now = AnimClock.localMs();
		List<Pending> due = new ArrayList<>();
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (p.atMs() <= now) {
				due.add(p);
				it.remove();
			}
		}
		for (Pending p : due) p.run().run();
	}

	static void reset() {
		PENDING.clear();
		startMs = Long.MIN_VALUE;
	}

	static boolean active() {
		return startMs != Long.MIN_VALUE && AnimClock.localMs() - startMs < ArrivalPlan.CARD_MS;
	}

	/** Red vignette pulses (after the vanilla vignette). */
	static void extractVignette(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (startMs == Long.MIN_VALUE) return;
		double ms = AnimClock.localMs() - startMs;
		double a = ArrivalPlan.vignette(ms, FxSettings.flashes());
		if (a <= 0.003) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int argb = CasinoPalette.withAlpha(CasinoPalette.CHIP_RED_DARK, (float) a);
		if (!FxSprites.blit(g, VIGNETTE, 0, 0, w, h, argb)) {
			int band = Math.max(16, h / 6);
			g.fillGradient(0, 0, w, band, argb, CasinoPalette.withAlpha(CasinoPalette.CHIP_RED_DARK, 0));
			g.fillGradient(0, h - band, w, h, CasinoPalette.withAlpha(CasinoPalette.CHIP_RED_DARK, 0), argb);
		}
	}

	/** The fist card above the vanilla title, jolting on each knock. */
	static void extractCard(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!active()) return;
		double ms = AnimClock.localMs() - startMs;
		float alpha = (float) ArrivalPlan.cardAlpha(ms);
		if (alpha <= 0.01f) return;
		int size = 48;
		int x = g.guiWidth() / 2 - size / 2 + (int) Math.round(ArrivalPlan.knockShake(ms, FxSettings.reduceMotion()));
		int y = Math.max(4, g.guiHeight() / 2 - 50 - size);
		if (!FxSprites.blit(g, CARD, x, y, size, size, CasinoPalette.withAlpha(0xFFFFFFFF, alpha))) {
			g.fill(x, y, x + size, y + size, CasinoPalette.withAlpha(CasinoPalette.CHIP_RED_DARK, alpha));
		}
	}
}
