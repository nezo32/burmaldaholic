package dev.nezo.burmaldaholic.gametest.v2.baccarat;

import dev.nezo.burmaldaholic.client.ui.v2.V2Chrome;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.client.BaccaratScreen;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.gametest.v2.RealFlow;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Lane A (baccarat / chemin de fer): REAL-FLOW client GameTests of the v2 screen (docs/architecture/ui-v2.md §11.2).
 * Opened by using the block; every step a click on the widget's window pixel, every step asserted on the SERVER: sit →
 * arm a chip → click the BANKER and TIE boxes → Undo → Deal → the coup plays → settle (balance change = the server's
 * returns) → Rebet. Deal with no bet shows its reason. Chemin de fer: the bank offered to the viewer is taken by clicking
 * the primary. Sizes 480 × 270, 640 × 360 (@4 and @3), 427 × 240; EN and RU. Screenshots {@code v2a_bac_*}.
 */
public class BaccaratV2ClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!RealFlow.enabled("A")) return;
		context.runOnClient(mc -> mc.gui.setScreen(null));
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 100000");
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			world.getServer().runOnServer(server -> {
				dev.nezo.burmaldaholic.core.config.CasinoConfig.contracts().enabled = false; // rewards would move the balance
				dev.nezo.burmaldaholic.core.config.CasinoConfig.streak().enabled = false;
			});
			RealFlow f = new RealFlow(context, world);
			int dx = -12;
			for (String lang : List.of("en_us", "ru_ru")) {
				f.language(lang);
				coup(f, lang, 1920, 1080, 4, dx += 3, true);
				coup(f, lang, 1920, 1080, 3, dx += 3, false);
				if (lang.equals("en_us")) {
					coup(f, lang, 1280, 720, 0, dx += 3, false); // Auto → 427 × 240
					coup(f, lang, 2560, 1440, 2, dx += 3, false); // 1280 × 720 framed
				}
			}
			f.language("en_us");
			f.size(1920, 1080, 4);
			chemmy(f, dx + 3);
			chemmyPunter(f);
			f.close();
		} finally {
			V2Chrome.dropNextSends = 0;
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
	}

	private static void coup(RealFlow f, String lang, int fw, int fh, int scale, int dx, boolean negatives) {
		f.size(fw, fh, scale);
		String tag = "v2a_bac_" + lang + "_" + f.viewport().w() + "x" + f.viewport().h();
		BlockPos pos = fresh(f, BaccaratModule.TABLE.block());
		f.open(pos, BaccaratScreen.class);
		f.shot(tag + "_open");
		f.click(KeyMap.Ids.PRIMARY); // Sit down
		UUID me = f.server(server -> RealFlow.player(server).getUUID());
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated by the Sit primary");
		if (negatives) {
			f.click(KeyMap.Ids.PRIMARY);
			String want = f.client(mc -> Component.translatable(V2Keys.REASON_PLACE_BET).getString());
			if (!f.status().equals(want)) throw new AssertionError("no-bet Deal: status '" + f.status() + "', expected '" + want + "'");
		}
		int chip = chipIndex(f, 100);
		f.click(KeyMap.Ids.chip(chip)); // arm 100
		f.click(KeyMap.Ids.felt("box.banker"));
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(BetKind.BANKER, 0L) > 0, "banker bet placed by a box click");
		int small = chipIndex(f, 5);
		f.click(KeyMap.Ids.chip(small));
		f.click(KeyMap.Ids.felt("box.tie"));
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(BetKind.TIE, 0L) == 5, "tie 5 placed");
		if (negatives) {
			// PLAYER 25 + 25 and P.PAIR 5 too (the five boxes P.PAIR · P · T · B · B.PAIR in one shot)
			int c25 = chipIndex(f, 25);
			f.click(KeyMap.Ids.chip(c25));
			f.click(KeyMap.Ids.felt("box.player"));
			f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(BetKind.PLAYER, 0L) == 25, "player 25");
			f.click(KeyMap.Ids.felt("box.player"));
			f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(BetKind.PLAYER, 0L) == 50, "player 50");
			f.click(KeyMap.Ids.chip(small));
			f.click(KeyMap.Ids.felt("box.player_pair"));
			f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(BetKind.PLAYER_PAIR, 0L) == 5, "p.pair 5");
			f.context().waitTicks(4);
			f.shot(tag + "_five_boxes");
			long bank = f.server(server -> table(server, pos).betsOf(me).getOrDefault(BetKind.BANKER, 0L));
			long bal0 = f.server(server -> Economies.get().balance(RealFlow.player(server)));
			// review R-AB m8: Undo takes back ONE chip (the newest: the 5 on P.PAIR), one packet
			f.click(KeyMap.Ids.tool("undo"));
			f.waitServer(pos, BaccaratTableBlockEntity.class, t -> !t.betsOf(me).containsKey(BetKind.PLAYER_PAIR), "undo took the p.pair chip");
			// right-click PLAYER: one 25 chip off it, not the box
			f.rightClick(KeyMap.Ids.felt("box.player"));
			f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).getOrDefault(BetKind.PLAYER, 0L) == 25, "right-click took one chip off PLAYER");
			f.context().waitTicks(4);
			long bal1 = f.server(server -> Economies.get().balance(RealFlow.player(server)));
			if (bal1 - bal0 != 30) throw new AssertionError("undo + right-click returned " + (bal1 - bal0) + ", expected 5 + 25");
			f.server(server -> {
				var b = table(server, pos).betsOf(me);
				if (b.getOrDefault(BetKind.BANKER, 0L) != bank || b.getOrDefault(BetKind.TIE, 0L) != 5) {
					throw new AssertionError("the other boxes moved: " + b);
				}
				return null;
			});
			f.shot(tag + "_undo");
		}
		f.context().waitTicks(4);
		f.shot(tag + "_bets");
		long staked = f.server(server -> table(server, pos).betsOf(me).values().stream().mapToLong(Long::longValue).sum());
		long before = f.server(server -> Economies.get().balance(RealFlow.player(server)));
		f.click(KeyMap.Ids.PRIMARY); // Deal (ready)
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> !"betting".equals(t.phase()) && !"idle".equals(t.phase()), "the coup started");
		if (negatives) {
			for (int i = 0; i < 80 && !"reveal".equals(f.server(server -> table(server, pos).phase())); i++) f.context().waitTicks(2);
			f.context().waitTicks(30);
			f.shot(tag + "_squeeze");
		}
		for (int i = 0; i < 12; i++) {
			boolean done = f.server(server -> "result".equals(table(server, pos).phase()));
			if (done) break;
			f.server(server -> {
				table(server, pos).fastForwardForTests();
				return null;
			});
			f.context().waitTicks(4);
		}
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> "result".equals(t.phase()), "settled");
		f.context().waitTicks(20);
		f.shot(tag + "_result");
		long after = f.server(server -> Economies.get().balance(RealFlow.player(server)));
		// the balance moved by exactly what the server returned (the stakes were taken when the chips went down)
		long ret = f.client(mc -> mc.gui.screen() instanceof BaccaratScreen s ? s.myReturnForTests() : -1L);
		if (ret < 0) throw new AssertionError("no result on the screen");
		if (after - before != ret) throw new AssertionError("balance moved " + (after - before) + ", the server returned " + ret + " on " + staked);
		// next coup: Rebet (server-side) offered as the primary once betting opens again
		f.server(server -> {
			table(server, pos).fastForwardForTests();
			return null;
		});
		f.waitChrome(c -> c.primary.gate().enabled() && c.primary.gate().action().equals("rebet"), "Rebet offered");
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).values().stream().mapToLong(Long::longValue).sum() == staked, "rebet placed");
		f.click(KeyMap.Ids.tool("clear"));
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.betsOf(me).isEmpty(), "cleared");
		f.close();
	}

	/** Chemin de fer: the bank comes to the viewer; the primary takes it. */
	private static void chemmy(RealFlow f, int dx) {
		BlockPos pos = fresh(f, BaccaratModule.CHEMMY_TABLE.block());
		f.open(pos, BaccaratScreen.class);
		f.click(KeyMap.Ids.PRIMARY);
		UUID me = f.server(server -> RealFlow.player(server).getUUID());
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated at chemin de fer");
		for (int i = 0; i < 30; i++) {
			boolean offered = f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h && h.chrome().primary.gate().enabled()
				&& h.chrome().primary.gate().action().equals("take_bank"));
			if (offered) break;
			f.server(server -> {
				table(server, pos).fastForwardForTests();
				return null;
			});
			f.context().waitTicks(10);
		}
		f.waitChrome(c -> c.primary.gate().enabled() && c.primary.gate().action().equals("take_bank"), "the bank offered to the viewer");
		f.shot("v2a_bac_en_us_chemmy_offer");
		f.click(KeyMap.Ids.PRIMARY);
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> me.equals(t.bank().banker()), "the viewer holds the bank");
		f.context().waitTicks(10);
		f.shot("v2a_bac_en_us_chemmy_bank");
		f.close();
	}

	/**
	 * Chemin de fer as a punter with two bots: the bank offered to the viewer is passed (action Pass), then the viewer punts
	 * with chips + the primary, or calls Banco when that is the open action; the server holds the punt.
	 */
	private static void chemmyPunter(RealFlow f) {
		BlockPos pos = fresh(f, BaccaratModule.CHEMMY_TABLE.block());
		f.server(server -> {
			table(server, pos).bots().setDefaults(new dev.nezo.burmaldaholic.core.bots.logic.BotSettings(dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy.MIXED,
				2, dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty.NORMAL, false, false, dev.nezo.burmaldaholic.core.bots.logic.BotSpeed.FAST));
			return null;
		});
		f.open(pos, BaccaratScreen.class);
		f.click(KeyMap.Ids.PRIMARY);
		UUID me = f.server(server -> RealFlow.player(server).getUUID());
		f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.seats().seatOf(me).isPresent(), "seated at chemin de fer (punter)");
		boolean punted = false;
		boolean passed = false;
		for (int i = 0; i < 80 && !punted; i++) {
			if (f.enabledTarget(KeyMap.Ids.action("pass"))) {
				f.shot("v2a_bac_en_us_chemmy_pass");
				f.click(KeyMap.Ids.action("pass"));
				passed = true;
			} else if (f.client(mc -> mc.gui.screen() instanceof V2Chrome.Holder h && h.chrome().bet.visible()
				&& h.chrome().hits().targets().stream().anyMatch(t -> t.id().startsWith("chip.") && t.enabled()))) {
				int c = chipIndex(f, 5);
				f.click(KeyMap.Ids.chip(c));
				f.waitChrome(x -> x.primary.gate().enabled() && x.primary.gate().action().equals("punt"), "Punt enabled");
				f.shot("v2a_bac_en_us_chemmy_punt");
				f.click(KeyMap.Ids.PRIMARY);
				f.waitServer(pos, BaccaratTableBlockEntity.class, t -> t.bank().punts().containsKey(me), "the punt is held by the table");
				punted = true;
			} else {
				f.server(server -> {
					table(server, pos).fastForwardForTests();
					return null;
				});
			}
			f.context().waitTicks(10);
		}
		if (!punted) throw new AssertionError("chemin de fer: never punted (passed " + passed + ")");
		f.context().waitTicks(10);
		f.shot("v2a_bac_en_us_chemmy_punted");
		f.close();
	}

	private static BlockPos fresh(RealFlow f, net.minecraft.world.level.block.Block block) {
		return dev.nezo.burmaldaholic.gametest.v2.blackjack.BlackjackV2ClientGameTests.fresh(f, block);
	}

	private static int chipIndex(RealFlow f, long denom) {
		f.waitChrome(c -> c.bet.visible() && c.bet.chips().length > 0, "chip tray");
		return f.client(mc -> {
			long[] chips = ((V2Chrome.Holder) mc.gui.screen()).chrome().bet.chips();
			int best = 0;
			for (int i = 0; i < chips.length; i++) if (chips[i] <= denom) best = i;
			return best;
		});
	}

	static BaccaratTableBlockEntity table(net.minecraft.server.MinecraftServer server, BlockPos pos) {
		return (BaccaratTableBlockEntity) server.overworld().getBlockEntity(pos);
	}
}
