package dev.nezo.burmaldaholic.client.table.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Vertex helpers of the table renderers (roulette wheel, craps dice and puck): horizontal quads on a table top, rotated
 * quads (the wheel head turns continuously in 3D), and small textured cubes. Texel coordinates are given on a texture of
 * {@code texW × texH}.
 */
public final class WorldQuads {
	public static final int FULL_BRIGHT = 0xF000F0;

	private WorldQuads() {}

	public static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int argb, int light, float nx, float ny,
			float nz) {
		vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	/**
	 * Horizontal quad facing up at height {@code y}, centred on (cx, cz) with half size {@code half}, rotated by
	 * {@code angleDeg} clockwise seen from above; the texture's top edge points to −Z at angle 0.
	 */
	public static void topQuad(PoseStack.Pose p, VertexConsumer vc, float cx, float y, float cz, float half, float angleDeg, int u, int v, int w, int h,
			int texW, int texH, int argb, int light) {
		double a = Math.toRadians(angleDeg);
		float c = (float) Math.cos(a);
		float s = (float) Math.sin(a);
		float[][] corners = {{-half, -half}, {-half, half}, {half, half}, {half, -half}};
		float[][] uv = {{u, v}, {u, v + h}, {u + w, v + h}, {u + w, v}};
		for (int i = 0; i < 4; i++) {
			float lx = corners[i][0];
			float lz = corners[i][1];
			float x = cx + lx * c - lz * s;
			float z = cz + lx * s + lz * c;
			vertex(p, vc, x, y, z, uv[i][0] / texW, uv[i][1] / texH, argb, light, 0, 1, 0);
		}
	}

	/** Axis-aligned horizontal rectangle facing up. */
	public static void topRect(PoseStack.Pose p, VertexConsumer vc, float x0, float z0, float x1, float z1, float y, int u, int v, int w, int h, int texW,
			int texH, int argb, int light) {
		vertex(p, vc, x0, y, z0, u / (float) texW, v / (float) texH, argb, light, 0, 1, 0);
		vertex(p, vc, x0, y, z1, u / (float) texW, (v + h) / (float) texH, argb, light, 0, 1, 0);
		vertex(p, vc, x1, y, z1, (u + w) / (float) texW, (v + h) / (float) texH, argb, light, 0, 1, 0);
		vertex(p, vc, x1, y, z0, (u + w) / (float) texW, v / (float) texH, argb, light, 0, 1, 0);
	}

	/**
	 * A cube of half size {@code h} centred at the pose origin, each face textured with {@code faceUv[i]} = {u, v, size}
	 * in the order top, bottom, north (−Z), south (+Z), east (+X), west (−X).
	 */
	public static void cube(PoseStack.Pose p, VertexConsumer vc, float h, int[][] faceUv, int texW, int texH, int light) {
		// top (+Y)
		face(p, vc, new float[] {-h, h, -h}, new float[] {-h, h, h}, new float[] {h, h, h}, new float[] {h, h, -h}, faceUv[0], texW, texH, 0xFFFFFFFF, light, 0, 1,
			0);
		// bottom (−Y)
		face(p, vc, new float[] {-h, -h, h}, new float[] {-h, -h, -h}, new float[] {h, -h, -h}, new float[] {h, -h, h}, faceUv[1], texW, texH, 0xFF909090, light,
			0, -1, 0);
		// north (−Z)
		face(p, vc, new float[] {h, h, -h}, new float[] {h, -h, -h}, new float[] {-h, -h, -h}, new float[] {-h, h, -h}, faceUv[2], texW, texH, 0xFFC8C8C8, light,
			0, 0, -1);
		// south (+Z)
		face(p, vc, new float[] {-h, h, h}, new float[] {-h, -h, h}, new float[] {h, -h, h}, new float[] {h, h, h}, faceUv[3], texW, texH, 0xFFC8C8C8, light, 0,
			0, 1);
		// east (+X)
		face(p, vc, new float[] {h, h, h}, new float[] {h, -h, h}, new float[] {h, -h, -h}, new float[] {h, h, -h}, faceUv[4], texW, texH, 0xFFDADADA, light, 1,
			0, 0);
		// west (−X)
		face(p, vc, new float[] {-h, h, -h}, new float[] {-h, -h, -h}, new float[] {-h, -h, h}, new float[] {-h, h, h}, faceUv[5], texW, texH, 0xFFDADADA, light,
			-1, 0, 0);
	}

	private static void face(PoseStack.Pose p, VertexConsumer vc, float[] a, float[] b, float[] c, float[] d, int[] uv, int texW, int texH, int argb, int light,
			float nx, float ny, float nz) {
		float u0 = uv[0] / (float) texW;
		float v0 = uv[1] / (float) texH;
		float u1 = (uv[0] + uv[2]) / (float) texW;
		float v1 = (uv[1] + uv[2]) / (float) texH;
		vertex(p, vc, a[0], a[1], a[2], u0, v0, argb, light, nx, ny, nz);
		vertex(p, vc, b[0], b[1], b[2], u0, v1, argb, light, nx, ny, nz);
		vertex(p, vc, c[0], c[1], c[2], u1, v1, argb, light, nx, ny, nz);
		vertex(p, vc, d[0], d[1], d[2], u1, v0, argb, light, nx, ny, nz);
	}
}
