package dev.nezo.burmaldaholic.core.data;

import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Offline-safe chat notices with one chip amount (e.g. "your bank of N chips was returned", §20.9 / §21.9):
 * sent at once to an online player, otherwise kept in the player's record ({@code casino.dat}) and sent on
 * their next join. The chips themselves are credited by the economy at once (offline-safe); this only
 * carries the message. Server thread only.
 */
public final class OfflineMail {
	private OfflineMail() {}

	/** Core only (CoreModule.register). */
	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> deliver(handler.getPlayer()));
	}

	/**
	 * Sends {@code Component.translatable(key, chips)} now or on the next join. {@code accusative}: the
	 * argument is {@link Texts#chipsAcc} (STRINGS.md type {@code chips_acc}) instead of {@link Texts#chips}.
	 */
	public static void chips(MinecraftServer server, UUID player, String key, long amount, boolean accusative) {
		if (server == null || player == null) {
			return;
		}
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null && !online.hasDisconnected()) {
			online.sendSystemMessage(render(key, amount, accusative));
			return;
		}
		CompoundTag t = new CompoundTag();
		t.putString("key", key);
		t.putLong("chips", amount);
		t.putBoolean("acc", accusative);
		CasinoWorldData data = CasinoWorldData.get(server);
		data.player(player).pendingMail.add(t);
		data.setDirty();
	}

	/**
	 * Sends any chat line now, or keeps it for the next join when the player is offline (e.g. a table's result line
	 * that was held back until the dice landed, tables.md §0.6.3, and the player left in between).
	 */
	public static void line(MinecraftServer server, UUID player, Component message) {
		if (server == null || player == null) {
			return;
		}
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null && !online.hasDisconnected()) {
			online.sendSystemMessage(message);
			return;
		}
		var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ComponentSerialization.CODEC.encodeStart(ops, message).result().ifPresent(tag -> {
			CompoundTag t = new CompoundTag();
			t.put("line", tag);
			CasinoWorldData data = CasinoWorldData.get(server);
			data.player(player).pendingMail.add(t);
			data.setDirty();
		});
	}

	private static Component render(String key, long amount, boolean accusative) {
		return Component.translatable(key, accusative ? Texts.chipsAcc(amount) : Texts.chips(amount));
	}

	private static void deliver(ServerPlayer player) {
		CasinoWorldData data = CasinoWorldData.get(player.level().getServer());
		PlayerRecord rec = data.player(player.getUUID());
		if (rec.pendingMail.isEmpty()) {
			return;
		}
		List<CompoundTag> mail = List.copyOf(rec.pendingMail);
		rec.pendingMail.clear();
		data.setDirty();
		var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		for (CompoundTag t : mail) {
			if (t.contains("line")) {
				ComponentSerialization.CODEC.parse(ops, t.get("line")).result().ifPresent(player::sendSystemMessage);
				continue;
			}
			t.getString("key").ifPresent(key -> player.sendSystemMessage(render(key, t.getLongOr("chips", 0), t.getBooleanOr("acc", false))));
		}
	}
}
