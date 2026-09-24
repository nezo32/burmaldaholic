package dev.nezo.burmaldaholic.bots;

import dev.nezo.burmaldaholic.bots.logic.SettingsRules;
import dev.nezo.burmaldaholic.core.bots.BotTable;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotRole;
import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Finds tables whose block entity implements {@link BotTable} and exposes its {@link TableBots}: at a
 * position, the one a player sits at, the one a player looks at (5 blocks), or all near a point. Tables
 * seen by any lookup are remembered weakly for the admin list ({@code /casino bots list}). Server thread.
 */
public final class BotTables {
	/** Reach of "the table block they look at" (BOTS.md §8.7). */
	public static final double REACH = 5.0;

	/** A located table. */
	public record Found(ServerLevel level, BlockPos pos, BlockEntity blockEntity, BotTable table, TableBots bots) {
		/** {@code <dimension>|x,y,z}. */
		public String key() {
			return BotTables.key(level, pos);
		}

		/** The table block's name ("Texas Hold'em Table"). */
		public Component name() {
			return Component.translatable(blockEntity.getBlockState().getBlock().getDescriptionId());
		}

		public boolean seated(UUID player) {
			return table.seatedHumans().contains(player);
		}

		/** The session host: {@code TableBots.host()} or, before core has chosen one, the §2.4 rule. */
		public @Nullable UUID host() {
			UUID h = bots.host();
			if (h != null && seated(h)) {
				return h;
			}
			return dev.nezo.burmaldaholic.core.bots.logic.SeatingMath.host(bots.keeper(), table.seatedHumans());
		}

		public Optional<UUID> owner() {
			return CoreServices.tableOwnership().owner(level, pos).map(o -> o.owner());
		}

		public boolean owned() {
			return owner().isPresent();
		}

		public SettingsRules.Actor actor(ServerPlayer player) {
			UUID id = player.getUUID();
			boolean owner = owner().map(id::equals).orElse(false);
			boolean keeper = !owned() && id.equals(bots.keeper());
			return new SettingsRules.Actor(isOperator(player), owner, keeper, id.equals(host()), seated(id));
		}

		public int botsSeated() {
			int n = 0;
			for (SeatOccupant o : table.occupants()) {
				if (o != null && o.isBot()) {
					n++;
				}
			}
			return n;
		}

		public SettingsRules.Table facts() {
			var cfg = CasinoConfig.bots();
			String game = table.botGameId();
			return new SettingsRules.Table(owned(), bots.effectiveLimits(level), table.botRole(), table.botSeatCount(), table.seatedHumans().size(),
				roleCap(game, table.botRole()), table.botDifficultyMatters(), "chemmy".equals(game), cfg.enabled, cfg.privateTables.enabled);
		}

		/** Facing of the table block (players' side), default north. */
		public Direction facing() {
			var state = blockEntity.getBlockState();
			return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
		}
	}

	private static final Map<String, WeakReference<BlockEntity>> KNOWN = new LinkedHashMap<>();

	private BotTables() {}

	public static boolean isOperator(ServerPlayer player) {
		return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
	}

	public static String key(ServerLevel level, BlockPos pos) {
		return level.dimension().identifier() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	/** Atmosphere cap per game ({@code bots.atmosphere.maxPerTable.<game>}); -1 = none. */
	static int roleCap(String game, BotRole role) {
		if (role != BotRole.ATMOSPHERE) {
			return -1;
		}
		var m = CasinoConfig.bots().atmosphere.maxPerTable;
		return switch (game) {
			case "blackjack" -> m.blackjack;
			case "uth" -> m.uth;
			case "roulette" -> m.roulette;
			case "craps" -> m.craps;
			case "baccarat" -> m.baccarat;
			default -> -1;
		};
	}

	/** A game created / attached its bot state (core {@code BotTableUi.attach}): the admin list and avatars know the table. */
	static void remember(BotTable table) {
		BlockEntity be = table instanceof BlockEntity b ? b : null;
		if (be == null && table.botTablePos() != null) {
			return; // helpers (AtmosphereBots, BaccaratBots): found through their block entity on the next lookup
		}
		if (be != null && be.getLevel() instanceof ServerLevel level) {
			of(level, be);
		}
	}

	/** The table at {@code pos}, if its block entity is a bot table with {@link TableBots}. Loaded chunks only. */
	public static Optional<Found> at(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return Optional.empty();
		}
		return of(level, level.getBlockEntity(pos));
	}

	private static Optional<Found> of(ServerLevel level, @Nullable BlockEntity be) {
		if (be instanceof BotTable t && !be.isRemoved()) {
			TableBots bots = t.tableBots();
			if (bots != null) {
				Found f = new Found(level, be.getBlockPos().immutable(), be, t, bots);
				KNOWN.put(f.key(), new WeakReference<>(be));
				return Optional.of(f);
			}
		}
		return Optional.empty();
	}

	/** Bot tables with their block within {@code radius} blocks of {@code center} (loaded chunks only). */
	public static List<Found> near(ServerLevel level, Vec3 center, int radius) {
		List<Found> out = new ArrayList<>();
		int minCx = ((int) Math.floor(center.x) - radius) >> 4;
		int maxCx = ((int) Math.floor(center.x) + radius) >> 4;
		int minCz = ((int) Math.floor(center.z) - radius) >> 4;
		int maxCz = ((int) Math.floor(center.z) + radius) >> 4;
		double r2 = (double) radius * radius;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
					if (be instanceof BotTable && Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(center) <= r2) {
						of(level, be).ifPresent(out::add);
					}
				}
			}
		}
		return out;
	}

	/** The table the player sits at (searched within {@code multiplayer.tableLeaveDistance} + 2). */
	public static Optional<Found> seated(ServerPlayer player) {
		int radius = CasinoConfig.multiplayer().tableLeaveDistance + 2;
		Found best = null;
		double bestD = Double.MAX_VALUE;
		for (Found f : near(player.level(), player.position(), radius)) {
			if (f.seated(player.getUUID())) {
				double d = Vec3.atCenterOf(f.pos()).distanceToSqr(player.position());
				if (d < bestD) {
					best = f;
					bestD = d;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** The bot table block the player looks at within {@link #REACH} blocks. */
	public static Optional<Found> lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(REACH));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return Optional.empty();
		}
		return at(player.level(), hit.getBlockPos());
	}

	/** Seated table first, else the looked-at one (BOTS.md §8.7). */
	public static Optional<Found> target(ServerPlayer player) {
		Optional<Found> s = seated(player);
		return s.isPresent() ? s : lookedAt(player);
	}

	/** Tables seen recently that are still loaded (admin list). */
	public static List<Found> known(Collection<ServerLevel> levels) {
		List<Found> out = new ArrayList<>();
		KNOWN.entrySet().removeIf(e -> {
			BlockEntity be = e.getValue().get();
			return be == null || be.isRemoved();
		});
		for (WeakReference<BlockEntity> ref : List.copyOf(KNOWN.values())) {
			BlockEntity be = ref.get();
			if (be != null && be.getLevel() instanceof ServerLevel level && levels.contains(level)) {
				of(level, be).ifPresent(out::add);
			}
		}
		return out;
	}

	static void clear() {
		KNOWN.clear();
	}
}
