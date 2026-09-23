package dev.nezo.burmaldaholic.vip;

import com.mojang.brigadier.arguments.LongArgumentType;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.vip.logic.VipRules;
import dev.nezo.burmaldaholic.vip.net.VipActionPayload;
import dev.nezo.burmaldaholic.vip.net.VipErrorPayload;
import dev.nezo.burmaldaholic.vip.net.VipSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * VIP tiers and perks (GAME_DESIGN.md §12) plus the daily contracts of §3.4.4 (core ships none,
 * same decision as Bedrock). See {@link VipService} and {@link Contracts}; client half in
 * {@code vip.client} (Casino Menu on key B, HUD segment).
 */
public final class VipModule implements CasinoModule {
	public static final String ID = "vip";
	public static Item DIAMOND_CARD;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		DIAMOND_CARD = ctx.registry().item("vip_diamond_casino_card", DiamondCasinoCardItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

		CoreServices.setVip((server, player) -> VipService.tier(server, player));
		CasinoEvents.PLAY_RESOLVED.register(VipService::onPlayResolved);
		Contracts.register();

		VipSyncPayload.TYPE = ctx.payloads().clientbound("vip_sync", VipSyncPayload.CODEC);
		VipErrorPayload.TYPE = ctx.payloads().clientbound("vip_error", VipErrorPayload.CODEC);
		VipActionPayload.TYPE = ctx.payloads().serverbound("vip_action", VipActionPayload.CODEC, (payload, context) -> {
			ServerPlayer player = context.player();
			switch (payload.action()) {
				case VipActionPayload.SYNC -> VipService.send(player, false, true);
				case VipActionPayload.REROLL -> {
					Component err = Contracts.reroll(player, payload.index());
					if (err != null) {
						VipService.sendError(player, err);
					}
					VipService.send(player, false, true);
				}
				default -> {
				}
			}
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> VipService.markSync(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			VipService.forget(handler.getPlayer().getUUID());
			Contracts.forget(handler.getPlayer().getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(VipService::tick);

		CasinoCommands.extend(root -> root.then(Commands.literal("vip")
			.then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
				ServerPlayer p = EntityArgument.getPlayer(c, "player");
				int tier = VipService.tier(c.getSource().getServer(), p.getUUID());
				long w = VipService.wagered(c.getSource().getServer(), p.getUUID());
				c.getSource().sendSuccess(() -> Component.translatable("gui.burmaldaholic.vip.current", VipTiers.name(tier)), false);
				c.getSource().sendSuccess(() -> Component.translatable("gui.burmaldaholic.menu.wallet.lifetime", Texts.chips(w)), false);
				return tier;
			})))
			.then(Commands.literal("wagered").then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("amount", LongArgumentType.longArg(0)).executes(c -> {
					ServerPlayer p = EntityArgument.getPlayer(c, "player");
					VipService.setWagered(p, LongArgumentType.getLong(c, "amount"));
					int tier = VipService.tier(c.getSource().getServer(), p.getUUID());
					c.getSource().sendSuccess(() -> Component.translatable("gui.burmaldaholic.menu.admin.done",
						Component.translatable("gui.burmaldaholic.vip.current", VipTiers.name(tier))), true);
					return VipRules.clamp(tier);
				}))))));
	}
}
