package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.BotRoster;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.bots.logic.SeatingMath;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Implemented by a table block entity whose seats may hold bots (poker, baccarat incl. chemin de fer,
 * UTH, blackjack, roulette, craps). The game keeps its own seat model ABSTRACT over
 * {@link SeatOccupant}; {@link TableBots} calls these hooks ONLY at the game's safe point (BOTS.md §3.1),
 * i.e. when the game calls {@link TableBots#safePoint}.
 *
 * <p>The defaults below work for a {@link BlockEntity}; override them where the game knows better
 * (poker: {@link #handsSinceBigBlind}, chemmy: {@link #isBotBanker}, poker stakes: {@link #botDifficultyMix}).
 */
public interface BotTable {
	/** Game id for config families: {@code poker | chemmy | blackjack | roulette | craps | baccarat | uth}. */
	String botGameId();

	/** MONEY (poker, chemmy, PvP) or ATMOSPHERE (virtual bets). */
	BotRole botRole();

	/** Seats available to occupants (players + bots, excluding a dealer seat). */
	int botSeatCount();

	/** Humans currently seated, in the order they sat down (host selection, BOTS.md §2.4). */
	List<UUID> seatedHumans();

	/** Current occupants in seat order (null = empty seat). */
	List<SeatOccupant> occupants();

	/** Seat a bot in a free seat (called at the safe point); return false if the game refuses. */
	boolean seatBot(SeatOccupant.Bot bot, long stack);

	/**
	 * Remove a bot at the safe point; returns what it holds (stack / bank escrow) so {@link TableBots} can
	 * return it to its purse. Atmosphere bots return 0.
	 */
	long unseatBot(String botKey);

	/** Which bot yields to a claimant (§3.3). */
	SeatingMath.YieldRule yieldRule();

	/** Chips a new money bot sits with (poker buy-in, chemmy bank / punt budget); 0 for atmosphere. */
	default long botBuyIn() {
		return 0;
	}

	/** False for games where decisions can't matter (difficulty hidden, BOTS.md §4.7/§4.8 "luck only"). */
	default boolean botDifficultyMatters() {
		return true;
	}

	/**
	 * Stable key of this table (ledger: daily buy-ins, bankroll escrow; chatter queue). Default for a
	 * block entity: {@code <dimension>@x,y,z}. Must not change while the table exists.
	 */
	default String botTableKey() {
		if (this instanceof BlockEntity be && be.getLevel() != null) {
			BlockPos p = be.getBlockPos();
			return be.getLevel().dimension().identifier() + "@" + p.getX() + "," + p.getY() + "," + p.getZ();
		}
		return getClass().getSimpleName() + "@" + System.identityHashCode(this);
	}

	/** Where the table is (ownership / purse, claimant distance, chatter audience); null = unknown. */
	default @Nullable BlockPos botTablePos() {
		return this instanceof BlockEntity be ? be.getBlockPos() : null;
	}

	/** False once the table is gone (a removed block entity), so its bots stop counting world-wide. */
	default boolean botTableActive() {
		return !(this instanceof BlockEntity be) || !be.isRemoved();
	}

	/** MIXED weights [easy, normal, hard] for new bots (poker: {@code poker.botMix.<stake>} with the stake gate). */
	default int[] botDifficultyMix() {
		return CasinoConfig.bots().difficultyMix;
	}

	/**
	 * May a fixed difficulty be chosen here (poker: no EASY above {@code bots.poker.easyMaxStake})? A gated
	 * level is applied as NORMAL; MIXED is gated by {@link #botDifficultyMix}. Default: every level.
	 */
	default boolean botLevelAllowed(BotDifficulty level) {
		return true;
	}

	/** Name pool (Piglin Parlor / End lounge tables: themed). */
	default BotRoster.Theme botNameTheme() {
		return BotRoster.Theme.ANY;
	}

	/**
	 * Presentation hook (global.md §4.12, lane J-L3): the key of the bot whose decision timer runs right now, or null.
	 * The nameplate shows thinking dots while it is set — identical for every decision (never correlated with the
	 * hand). Default: none (games opt in by returning their acting bot while its think delay runs).
	 */
	default @Nullable String botThinking() {
		return null;
	}

	/** Chemin de fer: this bot holds the bank (it yields last, §3.3). */
	default boolean isBotBanker(String botKey) {
		return false;
	}

	/** Poker: hands since this bot posted the big blind (0 = just posted it: yields first, §3.3). */
	default int handsSinceBigBlind(String botKey) {
		return Integer.MAX_VALUE;
	}

	/**
	 * The table's {@link TableBots} (the object the block entity owns). Used by the bots module (table
	 * settings screen, {@code /casino table}, avatars, chatter). Games implementing this interface return
	 * their instance; null = the table offers no bot / private-table settings.
	 */
	default @Nullable TableBots tableBots() {
		return null;
	}

	/**
	 * A {@link BotTable} whose hooks are all answered by a helper ({@link AtmosphereBots} for blackjack,
	 * roulette, craps): the block entity implements this and only {@link #botDelegate()}, so
	 * {@code be instanceof BotTable t && t.tableBots() != null} finds it like any other table.
	 */
	interface Delegating extends BotTable {
		BotTable botDelegate();

		@Override
		default String botGameId() {
			return botDelegate().botGameId();
		}

		@Override
		default BotRole botRole() {
			return botDelegate().botRole();
		}

		@Override
		default int botSeatCount() {
			return botDelegate().botSeatCount();
		}

		@Override
		default List<UUID> seatedHumans() {
			return botDelegate().seatedHumans();
		}

		@Override
		default List<SeatOccupant> occupants() {
			return botDelegate().occupants();
		}

		@Override
		default boolean seatBot(SeatOccupant.Bot bot, long stack) {
			return botDelegate().seatBot(bot, stack);
		}

		@Override
		default long unseatBot(String botKey) {
			return botDelegate().unseatBot(botKey);
		}

		@Override
		default SeatingMath.YieldRule yieldRule() {
			return botDelegate().yieldRule();
		}

		@Override
		default long botBuyIn() {
			return botDelegate().botBuyIn();
		}

		@Override
		default boolean botDifficultyMatters() {
			return botDelegate().botDifficultyMatters();
		}

		@Override
		default String botTableKey() {
			return botDelegate().botTableKey();
		}

		@Override
		default @Nullable BlockPos botTablePos() {
			return botDelegate().botTablePos();
		}

		@Override
		default boolean botTableActive() {
			return botDelegate().botTableActive();
		}

		@Override
		default int[] botDifficultyMix() {
			return botDelegate().botDifficultyMix();
		}

		@Override
		default boolean botLevelAllowed(BotDifficulty level) {
			return botDelegate().botLevelAllowed(level);
		}

		@Override
		default BotRoster.Theme botNameTheme() {
			return botDelegate().botNameTheme();
		}

		/** The helper rarely knows the game's timers: delegating tables override this with their own (J-L3 hook). */
		@Override
		default @Nullable String botThinking() {
			return botDelegate().botThinking();
		}

		@Override
		default boolean isBotBanker(String botKey) {
			return botDelegate().isBotBanker(botKey);
		}

		@Override
		default int handsSinceBigBlind(String botKey) {
			return botDelegate().handsSinceBigBlind(botKey);
		}

		@Override
		default @Nullable TableBots tableBots() {
			return botDelegate().tableBots();
		}
	}
}
