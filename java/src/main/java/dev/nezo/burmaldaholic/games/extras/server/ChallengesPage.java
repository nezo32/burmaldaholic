package dev.nezo.burmaldaholic.games.extras.server;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.ChallengeBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Casino Menu "Challenges" tab (UI.md §2): pending Dice Duel challenges with Accept / Decline, the
 * outgoing one, "challenge a player" (players within range + amount) and a shortcut to the house duel.
 * Every action goes through {@link DiceGame#action} (same validation as the dice screen).
 */
public final class ChallengesPage implements CasinoMenu.Page {
	@Override
	public String id() {
		return "challenges";
	}

	@Override
	public int order() {
		return 50;
	}

	@Override
	public Component label() {
		return Component.translatable("gui.burmaldaholic.menu.challenges");
	}

	@Override
	public boolean visible(ServerPlayer player) {
		return CasinoConfig.extras().diceDuel.enabled;
	}

	@Override
	public void render(ServerPlayer player, CasinoMenu.PageBuilder out) {
		MinecraftServer server = player.level().getServer();
		out.button("house", Component.translatable("gui.burmaldaholic.extras.dice.vs_house"));
		if (!CasinoConfig.extras().diceDuel.pvpEnabled) {
			return;
		}
		for (ChallengeBook.Challenge c : DiceGame.incoming(player)) {
			String name = DiceGame.nameOf(server, c.from());
			out.button("accept:" + c.id(), Component.translatable("gui.burmaldaholic.extras.dice.accept_from", Texts.raw(name), Texts.chips(c.stake())));
			out.button("decline:" + c.id(), Component.translatable("gui.burmaldaholic.extras.dice.decline_from", Texts.raw(name)));
		}
		double max = CasinoConfig.extras().diceDuel.maxDistance;
		boolean any = false;
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != player && other.level() == player.level() && other.distanceToSqr(player) <= max * max && !other.isSpectator()) {
				out.amountButton("challenge:" + other.getUUID(), Component.translatable("gui.burmaldaholic.extras.dice.challenge_target", other.getDisplayName()),
					10);
				any = true;
			}
		}
		out.line(Component.translatable("gui.burmaldaholic.extras.dice.pending"), 0xFFD700);
		DiceGame.outgoing(player).ifPresent(c -> out.line(Component.translatable("msg.burmaldaholic.extras.dice.challenge_sent",
			Texts.raw(DiceGame.nameOf(server, c.to())), Texts.chips(c.stake())), 0xAAAAAA));
		if (!any) {
			out.line(Component.translatable("gui.burmaldaholic.extras.dice.no_targets"), 0xAAAAAA);
		}
		out.blank();
		out.line(Component.translatable("gui.burmaldaholic.extras.dice.rules"), 0xAAAAAA);
	}

	@Override
	public @Nullable Component action(ServerPlayer player, String action, long amount) {
		CompoundTag args = new CompoundTag();
		if (action.equals("house")) {
			DiceGame.open(player, null);
			return null;
		}
		int colon = action.indexOf(':');
		if (colon < 0) {
			return null;
		}
		String verb = action.substring(0, colon);
		String arg = action.substring(colon + 1);
		switch (verb) {
			case "accept", "decline" -> {
				try {
					args.putInt("id", Integer.parseInt(arg));
				} catch (NumberFormatException e) {
					return null;
				}
				DiceGame.action(player, verb, args);
			}
			case "challenge" -> {
				args.putString("target", arg);
				args.putLong("amount", amount);
				DiceGame.action(player, "challenge", args);
			}
			default -> {
			}
		}
		return null;
	}
}
