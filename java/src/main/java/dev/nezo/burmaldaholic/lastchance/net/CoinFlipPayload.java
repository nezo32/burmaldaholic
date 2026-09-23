package dev.nezo.burmaldaholic.lastchance.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: a Last Chance coin was flipped for the receiving player; the client plays the
 * coin-flip overlay. Purely cosmetic; the server already applied the outcome.
 */
public record CoinFlipPayload(boolean heads, boolean highStakes) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<CoinFlipPayload> TYPE;

	public static final StreamCodec<RegistryFriendlyByteBuf, CoinFlipPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, CoinFlipPayload::heads,
		ByteBufCodecs.BOOL, CoinFlipPayload::highStakes,
		CoinFlipPayload::new);

	@Override
	public Type<CoinFlipPayload> type() {
		return TYPE;
	}
}
