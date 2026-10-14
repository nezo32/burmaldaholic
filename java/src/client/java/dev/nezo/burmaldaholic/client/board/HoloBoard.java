package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector3f;

/**
 * The table board (ui-v2.md §7.1, §2.4): an upright, YAW-ONLY billboard over the dealer side, {@link
 * BoardGeometry#BOARD_UP} above the felt and always above the plates and the content; ≤ 3 lines (title gold, state,
 * turn / result) at {@link HoloScale}'s distance-scaled text px. 8–16 blocks: state + result lines only. Also draws
 * the free labels (legacy billboard texts such as the poker pot).
 */
final class HoloBoard {
	private static final Vector3f P = new Vector3f(), C = new Vector3f();
	private static final HoloText.Spec SPEC = new HoloText.Spec();
	/** The board's frame: gold, the table's accent. */
	static final int FRAME = 0xFFFFD640;

	private HoloBoard() {}

	/** Registers the board's screen rectangle as a fixed occupant other tables' plates keep clear of ({@link PlateFrame}). */
	private static void occupy(BoardState s, HoloFrame f, Vector3f bottom, float halfW, float h) {
		float x0 = Float.POSITIVE_INFINITY, y0 = Float.POSITIVE_INFINITY, x1 = Float.NEGATIVE_INFINITY, y1 = Float.NEGATIVE_INFINITY;
		for (int c = 0; c < 4; c++) {
			C.set((c & 1) == 0 ? -halfW : halfW, (c & 2) == 0 ? 0 : h, 0).rotate(f.yawOnly).add(bottom);
			float x = f.screenX(C), y = f.screenY(C);
			if (Float.isNaN(x) || Float.isNaN(y)) return;
			x0 = Math.min(x0, x);
			x1 = Math.max(x1, x);
			y0 = Math.min(y0, y);
			y1 = Math.max(y1, y);
		}
		PlateFrame.table(s.blockPos.asLong(), PlateFrame.rank(s)).addFixed(x0, y0, x1, y1);
	}

	static void submit(PoseStack pose, SubmitNodeCollector out, Font font, BoardState s, BoardModel m, HoloFrame f, float minV, BoardStats.Table st) {
		HoloText.Spec sp = SPEC;
		sp.clear();
		sp.back = s.back;
		boolean full = s.hlod == HoloScale.Lod.FULL;
		if (m.boardCardCount > 0 && s.dist <= BoardState.FAN_RANGE) sp.cards(m.boardCards, m.boardCardCount);
		for (int i = 0; i < BoardModel.MAX_LINES; i++) {
			BoardModel.Line l = m.lines[i];
			if (!l.on || i == BoardState.TITLE && !full) continue;
			sp.add(l.seq, l.width, l.color, l.scale);
		}
		sp.border = FRAME;
		int n = sp.count;
		if (n > 0) {
			float[] a = BoardGeometry.boardAnchor(s.felt);
			f.toBlock(a[0], a[1], a[2], P);
			// above the plates and the content (plane v), with a gap
			float v = f.v(P);
			float need = minV + 0.06f;
			if (v < need && f.up.y > 0.2f) P.y += (need - v) / f.up.y;
			st.backings += HoloText.panel(pose, out, font, P, f.yawOnly, s.boardPx, sp);
			st.lines += n;
			occupy(s, f, P, sp.width() * s.boardPx / 2, sp.height() * s.boardPx);
		}
		// free labels: small full billboards (no backing), within 8 blocks
		for (int i = 0; i < m.labelCount; i++) {
			BoardModel.Label l = m.labels[i];
			if (l.seq == null) continue;
			f.toBlock(l.x, l.y, l.z, P);
			pose.pushPose();
			pose.translate(P.x, P.y, P.z);
			pose.rotateAround(f.orientation, 0f, 0f, 0f);
			float px = s.platePx;
			pose.scale(px, -px, px);
			out.submitText(pose, -font.width(l.seq) / 2f, 0, l.seq, false, Font.DisplayMode.POLYGON_OFFSET, Quads.FULL_BRIGHT, l.color, HoloText.BACKING, 0);
			pose.popPose();
			st.lines++;
		}
	}
}
