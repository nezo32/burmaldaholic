package dev.nezo.burmaldaholic.pvp.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: a button in a PvP screen (lobby, match, result, taunts, mode screens via
 * {@code client.pvp.PvpScreens#action}). Untrusted: the server re-checks everything through the engine.
 *
 * <p>Actions: {@code start}, {@code fill_bots}, {@code leave}, {@code press}, {@code decide} (arg = decision id,
 * value = option), {@code taunt} (value = line 0…7), {@code rematch}, {@code accept}, {@code decline},
 * {@code withdraw}, {@code join} (value = stake), {@code top_up} (value = extra), {@code show} (re-send + open).
 */
public record PvpActionPayload(String action, String matchId, String arg, long value) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<PvpActionPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, PvpActionPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(32), PvpActionPayload::action,
		ByteBufCodecs.stringUtf8(32), PvpActionPayload::matchId,
		ByteBufCodecs.stringUtf8(64), PvpActionPayload::arg,
		ByteBufCodecs.VAR_LONG, PvpActionPayload::value,
		PvpActionPayload::new);

	@Override
	public Type<PvpActionPayload> type() {
		return TYPE;
	}
}
