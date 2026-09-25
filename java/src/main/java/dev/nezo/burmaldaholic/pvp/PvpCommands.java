package dev.nezo.burmaldaholic.pvp;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpMatch;
import dev.nezo.burmaldaholic.core.pvp.PvpModes;
import dev.nezo.burmaldaholic.core.pvp.PvpService;
import dev.nezo.burmaldaholic.core.pvp.logic.HeadToHead;
import dev.nezo.burmaldaholic.core.pvp.logic.MatchState;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Result;
import dev.nezo.burmaldaholic.pvp.logic.Taunts;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /casino pvp …} (PVP.md §3.13). Players (permission 0): {@code challenge <player> <game> <amount> [heads|tails]},
 * {@code accept [id]}, {@code decline [id]}, {@code leave}, {@code record [player]}, {@code taunt <line>}.
 * Operators (level 2): {@code list}, {@code cancel <matchId>} (LOBBY → refund; DRAWN → settle now).
 */
final class PvpCommands {
	private PvpCommands() {}

	private static final SuggestionProvider<CommandSourceStack> MODES = (ctx, b) ->
		SharedSuggestionProvider.suggest(PvpModes.all().stream().map(PvpMode::id).toList(), b);
	private static final SuggestionProvider<CommandSourceStack> TAUNTS = (ctx, b) -> SharedSuggestionProvider.suggest(Taunts.IDS, b);
	private static final SuggestionProvider<CommandSourceStack> MATCHES = (ctx, b) ->
		SharedSuggestionProvider.suggest(Pvp.service().all().stream().map(m -> m.id).toList(), b);
	private static final SuggestionProvider<CommandSourceStack> MY_INVITES = (ctx, b) -> {
		ServerPlayer p = ctx.getSource().getPlayer();
		return SharedSuggestionProvider.suggest(p == null ? List.of() : Pvp.service().invitesFor(p.getUUID()).stream().map(m -> m.id).toList(), b);
	};

	static void register() {
		CasinoCommands.extendForPlayers(PvpCommands::build);
	}

