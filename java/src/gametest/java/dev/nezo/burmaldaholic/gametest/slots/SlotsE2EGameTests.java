package dev.nezo.burmaldaholic.gametest.slots;

import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.Clock;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotDraw;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotRng;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SlotTimeline;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.logic.TapeCodec;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotFrames;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotScript;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * End to end, per machine (SLOTS.md §15; the Java twin of the Bedrock e2e): a base win, free spins, the machine's bonus
 * game (Treasure Hunt / Piglin's Hoard / Dragon Wheel) and a jackpot, each through the real block entity (stake, pools,
 * persisted tape, Treasure Hunt picks, reveal gate, settlement) with only the RNG draw replaced by a seeded search. For
 * every round:
 * <ul>
 *   <li>the settled amount equals what the screen ends on: the pure present model (the timeline the screen builds from
 *       the tape the server publishes, re-built when a hunt completes) rolls up the spin total and then celebrates the
 *       jackpots; their sum is the payout;</li>
 *   <li>the screen's terminal frame, the block's rest window and the cabinet sync all end on the same stops and cells;</li>
 *   <li>chips are conserved: balance = start − stake + payout, and the stake is closed.</li>
 * </ul>
 */
public class SlotsE2EGameTests {
	private static final Transaction TEST = Transaction.of("slots", "gametest");

	private record Scenario(String name, Predicate<SpinTape> want) {}

	private static List<Scenario> scenarios(Tier tier) {
		List<Scenario> out = new ArrayList<>();
		out.add(new Scenario("base win", t -> t.totalFifths() > 0 && !t.featureTriggered() && t.jackpots().isEmpty()));
		out.add(new Scenario("free spins", t -> t.freeSpins() != null && t.hunt() == null && t.hoard() == null && t.wheel() == null));
		out.add(switch (tier) {
			case COPPER -> new Scenario("treasure hunt", t -> t.hunt() != null && t.freeSpins() == null);
			case GOLD -> new Scenario("piglin's hoard", t -> t.hoard() != null && t.freeSpins() == null);
			case NETHERITE -> new Scenario("dragon wheel", t -> t.wheel() != null && t.freeSpins() == null && t.wheel().segments().length >= 2);
		});
		out.add(new Scenario("jackpot", t -> !t.jackpots().isEmpty()));
		return out;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		return p;
	}

	@GameTest(maxTicks = 400)
	public void overworldEndToEnd(GameTestHelper helper) {
		machine(helper, Tier.COPPER);
	}

	@GameTest(maxTicks = 400)
	public void netherEndToEnd(GameTestHelper helper) {
		machine(helper, Tier.GOLD);
	}

	@GameTest(maxTicks = 400)
	public void endEndToEnd(GameTestHelper helper) {
		machine(helper, Tier.NETHERITE);
	}

