package dev.nezo.burmaldaholic.core.command;

import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.nezo.burmaldaholic.core.config.ConfigIssue;
import dev.nezo.burmaldaholic.core.config.ConfigManager;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin commands (permission level 2), root {@code /casino} with alias {@code /burmaldaholic}:
 *
 * <pre>
 * /casino balance get|set|add|take &lt;player&gt; [amount]
 * /casino config get|reset &lt;key&gt;     /casino config set &lt;key&gt; &lt;value&gt;     /casino config reload
 * </pre>
 *
 * Feature modules add their own sub-commands with {@link #extend} (e.g. {@code /casino debt}).
 * All feedback is translatable.
 */
public final class CasinoCommands {
	private static final List<java.util.function.Consumer<LiteralArgumentBuilder<CommandSourceStack>>> EXTENSIONS = new java.util.concurrent.CopyOnWriteArrayList<>();

	private CasinoCommands() {}

	/** Adds sub-commands under {@code /casino} (call from your module's register). */
	public static void extend(java.util.function.Consumer<LiteralArgumentBuilder<CommandSourceStack>> extension) {
		EXTENSIONS.add(extension);
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static final SuggestionProvider<CommandSourceStack> CONFIG_KEYS = (ctx, builder) ->
		SharedSuggestionProvider.suggest(ConfigManager.get().keys(), builder);

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("casino")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("balance")
				.then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player())
					.executes(ctx -> balanceGet(ctx))))
				.then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("amount", LongArgumentType.longArg(0)).executes(ctx -> balanceChange(ctx, Op.SET)))))
				.then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("amount", LongArgumentType.longArg(0)).executes(ctx -> balanceChange(ctx, Op.ADD)))))
				.then(Commands.literal("take").then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("amount", LongArgumentType.longArg(0)).executes(ctx -> balanceChange(ctx, Op.TAKE))))))
			.then(Commands.literal("config")
				.then(Commands.literal("get").then(Commands.argument("key", StringArgumentType.string()).suggests(CONFIG_KEYS)
					.executes(CasinoCommands::configGet)))
				.then(Commands.literal("set").then(Commands.argument("key", StringArgumentType.string()).suggests(CONFIG_KEYS)
					.then(Commands.argument("value", StringArgumentType.greedyString()).executes(CasinoCommands::configSet))))
				.then(Commands.literal("reset").then(Commands.argument("key", StringArgumentType.string()).suggests(CONFIG_KEYS)
					.executes(CasinoCommands::configReset)))
				.then(Commands.literal("reload").executes(CasinoCommands::configReload)));
		EXTENSIONS.forEach(e -> e.accept(root));
		var node = dispatcher.register(root);
		dispatcher.register(Commands.literal("burmaldaholic").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).redirect(node));
	}

	private enum Op {
		SET, ADD, TAKE
	}

	private static int balanceGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		long balance = Economies.get().balance(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance)), false);
		return (int) Math.min(Integer.MAX_VALUE, balance);
	}

	private static int balanceChange(CommandContext<CommandSourceStack> ctx, Op op) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		long amount = LongArgumentType.getLong(ctx, "amount");
		Economy eco = Economies.get();
		Transaction tx = new Transaction("core", "admin_" + op.name().toLowerCase(java.util.Locale.ROOT), Transaction.Kind.ADMIN);
		switch (op) {
			case SET -> eco.setBalance(ctx.getSource().getServer(), player.getUUID(), amount, tx);
			case ADD -> eco.deposit(player, amount, tx);
			case TAKE -> {
				long take = Math.min(amount, eco.balance(player));
				eco.tryWithdraw(player, take, tx);
			}
		}
		long balance = eco.balance(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("gui.burmaldaholic.menu.admin.done",
			Component.translatable("gui.burmaldaholic.common.balance", Texts.number(balance))), true);
		return 1;
	}

	private static int configGet(CommandContext<CommandSourceStack> ctx) {
		String key = StringArgumentType.getString(ctx, "key");
		var value = ConfigManager.get().value(key);
		if (value.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("config.burmaldaholic.unknown_key", Texts.raw(key)));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> keyValue(key, value.get()), false);
		return 1;
	}

	private static int configSet(CommandContext<CommandSourceStack> ctx) {
		String key = StringArgumentType.getString(ctx, "key");
		String raw = StringArgumentType.getString(ctx, "value");
		return report(ctx.getSource(), key, ConfigManager.get().setWorldValue(key, raw));
	}

	private static int configReset(CommandContext<CommandSourceStack> ctx) {
		String key = StringArgumentType.getString(ctx, "key");
		return report(ctx.getSource(), key, ConfigManager.get().resetWorldValue(key));
	}

	private static int configReload(CommandContext<CommandSourceStack> ctx) {
		List<ConfigIssue> issues = ConfigManager.get().load();
		issues.forEach(i -> ctx.getSource().sendSystemMessage(issueMessage(i)));
		ctx.getSource().sendSuccess(() -> Component.translatable("config.burmaldaholic.saved"), true);
		return 1;
	}

	private static int report(CommandSourceStack source, String key, ConfigManager.SetResult result) {
		if (!result.ok()) {
			result.issues().forEach(i -> source.sendFailure(issueMessage(i)));
			return 0;
		}
		result.issues().forEach(i -> source.sendSystemMessage(issueMessage(i)));
		source.sendSuccess(() -> Component.translatable("config.burmaldaholic.saved"), true);
		source.sendSuccess(() -> keyValue(key, result.effective()), false);
		return 1;
	}

	/** Translated message for a config problem. */
	public static Component issueMessage(ConfigIssue issue) {
		return switch (issue.kind()) {
			case CLAMPED -> Component.translatable("config.burmaldaholic.clamped", Texts.raw(issue.key()), Texts.raw(issue.replacement()));
			case INVALID -> Component.translatable("config.burmaldaholic.invalid", Texts.raw(issue.key()));
			case UNKNOWN -> Component.translatable("config.burmaldaholic.unknown_key", Texts.raw(issue.key()));
		};
	}

	private static Component keyValue(String key, JsonElement value) {
		return Component.translatable("gui.burmaldaholic.menu.admin.done", Texts.raw(key + " = " + value));
	}
}
