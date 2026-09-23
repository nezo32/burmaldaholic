package dev.nezo.burmaldaholic.client.hud;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.CoreClientModule;
import dev.nezo.burmaldaholic.core.chips.Chips;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.CoreConfig;
import dev.nezo.burmaldaholic.core.network.PlayerStatusPayload;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The casino HUD panel (UI.md §1): balance (gold during Golden Hour, floating +/− on change),
 * Lucky/Unlucky streak, VIP badge, loan timer, Golden Hour timer. Hidden when casino mode is off,
 * with F1, and dimmed while chat is open. Extension point: {@link #register} (via
 * {@code ClientModuleContext#hudSegment}).
 */
public final class CasinoHud {
	private record Registered(Identifier id, int order, HudSegment segment) {}

	private static final List<Registered> SEGMENTS = new CopyOnWriteArrayList<>();
	private static final int MARGIN = 4;
	private static final int LINE = 10;

	private CasinoHud() {}

	public static void register(Identifier id, int order, HudSegment segment) {
		SEGMENTS.removeIf(r -> r.id.equals(id));
		SEGMENTS.add(new Registered(id, order, segment));
		SEGMENTS.sort(Comparator.comparingInt(Registered::order));
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Burmaldaholic.id("hud"), CasinoHud::extract);
		register(Burmaldaholic.id("balance"), 0, CasinoHud::balance);
		register(Burmaldaholic.id("streak_vip"), 100, CasinoHud::streakAndVip);
		register(Burmaldaholic.id("loan"), 200, CasinoHud::loan);
		register(Burmaldaholic.id("golden_hour"), 300, CasinoHud::goldenHour);
	}

	// ---- core segments ------------------------------------------------------------------------

	private static void balance(HudContext ctx, java.util.function.Consumer<HudLine> out) {
		PlayerStatusPayload s = ctx.status();
		boolean golden = s.goldenHourTicks() > 0;
		Component text = Component.translatable("hud.burmaldaholic.balance", Texts.number(s.balance()));
		Component delta = null;
		if (ClientCasinoState.lastDelta() != 0 && ClientCasinoState.ticksSinceDelta() < 30) {
			long d = ClientCasinoState.lastDelta();
			delta = Texts.raw((d > 0 ? "+" : "−") + Numbers.format(Math.abs(d))).withStyle(d > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
		}
		out.accept(new HudLine(new ItemStack(Chips.item(100)), text, delta, golden ? 0xFFFFAA00 : 0xFFFFFFFF, false));
	}

	private static void streakAndVip(HudContext ctx, java.util.function.Consumer<HudLine> out) {
		PlayerStatusPayload s = ctx.status();
		Component streak = null;
		int color = 0xFFFFFFFF;
		if (s.streak() > 0) {
			streak = Component.translatable("hud.burmaldaholic.streak.lucky", Texts.number(s.streak()));
			color = 0xFFFFAA00;
		} else if (s.streak() < 0) {
			streak = Component.translatable("hud.burmaldaholic.streak.unlucky", Texts.number(-s.streak()));
			color = 0xFF8FA8C8;
		}
		Component vip = Component.translatable("hud.burmaldaholic.vip", VipTiers.name(s.vipTier()));
		boolean pulse = Math.abs(s.streak()) >= 7;
		out.accept(new HudLine(null, streak == null ? Component.empty() : streak, vip, color, pulse));
	}

	private static void loan(HudContext ctx, java.util.function.Consumer<HudLine> out) {
		PlayerStatusPayload s = ctx.status();
		if (s.debt() <= 0) {
			return;
		}
		if (s.inDefault()) {
			out.accept(new HudLine(null, Component.translatable("hud.burmaldaholic.loan_default", Texts.number(s.debt())), null, 0xFFFF5555, true));
		} else {
			long ticks = Math.max(0, s.debtTicks());
			Component time = Component.translatable("hud.burmaldaholic.time.dhm", Texts.number(ticks / 24000), Texts.raw(Numbers.hoursMinutes(ticks % 24000)));
			out.accept(HudLine.of(Component.translatable("hud.burmaldaholic.loan", time, Texts.number(s.debt()))));
		}
	}

	private static void goldenHour(HudContext ctx, java.util.function.Consumer<HudLine> out) {
		long ticks = ctx.status().goldenHourTicks();
		if (ticks > 0) {
			out.accept(HudLine.of(Component.translatable("hud.burmaldaholic.golden_hour", Texts.raw(Numbers.minutesSeconds(ticks))), 0xFFFFAA00));
		}
	}

	// ---- rendering ----------------------------------------------------------------------------

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !CoreClientModule.casinoEnabled() || !ClientCasinoState.hasStatus() || mc.gui.hud.isHidden()) {
			return;
		}
		CoreConfig.Hud hud = CasinoConfig.core().hud;
		if (!hud.enabled) {
			return;
		}
		HudContext ctx = new HudContext(mc, ClientCasinoState.status(), ClientCasinoState.clientTicks(), delta.getGameTimeDeltaPartialTick(false));
		List<HudLine> lines = new ArrayList<>();
		for (Registered r : SEGMENTS) {
			try {
				r.segment.addLines(ctx, lines::add);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("HUD segment {} failed", r.id, e);
				SEGMENTS.remove(r);
			}
		}
		if (lines.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int width = 0;
		for (HudLine l : lines) {
			int w = (l.icon() != null ? 10 : 0) + font.width(l.left()) + (l.right() != null ? 8 + font.width(l.right()) : 0);
			width = Math.max(width, w);
		}
		width += 8;
		int height = lines.size() * LINE + 6;
		CoreConfig.HudPosition pos = hud.position;
		int x = pos.right() ? graphics.guiWidth() - width - MARGIN : MARGIN;
		int y = pos.bottom() ? graphics.guiHeight() - height - MARGIN - 40 : MARGIN;
		if (pos == CoreConfig.HudPosition.TOP_RIGHT && !mc.player.getActiveEffects().isEmpty()) {
			y += 26 * (int) mc.player.getActiveEffects().stream().map(e -> e.getEffect().value().isBeneficial()).distinct().count();
		}
		boolean dim = mc.gui.screen() instanceof ChatScreen;
		int alpha = dim ? 0x33 : 0x66;
		graphics.fill(x, y, x + width, y + height, alpha << 24);
		int textAlpha = dim ? 0x80 : 0xFF;
		int ty = y + 4;
		for (HudLine line : lines) {
			if (line.pulse() && (ctx.clientTicks() / 10) % 2 == 1) {
				ty += LINE;
				continue;
			}
			int tx = x + 4;
			if (line.icon() != null) {
				graphics.pose().pushMatrix();
				graphics.pose().translate(tx, ty - 1);
				graphics.pose().scale(0.5f, 0.5f);
				graphics.item(line.icon(), 0, 0);
				graphics.pose().popMatrix();
				tx += 10;
			}
			int color = (textAlpha << 24) | (line.color() & 0xFFFFFF);
			graphics.text(font, line.left(), tx, ty, color, true);
			if (line.right() != null) {
				graphics.text(font, line.right(), x + width - 4 - font.width(line.right()), ty, color, true);
			}
			ty += LINE;
		}
	}
}
