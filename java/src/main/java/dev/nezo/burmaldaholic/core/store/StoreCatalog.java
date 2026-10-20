package dev.nezo.burmaldaholic.core.store;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.cashier.CashierShop;
import dev.nezo.burmaldaholic.core.cashier.ExchangeRows;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.EconomyConfig;
import dev.nezo.burmaldaholic.core.config.sections.StoreConfig;
import dev.nezo.burmaldaholic.core.economy.EconomyGuards;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * The chip store catalog (EC-SPEC §3): the {@code store.offers} config checked by {@link EconomyGuards} R3–R6 (a
 * violating offer is dropped and logged once), plus the {@link CashierShop} offers (scratch cards) as category Games.
 */
public final class StoreCatalog {
	public static final String GAMES = "games";
	public static final List<String> CATEGORIES = List.of("food", "potions", "books", "tools", "decor", "rare", GAMES);

	private StoreCatalog() {}

	/**
	 * One offer: {@code stack} = one purchase (count included), {@code shop} = the CashierShop id for Games ("" otherwise).
	 */
	public record Entry(String id, String category, ItemStack stack, long price, int minTier, String dims, int limitPerDay, int limitEveryDays,
			String shop) {
		public boolean availableIn(String dim) {
			return dims.isEmpty() || dims.contains(dim);
		}
	}

	/** The config offers as guard data (pure). */
	public static List<EconomyGuards.Offer> guardOffers(StoreConfig c) {
		List<EconomyGuards.Offer> out = new ArrayList<>();
		c.offers.forEach((id, o) -> out.add(new EconomyGuards.Offer(id, o.item, o.count, o.price)));
		return out;
	}

	/** R3–R6 over a store and economy config (pure). */
	public static List<EconomyGuards.Violation> violations(StoreConfig store, EconomyConfig economy, Map<String, Integer> appraisal) {
		EconomyConfig.ExchangeRow em = economy.exchange.get("emerald");
		int v = EconomyGuards.villagerValue(em == null ? 0 : em.toChips, economy.trade.perTradeBase, economy.trade.perEmerald);
		return EconomyGuards.offers(guardOffers(store), appraisal, economy.guardRows(), v);
	}

	private static Set<String> lastLogged = Set.of();

	/** Every offer (all dimensions), guards applied. */
	public static List<Entry> entries(HolderLookup.Provider registries) {
		StoreConfig c = CasinoConfig.store();
		List<Entry> out = new ArrayList<>();
		if (!c.enabled) return out;
		Set<String> bad = new HashSet<>();
		List<EconomyGuards.Violation> vs = violations(c, CasinoConfig.economy(), CasinoConfig.wager().appraisal);
		for (EconomyGuards.Violation v : vs) bad.add(v.id());
		Set<String> logged = new HashSet<>();
		for (EconomyGuards.Violation v : vs) logged.add(v.rule() + ":" + v.id() + ":" + v.detail());
		if (!logged.equals(lastLogged)) {
			lastLogged = Set.copyOf(logged);
			for (String l : logged) Burmaldaholic.LOGGER.warn("[store] offer disabled by economy guard {}", l);
		}
		c.offers.forEach((id, o) -> {
			if (bad.contains(id) || o.price <= 0) return;
			stack(o, registries).ifPresent(s -> out.add(new Entry(id, o.category, s, o.price, o.minTier, o.dims, o.limitPerDay, o.limitEveryDays, "")));
		});
		for (CashierShop.Offer o : CashierShop.offers()) {
			try {
				ItemStack s = o.item().get();
				if (s.isEmpty()) continue;
				out.add(new Entry("shop." + o.id(), GAMES, s.copy(), Math.max(0, o.price().getAsLong()), o.minTier(), "", 0, 1, o.id()));
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.warn("[store] shop offer {} failed", o.id(), e);
			}
		}
		return out;
	}

	/** Offers of dimension {@code dim}. */
	public static List<Entry> entries(HolderLookup.Provider registries, String dim) {
		return entries(registries).stream().filter(e -> e.availableIn(dim)).toList();
	}

	public static Optional<Entry> entry(HolderLookup.Provider registries, String dim, String id) {
		return entries(registries, dim).stream().filter(e -> e.id().equals(id)).findFirst();
	}

	/** The stack of one purchase, or empty when an id does not resolve. */
	public static Optional<ItemStack> stack(StoreConfig.Offer o, HolderLookup.Provider registries) {
		Optional<Item> item = ExchangeRows.item(o.item);
		if (item.isEmpty()) return Optional.empty();
		ItemStack s;
		if (!o.potion.isEmpty()) {
			Identifier pid = Identifier.tryParse(o.potion);
			Optional<Holder.Reference<Potion>> p = pid == null ? Optional.empty() : BuiltInRegistries.POTION.get(pid);
			if (p.isEmpty()) return Optional.empty();
			s = PotionContents.createItemStack(item.get(), p.get());
		} else if (!o.enchant.isEmpty()) {
			Identifier eid = Identifier.tryParse(o.enchant);
			if (eid == null) return Optional.empty();
			Optional<Holder.Reference<Enchantment>> e = registries.lookupOrThrow(Registries.ENCHANTMENT).get(ResourceKey.create(Registries.ENCHANTMENT, eid));
			if (e.isEmpty()) return Optional.empty();
			s = EnchantmentHelper.createBook(new EnchantmentInstance(e.get(), Math.max(1, o.level)));
		} else {
			s = new ItemStack(item.get());
		}
		if (o.flight > 0) s.set(DataComponents.FIREWORKS, new Fireworks(o.flight, List.of()));
		s.setCount(Math.max(1, Math.min(o.count, s.getMaxStackSize())));
		return Optional.of(s);
	}

	/** {@code dims} helper for callers that only have a session. */
	public static String dim(CounterSessions.Session s) {
		return s.dim();
	}
}
