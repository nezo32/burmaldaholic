package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A holo panel (ui-v2.md §7.3): ≤ 3 centred lines on an ink backing (70 %), full-bright, depth-tested, no shadow,
 * polygon-offset text. Positioned by its BOTTOM centre in block space, rotated by a billboard quaternion, sized by a
 * text pixel in blocks.
 */
final class HoloText {
	/** Ink backing, 70 % (REDESIGN §9.2: dark backing; review: 70 %). */
	static final int BACKING = 0xB3180A28;
	/** Text px padding around the lines; line pitch. */
	static final int PAD_X = 3, PAD_Y = 2, PITCH = 10;

	private HoloText() {}

	/** Panel width in text px for lines of the given widths. */
	static int width(int maxLineWidth) {
		return maxLineWidth + 2 * PAD_X;
	}

	/** Panel height in text px for {@code lines} lines. */
	static int height(int lines) {
		return lines * PITCH - 1 + 2 * PAD_Y;
	}

	/**
	 * Draws a panel. {@code edge} ≠ 0 draws a 1-px coloured line along the bottom (acting seat / result), returns the
	 * number of backing quads (1 or 2).
	 */
	static int panel(PoseStack pose, SubmitNodeCollector out, Font font, Vector3f bottom, Quaternionf rot, float px, FormattedCharSequence[] seqs, int[] widths,
			int[] colors, int count, int edge) {
		int maxW = 0;
		for (int i = 0; i < count; i++) maxW = Math.max(maxW, widths[i]);
		int w = width(maxW);
		int h = height(count);
		pose.pushPose();
		pose.translate(bottom.x, bottom.y, bottom.z);
		pose.rotateAround(rot, 0f, 0f, 0f);
		pose.scale(px, -px, px);
		float x0 = -w / 2f, x1 = w / 2f, y0 = -h, y1 = 0;
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(BoardTextures.BACKING), (p, vc) -> {
			Quads.v(p, vc, x0, y0, -0.6f, 0, 0, BACKING, Quads.FULL_BRIGHT, 0, 0, 1);
			Quads.v(p, vc, x0, y1, -0.6f, 0, 1, BACKING, Quads.FULL_BRIGHT, 0, 0, 1);
			Quads.v(p, vc, x1, y1, -0.6f, 1, 1, BACKING, Quads.FULL_BRIGHT, 0, 0, 1);
			Quads.v(p, vc, x1, y0, -0.6f, 1, 0, BACKING, Quads.FULL_BRIGHT, 0, 0, 1);
			if (edge != 0) {
				Quads.v(p, vc, x0, y1 - 1, -0.5f, 0, 0, edge, Quads.FULL_BRIGHT, 0, 0, 1);
				Quads.v(p, vc, x0, y1, -0.5f, 0, 1, edge, Quads.FULL_BRIGHT, 0, 0, 1);
				Quads.v(p, vc, x1, y1, -0.5f, 1, 1, edge, Quads.FULL_BRIGHT, 0, 0, 1);
				Quads.v(p, vc, x1, y1 - 1, -0.5f, 1, 0, edge, Quads.FULL_BRIGHT, 0, 0, 1);
			}
		});
		for (int i = 0; i < count; i++) {
			float y = y0 + PAD_Y + i * PITCH;
			out.submitText(pose, -widths[i] / 2f, y, seqs[i], false, Font.DisplayMode.POLYGON_OFFSET, Quads.FULL_BRIGHT, colors[i], 0, 0);
		}
		pose.popPose();
		return edge != 0 ? 2 : 1;
	}

	/** A thin line (leader) between two block-space points in the camera plane, {@code thick} blocks wide. */
	static void leader(PoseStack pose, SubmitNodeCollector out, HoloFrame f, Vector3f a, Vector3f b, float thick, int color) {
		Vector3f d = new Vector3f(b).sub(a);
		float du = d.dot(f.right), dv = d.dot(f.up);
		float len = (float) Math.sqrt(du * du + dv * dv);
		if (len < 1e-4f) return;
		// perpendicular in the plane
		float pu = -dv / len * thick / 2, pv = du / len * thick / 2;
		Vector3f off = new Vector3f(f.right).mul(pu).add(new Vector3f(f.up).mul(pv));
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(BoardTextures.BACKING), (p, vc) -> {
			Quads.v(p, vc, a.x - off.x, a.y - off.y, a.z - off.z, 0, 0, color, Quads.FULL_BRIGHT, f.fwd.x, f.fwd.y, f.fwd.z);
			Quads.v(p, vc, a.x + off.x, a.y + off.y, a.z + off.z, 0, 1, color, Quads.FULL_BRIGHT, f.fwd.x, f.fwd.y, f.fwd.z);
			Quads.v(p, vc, b.x + off.x, b.y + off.y, b.z + off.z, 1, 1, color, Quads.FULL_BRIGHT, f.fwd.x, f.fwd.y, f.fwd.z);
			Quads.v(p, vc, b.x - off.x, b.y - off.y, b.z - off.z, 1, 0, color, Quads.FULL_BRIGHT, f.fwd.x, f.fwd.y, f.fwd.z);
		});
	}
}
