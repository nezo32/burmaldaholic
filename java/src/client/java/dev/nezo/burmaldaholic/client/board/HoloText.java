package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A holo panel (ui-v2.md §7.3, readability pass): an optional row of cards at a readable size, then ≤ 3 centred lines
 * (each with its own scale — the prominent line is 2×) with a drop shadow, on a near-opaque ink backing framed by a
 * 1-px accent border. Full-bright, depth-tested, polygon-offset text. Positioned by its BOTTOM centre in block space,
 * rotated by a billboard quaternion, sized by a text px in blocks.
 */
final class HoloText {
	/** Ink backing, 90 % (high contrast against sky, felt and wood). */
	static final int BACKING = 0xE6180A28;
	/** Text px padding around the content; line pitch at scale 1. */
	static final int PAD_X = 4, PAD_Y = 3, PITCH = 10;
	/** Card fan inside a panel: card size and step in text px. */
	static final int CARD_W = 15, CARD_H = 20, CARD_STEP = 12, CARD_GAP = 3;

	/** What one panel shows (render thread scratch, filled by the caller). */
	static final class Spec {
		final FormattedCharSequence[] seqs = new FormattedCharSequence[3];
		final int[] widths = new int[3], colors = new int[3];
		final float[] scales = new float[3];
		int count;
		int border;
		final int[] cards = new int[BoardModel.MAX_FAN];
		int cardCount;
		int back = BoardTextures.BACK_CRIMSON;

		void clear() {
			count = 0;
			cardCount = 0;
			border = 0;
		}

		void add(FormattedCharSequence seq, int width, int color, float scale) {
			if (count >= 3 || seq == null) return;
			seqs[count] = seq;
			widths[count] = width;
			colors[count] = color;
			scales[count++] = scale;
		}

		void cards(int[] ids, int n) {
			cardCount = Math.min(n, cards.length);
			System.arraycopy(ids, 0, cards, 0, cardCount);
		}

		int rowWidth() {
			return cardCount == 0 ? 0 : CARD_W + (cardCount - 1) * CARD_STEP;
		}

		/** Panel width in text px. */
		int width() {
			float w = rowWidth();
			for (int i = 0; i < count; i++) w = Math.max(w, widths[i] * scales[i]);
			return (int) Math.ceil(w) + 2 * PAD_X;
		}

		/** Panel height in text px. */
		int height() {
			float h = cardCount == 0 ? 0 : CARD_H + CARD_GAP;
			for (int i = 0; i < count; i++) h += PITCH * scales[i];
			return (int) Math.ceil(h) - 1 + 2 * PAD_Y;
		}
	}

	private HoloText() {}

