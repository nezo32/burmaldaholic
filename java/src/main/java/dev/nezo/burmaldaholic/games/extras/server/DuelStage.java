package dev.nezo.burmaldaholic.games.extras.server;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.anim.dice.DiceFaces;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.sound.CasinoSounds;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.DiceDuel;
import dev.nezo.burmaldaholic.games.extras.logic.DuelStagePlan;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * The PvP Dice Duel in the world (docs/design/animation/tables.md §3.4, §3.6): four item-display dice (model
 * {@code burmaldaholic:extras/dice_display}) thrown from the two players to the ground between them on the same
 * {@link DuelStagePlan} (= the screens' throw paths and round beats), a number billboard over each landed pair, tied
 * rounds picked up and thrown again, the winner's burst at the last reveal and the throw / landing sounds for the
 * bystanders within 16 blocks (the duellists hear their screens). Motion comes from the server: every 3 ticks each die
 * is moved to where the plan will be when the client's 3-tick interpolation ends ({@code teleport_duration} and
 * {@code interpolation_duration} = 3). At most {@link #MAX} duels are staged at once; beyond that a duel gets no
 * in-world dice. Entities are tagged and never saved as ours: a leftover loaded from a save is removed.
 */
public final class DuelStage {
	/** At most this many duels are staged at once (tables.md §3.6). */
	public static final int MAX = 8;
	static final String TAG = "burmaldaholic_duel_die";
	private static final int STEP = 3;
	private static final double HEAR = 16;
	private static final List<Stage> STAGES = new ArrayList<>();
	/** Our live entities (registered BEFORE they join the level: the entity-load hook fires while spawning). */
	private static final Set<UUID> LIVE = new HashSet<>();

	private DuelStage() {}

	private static final class Stage {
		final ServerLevel level;
		final long start;
		final DuelStagePlan plan;
		final Set<UUID> duellists;
		/** winner side 0 / 1, -1 = refund */
		final int winner;
		final Display.ItemDisplay[] dice = new Display.ItemDisplay[4];
		final Display.TextDisplay[] totals = new Display.TextDisplay[2];
		final int[] totalRound = {-1, -1};
		final int[] thrownRound = {-1, -1};
		final int[] landedRound = {-1, -1};
		boolean burst;

		Stage(ServerLevel level, long start, DuelStagePlan plan, Set<UUID> duellists, int winner) {
			this.level = level;
			this.start = start;
			this.plan = plan;
			this.duellists = duellists;
			this.winner = winner;
		}
	}

	/** Staged duels (tests). */
	public static int active() {
		return STAGES.size();
	}

	/** Every live stage entity (tests: 4 dice + the totals shown now). */
	public static List<Entity> entities() {
		List<Entity> out = new ArrayList<>();
		for (Stage s : STAGES) {
			for (Entity e : s.dice) {
				if (e != null) {
					out.add(e);
				}
			}
			for (Entity e : s.totals) {
				if (e != null) {
					out.add(e);
				}
			}
		}
		return out;
	}

	/**
	 * Stages a resolved PvP duel between {@code a} (side 0) and {@code b} (side 1), started at game time {@code start}
	 * with the screens' {@code seed}. Returns false when {@link #MAX} duels are already staged (no in-world dice).
	 */
	public static boolean start(ServerPlayer a, ServerPlayer b, DiceDuel.PvpDuel duel, int seed, long start) {
		if (a.level() != b.level() || !(a.level() instanceof ServerLevel level)) {
			return false;
		}
		List<int[][]> rounds = new ArrayList<>();
		for (DiceDuel.PvpRound r : duel.rounds()) {
			rounds.add(new int[][] {{r.a().a(), r.a().b()}, {r.b().a(), r.b().b()}});
		}
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
		if (STAGES.size() >= MAX || rounds.isEmpty()) {
			return false;
		}
		double groundY = ground(level, by, chestA, chestB);
		DuelStagePlan plan = new DuelStagePlan(seed, rounds, new DuelStagePlan.Point(chestA.x, chestA.y, chestA.z),
			new DuelStagePlan.Point(chestB.x, chestB.y, chestB.z), groundY);
		Stage s = new Stage(level, start, plan, duellists, winner);
		STAGES.add(s);
		step(s, Math.max(0, level.getServer().overworld().getGameTime() - start));
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

	public static void tick(MinecraftServer server) {
		if (STAGES.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		STAGES.removeIf(s -> {
			long elapsed = now - s.start;
			if (elapsed < 0) {
				return false;
			}
			if (elapsed * 50 >= s.plan.endMs() + 100) {
				discard(s);
				return true;
			}
			if (elapsed % STEP == 0) {
				step(s, elapsed);
			}
			return false;
		});
	}

	private static final DuelStagePlan.Sample SAMPLE = new DuelStagePlan.Sample();

	/** Moves every die to where the plan is when this step's interpolation ends; totals, sounds, the burst. */
	private static void step(Stage s, long elapsed) {
		double target = (elapsed + STEP) * 50.0;
		double nowMs = elapsed * 50.0;
		for (int side = 0; side < 2; side++) {
			for (int die = 0; die < 2; die++) {
				s.plan.sample(side, die, target, SAMPLE);
				int k = side * 2 + die;
				Display.ItemDisplay d = s.dice[k];
				if (d != null && d.isRemoved()) {
					LIVE.remove(d.getUUID()); // unloaded with its chunk or killed: a fresh one takes over
					d = null;
					s.dice[k] = null;
				}
				if (d == null && SAMPLE.visible) {
					// a new die appears where it starts (the chest), then glides with the plan
					DuelStagePlan.Sample first = new DuelStagePlan.Sample();
					s.plan.sample(side, die, Math.max(nowMs, DuelTimeline.throwAt(SAMPLE.round, side)), first);
					d = spawnDie(s.level, first.visible ? first : SAMPLE);
					s.dice[k] = d;
				}
				if (d != null) {
					pose(d, SAMPLE);
				}
			}
			// sounds for the bystanders: the throw and the landing of each round
			int r = s.plan.roundAt(nowMs);
			if (s.thrownRound[side] != r && nowMs >= DuelTimeline.throwAt(r, side)) {
				s.thrownRound[side] = r;
				sound(s, "dice_throw", s.plan.restCentre(r, side), 1f);
			}
			if (s.landedRound[side] != r && nowMs >= DuelTimeline.throwAt(r, side) + s.plan.throwMs()) {
				s.landedRound[side] = r;
				sound(s, "dice_bounce", s.plan.restCentre(r, side), 1f);
			}
			total(s, side, target);
		}
		int last = s.plan.roundCount() - 1;
		if (!s.burst && nowMs >= DuelTimeline.reveal(last)) {
			s.burst = true;
			if (s.winner >= 0) {
				DuelStagePlan.Point c = s.plan.restCentre(last, s.winner);
				s.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x(), c.y() + 0.2, c.z(), 10, 0.25, 0.2, 0.25, 0.02);
				s.level.sendParticles(ParticleTypes.WAX_OFF, c.x(), c.y() + 0.3, c.z(), 6, 0.2, 0.2, 0.2, 0.05);
			} else {
				DuelStagePlan.Point c = s.plan.restCentre(last, 0);
				s.level.sendParticles(ParticleTypes.CLOUD, c.x(), c.y() + 0.1, c.z(), 4, 0.2, 0.05, 0.2, 0.01);
			}
		}
	}

	private static Display.ItemDisplay spawnDie(ServerLevel level, DuelStagePlan.Sample at) {
		Display.ItemDisplay d = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
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
		if (at.visible) {
			d.setPos(at.x, at.y, at.z);
		}
		d.setTransformation(transformation(at));
		d.setTransformationInterpolationDelay(0); // forced: restarts the client's 3-tick interpolation
	}

	/** Heading, then the tumble across the throw, then the face-up turn of the face (DiceFaces.upRotation). */
	static Transformation transformation(DuelStagePlan.Sample at) {
		float scale = (float) (DuelStagePlan.DIE * (at.visible ? at.scale : 0));
		Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(-at.yaw));
		if (!at.faceShown) {
			q.rotateX((float) Math.toRadians(at.tumble));
		}
		double[] up = DiceFaces.upRotation(at.face);
		q.rotateAxis((float) Math.toRadians(up[3]), (float) up[0], (float) up[1], (float) up[2]);
		return new Transformation(new Vector3f(), q, new Vector3f(scale, scale, scale), new Quaternionf());
	}

	/** The number billboard over a landed pair (only the number: no translation needed). */
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
		if (t != null && s.totalRound[side] == r) {
			return;
		}
		if (t != null) {
			remove(t);
		}
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

	/** A positional sound for the bystanders (not the duellists: their screens play it). */
	private static void sound(Stage s, String id, DuelStagePlan.Point at, float pitch) {
		SoundEvent e = CasinoSounds.get(id);
		if (e == null) {
			return;
		}
		Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(e);
		SoundSource src = CasinoSounds.sourceOf(id);
		long seed = s.level.getRandom().nextLong();
		for (ServerPlayer p : s.level.players()) {
			if (!s.duellists.contains(p.getUUID()) && p.distanceToSqr(at.x(), at.y(), at.z()) <= HEAR * HEAR) {
				p.connection.send(new ClientboundSoundPacket(holder, src, at.x(), at.y(), at.z(), 0.8f, pitch, seed));
			}
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
		if (entity.entityTags().contains(TAG) && !LIVE.contains(entity.getUUID())) {
			entity.discard();
		}
	}
}
