package dev.nezo.burmaldaholic.client.table;

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
 */
public final class ClientTableCache {
	private static final Map<BlockPos, CompoundTag> STATES = new HashMap<>();

	private ClientTableCache() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TableSyncPayload.TYPE, (payload, context) -> {
			STATES.put(payload.pos(), payload.state());
			if (Minecraft.getInstance().gui.screen() instanceof CasinoTableScreen screen && screen.getMenu().pos().equals(payload.pos())) {
				screen.acceptState(payload.state());
			}
		});
	}

	public static CompoundTag get(BlockPos pos) {
		return STATES.getOrDefault(pos, new CompoundTag());
	}

	public static void clear() {
		STATES.clear();
	}
}