	/** Draws a panel; returns the number of backing / frame / card quads. */
	static int panel(PoseStack pose, SubmitNodeCollector out, Font font, Vector3f bottom, Quaternionf rot, float px, Spec s) {
		int w = s.width();
		int h = s.height();
		pose.pushPose();
		pose.translate(bottom.x, bottom.y, bottom.z);
		pose.rotateAround(rot, 0f, 0f, 0f);
		pose.scale(px, -px, px);
		float x0 = -w / 2f, x1 = w / 2f, y0 = -h, y1 = 0;
		int border = s.border;
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(BoardTextures.BACKING), (p, vc) -> {
			if (border != 0) quad(p, vc, x0 - 1, y0 - 1, x1 + 1, y1 + 1, -0.7f, border);
			quad(p, vc, x0, y0, x1, y1, -0.6f, BACKING);
		});
		int quads = border != 0 ? 2 : 1;
		float y = y0 + PAD_Y;
		if (s.cardCount > 0) {
			float cx = -s.rowWidth() / 2f;
			float cy = y;
			int n = s.cardCount;
			int[] ids = s.cards.clone();
			int back = s.back;
			out.submitCustomGeometry(pose, RenderTypes.entityCutout(BoardTextures.FACES), (p, vc) -> {
				float[] uv = new float[4];
				for (int i = 0; i < n; i++) {
					if (ids[i] >= 0) BoardTextures.faceUv(ids[i], uv);
					else BoardTextures.backUv(back, uv);
					float a = cx + i * CARD_STEP;
					// later cards in front (the fan reads left to right, every index corner visible)
					float z = -0.3f + 0.01f * i;
					Quads.v(p, vc, a, cy, z, uv[0], uv[1], 0xFFFFFFFF, Quads.FULL_BRIGHT, 0, 0, 1);
					Quads.v(p, vc, a, cy + CARD_H, z, uv[0], uv[3], 0xFFFFFFFF, Quads.FULL_BRIGHT, 0, 0, 1);
					Quads.v(p, vc, a + CARD_W, cy + CARD_H, z, uv[2], uv[3], 0xFFFFFFFF, Quads.FULL_BRIGHT, 0, 0, 1);
					Quads.v(p, vc, a + CARD_W, cy, z, uv[2], uv[1], 0xFFFFFFFF, Quads.FULL_BRIGHT, 0, 0, 1);
				}
			});
			quads += n;
			y += CARD_H + CARD_GAP;
		}
		for (int i = 0; i < s.count; i++) {
			float sc = s.scales[i];
			pose.pushPose();
			pose.translate(0, y, 0);
			pose.scale(sc, sc, 1);
			out.submitText(pose, -s.widths[i] / 2f, 0, s.seqs[i], true, Font.DisplayMode.POLYGON_OFFSET, Quads.FULL_BRIGHT, s.colors[i], 0, 0);
			pose.popPose();
			y += PITCH * sc;
		}
		pose.popPose();
		return quads;
	}

	private static void quad(PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer vc, float x0, float y0, float x1, float y1, float z, int color) {
		Quads.v(p, vc, x0, y0, z, 0, 0, color, Quads.FULL_BRIGHT, 0, 0, 1);
		Quads.v(p, vc, x0, y1, z, 0, 1, color, Quads.FULL_BRIGHT, 0, 0, 1);
		Quads.v(p, vc, x1, y1, z, 1, 1, color, Quads.FULL_BRIGHT, 0, 0, 1);
		Quads.v(p, vc, x1, y0, z, 1, 0, color, Quads.FULL_BRIGHT, 0, 0, 1);
	}

	/** A thin line (leader) between two block-space points in the camera plane, {@code thick} blocks wide. */
	static void leader(PoseStack pose, SubmitNodeCollector out, HoloFrame f, Vector3f a, Vector3f b, float thick, int color) {
		Vector3f d = new Vector3f(b).sub(a);
		float du = d.dot(f.right), dv = d.dot(f.up);
		float len = (float) Math.sqrt(du * du + dv * dv);
		if (len < 1e-4f) return;
		float pu = -dv / len * thick / 2, pv = du / len * thick / 2;
		Vector3f off = new Vector3f(f.right).mul(pu).add(new Vector3f(f.up).mul(pv));
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(BoardTextures.BACKING), (p, vc) -> {
			Quads.v(p, vc, a.x - off.x, a.y - off.y, a.z - off.z, 0, 0, color, Quads.FULL_BRIGHT, -f.fwd.x, -f.fwd.y, -f.fwd.z);
			Quads.v(p, vc, a.x + off.x, a.y + off.y, a.z + off.z, 0, 1, color, Quads.FULL_BRIGHT, -f.fwd.x, -f.fwd.y, -f.fwd.z);
			Quads.v(p, vc, b.x + off.x, b.y + off.y, b.z + off.z, 1, 1, color, Quads.FULL_BRIGHT, -f.fwd.x, -f.fwd.y, -f.fwd.z);
			Quads.v(p, vc, b.x - off.x, b.y - off.y, b.z - off.z, 1, 0, color, Quads.FULL_BRIGHT, -f.fwd.x, -f.fwd.y, -f.fwd.z);
		});
	}
}
