package dev.nezo.burmaldaholic.core.network;

import dev.nezo.burmaldaholic.core.fx.ServerFx;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: one presentation event of the shared {@code fx} channel (global.md §3.1;
 * docs/architecture/animation.md §2.6). Pure decoration: the server has already applied every effect
 * (balance, items, mobs) before sending it, and a client without the mod gets vanilla titles instead
 * ({@code NetworkServerFx}). Budget: ≤ 1 per settlement per player, ≤ 4 per second per player (global §2.9).
 *
 * @param kind      what happened
 * @param tier      {@code WinTier} ordinal (WIN / BIG_WIN_NEARBY / BROADCAST), else 0
 * @param amount    total return (WIN), net (nearby / broadcast), or a kind-specific amount
 * @param stake     stake the multiples refer to (WIN)
 * @param game      game id; selects the client's registered tier words / table / stems ({@code core} default)
 * @param arg       kind-specific small int (WIN: jackpot sub-tier 0 none, 1 Mini … 4 Grand; TOAST: {@code ToastIcon}
 *                  ordinal; VIP_UP: tier; CHAOS: event)
 * @param flags     bit set: {@link #FLAG_MAX_WIN}
 * @param holdMs    WIN: the client holds the HUD balance delta for this long (global §4.1, F6)
 * @param seed      cosmetic seed (particle scatter; public values only)
 * @param pos       world position of a positional event (nearby FX), if any
 * @param actor     the player the event is about (broadcasts, nearby), if any
 * @param actorName that player's display name, if any
 * @param text      kind-specific text (TOAST: body; the title is {@code game} as a lang key), if any
 */
public record FxPayload(ServerFx.Kind kind, int tier, long amount, long stake, String game, int arg, int flags, int holdMs, int seed,
		Optional<Vec3> pos, Optional<UUID> actor, Optional<Component> actorName, Optional<Component> text) implements CustomPacketPayload {
	public static final int FLAG_MAX_WIN = 1;
	private static final int MAX_GAME = 64;

	public static CustomPacketPayload.Type<FxPayload> TYPE;

	private static final StreamCodec<ByteBuf, Vec3> VEC3 = new StreamCodec<>() {
		@Override
		public Vec3 decode(ByteBuf buf) {
			return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
		}

		@Override
		public void encode(ByteBuf buf, Vec3 v) {
			buf.writeDouble(v.x);
			buf.writeDouble(v.y);
			buf.writeDouble(v.z);
		}
	};
	private static final StreamCodec<ByteBuf, Optional<Vec3>> OPT_POS = ByteBufCodecs.optional(VEC3);
	private static final StreamCodec<ByteBuf, Optional<UUID>> OPT_UUID = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC);
	private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPT_TEXT = ComponentSerialization.OPTIONAL_STREAM_CODEC;

	public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC = new StreamCodec<>() {
		@Override
		public FxPayload decode(RegistryFriendlyByteBuf buf) {
			int k = ByteBufCodecs.VAR_INT.decode(buf);
			ServerFx.Kind[] kinds = ServerFx.Kind.values();
			ServerFx.Kind kind = k >= 0 && k < kinds.length ? kinds[k] : ServerFx.Kind.TOAST;
			return new FxPayload(kind, ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
				ByteBufCodecs.stringUtf8(MAX_GAME).decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.INT.decode(buf), OPT_POS.decode(buf), OPT_UUID.decode(buf), OPT_TEXT.decode(buf),
				OPT_TEXT.decode(buf));
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buf, FxPayload p) {
			ByteBufCodecs.VAR_INT.encode(buf, p.kind.ordinal());
			ByteBufCodecs.VAR_INT.encode(buf, p.tier);
			ByteBufCodecs.VAR_LONG.encode(buf, p.amount);
			ByteBufCodecs.VAR_LONG.encode(buf, p.stake);
			ByteBufCodecs.stringUtf8(MAX_GAME).encode(buf, p.game.length() > MAX_GAME ? p.game.substring(0, MAX_GAME) : p.game);
			ByteBufCodecs.VAR_INT.encode(buf, p.arg);
			ByteBufCodecs.VAR_INT.encode(buf, p.flags);
			ByteBufCodecs.VAR_INT.encode(buf, p.holdMs);
			ByteBufCodecs.INT.encode(buf, p.seed);
			OPT_POS.encode(buf, p.pos);
			OPT_UUID.encode(buf, p.actor);
			OPT_TEXT.encode(buf, p.actorName);
			OPT_TEXT.encode(buf, p.text);
		}
	};

	public FxPayload {
		game = game == null ? "core" : game;
		pos = pos == null ? Optional.empty() : pos;
		actor = actor == null ? Optional.empty() : actor;
		actorName = actorName == null ? Optional.empty() : actorName;
		text = text == null ? Optional.empty() : text;
	}

	/** A simple event without position / actor / text. */
	public static FxPayload simple(ServerFx.Kind kind, String game, int arg) {
		return new FxPayload(kind, 0, 0, 0, game, arg, 0, 0, 0, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	public boolean maxWin() {
		return (flags & FLAG_MAX_WIN) != 0;
	}

	@Override
	public Type<FxPayload> type() {
		return TYPE;
	}
}
