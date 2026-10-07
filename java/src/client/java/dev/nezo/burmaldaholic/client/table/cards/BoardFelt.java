package dev.nezo.burmaldaholic.client.table.cards;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.Burmaldaholic;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The felt top of a card table in the world (REDESIGN.md §9.1): a 2.25 × 1.25 felt centred on the block (render-only
 * overhang) with a wooden rail lip and the table's edge and underside, so the cards and chips of a crescent table
 * lie on a real table instead of floating beside a 1 × 1 block. Felt by dimension (village / bastion / end theme
 * tiles), tiled at 0.5 block. Block-local coordinates in the table's FACING frame.
 */
public final class BoardFelt {
	private static final Identifier[] FELT = {Burmaldaholic.id("textures/gui/tables/core/felt_village.png"),
		Burmaldaholic.id("textures/gui/tables/core/felt_bastion.png"), Burmaldaholic.id("textures/gui/tables/core/felt_end.png")};
	private static final Identifier RAIL = Identifier.withDefaultNamespace("textures/block/dark_oak_planks.png");
	/** Felt height, rail lip width / height, table thickness. */
	public static final float TOP = 1.0f, LIP = 0.08f, LIP_H = 0.035f, THICK = 0.12f;
	private static final float TILE = 0.5f;

	private int theme;

	public void set(@Nullable Level level, boolean highRoller) {
		theme = level == null ? 0 : level.dimension() == Level.NETHER ? 1 : level.dimension() == Level.END || highRoller ? 2 : 0;
	}

	public void submit(PoseStack pose, SubmitNodeCollector out, Direction facing, int light, float x0, float z0, float x1, float z1) {
		pose.pushPose();
		pose.translate(0.5f, 0f, 0.5f);
		pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
		pose.translate(-0.5f, 0f, -0.5f);
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(FELT[theme]), (p, vc) -> {
			for (float x = x0; x < x1 - 1e-4f; x += TILE) {
				for (float z = z0; z < z1 - 1e-4f; z += TILE) {
					float xe = Math.min(x1, x + TILE);
					float ze = Math.min(z1, z + TILE);
					quadUp(p, vc, x, z, xe, ze, TOP + 0.001f, (xe - x) / TILE, (ze - z) / TILE, 0xFFFFFFFF, light);
				}
			}
		});
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(RAIL), (p, vc) -> {
			float lx0 = x0 - LIP, lz0 = z0 - LIP, lx1 = x1 + LIP, lz1 = z1 + LIP;
			float y = TOP + LIP_H;
			int col = 0xFFB08060;
			// lip top: four bands
			quadUp(p, vc, lx0, lz0, lx1, z0, y, (lx1 - lx0) * 2, LIP * 2, col, light);
			quadUp(p, vc, lx0, z1, lx1, lz1, y, (lx1 - lx0) * 2, LIP * 2, col, light);
			quadUp(p, vc, lx0, z0, x0, z1, y, LIP * 2, (z1 - z0) * 2, col, light);
			quadUp(p, vc, x1, z0, lx1, z1, y, LIP * 2, (z1 - z0) * 2, col, light);
			// inner lip walls (felt to lip top)
			int shade = 0xFF8A6048;
			side(p, vc, x0, z0, x1, z0, TOP, y, shade, light);
			side(p, vc, x1, z1, x0, z1, TOP, y, shade, light);
			side(p, vc, x0, z1, x0, z0, TOP, y, shade, light);
			side(p, vc, x1, z0, x1, z1, TOP, y, shade, light);
			// outer edge of the table and the underside of the overhang
			float yb = TOP - THICK;
			side(p, vc, lx1, lz0, lx0, lz0, yb, y, shade, light);
			side(p, vc, lx0, lz1, lx1, lz1, yb, y, shade, light);
			side(p, vc, lx0, lz0, lx0, lz1, yb, y, shade, light);
			side(p, vc, lx1, lz1, lx1, lz0, yb, y, shade, light);
			quadDown(p, vc, lx0, lz0, lx1, lz1, yb, 0xFF6A4838, light);
		});
		pose.popPose();
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int argb, int light, float nx,
			float ny, float nz) {
		vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	private static void quadUp(PoseStack.Pose p, VertexConsumer vc, float x0, float z0, float x1, float z1, float y, float us, float vs, int argb,
			int light) {
		vertex(p, vc, x0, y, z1, 0, vs, argb, light, 0, 1, 0);
		vertex(p, vc, x1, y, z1, us, vs, argb, light, 0, 1, 0);
		vertex(p, vc, x1, y, z0, us, 0, argb, light, 0, 1, 0);
		vertex(p, vc, x0, y, z0, 0, 0, argb, light, 0, 1, 0);
	}

	private static void quadDown(PoseStack.Pose p, VertexConsumer vc, float x0, float z0, float x1, float z1, float y, int argb, int light) {
		float us = Math.min(1, x1 - x0), vs = Math.min(1, z1 - z0);
		vertex(p, vc, x0, y, z0, 0, 0, argb, light, 0, -1, 0);
		vertex(p, vc, x1, y, z0, us, 0, argb, light, 0, -1, 0);
		vertex(p, vc, x1, y, z1, us, vs, argb, light, 0, -1, 0);
		vertex(p, vc, x0, y, z1, 0, vs, argb, light, 0, -1, 0);
	}

	/** A vertical face from (ax, az) to (bx, bz), counter-clockwise seen from outside. */
	private static void side(PoseStack.Pose p, VertexConsumer vc, float ax, float az, float bx, float bz, float y0, float y1, int argb, int light) {
		float len = (float) Math.hypot(bx - ax, bz - az);
		float nx = (bz - az) / len, nz = -(bx - ax) / len;
		float us = Math.min(1, len), vs = (y1 - y0);
		vertex(p, vc, ax, y0, az, 0, vs, argb, light, nx, 0, nz);
		vertex(p, vc, bx, y0, bz, us, vs, argb, light, nx, 0, nz);
		vertex(p, vc, bx, y1, bz, us, 0, argb, light, nx, 0, nz);
		vertex(p, vc, ax, y1, az, 0, 0, argb, light, nx, 0, nz);
	}
}
