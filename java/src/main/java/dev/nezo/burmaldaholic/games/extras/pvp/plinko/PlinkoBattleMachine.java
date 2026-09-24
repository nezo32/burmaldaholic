package dev.nezo.burmaldaholic.games.extras.pvp.plinko;

import dev.nezo.burmaldaholic.core.bots.Bots;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.pvp.Participant;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpModes;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.AnchorKind;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Plinko Battle entries on the Plinko machine (PVP.md §7.4, §3.3.2): the machine's screen state carries
 * the set-up choices and the open battles near this machine; the machine forwards the {@code pvp_*}
 * table actions here. Server thread only. Everything else (escrow, lobby timer, seating, reveal) is the
 * engine's ({@link Pvp#service()}).
 *
 * <p>Actions (args are untrusted): {@code pvp_host} (amount, risk, balls, policy, difficulty, size),
 * {@code pvp_join} (id), {@code pvp_start} (id), {@code pvp_leave}.
 */
public final class PlinkoBattleMachine {
	public static final String PREFIX = "pvp_";

	private PlinkoBattleMachine() {}

	private static Optional<PlinkoBattleMode> mode() {
		return PvpModes.get(PlinkoBattleMode.ID).filter(PlinkoBattleMode.class::isInstance).map(PlinkoBattleMode.class::cast);
	}

	/** Adds {@code pvp} to the machine's client state (absent when the mode is off). */
	public static void writeState(CompoundTag tag, ServerPlayer viewer) {
		Optional<PlinkoBattleMode> m = mode();
		if (m.isEmpty() || !m.get().enabled()) {
			return;
		}
		PlinkoBattleMode mode = m.get();
		CompoundTag pvp = new CompoundTag();
		ListTag balls = new ListTag();
		for (int b : mode.ballChoices()) {
			balls.add(IntTag.valueOf(b));
		}
		pvp.put("balls", balls);
		pvp.putInt("balls_default", mode.defaults().balls());
		pvp.putInt("max_players", mode.maxPlayers());
		pvp.putLong("min_stake", CasinoConfig.pvp().minStake);
		boolean bots = Bots.enabled();
		pvp.putBoolean("bots", bots);
		pvp.putInt("bots_max", bots ? CasinoConfig.bots().pvp.maxPerMatch : 0);
		PvpService service = Pvp.service();
		Optional<PvpMatch> mine = service.matchOf(viewer.getUUID());
		mine.ifPresent(match -> {
			pvp.putString("in_match", match.id);
			pvp.putBoolean("host", viewer.getUUID().equals(match.host()));
			pvp.putBoolean("lobby", match.state() == MatchState.LOBBY);
		});
		ListTag lobbies = new ListTag();
		for (PvpMatch match : service.lobbiesNear(viewer, PlinkoBattleMode.ID)) {
			if (match.state() != MatchState.LOBBY) {
				continue;
			}
			List<Participant> ps = match.participants();
			CompoundTag row = new CompoundTag();
			row.putString("id", match.id);
			String hostKey = match.host() == null ? "" : match.host().toString();
			row.putString("host", ps.stream().filter(p -> p.occupant.key().equals(hostKey)).findFirst().or(() -> ps.stream().findFirst())
				.map(p -> p.occupant.name()).orElse(""));
			row.putLong("entry", ps.isEmpty() ? 0 : ps.get(0).stake());
			row.putInt("players", ps.size());
			// MIXED: the chosen table size (host + bot seats); HUMANS_ONLY: the mode's max
			int size = match.seating.policy() == SeatPolicy.MIXED ? match.seating.count() + 1 : mode.maxPlayers();
			row.putInt("max", Math.max(ps.size(), Math.min(size, mode.maxPlayers())));
			row.putBoolean("mixed", match.seating.policy() == SeatPolicy.MIXED);
			try {
				PlinkoBattleMode.Params p = mode.decodeParams(match.params);
				row.putString("risk", p.risk());
				row.putInt("balls", p.balls());
			} catch (RuntimeException e) {
				continue;
			}
			lobbies.add(row);
		}
		pvp.put("lobbies", lobbies);
		tag.put("pvp", pvp);
	}

	/** Handles one {@code pvp_*} action; returns the error to show, or null (then the caller re-syncs the screen). */
	public static @Nullable Component onAction(ServerPlayer player, String action, CompoundTag args, ServerLevel level, BlockPos pos) {
		Optional<PlinkoBattleMode> m = mode();
		if (m.isEmpty() || !m.get().enabled()) {
			return Component.translatable("gui.burmaldaholic.error.disabled");
		}
		PlinkoBattleMode mode = m.get();
		PvpService service = Pvp.service();
		return switch (action) {
			case "pvp_host" -> {
				PlinkoBattleMode.Params params = new PlinkoBattleMode.Params(args.getStringOr("risk", ""), args.getIntOr("balls", 0));
				String invalid = mode.validate(params);
				if (invalid != null || Plinko.Risk.parse(params.risk()) == null) {
					yield Component.translatable(invalid != null ? invalid : "gui.burmaldaholic.error.invalid_bet_position");
				}
				long stake = args.getLongOr("amount", 0);
				BotSettings seating = seating(args, mode.maxPlayers());
				Result<PvpMatch> r = service.openLobby(player, PlinkoBattleMode.ID, mode.encodeParams(params), stake,
					new PvpService.Anchor(AnchorKind.PLINKO_MACHINE, level, pos), seating, false);
				yield r.isOk() ? null : r.error();
			}
			case "pvp_join" -> {
				String id = args.getStringOr("id", "");
				Optional<PvpMatch> match = service.get(id).filter(x -> PlinkoBattleMode.ID.equals(x.mode));
				if (match.isEmpty() || match.get().state() != MatchState.LOBBY) {
					yield Component.translatable("gui.burmaldaholic.pvp.error.lobby_gone");
				}
				List<Participant> ps = match.get().participants();
				long entry = ps.isEmpty() ? 0 : ps.get(0).stake();
				Result<PvpMatch> r = service.join(player, id, entry);
				yield r.isOk() ? null : r.error();
			}
			case "pvp_start" -> {
				Result<PvpMatch> r = service.start(player, args.getStringOr("id", ""));
				yield r.isOk() ? null : r.error();
			}
			case "pvp_leave" -> {
				service.leave(player);
				yield null;
			}
			default -> null;
		};
	}

	/**
	 * Seating from the set-up (§3.15.1): policy, difficulty, table size (2 … mode max). The bot count is the
	 * table size − 1 (the host's seat); BOTS_ONLY = exactly that many, MIXED = up to that many.
	 */
	static BotSettings seating(CompoundTag args, int maxPlayers) {
		SeatPolicy policy = Bots.enabled() ? SeatPolicy.byId(args.getStringOr("policy", ""), SeatPolicy.HUMANS_ONLY) : SeatPolicy.HUMANS_ONLY;
		BotDifficulty difficulty = BotDifficulty.NORMAL;
		String d = args.getStringOr("difficulty", "");
		for (BotDifficulty x : BotDifficulty.values()) {
			if (x.id().equals(d)) {
				difficulty = x;
			}
		}
		int size = Math.max(2, Math.min(maxPlayers, args.getIntOr("size", maxPlayers)));
		return new BotSettings(policy, policy == SeatPolicy.HUMANS_ONLY ? 0 : size - 1, difficulty, false, true, BotSpeed.NORMAL);
	}
}
