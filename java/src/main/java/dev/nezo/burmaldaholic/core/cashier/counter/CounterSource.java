package dev.nezo.burmaldaholic.core.cashier.counter;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Who serves a counter session (EC-SPEC §3): a cashier block ({@link #pos}), a merchant NPC ({@link #entityId}) or the
 * op command {@code /casino store}. One record so it travels in one stream codec.
 */
public record CounterSource(Kind kind, BlockPos pos, int entityId) {
	public enum Kind {
		CASHIER, MERCHANT, COMMAND
	}

	public static CounterSource cashier(BlockPos pos) {
		return new CounterSource(Kind.CASHIER, pos.immutable(), -1);
	}

	public static CounterSource merchant(int entityId) {
		return new CounterSource(Kind.MERCHANT, BlockPos.ZERO, entityId);
	}

	public static CounterSource command() {
		return new CounterSource(Kind.COMMAND, BlockPos.ZERO, -1);
	}

	public boolean isCashier() {
		return kind == Kind.CASHIER;
	}

	public static final StreamCodec<ByteBuf, CounterSource> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT.map(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal), CounterSource::kind,
		BlockPos.STREAM_CODEC, CounterSource::pos,
		ByteBufCodecs.VAR_INT, CounterSource::entityId,
		CounterSource::new);
}
