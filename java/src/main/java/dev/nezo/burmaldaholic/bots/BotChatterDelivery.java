package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.logic.ChatterGate;
import dev.nezo.burmaldaholic.bots.logic.Quips;
import dev.nezo.burmaldaholic.core.bots.BotChatter;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Delivers bot chatter lines that core's {@link BotChatter} queue let through (rate limits, chance,
 * cooldowns; BOTS.md §7.4): if the table's Bot chatter toggle is on, emote particles, then the line to
 * seated humans and players within {@code multiplayer.spectatorRadius} who have not muted bot chatter,
 * with a soft "tin voice".
 */
final class BotChatterDelivery implements BotChatter.Sink {
	@Override
	public void deliver(ServerLevel level, @Nullable BlockPos pos, Collection<UUID> seatedIds, BotProfile bot, String event, Component message) {
		if (!CasinoMode.isEnabled(level.getServer()) || pos == null) {
			return;
		}
		Optional<BotTables.Found> found = BotTables.at(level, pos);
		if (found.isPresent() && !found.get().bots().settings().chatter()) {
			return;
		}
		emote(level, pos, bot, Quips.emote(event));
		MinecraftServer server = level.getServer();
		BotsData data = BotsData.get(server);
		int radius = CasinoConfig.multiplayer().spectatorRadius;
		Set<UUID> seated = new HashSet<>(seatedIds);
		found.ifPresent(f -> seated.addAll(f.table().seatedHumans()));
		Vec3 centre = Vec3.atCenterOf(pos);
		boolean any = false;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean isSeated = seated.contains(p.getUUID());
			double d2 = p.level() == level ? p.position().distanceToSqr(centre) : Double.MAX_VALUE;
			if (ChatterGate.hears(true, true, data.muted(p.getUUID()), isSeated, d2, radius)) {
				p.sendSystemMessage(message);
				any = true;
			}
		}
		if (any) {
			level.playSound(null, pos, SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.4F, 1.4F);
		}
	}

	static void tick(MinecraftServer server) {
		// core BotChatter drains its queue on the server tick; nothing to do here
	}

	private static void emote(ServerLevel level, BlockPos table, BotProfile bot, Quips.Emote emote) {
		ParticleOptions particle = switch (emote) {
			case HAPPY -> ParticleTypes.HAPPY_VILLAGER;
			case ANGRY -> ParticleTypes.ANGRY_VILLAGER;
			case NOTE -> ParticleTypes.NOTE;
			case SMOKE -> ParticleTypes.SMOKE;
			case NONE -> null;
		};
		if (particle == null) {
			return;
		}
		Vec3 at = BotAvatars.positionOf(level, table, bot.key());
		if (at == null) {
			at = Vec3.atCenterOf(table).add(0, 1.2, 0);
		}
		level.sendParticles(particle, at.x, at.y, at.z, 5, 0.25, 0.2, 0.25, 0.02);
	}

	static void clear() {}
}
