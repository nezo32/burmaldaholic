package dev.nezo.burmaldaholic.core.wager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nezo.burmaldaholic.Burmaldaholic;
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
	public record Penalty(int n, int hearts, long expiresAt) {
		static final Codec<Penalty> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("n").forGetter(Penalty::n),
			Codec.INT.fieldOf("hearts").forGetter(Penalty::hearts),
			Codec.LONG.fieldOf("expires_at").forGetter(Penalty::expiresAt)
		).apply(i, Penalty::new));

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
		long now = now(player.level().getServer());
		list.add(new Penalty(n, hearts, now + durationTicks));
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

	/** Drops expired penalties and makes the attribute modifiers match the stored list. */
	public static void refresh(ServerPlayer player) {
		List<Penalty> list = active(player);
		AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
		if (attr == null) {
			return;
		}
		long now = now(player.level().getServer());
		List<Penalty> keep = new ArrayList<>();
		boolean expired = false;
		for (Penalty p : list) {
			if (p.expiresAt() <= now) {
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
