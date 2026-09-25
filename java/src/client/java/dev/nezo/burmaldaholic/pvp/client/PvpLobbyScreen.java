package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.client.pvp.kit.Faces;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.client.pvp.kit.KitButton;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpDraw;
import dev.nezo.burmaldaholic.client.pvp.kit.PvpSeat;
import dev.nezo.burmaldaholic.client.pvp.kit.Scene;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.pvp.logic.PvpMotion;
import dev.nezo.burmaldaholic.pvp.logic.PvpText;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * PvP lobby (PVP.md §3.11.3; visual/extras.md §7.2 "Lobby", extras-pvp.md §9.3): seat rows on the arena scene — the
 * host's {@code lobby_row_host} with the crown, heads, names, badges (bot difficulty, head-to-head record, ALL-IN),
 * dashed empty seats breathing (a waiting bot in each seat a MIXED lobby will fill) — and on the right the timer ring
 * (gold, red for the last 5 s), entry, pot, house cut and players. New rows slide in from the right with
 * {@code chip_place}; a full table chimes. *Start now* breathes once there are two players.
 */
final class PvpLobbyScreen extends PvpScreen {
	private static final int ROW_X = 16;
	private static final int ROW_Y = 30;
	private static final int ROW_W = 240;
	private static final int ROW_H = 22;
	private static final int COL_X = 268;
	private final Map<String, Long> arrived = new HashMap<>();
	private boolean fullChimed;

	PvpLobbyScreen(JsonObject state) {
		super(Component.translatable("gui.burmaldaholic.pvp.lobby.title", game(state)), state);
		noteArrivals(state, true);
	}

	@Override
	protected void onState(JsonObject oldState, JsonObject newState) {
		noteArrivals(newState, false);
	}

	private void noteArrivals(JsonObject s, boolean opening) {
		long now = Util.getMillis();
		for (PvpSeat seat : PvpSeat.all(s)) {
			if (!arrived.containsKey(seat.key())) {
				arrived.put(seat.key(), opening ? 0L : now);
				if (!opening) Kit.vanilla("block.stone_button.click_on", 0.6f, 1.4f); // chip_place stand-in
			}
		}
		List<PvpSeat> seats = PvpSeat.all(s);
		long max = Math.max(seats.size(), num(s, "max", seats.size()));
		if (!opening && !fullChimed && seats.size() >= max && max >= 2) {
			fullChimed = true;
			Kit.vanilla("block.note_block.chime", 1f, 1.5f);
		}
	}

	long ticksLeft() {
		long left = num(state(), "ticksLeft", -1);
		return left < 0 ? -1 : Math.max(0, left - (ticks - stateTick));
	}

	@Override
	protected @Nullable Component titleRight() {
		JsonObject s = state();
		return Component.translatable("gui.burmaldaholic.pvp.lobby.players", Texts.number(participants(s).size()),
			Texts.number(Math.max(participants(s).size(), num(s, "max", 2))));
	}

