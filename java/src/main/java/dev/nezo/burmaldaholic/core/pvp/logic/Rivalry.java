package dev.nezo.burmaldaholic.core.pvp.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Settlement-time rivalry rules (PVP.md §3.8, §3.15.2, BOTS.md §5.3). Pure: mutates the given
 * {@link PvpPlayerRecord}s and returns the win-streak call-outs.
 *
 * <ul>
 *   <li>Head-to-head: each winning human gets a win against each losing human, each losing human a loss
 *       against each winning human; split winners score nothing against each other; losers nothing
 *       against each other. Bots never have rows.</li>
 *   <li>Totals and the PvP win streak only count matches with ≥ 1 human opponent (a bot-only match
 *       neither extends nor breaks the streak); bot-only matches go to the separate "vs bots" line.</li>
 * </ul>
 */
public final class Rivalry {
	private Rivalry() {}

	/** @param id null for a bot */
	public record Player(UUID id, String name, long stake) {
		public boolean human() {
			return id != null;
		}
	}

	/**
	 * @param tier    0 heating, 1 rampage, 2 legendary, -1 = streak broken
	 * @param breaker participant index of the winner who broke it (-1 = n/a)
	 */
	public record Callout(UUID player, int streak, int tier, int breaker) {}

	public static List<Callout> apply(List<Player> ps, long[] payouts, int[] winners, Map<UUID, PvpPlayerRecord> records, int[] thresholds) {
		int n = ps.size();
		boolean[] won = new boolean[n];
		for (int w : winners) {
			won[w] = true;
		}
		int humans = 0;
		for (Player p : ps) {
			if (p.human()) {
				humans++;
			}
		}
		int k = Math.max(1, winners.length);
		List<Callout> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Player p = ps.get(i);
			if (!p.human()) {
				continue;
			}
			PvpPlayerRecord rec = records.get(p.id());
			if (rec == null) {
				continue;
			}
			long net = payouts[i] - p.stake();
			if (humans < 2) {
				rec.botRounds++;
				rec.botNet += net;
				continue;
			}
			rec.net += net;
			if (won[i]) {
				rec.wins++;
				rec.streak++;
				int tier = WinStreaks.announceTier(rec.streak, thresholds);
				if (tier >= 0) {
					out.add(new Callout(p.id(), rec.streak, tier, -1));
				}
			} else {
				rec.losses++;
				int prev = rec.streak;
				rec.streak = 0;
				if (WinStreaks.brokenCallout(prev, thresholds)) {
					out.add(new Callout(p.id(), prev, -1, winners.length > 0 ? winners[0] : -1));
				}
			}
			for (int j = 0; j < n; j++) {
				Player o = ps.get(j);
				if (j == i || !o.human() || won[i] == won[j]) {
					continue;
				}
				HeadToHead h = rec.vs(o.id());
				h = won[i] ? h.win(o.stake() / k) : h.loss(p.stake() / k);
				rec.putRival(o.id(), o.name(), h);
			}
		}
		return out;
	}

	/**
	 * Grudge match (PVP.md §3.8): only 2-player matches of two humans. {@code aVsB} = A's record against B.
	 *
	 * @return 0 when A is the underdog (A's run vs B ≤ −grudgeLosses), 1 when B is, -1 = no grudge
	 */
	public static int grudgeUnderdog(HeadToHead aVsB, HeadToHead bVsA, int grudgeLosses) {
		if (aVsB.grudge(grudgeLosses)) {
			return 0;
		}
		if (bVsA.grudge(grudgeLosses)) {
			return 1;
		}
		return -1;
	}
}
