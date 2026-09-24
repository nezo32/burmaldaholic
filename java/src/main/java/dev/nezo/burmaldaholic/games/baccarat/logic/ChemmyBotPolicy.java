package dev.nezo.burmaldaholic.games.baccarat.logic;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotPolicy;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Chemin de fer MONEY bots (docs/design/BOTS.md §4.4, pvp-bots.md §4.6). PURE; same numbers as Bedrock
 * {@code games/baccarat/logic/bots.ts}. The tableau is fixed (§20.9), so the level is a <b>Style</b>
 * (Wild / Steady / Cool-headed) that changes tempo and stakes, never anyone's EV.
 *
 * <ul>
 *   <li>{@link #BANK}: take / keep / pass the bank and the bank size;</li>
 *   <li>{@link #PUNT}: a bet on the Player hand (snapped to the open coverage) or Banco;</li>
 *   <li>helpers: bot bank cap and buy-in, punting memory (Martingale), when a bot punter acts,
 *       making room for a human punter.</li>
 * </ul>
 * Every random choice takes the BOT rng ({@code TableBots.rng()}), never the shoe's.
 */
public final class ChemmyBotPolicy {
	private ChemmyBotPolicy() {}

	/** Style numbers (BOTS.md §4.4): take-the-bank chance, Banco chance, winning coups kept. */
	public record Style(double take, double banco, int keepWins) {}

	public static final Style EASY = new Style(0.6, 0.2, Integer.MAX_VALUE);
	public static final Style NORMAL = new Style(0.4, 0.05, 3);
	public static final Style HARD = new Style(0.3, 0, Integer.MAX_VALUE);

	/** Martingale reset: after 8× the next loss starts over at 1×. */
	public static final int MARTINGALE_MAX = 8;
	/** HARD takes the bank only when its chips last ≥ this many coups at the typical bet. */
	public static final int HARD_BANK_COUPS = 10;
	/** Bot punters act this many ticks (bot rng, inclusive) before the betting deadline (§7.3). */
	public static final int PUNT_OFFSET_MIN = 20;
	public static final int PUNT_OFFSET_MAX = 100;

	public static Style style(BotDifficulty level) {
		return switch (level) {
			case EASY -> EASY;
			case HARD -> HARD;
			default -> NORMAL;
		};
	}

	// ---- facts and views ---------------------------------------------------------------------------

	/**
	 * Public table facts a chemmy bot may know.
	 *
	 * @param tableMin     punter minimum of the table
	 * @param bankCap      bot bank cap ({@link #botBankCap})
	 * @param humanPunters humans seated who would punt against this bot's bank
	 * @param recentStakes individual human punter stakes of the last 5 coups
	 */
	public record Facts(long tableMin, long minBank, long bankCap, int humanPunters, int seatedHumans, List<Long> recentStakes) {
		public Facts {
			recentStakes = List.copyOf(recentStakes);
		}
	}

	/**
	 * A bank offer as the seat sees it.
	 *
	 * @param keep    a winning bank may be kept ({@code bank} = its amount), else a fresh offer
	 * @param balance the bot's free chips
	 * @param wins    winning coups of the bank being kept
	 */
	public record OfferView(boolean keep, long bank, long minBank, long balance, int wins, Facts facts) {}

	/** A bot's punting memory, kept per seated bot: EASY 1–3 × min Martingale, NORMAL flat 2–5 × min. */
	public static final class Memory {
		public long base;
		public int mult = 1;

		public Memory(long base) {
			this.base = base;
		}
	}

	/**
	 * A punter window as the seat sees it.
	 *
	 * @param max            the bot's own max this coup (the bot bank cap)
	 * @param humanBetOnCoup a human has chips on this coup (bots then never call Banco)
	 */
	public record PuntView(long coverage, long open, long min, long max, long balance, boolean bancoAvailable, boolean humanBetOnCoup,
			long base, int mult, Facts facts) {}

	public enum PuntKind { NONE, BET, BANCO }

	public record Punt(PuntKind kind, long amount) {
		public static final Punt NONE = new Punt(PuntKind.NONE, 0);
		public static final Punt BANCO = new Punt(PuntKind.BANCO, 0);

		public static Punt bet(long amount) {
			return new Punt(PuntKind.BET, amount);
		}
	}

	// ---- numbers -------------------------------------------------------------------------------------

	/** {@code bots.chemmy.bankCapMultiple} × table min, never below the minimum bank. */
	public static long botBankCap(int multiple, long tableMin, long minBank) {
		return Math.max(minBank, (long) Math.max(0, multiple) * Math.max(1, tableMin));
	}

	/** Chips a new chemmy bot sits with (from its purse): two full banks at the cap. */
	public static long buyIn(long bankCap, long minBank) {
		return 2 * Math.max(bankCap, minBank);
	}

	public static long median(List<Long> xs) {
		if (xs.isEmpty()) {
			return 0;
		}
		List<Long> s = new ArrayList<>(xs);
		s.sort(null);
		int m = s.size() >> 1;
		return s.size() % 2 == 1 ? s.get(m) : Math.floorDiv(s.get(m - 1) + s.get(m), 2);
	}

	/** The table's typical punter stake (median of recent human stakes, else 2 × min). */
	public static long typicalStake(Facts f) {
		long m = median(f.recentStakes());
		return m != 0 ? m : 2 * Math.max(1, f.tableMin());
	}

	/** Bank amount B by style (before {@link #BANK} legalize clamps it to [minBank, min(cap, chips)]). */
	public static long bankAmount(BotDifficulty level, OfferView v, BotRng rng) {
		Facts f = v.facts();
		return switch (level) {
			case EASY -> rng.between(2, 5) * v.minBank();
			case HARD -> Math.min(f.bankCap(), 3 * typicalStake(f) * Math.max(1, f.seatedHumans()));
			default -> 10 * Math.max(1, f.tableMin());
		};
	}

	/** Draw a new bot's punting memory (bot rng). */
	public static Memory newMemory(BotDifficulty level, long tableMin, BotRng rng) {
		long min = Math.max(1, tableMin);
		return switch (level) {
			case EASY -> new Memory(rng.between(1, 3) * min);
			case HARD -> new Memory(2 * min);
			default -> new Memory(rng.between(2, 5) * min);
		};
	}

	/** After a coup the bot punted: EASY doubles after a loss (reset after 8×), resets after a win. */
	public static void afterPunt(Memory m, BotDifficulty level, long net) {
		if (level != BotDifficulty.EASY || net == 0) {
			return;
		}
		m.mult = net < 0 ? (m.mult >= MARTINGALE_MAX ? 1 : m.mult * 2) : 1;
	}

	// ---- policies ------------------------------------------------------------------------------------

	private static SeatDecider.BankDecision normalize(SeatDecider.BankDecision d, OfferView v) {
		return switch (d.choice()) {
			case KEEP -> v.keep() && v.bank() >= v.minBank() ? d : SeatDecider.BankDecision.pass();
			case TAKE -> !v.keep() && d.amount() >= v.minBank() && d.amount() <= v.balance() ? d : SeatDecider.BankDecision.pass();
			case PASS -> d;
		};
	}

	/** Take / keep / pass the bank (BOTS.md §4.4). */
	public static final BotPolicy<OfferView, SeatDecider.BankDecision> BANK = new BotPolicy<>() {
		@Override
		public SeatDecider.BankDecision decide(BotProfile bot, OfferView v, Object work, BotRng rng) {
			Style st = style(bot.level());
			if (v.keep()) {
				if (bot.level() == BotDifficulty.HARD) {
					return v.bank() <= v.facts().bankCap() ? keep() : SeatDecider.BankDecision.pass();
				}
				return v.wins() < st.keepWins() ? keep() : SeatDecider.BankDecision.pass();
			}
			if (!rng.chance(st.take())) {
				return SeatDecider.BankDecision.pass();
			}
			if (bot.level() == BotDifficulty.HARD && v.balance() < HARD_BANK_COUPS * typicalStake(v.facts())) {
				return SeatDecider.BankDecision.pass();
			}
			return new SeatDecider.BankDecision(SeatDecider.BankChoice.TAKE, bankAmount(bot.level(), v, rng));
		}

		@Override
		public SeatDecider.BankDecision legalize(OfferView v, SeatDecider.BankDecision a) {
			// A bot banks only against ≥ 1 human punter: bot-vs-bot money is the purse against itself.
			if (v.facts().humanPunters() < 1 || a == null) {
				return SeatDecider.BankDecision.pass();
			}
			if (a.choice() == SeatDecider.BankChoice.TAKE) {
				long hi = Math.min(Math.max(v.facts().bankCap(), v.minBank()), v.balance());
				if (hi < v.minBank()) {
					return SeatDecider.BankDecision.pass();
				}
				long amount = Math.max(v.minBank(), Math.min(hi, a.amount()));
				return normalize(new SeatDecider.BankDecision(SeatDecider.BankChoice.TAKE, amount), v);
			}
			return normalize(a, v);
		}
	};

	private static SeatDecider.BankDecision keep() {
		return new SeatDecider.BankDecision(SeatDecider.BankChoice.KEEP, 0);
	}

	/** Punt: a bet on Player (snapped to the open coverage) or Banco (BOTS.md §4.4). */
	public static final BotPolicy<PuntView, Punt> PUNT = new BotPolicy<>() {
		@Override
		public Punt decide(BotProfile bot, PuntView v, Object work, BotRng rng) {
			double p = style(bot.level()).banco();
			if (p > 0 && v.bancoAvailable() && !v.humanBetOnCoup() && rng.chance(p)) {
				return Punt.BANCO;
			}
			return switch (bot.level()) {
				case EASY -> Punt.bet(v.base() * v.mult());
				case HARD -> Punt.bet(Math.min(v.open(), v.facts().bankCap()));
				default -> Punt.bet(v.base());
			};
		}

		@Override
		public Punt legalize(PuntView v, Punt a) {
			if (a == null) {
				return Punt.NONE;
			}
			if (a.kind() == PuntKind.BANCO) {
				return v.bancoAvailable() && !v.humanBetOnCoup() && v.coverage() > 0 && v.balance() >= v.coverage() ? a : Punt.NONE;
			}
			if (a.kind() != PuntKind.BET) {
				return Punt.NONE;
			}
			long amount = Math.min(Math.min(a.amount(), v.open()), Math.min(v.max(), v.balance()));
			return amount >= Math.max(1, v.min()) ? Punt.bet(amount) : Punt.NONE;
		}
	};

	// ---- tempo and coverage ----------------------------------------------------------------------------

	/**
	 * When a bot punter acts (BOTS.md §4.4 / §7.3): bots bet LAST — {@code offset} ticks (20–100, bot rng)
	 * before the betting deadline ({@code ticksToDeadline}: the bet timer once it runs, else the moment the
	 * idle bank would pass). Without a human punter seated (a human banks against bots only) bots act once
	 * their think delay since betting opened has passed, so the banker never waits for nothing.
	 */
	public static boolean puntDue(long ticksToDeadline, long sinceBettingOpened, int humanPunters, int offset, int think) {
		if (humanPunters == 0) {
			return sinceBettingOpened >= think;
		}
		return ticksToDeadline >= 0 && ticksToDeadline <= offset;
	}

	/**
	 * Bot stakes to shrink (latest first) so a human bet gets the coverage it asks for: bot punter bets never
	 * reduce the coverage a human requested. Mutates {@code punts} (placement order kept, emptied bots
	 * removed) and returns the per-bot refunds.
	 */
	public static <K> Map<K, Long> makeRoomForHuman(Map<K, Long> punts, Predicate<K> isBot, long need) {
		Map<K, Long> refunds = new LinkedHashMap<>();
		long left = Math.max(0, need);
		List<K> order = new ArrayList<>(punts.keySet());
		for (int i = order.size() - 1; i >= 0 && left > 0; i--) {
			K k = order.get(i);
			if (!isBot.test(k)) {
				continue;
			}
			long amount = punts.get(k);
			long cut = Math.min(amount, left);
			left -= cut;
			refunds.merge(k, cut, Long::sum);
			if (amount - cut <= 0) {
				punts.remove(k);
			} else {
				punts.put(k, amount - cut);
			}
		}
		return refunds;
	}
}
