package dev.nezo.burmaldaholic.vip.client;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.anim.AnimClock;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.CasinoParticle;
import dev.nezo.burmaldaholic.client.fx.CasinoToast;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.client.fx.FxText;
import dev.nezo.burmaldaholic.client.fx.GuiParticlePool;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.fx.CoreParticles;
import dev.nezo.burmaldaholic.core.fx.ServerFx;
import dev.nezo.burmaldaholic.core.network.FxPayload;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.vip.logic.TierUpPlan;
import dev.nezo.burmaldaholic.vip.logic.VipRules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * VIP tier-up celebration (global.md §4.11, lane J-L3), from the server's {@code VIP_UP} payload ({@code tier} = the
 * tier before, {@code arg} = the new tier, {@code actor} = the promoted player): for the promoted player a dimmed
 * backdrop, tier-coloured rays, the 48 × 48 badge popping in (earlier tiers of a multi-tier jump flick past first),
 * the shine sweep, "VIP tier up!" + the tier name in its colour + the max bet, the bell arpeggio, Netherite embers,
 * then a small casino toast. Spectators within 16 blocks see a rising sparkle ring in the tier colour. All timing is
 * {@link TierUpPlan}; the badge that stays is always the server's tier; reduced motion shows a static card.
 */
final class VipTierUpOverlay {
	private static final int EMBER = 1;
	private static final int BADGE = 48;
	private static TierUpPlan plan;
	private static long startMs;
	private static long lastMs;
	private static int notesPlayed;
	private static boolean toasted;
	private static final GuiParticlePool EMBERS = new GuiParticlePool();

	private VipTierUpOverlay() {}

	static void onPayload(FxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		int tier = VipTiers.clamp(p.arg());
		boolean mine = p.actor().map(id -> id.equals(mc.player.getUUID())).orElse(false);
		if (mine) start(p.tier(), tier);
		else p.pos().ifPresent(pos -> ring(pos, tier, p.seed()));
	}

	static void start(int fromTier, int tier) {
		plan = new TierUpPlan(fromTier, tier, FxSettings.reduceMotion());
		startMs = AnimClock.localMs();
		lastMs = startMs;
		notesPlayed = 0;
		toasted = false;
		EMBERS.clear();
		FxSounds.play("vip_tier_up", 1f);
	}

	static void reset() {
		plan = null;
		EMBERS.clear();
	}

	static boolean active() {
		return plan != null;
	}

	/** Test hook: the tier whose badge is drawn now. */
	static int shownTier() {
		return plan == null ? -1 : plan.badgeTier(AnimClock.localMs() - startMs);
	}

