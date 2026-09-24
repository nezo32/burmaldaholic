package dev.nezo.burmaldaholic.client.hud;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.ClientCasinoState;
import dev.nezo.burmaldaholic.client.CoreClientModule;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.StyledToast;
import dev.nezo.burmaldaholic.client.ui.UiSprites;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.CoreConfig;
import dev.nezo.burmaldaholic.core.fx.BigWinBroadcast;
import dev.nezo.burmaldaholic.core.network.PlayerStatusPayload;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.BalanceTicker;
import dev.nezo.burmaldaholic.core.ui.DeltaFloaters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The casino HUD (UI.md §1, global.md §4.1.1, docs/design/visual/extras.md §9; lane J-L2 task J6): the <b>chip
 * counter</b> pill ({@code core/hud/chip_counter}, golden during Golden Hour) with the animated chip icon and the
 * balance ticker, <b>floating delta pills</b> right of it, then the rows of every segment (streak with the animated
 * flame / cloud, VIP, loan with the bell that swings in default, Golden Hour with the turning sun). No panel: the pill
 * and outlined text sit on the world (the mockup {@code extras_hud.png}). Hidden when casino mode is off, with F1;
 * dimmed while chat is open. Extension point: {@link #register} (via {@code ClientModuleContext#hudSegment}).
 *
 * <p>Outcome fidelity (F6): the counter follows {@link ClientCasinoState#shownBalance()}, which a presentation holds
 * until its reveal ({@link ClientCasinoState#holdBalanceDelta}); the ticker always ends on the shown balance.
 */
public final class CasinoHud {
	private record Registered(Identifier id, int order, HudSegment segment) {}

	private static final List<Registered> SEGMENTS = new CopyOnWriteArrayList<>();
	private static final int MARGIN = 4;
	private static final int LINE = 10;
	/** Pseudo-sprites: glyph icons drawn from the E1 font (U+E176 bell, U+E175 sun). */
	static final Identifier GLYPH_BELL = Burmaldaholic.id("hud_glyph/bell");
	static final Identifier GLYPH_SUN = Burmaldaholic.id("hud_glyph/sun");
	private static final Identifier BALANCE_ID = Burmaldaholic.id("balance");

	private static final BalanceTicker TICKER = new BalanceTicker();
	private static final DeltaFloaters FLOATERS = new DeltaFloaters();
	private static long seenDelta = Long.MIN_VALUE;
	private static long tickFrom;
	private static long tickEvery;
	private static int ticksLeft;
	private static boolean wasDefault;
	private static boolean sawStatus;

	private CasinoHud() {}

	public static void register(Identifier id, int order, HudSegment segment) {
		SEGMENTS.removeIf(r -> r.id.equals(id));
		SEGMENTS.add(new Registered(id, order, segment));
		SEGMENTS.sort(Comparator.comparingInt(Registered::order));
	}

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Burmaldaholic.id("hud"), CasinoHud::extract);
		register(BALANCE_ID, 0, (ctx, out) -> {}); // drawn as the chip counter; kept so others can order around it
		register(Burmaldaholic.id("streak_vip"), 100, CasinoHud::streakAndVip);
		register(Burmaldaholic.id("loan"), 200, CasinoHud::loan);
		register(Burmaldaholic.id("golden_hour"), 300, CasinoHud::goldenHour);
		// J6 holdBalanceDelta: presentations (fx payload holdMs, the slot screen) keep the balance delta until the reveal
		dev.nezo.burmaldaholic.client.fx.ClientFx.balanceHold = ClientCasinoState::holdBalanceDelta;
	}

	// ---- core segments ------------------------------------------------------------------------

	private static void streakAndVip(HudContext ctx, java.util.function.Consumer<HudLine> out) {
		PlayerStatusPayload s = ctx.status();
		Component vip = VipTiers.name(s.vipTier()).withColor(CasinoPalette.VIP[VipTiers.clamp(s.vipTier())] & 0xFFFFFF);
		if (s.streak() == 0) {
			out.accept(new HudLine(null, vip, null, CasinoPalette.BONE, false));
			return;
		}
		boolean lucky = s.streak() > 0;
		int n = Math.abs(s.streak());
		// |S| ≥ 7: the flame / cloud animates (3 frames at 8 fps); flashes off or reduce motion: static
		int frame = n >= 7 && FxSettings.flashes() && !FxSettings.reduceMotion() ? (int) (Util.getMillis() / 125 % 3) : 0;
		Component text = Component.translatable(lucky ? "hud.burmaldaholic.streak.lucky" : "hud.burmaldaholic.streak.unlucky", Texts.number(n));
		out.accept(new HudLine(null, text, vip, lucky ? CasinoPalette.BONUS : CasinoPalette.COOL, n >= 10,
			lucky ? UiSprites.flame(frame) : UiSprites.cloud(frame)));
	}

	private static void loan(HudContext ctx, java.util.function.Consumer<HudLine> out) {
		PlayerStatusPayload s = ctx.status();
		if (s.debt() <= 0) {
			return;
		}
		if (s.inDefault()) {
			out.accept(new HudLine(null, Component.translatable("hud.burmaldaholic.loan_default", Texts.number(s.debt())), null, CasinoPalette.CHIP_RED_LIGHT,
				true, GLYPH_BELL));
		} else {
			long ticks = Math.max(0, s.debtTicks());
			Component time = Component.translatable("hud.burmaldaholic.time.dhm", Texts.number(ticks / 24000), Texts.raw(Numbers.hoursMinutes(ticks % 24000)));
			out.accept(new HudLine(null, Component.translatable("hud.burmaldaholic.loan", time, Texts.number(s.debt())), null, CasinoPalette.BONE, false,
				GLYPH_BELL));
		}
	}

	private static void goldenHour(HudContext ctx, java.util.function.Consumer<HudLine> out) {
		long ticks = ctx.status().goldenHourTicks();
		if (ticks > 0) {
			int color = ticks <= 600 ? CasinoPalette.CHIP_RED_LIGHT : CasinoPalette.GOLD;
			out.accept(new HudLine(null, Component.translatable("hud.burmaldaholic.golden_hour", Texts.raw(Numbers.minutesSeconds(ticks))), null, color,
				false, GLYPH_SUN));
		}
	}

	// ---- state --------------------------------------------------------------------------------

	/** Feeds the ticker / floaters / loan toast from the synced state (every frame; cheap). */
	private static void update(Minecraft mc, PlayerStatusPayload s, long now) {
		boolean reduced = FxSettings.reduceMotion();
		long shown = ClientCasinoState.shownBalance();
		long before = TICKER.target();
		boolean first = !sawStatus;
		TICKER.retarget(shown, now, reduced || first);
		if (!first && shown != before && mc.gui.screen() == null && Math.abs(shown - before) >= 100) {
			// chip_count ticks: at most 6 per tween, volume 0.25 (global.md §4.1.1)
			int dur = BalanceTicker.durationMs(shown - before);
			ticksLeft = 6;
			tickFrom = now;
			tickEvery = Math.max(40, dur / 6);
		}
		if (ticksLeft > 0 && now - tickFrom >= (6 - ticksLeft) * tickEvery) {
			ticksLeft--;
			FxSounds.play("chip_count", 0.25f, 1f + 0.05f * (6 - ticksLeft));
		}
		long stamp = ClientCasinoState.deltaStamp();
		if (stamp != seenDelta) {
			if (!first && seenDelta != Long.MIN_VALUE) FLOATERS.add(ClientCasinoState.lastDelta(), now);
			seenDelta = stamp;
		}
		if (s.inDefault() && !wasDefault && !first) {
			StyledToast.show(StyledToast.Style.LOAN, Component.translatable("toast.burmaldaholic.loan.overdue"),
				Component.translatable("toast.burmaldaholic.loan.owed", Texts.chips(s.debt())), UiSprites.TabIcon.LOAN);
		}
		wasDefault = s.inDefault();
		sawStatus = true;
	}

	/** Forget the per-session animation state (disconnect). */
	public static void reset() {
		sawStatus = false;
		seenDelta = Long.MIN_VALUE;
		wasDefault = false;
		FLOATERS.clear();
		ticksLeft = 0;
	}

	// ---- rendering ----------------------------------------------------------------------------

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !CoreClientModule.casinoEnabled() || !ClientCasinoState.hasStatus()) {
			if (mc.player == null) reset();
			return;
		}
		PlayerStatusPayload status = ClientCasinoState.status();
		long now = Util.getMillis();
		update(mc, status, now);
		if (mc.gui.hud.isHidden()) {
			return;
		}
		CoreConfig.Hud hud = CasinoConfig.core().hud;
		if (!hud.enabled) {
			return;
		}
		HudContext ctx = new HudContext(mc, status, ClientCasinoState.clientTicks(), delta.getGameTimeDeltaPartialTick(false));
		List<HudLine> lines = new ArrayList<>();
		for (Registered r : SEGMENTS) {
			try {
				r.segment.addLines(ctx, lines::add);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("HUD segment {} failed", r.id, e);
				SEGMENTS.remove(r);
			}
		}
		Font font = mc.font;
		boolean dim = mc.gui.screen() instanceof ChatScreen;
		int alpha = dim ? 0x80 : 0xFF;
		boolean golden = status.goldenHourTicks() > 0;
		boolean reduced = FxSettings.reduceMotion();

		// chip counter geometry
		long shown = TICKER.value(now);
		String number = Numbers.format(shown);
		int counterW = font.width(number) + 26;
		int counterH = 16;
		int linesW = 0;
		for (HudLine l : lines) linesW = Math.max(linesW, lineWidth(font, l));
		int blockW = Math.max(counterW, linesW);
		int blockH = counterH + 2 + lines.size() * LINE;
		CoreConfig.HudPosition pos = hud.position;
		boolean right = pos.right();
		int x = right ? graphics.guiWidth() - blockW - MARGIN : MARGIN;
		int y = pos.bottom() ? graphics.guiHeight() - blockH - MARGIN - 40 : MARGIN;
		if (pos == CoreConfig.HudPosition.TOP_RIGHT && !mc.player.getActiveEffects().isEmpty()) {
			y += 26 * (int) mc.player.getActiveEffects().stream().map(e -> e.getEffect().value().isBeneficial()).distinct().count();
		}
		int cx = right ? x + blockW - counterW : x;
		int tint = alpha << 24 | 0xFFFFFF;
		if (!CasinoUi.sprite(graphics, golden ? UiSprites.CHIP_COUNTER_GOLDEN : UiSprites.CHIP_COUNTER, cx, y, counterW, counterH, tint)) {
			graphics.fill(cx, y, cx + counterW, y + counterH, (alpha * 7 / 10) << 24 | 0x140822);
			graphics.outline(cx, y, counterW, counterH, golden ? CasinoPalette.GOLD : CasinoPalette.FRAME);
		}
		CasinoUi.chipIcon(graphics, cx + 3, y + 2);
		int dir = TICKER.direction(now);
		int base = CasinoPalette.GOLD;
		int numColor = dir == 0 ? base : CasinoUi.mix(base, dir > 0 ? CasinoPalette.BONUS : CasinoPalette.CHIP_RED_LIGHT, TICKER.tint(now));
		graphics.text(font, number, cx + 18, y + 4, withAlpha(numColor, alpha), true);

		// floating delta pills beside the counter (they rise and fade; newest at the counter row)
		int n = FLOATERS.size(now);
		for (int i = 0; i < n; i++) {
			long d = FLOATERS.amount(i);
			String label = (d > 0 ? "+" : "−") + Numbers.format(Math.abs(d)); // literal-ok: signed number
			int pw = font.width(label) + 6;
			int px = right ? cx - 2 - pw : cx + counterW + 2;
			int py = y + 3 + FLOATERS.yOffset(i, now, reduced);
			int a = Math.round(FLOATERS.alpha(i, now) * alpha);
			if (a < 8) continue;
			if (!CasinoUi.sprite(graphics, d > 0 ? UiSprites.DELTA_UP : UiSprites.DELTA_DOWN, px, py, pw, 10, a << 24 | 0xFFFFFF)) {
				graphics.fill(px, py, px + pw, py + 10, (a * 3 / 4) << 24 | (d > 0 ? 0x1E6A1E : 0x6A1420));
			}
			graphics.text(font, label, px + 3, py + 1, withAlpha(d > 0 ? 0xFFE8FFD8 : 0xFFFFE0E0, a), false);
		}

		// rows
		int ty = y + counterH + 3;
		for (HudLine line : lines) {
			int w = lineWidth(font, line);
			int lx = right ? x + blockW - w : x + 1;
			int tx = lx;
			if (line.sprite() != null || line.icon() != null) {
				drawIcon(graphics, font, line, lx, ty, alpha, reduced);
				tx += 11;
			}
			graphics.text(font, line.left(), tx, ty, withAlpha(line.color(), alpha), true);
			if (line.right() != null) {
				graphics.text(font, line.right(), tx + font.width(line.left()) + 6, ty, withAlpha(CasinoPalette.BONE_SHADE, alpha), true);
			}
			ty += LINE;
		}
	}

	private static int lineWidth(Font font, HudLine l) {
		return (l.sprite() != null || l.icon() != null ? 11 : 0) + font.width(l.left()) + (l.right() != null ? 6 + font.width(l.right()) : 0) + 1;
	}

	private static void drawIcon(GuiGraphicsExtractor g, Font font, HudLine line, int x, int y, int alpha, boolean reduced) {
		Identifier sprite = line.sprite();
		if (sprite == null) {
			g.pose().pushMatrix();
			g.pose().translate(x, y - 1);
			g.pose().scale(0.5f, 0.5f);
			g.item(line.icon(), 0, 0);
			g.pose().popMatrix();
			return;
		}
		if (sprite.equals(GLYPH_BELL) || sprite.equals(GLYPH_SUN)) {
			boolean bell = sprite.equals(GLYPH_BELL);
			MutableComponent glyph = Texts.raw(bell ? "" : "").withStyle(st -> st.withFont(BigWinBroadcast.GLYPHS)); // literal-ok: glyph
			float angle = 0;
			long t = Util.getMillis();
			if (!reduced && FxSettings.flashes()) {
				// the bell swings ±15° at 1 Hz in default; the sun turns in 45° steps at 4 fps
				angle = bell ? (line.pulse() ? (float) Math.toRadians(15 * Math.sin(t / 1000.0 * Math.PI * 2)) : 0) : (float) Math.toRadians(45 * (t / 250 % 8));
			}
			g.pose().pushMatrix();
			g.pose().translate(x + 4, y + 4);
			g.pose().rotate(angle);
			g.text(font, glyph, -4, -4, withAlpha(bell && line.pulse() ? CasinoPalette.CHIP_RED_LIGHT : 0xFFFFFFFF, alpha), false);
			g.pose().popMatrix();
			return;
		}
		CasinoUi.sprite(g, sprite, x, y, 8, 8, alpha << 24 | 0xFFFFFF);
	}

	private static int withAlpha(int argb, int alpha) {
		int a = ((argb >>> 24) * alpha) / 255;
		return a << 24 | (argb & 0xFFFFFF);
	}
}
