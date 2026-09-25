package dev.nezo.burmaldaholic.worldgen.net;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the generated casinos near the player (bounds + kind) for the client's attract mode (roofline
 * marquee lights, ambient motes; global.md §4.13), and which one the player just walked into ({@code arrived}, −1
 * none) for the arrival flourish. Sent when the nearby set changes or on arrival; public world data only.
 */
public record CasinoViewPayload(List<Casino> casinos, int arrived) implements CustomPacketPayload {
	public static final int MAX = 16;
	public static CustomPacketPayload.Type<CasinoViewPayload> TYPE;

	/** One casino: {@code CasinoKind} ordinal and block bounds (inclusive). */
	public record Casino(int kind, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		static final StreamCodec<RegistryFriendlyByteBuf, Casino> CODEC = StreamCodec.of((buf, c) -> {
			buf.writeVarInt(c.kind);
			buf.writeInt(c.minX);
			buf.writeInt(c.minY);
			buf.writeInt(c.minZ);
			buf.writeInt(c.maxX);
			buf.writeInt(c.maxY);
			buf.writeInt(c.maxZ);
		}, buf -> new Casino(buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, CasinoViewPayload> CODEC = StreamCodec.composite(
		Casino.CODEC.apply(ByteBufCodecs.list(MAX)), CasinoViewPayload::casinos,
		ByteBufCodecs.VAR_INT, CasinoViewPayload::arrived,
		CasinoViewPayload::new);

	public CasinoViewPayload {
		casinos = List.copyOf(casinos.size() > MAX ? casinos.subList(0, MAX) : casinos);
	}

	@Override
	public Type<CasinoViewPayload> type() {
		return TYPE;
	}
}
