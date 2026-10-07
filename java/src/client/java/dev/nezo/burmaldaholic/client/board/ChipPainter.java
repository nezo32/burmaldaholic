package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Chip stacks on the felt (ui-v2.md §7.1, REDESIGN §9.1): ≤ {@link BoardGeometry#CHIP_MAX} discs of
 * {@link BoardGeometry#CHIP_R} radius, largest denomination at the bottom. Consecutive discs of one denomination share
 * one octagonal band (8 quads, back faces culled), the top disc one quad: a typical stack is 9–17 quads.
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

	/** Quads a stack of {@code amount} costs (budget accounting). */
	public static int quads(long amount) {
		int[] d = new int[BoardGeometry.CHIP_MAX];
		int n = BoardGeometry.discs(amount, d);
		if (n == 0) return 0;
		int runs = 1;
		for (int i = 1; i < n; i++) if (d[i] != d[i - 1]) runs++;
		return runs * SIDES + 1;
	}

	static void submit(PoseStack pose, SubmitNodeCollector out, BoardModel m, int light) {
		int count = m.stackCount;
		out.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(BoardTextures.CHIPS), (p, vc) -> {
			int[] d = new int[BoardGeometry.CHIP_MAX];
			for (int i = 0; i < count; i++) {
				if (m.stackAlpha[i] < 0.5f) continue;
				stack(p, vc, m.stackX[i], m.stackZ[i], m.stackAmount[i], d, light);
			}
		});
	}

	private static void stack(PoseStack.Pose p, VertexConsumer vc, float x, float z, long amount, int[] d, int light) {
		int n = BoardGeometry.discs(amount, d);
		if (n == 0) return;
		float r = BoardGeometry.CHIP_R;
		float h = BoardGeometry.CHIP_H;
		float y0 = BoardGeometry.TOP + 0.002f;
		int i = 0;
		while (i < n) {
			int k = i;
			while (k < n && d[k] == d[i]) k++;
			int run = k - i;
			// band texture: column d, the top `run` discs of the 6-disc side strip
			float u0 = d[i] * 16f / BoardTextures.CHIPS_W;
			float u1 = (d[i] * 16f + 16f) / BoardTextures.CHIPS_W;
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
				// outward-facing winding (seen from outside: a → top a → top b → b reversed for CCW)
				Quads.v(p, vc, bx, yt, bz, ub, v0, col, light, nx, 0, nz);
				Quads.v(p, vc, bx, yb, bz, ub, v1, col, light, nx, 0, nz);
				Quads.v(p, vc, ax, yb, az, ua, v1, col, light, nx, 0, nz);
				Quads.v(p, vc, ax, yt, az, ua, v0, col, light, nx, 0, nz);
			}
			i = k;
		}
		// top disc of the highest denomination run
		int top = d[n - 1];
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
