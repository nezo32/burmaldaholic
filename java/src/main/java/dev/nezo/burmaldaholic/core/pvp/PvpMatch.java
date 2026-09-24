package dev.nezo.burmaldaholic.core.pvp;

import com.google.gson.JsonElement;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.Outcome;
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
