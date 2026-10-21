package dev.nezo.burmaldaholic.gametest.extras;

import dev.nezo.burmaldaholic.client.fx.CelebrationOverlay;
import dev.nezo.burmaldaholic.client.menu.ClientCasinoMenu;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import net.minecraft.client.CameraType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The extras in the world and the PvP hub page (lane J-L7): the machines as a spectator sees them and the Challenges
 * page of the Casino Menu, in English and Russian. Screenshots {@code jtest_extras_<lang>_<view>}; a layout report
 * {@code jtest_extras_layout.txt}. The v2 screens of the solo games and of PvP are played and captured by
 * {@code gametest/v2/extras/ExtrasV2ClientGameTests} and {@code gametest/v2/pvp/PvpV2ClientGameTests} (table UI v2, lane E).
 */
public class ExtrasVisualClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!dev.nezo.burmaldaholic.gametest.v2.RealFlow.enabled("LEGACY") && !dev.nezo.burmaldaholic.gametest.v2.RealFlow.enabled("ExtrasVisualClientGameTests")) return; // lane gate (BURMALDAHOLIC_CGT)
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 12250");
			world.getServer().runOnServer(server -> CasinoConfig.chaos().enabled = false);
			inWorld(context, world);
			// table UI v2 (lane E): the solo screens, the PvP screens and their compact sizes are captured and played by
			// gametest/v2/extras/ExtrasV2ClientGameTests and gametest/v2/pvp/PvpV2ClientGameTests; the hub page stays here
			for (String lang : List.of("en_us", "ru_ru")) {
				language(context, lang);
				hub(context, world, lang);
			}
			language(context, "en_us");
		} finally {
			write(context);
			language(context, "en_us");
		}
	}

	// ---- in-world spectator animations (lane J-L7 finish) ---------------------------------------------------------------

	/**
	 * The machines as a spectator sees them: a wheel and a Plinko machine facing the player (third person), a spin, a drop
	 * and a coin flip at the same tick: mid-flight (the coin still edge-on, the ball on the face, the wheel turning), the
	 * coin's face after its landing, the wheel stopped on its segment with the Plinko lamp lit.
	 */
	private void inWorld(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		BlockPos[] at = new BlockPos[2];
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.closeContainer();
			BlockPos base = p.blockPosition();
			at[0] = base.offset(5, 0, -2);
			at[1] = base.offset(5, 0, 2);
			server.overworld().setBlockAndUpdate(at[0], ExtrasModule.WHEEL.block().defaultBlockState()
				.setValue(CasinoTableBlock.FACING, Direction.WEST));
			server.overworld().setBlockAndUpdate(at[1], ExtrasModule.PLINKO.block().defaultBlockState()
				.setValue(CasinoTableBlock.FACING, Direction.WEST));
		});
		context.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); // the world only: no chat, HUD or toasts over the machines
			if (mc.player != null) {
				mc.player.setYRot(-90f);
				mc.player.setXRot(4f);
			}
		});
		context.waitTicks(20);
		shot(context, "inworld_idle");
		world.getServer().runOnServer(server -> {
			action(server, at[0], "spin", chips(50));
			CompoundTag drop = chips(20);
			drop.putString("risk", "high");
			action(server, at[1], "drop", drop);
			CompoundTag flip = chips(10);
			flip.putString("side", "heads");
			CoinFlipGame.action(player(server), "flip", flip);
		});
		context.waitTicks(9);
		shot(context, "inworld_flight");
		context.waitTicks(15);
		shot(context, "inworld_coin_face");
		context.runOnClient(mc -> CelebrationOverlay.get().clear());
		context.waitTicks(58);
		shot(context, "inworld_stop");
		boolean[] synced = context.computeOnClient(mc -> new boolean[] {
			mc.level.getBlockEntity(at[0]) instanceof WheelBlockEntity w && w.wheelSync() != null,
			mc.level.getBlockEntity(at[1]) instanceof PlinkoBlockEntity pl && pl.plinkoSync() != null});
		if (!synced[0] || !synced[1]) report.add("in-world sync missing on the client: wheel " + synced[0] + ", plinko " + synced[1]);
		context.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			CelebrationOverlay.get().clear();
		});
		world.getServer().runOnServer(server -> {
			server.overworld().removeBlock(at[0], false);
			server.overworld().removeBlock(at[1], false);
		});
		context.waitTicks(5);
	}

	// ---- the PvP hub page of the Casino Menu (extras.md §7.2) ----------------------------------------------------------------

	private void hub(ClientGameTestContext context, TestSingleplayerContext world, String lang) {
		open(context, world, server -> CasinoMenu.open(player(server), "challenges"));
		context.waitTicks(10);
		shot(context, lang + "_hub_live");
		// a busy hub: a record, the nemesis, an invite, three open lobbies (one with its own stake), every create card
		context.runOnClient(mc -> ClientCasinoMenu.setPageForTests("challenges", hubLines(), hubButtons()));
		context.waitTicks(12);
		shot(context, lang + "_hub");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
	}

	private static Component t(String key, Object... args) {
		return Component.translatable(key, args);
	}

	private static Component lit(String s) {
		return Component.literal(s);
	}

	private static List<ClientCasinoMenu.Line> hubLines() {
		List<ClientCasinoMenu.Line> l = new ArrayList<>();
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.title"), 0xFFD700));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.record", lit("12"), lit("7"), lit("+1,450")), 0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.nemesis", lit("Notch"), lit("2"), lit("5")), 0xFF8888));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.pending"), 0xFFD700));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.invite.body", lit("Alex"), t("gui.burmaldaholic.pvp.game.coin"), lit("250")), 0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.nearby", lit("3")), 0xFFD700));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.lobby_row", t("gui.burmaldaholic.pvp.game.scratch"), lit("Notch"), lit("2"), lit("6")),
			0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.lobby_row", t("gui.burmaldaholic.pvp.game.plinko"),
			t("block.burmaldaholic.plinko_machine"), lit("3"), lit("6")), 0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.lobby_row", t("gui.burmaldaholic.pvp.game.wheel"),
			t("block.burmaldaholic.wheel_of_fortune"), lit("4"), lit("8")), 0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(Component.empty(), 0xFFFFFF));
		l.add(new ClientCasinoMenu.Line(t("gui.burmaldaholic.pvp.hub.machine_hint"), 0xAAAAAA));
		return l;
	}

	private static List<ClientCasinoMenu.Button> hubButtons() {
		List<ClientCasinoMenu.Button> b = new ArrayList<>();
		b.add(new ClientCasinoMenu.Button("p:accept:m1", t("gui.burmaldaholic.pvp.hub.accept", lit("Alex"), t("gui.burmaldaholic.pvp.game.coin")), true,
			false, 0));
		b.add(new ClientCasinoMenu.Button("p:decline:m1", t("gui.burmaldaholic.pvp.hub.decline", lit("Alex")), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:join:m2", t("gui.burmaldaholic.pvp.lobby.join", lit("200")), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:join:m3", t("gui.burmaldaholic.pvp.lobby.join", lit("100")), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:join:m4", t("gui.burmaldaholic.pvp.hub.join"), true, true, 10));
		b.add(new ClientCasinoMenu.Button("p:new:coin", t("gui.burmaldaholic.pvp.game.coin"), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:new:scratch", t("gui.burmaldaholic.pvp.game.scratch"), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:view:new", t("gui.burmaldaholic.pvp.hub.new_match"), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:view:rivals", t("gui.burmaldaholic.pvp.hub.rivals"), true, false, 0));
		b.add(new ClientCasinoMenu.Button("p:invites", t("gui.burmaldaholic.pvp.toggle", t("gui.burmaldaholic.pvp.settings.invites"),
			t("gui.burmaldaholic.common.on")), true, false, 0));
		return b;
	}

	// ---- compact layout (GUI scale 3–4: the full scene at a lower whole scale, nothing overlaps or clips) ----------------

	// ---- pvp -------------------------------------------------------------------------------------------------------

	// ---- helpers ---------------------------------------------------------------------------------------------------

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static CompoundTag chips(long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("stake", "chips");
		t.putLong("amount", amount);
		return t;
	}

	private static void action(MinecraftServer server, BlockPos pos, String action, CompoundTag args) {
		if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity table) {
			table.onAction(player(server), action, args);
		}
	}

	private void open(ClientGameTestContext context, TestSingleplayerContext world, Consumer<MinecraftServer> opener) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			player(server).closeContainer();
			opener.accept(server);
		});
		try {
			context.waitFor(mc -> mc.gui.screen() != null, 200);
		} catch (RuntimeException | AssertionError e) {
			report.add("screen did not open");
		}
		context.waitTicks(10);
	}

	private void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot("jtest_extras_" + name);
		for (String p : context.computeOnClient(mc -> inspect(mc, mc.gui.screen()))) report.add(name + ": " + p);
	}

	private static List<String> inspect(Minecraft mc, Screen screen) {
		List<String> out = new ArrayList<>();
		if (screen == null) {
			out.add("no screen");
			return out;
		}
		out.add("screen " + screen.getClass().getSimpleName());
		for (var child : screen.children()) {
			if (!(child instanceof AbstractWidget w) || !w.visible) continue;
			String label = w.getMessage().getString();
			if (w instanceof AbstractButton && !label.isEmpty() && mc.font.width(w.getMessage()) > w.getWidth() - 4) {
				out.add("label squeezed (" + mc.font.width(w.getMessage()) + " > " + (w.getWidth() - 4) + "): \"" + label + "\"");
			}
			if (w.getX() < 0 || w.getY() < 0 || w.getX() + w.getWidth() > screen.width || w.getY() + w.getHeight() > screen.height) {
				out.add("widget outside the screen: \"" + label + "\"");
			}
		}
		return out;
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("jtest_extras_layout.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void language(ClientGameTestContext context, String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitFor(mc -> mc.gui.overlay() == null, 1200); // the Mojang reload overlay fades out (no overlay in screenshots)
		context.waitTicks(20);
	}
}
