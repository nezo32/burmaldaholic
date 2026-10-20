package dev.nezo.burmaldaholic.gametest.v2.blackjack;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableNetworking;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackModule;
import dev.nezo.burmaldaholic.games.blackjack.BlackjackTableBlockEntity;
import dev.nezo.burmaldaholic.games.blackjack.logic.BlackjackRound;
import dev.nezo.burmaldaholic.games.blackjack.logic.Shoe;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Arrays;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * E5, a countable shoe: through the packet handler ({@link TableNetworking#receive}) with a survival player whose table menu
 * was opened by using the block. The client state carries size / dealt / left / cut / discard counted from the cards ON
 * THE FELT; the shoe is reshuffled only when the cut card is out, at the collect between rounds (never mid-round); the
 * shoe's order and position survive a save / load and never reach the update tag.
 */
public class BlackjackShoeGameTests {
	private static final Transaction TEST = Transaction.of("blackjack", "gametest");

	private static ServerPlayer opened(GameTestHelper helper, BlockPos rel) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "bj-shoe"), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 1.5)));
		Economies.get().setBalance(server, player.getUUID(), 10_000, TEST);
		helper.useBlock(rel, player);
		helper.assertTrue(player.containerMenu instanceof CasinoTableMenu, "using the block opens the table menu");
		return player;
	}

	private static void act(ServerPlayer p, BlackjackTableBlockEntity t, String action, CompoundTag args) {
		TableNetworking.receive(p, new TableActionPayload(t.getBlockPos(), action, args));
	}

	private static CompoundTag amount(long n) {
		CompoundTag t = new CompoundTag();
		t.putLong("amount", n);
		return t;
	}

	private static CompoundTag shoe(BlackjackTableBlockEntity t, ServerPlayer p) {
		return t.writeClientState(p).getCompoundOrEmpty("shoe");
	}

	/** Plays the viewer's round to its end through the handler (declines insurance, stands), then collects it. */
	private static void finish(GameTestHelper helper, ServerPlayer p, BlackjackTableBlockEntity t) {
		for (int i = 0; i < 12 && t.round() != null && t.round().phase() != BlackjackRound.Phase.DONE; i++) {
			t.revealAllForTests();
			if (t.round().phase() == BlackjackRound.Phase.INSURANCE) {
				act(p, t, "insurance", amount(0));
				CompoundTag no = new CompoundTag();
				no.putBoolean("take", false);
				act(p, t, "even_money", no);
			} else {
				act(p, t, "stand", new CompoundTag());
			}
		}
		t.revealAllForTests();
		helper.assertTrue(t.round() != null && t.round().phase() == BlackjackRound.Phase.DONE, "the round finished");
		t.collectForTests();
		helper.assertTrue(t.round() == null, "collected");
	}

	@GameTest
	@SuppressWarnings("removal")
	public void shoeCountsCutCardAndSave(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, BlackjackModule.TABLE.block());
		BlackjackTableBlockEntity t = helper.getBlockEntity(rel, BlackjackTableBlockEntity.class);
		ServerPlayer p = opened(helper, rel);
		try {
			act(p, t, "sit", new CompoundTag());
			// betting: a fresh shoe is public before the first round
			CompoundTag s0 = shoe(t, p);
			Shoe shoe = t.shoeForTests();
			helper.assertTrue(shoe != null && s0.getIntOr("size", 0) == shoe.size() && shoe.size() % 52 == 0, "size sent: " + s0);
			helper.assertTrue(s0.getIntOr("dealt", -1) == 0 && s0.getIntOr("left", -1) == shoe.size() && s0.getIntOr("discard", -1) == 0,
				"fresh shoe: " + s0);
			int cut = s0.getIntOr("cut", -1);
			helper.assertTrue(cut > 0 && cut < shoe.size(), "cut card sent: " + cut);
			act(p, t, "bet", amount(10));
			helper.assertTrue(t.round() != null, "the bet dealt a round");
			// mid-deal: only cards on the felt count (one per beat), never what the round has drawn ahead
			CompoundTag s1 = shoe(t, p);
			helper.assertTrue(s1.getIntOr("dealt", -1) < shoe.dealt(), "undealt cards are not counted: " + s1 + " drawn " + shoe.dealt());
			t.revealAllForTests();
			CompoundTag s2 = shoe(t, p);
			helper.assertTrue(s2.getIntOr("dealt", -1) == shoe.dealt() && s2.getIntOr("left", -1) == shoe.size() - shoe.dealt(),
				"all on the felt: " + s2);
			finish(helper, p, t);
			int afterOne = shoe.dealt();
			CompoundTag s3 = shoe(t, p);
			helper.assertTrue(s3.getIntOr("discard", -1) == afterOne && s3.getIntOr("dealt", -1) == afterOne, "collected into the tray: " + s3);
			helper.assertTrue(t.shoeForTests() == shoe && afterOne > 0, "no reshuffle before the cut card");

			// the cut card: one card short of it, the next round passes it and must NOT reshuffle mid-round
			t.burnShoeForTests(cut - 1 - shoe.dealt());
			act(p, t, "bet", amount(10));
			helper.assertTrue(t.round() != null && shoe.dealt() >= cut, "the round dealt past the cut card");
			int midRound = shoe.dealt();
			t.revealAllForTests();
			helper.assertTrue(shoe.dealt() >= midRound && !shoe.refilled(), "no mid-round reshuffle");
			finish(helper, p, t);
			CompoundTag s4 = shoe(t, p);
			helper.assertTrue(shoe.dealt() == 0 && s4.getIntOr("dealt", -1) == 0 && s4.getIntOr("discard", -1) == 0
				&& s4.getIntOr("left", -1) == shoe.size(), "reshuffled at the collect: " + s4);
			helper.assertTrue("gui.burmaldaholic.blackjack.shuffling".equals(t.writeClientState(p).getStringOr("notice", "")), "Shuffling status");

			// save / load: order + position
			t.burnShoeForTests(37);
			var registries = helper.getLevel().registryAccess();
			CompoundTag saved = t.saveWithoutMetadata(registries);
			BlockPos rel2 = new BlockPos(3, 1, 1);
			helper.setBlock(rel2, BlackjackModule.TABLE.block());
			BlackjackTableBlockEntity copy = helper.getBlockEntity(rel2, BlackjackTableBlockEntity.class);
			copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
			Shoe loaded = copy.shoeForTests();
			helper.assertTrue(loaded != null && loaded.dealt() == 37 && loaded.discards() == 37 && Arrays.equals(loaded.codes(), shoe.codes()),
				"the shoe survives a save");
			helper.assertTrue(!t.getUpdateTag(registries).toString().contains("blackjack_shoe"), "the shoe order never reaches the update tag");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
		helper.succeed();
	}
}
