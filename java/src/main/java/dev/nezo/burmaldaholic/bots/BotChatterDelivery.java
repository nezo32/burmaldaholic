package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.logic.ChatterGate;
import dev.nezo.burmaldaholic.bots.logic.Quips;
import dev.nezo.burmaldaholic.core.bots.BotChatter;
import dev.nezo.burmaldaholic.core.bots.Bots;
import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * Delivers bot chatter events reported through core's {@link BotChatter} (BOTS.md §7.4): emote particles,
 * then — if the server switch and the table's Bot chatter toggle are on and the {@link ChatterGate} admits
 * it — a random quip as {@code msg.burmaldaholic.bots.say} to seated humans and players within
 * {@code multiplayer.spectatorRadius} who have not muted bot chatter, with a soft "tin voice".
 * Lines queued by the table cooldown (explanatory events, ≤ 40 t) are said on a later tick.
 */
final class BotChatterDelivery implements BotChatter.Sink {
	private record Queued(long due, ServerLevel level, BlockPos pos, Component message) {}

	private static final Map<String, ChatterGate> GATES = new HashMap<>();
	private static final Map<String, Long> LAST_USE = new HashMap<>();
	private static final List<Queued> QUEUE = new ArrayList<>();
	private static @Nullable BotRng rng;

	private static BotRng rng() {
		if (rng == null) {
			rng = Bots.newRng();
		}
		return rng;
	}

	@Override
	public void event(ServerLevel level, BlockPos table, BotProfile bot, String event, @Nullable String humanName) {
		if (!CasinoMode.isEnabled(level.getServer())) {
			return;
		}
		emote(level, table, bot, Quips.emote(event));
		var cfg = CasinoConfig.bots();
		if (!cfg.chatter.enabled) {
			return;
		}
		Optional<BotTables.Found> found = BotTables.at(level, table);
		if (found.isPresent() && !found.get().bots().settings().chatter()) {
			return;
		}
		String key = BotTables.key(level, table);
		long now = level.getGameTime();
		ChatterGate gate = GATES.computeIfAbsent(key, k -> new ChatterGate());
		LAST_USE.put(key, now);
		long wait = gate.admit(bot.key(), bot.level(), event, now,
			new ChatterGate.Config(cfg.chatter.chance, cfg.chatter.botCooldownTicks, cfg.chatter.tableCooldownTicks, cfg.chatter.maxPerMinute), rng());
		if (wait < 0) {
			return;
		}
		String line = Quips.pick(event, rng());
		if (line == null) {
			return;
		}
		Component message = Component.translatable("msg.burmaldaholic.bots.say", BotTexts.display(bot),
			Component.translatable(line, Texts.raw(humanName == null ? "" : humanName)));
		if (wait == 0) {
			deliver(level, table, message);
		} else {
			QUEUE.add(new Queued(now + wait, level, table.immutable(), message));
		}
	}

	static void tick(MinecraftServer server) {
		if (!QUEUE.isEmpty()) {
			List<Queued> due = new ArrayList<>();
			QUEUE.removeIf(q -> {
				if (q.level().getGameTime() >= q.due()) {
					due.add(q);
					return true;
				}
				return false;
			});
			due.forEach(q -> deliver(q.level(), q.pos(), q.message()));
		}
		if (server.getTickCount() % 6000 == 0) {
			long now = server.overworld().getGameTime();
			LAST_USE.entrySet().removeIf(e -> {
				boolean stale = now - e.getValue() > 6000;
				if (stale) {
					GATES.remove(e.getKey());
				}
				return stale;
			});
		}
	}

	private static void deliver(ServerLevel level, BlockPos table, Component message) {
		MinecraftServer server = level.getServer();
		BotsData data = BotsData.get(server);
		int radius = CasinoConfig.multiplayer().spectatorRadius;
		Set<UUID> seated = new HashSet<>();
		BotTables.at(level, table).ifPresent(f -> seated.addAll(f.table().seatedHumans()));
		Vec3 centre = Vec3.atCenterOf(table);
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
			level.playSound(null, table, SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.4F, 1.4F);
		}
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

	static void clear() {
		GATES.clear();
		LAST_USE.clear();
		QUEUE.clear();
		rng = null;
	}
}
