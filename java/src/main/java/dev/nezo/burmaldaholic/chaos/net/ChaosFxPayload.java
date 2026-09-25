package dev.nezo.burmaldaholic.chaos.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: world decoration of a chaos event at <b>real</b> spots (global.md §4.6, §6.4–§6.5): chip-pile
 * pops, diamond-rain glint columns (one per real drop, before the item lands), mob-wave summon runes (where the mobs
 * will appear), teleport rings (origin and target), buff / curse rings around a player. Sent to the target and to
 * spectators within 16 blocks; pure decoration — the server already did (or will do, at the stated tick) the effect.
 *
 * @param fx     what to draw ({@link #CHIP_POP}, {@link #DIAMOND_COLUMN}, …)
 * @param points world positions (≤ {@link #MAX_POINTS})
 * @param arg    fx-specific small int (diamond index for the chime pitch; tone for rings)
 * @param seed   cosmetic seed (public values only)
 */
public record ChaosFxPayload(String fx, List<Vec3> points, int arg, int seed) implements CustomPacketPayload {
	public static final String CHIP_POP = "chip_pop";
	public static final String BUFF_RING = "buff_ring";
	public static final String GOLD_RING = "gold_ring";
	public static final String CURSE_SPIRAL = "curse_spiral";
	public static final String DIAMOND_COLUMN = "diamond_column";
	public static final String XP_SPARKLE = "xp_sparkle";
	public static final String MOB_RUNE = "mob_rune";
	public static final String TELEPORT_RING = "teleport_ring";
	public static final int MAX_POINTS = 32;

	public static CustomPacketPayload.Type<ChaosFxPayload> TYPE;

	private static final StreamCodec<RegistryFriendlyByteBuf, Vec3> VEC3 = StreamCodec.of((buf, v) -> {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}, buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));

	public static final StreamCodec<RegistryFriendlyByteBuf, ChaosFxPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(32), ChaosFxPayload::fx,
		VEC3.apply(ByteBufCodecs.list(MAX_POINTS)), ChaosFxPayload::points,
		ByteBufCodecs.VAR_INT, ChaosFxPayload::arg,
		ByteBufCodecs.INT, ChaosFxPayload::seed,
		ChaosFxPayload::new);

	public ChaosFxPayload {
		points = points.size() > MAX_POINTS ? List.copyOf(points.subList(0, MAX_POINTS)) : List.copyOf(new ArrayList<>(points));
	}

	@Override
	public Type<ChaosFxPayload> type() {
		return TYPE;
	}
}
