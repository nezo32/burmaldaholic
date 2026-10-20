package dev.nezo.burmaldaholic.games.extras.server;

import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.wager.HouseEdges;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.ExtrasConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.rng.CasinoRng;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Inventories;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.item.ScratchCardItem;
import dev.nezo.burmaldaholic.games.extras.logic.Scratch;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/**
 * Scratch Cards (GAME_DESIGN.md §11.3, UI.md §9). Unscratched cards are plain fungible items. The outcome is
 * drawn on the FIRST scratch ({@code OddsService.play}, §14 streak re-draw with the card's RTP). B4 (owner playtest
 * 1): the stack stays where it is; the in-progress card (id, kind, mask and the hidden face) is held server-side in
 * {@link ScratchData} as the player's ACTIVE card of its kind, so it survives closing the screen, logging out and
 * restarts, and an unfinished card is always resumed (never re-drawn). Only revealed cells are ever sent to the
 * client (review m1). When the ninth cell is revealed ONE fresh card of that kind leaves the inventory, the prize is
 * paid, {@code PLAY_RESOLVED} fires (bet = card price) and a {@code scratch_card_used} is given with
 * {@code giveOrDrop} (used cards stack). Cards started before v0.2.0 carry {@code id}/{@code mask} on a split-off
 * single; they are finished the old way, in their own slot.
 */
public final class ScratchGame {
	public static final String SCREEN = "scratch";
	public static final String DATA_KEY = "burmaldaholic_scratch";
	/**
	 * T-E2 #2: the stack a started card belongs to carries the card's id (only the id: never the face, review m1), so the
	 * outcome is bound to the CARD, not the player: whoever holds the stack (or any part split off it) resumes the same
	 * card; handing it to another player never re-draws. Removed from the rest of the stack when the card is finished.
	 */
	public static final String LIVE_KEY = "burmaldaholic_scratch_live";

	private ScratchGame() {}

	private static ExtrasConfig.Scratch cfg() {
		return CasinoConfig.extras().scratch;
	}

	public static long price(Scratch.Kind kind) {
		return kind == Scratch.Kind.GOLD ? cfg().gold.price : cfg().basic.price;
	}

	public static List<Scratch.Prize> table(Scratch.Kind kind) {
		return Scratch.table(kind == Scratch.Kind.GOLD ? cfg().gold.prizes : cfg().basic.prizes);
	}

	/** Item keys that belong to the hidden face (kept server-side only, review m1). */
	private static final List<String> FACE_KEYS = List.of("cells", "prize", "creeper", "top", "price");

	/** Client-visible card state stored on a scratched stack ({@code id}, {@code kind}, {@code mask}), or null for a fresh card. */
	public static @Nullable CompoundTag data(ItemStack stack) {
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		if (custom == null) {
			return null;
		}
		CompoundTag tag = custom.copyTag();
		return tag.getCompound(DATA_KEY).orElse(null);
	}

	/**
	 * The full card (item state + hidden face from {@link ScratchData}), or null for a fresh card. A card written
	 * before review m1 (face on the item) is migrated: its face moves to the world data and is stripped from
	 * the stack.
	 */
	static @Nullable CompoundTag card(ServerPlayer player, ItemStack stack) {
		CompoundTag item = data(stack);
		if (item == null) {
			return null;
		}
		String id = item.getStringOr("id", "");
		ScratchData store = ScratchData.get(player.level().getServer());
		if (item.contains("cells")) {
			CompoundTag face = new CompoundTag();
			for (String k : FACE_KEYS) {
				if (item.contains(k)) {
					face.put(k, item.get(k).copy());
				}
			}
			store.put(id, face);
			writeData(stack, item);
		}
		CompoundTag face = store.face(id);
		if (face == null) {
			return null; // unknown card (face lost): treat as a fresh card of its kind
		}
		CompoundTag full = item.copy();
		full.merge(face);
		return full;
	}

