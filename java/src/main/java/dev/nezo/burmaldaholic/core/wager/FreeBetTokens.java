package dev.nezo.burmaldaholic.core.wager;

import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Core seam for free-bet tokens (EC-SPEC §4.3). The comps module owns the token item and installs the
 * {@link Provider}; games only see this class. Server thread only.
 *
 * <p>v0.2.0 accepts tokens at house-banked blackjack (main bet), slots, coin flip, wheel and plinko. PvP, poker and
 * owned casinos never accept them; roulette, baccarat, craps and UTH come later.
 */
public final class FreeBetTokens {
	/** Game ids that accept a token in v0.2.0. */
	public static final Set<String> GAMES = Set.of("blackjack", "slots", "coin_flip", "wheel_of_fortune", "plinko");

	/** A token: face value in chips and its expiry (overworld game time). */
	public record Held(long value, long expires) {}

	public interface Provider {
		/** The valid (bound to this player, not expired) token in the main hand, or null. */
		@Nullable Held peek(ServerPlayer player);

		/** Removes one valid token from the main hand; null if there is none. */
		@Nullable Held take(ServerPlayer player);

		/** A fresh token stack bound to {@code owner}. */
		ItemStack stack(UUID owner, Held token);

		/** Gives a token back (push / refund): inventory when online, else a mailbox delivered on join. */
		void giveBack(MinecraftServer server, UUID player, Held token);
	}

	private static volatile @Nullable Provider provider;

	private FreeBetTokens() {}

	public static void setProvider(@Nullable Provider p) {
		provider = p;
	}

	public static boolean accepts(String gameId) {
		return gameId != null && GAMES.contains(gameId);
	}

	/** The usable token for {@code gameId}, or null (game not supported / no comps module / no token in hand). */
	public static @Nullable Held peek(ServerPlayer player, String gameId) {
		Provider p = provider;
		return p == null || !accepts(gameId) ? null : p.peek(player);
	}

	public static @Nullable Held take(ServerPlayer player, String gameId) {
		Provider p = provider;
		return p == null || !accepts(gameId) ? null : p.take(player);
	}

	public static ItemStack stack(UUID owner, Held token) {
		Provider p = provider;
		return p == null ? ItemStack.EMPTY : p.stack(owner, token);
	}

	public static void giveBack(MinecraftServer server, UUID player, Held token) {
		Provider p = provider;
		if (p != null) {
			p.giveBack(server, player, token);
		}
	}
}
