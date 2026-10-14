package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * The felt (ui-v2.md §7.1, MT-DESIGN §2). A full multi-block table: the furniture model is baked (legs, apron, rail,
 * a 16-px felt at 14/16); this paints the hi-res felt print decal ({@code felt_print_<shape>_<furniture>}, one cutout
 * quad) just above it over the footprint-local {@code FeltFrame}. A compact table: the block's own top with its printed
 * rail ({@code felt_w_fallback_<θ>}, one quad). The shaped felts with an apron ({@link #apron}) stay for previews.
 * Coordinates: the renderer has rotated by FACING; footprint-local = design for the compact quad.
 */
public final class FeltPainter {
	/** Segments per rounded corner of the apron. */
	private static final int SEG = 4;
	private static final float[][] OUTLINES = new float[Felt.values().length][];

	static {
		for (Felt f : Felt.values()) OUTLINES[f.ordinal()] = BoardGeometry.outline(f, SEG);
	}

	private FeltPainter() {}

	/** Lift of the print decal above the baked felt (no z-fighting, under the content lift of 1/64). */
	public static final float DECAL_LIFT = 1 / 128f;

	/** Number of quads {@link #submit} draws for a felt (budget accounting). */
	public static int quads(Felt f) {
		return f == Felt.FALLBACK ? 1 : 1 + 2 * (OUTLINES[f.ordinal()].length / 2);
	}

	public static int quads(BoardState s) {
		return s.full ? 1 : quads(s.drawnFelt);
	}

	public static void submit(PoseStack pose, SubmitNodeCollector out, BoardState s, int light) {
		var frame = s.frame;
		var game = s.tableGame;
		if (s.full && frame != null && game != null) {
			float x0 = (float) (frame.cx() - frame.w() / 2), x1 = (float) (frame.cx() + frame.w() / 2);
			float z0 = (float) (frame.cz() - frame.d() / 2), z1 = (float) (frame.cz() + frame.d() / 2);
			float y = (float) frame.top() + DECAL_LIFT;
			int fl = s.feltLight;
			out.submitCustomGeometry(pose, RenderTypes.entityCutout(BoardTextures.print(game, s.furniture)), (p, vc) -> {
				int c = 0xFFFFFFFF;
				Quads.v(p, vc, x0, y, z1, 0, 1, c, fl, 0, 1, 0);
				Quads.v(p, vc, x1, y, z1, 1, 1, c, fl, 0, 1, 0);
				Quads.v(p, vc, x1, y, z0, 1, 0, c, fl, 0, 1, 0);
				Quads.v(p, vc, x0, y, z0, 0, 0, c, fl, 0, 1, 0);
			});
			return;
		}
		Felt f = s.drawnFelt;
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(BoardTextures.felt(f, s.theme)), (p, vc) -> top(p, vc, f, light));
		if (f != Felt.FALLBACK) out.submitCustomGeometry(pose, RenderTypes.entityCutout(BoardTextures.rail(s.theme)), (p, vc) -> apron(p, vc, f, light));
	}

	private static void top(PoseStack.Pose p, VertexConsumer vc, Felt f, int light) {
		float y = BoardGeometry.TOP;
		int c = 0xFFFFFFFF;
		Quads.v(p, vc, f.x0(), y, f.z1(), 0, 1, c, light, 0, 1, 0);
		Quads.v(p, vc, f.x1(), y, f.z1(), 1, 1, c, light, 0, 1, 0);
		Quads.v(p, vc, f.x1(), y, f.z0(), 1, 0, c, light, 0, 1, 0);
		Quads.v(p, vc, f.x0(), y, f.z0(), 0, 0, c, light, 0, 1, 0);
	}

	private static void apron(PoseStack.Pose p, VertexConsumer vc, Felt f, int light) {
		float[] o = OUTLINES[f.ordinal()];
		int n = o.length / 2;
		float yTop = BoardGeometry.TOP;
		float yBot = BoardGeometry.TOP - BoardGeometry.APRON;
		int side = 0xFFD8D8D8;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float x0 = o[i * 2], z0 = o[i * 2 + 1], x1 = o[j * 2], z1 = o[j * 2 + 1];
			float dx = x1 - x0, dz = z1 - z0;
			float len = (float) Math.sqrt(dx * dx + dz * dz);
			if (len < 1e-5f) continue;
			// outward normal of a counter-clockwise walk (x right, z toward the front)
			float nx = -dz / len, nz = dx / len;
			// the apron texture is clamped (atlas-free entity texture): each segment spans u 0..min(1, len)
			float u0 = 0, u1 = Math.min(1f, len);
			Quads.v(p, vc, x0, yTop, z0, u0, 0, side, light, nx, 0, nz);
			Quads.v(p, vc, x0, yBot, z0, u0, 0.5f, side, light, nx, 0, nz);
			Quads.v(p, vc, x1, yBot, z1, u1, 0.5f, side, light, nx, 0, nz);
			Quads.v(p, vc, x1, yTop, z1, u1, 0, side, light, nx, 0, nz);
		}
		// underside: a fan of degenerate quads (triangles) from the centre, the dark half of the rail texture
		int under = 0xFF707070;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			Quads.v(p, vc, 0.5f, yBot, 0.5f, 0.5f, 0.75f, under, light, 0, -1, 0);
			Quads.v(p, vc, 0.5f, yBot, 0.5f, 0.5f, 0.75f, under, light, 0, -1, 0);
			Quads.v(p, vc, o[j * 2], yBot, o[j * 2 + 1], 0.9f, 1f, under, light, 0, -1, 0);
			Quads.v(p, vc, o[i * 2], yBot, o[i * 2 + 1], 0.1f, 1f, under, light, 0, -1, 0);
		}
	}
}
