package dev.nezo.burmaldaholic.client.table;

import dev.nezo.burmaldaholic.client.anim.NetLagHook;
import dev.nezo.burmaldaholic.client.anim.SmoothClock;
import dev.nezo.burmaldaholic.core.anim.sync.SeqGate;
import dev.nezo.burmaldaholic.core.network.TableSyncPayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Last state received per table. The sync payload may arrive before the screen opens, so screens
 * read the cache on init and are notified on updates.
 *
 * <p>SY-SPEC §4: every packet passes the table's {@link SeqGate} first; a stale one ({@code sync_seq} not newer in the
 * same {@code sync_epoch}) is dropped before the cache or the screen sees it, and a new epoch resets the table's cache.
 * Its {@code game_time} feeds the shared {@link SmoothClock} (observe only, even when dropped).
 */
public final class ClientTableCache {
	private static final Map<BlockPos, CompoundTag> STATES = new HashMap<>();
	private static final Map<BlockPos, SeqGate> GATES = new HashMap<>();

	private ClientTableCache() {}

	public static void register() {
		SmoothClock.register();
		ClientPlayNetworking.registerGlobalReceiver(TableSyncPayload.TYPE, (payload, context) -> {
			if (NetLagHook.intercept(() -> receive(payload))) return;
			receive(payload);
		});
	}

	/** One table state packet (after the optional test delay). */
	static void receive(TableSyncPayload payload) {
		CompoundTag state = payload.state();
		long gameTime = state.getLongOr(SeqGate.GAME_TIME, 0);
		if (gameTime > 0) SmoothClock.observe(gameTime);
		SeqGate gate = GATES.computeIfAbsent(payload.pos(), p -> new SeqGate());
		SeqGate.Verdict v = gate.offer(state.getLongOr(SeqGate.SEQ, -1), state.getIntOr(SeqGate.EPOCH, 0));
		if (v == SeqGate.Verdict.DROP) return;
		if (v == SeqGate.Verdict.RESET) STATES.remove(payload.pos());
		STATES.put(payload.pos(), state);
		if (Minecraft.getInstance().gui.screen() instanceof CasinoTableScreen screen && screen.getMenu().pos().equals(payload.pos())) {
			screen.acceptState(state);
		}
	}

	public static CompoundTag get(BlockPos pos) {
		return STATES.getOrDefault(pos, new CompoundTag());
	}

	/** The last accepted {@code sync_seq} of the table at {@code pos} (0 when none; tests). */
	public static long lastSeq(BlockPos pos) {
		SeqGate g = GATES.get(pos);
		return g == null ? 0 : g.lastSeq();
	}

	public static void clear() {
		STATES.clear();
		GATES.clear();
	}
}
