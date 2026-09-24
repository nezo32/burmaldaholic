package dev.nezo.burmaldaholic.gametest.slots;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotsFx;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetFxPlan;
import dev.nezo.burmaldaholic.games.slots.cabinet.CabinetSync;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * In-world cabinet (lane J-L10, slots.md §5): the published sync reaches clients through the block entity update
 * tag unchanged, survives a save/load, and its world FX play on schedule without errors.
 */
public class SlotCabinetGameTests {
	private static int[][] strips() {
		int[][] s = new int[5][];
		for (int r = 0; r < 5; r++) {
			s[r] = new int[40];
			for (int i = 0; i < 40; i++) s[r][i] = (i * 7 + r * 3) % 11;
		}
		return s;
	}

	@GameTest(maxTicks = 200)
	public void cabinetSyncTravelsInTheUpdateTag(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, SlotsModule.MACHINES.get(Tier.COPPER).block());
		SlotMachineBlockEntity be = helper.getBlockEntity(rel, SlotMachineBlockEntity.class);
		var registries = helper.getLevel().registryAccess();
		helper.assertTrue(be.cabinetSync() == null, "no cabinet before the first spin");
		helper.assertFalse(be.getUpdateTag(registries).contains("cabinet"), "empty update tag before a spin");
		long now = helper.getLevel().getGameTime();
		CabinetSync sync = CabinetSync.builder(Machine.OVERWORLD, 1, now, 42, 100, strips(), new int[] {0, 1, 2, 3, 4})
			.spin(new int[] {5, 6, 7, 8, 9}).wins(0b111).done()
			.result(WinTier.BIG, 500, 0, false).build();
		be.publishCabinet(sync);
		helper.assertTrue(!CabinetFxPlan.events(sync).isEmpty(), "world FX planned (played by each client with its FX settings)");
		helper.assertTrue(SlotsFx.emberBurst() != null && BuiltInRegistries.PARTICLE_TYPE.getKey(SlotsFx.emberBurst()) != null,
			"ember_burst registered");
		CompoundTag tag = be.getUpdateTag(registries);
		int[] data = tag.getIntArray("cabinet").orElse(new int[0]);
		helper.assertTrue(sync.equals(CabinetSync.decode(data)), "update tag carries the sync");
		// the client path: the update tag is loaded into the block entity
		helper.setBlock(new BlockPos(3, 1, 1), SlotsModule.MACHINES.get(Tier.COPPER).block());
		SlotMachineBlockEntity copy = helper.getBlockEntity(new BlockPos(3, 1, 1), SlotMachineBlockEntity.class);
		copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		helper.assertTrue(sync.equals(copy.cabinetSync()), "client block entity decodes the sync");
		CompoundTag saved = be.saveWithoutMetadata(registries);
		helper.assertTrue(saved.getIntArray("cabinet").isPresent(), "the last window survives a save");
		helper.succeed();
	}
}
