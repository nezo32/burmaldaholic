package dev.nezo.burmaldaholic.core.ui.v2;

import java.util.Objects;

/**
 * State of the PrimaryButton (and of any action button) as a pure value (docs/architecture/ui-v2.md §5): what it says,
 * whether it can be pressed, and — when it cannot — WHY, as a translation key the status line shows. A disabled gate
 * without a reason cannot be constructed: "the main button always says why it is disabled" is enforced by the type.
 * Each game's pure {@code <Game>Flow.primary(view)} returns one; the client turns keys into components.
 *
 * @param action    the table action sent on press ({@code "deal"}, {@code "spin"}, {@code "sit"}, …), or "" (local)
 * @param labelKey  translation key of the label
 * @param labelArgs plain-number arguments of the label (e.g. Rebet 50)
 * @param enabled   pressable now
 * @param reasonKey why not (required when disabled), else null
 * @param reasonArgs arguments of the reason (e.g. the name of the player we wait for)
 */
public record Gate(String action, String labelKey, Object[] labelArgs, boolean enabled, String reasonKey, Object[] reasonArgs) {
	public Gate {
		Objects.requireNonNull(action);
		Objects.requireNonNull(labelKey);
		labelArgs = labelArgs == null ? new Object[0] : labelArgs.clone();
		reasonArgs = reasonArgs == null ? new Object[0] : reasonArgs.clone();
		if (!enabled && (reasonKey == null || reasonKey.isEmpty())) {
			throw new IllegalArgumentException("disabled gate '" + labelKey + "' without a reason");
		}
		if (enabled) reasonKey = null;
	}

	public static Gate on(String action, String labelKey, Object... args) {
		return new Gate(action, labelKey, args, true, null, null);
	}

	public static Gate off(String action, String labelKey, String reasonKey, Object... reasonArgs) {
		return new Gate(action, labelKey, null, false, reasonKey, reasonArgs);
	}
}
