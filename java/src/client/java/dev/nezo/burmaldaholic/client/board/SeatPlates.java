package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.core.table.parts.FeltFrame;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.PlateLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Vector3f;

/**
 * Seat plates (ui-v2.md §7.4): each plate's anchor (the seat rail edge outside the felt) and every piece of felt
 * content (cards, stacks, the dealer card row, extra obstacles) are projected ALONG THE VIEW RAY into the camera plane
 * through the table centre; {@link PlateLayout} places the plates above THEIR content and apart from each other
 * (viewer → acting → nearest), with a 1-frame memory against jitter; displaced plates get a leader line. Plates are
 * full billboards in that plane. Across tables, {@link PlateFrame} fades out a plate that would overlap a plate of a
 * better-ranked neighbour. Render thread only; all work arrays are static scratch (finding R-KW m11).
 */
final class SeatPlates {
	/** Gap between plates and above the content (blocks). */
	static final float GAP = 0.03f;
	/** Samples per edge of the table top outline (near-rail obstacles). */
	private static final int RAIL_SAMPLES = 8;
	static final int MAX_OBS = BoardModel.MAX_CARDS + BoardModel.MAX_STACKS + BoardModel.MAX_CONTENT + 1 + 4 * RAIL_SAMPLES;
	/** Below this fade a plate is not drawn at all (vanilla text treats alpha < 4/255 as opaque). */
	static final float MIN_ALPHA = 0.04f;

	// scratch (render thread only)
	private static final int MP = BoardModel.MAX_PLATES;
	private static final float[] AU = new float[MP], AV = new float[MP], W = new float[MP], H = new float[MP], OU = new float[MP], OV = new float[MP],
		NEAR = new float[MP];
	private static final int[] ORDER = new int[MP];
	private static final boolean[] LEADER = new boolean[MP];
	private static final float[] O0 = new float[MAX_OBS], O1 = new float[MAX_OBS], OT = new float[MAX_OBS];
	private static final int[] DISCS = new int[BoardGeometry.CHIP_MAX];
	private static final float[] RX = new float[5], RZ = new float[5];
	private static final PlateLayout.Scratch LAYOUT = new PlateLayout.Scratch(MP);
	private static final Vector3f P = new Vector3f(), Q = new Vector3f(), B = new Vector3f(), L = new Vector3f();
	private static final HoloText.Spec[] SPECS = new HoloText.Spec[MP];
	/** Plate frames: other seats, the viewer, the acting seat. */
	static final int FRAME = 0xFF783CBE, FRAME_YOU = 0xFF5CE8E0, FRAME_ACTING = 0xFFFFD640;

	static {
		for (int i = 0; i < SPECS.length; i++) SPECS[i] = new HoloText.Spec();
	}

	private SeatPlates() {}

