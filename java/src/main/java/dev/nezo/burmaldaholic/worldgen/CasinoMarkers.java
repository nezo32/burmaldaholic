package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoLoot;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.Facing;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Layout;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import dev.nezo.burmaldaholic.worldgen.logic.Markers;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Handles the data markers of a casino template after it was placed in one chunk
 * (see {@link Markers}). Runs on world-generation threads (and on the server thread for
 * {@code /casino worldgen build}); saved data is only touched through {@code server.execute}.
 */
public final class CasinoMarkers {
	private CasinoMarkers() {}

	public static void afterPlace(SinglePoolElement element, StructureTemplateManager templates, ServerLevelAccessor level,
			BlockPos position, Rotation rotation, BoundingBox chunkBB, RandomSource random) {
		Optional<Layout> layout = CasinoElements.layoutOf(element);
		if (layout.isEmpty()) {
			return;
		}
		List<StructureTemplate.StructureBlockInfo> markers = element.getDataMarkers(templates, position, rotation, true);
		for (StructureTemplate.StructureBlockInfo info : markers) {
			if (!chunkBB.isInside(info.pos()) || info.nbt() == null) {
				continue;
			}
			Optional<Markers.Marker> marker = Markers.parse(info.nbt().getStringOr("metadata", ""));
			if (marker.isEmpty()) {
				continue;
			}
			try {
				handle(level, layout.get(), marker.get(), info.pos(), position, rotation, random);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.warn("[worldgen] casino marker {} at {} failed", marker.get().metadata(), info.pos(), e);
			}
		}
	}

	private static void handle(ServerLevelAccessor level, Layout layout, Markers.Marker marker, BlockPos pos, BlockPos origin,
			Rotation rotation, RandomSource random) {
		// The marker's structure block is never placed (vanilla ignores structure blocks), so the world
		// block from before is still there: clear it.
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		int turns = CasinoStructures.quarterTurns(rotation);
		ServerLevel serverLevel = level.getLevel();
		MinecraftServer server = serverLevel.getServer();
		String dimension = serverLevel.dimension().identifier().toString();
		String casinoId = CasinoRecord.idFor(dimension, origin.getX(), origin.getY(), origin.getZ());
		switch (marker) {
			case Markers.Anchor anchor -> {
				Layout l = Layouts.byId(anchor.layoutId()).orElse(layout);
				Geometry.Box box = Geometry.templateBox(new Vec(origin.getX(), origin.getY(), origin.getZ()), l.size(), turns);
				server.execute(() -> CasinoIndex.get(server).setAnchor(casinoId, dimension, l.id(), l.kind(), box));
			}
			case Markers.Npc npc -> {
				BlockPos stand = pos.below();
				float yaw = npc.facing().rotate(turns).yaw();
				Entity entity = CasinoNpcs.spawn(level, npc.role(), stand, yaw);
				UUID uuid = entity == null ? null : entity.getUUID();
				Vec home = new Vec(stand.getX(), stand.getY(), stand.getZ());
				server.execute(() -> CasinoIndex.get(server).addNpc(casinoId, dimension, npc.role(), home, yaw, uuid));
			}
			case Markers.Chest chest -> {
				Direction facing = direction(chest.facing().rotate(turns));
				BlockState state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
				level.setBlock(pos, state, 2);
				BlockEntity be = level.getBlockEntity(pos);
				if (be instanceof Container container) {
					fill(level, container, chest.table(), random);
				}
			}
		}
	}

	/** Fills a container from a casino loot table (items whose module is missing are left out). */
	public static void fill(ServerLevelAccessor level, Container container, CasinoLoot.Table table, RandomSource random) {
		List<CasinoLoot.Stack> stacks = CasinoLoot.roll(table, random::nextInt,
			id -> BuiltInRegistries.ITEM.containsKey(Identifier.parse(id)));
		List<Integer> slots = CasinoLoot.scatterSlots(stacks.size(), container.getContainerSize(), random::nextInt);
		for (int i = 0; i < slots.size(); i++) {
			CasinoLoot.Stack s = stacks.get(i);
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(s.item())), s.count());
			if (s.enchant()) {
				enchantBook(level, stack, random);
			}
			container.setItem(slots.get(i), stack);
		}
	}

	private static void enchantBook(ServerLevelAccessor level, ItemStack book, RandomSource random) {
		CasinoLoot.EnchantPick pick = CasinoLoot.pickEnchant(random::nextInt);
		Optional<? extends Holder<Enchantment>> enchantment = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(pick.id())));
		if (enchantment.isEmpty()) {
			return;
		}
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(enchantment.get(), pick.level());
		book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
	}

	static Direction direction(Facing f) {
		return switch (f) {
			case NORTH -> Direction.NORTH;
			case EAST -> Direction.EAST;
			case SOUTH -> Direction.SOUTH;
			case WEST -> Direction.WEST;
		};
	}
}
