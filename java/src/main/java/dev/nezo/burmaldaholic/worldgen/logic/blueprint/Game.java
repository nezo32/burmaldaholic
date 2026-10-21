package dev.nezo.burmaldaholic.worldgen.logic.blueprint;

import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.worldgen.logic.Layouts;
import java.util.EnumSet;
import java.util.Set;

/**
 * Every game block a casino can hold. {@link Family} groups tiers/variants of one game; a "full" casino
 * (architecture doc §4.4) must contain at least one block of every {@link Family#REQUIRED} family.
 */
public enum Game {
	CASHIER(Layouts.CASHIER, Family.CASHIER),
	NETHER_CASHIER(Layouts.NETHER_CASHIER, Family.CASHIER),
	BLACKJACK(Layouts.BLACKJACK, Family.BLACKJACK),
	BLACKJACK_HIGH_ROLLER(Layouts.BLACKJACK_HIGH_ROLLER, Family.BLACKJACK),
	ROULETTE(Layouts.ROULETTE, Family.ROULETTE),
	ROULETTE_HIGH_ROLLER(Layouts.ROULETTE_HIGH_ROLLER, Family.ROULETTE),
	CRAPS(Layouts.CRAPS, Family.CRAPS),
	BACCARAT(Layouts.BACCARAT, Family.BACCARAT),
	BACCARAT_HIGH_ROLLER(Layouts.BACCARAT_HIGH_ROLLER, Family.BACCARAT),
	BACCARAT_PLAYER_BANKED("burmaldaholic:baccarat_table_player_banked", Family.BACCARAT),
	UTH(Layouts.UTH, Family.UTH),
	UTH_HIGH_ROLLER(Layouts.UTH_HIGH_ROLLER, Family.UTH),
	UTH_PLAYER_BANKED("burmaldaholic:uth_table_player_banked", Family.UTH),
	POKER(Layouts.POKER, Family.POKER),
	SLOTS_COPPER(Layouts.SLOTS_COPPER, Family.SLOTS),
	SLOTS_GOLD(Layouts.SLOTS_GOLD, Family.SLOTS),
	SLOTS_NETHERITE(Layouts.SLOTS_NETHERITE, Family.SLOTS),
	WHEEL(Layouts.WHEEL, Family.WHEEL),
	PLINKO(Layouts.PLINKO, Family.PLINKO);

	public enum Family {
		CASHIER, BLACKJACK, ROULETTE, CRAPS, BACCARAT, UTH, POKER, SLOTS, WHEEL, PLINKO;

		/** Every family is required: the owner wants every existing game in every full casino. */
		public static final Set<Family> REQUIRED = EnumSet.allOf(Family.class);
	}

	private final String blockId;
	private final Family family;

	Game(String blockId, Family family) {
		this.blockId = blockId;
		this.family = family;
	}

	public String blockId() {
		return blockId;
	}

	public Family family() {
		return family;
	}

	/** Card / dice tables get seats on their player side and a dealer side; machines are used from the front. */
	public boolean isTable() {
		return switch (family) {
			case BLACKJACK, ROULETTE, CRAPS, BACCARAT, UTH, POKER -> true;
			default -> false;
		};
	}

	/** Multi-block footprint kind of a table (MT-DESIGN §1), null for 1 × 1 machines and cashiers. */
	public TableGame tableGame() {
		return TableGame.ofBlockName(blockId.substring(blockId.indexOf(':') + 1));
	}

	/** Upright multi-block machine kind (MT-3: slot 1 × 2, wheel / Plinko 3 × 3, cashier 3 × 2), null for tables. */
	public dev.nezo.burmaldaholic.core.table.parts.MachineKind machineKind() {
		return dev.nezo.burmaldaholic.core.table.parts.MachineKind.ofBlockName(blockId.substring(blockId.indexOf(':') + 1));
	}

	public static Game byBlockId(String id) {
		for (Game g : values()) {
			if (g.blockId.equals(id)) {
				return g;
			}
		}
		return null;
	}
}
