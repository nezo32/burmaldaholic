package dev.nezo.burmaldaholic.games.extras.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.motion.MotionClock;
import dev.nezo.burmaldaholic.client.fx.motion.MotionFx;
import dev.nezo.burmaldaholic.client.table.fx.dice.DiceAudio;
import dev.nezo.burmaldaholic.client.table.fx.dice.DiceWorldDraw;
import dev.nezo.burmaldaholic.core.anim.dice.DiceEvent;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DiceTray;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.motion.EventCursor;
import dev.nezo.burmaldaholic.core.anim.motion.MotionEvent;
import dev.nezo.burmaldaholic.games.extras.logic.DuelStagePlan;
import dev.nezo.burmaldaholic.games.extras.net.DuelStageSync;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The in-world PvP Dice Duel for modded viewers (DI §6, PHASE2B §4 DCb): one {@link DuelStageSync} per duel rebuilds the
 * server's {@link DuelStagePlan} (same solver, same keys as the duel screens), sampled every frame on the shared clock
 * {@code levelMs − start·50} with the partial tick (smooth at any fps; a frozen {@code AnimClock} freezes it for tests)
 * and drawn with {@link DiceWorldDraw} (quaternion cube with the real faces, blob shadow). LOD by the camera's distance
 * to the midpoint:
 * <ul>
 *   <li>≤ 8 blocks: full motion, shadows, dust ({@code WHITE_ASH}, 3 per floor impact), positional contact sounds;</li>
 *   <li>8–24: full motion and shadows, no particles; only the settle and clack sounds within 16 blocks;</li>
 *   <li>24–48: each pair appears at its rest pose when it rests (never before: no spoiler, no cost);</li>
 *   <li>&gt; 48: culled.</li>
 * </ul>
 * The duellists hear their screens, so the stage is silent for them (resolved lazily: a sync that arrives before the
 * local player exists stays silent until it does). A stage draws only in its own dimension. Render thread only; the
 * rolls are solved once per stage (on receipt) and frames only sample the plan.
 */
public final class DuelStageRenderer {
	public static final double LOD_FULL = 8, LOD_MOTION = 24, LOD_REST = 48, HEAR_FAR = 16;
	private static final List<Live> STAGES = new ArrayList<>();
	private static final DuelStagePlan.Sample S = new DuelStagePlan.Sample();
	private static final DieSample REST = new DieSample();
	private static final Quaternionf YAW = new Quaternionf();
	/** The level the cached dimension id belongs to. */
	private static Object dimLevel;
	private static String dim = "";

	private DuelStageRenderer() {}

	/** One staged duel on this client. */
	static final class Live {
		final DuelStageSync sync;
		final DuelStagePlan plan;
		/** 1 = the local player is a duellist, 0 = a bystander, −1 = not known yet (no local player when it arrived). */
		int duellist;
		final DiceAudio[] audio = new DiceAudio[2];
		final EventCursor[] cursors = new EventCursor[2];
		/** Per-side contact listeners, made once (they read {@link #near} / {@link #lod} / {@link #round}). */
		final EventCursor.Listener[] listeners = new EventCursor.Listener[2];
		int cursorRound = -1;
		/** Last LOD drawn (tests). */
		int lod = -1;
		boolean near;
		int round;

		Live(DuelStageSync sync, int duellist) {
			this.sync = sync;
			this.plan = sync.plan();
			this.duellist = duellist;
			for (int s = 0; s < 2; s++) {
				audio[s] = DiceAudio.world();
				final int side = s;
				listeners[s] = (e, silent) -> contact(this, side, e, silent);
			}
		}

