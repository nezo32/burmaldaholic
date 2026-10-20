package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.client.render.SpectatorBlockEntityRenderer;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.core.table.TablePublic;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import dev.nezo.burmaldaholic.core.ui.v2.board.FullTableMap;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import dev.nezo.burmaldaholic.core.table.parts.FeltFrame;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteWorld;
import dev.nezo.burmaldaholic.core.table.parts.TableFootprint;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.table.parts.TableParts;
import org.jspecify.annotations.Nullable;

/**
 * Base of every in-world table board (docs/architecture/ui-v2.md §7): the felt print decal of a multi-block table (or
 * the compact block-top felt), cards at 1.5 × screen size,
 * chip stacks, collision-free seat plates outside the felt and a floating, yaw-only board that stays legible from 3 to
 * 8 blocks. A game's renderer only implements {@link #felt} and {@link #fill} — writing its PUBLIC state into the
 * {@link BoardState} in table-local coordinates ({@link BoardGeometry}) — and registers itself with
 * {@code BlockEntityRendererRegistry.register(type, MyBoardRenderer::new)}.
 *
 * <p>The base computes FACING, the design → drawn mapping ({@link #layout}: full 3×2 felt or compact block top), the
 * board LOD with hysteresis ({@link HoloScale#lod(double, HoloScale.Lod)}), the text pixel sizes from the FOV and the
 * window height, and submits felt → cards → chips → plates → board. Budget per table and frame (§7.5): ≤ 32 cards
 * (2 quads each), ≤ 16 stacks, ≤ 3 board lines + 8 plates × 2 lines + 8 labels; text sequences are cached by the
 * caller's key; counts and times go to {@link BoardStats}.
 *
 * <p>LOD: ≤ 8 blocks everything + tweens; 8–16 board state / result lines, cards, chips; 16–32 felt, cards, chips, no
 * text; beyond 32 the block model only.
 */
public abstract class BoardRenderer<T extends BlockEntity, S extends BoardState> extends SpectatorBlockEntityRenderer<T, S> {
	/** Minimum block light of the felt / cards (readable in a dim casino). */
	private static final int MIN_BLOCK_LIGHT = 7;
	private static final Map<Long, BiConsumer<BlockEntity, BoardState>> DEMO = new HashMap<>();

	protected final Font font;
	private final Map<T, BoardModel> models = new WeakHashMap<>();
	private final HoloFrame frame = new HoloFrame();

	protected BoardRenderer(BlockEntityRendererProvider.Context ctx) {
		this.font = ctx.font();
	}

	/** The design felt shape of this table (its content is laid out for it; see {@link BoardGeometry}). */
	protected abstract Felt felt(T be);

	/** Writes the table's PUBLIC state into {@code s} (table-local coordinates). Called only when the board is visible. */
	protected abstract void fill(T be, S s, float partialTick);

