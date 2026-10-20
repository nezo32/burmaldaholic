package dev.nezo.burmaldaholic.core.table;

import dev.nezo.burmaldaholic.core.table.parts.LocalCell;
import dev.nezo.burmaldaholic.core.table.parts.MachineKind;
import dev.nezo.burmaldaholic.core.table.parts.MachineShapes;
import dev.nezo.burmaldaholic.core.table.parts.MigrationDecision;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import dev.nezo.burmaldaholic.core.table.parts.TableProtection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A casino table that unfolds into a multi-block footprint (MT-DESIGN §2–§4). Default state {@code part=single}
 * (legacy / compact 1×1 table, the v0.1 behaviour); a player-placed table is {@code part=main} plus its parts.
 * Only the main (or single) block has the block entity; every part routes use / break / explosions to it.
 * World logic lives in {@link TableParts}; footprint maths in {@link TableFootprint}.
 */
public class MultiBlockTableBlock extends CasinoTableBlock {
	public static final EnumProperty<TablePart> PART = TableParts.TABLE_PART;
	private static final double TOP = 14.0;

	/** The part property a machine block is being constructed with (createBlockStateDefinition runs in Block's constructor). */
	private static final ThreadLocal<EnumProperty<TablePart>> PENDING_PART = new ThreadLocal<>();

	private final @Nullable TableGame game;
	private final @Nullable MachineKind machine;
	private final EnumProperty<TablePart> partProperty;
	private final List<LocalCell> cells;
	private final Map<TablePart, LocalCell> byPart = new EnumMap<>(TablePart.class);
	/** [2D data value of facing][part ordinal]. */
	private final VoxelShape[][] shapes = new VoxelShape[4][TablePart.values().length];

	public MultiBlockTableBlock(Properties properties, TableType<?> type, TableGame game) {
		this(properties, type, game, null, TableFootprint.localCells(game));
	}

	/** MT-3: an upright multi-block machine (slots, wheel, Plinko, cashier) with its own {@code part} property. */
	public static MultiBlockTableBlock machine(Properties properties, TableType<?> type, MachineKind kind) {
		PENDING_PART.set(TableParts.propertyFor(kind));
		try {
			return new MultiBlockTableBlock(properties, type, null, kind, kind.localCells());
		} finally {
			PENDING_PART.remove();
		}
	}

