package dev.nezo.burmaldaholic.worldgen.client;

import dev.nezo.burmaldaholic.client.fx.CasinoParticle;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.fx.CoreParticles;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.worldgen.CasinoAttract;
import dev.nezo.burmaldaholic.worldgen.logic.AttractRules;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoKind;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.net.CasinoViewPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Casino attract mode on the client (global.md §4.13, lane J-L3):
 * <ul>
 *   <li><b>Marquee lights</b>: the roofline of every generated casino near the player chases in its theme colours
 *       (village gold, Piglin ember, End lilac-cyan); walking in runs a bright wave once around the roof and rings the
 *       attract chime (the arrival).</li>
 *   <li><b>Ambient motes</b> float around the player inside a casino.</li>
 *   <li><b>Casino blocks</b> within 16 blocks roll a glint once per 40 ticks (tables: chip glint over the felt, slot
 *       machines: a sparkle over the marquee, cashier / wheel / Plinko: a golden mote), paused while someone stands at
 *       them; idle slot machines chime once after 60 s with a viewer within 8 blocks (then not for 120 s).</li>
 * </ul>
 * Never symbols, numbers or "win" words (global §6.7); the casino particle budget applies.
 */
final class CasinoAttractFx {
	private record View(CasinoKind kind, Geometry.Box box, List<double[]> bulbs, AttractRules.Theme theme) {}

	/** Idle / viewer check of a slot machine's attract chime (the 60 s / 120 s timers do not need tick precision). */
	private static final int CHIME_CHECK_TICKS = 20;

	private static final List<View> NEAR = new ArrayList<>();
	private static final List<BlockPos> BLOCKS = new ArrayList<>();
	private static final Map<BlockPos, long[]> CHIME = new HashMap<>();
	private static View arrived;
	private static long arrivedTick = Long.MIN_VALUE;
	private static long tick;

	private CasinoAttractFx() {}

	static void onPayload(CasinoViewPayload p) {
		NEAR.clear();
		CasinoKind[] kinds = CasinoKind.values();
		for (CasinoViewPayload.Casino c : p.casinos()) {
			if (c.kind() < 0 || c.kind() >= kinds.length) continue;
			Geometry.Box box = new Geometry.Box(c.minX(), c.minY(), c.minZ(), c.maxX(), c.maxY(), c.maxZ());
			CasinoKind kind = kinds[c.kind()];
			NEAR.add(new View(kind, box, AttractRules.bulbs(box), AttractRules.theme(kind)));
		}
		if (p.arrived() >= 0 && p.arrived() < NEAR.size()) arrive(NEAR.get(p.arrived()));
	}

