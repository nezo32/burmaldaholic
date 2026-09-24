package dev.nezo.burmaldaholic.bots;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nezo.burmaldaholic.bots.logic.CommandArgs;
import dev.nezo.burmaldaholic.core.bots.BotLedger;
import dev.nezo.burmaldaholic.core.bots.BotLedgerData;
import dev.nezo.burmaldaholic.core.bots.TableBots;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * BOTS.md §8.7. Players (permission 0; they act on the table they sit at as host, or the table block they
 * look at within 5 blocks as keeper / owner; ops at any):
 * <pre>
 * /casino table settings | defaults
 * /casino table private on|off
 * /casino table invite &lt;player&gt;      /casino table uninvite &lt;name&gt;      /casino table letin &lt;player&gt;
 * /casino table bots &lt;humans|mixed|bots&gt; [count] [easy|normal|hard|mixed]
 * </pre>
 * Operators (level 2): {@code /casino bots list}, {@code /casino bots clear <table|all>},
 * {@code /casino bots heat <player> reset}.
 */
final class BotCommands {
	private BotCommands() {}

	static void register() {
		CasinoCommands.extendForPlayers(root -> root.then(table()));
		CasinoCommands.extend(root -> root.then(admin()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> table() {
		return Commands.literal("table")
			.then(Commands.literal("settings").executes(ctx -> open(ctx, false)))
			.then(Commands.literal("defaults").executes(ctx -> open(ctx, true)))
			.then(Commands.literal("private")
				.then(Commands.literal("on").executes(ctx -> privateTable(ctx, true)))
				.then(Commands.literal("off").executes(ctx -> privateTable(ctx, false))))
			.then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(BotCommands::invite)))
			.then(Commands.literal("uninvite").then(Commands.argument("name", StringArgumentType.word())
				.suggests((ctx, b) -> SharedSuggestionProvider.suggest(invitedNames(ctx.getSource()), b))
				.executes(BotCommands::uninvite)))
			.then(Commands.literal("letin").then(Commands.argument("player", EntityArgument.player()).executes(BotCommands::letIn)))
			.then(Commands.literal("bots").then(Commands.argument("policy", StringArgumentType.word())
				.suggests((ctx, b) -> SharedSuggestionProvider.suggest(CommandArgs.POLICIES, b))
				.executes(ctx -> bots(ctx, null, null))
				.then(Commands.argument("count", IntegerArgumentType.integer(0, 8))
					.executes(ctx -> bots(ctx, IntegerArgumentType.getInteger(ctx, "count"), null))
					.then(Commands.argument("difficulty", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(CommandArgs.DIFFICULTIES, b))
						.executes(ctx -> bots(ctx, IntegerArgumentType.getInteger(ctx, "count"), StringArgumentType.getString(ctx, "difficulty")))))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> admin() {
		return Commands.literal("bots")
			.then(Commands.literal("list").executes(BotCommands::list))
			.then(Commands.literal("clear")
				.then(Commands.literal("table").executes(ctx -> clear(ctx, false)))
				.then(Commands.literal("all").executes(ctx -> clear(ctx, true))))
			.then(Commands.literal("heat").then(Commands.argument("player", EntityArgument.player())
				.then(Commands.literal("reset").executes(BotCommands::heatReset))));
	}

	// ---- player commands ------------------------------------------------------------------------

	/** The acting player's table, or null after telling them why not. */
	private static BotTables.@Nullable Found target(CommandSourceStack src, ServerPlayer player) {
		if (!CasinoMode.isEnabled(player)) {
			src.sendFailure(Component.translatable("gui.burmaldaholic.error.casino_off"));
			return null;
		}
		Optional<BotTables.Found> f = BotTables.target(player);
		if (f.isEmpty()) {
			src.sendFailure(Component.translatable("gui.burmaldaholic.bots.error.no_table"));
			return null;
		}
		return f.get();
	}

	private static int result(CommandSourceStack src, @Nullable Component error, @Nullable Component success) {
		if (error != null) {
			src.sendFailure(error);
			return 0;
		}
		if (success != null) {
			src.sendSuccess(() -> success, false);
		}
		return 1;
	}

	private static int open(CommandContext<CommandSourceStack> ctx, boolean defaults) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		BotTables.Found f = target(ctx.getSource(), player);
		if (f == null) {
			return 0;
		}
		TableSettings.send(player, f, true, defaults, null);
		return 1;
	}

	private static int privateTable(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		BotTables.Found f = target(ctx.getSource(), player);
		if (f == null) {
			return 0;
		}
		return result(ctx.getSource(), TableSettings.setPrivate(player, f, on),
			Component.translatable(on ? "msg.burmaldaholic.bots.private_on" : "msg.burmaldaholic.bots.private_off"));
	}

	private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerPlayer who = EntityArgument.getPlayer(ctx, "player");
		BotTables.Found f = target(ctx.getSource(), player);
		if (f == null) {
			return 0;
		}
		TableSettings.remember(who);
		return result(ctx.getSource(), TableSettings.invite(player, f, who.getUUID()), null);
	}

	private static List<String> invitedNames(CommandSourceStack src) {
		List<String> out = new ArrayList<>();
		if (src.getPlayer() instanceof ServerPlayer p) {
			BotTables.target(p).ifPresent(f -> {
				f.bots().access().guests().forEach(id -> out.add(TableSettings.name(src.getServer(), id)));
				f.bots().access().sessionInvites().forEach(id -> out.add(TableSettings.name(src.getServer(), id)));
			});
		}
		return out;
	}

	private static int uninvite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "name");
		BotTables.Found f = target(ctx.getSource(), player);
		if (f == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		List<UUID> all = new ArrayList<>(f.bots().access().guests());
		all.addAll(f.bots().access().sessionInvites());
		for (UUID id : all) {
			if (TableSettings.name(server, id).equalsIgnoreCase(name)) {
				return result(ctx.getSource(), TableSettings.uninvite(player, f, id), null);
			}
		}
		ctx.getSource().sendFailure(Component.translatable("argument.player.unknown"));
		return 0;
	}

	private static int letIn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerPlayer who = EntityArgument.getPlayer(ctx, "player");
		BotTables.Found f = target(ctx.getSource(), player);
		if (f == null) {
			return 0;
		}
		TableSettings.remember(who);
		return result(ctx.getSource(), TableSettings.letIn(player, f, who.getUUID()), null);
	}

