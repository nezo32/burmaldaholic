package dev.nezo.burmaldaholic.multiplayer;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.multiplayer.block.CharterBlock;
import dev.nezo.burmaldaholic.multiplayer.net.CharterActionPayload;
import dev.nezo.burmaldaholic.multiplayer.net.CharterStatePayload;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Multiplayer (GAME_DESIGN.md §18): player-owned casinos — Casino Charter claim, owner bankroll with the
 * §18.2 reservation / insolvency rules, owned-table routing through {@code CoreServices.setTableOwnership},
 * protection, charter screen — plus the §18.1 spectator summary. Seats / leave / walk-away for player-hosted
 * tables are core's table framework.
 */
public final class MultiplayerModule implements CasinoModule {
	public static final String ID = "multiplayer";
	/** Block id from GAME_DESIGN.md §18.2; the name is core-namespaced (see config/core-ids.txt). */
	public static final String CHARTER_NAME = "casino_charter";

	public static CharterBlock CHARTER;
	public static Item CHARTER_ITEM;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// The spec id "casino_charter" is not in this module's namespace, so ctx.registry() refuses it;
		// register it directly under the same (unique) id.
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Burmaldaholic.id(CHARTER_NAME));
		CHARTER = Registry.register(BuiltInRegistries.BLOCK, blockKey, new CharterBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD).strength(5.0F, 3_600_000.0F).sound(SoundType.LODESTONE).setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Burmaldaholic.id(CHARTER_NAME));
		CHARTER_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(CHARTER, new Item.Properties().useBlockDescriptionPrefix().stacksTo(1).setId(itemKey)));
		CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Burmaldaholic.id("main")))
			.register(output -> output.accept(CHARTER_ITEM));

		CharterActionPayload.TYPE = ctx.payloads().serverbound("multiplayer_charter_action", CharterActionPayload.CODEC, Ownership::handleAction);
		CharterStatePayload.TYPE = ctx.payloads().clientbound("multiplayer_charter_state", CharterStatePayload.CODEC);

		CoreServices.setTableOwnership(Ownership::owner);
		PlayerBlockBreakEvents.BEFORE.register(Ownership::beforeBreak);
		PlayerBlockBreakEvents.AFTER.register(Ownership::afterBreak);
		UseBlockCallback.EVENT.register(Ownership::onUseBlock);
		CasinoEvents.PLAY_RESOLVED.register(Ownership::onPlayResolved);
		ServerTickEvents.END_SERVER_TICK.register(Ownership::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Ownership.onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Ownership.onLeave(handler.getPlayer()));
	}
}
