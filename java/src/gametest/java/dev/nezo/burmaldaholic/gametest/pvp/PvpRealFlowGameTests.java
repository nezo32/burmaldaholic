package dev.nezo.burmaldaholic.gametest.pvp;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.network.MenuRequestPayload;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import dev.nezo.burmaldaholic.games.extras.pvp.coin.CoinDuelNet;
import dev.nezo.burmaldaholic.games.extras.pvp.wheel.WheelPartyNet;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.pvp.net.PvpActionPayload;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;

/**
 * REAL-FLOW PvP tests (v0.1.2 hotfix: "PvP challenges only work for Coin Flip"). Two test players go through the
 * player's actual path for every mode — the Casino Menu hub, the Lucky Coin set-up, the machines' screens (Plinko,
 * Wheel of Fortune, slot machine), the clickable chat [Accept], the lobby screen's Start, the mode screens' advance
 * buttons — as C2S payloads through the real packet listener ({@link MockClient}). They assert what each CLIENT
 * receives (the duel screen opens for BOTH players, the result arrives) and the money (stakes escrowed once, payouts
 * + rake = pot, nothing created or lost).
 */
public class PvpRealFlowGameTests {
	private static final long STAKE = 100;
	private static final long START = 5000;

	// ---- shared ---------------------------------------------------------------------------------------------

	private static MockClient[] two(GameTestHelper helper) {
		return new MockClient[] {MockClient.join(helper, new Vec3(2.5, 1, 3.5), START), MockClient.join(helper, new Vec3(3.5, 1, 3.5), START)};
	}

	private static void hub(MockClient c, String action, long amount) {
		c.send(new MenuRequestPayload("challenges", action, amount));
	}

	private static void pvp(MockClient c, String action, String arg, long value) {
		c.send(new PvpActionPayload(action, c.matchId(), arg, value));
	}

	private static PvpMatch matchOf(GameTestHelper helper, MockClient c) {
		Optional<PvpMatch> m = Pvp.service().matchOf(c.player.getUUID());
		helper.assertTrue(m.isPresent(), c + " is in a match");
		return m.get();
	}

	/** Accepts the invite with the chat line's clickable [Accept] (what the target clicks). */
	private static void acceptFromChat(GameTestHelper helper, MockClient target) {
		target.drain();
		String cmd = target.chatCommand("casino pvp accept");
		helper.assertTrue(cmd != null, "the target got a clickable [Accept]: " + target);
		target.command(cmd);
	}

	/**
	 * Plays the running match out the way the two screens do (advance button of the mode screen; Coin Flip Duel:
	 * walk away / take the money) and asserts: the mode screen opened for both, the result reached both, money is
	 * conserved (players' total = start − rake) and both stakes were escrowed exactly once.
	 */
	private static void playOut(GameTestHelper helper, String mode, MockClient a, MockClient b, Consumer<MockClient> press) {
		PvpMatch[] match = {null};
		helper.succeedWhen(() -> {
			for (MockClient c : List.of(a, b)) {
				c.drain();
				if (match[0] == null) {
					match[0] = Pvp.service().matchOf(c.player.getUUID()).filter(m -> m.state() == MatchState.DRAWN).orElse(null);
				}
				JsonObject s = c.state;
				if (s != null && s.has("decision") && s.get("decision").isJsonObject()) {
					JsonObject d = s.getAsJsonObject("decision");
					CompoundTag args = MockClient.args("decision", d.get("id").getAsString(), "option", 0L, "match", d.get("match").getAsString(), "seq",
						d.get("seq").getAsLong());
					c.send(new CoinDuelNet.Action("decide", args));
				} else if (c.kind.equals("match") && c.screen != null && c.screen.startsWith("match:")) {
					press.accept(c);
				}
			}
			helper.assertTrue(match[0] != null, mode + ": the match started");
			PvpMatch m = match[0];
			helper.assertTrue(m.state() == MatchState.SETTLED, mode + ": settled (" + m.state() + ") a=" + a + " b=" + b);
			for (MockClient c : List.of(a, b)) {
				helper.assertTrue(c.opened.contains("match:" + mode), mode + ": the duel screen opened for " + c);
				helper.assertTrue(c.state != null && "SETTLED".equals(c.state.get("state").getAsString()), mode + ": the result reached " + c);
			}
			long paid = 0;
			for (long p : m.payouts()) {
				paid += p;
			}
			helper.assertTrue(paid + m.rake() == m.pot(), mode + ": payouts + rake = pot");
			helper.assertTrue(a.balance() + b.balance() == 2 * START - m.rake(), mode + ": chips conserved: " + a.balance() + " + " + b.balance()
				+ " vs " + (2 * START) + " − " + m.rake());
			a.remove(helper);
			b.remove(helper);
		});
	}

