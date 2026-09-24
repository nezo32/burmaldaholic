package dev.nezo.burmaldaholic.multiplayer;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.economy.AccountId;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.service.TableOwnershipProvider.OwnedTable;
import dev.nezo.burmaldaholic.core.service.VipTiers;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.table.TableSeats;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Inventories;
import dev.nezo.burmaldaholic.multiplayer.logic.CasinoBook;
import dev.nezo.burmaldaholic.multiplayer.logic.CasinoBook.Casino;
import dev.nezo.burmaldaholic.multiplayer.logic.CasinoBook.TablePos;
import dev.nezo.burmaldaholic.multiplayer.logic.CasinoBook.TableRecord;
import dev.nezo.burmaldaholic.multiplayer.logic.CasinoStats;
import dev.nezo.burmaldaholic.multiplayer.logic.Claims;
import dev.nezo.burmaldaholic.multiplayer.logic.OwnerLimits;
import dev.nezo.burmaldaholic.multiplayer.logic.Solvency;
import dev.nezo.burmaldaholic.multiplayer.net.CharterActionPayload;
import dev.nezo.burmaldaholic.multiplayer.net.CharterStatePayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Player-owned casinos, server side (GAME_DESIGN.md §18.2): Casino Charter claim, owned tables, owner bankroll
 * (core bankroll account), insolvency closing / reopening, protection, statistics and the charter screen.
 *
 * <p>Money routing is done by core's table framework through {@link #owner} (installed as
 * {@code CoreServices.setTableOwnership}): stakes at an owned table go into the owner's bankroll, payouts
 * come out of it, and every stake reserves the game's worst case (refused with {@code house_broke} /
 * {@code exposure} when the bankroll cannot cover it). Server thread only.
 */
public final class Ownership {
	static final String ID = MultiplayerModule.ID;
	/** A settled round up to this many ticks after the player last used an owned table still counts for its stats. */
	private static final long RECENT_TABLE_TICKS = 1200;
	/** Chunk-scan limit per "Link tables" press (claims are ≤ 128 blocks: ≤ 17×17 chunks). */
	private static final int MAX_LINK_CHUNKS = 17 * 17;

	private record Recent(String key, long tick) {}

	private static final Map<UUID, Recent> RECENT = new HashMap<>();
	private static final Map<UUID, String> INSIDE = new HashMap<>();

	private Ownership() {}

	// ---- helpers -----------------------------------------------------------------------------

	static boolean enabled(MinecraftServer server) {
		return CasinoMode.isEnabled(server) && CasinoConfig.ownership().enabled;
	}

	static String dim(Level level) {
		return level.dimension().identifier().toString();
	}

	static String key(Level level, BlockPos pos) {
		return new TablePos(dim(level), pos.getX(), pos.getY(), pos.getZ()).key();
	}

	/**
	 * Charter "Bots" switch changed: the table's Seats &amp; Bots owner mode follows (off → Off; on → Allowed if it
	 * was Off). Bots already seated leave at the table's next safe point (BOTS.md §6.2).
	 */
	static void syncTableBots(MinecraftServer server, String tableKey, boolean allowed) {
		TablePos.parse(tableKey).ifPresent(tp -> {
			ServerLevel level = level(server, tp.dimension());
			BlockPos pos = new BlockPos(tp.x(), tp.y(), tp.z());
			if (level == null || !level.isLoaded(pos)
					|| !(level.getBlockEntity(pos) instanceof dev.nezo.burmaldaholic.core.bots.BotTable bt) || bt.tableBots() == null) {
				return;
			}
			var tb = bt.tableBots();
			var cur = tb.limits();
			var mode = allowed ? (cur.botsMode() == dev.nezo.burmaldaholic.core.bots.logic.BotsMode.OFF
				? dev.nezo.burmaldaholic.core.bots.logic.BotsMode.ALLOWED : cur.botsMode()) : dev.nezo.burmaldaholic.core.bots.logic.BotsMode.OFF;
			if (mode != cur.botsMode()) {
				tb.setLimits(new dev.nezo.burmaldaholic.core.bots.logic.OwnerControls(mode, cur.hostMayChange(), cur.maxBots(), cur.allowPrivate()));
				level.getBlockEntity(pos).setChanged();
			}
		});
	}

