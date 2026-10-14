package dev.nezo.burmaldaholic.games.extras.net;

import dev.nezo.burmaldaholic.games.extras.logic.DuelStagePlan;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → modded clients within 48 blocks: one in-world PvP Dice Duel, once per duel (DI §6, PHASE2B §4 DCb). The
 * client rebuilds the same {@link DuelStagePlan} (same solver, same keys) and renders the four dice itself, so it gets
 * no item-display entities and no sound packets for this duel. About 120 bytes.
 *
 * @param id        stage id (dedupe; unique per server run)
 * @param seed      the duel's cosmetic seed (the screens get the same)
 * @param start     game tick of the duel start (the screens' and the chat lines' clock origin)
 * @param chestA    player A's chest
 * @param chestB    player B's chest
 * @param groundY   ground height under the midpoint
 * @param rounds    per round {@code [side][die]} faces (side 0 = A)
 * @param winner    0 = A, 1 = B, −1 = refund
 * @param duellists the two players (they hear their screens, not the stage)
 */
public record DuelStageSync(int id, int seed, long start, DuelStagePlan.Point chestA, DuelStagePlan.Point chestB, double groundY, List<int[][]> rounds,
		int winner, List<UUID> duellists) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<DuelStageSync> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, DuelStageSync> CODEC = StreamCodec.of(DuelStageSync::write, DuelStageSync::read);

	public DuelStageSync {
		rounds = List.copyOf(rounds);
		duellists = List.copyOf(duellists);
	}

	private static void write(RegistryFriendlyByteBuf buf, DuelStageSync s) {
		buf.writeVarInt(s.id);
		buf.writeInt(s.seed);
		buf.writeVarLong(s.start);
		point(buf, s.chestA);
		point(buf, s.chestB);
		buf.writeDouble(s.groundY);
		buf.writeVarInt(s.rounds.size());
		for (int[][] r : s.rounds) {
			for (int side = 0; side < 2; side++) for (int d = 0; d < 2; d++) buf.writeByte(r[side][d]);
		}
		buf.writeByte(s.winner);
		buf.writeVarInt(s.duellists.size());
		for (UUID u : s.duellists) buf.writeUUID(u);
	}

	private static DuelStageSync read(RegistryFriendlyByteBuf buf) {
		int id = buf.readVarInt();
		int seed = buf.readInt();
		long start = buf.readVarLong();
		DuelStagePlan.Point a = point(buf);
		DuelStagePlan.Point b = point(buf);
		double ground = buf.readDouble();
		int n = Math.min(16, buf.readVarInt());
		List<int[][]> rounds = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int[][] r = new int[2][2];
			for (int side = 0; side < 2; side++) for (int d = 0; d < 2; d++) r[side][d] = Math.max(1, Math.min(6, buf.readByte()));
			rounds.add(r);
		}
		int winner = buf.readByte();
		int m = Math.min(4, buf.readVarInt());
		List<UUID> who = new ArrayList<>(m);
		for (int i = 0; i < m; i++) who.add(buf.readUUID());
		return new DuelStageSync(id, seed, start, a, b, ground, rounds, winner, who);
	}

	private static void point(RegistryFriendlyByteBuf buf, DuelStagePlan.Point p) {
		buf.writeDouble(p.x());
		buf.writeDouble(p.y());
		buf.writeDouble(p.z());
	}

	private static DuelStagePlan.Point point(RegistryFriendlyByteBuf buf) {
		return new DuelStagePlan.Point(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	/** The plan this sync describes (solves the rolls: once per duel per client). */
	public DuelStagePlan plan() {
		return new DuelStagePlan(seed, rounds, chestA, chestB, groundY);
	}

	@Override
	public Type<DuelStageSync> type() {
		return TYPE;
	}
}
