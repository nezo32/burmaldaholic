package dev.nezo.burmaldaholic.gametest.poker;

import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.games.poker.PokerModule;
import dev.nezo.burmaldaholic.games.poker.PokerTableBlockEntity;
import dev.nezo.burmaldaholic.games.poker.client.PokerScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Poker screen in a real client (screenshots in EN and RU): stake chooser, then a running hand against
 * bots. {@code xvfb-run -a ./gradlew runClientGameTest}; screenshots land in
 * {@code build/run/clientGameTest/screenshots}.
 */
public class PokerClientGameTests implements FabricClientGameTest {
	private static final BlockPos[] TABLE = new BlockPos[1];

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create()) {
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().offset(2, 0, 0);
				TABLE[0] = pos;
				server.overworld().setBlockAndUpdate(pos, PokerModule.TABLE.block().defaultBlockState());
				if (server.overworld().getBlockEntity(pos) instanceof PokerTableBlockEntity table) {
					player.openMenu(table);
					table.sendStateTo(player);
				}
			});
			context.waitForScreen(PokerScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("burmaldaholic_poker_join_en");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				if (server.overworld().getBlockEntity(TABLE[0]) instanceof PokerTableBlockEntity table) {
					CompoundTag args = new CompoundTag();
					args.putString("level", "micro");
					args.putLong("amount", 200);
					table.onAction(player, "buy_in", args);
				}
			});
			// wait until it is our turn in a running hand
			context.waitFor(mc -> mc.gui.screen() instanceof PokerScreen screen && screen.isMyTurn(), 2000);
			context.waitTicks(3);
			context.takeScreenshot("burmaldaholic_poker_turn_en");
			language(context, "ru_ru");
			context.waitFor(mc -> mc.gui.screen() instanceof PokerScreen screen && screen.isMyTurn(), 2000);
			context.waitTicks(3);
			context.takeScreenshot("burmaldaholic_poker_turn_ru");
			// play the hand out (check/call) and look at the result view
			for (int i = 0; i < 400; i++) {
				boolean done = world.getServer().computeOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					if (!(server.overworld().getBlockEntity(TABLE[0]) instanceof PokerTableBlockEntity table)) {
						return true;
					}
					CompoundTag st = table.writeClientState(player);
					CompoundTag legal = st.getCompoundOrEmpty("legal");
					if (!legal.isEmpty()) {
						CompoundTag act = new CompoundTag();
						act.putString("kind", legal.getBooleanOr("can_check", false) ? "check" : "call");
						act.putInt("seq", legal.getIntOr("seq", 0));
						table.onAction(player, "act", act);
					}
					return !st.getBooleanOr("live", true) && !st.getListOrEmpty("result").isEmpty();
				});
				if (done) {
					break;
				}
				context.waitTicks(5);
			}
			context.waitTicks(3);
			context.takeScreenshot("burmaldaholic_poker_result_ru");
			// wide layout (GUI scale 1)
			context.runOnClient(mc -> {
				mc.options.guiScale().set(1);
				mc.resizeGui();
			});
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				if (server.overworld().getBlockEntity(TABLE[0]) instanceof PokerTableBlockEntity table) {
					player.closeContainer();
					player.openMenu(table);
					table.sendStateTo(player);
				}
			});
			context.waitTicks(10);
			context.takeScreenshot("burmaldaholic_poker_wide_ru");
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			language(context, "en_us");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	private static void language(ClientGameTestContext context, String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitTicks(60);
	}
}
