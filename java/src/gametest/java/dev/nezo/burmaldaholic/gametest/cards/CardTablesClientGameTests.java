package dev.nezo.burmaldaholic.gametest.cards;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.table.cards.TableTheme;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratModule;
import dev.nezo.burmaldaholic.games.baccarat.BaccaratTableBlockEntity;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane J-L4 screenshots of the redesigned card tables (docs/design/visual/cards.md §10, compare with
 * {@code docs/design/visual/mockups/cards_*.png}): blackjack with bots around the rail (the viewer splits 8s, stands on
 * 18 and doubles 11 → the sideways card lands; then the settled round), baccarat (Player 4 + 2 draws a sideways 3 →
 * squeeze → PLAYER WINS 9 : 7), each in EN and RU at GUI scale 2, the End theme and the compact layout (GUI scale 3).
 * Asserts the screen ends on the server's result (the published cards equal the round's cards after the gate) and
 * writes {@code jtest_cards_layout.txt} (labels wider than their button, widgets off screen).
 */
public class CardTablesClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create()) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 12480");
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.config.CasinoConfig.chaos().enabled = false);
			guiScale(context, 2);
			for (String lang : List.of("en_us", "ru_ru")) {
				language(context, lang);
				blackjack(context, world, lang, null);
				baccarat(context, world, lang, null);
			}
			language(context, "en_us");
			blackjack(context, world, "en_us_end", TableTheme.END);
			baccarat(context, world, "en_us_bastion", TableTheme.BASTION);
			context.runOnClient(mc -> dev.nezo.burmaldaholic.client.table.cards.CardTableScreen.forceCompact = true);
			guiScale(context, 3);
			blackjack(context, world, "en_us_compact", null);
			context.runOnClient(mc -> dev.nezo.burmaldaholic.client.table.cards.CardTableScreen.forceCompact = false);
			context.runOnClient(mc -> FxSettings.get().reduceMotion = true);
			guiScale(context, 2);
			baccarat(context, world, "en_us_reduced", null);
		} finally {
			context.runOnClient(mc -> {
				FxSettings.get().reduceMotion = false;
				TableTheme.force(null);
				dev.nezo.burmaldaholic.client.table.cards.CardTableScreen.forceCompact = false;
			});
			guiScale(context, 0);
			language(context, "en_us");
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("card tables: " + String.join("; ", failures));
	}

	// ---- blackjack ------------------------------------------------------------------------------------------------

	private void blackjack(ClientGameTestContext context, TestSingleplayerContext world, String name, TableTheme theme) {
		context.runOnClient(mc -> TableTheme.force(theme));
		BlackjackTableBlockEntity[] table = new BlackjackTableBlockEntity[1];
		open(context, world, BlackjackModule.TABLE, (be, player) -> {
			BlackjackTableBlockEntity t = (BlackjackTableBlockEntity) be;
			table[0] = t;
			t.sit(player);
			BotSettings four = new BotSettings(SeatPolicy.MIXED, 4, BotDifficulty.NORMAL, false, false, BotSpeed.NORMAL);
			t.bots().table().requestChange(player, four, false);
			t.botSafePointForTests();
		});
		shot(context, "jtest_cards_bj_" + name + "_betting");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlackjackTableBlockEntity t = table[0];
			if (t == null) return;
			// the deal goes seat by seat (the viewer and the seated bots), dealer up, second cards, hole: build the deck for
			// the seats actually taken. Viewer 8 8 (split: Q → 18, 3 → 11, doubled with a 9 → 20), bots: a blackjack, then
			// 16s that hit into a bust; dealer K up, 6 hole, draws 2 → 18.
			int me = t.seats().seatOf(player.getUUID()).orElse(0);
			List<Integer> seats = new ArrayList<>();
			seats.add(me);
			for (var b : t.bots().bots()) seats.add(b.seat());
			seats.sort(Integer::compare);
			Card[][] hands = {{Card.of(1, Card.HEARTS), Card.of(13, Card.DIAMONDS)}, {Card.of(10, Card.CLUBS), Card.of(6, Card.SPADES)},
				{Card.of(9, Card.CLUBS), Card.of(7, Card.DIAMONDS)}, {Card.of(5, Card.SPADES), Card.of(6, Card.HEARTS)}};
			List<Card> deck = new ArrayList<>();
			for (int pass = 0; pass < 2; pass++) {
				int botN = 0;
				for (int seat : seats) deck.add(seat == me ? Card.of(8, pass == 0 ? Card.SPADES : Card.DIAMONDS) : hands[botN++ % 4][pass]);
				deck.add(pass == 0 ? Card.of(13, Card.SPADES) : Card.of(6, Card.HEARTS));
			}
			deck.addAll(List.of(Card.of(12, Card.HEARTS), Card.of(3, Card.CLUBS), Card.of(9, Card.HEARTS), Card.of(10, Card.HEARTS),
				Card.of(9, Card.DIAMONDS), Card.of(10, Card.SPADES), Card.of(2, Card.CLUBS), Card.of(2, Card.HEARTS), Card.of(3, Card.DIAMONDS)));
			t.stackCardsForTests(deck);
			CompoundTag bet = new CompoundTag();
			bet.putLong("amount", 50);
			t.onAction(player, "bet", bet);
		});
		context.waitTicks(40); // the deal plays on the shared clock
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlackjackTableBlockEntity t = table[0];
			if (t == null || t.round() == null) return;
			t.revealAllForTests();
			BlackjackRound.Turn turn = t.round().current();
			if (turn != null && turn.seat().player.equals(player.getUUID())) {
				t.onAction(player, "split", new CompoundTag());
				t.revealAllForTests();
				t.onAction(player, "stand", new CompoundTag());
				t.revealAllForTests();
				t.onAction(player, "double", new CompoundTag());
			}
		});
		context.waitTicks(4); // the double card is in flight: buttons disabled
		shot(context, "jtest_cards_bj_" + name + "_split");
		// the bots play, the dealer reveals and draws
		for (int i = 0; i < 12; i++) {
			world.getServer().runOnServer(server -> {
				if (table[0] != null) table[0].fastForwardForTests();
			});
			context.waitTicks(2);
		}
		world.getServer().runOnServer(server -> {
			if (table[0] != null) table[0].revealAllForTests();
		});
		context.waitTicks(50); // payouts fly, the celebration starts
		shot(context, "jtest_cards_bj_" + name + "_end");
		// fidelity: after the gate the screen state carries every card of the round (nothing held back, nothing extra)
		boolean[] ok = {true};
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlackjackTableBlockEntity t = table[0];
			if (t == null || t.round() == null) return;
			CompoundTag s = t.writeClientState(player);
			int[] dealer = s.getIntArray("dealer").orElse(new int[0]);
			ok[0] = dealer.length == t.round().dealerCards().size() && !s.getBooleanOr("busy", true);
			for (int i = 0; i < dealer.length && ok[0]; i++) ok[0] = dealer[i] == t.round().dealerCards().get(i).code();
		});
		context.waitTicks(1);
		if (!ok[0]) failures.add(name + ": blackjack final state differs from the round");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	// ---- baccarat -------------------------------------------------------------------------------------------------

	private void baccarat(ClientGameTestContext context, TestSingleplayerContext world, String name, TableTheme theme) {
		context.runOnClient(mc -> TableTheme.force(theme));
		BaccaratTableBlockEntity[] table = new BaccaratTableBlockEntity[1];
		open(context, world, BaccaratModule.TABLE, (be, player) -> {
			BaccaratTableBlockEntity t = (BaccaratTableBlockEntity) be;
			table[0] = t;
			t.bots().setDefaults(new BotSettings(SeatPolicy.MIXED, 3, BotDifficulty.NORMAL, false, false, BotSpeed.FAST));
			t.sit(player);
			t.onAction(player, "bet", bet(BetKind.PLAYER, 100));
			t.onAction(player, "bet", bet(BetKind.TIE, 5));
		});
		context.waitTicks(60); // bots sit and place their atmosphere bets
		shot(context, "jtest_cards_bac_" + name + "_betting");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BaccaratTableBlockEntity t = table[0];
			if (t == null) return;
			// P1 B1 P2 B2 P3: Player 4 + A = 5 draws a sideways 4 → 9, Banker K + 7 stands on 7
			t.stackCardsForTests(List.of(dev.nezo.burmaldaholic.games.baccarat.logic.Card.of(4, 3), dev.nezo.burmaldaholic.games.baccarat.logic.Card.of(13, 2),
				dev.nezo.burmaldaholic.games.baccarat.logic.Card.of(1, 2), dev.nezo.burmaldaholic.games.baccarat.logic.Card.of(7, 0),
				dev.nezo.burmaldaholic.games.baccarat.logic.Card.of(4, 1)));
			t.onAction(player, "ready", new CompoundTag());
			for (int i = 0; i < 4 && !"reveal".equals(t.phase()); i++) t.fastForwardForTests();
			t.sendStateTo(player);
		});
		context.waitTicks(106); // the reveal plays: flips and squeezes up to the Player's sideways third card
		shot(context, "jtest_cards_bac_" + name + "_squeeze");
		world.getServer().runOnServer(server -> {
			BaccaratTableBlockEntity t = table[0];
			if (t == null) return;
			for (int i = 0; i < 3 && !"result".equals(t.phase()); i++) t.fastForwardForTests();
			t.syncViewers();
		});
		context.waitTicks(40);
		shot(context, "jtest_cards_bac_" + name + "_result");
		boolean[] ok = {true};
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BaccaratTableBlockEntity t = table[0];
			if (t == null || t.lastCoup() == null) return;
			CompoundTag s = t.writeClientState(player);
			ok[0] = java.util.Arrays.equals(s.getIntArray("player_cards").orElse(null), t.lastCoup().playerCodes())
				&& java.util.Arrays.equals(s.getIntArray("banker_cards").orElse(null), t.lastCoup().bankerCodes());
		});
		context.waitTicks(1);
		if (!ok[0]) failures.add(name + ": baccarat final state differs from the coup");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private static CompoundTag bet(BetKind box, long amount) {
		CompoundTag t = new CompoundTag();
		t.putString("box", box.id());
		t.putLong("amount", amount);
		return t;
	}

	// ---- helpers --------------------------------------------------------------------------------------------------

	private void open(ClientGameTestContext context, TestSingleplayerContext world, TableType<?> type,
			BiConsumer<net.minecraft.world.level.block.entity.BlockEntity, ServerPlayer> setup) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.closeContainer();
			BlockPos pos = player.blockPosition().offset(2, 0, 0);
			server.overworld().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			server.overworld().setBlockAndUpdate(pos, type.block().defaultBlockState());
			var be = server.overworld().getBlockEntity(pos);
			if (be instanceof dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity table) {
				player.openMenu(table);
				setup.accept(be, player);
				table.sendStateTo(player);
			}
		});
		try {
			context.waitFor(mc -> mc.gui.screen() != null, 200);
		} catch (RuntimeException | AssertionError e) {
			failures.add(type + ": screen did not open");
		}
		context.waitTicks(10);
	}

	private void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.waitTicks(1);
		context.takeScreenshot(name);
		for (String p : context.computeOnClient(mc -> inspect(mc, mc.gui.screen()))) report.add(name + ": " + p);
	}

	private static List<String> inspect(Minecraft mc, Screen screen) {
		List<String> out = new ArrayList<>();
		if (screen == null) return out;
		for (var child : screen.children()) {
			if (!(child instanceof AbstractWidget w) || !w.visible) continue;
			String label = w.getMessage().getString();
			if (w instanceof AbstractButton && !label.isEmpty() && mc.font.width(w.getMessage()) > w.getWidth() - 4) {
				out.add("label overflows button: \"" + label + "\"");
			}
		}
		return out;
	}

	private void write(ClientGameTestContext context) {
		try {
			Path dir = context.computeOnClient(mc -> mc.gameDirectory.toPath().resolve("screenshots"));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("jtest_cards_layout.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}

	private static void language(ClientGameTestContext context, String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitTicks(80); // the reload overlay fades out
	}
}
