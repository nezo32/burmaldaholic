package dev.nezo.burmaldaholic.core.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -&gt; client: the Casino Menu's server pages ({@code core.menu.CasinoMenu}). {@code data} holds
 * {@code tabs} (id, label), {@code page} (current id), {@code lines} (text, color), {@code buttons}
 * (action, label, active, amount) and an optional {@code error}; components are encoded with the
 * registry ops. {@code open} asks the client to open the menu on that page.
 */
public record MenuPayload(boolean open, CompoundTag data) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<MenuPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, MenuPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, MenuPayload::open,
		ByteBufCodecs.TRUSTED_COMPOUND_TAG, MenuPayload::data,
		MenuPayload::new);

	@Override
	public Type<MenuPayload> type() {
		return TYPE;
	}
}
