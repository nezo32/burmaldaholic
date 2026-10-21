package dev.nezo.burmaldaholic.worldgen.npc;

import dev.nezo.burmaldaholic.client.dealer.DealerGestureSink;
import dev.nezo.burmaldaholic.client.dealer.GesturingDealer;
import dev.nezo.burmaldaholic.core.anim.cards.DealerGesture;
import dev.nezo.burmaldaholic.core.cashier.CashierBlockEntity;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.parts.DealerSpot;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.worldgen.HouseGames;
import dev.nezo.burmaldaholic.worldgen.logic.DealerBinding;
import dev.nezo.burmaldaholic.worldgen.logic.NpcLook;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The house dealer of a generated casino table (DL-SPEC §1; owner playtest N1/N2): a human, piglin or enderman in
 * casino dress standing on its table's dealer cell. Cosmetic and indestructible: it never moves (no physics, no
 * gravity, no pushing, snaps back home), takes no damage except from creative players and {@code /kill}, has one goal
 * (look at players, head ±75° around the table's facing) and gestures with its table on the client.
 *
 * <p>Binding: the synced {@link #TABLE} holds the main block of its table, found lazily on the first server tick and
 * re-checked every 100 ticks ({@link DealerBinding} candidates, else the nearest table within 3 blocks; never a
 * player-placed or owned table); the home follows the table's dealer cell when the table unfolds or folds. Clicking
 * the dealer uses the table exactly like clicking its main block (same callback chain). Gestures: blackjack / baccarat push them through
 * {@link DealerGestureSink} (synced GESTURE + GESTURE_TICK); the other games are read by the renderer from the table's
 * {@code DealerCueSource}.
 */
public class HouseDealer extends PathfinderMob implements GesturingDealer, DealerGestureSink, HasLook {
	public static final EntityDataAccessor<Optional<BlockPos>> TABLE = SynchedEntityData.defineId(HouseDealer.class,
		EntityDataSerializers.OPTIONAL_BLOCK_POS);
	private static final EntityDataAccessor<Byte> GESTURE = SynchedEntityData.defineId(HouseDealer.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> GESTURE_TICK = SynchedEntityData.defineId(HouseDealer.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> LOOK = SynchedEntityData.defineId(HouseDealer.class, EntityDataSerializers.BYTE);

	/** Fallback table search radius (horizontal; ±1 vertically). */
	public static final int TABLE_RADIUS = 3;
	public static final int REBIND_TICKS = 100;
	public static final int HOME_CHECK_TICKS = 20;
	/** Head turn limit around the table's facing. */
	public static final int HEAD_YAW = 75;

	private @Nullable BlockPos home;
	private float homeYaw;
	private boolean boundOnce;
	/** The bound table's dealer cell at the last check (transient): an unfold / fold moves it, the dealer follows. */
	private @Nullable BlockPos lastSpot;

	public HouseDealer(EntityType<? extends HouseDealer> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setNoGravity(true);
		noPhysics = true;
		setLook(NpcLook.pick(lookPool(), getUUID()));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(TABLE, Optional.empty());
		builder.define(GESTURE, (byte) 0);
		builder.define(GESTURE_TICK, 0);
		builder.define(LOOK, (byte) 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
	}

	// ---- identity ---------------------------------------------------------------------------------------------

	public NpcLook.Species species() {
		EntityType<?> t = getType();
		return t == NpcContent.PIGLIN_HOUSE_DEALER ? NpcLook.Species.PIGLIN
			: t == NpcContent.ENDER_HOUSE_DEALER ? NpcLook.Species.ENDER : NpcLook.Species.HUMAN;
	}

	@Override
	public int lookPool() {
		return species().dealerPool();
	}

	@Override
	public int look() {
		return Math.floorMod(entityData.get(LOOK), lookPool());
	}

	@Override
	public void setLook(int look) {
		entityData.set(LOOK, (byte) Math.floorMod(look, lookPool()));
	}

	/** The bound table's main block, or null (idle). */
	public @Nullable BlockPos table() {
		return entityData.get(TABLE).orElse(null);
	}

	public @Nullable BlockPos home() {
		return home;
	}

	/** Pins the dealer to {@code stand}, facing {@code yaw} (respawn / table follow; server thread). */
	public void setHome(BlockPos stand, float yaw) {
		setHome(level(), stand, yaw);
	}

	/**
	 * Pins the dealer to {@code stand}, reading the floor from {@code world}. Worldgen passes its WorldGenRegion here:
	 * structure pieces are placed on worker threads, and reading the live ServerLevel from there deadlocks against a
	 * main-thread {@code getChunk} (INTEGRATION-CB P1).
	 */
	public void setHome(BlockGetter world, BlockPos stand, float yaw) {
		home = stand.immutable();
		homeYaw = yaw;
		snapTo(stand.getX() + 0.5, feetY(world, stand), stand.getZ() + 0.5, yaw, 0f);
		setYHeadRot(yaw);
		setYBodyRot(yaw);
	}

	/** Feet height in {@code stand}: on top of a carpet (v0.1.3 dealer cells), else the block's floor. */
	private static double feetY(BlockGetter world, BlockPos stand) {
		return stand.getY() + Math.max(0, HouseDealers.floorHeight(world, stand));
	}

	@Override
	public @Nullable BlockPos boundTable() {
		return table();
	}

	// ---- gestures ---------------------------------------------------------------------------------------------

	@Override
	public void gesture(DealerGesture gesture) {
		entityData.set(GESTURE_TICK, (int) level().getGameTime());
		entityData.set(GESTURE, gesture.id());
	}

	@Override
	public DealerGesture dealerGesture() {
		return DealerGesture.byId(entityData.get(GESTURE));
	}

	@Override
	public long dealerGestureTick() {
		return entityData.get(GESTURE_TICK);
	}

	// ---- ticking: home, facing, binding -----------------------------------------------------------------------

	@Override
	public void tick() {
		noPhysics = true;
		setDeltaMovement(Vec3.ZERO);
		super.tick();
		setDeltaMovement(Vec3.ZERO);
		float yaw = facingYaw();
		setYRot(yaw);
		setYBodyRot(yaw);
		yBodyRotO = yaw;
		if (level() instanceof ServerLevel sl) {
			if (home == null) {
				home = blockPosition();
				homeYaw = getYRot();
			}
			if (!boundOnce || (tickCount + getId()) % REBIND_TICKS == 0) {
				boundOnce = true;
				rebind(sl);
			}
			if (tickCount % HOME_CHECK_TICKS == 0) {
				followTable(sl);
				Vec3 feet = new Vec3(home.getX() + 0.5, feetY(sl, home), home.getZ() + 0.5);
				if (position().distanceToSqr(feet) > 0.3 * 0.3) {
					snapTo(feet.x, feet.y, feet.z, facingYaw(), getXRot());
				}
			}
		}
	}

	/** Body yaw: the bound table's facing (the dealer looks at its players), else the home yaw. */
	private float facingYaw() {
		BlockPos t = table();
		if (t != null) {
			BlockState s = level().getBlockState(t);
			if (TableParts.gameOf(s) != null) {
				return TableParts.facingOf(s).toYRot();
			}
		}
		return home == null ? getYRot() : homeYaw;
	}

	@Override
	public int getMaxHeadYRot() {
		return HEAD_YAW;
	}

	/** Re-checks the binding (server). */
	public void rebind(ServerLevel level) {
		BlockPos stand = home != null ? home : blockPosition();
		BlockPos found = findTable(level, stand, Direction.fromYRot(home != null ? homeYaw : getYRot()));
		if (!java.util.Objects.equals(found, table())) {
			entityData.set(TABLE, Optional.ofNullable(found));
		}
		followTable(level);
	}

	/**
	 * Keeps the dealer on its bound table's dealer cell ({@link TableParts#dealerSpot}) when the table changes shape:
	 * a compact table that unfolds (sneak-click, legacy first tick) moves the cell from f − 1 to f − 2, a fold moves it
	 * back. The dealer follows when its home was the previous cell or is now covered by the table, and the new cell is
	 * standable; an egg-placed dealer elsewhere keeps its spot.
	 */
	private void followTable(ServerLevel level) {
		BlockPos t = table();
		if (t == null || home == null) {
			lastSpot = null;
			return;
		}
		DealerSpot spot = TableParts.dealerSpot(level, t);
		if (spot == null || !spot.main().equals(t)) {
			return;
		}
		BlockPos cell = spot.cell();
		if (!cell.equals(home) && (home.equals(lastSpot) || !HouseDealers.standable(level, home)) && HouseDealers.standable(level, cell)) {
			setHome(cell, spot.yaw());
		}
		lastSpot = cell.immutable();
	}

	/** The table a dealer standing in {@code stand} and facing {@code facing} serves, or null. */
	public static @Nullable BlockPos findTable(Level level, BlockPos stand, Direction facing) {
		for (DealerBinding.Candidate c : DealerBinding.candidates(facing.getStepX(), facing.getStepZ())) {
			BlockPos main = stand.offset(c.dx(), 0, c.dz());
			if (isTable(level, main, c.game(), facing)) {
				return main;
			}
		}
		return nearestTable(level, stand);
	}

	/** True if {@code main} is the main block of a game table of kind {@code game} ({@code null} = compact) facing {@code facing}. */
	static boolean isTable(Level level, BlockPos main, @Nullable TableGame game, Direction facing) {
		if (!bindable(level.getBlockEntity(main))) {
			return false;
		}
		DealerSpot spot = TableParts.dealerSpot(level, main); // MT's dealer API: the cell this table's dealer stands on
		if (spot == null || !spot.main().equals(main)) {
			return false;
		}
		BlockState s = level.getBlockState(main);
		TableGame g = TableParts.gameOf(s);
		if (g == null || TableParts.facingOf(s) != facing) {
			return false;
		}
		TablePart part = TableParts.partOf(s);
		return game == null ? part == TablePart.SINGLE : part == TablePart.MAIN && g == game;
	}

	/** Main block of the nearest game table within {@link #TABLE_RADIUS} blocks (spawn eggs, odd layouts). */
	static @Nullable BlockPos nearestTable(Level level, BlockPos center) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-TABLE_RADIUS, -1, -TABLE_RADIUS), center.offset(TABLE_RADIUS, 1, TABLE_RADIUS))) {
			BlockState s = level.getBlockState(pos);
			if (TableParts.gameOf(s) == null) {
				continue;
			}
			BlockPos main = TableParts.mainPos(pos, s);
			if (!bindable(level.getBlockEntity(main))) {
				continue;
			}
			double d = pos.distSqr(center);
			if (d < bestDist) {
				bestDist = d;
				best = main.immutable();
			}
		}
		return best;
	}

	/** A table a house dealer may serve: a game table no player placed or owns (never a cashier). */
	static boolean bindable(@Nullable BlockEntity be) {
		return be instanceof CasinoTableBlockEntity table && !(be instanceof CashierBlockEntity) && !HouseGames.isPlayerPlaced(be)
			&& table.ownership().isEmpty();
	}

	// ---- interaction ------------------------------------------------------------------------------------------

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && level() instanceof ServerLevel sl) {
			use(sp, sl);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Uses the bound table as if the player clicked the top of its main block with an empty hand: the same chain as a
	 * real block use (spawn protection, {@code UseBlockCallback} — Ownership refusals, bot ask-to-join — then the block).
	 */
	public void use(ServerPlayer player, ServerLevel level) {
		if (!CasinoMode.isEnabled(player)) {
			player.sendOverlayMessage(Component.translatable("gui.burmaldaholic.error.casino_off"));
			return;
		}
		rebind(level);
		BlockPos t = table();
		if (t == null) {
			player.sendOverlayMessage(Component.translatable("msg.burmaldaholic.worldgen.dealer.no_table"));
			return;
		}
		if (!level.mayInteract(player, t)) {
			return; // spawn protection, as ServerGamePacketListenerImpl.handleUseItemOn
		}
		Vec3 top = Vec3.atBottomCenterOf(t).add(0, 1, 0);
		player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(top, Direction.UP, t, false));
	}

	// ---- protection -------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.isCreativePlayer() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, damage);
		}
		return false;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {}

	@Override
	protected void pushEntities() {}

	@Override
	public void push(double x, double y, double z) {}

	@Override
	public void knockback(double strength, double x, double z, DamageSource source, float yRot) {}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean startRiding(Entity vehicle, boolean force, boolean sendEventAndTriggers) {
		return false;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null; // a professional: silent at the table
	}

	// ---- save -------------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		BlockPos t = table();
		if (t != null) {
			output.putLong("table", t.asLong());
		}
		if (home != null) {
			output.putLong("home", home.asLong());
			output.putFloat("home_yaw", homeYaw);
		}
		output.putByte("look", (byte) look());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(TABLE, input.getLong("table").map(BlockPos::of));
		home = input.getLong("home").map(BlockPos::of).orElse(null);
		homeYaw = input.getFloatOr("home_yaw", getYRot());
		setLook(input.getByteOr("look", (byte) look()));
	}
}
