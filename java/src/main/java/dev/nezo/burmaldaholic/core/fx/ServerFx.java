package dev.nezo.burmaldaholic.core.fx;

import dev.nezo.burmaldaholic.core.anim.WinTier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the presentation layer (global.md §3.1; docs/architecture/animation.md §2.6). Games call
 * this instead of sending titles / particles themselves. SKELETON: the default is {@link #VANILLA_FALLBACK}
 * (does nothing new — today's per-game titles keep running); lane J-L1 installs the real implementation that
 * sends the {@code burmaldaholic:fx} payload when {@code ServerPlayNetworking.canSend(player, FxPayload.TYPE)}
 * and falls back to vanilla titles otherwise.
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
