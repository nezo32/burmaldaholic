package dev.nezo.burmaldaholic.core.economy;

import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Default {@link Economy}: a {@link Ledger} stored in {@link CasinoWorldData}. */
public final class LedgerEconomy implements Economy {
	private final List<CreditHook> hooks = new CopyOnWriteArrayList<>();

	private static Ledger ledger(MinecraftServer server) {
		return CasinoWorldData.get(server).ledger();
	}

	private static long maxBalance() {
		return CasinoConfig.economy().maxBalance;
	}

	@Override
	public long balance(ServerPlayer player) {
		return balance(player.level().getServer(), player.getUUID());
	}

	@Override
	public long balance(MinecraftServer server, UUID playerId) {
		return ledger(server).balance(playerId);
	}

	@Override
	public boolean tryWithdraw(ServerPlayer player, long amount, Transaction reason) {
		requireNonNegative(amount);
		return transfer(player.level().getServer(), AccountId.player(player.getUUID()), AccountId.HOUSE, amount, reason).ok();
	}

	@Override
	public long deposit(ServerPlayer player, long amount, Transaction reason) {
		return deposit(player.level().getServer(), player.getUUID(), amount, reason);
	}

	@Override
	public long deposit(MinecraftServer server, UUID playerId, long amount, Transaction reason) {
		requireNonNegative(amount);
		long before = ledger(server).balance(playerId);
		transfer(server, AccountId.HOUSE, AccountId.player(playerId), amount, reason);
		return ledger(server).balance(playerId) - before;
	}

	@Override
	public void setBalance(MinecraftServer server, UUID playerId, long amount, Transaction reason) {
		Ledger ledger = ledger(server);
		long before = ledger.balance(playerId);
		ledger.setBalance(playerId, amount, maxBalance());
		CasinoWorldData.get(server).setDirty();
		fireChanged(server, playerId, before, ledger.balance(playerId), reason);
	}

	@Override
	public Batch batch(MinecraftServer server) {
		return new LedgerBatch(server);
	}

	@Override
	public Bankrolls bankrolls(MinecraftServer server) {
		return new Bankrolls() {
			@Override
			public BankrollInfo open(String id, UUID owner) {
				CasinoWorldData.get(server).setDirty();
				return ledger(server).openBankroll(id, owner);
			}

			@Override
			public Optional<BankrollInfo> get(String id) {
				return ledger(server).bankroll(id);
			}

			@Override
			public boolean reserve(String id, long amount) {
				requireNonNegative(amount);
				boolean ok = ledger(server).reserve(id, amount);
				CasinoWorldData.get(server).setDirty();
				return ok;
			}

			@Override
			public void release(String id, long amount) {
				ledger(server).release(id, amount);
				CasinoWorldData.get(server).setDirty();
			}

			@Override
			public long close(String id) {
				CasinoWorldData.get(server).setDirty();
				return ledger(server).closeBankroll(id);
			}
		};
	}

	@Override
	public void addCreditHook(CreditHook hook) {
		hooks.add(hook);
	}

	private final class LedgerBatch implements Batch {
		private final MinecraftServer server;
		private final List<Ledger.Leg> legs = new ArrayList<>();
		private boolean committed;

		LedgerBatch(MinecraftServer server) {
			this.server = server;
		}

		@Override
		public Batch debit(AccountId account, long amount) {
			requireNonNegative(amount);
			legs.add(new Ledger.Leg(account, -amount));
			return this;
		}

		@Override
		public Batch credit(AccountId account, long amount) {
			requireNonNegative(amount);
			legs.add(new Ledger.Leg(account, amount));
			return this;
		}

		@Override
		public TxResult commit(Transaction reason) {
			if (committed) {
				throw new IllegalStateException("batch already committed");
			}
			committed = true;
			CasinoWorldData data = CasinoWorldData.get(server);
			Ledger.Commit result = data.ledger().commit(legs, maxBalance(), e -> {
				long amount = e.getValue();
				if (!reason.kind().garnishable() || !(e.getKey() instanceof AccountId.Player p)) {
					return amount;
				}
				for (CreditHook hook : hooks) {
					amount = Math.max(0, Math.min(amount, hook.beforeCredit(server, p.id(), amount, reason)));
				}
				return amount;
			});
			if (!result.ok()) {
				return TxResult.insufficient(result.failed());
			}
			data.setDirty();
			for (Map.Entry<AccountId, Long> e : result.before().entrySet()) {
				if (e.getKey() instanceof AccountId.Player p) {
					long before = e.getValue();
					fireChanged(server, p.id(), before, before + result.applied().get(p), reason);
				}
			}
			if (result.lostToCap() > 0) {
				for (AccountId id : result.applied().keySet()) {
					if (id instanceof AccountId.Player p) {
						ServerPlayer online = server.getPlayerList().getPlayer(p.id());
						if (online != null && data.ledger().balance(p.id()) >= maxBalance()) {
							online.sendSystemMessage(Component.translatable("msg.burmaldaholic.core.balance_capped"));
						}
					}
				}
			}
			return TxResult.success(result.lostToCap());
		}
	}

	private static void fireChanged(MinecraftServer server, UUID playerId, long before, long after, Transaction reason) {
		if (before == after) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player != null) {
			CasinoEvents.BALANCE_CHANGED.invoker().onBalanceChanged(player, before, after, reason);
		}
	}

	private static void requireNonNegative(long amount) {
		if (amount < 0) {
			throw new IllegalArgumentException("amount must be >= 0: " + amount);
		}
	}
}
