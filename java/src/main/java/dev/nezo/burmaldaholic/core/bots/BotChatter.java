package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.core.bots.logic.BotProfile;
import dev.nezo.burmaldaholic.core.bots.logic.BotRng;
import dev.nezo.burmaldaholic.core.bots.logic.ChatterQueue;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Bot quips (BOTS.md §7.4): a rate-limited queue per table / match ({@link ChatterQueue}), drained on
 * the server tick. Lines are fixed translated keys ({@code dialog.burmaldaholic.bots.<event>.<n>}) sent
 * as {@code msg.burmaldaholic.bots.say} ("[BOT] name: line"). Delivery goes through a {@link Sink}: the
 * default sends to the seated humans and players within {@code multiplayer.spectatorRadius} of the table
 * who are not muted ({@link #setMuteFilter}); the bots module may replace it (sound, particles, avatars).
 * Never call it with a line about hidden cards before the reveal (§7.4) — that is the caller's job.
 */
public final class BotChatter {
	/** Delivers a due line. */
	@FunctionalInterface
	public interface Sink {
		void deliver(ServerLevel level, @Nullable BlockPos pos, Collection<UUID> seated, BotProfile bot, String event, Component line);
	}

	private static final class Table {
		final ChatterQueue queue = new ChatterQueue();
		final Map<String, BotProfile> speakers = new HashMap<>();
		ServerLevel level;
		@Nullable BlockPos pos;
		Supplier<? extends Collection<UUID>> seated;
	}

	private static final Map<String, Table> TABLES = new HashMap<>();
	private static Predicate<ServerPlayer> muted = p -> false;
	private static Sink sink = BotChatter::defaultDeliver;

	private BotChatter() {}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BotChatter::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TABLES.clear();
			eventRng = null;
		});
	}

	/** Bots module: per-player mute (Casino Menu → Settings → Bot chatter). */
	public static void setMuteFilter(Predicate<ServerPlayer> filter) {
		muted = Objects.requireNonNull(filter);
	}

	/** Bots module: custom delivery (sound, emotes, avatars). */
	public static void setSink(Sink s) {
		sink = Objects.requireNonNull(s);
	}

	/** Default audience of a table line: seated humans + nearby players, minus muted ones. */
	public static List<ServerPlayer> audience(ServerLevel level, @Nullable BlockPos pos, Collection<UUID> seated) {
		Set<ServerPlayer> out = new LinkedHashSet<>();
		for (UUID id : seated) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				out.add(p);
			}
		}
		if (pos != null) {
			double r = CasinoConfig.multiplayer().spectatorRadius;
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r * r) {
					out.add(p);
				}
			}
		}
		out.removeIf(muted);
		return List.copyOf(out);
	}

	/**
	 * Offers a quip of {@code bot} for {@code event}; returns the queued line or null (dropped by a switch,
	 * the chance or a limit). {@code tableChatter} = the table's Chatter setting.
	 */
	public static ChatterQueue.@Nullable Line say(ServerLevel level, @Nullable BlockPos pos, String tableKey, Supplier<? extends Collection<UUID>> seated,
			BotProfile bot, String event, @Nullable String human, boolean tableChatter, BotRng rng) {
		var c = CasinoConfig.bots().chatter;
		Table t = TABLES.computeIfAbsent(tableKey, k -> new Table());
		t.level = level;
		t.pos = pos;
		t.seated = seated;
		ChatterQueue.Config cfg = new ChatterQueue.Config(c.enabled && tableChatter, c.chance, c.botCooldownTicks, c.tableCooldownTicks, c.maxPerMinute);
		ChatterQueue.Line line = t.queue.offer(bot.key(), bot.level(), event, human == null ? "" : human, level.getGameTime(), cfg, rng);
		if (line != null) {
			t.speakers.put(bot.key(), bot);
		}
		return line;
	}

	/** Drops the queue and cooldowns of a table (session over). Keeps the state otherwise (cooldowns span lines). */
	public static void forget(String tableKey) {
		TABLES.remove(tableKey);
	}

	static void tick(MinecraftServer server) {
		for (Table t : List.copyOf(TABLES.values())) {
			for (ChatterQueue.Line l : t.queue.due(t.level.getGameTime())) {
				BotProfile bot = t.speakers.get(l.botKey());
				if (bot != null) {
					Component line = Component.translatable("msg.burmaldaholic.bots.say", BotNames.display(bot), Component.translatable(l.key(), l.human()));
					try {
						sink.deliver(t.level, t.pos, t.seated.get(), bot, l.event(), line);
					} catch (RuntimeException e) {
						dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.warn("Bot chatter failed for {}", l.event(), e);
					}
				}
			}
		}
	}

	/**
	 * A chatter event not tied to a {@link TableBots} (PvP bot participants, heat notices): queued per
	 * position with the server switch only (the bots module's sink applies the table toggle when a table
	 * stands there). Never throws.
	 */
	public static void event(ServerLevel level, BlockPos pos, BotProfile bot, String event, @Nullable String human) {
		try {
			String key = "at:" + level.dimension().identifier() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
			if (eventRng == null) {
				eventRng = Bots.newRng();
			}
			say(level, pos, key, List::of, bot, event, human, true, eventRng);
		} catch (RuntimeException e) {
			dev.nezo.burmaldaholic.Burmaldaholic.LOGGER.warn("Bot chatter failed for {}", event, e);
		}
	}

	private static @Nullable BotRng eventRng;

	private static void defaultDeliver(ServerLevel level, @Nullable BlockPos pos, Collection<UUID> seated, BotProfile bot, String event, Component line) {
		for (ServerPlayer p : audience(level, pos, seated)) {
			p.sendSystemMessage(line);
		}
	}
}
