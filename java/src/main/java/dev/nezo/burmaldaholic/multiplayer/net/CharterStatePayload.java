package dev.nezo.burmaldaholic.multiplayer.net;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: Casino Charter screen state (bankroll, tables, stats). {@code open} = open the screen;
 * otherwise only update it if it is showing. {@code message} = feedback line (error in red, info in green).
 */
public record CharterStatePayload(CompoundTag state, boolean open, Optional<Component> message, boolean error) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<CharterStatePayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, CharterStatePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.COMPOUND_TAG, CharterStatePayload::state,
		ByteBufCodecs.BOOL, CharterStatePayload::open,
		ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs::optional), CharterStatePayload::message,
		ByteBufCodecs.BOOL, CharterStatePayload::error,
		CharterStatePayload::new);

	@Override
	public Type<CharterStatePayload> type() {
		return TYPE;
	}
}
