package dev.nezo.burmaldaholic.games.uth;

import com.mojang.brigadier.tree.CommandNode;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * The parts of the bots module's UI (J-B2: table settings screen, BOTS.md §8) a hold'em table calls. The
 * bots module may install its own ({@link #set}); the default opens the table settings through the
 * bots module's {@code /casino table settings} command when that command exists, and does nothing
 * otherwise — the table works without the bots UI.
 */
public interface UthBotUi {
	/** "Table settings…" pressed on the table screen by {@code player} (seated, or looking at the table). */
	void openSettings(ServerPlayer player, UthTableBlockEntity table);

	/** Whether the table screen offers the "Table settings…" button. */
	default boolean available(ServerPlayer player) {
		return true;
	}

	UthBotUi COMMAND_FALLBACK = new UthBotUi() {
		@Override
		public void openSettings(ServerPlayer player, UthTableBlockEntity table) {
			if (available(player)) {
				player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "casino table settings");
			}
		}

		@Override
		public boolean available(ServerPlayer player) {
			try {
				CommandNode<CommandSourceStack> root = player.level().getServer().getCommands().getDispatcher().getRoot().getChild("casino");
				CommandNode<CommandSourceStack> table = root == null ? null : root.getChild("table");
				return table != null && table.getChild("settings") != null;
			} catch (RuntimeException e) {
				return false;
			}
		}
	};

	/** The installed UI (the command fallback until the bots module installs its own). */
	static UthBotUi get() {
		return Holder.ui;
	}

	static void set(UthBotUi ui) {
		Holder.ui = Objects.requireNonNull(ui);
	}

	final class Holder {
		private static UthBotUi ui = COMMAND_FALLBACK;

		private Holder() {}
	}
}
