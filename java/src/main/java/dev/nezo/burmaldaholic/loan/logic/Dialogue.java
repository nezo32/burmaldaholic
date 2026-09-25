package dev.nezo.burmaldaholic.loan.logic;

import java.util.Map;
import java.util.function.IntUnaryOperator;

/** NPC line variants (STRINGS.md, LOCALIZATION.md §1.2): base key → number of variants {@code .1 … .n}. PURE. */
public final class Dialogue {
	public static final String GREETING = "dialog.burmaldaholic.loan.greeting";
	public static final String GIVEN = "dialog.burmaldaholic.loan.given";
	public static final String REPAID = "dialog.burmaldaholic.loan.repaid";
	public static final String OVERDUE = "dialog.burmaldaholic.loan.overdue";
	public static final String REFUSE_VIP = "dialog.burmaldaholic.loan.refuse_vip";
	public static final String PIGLIN_GREETING = "dialog.burmaldaholic.loan.piglin_greeting";
	public static final String DEMAND = "dialog.burmaldaholic.collector.demand";
	public static final String HOSTILE = "dialog.burmaldaholic.collector.hostile";
	public static final String PAID = "dialog.burmaldaholic.collector.paid";
	public static final String PARTIAL = "dialog.burmaldaholic.collector.partial";
	public static final String REPOSSESS = "dialog.burmaldaholic.collector.repossess";
	public static final String AUDIT = "dialog.burmaldaholic.accountant.audit";
	public static final String ENFORCER = "dialog.burmaldaholic.enforcer.line";

	public static final Map<String, Integer> VARIANTS = Map.ofEntries(
		Map.entry(GREETING, 5), Map.entry(GIVEN, 4), Map.entry(REPAID, 4), Map.entry(OVERDUE, 5), Map.entry(REFUSE_VIP, 3),
		Map.entry(PIGLIN_GREETING, 2), Map.entry(DEMAND, 5), Map.entry(HOSTILE, 5), Map.entry(PAID, 4), Map.entry(PARTIAL, 3),
		Map.entry(REPOSSESS, 3), Map.entry(AUDIT, 3), Map.entry(ENFORCER, 3));

	private Dialogue() {}

	/** Random variant key, e.g. {@code dialog.burmaldaholic.loan.greeting.3}. {@code rng.applyAsInt(n)} ∈ [0, n). */
	public static String pick(String base, IntUnaryOperator rng) {
		int n = VARIANTS.getOrDefault(base, 1);
		int i = Math.floorMod(rng.applyAsInt(n), n);
		return base + "." + (i + 1);
	}
}
