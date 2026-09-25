package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.table.fx.TableButton;
import dev.nezo.burmaldaholic.client.table.fx.TableKit;
import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.table.fx.TableChrome;
import dev.nezo.burmaldaholic.client.table.fx.TableGfx;
import dev.nezo.burmaldaholic.client.table.fx.TableTheme;
import dev.nezo.burmaldaholic.core.text.Texts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * PvP Dice Duel invitation (UI.md §9; animation/tables.md §3.3 "Invite screen"): a themed plaque with the dice cup
 * (rattling once per second, static with reduced motion) inside the countdown ring (red and pulsing for the last 5 s),
 * "Alex challenges you to a Dice Duel for 200 chips", the stake as a chip stack, [Accept] with a 1 px green glow and
 * [Decline]. Only shown when the target has no other screen open; closes itself when the challenge times out.
 */
final class DuelInviteScreen extends ExtrasScreen {
	private static final int PW = 220;
	private static final int PH = 96;
	private int px;
	private int py;
	private final long openedAt = Util.getMillis();

	DuelInviteScreen(CompoundTag state) {
		super("dice", Component.translatable("gui.burmaldaholic.extras.dice.title"), state, PW, PH);
	}

	private Component text() {
		CompoundTag s = state();
		return Component.translatable("gui.burmaldaholic.extras.dice.invite", Texts.raw(s.getStringOr("name", "")), Texts.chips(s.getLongOr("stake", 0)));
	}

	@Override
	protected void layout() {
		px = (width - PW) / 2;
		py = (height - PH) / 2;
		int id = state().getIntOr("id", -1);
		TableTheme t = TableTheme.VILLAGE;
		CasinoButton accept = addRenderableWidget(TableKit.button(px + 54, py + PH - 28, 70, Component.translatable("gui.burmaldaholic.extras.dice.accept"), t,
			b -> answer("accept", id)).style(CasinoButton.Style.PRIMARY));
		addRenderableWidget(TableKit.button(accept.getX() + accept.getWidth() + 8, py + PH - 28, 70, Component.translatable("gui.burmaldaholic.extras.dice.decline"),
			t, b -> answer("decline", id)));
	}

	private void answer(String action, int id) {
		CompoundTag args = new CompoundTag();
		args.putInt("id", id);
		send(action, args);
		onClose();
	}

	@Override
	public void tick() {
		super.tick();
		if (ticks >= state().getLongOr("ticks", 600)) {
			onClose();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractContent(g, mouseX, mouseY, a);
		for (var child : children()) {
			if (child instanceof Renderable r) {
				r.extractRenderState(g, mouseX, mouseY, a);
			}
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		TableGfx.blit(g, "core/banner_village", px, py, PW, PH);
		long total = Math.max(1, state().getLongOr("ticks", 600));
		double left = Math.max(0, total - ticks - a) / 20.0;
		// the countdown ring around the cup (12 → 0 segments, red for the last 5 s)
		int cx = px + 14;
		int cy = py + 14;
		boolean urgent = left <= 5;
		int seg = (int) Math.ceil(12 * left / (total / 20.0));
		int frame = 12 - Math.max(0, Math.min(12, seg));
		int pulse = urgent && !FxSettings.reduceMotion() && (Util.getMillis() / 500) % 2 == 0 ? 1 : 0;
		// the ring (16² sprite) drawn ×1 behind a 40 px cup: four quarter rings would need scaling, so the ring sits beside
		TableGfx.frame(g, urgent ? "core/timer_urgent" : "core/timer", 16, 16, 13, frame, cx + 30 - pulse, cy + 38 - pulse, 0xFFFFFFFF);
		long since = Util.getMillis() - openedAt;
		int cupFrame = FxSettings.reduceMotion() ? 0 : (since % 1000) < 240 ? 1 + (int) ((since % 1000) / 120) : 0;
		TableGfx.frame(g, "extras/dice_duel_cup", 40, 40, 6, cupFrame, cx - 2, cy - 4, 0xFFFFFFFF);
		// the invitation text, wrapped right of the cup
		int tx = px + 58;
		int ty = py + 14;
		for (FormattedCharSequence seq : font.split(text(), PW - 70)) {
			g.text(font, seq, tx, ty, TableChrome.BONE, true);
			ty += 10;
		}
		long secs = (long) Math.ceil(left);
		Component timer = Component.translatable("gui.burmaldaholic.common.timer", Texts.plural("unit.burmaldaholic.second", secs));
		g.text(font, TableChrome.fit(font, timer, PW - 70), tx, Math.max(ty + 2, py + 44), urgent ? TableChrome.RED : TableChrome.MUTED, true);
		// the stake as a chip stack
		TableChrome.stack(g, state().getLongOr("stake", 0), px + 30, py + PH - 12, 0, 0xFFFFFFFF);
	}
}
