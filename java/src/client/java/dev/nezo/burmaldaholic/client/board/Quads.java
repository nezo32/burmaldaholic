package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Vertex helper of the board painters (entity vertex format). */
final class Quads {
	static final int FULL_BRIGHT = 0xF000F0;

	private Quads() {}

	static void v(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int argb, int light, float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	/** ARGB with its colour multiplied by {@code k} (0..1). */
	static int shade(int argb, float k) {
		int r = (int) (((argb >> 16) & 0xFF) * k);
		int g = (int) (((argb >> 8) & 0xFF) * k);
		int b = (int) ((argb & 0xFF) * k);
		return (argb & 0xFF000000) | r << 16 | g << 8 | b;
	}
}
