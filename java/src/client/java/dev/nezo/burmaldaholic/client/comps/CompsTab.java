package dev.nezo.burmaldaholic.client.comps;

import dev.nezo.burmaldaholic.client.cashier.CounterShell;
import dev.nezo.burmaldaholic.client.cashier.CounterTab;
import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.ui.v2.V2Button;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.client.ui.v2.V2Sprites;
import dev.nezo.burmaldaholic.comps.logic.CompsLayout;
import dev.nezo.burmaldaholic.comps.net.CompsActionPayload;
import dev.nezo.burmaldaholic.comps.net.CompsSyncPayload;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.HitMap;
import dev.nezo.burmaldaholic.core.ui.v2.Press;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The Comps tab of the counter (EC-SPEC §4.4). Left: points, rate, today's theoretical loss, the cashback estimate and
 * the daily card (Claim or the reason it is locked); right: the redemption rows. Targets: {@code comps.claim},
 * {@code comps.redeem.<id>}. Data: {@link CompsSyncPayload} (asked for when the tab opens).
 */
public final class CompsTab implements CounterTab {
	public static final String ID = "comps";
	public static final String CLAIM = "comps.claim";
	public static final String REDEEM = "comps.redeem.";

	private final CounterShell shell;
	private CompsLayout.Layout layout;

	public CompsTab(CounterShell shell) {
		this.shell = shell;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("gui.burmaldaholic.cashier.tab.comps");
	}

	static CompsSyncPayload data() {
		return CompsClient.last();
	}

	@Override
	public void opened() {
		CompsClient.send(new CompsActionPayload(CompsActionPayload.SYNC, "", 0));
	}

	@Override
	public void layout(Box body, HitMap hits) {
		CompsSyncPayload d = data();
		layout = CompsLayout.compute(body, d.rewards().size());
		hits.add(CLAIM, layout.claim(), "CLAIMABLE".equals(d.daily()));
		for (int i = 0; i < d.rewards().size(); i++) {
			CompsSyncPayload.RewardView r = d.rewards().get(i);
			hits.add(REDEEM + r.id(), layout.redeem().get(i), r.lock().isEmpty());
		}
	}

	@Override
	public boolean onTarget(String id, boolean enabled, Press press, int mods) {
		if (!enabled || !press.left()) {
			return false;
		}
		if (CLAIM.equals(id)) {
			CompsClient.send(new CompsActionPayload(CompsActionPayload.CLAIM_DAILY, "", 1));
			return true;
		}
		if (id.startsWith(REDEEM)) {
			CompsClient.send(new CompsActionPayload(CompsActionPayload.REDEEM, id.substring(REDEEM.length()), 1));
			return true;
		}
		return false;
	}

	/** Tenths of a percent as text ({@code 150} → "15", {@code 125} → "12.5"). */
	static MutableComponent percent(long tenths) {
		return tenths % 10 == 0 ? Texts.number(tenths / 10) : Texts.raw((tenths / 10) + "." + Math.abs(tenths % 10)); // literal-ok: decimal percent
	}

	static Component dailyLine(CompsSyncPayload d) {
		return switch (d.daily()) {
			case "CLAIMABLE" -> Component.translatable("gui.burmaldaholic.comps.daily.claim", Texts.chips(d.dailyAmount()));
			case "CLAIMED" -> Component.translatable("gui.burmaldaholic.comps.daily.wait",
				Texts.plural("unit.burmaldaholic.minute", Math.max(1, (d.ticksToNextDay() + 1199) / 1200)));
			case "LOCKED_ACTIVE" -> Component.translatable("gui.burmaldaholic.comps.daily.locked_active",
				Texts.plural("unit.burmaldaholic.minute", Math.max(1, (d.minActiveTicks() - d.activeTicks() + 1199) / 1200)));
			case "LOCKED_IP" -> Component.translatable("gui.burmaldaholic.comps.daily.locked_ip");
			default -> Component.translatable("gui.burmaldaholic.comps.daily.locked_wager", Texts.chips(d.minWager()));
		};
	}