	/**
	 * Lays out and draws the plates; returns the highest plate top in plane v (or the content top when there are no
	 * plates), so the board can sit above everything.
	 */
	static float submit(PoseStack pose, SubmitNodeCollector out, Font font, BoardState s, BoardModel m, HoloFrame f, BoardStats.Table st) {
		int obs = obstacles(m, f, s);
		float contentTop = Float.NEGATIVE_INFINITY;
		for (int k = 0; k < obs; k++) contentTop = Math.max(contentTop, OT[k]);
		int n = 0;
		float px = s.platePx;
		for (int i = 0; i < m.plateCount; i++) {
			BoardModel.Plate p = m.plates[i];
			if (!p.on || !p.name.on) continue;
			f.toBlock(p.ax, p.ay, p.az, P);
			f.project(P);
			AU[i] = f.pu;
			AV[i] = f.pv;
			HoloText.Spec sp = SPECS[i];
			sp.clear();
			sp.back = s.back;
			if (p.cardCount > 0 && s.dist <= BoardState.FAN_RANGE) sp.cards(p.cards, p.cardCount);
			sp.add(p.name.seq, p.name.width, p.name.color != 0 ? p.name.color : p.viewer ? BoardState.CYAN : BoardState.WHITE, 1f);
			if (p.detail.on) sp.add(p.detail.seq, p.detail.width, p.detail.color, p.detail.scale);
			sp.border = p.acting ? FRAME_ACTING : p.viewer ? FRAME_YOU : FRAME;
			// the frame (1 px) is part of the footprint
			W[i] = (sp.width() + 2) * px;
			H[i] = (sp.height() + 2) * px;
			NEAR[i] = f.dist(P) - (p.viewer ? 1000 : 0) - (p.acting ? 100 : 0);
			ORDER[n++] = i;
		}
		if (n == 0) return contentTop;
		// priority: viewer → acting → nearest (insertion sort, n ≤ 8)
		for (int a = 1; a < n; a++) {
			int x = ORDER[a];
			int b = a - 1;
			while (b >= 0 && NEAR[ORDER[b]] > NEAR[x]) {
				ORDER[b + 1] = ORDER[b];
				b--;
			}
			ORDER[b + 1] = x;
		}
		PlateLayout.place(AU, AV, W, H, ORDER, n, O0, O1, OT, obs, GAP, m.plateShift, OU, OV, LEADER, LAYOUT);
		long key = s.blockPos.asLong();
		PlateLayout.TablePlates frame = PlateFrame.table(key, PlateFrame.rank(s));
		float step = PlateFrame.dtNs() / (float) PlateFrame.FADE_NS;
		float top = contentTop;
		for (int a = 0; a < n; a++) {
			int i = ORDER[a];
			BoardModel.Plate p = m.plates[i];
			int id = p.seat;
			float x0 = f.sx(OU[i] - W[i] / 2), y0 = f.sy(OV[i]), x1 = f.sx(OU[i] + W[i] / 2), y1 = f.sy(OV[i] + H[i]);
			boolean want = PlateFrame.shown(key, id);
			frame.add(id, x0, y0, x1, y1, want);
			if (p.seenFrame != PlateFrame.previous()) p.alpha = 0; // not drawn last frame: fade in
			p.seenFrame = PlateFrame.current();
			p.alpha = want ? Math.min(1, p.alpha + step) : Math.max(0, p.alpha - step);
			top = Math.max(top, OV[i] + H[i]);
			PlateFrame.drawn(x0, y0, x1, y1, p.alpha >= 0.5f);
			if (p.alpha < MIN_ALPHA) continue;
			// every plate lives in the camera plane through the table centre: one depth, so the plane layout (disjoint
			// rectangles) is exactly what the screen shows — no perspective overlap between near and far seats
			f.onPlane(OU[i], OV[i] + px, B);
			HoloText.Spec sp = SPECS[i];
			sp.alpha = p.alpha;
			st.backings += HoloText.panel(pose, out, font, B, f.orientation, px, sp);
			st.lines += sp.count;
			st.plates++;
			if (LEADER[i]) {
				f.onPlane(AU[i], AV[i] - 0.02f, L);
				HoloText.leader(pose, out, f, B, L, 0.012f, (int) (0x99 * p.alpha) << 24 | 0xFFD640);
				st.leaders++;
			}
			if (st.plateRectCount < st.plateRects.length / 4) {
				int r = st.plateRectCount++ * 4;
				st.plateRects[r] = OU[i] - W[i] / 2;
				st.plateRects[r + 1] = OV[i];
				st.plateRects[r + 2] = OU[i] + W[i] / 2;
				st.plateRects[r + 3] = OV[i] + H[i];
			}
		}
		return top;
	}