	private static int bots(CommandContext<CommandSourceStack> ctx, @Nullable Integer count, @Nullable String difficultyWord) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		SeatPolicy policy = CommandArgs.policy(StringArgumentType.getString(ctx, "policy"));
		BotDifficulty difficulty = difficultyWord == null ? null : CommandArgs.difficulty(difficultyWord);
		if (policy == null || (difficultyWord != null && difficulty == null)) {
			ctx.getSource().sendFailure(Component.translatable("gui.burmaldaholic.error.invalid_amount"));
			return 0;
		}
		BotTables.Found f = target(ctx.getSource(), player);
		if (f == null) {
			return 0;
		}
		BotSettings wanted = CommandArgs.apply(TableSettings.currentOrPending(f.bots()), policy, count, difficulty);
		Component error = TableSettings.save(player, f, wanted, false);
		boolean seated = f.seated(player.getUUID());
		return result(ctx.getSource(), error, seated ? null : Component.translatable("gui.burmaldaholic.bots.pending"));
	}

	// ---- admin ----------------------------------------------------------------------------------

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		int n = 0;
		for (BotTables.Found f : BotTables.known(List.copyOf(toList(src.getServer().getAllLevels())))) {
			for (TableBots.SeatedBot b : f.bots().bots()) {
				Component line = Component.translatable("gui.burmaldaholic.bots.admin.line", BotTexts.displayLevel(b.profile),
					Component.translatable("chat.coordinates", Texts.raw(Integer.toString(f.pos().getX())), Texts.raw(Integer.toString(f.pos().getY())),
						Texts.raw(Integer.toString(f.pos().getZ()))),
					Texts.raw(b.purse.kind().name().toLowerCase(java.util.Locale.ROOT)), Texts.chips(b.stack)); // literal-ok: purse id
				src.sendSuccess(() -> line, false);
				n++;
			}
		}
		if (n == 0) {
			src.sendSuccess(() -> Component.translatable("msg.burmaldaholic.bots.list_empty"), false);
		}
		return n;
	}

	private static <T> List<T> toList(Iterable<T> it) {
		List<T> out = new ArrayList<>();
		it.forEach(out::add);
		return out;
	}

	/**
	 * Bots leave at the next safe point (never mid-round, review wave 2 m1): the session switches to Humans only
	 * ({@link dev.nezo.burmaldaholic.core.bots.TableBots#clear}). {@code all} works from the console too.
	 */
	private static int clear(CommandContext<CommandSourceStack> ctx, boolean all) throws CommandSyntaxException {
		List<BotTables.Found> tables = new ArrayList<>();
		if (all) {
			tables.addAll(BotTables.known(toList(ctx.getSource().getServer().getAllLevels())));
		} else {
			ServerPlayer op = ctx.getSource().getPlayerOrException();
			BotTables.Found f = target(ctx.getSource(), op);
			if (f == null) {
				return 0;
			}
			tables.add(f);
		}
		java.util.Set<dev.nezo.burmaldaholic.core.bots.TableBots> done = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		int n = 0;
		for (BotTables.Found f : tables) {
			BotSettings cur = TableSettings.currentOrPending(f.bots());
			if (cur.policy() == SeatPolicy.HUMANS_ONLY && f.bots().bots().isEmpty()) {
				continue;
			}
			f.bots().clear();
			done.add(f.bots());
			f.blockEntity().setChanged();
			n++;
		}
		if (all) {
			for (dev.nezo.burmaldaholic.core.bots.TableBots t : dev.nezo.burmaldaholic.core.bots.Bots.tables()) {
				if (done.add(t)) {
					t.clear(); // tables with bots the module has not seen yet
					n++;
				}
			}
		}
		int cleared = n;
		ctx.getSource().sendSuccess(() -> Component.translatable("msg.burmaldaholic.bots.cleared", Texts.number(cleared)), true);
		return n;
	}

	private static int heatReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer who = EntityArgument.getPlayer(ctx, "player");
		MinecraftServer server = ctx.getSource().getServer();
		long net = BotLedger.netToday(server, who.getUUID());
		if (net != 0) {
			BotLedgerData.get(server).add(who.getUUID(), BotEconomyMath.mcDay(server.overworld().getGameTime()), -net);
		}
		BotHeat.WATCH.reset(who.getUUID());
		ctx.getSource().sendSuccess(() -> Component.translatable("msg.burmaldaholic.bots.heat_reset", Texts.raw(who.getName().getString()))
			.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}
}
