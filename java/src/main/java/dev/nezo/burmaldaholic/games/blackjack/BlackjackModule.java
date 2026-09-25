package dev.nezo.burmaldaholic.games.blackjack;

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
 * Blackjack (GAME_DESIGN §6, UI.md §4): the standard table {@code blackjack_table}, the High-Roller
 * table {@code blackjack_table_high_roller} (min {@code highRollerMinBet}, max × tier, Gold VIP) and the
 * dealer NPC {@code blackjack_dealer} (+ spawn egg). Rules: {@code logic/} (pure, unit-tested).
 */
public final class BlackjackModule implements CasinoModule {
	public static final String ID = "blackjack";

	public static TableType<BlackjackTableBlockEntity> TABLE;
	public static TableType<BlackjackTableBlockEntity> HIGH_ROLLER_TABLE;
	public static EntityType<BlackjackDealer> DEALER;
	public static Item DEALER_SPAWN_EGG;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		TABLE = ctx.tables().register("blackjack_table", BlackjackTableBlockEntity::new);
		HIGH_ROLLER_TABLE = ctx.tables().register("blackjack_table_high_roller", BlackjackTableBlockEntity::new,
			TableRegistrar.defaultProperties().mapColor(MapColor.COLOR_PURPLE).strength(3.0f));

		ResourceKey<EntityType<?>> dealerKey = ResourceKey.create(Registries.ENTITY_TYPE, ctx.id("blackjack_dealer"));
		DEALER = Registry.register(BuiltInRegistries.ENTITY_TYPE, dealerKey,
			EntityType.Builder.of(BlackjackDealer::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10).build(dealerKey));
		FabricDefaultAttributeRegistry.register(DEALER, BlackjackDealer.createAttributes());
		DEALER_SPAWN_EGG = ctx.registry().item("blackjack_dealer_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(DEALER));
	}
}