	static Component rowName(CompsSyncPayload.RewardView r) {
		return Component.translatable("gui.burmaldaholic.comps.reward." + r.id());
	}

	static Component costLine(CompsSyncPayload.RewardView r) {
		Component cost = r.cost() == 0 ? Component.translatable("gui.burmaldaholic.comps.free")
			: Component.translatable("gui.burmaldaholic.comps.cost", Texts.number(r.cost()));
		if (r.limit() > 0) {
			return Component.empty().append(cost).append(" · ") // literal-ok: separator
				.append(Component.translatable("gui.burmaldaholic.comps.limit", Texts.number(r.used()), Texts.number(r.limit())));
		}
		return cost;
	}

	static Component lockLine(CompsSyncPayload.RewardView r) {
		if ("tier".equals(r.lock())) {
			return VipTiers.name(r.minTier());
		}
		return Component.translatable("gui.burmaldaholic.comps.lock." + r.lock());
	}

	@Override
	public void render(GuiGraphicsExtractor g, Box body, int mouseX, int mouseY, float partial) {
		if (layout == null) {
			return;
		}
		var font = shell.font();
		CompsSyncPayload d = data();
		List<Box> lines = layout.lines();
		V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.comps.points", Texts.number(d.pointsMilli() / 1000)), lines.get(0), 0, 1,
			CasinoPalette.V2_GOLD, true);
		V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.comps.rate", percent(d.rateTenths())), lines.get(1), 0, 1, CasinoPalette.V2_MUTED, false);
		V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.comps.theo_today", Texts.chips(Math.round(d.theoTodayMilli() / 1000.0))),
			lines.get(2), 0, 1, CasinoPalette.V2_TEXT, false);
		V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.comps.cashback_estimate", Texts.chips(d.cbEstimate())), lines.get(3), 0, 1,
			CasinoPalette.V2_TEXT, false);

		Box card = layout.card();
		V2Draw.fill(g, card, CasinoPalette.V2_PANEL_HI);
		V2Draw.outline(g, card, CasinoPalette.V2_EDGE);
		V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.comps.daily.title"), layout.cardTitle(), 0, 1, CasinoPalette.V2_GOLD, true);
		boolean claimable = "CLAIMABLE".equals(d.daily());
		if (!claimable) {
			V2Draw.left(g, font, dailyLine(d), layout.cardText(), 0, 1, CasinoPalette.V2_MUTED, false);
		}
		V2Button.draw(g, font, layout.claim(), claimable ? dailyLine(d) : Component.translatable("gui.burmaldaholic.comps.daily.title"), null,
			V2Sprites.ButtonStyle.PRIMARY, claimable, layout.claim().contains(mouseX, mouseY), false, false);

		V2Draw.left(g, font, Component.translatable("gui.burmaldaholic.comps.rewards"), layout.header(), 0, 1, CasinoPalette.V2_GOLD, true);
		for (int i = 0; i < d.rewards().size() && i < layout.rows().size(); i++) {
			CompsSyncPayload.RewardView r = d.rewards().get(i);
			Box row = layout.rows().get(i);
			if (row.contains(mouseX, mouseY)) {
				V2Draw.fill(g, row, CasinoPalette.V2_PANEL_HI);
			}
			boolean ok = r.lock().isEmpty();
			V2Draw.left(g, font, rowName(r), layout.names().get(i), 0, 1, ok ? CasinoPalette.V2_TEXT : CasinoPalette.V2_MUTED, false);
			V2Draw.left(g, font, ok || "points".equals(r.lock()) ? costLine(r) : lockLine(r), layout.costs().get(i), 0, 1,
				ok ? CasinoPalette.V2_GOLD : CasinoPalette.V2_LOSE, false);
			Box btn = layout.redeem().get(i);
			V2Button.draw(g, font, btn, Component.translatable("gui.burmaldaholic.comps.redeem"), null, V2Sprites.ButtonStyle.NORMAL, ok,
				btn.contains(mouseX, mouseY), false, false);
		}
	}
}
