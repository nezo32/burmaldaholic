package dev.nezo.burmaldaholic.worldgen.logic;

import java.util.List;

/**
 * What the casino NPCs sell (PURE; GAME_DESIGN §16.1 / §16.3 / §11.3, same lists as Bedrock's
 * {@code CROUPIER_OFFERS} / {@code SHULKER_OFFERS}). Scratch card prices and VIP tiers come from the
 * Cashier Shop offer of the extras module when it is registered (config {@code extras.scratch.*.price});
 * the numbers here are the spec defaults used otherwise.
 *
 * @param id       item path in the {@code burmaldaholic} namespace (also the Cashier Shop offer id)
 * @param price    chips (spec default)
 * @param minTier  VIP tier required (spec default)
 */
public record NpcShop(String id, long price, int minTier) {
	public static final List<NpcShop> CROUPIER = List.of(
		new NpcShop("scratch_card", 10, 0),
		new NpcShop("scratch_card_gold", 100, 1),
		new NpcShop("lucky_coin", 25, 0));
	public static final List<NpcShop> SHULKER_CROUPIER = List.of(new NpcShop("scratch_card_gold", 100, 1));

	/** Offers of an NPC role (empty for roles without a shop). */
	public static List<NpcShop> of(NpcRole role) {
		return switch (role) {
			case CROUPIER -> CROUPIER;
			case SHULKER_CROUPIER -> SHULKER_CROUPIER;
			default -> List.of();
		};
	}

	public enum Check { OK, VIP, FUNDS }

	public static Check check(long price, int minTier, long balance, int tier) {
		if (tier < minTier) {
			return Check.VIP;
		}
		return balance < price ? Check.FUNDS : Check.OK;
	}

	/** Greeting line count per NPC ({@code dialog.burmaldaholic.<npc>.greeting.1..N}, STRINGS.md). */
	public static int greetings(NpcRole role) {
		return switch (role) {
			case CROUPIER, PIGLIN_DEALER -> 3;
			case SHULKER_CROUPIER -> 2;
			default -> 0;
		};
	}

	/** {@code dialog.burmaldaholic.<npc>.greeting.<n>} for {@code n} in 1..greetings (clamped). */
	public static String greetingKey(NpcRole role, int n) {
		int count = Math.max(1, greetings(role));
		return "dialog.burmaldaholic." + role.id() + ".greeting." + Math.max(1, Math.min(count, n));
	}
}
