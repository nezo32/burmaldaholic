package dev.nezo.burmaldaholic.worldgen.npc;

import dev.nezo.burmaldaholic.core.module.ModuleContext;
import java.util.List;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** Entity types + spawn eggs of the casino NPCs (ids from STRINGS.md, owned by worldgen). */
public final class NpcContent {
	public static EntityType<CasinoNpcEntity> CROUPIER;
	public static EntityType<CasinoNpcEntity> PIGLIN_DEALER;
	public static EntityType<CasinoNpcEntity> SHULKER_CROUPIER;
	/** DL: End croupier, Parlor pit boss, merchants beside the cashiers (one per species). */
	public static EntityType<CasinoNpcEntity> ENDER_CROUPIER;
	public static EntityType<CasinoNpcEntity> PIT_BOSS;
	public static EntityType<CasinoNpcEntity> MERCHANT;
	public static EntityType<CasinoNpcEntity> PIGLIN_MERCHANT;
	public static EntityType<CasinoNpcEntity> ENDER_MERCHANT;
	/** DL: the house dealers on the tables' dealer cells (human / piglin / ender by dimension). */
	public static EntityType<HouseDealer> HOUSE_DEALER;
	public static EntityType<HouseDealer> PIGLIN_HOUSE_DEALER;
	public static EntityType<HouseDealer> ENDER_HOUSE_DEALER;

	private NpcContent() {}

	public static void register(ModuleContext ctx) {
		CROUPIER = entity(ctx, "croupier", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
		PIGLIN_DEALER = entity(ctx, "piglin_dealer", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.79F).clientTrackingRange(10).fireImmune());
		SHULKER_CROUPIER = entity(ctx, "shulker_croupier", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(1.0F, 1.0F).eyeHeight(0.5F).clientTrackingRange(10).fireImmune());
		ENDER_CROUPIER = entity(ctx, "ender_croupier", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(0.6F, 2.6F).eyeHeight(2.3F).clientTrackingRange(10));
		PIT_BOSS = entity(ctx, "pit_boss", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.79F).clientTrackingRange(10).fireImmune());
		MERCHANT = entity(ctx, "merchant", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
		PIGLIN_MERCHANT = entity(ctx, "piglin_merchant", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.79F).clientTrackingRange(10).fireImmune());
		ENDER_MERCHANT = entity(ctx, "ender_merchant", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(0.6F, 2.6F).eyeHeight(2.3F).clientTrackingRange(10));
		for (EntityType<CasinoNpcEntity> type : List.of(CROUPIER, PIGLIN_DEALER, SHULKER_CROUPIER, ENDER_CROUPIER, PIT_BOSS, MERCHANT,
				PIGLIN_MERCHANT, ENDER_MERCHANT)) {
			FabricDefaultAttributeRegistry.register(type, CasinoNpcEntity.createAttributes());
		}
		HOUSE_DEALER = dealer(ctx, "house_dealer", 1.95F, 1.62F);
		PIGLIN_HOUSE_DEALER = dealer(ctx, "piglin_house_dealer", 1.95F, 1.79F);
		ENDER_HOUSE_DEALER = dealer(ctx, "ender_house_dealer", 2.6F, 2.3F);
		for (EntityType<HouseDealer> type : List.of(HOUSE_DEALER, PIGLIN_HOUSE_DEALER, ENDER_HOUSE_DEALER)) {
			FabricDefaultAttributeRegistry.register(type, HouseDealer.createAttributes());
		}
		egg(ctx, "house_dealer_spawn_egg", HOUSE_DEALER);
		egg(ctx, "croupier_spawn_egg", CROUPIER);
		egg(ctx, "piglin_dealer_spawn_egg", PIGLIN_DEALER);
		egg(ctx, "shulker_croupier_spawn_egg", SHULKER_CROUPIER);
	}

	private static EntityType<HouseDealer> dealer(ModuleContext ctx, String name, float height, float eye) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ctx.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<HouseDealer>of(HouseDealer::new, MobCategory.MISC)
			.sized(0.6F, height).eyeHeight(eye).clientTrackingRange(8).fireImmune().build(key));
	}

	private static EntityType<CasinoNpcEntity> entity(ModuleContext ctx, String name, EntityType.Builder<CasinoNpcEntity> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ctx.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static void egg(ModuleContext ctx, String name, EntityType<?> type) {
		ctx.registry().item(name, SpawnEggItem::new, new Item.Properties().spawnEgg(type));
	}
}