	/** Bots module → charter: the owner saved a bots mode for an owned table. */
	static boolean setCharterBots(ServerLevel level, BlockPos pos, boolean allowed) {
		MinecraftServer server = level.getServer();
		Optional<TableRecord> rec = book(server).table(key(level, pos));
		if (rec.isEmpty() || rec.get().casinoId == null) {
			return false;
		}
		if (rec.get().bots != allowed) {
			rec.get().bots = allowed;
			data(server).setDirty();
		}
		return true;
	}

	static MultiplayerData data(MinecraftServer server) {
		return MultiplayerData.get(server);
	}

	static CasinoBook book(MinecraftServer server) {
		return data(server).book();
	}

	static boolean isOperator(ServerPlayer player) {
		return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
	}

	static @Nullable ServerLevel level(MinecraftServer server, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	static boolean isLoaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	static Economy.Transaction tx(String detail) {
		return new Economy.Transaction(ID, detail, Economy.Transaction.Kind.TRANSFER);
	}

	static Economy.BankrollInfo bankroll(MinecraftServer server, Casino c) {
		Economy.Bankrolls b = Economies.get().bankrolls(server);
		return b.get(c.bankrollId()).orElseGet(() -> b.open(c.bankrollId(), c.owner));
	}

	/** Highest VIP tier max: the cap for owner max bets. */
	static long globalMax() {
		return VipTiers.maxBet(VipTiers.NETHERITE);
	}

	static long day(MinecraftServer server) {
		return CasinoStats.dayOf(server.overworld().getGameTime());
	}

	static Component tableLabel(TableRecord t) {
		return t.descriptionId == null || t.descriptionId.isEmpty()
			? Component.translatable("gui.burmaldaholic.charter.tables") : Component.translatable(t.descriptionId);
	}

	// ---- core provider -----------------------------------------------------------------------

	/**
	 * {@code TableOwnershipProvider}: owned tables route bets/payouts through the owner's bankroll. Inactive tables
	 * (charter removed) and closed / insolvent casinos report {@code open = false}, so core refuses new bets.
	 */
	public static Optional<OwnedTable> owner(ServerLevel level, BlockPos pos) {
		if (!CasinoConfig.ownership().enabled) {
			return Optional.empty();
		}
		CasinoBook book = book(level.getServer());
		Optional<TableRecord> rec = book.table(key(level, pos));
		if (rec.isEmpty()) {
			return Optional.empty();
		}
		TableRecord t = rec.get();
		Optional<Casino> casino = book.casino(t.casinoId);
		if (casino.isEmpty()) {
			return Optional.of(new OwnedTable(t.owner, "", 0, 0, false));
		}
		Casino c = casino.get();
		return Optional.of(new OwnedTable(c.owner, c.bankrollId(), t.min, t.max, t.open && !c.broke, t.bots));
	}

	// ---- public claim lookups (see MultiplayerApi) ---------------------------------------------

	static Optional<Casino> casinoAt(ServerLevel level, BlockPos pos) {
		return book(level.getServer()).casinoAt(dim(level), pos.getX(), pos.getZ());
	}

	// ---- claim / charter ---------------------------------------------------------------------

	/** Why the player may not place a charter here (null = allowed). */
	public static @Nullable Component claimError(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!CasinoMode.isEnabled(level)) {
			return Component.translatable("gui.burmaldaholic.error.casino_off");
		}
		MinecraftServer server = level.getServer();
		var cfg = CasinoConfig.ownership();
		LevelData.RespawnData spawn = server.getRespawnData();
		int buffer = server instanceof DedicatedServer d ? Math.max(0, d.spawnProtectionRadius()) : Claims.DEFAULT_SPAWN_BUFFER;
		Claims.Rules rules = new Claims.Rules(cfg.enabled, cfg.claimRadius, cfg.maxPerPlayer,
			spawn.dimension().identifier().toString(), spawn.pos().getX(), spawn.pos().getZ(), buffer);
		switch (Claims.check(book(server).casinos(), player.getUUID(), dim(level), pos.getX(), pos.getZ(), rules)) {
			case DISABLED -> {
				return Component.translatable("msg.burmaldaholic.multiplayer.casinos_disabled");
			}
			case LIMIT -> {
				return Component.translatable("msg.burmaldaholic.multiplayer.charter_limit");
			}
			case OVERLAP -> {
				return Component.translatable("msg.burmaldaholic.multiplayer.charter_overlap");
			}
			case OK -> {
			}
		}
		long fee = cfg.licenseFee;
		if (Economies.get().balance(player) < fee) {
			return Component.translatable("msg.burmaldaholic.multiplayer.charter_fee_missing", Texts.chips(fee));
		}
		return null;
	}

