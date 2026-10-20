package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Chip stacks on the felt (ui-v2.md §7.1, REDESIGN §9.1): ≤ {@link BoardGeometry#CHIP_MAX} discs of
 * {@link BoardGeometry#CHIP_R} radius, largest denomination at the bottom, in piles of ≤ {@link BoardGeometry#CHIP_PILE}
 * (a second pile stands beside the first). Consecutive discs of one denomination share one octagonal band (8 quads,
 * back faces culled) shaded darker toward each disc run's bottom edge, the top disc one quad; a soft contact shadow
 * (translucent octagon) sits on the felt under each pile.
 */
public final class ChipPainter {
	private static final int SIDES = 8;
	private static final float[] COS = new float[SIDES + 1], SIN = new float[SIDES + 1];

	static {
		for (int i = 0; i <= SIDES; i++) {
			double a = 2 * Math.PI * i / SIDES + Math.PI / SIDES;
			COS[i] = (float) Math.cos(a);
			SIN[i] = (float) Math.sin(a);
		}
	}

	private ChipPainter() {}

	/** Contact shadow: colour and how far the octagon reaches past the chip. */
	private static final int SHADOW = 0x50000000;
	private static final float SHADOW_R = 1.18f;
	/** Side band shade at the bottom of a disc run (the top edge is full white). */
	private static final int BAND_BOTTOM = 0xFFA8A8A8;

	/** Quads a stack of {@code amount} costs (budget accounting). */
	public static int quads(long amount) {
		int[] d = new int[BoardGeometry.CHIP_MAX];
		int n = BoardGeometry.discs(amount, d);
		if (n == 0) return 0;
		int q = 0;
		for (int start = 0; start < n; start += BoardGeometry.CHIP_PILE) {
			int end = Math.min(n, start + BoardGeometry.CHIP_PILE);
			int runs = 1;
			for (int i = start + 1; i < end; i++) if (d[i] != d[i - 1]) runs++;
			q += runs * SIDES + 1 + SIDES;
		}
		return q;
	}

	/** x offset of pile {@code k} of a stack (table-local; the second pile stands to the right). */
	private static float pileX(int k) {
		return k * BoardGeometry.CHIP_R * 2.15f;
	}

	static void submit(PoseStack pose, SubmitNodeCollector out, BoardModel m, int light) {
		int count = m.stackCount;
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(BoardTextures.BACKING), (p, vc) -> {
			int[] d = new int[BoardGeometry.CHIP_MAX];
			for (int i = 0; i < count; i++) {
				if (m.stackAlpha[i] < 0.5f) continue;
				int n = BoardGeometry.discs(m.stackAmount[i], d);
				for (int k = 0; k * BoardGeometry.CHIP_PILE < n; k++) shadow(p, vc, m.stackX[i] + pileX(k), m.stackZ[i], light);
			}
		});
		out.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(BoardTextures.CHIPS), (p, vc) -> {
			int[] d = new int[BoardGeometry.CHIP_MAX];
			for (int i = 0; i < count; i++) {
				if (m.stackAlpha[i] < 0.5f) continue;
				int n = BoardGeometry.discs(m.stackAmount[i], d);
				for (int k = 0; k * BoardGeometry.CHIP_PILE < n; k++) {
					pile(p, vc, m.stackX[i] + pileX(k), m.stackZ[i], d, k * BoardGeometry.CHIP_PILE, Math.min(n, (k + 1) * BoardGeometry.CHIP_PILE), light);
				}
			}
		});
	}

	/** A soft octagonal shadow on the felt, offset a little away from the light (toward +x, +z). */
	private static void shadow(PoseStack.Pose p, VertexConsumer vc, float x, float z, int light) {
		float r = BoardGeometry.CHIP_R * SHADOW_R;
		float y = BoardGeometry.TOP + BoardGeometry.CHIP_SHADOW_Y;
		float cx = x + 0.012f, cz = z + 0.016f;
		for (int s = 0; s < SIDES; s++) {
			Quads.v(p, vc, cx, y, cz, 0.5f, 0.5f, SHADOW, light, 0, 1, 0);
			Quads.v(p, vc, cx + r * COS[s + 1], y, cz + r * SIN[s + 1], 0.5f, 0.5f, SHADOW, light, 0, 1, 0);
			Quads.v(p, vc, cx + r * COS[s], y, cz + r * SIN[s], 0.5f, 0.5f, SHADOW, light, 0, 1, 0);
			Quads.v(p, vc, cx, y, cz, 0.5f, 0.5f, SHADOW, light, 0, 1, 0);
		}
	}

	/** Discs {@code from}..{@code to} of {@code d} as one pile at (x, z). */
	private static void pile(PoseStack.Pose p, VertexConsumer vc, float x, float z, int[] d, int from, int to, int light) {
		int n = to - from;
		if (n <= 0) return;
		float r = BoardGeometry.CHIP_R;
		float h = BoardGeometry.CHIP_H;
		float y0 = BoardGeometry.TOP + BoardGeometry.CHIP_BASE_Y;
		int i = 0;
		while (i < n) {
			int k = i;
			while (k < n && d[from + k] == d[from + i]) k++;
			int run = k - i;
			int den = d[from + i];
			// band texture: column d, the top `run` discs of the 6-disc side strip
			float u0 = den * 16f / BoardTextures.CHIPS_W;
			float u1 = (den * 16f + 16f) / BoardTextures.CHIPS_W;
			float v0 = BoardTextures.CHIP_TOP / (float) BoardTextures.CHIPS_H;
			float v1 = (BoardTextures.CHIP_TOP + BoardTextures.CHIP_BAND * run) / (float) BoardTextures.CHIPS_H;
			float yb = y0 + i * h;
			float yt = y0 + k * h;
			for (int s = 0; s < SIDES; s++) {
				float ax = x + r * COS[s], az = z + r * SIN[s];
				float bx = x + r * COS[s + 1], bz = z + r * SIN[s + 1];
				float nx = (COS[s] + COS[s + 1]) / 2, nz = (SIN[s] + SIN[s + 1]) / 2;
				float ua = u0 + (u1 - u0) * s / SIDES, ub = u0 + (u1 - u0) * (s + 1) / SIDES;
				int col = 0xFFFFFFFF;
				// outward-facing winding (seen from outside: a → top a → top b → b reversed for CCW); darker at the bottom
				Quads.v(p, vc, bx, yt, bz, ub, v0, col, light, nx, 0, nz);
				Quads.v(p, vc, bx, yb, bz, ub, v1, BAND_BOTTOM, light, nx, 0, nz);
				Quads.v(p, vc, ax, yb, az, ua, v1, BAND_BOTTOM, light, nx, 0, nz);
				Quads.v(p, vc, ax, yt, az, ua, v0, col, light, nx, 0, nz);
			}
			i = k;
		}
		// top disc of the highest denomination run
		int top = d[to - 1];
		float yt = y0 + n * h;
		float u0 = top * 16f / BoardTextures.CHIPS_W, u1 = (top * 16f + 16f) / BoardTextures.CHIPS_W;
		float v1 = 16f / BoardTextures.CHIPS_H;
		int col = 0xFFFFFFFF;
		Quads.v(p, vc, x - r, yt, z + r, u0, v1, col, light, 0, 1, 0);
		Quads.v(p, vc, x + r, yt, z + r, u1, v1, col, light, 0, 1, 0);
		Quads.v(p, vc, x + r, yt, z - r, u1, 0, col, light, 0, 1, 0);
		Quads.v(p, vc, x - r, yt, z - r, u0, 0, col, light, 0, 1, 0);
	}
}
