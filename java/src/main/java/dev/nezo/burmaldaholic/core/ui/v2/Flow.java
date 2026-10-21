package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.List;
import java.util.Optional;

/**
 * A game's pure presentation flow (docs/architecture/ui-v2.md §5.1): from the game's view record {@code V} (built from
 * the state tag) to the gates of the primary button, the action buttons and the bet tools, plus the status hint.
 * Implementations live in {@code games/<g>/present/<G>Flow.java} and are exhaustively unit-tested with {@link FlowCheck}.
 */
public interface Flow<V> {
	/** The PrimaryButton (always present; disabled ⇒ the reason shows in the status line). */
	Gate primary(V v);

	/** The ActionRow, left to right (≤ 5; empty = the Dock shows the BetBar instead). */
	default List<Gate> actions(V v) {
		return List.of();
	}

	/** The bet model of the view, or null for a game without a BetBar (e.g. poker in a hand). */
	default BetView bet(V v) {
		return null;
	}

	/** A bet tool ({@link BetGates#TOOLS}); default: the shared rules over {@link #bet}. */
	default Gate tool(String id, V v) {
		BetView b = bet(v);
		return b == null ? Gate.off(id, toolLabel(id), V2Keys.REASON_ROUND_IN_PROGRESS) : BetGates.tool(id, b);
	}

	/** A chip of the tray; default: the shared rules over {@link #bet}. */
	default Gate chip(long denom, V v) {
		BetView b = bet(v);
		return b == null ? Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_ROUND_IN_PROGRESS) : BetGates.chip(denom, b);
	}

	/** Translation key of the status hint (line 2 of the status) when nothing more urgent is shown. */
	default Optional<String> hintKey(V v) {
		return Optional.empty();
	}

	/** Arguments of {@link #hintKey} (plain numbers / names). */
	default Object[] hintArgs(V v) {
		return new Object[0];
	}

	private static String toolLabel(String id) {
		return switch (id) {
			case BetGates.UNDO -> V2Keys.TOOL_UNDO;
			case BetGates.CLEAR -> V2Keys.TOOL_CLEAR;
			case BetGates.REBET -> V2Keys.TOOL_REBET;
			default -> V2Keys.TOOL_DOUBLE;
		};
	}
}