	/** Spectators: 24 sparkles rising in a ring around the promoted player (1.5 s). */
	private static void ring(Vec3 pos, int tier, int seed) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || !FxSettings.othersCelebrations()) return;
		int n = FxSettings.reduceMotion() ? 10 : 24;
		n = CasinoParticle.burstAllowance(level, n, false);
		SeedMix.FxRng rng = new SeedMix.FxRng(seed);
		String type = tier >= VipRules.NETHERITE ? "gold_burst" : tier >= VipRules.GOLD ? "chip_glint" : "sparkle";
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n + rng.nextDouble() * 0.2;
			level.addParticle(CoreParticles.get(type), pos.x + Math.cos(a) * 0.9, pos.y + 0.2 + (i % 3) * 0.3, pos.z + Math.sin(a) * 0.9, 0, 0.08, 0);
		}
		FxSounds.playRaw(Identifier.withDefaultNamespace("ui.toast.challenge_complete"), SoundSource.PLAYERS, 0.4f, 1.1f, 0, pos);
	}

	private static Identifier badge(int tier) {
		return Burmaldaholic.id("vip/badge_large_" + VipTiers.IDS[VipTiers.clamp(tier)]);
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (plan == null) return;
		long now = AnimClock.localMs();
		double ms = now - startMs;
		if (plan.done(ms)) {
			finish();
			return;
		}
		sounds(ms);
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cx = w / 2;
		int cy = Math.max(BADGE, h / 4);
		float alpha = (float) plan.alpha(ms);
		int tier = plan.badgeTier(ms);
		int tierColor = CasinoPalette.VIP[VipTiers.clamp(tier)];
		boolean netherite = plan.tier() >= VipRules.NETHERITE;

		double dim = plan.backdrop(ms);
		if (dim > 0.003) g.fill(0, 0, w, h, CasinoPalette.withAlpha(CasinoPalette.BG_DARKEST, (float) dim));
		double rays = plan.rays(ms);
		if (rays > 0.01) {
			int rc = netherite ? 0xFFFF7A3C : tier == VipRules.SILVER || tier == VipRules.PLATINUM ? 0xFFE8F4FF : tierColor;
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate((float) Math.toRadians(plan.raysDegrees(ms)));
			int s = Math.min(w, h) * 3 / 4;
			FxSprites.blit(g, FxSprites.RAYS, -s / 2, -s / 2, s, s, CasinoPalette.withAlpha(rc, 0.45f * (float) rays));
			g.pose().popMatrix();
		}
		// embers (Netherite)
		float dt = Math.max(0, Math.min(100, now - lastMs));
		lastMs = now;
		if (plan.embers(VipRules.NETHERITE) && EMBERS.count() < TierUpPlan.EMBERS && ms < 1600) {
			SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix((int) startMs, EMBERS.count()));
			EMBERS.spawn(EMBER, cx + (float) (rng.nextDouble() * 120 - 60), cy + 30, (float) (rng.nextDouble() * 0.02 - 0.01),
				(float) (-0.04 - rng.nextDouble() * 0.04), 0, now, 1200);
		}
		EMBERS.step(now, dt);
		for (int i = 0; i < EMBERS.count(); i++) {
			float a = alpha * (1 - EMBERS.age(i, now));
			int x = (int) EMBERS.x(i);
			int y = (int) EMBERS.y(i);
			g.fill(x, y, x + 2, y + 2, CasinoPalette.withAlpha(0xFFFF7A3C, a));
			g.fill(x, y, x + 1, y + 1, CasinoPalette.withAlpha(0xFFFFD080, a));
		}

		// badge pop (+ shine)
		float scale = (float) plan.badgeScale(ms);
		if (scale > 0.01f) {
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().scale(scale * 2, scale * 2);
			int argb = CasinoPalette.withAlpha(0xFFFFFFFF, alpha);
			if (!FxSprites.blit(g, badge(tier), -BADGE / 2, -BADGE / 2, BADGE, BADGE, argb)) {
				g.fill(-20, -20, 20, 20, CasinoPalette.withAlpha(tierColor, alpha));
			}
			int shine = plan.shineFrame(ms);
			if (shine >= 0) FxSprites.blit(g, Burmaldaholic.id("vip/badge_shine_" + shine), -BADGE / 2, -BADGE / 2, BADGE, BADGE, argb);
			g.pose().popMatrix();
		}

		// text: "VIP tier up!" + tier name (tier colour) + max bet
		float ta = (float) plan.textAlpha(ms);
		if (ta > 0.01f) {
			int maxW = Math.max(60, w - 32);
			int ink = CasinoPalette.withAlpha(CasinoPalette.INK, ta);
			int y = cy + BADGE + 8;
			Component head = Component.translatable("gui.burmaldaholic.vip.tier_up");
			int hs = FxText.fitScale(font, head, 2, maxW);
			FxText.outlinedCentered(g, font, head, cx, y, hs, CasinoPalette.withAlpha(CasinoPalette.GOLD, ta), ink);
			y += font.lineHeight * hs + 4;
			Component name = VipTiers.name(plan.tier());
			int ns = FxText.fitScale(font, name, 2, maxW);
			int nameColor = netherite ? 0xFFFF7A3C : tier == VipRules.BRONZE ? CasinoPalette.VIP[0] : CasinoPalette.VIP[VipTiers.clamp(plan.tier())];
			FxText.outlinedCentered(g, font, name, cx, y, ns, CasinoPalette.withAlpha(nameColor, ta), ink);
			y += font.lineHeight * ns + 4;
			Component bet = Component.translatable("gui.burmaldaholic.vip.max_bet", Texts.chips(VipTiers.maxBet(plan.tier())));
			FxText.outlinedCentered(g, font, bet, cx, y, 1f, CasinoPalette.withAlpha(CasinoPalette.BONE, ta), ink);
		}
	}

	private static void sounds(double ms) {
		int due = plan.arpeggioNotes(ms);
		while (notesPlayed < due) {
			float pitch = TierUpPlan.ARPEGGIO[notesPlayed];
			FxSounds.playRaw(Identifier.withDefaultNamespace("block.note_block.bell"), SoundSource.PLAYERS, 0.7f, pitch, 0, null);
			notesPlayed++;
		}
	}

	private static void finish() {
		if (!toasted && plan != null) {
			toasted = true;
			CasinoToast.show(Component.translatable("toast.burmaldaholic.vip.title"),
				Component.translatable("toast.burmaldaholic.vip.body", VipTiers.name(plan.tier()), Texts.chips(VipTiers.maxBet(plan.tier()))),
				ServerFx.ToastIcon.VIP);
		}
		plan = null;
		EMBERS.clear();
	}
}
