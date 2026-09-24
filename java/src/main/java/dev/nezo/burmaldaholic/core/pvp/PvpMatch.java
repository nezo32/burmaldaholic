package dev.nezo.burmaldaholic.core.pvp;

import com.google.gson.JsonElement;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.CoinChain;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import org.jspecify.annotations.Nullable;

/**
 * A live PvP match (PVP.md §3.1, §3.6). Owned and mutated by the engine only; modes and UIs read it.
 * Persisted form: {@link PvpMatchData} (JSON per match: every field below except the transient ones).
 */
public final class PvpMatch {
	/** 8-char base-36 id. */
	public final String id;
	public final String mode;
	/** Mode params ({@code PvpMode#encodeParams}). */
	public final JsonElement params;
	public final AnchorKind anchorKind;
	/** Anchor block, or the host / challenger position for anchor-less matches. */
	public final GlobalPos anchor;
	/** Owned-casino bankroll the anchor was linked to at creation ("" = world bank): rake destination. */
	public final String bankroll;
	/** Seating of this match (policy, bot count, difficulty…); duels vs a bot use BOTS_ONLY count 1. */
	public final BotSettings seating;
	public final boolean inviteOnly;
	public final long createdTick;
	/** Coin Flip Duel chain: id of the first flip's match ("" = none) and link number (1-based). */
	public final String chainOf;
	public final int link;

	final List<Participant> participants = new ArrayList<>();
	MatchState state;
	@Nullable UUID host;
	@Nullable JsonElement tape;
	@Nullable Outcome outcome;
	long drawnTick;
	long rake;
	long[] payouts = new long[0];
	boolean grudge;
	/** Index of the next timeline step to reveal (transient; a reload settles from the tape). */
	transient int cursor;
	/** Bot random stream of this match (BOTS.md §4.1; transient, re-seeded on load). */
	transient @Nullable BotRng botRng;

	// ---- engine-internal pacing (transient unless noted) ----------------------------------------------

	/** Engine phase (finer than {@link MatchState}). */
	Phase phase = Phase.LOBBY;
	/** Tick at which the current phase / step ends. */
	long phaseEnd;
	/** Created by a challenge (duel); persisted. */
	boolean duel;
	/** Duel invite: the target human (participant 1). */
	@Nullable UUID invitee;
	/** LOBBY: tick of the lobby timer (persisted); Wheel Party: cancel with &lt; 2 players at this tick. */
	long lobbyEnd;
	/** LOBBY: tick of the last human join (MIXED delayed bot fill). */
	long lastJoin;
	/** Wheel Party: tick at which "No more bets" starts (0 = the countdown is not running). */
	long noMoreBetsAt;
	/** Wheel Party: bots that still join during the countdown, with their join ticks. */
	final List<Long> botJoinsAt = new ArrayList<>();
	/** Timeline (engine countdown + mode steps + Final Reveal). */
	List<Step> steps = List.of();
	long settledTick;
	/** Coin Flip Duel chain state after this flip (null = not a chain / not settled). */
	@Nullable CoinChain chain;
	/** Chain state after the previous link (null for the first flip). */
	@Nullable CoinChain prevChain;
	/** DoN side called by the chain loser (0 heads, 1 tails; -1 = not yet). */
	int donSide = -1;
	/** Why the Double-or-nothing offer is disabled (null = allowed). */
	@Nullable String offerBlock;
	/** Final Reveal: places revealed so far, from the last (1 = the last place). */
	int revealedPlaces;
	/** Grudge underdog participant index (-1 = none). */
	int grudgeUnderdog = -1;
	/** Settled by a play-out (world load, server stop, casino mode off, admin): no offers, no rematch. */
	boolean forcedSettle;
	/** Engine-wide sequence of the open Double-or-nothing / let-it-ride question (stale answers are dropped). */
	long decisionSeq;
	/** Settlement failed: next retry tick and the current back-off (review wave 2, m6). */
	long settleRetryAt;
	int settleBackoff;
	/** Invite-only lobby: players the host invited (BOTS.md §2.5; the host and ops always may join). */
	final java.util.Set<UUID> guests = new java.util.LinkedHashSet<>();

