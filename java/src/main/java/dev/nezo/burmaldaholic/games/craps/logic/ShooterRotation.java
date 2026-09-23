package dev.nezo.burmaldaholic.games.craps.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Clockwise shooter rotation among seated players (GAME_DESIGN §10.2). Pure Java. */
public final class ShooterRotation {
	private ShooterRotation() {}

	/** @param seat seat index (clockwise order) */
	public record SeatInfo(UUID player, int seat, boolean hasLineBet) {}

	/**
	 * Next shooter clockwise after {@code current} (by seat, wrapping). With {@code requireLineBet}
	 * only players with a Pass/Don't Pass bet qualify. {@code includeCurrent} lets the current
	 * shooter keep the dice when nobody else qualifies (seven-out at a one-player table).
	 * {@code current} may have left already (then the next seat after theirs is used if known,
	 * otherwise the first seat).
	 */
	public static @Nullable UUID next(List<SeatInfo> seats, @Nullable UUID current, boolean requireLineBet, boolean includeCurrent,
			int currentSeatHint) {
		if (seats.isEmpty()) {
			return null;
		}
		List<SeatInfo> sorted = new ArrayList<>(seats);
		sorted.sort(Comparator.comparingInt(SeatInfo::seat));
		int curSeat = currentSeatHint;
		for (SeatInfo s : sorted) {
			if (s.player().equals(current)) {
				curSeat = s.seat();
			}
		}
		List<SeatInfo> order = new ArrayList<>();
		if (curSeat < 0) {
			order.addAll(sorted);
		} else {
			int start = 0;
			while (start < sorted.size() && sorted.get(start).seat() <= curSeat) {
				start++;
			}
			order.addAll(sorted.subList(start, sorted.size()));
			order.addAll(sorted.subList(0, start));
		}
		for (SeatInfo s : order) {
			if (s.player().equals(current) && !includeCurrent) {
				continue;
			}
			if (!requireLineBet || s.hasLineBet()) {
				return s.player();
			}
		}
		return null;
	}

	public static @Nullable UUID next(List<SeatInfo> seats, @Nullable UUID current, boolean requireLineBet) {
		return next(seats, current, requireLineBet, true, -1);
	}
}
