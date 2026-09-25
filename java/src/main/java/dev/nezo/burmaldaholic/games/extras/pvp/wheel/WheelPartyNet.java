package dev.nezo.burmaldaholic.games.extras.pvp.wheel;

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
 * Wheel Party's own payloads (registered by the mode from {@code ExtrasPvpModes.register}). The party panel is a
 * plain screen next to the wheel's machine screen, so it has its own state channel; the server re-sends the
 * panel state while it is open and something changed.
 */
public final class WheelPartyNet {
	/** Server → client: {@code open} = open the panel if not showing; {@code message} = error / info line. */
	public record Panel(boolean open, CompoundTag state, Component message) implements CustomPacketPayload {
		public static CustomPacketPayload.Type<Panel> TYPE;
		public static final StreamCodec<RegistryFriendlyByteBuf, Panel> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Panel::open,
			ByteBufCodecs.COMPOUND_TAG, Panel::state,
			ComponentSerialization.TRUSTED_STREAM_CODEC, Panel::message,
			Panel::new);

		@Override
		public Type<Panel> type() {
			return TYPE;
		}
	}

	/**
	 * Client → server: {@code open {x,y,z}}, {@code close}, {@code host {cap, stake, policy, difficulty, size}},
	 * {@code join {stake}}, {@code top_up {extra}}, {@code spin}, {@code leave}, {@code taunt {line}},
	 * {@code rematch {id}}.
	 */
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

	private WheelPartyNet() {}

	static void register(BiConsumer<ServerPlayer, Action> handler) {
		Panel.TYPE = new CustomPacketPayload.Type<>(Burmaldaholic.id("pvp_wheel_panel"));
		Action.TYPE = new CustomPacketPayload.Type<>(Burmaldaholic.id("pvp_wheel_action"));
		PayloadTypeRegistry.clientboundPlay().register(Panel.TYPE, Panel.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> {
			if (CasinoMode.isEnabled(context.server())) {
				handler.accept(context.player(), payload);
			}
		});
	}

	static void send(ServerPlayer player, boolean open, CompoundTag state, Component message) {
		if (Panel.TYPE != null && ServerPlayNetworking.canSend(player, Panel.TYPE)) {
			ServerPlayNetworking.send(player, new Panel(open, state, message));
		}
	}
}
