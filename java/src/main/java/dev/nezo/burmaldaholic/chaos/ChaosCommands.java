package dev.nezo.burmaldaholic.chaos;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.nezo.burmaldaholic.chaos.logic.ChaosEvent;
import dev.nezo.burmaldaholic.chaos.logic.TriggerResult;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin sub-commands (permission of {@code /casino}, level 2):
 *
 * <pre>
 * /casino chaos trigger &lt;event&gt; [targets]   run an event now (ignores the per-player cooldown, not the safety rules)
 * /casino chaos golden_hour start|stop       start (respects its cooldown) / end the current Golden Hour
 * </pre>
 */
final class ChaosCommands {
	private ChaosCommands() {}

	static void register() {
		CasinoCommands.extend(root -> root.then(Commands.literal("chaos")
			.then(Commands.literal("trigger")
				.then(Commands.argument("event", StringArgumentType.word())
					.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(ChaosEvent.values()).map(ChaosEvent::id), b))
					.executes(ctx -> trigger(ctx, List.of(ctx.getSource().getPlayerOrException())))
					.then(Commands.argument("targets", EntityArgument.players())
						.executes(ctx -> trigger(ctx, EntityArgument.getPlayers(ctx, "targets"))))))
			.then(Commands.literal("golden_hour")
				.then(Commands.literal("start").executes(ChaosCommands::goldenHourStart))
				.then(Commands.literal("stop").executes(ChaosCommands::goldenHourStop)))));
	}

	private static Component resultText(TriggerResult r) {
		return Component.translatable("msg.burmaldaholic.chaos.admin.result." + r.id());
	}

	private static int trigger(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "event");
		Optional<ChaosEvent> event = ChaosEvent.byId(id);
		if (event.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("msg.burmaldaholic.chaos.admin.unknown_event", dev.nezo.burmaldaholic.core.text.Texts.raw(id)));
			return 0;
		}
		int started = 0;
		for (ServerPlayer p : targets) {
			TriggerResult r = ChaosEngine.trigger(p, event.get(), "admin", true);
			if (r == TriggerResult.STARTED) {
				started++;
			}
			Component line = Component.translatable("msg.burmaldaholic.chaos.admin.result_for", p.getDisplayName(), resultText(r));
			ctx.getSource().sendSuccess(() -> line, true);
			if (event.get() == ChaosEvent.GOLDEN_HOUR) {
				break; // server-wide: once is enough
			}
		}
		return started;
	}

	private static int goldenHourStart(CommandContext<CommandSourceStack> ctx) {
		TriggerResult r = GoldenHour.start(ctx.getSource().getServer(), null, "admin");
		ctx.getSource().sendSuccess(() -> resultText(r), true);
		return r == TriggerResult.STARTED ? 1 : 0;
	}

	private static int goldenHourStop(CommandContext<CommandSourceStack> ctx) {
		if (!GoldenHour.stop(ctx.getSource().getServer())) {
			ctx.getSource().sendFailure(Component.translatable("msg.burmaldaholic.chaos.admin.golden_hour_inactive"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("msg.burmaldaholic.chaos.admin.golden_hour_stopped"), true);
		return 1;
	}
}