	private static void machine(GameTestHelper helper, Tier tier) {
		MinecraftServer server = helper.getLevel().getServer();
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, SlotsModule.MACHINES.get(tier).block());
		SlotMachineBlockEntity be = helper.getBlockEntity(pos, SlotMachineBlockEntity.class);
		var cfg = dev.nezo.burmaldaholic.games.slots.SlotMachinesV2.cfg(be.machineV2());
		int vipBefore = cfg.minVipTier;
		cfg.minVipTier = 0; // the test player is Bronze; End Void's Gold gate has its own test
		// only the slot round moves chips: no contract rewards (slots_feature), no chaos events (Golden Hour bonuses)
		boolean contractsBefore = CasinoConfig.contracts().enabled;
		double rewardBefore = CasinoConfig.contracts().rewardMultiplier;
		boolean chaosBefore = CasinoConfig.chaos().enabled;
		CasinoConfig.contracts().enabled = false;
		CasinoConfig.contracts().rewardMultiplier = 0;
		CasinoConfig.chaos().enabled = false;
		ServerPlayer player = player(helper, 10_000_000);
		try {
			int seed = 1;
			for (Scenario s : scenarios(tier)) {
				int[] found = {seed};
				be.drawForTesting(req -> {
					for (int k = found[0]; k < found[0] + 3_000_000; k++) {
						SpinTape t = SlotDraw.draw(req, SlotRng.seeded(k));
						if (s.want().test(t)) {
							found[0] = k + 1;
							return t;
						}
					}
					throw new IllegalStateException("no " + s.name() + " tape");
				});
				round(helper, server, be, player, tier + " " + s.name());
				seed = found[0];
			}
		} finally {
			be.drawForTesting(null);
			cfg.minVipTier = vipBefore;
			CasinoConfig.contracts().enabled = contractsBefore;
			CasinoConfig.contracts().rewardMultiplier = rewardBefore;
			CasinoConfig.chaos().enabled = chaosBefore;
			be.leave(player.getUUID(), SlotMachineBlockEntity.LeaveReason.LEFT);
			server.getPlayerList().remove(player);
		}
		helper.succeed();
	}

	private static void round(GameTestHelper helper, MinecraftServer server, SlotMachineBlockEntity be, ServerPlayer player, String what) {
		long start = Economies.get().balance(player);
		int[] prevStops = be.restStops();
		int[] prevCells = be.restCells();
		CompoundTag args = new CompoundTag();
		long bet = be.writeClientState(player).getCompoundOrEmpty("v2").getLongArray("bets").orElse(new long[] {5})[0];
		args.putLong("bet", bet);
		be.onAction(player, "spin", args);
		SpinTape drawn = be.roundTape();
		helper.assertTrue(drawn != null, what + ": round started");
		long stake = start - Economies.get().balance(player);
		helper.assertTrue(stake == bet, what + ": stake " + stake);
		MachineDef def = dev.nezo.burmaldaholic.games.slots.SlotMachinesV2.def(be.machineV2());

		// the screen: the tape section and timeline seed it receives at the spin start
		CompoundTag spin = be.writeClientState(player).getCompoundOrEmpty("v2").getCompoundOrEmpty("spin");
		SpinTape shown = TapeCodec.decode(spin.getStringOr("tape", ""));
		int speed = spin.getIntOr("speed", 100);
		int cosmetic = spin.getIntOr("seed", 0);
		boolean anticipation = spin.getBooleanOr("anticipation", true);
		int[] tiers = CasinoConfig.slots().bigWinTiers;
		Timeline first = SlotTimeline.build(shown, def, TimingProfile.SHARED.withSpeed(speed), TimingProfile.SHARED, cosmetic, anticipation, tiers);

		// Treasure Hunt: the shared clock pauses at the end of the intro; every pick publishes the next entry
		if (drawn.hunt() != null) {
			helper.assertTrue(shown.totalFifths() < 0 && shown.hunt().opened() == 0, what + ": nothing of the hunt is sent before the picks");
			be.fireRoundTimerForTesting(); // the pause
			int picks = 0;
			while (be.roundTape() != null && be.writeClientState(player).getCompoundOrEmpty("v2").getCompoundOrEmpty("spin").getIntOr("hold_ms", -1) >= 0) {
				CompoundTag pick = new CompoundTag();
				pick.putInt("chest", (picks * 7) % 15);
				be.onAction(player, "pick", pick);
				picks++;
				helper.assertTrue(picks <= 15, what + ": the hunt ends");
				SpinTape now = TapeCodec.decode(be.writeClientState(player).getCompoundOrEmpty("v2").getCompoundOrEmpty("spin").getStringOr("tape", ""));
				helper.assertTrue(now.hunt().opened() == picks, what + ": pick " + picks + " reveals entry " + picks);
			}
			shown = TapeCodec.decode(be.writeClientState(player).getCompoundOrEmpty("v2").getCompoundOrEmpty("spin").getStringOr("tape", ""));
			helper.assertTrue(shown.totalFifths() >= 0, what + ": the full tape arrives when the hunt is over");
			CabinetSync hunting = be.cabinetSync();
			helper.assertTrue(hunting != null && hunting.hunt() != null && hunting.hunt().endMs() >= 0
				&& hunting.hunt().cells().length == picks, what + ": the cabinet shows the picks");
		}
		Timeline tl = SlotTimeline.build(shown, def, TimingProfile.SHARED.withSpeed(speed), TimingProfile.SHARED, cosmetic, anticipation, tiers);
		helper.assertTrue(first.sharedEndMs() == tl.sharedEndMs(), what + ": the shared part never depends on hidden entries");
		for (int i = 0, j = 0; i < first.beats().size(); i++) {
			Beat b = first.beats().get(i);
			if (b.clock() != Clock.SHARED) continue;
			while (tl.beats().get(j).clock() != Clock.SHARED) j++;
			helper.assertTrue(b.equals(tl.beats().get(j++)), what + ": identical shared beat " + b);
		}
		SlotScript script = new SlotScript(tl, def, prevStops, prevCells);
		SlotFrames.Sampler sampler = new SlotFrames.Sampler(script);
		sampler.sample(tl.endMs());
		SlotFrames.Frame end = sampler.snapshot();

		// the gate: settle
		be.fireRoundTimerForTesting();
		helper.assertTrue(be.roundTape() == null, what + ": settled at the gate");
		helper.assertTrue(be.stakeOf(player.getUUID()) == 0, what + ": stake closed");
		SpinTape settled = TapeCodec.decode(be.writeClientState(player).getCompoundOrEmpty("v2").getCompoundOrEmpty("result").getStringOr("tape", ""));
		long paid = Economies.get().balance(player) - (start - stake);
		helper.assertTrue(paid == settled.payoutChips(), what + ": chips conserved (paid " + paid + ", tape " + settled.payoutChips() + ")");

		// the screen's final value: the roll-up of the spin total, then the jackpot celebrations
		long rolled = 0;
		long celebrated = 0;
		int rollAt = -1;
		int firstJackpot = Integer.MAX_VALUE;
		for (Beat b : tl.beats()) {
			if (b.kind().equals(SlotTimeline.ROLLUP)) {
				rolled += Integer.toUnsignedLong(b.arg(0));
				rollAt = b.at();
			}
			if (b.kind().equals(SlotTimeline.JACKPOT)) {
				if (!settled.jackpots().get(b.lane()).owned()) celebrated += Integer.toUnsignedLong(b.arg(1));
				firstJackpot = Math.min(firstJackpot, b.at());
			}
		}
		helper.assertTrue(rolled + celebrated == paid, what + ": screen ends on " + (rolled + celebrated) + ", settled " + paid);
		helper.assertTrue(firstJackpot == Integer.MAX_VALUE || rollAt < 0 || rollAt < firstJackpot, what + ": roll-up before the jackpots");

		// one terminal: screen frames, block rest window, cabinet
		CabinetSync cabinet = be.cabinetSync();
		helper.assertTrue(cabinet != null, what + ": cabinet published");
		helper.assertTrue(Arrays.equals(cabinet.finalStops(), be.restStops()), what + ": cabinet stops " + Arrays.toString(cabinet.finalStops())
			+ " vs rest " + Arrays.toString(be.restStops()));
		helper.assertTrue(Arrays.equals(cabinet.finalWindow(), be.restCells()), what + ": cabinet window = rest window");
		helper.assertTrue(Arrays.equals(end.cells(), be.restCells()), what + ": screen terminal frame = rest window");
	}
}
