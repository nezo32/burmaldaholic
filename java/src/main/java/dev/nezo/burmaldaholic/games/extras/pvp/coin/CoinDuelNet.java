package dev.nezo.burmaldaholic.games.extras.pvp.coin;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Coin Flip Duel's own payloads (the mode registers them itself, from {@code ExtrasPvpModes.register}, so the
 * extras module entry file stays untouched). Same rules as core's {@code Payloads}: the server handler runs
 * only while casino mode is on and treats every field as untrusted.
 */
public final class CoinDuelNet {
	/**
	 * Server → client: the set-up panel ({@code screen = "setup"}) or a message for the open coin screens
	 * ({@code screen = "message"}; {@code state.close} closes the set-up panel).
	 */
	public record Screen(String screen, CompoundTag state, Component message) implements CustomPacketPayload {
		public static CustomPacketPayload.Type<Screen> TYPE;
		public static final StreamCodec<RegistryFriendlyByteBuf, Screen> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), Screen::screen,
			ByteBufCodecs.COMPOUND_TAG, Screen::state,
			ComponentSerialization.TRUSTED_STREAM_CODEC, Screen::message,
			Screen::new);

		@Override
		public Type<Screen> type() {
			return TYPE;
		}
	}

	/** Client → server: {@code challenge}, {@code decide}, {@code rematch}, {@code taunt}, {@code withdraw}. */
	public record Action(String action, CompoundTag args) implements CustomPacketPayload {
		public static CustomPacketPayload.Type<Action> TYPE;
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), Action::action,
			ByteBufCodecs.COMPOUND_TAG, Action::args,
			Action::new);

		@Override
		public Type<Action> type() {
			return TYPE;
		}
	}

	private CoinDuelNet() {}

	static void register(BiConsumer<ServerPlayer, Action> handler) {
		Screen.TYPE = new CustomPacketPayload.Type<>(Burmaldaholic.id("pvp_coin_screen"));
		Action.TYPE = new CustomPacketPayload.Type<>(Burmaldaholic.id("pvp_coin_action"));
		PayloadTypeRegistry.clientboundPlay().register(Screen.TYPE, Screen.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> {
			if (CasinoMode.isEnabled(context.server())) {
				handler.accept(context.player(), payload);
			}
		});
	}

	static void send(ServerPlayer player, String screen, CompoundTag state, Component message) {
		if (Screen.TYPE != null && ServerPlayNetworking.canSend(player, Screen.TYPE)) {
			ServerPlayNetworking.send(player, new Screen(screen, state, message));
		}
	}
}
