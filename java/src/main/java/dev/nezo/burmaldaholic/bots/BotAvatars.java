package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.logic.SeatPoints;
import dev.nezo.burmaldaholic.bots.mixin.DisplayAccessor;
import dev.nezo.burmaldaholic.bots.mixin.TextDisplayAccessor;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.BotsConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bot avatars (BOTS.md §7.2), mode {@code NAMEPLATE} (Java default): one vanilla text display per seated
 * bot, billboarded 1.6 blocks above its seat point on the table edge, reading
 * "[BOT] Creeper42 · N · 180 chips" (translated on each client). Shown only while a player is within
 * {@code multiplayer.spectatorRadius}; at most {@code bots.avatars.maxEntities} per server; never inside
 * blocks. Plates carry {@link #TAG} and are discarded when a chunk loads them again, so they are never
 * restored from a save. {@code ENTITY} (NICE) currently behaves as {@code NAMEPLATE}.
 */
final class BotAvatars {
	static final String TAG = "burmaldaholic_bot_plate";
	private static final int PERIOD = 20;

	private record Plate(UUID entity, Vec3 pos, Component text) {}

	private record TablePlates(ServerLevel level, BlockPos pos, Map<String, Plate> plates) {}

	private static final Map<String, TablePlates> TABLES = new LinkedHashMap<>();

	private BotAvatars() {}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % PERIOD != 0) {
			return;
		}
		BotsConfig cfg = CasinoConfig.bots();
		if (cfg.avatars.mode == BotsConfig.AvatarMode.NONE || !cfg.enabled || !CasinoMode.isEnabled(server)) {
			removeAll();
			return;
		}
		int radius = Math.max(1, CasinoConfig.multiplayer().spectatorRadius);
		Map<String, BotTables.Found> active = new LinkedHashMap<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) {
				continue;
			}
			for (BotTables.Found f : BotTables.near(p.level(), p.position(), radius)) {
				if (f.botsSeated() > 0) {
					active.putIfAbsent(f.key(), f);
				}
			}
		}
		TABLES.entrySet().removeIf(e -> {
			if (!active.containsKey(e.getKey())) {
				discard(e.getValue());
				return true;
			}
			return false;
		});
		int budget = cfg.avatars.maxEntities;
		for (TablePlates t : TABLES.values()) {
			budget -= t.plates().size();
		}
		for (BotTables.Found f : active.values()) {
			budget = sync(f, budget);
		}
	}

	/** Creates / moves / relabels / removes the plates of one table; returns the entity budget left. */
	private static int sync(BotTables.Found f, int budget) {
		TablePlates t = TABLES.computeIfAbsent(f.key(), k -> new TablePlates(f.level(), f.pos(), new HashMap<>()));
		List<SeatOccupant> occupants = f.table().occupants();
		int seats = Math.max(occupants.size(), f.table().botSeatCount());
		Direction facing = f.facing();
		Set<String> present = new HashSet<>();
		for (int i = 0; i < occupants.size(); i++) {
			if (!(occupants.get(i) instanceof SeatOccupant.Bot bot)) {
				continue;
			}
			String key = bot.key();
			present.add(key);
			double[] off = SeatPoints.offset(i, seats, facing.getStepX(), facing.getStepZ());
			Vec3 at = new Vec3(f.pos().getX() + 0.5 + off[0], f.pos().getY() + SeatPoints.HEIGHT, f.pos().getZ() + 0.5 + off[1]);
			Component text = plateText(f.bots(), bot);
			Plate plate = t.plates().get(key);
			Entity entity = plate == null ? null : f.level().getEntity(plate.entity());
			if (entity == null || entity.isRemoved()) {
				if (plate != null) {
					t.plates().remove(key);
					budget++;
				}
				if (budget <= 0 || !clear(f.level(), at)) {
					continue;
				}
				Display.TextDisplay display = spawn(f.level(), at, text);
				if (display != null) {
					t.plates().put(key, new Plate(display.getUUID(), at, text));
					budget--;
				}
				continue;
			}
			if (!plate.pos().equals(at)) {
				entity.setPos(at);
			}
			if (!plate.text().equals(text) && entity instanceof TextDisplayAccessor acc) {
				acc.burmaldaholic$setText(text);
			}
			t.plates().put(key, new Plate(plate.entity(), at, text));
		}
		var it = t.plates().entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			if (!present.contains(e.getKey())) {
				Entity entity = f.level().getEntity(e.getValue().entity());
				if (entity != null) {
					entity.discard();
				}
				it.remove();
				budget++;
			}
		}
		return budget;
	}

	/** "[BOT] Name · N" (atmosphere) or "[BOT] Name · N · 180 chips" (money bots). */
	static Component plateText(TableBots bots, SeatOccupant.Bot bot) {
		Component name = BotTexts.displayLevel(bot.profile());
		if (bot.role() != BotRole.MONEY) {
			return name;
		}
		long stack = 0;
		for (TableBots.SeatedBot b : bots.bots()) {
			if (b.profile.id().equals(bot.profile().id())) {
				stack = b.stack;
			}
		}
		return Component.translatable("gui.burmaldaholic.bots.nameplate", name, Texts.chips(stack));
	}

	/** Two blocks of air (no collision) at the plate and below it (BOTS.md §7.2). */
	private static boolean clear(ServerLevel level, Vec3 at) {
		BlockPos p = BlockPos.containing(at);
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
			&& level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
	}

	private static Display.@Nullable TextDisplay spawn(ServerLevel level, Vec3 at, Component text) {
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		d.setPos(at);
		d.addTag(TAG);
		d.setNoGravity(true);
		((TextDisplayAccessor) d).burmaldaholic$setText(text);
		((TextDisplayAccessor) d).burmaldaholic$setBackgroundColor(0x40000000);
		((DisplayAccessor) d).burmaldaholic$setBillboardConstraints(Display.BillboardConstraints.CENTER);
		((DisplayAccessor) d).burmaldaholic$setViewRange(0.5F);
		return level.addFreshEntity(d) ? d : null;
	}

	/** The plate position of a bot (emote particles), or null. */
	static @Nullable Vec3 positionOf(ServerLevel level, BlockPos table, String botKey) {
		TablePlates t = TABLES.get(BotTables.key(level, table));
		Plate p = t == null ? null : t.plates().get(botKey);
		return p == null ? null : p.pos().add(0, 0.4, 0);
	}

	/** A plate loaded from a save (the chunk was saved while it existed): not ours any more → removed. */
	static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!entity.entityTags().contains(TAG)) {
			return;
		}
		for (TablePlates t : TABLES.values()) {
			for (Plate p : t.plates().values()) {
				if (p.entity().equals(entity.getUUID())) {
					return;
				}
			}
		}
		entity.discard();
	}

	private static void discard(TablePlates t) {
		for (Plate p : t.plates().values()) {
			Entity e = t.level().getEntity(p.entity());
			if (e != null) {
				e.discard();
			}
		}
		t.plates().clear();
	}

	static void removeAll() {
		TABLES.values().forEach(BotAvatars::discard);
		TABLES.clear();
	}

	/** Server stopping: plates go before the world is saved. */
	static void clear() {
		removeAll();
	}
}
