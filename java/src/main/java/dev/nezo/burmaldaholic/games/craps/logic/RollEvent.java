package dev.nezo.burmaldaholic.games.craps.logic;

import java.util.Locale;

/** Table-level meaning of a roll (drives the puck and the announcements). */
public record RollEvent(Kind kind, int total, int point) {
	public enum Kind {
		/** come-out 7/11 */
		NATURAL,
		/** come-out 2/3/12 */
		CRAPS,
		/** come-out 4,5,6,8,9,10: puck ON */
		POINT_SET,
		/** point rolled again: pass wins, puck OFF, same shooter */
		POINT_MADE,
		/** 7 with a point on: puck OFF, next shooter */
		SEVEN_OUT,
		/** any other roll with a point on */
		ROLL;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Point after this roll (0 = puck OFF). */
	public int nextPoint() {
		return switch (kind) {
			case POINT_SET, ROLL -> point;
			default -> 0;
		};
	}

	/** @param point the table point BEFORE the roll (0 = come-out) */
	public static RollEvent of(int point, int total) {
		if (point == 0) {
			if (total == 7 || total == 11) {
				return new RollEvent(Kind.NATURAL, total, 0);
			}
			if (total == 2 || total == 3 || total == 12) {
				return new RollEvent(Kind.CRAPS, total, 0);
			}
			return new RollEvent(Kind.POINT_SET, total, total);
		}
		if (total == point) {
			return new RollEvent(Kind.POINT_MADE, total, point);
		}
		if (total == 7) {
			return new RollEvent(Kind.SEVEN_OUT, total, point);
		}
		return new RollEvent(Kind.ROLL, total, point);
	}
}
