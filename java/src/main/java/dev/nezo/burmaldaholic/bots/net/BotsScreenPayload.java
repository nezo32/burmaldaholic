package dev.nezo.burmaldaholic.bots.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -&gt; client: a bots-module screen. {@code screen} = {@code settings} (Table settings, BOTS.md §8.2) or
 * {@code invite_confirm} (Casino Card on a player, §8.4). {@code open} = open it if it is not showing
 * (otherwise only update the open one). {@code state} is display data only; the server decides.
 */
public record BotsScreenPayload(String screen, boolean open, CompoundTag state) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<BotsScreenPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, BotsScreenPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(32), BotsScreenPayload::screen,
		ByteBufCodecs.BOOL, BotsScreenPayload::open,
		ByteBufCodecs.COMPOUND_TAG, BotsScreenPayload::state,
		BotsScreenPayload::new);

	@Override
	public Type<BotsScreenPayload> type() {
		return TYPE;
	}
}
