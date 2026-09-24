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

	private NpcContent() {}

	public static void register(ModuleContext ctx) {
		CROUPIER = entity(ctx, "croupier", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
		PIGLIN_DEALER = entity(ctx, "piglin_dealer", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.79F).clientTrackingRange(10).fireImmune());
		SHULKER_CROUPIER = entity(ctx, "shulker_croupier", EntityType.Builder.of(CasinoNpcEntity::new, MobCategory.MISC)
			.sized(1.0F, 1.0F).eyeHeight(0.5F).clientTrackingRange(10).fireImmune());
		for (EntityType<CasinoNpcEntity> type : List.of(CROUPIER, PIGLIN_DEALER, SHULKER_CROUPIER)) {
			FabricDefaultAttributeRegistry.register(type, CasinoNpcEntity.createAttributes());
		}
		egg(ctx, "croupier_spawn_egg", CROUPIER);
		egg(ctx, "piglin_dealer_spawn_egg", PIGLIN_DEALER);
		egg(ctx, "shulker_croupier_spawn_egg", SHULKER_CROUPIER);
	}

	private static EntityType<CasinoNpcEntity> entity(ModuleContext ctx, String name, EntityType.Builder<CasinoNpcEntity> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ctx.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static void egg(ModuleContext ctx, String name, EntityType<?> type) {
		ctx.registry().item(name, SpawnEggItem::new, new Item.Properties().spawnEgg(type));
	}
}
