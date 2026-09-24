package dev.nezo.burmaldaholic.games.craps.logic;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The bots' VIRTUAL bets at one craps table (BOTS.md §4.7): a shadow {@link CrapsTable} whose puck follows
 * the real one. Owners are the bots' stand-in UUIDs ({@code VirtualSeats.botUuid}). PURE; no money, no
 * shooter (the shadow table never has one), no dice of its own — every roll is the REAL table's roll.
 */
public final class CrapsBotTable {
	private final CrapsTable shadow;

	public CrapsBotTable(CrapsRules rules) {
		this.shadow = new CrapsTable(rules);
	}

	public CrapsTable shadow() {
		return shadow;
	}

	/** Follow the real table's puck and rules (before bets / the roll). */
	public void sync(int point, CrapsRules rules) {
		shadow.syncPoint(point);
		shadow.setRules(rules);
	}

	/** What a human in the bot's seat would see: the puck, its own bets and their odds room. */
	public CrapsBettor.View view(UUID owner, long minBet) {
		List<Bet> bets = shadow.betsOf(owner);
		Map<Integer, CrapsBettor.Room> room = new LinkedHashMap<>();
		for (Bet b : bets) {
			CrapsTable.OddsInfo info = shadow.oddsInfo(b);
			if (info != null) {
				room.put(b.id(), new CrapsBettor.Room(info.room(), info.unit()));
			}
		}
		return new CrapsBettor.View(shadow.point(), bets, minBet, shadow.rules(), room);
	}

	/** Applies a bot's (legalized) actions. */
	public void apply(UUID owner, List<CrapsBettor.Action> actions) {
		for (CrapsBettor.Action a : actions) {
			switch (a) {
				case CrapsBettor.Action.Flat f -> {
					if (shadow.placeError(owner, f.kind()) == null && f.amount() > 0) {
						shadow.addBet(owner, f.kind(), f.amount());
					}
				}
				case CrapsBettor.Action.Odds o -> {
					Bet b = shadow.bet(o.betId());
					if (b != null && b.owner().equals(owner) && shadow.oddsError(b, o.amount()) == null) {
						shadow.addOdds(b.id(), o.amount());
					}
				}
			}
		}
	}

	/** Resolves the virtual bets with the REAL dice from the real table's pre-roll puck. */
	public CrapsTable.TableRoll roll(int pointBefore, int d1, int d2) {
		shadow.syncPoint(pointBefore);
		return shadow.roll(d1, d2, List.of());
	}

	/** A bot left: its virtual bets vanish. */
	public void remove(UUID owner) {
		shadow.removeOwner(owner);
	}

	public Set<UUID> owners() {
		Set<UUID> out = new LinkedHashSet<>();
		for (Bet b : shadow.bets()) {
			out.add(b.owner());
		}
		return out;
	}

	public void clear() {
		shadow.clear();
	}
}
