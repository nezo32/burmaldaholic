package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.PlateLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector3f;

/**
 * Seat plates (ui-v2.md §7.4): each plate's anchor (rail edge outside the felt, 0.35 above it) and every piece of felt
 * content (cards, stacks, extra obstacles) are projected into the camera plane through the table centre; {@link
 * PlateLayout} places the plates above THEIR content and apart from each other (viewer → acting → nearest), with a
 * 1-frame memory against jitter; displaced plates get a leader line. Plates are full billboards in the camera plane.
 */
final class SeatPlates {
	/** Gap between plates and above the content (blocks). */
	static final float GAP = 0.03f;
	static final int MAX_OBS = BoardModel.MAX_CARDS + BoardModel.MAX_STACKS + BoardModel.MAX_CONTENT;

	// scratch (render thread only)
	private static final float[] AU = new float[BoardModel.MAX_PLATES], AV = new float[BoardModel.MAX_PLATES], W = new float[BoardModel.MAX_PLATES],
		H = new float[BoardModel.MAX_PLATES], OU = new float[BoardModel.MAX_PLATES], OV = new float[BoardModel.MAX_PLATES];
	private static final boolean[] LEADER = new boolean[BoardModel.MAX_PLATES];
	private static final float[] O0 = new float[MAX_OBS], O1 = new float[MAX_OBS], OT = new float[MAX_OBS];
	private static final Vector3f P = new Vector3f(), Q = new Vector3f();

	private SeatPlates() {}

	/**
	 * Lays out and draws the plates; returns the highest plate top in plane v (or the content top when there are no
	 * plates), so the board can sit above everything.
	 */
	static float submit(PoseStack pose, SubmitNodeCollector out, Font font, BoardState s, BoardModel m, HoloFrame f, BoardStats.Table st) {
		int obs = obstacles(m, f);
		float contentTop = Float.NEGATIVE_INFINITY;
		for (int k = 0; k < obs; k++) contentTop = Math.max(contentTop, OT[k]);
		int n = 0;
		for (int i = 0; i < m.plateCount; i++) if (m.plates[i].on && m.plates[i].name.on) n++;
		if (n == 0) return contentTop;
		int[] order = new int[n];
		float px = s.platePx;
		int k = 0;
		float[] near = new float[m.plateCount];
		for (int i = 0; i < m.plateCount; i++) {
			BoardModel.Plate p = m.plates[i];
			if (!p.on || !p.name.on) continue;
			f.toBlock(p.ax, p.ay, p.az, P);
			AU[i] = f.u(P);
			AV[i] = f.v(P);
			int lw = Math.max(p.name.width, p.detail.on ? p.detail.width : 0);
			W[i] = HoloText.width(lw) * px;
			H[i] = HoloText.height(p.detail.on ? 2 : 1) * px;
			near[i] = f.dist(P) - (p.viewer ? 1000 : 0) - (p.acting ? 100 : 0);
			order[k++] = i;
		}
		// priority: viewer → acting → nearest (insertion sort, n ≤ 8)
		for (int a = 1; a < n; a++) {
			int x = order[a];
			int b = a - 1;
			while (b >= 0 && near[order[b]] > near[x]) {
				order[b + 1] = order[b];
				b--;
			}
			order[b + 1] = x;
		}
		PlateLayout.place(AU, AV, W, H, order, O0, O1, OT, obs, GAP, m.plateShift, OU, OV, LEADER);
		float top = contentTop;
		FormattedCharSequence[] seqs = new FormattedCharSequence[2];
		int[] widths = new int[2];
		int[] colors = new int[2];
		for (int a = 0; a < n; a++) {
			int i = order[a];
			BoardModel.Plate p = m.plates[i];
			f.toBlock(p.ax, p.ay, p.az, P);
			Vector3f bottom = new Vector3f(P).add(new Vector3f(f.right).mul(OU[i] - AU[i])).add(new Vector3f(f.up).mul(OV[i] - AV[i]));
			int lines = 0;
			seqs[lines] = p.name.seq;
			widths[lines] = p.name.width;
			colors[lines++] = p.name.color != 0 ? p.name.color : p.viewer ? BoardState.CYAN : BoardState.WHITE;
			if (p.detail.on) {
				seqs[lines] = p.detail.seq;
				widths[lines] = p.detail.width;
				colors[lines++] = p.detail.color;
			}
			st.backings += HoloText.panel(pose, out, font, bottom, f.orientation, px, seqs, widths, colors, lines, p.acting ? BoardState.GOLD : 0);
			st.lines += lines;
			st.plates++;
			if (LEADER[i]) {
				HoloText.leader(pose, out, f, new Vector3f(bottom), new Vector3f(P).sub(new Vector3f(f.up).mul(0.02f)), 0.012f, 0x99FFD640);
				st.leaders++;
			}
			if (st.plateRectCount < st.plateRects.length / 4) {
				int r = st.plateRectCount++ * 4;
				st.plateRects[r] = OU[i] - W[i] / 2;
				st.plateRects[r + 1] = OV[i];
				st.plateRects[r + 2] = OU[i] + W[i] / 2;
				st.plateRects[r + 3] = OV[i] + H[i];
			}
			top = Math.max(top, OV[i] + H[i]);
		}
		return top;
	}

	/** Projects cards, stacks and extra content into plane obstacles (u-range + top v); returns their count. */
	private static int obstacles(BoardModel m, HoloFrame f) {
		int n = 0;
		Vector3f h = new Vector3f(f.fwd.x, 0, f.fwd.z);
		if (h.lengthSquared() < 1e-6f) h.set(0, 0, 1);
		h.normalize();
		for (int i = 0; i < m.cardCount && n < MAX_OBS; i++) {
			BoardCard c = m.cards[i];
			float r = (float) Math.sqrt(c.w * c.w + c.h * c.h) / 2;
			float lean = (float) Math.sin(Math.toRadians(c.tilt)) * c.h;
			n = add(f, n, c.x, BoardGeometry.TOP + BoardGeometry.CARD_LIFT + c.lift + lean, c.z, r, h);
		}
		for (int i = 0; i < m.stackCount && n < MAX_OBS; i++) {
			int[] d = new int[BoardGeometry.CHIP_MAX];
			int discs = BoardGeometry.discs(m.stackAmount[i], d);
			n = add(f, n, m.stackX[i], BoardGeometry.TOP + discs * BoardGeometry.CHIP_H, m.stackZ[i], BoardGeometry.CHIP_R, h);
		}
		for (int i = 0; i < m.contentCount && n < MAX_OBS; i++) {
			int k = i * 4;
			n = add(f, n, m.content[k], m.content[k + 1], m.content[k + 2], m.content[k + 3], h);
		}
		return n;
	}

	private static int add(HoloFrame f, int n, float x, float y, float z, float r, Vector3f h) {
		f.toBlock(x, y, z, Q);
		float u = f.u(Q);
		// the far edge of a flat object rises highest in the camera plane
		Q.add(h.x * r, 0, h.z * r);
		float v = f.v(Q);
		O0[n] = u - r;
		O1[n] = u + r;
		OT[n] = v;
		return n + 1;
	}
}