	/** Engine phases. */
	enum Phase {
		INVITE, LOBBY, NO_MORE_BETS, REVEAL, OFFER_LOSER, OFFER_WINNER, REMATCH, HISTORY, CLOSED;

		String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	PvpMatch(String id, String mode, JsonElement params, AnchorKind anchorKind, GlobalPos anchor, String bankroll, BotSettings seating,
			boolean inviteOnly, long createdTick, String chainOf, int link, MatchState state) {
		this.id = id;
		this.mode = mode;
		this.params = params;
		this.anchorKind = anchorKind;
		this.anchor = anchor;
		this.bankroll = bankroll;
		this.seating = seating;
		this.inviteOnly = inviteOnly;
		this.createdTick = createdTick;
		this.chainOf = chainOf;
		this.link = link;
		this.state = state;
	}

	public MatchState state() {
		return state;
	}

	/**
	 * Engine phase id for UIs: {@code invite, lobby, no_more_bets, reveal, offer_loser, offer_winner, rematch,
	 * history, closed}.
	 */
	public String phase() {
		return phase.id();
	}

	/** World tick at which the current phase / timeline step ends (lobby timer, invite expiry, decision timeout). */
	public long phaseEndTick() {
		return phaseEnd;
	}

	/** Created by a challenge (duel) rather than a lobby. */
	public boolean duel() {
		return duel;
	}

	/** Duel target (human) while the invite is pending. */
	public @Nullable UUID invitee() {
		return invitee;
	}

	/** Wheel Party: tick at which "No more bets" starts (0 = countdown not running). */
	public long noMoreBetsTick() {
		return noMoreBetsAt;
	}

	/** Index of the timeline step being revealed (REVEAL only). */
	public int stepIndex() {
		return cursor;
	}

	/** Coin Flip Duel: deficit D of the chain loser after this flip (0 = no chain). */
	public long chainDeficit() {
		return chain == null ? 0 : chain.deficit();
	}

	/** Coin Flip Duel: the chain loser's participant index (-1 = none). */
	public int chainLoser() {
		return chain == null ? -1 : chain.loser();
	}

	/** Why the Double-or-nothing offer is disabled (a translation key) or null. */
	public @Nullable String offerBlock() {
		return offerBlock;
	}

	/** Sequence number of the open chain question (match id + this = a decision's identity). */
	public long decisionSeq() {
		return decisionSeq;
	}

	/** Invite-only lobby guests. */
	public java.util.Set<UUID> guests() {
		return java.util.Set.copyOf(guests);
	}

	public long drawnTick() {
		return drawnTick;
	}

	/** Payouts per participant after SETTLE (empty before). */
	public long[] payouts() {
		return state == MatchState.SETTLED ? payouts.clone() : new long[0];
	}

	/** One revealed place of the Final Reveal. */
	public record Placing(int place, int participant, long points) {}

	/**
	 * Place {@code place} (1 = winner) once the Final Reveal has shown it; empty before (the ranking is never
	 * exposed ahead of the reveal).
	 */
	public java.util.Optional<Placing> placing(int place) {
		if (outcome == null || place < 1 || place > participants.size()) {
			return java.util.Optional.empty();
		}
		boolean shown = state == MatchState.SETTLED || place > participants.size() - revealedPlaces;
		if (!shown) {
			return java.util.Optional.empty();
		}
		int idx = outcome.rankOrder()[place - 1];
		return java.util.Optional.of(new Placing(place, idx, outcome.points()[idx]));
	}

	public List<Participant> participants() {
		return List.copyOf(participants);
	}

	public @Nullable UUID host() {
		return host;
	}

	/** Null until SETTLE computed it (never expose before the reveal ends). */
	public @Nullable Outcome outcome() {
		return state == MatchState.SETTLED ? outcome : null;
	}

	public long pot() {
		long p = 0;
		for (Participant x : participants) {
			p += x.stake();
		}
		return p;
	}

	public long rake() {
		return rake;
	}

	public boolean grudge() {
		return grudge;
	}

	public boolean hasBots() {
		return participants.stream().anyMatch(Participant::isBot);
	}

	public boolean hasHumanOpponentFor(UUID player) {
		return participants.stream().anyMatch(p -> !p.isBot() && !p.occupant.key().equals(player.toString()));
	}
}
