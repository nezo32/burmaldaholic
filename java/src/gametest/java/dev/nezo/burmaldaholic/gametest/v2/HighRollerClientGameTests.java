package dev.nezo.burmaldaholic.gametest.v2;

import dev.nezo.burmaldaholic.client.ui.v2.BetEntry;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.client.BlackjackScreen;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * Lane HR (owner playtest E4 / B5), REAL FLOW: a blackjack table opened by using the block; the bet-entry strip is opened
 * with the E key, an amount is TYPED with real key presses (digit keys of the running version, 26.3 SDL3 included),
 * Enter sets the bet; then E again, a click on Max at its real pixel fills in the VIP maximum and the status line says
 * whose cap it is; OK, and Deal — the SERVER takes exactly that bet. Filter {@code BURMALDAHOLIC_CGT=HR}.
 */
public class HighRollerClientGameTests implements FabricClientGameTest {
	private static final long VIP_MAX = 20_000;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("HR")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		int[] saved = new int[1];
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 100000");
			world.getServer().runOnServer(server -> {
				CasinoConfig.chaos().enabled = false;
				CasinoConfig.contracts().enabled = false;
				CasinoConfig.streak().enabled = false;
				saved[0] = CasinoConfig.vip().maxBet.bronze;
				CasinoConfig.vip().maxBet.bronze = (int) VIP_MAX; // a Bronze high roller: the VIP tier is the cap
			});
			world.getServer().runCommand("time set noon");
			RealFlow f = new RealFlow(context, world);
			f.language("en_us");
			f.size(1920, 1080, 4);
			BlockPos pos = f.place(BlackjackModule.TABLE);
			f.open(pos, BlackjackScreen.class);
			f.click(KeyMap.Ids.PRIMARY); // Sit down
			UUID me = f.server(server -> RealFlow.player(server).getUUID());
			f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated by the Sit primary");
			f.waitChrome(c -> c.bet.visible() && c.bet.view().canBet(), "bets open");

			// 1. type an amount: E opens the strip, digit keys type, Enter sets the bet (a local draft until Deal)
			f.key(KeyMap.E);
			f.waitChrome(c -> c.entry.isOpen() && f.box(BetEntry.MAX).isPresent(), "E opens the bet entry");
			for (char c : "2500".toCharArray()) f.key(String.valueOf(c));
			String typed = f.client(mc -> ((dev.nezo.burmaldaholic.client.ui.v2.V2Chrome.Holder) mc.gui.screen()).chrome().entry.text());
			if (!typed.equals("2500")) throw new AssertionError("typed '" + typed + "', expected 2500");
			f.key(KeyMap.ENTER);
			f.waitChrome(c -> !c.entry.isOpen() && c.bet.view().myTotal() == 2500, "Enter sets the bet to 2 500");
			f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.stakeOf(me) == 0, "a draft: nothing sent before Deal");

			// 2. Max: the VIP maximum, and the status says it is the VIP tier's
			f.key(KeyMap.E);
			f.waitChrome(c -> c.entry.isOpen() && f.box(BetEntry.MAX).isPresent(), "E opens the bet entry again");
			f.click(BetEntry.MAX);
			f.waitChrome(c -> c.entry.value() == VIP_MAX, "Max fills in the VIP maximum " + VIP_MAX);
			String status = f.status();
			String shownMax = f.client(mc -> Texts.number(VIP_MAX).getString());
			if (!status.contains(shownMax) || !status.contains("VIP")) throw new AssertionError("Max should say whose cap: '" + status + "'");
			f.click(BetEntry.OK);
			f.waitChrome(c -> !c.entry.isOpen() && c.bet.view().myTotal() == VIP_MAX, "OK sets the bet to the maximum");
			f.click(KeyMap.Ids.PRIMARY); // Deal
			f.waitServer(pos, BlackjackTableBlockEntity.class, t -> t.stakeOf(me) == VIP_MAX, "the server took the Max bet of " + VIP_MAX);

			f.close();
			// T-HR D4: the cashier's counting-tray head fits the largest one-move amount in EN and RU (8 chip columns now)
			for (String lang : java.util.List.of("en_us", "ru_ru")) {
				f.language(lang);
				for (String k : java.util.List.of("withdrawn", "deposited", "credited", "paid")) {
					int w = f.client(mc -> mc.font.width(net.minecraft.network.chat.Component.translatable("gui.burmaldaholic.cashier." + k,
						Texts.number(57_600_000L))));
					int room = dev.nezo.burmaldaholic.client.cashier.CashierScreen.trayHeadWidth();
					if (w > room) throw new AssertionError(lang + " cashier." + k + " head is " + w + " px, the tray leaves " + room);
				}
			}
			f.language("en_us");
		} finally {
			context.runOnClient(mc -> {
				if (saved[0] > 0) CasinoConfig.vip().maxBet.bronze = saved[0];
			});
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
	}
}