	private static final Consumer<MockClient> NO_PRESS = c -> {
	};
	/** Plinko Battle / Scratch Showdown screens: Drop! / Scratch! = extras_action pvp press. */
	private static final Consumer<MockClient> EXTRAS_PRESS = c -> c.send(new ExtrasActionPayload("pvp", "press", new CompoundTag()));
	/** Slot Showdown screen: Spin! = pvp_action press. */
	private static final Consumer<MockClient> PVP_PRESS = c -> c.send(new PvpActionPayload("press", c.matchId(), "", 0));

	// ---- Coin Flip Duel (the mode that worked): Lucky Coin set-up → chat [Accept] ----------------------------

	@GameTest(maxTicks = 1200)
	public void coinDuelLuckyCoinAndChatAccept(GameTestHelper helper) {
		MockClient[] p = two(helper);
		MockClient a = p[0];
		MockClient b = p[1];
		a.send(new CoinDuelNet.Action("challenge", MockClient.args("stake", STAKE, "heads", true, "target", b.player.getUUID().toString())));
		helper.assertTrue(a.balance() == START, "an invite escrows nothing");
		helper.assertTrue(Pvp.service().invitesFor(b.player.getUUID()).size() == 1, "invite sent: " + a);
		acceptFromChat(helper, b);
		helper.assertTrue(a.balance() == START - STAKE && b.balance() == START - STAKE, "both stakes escrowed at accept");
		playOut(helper, "coin", a, b, NO_PRESS);
	}

	// ---- Scratch Showdown from the hub: duel + lobby --------------------------------------------------------

	@GameTest(maxTicks = 1500)
	public void scratchDuelFromHub(GameTestHelper helper) {
		MockClient[] p = two(helper);
		MockClient a = p[0];
		MockClient b = p[1];
		hub(a, "p:new:scratch", 0);
		hub(a, "p:ch:" + b.player.getUUID(), STAKE);
		helper.assertTrue(Pvp.service().invitesFor(b.player.getUUID()).size() == 1, "invite sent: " + a);
		acceptFromChat(helper, b);
		helper.assertTrue(a.balance() == START - STAKE && b.balance() == START - STAKE, "both stakes escrowed at accept");
		playOut(helper, "scratch", a, b, EXTRAS_PRESS);
	}

	@GameTest(maxTicks = 1500)
	public void scratchLobbyFromHub(GameTestHelper helper) {
		MockClient[] p = two(helper);
		MockClient a = p[0];
		MockClient b = p[1];
		hub(a, "p:new:scratch", 0);
		hub(a, "p:lobby", STAKE);
		PvpMatch m = matchOf(helper, a);
		helper.assertTrue(m.state() == MatchState.LOBBY && a.balance() == START - STAKE, "lobby open, host escrowed");
		hub(b, "", 0);
		hub(b, "p:join:" + m.id, STAKE);
		helper.assertTrue(b.balance() == START - STAKE && m.participants().size() == 2, "joined: " + b);
		a.drain();
		pvp(a, "start", "", 0);
		playOut(helper, "scratch", a, b, EXTRAS_PRESS);
	}

	// ---- Plinko Battle at the Plinko machine ----------------------------------------------------------------

