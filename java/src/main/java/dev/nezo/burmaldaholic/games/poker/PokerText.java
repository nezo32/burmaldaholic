package dev.nezo.burmaldaholic.games.poker;

import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.bots.BotNames;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.games.poker.logic.PokerBotPolicy;
import dev.nezo.burmaldaholic.games.poker.logic.Cards;
import dev.nezo.burmaldaholic.games.poker.logic.Hand;
import dev.nezo.burmaldaholic.games.poker.logic.HandEvaluator;
import dev.nezo.burmaldaholic.games.poker.logic.PokerTable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Poker text building (server log / chat lines and client rendering). Every word comes from a lang key;
 * the only raw text is player/bot names, numbers, card digits and suit symbols.
 */
public final class PokerText {
	public static final String KEY = "gui.burmaldaholic.poker.";
	public static final String MSG = "msg.burmaldaholic.poker.";
	private static final String[] SUIT_GLYPHS = {"♠", "♥", "♦", "♣"};

	private PokerText() {}

	public static MutableComponent gui(String suffix, Object... args) {
		return Component.translatable(KEY + suffix, args);
	}

	public static MutableComponent msg(String suffix, Object... args) {
		return Component.translatable(MSG + suffix, args);
	}

	/** Card rank as text: digits 2–9 raw, 10/J/Q/K/A from {@code gui.burmaldaholic.card.rank.*} (RU: Т/К/Д/В). */
	public static MutableComponent rank(int card) {
		int r = Cards.rank(card);
		if (r <= 9) {
			return Texts.raw(Integer.toString(r));
		}
		String id = switch (r) {
			case 10 -> "10";
			case 11 -> "j";
			case 12 -> "q";
			case 13 -> "k";
			default -> "a";
		};
		return Component.translatable("gui.burmaldaholic.card.rank." + id);
	}

	/** Suit symbol (language neutral). */
	public static MutableComponent suit(int card) {
		return Texts.raw(SUIT_GLYPHS[Cards.suit(card)]);
	}

	public static boolean red(int card) {
		int s = Cards.suit(card);
		return s == 1 || s == 2;
	}

	/** "A♠" with the suit colored (for chat / log lines). */
	public static MutableComponent card(int card) {
		return Component.empty().append(rank(card).withStyle(ChatFormatting.WHITE))
			.append(suit(card).withStyle(red(card) ? ChatFormatting.RED : ChatFormatting.WHITE));
	}

