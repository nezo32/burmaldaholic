package dev.nezo.burmaldaholic.games.extras.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -&gt; client: state of an item-driven extras screen ({@code coin}, {@code dice}, {@code scratch},
 * {@code duel_invite}). {@code open} = open the screen if it is not showing (otherwise only update it);
 * {@code error} (optional, may be empty tag) carries nothing — errors travel as translated components in
 * {@code state.error} via {@link ExtrasErrorPayload}.
 */
public record ExtrasScreenPayload(String screen, boolean open, CompoundTag state) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<ExtrasScreenPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, ExtrasScreenPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(32), ExtrasScreenPayload::screen,
		ByteBufCodecs.BOOL, ExtrasScreenPayload::open,
		ByteBufCodecs.COMPOUND_TAG, ExtrasScreenPayload::state,
		ExtrasScreenPayload::new);

	@Override
	public Type<ExtrasScreenPayload> type() {
		return TYPE;
	}
}
