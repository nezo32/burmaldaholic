package dev.nezo.burmaldaholic.core.economy;

import java.util.Objects;
import java.util.UUID;

/**
 * Who holds chips. {@link Player} = a player's balance, {@link Bankroll} = an owned casino's
 * bankroll (GAME_DESIGN.md §18.2), {@link House} = the world bank (infinite: mints and sinks chips).
 */
public sealed interface AccountId {
	static AccountId player(UUID id) {
		return new Player(id);
	}

	static AccountId bankroll(String id) {
		return new Bankroll(id);
	}

	AccountId HOUSE = House.INSTANCE;

	record Player(UUID id) implements AccountId {
		public Player {
			Objects.requireNonNull(id);
		}
	}

	/** {@code id} is chosen by the owning module (e.g. "multiplayer:charter/<dim>/<x>,<y>,<z>"). */
	record Bankroll(String id) implements AccountId {
		public Bankroll {
			Objects.requireNonNull(id);
		}
	}

	enum House implements AccountId {
		INSTANCE
	}
}
