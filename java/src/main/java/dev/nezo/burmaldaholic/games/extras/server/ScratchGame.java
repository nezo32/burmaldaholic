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
 * drawn on the FIRST scratch ({@code OddsService.play}, §14 streak re-draw with the card's RTP) and one card is
 * split off the stack. The card's {@code custom_data} holds only its {@code id}, {@code kind} and the revealed
 * {@code mask}; the hidden face (cells, prize, creeper) lives server-side in {@link ScratchData} keyed by the id
 * (review m1: item components are synced to clients). Progress survives closing the screen, logging out and
 * restarts. Only revealed cells are ever sent to the client. When the
 * ninth cell is revealed the prize is paid, {@code PLAY_RESOLVED} fires (bet = card price) and the card
 * becomes {@code scratch_card_used}.
 */
public final class ScratchGame {
	public static final String SCREEN = "scratch";
	public static final String DATA_KEY = "burmaldaholic_scratch";

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
		ExtrasGames.send(player, SCREEN, true, state(player, kind, data));
	}

	static CompoundTag state(ServerPlayer player, Scratch.Kind kind, @Nullable CompoundTag card) {
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", kind.id());
		tag.putLong("balance", Economies.get().balance(player));
		tag.putLong("buy_price", price(kind));
		tag.putLong("top_prize", Scratch.topPrize(table(kind)));
		int tier = CoreServices.vip().tier(player.level().getServer(), player.getUUID());
		tag.putBoolean("can_buy", tier >= kind.minTier());
		tag.putInt("fresh", countFresh(player, kind));
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
			case "new" -> ExtrasGames.send(player, SCREEN, false, state(player, kind, null));
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

	private static int findFreshSlot(ServerPlayer player, Scratch.Kind kind) {
		Inventory inv = player.getInventory();
		if (isFresh(inv.getItem(inv.getSelectedSlot()), kind)) {
			return inv.getSelectedSlot();
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isFresh(inv.getItem(i), kind)) {
				return i;
			}
		}
		return -1;
	}

	/** First scratch of a fresh card: draw the outcome, split one card off the stack, store the face on it. */
	private static int start(ServerPlayer player, Scratch.Kind kind) {
		Inventory inv = player.getInventory();
		int slot = findFreshSlot(player, kind);
		if (slot < 0) {
			return -1;
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
		ScratchData.get(player.level().getServer()).put(id, faceOf(card));
		ItemStack source = inv.getItem(slot);
		ItemStack single = source.split(1);
		writeData(single, card);
		ItemStack rest = source.isEmpty() ? ItemStack.EMPTY : source.copy();
		inv.setItem(slot, single);
		if (!rest.isEmpty()) {
			Inventories.giveOrDrop(player, rest);
		}
		return slot;
	}

	private static CompoundTag faceOf(CompoundTag card) {
		CompoundTag face = new CompoundTag();
		for (String k : FACE_KEYS) {
			if (card.contains(k)) {
				face.put(k, card.get(k).copy());
			}
		}
		return face;
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
		int slot = id.isEmpty() ? start(player, kind) : findSlot(inv, id);
		if (slot < 0) {
			ExtrasGames.sendError(player, ExtrasGames.error("invalid_bet_position"));
			ExtrasGames.send(player, SCREEN, false, state(player, kind, null));
			return;
		}
		ItemStack stack = inv.getItem(slot);
		CompoundTag card = card(player, stack);
		if (card == null) {
			ExtrasGames.sendError(player, ExtrasGames.error("invalid_bet_position"));
			ExtrasGames.send(player, SCREEN, false, state(player, kind, null));
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
			writeData(stack, card);
			ExtrasGames.send(player, SCREEN, false, state(player, kind, card));
			return;
		}
		finish(player, slot, kind, card);
	}

	private static void finish(ServerPlayer player, int slot, Scratch.Kind kind, CompoundTag card) {
		long price = card.getLongOr("price", price(kind));
		long prize = card.getLongOr("prize", 0);
		boolean creeper = card.getBooleanOr("creeper", false);
		boolean top = card.getBooleanOr("top", false);
		player.getInventory().setItem(slot, new ItemStack(ExtrasModule.SCRATCH_CARD_USED));
		ScratchData.get(player.level().getServer()).remove(card.getStringOr("id", ""));
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