	@GameTest(maxTicks = 2400)
	public void plinkoBattleAtTheMachine(GameTestHelper helper) {
		MockClient[] p = two(helper);
		MockClient a = p[0];
		MockClient b = p[1];
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, ExtrasModule.PLINKO.block());
		BlockPos pos = helper.absolutePos(rel);
		helper.useBlock(rel, a.player);
		a.drain();
		helper.assertTrue(a.table != null && a.table.contains("pvp"), "the machine screen offers Plinko Battle: " + a.table);
		a.send(new TableActionPayload(pos, "pvp_host", MockClient.args("amount", STAKE, "risk", "medium", "balls", 3, "policy", "humans_only",
			"difficulty", "normal", "size", 2)));
		PvpMatch m = matchOf(helper, a);
		helper.assertTrue(a.balance() == START - STAKE, "host escrowed: " + a);
		helper.useBlock(rel, b.player);
		b.drain();
		ListTag lobbies = b.table == null ? new ListTag() : b.table.getCompoundOrEmpty("pvp").getListOrEmpty("lobbies");
		helper.assertTrue(lobbies.size() == 1, "the joiner sees the battle at the machine: " + b.table);
		b.send(new TableActionPayload(pos, "pvp_join", MockClient.args("id", lobbies.getCompoundOrEmpty(0).getStringOr("id", ""))));
		helper.assertTrue(b.balance() == START - STAKE && m.participants().size() == 2, "joined: " + b);
		helper.runAfterDelay(15, () -> {
			a.drain();
			b.drain();
			pvp(a, "start", "", 0);
			playOut(helper, "plinko", a, b, EXTRAS_PRESS);
		});
	}

	// ---- Wheel Party at the Wheel of Fortune ----------------------------------------------------------------

	@GameTest(maxTicks = 2400)
	public void wheelPartyAtTheWheel(GameTestHelper helper) {
		MockClient[] p = two(helper);
		MockClient a = p[0];
		MockClient b = p[1];
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, ExtrasModule.WHEEL.block());
		BlockPos pos = helper.absolutePos(rel);
		helper.useBlock(rel, a.player);
		a.send(new WheelPartyNet.Action("open", MockClient.args("x", pos.getX(), "y", pos.getY(), "z", pos.getZ())));
		a.send(new WheelPartyNet.Action("host", MockClient.args("cap", STAKE, "stake", STAKE, "policy", "humans_only", "difficulty", "normal", "size", 2)));
		PvpMatch m = matchOf(helper, a);
		helper.assertTrue(a.balance() == START - STAKE, "host escrowed: " + a);
		helper.useBlock(rel, b.player);
		b.send(new WheelPartyNet.Action("open", MockClient.args("x", pos.getX(), "y", pos.getY(), "z", pos.getZ())));
		b.send(new WheelPartyNet.Action("join", MockClient.args("stake", STAKE)));
		helper.assertTrue(b.balance() == START - STAKE && m.participants().size() == 2, "joined: " + b);
		helper.runAfterDelay(15, () -> {
			a.drain();
			b.drain();
			a.send(new WheelPartyNet.Action("spin", new CompoundTag()));
			playOut(helper, "wheel", a, b, NO_PRESS);
		});
	}

	// ---- Slot Showdown at a slot machine --------------------------------------------------------------------

	@GameTest(maxTicks = 3000)
	public void slotShowdownAtTheMachine(GameTestHelper helper) {
		MockClient[] p = two(helper);
		MockClient a = p[0];
		MockClient b = p[1];
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, SlotsModule.MACHINES.get(Tier.COPPER).block());
		BlockPos pos = helper.absolutePos(rel);
		SlotMachineBlockEntity be = helper.getBlockEntity(rel, SlotMachineBlockEntity.class);
		helper.assertTrue(be != null, "slot machine placed");
		helper.useBlock(rel, a.player);
		a.drain();
		helper.assertTrue(a.table != null && a.table.getCompoundOrEmpty("pvp").getBooleanOr("enabled", false), "the machine offers Slot Showdown");
		a.send(new TableActionPayload(pos, "pvp_open", MockClient.args("entry", STAKE, "spins", 3, "policy", "humans_only", "size", 2)));
		PvpMatch m = matchOf(helper, a);
		helper.assertTrue(a.balance() == START - STAKE, "host escrowed: " + a);
		helper.useBlock(rel, b.player);
		b.drain();
		ListTag lobbies = b.table == null ? new ListTag() : b.table.getCompoundOrEmpty("pvp").getListOrEmpty("lobbies");
		helper.assertTrue(lobbies.size() == 1, "the joiner sees the showdown at the machine: " + b.table);
		b.send(new TableActionPayload(pos, "pvp_join", MockClient.args("id", lobbies.getCompoundOrEmpty(0).getStringOr("id", ""))));
		helper.assertTrue(b.balance() == START - STAKE && m.participants().size() == 2, "joined: " + b);
		helper.runAfterDelay(15, () -> {
			a.drain();
			b.drain();
			pvp(a, "start", "", 0);
			playOut(helper, "slots", a, b, PVP_PRESS);
		});
	}

	// ---- /casino pvp challenge <player> <game> for every game ----------------------------------------------

	private static void commandDuel(GameTestHelper helper, String mode, Consumer<MockClient> press) {
		MockClient[] p = two(helper);
		MockClient a = p[0];
		MockClient b = p[1];
		a.command("casino pvp challenge " + b.player.getName().getString() + " " + mode + " " + STAKE);
		helper.assertTrue(Pvp.service().invitesFor(b.player.getUUID()).size() == 1, mode + ": invite sent: " + a);
		acceptFromChat(helper, b);
		helper.assertTrue(a.balance() == START - STAKE && b.balance() == START - STAKE, mode + ": both stakes escrowed at accept: " + b.chat);
		playOut(helper, mode, a, b, press);
	}

	@GameTest(maxTicks = 1500)
	public void commandChallengeScratch(GameTestHelper helper) {
		commandDuel(helper, "scratch", EXTRAS_PRESS);
	}

	@GameTest(maxTicks = 2400)
	public void commandChallengePlinko(GameTestHelper helper) {
		commandDuel(helper, "plinko", EXTRAS_PRESS);
	}

	@GameTest(maxTicks = 2400)
	public void commandChallengeWheel(GameTestHelper helper) {
		commandDuel(helper, "wheel", NO_PRESS);
	}

	@GameTest(maxTicks = 3000)
	public void commandChallengeSlots(GameTestHelper helper) {
		commandDuel(helper, "slots", PVP_PRESS);
	}

	// ---- Casino Menu → Challenges for the machine modes (v0.1.2: their hub cards did nothing) ------------------

	private static void hubDuel(GameTestHelper helper, String mode, Consumer<MockClient> press) {
		MockClient[] p = two(helper);
		MockClient a = p[0];
		MockClient b = p[1];
		hub(a, "", 0);
		CompoundTag menu = lastMenu(a);
		helper.assertTrue(menuHasButton(menu, "p:new:" + mode), mode + ": the hub offers the game: " + menu);
		hub(a, "p:new:" + mode, 0);
		helper.assertTrue(menuHasButton(lastMenu(a), "p:ch:" + b.player.getUUID()), mode + ": the rival is offered as opponent");
		hub(a, "p:ch:" + b.player.getUUID(), STAKE);
		helper.assertTrue(Pvp.service().invitesFor(b.player.getUUID()).size() == 1, mode + ": invite sent: " + a);
		acceptFromChat(helper, b);
		helper.assertTrue(a.balance() == START - STAKE && b.balance() == START - STAKE, mode + ": both stakes escrowed at accept");
		playOut(helper, mode, a, b, press);
	}

	private static CompoundTag lastMenu(MockClient c) {
		for (int i = c.inbox.size() - 1; i >= 0; i--) {
			if (c.inbox.get(i) instanceof dev.nezo.burmaldaholic.core.network.MenuPayload m) {
				return m.data();
			}
		}
		return new CompoundTag();
	}

	private static boolean menuHasButton(CompoundTag menu, String action) {
		ListTag buttons = menu.getListOrEmpty("buttons");
		for (int i = 0; i < buttons.size(); i++) {
			if (action.equals(buttons.getCompoundOrEmpty(i).getStringOr("action", ""))) {
				return true;
			}
		}
		return false;
	}

	@GameTest(maxTicks = 2400)
	public void hubDuelPlinko(GameTestHelper helper) {
		hubDuel(helper, "plinko", EXTRAS_PRESS);
	}

	@GameTest(maxTicks = 2400)
	public void hubDuelWheel(GameTestHelper helper) {
		hubDuel(helper, "wheel", NO_PRESS);
	}

	@GameTest(maxTicks = 3000)
	public void hubDuelSlots(GameTestHelper helper) {
		hubDuel(helper, "slots", PVP_PRESS);
	}

	/** Singleplayer: hub → game → Bot opponent: Normal; the match plays out against the house bot. */
	private static void hubVsBot(GameTestHelper helper, String mode, Consumer<MockClient> press) {
		MockClient a = MockClient.join(helper, new Vec3(2.5, 1, 3.5), START);
		hub(a, "p:new:" + mode, 0);
		hub(a, "p:bot:normal", STAKE);
		PvpMatch[] match = {null};
		helper.succeedWhen(() -> {
			a.drain();
			if (match[0] == null) {
				match[0] = Pvp.service().matchOf(a.player.getUUID()).filter(m -> m.state() == MatchState.DRAWN).orElse(null);
			}
			JsonObject s = a.state;
			if (s != null && s.has("decision") && s.get("decision").isJsonObject()) {
				JsonObject d = s.getAsJsonObject("decision");
				a.send(new CoinDuelNet.Action("decide", MockClient.args("decision", d.get("id").getAsString(), "option", 0L, "match",
					d.get("match").getAsString(), "seq", d.get("seq").getAsLong())));
			} else if (a.kind.equals("match")) {
				press.accept(a);
			}
			helper.assertTrue(match[0] != null, mode + ": the bot accepted and the match started: " + a);
			PvpMatch m = match[0];
			helper.assertTrue(m.state() == MatchState.SETTLED, mode + ": settled");
			helper.assertTrue(a.opened.contains("match:" + mode), mode + ": the duel screen opened: " + a);
			helper.assertTrue(a.state != null && "SETTLED".equals(a.state.get("state").getAsString()), mode + ": the result reached the player");
			long net = m.payouts()[0] - m.participants().get(0).stake();
			helper.assertTrue(a.balance() == START + net, mode + ": stake at accept, payout at settle");
			a.remove(helper);
		});
	}

	@GameTest(maxTicks = 1500)
	public void hubVsBotCoin(GameTestHelper helper) {
		hubVsBot(helper, "coin", NO_PRESS);
	}

	@GameTest(maxTicks = 1500)
	public void hubVsBotScratch(GameTestHelper helper) {
		hubVsBot(helper, "scratch", EXTRAS_PRESS);
	}

	@GameTest(maxTicks = 2400)
	public void hubVsBotPlinko(GameTestHelper helper) {
		hubVsBot(helper, "plinko", EXTRAS_PRESS);
	}

	@GameTest(maxTicks = 2400)
	public void hubVsBotWheel(GameTestHelper helper) {
		hubVsBot(helper, "wheel", NO_PRESS);
	}

	@GameTest(maxTicks = 3000)
	public void hubVsBotSlots(GameTestHelper helper) {
		hubVsBot(helper, "slots", PVP_PRESS);
	}

	// ---- the result window opens once ----------------------------------------------------------------------------

	/**
	 * The result window opens once at the settle; after the player closed it, nothing re-opens it — not the rematch
	 * window ending, not the rival leaving, not another player's lobby nearby (a real client run saw the Plinko Battle
	 * result pop up again over the next machine screen).
	 */
	@GameTest(maxTicks = 2400)
	public void resultWindowOpensOnce(GameTestHelper helper) {
		MockClient[] p = two(helper);
		MockClient a = p[0];
		MockClient b = p[1];
		BlockPos rel = new BlockPos(1, 1, 1);
		BlockPos pos = helper.absolutePos(rel);
		helper.setBlock(rel, ExtrasModule.PLINKO.block());
		helper.useBlock(rel, b.player);
		b.send(new TableActionPayload(pos, "pvp_host", MockClient.args("amount", STAKE, "risk", "low", "balls", 3, "policy", "humans_only",
			"difficulty", "normal", "size", 2)));
		helper.useBlock(rel, a.player);
		PvpMatch m = matchOf(helper, b);
		a.send(new TableActionPayload(pos, "pvp_join", MockClient.args("id", m.id)));
		b.send(new PvpActionPayload("start", m.id, "", 0));
		long[] closedAt = {-1};
		MockClient[] host = {null};
		helper.succeedWhen(() -> {
			for (MockClient c : List.of(a, b)) {
				if (closedAt[0] >= 0 && c == b) {
					continue;
				}
				c.drain();
				if (c.kind.equals("match")) {
					EXTRAS_PRESS.accept(c);
				}
			}
			if (closedAt[0] < 0) {
				helper.assertTrue(m.state() == MatchState.SETTLED && "result".equals(a.screen), "settled, result window open: " + a);
				a.closeScreen();
				a.opened.clear();
				closedAt[0] = helper.getTick();
				b.remove(helper); // the rival leaves during the rematch window
			}
			if (host[0] == null && helper.getTick() > closedAt[0] + 20) {
				// the next game: a Wheel of Fortune where the machine was; another player opens a party there
				helper.setBlock(rel, ExtrasModule.WHEEL.block());
				helper.useBlock(rel, a.player);
				host[0] = MockClient.join(helper, new Vec3(1.5, 1, 3.5), START);
				host[0].send(new WheelPartyNet.Action("open", MockClient.args("x", pos.getX(), "y", pos.getY(), "z", pos.getZ())));
				host[0].send(new WheelPartyNet.Action("host", MockClient.args("cap", STAKE, "stake", STAKE, "policy", "humans_only",
					"difficulty", "normal", "size", 2)));
				helper.useBlock(rel, a.player);
				// and a match revealing right next to the player (its steps reach them as a spectator ticker)
				MockClient c = MockClient.join(helper, new Vec3(3.5, 1, 1.5), START);
				hub(c, "p:new:scratch", 0);
				hub(c, "p:bot:normal", STAKE);
			}
			helper.assertTrue(a.opened.isEmpty(), "nothing re-opened a PvP screen after Close: " + a.opened);
			helper.assertTrue(helper.getTick() > closedAt[0] + CasinoConfig.pvp().decisionTimeoutTicks + 60 && m.phase().equals("history"),
				"waited past the rematch window: " + m.phase());
			Pvp.service().leave(host[0].player);
			host[0].remove(helper);
			a.remove(helper);
		});
	}

	// ---- after a match: the machines' PvP entries work again at once ----------------------------------------

	/**
	 * v0.1.2: a settled match stays the player's match for the rematch window + 5 min of history; the Plinko machine
	 * then sent {@code in_match} (an empty Plinko Battle card: no Start, no Join) and the slot machine a {@code mine}
	 * (a Showdown panel with only Back), and a new lobby lost to the settled match in {@code matchOf}.
	 */
	@GameTest(maxTicks = 2400)
	public void machineEntriesWorkRightAfterAMatch(GameTestHelper helper) {
		MockClient[] p = two(helper);
		MockClient a = p[0];
		MockClient b = p[1];
		hub(a, "p:new:scratch", 0);
		hub(a, "p:ch:" + b.player.getUUID(), STAKE);
		acceptFromChat(helper, b);
		PvpMatch first = matchOf(helper, a);
		BlockPos plinkoRel = new BlockPos(1, 1, 1);
		BlockPos slotRel = new BlockPos(5, 1, 1);
		helper.setBlock(plinkoRel, ExtrasModule.PLINKO.block());
		helper.setBlock(slotRel, SlotsModule.MACHINES.get(Tier.COPPER).block());
		boolean[] hosted = {false};
		helper.succeedWhen(() -> {
			for (MockClient c : List.of(a, b)) {
				c.drain();
				if (c.kind.equals("match")) {
					EXTRAS_PRESS.accept(c);
				}
			}
			helper.assertTrue(first.state() == MatchState.SETTLED, "first match settled");
			if (!hosted[0]) {
				// still in the rematch window: the machines must offer their PvP entries
				helper.useBlock(plinkoRel, a.player);
				a.drain();
				helper.assertTrue(a.table != null, "the Plinko machine screen opened: " + a);
				CompoundTag pvp = a.table.getCompoundOrEmpty("pvp");
				helper.assertFalse(pvp.contains("in_match"), "the Plinko Battle card is not stuck on the settled match: " + pvp);
				helper.useBlock(slotRel, b.player);
				b.drain();
				helper.assertTrue(b.table != null, "the slot machine screen opened: " + b);
				CompoundTag sd = b.table.getCompoundOrEmpty("pvp");
				helper.assertFalse(sd.contains("mine"), "the Showdown panel is not stuck on the settled match: " + sd);
				a.send(new TableActionPayload(helper.absolutePos(plinkoRel), "pvp_host", MockClient.args("amount", STAKE, "risk", "low", "balls", 3,
					"policy", "humans_only", "difficulty", "normal", "size", 2)));
				a.drain();
				hosted[0] = true;
			}
			PvpMatch now = matchOf(helper, a);
			helper.assertTrue(now != first && now.state() == MatchState.LOBBY, "the new lobby is the player's match: " + now.state());
			helper.assertTrue(a.table != null && a.table.getCompoundOrEmpty("pvp").getBooleanOr("lobby", false), "the card shows Start / Leave of the new lobby");
			helper.assertTrue("lobby".equals(a.screen) && now.id.equals(a.matchId()), "the lobby screen opened: " + a);
			Pvp.service().leave(a.player);
			a.remove(helper);
			b.remove(helper);
		});
	}
}
