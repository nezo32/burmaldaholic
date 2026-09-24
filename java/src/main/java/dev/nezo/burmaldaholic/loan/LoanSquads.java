package dev.nezo.burmaldaholic.loan;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.LoanConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import dev.nezo.burmaldaholic.loan.entity.AccountantEntity;
import dev.nezo.burmaldaholic.loan.entity.SquadMob;
import dev.nezo.burmaldaholic.loan.logic.Dialogue;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord.Status;
import dev.nezo.burmaldaholic.loan.logic.LoanRules;
import dev.nezo.burmaldaholic.loan.logic.SpawnSearch;
import dev.nezo.burmaldaholic.loan.logic.SquadRules;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Choice;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Effect;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.EndReason;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Machine;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.State;
import dev.nezo.burmaldaholic.loan.logic.SquadRules.Unit;
import dev.nezo.burmaldaholic.loan.net.LoanActionPayload;
import dev.nezo.burmaldaholic.loan.net.LoanUiPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Debt Collector squads (GAME_DESIGN.md §5.5, §5.7, §5.8): wave scheduling per debtor, safe spawning
 * 24–40 blocks away, the {@link SquadRules} state machine driven every 10 ticks, the negotiation dialog,
 * Accountant "Audit" side effects, Repossession on the debtor's death and PAID when the debt hits 0.
 * Squads live in memory; after a restart their members remove themselves (the next wave comes at the
 * next MCD boundary as scheduled in the debtor's record).
 */
public final class LoanSquads {
	private static final Map<UUID, Squad> SQUADS = new LinkedHashMap<>();
	private static final int RETRY_TICKS = 600;
	private static final double SPEECH_RANGE = 24;
	/** Owned casinos: no spawn within this many blocks of another player's owned table. */
	private static final int CLAIM_RADIUS = 16;

	private static final class Squad {
		final UUID id = UUID.randomUUID();
		final UUID debtor;
		final ResourceKey<Level> dimension;
		/** leader first */
		final List<UUID> members = new ArrayList<>();
		final Map<UUID, Unit> units = new LinkedHashMap<>();
		final Machine machine;
		boolean attacked;
		boolean debtorDead;

		Squad(UUID debtor, ResourceKey<Level> dimension, long now) {
			this.debtor = debtor;
			this.dimension = dimension;
			this.machine = new Machine(now);
		}
	}

	private LoanSquads() {}

	public static @Nullable State stateOf(UUID squadId) {
		Squad s = SQUADS.get(squadId);
		return s == null ? null : s.machine.state;
	}

	private static @Nullable Squad squadOf(UUID debtor) {
		for (Squad s : SQUADS.values()) {
			if (s.debtor.equals(debtor) && s.machine.state != State.DONE) {
				return s;
			}
		}
		return null;
	}

	public static boolean hasLiveSquad(UUID debtor) {
		return squadOf(debtor) != null;
	}

	static void clear() {
		SQUADS.clear();
	}

	// ---- scheduling -----------------------------------------------------------------------------

	/** Called every second per online defaulting player (after the loan tick) in collectors mode. */
	static void maybeSpawn(ServerPlayer player, LoanRecord rec, long now) {
		if (!SquadRules.waveDue(now, rec.nextWaveTick, hasLiveSquad(player.getUUID()))) {
			return;
		}
		if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
			SquadRules.waveRetry(rec, now, RETRY_TICKS);
		} else if (spawnWave(player, rec.owed, rec.wave + 1)) {
			SquadRules.waveSpawned(rec, now);
		} else {
			SquadRules.waveRetry(rec, now, RETRY_TICKS);
		}
		LoanData.get(player.level().getServer()).setDirty();
	}

	/** Debtor joined: a queued / missed wave comes {@code offlineWaveDelayTicks} later (max one). */
	static void onJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (!LoanService.active(server)) {
			return;
		}
		LoanData data = LoanData.get(server);
		LoanRecord rec = data.peek(player.getUUID());
		if (rec == null || rec.status != Status.DEFAULT || !LoanService.collectorsMode(server)) {
			return;
		}
		if (SquadRules.onDebtorJoin(rec, LoanService.now(server), CasinoConfig.loan().offlineWaveDelayTicks)) {
			data.setDirty();
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.loan.wave_queued").withStyle(ChatFormatting.YELLOW));
		}
	}

	/** Operator: the next wave spawns now (player must be in default). */
	static boolean forceWave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		LoanRecord rec = LoanService.record(server, player.getUUID());
		if (rec.status != Status.DEFAULT || hasLiveSquad(player.getUUID())) {
			return false;
		}
		rec.nextWaveTick = Math.max(1, LoanService.now(server));
		LoanData.get(server).setDirty();
		LoanService.tick(player);
		return hasLiveSquad(player.getUUID());
	}

	// ---- spawning -------------------------------------------------------------------------------

	private static double healthMultiplier(MinecraftServer server) {
		LoanConfig c = CasinoConfig.loan();
		return switch (LoanService.band(server)) {
			case EASY -> c.collectorHealthMultiplier.easy;
			case NORMAL -> 1.0;
			case HARD -> c.collectorHealthMultiplier.hard;
		};
	}

	private static EntityType<? extends SquadMob> typeOf(Unit u) {
		return switch (u) {
			case DEBT_COLLECTOR -> LoanContent.DEBT_COLLECTOR;
			case REPO_MAN -> LoanContent.REPO_MAN;
			case ACCOUNTANT -> LoanContent.ACCOUNTANT;
			case ENFORCER -> LoanContent.ENFORCER;
		};
	}

	/** Spawns a wave around the debtor. False = no safe spot for every member (retry later). */
	static boolean spawnWave(ServerPlayer player, long owed, int wave) {
		ServerLevel level = (ServerLevel) player.level();
		MinecraftServer server = level.getServer();
		AABB boss = player.getBoundingBox().inflate(128);
		if (!level.getEntitiesOfClass(EnderDragon.class, boss, e -> true).isEmpty()
			|| !level.getEntitiesOfClass(WitherBoss.class, boss, e -> true).isEmpty()) {
			return false; // never inside a boss fight (§13.4)
		}
		LoanConfig cfg = CasinoConfig.loan();
		List<Unit> units = SquadRules.members(SquadRules.composition(owed, wave, LoanService.band(server), cfg.squadMax, cfg.escalationMax));
		SpawnSearch.Rules rules = SpawnSearch.Rules.standard(level.getMinY(), level.getMaxY() + 1);
		SpawnSearch.CellReader cells = (x, y, z) -> classify(level, new BlockPos(x, y, z));
		SpawnSearch.ColumnFilter filter = (x, y, z) -> allowed(level, new BlockPos(x, y, z), player.getUUID());
		List<SpawnSearch.Spot> spots = new ArrayList<>();
		for (int i = 0; i < units.size(); i++) {
			SpawnSearch.Spot spot = SpawnSearch.find(level.getRandom()::nextDouble, player.getX(), player.getBlockY(), player.getZ(), cells, filter, rules);
			if (spot == null) {
				return false;
			}
			spots.add(spot);
		}
		long now = LoanService.now(server);
		Squad squad = new Squad(player.getUUID(), level.dimension(), now);
		double mult = healthMultiplier(server);
		for (int i = 0; i < units.size(); i++) {
			Unit u = units.get(i);
			SpawnSearch.Spot spot = spots.get(i);
			SquadMob mob = typeOf(u).create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			BlockPos pos = new BlockPos(spot.x(), spot.y(), spot.z());
			mob.snapTo(spot.x() + 0.5, spot.y(), spot.z() + 0.5, level.getRandom().nextFloat() * 360F, 0);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
			mob.joinSquad(squad.id, player.getUUID());
			double hp = SquadRules.scaledHealth(u, mult);
			var attr = mob.getAttribute(Attributes.MAX_HEALTH);
			if (attr != null) {
				attr.setBaseValue(hp);
			}
			mob.setHealth((float) hp);
			if (level.addFreshEntity(mob)) {
				squad.members.add(mob.getUUID());
				squad.units.put(mob.getUUID(), u);
			}
		}
		if (squad.members.isEmpty()) {
			return false;
		}
		SQUADS.put(squad.id, squad);
		LoanService.title(player, Component.translatable("msg.burmaldaholic.loan.wave_title").withStyle(ChatFormatting.RED),
			Component.translatable("msg.burmaldaholic.loan.wave_subtitle"));
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.loan.wave_incoming", Texts.chips(owed)).withStyle(ChatFormatting.RED));
		LoanService.sound(player, dev.nezo.burmaldaholic.core.CoreSounds.COLLECTOR_KNOCK, 0.6F);
		LoanModule.LOG.info("Debt collector wave {} ({} members) for {}, owed {}", wave, squad.members.size(), player.getName().getString(), owed);
		return true;
	}

	/** Block classifier for the spawn search (null = unloaded / outside the border). */
	private static SpawnSearch.@Nullable Cell classify(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return null;
		}
		BlockState s = level.getBlockState(pos);
		if (!s.getFluidState().isEmpty() || s.is(BlockTags.FIRE) || s.is(Blocks.POWDER_SNOW)) {
			return SpawnSearch.Cell.LIQUID;
		}
		if (s.isAir()) {
			return SpawnSearch.Cell.AIR;
		}
		if (s.getCollisionShape(level, pos).isEmpty()) {
			return SpawnSearch.Cell.AIR; // grass, flowers: free head room, but nothing to stand on
		}
		if (s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE)) {
			return SpawnSearch.Cell.PASSABLE;
		}
		return s.isFaceSturdy(level, pos, Direction.UP) ? SpawnSearch.Cell.SOLID : SpawnSearch.Cell.PASSABLE;
	}

	/** Inside the world border and not inside another player's owned casino (§5.8.4). */
	private static boolean allowed(ServerLevel level, BlockPos feet, UUID debtor) {
		if (!level.getWorldBorder().isWithinBounds(feet)) {
			return false;
		}
		// Casino claims (core claim provider, multiplayer): never spawn in someone else's casino.
		if (CoreServices.claims().ownerAt(level, feet).filter(o -> !o.equals(debtor)).isPresent()) {
			return false;
		}
		int cx = feet.getX() >> 4;
		int cz = feet.getZ() >> 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof CasinoTableBlockEntity && be.getBlockPos().closerThan(feet, CLAIM_RADIUS)) {
						boolean foreign = CoreServices.tableOwnership().owner(level, be.getBlockPos()).filter(o -> !o.owner().equals(debtor)).isPresent();
						if (foreign) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	// ---- runtime --------------------------------------------------------------------------------

	private static List<SquadMob> alive(ServerLevel level, Squad s) {
		List<SquadMob> out = new ArrayList<>();
		if (level == null) {
			return out;
		}
		for (UUID id : s.members) {
			if (level.getEntity(id) instanceof SquadMob mob && mob.isAlive()) {
				out.add(mob);
			}
		}
		return out;
	}

	/** Every 10 ticks. */
	static void tick(MinecraftServer server) {
		boolean active = LoanService.active(server);
		long now = LoanService.now(server);
		for (Squad s : new ArrayList<>(SQUADS.values())) {
			if (s.machine.state == State.DONE) {
				SQUADS.remove(s.id);
				continue;
			}
			ServerLevel level = server.getLevel(s.dimension);
			ServerPlayer debtor = server.getPlayerList().getPlayer(s.debtor);
			if (!active || level == null) {
				s.machine.state = State.DONE;
				despawn(server, s, level, EndReason.LEFT, debtor);
				continue;
			}
			List<SquadMob> alive = alive(level, s);
			boolean same = debtor != null && debtor.level() == level;
			double leaderDist = Double.NaN;
			double nearest = Double.NaN;
			if (same) {
				if (!alive.isEmpty()) {
					leaderDist = alive.get(0).distanceTo(debtor);
				}
				for (SquadMob m : alive) {
					double d = m.distanceTo(debtor);
					nearest = Double.isNaN(nearest) ? d : Math.min(nearest, d);
				}
			}
			long owed = debtor != null ? LoanService.record(server, s.debtor).owed : 1;
			SquadRules.Observation o = new SquadRules.Observation(now, debtor != null, same, s.debtorDead, leaderDist, nearest,
				s.members.size(), owed, s.attacked);
			s.attacked = false;
			List<Effect> fx = SquadRules.step(s.machine, o, timers());
			apply(server, s, fx);
		}
	}

	private static SquadRules.Timers timers() {
		LoanConfig c = CasinoConfig.loan();
		SquadRules.Timers d = SquadRules.Timers.DEFAULT;
		return new SquadRules.Timers(c.approachTicks, c.negotiateTicks, c.hostileTicks, d.leaveTicks(), d.farDistance(), d.farTicks(), d.negotiateDistance());
	}

	/** Applies state effects: dialogue, negotiation dialog, despawn, schedule changes. */
	private static void apply(MinecraftServer server, Squad s, List<Effect> effects) {
		if (effects.isEmpty()) {
			return;
		}
		ServerLevel level = server.getLevel(s.dimension);
		ServerPlayer debtor = server.getPlayerList().getPlayer(s.debtor);
		List<SquadMob> alive = alive(level, s);
		SquadMob leader = alive.isEmpty() ? null : alive.get(0);
		for (Effect fx : effects) {
			switch (fx.type()) {
				case NEGOTIATE -> {
					if (debtor != null) {
						openNegotiation(server, s, debtor, leader);
					}
				}
				case HOSTILE -> {
					closeNegotiation(debtor);
					say(leader, debtor, Component.translatable(Dialogue.pick(Dialogue.HOSTILE, server.overworld().getRandom()::nextInt)));
					alive.stream().filter(m -> m.unit() == Unit.ENFORCER).findFirst().ifPresent(big ->
						say(big, debtor, Component.translatable(Dialogue.pick(Dialogue.ENFORCER, server.overworld().getRandom()::nextInt))));
					if (debtor != null) {
						debtor.sendOverlayMessage(Component.translatable("msg.burmaldaholic.loan.wave_subtitle").withStyle(ChatFormatting.RED));
					}
				}
				case LEAVE -> {
					closeNegotiation(debtor);
					for (SquadMob m : alive) {
						m.setTarget(null);
						m.setAggressive(false);
					}
					if (fx.reason() == EndReason.PAID) {
						say(leader, debtor, Component.translatable(Dialogue.pick(Dialogue.PAID, server.overworld().getRandom()::nextInt)));
						if (debtor != null) {
							debtor.sendSystemMessage(Component.translatable("msg.burmaldaholic.loan.paid_in_full_collectors").withStyle(ChatFormatting.GREEN));
						}
					} else if (fx.reason() == EndReason.PARTIAL) {
						say(leader, debtor, Component.translatable(Dialogue.pick(Dialogue.PARTIAL, server.overworld().getRandom()::nextInt)));
					}
				}
				case DESPAWN -> despawn(server, s, level, fx.reason(), debtor);
			}
		}
	}

	private static void despawn(MinecraftServer server, Squad s, @Nullable ServerLevel level, EndReason reason, @Nullable ServerPlayer debtor) {
		for (SquadMob m : alive(level, s)) {
			level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1, m.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
			m.discard();
		}
		closeNegotiation(debtor);
		if (reason == EndReason.DEFEATED) {
			CasinoAdvancements.grant(server, s.debtor, "hostile_takeover");
			if (debtor != null) {
				debtor.sendSystemMessage(Component.translatable("msg.burmaldaholic.loan.squad_defeated").withStyle(ChatFormatting.YELLOW));
			}
		}
		if (reason == EndReason.OFFLINE) {
			LoanRecord rec = LoanService.record(server, s.debtor);
			SquadRules.waveQueued(rec);
			LoanData.get(server).setDirty();
		}
		SQUADS.remove(s.id);
	}

	/** A squad member speaks: the debtor and players within 24 blocks of the speaker hear it. */
	private static void say(@Nullable SquadMob who, @Nullable ServerPlayer debtor, Component line) {
		if (who == null) {
			return;
		}
		Component msg = LoanShark.speech(who.getName(), line).withStyle(ChatFormatting.RED);
		if (debtor != null) {
			debtor.sendSystemMessage(msg);
		}
		for (Player p : who.level().players()) {
			if (p != debtor && p instanceof ServerPlayer sp && p.distanceTo(who) <= SPEECH_RANGE) {
				sp.sendSystemMessage(msg);
			}
		}
	}

	public static void auditLine(AccountantEntity accountant) {
		ServerPlayer debtor = accountant.debtor();
		say(accountant, debtor, Component.translatable(Dialogue.pick(Dialogue.AUDIT, accountant.getRandom()::nextInt)));
	}

	// ---- negotiation ----------------------------------------------------------------------------

	private static void openNegotiation(MinecraftServer server, Squad s, ServerPlayer debtor, @Nullable SquadMob leader) {
		long owed = LoanService.record(server, s.debtor).owed;
		long part = SquadRules.partialPayment(owed, CasinoConfig.loan().partialPaymentMin);
		long balance = Economies.get().balance(debtor);
		Component line = Component.translatable(Dialogue.pick(Dialogue.DEMAND, server.overworld().getRandom()::nextInt), Texts.chips(owed));
		say(leader, debtor, line);
		CompoundTag t = new CompoundTag();
		LoanShark.putComponent(server, t, "line", leader == null ? line : LoanShark.speech(leader.getName(), line));
		t.putLong("owed", owed);
		t.putLong("part", part);
		t.putLong("balance", balance);
		t.putInt("ticks", CasinoConfig.loan().negotiateTicks);
		t.putBoolean("can_all", balance >= owed);
		t.putBoolean("can_part", part < owed && balance >= part);
		ServerPlayNetworking.send(debtor, new LoanUiPayload(LoanUiPayload.NEGOTIATE, true, t));
	}

	private static void closeNegotiation(@Nullable ServerPlayer debtor) {
		if (debtor != null) {
			ServerPlayNetworking.send(debtor, new LoanUiPayload(LoanUiPayload.NEGOTIATE, false, new CompoundTag()));
		}
	}

	/** The debtor's answer from the negotiation dialog. */
	static void onAnswer(ServerPlayer player, LoanActionPayload a) {
		Squad s = squadOf(player.getUUID());
		if (s == null || s.machine.state != State.NEGOTIATE) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		long owed = LoanService.record(server, player.getUUID()).owed;
		long now = LoanService.now(server);
		switch (a.action()) {
			case "pay_all" -> {
				if (Economies.get().balance(player) >= owed) {
					LoanService.pay(player, owed, true);
				}
				if (s.machine.state == State.NEGOTIATE) { // not enough money after all
					apply(server, s, SquadRules.answer(s.machine, Choice.PAY_ALL, false, now));
				}
			}
			case "pay_part" -> {
				long part = SquadRules.partialPayment(owed, CasinoConfig.loan().partialPaymentMin);
				long paid = Economies.get().balance(player) >= part ? LoanService.pay(player, part, false) : 0;
				boolean ok = paid >= part;
				long left = LoanService.record(server, player.getUUID()).owed;
				if (ok && left > 0) {
					player.sendSystemMessage(Component.translatable("msg.burmaldaholic.loan.partial_collectors", Texts.chips(paid), Texts.chips(left))
						.withStyle(ChatFormatting.YELLOW));
				}
				if (s.machine.state == State.NEGOTIATE) {
					apply(server, s, SquadRules.answer(s.machine, Choice.PAY_PART, ok, now));
				}
			}
			default -> apply(server, s, SquadRules.answer(s.machine, Choice.REFUSE, false, now));
		}
	}

	// ---- events ---------------------------------------------------------------------------------

	/** Debt reached 0 by any means → every live squad of that debtor is PAID. */
	static void onDebtCleared(MinecraftServer server, UUID debtor) {
		Squad s = squadOf(debtor);
		if (s != null) {
			apply(server, s, SquadRules.paid(s.machine, LoanService.now(server)));
		}
	}

	public static void onMemberHurt(SquadMob mob, Entity attacker) {
		Squad s = SQUADS.get(mob.squadId());
		if (s != null && attacker.getUUID().equals(s.debtor)) {
			s.attacked = true;
		}
	}

	public static void onMemberDeath(SquadMob mob) {
		Squad s = SQUADS.get(mob.squadId());
		if (s != null) {
			s.members.remove(mob.getUUID());
			s.units.remove(mob.getUUID());
		}
	}

	/** Audit hits apply Weakness I (§5.5). */
	static void onDamage(LivingEntity victim, DamageSource source) {
		if (victim instanceof Player && source.getEntity() instanceof AccountantEntity && source.getDirectEntity() instanceof EvokerFangs) {
			victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SquadRules.AUDIT_WEAKNESS_TICKS, 0));
		}
	}

	/** Repossession (§5.5) when a member of this debtor's squad killed them. */
	static void onPlayerDeath(ServerPlayer player, DamageSource source) {
		Squad s = squadOf(player.getUUID());
		if (s == null) {
			return;
		}
		if (s.machine.state == State.HOSTILE) {
			s.debtorDead = true; // any death of the debtor ends a hostile wave
		}
		if (!(source.getEntity() instanceof SquadMob killer) || !s.id.equals(killer.squadId())) {
			return;
		}
		s.debtorDead = true;
		MinecraftServer server = player.level().getServer();
		LoanRecord rec = LoanService.record(server, player.getUUID());
		if (rec.owed <= 0) {
			return;
		}
		LoanConfig cfg = CasinoConfig.loan();
		ItemTarget item = cfg.repossessItem ? findItem(player) : null;
		SquadRules.Repossession plan = SquadRules.repossession(Economies.get().balance(player), rec.owed, cfg.repossessBalancePercent,
			item == null ? 0 : item.value);
		long seized = 0;
		if (plan.seized() > 0 && Economies.get().tryWithdraw(player, plan.seized(), LoanService.REPOSSESS)) {
			seized = LoanService.applyToDebt(server, player.getUUID(), plan.seized(), false);
		}
		Component itemName = null;
		if (item != null && plan.itemCredit() > 0) {
			itemName = item.stack.getHoverName();
			item.take.run();
			LoanService.applyToDebt(server, player.getUUID(), plan.itemCredit(), false);
		}
		say(killer, player, Component.translatable(Dialogue.pick(Dialogue.REPOSSESS, killer.getRandom()::nextInt)));
		player.sendSystemMessage((itemName != null
			? Component.translatable("msg.burmaldaholic.loan.repossessed", Texts.chips(seized), itemName)
			: Component.translatable("msg.burmaldaholic.loan.repossessed_chips_only", Texts.chips(seized))).withStyle(ChatFormatting.RED));
	}

	private record ItemTarget(ItemStack stack, long value, Runnable take) {}

	/**
	 * Highest-appraised stack (§4.3.1 table): still in the inventory (keepInventory) or just dropped at the
	 * death spot. Chip items are never appraised, so they stay where they fell.
	 */
	private static @Nullable ItemTarget findItem(ServerPlayer player) {
		Inventory inv = player.getInventory();
		long[] values = new long[inv.getContainerSize()];
		for (int i = 0; i < values.length; i++) {
			values[i] = Stakes.appraise(inv.getItem(i));
		}
		int best = SquadRules.bestAppraised(values);
		if (best >= 0) {
			int slot = best;
			ItemStack stack = inv.getItem(slot).copy();
			return new ItemTarget(stack, values[slot], () -> inv.setItem(slot, ItemStack.EMPTY));
		}
		List<ItemEntity> drops = player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3), e -> e.isAlive());
		long[] dropValues = new long[drops.size()];
		for (int i = 0; i < dropValues.length; i++) {
			dropValues[i] = Stakes.appraise(drops.get(i).getItem());
		}
		int d = SquadRules.bestAppraised(dropValues);
		if (d < 0) {
			return null;
		}
		ItemEntity e = drops.get(d);
		return new ItemTarget(e.getItem().copy(), dropValues[d], e::discard);
	}
}
