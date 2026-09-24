package dev.nezo.burmaldaholic.core.pvp;

import com.google.gson.JsonElement;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.DecisionView;
import dev.nezo.burmaldaholic.core.pvp.logic.HeadToHead;
import dev.nezo.burmaldaholic.core.util.Result;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The PvP engine (PVP.md §3). Server thread only. Get it with {@link Pvp#service()}.
 *
 * <p>Callers: machine UIs of the mode-owning modules (slot machine / wheel / plinko "Start a …" and
 * "Join …" entries, Lucky Coin on a player), the {@code pvp} module (hub page, lobby / result screens,
 * {@code /casino pvp …}), and the engine itself (timers, bots). Every money-moving call re-checks
 * eligibility (§3.2) and moves chips in ONE atomic {@code Economy.batch} (escrow in the bank; settle =
 * payouts + rake to the bank or the anchor's bankroll; bots via their purse, BOTS.md §5.1).
 * Errors come back as {@link Result#fail} with a translated component (already shown to nobody).
 */
public interface PvpService {
	/** A machine anchor or none (anchor-less lobbies use the host's position). */
	record Anchor(AnchorKind kind, ServerLevel level, BlockPos pos) {}

	/** Duel opponent: a named nearby player, or a house bot of a difficulty (BOTS.md §3.6). */
	sealed interface Opponent {
		record PlayerTarget(UUID player) implements Opponent {}

		record BotTarget(BotDifficulty difficulty) implements Opponent {}
	}

	/** Per-player totals for the hub (PVP.md §3.11.2). */
	record Stats(int wins, int losses, long net, int winStreak, @Nullable UUID nemesis) {}

	record Rival(UUID player, String name, HeadToHead record) {}

	// ---- duels (§3.3.1) ----------------------------------------------------------------------

	/** Validates both sides and sends the invite (nothing escrowed). A bot opponent accepts after its think time. */
	Result<PvpMatch> challenge(ServerPlayer challenger, String mode, JsonElement params, long stake, Opponent opponent);

	/** Escrows both stakes atomically, draws + persists the tape, starts the reveal. */
	Result<PvpMatch> accept(ServerPlayer target, String matchId);

	void decline(ServerPlayer target, String matchId);

	void withdraw(ServerPlayer challenger, String matchId);

	// ---- lobbies (§3.3.2, §3.15.1) ----------------------------------------------------------------

	/** Host escrows the entry; seating = HUMANS_ONLY / MIXED / BOTS_ONLY (BOTS_ONLY starts at once). */
	Result<PvpMatch> openLobby(ServerPlayer host, String mode, JsonElement params, long stake, Anchor anchor, BotSettings seating,
		boolean inviteOnly);

	/** Escrows the joiner's entry (Wheel Party: their chosen stake). */
	Result<PvpMatch> join(ServerPlayer player, String matchId, long stake);

	/** Wheel Party: add to the slice until No more bets. */
	Result<Long> topUp(ServerPlayer player, String matchId, long extra);

	/** Leave a lobby (refund) / the rematch window; no effect after START (the match plays out). */
	void leave(ServerPlayer player);

	/** Host "Start" (MIXED: fills empty seats with bots first). */
	Result<PvpMatch> start(ServerPlayer host, String matchId);

	/**
	 * Invite-only lobby (BOTS.md §2.5): the host of {@code player}'s lobby invites {@code guest} (chat line with a
	 * clickable [Join]); only guests, the host and operators may join. Max {@code bots.private.maxInvites}.
	 */
	Result<Boolean> inviteToLobby(ServerPlayer host, UUID guest);

	/** Host "Fill with bots" (MIXED lobbies). */
	void fillWithBots(ServerPlayer host, String matchId);

	// ---- during a match -------------------------------------------------------------------------

	/** The mode's advance button (Spin! / Drop! / Scratch!); only speeds up the timeline. */
	void press(ServerPlayer player);

	/**
	 * A decision between Coin Flip Duel links (PVP.md §4.2): {@code coin.don_offer} (chain loser) with
	 * option 0 = walk away, 1 = Double or nothing on Heads, 2 = on Tails; {@code coin.let_it_ride} (chain
	 * winner) with 1 = let it ride, 0 = take the money. {@code coin.side} (0 heads / 1 tails) before
	 * {@code coin.don_offer} 1 sets the called side (Bedrock form flow). The open decision is {@link #decisionFor}.
	 */
	default void decide(ServerPlayer player, String decision, long option) {
		decide(player, decision, option, null, -1);
	}

	/**
	 * {@link #decide(ServerPlayer, String, long)} for the question the UI was opened for: {@code matchId} and
	 * {@code seq} ({@link PvpMatch#decisionSeq}, sent with the view's {@code decision}) must still be the open
	 * question, else the answer is stale and dropped (review wave 2, m2). {@code matchId} null / {@code seq} &lt; 0 =
	 * unchecked (commands).
	 */
	void decide(ServerPlayer player, String decision, long option, @Nullable String matchId, long seq);

	/** One of the 8 fixed taunt lines (§3.9). */
	Result<Void> taunt(ServerPlayer player, int line);

	/** Rematch button in the result window (§3.10). */
	void rematch(ServerPlayer player, String matchId);

	// ---- queries -------------------------------------------------------------------------------

	Optional<PvpMatch> matchOf(UUID player);

	Optional<PvpMatch> get(String matchId);

	/** Open lobbies within {@code pvp.joinRadius} (and, for machines, {@code pvp.<mode>.linkRadius}); mode null = all. */
	List<PvpMatch> lobbiesNear(ServerPlayer player, @Nullable String mode);

	List<PvpMatch> invitesFor(UUID player);

	HeadToHead record(UUID a, UUID b);

	List<Rival> rivals(UUID player, int max);

	Stats stats(UUID player);

	/** The decision {@code player} has to answer right now (Double or nothing / let it ride), if any. */
	Optional<DecisionView> decisionFor(UUID player);

	/** Per-player "Accept PvP challenges" setting (PVP.md §3.11.2, default on). */
	boolean acceptsInvites(UUID player);

	void setAcceptInvites(UUID player, boolean accept);

	// ---- admin (§3.13) ---------------------------------------------------------------------------

	List<PvpMatch> all();

	/** LOBBY → refund; DRAWN → settle now. */
	void cancel(String matchId);
}
