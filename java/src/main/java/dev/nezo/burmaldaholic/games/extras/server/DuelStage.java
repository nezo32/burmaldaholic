package dev.nezo.burmaldaholic.games.extras.server;

import com.mojang.math.Transformation;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.sound.CasinoSounds;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.DiceDuel;
import dev.nezo.burmaldaholic.games.extras.logic.DuelStagePlan;
import dev.nezo.burmaldaholic.games.extras.net.DuelStageSync;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The PvP Dice Duel in the world (DI §6, PHASE2B §4 DCb). One {@link DuelStagePlan} per duel (the screens' solver,
 * keys and beats):
 * <ul>
 *   <li><b>Modded players</b> within {@link #SYNC_RANGE} blocks get ONE {@link DuelStageSync} and render the dice
 *       themselves ({@code DuelStageRenderer}, smooth at any fps, per-contact sounds). They receive no item-display
 *       entities and no sound packets for the duel.</li>
 *   <li><b>Players without the mod</b> see four item displays ({@link #modded} false), posed every {@link #STEP} ticks
 *       to the plan's model quaternion at the step's end; the solver caps the spin at 22 rad/s, so a 2-tick step turns
 *       less than 180° and the client's slerp never takes the long way round (tested). They hear the throw, the first
 *       landing and the settle of each pair as positional sound packets (bystanders within {@link #HEAR}).</li>
 *   <li>Everyone sees the total billboards (text displays, cheap) at the pair's rest + count-up, and the winner burst at
 *       the last reveal.</li>
 * </ul>
 * At most {@link #MAX} duels are staged at once. Entities are tagged, never saved, and a leftover loaded from an old
 * save is removed.
 */
public final class DuelStage {
	/** At most this many duels are staged at once. */
	public static final int MAX = 8;
	static final String TAG = "burmaldaholic_duel_die";
	/** Display-entity step (ticks): ≤ 2 so a step never turns a die by 180° or more. */
	public static final int STEP = 2;
	/** Bystander hearing range of the fallback sounds. */
	private static final double HEAR = 16;
	/** Modded players within this range render the stage themselves. */
	public static final double SYNC_RANGE = 48;
	private static final List<Stage> STAGES = new ArrayList<>();
	/** Our live entities (registered BEFORE they join the level: the entity-load hook fires while spawning). */
	private static final Set<UUID> LIVE = new HashSet<>();
	private static int nextId = 1;

	/** Whether a player renders the stage itself (has the mod's {@link DuelStageSync} channel). Tests may swap it. */
	public static volatile Predicate<ServerPlayer> modded = p -> DuelStageSync.TYPE != null && !p.hasDisconnected()
		&& ServerPlayNetworking.canSend(p, DuelStageSync.TYPE);
	/** Test hook: every stage sound packet sent (player, sound id); null in production. */
	public static volatile @Nullable BiConsumer<ServerPlayer, String> soundSink;
	/** Test hook: every sync sent (player, sync); null in production. */
	public static volatile @Nullable BiConsumer<ServerPlayer, DuelStageSync> syncSink;

	private DuelStage() {}

	private static final class Stage {
		final ServerLevel level;
		final long start;
		final DuelStagePlan plan;
		final DuelStageSync sync;
		final Set<UUID> duellists;
		final Set<UUID> synced = new HashSet<>();
		/** winner side 0 / 1, -1 = refund */
		final int winner;
		final DieDisplay[] dice = new DieDisplay[4];
		final Display.TextDisplay[] totals = new Display.TextDisplay[2];
		final int[] totalRound = {-1, -1};
		/** per side: round of the last throw / first floor / settle sound sent */
		final int[][] sounded = {{-1, -1, -1}, {-1, -1, -1}};
		final boolean[] restingSent = new boolean[4];
		boolean burst;

		Stage(ServerLevel level, long start, DuelStagePlan plan, DuelStageSync sync, Set<UUID> duellists, int winner) {
			this.level = level;
			this.start = start;
			this.plan = plan;
			this.sync = sync;
			this.duellists = duellists;
			this.winner = winner;
		}
	}

	/** An item-display die that only players WITHOUT the mod are sent (modded clients render the stage). */
	static final class DieDisplay extends Display.ItemDisplay {
		DieDisplay(ServerLevel level) {
			super(EntityTypes.ITEM_DISPLAY, level);
		}

		@Override
		public boolean broadcastToPlayer(ServerPlayer player) {
			return !modded.test(player);
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}
	}

	/** Staged duels (tests). */
	public static int active() {
		return STAGES.size();
	}

	/** Every live stage entity (tests: the dice shown now + the totals shown now). */
	public static List<Entity> entities() {
		List<Entity> out = new ArrayList<>();
		for (Stage s : STAGES) {
			for (Entity e : s.dice) if (e != null) out.add(e);
			for (Entity e : s.totals) if (e != null) out.add(e);
		}
		return out;
	}

	/** The plan of the staged duel started at {@code start} (tests), or null. */
	public static @Nullable DuelStagePlan plan(long start) {
		for (Stage s : STAGES) if (s.start == start) return s.plan;
		return null;
	}

	/**
	 * Stages a resolved PvP duel between {@code a} (side 0) and {@code b} (side 1), started at game time {@code start}
	 * with the screens' {@code seed}. Returns false when {@link #MAX} duels are already staged (no in-world dice).
	 */
	public static boolean start(ServerPlayer a, ServerPlayer b, DiceDuel.PvpDuel duel, int seed, long start) {
		if (a.level() != b.level() || !(a.level() instanceof ServerLevel level)) return false;
		List<int[][]> rounds = new ArrayList<>();
		for (DiceDuel.PvpRound r : duel.rounds()) rounds.add(new int[][] {{r.a().a(), r.a().b()}, {r.b().a(), r.b().b()}});
		int winner = switch (duel.result()) {
			case A -> 0;
			case B -> 1;
			case REFUND -> -1;
		};
		return stage(level, chest(a), chest(b), a, rounds, winner, seed, start, Set.of(a.getUUID(), b.getUUID()));
	}

	/**
	 * Stages a duel between two chest positions (also previews / client GameTests). {@code rounds}: per round
	 * {@code [side][die]} faces; {@code winner} 0 / 1, -1 = refund; {@code duellists} hear their screens, not the stage.
	 */
	public static boolean stage(ServerLevel level, Vec3 chestA, Vec3 chestB, @Nullable Entity by, List<int[][]> rounds, int winner, int seed, long start,
			Set<UUID> duellists) {
		if (STAGES.size() >= MAX || rounds.isEmpty()) return false;
		double groundY = ground(level, by, chestA, chestB);
		DuelStagePlan.Point pa = new DuelStagePlan.Point(chestA.x, chestA.y, chestA.z);
		DuelStagePlan.Point pb = new DuelStagePlan.Point(chestB.x, chestB.y, chestB.z);
		DuelStagePlan plan = new DuelStagePlan(seed, rounds, pa, pb, groundY);
		DuelStageSync sync = new DuelStageSync(nextId++, seed, start, pa, pb, groundY, rounds, winner, List.copyOf(duellists));
		Stage s = new Stage(level, start, plan, sync, duellists, winner);
		STAGES.add(s);
		syncNearby(s);
		long elapsed = Math.max(0, level.getServer().overworld().getGameTime() - start);
		step(s, elapsed);
		return true;
	}

	private static Vec3 chest(ServerPlayer p) {
		return new Vec3(p.getX(), p.getY() + p.getBbHeight() * 0.62, p.getZ());
	}

	/** The ground under the midpoint (raycast down, 3 blocks below the players' feet); none: the players' feet height. */
	private static double ground(ServerLevel level, @Nullable Entity by, Vec3 a, Vec3 b) {
		Vec3 mid = a.add(b).scale(0.5);
		double feet = mid.y - 1.1;
		ClipContext ctx = by != null ? new ClipContext(mid, new Vec3(mid.x, feet - 3, mid.z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, by)
			: new ClipContext(mid, new Vec3(mid.x, feet - 3, mid.z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
		BlockHitResult hit = level.clip(ctx);
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().y : feet;
	}

	private static Vec3 mid(Stage s) {
		DuelStagePlan.Point a = s.sync.chestA();
		DuelStagePlan.Point b = s.sync.chestB();
		return new Vec3((a.x() + b.x()) / 2, s.plan.groundY(), (a.z() + b.z()) / 2);
	}

	/** Sends the sync to every modded player in range who has not got it yet (late arrivals too). */
	private static void syncNearby(Stage s) {
		Vec3 m = mid(s);
		for (ServerPlayer p : s.level.players()) {
			if (s.synced.contains(p.getUUID()) || p.distanceToSqr(m) > SYNC_RANGE * SYNC_RANGE || !modded.test(p)) continue;
			s.synced.add(p.getUUID());
			BiConsumer<ServerPlayer, DuelStageSync> sink = syncSink;
			if (sink != null) sink.accept(p, s.sync);
			if (DuelStageSync.TYPE != null && ServerPlayNetworking.canSend(p, DuelStageSync.TYPE)) ServerPlayNetworking.send(p, s.sync);
		}
	}

	public static void tick(MinecraftServer server) {
		if (STAGES.isEmpty()) return;
		long now = server.overworld().getGameTime();
		STAGES.removeIf(s -> {
			long elapsed = now - s.start;
			if (elapsed < 0) return false;
			if (elapsed * 50 >= s.plan.endMs() + 100) {
				discard(s);
				return true;
			}
			if (elapsed % 10 == 0) syncNearby(s);
			if (elapsed % STEP == 0) step(s, elapsed);
			return false;
		});
	}

	private static final DuelStagePlan.Sample SAMPLE = new DuelStagePlan.Sample();
	private static final DuelStagePlan.Sample FIRST = new DuelStagePlan.Sample();

	/** Moves every fallback die to where the plan is when this step's interpolation ends; totals, sounds, the burst. */
	private static void step(Stage s, long elapsed) {
		double target = (elapsed + STEP) * 50.0;
		double nowMs = elapsed * 50.0;
		for (int side = 0; side < 2; side++) {
			for (int die = 0; die < 2; die++) {
				s.plan.sample(side, die, target, SAMPLE);
				int k = side * 2 + die;
				DieDisplay d = s.dice[k];
				if (d != null && d.isRemoved()) {
					LIVE.remove(d.getUUID()); // unloaded with its chunk or killed: a fresh one takes over
					d = null;
					s.dice[k] = null;
				}
				if (d == null && SAMPLE.visible) {
					// a new die appears where it is now (the hand at the release), then glides with the plan
					s.plan.sample(side, die, Math.max(nowMs, s.plan.throwAt(SAMPLE.round, side)), FIRST);
					d = spawnDie(s.level, FIRST.visible ? FIRST : SAMPLE);
					s.dice[k] = d;
					s.restingSent[k] = false;
				}
				if (d != null) {
					// at rest nothing changes: no packets until the pick-up / the next round
					boolean still = SAMPLE.visible && SAMPLE.resting && SAMPLE.scale >= 1;
					if (!(still && s.restingSent[k])) pose(d, SAMPLE);
					s.restingSent[k] = still;
				}
			}
			sounds(s, side, nowMs);
			total(s, side, target);
		}
		int last = s.plan.roundCount() - 1;
		if (!s.burst && nowMs >= DuelTimeline.reveal(last)) {
			s.burst = true;
			DuelStagePlan.Point c = s.plan.restCentre(last, Math.max(0, s.winner));
			if (s.winner >= 0) {
				s.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x(), c.y() + 0.2, c.z(), 10, 0.25, 0.2, 0.25, 0.02);
				s.level.sendParticles(ParticleTypes.WAX_OFF, c.x(), c.y() + 0.3, c.z(), 6, 0.2, 0.2, 0.2, 0.05);
			} else {
				s.level.sendParticles(ParticleTypes.CLOUD, c.x(), c.y() + 0.1, c.z(), 4, 0.2, 0.05, 0.2, 0.01);
			}
		}
	}

	/** Fallback sounds of a pair for unmodded bystanders: the throw, the first landing and the settle (once per round). */
	private static void sounds(Stage s, int side, double nowMs) {
		int r = s.plan.roundAt(nowMs);
		DuelStagePlan.Point at = s.plan.restCentre(r, side);
		int[] done = s.sounded[side];
		if (done[0] != r && nowMs >= s.plan.throwAt(r, side)) {
			done[0] = r;
			sound(s, "dice_throw", at, 1.3f, 0.5f);
		}
		double floor = s.plan.firstFloor(r, side);
		if (done[1] != r && floor >= 0 && nowMs >= floor) {
			done[1] = r;
			sound(s, "dice_bounce", at, 1.15f, 0.8f);
		}
		if (done[2] != r && nowMs >= s.plan.restAt(r, side)) {
			done[2] = r;
			sound(s, "dice_settle", at, 1.4f, 0.8f);
		}
	}

	private static DieDisplay spawnDie(ServerLevel level, DuelStagePlan.Sample at) {
		DieDisplay d = new DieDisplay(level);
		ItemStack die = new ItemStack(Items.PAPER);
		die.set(DataComponents.ITEM_MODEL, Burmaldaholic.id("extras/dice_display"));
		d.setItemStack(die);
		d.setItemTransform(ItemDisplayContext.NONE);
		d.setPos(at.x, at.y, at.z);
		d.addTag(TAG);
		d.setNoGravity(true);
		d.setViewRange(0.5f); // 32 blocks: bystanders within 16 see everything
		d.setShadowRadius(0.12f);
		d.setShadowStrength(0.6f);
		d.setTransformation(transformation(at));
		LIVE.add(d.getUUID());
		level.addFreshEntity(d);
		d.setPosRotInterpolationDuration(STEP);
		d.setTransformationInterpolationDuration(STEP);
		return d;
	}

	private static void pose(Display.ItemDisplay d, DuelStagePlan.Sample at) {
		if (at.visible) d.setPos(at.x, at.y, at.z);
		d.setTransformation(transformation(at));
		d.setTransformationInterpolationDelay(0); // forced: restarts the client's STEP-tick interpolation
	}

	/** The plan's MC model quaternion as {@code left_rotation} (the item model carries the canonical labels). */
	static Transformation transformation(DuelStagePlan.Sample at) {
		float scale = (float) (DuelStagePlan.DIE * (at.visible ? at.scale : 0));
		Quaternionf q = new Quaternionf((float) at.q[1], (float) at.q[2], (float) at.q[3], (float) at.q[0]);
		return new Transformation(new Vector3f(), q, new Vector3f(scale, scale, scale), new Quaternionf());
	}

	/** The number billboard over a counted pair (only the number: no translation needed). */
	private static void total(Stage s, int side, double tMs) {
		int r = s.plan.roundAt(tMs);
		boolean show = s.plan.totalVisible(side, tMs);
		Display.TextDisplay t = s.totals[side];
		if (!show) {
			if (t != null) {
				remove(t);
				s.totals[side] = null;
				s.totalRound[side] = -1;
			}
			return;
		}
		if (t != null && s.totalRound[side] == r) return;
		if (t != null) remove(t);
		DuelStagePlan.Point c = s.plan.restCentre(r, side);
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, s.level);
		d.setPos(c.x(), c.y() + 0.45, c.z());
		d.addTag(TAG);
		d.setNoGravity(true);
		d.setText(Texts.number(s.plan.total(r, side)));
		d.setBackgroundColor(0x80180A28);
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.setViewRange(0.5f);
		d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.2f, 1.2f, 1.2f), new Quaternionf()));
		LIVE.add(d.getUUID());
		s.level.addFreshEntity(d);
		s.totals[side] = d;
		s.totalRound[side] = r;
	}

	/** A positional sound for unmodded bystanders (not the duellists: their screens play it; modded: their renderer). */
	private static void sound(Stage s, String id, DuelStagePlan.Point at, float pitch, float volume) {
		SoundEvent e = CasinoSounds.get(id);
		if (e == null) return;
		Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(e);
		SoundSource src = CasinoSounds.sourceOf(id);
		long seed = s.level.getRandom().nextLong();
		for (ServerPlayer p : s.level.players()) {
			if (s.duellists.contains(p.getUUID()) || p.distanceToSqr(at.x(), at.y(), at.z()) > HEAR * HEAR || modded.test(p)) continue;
			BiConsumer<ServerPlayer, String> sink = soundSink;
			if (sink != null) sink.accept(p, id);
			p.connection.send(new ClientboundSoundPacket(holder, src, at.x(), at.y(), at.z(), volume, pitch, seed));
		}
	}

	private static void remove(Entity e) {
		LIVE.remove(e.getUUID());
		e.discard();
	}

	private static void discard(Stage s) {
		for (int i = 0; i < s.dice.length; i++) {
			if (s.dice[i] != null) {
				remove(s.dice[i]);
				s.dice[i] = null;
			}
		}
		for (int i = 0; i < s.totals.length; i++) {
			if (s.totals[i] != null) {
				remove(s.totals[i]);
				s.totals[i] = null;
			}
		}
	}

	/** Server stopping: every staged die goes (they are never saved as ours). */
	public static void stopAll(@Nullable MinecraftServer server) {
		STAGES.forEach(DuelStage::discard);
		STAGES.clear();
		LIVE.clear();
	}

	public static void clear() {
		STAGES.clear();
		LIVE.clear();
	}

	/** A duel die loaded from a save (its chunk was saved mid-duel): not ours any more → removed. */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity.entityTags().contains(TAG) && !LIVE.contains(entity.getUUID())) entity.discard();
	}
}
