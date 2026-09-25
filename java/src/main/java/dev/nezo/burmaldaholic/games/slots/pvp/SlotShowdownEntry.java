package dev.nezo.burmaldaholic.games.slots.pvp;

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
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.pvp.SlotShowdownMode.Params;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Machine entry of Slot Showdown (PVP.md §5.5 Java): the slot machine screen's "Slot Showdown" panel sends
 * {@code pvp_*} table actions, routed here by the one hook in {@link SlotMachineBlockEntity#onAction}; the
 * panel's data comes from {@link #clientState} (hooked into {@code writeClientState} under key {@code pvp}).
 * Everything else (eligibility, escrow, lobby timers, bots, reveal) is the PvP engine's.
 *
 * <p>Actions: {@code pvp_open {entry, spins, policy, size}}, {@code pvp_join {id}}, {@code pvp_start},
 * {@code pvp_leave}, {@code pvp_spin} (Spin! = {@link PvpService#press}).
 */
public final class SlotShowdownEntry {
	public static final String STATE_KEY = "pvp";

	private SlotShowdownEntry() {}

	static @Nullable SlotShowdownMode mode() {
		return PvpModes.get(SlotShowdownMode.ID).orElse(null) instanceof SlotShowdownMode m ? m : null;
	}

	/** @return true if the action was a Slot Showdown action (handled here) */
	public static boolean onAction(SlotMachineBlockEntity machine, ServerPlayer player, String action, CompoundTag args) {
		if (!action.startsWith("pvp_")) {
			return false;
		}
		SlotShowdownMode mode = mode();
		if (mode == null || !(machine.getLevel() instanceof ServerLevel level)) {
			return true;
		}
		PvpService pvp = Pvp.service();
		// a Showdown at a machine needs the machine's VIP tier (Netherite High Roller), like a solo spin
		if (("pvp_open".equals(action) || "pvp_join".equals(action)) && !vipOk(machine, player)) {
			return true;
		}
		switch (action) {
			case "pvp_open" -> {
				if (!mode.enabled()) {
					machine.sendError(player, Component.translatable("gui.burmaldaholic.error.disabled"));
					return true;
				}
				Params defaults = mode.defaults(machine.tier());
				Params params = new Params(machine.tier().id(), args.getIntOr("spins", defaults.spins()), defaults.rules());
				String invalid = mode.validate(params);
				if (invalid != null) {
					machine.sendError(player, Component.translatable(invalid));
					return true;
				}
				long entry = args.getLongOr("entry", CasinoConfig.pvp().minStake);
				BotSettings seating = seating(SeatPolicy.byId(args.getStringOr("policy", ""), SeatPolicy.HUMANS_ONLY),
					args.getIntOr("size", mode.maxPlayers()), mode.maxPlayers());
				report(machine, player, pvp.openLobby(player, SlotShowdownMode.ID, mode.encodeParams(params), entry,
					new PvpService.Anchor(AnchorKind.SLOT_MACHINE, level, machine.getBlockPos()), seating, false));
			}
			case "pvp_join" -> {
				String id = args.getStringOr("id", "");
				Optional<PvpMatch> match = pvp.get(id);
				if (match.isEmpty() || !joinable(machine, match.get())) {
					machine.sendError(player, Component.translatable("gui.burmaldaholic.pvp.error.lobby_gone"));
					return true;
				}
				report(machine, player, pvp.join(player, id, entry(match.get())));
			}
			case "pvp_start" -> pvp.matchOf(player.getUUID()).filter(m -> m.state() == MatchState.LOBBY)
				.ifPresent(m -> report(machine, player, pvp.start(player, m.id)));
			case "pvp_leave" -> pvp.leave(player);
			case "pvp_spin" -> pvp.press(player);
			default -> {
			}
		}
		machine.syncViewers();
		return true;
	}

	/**
	 * Seating of a lobby from the set-up panel (PVP.md §3.15.1, BOTS.md): {@code size} = table size; bots may
	 * fill up to {@code size − 1} seats, capped by {@code bots.pvp.maxPerMatch}. Slot Showdown is luck only, so
	 * the difficulty is NORMAL (it only sets bot pacing). Bots off → humans only.
	 */
	static BotSettings seating(SeatPolicy policy, int size, int maxPlayers) {
		int table = Math.max(2, Math.min(maxPlayers, size));
		if (policy == SeatPolicy.HUMANS_ONLY || !CasinoConfig.bots().enabled) {
			return BotSettings.HUMANS_ONLY;
		}
		int bots = Math.min(table - 1, CasinoConfig.bots().pvp.maxPerMatch);
		return new BotSettings(policy, bots, BotDifficulty.NORMAL, false, true, BotSpeed.NORMAL);
	}

	/** The machine's VIP requirement ({@code slots.<m>.minVipTier}); false after telling the player. */
	static boolean vipOk(SlotMachineBlockEntity machine, ServerPlayer player) {
		int min = dev.nezo.burmaldaholic.games.slots.SlotMachinesV2.cfg(machine.machineV2()).minVipTier;
		if (min <= 0 || CoreServices.vip().tier(player.level().getServer(), player.getUUID()) >= min) {
			return true;
		}
		machine.sendError(player, Component.translatable("gui.burmaldaholic.error.vip_required", dev.nezo.burmaldaholic.core.service.VipTiers.name(min)));
		return false;
	}

	private static void report(SlotMachineBlockEntity machine, ServerPlayer player, Result<?> result) {
		if (!result.isOk() && result.error() != null) {
			machine.sendError(player, result.error());
		}
	}

	private static long entry(PvpMatch m) {
		List<Participant> ps = m.participants();
		return ps.isEmpty() ? 0 : ps.getFirst().stake();
	}

	/** Same tier, and this machine is the anchor or within {@code pvp.slots.linkRadius} of it (same dimension). */
	static boolean joinable(SlotMachineBlockEntity machine, PvpMatch m) {
		SlotShowdownMode mode = mode();
		if (mode == null || !m.mode.equals(SlotShowdownMode.ID) || m.state() != MatchState.LOBBY || machine.getLevel() == null) {
			return false;
		}
		Params p;
		try {
			p = mode.decodeParams(m.params);
		} catch (RuntimeException e) {
			return false;
		}
		if (Tier.byId(p.tier()) != machine.tier()) {
			return false;
		}
		GlobalPos here = GlobalPos.of(machine.getLevel().dimension(), machine.getBlockPos());
		if (!here.dimension().equals(m.anchor.dimension())) {
			return false;
		}
		int r = CasinoConfig.pvp().slots.linkRadius;
		return here.pos().equals(m.anchor.pos()) || here.pos().distSqr(m.anchor.pos()) <= (double) r * r;
	}

	/** The Slot Showdown panel data for {@code viewer} (never any tape data). */
	public static CompoundTag clientState(SlotMachineBlockEntity machine, ServerPlayer viewer) {
		CompoundTag t = new CompoundTag();
		SlotShowdownMode mode = mode();
		MinecraftServer server = viewer.level().getServer();
		boolean enabled = mode != null && mode.enabled();
		t.putBoolean("enabled", enabled);
		if (!enabled) {
			return t;
		}
		Params defaults = mode.defaults(machine.tier());
		t.putLong("min", CasinoConfig.pvp().minStake);
		t.putLong("max", CoreServices.vip().maxBet(server, viewer.getUUID()));
		t.putIntArray("choices", mode.settings().spinChoices());
		t.putInt("spins", defaults.spins());
		t.putInt("max_players", mode.maxPlayers());
		t.putBoolean("bots", CasinoConfig.bots().enabled && CasinoConfig.bots().pvp.maxPerMatch > 0);
		t.putInt("star", defaults.rules().starPoints());
		// the rule switches the panel explains (only the rules this game really plays)
		t.putBoolean("hot", defaults.rules().hotSymbol());
		t.putBoolean("underdog", defaults.rules().underdogBoost());
		t.putBoolean("kaboom", defaults.rules().kaboom());
		t.putBoolean("swap", defaults.rules().pearlSwap());
		t.putLong("pearl_points", ShowdownScoring.PEARL_POINTS);
		t.putLong("clock_points", ShowdownScoring.CLOCK_POINTS);
		PvpService pvp = Pvp.service();
		Optional<PvpMatch> mine = pvp.matchOf(viewer.getUUID());
		if (mine.isPresent()) {
			PvpMatch m = mine.get();
			CompoundTag my = new CompoundTag();
			my.putString("id", m.id);
			my.putString("mode", m.mode);
			my.putString("state", m.state().name());
			my.putBoolean("host", viewer.getUUID().equals(m.host()));
			my.putInt("count", m.participants().size());
			my.putLong("entry", entry(m));
			t.put("mine", my);
		}
		ListTag lobbies = new ListTag();
		for (PvpMatch m : pvp.lobbiesNear(viewer, SlotShowdownMode.ID)) {
			if (!joinable(machine, m) || lobbies.size() >= 3) {
				continue;
			}
			CompoundTag l = new CompoundTag();
			l.putString("id", m.id);
			l.putLong("entry", entry(m));
			l.putInt("count", m.participants().size());
			l.putInt("max", mode.maxPlayers());
			try {
				l.putInt("spins", mode.decodeParams(m.params).spins());
			} catch (RuntimeException e) {
				l.putInt("spins", defaults.spins());
			}
			lobbies.add(l);
		}
		t.put("lobbies", lobbies);
		return t;
	}
}
