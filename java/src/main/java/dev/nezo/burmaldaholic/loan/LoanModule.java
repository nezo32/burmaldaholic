package dev.nezo.burmaldaholic.loan;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;

/**
 * Loan shark NPC, debt and debt collectors.
 *
 * <p>Owner: the "loan" feature developer. You own ONLY: this package (main + client source sets),
 * {@code src/main/lang/loan/}, {@code src/main/sounds/loan/}, and assets/data files named
 * {@code loan_*} or inside {@code loan/} folders. See docs/architecture/java.md.
 */
public final class LoanModule implements CasinoModule {
	public static final String ID = "loan";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		// TODO(loan): register content here, e.g.
		// TABLE = ctx.tables().register("loan_table", LoanTableBlockEntity::new);
	}
}
