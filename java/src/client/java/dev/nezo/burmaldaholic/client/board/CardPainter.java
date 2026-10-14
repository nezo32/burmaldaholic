package dev.nezo.burmaldaholic.client.board;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Cards on the felt (ui-v2.md §7.1): {@code faces_w2.png} faces, lifted {@link BoardGeometry#CARD_LIFT}, leaning
 * {@link BoardCard#tilt}° toward their owner (the far edge raised, so the owner and the spectators behind them read the
 * index), flipping by narrowing to the edge. Back-face culled: the front quad shows the face (or back), a second quad
 * shows the card back from below / behind. Table-local coordinates.
 */
public final class CardPainter {
	private CardPainter() {}

	/** Quads per card. */
	public static final int QUADS_PER_CARD = 2;

	/** Contact shadow under a card: colour, growth, offset away from the light. */
	private static final int SHADOW = 0x48000000;

	static void submit(PoseStack pose, SubmitNodeCollector out, BoardState s, BoardModel m, int light) {
		int count = m.cardCount;
		int back = s.back;
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(BoardTextures.BACKING), (p, vc) -> {
			for (int i = 0; i < count; i++) shadow(p, vc, m.cards[i], light);
		});
		out.submitCustomGeometry(pose, RenderTypes.entityCutoutCull(BoardTextures.FACES), (p, vc) -> {
			float[] uv = new float[4];
			float[] bk = new float[4];
			BoardTextures.backUv(back, bk);
			for (int i = 0; i < count; i++) card(p, vc, m.cards[i], i, back, uv, bk, light);
		});
	}

	/** A soft shadow on the felt under a card (grows and fades as the card rises: deal arcs, flips). */
	private static void shadow(PoseStack.Pose p, VertexConsumer vc, BoardCard c, int light) {
		float rise = Math.max(0, c.lift);
		if (rise > 0.25f) return;
		float g = 1.06f + rise * 1.5f;
		int col = Quads.shade(SHADOW, 1f) & 0x00FFFFFF | (int) (0x48 * (1 - rise * 3.5f) + 0.5f) << 24;
		if ((col >>> 24) < 6) return;
		float hw = c.w / 2 * g, hh = c.h / 2 * g;
		double a = Math.toRadians(c.yaw);
		float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
		float cx = c.x + 0.012f + rise * 0.08f, cz = c.z + 0.016f + rise * 0.1f;
		float y = BoardGeometry.TOP + 0.0011f;
		float[] lx = {-hw, hw, hw, -hw};
		float[] lz = {hh, hh, -hh, -hh};
		for (int j = 0; j < 4; j++) Quads.v(p, vc, cx + lx[j] * cos - lz[j] * sin, y, cz + lx[j] * sin + lz[j] * cos, 0.5f, 0.5f, col, light, 0, 1, 0);
	}

	private static void card(PoseStack.Pose p, VertexConsumer vc, BoardCard c, int layer, int back, float[] uv, float[] bk, int light) {
		float base = BoardGeometry.TOP + BoardGeometry.CARD_LIFT + BoardGeometry.LAYER * layer + c.lift;
		// flip: the card narrows to its edge and widens again, back first
		float sx = c.flip > 0 ? (float) Math.abs(Math.cos(Math.PI * c.flip)) : 1f;
		boolean showFace = c.face >= 0 && (c.flip == 0 || c.flip >= 0.5f);
		if (showFace) BoardTextures.faceUv(c.face, uv);
		else System.arraycopy(bk, 0, uv, 0, 4);
		int col = c.dim ? 0xFF8A8A8A : 0xFFFFFFFF;
		float hw = c.w / 2 * Math.max(0.04f, sx);
		float hh = c.h / 2;
		double tilt = Math.toRadians(c.tilt);
		float tc = (float) Math.cos(tilt);
		float ts = (float) Math.sin(tilt);
		double a = Math.toRadians(c.yaw);
		float cos = (float) Math.cos(a);
		float sin = (float) Math.sin(a);
		// card space: lx across, lz toward the owner (+z = the card's bottom edge); the far edge (lz = −hh) is raised
		float[] lx = {-hw, hw, hw, -hw};
		float[] lz = {hh, hh, -hh, -hh};
		float[] tu = {uv[0], uv[2], uv[2], uv[0]};
		float[] tv = {uv[3], uv[3], uv[1], uv[1]};
		float[] bx = new float[4], by = new float[4], bz = new float[4];
		for (int j = 0; j < 4; j++) {
			float z = lz[j] * tc;
			bx[j] = c.x + lx[j] * cos - z * sin;
			bz[j] = c.z + lx[j] * sin + z * cos;
			by[j] = base + (hh - lz[j]) * ts;
		}
		// normal of the leaning card: up, tipped toward the owner
		float nx = ts * sin, ny = tc, nz = -ts * cos;
		nx = -nx;
		nz = -nz;
		for (int j = 0; j < 4; j++) Quads.v(p, vc, bx[j], by[j], bz[j], tu[j], tv[j], col, light, nx, ny, nz);
		// the back, seen from below / behind: reversed winding, the back design mirrored
		int dark = Quads.shade(col, 0.85f);
		for (int j = 3; j >= 0; j--) {
			float u = j == 0 || j == 3 ? bk[2] : bk[0];
			float v = j == 0 || j == 1 ? bk[3] : bk[1];
			Quads.v(p, vc, bx[j], by[j] - 0.0008f, bz[j], u, v, dark, light, -nx, -ny, -nz);
		}
	}
}
