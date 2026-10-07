package dev.nezo.burmaldaholic.client.table.cards;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import dev.nezo.burmaldaholic.core.ui.v2.board.PlateLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;

/**
 * The readable part of a card table in the world (docs/architecture/ui-v2.md §2.4, §7.3, §7.4): the table BOARD (an
 * upright, yaw-only billboard over the dealer side, ≤ 3 lines on an ink backing) and the SEAT PLATES (name + one
 * value line, at each seat's rail edge, pushed above the projected felt content and resolved collision-free in the
 * camera plane by {@link PlateLayout}). Text size grows with distance ({@link HoloScale}) so the glyph stays ≥ 13 px
 * from 3 to 8 blocks. Full-bright, depth-tested, no shadow.
 *
 * <p>Filled during extract (sequences cached by the renderer, rebuilt only when the public data changes), drawn in
 * submit. Block-local coordinates in the table's FACING frame ({@code CardTableRenderer} convention).
 */
public final class BoardHolo {
	public static final int MAX_BOARD = 3, MAX_PLATES = 8;
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Backing: ink at 70 %. */
	public static final int BACKING = 0xB3180A28;
	public static final int GOLD = 0xFFFFD640, TEXT = 0xFFF4ECF8, MUTED = 0xFFC0B0DC, YOU = 0xFF5CE8E0, WIN = 0xFF80FF40, LOSE = 0xFFFF6E6A,
		PUSH = 0xFF8FA8C8;

	public final FormattedCharSequence[] board = new FormattedCharSequence[MAX_BOARD];
	public final int[] boardColor = new int[MAX_BOARD];
	public int boardLines;
	public float bx, by, bz;
	public final FormattedCharSequence[] plate1 = new FormattedCharSequence[MAX_PLATES];
	public final FormattedCharSequence[] plate2 = new FormattedCharSequence[MAX_PLATES];
	public final int[] color1 = new int[MAX_PLATES], color2 = new int[MAX_PLATES];
	public final float[] px = new float[MAX_PLATES], py = new float[MAX_PLATES], pz = new float[MAX_PLATES];
	/** Placement priority: 0 = the viewer, 1 = acting, 2 = others. */
	public final int[] prio = new int[MAX_PLATES];
	public int plates;
	/** Highest y of the felt content (cards, stacks) in block-local units. */
	public float contentTop = 1.05f;
	/** Felt content points (block-local x, z pairs: card centres, stacks); plates are pushed above all of them. */
	public final float[] content = new float[96];
	public int contentCount;
	public boolean showBoard, showPlates;
	/** Distance to the camera (blocks), for {@link HoloScale}. */
	public double distance;
	/** Only the first {@code boardLinesFar} lines between 8 and 16 blocks (state + result). */
	public int boardLinesFar = 2;

	public void clear() {
		boardLines = 0;
		plates = 0;
		contentCount = 0;
		contentTop = 1.05f;
	}

	public void boardLine(FormattedCharSequence s, int color) {
		if (s == null || boardLines >= MAX_BOARD) return;
		board[boardLines] = s;
		boardColor[boardLines++] = color;
	}

	public void content(float x, float z) {
		if (contentCount * 2 >= content.length) return;
		content[contentCount * 2] = x;
		content[contentCount * 2 + 1] = z;
		contentCount++;
	}

	public void plate(float x, float y, float z, FormattedCharSequence l1, int c1, FormattedCharSequence l2, int c2, int priority) {
		if (plates >= MAX_PLATES || l1 == null) return;
		int i = plates++;
		px[i] = x;
		py[i] = y;
		pz[i] = z;
		plate1[i] = l1;
		plate2[i] = l2;
		color1[i] = c1;
		color2[i] = c2;
		prio[i] = priority;
	}

	/** Sets visibility by distance (LOD with the base's thresholds; the hysteresis lives in the renderer's cache). */
	public void lod(double distance) {
		this.distance = distance;
		HoloScale.Lod l = HoloScale.lod(distance);
		showBoard = l == HoloScale.Lod.FULL || l == HoloScale.Lod.BOARD;
		showPlates = l == HoloScale.Lod.FULL;
	}

	// ---- submit ------------------------------------------------------------------------------------------------

