package dev.nezo.burmaldaholic.games.craps.present;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * What the craps v2 screen knows, read once per state packet from the table's client state
 * ({@code CrapsTableBlockEntity#writeClientState}) — the input of the pure {@link CrapsFlow} (ui-v2.md §5.1).
 *
 * @param seated        the viewer has a seat
 * @param seatFree      a seat is free (the viewer could sit)
 * @param balance       the viewer's chips
 * @param min           table minimum (flat bets)
 * @param max           table maximum (≤ 0 = none)
 * @param enabled       craps enabled in the config
 * @param point         point now (0 = OFF)
 * @param youShoot      the viewer holds the dice
 * @param canRoll       the server would accept {@code roll} now
 * @param needLine      the shooter has no line bet on the come-out
 * @param window        multi-player betting window open
 * @param windowSeconds seconds left of the window (-1 = none)
 * @param rollSeconds   seconds until the auto roll (-1 = none)
 * @param shooter       the shooter's name ("" = nobody)
 * @param canUndo       chips put down since the last roll exist
 * @param allowed       flat bet kinds the server accepts now ({@code pass}, {@code dont_pass}, {@code come}, {@code dont_come}, {@code field})
 * @param bets          every bet on the layout (others' with id -1)
 * @param rolling       the throw is still being shown (client presentation)
 * @param seats         players seated
 * @param settling      the roll is still being settled on the felt (chips, stick; client presentation): Roll waits (D5)
 */
public record CrapsView(boolean seated, boolean seatFree, long balance, long min, long max, boolean enabled, int point, boolean youShoot,
		boolean canRoll, boolean needLine, boolean window, long windowSeconds, long rollSeconds, String shooter, boolean canUndo,
		List<String> allowed, List<BetPub> bets, boolean rolling, int seats, boolean settling) {
	public static final String[] KINDS = {"pass", "dont_pass", "come", "dont_come", "field"};

	public CrapsView {
		allowed = List.copyOf(allowed);
		bets = List.copyOf(bets);
		settling |= rolling;
	}

	/** This view with other bets on the layout (the screen shows the bets as they were while the throw is shown). */
	public CrapsView withBets(List<BetPub> other) {
		return new CrapsView(seated, seatFree, balance, min, max, enabled, point, youShoot, canRoll, needLine, window, windowSeconds, rollSeconds, shooter,
			canUndo, allowed, other, rolling, seats, settling);
	}

	/** A view without the settling beat (the felt and Roll share the throw gate). */
	public CrapsView(boolean seated, boolean seatFree, long balance, long min, long max, boolean enabled, int point, boolean youShoot, boolean canRoll,
			boolean needLine, boolean window, long windowSeconds, long rollSeconds, String shooter, boolean canUndo, List<String> allowed, List<BetPub> bets,
			boolean rolling, int seats) {
		this(seated, seatFree, balance, min, max, enabled, point, youShoot, canRoll, needLine, window, windowSeconds, rollSeconds, shooter, canUndo, allowed,
			bets, rolling, seats, rolling);
	}

	/**
	 * One bet on the layout.
	 *
	 * @param id     the bet id (the viewer's own bets only, else -1)
	 * @param point  the come / don't come point (0 = on its own area)
	 * @param unit   odds unit (own bets with a point)
	 * @param room   odds that can still be added (own bets)
	 * @param off    come odds are off on the come-out
	 */
	public record BetPub(int id, String kind, long flat, long odds, int point, boolean mine, int unit, long oddsMax, long room, boolean off) {
		public long staked() {
			return flat + odds;
		}
	}

	public boolean allowed(String kind) {
		return allowed.contains(kind);
	}

	public boolean comeOut() {
		return point == 0;
	}

	/** Chips the viewer has on the layout (flat + odds). */
	public long myTotal() {
		long t = 0;
		for (BetPub b : bets) {
			if (b.mine()) t += b.staked();
		}
		return t;
	}

	public List<BetPub> mine() {
		List<BetPub> out = new ArrayList<>();
		for (BetPub b : bets) {
			if (b.mine()) out.add(b);
		}
		return out;
	}

	/** Own bets that can take more odds now. */
	public List<BetPub> oddsTargets() {
		List<BetPub> out = new ArrayList<>();
		for (BetPub b : bets) {
			if (b.mine() && b.room() > 0 && !"field".equals(b.kind())) out.add(b);
		}
		return out;
	}

	/**
	 * Reads the client state. {@code windowSeconds} / {@code rollSeconds} are computed by the caller from the synced
	 * timers (they tick down between packets); {@code rolling} is the screen's own presentation state.
	 */
	public static CrapsView read(CompoundTag s, long windowSeconds, long rollSeconds, boolean rolling) {
		return read(s, windowSeconds, rollSeconds, rolling, rolling);
	}

	/** {@link #read(CompoundTag, long, long, boolean)} with the settling beat (Roll waits for the stick and idle, D5). */
	public static CrapsView read(CompoundTag s, long windowSeconds, long rollSeconds, boolean rolling, boolean settling) {
		List<String> allowed = new ArrayList<>();
		CompoundTag a = s.getCompoundOrEmpty("allowed");
		for (String k : KINDS) {
			if (a.getBooleanOr(k, false)) allowed.add(k);
		}
		List<BetPub> bets = new ArrayList<>();
		ListTag list = s.getListOrEmpty("bets");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			bets.add(new BetPub(t.getIntOr("id", -1), t.getStringOr("kind", ""), t.getLongOr("flat", 0), t.getLongOr("odds", 0), t.getIntOr("point", 0),
				t.getBooleanOr("mine", false), t.getIntOr("unit", 1), t.getLongOr("odds_max", 0), t.getLongOr("room", 0), t.getBooleanOr("off", false)));
		}
		int seated = s.getListOrEmpty("seats").size();
		boolean window = s.getBooleanOr("window", false);
		return new CrapsView(s.getIntOr("seat", -1) >= 0, seated < s.getIntOr("seat_count", 0), s.getLongOr("balance", 0), s.getLongOr("min", 1),
			s.getLongOr("max", 0), s.getBooleanOr("enabled", true), s.getIntOr("point", 0), s.getBooleanOr("you_shoot", false),
			s.getBooleanOr("can_roll", false), s.getBooleanOr("need_line", false), window, window ? windowSeconds : -1, rollSeconds,
			s.getStringOr("shooter", ""), s.getBooleanOr("can_undo", false), allowed, bets, rolling, seated, settling);
	}
}