	/** Using a card: resume its own game if it was started, else offer to scratch a fresh card of that kind. */
	public static void use(ServerPlayer player, ItemStack stack, Scratch.Kind kind) {
		if (!ExtrasGames.guard(player, cfg().enabled)) {
			return;
		}
		CompoundTag data = card(player, stack);
		if (data == null) {
			int slot = liveSlot(player, kind, liveId(stack));
			data = slot < 0 ? null : liveCard(player, kind, player.getInventory().getItem(slot));
		}
		ExtrasGames.send(player, SCREEN, true, state(player, kind, data));
	}

	/** The started card's id carried by {@code stack} ({@link #LIVE_KEY}), or "". */
	public static String liveId(ItemStack stack) {
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		return custom == null ? "" : custom.copyTag().getStringOr(LIVE_KEY, "");
	}

	private static void tagLive(ItemStack stack, String id) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(LIVE_KEY, id));
	}

	private static void untagLive(ItemStack stack) {
		if (stack.isEmpty() || liveId(stack).isEmpty()) return;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(LIVE_KEY));
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		if (custom != null && custom.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
	}

	/** The card of a stack carrying a started card whose face is known (the player becomes its scratcher), or null. */
	private static @Nullable CompoundTag liveCard(ServerPlayer player, Scratch.Kind kind, ItemStack stack) {
		String id = liveId(stack);
		if (id.isEmpty()) return null;
		ScratchData store = ScratchData.get(player.level().getServer());
		CompoundTag card = store.face(id);
		if (card == null || !card.contains("cells")) return null;
		card.putString("id", id);
		card.putString("kind", kind.id());
		if (!id.equals(store.active(player.getUUID(), kind.id()))) store.setActive(player.getUUID(), kind.id(), id);
		return card;
	}

	/**
	 * Slot of a stack of {@code kind} in the inventory carrying a started card with a known face: {@code prefer}'s card
	 * first, then the selected slot, then any. Stale tags (card already finished) are removed on the way. -1 if none.
	 */
	private static int liveSlot(ServerPlayer player, Scratch.Kind kind, String prefer) {
		Inventory inv = player.getInventory();
		ScratchData store = ScratchData.get(player.level().getServer());
		int best = -1;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!isFresh(s, kind)) continue;
			String id = liveId(s);
			if (id.isEmpty()) continue;
			if (store.face(id) == null) {
				untagLive(s);
				continue;
			}
			if (!prefer.isEmpty() && id.equals(prefer)) return i;
			if (best < 0 || i == inv.getSelectedSlot()) best = i;
		}
		return best;
	}

	/** The player's in-progress card of {@code kind} (B4: held server-side, its stack untouched), or null. */
	static @Nullable CompoundTag active(ServerPlayer player, Scratch.Kind kind) {
		ScratchData store = ScratchData.get(player.level().getServer());
		String id = store.active(player.getUUID(), kind.id());
		if (id == null) {
			return null;
		}
		CompoundTag card = store.face(id);
		if (card == null || !card.contains("cells")) {
			store.clearActive(player.getUUID(), kind.id());
			return null;
		}
		card.putString("id", id);
		card.putString("kind", kind.id());
		return card;
	}

	private static boolean isActive(ServerPlayer player, Scratch.Kind kind, CompoundTag card) {
		String id = ScratchData.get(player.level().getServer()).active(player.getUUID(), kind.id());
		return id != null && id.equals(card.getStringOr("id", ""));
	}

	static CompoundTag state(ServerPlayer player, Scratch.Kind kind, @Nullable CompoundTag card) {
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", kind.id());
		tag.putLong("balance", Economies.get().balance(player));
		tag.putLong("buy_price", price(kind));
		tag.putLong("top_prize", Scratch.topPrize(table(kind)));
		int tier = CoreServices.vip().tier(player.level().getServer(), player.getUUID());
		tag.putBoolean("can_buy", tier >= kind.minTier());
		// the active card is still one of the fresh cards in the stack (B4); "fresh" counts the ones after it
		int fresh = countFresh(player, kind);
		if (card != null && !Scratch.fullyRevealed(card.getIntOr("mask", 0)) && isActive(player, kind, card)) {
			fresh = Math.max(0, fresh - 1);
		}
		tag.putInt("fresh", fresh);
		// the public prize table (amount, probability) for the ticket's prize card (visual/extras.md §6.3)
		net.minecraft.nbt.ListTag prizes = new net.minecraft.nbt.ListTag();
		for (Scratch.Prize p : table(kind)) {
			CompoundTag pt = new CompoundTag();
			pt.putLong("a", p.amount());
			pt.putDouble("p", p.probability());
			prizes.add(pt);
		}
		tag.put("prizes", prizes);
		long[] shown = new long[Scratch.CELLS];
		java.util.Arrays.fill(shown, -1);
		if (card != null) {
			tag.putString("id", card.getStringOr("id", ""));
			int mask = card.getIntOr("mask", 0);
			long[] cells = card.getLongArray("cells").orElse(new long[Scratch.CELLS]);
			for (int i = 0; i < Scratch.CELLS && i < cells.length; i++) {
				if ((mask >> i & 1) == 1) {
					shown[i] = cells[i];
				}
			}
			tag.putInt("mask", mask);
			boolean done = Scratch.fullyRevealed(mask);
			tag.putBoolean("done", done);
			if (done) {
				tag.putLong("prize", card.getLongOr("prize", 0));
				tag.putBoolean("creeper", card.getBooleanOr("creeper", false));
				tag.putBoolean("top", card.getBooleanOr("top", false));
			}
		} else {
			tag.putString("id", "");
			tag.putInt("mask", 0);
			tag.putBoolean("done", false);
		}
		tag.putLongArray("cells", shown);
		return tag;
	}

	private static int countFresh(ServerPlayer player, Scratch.Kind kind) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (isFresh(s, kind)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static boolean isFresh(ItemStack s, Scratch.Kind kind) {
		return s.getItem() instanceof ScratchCardItem card && card.kind() == kind && data(s) == null;
	}

	public static void action(ServerPlayer player, String action, CompoundTag args) {
		if (!ExtrasGames.guard(player, cfg().enabled)) {
			return;
		}
		Scratch.Kind kind = Scratch.Kind.parse(args.getStringOr("kind", ""));
		if (kind == null) {
			return;
		}
		switch (action) {
			case "scratch" -> scratch(player, kind, args.getStringOr("id", ""), args.getIntOr("cell", -1));
			case "all" -> scratch(player, kind, args.getStringOr("id", ""), -1);
			case "new", "refresh" -> ExtrasGames.send(player, SCREEN, false, state(player, kind, null));
			case "buy" -> buy(player, kind);
			default -> {
			}
		}
	}

	private static void buy(ServerPlayer player, Scratch.Kind kind) {
		int tier = CoreServices.vip().tier(player.level().getServer(), player.getUUID());
		if (tier < kind.minTier()) {
			ExtrasGames.sendError(player, ExtrasGames.error("vip_required", VipTiers.name(kind.minTier())));
			return;
		}
		long price = price(kind);
		if (!Economies.get().tryWithdraw(player, price, Transaction.of(ExtrasGames.SCRATCH, "scratch_buy"))) {
			ExtrasGames.sendError(player, ExtrasGames.error("insufficient_funds", Texts.number(Economies.get().balance(player))));
			return;
		}
		Inventories.giveOrDrop(player, new ItemStack(kind == Scratch.Kind.GOLD ? ExtrasModule.SCRATCH_CARD_GOLD : ExtrasModule.SCRATCH_CARD));
		ExtrasGames.send(player, SCREEN, false, state(player, kind, null));
	}

	private static int findSlot(Inventory inv, String id) {
		if (id.isEmpty()) {
			return -1;
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			CompoundTag d = data(inv.getItem(i));
			if (d != null && d.getStringOr("id", "").equals(id)) {
				return i;
			}
		}
		return -1;
	}

	/** A fresh stack of {@code kind} that carries no started card (a new card starts there): the held one first. */
	private static int findFreshSlot(ServerPlayer player, Scratch.Kind kind) {
		Inventory inv = player.getInventory();
		if (startable(inv.getItem(inv.getSelectedSlot()), kind)) {
			return inv.getSelectedSlot();
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (startable(inv.getItem(i), kind)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean startable(ItemStack s, Scratch.Kind kind) {
		return isFresh(s, kind) && liveId(s).isEmpty();
	}

	/**
	 * First scratch of a fresh card: draw the outcome, keep the whole card server-side and tag its stack with the card's
	 * id (B4: the stack stays in its slot, no card leaves it until the finish; T-E2 #2: the outcome follows the stack).
	 * Null without a fresh card.
	 */
	private static @Nullable CompoundTag start(ServerPlayer player, Scratch.Kind kind) {
		int slot = findFreshSlot(player, kind);
		if (slot < 0) {
			return null;
		}
		long price = price(kind);
		List<Scratch.Prize> table = table(kind);
		double creeperChance = cfg().creeperChance;
		OddsContext ctx = ExtrasGames.odds(player, ExtrasGames.SCRATCH, price);
		OddsService odds = OddsService.get();
		CasinoRng rng = odds.rng(ctx);
		Scratch.Outcome outcome = odds.play(ctx, Scratch.rtp(table, price), () -> Scratch.draw(rng, table, creeperChance), o -> o.prize() < price);
		long[] face = Scratch.buildFace(rng, table, outcome);
		CompoundTag card = new CompoundTag();
		String id = UUID.randomUUID().toString();
		card.putString("id", id);
		card.putString("kind", kind.id());
		card.putLong("price", price);
		card.putLongArray("cells", face);
		card.putInt("mask", 0);
		card.putLong("prize", outcome.prize());
		card.putBoolean("creeper", outcome.creeper());
		card.putBoolean("top", outcome.prize() > 0 && outcome.prize() == Scratch.topPrize(table));
		ScratchData store = ScratchData.get(player.level().getServer());
		store.put(id, card);
		store.setActive(player.getUUID(), kind.id(), id);
		tagLive(player.getInventory().getItem(slot), id);
		return card;
	}

	/** Writes the client-visible part of a card onto the stack (never the face, review m1). */
	private static void writeData(ItemStack stack, CompoundTag card) {
		CompoundTag visible = card.copy();
		FACE_KEYS.forEach(visible::remove);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(DATA_KEY, visible));
	}

	private static void scratch(ServerPlayer player, Scratch.Kind kind, String id, int cell) {
		ExtrasGames.playSound(player, dev.nezo.burmaldaholic.core.CoreSounds.SCRATCH, 1.0f);
		Inventory inv = player.getInventory();
		// a card started before v0.2.0 (id + mask on a split-off single)
		int legacySlot = id.isEmpty() ? -1 : findSlot(inv, id);
		CompoundTag card = legacySlot < 0 ? null : card(player, inv.getItem(legacySlot));
		int liveSlot = -1;
		if (card == null) {
			// a stack carrying a started card is resumed whatever the screen sent as its id (never re-drawn, whoever holds
			// it; T-E2 #2); a new card starts only on a stack without one
			liveSlot = liveSlot(player, kind, id);
			card = liveSlot < 0 ? null : liveCard(player, kind, inv.getItem(liveSlot));
			if (card == null) {
				card = start(player, kind);
				liveSlot = card == null ? -1 : liveSlot(player, kind, card.getStringOr("id", ""));
			}
		}
		if (card == null || legacySlot < 0 && liveSlot < 0) {
			// no card here (the started card's stack is not in this inventory): nothing is revealed
			ExtrasGames.sendError(player, ExtrasGames.error("invalid_bet_position"));
			ExtrasGames.send(player, SCREEN, false, state(player, kind, card == null ? active(player, kind) : card));
			return;
		}
		int mask = card.getIntOr("mask", 0);
		if (cell >= 0 && cell < Scratch.CELLS) {
			mask |= 1 << cell;
		} else if (cell < 0) {
			mask = (1 << Scratch.CELLS) - 1;
		}
		card.putInt("mask", mask);
		if (!Scratch.fullyRevealed(mask)) {
			if (legacySlot >= 0) {
				writeData(inv.getItem(legacySlot), card);
			} else {
				ScratchData.get(player.level().getServer()).put(card.getStringOr("id", ""), card);
			}
			ExtrasGames.send(player, SCREEN, false, state(player, kind, card));
			return;
		}
		finish(player, legacySlot >= 0 ? legacySlot : liveSlot, legacySlot >= 0, kind, card);
	}

	/**
	 * The ninth cell: one card leaves its stack in {@code slot} (the legacy single, or the stack carrying the card, whose
	 * rest becomes plain fresh cards again), the card is forgotten, then the prize is paid and a used card is given
	 * ({@code giveOrDrop}, so used cards stack; B4).
	 */
	private static void finish(ServerPlayer player, int slot, boolean legacy, Scratch.Kind kind, CompoundTag card) {
		long price = card.getLongOr("price", price(kind));
		long prize = card.getLongOr("prize", 0);
		boolean creeper = card.getBooleanOr("creeper", false);
		boolean top = card.getBooleanOr("top", false);
		Inventory inv = player.getInventory();
		if (slot < 0) {
			return; // checked by scratch(): never pay without taking the card
		}
		ItemStack stack = inv.getItem(slot);
		stack.shrink(1);
		if (!legacy) {
			untagLive(stack);
		}
		inv.setChanged();
		ScratchData store = ScratchData.get(player.level().getServer());
		store.remove(card.getStringOr("id", ""));
		if (!legacy) {
			store.clearActive(player.getUUID(), kind.id());
		}
		Inventories.giveOrDrop(player, new ItemStack(ExtrasModule.SCRATCH_CARD_USED));
		if (prize > 0) {
			Economies.get().deposit(player, prize, Transaction.payout(ExtrasGames.SCRATCH));
			Component line = top
				? Component.translatable("gui.burmaldaholic.extras.scratch.top_prize", Texts.chips(prize)).withStyle(ChatFormatting.GOLD)
				: Component.translatable("gui.burmaldaholic.extras.scratch.win", Texts.chips(prize)).withStyle(ChatFormatting.GREEN);
			player.sendSystemMessage(line);
		} else if (creeper) {
			player.sendSystemMessage(Component.translatable("gui.burmaldaholic.extras.scratch.creeper").withStyle(ChatFormatting.DARK_GREEN));
			ExtrasGames.requestMobWave(player, "scratch");
		}
		// A prepaid round: the card price was the stake (bought earlier), the prize the return. Edge per card (§17).
		PlayResults.fire(player, CasinoEvents.PlayResult.of(ExtrasGames.SCRATCH, price, prize)
			.withEdge(kind == Scratch.Kind.GOLD ? HouseEdges.SCRATCH_GOLD : HouseEdges.SCRATCH_BASIC).withTags(kind == Scratch.Kind.GOLD ? "gold" : "basic"));
		if (top && prize > 0) {
			CasinoAdvancements.grant(player, "scratch_top");
		}
		ExtrasGames.send(player, SCREEN, false, state(player, kind, card));
		// the tier from the card's own price (the screen holds the overlay until its end emphasis)
		ExtrasGames.celebrate(player, ExtrasGames.SCRATCH, price, prize, top && prize > 0, true);
	}
}
