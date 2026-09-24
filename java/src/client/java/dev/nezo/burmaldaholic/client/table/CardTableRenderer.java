package dev.nezo.burmaldaholic.client.table;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.render.SpectatorBlockEntityRenderer;
import dev.nezo.burmaldaholic.client.render.SpectatorRenderState;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlock;
import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.games.poker.present.TableWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base in-world view of a card table (animation/cards.md §1.4, §2.4, §3.4, §0.8; task J-C10): cards lying on the
 * table top from the W atlas ({@code textures/entity/core/cards/faces.png}, visual/cards.md §3.8: language-neutral
 * indices), chip stacks from {@code chips.png}, billboarded texts within 8 blocks. Subclasses fill the {@link State}
 * from their table's PUBLIC tag only (never a hole card that is not face up) and the same beat timeline as the
 * screens (shared clock), so a spectator sees every deal, flip and pot slide on the same tick as the players.
 *
 * <p>Budget (§0.8): ≤ {@link #MAX_CARDS} cards × 1 quad, ≤ {@link #MAX_DISCS} chip discs × 1 quad, ≤ 8 texts; tweened
 * within 20 blocks, settled to 32 (LOD from the base). Reduce motion: cards appear at their slot and flip in place.
 * Table sounds are positional and only for viewers who do not have this table's screen open (the screen plays its
 * own), via {@link FxSounds#playAt} (volume × {@code anim.volume}).
 */
public abstract class CardTableRenderer<T extends BlockEntity, S extends CardTableRenderer.State> extends SpectatorBlockEntityRenderer<T, S> {
	public static final Identifier FACES = Burmaldaholic.id("textures/entity/core/cards/faces.png");
	public static final Identifier CHIPS = Burmaldaholic.id("textures/entity/core/cards/chips.png");
	public static final int MAX_CARDS = 32, MAX_DISCS = 64, MAX_TEXTS = 8;
	/** Back rows of the W atlas (visual/cards.md §3.7): navy, burgundy, crimson, emerald, bastion, end. */
	public static final int BACK_CRIMSON = 2, BACK_EMERALD = 3, BACK_BASTION = 4, BACK_END = 5;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final double TEXT_RANGE_SQ = 8 * 8;
	/** Denominations of the chips.png discs, left to right. */
	private static final int[] DENOMS = {1, 5, 25, 100, 500};

	/** One card on the table (block-local; filled per frame, pooled in the state). */
	public static final class WorldCard {
		public float x, z, lift, yaw, w, h;
		/** Card id 0..51 (face up) or −1 (back). */
		public int face = -1;
		/** 0 = lying as {@link #face} says; (0, 1) = mid-flip back → face (the face shows after ½). */
		public float flip;
		public boolean dim;

		void set(float x, float z, float yaw, float w, float h, int face) {
			this.x = x;
			this.z = z;
			this.yaw = yaw;
			this.w = w;
			this.h = h;
			this.face = face;
			this.lift = 0;
			this.flip = 0;
			this.dim = false;
		}
	}

	/** A chip stack: a single disc per denomination step, ≤ 5 high. */
	public static final class Stack {
		public float x, z;
		public long amount;
		public float alpha = 1;
	}

	public static class State extends SpectatorRenderState {
		public Direction facing = Direction.NORTH;
		public int back = BACK_CRIMSON;
		public final WorldCard[] cards = new WorldCard[MAX_CARDS];
		public int cardCount;
		public final Stack[] stacks = new Stack[16];
		public int stackCount;
		public final FormattedCharSequence[] texts = new FormattedCharSequence[MAX_TEXTS];
		public final float[] textPos = new float[MAX_TEXTS * 3];
		public final int[] textColor = new int[MAX_TEXTS];
		public int textCount;
		public boolean visible;

		public State() {
			for (int i = 0; i < cards.length; i++) cards[i] = new WorldCard();
			for (int i = 0; i < stacks.length; i++) stacks[i] = new Stack();
		}

		public void clear() {
			cardCount = 0;
			stackCount = 0;
			textCount = 0;
		}

		/** Adds a card; returns it (or a scratch card when the budget is used up). */
		public WorldCard card(float x, float z, float yaw, float w, float h, int face) {
			WorldCard c = cards[Math.min(cardCount, MAX_CARDS - 1)];
			if (cardCount < MAX_CARDS) cardCount++;
			c.set(x, z, yaw, w, h, face);
			return c;
		}

		public void stack(float x, float z, long amount, float alpha) {
			if (amount <= 0 || stackCount >= stacks.length) return;
			Stack s = stacks[stackCount++];
			s.x = x;
			s.z = z;
			s.amount = amount;
			s.alpha = alpha;
		}

		public void text(float x, float y, float z, FormattedCharSequence text, int color) {
			if (textCount >= MAX_TEXTS || text == null || distSq > TEXT_RANGE_SQ) return;
			texts[textCount] = text;
			textPos[textCount * 3] = x;
			textPos[textCount * 3 + 1] = y;
			textPos[textCount * 3 + 2] = z;
			textColor[textCount] = color;
			textCount++;
		}
	}

	protected final Font font;

	protected CardTableRenderer(BlockEntityRendererProvider.Context ctx) {
		this.font = ctx.font();
	}

	@Override
	protected int animateRadius() {
		return 20;
	}

	@Override
	public int getViewDistance() {
		return 32;
	}

	/** Fills the state from the block entity's public tag; called by {@link #extractAnimated} after the common part. */
	protected abstract void fill(T be, S state, float partialTick);

	@Override
	protected final void extractAnimated(T be, S state, float partialTick) {
		state.clear();
		BlockState bs = be.getBlockState();
		state.facing = bs.hasProperty(CasinoTableBlock.FACING) ? bs.getValue(CasinoTableBlock.FACING) : Direction.NORTH;
		state.visible = state.lod != Lod.STATIC;
		if (state.visible) fill(be, state, partialTick);
	}

	// ---- helpers for subclasses -------------------------------------------------------------------------------

	/** Motion is tweened (near, not reduced); otherwise cards snap to their slot and flip in place. */
	protected static boolean tweened(State s) {
		return s.lod == Lod.FULL && !FxSettings.reduceMotion();
	}

	/** The local player has this table's screen open (it plays its own sounds). */
	protected static boolean viewingOwnScreen(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		return mc.gui.screen() instanceof AbstractContainerScreen<?> s && s.getMenu() instanceof CasinoTableMenu m && m.pos().equals(pos);
	}

	/** Plays a table sound at the table top for a spectator (never for a viewer of the screen, never far away). */
	protected static void tableSound(BlockPos pos, State s, String id, float volume, float pitch) {
		if (s.lod != Lod.FULL || viewingOwnScreen(pos)) return;
		FxSounds.playAt(id, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, volume, pitch);
	}

	/**
	 * Places a card that is dealt from {@code (fx, fz)} to {@code (tx, tz)}: {@code dealU} 0..1 along the arc (1 = in
	 * its slot), then {@code flipU} 0..1 turning it over (the face shows after ½; {@code face} = −1 keeps the back).
	 */
	protected static WorldCard dealt(State s, float fx, float fz, float tx, float tz, float yaw, float w, float h, int face, double dealU,
			double flipU) {
		boolean motion = tweened(s);
		if (!motion) dealU = dealU > 0 ? 1 : 0;
		if (dealU <= 0) return null;
		float[] p = TableWorld.arc(fx, fz, tx, tz, dealU, 0.10f);
		boolean flipping = face >= 0 && flipU > 0 && flipU < 1;
		WorldCard c = s.card(p[0], p[1], yaw - 20f * (float) (1 - Math.min(1, dealU)), w, h, face >= 0 && (flipU >= 1 || !motion && flipU >= 0.5) ? face : -1);
		c.lift = p[2];
		if (flipping && motion) {
			c.face = face;
			c.flip = (float) flipU;
			c.lift += 0.05f * (float) Math.sin(Math.PI * flipU);
		}
		return c;
	}

	// ---- submit --------------------------------------------------------------------------------------------------

	@Override
	public void submit(S s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		if (!s.visible || s.cardCount + s.stackCount + s.textCount == 0) return;
		int light = s.lightCoords;
		pose.pushPose();
		pose.translate(0.5f, 0f, 0.5f);
		pose.rotateAround(Axis.YP.rotationDegrees(-s.facing.toYRot()), 0f, 0f, 0f);
		pose.translate(-0.5f, 0f, -0.5f);
		if (s.cardCount > 0) out.submitCustomGeometry(pose, RenderTypes.entityCutout(FACES), (p, vc) -> cards(p, vc, s, light));
		if (s.stackCount > 0) out.submitCustomGeometry(pose, RenderTypes.entityCutout(CHIPS), (p, vc) -> chips(p, vc, s, light));
		for (int i = 0; i < s.textCount; i++) {
			pose.pushPose();
			pose.translate(s.textPos[i * 3], s.textPos[i * 3 + 1], s.textPos[i * 3 + 2]);
			pose.rotateAround(Axis.YP.rotationDegrees(s.facing.toYRot()), 0f, 0f, 0f);
			pose.rotateAround(camera.orientation, 0f, 0f, 0f);
			pose.scale(0.012f, -0.012f, 0.012f);
			FormattedCharSequence t = s.texts[i];
			out.submitText(pose, -font.width(t) / 2f, 0, t, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, s.textColor[i], 0, 0xFF180A28);
			pose.popPose();
		}
		pose.popPose();
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int argb, int light) {
		vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
	}

	private static void cards(PoseStack.Pose p, VertexConsumer vc, State s, int light) {
		for (int i = 0; i < s.cardCount; i++) {
			WorldCard c = s.cards[i];
			float y = TableWorld.TOP + 0.002f + TableWorld.LAYER * i + c.lift;
			// flip: the card narrows to its edge and widens again, back first
			float sx = c.flip > 0 ? (float) Math.abs(Math.cos(Math.PI * c.flip)) : 1f;
			boolean showFace = c.face >= 0 && (c.flip == 0 || c.flip >= 0.5f);
			float[] uv = showFace ? faceUv(c.face) : backUv(s.back);
			int col = c.dim ? 0xFF8A8A8A : 0xFFFFFFFF;
			float hw = c.w / 2 * Math.max(0.04f, sx);
			float hh = c.h / 2;
			double a = Math.toRadians(c.yaw);
			float cos = (float) Math.cos(a);
			float sin = (float) Math.sin(a);
			// corners (−hw, −hh) … in card space → block space; the card's top edge faces the centre (−z)
			float[][] k = {{-hw, hh}, {hw, hh}, {hw, -hh}, {-hw, -hh}};
			float[][] t = {{uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}, {uv[0], uv[1]}};
			for (int j = 0; j < 4; j++) {
				float bx = c.x + k[j][0] * cos - k[j][1] * sin;
				float bz = c.z + k[j][0] * sin + k[j][1] * cos;
				vertex(p, vc, bx, y, bz, t[j][0], t[j][1], col, light);
			}
		}
	}

	/** UV (u0, v0, u1, v1) of a face in the 256² W atlas: column A,2…10,J,Q,K; row ♠♥♦♣ (card = rankIdx × 4 + suit). */
	public static float[] faceUv(int card) {
		int rank = (card >> 2) + 2;
		int col = rank == 14 ? 0 : rank - 1;
		int row = card & 3;
		return uv(col * 16, row * 22, 16, 22);
	}

	public static float[] backUv(int back) {
		return uv(Math.max(0, Math.min(5, back)) * 16, 88, 16, 22);
	}

	private static float[] uv(int x, int y, int w, int h) {
		return new float[] {x / 256f, y / 256f, (x + w) / 256f, (y + h) / 256f};
	}

	private static void chips(PoseStack.Pose p, VertexConsumer vc, State s, int light) {
		int discs = 0;
		for (int i = 0; i < s.stackCount && discs < MAX_DISCS; i++) {
			Stack st = s.stacks[i];
			long left = st.amount;
			int n = 0;
			// greedy, largest at the bottom, ≤ 5 discs (visual/cards.md §5.2)
			for (int d = DENOMS.length - 1; d >= 0 && n < 5; d--) {
				while (left >= DENOMS[d] && n < 5) {
					left -= DENOMS[d];
					disc(p, vc, st.x, st.z, TableWorld.TOP + 0.004f + 0.012f * n, d, light);
					n++;
					discs++;
				}
			}
		}
	}

	private static void disc(PoseStack.Pose p, VertexConsumer vc, float x, float z, float y, int denom, int light) {
		float r = 0.035f;
		float u0 = denom * 13 / 65f;
		float u1 = (denom * 13 + 13) / 65f;
		vertex(p, vc, x - r, y, z + r, u0, 1, 0xFFFFFFFF, light);
		vertex(p, vc, x + r, y, z + r, u1, 1, 0xFFFFFFFF, light);
		vertex(p, vc, x + r, y, z - r, u1, 0, 0xFFFFFFFF, light);
		vertex(p, vc, x - r, y, z - r, u0, 0, 0xFFFFFFFF, light);
	}
}
