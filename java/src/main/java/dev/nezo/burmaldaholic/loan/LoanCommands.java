package dev.nezo.burmaldaholic.loan;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.loan.entity.LoanSharkEntity;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Operator commands (§5.8.5), under {@code /casino} (level 2, added by core):
 *
 * <pre>
 * /casino debt &lt;player&gt; clear | set &lt;n&gt;                  (GAME_DESIGN.md spelling)
 * /casino loan info|clear|default|wave &lt;player&gt;
 * /casino loan set &lt;player&gt; &lt;owed&gt;      /casino loan standing &lt;player&gt; &lt;n&gt;
 * /casino loan shark [piglin]                           (spawn a Loan Shark at your feet)
 * </pre>
 */
final class LoanCommands {
	private LoanCommands() {}

	static void register() {
		CasinoCommands.extend(LoanCommands::build);
	}

	private static void build(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("debt").then(Commands.argument("player", EntityArgument.player())
			.then(Commands.literal("clear").executes(c -> set(c, 0)))
			.then(Commands.literal("set").then(Commands.argument("amount", LongArgumentType.longArg(0))
				.executes(c -> set(c, LongArgumentType.getLong(c, "amount")))))));
		root.then(Commands.literal("loan")
			.then(Commands.literal("info").then(Commands.argument("player", EntityArgument.player()).executes(LoanCommands::info)))
			.then(Commands.literal("clear").then(Commands.argument("player", EntityArgument.player()).executes(c -> set(c, 0))))
			.then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("amount", LongArgumentType.longArg(0)).executes(c -> set(c, LongArgumentType.getLong(c, "amount"))))))
			.then(Commands.literal("default").then(Commands.argument("player", EntityArgument.player()).executes(LoanCommands::forceDefault)))
			.then(Commands.literal("wave").then(Commands.argument("player", EntityArgument.player()).executes(LoanCommands::wave)))
			.then(Commands.literal("standing").then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("count", IntegerArgumentType.integer(0, 1000)).executes(LoanCommands::standing))))
			.then(Commands.literal("shark").executes(c -> shark(c, false))
				.then(Commands.literal("piglin").executes(c -> shark(c, true)))));
	}

	private static Component describe(MinecraftServer server, ServerPlayer p) {
		LoanRecord rec = LoanService.record(server, p.getUUID());
		return Component.translatable("gui.burmaldaholic.loan.admin.info", p.getName(), LoanShark.statusLine(rec, LoanService.now(server)),
			Texts.number(rec.goodStanding));
	}

	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = EntityArgument.getPlayer(c, "player");
		Component msg = describe(c.getSource().getServer(), p);
		c.getSource().sendSuccess(() -> msg, false);
		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> c, long amount) throws CommandSyntaxException {
		ServerPlayer p = EntityArgument.getPlayer(c, "player");
		MinecraftServer server = c.getSource().getServer();
		LoanService.adminSet(server, p.getUUID(), amount);
		Component msg = Component.translatable("gui.burmaldaholic.menu.admin.done", describe(server, p));
		c.getSource().sendSuccess(() -> msg, true);
		return 1;
	}

	private static int forceDefault(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = EntityArgument.getPlayer(c, "player");
		MinecraftServer server = c.getSource().getServer();
		LoanService.adminDefault(server, p.getUUID());
		Component msg = Component.translatable("gui.burmaldaholic.menu.admin.done", describe(server, p));
		c.getSource().sendSuccess(() -> msg, true);
		return 1;
	}

	private static int wave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = EntityArgument.getPlayer(c, "player");
		if (!LoanService.active(c.getSource().getServer()) || !LoanService.collectorsMode(c.getSource().getServer()) || !LoanSquads.forceWave(p)) {
			c.getSource().sendFailure(Component.translatable("gui.burmaldaholic.loan.admin.wave_failed"));
			return 0;
		}
		Component msg = Component.translatable("gui.burmaldaholic.loan.admin.wave_sent", p.getName());
		c.getSource().sendSuccess(() -> msg, true);
		return 1;
	}

	private static int standing(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = EntityArgument.getPlayer(c, "player");
		MinecraftServer server = c.getSource().getServer();
		LoanService.record(server, p.getUUID()).goodStanding = IntegerArgumentType.getInteger(c, "count");
		LoanData.get(server).setDirty();
		Component msg = Component.translatable("gui.burmaldaholic.menu.admin.done", describe(server, p));
		c.getSource().sendSuccess(() -> msg, true);
		return 1;
	}

	private static int shark(CommandContext<CommandSourceStack> c, boolean piglin) {
		ServerLevel level = c.getSource().getLevel();
		LoanSharkEntity shark = (piglin ? LoanContent.PIGLIN_MONEYLENDER : LoanContent.LOAN_SHARK).create(level, EntitySpawnReason.COMMAND);
		if (shark == null) {
			return 0;
		}
		var pos = c.getSource().getPosition();
		shark.snapTo(pos.x, pos.y, pos.z);
		level.addFreshEntity(shark);
		Component msg = Component.translatable("gui.burmaldaholic.menu.admin.done", shark.getName());
		c.getSource().sendSuccess(() -> msg, true);
		return 1;
	}
}
