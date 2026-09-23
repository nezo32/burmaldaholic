package dev.nezo.burmaldaholic.loan;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.loan.entity.AccountantEntity;
import dev.nezo.burmaldaholic.loan.entity.CollectorEntity;
import dev.nezo.burmaldaholic.loan.entity.EnforcerEntity;
import dev.nezo.burmaldaholic.loan.entity.LoanSharkEntity;
import dev.nezo.burmaldaholic.loan.entity.RepoManEntity;
import dev.nezo.burmaldaholic.loan.entity.SquadMob;
import java.util.List;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;

/**
 * Loan entities and items.
 *
 * <p>Entity ids are the design ids from GAME_DESIGN.md §5 ({@code loan_shark}, {@code piglin_moneylender},
 * {@code debt_collector}, {@code repo_man}, {@code accountant}, {@code enforcer}) so worldgen, commands and
 * the Bedrock edition agree, and their {@code entity.burmaldaholic.*} names already exist in STRINGS.md.
 * Four of them are not (yet) listed for loan in {@code config/namespaces.properties}; see
 * {@link #designId}. Items only have owned ids (their asset files must be owned) and point their
 * names at the STRINGS.md keys with {@code overrideDescription}.
 */
public final class LoanContent {
	public static EntityType<LoanSharkEntity> LOAN_SHARK;
	public static EntityType<LoanSharkEntity> PIGLIN_MONEYLENDER;
	public static EntityType<CollectorEntity> DEBT_COLLECTOR;
	public static EntityType<RepoManEntity> REPO_MAN;
	public static EntityType<AccountantEntity> ACCOUNTANT;
	public static EntityType<EnforcerEntity> ENFORCER;

	public static Item OVERDUE_NOTICE;
	public static Item LEDGER_PAGE;

	/** Death messages {@code death.attack.burmaldaholic.debt_collection[.player]} for squad melee kills. */
	public static final ResourceKey<DamageType> DEBT_COLLECTION = ResourceKey.create(Registries.DAMAGE_TYPE, Burmaldaholic.id("debt_collection"));

	private LoanContent() {}

	/**
	 * A GAME_DESIGN.md §5 entity id assigned to the loan module. Owned ids go through the namespace
	 * check; the rest ({@code repo_man, accountant, enforcer, piglin_moneylender}) are design ids that
	 * core still has to add to loan's namespaces (reported) — until then they bypass {@code ctx.id}.
	 */
	static Identifier designId(ModuleContext ctx, String path) {
		return switch (path) {
			case "repo_man", "accountant", "enforcer", "piglin_moneylender" -> Burmaldaholic.id(path);
			default -> ctx.id(path);
		};
	}

	static void register(ModuleContext ctx) {
		LOAN_SHARK = entity(ctx, "loan_shark", EntityType.Builder.of(LoanSharkEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
		PIGLIN_MONEYLENDER = entity(ctx, "piglin_moneylender", EntityType.Builder.of(LoanSharkEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.95F).eyeHeight(1.79F).clientTrackingRange(10).fireImmune());
		DEBT_COLLECTOR = entity(ctx, "debt_collector", squad(CollectorEntity::new, 0.6F, 1.95F));
		REPO_MAN = entity(ctx, "repo_man", squad(RepoManEntity::new, 0.6F, 1.95F));
		ACCOUNTANT = entity(ctx, "accountant", squad(AccountantEntity::new, 0.6F, 1.95F));
		ENFORCER = entity(ctx, "enforcer", squad(EnforcerEntity::new, 0.6F, 1.95F));

		FabricDefaultAttributeRegistry.register(LOAN_SHARK, LoanSharkEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(PIGLIN_MONEYLENDER, LoanSharkEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(DEBT_COLLECTOR, SquadMob.createAttributes(SquadMob.statsOf(CollectorEntity.UNIT)));
		FabricDefaultAttributeRegistry.register(REPO_MAN, SquadMob.createAttributes(SquadMob.statsOf(RepoManEntity.UNIT)));
		FabricDefaultAttributeRegistry.register(ACCOUNTANT, SquadMob.createAttributes(SquadMob.statsOf(AccountantEntity.UNIT)));
		FabricDefaultAttributeRegistry.register(ENFORCER, EnforcerEntity.createEnforcerAttributes());

		OVERDUE_NOTICE = ctx.registry().item("loan_overdue_notice", Item::new, new Item.Properties()
			.overrideDescription("item.burmaldaholic.overdue_notice")
			.component(DataComponents.LORE, lore("tooltip.burmaldaholic.overdue_notice")));
		LEDGER_PAGE = ctx.registry().item("loan_ledger_page", Item::new, new Item.Properties()
			.overrideDescription("item.burmaldaholic.ledger_page")
			.rarity(Rarity.UNCOMMON)
			.component(DataComponents.LORE, lore("tooltip.burmaldaholic.ledger_page")));

		egg(ctx, "loan_shark_spawn_egg", LOAN_SHARK, null);
		egg(ctx, "loan_shark_piglin_spawn_egg", PIGLIN_MONEYLENDER, "item.burmaldaholic.piglin_moneylender_spawn_egg");
		egg(ctx, "debt_collector_spawn_egg", DEBT_COLLECTOR, null);
		egg(ctx, "debt_collector_repo_man_spawn_egg", REPO_MAN, "item.burmaldaholic.repo_man_spawn_egg");
		egg(ctx, "debt_collector_accountant_spawn_egg", ACCOUNTANT, "item.burmaldaholic.accountant_spawn_egg");
		egg(ctx, "debt_collector_enforcer_spawn_egg", ENFORCER, "item.burmaldaholic.enforcer_spawn_egg");
	}

	private static ItemLore lore(String key) {
		return new ItemLore(List.of(Component.translatable(key).setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false))));
	}

	private static <T extends SquadMob> EntityType.Builder<T> squad(EntityType.EntityFactory<T> factory, float w, float h) {
		return EntityType.Builder.of(factory, MobCategory.MONSTER).sized(w, h).passengerAttachments(2.0F).ridingOffset(-0.6F)
			.clientTrackingRange(8).notInPeaceful();
	}

	private static <T extends Entity> EntityType<T> entity(ModuleContext ctx, String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, designId(ctx, name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static void egg(ModuleContext ctx, String name, EntityType<?> type, String descriptionKey) {
		Item.Properties props = new Item.Properties().spawnEgg(type);
		if (descriptionKey != null) {
			props.overrideDescription(descriptionKey);
		}
		ctx.registry().item(name, SpawnEggItem::new, props);
	}
}
