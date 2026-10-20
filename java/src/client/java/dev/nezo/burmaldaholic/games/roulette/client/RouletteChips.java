package dev.nezo.burmaldaholic.games.roulette.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nezo.burmaldaholic.client.board.BoardTextures;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteWorld;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Small chip stacks for the roulette layout in the world: the board kit's {@code chips_w.png} discs at the size of a
 * printed cell ({@link RouletteWorld#CHIP_D}; the kit's 0.14-block discs would cover three numbers), the top disc rimmed
 * in the owner's seat colour, faded for a losing bet on the result. Octagonal band + top quad per run of equal discs.
 */
final class RouletteChips {
	private static final int SIDES = 8;
	private static final float[] COS = new float[SIDES + 1], SIN = new float[SIDES + 1];

	static {
		for (int i = 0; i <= SIDES; i++) {
			double a = 2 * Math.PI * i / SIDES + Math.PI / SIDES;
			COS[i] = (float) Math.cos(a);
			SIN[i] = (float) Math.sin(a);
		}
	}

	private RouletteChips() {}

	static void stack(PoseStack.Pose p, VertexConsumer vc, float x, float z, long amount, int tint, float alpha, int[] d, int light) {
		stack(p, vc, x, z, amount, tint, alpha, d, light, 0);
	}

	/** A stack lifted by {@code dy} (a drop) and faded to {@code alpha} (needs a translucent render type below 1). */
	static void stack(PoseStack.Pose p, VertexConsumer vc, float x, float z, long amount, int tint, float alpha, int[] d, int light, float dy) {
		int n = Math.min(RouletteWorld.discs(amount), BoardGeometry.discs(amount, d));
		if (n == 0 || alpha <= 0.01f) return;
		float r = RouletteWorld.CHIP_D / 2;
		float h = RouletteWorld.CHIP_H;
		float y0 = BoardGeometry.TOP + 0.004f + dy;
		int a8 = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
		int shade = a8 << 24 | 0xFFFFFF;
		tint = (tint & 0xFFFFFF) | a8 << 24;
		int i = 0;
		while (i < n) {
			int k = i;
			while (k < n && d[k] == d[i]) k++;
			int run = k - i;
			float u0 = d[i] * 16f / BoardTextures.CHIPS_W, u1 = (d[i] * 16f + 16f) / BoardTextures.CHIPS_W;
			float v0 = BoardTextures.CHIP_TOP / (float) BoardTextures.CHIPS_H;
			float v1 = (BoardTextures.CHIP_TOP + BoardTextures.CHIP_BAND * run) / (float) BoardTextures.CHIPS_H;
			float yb = y0 + i * h, yt = y0 + k * h;
			for (int s = 0; s < SIDES; s++) {
				float ax = x + r * COS[s], az = z + r * SIN[s];
				float bx = x + r * COS[s + 1], bz = z + r * SIN[s + 1];
				float nx = (COS[s] + COS[s + 1]) / 2, nz = (SIN[s] + SIN[s + 1]) / 2;
				float ua = u0 + (u1 - u0) * s / SIDES, ub = u0 + (u1 - u0) * (s + 1) / SIDES;
				v(p, vc, bx, yt, bz, ub, v0, shade, light, nx, 0, nz);
				v(p, vc, bx, yb, bz, ub, v1, shade, light, nx, 0, nz);
				v(p, vc, ax, yb, az, ua, v1, shade, light, nx, 0, nz);
				v(p, vc, ax, yt, az, ua, v0, shade, light, nx, 0, nz);
			}
			i = k;
		}
		int top = d[n - 1];
		float yt = y0 + n * h;
		float u0 = top * 16f / BoardTextures.CHIPS_W, u1 = (top * 16f + 16f) / BoardTextures.CHIPS_W;
		float v1 = 16f / BoardTextures.CHIPS_H;
		// the owner's colour: a rim one size larger just under the top disc, then the top disc
		float rr = r * 1.25f;
		int rim = tint;
		v(p, vc, x - rr, yt - 0.001f, z + rr, u0 + 0.5f / BoardTextures.CHIPS_W, 0.5f / BoardTextures.CHIPS_H, rim, light, 0, 1, 0);
		v(p, vc, x + rr, yt - 0.001f, z + rr, u0 + 0.5f / BoardTextures.CHIPS_W, 0.5f / BoardTextures.CHIPS_H, rim, light, 0, 1, 0);
		v(p, vc, x + rr, yt - 0.001f, z - rr, u0 + 0.5f / BoardTextures.CHIPS_W, 0.5f / BoardTextures.CHIPS_H, rim, light, 0, 1, 0);
		v(p, vc, x - rr, yt - 0.001f, z - rr, u0 + 0.5f / BoardTextures.CHIPS_W, 0.5f / BoardTextures.CHIPS_H, rim, light, 0, 1, 0);
		v(p, vc, x - r, yt, z + r, u0, v1, shade, light, 0, 1, 0);
		v(p, vc, x + r, yt, z + r, u1, v1, shade, light, 0, 1, 0);
		v(p, vc, x + r, yt, z - r, u1, 0, shade, light, 0, 1, 0);
		v(p, vc, x - r, yt, z - r, u0, 0, shade, light, 0, 1, 0);
	}

	private static void v(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float vv, int argb, int light, float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z).setColor(argb).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