	protected MultiBlockTableBlock(Properties properties, TableType<?> type, @Nullable TableGame game, @Nullable MachineKind machine,
			List<LocalCell> cells) {
		super(properties, type);
		this.game = game;
		this.machine = machine;
		this.partProperty = machine == null ? PART : TableParts.propertyFor(machine);
		this.cells = List.copyOf(cells);
		for (LocalCell c : this.cells) {
			byPart.put(c.part(), c);
		}
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(partProperty(), TablePart.SINGLE));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (TablePart p : TablePart.values()) {
				shapes[d.get2DDataValue()][p.ordinal()] = p == TablePart.SINGLE ? Shapes.block()
					: machine != null ? MachineShapes.shape(machine, byPart.get(p), d) : buildShape(p, d);
			}
		}
	}

	/** The part property of this block: {@link #PART} for tables, the machine kind's own property for machines. */
	@SuppressWarnings("ConstantConditions")
	public EnumProperty<TablePart> partProperty() {
		if (partProperty != null) {
			return partProperty;
		}
		EnumProperty<TablePart> pending = PENDING_PART.get(); // still inside the constructor
		return pending != null ? pending : PART;
	}

	/** Footprint kind of a TABLE (null for machines). */
	public @Nullable TableGame game() {
		return game;
	}

	/** Kind of an upright MACHINE (null for tables). */
	public @Nullable MachineKind machine() {
		return machine;
	}

	/** Every cell of the full footprint, main first. */
	public List<LocalCell> cells() {
		return cells;
	}

	public @Nullable LocalCell cellOf(TablePart part) {
		return byPart.get(part);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(partProperty());
	}

	// ---- shape ---------------------------------------------------------------------------------

	/** Outline/collision of one part: felt height 14/16, stepped arcs where the felt corners are rounded. */
	protected VoxelShape buildShape(TablePart part, Direction facing) {
		LocalCell cell = byPart.get(part);
		if (cell == null || game == null) {
			return Shapes.box(0, 0, 0, 1, TOP / 16, 1);
		}
		dev.nezo.burmaldaholic.core.table.parts.FeltFrame frame = dev.nezo.burmaldaholic.core.table.parts.FeltFrame.of(game, TablePart.MAIN);
		double hx = game.width() / 2.0;
		double hz = game.depth() / 2.0;
		double rcF = frame.outerCornerRadius(true);
		double rcB = frame.outerCornerRadius(false);
		int fx = facing.getStepX();
		int fz = facing.getStepZ();
		int[] co = TableFootprint.cellOffset(cell.r(), cell.f(), fx, fz);
		VoxelShape shape = Shapes.empty();
		int strips = 8;
		for (int i = 0; i < strips; i++) {
			double x0 = cell.r() + i / (double) strips;
			double x1 = x0 + 1.0 / strips;
			double xm = Math.abs((x0 + x1) / 2 - 0.5);
			double zTop = edge(xm, hx, hz, rcF);
			double zBot = -edge(xm, hx, hz, rcB);
			double z0 = Math.max(cell.f(), snap(zBot));
			double z1 = Math.min(cell.f() + 1, snap(zTop));
			if (z1 <= z0) {
				continue;
			}
			double[] a = TableFootprint.toWorld(x0, z0, fx, fz);
			double[] b = TableFootprint.toWorld(x1, z1, fx, fz);
			double minX = Math.min(a[0], b[0]) - co[0];
			double maxX = Math.max(a[0], b[0]) - co[0];
			double minZ = Math.min(a[1], b[1]) - co[1];
			double maxZ = Math.max(a[1], b[1]) - co[1];
			shape = Shapes.or(shape, Shapes.box(clamp(minX), 0, clamp(minZ), clamp(maxX), TOP / 16, clamp(maxZ)));
		}
		return shape.isEmpty() ? Shapes.box(0, 0, 0, 1, TOP / 16, 1) : shape.optimize();
	}

	/** Half-depth of a rounded rectangle (half sizes hx, hz, corner radius rc) at |x - centre| = ax. */
	private static double edge(double ax, double hx, double hz, double rc) {
		double r = Math.min(rc, Math.min(hx, hz));
		if (r <= 0 || ax <= hx - r) {
			return hz;
		}
		double d = ax - (hx - r);
		return (hz - r) + Math.sqrt(Math.max(0, r * r - d * d));
	}

	private static double snap(double v) {
		return Math.round(v * 16) / 16.0;
	}

	private static double clamp(double v) {
		return Math.max(0, Math.min(1, v));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes[state.getValue(FACING).get2DDataValue()][state.getValue(partProperty()).ordinal()];
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	// ---- placement -----------------------------------------------------------------------------

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState main = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(partProperty(), TablePart.MAIN);
		int blocked = TableParts.blockedCells(context, context.getClickedPos(), main);
		if (blocked > 0) {
			if (!context.getLevel().isClientSide() && context.getPlayer() instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(TableParts.noRoomMessage(blocked));
			}
			return null;
		}
		return main;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && state.getValue(partProperty()) == TablePart.MAIN) {
			TableParts.placeParts(level, pos, state);
		}
	}

	// ---- integrity -----------------------------------------------------------------------------

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
			BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		TablePart part = state.getValue(partProperty());
		// worldgen regions (no neighbour updates there anyway) and unloaded neighbours: never cascade
		if (part == TablePart.SINGLE || !(level instanceof Level) || TableParts.placing() || !level.hasChunkAt(neighborPos)) {
			return state;
		}
		LocalCell here = byPart.get(part);
		if (here == null) {
			return state;
		}
		Direction facing = state.getValue(FACING);
		int fx = facing.getStepX();
		int fz = facing.getStepZ();
		int dr = dir.getStepX() * TableFootprint.rightX(fx, fz) + dir.getStepZ() * TableFootprint.rightZ(fx, fz);
		int df = dir.getStepX() * fx + dir.getStepZ() * fz;
		int du = dir.getStepY();
		LocalCell expected = null;
		for (LocalCell c : cells) {
			if (c.r() == here.r() + dr && c.u() == here.u() + du && c.f() == here.f() + df) {
				expected = c;
				break;
			}
		}
		if (expected == null) {
			return state;
		}
		if (neighborState.is(this) && neighborState.getValue(FACING) == facing && neighborState.getValue(partProperty()) == expected.part()) {
			return state;
		}
		return Blocks.AIR.defaultBlockState();
	}

	// ---- breaking ------------------------------------------------------------------------------

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		TablePart part = state.getValue(partProperty());
		if (!level.isClientSide() && part.isMultiBlock() && part != TablePart.MAIN) {
			// breaking any part breaks the table from its main block: one item (none in creative), stakes settled
			TableParts.breakFromPart(level, pos, state, player);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * Only the main (or compact) block ever drops the item, and never a house table's (CB-G {@link TableProtection}).
	 * Done here because every removal path reaches it — player breaks, explosions, the part cascade, withers,
	 * {@code /setblock … destroy}, other mods. The part rule is also in code (not only the loot table): a JSON-only
	 * check depends on the loot predicate format, which 26.3 changed.
	 */
	@Override
	protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
		if (!state.getValue(partProperty()).isController()) {
			return List.of();
		}
		net.minecraft.world.phys.Vec3 origin = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
		if (origin != null && !TableProtection.get().dropsAllowed(params.getLevel(), BlockPos.containing(origin),
			params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY))) {
			return List.of();
		}
		return super.getDrops(state, params);
	}

	@Override
	protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> drops) {
		TablePart part = state.getValue(partProperty());
		if (!TableProtection.get().dropsAllowed(level, TableParts.mainPos(pos, state))) {
			drops = (stack, at) -> {}; // house game (CB-G): no item
		}
		if (part.isMultiBlock() && part != TablePart.MAIN) {
			BlockPos main = TableParts.mainPos(pos, state);
			BlockState mainState = level.getBlockState(main);
			if (mainState.is(this) && mainState.getValue(partProperty()) == TablePart.MAIN) {
				// idempotent: once the main is gone, later hits on parts find nothing to drop
				mainState.onExplosionHit(level, main, explosion, drops);
				return;
			}
		}
		super.onExplosionHit(state, level, pos, explosion, drops);
	}

	// ---- rotation ------------------------------------------------------------------------------

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		BlockState s = super.mirror(state, mirror);
		return mirror == Mirror.NONE ? s : s.setValue(partProperty(), s.getValue(partProperty()).mirrored());
	}

	// ---- block entity --------------------------------------------------------------------------

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return state.getValue(partProperty()).isController() ? super.newBlockEntity(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
		return state.getValue(partProperty()).isController() ? super.getTicker(level, state, beType) : null;
	}

	// ---- use -----------------------------------------------------------------------------------

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		TablePart part = state.getValue(partProperty());
		if (part == TablePart.SINGLE) {
			if (player.isSecondaryUseActive()) {
				if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
					unfold(sl, pos, sp);
				}
				return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
			}
			InteractionResult r = openAt(level, pos, player);
			if (player instanceof ServerPlayer sp && dev.nezo.burmaldaholic.core.mode.CasinoMode.isEnabled(level)) {
				TableParts.compactHint(sp);
			}
			return r;
		}
		return openAt(level, TableParts.mainPos(pos, state), player);
	}

	private static void unfold(ServerLevel level, BlockPos pos, ServerPlayer player) {
		MigrationDecision.Outcome o = TableParts.tryUnfold(level, pos);
		if (o == MigrationDecision.Outcome.GROW) {
			player.sendOverlayMessage(Component.translatable("msg.burmaldaholic.table.unfolded"));
		} else if (o == MigrationDecision.Outcome.RETRY) {
			player.sendOverlayMessage(Component.translatable("msg.burmaldaholic.table.unfold_busy"));
		} else {
			player.sendOverlayMessage(TableParts.noRoomMessage(Math.max(1, TableParts.blockedForUnfold(level, pos))));
		}
	}
}
