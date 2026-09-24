package dev.nezo.burmaldaholic.games.baccarat;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.BaccaratConfig;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.table.TableRegistrar;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.baccarat.logic.BaccaratOdds;
import dev.nezo.burmaldaholic.games.baccarat.logic.BetKind;
import dev.nezo.burmaldaholic.games.baccarat.logic.Paytable;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.material.MapColor;

/**
 * Baccarat — Punto Banco (GAME_DESIGN §20, UI.md §14): the standard table {@code baccarat_table}, the
 * High-Roller table {@code baccarat_table_high_roller}, the player-banked Chemin de fer table
 * {@code baccarat_table_player_banked} (§20.9) and the cosmetic dealer NPC {@code baccarat_dealer}
 * (+ spawn egg). Rules, payouts, exact odds and the chemin de fer bank are pure Java in {@code logic/}.
 */
public final class BaccaratModule implements CasinoModule {
	public static final String ID = "baccarat";
	public static final String HIGH_ROLLER_NAME = "baccarat_table_high_roller";
	public static final String CHEMMY_NAME = "baccarat_table_player_banked";

	public static TableType<BaccaratTableBlockEntity> TABLE;
	public static TableType<BaccaratTableBlockEntity> HIGH_ROLLER_TABLE;
	public static TableType<BaccaratTableBlockEntity> CHEMMY_TABLE;
	public static EntityType<BaccaratDealer> DEALER;
	public static Item DEALER_SPAWN_EGG;

	@Override
	public String id() {
		return ID;
	}

	/** The {@code baccarat} config section (CONFIG.md); call again after reloads. */
	public static BaccaratConfig config() {
		return CasinoConfig.baccarat();
	}

	/** Paytable of the current config. */
	public static Paytable paytable() {
		BaccaratConfig c = config();
		return Paytable.of(c.bankerCommission, c.tiePays, c.pairPays);
	}

	@Override
	public void register(ModuleContext ctx) {
		TABLE = ctx.tables().register("baccarat_table", BaccaratTableBlockEntity::new);
		HIGH_ROLLER_TABLE = ctx.tables().register(HIGH_ROLLER_NAME, BaccaratTableBlockEntity::new,
			TableRegistrar.defaultProperties().mapColor(MapColor.COLOR_PURPLE).strength(3.0f));
		CHEMMY_TABLE = ctx.tables().register(CHEMMY_NAME, BaccaratTableBlockEntity::new,
			TableRegistrar.defaultProperties().mapColor(MapColor.COLOR_RED));

		ResourceKey<EntityType<?>> dealerKey = ResourceKey.create(Registries.ENTITY_TYPE, ctx.id("baccarat_dealer"));
		DEALER = Registry.register(BuiltInRegistries.ENTITY_TYPE, dealerKey,
			EntityType.Builder.of(BaccaratDealer::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10).build(dealerKey));
		FabricDefaultAttributeRegistry.register(DEALER, BaccaratDealer.createAttributes());
		DEALER_SPAWN_EGG = ctx.registry().item("baccarat_dealer_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(DEALER));

		BaccaratPresets.install(); // after worldgen (module order): baccarat tables in generated casinos
		ServerLifecycleEvents.SERVER_STARTED.register(server -> checkConfig());
	}

	/** §20.1: a commission without an exact Banker step ≤ 100 floors the payout — log the resulting edge. */
	private static void checkConfig() {
		Paytable pay = paytable();
		if (!pay.exactStep()) {
			double edge = BaccaratOdds.of(config().decks).edge(BetKind.BANKER, pay);
			Burmaldaholic.LOGGER.warn("baccarat.bankerCommission {} has no exact Banker step <= 100; Banker bets use step 100 and are floored "
				+ "(Banker edge ~{}%)", config().bankerCommission, String.format(java.util.Locale.ROOT, "%.3f", edge * 100));
		}
	}
}
