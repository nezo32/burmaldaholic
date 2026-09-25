package dev.nezo.burmaldaholic.core.registry;

import dev.nezo.burmaldaholic.core.module.ModuleContext;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Thin, namespace-checked wrapper around vanilla registries. Every item/block-item registered
 * here is automatically added to the Burmaldaholic creative tab.
 */
public final class ModRegistrar {
	private final ModuleContext ctx;

	public ModRegistrar(ModuleContext ctx) {
		this.ctx = ctx;
	}

	public <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ctx.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
	}

	/** Registers a block AND its block item (added to the creative tab). */
	public <B extends Block> B blockWithItem(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties props) {
		B block = block(name, factory, props);
		item(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	public <I extends Item> I item(String name, Function<Item.Properties, I> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ctx.id(name));
		I item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		CasinoCreativeTab.add(item);
		return item;
	}

	public Item item(String name) {
		return item(name, Item::new, new Item.Properties());
	}

	public <T extends BlockEntity> BlockEntityType<T> blockEntity(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ctx.id(name), new BlockEntityType<>(factory, Set.of(blocks)));
	}

	/** Menu whose client side receives {@code D} (e.g. a BlockPos) when opened. */
	public <M extends AbstractContainerMenu, D> ExtendedMenuType<M, D> menu(String name, ExtendedMenuType.ExtendedFactory<M, D> factory,
			StreamCodec<? super RegistryFriendlyByteBuf, D> openingDataCodec) {
		return Registry.register(BuiltInRegistries.MENU, ctx.id(name), new ExtendedMenuType<>(factory, openingDataCodec));
	}

	/** Sound event {@code burmaldaholic:<name>}; declare it in src/main/sounds/&lt;module&gt;/sounds.json. */
	public SoundEvent sound(String name) {
		Identifier id = ctx.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
