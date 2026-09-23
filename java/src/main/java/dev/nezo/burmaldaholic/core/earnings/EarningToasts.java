package dev.nezo.burmaldaholic.core.earnings;

import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Action-bar toast {@code msg.burmaldaholic.core.earned} ("+20 chips (Diamond Ore)"); credits from the
 * same source within 20 ticks are merged into one toast (§3.4).
 */
public final class EarningToasts {
	private static final int MERGE_TICKS = 20;
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private record Pending(long amount, Component source, String sourceKey, long lastTick) {}

	private EarningToasts() {}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(EarningToasts::tick);
	}

	/** Shows (or merges) a toast. {@code sourceKey} identifies the source for merging. */
	public static void show(ServerPlayer player, long amount, Component source, String sourceKey) {
		if (amount <= 0) {
			return;
		}
		long now = player.level().getServer().getTickCount();
		Pending p = PENDING.get(player.getUUID());
		if (p != null && p.sourceKey.equals(sourceKey) && now - p.lastTick < MERGE_TICKS) {
			p = new Pending(p.amount + amount, source, sourceKey, now);
		} else {
			p = new Pending(amount, source, sourceKey, now);
		}
		PENDING.put(player.getUUID(), p);
		player.sendOverlayMessage(Component.translatable("msg.burmaldaholic.core.earned", Texts.chips(p.amount), p.source));
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (Iterator<Pending> it = PENDING.values().iterator(); it.hasNext(); ) {
			if (now - it.next().lastTick >= MERGE_TICKS) {
				it.remove();
			}
		}
	}
}
