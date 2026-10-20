package dev.nezo.burmaldaholic.core.store.net;

import dev.nezo.burmaldaholic.core.cashier.counter.CounterSource;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: open the counter screen for a menu-less source (merchant NPC, {@code /casino store}). {@code tabs} =
 * tab ids in order ({@code store}, {@code comps}, …), {@code dimension} = the source's dimension code ({@code O / N / E / ?},
 * {@code CounterSessions#dimCode}). The cashier block opens through its table menu instead (tabs exchange, store, comps).
 */
public record CounterOpenPayload(CounterSource source, List<String> tabs, String dimension) implements CustomPacketPayload {
	public static CustomPacketPayload.Type<CounterOpenPayload> TYPE;
	public static final StreamCodec<RegistryFriendlyByteBuf, CounterOpenPayload> CODEC = StreamCodec.composite(
		CounterSource.STREAM_CODEC.cast(), CounterOpenPayload::source,
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(16)), CounterOpenPayload::tabs,
		ByteBufCodecs.STRING_UTF8, CounterOpenPayload::dimension,
		CounterOpenPayload::new);

	@Override
	public Type<CounterOpenPayload> type() {
		return TYPE;
	}
}
