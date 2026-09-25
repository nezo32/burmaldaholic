package dev.nezo.burmaldaholic.worldgen.npc;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.cashier.CashierShop;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Inventories;
import dev.nezo.burmaldaholic.worldgen.WorldgenModule;
import dev.nezo.burmaldaholic.worldgen.logic.NpcRole;
import dev.nezo.burmaldaholic.worldgen.logic.NpcShop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What happens when a player right-clicks a casino NPC (same behaviour as Bedrock's {@code worldgen/npcs.ts}):
 * <ul>
 *   <li>Croupier / Shulker Croupier: a greeting and their shop ({@link NpcShop}) as a Casino Menu page
 *       ({@code worldgen_shop}, shown only while the player stands near that NPC): VIP tier and funds are
 *       checked, the price is debited and the item handed out.</li>
 *   <li>Piglin Dealer: a greeting in chat, then the nearest game table around him (4 blocks) opens.</li>
 * </ul>
 * Server thread only; dormant while casino mode is off.
 */
public final class NpcInteractions {
	public static final String PAGE = "worldgen_shop";
	/** How far from the NPC the shop stays usable. */
	static final double RANGE = 8.0;
	/** Search radius of the Piglin Dealer's table (horizontal; ±1 block vertically). */
	static final int TABLE_RADIUS = 4;

	/** The NPC a player talks to (its greeting is fixed per conversation). */
	record Session(UUID npc, NpcRole role, String greeting) {}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private NpcInteractions() {}

	public static void register() {
		CasinoMenu.register(new ShopPage());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> SESSIONS.remove(handler.getPlayer().getUUID())));
	}

	public static void interact(ServerPlayer player, CasinoNpcEntity npc) {
		if (!CasinoMode.isEnabled(player)) {
			player.sendOverlayMessage(Component.translatable("gui.burmaldaholic.error.casino_off"));
			return;
		}
		NpcRole role = npc.role();
		String greeting = NpcShop.greetingKey(role, 1 + npc.getRandom().nextInt(Math.max(1, NpcShop.greetings(role))));
		if (role == NpcRole.PIGLIN_DEALER) {
			say(player, npc, greeting);
			nearestTable((ServerLevel) npc.level(), npc.blockPosition()).ifPresent(table -> openTable(player, table));
			return;
		}
		SESSIONS.put(player.getUUID(), new Session(npc.getUUID(), role, greeting));
		CasinoMenu.open(player, PAGE);
	}

	static void say(ServerPlayer player, Entity npc, String key) {
		player.sendSystemMessage(Component.translatable("chat.type.text", npc.getDisplayName(), Component.translatable(key)));
	}

	/** Nearest game table (not a cashier) around {@code center}. */
	public static Optional<CasinoTableBlockEntity> nearestTable(ServerLevel level, BlockPos center) {
		CasinoTableBlockEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-TABLE_RADIUS, -1, -TABLE_RADIUS), center.offset(TABLE_RADIUS, 1, TABLE_RADIUS))) {
			if (level.getBlockEntity(pos) instanceof CasinoTableBlockEntity table && !(table instanceof CashierBlockEntity)) {
				double d = pos.distSqr(center);
				if (d < bestDist) {
					bestDist = d;
					best = table;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** Opens a table's screen exactly like right-clicking the block. */
	static void openTable(ServerPlayer player, CasinoTableBlockEntity table) {
		player.openMenu(table);
		table.sendStateTo(player);
	}

	/** The NPC the player is talking to if it is still alive and in range. */
	static @Nullable CasinoNpcEntity partner(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		if (s == null) {
			return null;
		}
		Entity e = player.level().getEntity(s.npc());
		if (e instanceof CasinoNpcEntity npc && npc.isAlive() && npc.distanceToSqr(player) <= RANGE * RANGE) {
			return npc;
		}
		return null;
	}

	/** One offer as currently sold (price / tier from the Cashier Shop offer when that exists). */
	record LiveOffer(String id, ItemStack item, long price, int minTier) {}

	static List<LiveOffer> offers(NpcRole role) {
		List<LiveOffer> out = new ArrayList<>();
		for (NpcShop o : NpcShop.of(role)) {
			Identifier itemId = Burmaldaholic.id(o.id());
			if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
				continue; // module not present
			}
			CashierShop.Offer cashier = CashierShop.byId(o.id());
			if (cashier != null) {
				out.add(new LiveOffer(o.id(), cashier.item().get(), Math.max(0, cashier.price().getAsLong()), cashier.minTier()));
			} else if (!o.id().startsWith("scratch_card")) { // scratch cards follow extras.scratch.enabled via their cashier offer
				out.add(new LiveOffer(o.id(), new ItemStack(BuiltInRegistries.ITEM.getValue(itemId)), o.price(), o.minTier()));
			}
		}
		return out;
	}

	/** Buys one item; returns the error or {@code null}. */
	static @Nullable Component buy(ServerPlayer player, NpcRole role, String id) {
		LiveOffer offer = offers(role).stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
		if (offer == null) {
			return Component.translatable("gui.burmaldaholic.error.disabled");
		}
		int tier = CoreServices.vip().tier(player.level().getServer(), player.getUUID());
		long balance = Economies.get().balance(player);
		switch (NpcShop.check(offer.price(), offer.minTier(), balance, tier)) {
			case VIP -> {
				return Component.translatable("gui.burmaldaholic.error.vip_required", VipTiers.name(offer.minTier()));
			}
			case FUNDS -> {
				return Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(balance));
			}
			case OK -> {
			}
		}
		if (!Economies.get().tryWithdraw(player, offer.price(), Transaction.of(WorldgenModule.ID, "shop_" + id))) {
			return Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player)));
		}
		Component name = offer.item().getHoverName();
		if (Inventories.giveOrDrop(player, offer.item().copy())) {
			player.sendSystemMessage(Component.translatable("gui.burmaldaholic.error.inventory_full"));
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.worldgen.bought", name, Texts.chipsAcc(offer.price())));
		return null;
	}

	/** The Croupier's / Shulker Croupier's shop tab of the Casino Menu. */
	static final class ShopPage implements CasinoMenu.Page {
		@Override
		public String id() {
			return PAGE;
		}

		@Override
		public int order() {
			return 5; // first tab while talking to a croupier
		}

		@Override
		public Component label() {
			return Component.translatable("gui.burmaldaholic.croupier.title");
		}

		@Override
		public boolean visible(ServerPlayer player) {
			return partner(player) != null;
		}

		@Override
		public void render(ServerPlayer player, CasinoMenu.PageBuilder out) {
			CasinoNpcEntity npc = partner(player);
			Session s = SESSIONS.get(player.getUUID());
			if (npc == null || s == null) {
				return;
			}
			out.line(npc.getDisplayName(), 0xFFD700);
			out.line(Component.translatable(s.greeting()), 0xCCCCCC);
			out.blank();
			out.line(Component.translatable("gui.burmaldaholic.common.balance", Texts.chips(Economies.get().balance(player))));
			int tier = CoreServices.vip().tier(player.level().getServer(), player.getUUID());
			List<LiveOffer> offers = offers(s.role());
			if (offers.isEmpty()) {
				out.line(Component.translatable("gui.burmaldaholic.error.disabled"), 0xFF5555);
			}
			for (LiveOffer o : offers) {
				if (tier < o.minTier()) {
					out.line(Component.translatable("gui.burmaldaholic.worldgen.shop.offer", o.item().getHoverName(),
						Component.translatable("gui.burmaldaholic.common.requires_vip", VipTiers.name(o.minTier()))), 0xFF5555);
				}
			}
			for (LiveOffer o : offers) {
				out.button("buy:" + o.id(), Component.translatable("gui.burmaldaholic.worldgen.shop.offer", o.item().getHoverName(),
					Texts.chips(o.price())), tier >= o.minTier());
			}
		}

		@Override
		public @Nullable Component action(ServerPlayer player, String action, long amount) {
			Session s = SESSIONS.get(player.getUUID());
			if (partner(player) == null || s == null || !action.startsWith("buy:")) {
				return Component.translatable("gui.burmaldaholic.error.too_far");
			}
			return buy(player, s.role(), action.substring("buy:".length()));
		}
	}
}
