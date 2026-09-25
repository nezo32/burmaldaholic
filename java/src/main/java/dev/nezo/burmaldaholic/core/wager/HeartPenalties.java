package dev.nezo.burmaldaholic.core.wager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Temporary max-health penalties from lost heart wagers (§4.3.3). Stored as a persistent player
 * attachment (survives death/relog), expiring by world time (counts while offline); the
 * {@code burmaldaholic:heart_wager_<n>} attribute modifiers are (re)applied on join, respawn and every second.
 */
public final class HeartPenalties {
	/**
	 * @param dormantBase {@link CasinoWorldData#dormantTicks()} when the penalty was added: the time casino
	 *                    mode was off since then is added to the expiry (the mod is dormant, §2.1)
	 */
	public record Penalty(int n, int hearts, long expiresAt, long dormantBase) {
		static final Codec<Penalty> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("n").forGetter(Penalty::n),
			Codec.INT.fieldOf("hearts").forGetter(Penalty::hearts),
			Codec.LONG.fieldOf("expires_at").forGetter(Penalty::expiresAt),
			Codec.LONG.optionalFieldOf("dormant_base", 0L).forGetter(Penalty::dormantBase)
		).apply(i, Penalty::new));

		/** Expiry in world time, shifted by the time casino mode was off. */
		public long effectiveExpiry(long dormantNow) {
			return expiresAt + Math.max(0, dormantNow - dormantBase);
		}

		Identifier modifierId() {
			return Burmaldaholic.id("heart_wager_" + n);
		}
	}

	private static AttachmentType<List<Penalty>> type;

	private HeartPenalties() {}

	public static void register() {
		type = AttachmentRegistry.create(Burmaldaholic.id("heart_wager"), builder -> builder
			.persistent(Penalty.CODEC.listOf())
			.copyOnDeath()
			.initializer(List::of));
		ServerPlayerEvents.JOIN.register(HeartPenalties::refresh);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!CasinoMode.isEnabled(server)) {
				CasinoWorldData.get(server).addDormantTicks(1);
			}
			if (server.getTickCount() % 20 == 0) {
				server.getPlayerList().getPlayers().forEach(HeartPenalties::refresh);
			}
		});
	}

	public static List<Penalty> active(ServerPlayer player) {
		List<Penalty> list = player.getAttached(type);
		return list == null ? List.of() : list;
	}

	/** Sum of hearts currently lost to heart wagers. */
	public static int activeHearts(ServerPlayer player) {
		return active(player).stream().mapToInt(Penalty::hearts).sum();
	}

	/** Applies a new penalty of {@code hearts} for {@code durationTicks} of world time. */
	public static void add(ServerPlayer player, int hearts, long durationTicks) {
		List<Penalty> list = new ArrayList<>(active(player));
		int n = list.stream().mapToInt(Penalty::n).max().orElse(0) + 1;
		MinecraftServer server = player.level().getServer();
		long now = now(server);
		list.add(new Penalty(n, hearts, now + durationTicks, CasinoWorldData.get(server).dormantTicks()));
		player.setAttached(type, List.copyOf(list));
		refresh(player);
	}

	/** Removes all penalties (admin). */
	public static void clear(ServerPlayer player) {
		AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
		for (Penalty p : active(player)) {
			if (attr != null) {
				attr.removeModifier(p.modifierId());
			}
		}
		player.setAttached(type, List.of());
	}

	/**
	 * Drops expired penalties and makes the attribute modifiers match the stored list. While casino mode
	 * is off the penalties are paused: modifiers removed, nothing expires (review M2, §2.1/§2.2).
	 */
	public static void refresh(ServerPlayer player) {
		List<Penalty> list = active(player);
		AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
		if (attr == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (!CasinoMode.isEnabled(server)) {
			for (Penalty p : list) {
				attr.removeModifier(p.modifierId());
			}
			return;
		}
		long now = now(server);
		long dormant = CasinoWorldData.get(server).dormantTicks();
		List<Penalty> keep = new ArrayList<>();
		boolean expired = false;
		for (Penalty p : list) {
			if (p.effectiveExpiry(dormant) <= now) {
				attr.removeModifier(p.modifierId());
				expired = true;
			} else {
				keep.add(p);
				if (attr.getModifier(p.modifierId()) == null) {
					attr.addTransientModifier(new AttributeModifier(p.modifierId(), -2.0 * p.hearts(), AttributeModifier.Operation.ADD_VALUE));
				}
			}
		}
		if (expired) {
			player.setAttached(type, List.copyOf(keep));
			if (keep.isEmpty()) {
				player.sendSystemMessage(Component.translatable("msg.burmaldaholic.wager.hearts_restored"));
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}
}
