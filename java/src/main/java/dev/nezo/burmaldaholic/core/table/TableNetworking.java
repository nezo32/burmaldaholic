package dev.nezo.burmaldaholic.core.table;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.network.TableActionPayload;
import dev.nezo.burmaldaholic.core.network.TableErrorPayload;
import dev.nezo.burmaldaholic.core.network.TableSyncPayload;
import net.minecraft.server.level.ServerPlayer;

/** Registers the generic table payloads and routes actions to the right block entity. Core only. */
public final class TableNetworking {
	private TableNetworking() {}

	public static void register(ModuleContext core) {
		TableSyncPayload.TYPE = core.payloads().clientbound("table_sync", TableSyncPayload.CODEC);
		TableErrorPayload.TYPE = core.payloads().clientbound("table_error", TableErrorPayload.CODEC);
		TableActionPayload.TYPE = core.payloads().serverbound("table_action", TableActionPayload.CODEC, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!(player.containerMenu instanceof CasinoTableMenu menu) || !menu.pos().equals(payload.pos()) || !menu.stillValid(player)) {
				return;
			}
			if (player.level().getBlockEntity(payload.pos()) instanceof CasinoTableBlockEntity table) {
				try {
					table.handleAction(player, payload.action(), payload.args());
				} catch (RuntimeException e) {
					Burmaldaholic.LOGGER.error("Table action '{}' at {} failed", payload.action(), payload.pos(), e);
				}
			}
		});
	}
}
