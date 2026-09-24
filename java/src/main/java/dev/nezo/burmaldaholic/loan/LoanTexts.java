package dev.nezo.burmaldaholic.loan;

import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.LoanLook;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Loan text shared by the server pages and the Loan Shark screen (no server state; safe on both sides). */
public final class LoanTexts {
	private LoanTexts() {}

	/** "2 days 5 hours" / "5 hours" / "less than an hour" (RU plurals) for {@code ticks} before the deadline, in-game time. */
	public static MutableComponent dueIn(long ticks) {
		long[] dh = LoanLook.dueIn(ticks);
		if (dh[0] > 0 && dh[1] > 0) {
			return Component.translatable("gui.burmaldaholic.loan.time.days_hours", Texts.plural("unit.burmaldaholic.day", dh[0]),
				Texts.plural("unit.burmaldaholic.hour", dh[1]));
		}
		if (dh[0] > 0) return Texts.plural("unit.burmaldaholic.day", dh[0]);
		if (dh[1] > 0) return Texts.plural("unit.burmaldaholic.hour", dh[1]);
		return Component.translatable("gui.burmaldaholic.loan.time.under_hour");
	}
}
