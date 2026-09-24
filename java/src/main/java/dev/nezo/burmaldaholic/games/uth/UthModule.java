package dev.nezo.burmaldaholic.games.uth;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.ConfigManager;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.table.TableRegistrar;
import dev.nezo.burmaldaholic.core.table.TableType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.material.MapColor;

/**
 * Ultimate Texas Hold'em (GAME_DESIGN §21, UI.md §15): the standard table {@code uth_table}, the High-Roller
 * table {@code uth_table_high_roller} (Gold VIP, min Ante 50, W ≤ 2 × tier max) and the player-banked table
 * {@code uth_table_player_banked} (§21.9). Rules: {@code logic/} (pure, unit-tested).
 */
public final class UthModule implements CasinoModule {
	public static final String ID = "uth";

	public static TableType<UthTableBlockEntity> TABLE;
	public static TableType<UthTableBlockEntity> HIGH_ROLLER_TABLE;
	public static TableType<UthTableBlockEntity> PLAYER_BANKED_TABLE;
	public static EntityType<UthDealer> DEALER;
	public static Item DEALER_SPAWN_EGG;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		TABLE = ctx.tables().register("uth_table", UthTableBlockEntity::new);
		HIGH_ROLLER_TABLE = ctx.tables().register("uth_table_high_roller", UthTableBlockEntity::new,
			TableRegistrar.defaultProperties().mapColor(MapColor.COLOR_PURPLE).strength(3.0f));
		PLAYER_BANKED_TABLE = ctx.tables().register("uth_table_player_banked", UthTableBlockEntity::new,
			TableRegistrar.defaultProperties().mapColor(MapColor.COLOR_BLUE));
		// Hold'em Dealer NPC (§21.6): cosmetic, opens the nearest hold'em table
		ResourceKey<EntityType<?>> dealerKey = ResourceKey.create(Registries.ENTITY_TYPE, ctx.id("uth_dealer"));
		DEALER = Registry.register(BuiltInRegistries.ENTITY_TYPE, dealerKey,
			EntityType.Builder.of(UthDealer::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10).build(dealerKey));
		FabricDefaultAttributeRegistry.register(DEALER, UthDealer.createAttributes());
		DEALER_SPAWN_EGG = ctx.registry().item("uth_dealer_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(DEALER));
		// uth.validateEdge: checked after every config load / change (exact integer math, TripsMath)
		ConfigManager.get().addListener(() -> {
			if (CasinoConfig.uth().validateEdge) {
				UthMath.validate(UthMath.paytables());
			}
		});
	}
}
