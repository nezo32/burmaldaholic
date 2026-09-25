package dev.nezo.burmaldaholic.gametest.core;

import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.chips.ChipItem;
import dev.nezo.burmaldaholic.core.chips.ChipMath;
import dev.nezo.burmaldaholic.core.chips.Chips;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.table.TableLifecycle;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.core.wager.Stake;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import dev.nezo.burmaldaholic.core.wager.WagerVeto;
import dev.nezo.burmaldaholic.core.wager.Wagers;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.logic.Card;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import dev.nezo.burmaldaholic.games.extras.server.ScratchData;
import dev.nezo.burmaldaholic.games.extras.server.ScratchGame;
import dev.nezo.burmaldaholic.lastchance.LastChance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;

/** Regression tests for docs/review/java-review.md (M1–M3, m1–m8; B1 is in PokerGameTests). */
public class JavaReviewGameTests {
	private static final Transaction TEST = Transaction.of("core", "gametest");

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, long balance) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Economies.get().setBalance(helper.getLevel().getServer(), p.getUUID(), balance, TEST);
		p.getInventory().clearContent();
		return p;
	}

	private static void remove(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	private static long balance(ServerPlayer p) {
		return Economies.get().balance(p);
	}

	private static CompoundTag amount(long n) {
		CompoundTag t = new CompoundTag();
		t.putLong("amount", n);
		return t;
	}

	/** Player 10,10 — dealer 10 up, 7 hole; then 2s (a double busts the player). */
	private static BlackjackTableBlockEntity blackjack(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, BlackjackModule.TABLE.block());
		BlackjackTableBlockEntity t = helper.getBlockEntity(pos, BlackjackTableBlockEntity.class);
		List<Card> cards = new ArrayList<>(List.of(Card.of(10), Card.of(10), Card.of(10), Card.of(7)));
		for (int i = 0; i < 10; i++) {
			cards.add(Card.of(2));
		}
		t.stackCardsForTests(cards);
		return t;
	}

	// ---- M1 -------------------------------------------------------------------------------------

	/**
	 * M1: a table that stops mid-round (chunk unload / server stop call {@code playOutNow}) settles the drawn round
	 * — 20 vs 17 pays — and saves no open stake, so the next load has nothing to refund.
	 */
	@GameTest
	public void m1DrawnRoundIsSettledWhenTheTableStops(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		try {
			BlackjackTableBlockEntity t = blackjack(helper, new BlockPos(1, 1, 1));
			helper.assertTrue(TableLifecycle.tracked(t), "loaded tables are known to core (server-stop play-out)");
			t.sit(p);
			t.onAction(p, "bet", amount(10));
			helper.assertTrue(t.round() != null && balance(p) == 990, "dealt, stake taken");
			helper.assertTrue(t.playOutNow("gametest"), "a round was in play");
			helper.assertTrue(balance(p) == 1010, "played out: 20 vs 17 wins (a refund would leave 1000), got " + balance(p));
			helper.assertTrue(t.openStakes().isEmpty(), "nothing open");
			CompoundTag saved = t.saveWithoutMetadata(helper.getLevel().registryAccess());
			helper.assertFalse(saved.contains("burmaldaholic_open_stakes"), "no stake saved for a reload refund");
			helper.assertFalse(t.playOutNow("again"), "idempotent");
			helper.assertTrue(balance(p) == 1010, "paid once");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	// ---- M2 -------------------------------------------------------------------------------------

	/**
	 * M2: a raise (blackjack double) after the table's ownership changed goes to — and is reserved on — the bank
	 * the round started with. Table A: house round, then linked → the double still goes to the house. Table B:
	 * owned round, then unlinked → the double still goes to the owner's bankroll, and its reservation is released.
	 */
	@GameTest(maxTicks = 400)
	public void m2RaiseAfterOwnershipChangeStaysWithTheRoundsBank(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Economy eco = Economies.get();
		BlockPos posA = new BlockPos(1, 1, 1);
		BlockPos posB = new BlockPos(3, 1, 1);
		Map<BlockPos, OwnedTable> owned = new ConcurrentHashMap<>();
		TableOwnershipProvider prev = CoreServices.tableOwnership();
		CoreServices.setTableOwnership((level, pos) -> owned.containsKey(pos) ? Optional.of(owned.get(pos)) : prev.owner(level, pos));
		String bankA = "gametest_m2a_" + UUID.randomUUID();
		String bankB = "gametest_m2b_" + UUID.randomUUID();
		UUID owner = UUID.randomUUID();
		eco.bankrolls(server).open(bankA, owner);
		eco.bankrolls(server).open(bankB, owner);
		helper.assertTrue(eco.transfer(server, AccountId.HOUSE, AccountId.bankroll(bankA), 1000, TEST).ok()
			&& eco.transfer(server, AccountId.HOUSE, AccountId.bankroll(bankB), 1000, TEST).ok(), "bankrolls funded");
		ServerPlayer a = player(helper, 1000);
		ServerPlayer b = player(helper, 1000);
		BlackjackTableBlockEntity tableA = blackjack(helper, posA);
		BlackjackTableBlockEntity tableB = blackjack(helper, posB);
		// A: house round, then linked
		tableA.sit(a);
		tableA.onAction(a, "bet", amount(10));
		owned.put(helper.absolutePos(posA), new OwnedTable(owner, bankA, 0, 0, true));
		tableA.onAction(a, "double", new CompoundTag());
		// B: owned round, then unlinked
		owned.put(helper.absolutePos(posB), new OwnedTable(owner, bankB, 0, 0, true));
		tableB.sit(b);
		tableB.onAction(b, "bet", amount(10));
		helper.assertTrue(eco.bankrolls(server).get(bankB).orElseThrow().reserved() > 0, "B's bet reserved on bankroll B");
		owned.remove(helper.absolutePos(posB));
		tableB.onAction(b, "double", new CompoundTag());
		helper.assertTrue(balance(a) == 980 && balance(b) == 980, "both doubled (20 + 2 busts)");
		helper.succeedWhen(() -> {
			helper.assertTrue(tableA.openStakes().isEmpty() && tableB.openStakes().isEmpty(), "rounds settled");
			Economy.BankrollInfo infoA = eco.bankrolls(server).get(bankA).orElseThrow();
			Economy.BankrollInfo infoB = eco.bankrolls(server).get(bankB).orElseThrow();
			CoreServices.setTableOwnership(prev);
			eco.bankrolls(server).close(bankA);
			eco.bankrolls(server).close(bankB);
			remove(helper, a);
			remove(helper, b);
			helper.assertTrue(infoA.balance() == 1000 && infoA.reserved() == 0, "A's house round never touched bankroll A: " + infoA);
			helper.assertTrue(infoB.balance() == 1020 && infoB.reserved() == 0, "B's whole round (bet + double) went to bankroll B, reservation released: " + infoB);
		});
	}

	// ---- M3 / m2 ------------------------------------------------------------------------------

	/** M3: a huge withdrawal hands out at most one inventory of stacks (nothing dropped); a bad denomination is refused. */
	@GameTest
	public void m3WithdrawIsCappedAndDenominationValidated(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, CoreContent.CASHIER.block());
		CashierBlockEntity cashier = helper.getBlockEntity(pos, CashierBlockEntity.class);
		ServerPlayer p = player(helper, 100_000_000L);
		try {
			helper.assertFalse(cashier.withdraw(p, 1000, 7), "denomination 7 does not exist");
			helper.assertTrue(balance(p) == 100_000_000L, "nothing taken");
			helper.assertTrue(cashier.withdraw(p, 100_000_000L, 0), "withdraw (capped)");
			long capped = ChipMath.capToStacks(100_000_000L, 0, CashierBlockEntity.MAX_WITHDRAW_STACKS, 64);
			helper.assertTrue(balance(p) == 100_000_000L - capped, "only the capped amount left the balance: " + balance(p));
			long carried = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				carried += ChipItem.valueOf(p.getInventory().getItem(i));
			}
			AABB box = new AABB(p.blockPosition()).inflate(2);
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().getItem() instanceof ChipItem);
			helper.assertTrue(carried == capped && dropped.isEmpty(), "everything fits in the inventory: carried " + carried + ", dropped " + dropped.size());
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** m2: at the balance cap, deposit / buy take only what fits and leave the rest in the inventory. */
	@GameTest
	public void m2DepositAndBuyStopAtTheBalanceCap(GameTestHelper helper) {
		BlockPos pos = new BlockPos(1, 1, 1);
		helper.setBlock(pos, CoreContent.CASHIER.block());
		CashierBlockEntity cashier = helper.getBlockEntity(pos, CashierBlockEntity.class);
		long max = CasinoConfig.economy().maxBalance;
		ServerPlayer p = player(helper, max - 150);
		try {
			p.getInventory().add(new ItemStack(Chips.item(100), 2));
			helper.assertTrue(cashier.depositAll(p) == 100, "one 100 chip fits");
			helper.assertTrue(balance(p) == max - 50 && p.getInventory().countItem(Chips.item(100)) == 1, "the other stays in the inventory");
			int rate = CashierBlockEntity.emeraldBuyRate(p);
			p.getInventory().add(new ItemStack(Items.EMERALD, 64));
			CompoundTag buy = new CompoundTag();
			buy.putInt("count", 64);
			cashier.onAction(p, "buy", buy);
			long bought = 50 / rate;
			helper.assertTrue(p.getInventory().countItem(Items.EMERALD) == 64 - bought, "only " + bought + " emeralds taken");
			helper.assertTrue(balance(p) == max - 50 + bought * rate, "nothing lost to the cap");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	// ---- m3 / m4 / m8 ---------------------------------------------------------------------------

	/** m3: an XP stake takes whole levels only; the partial progress survives a loss. */
	@GameTest
	public void m3XpStakeKeepsPartialProgress(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		try {
			p.setExperienceLevels(10);
			p.experienceProgress = 0.5f;
			Result<Stake> r = Stakes.xp(p, "gametest", 5);
			helper.assertTrue(r.isOk(), "staked: " + (r.isOk() ? "" : r.error().getString()));
			helper.assertTrue(p.experienceLevel == 5 && p.experienceProgress == 0.5f, "progress untouched: " + p.experienceProgress);
			Stakes.settle(p, r.value(), Stakes.Outcome.LOSS, 0);
			helper.assertTrue(p.experienceLevel == 5 && p.experienceProgress == 0.5f, "loss keeps the progress");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** m4: pawn stakes are refused at an owned table (the bankroll cannot hold them); chips pass the gate. */
	@GameTest
	public void m4PawnRefusedAtOwnedTables(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
		TableOwnershipProvider prev = CoreServices.tableOwnership();
		CoreServices.setTableOwnership((level, pos) -> pos.equals(abs) ? Optional.of(new OwnedTable(UUID.randomUUID(), "gametest_m4", 0, 0, true)) : prev.owner(level, pos));
		try {
			Component err = Wagers.check(p, new WagerVeto.Context("wheel_of_fortune", Stake.Kind.XP, abs, false));
			helper.assertTrue(err != null && err.getContents() instanceof TranslatableContents tc && tc.getKey().equals("gui.burmaldaholic.error.pawn_owned_table"),
				"XP pawn refused at an owned wheel: " + err);
			helper.assertTrue(Wagers.check(p, new WagerVeto.Context("wheel_of_fortune", Stake.Kind.CHIPS, abs, false)) == null, "chips allowed");
			p.setExperienceLevels(10);
			helper.assertFalse(Stakes.xp(p, "wheel_of_fortune", 5, abs).isOk(), "Stakes.xp at the owned wheel refused");
			helper.assertTrue(p.experienceLevel == 10, "levels untouched");
		} finally {
			CoreServices.setTableOwnership(prev);
			remove(helper, p);
		}
		helper.succeed();
	}

	/** m8: a settled bet returns its stake as a non-garnishable transfer; only the winnings reach credit hooks. */
	@GameTest
	public void m8OnlyWinningsAreGarnishable(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		AtomicLong garnishable = new AtomicLong();
		UUID id = p.getUUID();
		Economies.get().addCreditHook((server, player, amt, why) -> {
			if (player.equals(id)) {
				garnishable.addAndGet(amt);
			}
			return amt;
		});
		try {
			BlackjackTableBlockEntity t = blackjack(helper, new BlockPos(1, 1, 1));
			t.sit(p);
			t.onAction(p, "bet", amount(10));
			t.onAction(p, "stand", new CompoundTag());
			t.playOutNow("gametest");
			helper.assertTrue(balance(p) == 1010, "20 vs 17 pays 20");
			helper.assertTrue(garnishable.get() == 10, "only the 10 won is garnishable, got " + garnishable.get());
			Result<Stake> r = Stakes.chips(p, "gametest", 50, 1, 0);
			helper.assertTrue(r.isOk(), "chip stake");
			Stakes.settle(p, r.value(), Stakes.Outcome.WIN, 30);
			helper.assertTrue(balance(p) == 1040 && garnishable.get() == 40, "Stakes: stake back + 30 winnings garnishable: " + garnishable.get());
		} finally {
			remove(helper, p); // the hook stays registered but only counts this (random, now removed) player
		}
		helper.succeed();
	}

	// ---- m5 / m6 --------------------------------------------------------------------------------

	/** m5: the Last Chance scar is dormant while casino mode is off and comes back when it is on. Synchronous. */
	@GameTest
	public void m5ScarDormantWhileCasinoModeOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean before = CasinoMode.isEnabled(level);
		ServerPlayer p = player(helper, 0);
		try {
			LastChance.setState(p, LastChance.state(p).withScar(2));
			LastChance.refreshScar(p);
			helper.assertTrue(p.getMaxHealth() == 18f, "scar applied: " + p.getMaxHealth());
			CasinoMode.set(level.getServer(), false);
			LastChance.refreshScar(p);
			helper.assertTrue(p.getMaxHealth() == 20f, "dormant while off: " + p.getMaxHealth());
			helper.assertTrue(LastChance.state(p).scarHp() == 2, "the scar itself is kept");
			CasinoMode.set(level.getServer(), true);
			LastChance.refreshScar(p);
			helper.assertTrue(p.getMaxHealth() == 18f, "back when on");
		} finally {
			CasinoMode.set(level.getServer(), before);
			remove(helper, p);
		}
		helper.succeed();
	}

	/** m6: decimal multipliers use the translated separator ("0.5" EN, «0,5» RU). */
	@GameTest
	public void m6DecimalSeparatorIsTranslated(GameTestHelper helper) {
		Component c = Texts.decimal("0.5");
		helper.assertTrue(c.getContents() instanceof PlainTextContents.LiteralContents l && l.text().equals("0"), "integer part");
		helper.assertTrue(c.getSiblings().size() == 2 && c.getSiblings().get(0).getContents() instanceof TranslatableContents tc
			&& tc.getKey().equals(Texts.DECIMAL_SEPARATOR) && c.getSiblings().get(1).getString().equals("5"), "separator is a translation: " + c);
		helper.assertTrue(Texts.decimal("10").getString().equals("10"), "whole numbers stay plain");
		helper.succeed();
	}

	// ---- m1 / m7 --------------------------------------------------------------------------------

	/** m1: a scratched card's item carries only id + mask; the face lives in world data until the card is finished. */
	@GameTest
	public void m1ScratchFaceNeverOnTheItem(GameTestHelper helper) {
		ServerPlayer p = player(helper, 0);
		MinecraftServer server = helper.getLevel().getServer();
		try {
			p.getInventory().add(new ItemStack(ExtrasModule.SCRATCH_CARD));
			CompoundTag first = new CompoundTag();
			first.putString("kind", "basic");
			first.putString("id", "");
			first.putInt("cell", 0);
			ScratchGame.action(p, "scratch", first);
			ItemStack card = ItemStack.EMPTY;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (ScratchGame.data(p.getInventory().getItem(i)) != null) {
					card = p.getInventory().getItem(i);
				}
			}
			helper.assertFalse(card.isEmpty(), "card started");
			CompoundTag onItem = card.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(ScratchGame.DATA_KEY);
			String id = onItem.getStringOr("id", "");
			helper.assertTrue(!id.isEmpty() && onItem.getIntOr("mask", 0) == 1, "id + mask on the item: " + onItem);
			for (String secret : List.of("cells", "prize", "creeper", "top")) {
				helper.assertFalse(onItem.contains(secret), "'" + secret + "' is not client-visible: " + onItem);
			}
			helper.assertTrue(ScratchData.get(server).face(id) != null, "face kept server-side");
			CompoundTag all = new CompoundTag();
			all.putString("kind", "basic");
			all.putString("id", id);
			ScratchGame.action(p, "all", all);
			helper.assertTrue(p.getInventory().countItem(ExtrasModule.SCRATCH_CARD_USED) == 1, "finished");
			helper.assertTrue(ScratchData.get(server).face(id) == null, "face forgotten once finished");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	/** m7: Coin Flip actions need the Lucky Coin in hand or a screen the server opened. */
	@GameTest
	public void m7CoinFlipNeedsTheItemServerSide(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1000);
		try {
			helper.assertFalse(ExtrasGames.mayAct(p, CoinFlipGame.SCREEN, ExtrasModule.LUCKY_COIN), "crafted payload without the coin is ignored");
			p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(ExtrasModule.LUCKY_COIN));
			helper.assertTrue(ExtrasGames.mayAct(p, CoinFlipGame.SCREEN, ExtrasModule.LUCKY_COIN), "coin in the off hand");
			p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
			CoinFlipGame.open(p);
			helper.assertTrue(ExtrasGames.mayAct(p, CoinFlipGame.SCREEN, ExtrasModule.LUCKY_COIN), "screen opened by the server");
		} finally {
			ExtrasGames.forgetScreen(p.getUUID());
			remove(helper, p);
		}
		helper.succeed();
	}
}
