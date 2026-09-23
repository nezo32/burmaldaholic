package dev.nezo.burmaldaholic.core.economy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * Pure-Java chip ledger (unit-tested): player balances and bankroll accounts. All-or-nothing
 * commits: legs are summed per account, every account must stay ≥ 0 (the house is infinite),
 * player balances are capped at {@code maxBalance} (the excess is lost — returned as {@code lostToCap}).
 */
public final class Ledger {
	private final Map<UUID, Long> balances = new HashMap<>();
	private final Map<String, Bankroll> bankrolls = new LinkedHashMap<>();

	public static final class Bankroll {
		final UUID owner;
		long balance;
		long reserved;

		Bankroll(UUID owner, long balance, long reserved) {
			this.owner = owner;
			this.balance = balance;
			this.reserved = reserved;
		}

		Economy.BankrollInfo info(String id) {
			return new Economy.BankrollInfo(id, owner, balance, reserved);
		}
	}

	public record Leg(AccountId account, long delta) {}

	/** Result of a commit. {@code applied} = final per-account delta actually applied. */
	public record Commit(boolean ok, AccountId failed, Map<AccountId, Long> applied, Map<AccountId, Long> before, long lostToCap) {}

	public long balance(UUID player) {
		return balances.getOrDefault(player, 0L);
	}

	public Map<UUID, Long> balances() {
		return balances;
	}

	public Map<String, Bankroll> bankrollMap() {
		return bankrolls;
	}

	public void setBalance(UUID player, long value, long maxBalance) {
		balances.put(player, Math.max(0, Math.min(maxBalance, value)));
	}

	public Optional<Economy.BankrollInfo> bankroll(String id) {
		Bankroll b = bankrolls.get(id);
		return b == null ? Optional.empty() : Optional.of(b.info(id));
	}

	public Economy.BankrollInfo openBankroll(String id, UUID owner) {
		return bankrolls.computeIfAbsent(id, k -> new Bankroll(owner, 0, 0)).info(id);
	}

	public void loadBankroll(String id, UUID owner, long balance, long reserved) {
		bankrolls.put(id, new Bankroll(owner, balance, reserved));
	}

	public long closeBankroll(String id) {
		Bankroll b = bankrolls.remove(id);
		return b == null ? 0 : b.balance;
	}

	public boolean reserve(String id, long amount) {
		Bankroll b = bankrolls.get(id);
		if (b == null || amount < 0 || b.balance - b.reserved < amount) {
			return false;
		}
		b.reserved += amount;
		return true;
	}

	public void release(String id, long amount) {
		Bankroll b = bankrolls.get(id);
		if (b != null) {
			b.reserved = Math.max(0, b.reserved - Math.max(0, amount));
		}
	}

	/** Sums legs per account (insertion order kept). */
	public static Map<AccountId, Long> net(List<Leg> legs) {
		Map<AccountId, Long> net = new LinkedHashMap<>();
		for (Leg leg : legs) {
			net.merge(leg.account(), leg.delta(), Math::addExact);
		}
		return net;
	}

	/** Returns the first account that cannot cover its net debit, or null. */
	public AccountId check(Map<AccountId, Long> net) {
		for (Map.Entry<AccountId, Long> e : net.entrySet()) {
			long delta = e.getValue();
			if (delta >= 0) {
				continue;
			}
			switch (e.getKey()) {
				case AccountId.House h -> {
				}
				case AccountId.Player p -> {
					if (balance(p.id()) + delta < 0) {
						return p;
					}
				}
				case AccountId.Bankroll b -> {
					Bankroll br = bankrolls.get(b.id());
					if (br == null || br.balance + delta < 0) {
						return b;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Commits the legs atomically. {@code creditFilter} may reduce positive player deltas
	 * (garnishment) — it runs only after the check passed, so a filtered commit never fails.
	 */
	public Commit commit(List<Leg> legs, long maxBalance, ToLongFunction<Map.Entry<AccountId, Long>> creditFilter) {
		for (Leg leg : legs) {
			if (leg.delta() == Long.MIN_VALUE) {
				throw new IllegalArgumentException("bad delta");
			}
		}
		Map<AccountId, Long> net = net(legs);
		AccountId failed = check(net);
		if (failed != null) {
			return new Commit(false, failed, Map.of(), Map.of(), 0);
		}
		Map<AccountId, Long> applied = new LinkedHashMap<>();
		Map<AccountId, Long> before = new LinkedHashMap<>();
		long lost = 0;
		for (Map.Entry<AccountId, Long> e : net.entrySet()) {
			long delta = e.getValue();
			switch (e.getKey()) {
				case AccountId.House h -> applied.put(h, delta);
				case AccountId.Player p -> {
					if (delta > 0 && creditFilter != null) {
						delta = Math.max(0, Math.min(delta, creditFilter.applyAsLong(e)));
					}
					long old = balance(p.id());
					long target = old + delta;
					if (target > maxBalance) {
						lost += target - Math.max(old, maxBalance);
						target = Math.max(old, maxBalance);
					}
					balances.put(p.id(), target);
					before.put(p, old);
					applied.put(p, target - old);
				}
				case AccountId.Bankroll b -> {
					Bankroll br = bankrolls.get(b.id());
					before.put(b, br.balance);
					br.balance += delta;
					applied.put(b, delta);
				}
			}
		}
		return new Commit(true, null, applied, before, lost);
	}
}
