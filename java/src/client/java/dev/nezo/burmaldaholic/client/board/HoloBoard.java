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
	private static final Vector3f P = new Vector3f();
	private static final HoloText.Spec SPEC = new HoloText.Spec();
	/** The board's frame: gold, the table's accent. */
	static final int FRAME = 0xFFFFD640;

	private HoloBoard() {}

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
			Vector3f bottom = new Vector3f(P);
			st.backings += HoloText.panel(pose, out, font, bottom, f.yawOnly, s.boardPx, sp);
			st.lines += n;
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
