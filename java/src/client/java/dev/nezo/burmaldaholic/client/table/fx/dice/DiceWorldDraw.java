package dev.nezo.burmaldaholic.client.table.fx.dice;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.table.fx.WorldQuads;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

/**
 * World dice (PHASE2B §3.3, DI §6, CS §6): a textured cube oriented by the sample's MODEL quaternion (so the faces are
 * real all the time and the rest pose shows the server's number), centred at the support height {@code zc} so no
 * corner ever sinks into the table, plus a blob shadow on the ground whose size and alpha follow the height.
 *
 * <p>The caller's pose is the TRAY origin on the playing surface with tray x along model X and tray y along model Z
 * (apply the block's FACING rotation first); {@code blocksPerU} converts tray units (die half-edges) to blocks. The
 * cube uses the generated {@code entity/core/dice_world} texture (faces 1–6 as 32 × 32 with gutters, the atlas pips and a rounded bevel), whose
 * model labels are the
 * canonical {@code CubeGroup} labels (1 up, 2 north, 5 south, 3 east, 4 west). Render thread only.
 */
public final class DiceWorldDraw {
	/** Dice texture (generated, dicekit): 136 × 68, face k as 32 × 32 at (34·((k − 1) mod 4) + 1, 34·⌊(k − 1)/4⌋ + 1), 1-px gutters. */
	public static final Identifier DIE_TEXTURE = Burmaldaholic.id("textures/entity/core/dice_world.png");
	/** Blob shadow texture (the 32-px die shadow, INK, alpha at runtime). */
	public static final Identifier SHADOW_TEXTURE = Burmaldaholic.id("textures/gui/sprites/tables/core/dice/shadow_32_0.png");
	private static final int[][] UV = faceUv();
	private static final double[] MC = new double[4];
	private static final Quaternionf ROT = new Quaternionf();

	private DiceWorldDraw() {}

	/** {u, v, size} of label k on {@link #DIE_TEXTURE}. */
	private static int[] cell(int k) {
		return new int[] {34 * ((k - 1) % 4) + 1, 34 * ((k - 1) / 4) + 1, 32};
	}

	/** WorldQuads.cube face order: top, bottom, north (−Z), south (+Z), east (+X), west (−X) = labels 1, 6, 2, 5, 3, 4. */
	private static int[][] faceUv() {
		return new int[][] {cell(1), cell(6), cell(2), cell(5), cell(3), cell(4)};
	}

	/** Submits one die. {@code surfaceY}: model Y of the playing surface (the tray floor) in the caller's pose. */
	public static void die(PoseStack pose, SubmitNodeCollector out, DieSample s, float blocksPerU, float surfaceY, int light) {
		if (s.phase == DieSample.HELD) return;
		float h = blocksPerU;
		Quat.trayToMc(s.q, MC);
		ROT.set((float) MC[1], (float) MC[2], (float) MC[3], (float) MC[0]);
		pose.pushPose();
		pose.translate(s.x * h, surfaceY + s.zc * h, s.y * h);
		pose.rotateAround(ROT, 0f, 0f, 0f);
		pose.scale(h, h, h);
		out.submitCustomGeometry(pose, RenderTypes.entityCutout(DIE_TEXTURE), cube(light));
		pose.popPose();
	}

	/** Unit-cube geometry per packed light (no per-frame lambda; the size is in the pose). Render thread only. */
	private static final Int2ObjectOpenHashMap<SubmitNodeCollector.CustomGeometryRenderer> CUBES = new Int2ObjectOpenHashMap<>();
	/** Unit shadow quad per (argb, light). Render thread only. */
	private static final Long2ObjectOpenHashMap<SubmitNodeCollector.CustomGeometryRenderer> SHADOWS = new Long2ObjectOpenHashMap<>();

	private static SubmitNodeCollector.CustomGeometryRenderer cube(int light) {
		SubmitNodeCollector.CustomGeometryRenderer r = CUBES.get(light);
		if (r == null) {
			if (CUBES.size() > 1024) CUBES.clear();
			r = (p, vc) -> WorldQuads.cube(p, vc, 1f, UV, 136, 68, light);
			CUBES.put(light, r);
		}
		return r;
	}

	private static SubmitNodeCollector.CustomGeometryRenderer shadowQuad(int argb, int light) {
		long k = ((long) argb << 32) | (light & 0xFFFFFFFFL);
		SubmitNodeCollector.CustomGeometryRenderer r = SHADOWS.get(k);
		if (r == null) {
			if (SHADOWS.size() > 4096) SHADOWS.clear();
			r = (p, vc) -> WorldQuads.topRect(p, vc, -1, -1, 1, 1, 0, 0, 0, 32, 13, 32, 13, argb, light);
			SHADOWS.put(k, r);
		}
		return r;
	}

	/**
	 * Submits the blob shadow of a die: offset away from the sky light by (+0.2, +0.1)·z, alpha 0.35 → 0.12 and size
	 * 100 % → 60 % with height (CS §6).
	 */
	public static void shadow(PoseStack pose, SubmitNodeCollector out, DieSample s, float blocksPerU, float surfaceY, int light) {
		if (s.phase == DieSample.HELD) return;
		double z = Math.max(0, s.zLow);
		double alpha = Math.max(0.12, 0.35 - 0.04 * z);
		double size = Math.max(0.6, 1 - z / 10);
		float h = blocksPerU;
		float cx = (float) ((s.x + 0.2 * z) * h);
		float cz = (float) ((s.y + 0.1 * z) * h);
		float hw = (float) (1.25 * size * h);
		float hd = (float) (0.9 * size * h);
		int argb = ((int) Math.round(alpha * 255) << 24) | 0xFFFFFF;
		pose.pushPose();
		pose.translate(cx, surfaceY + 0.001f, cz);
		pose.scale(hw, 1f, hd);
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(SHADOW_TEXTURE), shadowQuad(argb, light));
		pose.popPose();
	}
}
