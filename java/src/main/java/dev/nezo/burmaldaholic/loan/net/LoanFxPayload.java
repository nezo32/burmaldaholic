package dev.nezo.burmaldaholic.loan.net;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: a Debt Collector squad has just spawned (global.md §4.9). The debtor's client plays the arrival
 * (three knocks, "Knock knock" card, red vignette pulses); every receiver within 16 blocks sees the smoke columns at
 * the <b>real</b> member positions and hears the knocks at the debtor. Decoration only: the members already exist.
 *
 * @param debtor  the debtor's UUID
 * @param at      the debtor's position (knocks are positional there for spectators)
 * @param members member spawn positions (feet)
 */
public record LoanFxPayload(UUID debtor, Vec3 at, List<Vec3> members) implements CustomPacketPayload {
	public static final int MAX_MEMBERS = 16;
	public static CustomPacketPayload.Type<LoanFxPayload> TYPE;

	private static final StreamCodec<RegistryFriendlyByteBuf, Vec3> VEC3 = StreamCodec.of((buf, v) -> {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}, buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));

	public static final StreamCodec<RegistryFriendlyByteBuf, LoanFxPayload> CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, LoanFxPayload::debtor,
		VEC3, LoanFxPayload::at,
		VEC3.apply(ByteBufCodecs.list(MAX_MEMBERS)), LoanFxPayload::members,
		LoanFxPayload::new);

	public LoanFxPayload {
		members = List.copyOf(members.size() > MAX_MEMBERS ? members.subList(0, MAX_MEMBERS) : members);
	}

	@Override
	public Type<LoanFxPayload> type() {
		return TYPE;
	}
}