	/**
	 * Where the dealer's (or the community) cards lie, table-local {x0, z0, x1, z1, yTop}: an obstacle the seat plates
	 * stay above even before a card is dealt (design audit W P1). Default: the felt's card row
	 * ({@link BoardGeometry#dealerRect}); null = none.
	 */
	protected float @Nullable [] dealerRect(T be) {
		return BoardGeometry.dealerRect(felt(be));
	}

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
			int light = LightCoordsUtil.getLightCoords(level, be.getBlockPos().above());
			s.feltLight = light;
			s.lightCoords = LightCoordsUtil.max(light, LightCoordsUtil.pack(MIN_BLOCK_LIGHT, 0));
		}
		s.felt = wanted;
		layout(s, wanted, bs);
		double dist = feltDistance(s, mc);
		s.dist = dist;
		s.hlod = HoloScale.lod(dist, m.lod);
		m.lod = s.hlod;
		double fov = HoloScale.effectiveFov(mc.gameRenderer.mainCamera().getFov(), mc.options.fov().get());
		int screenH = Math.max(240, mc.getWindow().getHeight());
		s.boardPx = HoloScale.readablePx(HoloScale.BOARD_READ, dist, fov, screenH);
		s.platePx = HoloScale.readablePx(HoloScale.PLATE_READ, dist, fov, screenH);
		s.viewer = mc.player == null ? null : mc.player.getUUID();
		s.seated = false;
		s.seatCount = be instanceof CasinoTableBlockEntity seatTable ? seatTable.clientSeats().length : 0;
		if (s.viewer != null && be instanceof CasinoTableBlockEntity table) {
			for (TablePublic.SeatPub pub : table.clientSeats()) {
				if (pub != null && pub.is(s.viewer)) {
					s.seated = true;
					break;
				}
			}
		}
		// any part of a multi-block table counts as looking at it (MT: TableParts.mainOf)
		s.lookedAt = level != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
				&& (hit.getBlockPos().equals(be.getBlockPos()) || TableParts.mainOf(level, hit.getBlockPos()).equals(be.getBlockPos()));
		s.theme = theme(be);
		s.back = defaultBack(level);
		BoardStats.Table st = BoardStats.begin(be.getBlockPos(), 0);
		st.reset(0);
		st.lod = s.hlod;
		st.platePx = s.platePx;
		st.boardPx = s.boardPx;
		st.dist = s.dist;
		st.full = s.full;
		if (s.hlod != HoloScale.Lod.MODEL) {
			BiConsumer<BlockEntity, BoardState> demo = DEMO.isEmpty() ? null : DEMO.get(be.getBlockPos().asLong());
			if (demo != null) demo.accept(be, s);
			else fill(be, s, partialTick);
			if (s.full) {
				// a 3-block table: cards at a believable scale (the design sizes were made for the 1-block-wide overhang)
				for (int i = 0; i < m.cardCount; i++) {
					m.cards[i].w *= BoardGeometry.FULL_CARD_SCALE;
					m.cards[i].h *= BoardGeometry.FULL_CARD_SCALE;
				}
				m.deckW *= BoardGeometry.FULL_CARD_SCALE;
				m.deckH *= BoardGeometry.FULL_CARD_SCALE;
			}
			float[] d = s.showsPlates() ? dealerRect(be) : null;
			if (d != null) {
				System.arraycopy(d, 0, m.dealer, 0, 5);
				m.hasDealer = true;
			}
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
		PlateFrame.beginTable(s.blockPos.asLong());
		BoardStats.Table st = BoardStats.of(s.blockPos);
		int light = s.lightCoords;
		pose.pushPose();
		pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0.5f, 0f, 0.5f);
		FeltPainter.submit(pose, out, s, light);
		st.feltQuads += FeltPainter.quads(s);
		if (s.font != null) FeltPrint.placard(pose, out, s.font, s, m);
		mapDesign(pose, s);
		if (s.font != null) FeltPrint.submit(pose, out, s.font, s, s.seatCount);
		if (m.cardCount > 0 || m.hasDeck) {
			CardPainter.submit(pose, out, s, m, light);
			st.cardQuads += m.cardCount * CardPainter.QUADS_PER_CARD + (m.hasDeck ? CardPainter.deckQuads(m.deckCards) : 0);
		}
		if (m.stackCount > 0) {
			ChipPainter.submit(pose, out, m, light);
			for (int i = 0; i < m.stackCount; i++) st.chipQuads += ChipPainter.quads(m.stackAmount[i]);
		}
		pose.popPose();
		if (s.showsBoard() && s.font != null) {
			frame.set(camera, s.blockPos, s.facing, s);
			float top = s.showsPlates() ? SeatPlates.submit(pose, out, font, s, m, frame, st) : contentTop(m);
			HoloBoard.submit(pose, out, font, s, m, frame, top, st);
		}
		st.nanos += System.nanoTime() - t0;
	}

	/** Without plates (8–16 blocks) the board only needs to clear the felt. */
	private static float contentTop(BoardModel m) {
		return 0.15f;
	}

	// ---- the felt: full multi-block table or compact (MT-DESIGN §2) -------------------------------------------------

	/**
	 * Sets the design → drawn mapping of this frame: a full table (part ≠ SINGLE) maps the design felt onto its
	 * footprint-local {@link FeltFrame} (centre (0.5, 0) for the 3×2 tables, felt top 14/16 + lift); a compact table
	 * draws the fallback felt on its own block top and shrinks the content onto it.
	 */
	static void layout(BoardState s, Felt wanted, BlockState bs) {
		TableGame game = s.tableGame != null ? s.tableGame : TableParts.gameOf(bs);
		TablePart part = s.tablePart;
		if (game != null && part != TablePart.SINGLE && part.isMultiBlock()) {
			FeltFrame f = FeltFrame.of(game, part);
			s.full = true;
			s.frame = f;
			s.drawnFelt = wanted;
			// roulette: the design wheel lands on the furniture's bowl and the layout to its right, on the betting felt
			FullTableMap map = FullTableMap.of(f, wanted, RouletteWorld.WHEEL_X, RouletteWorld.WHEEL_Z, RouletteWorld.WHEEL_R);
			s.map = map;
			s.contentScale = map.scale();
			s.originX = map.originX();
			s.originY = map.originY();
			s.originZ = map.originZ();
			String path = BuiltInRegistries.BLOCK.getKey(bs.getBlock()).getPath();
			s.furniture = path.contains("high_roller") ? 1 : path.contains("player_banked") ? 2 : 0;
		} else {
			s.full = false;
			s.frame = null;
			s.map = null;
			s.drawnFelt = BoardGeometry.fallback(wanted, true);
			s.contentScale = BoardGeometry.contentScale(wanted, s.drawnFelt);
			s.originX = 0.5f;
			s.originY = BoardGeometry.TOP;
			s.originZ = 0.5f;
		}
	}

	/** Camera → felt centre (world), so a 3×2 table's LOD and text size follow its felt, not its corner block. */
	private static double feltDistance(BoardState s, Minecraft mc) {
		double[] w = TableFootprint.toWorld(s.originX, s.originZ, s.facing.getStepX(), s.facing.getStepZ());
		var cam = mc.gameRenderer.mainCamera().position();
		double dx = s.blockPos.getX() + w[0] - cam.x, dy = s.blockPos.getY() + s.originY - cam.y, dz = s.blockPos.getZ() + w[1] - cam.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Applies the design → drawn mapping to a pose already rotated by FACING (see {@link BoardState#contentScale}). */
	static void mapDesign(PoseStack pose, BoardState s) {
		if (s.contentScale == 1f && s.originX == 0.5f && s.originY == BoardGeometry.TOP && s.originZ == 0.5f) return;
		pose.translate(s.originX, s.originY, s.originZ);
		pose.scale(s.contentScale, s.contentScale, s.contentScale);
		pose.translate(-0.5f, -BoardGeometry.TOP, -0.5f);
	}

	/**
	 * For a game renderer's own extra drawing (wheel, print, dice): rotates by FACING and applies the design → drawn
	 * mapping, so content written in design coordinates lands on the full felt (or the compact block top). Push the
	 * pose before, pop after.
	 */
	public static void feltPose(PoseStack pose, BoardState s) {
		pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0.5f, 0f, 0.5f);
		mapDesign(pose, s);
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
	public static void seatPlates(BoardState s, CasinoTableBlockEntity be, SeatSpot spot, @Nullable PlateDetail detail) {
		if (!s.showsPlates()) return;
		TablePublic.SeatPub[] seats = be.clientSeats();
		long seq = be.clientSeatsSeq();
		for (int i = 0; i < seats.length; i++) {
			TablePublic.SeatPub pub = seats[i];
			if (pub == null) continue;
			float[] sp = spot.at(i, seats.length);
			float[] a = BoardGeometry.plateAnchor(s.felt, sp[0], sp[1]); // a full table moves it past its rail (BoardState.plate)
			BoardState.PlateRef ref = s.plate(i, a[0], a[1], a[2]);
			if (ref == null) return;
			boolean you = pub.is(s.viewer);
			ref.name(seq * 31 + i, pub::displayName, 0).viewer(you);
			if (detail != null) detail.fill(i, pub, ref);
		}
	}

	/** Height of a full table's plate anchors (footprint-local y): just above the padded rail top (15.5/16). */
	public static final float FULL_PLATE_Y = FullTableMap.PLATE_Y;

	/**
	 * A full table's plate anchor for the seat whose design spot is (sx, sz), in DESIGN coordinates: from the dealer
	 * (crescent) or the felt centre through the seat's spot, out to the middle of the padded rail — the seat point on the
	 * rail, where the leader line ends — at {@link #FULL_PLATE_Y}. The plate itself floats above its content and the
	 * near rail edge (never behind the felt edge, D2).
	 */
	static float[] fullPlateAnchor(BoardState s, FeltFrame f, float sx, float sz) {
		FullTableMap map = s.map;
		if (map == null) map = new FullTableMap(s.contentScale, s.originX, s.originY, s.originZ);
		float[] a = map.plateAnchor(f, s.felt, sx, sz);
		return new float[] {map.designX(a[0]), map.designY(a[1]), map.designZ(a[2])};
	}

	/**
	 * Registers a demo filler for the table at {@code pos} (GameTests, design captures): called INSTEAD of
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
