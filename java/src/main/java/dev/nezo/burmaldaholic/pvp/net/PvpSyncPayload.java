package dev.nezo.burmaldaholic.pvp.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: public state of the player's PvP match (the "PvpMatchSyncPayload" of pvp-bots.md §3.6).
 *
 * <p>{@code kind}: {@code lobby} (LOBBY), {@code match} (DRAWN: revealing), {@code result} (SETTLED), {@code clear}
 * (no match any more), {@code error} (json = {"error": component}). {@code open}: the client opens the matching
 * screen (lobby screen / the mode's {@code PvpScreens} screen or the generic match screen / result window);
 * otherwise it only updates an open PvP screen and the HUD ticker. {@code json}: see {@code PvpMatchView} — only
 * revealed steps, never the tape.
 */
public record PvpSyncPayload(String kind, boolean open, String json) implements CustomPacketPayload {
	public static final int MAX_JSON = 1 << 18;
	public static CustomPacketPayload.Type<PvpSyncPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, PvpSyncPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(16), PvpSyncPayload::kind,
		ByteBufCodecs.BOOL, PvpSyncPayload::open,
		ByteBufCodecs.stringUtf8(MAX_JSON), PvpSyncPayload::json,
		PvpSyncPayload::new);

	@Override
	public Type<PvpSyncPayload> type() {
		return TYPE;
	}
}