	/** Projects cards, stacks, the dealer row and extra content into plane obstacles (u-range + top v); returns their count. */
	private static int obstacles(BoardModel m, HoloFrame f, BoardState s) {
		int n = 0;
		float hx = f.fwd.x, hz = f.fwd.z;
		float hl = (float) Math.sqrt(hx * hx + hz * hz);
		if (hl < 1e-3f) {
			hx = 0;
			hz = 1;
		} else {
			hx /= hl;
			hz /= hl;
		}
		for (int i = 0; i < m.cardCount && n < MAX_OBS; i++) {
			BoardCard c = m.cards[i];
			float r = (float) Math.sqrt(c.w * c.w + c.h * c.h) / 2;
			float lean = (float) Math.sin(Math.toRadians(c.tilt)) * c.h;
			n = add(f, n, c.x, BoardGeometry.TOP + BoardGeometry.CARD_LIFT + c.lift + lean, c.z, r, hx, hz);
		}
		for (int i = 0; i < m.stackCount && n < MAX_OBS; i++) {
			int discs = BoardGeometry.discs(m.stackAmount[i], DISCS);
			n = add(f, n, m.stackX[i], BoardGeometry.TOP + discs * BoardGeometry.CHIP_H, m.stackZ[i], BoardGeometry.CHIP_R, hx, hz);
		}
		for (int i = 0; i < m.contentCount && n < MAX_OBS; i++) {
			int k = i * 4;
			n = add(f, n, m.content[k], m.content[k + 1], m.content[k + 2], m.content[k + 3], hx, hz);
		}
		if (m.hasDealer && n < MAX_OBS) n = rect(f, n, m.dealer);
		n = rail(f, n, s);
		return n;
	}

	/**
	 * The table top's outer edge (the padded rail of a full table, the block top of a compact one) where it lies IN
	 * FRONT of the plate plane: it would hide the bottom of a plate drawn behind it (D2: at 3 blocks the name plate sank
	 * behind the felt edge), so plates stay above its projection. The far edge lies behind the plates and is ignored.
	 */
	private static int rail(HoloFrame f, int n, BoardState s) {
		float x0, z0, x1, z1, y;
		if (s.full && s.frame != null) {
			x0 = -1;
			x1 = 2;
			z0 = -1;
			z1 = 1;
			y = s.felt == BoardGeometry.Felt.RECT && !s.frame.hasWheel() ? 1f : (float) FeltFrame.RAIL_TOP;
		} else {
			x0 = 0;
			x1 = 1;
			z0 = 0;
			z1 = 1;
			y = 1f;
		}
		// the outline x0z0 → x1z0 → x1z1 → x0z1 → x0z0, each edge in RAIL_SAMPLES segments
		float[] cx = RX, cz = RZ;
		cx[0] = cx[3] = cx[4] = x0;
		cx[1] = cx[2] = x1;
		cz[0] = cz[1] = cz[4] = z0;
		cz[2] = cz[3] = z1;
		for (int e = 0; e < 4; e++) {
			float pu = 0, pv = 0;
			boolean prev = false;
			for (int k = 0; k <= RAIL_SAMPLES && n < MAX_OBS; k++) {
				float t = k / (float) RAIL_SAMPLES;
				f.footToBlock(cx[e] + (cx[e + 1] - cx[e]) * t, y, cz[e] + (cz[e + 1] - cz[e]) * t, Q);
				f.project(Q);
				boolean front = f.pz > 0.05f && f.pz < f.depth0;
				if (front && prev) {
					O0[n] = Math.min(pu, f.pu);
					O1[n] = Math.max(pu, f.pu);
					OT[n] = Math.max(pv, f.pv);
					n++;
				}
				pu = f.pu;
				pv = f.pv;
				prev = front;
			}
		}
		return n;
	}

	private static int add(HoloFrame f, int n, float x, float y, float z, float r, float hx, float hz) {
		f.toBlock(x, y, z, Q);
		f.project(Q);
		float u = f.pu, rs = r * f.ps;
		// the far edge of a flat object rises highest in the camera plane
		Q.add(hx * r, 0, hz * r);
		f.project(Q);
		O0[n] = u - rs;
		O1[n] = u + rs;
		OT[n] = f.pv;
		return n + 1;
	}

	/** The dealer card area: its four top corners projected; u-range and the highest v. */
	private static int rect(HoloFrame f, int n, float[] d) {
		float u0 = Float.POSITIVE_INFINITY, u1 = Float.NEGATIVE_INFINITY, top = Float.NEGATIVE_INFINITY;
		for (int c = 0; c < 4; c++) {
			f.toBlock((c & 1) == 0 ? d[0] : d[2], d[4], (c & 2) == 0 ? d[1] : d[3], Q);
			f.project(Q);
			u0 = Math.min(u0, f.pu);
			u1 = Math.max(u1, f.pu);
			top = Math.max(top, f.pv);
		}
		O0[n] = u0;
		O1[n] = u1;
		OT[n] = top;
		return n + 1;
	}
}
