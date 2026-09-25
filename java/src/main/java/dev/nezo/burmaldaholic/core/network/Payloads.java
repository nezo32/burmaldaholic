package dev.nezo.burmaldaholic.core.network;

import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Module-scoped payload registration.
 *
 * <pre>
 * public record SpinC2S(BlockPos pos, long bet) implements CustomPacketPayload {
 *     public static CustomPacketPayload.Type&lt;SpinC2S&gt; TYPE;          // assigned in register()
 *     public static final StreamCodec&lt;RegistryFriendlyByteBuf, SpinC2S&gt; CODEC = StreamCodec.composite(...);
 *     public Type&lt;SpinC2S&gt; type() { return TYPE; }
 * }
 * // in SlotsModule.register(ctx):
 * SpinC2S.TYPE = ctx.payloads().serverbound("slots_spin", SpinC2S.CODEC, (p, c) -&gt; ...);
 * </pre>
 *
 * Most games do NOT need custom payloads: use the generic table payloads
 * ({@link TableActionPayload} / {@link TableSyncPayload}) via {@code CasinoTableBlockEntity}.
 */
public final class Payloads {
	private final ModuleContext ctx;

	public Payloads(ModuleContext ctx) {
		this.ctx = ctx;
	}

	/**
	 * Client -> server. The handler runs on the server thread and is only invoked when casino
	 * mode is enabled. ALWAYS validate everything in the payload (never trust the client).
	 */
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> serverbound(String name,
			StreamCodec<RegistryFriendlyByteBuf, T> codec, ServerPlayNetworking.PlayPayloadHandler<T> handler) {
		CustomPacketPayload.Type<T> type = new CustomPacketPayload.Type<>(ctx.id(name));
		PayloadTypeRegistry.serverboundPlay().register(type, codec);
		ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
			if (CasinoMode.isEnabled(context.server())) {
				handler.receive(payload, context);
			}
		});
		return type;
	}

	/** Server -> client. Register the receiver in your client module with ClientPlayNetworking. */
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> clientbound(String name,
			StreamCodec<RegistryFriendlyByteBuf, T> codec) {
		CustomPacketPayload.Type<T> type = new CustomPacketPayload.Type<>(ctx.id(name));
		PayloadTypeRegistry.clientboundPlay().register(type, codec);
		return type;
	}
}
