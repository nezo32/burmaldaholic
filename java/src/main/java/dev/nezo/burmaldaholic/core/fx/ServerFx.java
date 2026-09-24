package dev.nezo.burmaldaholic.core.fx;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the presentation layer (global.md §3.1; docs/architecture/animation.md §2.6). Games call
 * this instead of sending titles / particles themselves. Core installs {@link NetworkServerFx} at init
 * ({@link CoreFx#register}): it sends the {@code burmaldaholic:fx} payload when
 * {@code ServerPlayNetworking.canSend(player, FxPayload.TYPE)} and falls back to vanilla titles otherwise.
 * {@link #VANILLA_FALLBACK} (no-op) is what runs before core init and in pure unit tests.
 *
 * <p>Call rules: celebrate at the reveal gate ({@code gateTick}), never before the outcome is persisted; one
 * {@link #celebrate} per settlement per player. BIG and above automatically notify players within 32 blocks
 * (global §4.7); EPIC+ server-wide chat/toasts come from {@code BigWinBroadcast} (core, on
 * {@code PLAY_RESOLVED}), so games do not broadcast big wins themselves.
 */
public interface ServerFx {
	/** Kinds of the {@code fx} payload (global.md §3.1). */
	enum Kind {
		WIN,
		BIG_WIN_NEARBY,
		BROADCAST,
		CHAOS,
		GOLDEN_HOUR_START,
		GOLDEN_HOUR_END,
		VIP_UP,
		COLLECTORS,
		CASHIER,
		TOAST
	}

	/**
	 * A settled result to celebrate. {@code game} selects the client's {@code TierWords} / {@code WinTierTable}
	 * registration ({@code core} default, {@code slots}, …); the tier is already computed by the server.
	 *
	 * @param tier         server tier
	 * @param ret          total return (chips)
	 * @param stake        stake the multiples refer to
	 * @param game         game id (tier words / table / stems registered client-side under this id)
	 * @param subTier      jackpot sub-tier (0 none, 1 Mini … 4 Grand)
	 * @param maxWin       the max-win cap was hit
	 * @param holdUntilMs  the client holds the HUD balance delta until this local-time offset (F6 / global §4.1)
	 */
	record Celebration(WinTier tier, long ret, long stake, String game, int subTier, boolean maxWin, int holdUntilMs) {}

	void celebrate(ServerPlayer player, Celebration c);

	void event(ServerPlayer player, Kind kind, String game, int arg);

	/**
	 * Players within 32 blocks of {@code pos} (except the winner) see a BIG+ win at a table / machine (global §4.7):
	 * chip fountain and a positional {@code win_big} at half volume. {@link #celebrate} calls this at the winner's
	 * position; call it yourself only for a celebration you present without {@code celebrate}.
	 */
	default void nearby(ServerPlayer winner, Vec3 pos, WinTier tier, long net, String game) {}

	/** Icon of a casino toast (global §4.10); the client maps it to a GUI sprite or an item. */
	enum ToastIcon {
		CHIP,
		JACKPOT,
		BIG_WIN,
		CONTRACT,
		CASHBACK,
		VIP,
		GOLDEN_HOUR
	}

	/** A casino toast for one player (global §4.10): {@code titleKey} is a lang key, {@code body} any component. */
	default void toast(ServerPlayer player, String titleKey, Component body, ToastIcon icon) {}

	/**
	 * Server-wide EPIC / JACKPOT notice for every other player (Java: casino toast for viewers with
	 * {@code anim.celebrations = all}; the chat line is sent separately). Rate limited to 1 per 10 s (≤ 3 queued).
	 */
	default void broadcast(ServerPlayer winner, WinTier tier, long net, String game) {}

	ServerFx VANILLA_FALLBACK = new ServerFx() {
		@Override
		public void celebrate(ServerPlayer player, Celebration c) {}

		@Override
		public void event(ServerPlayer player, Kind kind, String game, int arg) {}
	};

	/** Current implementation (set once by core at init; never {@code null}). */
	static ServerFx get() {
		return Holder.instance;
	}

	static void set(ServerFx fx) {
		Holder.instance = fx == null ? VANILLA_FALLBACK : fx;
	}

	/** Holder for the installed implementation. */
	final class Holder {
		private static volatile ServerFx instance = VANILLA_FALLBACK;

		private Holder() {}
	}
}
