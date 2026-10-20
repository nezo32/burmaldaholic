package dev.nezo.burmaldaholic.worldgen.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** DL-SPEC §3 look selection. */
class NpcLookTest {
	@Test
	void deterministicAndInRange() {
		String casino = "minecraft:overworld|120|64|-300";
		for (int i = 0; i < 200; i++) {
			int a = NpcLook.pick(NpcLook.HUMAN, casino, i * 7, 64, -i * 3);
			assertEquals(a, NpcLook.pick(NpcLook.HUMAN, casino, i * 7, 64, -i * 3), "same cell, same look (respawn)");
			assertTrue(a >= 0 && a < NpcLook.HUMAN);
			int u = NpcLook.pick(NpcLook.ENDER, new UUID(i, i * 31L));
			assertTrue(u >= 0 && u < NpcLook.ENDER);
		}
		assertEquals(0, NpcLook.pick(1, casino, 1, 2, 3));
	}

	@Test
	void packsLikeBlockPos() {
		for (int[] p : new int[][] {{0, 0, 0}, {12, 64, -40}, {-30000000 + 1, -64, 29999999}, {5, 319, -5}}) {
			assertEquals(BlockPos.asLong(p[0], p[1], p[2]), NpcLook.asLong(p[0], p[1], p[2]));
		}
	}

	@Test
	void speciesFollowsTheDimension() {
		assertEquals(NpcLook.Species.HUMAN, NpcLook.Species.of("minecraft:overworld"));
		assertEquals(NpcLook.Species.PIGLIN, NpcLook.Species.of("minecraft:the_nether"));
		assertEquals(NpcLook.Species.ENDER, NpcLook.Species.of("minecraft:the_end"));
		assertEquals(6, NpcLook.Species.HUMAN.dealerPool());
		assertEquals(4, NpcLook.Species.PIGLIN.dealerPool());
		assertEquals(3, NpcLook.Species.ENDER.dealerPool());
	}

	@Test
	void sixTablesShowAtLeastThreeLooks() {
		// every casino of a sample: its six dealer cells (two rows of three pods) wear >= 3 distinct skins
		for (int c = 0; c < 50; c++) {
			String casino = "minecraft:overworld|" + c * 160 + "|70|" + -c * 96;
			Set<Integer> looks = new HashSet<>();
			for (int row = 0; row < 2; row++) {
				for (int col = 0; col < 3; col++) {
					looks.add(NpcLook.pick(NpcLook.HUMAN, casino, c * 160 + 3 + col * 5, 71, -c * 96 + 1 + row * 4));
				}
			}
			assertTrue(looks.size() >= 3, casino + ": " + looks);
		}
	}
}
