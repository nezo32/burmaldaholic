package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.client.render.SpectatorBlockEntityRenderer;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TablePublic;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Base of every in-world table board (docs/architecture/ui-v2.md §7): the felt overhang, cards at 1.5 × screen size,
 * chip stacks, collision-free seat plates outside the felt and a floating, yaw-only board that stays legible from 3 to
 * 8 blocks. A game's renderer only implements {@link #felt} and {@link #fill} — writing its PUBLIC state into the
 * {@link BoardState} in table-local coordinates ({@link BoardGeometry}) — and registers itself with
 * {@code BlockEntityRendererRegistry.register(type, MyBoardRenderer::new)}.
 *
 * <p>The base computes FACING, the blocked-overhang fallback (cached, re-checked every {@link #BLOCK_CHECK_TICKS}), the
 * board LOD with hysteresis ({@link HoloScale#lod(double, HoloScale.Lod)}), the text pixel sizes from the FOV and the
 * window height, and submits felt → cards → chips → plates → board. Budget per table and frame (§7.5): ≤ 32 cards
 * (2 quads each), ≤ 16 stacks, ≤ 3 board lines + 8 plates × 2 lines + 8 labels; text sequences are cached by the
 * caller's key; counts and times go to {@link BoardStats}.
 *
 * <p>LOD: ≤ 8 blocks everything + tweens; 8–16 board state / result lines, cards, chips; 16–32 felt, cards, chips, no
 * text; beyond 32 the block model only.
 */
public abstract class BoardRenderer<T extends BlockEntity, S extends BoardState> extends SpectatorBlockEntityRenderer<T, S> {
	/** Ticks between two checks of the overhang's neighbours. */
	public static final int BLOCK_CHECK_TICKS = 40;
	/** Minimum block light of the felt / cards (readable in a dim casino). */
	private static final int MIN_BLOCK_LIGHT = 7;
	private static final Map<Long, BiConsumer<BlockEntity, BoardState>> DEMO = new HashMap<>();

	protected final Font font;
	private final Map<T, BoardModel> models = new WeakHashMap<>();
	private final HoloFrame frame = new HoloFrame();

	protected BoardRenderer(BlockEntityRendererProvider.Context ctx) {
		this.font = ctx.font();
	}

	/** The felt shape of this table (before the blocked fallback). */
	protected abstract Felt felt(T be);

	/** Writes the table's PUBLIC state into {@code s} (table-local coordinates). Called only when the board is visible. */
	protected abstract void fill(T be, S s, float partialTick);

	/** Felt theme: 0 village, 1 bastion, 2 end. Default by dimension; tables with a synced theme override. */
	protected int theme(T be) {
		Level level = be.getLevel();
		if (level == null) return 0;
		if (level.dimension() == Level.NETHER) return 1;
		if (level.dimension() == Level.END) return 2;
		return 0;
	}

	@Override
	protected int animateRadius() {
		return 16;
	}

	@Override
	public int getViewDistance() {
		return 32;
	}

	@Override
	protected final void extractAnimated(T be, S s, float partialTick) {
		long t0 = System.nanoTime();
		BoardModel m = models.computeIfAbsent(be, k -> new BoardModel());
		m.clear();
		s.model = m;
		s.font = font;
		Minecraft mc = Minecraft.getInstance();
		s.lang = mc.getLanguageManager().getSelected();
		BlockState bs = be.getBlockState();
		s.facing = bs.hasProperty(CasinoTableBlock.FACING) ? bs.getValue(CasinoTableBlock.FACING) : Direction.NORTH;
		Level level = be.getLevel();
		Felt wanted = felt(be);
		if (level != null) {
			long now = level.getGameTime();
			if (now - m.blockedCheckedAt >= BLOCK_CHECK_TICKS || now < m.blockedCheckedAt) {
				m.blocked = blocked(level, be.getBlockPos(), s.facing, wanted);
				m.blockedCheckedAt = now;
			}
			int light = LightCoordsUtil.getLightCoords(level, be.getBlockPos().above());
			s.lightCoords = LightCoordsUtil.max(light, LightCoordsUtil.pack(MIN_BLOCK_LIGHT, 0));
		}
		s.felt = BoardGeometry.fallback(wanted, m.blocked);
		double dist = Math.sqrt(s.distSq);
		s.dist = dist;
		s.hlod = HoloScale.lod(dist, m.lod);
		m.lod = s.hlod;
		double fov = mc.options.fov().get();
		int screenH = Math.max(240, mc.getWindow().getHeight());
		s.boardPx = HoloScale.textPx(HoloScale.BOARD_BASE, dist, fov, screenH);
		s.platePx = HoloScale.textPx(HoloScale.PLATE_BASE, dist, fov, screenH);
		s.viewer = mc.player == null ? null : mc.player.getUUID();
		s.theme = theme(be);
		s.back = defaultBack(level);
		BoardStats.Table st = BoardStats.begin(be.getBlockPos(), 0);
		st.reset(0);
		st.lod = s.hlod;
		if (s.hlod != HoloScale.Lod.MODEL) {
			fill(be, s, partialTick);
			BiConsumer<BlockEntity, BoardState> demo = DEMO.get(be.getBlockPos().asLong());
			if (demo != null) demo.accept(be, s);
		}
		st.nanos += System.nanoTime() - t0;
	}

	private static int defaultBack(@Nullable Level level) {
		if (level == null) return BoardTextures.BACK_CRIMSON;
		if (level.dimension() == Level.NETHER) return BoardTextures.BACK_BASTION;
		if (level.dimension() == Level.END) return BoardTextures.BACK_END;
		return BoardTextures.BACK_CRIMSON;
	}

	@Override
	public void submit(S s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		BoardModel m = s.model;
		if (m == null || !s.visibleBoard()) return;
		long t0 = System.nanoTime();
		BoardStats.Table st = BoardStats.of(s.blockPos);
		int light = s.lightCoords;
		pose.pushPose();
		pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0.5f, 0f, 0.5f);
		FeltPainter.submit(pose, out, s, light);
		st.feltQuads += FeltPainter.quads(s.felt);
		if (m.cardCount > 0) {
			CardPainter.submit(pose, out, s, m, light);
			st.cardQuads += m.cardCount * CardPainter.QUADS_PER_CARD;
		}
		if (m.stackCount > 0) {
			ChipPainter.submit(pose, out, m, light);
			for (int i = 0; i < m.stackCount; i++) st.chipQuads += ChipPainter.quads(m.stackAmount[i]);
		}
		pose.popPose();
		if (s.showsBoard() && s.font != null) {
			frame.set(camera, s.blockPos, s.facing, BoardGeometry.TOP);
			float top = s.showsPlates() ? SeatPlates.submit(pose, out, font, s, m, frame, st) : contentTop(m);
			HoloBoard.submit(pose, out, font, s, m, frame, top, st);
		}
		st.nanos += System.nanoTime() - t0;
	}

	/** Without plates (8–16 blocks) the board only needs to clear the felt. */
	private static float contentTop(BoardModel m) {
		return 0.15f;
	}

	// ---- the blocked-overhang fallback ------------------------------------------------------------------------------

	/**
	 * True when a neighbour would intersect the felt overhang: a colliding block in a cell the felt covers (at the
	 * table's level), or another table two blocks away along the long axis (both overhangs would meet in the gap).
	 */
	public static boolean blocked(Level level, BlockPos pos, Direction facing, Felt felt) {
		if (felt == Felt.FALLBACK) return false;
		Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(-facing.toYRot()));
		int[] cells = BoardGeometry.overhangCells(felt);
		Vector3f v = new Vector3f();
		for (int i = 0; i < cells.length; i += 2) {
			int dx = cells[i], dz = cells[i + 1];
			if (!covers(felt, dx, dz)) continue;
			BlockPos p = offset(pos, q, dx, dz, v);
			BlockState st = level.getBlockState(p);
			if (!st.getCollisionShape(level, p).isEmpty()) return true;
		}
		for (int dx : new int[] {-2, 2}) {
			if (level.getBlockEntity(offset(pos, q, dx, 0, v)) instanceof CasinoTableBlockEntity) return true;
		}
		return false;
	}

	/** Does the felt cover any part of table-local cell (dx, dz)? (5 × 5 samples.) */
	static boolean covers(Felt felt, int dx, int dz) {
		for (int a = 0; a < 5; a++) {
			for (int b = 0; b < 5; b++) {
				if (BoardGeometry.inside(felt, dx + 0.1f + 0.2f * a, dz + 0.1f + 0.2f * b)) return true;
			}
		}
		return false;
	}

	private static BlockPos offset(BlockPos pos, Quaternionf q, int dx, int dz, Vector3f v) {
		v.set(dx, 0, dz).rotate(q);
		return pos.offset(Math.round(v.x), 0, Math.round(v.z));
	}

	// ---- helpers for game renderers ---------------------------------------------------------------------------------

	/** Where a seat's betting spot is: {x, z} table-local for seat {@code seat} of {@code seats}. */
	@FunctionalInterface
	public interface SeatSpot {
		float[] at(int seat, int seats);
	}

	/** Adds a plate's detail line (or flags) for a seated seat. */
	@FunctionalInterface
	public interface PlateDetail {
		void fill(int seat, TablePublic.SeatPub pub, BoardState.PlateRef plate);
	}

	/**
	 * Seat plates of every occupied seat from the table's public seat header ({@link TablePublic}): the name (cyan for
	 * the viewer, bots by their translated name), anchored outside the felt behind the seat's spot. {@code detail}
	 * (nullable) adds the second line.
	 */
	protected static void seatPlates(BoardState s, CasinoTableBlockEntity be, SeatSpot spot, @Nullable PlateDetail detail) {
		if (!s.showsPlates()) return;
		TablePublic.SeatPub[] seats = be.clientSeats();
		long seq = be.clientSeatsSeq();
		for (int i = 0; i < seats.length; i++) {
			TablePublic.SeatPub pub = seats[i];
			if (pub == null) continue;
			float[] sp = spot.at(i, seats.length);
			float[] a = BoardGeometry.plateAnchor(s.felt, sp[0], sp[1]);
			BoardState.PlateRef ref = s.plate(i, a[0], a[1], a[2]);
			if (ref == null) return;
			boolean you = pub.is(s.viewer);
			ref.name(seq * 31 + i, pub::displayName, 0).viewer(you);
			if (detail != null) detail.fill(i, pub, ref);
		}
	}

	/**
	 * Registers a demo filler for the table at {@code pos} (GameTests, {@code /casino debug board}): called after
	 * {@link #fill} each frame; null removes it.
	 */
	public static void demo(BlockPos pos, @Nullable BiConsumer<BlockEntity, BoardState> filler) {
		if (filler == null) DEMO.remove(pos.asLong());
		else DEMO.put(pos.asLong(), filler);
	}

	public static void clearDemos() {
		DEMO.clear();
	}
}
