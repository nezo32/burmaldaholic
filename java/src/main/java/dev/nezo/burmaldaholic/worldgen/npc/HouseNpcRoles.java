package dev.nezo.burmaldaholic.worldgen.npc;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Click handlers other modules attach to casino NPC roles (DL for EC): {@code HouseNpcRoles.register("merchant", (player,
 * npc) -> openStore(player))}. The role id is the lower-case {@code NpcRole} name ({@code merchant}, {@code croupier} …).
 * A registered handler replaces the role's built-in behaviour after the greeting; the casino-mode check has already
 * passed. Registration happens at mod init; clicks arrive on the server thread.
 */
public final class HouseNpcRoles {
	private static final Map<String, BiConsumer<ServerPlayer, Entity>> HANDLERS = new ConcurrentHashMap<>();

	private HouseNpcRoles() {}

	/** Attaches (or replaces) the click handler of {@code role}. */
	public static void register(String role, BiConsumer<ServerPlayer, Entity> onInteract) {
		HANDLERS.put(role, onInteract);
	}

	/** Removes a handler (tests). */
	public static void unregister(String role) {
		HANDLERS.remove(role);
	}

	public static Optional<BiConsumer<ServerPlayer, Entity>> handler(@Nullable String role) {
		return role == null ? Optional.empty() : Optional.ofNullable(HANDLERS.get(role));
	}
}