		/** Resolves the duellist role once the local player exists; true while it is (or may be) a duellist. */
		boolean silent(Minecraft mc) {
			if (duellist < 0 && mc.player != null) duellist = sync.duellists().contains(mc.player.getUUID()) ? 1 : 0;
			return duellist != 0;
		}
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DuelStageSync.TYPE, (payload, context) -> accept(payload));
		LevelRenderEvents.COLLECT_SUBMITS.register(DuelStageRenderer::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STAGES.clear());
	}

	/** A new duel (deduplicated by id). */
	public static void accept(DuelStageSync sync) {
		for (Live l : STAGES) if (l.sync.id() == sync.id() && l.sync.start() == sync.start()) return;
		Minecraft mc = Minecraft.getInstance();
		STAGES.add(new Live(sync, mc.player == null ? -1 : sync.duellists().contains(mc.player.getUUID()) ? 1 : 0));
	}

	/** Whether the newest stage treats the local player as a duellist (1), a bystander (0) or not yet known (−1) (tests). */
	public static int newestRole() {
		return STAGES.isEmpty() ? -2 : STAGES.get(STAGES.size() - 1).duellist;
	}

	/** Staged duels on this client (tests). */
	public static int active() {
		return STAGES.size();
	}

	/** The LOD last drawn for the newest stage (0 full, 1 motion, 2 rest-only, 3 culled; −1 none) (tests). */
	public static int lastLod() {
		return STAGES.isEmpty() ? -1 : STAGES.get(STAGES.size() - 1).lod;
	}

	/** The plan of the newest stage (tests), or null. */
	public static DuelStagePlan newestPlan() {
		return STAGES.isEmpty() ? null : STAGES.get(STAGES.size() - 1).plan;
	}

	public static void clear() {
		STAGES.clear();
	}

	private static void render(LevelRenderContext ctx) {
		if (STAGES.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			STAGES.clear();
			return;
		}
		if (mc.level != dimLevel) {
			dimLevel = mc.level;
			dim = mc.level.dimension().identifier().toString();
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double levelMs = MotionClock.levelMs(partial);
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack pose = ctx.poseStack();
		for (int i = STAGES.size() - 1; i >= 0; i--) {
			Live l = STAGES.get(i);
			double t = levelMs - l.sync.start() * 50.0;
			if (t > l.plan.endMs() + 100) {
				STAGES.remove(i);
				continue;
			}
			if (!l.sync.inDimension(dim)) {
				// another dimension's duel (we changed dimension mid-duel): not drawn, not heard; it expires on its clock
				l.lod = 3;
				continue;
			}
			DuelStagePlan.Point a = l.sync.chestA();
			DuelStagePlan.Point b = l.sync.chestB();
			double mx = (a.x() + b.x()) / 2;
			double mz = (a.z() + b.z()) / 2;
			double dist = Math.sqrt(cam.distanceToSqr(mx, l.plan.groundY(), mz));
			int lod = dist <= LOD_FULL ? 0 : dist <= LOD_MOTION ? 1 : dist <= LOD_REST ? 2 : 3;
			l.lod = lod;
			if (lod == 3 || t < 0) continue;
			draw(ctx, pose, cam, l, t, lod);
			sounds(mc, l, t, lod, dist);
		}
	}

	private static void draw(LevelRenderContext ctx, PoseStack pose, Vec3 cam, Live l, double t, int lod) {
		Minecraft mc = Minecraft.getInstance();
		float h = (float) DiceTray.WORLD_BLOCKS_PER_U;
		for (int side = 0; side < 2; side++) {
			for (int die = 0; die < 2; die++) {
				l.plan.sample(side, die, t, S);
				if (!S.visible) continue;
				DieSample smp = S.tray;
				int round = S.round;
				if (lod == 2) {
					// far: nothing before the pair rests, then the exact rest pose
					if (t < l.plan.restAt(round, side)) continue;
					DiceRoll roll = l.plan.roll(round, side);
					roll.sample(die, roll.restTime(die) + 1e6, REST);
					smp = REST;
				}
				int light = LightCoordsUtil.getLightCoords(mc.level, BlockPos.containing(S.x, l.plan.groundY() + 0.05, S.z));
				pose.pushPose();
				pose.translate(l.plan.originX(side) - cam.x, l.plan.groundY() - cam.y, l.plan.originZ(side) - cam.z);
				YAW.identity().rotateY((float) Math.toRadians(l.plan.yawDeg(side)));
				pose.rotateAround(YAW, 0f, 0f, 0f); // 26.3 PoseStack has no mulPose(Quaternionf)
				DiceWorldDraw.shadow(pose, ctx.submitNodeCollector(), smp, h, 0f, light);
				if (S.scale < 1) {
					// a tied round's dice shrink into the thrower's hand (about the die centre)
					float cx = (float) (smp.x * h);
					float cy = (float) (smp.zc * h);
					float cz = (float) (smp.y * h);
					pose.translate(cx, cy, cz);
					pose.scale((float) S.scale, (float) S.scale, (float) S.scale);
					pose.translate(-cx, -cy, -cz);
				}
				DiceWorldDraw.die(pose, ctx.submitNodeCollector(), smp, h, 0f, light);
				pose.popPose();
			}
		}
	}

	/** Contact sounds (positional, by LOD) and dust at full LOD; silent for the duellists (their screens play it). */
	private static void sounds(Minecraft mc, Live l, double t, int lod, double dist) {
		int round = l.plan.roundAt(t);
		if (l.cursorRound != round) {
			for (int s = 0; s < 2; s++) {
				l.cursors[s] = DiceAudio.cursor(l.plan.roll(round, s).events());
				double rollMs = t - l.plan.throwAt(round, s);
				// joining late: what already happened stays silent
				if (rollMs > 0) l.cursors[s].reset(rollMs, true);
			}
			l.cursorRound = round;
		}
		boolean quiet = l.silent(mc);
		l.near = !quiet && lod == 1 && dist <= HEAR_FAR;
		l.round = round;
		for (int s = 0; s < 2; s++) {
			double rollMs = t - l.plan.throwAt(round, s);
			DuelStagePlan.Point c = l.plan.restCentre(round, s);
			l.audio[s].at(c.x(), c.y() + 0.1, c.z());
			l.audio[s].muted(quiet || lod != 0);
			l.audio[s].advance(l.cursors[s], rollMs, 0, l.listeners[s]);
		}
	}

	private static void contact(Live l, int side, MotionEvent e, boolean silent) {
		if (silent) return;
		if (l.near && (e.kind() == DiceEvent.REST || e.kind() == DiceEvent.CLACK)) {
			// 8–24 blocks: only the settle and the clacks, and only within 16
			DuelStagePlan.Point c = l.plan.restCentre(l.round, side);
			FxSounds.playAt(e.kind() == DiceEvent.REST ? "dice_settle" : "dice_clack", c.x(), c.y() + 0.1, c.z(), 0.6f,
				e.kind() == DiceEvent.REST ? 1.4f : 1.8f);
		}
		Minecraft mc = Minecraft.getInstance();
		if (l.lod == 0 && e.kind() == DiceEvent.FLOOR && MotionFx.particles("duel_world_dust", 3) && mc.level != null) {
			double wx = l.plan.worldX(side, e.x(), e.y());
			double wz = l.plan.worldZ(side, e.x(), e.y());
			for (int k = 0; k < 3; k++) {
				double ox = ((k * 37 + e.aux() * 11) % 7 - 3) * 0.02;
				double oz = ((k * 53 + e.aux() * 5) % 7 - 3) * 0.02;
				mc.level.addParticle(ParticleTypes.WHITE_ASH, wx + ox, l.plan.groundY() + 0.03, wz + oz, ox * 0.5, 0.02, oz * 0.5);
			}
		}
	}
}
