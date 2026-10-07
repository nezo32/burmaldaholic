package dev.nezo.burmaldaholic.games.extras.present;

import dev.nezo.burmaldaholic.core.ui.v2.BetView;

/**
 * What the v2 extras screens show the kit (docs/architecture/ui-v2.md §5.1): the bet model of the BetBar ({@code null}
 * when the round is staked in levels / hearts / a held item and the Dock shows −/+ instead, or for the scratch ticket),
 * whether a reveal runs (the ball drops, the wheel turns, the coin flies, the dice roll), and a game rule that blocks the
 * primary ({@code blockKey}, e.g. "Pick heads or tails"), else null.
 *
 * @param bet      the bet model, or null (no BetBar)
 * @param busy     a round of this player is being revealed (primary: the game's busy reason)
 * @param blockKey translation key of a game rule that blocks the primary, or null
 * @param stake    the stake as the readout / status shows it when there is no BetBar (levels, hearts), else 0
 * @param stakeMax the stake's upper bound in that unit (−/+ gates), else 0
 * @param winUpTo  the most the current bet can return (status hint "Win up to …"), 0 = no hint
 */
public record ExtrasView(BetView bet, boolean busy, String blockKey, long stake, long stakeMax, long winUpTo) {
	public static ExtrasView chips(BetView bet, boolean busy, String blockKey, long winUpTo) {
		return new ExtrasView(bet, busy, blockKey, 0, 0, winUpTo);
	}

	/** A pawn stake (levels / hearts / held item): no BetBar; {@code stakeMax} 0 = a held item (no −/+). */
	public static ExtrasView pawn(boolean busy, String blockKey, long stake, long stakeMax) {
		return new ExtrasView(null, busy, blockKey, stake, stakeMax, 0);
	}
}
