package dev.nezo.burmaldaholic.comps;

import com.mojang.serialization.Codec;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Inventories;
import dev.nezo.burmaldaholic.core.wager.FreeBetTokens;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * Free-bet tokens (EC-SPEC §4.3): the {@code free_bet_token} item with components {@code token_value},
 * {@code bound_to} and an expiry 7 in-game days out; the {@link FreeBetTokens.Provider} core's games use.
 * Tokens can't be deposited, pawned (no appraisal) or used by anyone but the bound player.
 */
public final class FreeBets implements FreeBetTokens.Provider {
	public static Item TOKEN;
	public static DataComponentType<Long> VALUE;
	public static DataComponentType<UUID> BOUND_TO;
	public static DataComponentType<Long> EXPIRES;

	static final FreeBets INSTANCE = new FreeBets();

	private FreeBets() {}

	static void register(ModuleContext ctx) {
		VALUE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ctx.id("free_bet_token_value"),
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());
		BOUND_TO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ctx.id("free_bet_token_bound_to"),
			DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());
		EXPIRES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ctx.id("free_bet_token_expires"),
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());
		TOKEN = ctx.registry().item("free_bet_token", TokenItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
		FreeBetTokens.setProvider(INSTANCE);
	}

	static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** A new token bound to {@code owner}, worth {@code value}, expiring at {@code expires} (overworld game time). */
	public static ItemStack create(UUID owner, long value, long expires) {
		ItemStack s = new ItemStack(TOKEN);
		s.set(VALUE, value);
		s.set(BOUND_TO, owner);
		s.set(EXPIRES, expires);
		return s;
	}

	/** Why a stack is not a usable token for {@code player} at {@code now} ("" = usable). */
	static String invalid(ItemStack s, UUID player, long now) {
		if (s.isEmpty() || TOKEN == null || !s.is(TOKEN)) {
			return "none";
		}
		Long v = s.get(VALUE);
		if (v == null || v <= 0 || !player.equals(s.get(BOUND_TO))) {
			return "not_yours";
		}
		Long e = s.get(EXPIRES);
		if (e == null || now >= e) {
			return "expired";
		}
		return "";
	}

	@Override
	public FreeBetTokens.@Nullable Held peek(ServerPlayer player) {
		ItemStack s = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!CompsService.enabled() || !invalid(s, player.getUUID(), now(player.level().getServer())).isEmpty()) {
			return null;
		}
		return new FreeBetTokens.Held(s.get(VALUE), s.get(EXPIRES));
	}

	@Override
	public FreeBetTokens.@Nullable Held take(ServerPlayer player) {
		FreeBetTokens.Held h = peek(player);
		if (h != null) {
			player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
			player.inventoryMenu.broadcastChanges();
		}
		return h;
	}

	@Override
	public ItemStack stack(UUID owner, FreeBetTokens.Held token) {
		return create(owner, token.value(), token.expires());
	}

	@Override
	public void giveBack(MinecraftServer server, UUID player, FreeBetTokens.Held token) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null && !online.hasDisconnected()) {
			Inventories.giveOrDrop(online, create(player, token.value(), token.expires()));
			online.sendSystemMessage(Component.translatable("msg.burmaldaholic.comps.token_returned", Texts.chips(token.value())).withStyle(ChatFormatting.GRAY));
			return;
		}
		CompsWorldData.get(server).queueToken(player, token);
	}

	/** Join: delivers tokens returned while the player was offline. */
	static void deliver(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		for (FreeBetTokens.Held h : CompsWorldData.get(server).drainTokens(player.getUUID())) {
			Inventories.giveOrDrop(player, create(player.getUUID(), h.value(), h.expires()));
		}
	}

	/** Every few seconds: expired tokens in the inventory crumble (with a message). */
	static void sweepExpired(ServerPlayer player) {
		long now = now(player.level().getServer());
		var inv = player.getInventory();
		int removed = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && s.is(TOKEN) && "expired".equals(invalid(s, player.getUUID(), now))) {
				removed += s.getCount();
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		if (removed > 0) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.comps.token_expired").withStyle(ChatFormatting.GRAY));
		}
	}

	/** The token item: tooltip with value, owner hint and days left. */
	public static final class TokenItem extends Item {
		public TokenItem(Properties properties) {
			super(properties);
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			Long v = stack.get(VALUE);
			builder.accept(Component.translatable("item.burmaldaholic.free_bet_token.tooltip", Texts.chips(v == null ? 0 : v)).withStyle(ChatFormatting.GRAY));
			// days left: client tooltip callback (CompsClient) — the context has no level here
			builder.accept(Component.translatable("item.burmaldaholic.free_bet_token.bound").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
