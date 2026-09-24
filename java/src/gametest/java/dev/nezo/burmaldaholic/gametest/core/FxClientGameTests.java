package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.fx.FxSettingsScreen;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.core.fx.ServerFx;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lane J-L1 client check (real client, Xvfb): the server's {@code ServerFx.celebrate} reaches the client through
 * the {@code fx} payload, the overlay draws on the HUD and on top of a casino screen, the roll-up ends on the exact
 * amount with the server tier, skip works, and the "Client effects" screen opens. Screenshots {@code jtest_fx_*}.
 */
public class FxClientGameTests implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.setScreen(() -> new FxSettingsScreen(null));
		context.waitForScreen(FxSettingsScreen.class);
		context.takeScreenshot("jtest_fx_settings");
		context.runOnClient(mc -> mc.gui.setScreen(null));

		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create()) {
			// HUD: EPIC at 60x (upgrades BIG → MEGA → EPIC)
			celebrate(world, WinTier.EPIC, 6000, 100);
			context.waitFor(mc -> CelebrationOverlay.get().isActive(), 100);
			context.waitTicks(12);
			context.takeScreenshot("jtest_fx_epic_hud");
			context.waitFor(mc -> !CelebrationOverlay.get().isActive(), 200);

			// on top of a casino screen, then skipped
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().offset(2, 0, 0);
				server.overworld().setBlockAndUpdate(pos, CoreContent.CASHIER.block().defaultBlockState());
				if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity table) player.openMenu(table);
			});
			context.waitFor(mc -> mc.gui.screen() != null, 200);
			celebrate(world, WinTier.JACKPOT, 250000, 100);
			context.waitFor(mc -> CelebrationOverlay.get().isActive(), 100);
			context.waitTicks(16);
			context.takeScreenshot("jtest_fx_jackpot_screen");
			boolean skipped = context.computeOnClient(mc -> CelebrationOverlay.get().onClick());
			if (!skipped) throw new AssertionError("a 4 s celebration must be skippable");
			context.waitTicks(1);
			long shown = context.computeOnClient(mc -> CelebrationOverlay.get().shownAmount(net.minecraft.util.Util.getMillis()));
			WinTier word = context.computeOnClient(mc -> CelebrationOverlay.get().shownTier(net.minecraft.util.Util.getMillis()));
			if (shown != 250000 || word != WinTier.JACKPOT) throw new AssertionError("skip must show the final frame: " + shown + " " + word);
			context.takeScreenshot("jtest_fx_jackpot_skipped");
			context.waitFor(mc -> !CelebrationOverlay.get().isActive(), 100);
			context.runOnClient(mc -> mc.gui.setScreen(null));

			// small tiers: in-screen banners
			celebrate(world, WinTier.WIN, 150, 100);
			context.waitFor(mc -> CelebrationOverlay.get().isActive(), 100);
			context.waitTicks(6);
			context.takeScreenshot("jtest_fx_win_banner");
			context.waitFor(mc -> !CelebrationOverlay.get().isActive(), 100);
		}
	}

	private static void celebrate(TestSingleplayerContext world, WinTier tier, long ret, long stake) {
		world.getServer().runOnServer(server -> ServerFx.get().celebrate(server.getPlayerList().getPlayers().getFirst(),
			new ServerFx.Celebration(tier, ret, stake, "core", tier == WinTier.JACKPOT ? 4 : 0, false, 0)));
	}
}
