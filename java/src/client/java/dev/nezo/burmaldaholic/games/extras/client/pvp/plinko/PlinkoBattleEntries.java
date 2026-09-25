package dev.nezo.burmaldaholic.games.extras.client.pvp.plinko;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Plinko Battle entries on the Plinko machine screen (PVP.md §7.4): *Start a Plinko Battle* expands the
 * set-up (the machine's own risk toggle and bet selector give risk and entry; here: balls, seats, bot
 * difficulty, table size, *Open lobby*), *Join the battle: 100 · 2/6* per open battle nearby, and *Start
 * now* / *Leave lobby* while waiting in one. Reads the {@code pvp} tag the server adds to the machine state
 * ({@code PlinkoBattleMachine.writeState}); sends {@code pvp_*} table actions.
 */
public final class PlinkoBattleEntries {
	private static boolean expanded;
	private static int balls = -1;
	private static SeatPolicy policy = SeatPolicy.MIXED;
	private static BotDifficulty difficulty = BotDifficulty.MIXED;
	private static int size = -1;

	private PlinkoBattleEntries() {}

	/** Simple wrapping row layout in absolute coordinates. */
	private static final class Row {
		final Font font;
		final Consumer<AbstractWidget> add;
		final int left;
		final int right;
		int x;
		int y;

		Row(Font font, Consumer<AbstractWidget> add, int left, int top, int width) {
			this.font = font;
			this.add = add;
			this.left = left;
			this.right = left + width;
			this.x = left;
			this.y = top;
		}

		Button button(Component label, int minWidth, Button.OnPress press) {
			int w = Math.min(right - left, Math.max(minWidth, font.width(label) + 10));
			if (x + w > right && x > left) {
				newRow();
			}
			Button b = Button.builder(label, press).bounds(x, y, w, 20).build();
			add.accept(b);
			x += w + 3;
			return b;
		}

		void newRow() {
			if (x > left) {
				x = left;
				y += 22;
			}
		}

		int bottom() {
			return x > left ? y + 22 : y;
		}
	}

	/**
	 * Adds the entries below the machine's controls.
	 *
	 * @param state  machine state (with the server's {@code pvp} tag)
	 * @param risk   the machine screen's current risk id
	 * @param amount the machine screen's current bet (the entry)
	 * @param send   the screen's table action sender
	 * @param rebuild re-layout the screen
	 * @return absolute y below the added widgets
	 */
	public static int layout(Font font, Consumer<AbstractWidget> add, int left, int top, int width, CompoundTag state, String risk,
			LongSupplier amount, BiConsumer<String, CompoundTag> send, Runnable rebuild, boolean busy) {
		if (!state.contains("pvp")) {
			return top;
		}
		CompoundTag pvp = state.getCompoundOrEmpty("pvp");
		Row row = new Row(font, add, left, top, width);
		String inMatch = pvp.getStringOr("in_match", "");
		if (!inMatch.isEmpty()) {
			if (pvp.getBooleanOr("lobby", false)) {
				if (pvp.getBooleanOr("host", false)) {
					row.button(Component.translatable("gui.burmaldaholic.pvp.lobby.start").withStyle(ChatFormatting.BOLD), 60, b -> {
						CompoundTag args = new CompoundTag();
						args.putString("id", inMatch);
						send.accept("pvp_start", args);
					});
				}
				row.button(Component.translatable("gui.burmaldaholic.pvp.lobby.leave"), 60, b -> send.accept("pvp_leave", new CompoundTag()));
			}
			return row.bottom();
		}
		List<Integer> choices = new ArrayList<>();
		ListTag list = pvp.getListOrEmpty("balls");
		for (int i = 0; i < list.size(); i++) {
			choices.add(list.getIntOr(i, 3));
		}
		if (choices.isEmpty()) {
			choices.add(3);
		}
		if (!choices.contains(balls)) {
			balls = pvp.getIntOr("balls_default", choices.get(choices.size() / 2));
		}
		int maxPlayers = Math.max(2, pvp.getIntOr("max_players", 6));
		if (size < 2 || size > maxPlayers) {
			size = maxPlayers;
		}
		boolean bots = pvp.getBooleanOr("bots", false);
		if (!bots) {
			policy = SeatPolicy.HUMANS_ONLY;
		}
		Component host = Component.translatable("gui.burmaldaholic.pvp.plinko.host");
		row.button(expanded ? host.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE) : host, 80, b -> {
			expanded = !expanded;
			rebuild.run();
		}).active = !busy;
		if (expanded) {
			row.newRow();
			row.button(Component.translatable("gui.burmaldaholic.pvp.plinko.balls_value", Texts.number(balls)), 60, b -> {
				balls = choices.get((choices.indexOf(balls) + 1) % choices.size());
				rebuild.run();
			});
			if (bots) {
				row.button(Component.translatable("options.generic_value", Component.translatable("gui.burmaldaholic.pvp.bots.seats"),
					Component.translatable(policy.translationKey())), 60, b -> {
						policy = SeatPolicy.values()[(policy.ordinal() + 1) % SeatPolicy.values().length];
						rebuild.run();
					});
				if (policy != SeatPolicy.HUMANS_ONLY) {
					row.button(Component.translatable("options.generic_value", Component.translatable("gui.burmaldaholic.pvp.bots.difficulty"),
						Component.translatable(difficulty.translationKey())), 60, b -> {
							difficulty = BotDifficulty.values()[(difficulty.ordinal() + 1) % BotDifficulty.values().length];
							rebuild.run();
						});
					row.button(Component.translatable("gui.burmaldaholic.pvp.bots.table_size_value", Texts.number(size)), 50, b -> {
						size = size >= maxPlayers ? 2 : size + 1;
						rebuild.run();
					});
				}
			}
			row.button(Component.translatable("gui.burmaldaholic.pvp.lobby.open").withStyle(ChatFormatting.BOLD), 60, b -> {
				CompoundTag args = new CompoundTag();
				args.putLong("amount", amount.getAsLong());
				args.putString("risk", risk);
				args.putInt("balls", balls);
				args.putString("policy", policy.id());
				args.putString("difficulty", difficulty.id());
				args.putInt("size", size);
				send.accept("pvp_host", args);
				expanded = false;
			}).active = !busy;
		}
		ListTag lobbies = pvp.getListOrEmpty("lobbies");
		if (!lobbies.isEmpty()) {
			row.newRow();
		}
		for (int i = 0; i < lobbies.size(); i++) {
			CompoundTag l = lobbies.getCompoundOrEmpty(i);
			String id = l.getStringOr("id", "");
			Component label = Component.translatable("gui.burmaldaholic.pvp.plinko.join", Texts.chips(l.getLongOr("entry", 0)),
				Texts.number(l.getIntOr("players", 1)), Texts.number(l.getIntOr("max", maxPlayers)));
			row.button(label, 80, b -> {
				CompoundTag args = new CompoundTag();
				args.putString("id", id);
				send.accept("pvp_join", args);
			}).active = !busy;
		}
		return row.bottom();
	}
}
