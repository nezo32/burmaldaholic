package dev.nezo.burmaldaholic.worldgen;

import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoRecord;
import dev.nezo.burmaldaholic.worldgen.logic.RespawnRule;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/**
 * Server-side upkeep of generated casinos:
 * <ul>
 *   <li>"Welcome to …" title once per visit (STRINGS.md {@code msg.burmaldaholic.worldgen.entered});</li>
 *   <li>advancements {@code burmaldaholic:piglin_parlor} (enter a Parlor) and {@code burmaldaholic:high_roller}
 *       (settle a bet inside the Lounge) via core's {@code CasinoAdvancements} (§19);</li>
 *   <li>NPC respawn after {@code worldgen.loanSharkRespawnTicks} (§5.1).</li>
 * </ul>
 * Everything is dormant while casino mode is off.
 */
public final class CasinoTracker {
	static final int ENTER_INTERVAL = 20;
	static final int RESPAWN_INTERVAL = 200;

	private static final Map<UUID, String> INSIDE = new HashMap<>();

	private CasinoTracker() {}

	static void clear() {
		INSIDE.clear();
	}

	static void tick(MinecraftServer server) {
		if (!CasinoMode.isEnabled(server)) {
			return;
		}
		long now = server.getTickCount();
		if (now % ENTER_INTERVAL == 0) {
			checkEntering(server);
		}
		if (now % RESPAWN_INTERVAL == 0) {
			checkNpcs(server, server.overworld().getGameTime()); // persistent clock (survives restarts)
		}
	}

	/** The generated casino the player stands in, or {@code null}. */
	public static CasinoRecord casinoAt(ServerPlayer player) {
		CasinoIndex index = CasinoIndex.get(player.level().getServer());
		String dim = player.level().dimension().identifier().toString();
		return CasinoRecord.findAt(index.casinos(), dim, player.getX(), player.getY(), player.getZ());
	}

	private static void checkEntering(MinecraftServer server) {
		CasinoIndex index = CasinoIndex.get(server);
		if (index.casinos().isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			CasinoRecord casino = casinoAt(player);
			if (casino == null) {
				INSIDE.remove(player.getUUID());
				continue;
			}
			if (casino.id().equals(INSIDE.put(player.getUUID(), casino.id()))) {
				continue;
			}
			welcome(player, casino.kind());
			if (casino.kind() == CasinoKind.PIGLIN_PARLOR) {
				award(player, "piglin_parlor");
			}
		}
	}

	static void welcome(ServerPlayer player, CasinoKind kind) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		player.connection.send(new ClientboundSetTitleTextPacket(
			Component.translatable("msg.burmaldaholic.worldgen.entered", Component.translatable(kind.nameKey()))));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("msg.burmaldaholic.worldgen.entered_subtitle")));
	}

	/** High Roller advancement: a bet settled while standing in a High Roller Lounge. */
	static void onPlayResolved(ServerPlayer player) {
		if (!CasinoMode.isEnabled(player)) {
			return;
		}
		CasinoRecord casino = casinoAt(player);
		if (casino != null && casino.kind() == CasinoKind.HIGH_ROLLER) {
			award(player, "high_roller");
		}
	}

	/** §19 advancement through core's granter ({@code burmaldaholic:core/<id>}; no-op if already granted). */
	static void award(ServerPlayer player, String id) {
		if (!CasinoAdvancements.has(player, id)) {
			CasinoAdvancements.grant(player, id);
		}
	}

	private static void checkNpcs(MinecraftServer server, long now) {
		CasinoIndex index = CasinoIndex.get(server);
		long respawnTicks = CasinoConfig.worldgen().loanSharkRespawnTicks;
		for (CasinoRecord casino : index.casinos()) {
			if (casino.npcs().isEmpty()) {
				continue;
			}
			ServerLevel level = level(server, casino.dimension());
			if (level == null) {
				continue;
			}
			for (CasinoRecord.NpcSlot slot : casino.npcs()) {
				BlockPos home = new BlockPos(slot.home().x(), slot.home().y(), slot.home().z());
				if (!level.areEntitiesLoaded(ChunkPos.pack(home))) {
					continue; // unknown while unloaded; the timer only runs while someone is near
				}
				Entity entity = slot.entity == null ? null : level.getEntity(slot.entity);
				boolean present = entity != null && entity.isAlive();
				RespawnRule.Decision d = RespawnRule.decide(present, slot.missingSince, now, respawnTicks);
				boolean changed = d.missingSince() != slot.missingSince;
				slot.missingSince = d.missingSince();
				if (d.respawn()) {
					Entity spawned = CasinoNpcs.spawn(level, slot.role(), home, slot.yaw());
					if (spawned != null) {
						slot.entity = spawned.getUUID();
					}
					changed = true;
				}
				if (changed) {
					index.changed();
				}
			}
		}
	}

	private static ServerLevel level(MinecraftServer server, String dimension) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().identifier().toString().equals(dimension)) {
				return level;
			}
		}
		return null;
	}
}