	private static void arrive(View v) {
		arrived = v;
		arrivedTick = tick;
		FxSounds.play("attract_chime", 1f);
		FxSounds.play("attract_chime", 0.7f, 1.26f);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		// a swirl of themed motes greets the player
		int n = CasinoParticle.burstAllowance(mc.level, FxSettings.reduceMotion() ? 6 : 16, false);
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix(mc.player.getId(), v.box().minX(), v.box().minZ()));
		Vec3 at = mc.player.position();
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / Math.max(1, n);
			double r = 1 + rng.nextDouble() * 1.5;
			glow(mc.level, CasinoAttract.mote(), at.x + Math.cos(a) * r, at.y + 0.3 + rng.nextDouble() * 1.6, at.z + Math.sin(a) * r,
				i % 2 == 0 ? v.theme().bulbOn() : v.theme().bulbAlt());
		}
	}

	static void reset() {
		NEAR.clear();
		BLOCKS.clear();
		CHIME.clear();
		arrived = null;
		arrivedTick = Long.MIN_VALUE;
	}

	/** Test hook: casinos the client knows near it. */
	static int nearCount() {
		return NEAR.size();
	}

	static void tick(Minecraft mc) {
		tick++;
		ClientLevel level = mc.level;
		Player player = mc.player;
		if (level == null || player == null) return;
		Vec3 eye = player.position();
		boolean reduced = FxSettings.reduceMotion();
		int chaseTicks = reduced ? AttractRules.CHASE_TICKS * 4 : AttractRules.CHASE_TICKS;
		// marquee chase (+ the arrival wave)
		if (tick % chaseTicks == 0 || (arrived != null && tick - arrivedTick < AttractRules.ARRIVAL_WAVE_TICKS)) {
			for (View v : NEAR) {
				boolean waving = v == arrived && tick - arrivedTick < AttractRules.ARRIVAL_WAVE_TICKS && !reduced;
				int n = v.bulbs().size();
				for (int i = 0; i < n; i++) {
					double[] b = v.bulbs().get(i);
					boolean lit = waving ? AttractRules.arrivalLit(i, n, tick - arrivedTick) : tick % chaseTicks == 0 && AttractRules.lit(i, tick / (reduced ? 4 : 1));
					if (!lit) continue;
					double dx = b[0] - eye.x;
					double dz = b[2] - eye.z;
					if (dx * dx + dz * dz > 32 * 32) continue;
					glow(level, CasinoAttract.bulb(), b[0], b[1], b[2], waving ? 0xFFFFFFFF : (i / AttractRules.CHASE_GROUP) % 2 == 0 ? v.theme().bulbOn() : v.theme().bulbAlt());
				}
			}
		}
		// ambient motes inside
		if (tick % (reduced ? 12 : 4) == 0) {
			for (View v : NEAR) {
				if (!v.box().contains(eye.x, eye.y, eye.z, 0.5)) continue;
				SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix((int) tick, player.getId()));
				double x = eye.x + (rng.nextDouble() - 0.5) * 12;
				double z = eye.z + (rng.nextDouble() - 0.5) * 12;
				double y = eye.y + rng.nextDouble() * 2.5;
				if (v.box().contains(x, y, z, 0)) glow(level, CasinoAttract.mote(), x, y, z, rng.nextInt(2) == 0 ? v.theme().bulbOn() : v.theme().bulbAlt());
			}
		}
		// casino blocks (J19)
		if (tick % 40 == 0) scan(level, player.blockPosition());
		for (BlockPos pos : BLOCKS) attract(level, pos, player);
	}

	/** Refreshes the list of casino blocks within 16 blocks (client block entities; no server data). */
	private static void scan(ClientLevel level, BlockPos center) {
		BLOCKS.clear();
		int r = AttractRules.AMBIENT_RADIUS;
		for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
			for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
				if (chunk == null) continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof CasinoTableBlockEntity && be.getBlockPos().distSqr(center) <= r * r) BLOCKS.add(be.getBlockPos().immutable());
				}
			}
		}
		CHIME.keySet().retainAll(BLOCKS);
	}

	/**
	 * One casino block: nothing at all on most ticks — the block state / player query runs only on its staggered roll
	 * tick (once per {@link AttractRules#ROLL_TICKS}) or its chime check (once per {@link #CHIME_CHECK_TICKS}), so the
	 * cost stays flat with many machines in range.
	 */
	private static void attract(ClientLevel level, BlockPos pos, Player viewer) {
		boolean roll = AttractRules.rolls(pos.getX(), pos.getY(), pos.getZ(), tick);
		boolean chimeCheck = Math.floorMod(tick + pos.hashCode(), (long) CHIME_CHECK_TICKS) == 0;
		if (!roll && !chimeCheck) return;
		String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
		boolean slot = path.startsWith("slot_machine");
		if (!roll && !slot) return;
		boolean busy = !level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(pos).inflate(AttractRules.BUSY_RADIUS - 0.5)).isEmpty();
		if (slot && chimeCheck) chime(pos, viewer, busy);
		if (busy || !roll) return;
		SeedMix.FxRng rng = new SeedMix.FxRng(SeedMix.mix(pos.getX(), pos.getY(), pos.getZ(), (int) (tick / 40)));
		if (rng.nextInt(2) != 0) return; // one glint per ~80 t per block on average
		double x = pos.getX() + 0.2 + rng.nextDouble() * 0.6;
		double z = pos.getZ() + 0.2 + rng.nextDouble() * 0.6;
		ParticleOptions type;
		double y;
		if (slot) {
			type = CoreParticles.get("sparkle");
			y = pos.getY() + 2.15;
		} else if (path.contains("table")) {
			type = CoreParticles.get("chip_glint");
			y = pos.getY() + 1.1;
		} else {
			type = CoreParticles.get("golden_mote");
			y = pos.getY() + 1.2;
		}
		if (type != null && CasinoParticle.burstAllowance(level, 1, false) >= 1) level.addParticle(type, x, y, z, 0, 0.01, 0);
	}

	/** Idle slot machine chime: 60 s idle with a viewer within 8 blocks, then 120 s quiet (client timers only). */
	private static void chime(BlockPos pos, Player viewer, boolean busy) {
		long[] t = CHIME.computeIfAbsent(pos, p -> new long[] {tick, Long.MIN_VALUE / 2});
		if (busy) {
			t[0] = tick;
			return;
		}
		boolean near = viewer.blockPosition().distSqr(pos) <= AttractRules.CHIME_RADIUS * AttractRules.CHIME_RADIUS;
		if (AttractRules.chime(tick, t[0], t[1], near, false)) {
			t[1] = tick;
			FxSounds.playAt("attract_chime", pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 1f, 1f);
		}
	}

	private static void glow(ClientLevel level, SimpleParticleType type, double x, double y, double z, int argb) {
		if (type == null || CasinoParticle.burstAllowance(level, 1, false) < 1) return;
		level.addParticle(type, x, y, z, ((argb >> 16) & 0xFF) / 255.0, ((argb >> 8) & 0xFF) / 255.0, (argb & 0xFF) / 255.0);
	}
}
