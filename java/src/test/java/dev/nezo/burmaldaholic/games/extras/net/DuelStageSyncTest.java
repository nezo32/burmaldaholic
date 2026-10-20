package dev.nezo.burmaldaholic.games.extras.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.games.extras.logic.DuelStagePlan;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** R-DCb: the wire caps hold on both sides (no desync), and the dimension id round-trips and filters. */
class DuelStageSyncTest {
	private static final DuelStagePlan.Point A = new DuelStagePlan.Point(1.5, 65.1, -3.5);
	private static final DuelStagePlan.Point B = new DuelStagePlan.Point(4.5, 65.1, -3.5);

	private static RegistryFriendlyByteBuf buf() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
	}

	private static DuelStageSync sync(int rounds, int duellists, String dim) {
		List<int[][]> r = new ArrayList<>();
		for (int i = 0; i < rounds; i++) r.add(new int[][] {{1 + i % 6, 2}, {3, 4}});
		List<UUID> who = new ArrayList<>();
		for (int i = 0; i < duellists; i++) who.add(new UUID(i, i + 1));
		return new DuelStageSync(7, 42, 1234L, A, B, 64.0, r, 1, who, dim);
	}

	private static DuelStageSync roundTrip(DuelStageSync s) {
		RegistryFriendlyByteBuf b = buf();
		DuelStageSync.CODEC.encode(b, s);
		DuelStageSync back = DuelStageSync.CODEC.decode(b);
		assertEquals(0, b.readableBytes(), "decoder consumed exactly what the encoder wrote");
		return back;
	}

	@Test
	void roundTripsATypicalDuel() {
		DuelStageSync s = sync(3, 2, "minecraft:the_nether");
		DuelStageSync back = roundTrip(s);
		assertEquals(s.id(), back.id());
		assertEquals(s.seed(), back.seed());
		assertEquals(s.start(), back.start());
		assertEquals(s.chestA(), back.chestA());
		assertEquals(s.chestB(), back.chestB());
		assertEquals(s.winner(), back.winner());
		assertEquals(s.duellists(), back.duellists());
		assertEquals("minecraft:the_nether", back.dimension());
		assertEquals(3, back.rounds().size());
		for (int i = 0; i < 3; i++) for (int side = 0; side < 2; side++) assertArrayEquals(s.rounds().get(i)[side], back.rounds().get(i)[side]);
	}

	@Test
	void writeCapsMatchReadCaps() {
		// more than the caps: the writer truncates, so the reader stays in step and the stream decodes completely
		DuelStageSync back = roundTrip(sync(DuelStageSync.MAX_ROUNDS + 5, DuelStageSync.MAX_DUELLISTS + 3, "minecraft:overworld"));
		assertEquals(DuelStageSync.MAX_ROUNDS, back.rounds().size());
		assertEquals(DuelStageSync.MAX_DUELLISTS, back.duellists().size());
		assertEquals(1, back.winner());
		assertEquals("minecraft:overworld", back.dimension());
		// exactly at the caps: unchanged
		DuelStageSync at = roundTrip(sync(DuelStageSync.MAX_ROUNDS, DuelStageSync.MAX_DUELLISTS, "minecraft:overworld"));
		assertEquals(DuelStageSync.MAX_ROUNDS, at.rounds().size());
		assertEquals(DuelStageSync.MAX_DUELLISTS, at.duellists().size());
	}

	@Test
	void readRejectsOverCapCounts() {
		RegistryFriendlyByteBuf b = buf();
		b.writeVarInt(1);
		b.writeInt(2);
		b.writeVarLong(3);
		for (int i = 0; i < 7; i++) b.writeDouble(0);
		b.writeVarInt(DuelStageSync.MAX_ROUNDS + 1);
		assertThrows(RuntimeException.class, () -> DuelStageSync.CODEC.decode(b));
	}

	@Test
	void dimensionFilter() {
		DuelStageSync s = roundTrip(sync(3, 2, "minecraft:overworld"));
		assertTrue(s.inDimension("minecraft:overworld"));
		assertFalse(s.inDimension("minecraft:the_nether"));
		assertFalse(s.inDimension(""));
		// a null id never matches a real dimension
		assertFalse(sync(3, 2, null).inDimension("minecraft:overworld"));
	}
}
