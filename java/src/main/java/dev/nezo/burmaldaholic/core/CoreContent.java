package dev.nezo.burmaldaholic.core;

import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.chips.CasinoCardItem;
import dev.nezo.burmaldaholic.core.chips.Chips;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.registry.CasinoCreativeTab;
import dev.nezo.burmaldaholic.core.table.TableRegistrar;
import dev.nezo.burmaldaholic.core.table.TableType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/** Core blocks and items: chips, Casino Card, Cashier and Nether Cashier. */
public final class CoreContent {
	public static TableType<CashierBlockEntity> CASHIER;
	public static TableType<CashierBlockEntity> NETHER_CASHIER;
	public static Item CASINO_CARD;

	private CoreContent() {}

	static void register(ModuleContext ctx) {
		Chips.register(ctx);
		CASINO_CARD = ctx.registry().item("casino_card", CasinoCardItem::new, new Item.Properties().stacksTo(1));
		CASHIER = ctx.tables().register("cashier", CashierBlockEntity::new,
			TableRegistrar.defaultProperties().mapColor(MapColor.WOOD).strength(3.0f).sound(SoundType.WOOD));
		NETHER_CASHIER = ctx.tables().register("nether_cashier", CashierBlockEntity::new,
			TableRegistrar.defaultProperties().mapColor(MapColor.NETHER).strength(3.0f).sound(SoundType.NETHER_BRICKS));
		CasinoCreativeTab.setIcon(Chips.item(100));
	}
}
