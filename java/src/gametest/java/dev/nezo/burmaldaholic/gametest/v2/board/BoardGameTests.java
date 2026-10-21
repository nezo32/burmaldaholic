package dev.nezo.burmaldaholic.gametest.v2.board;

import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.Purse;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TablePublic;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.craps.CrapsModule;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.roulette.RouletteModule;
import dev.nezo.burmaldaholic.games.uth.UthModule;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lane W (in-world board kit): server GameTests of the board's public data (docs/architecture/ui-v2.md §7.2). The
 * update tag every passer-by receives must carry the seat header ({@link TablePublic}) — names for the seat plates —
 * on every multi-seat table, including the games that override {@code getUpdateTag} (finding R-KW B3 / m16).
 */
public class BoardGameTests {
	/**
	 * Blackjack, baccarat (+ player-banked chemmy), Hold'em, UTH (+ player-banked), roulette and craps, each with two
	 * bots seated through the bot hook ({@link BotTable#seatBot}, the safe-point call): the update tag decodes to a
	 * header with both bots, by name key, flagged as bots.
	 */
	private static final Transaction TEST = Transaction.of("poker", "gametest");

	@GameTest
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: the only mock player API on 26.2
	public void updateTagsCarrySeatHeader(GameTestHelper helper) {
		List<TableType<?>> types = List.of(BlackjackModule.TABLE, BaccaratModule.TABLE, BaccaratModule.CHEMMY_TABLE, PokerModule.TABLE, UthModule.TABLE,
			UthModule.PLAYER_BANKED_TABLE, RouletteModule.TABLE, CrapsModule.TABLE);
		List<String> failures = new ArrayList<>();
		List<ServerPlayer> hosts = new ArrayList<>();
		int x = 1;
		for (TableType<?> type : types) {
			BlockPos pos = new BlockPos(x, 1, 1);
			x += 2;
			helper.setBlock(pos, type.block());
			if (!(helper.getBlockEntity(pos, CasinoTableBlockEntity.class) instanceof CasinoTableBlockEntity be) || !(be instanceof BotTable table)) {
				failures.add(type.name() + ": not a bot table");
				continue;
			}
			if (type == PokerModule.TABLE) {
				// Hold'em creates its table model on the first buy-in (stakes level): a mock player buys in first
				ServerPlayer host = helper.makeMockServerPlayerInLevel();
				hosts.add(host);
				Economies.get().setBalance(helper.getLevel().getServer(), host.getUUID(), 10_000, TEST);
				CompoundTag args = new CompoundTag();
				args.putString("level", "micro");
				args.putLong("amount", 200);
				be.onAction(host, "buy_in", args);
			}
			int seated = 0;
			for (int i = 0; i < 2; i++) {
				BotProfile p = new BotProfile("bw2t" + i, i == 0 ? "creeper42" : "axolotl_al", BotDifficulty.NORMAL, Personality.TAG);
				boolean money = table.botRole() == BotRole.MONEY;
				SeatOccupant.Bot bot = new SeatOccupant.Bot(p, table.botRole(), money ? Purse.BANK : Purse.NONE);
				if (table.seatBot(bot, money ? Math.max(200, table.botBuyIn()) : 0)) seated++;
			}
			CompoundTag tag = be.getUpdateTag(helper.getLevel().registryAccess());
			TablePublic.SeatPub[] seats = TablePublic.readSeats(tag);
			int bots = 0;
			for (TablePublic.SeatPub s : seats) if (s != null && s.bot() && !s.name().isEmpty()) bots++;
			if (seated != 2) failures.add(type.name() + ": seatBot accepted " + seated + "/2");
			else if (bots != 2) failures.add(type.name() + ": update tag header shows " + bots + " bots (seats " + seats.length + ")");
		}
		for (ServerPlayer h : hosts) helper.getLevel().getServer().getPlayerList().remove(h);
		helper.assertTrue(failures.isEmpty(), String.join("; ", failures));
		helper.succeed();
	}
}