	static void build(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("pvp")
			.then(Commands.literal("challenge").then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("game", StringArgumentType.word()).suggests(MODES)
					.then(Commands.argument("amount", LongArgumentType.longArg(1))
						.executes(c -> challenge(c, true))
						.then(Commands.literal("heads").executes(c -> challenge(c, true)))
						.then(Commands.literal("tails").executes(c -> challenge(c, false)))))))
			.then(Commands.literal("accept").executes(c -> answer(c, true, null))
				.then(Commands.argument("id", StringArgumentType.word()).suggests(MY_INVITES)
					.executes(c -> answer(c, true, StringArgumentType.getString(c, "id")))))
			.then(Commands.literal("decline").executes(c -> answer(c, false, null))
				.then(Commands.argument("id", StringArgumentType.word()).suggests(MY_INVITES)
					.executes(c -> answer(c, false, StringArgumentType.getString(c, "id")))))
			.then(Commands.literal("leave").executes(PvpCommands::leave))
			.then(Commands.literal("join").then(Commands.argument("id", StringArgumentType.word()).executes(PvpCommands::join)))
			.then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(PvpCommands::invite)))
			.then(Commands.literal("record").executes(c -> record(c, null))
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> record(c, EntityArgument.getPlayer(c, "player")))))
			.then(Commands.literal("taunt").then(Commands.argument("line", StringArgumentType.word()).suggests(TAUNTS)
				.executes(PvpCommands::taunt)))
			.then(Commands.literal("list").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(PvpCommands::list))
			.then(Commands.literal("cancel").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("match", StringArgumentType.word()).suggests(MATCHES).executes(PvpCommands::cancel))));
	}

	/** Casino mode check shared by the player commands; returns the player or null after reporting the error. */
	private static @Nullable ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		if (!CasinoMode.isEnabled(c.getSource().getServer())) {
			c.getSource().sendFailure(Component.translatable("gui.burmaldaholic.error.casino_off"));
			return null;
		}
		return p;
	}

	private static int result(CommandContext<CommandSourceStack> c, Result<?> r) {
		if (r.isOk()) {
			return 1;
		}
		c.getSource().sendFailure(r.error());
		return 0;
	}

	/** {@code /casino pvp join <id>} (the [Join] of an invite-only lobby invite; equal-stake modes use the entry). */
	private static int join(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		if (p == null) {
			return 0;
		}
		String id = StringArgumentType.getString(c, "id");
		java.util.Optional<dev.nezo.burmaldaholic.core.pvp.PvpMatch> m = Pvp.service().get(id);
		long stake = m.map(PvpMatchView::entry).orElse(0L);
		Result<dev.nezo.burmaldaholic.core.pvp.PvpMatch> r = Pvp.service().join(p, id, stake);
		if (r.isOk() && r.value() != null) {
			PvpUi.push(p, r.value(), true);
		}
		return result(c, r);
	}

	/** {@code /casino pvp invite <player>}: the host of an invite-only lobby invites a player (BOTS.md §2.5). */
	private static int invite(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		if (p == null) {
			return 0;
		}
		ServerPlayer guest = EntityArgument.getPlayer(c, "player");
		return result(c, Pvp.service().inviteToLobby(p, guest.getUUID()));
	}

	private static int challenge(CommandContext<CommandSourceStack> c, boolean heads) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		if (p == null) {
			return 0;
		}
		ServerPlayer target = EntityArgument.getPlayer(c, "player");
		String game = StringArgumentType.getString(c, "game").toLowerCase(Locale.ROOT);
		if (PvpModes.get(game).isEmpty()) {
			c.getSource().sendFailure(Component.translatable("msg.burmaldaholic.pvp.command.unknown_game", Texts.raw(game)));
			return 0;
		}
		long amount = LongArgumentType.getLong(c, "amount");
		return result(c, Pvp.service().challenge(p, game, PvpHubPage.params(game, heads, amount), amount,
			new PvpService.Opponent.PlayerTarget(target.getUUID())));
	}

	private static int answer(CommandContext<CommandSourceStack> c, boolean accept, @Nullable String id) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		if (p == null) {
			return 0;
		}
		List<PvpMatch> invites = Pvp.service().invitesFor(p.getUUID());
		Optional<PvpMatch> invite = id == null ? invites.stream().reduce((a, b) -> b)
			: invites.stream().filter(m -> m.id.equals(id)).findFirst();
		if (invite.isEmpty()) {
			c.getSource().sendFailure(Component.translatable("msg.burmaldaholic.pvp.command.no_invite"));
			return 0;
		}
		if (!accept) {
			Pvp.service().decline(p, invite.get().id);
			return 1;
		}
		Result<PvpMatch> r = Pvp.service().accept(p, invite.get().id);
		if (r.isOk() && r.value() != null) {
			PvpUi.push(p, r.value(), true);
		}
		return result(c, r);
	}

	private static int leave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		if (p == null) {
			return 0;
		}
		Optional<PvpMatch> m = Pvp.service().matchOf(p.getUUID());
		if (m.isEmpty()) {
			c.getSource().sendFailure(Component.translatable("msg.burmaldaholic.pvp.command.no_match"));
			return 0;
		}
		if (m.get().state() == MatchState.INVITED) {
			Pvp.service().withdraw(p, m.get().id);
		} else {
			Pvp.service().leave(p);
		}
		return 1;
	}

	private static int record(CommandContext<CommandSourceStack> c, @Nullable ServerPlayer other) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		PvpService pvp = Pvp.service();
		if (other == null || other == p) {
			PvpService.Stats s = pvp.stats(p.getUUID());
			Component line = s.wins() + s.losses() == 0 ? Component.translatable("gui.burmaldaholic.pvp.hub.no_record")
				: Component.translatable("gui.burmaldaholic.pvp.hub.record", Texts.number(s.wins()), Texts.number(s.losses()), PvpHubPage.signed(s.net()));
			c.getSource().sendSuccess(() -> line, false);
			if (s.nemesis() != null) {
				HeadToHead r = pvp.record(p.getUUID(), s.nemesis());
				Component nem = Component.translatable("gui.burmaldaholic.pvp.hub.nemesis", Texts.raw(PvpHubPage.nameOf(c.getSource().getServer(), s.nemesis())),
					Texts.number(r.wins()), Texts.number(r.losses()));
				c.getSource().sendSuccess(() -> nem, false);
			}
			return 1;
		}
		HeadToHead r = pvp.record(p.getUUID(), other.getUUID());
		Component line = r.wins() + r.losses() == 0 ? Component.translatable("gui.burmaldaholic.pvp.invite.record_none", other.getDisplayName())
			: Component.translatable("gui.burmaldaholic.pvp.rivals.row", other.getDisplayName(), Texts.number(r.wins()), Texts.number(r.losses()),
				PvpHubPage.signed(r.net()));
		c.getSource().sendSuccess(() -> line, false);
		return 1;
	}

	private static int taunt(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = player(c);
		if (p == null) {
			return 0;
		}
		int line = Taunts.parse(StringArgumentType.getString(c, "line"));
		if (line < 0) {
			c.getSource().sendFailure(Component.translatable("msg.burmaldaholic.pvp.command.unknown_taunt", Texts.raw(String.join(", ", Taunts.IDS))));
			return 0;
		}
		return result(c, Pvp.service().taunt(p, line));
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		List<PvpMatch> all = Pvp.service().all();
		if (all.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("gui.burmaldaholic.pvp.admin.none"), false);
		}
		for (PvpMatch m : all) {
			c.getSource().sendSuccess(() -> PvpAdminPage.row(m), false);
		}
		Component warning = PvpAdminPage.rakeWarning();
		if (warning != null) {
			c.getSource().sendSuccess(() -> warning, false);
		}
		return all.size();
	}

	private static int cancel(CommandContext<CommandSourceStack> c) {
		String id = StringArgumentType.getString(c, "match");
		if (Pvp.service().get(id).isEmpty()) {
			c.getSource().sendFailure(Component.translatable("msg.burmaldaholic.pvp.command.unknown_match", Texts.raw(id)));
			return 0;
		}
		Pvp.service().cancel(id);
		c.getSource().sendSuccess(() -> Component.translatable("msg.burmaldaholic.pvp.command.cancelled", Texts.raw(id)), true);
		return 1;
	}
}