	/** Charter placed: charge the license fee and create the casino (or undo the placement). */
	public static void claim(@Nullable ServerPlayer player, ServerLevel level, BlockPos pos) {
		Component error = player == null ? Component.translatable("gui.burmaldaholic.error.no_permission") : claimError(player, level, pos);
		long fee = CasinoConfig.ownership().licenseFee;
		if (error == null && fee > 0 && !Economies.get().tryWithdraw(player, fee, new Economy.Transaction(ID, "license_fee", Economy.Transaction.Kind.OTHER))) {
			error = Component.translatable("msg.burmaldaholic.multiplayer.charter_fee_missing", Texts.chips(fee));
		}
		if (error != null) {
			level.removeBlock(pos, false);
			if (player != null) {
				player.sendSystemMessage(error);
				if (!player.isCreative()) {
					Inventories.giveOrDrop(player, new ItemStack(MultiplayerModule.CHARTER_ITEM));
				}
			}
			return;
		}
		MinecraftServer server = level.getServer();
		MultiplayerData data = data(server);
		int radius = CasinoConfig.ownership().claimRadius;
		Casino c = data.book().create(player.getUUID(), player.getName().getString(), dim(level), pos.getX(), pos.getY(), pos.getZ(),
			radius, level.getGameTime());
		Economies.get().bankrolls(server).open(c.bankrollId(), c.owner);
		data.book().relinkInactive(c);
		data.setDirty();
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.multiplayer.charter_placed", Texts.chips(fee),
			Texts.plural("unit.burmaldaholic.block", radius)));
		Burmaldaholic.LOGGER.info("[multiplayer] casino {} claimed by {} at {} {}", c.id, c.ownerName, c.dimension, pos.toShortString());
		CasinoAdvancements.grant(player, "the_house");
		refreshSolvency(server, c, true);
	}

	public static void onCharterUse(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Optional<Casino> c = book(level.getServer()).charterAt(dim(level), pos.getX(), pos.getY(), pos.getZ());
		if (c.isEmpty()) {
			return;
		}
		if (!c.get().owner.equals(player.getUUID()) && !isOperator(player)) {
			player.sendOverlayMessage(Component.translatable("msg.burmaldaholic.multiplayer.charter_foreign", c.get().ownerName));
			return;
		}
		sendState(player, c.get(), true, null, false);
	}

	/** Charter removed: tables go inactive, the bankroll is paid out once all open rounds settled. */
	static void closeCasino(MinecraftServer server, Casino c, String reason) {
		MultiplayerData data = data(server);
		if (data.book().close(c.id).isEmpty()) {
			return;
		}
		data.setDirty();
		Burmaldaholic.LOGGER.info("[multiplayer] casino {} of {} closed ({})", c.id, c.ownerName, reason);
		processClosing(server);
	}

	static void processClosing(MinecraftServer server) {
		MultiplayerData data = data(server);
		Map<String, UUID> closing = data.book().closing();
		if (closing.isEmpty()) {
			return;
		}
		Economy eco = Economies.get();
		for (Map.Entry<String, UUID> e : new ArrayList<>(closing.entrySet())) {
			String bankrollId = Claims.bankrollId(e.getKey());
			UUID owner = e.getValue();
			Optional<Economy.BankrollInfo> info = eco.bankrolls(server).get(bankrollId);
			if (info.isPresent() && info.get().reserved() > 0) {
				continue; // open rounds still need their reservation
			}
			long amount = 0;
			if (info.isPresent()) {
				long balance = info.get().balance();
				if (balance > 0 && eco.transfer(server, AccountId.bankroll(bankrollId), AccountId.player(owner), balance, tx("charter_removed")).ok()) {
					amount = balance;
				}
				long rest = eco.bankrolls(server).close(bankrollId);
				if (rest > 0) {
					amount += eco.deposit(server, owner, rest, tx("charter_removed"));
				}
			}
			closing.remove(e.getKey());
			data.setDirty();
			ServerPlayer online = server.getPlayerList().getPlayer(owner);
			if (online != null) {
				online.sendSystemMessage(Component.translatable("msg.burmaldaholic.multiplayer.charter_removed", Texts.chips(amount)));
			} else {
				data.addNotice(owner, amount);
			}
		}
	}

	// ---- tables ------------------------------------------------------------------------------

	/** A block was placed by a player (mixin): the owner's casino tables inside their claim link automatically. */
	public static void onBlockPlaced(ServerPlayer player, ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		if (!enabled(server) || !(level.getBlockEntity(pos) instanceof CasinoTableBlockEntity table)) {
			return;
		}
		CasinoBook book = book(server);
		Optional<Casino> c = book.casinoAt(dim(level), pos.getX(), pos.getZ());
		if (c.isEmpty() || !c.get().owner.equals(player.getUUID())) {
			return;
		}
		if (link(book, c.get(), level, pos, table)) {
			data(server).setDirty();
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.multiplayer.table_linked",
				Component.translatable(table.getBlockState().getBlock().getDescriptionId())));
			refreshSolvency(server, c.get(), false);
		}
	}

	private static boolean link(CasinoBook book, Casino c, ServerLevel level, BlockPos pos, CasinoTableBlockEntity table) {
		return book.link(c, key(level, pos), table.gameId(), table.tableType().name(), table.getBlockState().getBlock().getDescriptionId());
	}

	/** "Link unlinked tables in range": the owner's inactive tables plus every table block in loaded chunks of the claim. */
	static int linkInRange(MinecraftServer server, Casino c) {
		CasinoBook book = book(server);
		int linked = book.relinkInactive(c);
		ServerLevel level = level(server, c.dimension);
		if (level != null) {
			int minCx = (c.x - c.radius) >> 4;
			int maxCx = (c.x + c.radius) >> 4;
			int minCz = (c.z - c.radius) >> 4;
			int maxCz = (c.z + c.radius) >> 4;
			int scanned = 0;
			for (int cx = minCx; cx <= maxCx; cx++) {
				for (int cz = minCz; cz <= maxCz && scanned < MAX_LINK_CHUNKS; cz++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
					scanned++;
					if (chunk == null) {
						continue;
					}
					for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
						BlockPos p = be.getBlockPos();
						if (be instanceof CasinoTableBlockEntity table && Claims.inClaim(c, c.dimension, p.getX(), p.getZ())
							&& link(book, c, level, p, table)) {
							linked++;
						}
					}
				}
			}
		}
		if (linked > 0) {
			data(server).setDirty();
			refreshSolvency(server, c, false);
		}
		return linked;
	}

	// ---- protection ----------------------------------------------------------------------------

	/** PlayerBlockBreakEvents.BEFORE: only the owner (and operators) break the charter and linked tables. */
	static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp) || !CasinoMode.isEnabled(serverLevel)) {
			return true;
		}
		CasinoBook book = book(serverLevel.getServer());
		if (state.is(MultiplayerModule.CHARTER)) {
			Optional<Casino> c = book.charterAt(dim(level), pos.getX(), pos.getY(), pos.getZ());
			if (c.isPresent() && !Claims.mayBreak(c.get().owner.equals(sp.getUUID()), isOperator(sp), true, true)) {
				sp.sendOverlayMessage(Component.translatable("msg.burmaldaholic.multiplayer.charter_foreign", c.get().ownerName));
				return false;
			}
			return true;
		}
		if (be instanceof CasinoTableBlockEntity) {
			Optional<Casino> c = book.activeCasinoOf(key(level, pos));
			if (c.isPresent() && !Claims.mayBreak(c.get().owner.equals(sp.getUUID()), isOperator(sp), CasinoConfig.ownership().protectTables, false)) {
				sp.sendOverlayMessage(Component.translatable("msg.burmaldaholic.multiplayer.table_protected", c.get().ownerName));
				return false;
			}
		}
		return true;
	}

	/** PlayerBlockBreakEvents.AFTER: breaking the charter closes the casino; broken tables are forgotten. */
	static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		MinecraftServer server = serverLevel.getServer();
		CasinoBook book = book(server);
		if (state.is(MultiplayerModule.CHARTER)) {
			book.charterAt(dim(level), pos.getX(), pos.getY(), pos.getZ())
				.ifPresent(c -> closeCasino(server, c, "charter broken by " + player.getName().getString()));
			return;
		}
		Optional<TableRecord> removed = book.removeTable(key(level, pos));
		if (removed.isPresent()) {
			data(server).setDirty();
			book.casino(removed.get().casinoId).ifPresent(c -> refreshSolvency(server, c, false));
		}
	}

	/** Explosion mixin: charters and linked tables of live casinos don't get destroyed. */
	public static boolean isExplosionProof(ServerLevel level, BlockPos pos) {
		if (!CasinoMode.isEnabled(level) || !CasinoConfig.ownership().explosionProof) {
			return false;
		}
		CasinoBook book = book(level.getServer());
		if (book.tables().isEmpty() && book.casinos().isEmpty()) {
			return false;
		}
		return book.activeCasinoOf(key(level, pos)).isPresent() || book.charterAt(dim(level), pos.getX(), pos.getY(), pos.getZ()).isPresent();
	}

	/**
	 * UseBlockCallback: refuse opening an owned table that takes no bets (inactive, closed, insolvent) with the
	 * reason; players already seated / with an open stake may always reopen it to finish their round.
	 */
	static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp) || !enabled(serverLevel.getServer())) {
			return InteractionResult.PASS;
		}
		if (player.isSecondaryUseActive() && !player.getItemInHand(hand).isEmpty()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!(level.getBlockEntity(pos) instanceof CasinoTableBlockEntity table)) {
			return InteractionResult.PASS;
		}
		CasinoBook book = book(serverLevel.getServer());
		String key = key(level, pos);
		Optional<TableRecord> rec = book.table(key);
		if (rec.isEmpty()) {
			return InteractionResult.PASS;
		}
		RECENT.put(sp.getUUID(), new Recent(key, serverLevel.getGameTime()));
		if (table.isSeated(sp) || table.stakeOf(sp.getUUID()) > 0) {
			return InteractionResult.PASS;
		}
		Optional<Casino> c = book.casino(rec.get().casinoId);
		Component refusal = null;
		if (c.isEmpty()) {
			refusal = Component.translatable("msg.burmaldaholic.multiplayer.table_inactive");
		} else if (!c.get().owner.equals(sp.getUUID())) {
			if (!rec.get().open) {
				refusal = Component.translatable("gui.burmaldaholic.error.table_closed");
			} else if (c.get().broke) {
				refusal = Component.translatable("gui.burmaldaholic.error.house_broke");
			}
		}
		if (refusal != null) {
			sp.sendOverlayMessage(refusal);
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	// ---- money / solvency ----------------------------------------------------------------------

	/** Re-evaluates the §18.2 insolvency rule; notifies the owner on a change (unless quiet). */
	static void refreshSolvency(MinecraftServer server, Casino c, boolean quiet) {
		long balance = bankroll(server, c).balance();
		boolean broke = Solvency.isBroke(balance, book(server).exposure(c.id));
		if (broke == c.broke) {
			return;
		}
		c.broke = broke;
		data(server).setDirty();
		if (broke) {
			CasinoAdvancements.grant(server, c.owner, "bankrupt");
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
		if (!quiet && owner != null) {
			owner.sendSystemMessage(Component.translatable(broke ? "msg.burmaldaholic.multiplayer.casino_broke" : "msg.burmaldaholic.multiplayer.casino_reopened"));
		}
	}

	/** The owned table a player is (or was just) playing at. */
	private static Optional<String> tableOf(ServerPlayer player) {
		if (player.containerMenu instanceof CasinoTableMenu menu) {
			String key = key(player.level(), menu.pos());
			RECENT.put(player.getUUID(), new Recent(key, player.level().getGameTime()));
			return Optional.of(key);
		}
		Recent r = RECENT.get(player.getUUID());
		if (r != null && player.level().getGameTime() - r.tick() <= RECENT_TABLE_TICKS) {
			return Optional.of(r.key());
		}
		return Optional.empty();
	}

	/** PLAY_RESOLVED: statistics of the casino whose table the round was played at (the result names the table). */
	static void onPlayResolved(ServerPlayer player, CasinoEvents.PlayResult result) {
		MinecraftServer server = player.level().getServer();
		if (!enabled(server)) {
			return;
		}
		if (!result.ownedCasino() && result.table() != null) {
			return; // world-bank round at a known table: not an owned casino
		}
		Optional<String> key = result.table() != null
			? Optional.of(new TablePos(result.table().dimension().identifier().toString(), result.table().pos().getX(),
				result.table().pos().getY(), result.table().pos().getZ()).key())
			: tableOf(player);
		if (key.isEmpty()) {
			return;
		}
		CasinoBook book = book(server);
		Optional<TableRecord> rec = book.table(key.get());
		Optional<Casino> c = book.activeCasinoOf(key.get());
		if (rec.isEmpty() || c.isEmpty() || (!rec.get().game.isEmpty() && !rec.get().game.equals(result.gameId()))) {
			return;
		}
		c.get().stats.recordRound(day(server), result.bet(), result.payout());
		data(server).setDirty();
		checkProfit(server, c.get());
		refreshSolvency(server, c.get(), false);
	}

	/** §19 house_always_wins: the owned casino's total net profit reached 10 000. */
	static void checkProfit(MinecraftServer server, Casino c) {
		if (c.stats.total().profit() >= CasinoAdvancements.HOUSE_PROFIT) {
			CasinoAdvancements.grant(server, c.owner, "house_always_wins");
		}
	}

	/** Core RAKE_COLLECTED: poker rake paid into an owned casino's bankroll (statistics). */
	static void onRake(ServerLevel level, BlockPos pos, long rake, String bankroll) {
		if (!bankroll.isEmpty() && rake > 0) {
			recordRake(level, pos, rake);
		}
	}

	/** Poker rake paid into a bankroll (for games that report it; see MultiplayerApi#recordRake). */
	static void recordRake(ServerLevel level, BlockPos pos, long rake) {
		MinecraftServer server = level.getServer();
		book(server).activeCasinoOf(key(level, pos)).ifPresent(c -> {
			c.stats.recordRake(day(server), rake);
			data(server).setDirty();
			checkProfit(server, c);
		});
	}

	// ---- ticking -----------------------------------------------------------------------------

	static void onJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		MultiplayerData data = data(server);
		long notice = data.takeNotice(player.getUUID());
		if (notice >= 0) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.multiplayer.charter_removed", Texts.chips(notice)));
		}
		for (Casino c : data.book().ownedBy(player.getUUID())) {
			String name = player.getName().getString();
			if (!name.equals(c.ownerName)) {
				c.ownerName = name;
				data.setDirty();
			}
			if (c.broke && CasinoMode.isEnabled(server)) {
				player.sendSystemMessage(Component.translatable("msg.burmaldaholic.multiplayer.casino_broke"));
			}
		}
	}

	static void onLeave(ServerPlayer player) {
		RECENT.remove(player.getUUID());
		INSIDE.remove(player.getUUID());
	}

	static void tick(MinecraftServer server) {
		if (!enabled(server)) {
			return;
		}
		int t = server.getTickCount();
		if (t % 20 == 0) {
			tickPlayers(server);
			CasinoBook book = book(server);
			for (Casino c : new ArrayList<>(book.casinos())) {
				refreshSolvency(server, c, false);
			}
		}
		if (t % 100 == 50) {
			sweep(server);
		}
	}

	/** Closes casinos whose charter vanished, forgets tables whose block vanished, refreshes game minimums. */
	private static void sweep(MinecraftServer server) {
		processClosing(server);
		MultiplayerData data = data(server);
		CasinoBook book = data.book();
		for (Casino c : new ArrayList<>(book.casinos())) {
			ServerLevel level = level(server, c.dimension);
			BlockPos pos = new BlockPos(c.x, c.y, c.z);
			if (level != null && isLoaded(level, pos) && !level.getBlockState(pos).is(MultiplayerModule.CHARTER)) {
				closeCasino(server, c, "charter block missing");
			}
		}
		ServerPlayer anyone = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().getFirst();
		for (Map.Entry<String, TableRecord> e : new ArrayList<>(book.tables().entrySet())) {
			Optional<TablePos> tp = TablePos.parse(e.getKey());
			if (tp.isEmpty()) {
				continue;
			}
			ServerLevel level = level(server, tp.get().dimension());
			BlockPos pos = new BlockPos(tp.get().x(), tp.get().y(), tp.get().z());
			if (level == null || !isLoaded(level, pos)) {
				continue;
			}
			if (!(level.getBlockEntity(pos) instanceof CasinoTableBlockEntity table)) {
				book.removeTable(e.getKey());
				data.setDirty();
				continue;
			}
			if (anyone != null) {
				// limitsFor = max(game min, owner min): it is the game's own minimum unless the owner's setting decides it.
				long min = Math.max(1, table.limitsFor(anyone)[0]);
				TableRecord rec = e.getValue();
				if ((rec.min <= 0 || min > rec.min) && min != rec.gameMin) {
					rec.gameMin = min;
					data.setDirty();
				}
			}
		}
	}

	private static void tickPlayers(MinecraftServer server) {
		CasinoBook book = book(server);
		double spectatorRadius = CasinoConfig.multiplayer().spectatorRadius;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.containerMenu instanceof CasinoTableMenu) {
				tableOf(p); // refresh "recent"
			}
			Optional<Casino> c = book.casinoAt(dim(p.level()), p.getBlockX(), p.getBlockZ());
			String now = c.map(x -> x.id).orElse(null);
			String before = INSIDE.get(p.getUUID());
			if (!java.util.Objects.equals(now, before)) {
				if (now == null) {
					INSIDE.remove(p.getUUID());
				} else {
					INSIDE.put(p.getUUID(), now);
					if (!c.get().owner.equals(p.getUUID())) {
						p.sendOverlayMessage(Component.translatable("msg.burmaldaholic.multiplayer.entered_casino", c.get().ownerName));
						continue;
					}
				}
			}
			if (spectatorRadius > 0 && server.getTickCount() % 40 == 0 && p.containerMenu == p.inventoryMenu && !p.isSpectator()) {
				spectate(p, spectatorRadius);
			}
		}
	}

	/** §18.1 spectators: looking at a table with players within the spectator radius shows a public summary. */
	private static void spectate(ServerPlayer p, double radius) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1.0F).scale(radius));
		BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.BLOCK || !(p.level().getBlockEntity(hit.getBlockPos()) instanceof CasinoTableBlockEntity table)) {
			return;
		}
		TableSeats seats = table.seats();
		if (seats.size() <= 1 || seats.isEmpty() || seats.isSeated(p.getUUID())) {
			return;
		}
		long inPlay = table.openStakes().stream().mapToLong(CasinoTableBlockEntity.OpenStake::amount).sum();
		p.sendOverlayMessage(Component.translatable("gui.burmaldaholic.multiplayer.spectate",
			Component.translatable(table.getBlockState().getBlock().getDescriptionId()),
			Texts.number(seats.occupied().size()), Texts.number(seats.size()), Texts.chips(inPlay)));
	}

	// ---- charter screen --------------------------------------------------------------------------

	static void sendState(ServerPlayer player, Casino c, boolean open, @Nullable Component message, boolean error) {
		ServerPlayNetworking.send(player, new CharterStatePayload(state(player, c), open, Optional.ofNullable(message), error));
	}

	private static CompoundTag state(ServerPlayer player, Casino c) {
		MinecraftServer server = player.level().getServer();
		Economy.BankrollInfo b = bankroll(server, c);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", c.id);
		tag.putString("owner_name", c.ownerName);
		tag.putBoolean("is_owner", c.owner.equals(player.getUUID()));
		tag.putLong("bankroll", b.balance());
		tag.putLong("reserved", b.reserved());
		tag.putLong("available", Solvency.withdrawable(b.balance(), b.reserved()));
		tag.putBoolean("broke", c.broke);
		tag.putInt("radius", c.radius);
		tag.putInt("x", c.x);
		tag.putInt("y", c.y);
		tag.putInt("z", c.z);
		tag.putLong("balance", Economies.get().balance(player));
		tag.putLong("global_max", globalMax());
		c.stats.roll(day(server));
		tag.put("today", tally(c.stats.today()));
		tag.put("total", tally(c.stats.total()));
		ListTag list = new ListTag();
		for (Map.Entry<String, TableRecord> e : book(server).tablesOf(c.id)) {
			TableRecord t = e.getValue();
			CompoundTag row = new CompoundTag();
			row.putString("key", e.getKey());
			row.putString("desc", t.descriptionId == null ? "" : t.descriptionId);
			TablePos.parse(e.getKey()).ifPresent(p -> {
				row.putInt("x", p.x());
				row.putInt("y", p.y());
				row.putInt("z", p.z());
			});
			row.putBoolean("open", t.open);
			row.putLong("min", t.min);
			row.putLong("max", t.max);
			row.putBoolean("bots", t.bots);
			row.putBoolean("poker", "poker".equals(t.game));
			list.add(row);
		}
		tag.put("tables", list);
		return tag;
	}

	private static CompoundTag tally(CasinoStats.Tally t) {
		CompoundTag tag = new CompoundTag();
		tag.putLong("handle", t.handle);
		tag.putLong("paid", t.paid);
		tag.putLong("rake", t.rake);
		tag.putLong("rounds", t.rounds);
		tag.putLong("profit", t.profit());
		return tag;
	}

	/** Charter screen action (C2S). Only the owner moves bankroll chips; operators may view and edit tables. */
	static void handleAction(CharterActionPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		MinecraftServer server = player.level().getServer();
		Optional<Casino> found = book(server).casino(payload.casinoId());
		if (found.isEmpty()) {
			return;
		}
		Casino c = found.get();
		boolean owner = c.owner.equals(player.getUUID());
		if (!owner && !isOperator(player)) {
			return;
		}
		CompoundTag args = payload.args();
		Component msg = null;
		boolean error = false;
		switch (payload.action()) {
			case "deposit" -> {
				long amount = args.getLongOr("amount", 0);
				if (!owner) {
					msg = Component.translatable("gui.burmaldaholic.error.no_permission");
					error = true;
				} else if (amount <= 0) {
					msg = Component.translatable("gui.burmaldaholic.error.invalid_amount");
					error = true;
				} else if (!Economies.get().transfer(server, AccountId.player(player.getUUID()), AccountId.bankroll(c.bankrollId()), amount, tx("bankroll_deposit")).ok()) {
					msg = Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player)));
					error = true;
				} else {
					msg = Component.translatable("msg.burmaldaholic.multiplayer.bankroll_deposit", Texts.chips(amount), Texts.chips(bankroll(server, c).balance()));
					refreshSolvency(server, c, false);
				}
			}
			case "withdraw" -> {
				long amount = args.getLongOr("amount", 0);
				Economy.BankrollInfo b = bankroll(server, c);
				long available = Solvency.withdrawable(b.balance(), b.reserved());
				if (!owner) {
					msg = Component.translatable("gui.burmaldaholic.error.no_permission");
					error = true;
				} else if (amount <= 0) {
					msg = Component.translatable("gui.burmaldaholic.error.invalid_amount");
					error = true;
				} else if (amount > available) {
					msg = Component.translatable("msg.burmaldaholic.multiplayer.bankroll_reserved", Texts.chips(available));
					error = true;
				} else if (!Economies.get().transfer(server, AccountId.bankroll(c.bankrollId()), AccountId.player(player.getUUID()), amount, tx("bankroll_withdraw")).ok()) {
					msg = Component.translatable("msg.burmaldaholic.multiplayer.bankroll_reserved", Texts.chips(available));
					error = true;
				} else {
					msg = Component.translatable("msg.burmaldaholic.multiplayer.bankroll_withdraw", Texts.chips(amount), Texts.chips(bankroll(server, c).balance()));
					refreshSolvency(server, c, false);
				}
			}
			case "table" -> {
				String key = args.getStringOr("key", "");
				Optional<TableRecord> rec = book(server).table(key);
				if (rec.isEmpty() || !c.id.equals(rec.get().casinoId)) {
					break;
				}
				OwnerLimits.Parsed limits = OwnerLimits.validate(args.getLongOr("min", 0), args.getLongOr("max", 0), globalMax());
				if (!limits.ok()) {
					msg = switch (limits.error()) {
						case MIN_OVER_MAX -> Component.translatable("gui.burmaldaholic.multiplayer.error_min_max");
						case OVER_GLOBAL -> Component.translatable("gui.burmaldaholic.multiplayer.error_over_global", Texts.chips(globalMax()));
						default -> Component.translatable("gui.burmaldaholic.error.invalid_amount");
					};
					error = true;
					break;
				}
				TableRecord t = rec.get();
				t.open = args.getBooleanOr("open", t.open);
				t.min = limits.min();
				t.max = limits.max();
				boolean botsBefore = t.bots;
				t.bots = args.getBooleanOr("bots", t.bots);
				data(server).setDirty();
				if (botsBefore != t.bots) {
					syncTableBots(server, key, t.bots);
				}
				msg = Component.translatable("msg.burmaldaholic.multiplayer.table_saved", tableLabel(t));
				refreshSolvency(server, c, false);
			}
			case "link" -> {
				int n = linkInRange(server, c);
				msg = n > 0 ? Component.translatable("msg.burmaldaholic.multiplayer.linked_count", Texts.number(n))
					: Component.translatable("msg.burmaldaholic.multiplayer.linked_none");
			}
			default -> {
			}
		}
		sendState(player, c, false, msg, error);
	}

	/** Casinos currently owned by a player (e.g. for a "My Casino" menu entry). */
	static List<Casino> ownedBy(MinecraftServer server, UUID player) {
		return book(server).ownedBy(player);
	}
}
