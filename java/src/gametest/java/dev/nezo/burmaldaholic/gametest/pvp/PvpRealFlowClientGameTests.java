package dev.nezo.burmaldaholic.gametest.pvp;

import dev.nezo.burmaldaholic.client.input.Inputs;
import dev.nezo.burmaldaholic.client.ui.FitScaled;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.MenuRequestPayload;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelPartyNet;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * REAL-FLOW PvP in a real client (v0.1.2 hotfix "PvP only works for Coin Flip"): the client player starts and plays
 * every PvP mode the way a player does — key B → Casino Menu → Challenges → the mode card → the rival's button
 * (Coin Flip Duel, Scratch Showdown), accepting an invite with the hub's [Accept] button, using the Plinko machine /
 * Wheel of Fortune / slot machine (look at it, press Use) and clicking their PvP entries, the lobby screen's Start, the
 * mode screens' Drop! / Scratch! / Spin! — with a second (test) player on the integrated server as the rival. Each
 * flow asserts the mode's duel screen opened on the client, the match settled on the server, the result reached the
 * client, and chips were conserved. Vs-bots flows (singleplayer) for Scratch Showdown and Plinko Battle too.
 * Problems of every flow are collected and reported together.
 */
public class PvpRealFlowClientGameTests implements FabricClientGameTest {
	private static final Transaction TEST = Transaction.of("core", "gametest");
	private static final long START = 5000;
	private static final int TIMEOUT = 400;
	private final List<String> problems = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			run(context, world, "coin_hub", () -> hubDuel(context, world, "coin"));
			run(context, world, "scratch_hub", () -> hubDuel(context, world, "scratch"));
			run(context, world, "plinko_hub", () -> hubDuel(context, world, "plinko"));
			run(context, world, "wheel_hub", () -> hubDuel(context, world, "wheel"));
			run(context, world, "slots_hub", () -> hubDuel(context, world, "slots"));
			run(context, world, "scratch_accept_ui", () -> acceptFromHub(context, world));
			run(context, world, "scratch_vs_bot", () -> hubVsBot(context, world));
			run(context, world, "plinko_machine", () -> plinko(context, world, false));
			run(context, world, "plinko_vs_bots", () -> plinko(context, world, true));
			run(context, world, "wheel_machine", () -> wheel(context, world));
			run(context, world, "slots_machine", () -> slots(context, world));
			run(context, world, "scratch_lobby_join", () -> scratchLobbyJoin(context, world));
			run(context, world, "plinko_join", () -> plinkoJoin(context, world));
			run(context, world, "wheel_join", () -> wheelJoin(context, world));
			run(context, world, "slots_join", () -> slotsJoin(context, world));
		}
		if (!problems.isEmpty()) {
			throw new AssertionError("PvP real-flow problems:\n - " + String.join("\n - ", problems));
		}
	}

	private interface Flow {
		void run();
	}

	private void run(ClientGameTestContext context, TestSingleplayerContext world, String name, Flow flow) {
		try {
			flow.run();
			System.out.println("PVP_REALFLOW ok " + name);
		} catch (RuntimeException | AssertionError e) {
			problems.add(name + ": " + e.getMessage());
			context.takeScreenshot("jtest_pvp_realflow_fail_" + name);
		} finally {
			ClientTestWorlds.quiesce(context, world);
			world.getServer().runOnServer(s -> {
				for (ServerPlayer p : List.copyOf(s.getPlayerList().getPlayers())) {
					if (p.getName().getString().startsWith("rf")) {
						s.getPlayerList().remove(p);
					}
				}
			});
			context.waitTicks(5);
		}
	}

	// ---- set-up ---------------------------------------------------------------------------------------------

	private static UUID me(ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.player.getUUID());
	}

	private static ServerPlayer mePlayer(MinecraftServer s, UUID id) {
		return s.getPlayerList().getPlayer(id);
	}

	/** Gives the client player START chips, spawns the rival next to them (START chips, or none for vs-bots flows). */
	private static @Nullable MockClient setUp(ClientGameTestContext context, TestSingleplayerContext world, boolean rival) {
		UUID me = me(context);
		return world.getServer().computeOnServer(s -> {
			ServerPlayer p = mePlayer(s, me);
			Economies.get().setBalance(s, me, START, TEST);
			if (!rival) {
				return null;
			}
			return MockClient.spawn(p.level(), p.position().add(1.5, 0, 1.5), START);
		});
	}

	/** Places {@code block} 2 blocks in front of the player (east), looks at it and presses Use. */
	private static BlockPos useMachine(ClientGameTestContext context, TestSingleplayerContext world, Block block) {
		UUID me = me(context);
		BlockPos pos = world.getServer().computeOnServer(s -> {
			ServerPlayer p = mePlayer(s, me);
			BlockPos at = p.blockPosition().east(2);
			p.level().setBlockAndUpdate(at, block.defaultBlockState());
			return at;
		});
		context.waitTicks(5);
		context.getInput().lookAt(pos);
		context.waitTicks(2);
		context.getInput().pressKey(o -> o.keyUse);
		waitForScreen(context, s -> s != null && s.getClass().getSimpleName().contains("Screen"), "the machine screen opens");
		return pos;
	}

	// ---- widgets ----------------------------------------------------------------------------------------------

	private static String keyOf(@Nullable Component c) {
		if (c == null) {
			return "";
		}
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static String tooltipKey(AbstractWidget w) {
		for (Class<?> k = w.getClass(); k != null; k = k.getSuperclass()) {
			for (Field f : k.getDeclaredFields()) {
				if (f.getType() == Tooltip.class) {
					try {
						f.setAccessible(true);
						Tooltip t = (Tooltip) f.get(w);
						if (t != null) {
							Field m = Tooltip.class.getDeclaredField("message");
							m.setAccessible(true);
							return keyOf((Component) m.get(t));
						}
					} catch (ReflectiveOperationException e) {
						return "";
					}
				}
			}
		}
		return "";
	}

	private static List<AbstractWidget> widgets(Screen s) {
		List<AbstractWidget> out = new ArrayList<>();
		for (GuiEventListener l : s.children()) {
			if (l instanceof AbstractWidget w) {
				out.add(w);
			}
		}
		return out;
	}

	private static Predicate<AbstractWidget> labelled(String key) {
		return w -> !(w instanceof net.minecraft.client.gui.components.EditBox) && (key.equals(keyOf(w.getMessage())) || key.equals(tooltipKey(w)));
	}

	private static Predicate<AbstractWidget> labelText(String text) {
		return w -> !(w instanceof net.minecraft.client.gui.components.EditBox) && w.getMessage().getString().contains(text);
	}

	private static String describe(Minecraft mc) {
		Screen s = mc.gui.screen();
		if (s == null) {
			return "no screen";
		}
		StringBuilder b = new StringBuilder(s.getClass().getSimpleName()).append(" [");
		for (AbstractWidget w : widgets(s)) {
			b.append(keyOf(w.getMessage())).append(w.isActive() ? "" : "(off)").append(tooltipKey(w).isEmpty() ? "" : "/" + tooltipKey(w)).append("; ");
		}
		return b.append("]").toString();
	}

	/** Waits for an active widget, then clicks it with the real mouse (cursor to its centre, left button). */
	private static void click(ClientGameTestContext context, Predicate<AbstractWidget> which, String what) {
		double[] pos = null;
		for (int waited = 0; waited < TIMEOUT && pos == null; waited += 2) {
			pos = context.computeOnClient(mc -> {
				Screen s = mc.gui.screen();
				if (s == null) {
					return null;
				}
				for (AbstractWidget w : widgets(s)) {
					if (w.visible && w.isActive() && which.test(w)) {
						double f = mc.getWindow().getGuiScale() * FitScaled.current();
						return new double[] {(w.getX() + w.getWidth() / 2.0) * f, (w.getY() + w.getHeight() / 2.0) * f};
					}
				}
				return null;
			});
			if (pos == null) {
				// scrolled out of the page (the Casino Menu hides widgets outside its content area): scroll down, like a player
				boolean hidden = context.computeOnClient(mc -> mc.gui.screen() != null && widgets(mc.gui.screen()).stream()
					.anyMatch(w -> !w.visible && w.active && which.test(w)));
				if (hidden) {
					double[] centre = context.computeOnClient(mc -> new double[] {mc.getWindow().getWidth() / 2.0, mc.getWindow().getHeight() / 2.0});
					context.getInput().setCursorPos(centre[0], centre[1]);
					context.getInput().scroll(-1);
				}
				context.waitTicks(2);
			}
		}
		if (pos == null) {
			throw new AssertionError("no active '" + what + "' on " + context.computeOnClient(PvpRealFlowClientGameTests::describe));
		}
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(Inputs.MOUSE_LEFT);
		context.waitTicks(3);
	}

	private static boolean present(ClientGameTestContext context, Predicate<AbstractWidget> which) {
		return context.computeOnClient(mc -> mc.gui.screen() != null && widgets(mc.gui.screen()).stream().anyMatch(w -> w.visible && w.isActive()
			&& which.test(w)));
	}

	private static void waitForScreen(ClientGameTestContext context, Predicate<@Nullable Screen> test, String what) {
		try {
			context.waitFor(mc -> test.test(mc.gui.screen()), TIMEOUT);
		} catch (RuntimeException | AssertionError e) {
			throw new AssertionError(what + " (now: " + context.computeOnClient(PvpRealFlowClientGameTests::describe) + ")");
		}
	}

	private static Predicate<@Nullable Screen> named(String simpleName) {
		return s -> s != null && s.getClass().getSimpleName().equals(simpleName);
	}

	// ---- the match ----------------------------------------------------------------------------------------

	private static Optional<PvpMatch> match(TestSingleplayerContext world, UUID player) {
		return world.getServer().computeOnServer(s -> Pvp.service().matchOf(player));
	}

	/**
	 * The duel screen of {@code mode} is on the client; the client presses the mode's advance button (and the rival
	 * test player its own) until the match settles; the result reaches the client; chips are conserved.
	 */
	private static void playOut(ClientGameTestContext context, TestSingleplayerContext world, String screen, @Nullable String pressKey,
			@Nullable MockClient rival, long before) {
		UUID me = me(context);
		waitForScreen(context, named(screen), "the " + screen + " opens on the client");
		PvpMatch m = match(world, me).orElseThrow(() -> new AssertionError("no match on the server"));
		context.takeScreenshot("jtest_pvp_realflow_" + m.mode);
		for (int waited = 0; waited < 1600; waited += 5) {
			if (world.getServer().computeOnServer(s -> m.state() == MatchState.SETTLED)) {
				break;
			}
			if (rival != null) {
				world.getServer().runOnServer(s -> {
					rival.drain();
					if (rival.kind.equals("match")) {
						rival.send(m.mode.equals("slots") ? new dev.nezo.burmaldaholic.pvp.net.PvpActionPayload("press", m.id, "", 0)
							: new dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload("pvp", "press", new CompoundTag()));
					}
				});
			}
			if (pressKey != null && present(context, labelled(pressKey))) {
				click(context, labelled(pressKey), pressKey);
			} else {
				context.waitTicks(5);
			}
			String now = context.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
			if (!now.equals(screen) && !now.equals("PvpResultScreen") && !now.equals("PvpTauntScreen")) {
				throw new AssertionError("the duel screen was replaced by " + now + " before the end");
			}
		}
		boolean settled = world.getServer().computeOnServer(s -> m.state() == MatchState.SETTLED);
		if (!settled) {
			throw new AssertionError("the " + m.mode + " match did not settle (" + m.state() + ")");
		}
		try {
			context.waitFor(mc -> dev.nezo.burmaldaholic.pvp.client.ClientPvp.state() != null
				&& "SETTLED".equals(dev.nezo.burmaldaholic.pvp.client.ClientPvp.state().get("state").getAsString()), TIMEOUT);
		} catch (RuntimeException | AssertionError e) {
			throw new AssertionError("the result never reached the client");
		}
		waitForScreen(context, named("PvpResultScreen"), "the result window opens");
		context.waitTicks(10);
		context.takeScreenshot("jtest_pvp_realflow_" + m.mode + "_result");
		closeResult(context);
		long after = world.getServer().computeOnServer(s -> Economies.get().balance(s, me) + (rival == null ? 0 : rival.balance()));
		long rake = m.rake();
		if (rival != null && after != before - rake) {
			throw new AssertionError("chips not conserved: " + after + " vs " + before + " − " + rake);
		}
	}

	private static long total(TestSingleplayerContext world, UUID me, @Nullable MockClient rival) {
		return world.getServer().computeOnServer(s -> Economies.get().balance(s, me) + (rival == null ? 0 : rival.balance()));
	}

	/** Key B → Casino Menu → Challenges tab. */
	private static void openHub(ClientGameTestContext context) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		context.getInput().pressKey(Inputs.KEY_B);
		waitForScreen(context, named("CasinoMenuScreen"), "key B opens the Casino Menu");
		click(context, labelled("gui.burmaldaholic.menu.challenges"), "Challenges tab");
		context.waitTicks(10);
		// a view left open last time (New match…): Back to the hub's main view
		for (int i = 0; i < 3 && present(context, labelled("gui.burmaldaholic.common.back")); i++) {
			click(context, labelled("gui.burmaldaholic.common.back"), "Back");
			context.waitTicks(5);
		}
	}

	// ---- flows ----------------------------------------------------------------------------------------------

	/** Hub → the mode card → the rival's button (amount prefilled) → the rival clicks [Accept] in chat. */
	private void hubDuel(ClientGameTestContext context, TestSingleplayerContext world, String mode) {
		MockClient rival = setUp(context, world, true);
		UUID me = me(context);
		long before = total(world, me, rival);
		openHub(context);
		click(context, labelled("gui.burmaldaholic.pvp.game." + mode), mode + " card");
		String rivalName = rival.player.getName().getString();
		click(context, labelText(rivalName), "the rival's challenge button");
		boolean invited = world.getServer().computeOnServer(s -> !Pvp.service().invitesFor(rival.player.getUUID()).isEmpty());
		if (!invited) {
			throw new AssertionError("no invite after clicking the rival (" + context.computeOnClient(PvpRealFlowClientGameTests::describe) + ")");
		}
		world.getServer().runOnServer(s -> {
			rival.drain();
			String cmd = rival.chatCommand("casino pvp accept");
			if (cmd == null) {
				throw new AssertionError("the rival got no [Accept]: " + rival);
			}
			rival.command(cmd);
		});
		String screen = SCREENS.get(mode);
		if (mode.equals("coin")) {
			// the coin flip runs itself; walk away from any Double or nothing
			waitForScreen(context, named(screen), "the coin duel screen opens");
			// until no Double or nothing / let it ride question is open (the chain is over: rematch window or history)
			for (int i = 0; i < 1200 && !world.getServer().computeOnServer(s -> Pvp.service().matchOf(me).map(x -> x.state() == MatchState.SETTLED
				&& (x.phase().equals("rematch") || x.phase().equals("history"))).orElse(true)); i += 5) {
				if (present(context, labelled("gui.burmaldaholic.pvp.coin.walk_away"))) {
					click(context, labelled("gui.burmaldaholic.pvp.coin.walk_away"), "walk away");
				} else if (present(context, labelled("gui.burmaldaholic.pvp.coin.take_money"))) {
					click(context, labelled("gui.burmaldaholic.pvp.coin.take_money"), "take the money");
				}
				world.getServer().runOnServer(s -> {
					rival.drain();
					if (rival.state != null && rival.state.has("decision")) {
						var d = rival.state.getAsJsonObject("decision");
						rival.send(new dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelNet.Action("decide", MockClient.args("decision",
							d.get("id").getAsString(), "option", 0L, "match", d.get("match").getAsString(), "seq", d.get("seq").getAsLong())));
					}
				});
				context.waitTicks(5);
			}
			if (!world.getServer().computeOnServer(s -> Pvp.service().matchOf(me).map(x -> x.state() == MatchState.SETTLED).orElse(true))) {
				throw new AssertionError("the coin duel did not end");
			}
			closeResult(context);
			return;
		}
		playOut(context, world, screen, PRESS.get(mode), rival, before);
	}

	private static final java.util.Map<String, String> SCREENS = java.util.Map.of("coin", "CoinDuelScreen", "scratch", "ScratchShowdownScreen",
		"plinko", "PlinkoBattleScreen", "wheel", "WheelPartyScreen", "slots", "SlotShowdownScreen");
	private static final java.util.Map<String, String> PRESS = java.util.Map.of("scratch", "gui.burmaldaholic.pvp.scratch.scratch_now",
		"plinko", "gui.burmaldaholic.pvp.plinko.drop_now", "slots", "gui.burmaldaholic.pvp.slots.spin_now");

	/** The result window opens after the Final Reveal; the player closes it. */
	private static void closeResult(ClientGameTestContext context) {
		waitForScreen(context, named("PvpResultScreen"), "the result window opens");
		click(context, labelled("gui.burmaldaholic.common.close"), "Close");
		context.waitTicks(5);
	}

	/** The rival challenges the client from the hub; the client accepts with the hub's [Accept] button. */
	private void acceptFromHub(ClientGameTestContext context, TestSingleplayerContext world) {
		MockClient rival = setUp(context, world, true);
		UUID me = me(context);
		long before = total(world, me, rival);
		world.getServer().runOnServer(s -> {
			rival.send(new MenuRequestPayload("challenges", "p:new:scratch", 0));
			rival.send(new MenuRequestPayload("challenges", "p:ch:" + me, 100));
		});
		boolean invited = world.getServer().computeOnServer(s -> !Pvp.service().invitesFor(me).isEmpty());
		if (!invited) {
			throw new AssertionError("the rival's challenge did not reach the client: " + rival);
		}
		openHub(context);
		click(context, labelled("gui.burmaldaholic.pvp.hub.accept"), "[Accept]");
		playOut(context, world, "ScratchShowdownScreen", "gui.burmaldaholic.pvp.scratch.scratch_now", rival, before);
	}

	/** Singleplayer: hub → Scratch Showdown → Bot opponent: Normal. */
	private void hubVsBot(ClientGameTestContext context, TestSingleplayerContext world) {
		setUp(context, world, false);
		openHub(context);
		click(context, labelled("gui.burmaldaholic.pvp.game.scratch"), "Scratch Showdown card");
		click(context, labelled("gui.burmaldaholic.pvp.bots.opponent"), "Bot opponent");
		playOut(context, world, "ScratchShowdownScreen", "gui.burmaldaholic.pvp.scratch.scratch_now", null, 0);
	}

	/** Plinko machine → Plinko Battle → Open lobby; the rival joins at the machine; the client Starts; Drop!. */
	private void plinko(ClientGameTestContext context, TestSingleplayerContext world, boolean vsBots) {
		MockClient rival = setUp(context, world, !vsBots);
		UUID me = me(context);
		long before = total(world, me, rival);
		BlockPos pos = useMachine(context, world, ExtrasModule.PLINKO.block());
		waitForScreen(context, named("PlinkoScreen"), "the Plinko screen opens");
		click(context, labelled("gui.burmaldaholic.pvp.plinko.host"), "the Plinko Battle tab");
		click(context, w -> keyOf(w.getMessage()).equals("gui.burmaldaholic.pvp.plinko.host"), "Start a Plinko Battle");
		if (!vsBots) {
			// seats: humans only (the set-up cycles humans only → mixed → bots only)
			for (int i = 0; i < 3 && !present(context, w -> w.getMessage().getString().contains(Component.translatable("gui.burmaldaholic.bots.policy.humans_only")
				.getString())); i++) {
				click(context, w -> w.getMessage().getString().startsWith(Component.translatable("gui.burmaldaholic.pvp.bots.seats").getString()), "Seats");
			}
		} else {
			for (int i = 0; i < 3 && !present(context, w -> w.getMessage().getString().contains(Component.translatable("gui.burmaldaholic.bots.policy.bots_only")
				.getString())); i++) {
				click(context, w -> w.getMessage().getString().startsWith(Component.translatable("gui.burmaldaholic.pvp.bots.seats").getString()), "Seats");
			}
		}
		click(context, labelled("gui.burmaldaholic.pvp.lobby.open"), "Open lobby");
		if (!vsBots) {
			waitForScreen(context, named("PvpLobbyScreen"), "the lobby screen opens for the host");
			String id = match(world, me).map(x -> x.id).orElseThrow(() -> new AssertionError("no lobby on the server"));
			world.getServer().runOnServer(s -> {
				rival.player.openMenu((net.minecraft.world.MenuProvider) rival.player.level().getBlockEntity(pos));
				rival.send(new TableActionPayload(pos, "pvp_join", MockClient.args("id", id)));
				if (Pvp.service().matchOf(rival.player.getUUID()).isEmpty()) {
					throw new AssertionError("the rival could not join: " + rival);
				}
			});
			click(context, labelled("gui.burmaldaholic.pvp.lobby.start"), "Start");
		}
		playOut(context, world, "PlinkoBattleScreen", "gui.burmaldaholic.pvp.plinko.drop_now", rival, before);
	}

	/**
	 * The rival hosts at a machine ({@code host} sends its set-up there); the client uses the same machine, clicks the
	 * machine's join entry ({@code entry} = clicks to reach the join button), the rival starts; the client plays.
	 */
	private void joinAtMachine(ClientGameTestContext context, TestSingleplayerContext world, Block block, String machineScreen,
			java.util.function.BiConsumer<MockClient, BlockPos> host, Runnable entry, String screen, @Nullable String press) {
		MockClient rival = setUp(context, world, true);
		UUID me = me(context);
		long before = total(world, me, rival);
		BlockPos pos = useMachine(context, world, block);
		waitForScreen(context, named(machineScreen), "the machine screen opens");
		context.runOnClient(mc -> mc.gui.screen().onClose());
		context.waitTicks(5);
		world.getServer().runOnServer(s -> {
			host.accept(rival, pos);
			if (Pvp.service().matchOf(rival.player.getUUID()).isEmpty()) {
				throw new AssertionError("the rival could not host: " + rival);
			}
		});
		context.getInput().lookAt(pos);
		context.getInput().pressKey(o -> o.keyUse);
		waitForScreen(context, named(machineScreen), "the machine screen reopens (no old result window pops up)");
		context.waitTicks(10);
		entry.run();
		context.waitTicks(10);
		if (match(world, me).isEmpty()) {
			throw new AssertionError("the client did not join: " + context.computeOnClient(PvpRealFlowClientGameTests::describe));
		}
		world.getServer().runOnServer(s -> {
			rival.drain();
			PvpMatch m = Pvp.service().matchOf(rival.player.getUUID()).orElseThrow();
			if (m.mode.equals("wheel")) {
				rival.send(new WheelPartyNet.Action("spin", new CompoundTag()));
			} else {
				rival.send(new dev.nezo.burmaldaholic.pvp.net.PvpActionPayload("start", m.id, "", 0));
			}
		});
		playOut(context, world, screen, press, rival, before);
	}

	private void plinkoJoin(ClientGameTestContext context, TestSingleplayerContext world) {
		joinAtMachine(context, world, ExtrasModule.PLINKO.block(), "PlinkoScreen", (rival, pos) -> {
			rival.player.openMenu((net.minecraft.world.MenuProvider) rival.player.level().getBlockEntity(pos));
			rival.send(new TableActionPayload(pos, "pvp_host", MockClient.args("amount", 10L, "risk", "medium", "balls", 3, "policy", "humans_only",
				"difficulty", "normal", "size", 2)));
		}, () -> {
			click(context, labelled("gui.burmaldaholic.pvp.plinko.host"), "the Plinko Battle tab");
			click(context, w -> keyOf(w.getMessage()).equals("gui.burmaldaholic.pvp.plinko.join"), "Join the battle");
		}, "PlinkoBattleScreen", "gui.burmaldaholic.pvp.plinko.drop_now");
	}

	private void wheelJoin(ClientGameTestContext context, TestSingleplayerContext world) {
		joinAtMachine(context, world, ExtrasModule.WHEEL.block(), "WheelScreen", (rival, pos) -> {
			rival.send(new WheelPartyNet.Action("open", MockClient.args("x", pos.getX(), "y", pos.getY(), "z", pos.getZ())));
			rival.send(new WheelPartyNet.Action("host", MockClient.args("cap", 50L, "stake", 20L, "policy", "humans_only", "difficulty", "normal",
				"size", 2)));
		}, () -> {
			click(context, labelled("gui.burmaldaholic.pvp.wheel.join"), "the Join the party button");
			waitForScreen(context, named("WheelPartyPanel"), "the party panel opens");
			click(context, labelled("gui.burmaldaholic.pvp.wheel.join"), "Join the party");
		}, "WheelPartyScreen", null);
	}

	private void slotsJoin(ClientGameTestContext context, TestSingleplayerContext world) {
		joinAtMachine(context, world, SlotsModule.MACHINES.get(Tier.COPPER).block(), "SlotMachineV2Screen", (rival, pos) -> {
			rival.player.openMenu((net.minecraft.world.MenuProvider) rival.player.level().getBlockEntity(pos));
			rival.send(new TableActionPayload(pos, "pvp_open", MockClient.args("entry", 10L, "spins", 3, "policy", "humans_only", "size", 2)));
		}, () -> {
			click(context, labelled("gui.burmaldaholic.pvp.slots.join"), "the Join Showdown button");
			click(context, labelled("gui.burmaldaholic.pvp.slots.join"), "Join Showdown");
		}, "SlotShowdownScreen", "gui.burmaldaholic.pvp.slots.spin_now");
	}

	/** The rival opens a Scratch Showdown lobby from the hub; the client joins from the hub's Open lobbies nearby. */
	private void scratchLobbyJoin(ClientGameTestContext context, TestSingleplayerContext world) {
		MockClient rival = setUp(context, world, true);
		UUID me = me(context);
		long before = total(world, me, rival);
		world.getServer().runOnServer(s -> {
			rival.send(new MenuRequestPayload("challenges", "p:new:scratch", 0));
			rival.send(new MenuRequestPayload("challenges", "p:lobby", 10));
			if (Pvp.service().matchOf(rival.player.getUUID()).isEmpty()) {
				throw new AssertionError("the rival could not open a lobby: " + rival);
			}
		});
		openHub(context);
		click(context, labelled("gui.burmaldaholic.pvp.lobby.join"), "Join");
		context.waitTicks(10);
		if (match(world, me).isEmpty()) {
			throw new AssertionError("the client did not join: " + context.computeOnClient(PvpRealFlowClientGameTests::describe));
		}
		world.getServer().runOnServer(s -> {
			PvpMatch m = Pvp.service().matchOf(rival.player.getUUID()).orElseThrow();
			rival.send(new dev.nezo.burmaldaholic.pvp.net.PvpActionPayload("start", m.id, "", 0));
		});
		playOut(context, world, "ScratchShowdownScreen", "gui.burmaldaholic.pvp.scratch.scratch_now", rival, before);
	}

	/** Wheel of Fortune → Wheel Party → Start a Wheel Party; the rival joins; the client spins. */
	private void wheel(ClientGameTestContext context, TestSingleplayerContext world) {
		MockClient rival = setUp(context, world, true);
		UUID me = me(context);
		long before = total(world, me, rival);
		BlockPos pos = useMachine(context, world, ExtrasModule.WHEEL.block());
		waitForScreen(context, named("WheelScreen"), "the wheel screen opens");
		click(context, labelled("gui.burmaldaholic.pvp.wheel.host"), "the Wheel Party button");
		waitForScreen(context, named("WheelPartyPanel"), "the party panel opens");
		click(context, labelled("gui.burmaldaholic.pvp.wheel.host"), "Start a Wheel Party");
		context.waitTicks(15);
		if (match(world, me).isEmpty()) {
			throw new AssertionError("no party on the server: " + context.computeOnClient(PvpRealFlowClientGameTests::describe));
		}
		world.getServer().runOnServer(s -> {
			rival.send(new WheelPartyNet.Action("open", MockClient.args("x", pos.getX(), "y", pos.getY(), "z", pos.getZ())));
			rival.send(new WheelPartyNet.Action("join", MockClient.args("stake", 10L)));
			if (Pvp.service().matchOf(rival.player.getUUID()).isEmpty()) {
				throw new AssertionError("the rival could not join: " + rival);
			}
		});
		context.waitTicks(15);
		// whichever screen the host now has (party panel or lobby): its spin / start button
		if (present(context, labelled("gui.burmaldaholic.pvp.wheel.spin_now"))) {
			click(context, labelled("gui.burmaldaholic.pvp.wheel.spin_now"), "Spin the wheel!");
		} else {
			click(context, labelled("gui.burmaldaholic.pvp.lobby.start"), "Start");
		}
		playOut(context, world, "WheelPartyScreen", null, rival, before);
	}

	/** Slot machine → Slot Showdown → Open lobby; the rival joins; the client Starts; Spin!. */
	private void slots(ClientGameTestContext context, TestSingleplayerContext world) {
		MockClient rival = setUp(context, world, true);
		UUID me = me(context);
		long before = total(world, me, rival);
		BlockPos pos = useMachine(context, world, SlotsModule.MACHINES.get(Tier.COPPER).block());
		waitForScreen(context, named("SlotMachineV2Screen"), "the slot machine screen opens");
		click(context, labelled("gui.burmaldaholic.pvp.slots.title"), "the Slot Showdown button");
		click(context, labelled("gui.burmaldaholic.pvp.lobby.open"), "Open lobby");
		context.waitTicks(15);
		String id = match(world, me).map(x -> x.id).orElseThrow(() -> new AssertionError("no showdown lobby on the server: "
			+ context.computeOnClient(PvpRealFlowClientGameTests::describe)));
		world.getServer().runOnServer(s -> {
			rival.player.openMenu((net.minecraft.world.MenuProvider) rival.player.level().getBlockEntity(pos));
			rival.send(new TableActionPayload(pos, "pvp_join", MockClient.args("id", id)));
			if (Pvp.service().matchOf(rival.player.getUUID()).isEmpty()) {
				throw new AssertionError("the rival could not join: " + rival);
			}
		});
		context.waitTicks(15);
		click(context, labelled("gui.burmaldaholic.pvp.lobby.start"), "Start");
		playOut(context, world, "SlotShowdownScreen", "gui.burmaldaholic.pvp.slots.spin_now", rival, before);
	}
}
