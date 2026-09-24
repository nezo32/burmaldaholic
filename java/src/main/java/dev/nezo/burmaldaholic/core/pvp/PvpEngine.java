package dev.nezo.burmaldaholic.core.pvp;

import com.google.gson.JsonElement;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.pvp.logic.HeadToHead;
import dev.nezo.burmaldaholic.core.util.Result;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * SKELETON of the PvP engine (owner: dev "PvP core", docs/architecture/pvp-bots.md §7 task J-P1).
 * Every public entry point currently answers "disabled" so nothing changes in game until the engine is
 * implemented. Implementation notes per method refer to PVP.md sections.
 */
final class PvpEngine implements PvpService {
	private final Map<String, PvpMatch> matches = new LinkedHashMap<>();

	void registerLifecycle() {
		// TODO(J-P1): SERVER_STARTED → PvpMatchData.get(server): LOBBY → refund (offline-safe, lobby.refunded on join),
		//   DRAWN → settle from tape (result.offline on join); SERVER_STOPPING → settle DRAWN before the save;
		//   CasinoMode.onChange(off) → withdraw invites, refund lobbies, settle DRAWN (result.casino_off);
		//   END_SERVER_TICK → timers (invite / lobby / countdown / steps / decisions / rematch), bot pacing.
	}

	private static <T> Result<T> notYet() {
		return Result.fail(Component.translatable("gui.burmaldaholic.error.disabled"));
	}

	@Override
	public Result<PvpMatch> challenge(ServerPlayer challenger, String mode, JsonElement params, long stake, Opponent opponent) {
		return notYet(); // TODO(J-P1) §3.2 eligibility both sides, §3.3.1 invite; bot opponent: accept after think time (BOTS.md §3.6)
	}

	@Override
	public Result<PvpMatch> accept(ServerPlayer target, String matchId) {
		return notYet(); // TODO(J-P1) escrow (one batch) → STARTING → draw tape (fair rng) → persist DRAWN → reveal
	}

	@Override
	public void decline(ServerPlayer target, String matchId) {
		// TODO(J-P1) DECLINED + pvp.declineCooldownTicks for the pair
	}

	@Override
	public void withdraw(ServerPlayer challenger, String matchId) {
		// TODO(J-P1)
	}

	@Override
	public Result<PvpMatch> openLobby(ServerPlayer host, String mode, JsonElement params, long stake, Anchor anchor, BotSettings seating,
			boolean inviteOnly) {
		return notYet(); // TODO(J-P1) §3.3.2 + BOTS.md §3.4/§3.6 seating; owned anchor without money bots → HUMANS_ONLY + no_bots_here
	}

	@Override
	public Result<PvpMatch> join(ServerPlayer player, String matchId, long stake) {
		return notYet();
	}

	@Override
	public Result<Long> topUp(ServerPlayer player, String matchId, long extra) {
		return notYet(); // TODO(J-P1) Wheel Party only, ≤ min(cap, tier max), before No more bets
	}

	@Override
	public void leave(ServerPlayer player) {
		// TODO(J-P1) LOBBY: refund at once; host leaves → earliest joiner hosts (bots never host)
	}

	@Override
	public Result<PvpMatch> start(ServerPlayer host, String matchId) {
		return notYet();
	}

	@Override
	public void fillWithBots(ServerPlayer host, String matchId) {
		// TODO(J-P1) BOTS.md §3.4
	}

	@Override
	public void press(ServerPlayer player) {
		// TODO(J-P1)
	}

	@Override
	public void decide(ServerPlayer player, String decision, long option) {
		// TODO(J-P1) Coin Flip Duel chain (§4.2) — the new link is a NEW match (chainOf, link+1)
	}

	@Override
	public Result<Void> taunt(ServerPlayer player, int line) {
		return notYet(); // TODO(J-P1) §3.9 cooldown / per-match cap / pvp.taunts.enabled
	}

	@Override
	public void rematch(ServerPlayer player, String matchId) {
		// TODO(J-P1) §3.10
	}

	@Override
	public Optional<PvpMatch> matchOf(UUID player) {
		String key = player.toString();
		return matches.values().stream().filter(m -> m.participants.stream().anyMatch(p -> p.occupant.key().equals(key))).findFirst();
	}

	@Override
	public Optional<PvpMatch> get(String matchId) {
		return Optional.ofNullable(matches.get(matchId));
	}

	@Override
	public List<PvpMatch> lobbiesNear(ServerPlayer player, @Nullable String mode) {
		return List.of();
	}

	@Override
	public List<PvpMatch> invitesFor(UUID player) {
		return List.of();
	}

	@Override
	public HeadToHead record(UUID a, UUID b) {
		return HeadToHead.EMPTY; // TODO(J-P1) PvpRecordData
	}

	@Override
	public List<Rival> rivals(UUID player, int max) {
		return List.of();
	}

	@Override
	public Stats stats(UUID player) {
		return new Stats(0, 0, 0, 0, null);
	}

	@Override
	public List<PvpMatch> all() {
		return new ArrayList<>(matches.values());
	}

	@Override
	public void cancel(String matchId) {
		// TODO(J-P1)
	}
}