	/**
	 * Draws board and plates. {@code pose} is at the block origin, NOT rotated by facing (the method rotates the
	 * local anchors itself, so the plane math is in world axes).
	 */
	public void submit(PoseStack pose, SubmitNodeCollector out, CameraRenderState camera, Direction facing, Font font) {
		if (!showBoard && !showPlates) return;
		Minecraft mc = Minecraft.getInstance();
		double fov = mc.options == null ? 70 : mc.options.fov().get();
		int screenH = mc.getWindow() == null ? 1080 : mc.getWindow().getHeight();
		float yaw = camera.yRot;
		double yr = Math.toRadians(yaw);
		double pr = Math.toRadians(camera.xRot);
		// camera right / up in world axes (MC: yaw 0 looks south, pitch > 0 looks down)
		float rx = (float) -Math.cos(yr), rz = (float) -Math.sin(yr);
		float ux = (float) (-Math.sin(yr) * Math.sin(pr)), uy = (float) Math.cos(pr), uz = (float) (Math.cos(yr) * Math.sin(pr));
		double th = Math.toRadians(-facing.toYRot());
		float cos = (float) Math.cos(th), sin = (float) Math.sin(th);
		if (showBoard && boardLines > 0) {
			float tp = HoloScale.textPx(HoloScale.BOARD_BASE, distance, fov, screenH);
			int lines = distance > HoloScale.READ_RANGE ? Math.min(boardLines, boardLinesFar) : boardLines;
			float[] w = world(bx, by, bz, cos, sin);
			int maxW = 0;
			for (int i = 0; i < lines; i++) maxW = Math.max(maxW, font.width(board[i]));
			panel(pose, out, font, w[0], w[1], w[2], yaw, tp, board, boardColor, 0, lines, maxW);
		}
		if (!showPlates || plates == 0) return;
		float tp = HoloScale.textPx(HoloScale.PLATE_BASE, distance, fov, screenH);
		float[] au = new float[plates], av = new float[plates], pw = new float[plates], ph = new float[plates];
		float[][] wp = new float[plates][];
		float cx = 0.5f, cy = 1f, cz = 0.5f;
		for (int i = 0; i < plates; i++) {
			wp[i] = world(px[i], py[i], pz[i], cos, sin);
			float dx = wp[i][0] - cx, dy = wp[i][1] - cy, dz = wp[i][2] - cz;
			au[i] = dx * rx + dz * rz;
			av[i] = dx * ux + dy * uy + dz * uz;
			int tw = Math.max(font.width(plate1[i]), plate2[i] == null ? 0 : font.width(plate2[i]));
			pw[i] = (tw + 4) * tp;
			ph[i] = (plate2[i] == null ? 10 : 20) * tp + 2 * tp;
		}
		// content top in the plane: the highest projected card / stack (plates never cover a card)
		float top = Float.NEGATIVE_INFINITY;
		for (int k = 0; k < contentCount; k++) {
			float[] c = world(content[k * 2], contentTop, content[k * 2 + 1], cos, sin);
			float dx = c[0] - cx, dy = c[1] - cy, dz = c[2] - cz;
			top = Math.max(top, dx * ux + dy * uy + dz * uz);
		}
		if (contentCount == 0) top = minOf(av);
		Integer[] ord = new Integer[plates];
		for (int i = 0; i < plates; i++) ord[i] = i;
		java.util.Arrays.sort(ord, (a, b) -> prio[a] != prio[b] ? Integer.compare(prio[a], prio[b]) : Integer.compare(a, b));
		int[] order = new int[plates];
		for (int i = 0; i < plates; i++) order[i] = ord[i];
		float[] ou = new float[plates], ov = new float[plates];
		boolean[] leader = new boolean[plates];
		PlateLayout.place(au, av, pw, ph, order, top, 2 * tp, ou, ov, leader);
		for (int i = 0; i < plates; i++) {
			float du = ou[i] - au[i], dv = ov[i] - av[i];
			float x = wp[i][0] + rx * du + ux * dv;
			float y = wp[i][1] + uy * dv;
			float z = wp[i][2] + rz * du + uz * dv;
			FormattedCharSequence[] ls = {plate1[i], plate2[i]};
			int[] cs = {color1[i], color2[i]};
			int n = plate2[i] == null ? 1 : 2;
			int maxW = 0;
			for (int k = 0; k < n; k++) maxW = Math.max(maxW, font.width(ls[k]));
			panel(pose, out, font, x, y, z, yaw, tp, ls, cs, 0, n, maxW);
		}
	}

	private static float minOf(float[] a) {
		float m = Float.POSITIVE_INFINITY;
		for (float f : a) m = Math.min(m, f);
		return m;
	}

	/** Block-local (facing frame) → block-local world axes. */
	private static float[] world(float x, float y, float z, float cos, float sin) {
		float dx = x - 0.5f, dz = z - 0.5f;
		return new float[] {0.5f + dx * cos + dz * sin, y, 0.5f - dx * sin + dz * cos};
	}

	/** A panel of lines whose bottom-centre is at (x, y, z), yaw-only billboard. */
	private static void panel(PoseStack pose, SubmitNodeCollector out, Font font, float x, float y, float z, float yaw, float tp,
			FormattedCharSequence[] lines, int[] colors, int from, int n, int maxW) {
		int lh = 10;
		int h = n * lh + 2;
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
		pose.scale(tp, -tp, tp);
		float left = -maxW / 2f - 3;
		float right = maxW / 2f + 3;
		float topY = -h;
		out.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, vc) -> quad(p, vc, left, topY, right, 0, BACKING));
		pose.translate(0, 0, -0.01f);
		for (int i = 0; i < n; i++) {
			FormattedCharSequence s = lines[from + i];
			if (s == null) continue;
			out.submitText(pose, -font.width(s) / 2f, topY + 2 + i * lh, s, false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, colors[from + i], 0, 0);
		}
		pose.popPose();
	}

	private static void quad(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float x1, float y1, int argb) {
		vc.addVertex(p, x0, y0, 0).setColor(argb).setLight(FULL_BRIGHT);
		vc.addVertex(p, x0, y1, 0).setColor(argb).setLight(FULL_BRIGHT);
		vc.addVertex(p, x1, y1, 0).setColor(argb).setLight(FULL_BRIGHT);
		vc.addVertex(p, x1, y0, 0).setColor(argb).setLight(FULL_BRIGHT);
	}
}
