package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.logic.PlateAnim;
import dev.nezo.burmaldaholic.bots.logic.SeatPoints;
import dev.nezo.burmaldaholic.bots.mixin.DisplayAccessor;
import dev.nezo.burmaldaholic.bots.mixin.TextDisplayAccessor;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.BotsConfig;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.fx.BigWinBroadcast;
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
import net.minecraft.network.chat.MutableComponent;
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

	/** A live plate: entity, position, the text shown, and the state the text / pulse derive from. */
	private record Plate(UUID entity, Vec3 pos, Component text, long stack, boolean thinking) {}

	/** A queued transformation key of a plate (join / leave / pulse, global §4.12); {@code discard} removes the entity. */
	private record Key(ServerLevel level, UUID entity, long dueTick, float scale, int duration, boolean discard) {}

	private static final java.util.List<Key> KEYS = new java.util.ArrayList<>();
	private static final net.minecraft.network.chat.FontDescription BOT_FONT =
		new net.minecraft.network.chat.FontDescription.Resource(dev.nezo.burmaldaholic.Burmaldaholic.id("bots"));

	private record TablePlates(ServerLevel level, BlockPos pos, Map<String, Plate> plates) {}

	private static final Map<String, TablePlates> TABLES = new LinkedHashMap<>();

	private BotAvatars() {}

	static void tick(MinecraftServer server) {
		runKeys(server);
		if (server.getTickCount() % PlateAnim.DOTS_TICKS == 0 && server.getTickCount() % PERIOD != 0) {
			refreshThinking(server);
		}
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
			boolean thinking = key.equals(f.table().botThinking());
			long stack = stackOf(f.bots(), bot);
			Component text = plateText(f.bots(), bot, thinking, f.level().getServer().getTickCount());
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
					t.plates().put(key, new Plate(display.getUUID(), at, text, stack, thinking));
					animate(f.level(), display.getUUID(), PlateAnim.join(), false);
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
			if (plate.stack() != stack || (plate.thinking() && !thinking)) {
				animate(f.level(), plate.entity(), PlateAnim.pulse(), false); // the bot acted (global §4.12)
			}
			t.plates().put(key, new Plate(plate.entity(), at, text, stack, thinking));
		}
		var it = t.plates().entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			if (!present.contains(e.getKey())) {
				leave(f.level(), e.getValue().entity());
				it.remove();
				budget++;
			}
		}
		return budget;
	}

	/** "[BOT] Name · N" (atmosphere) or "[BOT] Name · N · 180 chips" (money bots). */
	static Component plateText(TableBots bots, SeatOccupant.Bot bot) {
		return plateText(bots, bot, false, 0);
	}

	/**
	 * The plate (global §4.12): bot glyph, "[BOT] Name · ◖●●◗N" with the difficulty pill (shape + colour + letter),
	 * the stack of money bots, and the thinking dots (cycling every 10 t) while the bot's decision timer runs.
	 */
	static Component plateText(TableBots bots, SeatOccupant.Bot bot, boolean thinking, long tick) {
		// glyphs are siblings of an empty root, never parents: children inherit the parent's font
		MutableComponent badge = Component.empty()
			.append(Texts.raw(PlateAnim.pill(bot.profile().level())).withStyle(st -> st.withFont(BOT_FONT))) // literal-ok: font glyph
			.append(BotTexts.badge(bot.profile().level()));
		MutableComponent name = Component.translatable("gui.burmaldaholic.bots.display_level", Component.translatable(bot.profile().nameKey()), badge);
		MutableComponent line = Component.empty()
			.append(Texts.raw(PlateAnim.BOT_GLYPH).withStyle(st -> st.withFont(BigWinBroadcast.GLYPHS))) // literal-ok: font glyph
			.append(Texts.raw(" ")).append(name); // literal-ok: separator
		if (bot.role() == BotRole.MONEY) {
			line = Component.translatable("gui.burmaldaholic.bots.nameplate", line, Texts.chips(stackOf(bots, bot)));
		}
		if (thinking) {
			line = Component.empty().append(line).append(Texts.raw(" ")) // literal-ok: separator
				.append(Texts.raw(PlateAnim.dots(tick)).withStyle(st -> st.withFont(BigWinBroadcast.GLYPHS))); // literal-ok: font glyph
		}
		return line;
	}

	private static long stackOf(TableBots bots, SeatOccupant.Bot bot) {
		if (bot.role() != BotRole.MONEY) {
			return 0;
		}
		for (TableBots.SeatedBot b : bots.bots()) {
			if (b.profile.id().equals(bot.profile().id())) {
				return b.stack;
			}
		}
		return 0;
	}

	/** Every 10 t between syncs: advance the thinking dots of plates whose bot is deciding (≤ 1 text update / 10 t). */
	private static void refreshThinking(MinecraftServer server) {
		for (TablePlates t : TABLES.values()) {
			if (!(t.level().getBlockEntity(t.pos()) instanceof dev.nezo.burmaldaholic.core.bots.BotTable table)) {
				continue;
			}
			String thinking = table.botThinking();
			if (thinking == null || table.tableBots() == null) {
				continue;
			}
			Plate plate = t.plates().get(thinking);
			if (plate == null) {
				continue;
			}
			for (SeatOccupant o : table.occupants()) {
				if (o instanceof SeatOccupant.Bot bot && bot.key().equals(thinking)
					&& t.level().getEntity(plate.entity()) instanceof TextDisplayAccessor acc) {
					Component text = plateText(table.tableBots(), bot, true, server.getTickCount());
					acc.burmaldaholic$setText(text);
					t.plates().put(thinking, new Plate(plate.entity(), plate.pos(), text, plate.stack(), true));
				}
			}
		}
	}

	// ---- plate motion (vanilla display interpolation; global §4.12) ----------------------------------------------------

	private static void animate(ServerLevel level, UUID entity, PlateAnim.Key[] keys, boolean discardAfter) {
		long now = level.getServer().getTickCount();
		for (int i = 0; i < keys.length; i++) {
			PlateAnim.Key k = keys[i];
			if (k.atTick() == 0) {
				apply(level.getEntity(entity), k.scale(), k.duration());
			} else {
				KEYS.add(new Key(level, entity, now + k.atTick(), k.scale(), k.duration(), false));
			}
		}
		if (discardAfter) {
			KEYS.add(new Key(level, entity, now + PlateAnim.leaveDoneTicks(), 0, 0, true));
		}
	}

	/** A plate leaves: shrink to 0 over 6 t, then the entity goes. */
	private static void leave(ServerLevel level, UUID entity) {
		Entity e = level.getEntity(entity);
		if (e == null) {
			return;
		}
		animate(level, entity, PlateAnim.leave(), true);
	}

	private static void apply(@Nullable Entity e, float scale, int duration) {
		if (!(e instanceof DisplayAccessor d)) {
			return;
		}
		d.burmaldaholic$setTransformationInterpolationDelay(0);
		d.burmaldaholic$setTransformationInterpolationDuration(duration);
		d.burmaldaholic$setTransformation(new com.mojang.math.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
			new org.joml.Vector3f(scale, scale, scale), new org.joml.Quaternionf()));
	}

	private static void runKeys(MinecraftServer server) {
		if (KEYS.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		var it = KEYS.iterator();
		java.util.List<Key> due = new java.util.ArrayList<>();
		while (it.hasNext()) {
			Key k = it.next();
			if (k.dueTick() <= now || k.dueTick() - now > 100) {
				due.add(k);
				it.remove();
			}
		}
		for (Key k : due) {
			Entity e = k.level().getEntity(k.entity());
			if (e == null) {
				continue;
			}
			if (k.discard()) {
				e.discard();
			} else {
				apply(e, k.scale(), k.duration());
			}
		}
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
		d.setNoGravity(true);
		((TextDisplayAccessor) d).burmaldaholic$setText(text);
		((TextDisplayAccessor) d).burmaldaholic$setBackgroundColor(PlateAnim.BACKGROUND);
		((TextDisplayAccessor) d).burmaldaholic$setFlags(Display.TextDisplay.FLAG_SHADOW);
		((DisplayAccessor) d).burmaldaholic$setBillboardConstraints(Display.BillboardConstraints.CENTER);
		((DisplayAccessor) d).burmaldaholic$setViewRange(0.5F);
		if (!level.addFreshEntity(d)) {
			return null;
		}
		// tagged after it joined: ENTITY_LOAD fires inside addFreshEntity, and a tagged plate unknown to TABLES is
		// discarded there (the "restored from a save" rule) — the new plate would never have shown
		d.addTag(TAG);
		return d;
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
		KEYS.removeIf(k -> k.level() == t.level() && !k.discard());
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
		for (Key k : KEYS) {
			Entity e = k.discard() ? k.level().getEntity(k.entity()) : null;
			if (e != null) {
				e.discard(); // leaving plates must not outlive the table list
			}
		}
		KEYS.clear();
	}

	/** Server stopping: plates go before the world is saved. */
	static void clear() {
		removeAll();
	}
}