	public static MutableComponent cards(int[] cards) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < cards.length; i++) {
			if (i > 0) {
				out.append(Texts.raw(" "));
			}
			out.append(card(cards[i]));
		}
		return out;
	}

	/** Spoken card name for narration: "Ace of Spades". */
	public static MutableComponent cardNarration(int card) {
		int r = Cards.rank(card);
		MutableComponent name = r <= 10 ? Texts.raw(Integer.toString(r))
			: Component.translatable("gui.burmaldaholic.card.name." + switch (r) {
				case 11 -> "j";
				case 12 -> "q";
				case 13 -> "k";
				default -> "a";
			});
		return Component.translatable("gui.burmaldaholic.card.narration", name,
			Component.translatable("gui.burmaldaholic.card.suit." + Cards.SUIT_IDS[Cards.suit(card)]));
	}

	public static MutableComponent handName(int value) {
		return gui("hand." + HandEvaluator.handName(value));
	}

	/**
	 * The specific name of an evaluated hand for the plates ("Three kings", «Три короля», "Flush, ace high"), from the
	 * ranks the evaluator packs; a royal flush keeps its category name. Each slot names the grammatical form the
	 * languages need there ({@code rank.<r>.<slot>}: one / high / three / many / set).
	 */
	public static MutableComponent handLabel(int value) {
		int cat = HandEvaluator.category(value);
		String id = HandEvaluator.handName(value);
		int[] r = HandEvaluator.ranks(value);
		return switch (cat) {
			case HandEvaluator.HIGH_CARD -> gui("hand_named." + id, rank(r[0], "one"));
			case HandEvaluator.PAIR -> gui("hand_named." + id, rank(r[0], "set"));
			case HandEvaluator.TWO_PAIR -> gui("hand_named." + id, rank(r[0], "many"), rank(r[2], "many"));
			case HandEvaluator.THREE_OF_A_KIND -> gui("hand_named." + id, rank(r[0], "three"));
			case HandEvaluator.STRAIGHT, HandEvaluator.FLUSH -> gui("hand_named." + id, rank(r[0], "high"));
			case HandEvaluator.FULL_HOUSE -> gui("hand_named." + id, rank(r[0], "set"), rank(r[3], "set"));
			case HandEvaluator.FOUR_OF_A_KIND -> gui("hand_named." + id, rank(r[0], "set"));
			case HandEvaluator.STRAIGHT_FLUSH -> "royal_flush".equals(id) ? handName(value) : gui("hand_named." + id, rank(r[0], "high"));
			default -> handName(value);
		};
	}

	private static MutableComponent rank(int r, String slot) {
		String id = switch (r) {
			case 1, 14 -> "a";
			case 11 -> "j";
			case 12 -> "q";
			case 13 -> "k";
			default -> Integer.toString(Math.max(2, Math.min(10, r)));
		};
		return gui("rank." + id + "." + slot);
	}

	/** Level color (BOTS.md §4.2: E green, N yellow, H red; never color alone — the word is shown too). */
	public static ChatFormatting levelColor(BotDifficulty level) {
		return switch (level) {
			case EASY -> ChatFormatting.GREEN;
			case HARD -> ChatFormatting.RED;
			default -> ChatFormatting.YELLOW;
		};
	}

	/** The poker name of a bot level: Fish / Regular / Shark ({@code gui.burmaldaholic.poker.bot.<tier>}). */
	public static MutableComponent tier(BotDifficulty level) {
		return gui("bot." + PokerBotPolicy.tierOf(level)).withStyle(levelColor(level));
	}

	/** "Name" for humans, "[glyph] [BOT] Lucky Steve [Shark]" for bots (name translated from its id). */
	public static MutableComponent seatName(PokerTable.Seat s) {
		if (s.human || s.bot == null) {
			return Texts.raw(s.name);
		}
		return BotNames.display(s.bot).append(Texts.raw(" [")).append(tier(s.bot.level())).append(Texts.raw("]"));
	}

	public static MutableComponent street(Hand.Street street) {
		return gui(street.id());
	}

	/** One log / action-bar line for a hand event. */
	public static MutableComponent event(PokerTable table, Hand h, Hand.Event e) {
		if (e instanceof Hand.Dealt d) {
			return Component.empty().append(street(d.street())).append(Texts.raw(": ")).append(cards(d.cards()));
		}
		int player = e instanceof Hand.Blind b ? b.player() : ((Hand.Acted) e).player();
		PokerTable.Seat s = table.seatOf(h.player(player).id);
		MutableComponent name = s != null ? seatName(s) : Texts.raw(h.player(player).id);
		if (e instanceof Hand.Blind b) {
			return msg(b.big() ? "action.big_blind" : "action.small_blind", name, Texts.number(b.amount()));
		}
		Hand.Acted a = (Hand.Acted) e;
		if (a.allIn() && a.type() != Hand.ActionType.FOLD && a.type() != Hand.ActionType.CHECK) {
			long total = a.type() == Hand.ActionType.CALL ? h.player(player).bet() : a.amount();
			return msg("action.all_in", name, Texts.number(Math.max(total, a.amount()))).withStyle(ChatFormatting.GOLD);
		}
		return switch (a.type()) {
			case FOLD -> msg("action.fold", name);
			case CHECK -> msg("action.check", name);
			case CALL -> msg("action.call", name, Texts.number(a.amount()));
			case BET -> msg("action.bet", name, Texts.number(a.amount()));
			case RAISE -> msg("action.raise", name, Texts.number(a.amount()));
		};
	}

	/** Short seat-plate label of a player's last action on this street ("Call 20", "Raise to 60", "Bet: 2"). */
	public static MutableComponent shortAction(Hand h, Hand.Event e) {
		if (e instanceof Hand.Blind b) {
			return Component.translatable("gui.burmaldaholic.common.bet_amount", Texts.number(b.amount()));
		}
		Hand.Acted a = (Hand.Acted) e;
		if (a.allIn() && a.type() != Hand.ActionType.FOLD && a.type() != Hand.ActionType.CHECK) {
			return gui("all_in", Texts.number(Math.max(h.player(a.player()).bet(), a.amount())));
		}
		return switch (a.type()) {
			case FOLD -> gui("fold");
			case CHECK -> gui("check");
			case CALL -> gui("call", Texts.number(a.amount()));
			case BET -> Component.translatable("gui.burmaldaholic.common.bet_amount", Texts.number(a.amount()));
			case RAISE -> gui("raise_to", Texts.number(a.amount()));
		};
	}

	/** Chat lines announcing the winners of a finished hand, with the specific hand names of the plates ("Pair of sixes"). */
	public static List<Component> resultLines(PokerTable table, Hand h) {
		List<Component> out = new ArrayList<>();
		Hand.Result r = h.result();
		if (r == null) {
			return out;
		}
		for (int k = 0; k < r.pots().size(); k++) {
			Hand.PotResult pot = r.pots().get(k);
			if (pot.winners().isEmpty()) {
				continue;
			}
			long total = 0;
			for (long s : pot.shares()) {
				total += s;
			}
			if (r.uncontested()) {
				out.add(msg("wins_uncontested", nameOf(table, h, pot.winners().get(0)), Texts.number(total)).withStyle(ChatFormatting.GREEN));
				continue;
			}
			if (pot.winners().size() > 1) {
				MutableComponent names = Component.empty();
				for (int w = 0; w < pot.winners().size(); w++) {
					if (w > 0) {
						names.append(Texts.raw(", "));
					}
					names.append(nameOf(table, h, pot.winners().get(w)));
				}
				out.add(msg("split_pot", Texts.number(pot.shares()[pot.shares().length - 1])).append(Texts.raw(" — ")).append(names)
					.append(Texts.raw(" — ")).append(handLabel(pot.value())).withStyle(ChatFormatting.GREEN));
				continue;
			}
			int w = pot.winners().get(0);
			out.add(msg(k == 0 ? "wins_pot" : "wins_side_pot", nameOf(table, h, w), Texts.number(total), handLabel(pot.value()))
				.withStyle(ChatFormatting.GREEN));
		}
		return out;
	}

	private static MutableComponent nameOf(PokerTable table, Hand h, int i) {
		PokerTable.Seat s = table.seatOf(h.player(i).id);
		return s != null ? seatName(s) : Texts.raw("?");
	}
}