	@Override
	protected void layout() {
		JsonObject s = state();
		boolean host = num(s, "host", -2) == num(s, "you", -1) && num(s, "you", -1) >= 0;
		int players = participants(s).size();
		boolean mixed = "mixed".equals(str(s, "policy", ""));
		int x = 16;
		if (host) {
			Component start = Component.translatable(mixed && num(s, "botsToFill", 0) > 0 ? "gui.burmaldaholic.pvp.bots.start_with_bots"
				: "gui.burmaldaholic.pvp.lobby.start");
			int w = labelWidth(start, 90);
			button(x, 206, w, start, KitButton.Style.PRIMARY, b -> send("start", "", 0)).active(players >= 2 || mixed).breathe(players >= 2);
			x += w + 4;
		}
		if (num(s, "you", -1) >= 0) {
			Component leave = Component.translatable("gui.burmaldaholic.pvp.lobby.leave");
			int w = labelWidth(leave, 70);
			button(x, 206, w, leave, KitButton.Style.SECONDARY, b -> {
				send("leave", "", 0);
				onClose();
			});
			x += w + 4;
			Component taunt = Component.translatable("gui.burmaldaholic.pvp.taunt.button");
			int tw = labelWidth(taunt, 60);
			button(x, 206, tw, taunt, KitButton.Style.SECONDARY, b -> PvpScreens.openTaunts(this));
		}
		Component close = Component.translatable("gui.burmaldaholic.common.close");
		int cw = labelWidth(close, 60);
		button(Scene.W - 16 - cw, 206, cw, close, KitButton.Style.SECONDARY, b -> onClose());
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		JsonObject s = state();
		List<PvpSeat> seats = PvpSeat.all(s);
		long max = Math.max(seats.size(), num(s, "max", seats.size()));
		boolean mixed = "mixed".equals(str(s, "policy", ""));
		long toFill = num(s, "botsToFill", 0);
		long now = Util.getMillis();
		boolean still = Kit.reduceMotion();
		int rows = (int) Math.min(7, max);
		int pitch = rows > 6 ? 22 : 25;
		for (int seat = 0; seat < rows; seat++) {
			int y = top + ROW_Y + seat * pitch;
			int x = left + ROW_X;
			if (seat >= seats.size()) {
				double alpha = PvpMotion.breathe(now, still);
				Kit.sprite(g, Kit.pvp("seat_empty"), x, y, ROW_W, ROW_H, Kit.fade(alpha));
				boolean botSeat = mixed && seat - seats.size() < toFill;
				if (botSeat) Kit.region(g, PvpDraw.BADGES, 96, 16, 3 * 16, 0, 16, 16, x + 6, y + 3, 16, 16, Kit.fade(alpha));
				Kit.text(g, font, Component.translatable("gui.burmaldaholic.common.seat_empty"), x + 28, y + 7, Kit.alpha(MUTED, alpha));
				continue;
			}
			PvpSeat p = seats.get(seat);
			double slide = PvpMotion.rowSlide(now - arrived.getOrDefault(p.key(), 0L), still);
			x += (int) Math.round(slide * 140);
			Kit.sprite(g, Kit.pvp(p.host() ? "lobby_row_host" : "lobby_row"), x, y, ROW_W, ROW_H);
			Faces.draw(g, p.key(), p.bot(), p.name(), x + 5, y + 3, 16);
			if (p.host()) Kit.sprite(g, Kit.pvp("crown"), x + 5, y - 7, 16, 12);
			int bx = x + ROW_W - 6;
			if (p.bot() && !p.level().isEmpty()) {
				bx -= 22;
				Kit.sprite(g, Kit.pvp("bot_" + p.level()), bx, y + 5, 22, 11);
			}
			if (p.hasRecord()) {
				Component rec = Component.translatable("gui.burmaldaholic.pvp.record_chip", Texts.number(p.wins()), Texts.number(p.losses()));
				int rw = font.width(rec) + 6;
				bx -= rw + 3;
				String chip = switch (PvpMotion.recordChip(p.wins(), p.losses())) {
					case 0 -> "record_chip_lead";
					case 1 -> "record_chip_trail";
					default -> "record_chip_even";
				};
				Kit.sprite(g, Kit.pvp(chip), bx, y + 6, rw, 10);
				g.text(font, rec, bx + 3, y + 7, Kit.BONE, false);
			}
			if (p.allIn()) {
				Component tag = Component.translatable("gui.burmaldaholic.pvp.all_in_tag");
				int tw = Math.min(font.width(tag) + 8, 52);
				bx -= tw + 3;
				double glow = still ? 1 : 0.75 + 0.25 * Math.sin(now / 1000.0 * Math.PI * 2);
				Kit.sprite(g, Kit.pvp("all_in"), bx, y + 5, tw, 12, Kit.shade(glow));
				Kit.fit(g, font, tag, bx + 4, y + 7, tw - 6, Kit.BONE, false);
			}
			if (!bool(s, "equalStakes")) {
				Component stake = Texts.number(p.stake());
				bx -= font.width(stake) + 4;
				g.text(font, stake, bx, y + 7, Kit.GOLD, true);
			}
			Kit.fit(g, font, p.you() ? Component.translatable("gui.burmaldaholic.common.you") : p.displayName(), x + 26, y + 7,
				Math.max(20, bx - (x + 26) - 4), p.you() ? Kit.GOLD : Kit.BONE, true);
		}
		// right column: timer ring, money, players
		int cx = left + COL_X + 58;
		Scene.inset(g, left + COL_X, top + 28, 116, 172);
		long tl = ticksLeft();
		if (tl >= 0) {
			timerTotal = Math.max(timerTotal, tl);
			long total = timerTotal;
			Kit.ring(g, cx, top + 64, 26, 1 - tl / (double) Math.max(1, total), PvpMotion.timerColor(tl), 0x60180A28);
			Component secs = Texts.number(PvpText.seconds(tl));
			Kit.big(g, font, secs, cx, top + 57, 2, PvpMotion.timerColor(tl), Kit.INK);
			if (tl <= 100 && tl > 0 && (ticks - stateTick) % 20 == 0 && lastTickSound != ticks) {
				lastTickSound = ticks;
				Kit.vanilla("block.note_block.hat", 0.5f, 1.2f);
			}
		}
		int y = top + 98;
		y = Kit.wrap(g, font, Component.translatable("gui.burmaldaholic.pvp.lobby.entry", Texts.chips(num(s, "entry", 0))), left + COL_X + 6, y, 106,
			TEXT);
		y = Kit.wrap(g, font, Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.chips(num(s, "pot", 0))), left + COL_X + 6, y, 106, GOLD);
		y = Kit.wrap(g, font, Component.translatable("gui.burmaldaholic.pvp.lobby.rake", percent(s)), left + COL_X + 6, y, 106, MUTED) + 4;
		if (toFill > 0) {
			y = Kit.wrap(g, font, Component.translatable("gui.burmaldaholic.pvp.bots.will_fill", Texts.plural("unit.burmaldaholic.bot", toFill)),
				left + COL_X + 6, y, 106, MUTED);
		} else if (seats.size() < 2) {
			y = Kit.wrap(g, font, Component.translatable("gui.burmaldaholic.pvp.lobby.need_more"), left + COL_X + 6, y, 106, MUTED);
		} else if (seats.size() >= max) {
			y = Kit.wrap(g, font, Component.translatable("gui.burmaldaholic.anim.lobby.full"), left + COL_X + 6, y, 106, Kit.BONUS);
		}
		if (bool(s, "grudge")) {
			Kit.wrap(g, font, Component.translatable("gui.burmaldaholic.pvp.grudge.title"), left + COL_X + 6, y + 2, 106, RED);
		}
	}

	private int lastTickSound = -1;
	private long timerTotal = 1;
}
