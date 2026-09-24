package dev.nezo.burmaldaholic.games.extras;

import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.table.TableRegistrar;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.extras.block.PlinkoBlockEntity;
import dev.nezo.burmaldaholic.games.extras.block.WheelBlockEntity;
import dev.nezo.burmaldaholic.games.extras.item.DiceItem;
import dev.nezo.burmaldaholic.games.extras.item.LuckyCoinItem;
import dev.nezo.burmaldaholic.games.extras.item.ScratchCardItem;
import dev.nezo.burmaldaholic.games.extras.item.UsedScratchCardItem;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasActionPayload;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasErrorPayload;
import dev.nezo.burmaldaholic.games.extras.net.ExtrasScreenPayload;
import dev.nezo.burmaldaholic.games.extras.server.CoinFlipGame;
import dev.nezo.burmaldaholic.games.extras.server.DiceGame;
import dev.nezo.burmaldaholic.games.extras.server.ExtrasGames;
import dev.nezo.burmaldaholic.games.extras.server.ScratchGame;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * Small games (GAME_DESIGN.md §11): Coin Flip (Lucky Coin), Wheel of Fortune (block), Scratch Cards (items),
 * Plinko (block), Dice Duel (dice item: vs house / PvP).
 *
 * <p>The two machines are core tables ({@code CasinoTableBlockEntity}, generic table payloads, owned-casino
 * bankrolls). The item games use this module's own payloads ({@link ExtrasActionPayload} in,
 * {@link ExtrasScreenPayload}/{@link ExtrasErrorPayload} out). Every round is staked, drawn and settled in the
 * same server tick; client animations are cosmetic, so disconnects never strand a stake. Pure rules and the RTP
 * tests live in {@code logic/}.
 */
public final class ExtrasModule implements CasinoModule {
	public static final String ID = "extras";

	public static Item LUCKY_COIN;
	public static Item DICE;
	public static Item SCRATCH_CARD;
	public static Item SCRATCH_CARD_GOLD;
	public static Item SCRATCH_CARD_USED;
	public static TableType<WheelBlockEntity> WHEEL;
	public static TableType<PlinkoBlockEntity> PLINKO;
	public static SoundEvent COIN_FLIP_SOUND;
	public static SoundEvent PLINKO_PEG_SOUND;
	public static SoundEvent DICE_ROLL_SOUND;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// Cashier "Shop" tab (UI.md §3): scratch cards (Gold is VIP-gated, §11.3).
		for (dev.nezo.burmaldaholic.games.extras.logic.Scratch.Kind kind : dev.nezo.burmaldaholic.games.extras.logic.Scratch.Kind.values()) {
			boolean gold = kind == dev.nezo.burmaldaholic.games.extras.logic.Scratch.Kind.GOLD;
			dev.nezo.burmaldaholic.core.cashier.CashierShop.add(new dev.nezo.burmaldaholic.core.cashier.CashierShop.Offer(
				gold ? "scratch_card_gold" : "scratch_card",
				() -> new net.minecraft.world.item.ItemStack(gold ? SCRATCH_CARD_GOLD : SCRATCH_CARD),
				() -> dev.nezo.burmaldaholic.games.extras.server.ScratchGame.price(kind), kind.minTier(),
				() -> dev.nezo.burmaldaholic.core.config.CasinoConfig.extras().scratch.enabled));
		}
		dev.nezo.burmaldaholic.core.menu.CasinoMenu.register(new dev.nezo.burmaldaholic.games.extras.server.ChallengesPage());
		LUCKY_COIN = ctx.registry().item("lucky_coin", LuckyCoinItem::new, new Item.Properties().stacksTo(1));
		SCRATCH_CARD = ctx.registry().item("scratch_card", p -> new ScratchCardItem(p, Scratch.Kind.BASIC), new Item.Properties().stacksTo(16));
		SCRATCH_CARD_GOLD = ctx.registry().item("scratch_card_gold", p -> new ScratchCardItem(p, Scratch.Kind.GOLD), new Item.Properties().stacksTo(16));
		SCRATCH_CARD_USED = ctx.registry().item("scratch_card_used", UsedScratchCardItem::new, new Item.Properties().stacksTo(64));
		DICE = ctx.registry().item("dice", DiceItem::new, new Item.Properties().stacksTo(64));

		WHEEL = ctx.tables().register("wheel_of_fortune", WheelBlockEntity::new,
			TableRegistrar.defaultProperties().mapColor(MapColor.COLOR_YELLOW).sound(SoundType.WOOD));
		PLINKO = ctx.tables().register("plinko_machine", PlinkoBlockEntity::new,
			TableRegistrar.defaultProperties().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3.0f));

		COIN_FLIP_SOUND = ctx.registry().sound("coin_flip");
		PLINKO_PEG_SOUND = ctx.registry().sound("plinko_peg");
		DICE_ROLL_SOUND = ctx.registry().sound("dice_roll");

		ExtrasActionPayload.TYPE = ctx.payloads().serverbound("extras_action", ExtrasActionPayload.CODEC, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!CasinoMode.isEnabled(player)) {
				return;
			}
			switch (payload.game()) {
				// review m7: owning the item (or a server-opened screen) is checked here, not only by the client
				case CoinFlipGame.SCREEN -> {
					if (ExtrasGames.mayAct(player, CoinFlipGame.SCREEN, LUCKY_COIN)) {
						CoinFlipGame.action(player, payload.action(), payload.args());
					}
				}
				case DiceGame.SCREEN -> {
					if (ExtrasGames.mayAct(player, DiceGame.SCREEN, DICE)) {
						DiceGame.action(player, payload.action(), payload.args());
					}
				}
				case ScratchGame.SCREEN -> ScratchGame.action(player, payload.action(), payload.args());
				default -> {
				}
			}
		});
		ExtrasScreenPayload.TYPE = ctx.payloads().clientbound("extras_screen", ExtrasScreenPayload.CODEC);
		ExtrasErrorPayload.TYPE = ctx.payloads().clientbound("extras_error", ExtrasErrorPayload.CODEC);

		ServerTickEvents.END_SERVER_TICK.register(DiceGame::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			DiceGame.forget(handler.player.getUUID());
			CoinFlipGame.forget(handler.player.getUUID());
			ExtrasGames.forgetScreen(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DiceGame.clear();
			ExtrasGames.clearScreens();
		});
	}
}
