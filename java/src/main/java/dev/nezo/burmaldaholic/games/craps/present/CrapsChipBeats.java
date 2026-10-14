package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.games.craps.logic.CrapsBeats;
import dev.nezo.burmaldaholic.games.craps.present.CrapsLayout.Layer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the chips move after a roll (CS §4.6), in casino order: the dealer TAKES the losers first (one wave left to right,
 * 40 ms apart, each stack dragged flat to the house bank at the top centre of the back wall), then come / don't-come
 * bets TRAVEL to their number while the winners are PAID (payout discs slide one by one from the bank into a column
 * beside the bet), pushes and odds that were off slide back to the tray, and finally your won chips are COLLECTED to the
 * balance (everybody else's slide off the felt at the bottom edge). Driven only by the server's {@code last_res} (the
 * amounts and returns are the server's; no number is invented) and the layout boxes. Every flight ends exactly on its
 * target box. Pure: plan once per roll, then {@link #sample} per frame (allocation-free).
 *
 * <p>Times are absolute ms on the roll clock ({@code roll_time}); local durations scale with {@link CrapsBeats#f}.
 */
public final class CrapsChipBeats {
	/** Take: drag duration and the wave's stagger (Normal). */
	public static final double TAKE_MS = 260, TAKE_STAGGER = 40;
	public static final double TRAVEL_MS = 400, PAY_MS = 220, PAY_STAGGER = 60, COLLECT_MS = 380, COLLECT_STAGGER = 30, PUSH_MS = 300,
		EXIT_MS = 220;
	/** Arc heights (px at T2; scaled by the caller's chip size). */
	public static final double TRAVEL_ARC = 8, COLLECT_ARC = 10;
	/** Disc pitch of a payout column / a raked line (px). */
	public static final int STEP = CrapsLayout.STACK_STEP;

	private CrapsChipBeats() {}

	public enum Kind {
		/** A losing stack raked to the bank. */
		TAKE,
		/** A come / don't come bet travelling to its number. */
		TRAVEL,
		/** One payout disc from the bank to the column beside a winning bet. */
		PAY,
		/** Your winning stack (or its payout column) flying to the balance. */
		COLLECT,
		/** A push / odds that were off sliding back to the tray. */
		PUSH,
		/** Somebody else's won / pushed chips sliding off the felt at the bottom edge. */
		EXIT
	}

	/** What the last roll did to one bet (server {@code last_res}). */
	public record Res(String kind, int point, boolean mine, String outcome, long flat, long odds, long ret, int movedTo) {
		public long staked() {
			return flat + odds;
		}

		public long net() {
			return ret - flat - odds;
		}
	}

	/** Where the chips go: the layout's stack boxes and the targets outside the felt. */
	public interface Boxes {
		Box stack(String kind, int point, Layer layer);

		/** The house bank (top centre of the back wall); a disc-sized box. */
		Box bank();

		/** Your chip tray (the Dock) for {@code amount}. */
		Box tray(long amount);

		/** The balance in the TopBar. */
		Box balance();

		/** Where somebody else's chips leave the felt. */
		Box exit(boolean left);
	}

	/**
	 * One chip movement: {@code amount} drawn as a stack of {@code size} px (or a single disc of denomination
	 * {@code denom} when {@code denom > 0}), from top-left {@code (x0, y0)} to {@code (x1, y1)}.
	 *
	 * @param shownFrom the stack is drawn (resting at the start) from this time
	 * @param hideAt    the stack is no longer drawn from this time ({@code +∞} = stays at the end)
	 * @param res       index into the plan's results (the bet it belongs to), -1 = none
	 */
	public record Flight(Kind kind, int res, boolean mine, long amount, long denom, int size, double x0, double y0, double x1, double y1, double shownFrom,
			double start, double dur, double hideAt, int curve, double arc, double spread, float a0, float a1, double fadeFrom) {
		public double end() {
			return start + dur;
		}
	}

	/** Easing curves of a flight. */
	public static final int IN_QUAD = 0, OUT_CUBIC = 1, IN_OUT_CUBIC = 2, IN_CUBIC = 3;

	/** A net pill of one of your bets: shown from {@code from} to {@code to} above {@code at}. */
	public record Pill(Box at, long net, double from, double to) {}

	/** The plan of one roll. */
	public record Plan(List<Flight> flights, List<Pill> pills, List<Res> res, double lastPayLand, double collectEnd) {
		/** Flights of travelled come bets (the static stack on the number waits for its landing). */
		public double travelLanding(String kind, int point, boolean mine) {
			for (Flight f : flights) {
				if (f.kind == Kind.TRAVEL && f.mine == mine) {
					Res r = res.get(f.res);
					if (r.kind().equals(kind) && r.movedTo() == point) return f.end();
				}
			}
			return Double.NEGATIVE_INFINITY;
		}
	}

	/** The sample of a flight at time t. */
	public static final class Pos {
		public boolean visible;
		public double x;
		public double y;
		public float alpha;
		/** Rake spread: px between consecutive discs drawn as a line trailing the top disc. */
		public double spread;
		/** 0..1 progress of the move (0 before start). */
		public double p;
	}

	private static Box box(Boxes b, Res r, boolean odds, int point) {
		Layer l = r.mine() ? (odds ? Layer.ODDS : Layer.MINE) : Layer.OTHERS;
		return b.stack(r.kind(), point, l);
	}

	/**
	 * Plans the chip beats of a roll. {@code sevenOut}: every losing stack goes in one wave (come points included).
	 * Others' stacks are merged per spot (the screen draws one stack per spot for everybody else).
	 */
	public static Plan plan(List<Res> res, Boxes b, CrapsBeats beats, boolean sevenOut) {
		List<Flight> out = new ArrayList<>();
		List<Pill> pills = new ArrayList<>();
		double f = beats.f;
		// ---- the take: one wave, left to right by stack x ----
		record Take(int res, boolean mine, Box at, long amount) {}
		List<Take> takes = new ArrayList<>();
		Map<String, Integer> othersTake = new LinkedHashMap<>();
		for (int i = 0; i < res.size(); i++) {
			Res r = res.get(i);
			if (!"lose".equals(r.outcome())) continue;
			if (r.mine()) {
				takes.add(new Take(i, true, box(b, r, false, r.point()), r.flat()));
				// come odds that were off on the come-out come back (inside ret): they slide to the tray instead
				if (r.odds() > 0 && r.ret() <= 0) takes.add(new Take(i, true, box(b, r, true, r.point()), r.odds()));
			} else {
				Box at = box(b, r, false, r.point());
				String key = at.toString();
				Integer j = othersTake.get(key);
				if (j == null) {
					othersTake.put(key, takes.size());
					takes.add(new Take(i, false, at, r.staked()));
				} else {
					Take t = takes.get(j);
					takes.set(j, new Take(t.res, false, t.at, t.amount + r.staked()));
				}
			}
		}
		takes.sort(Comparator.comparingInt((Take t) -> t.at.x()).thenComparingInt(t -> t.at.y()));
		Box bank = b.bank();
		for (int i = 0; i < takes.size(); i++) {
			Take t = takes.get(i);
			double start = beats.CHIPS + i * TAKE_STAGGER * f;
			out.add(new Flight(Kind.TAKE, t.res, t.mine, t.amount, 0, t.at.w(), t.at.x(), t.at.y(), bank.x() + (bank.w() - t.at.w()) / 2.0,
				bank.bottom() - t.at.h(), beats.CHIPS, start, TAKE_MS * f, start + TAKE_MS * f, IN_QUAD, 0, STEP, 1f, 0f, 0.7));
			if (t.mine) pills.add(new Pill(t.at, -t.amount, beats.CHIPS, beats.COLLECT));
		}
		// ---- travel, pay, push ----
		double travel = beats.TRAVEL;
		double lastPayLand = travel;
		List<Flight> collect = new ArrayList<>();
		int payOrder = 0;
		Map<String, long[]> othersWon = new LinkedHashMap<>();
		Map<String, Box> othersWonBox = new LinkedHashMap<>();
		for (int i = 0; i < res.size(); i++) {
			Res r = res.get(i);
			if ("lose".equals(r.outcome()) && r.mine() && r.odds() > 0 && r.ret() > 0) {
				Box from = box(b, r, true, r.point());
				Box tray = b.tray(r.ret());
				out.add(new Flight(Kind.PUSH, i, true, r.ret(), 0, from.w(), from.x(), from.y(), tray.x() + (tray.w() - from.w()) / 2.0,
					tray.y() + (tray.h() - from.h()) / 2.0, beats.CHIPS, travel, PUSH_MS * f, travel + PUSH_MS * f, OUT_CUBIC, 6, 0, 1f, 0.4f, 0));
			}
			switch (r.outcome()) {
				case "move" -> {
					Box from = box(b, r, false, 0);
					Box to = box(b, r, false, r.movedTo());
					out.add(new Flight(Kind.TRAVEL, i, r.mine(), r.flat(), 0, from.w(), from.x(), from.y(), to.x(), to.y(), beats.CHIPS, travel, TRAVEL_MS * f,
						travel + TRAVEL_MS * f, IN_OUT_CUBIC, TRAVEL_ARC, 0, 1f, 1f, 1));
				}
				case "win" -> {
					Box flat = box(b, r, false, r.point());
					long payout = Math.max(0, r.net());
					if (!r.mine()) {
						String key = flat.toString();
						othersWon.computeIfAbsent(key, k -> new long[1])[0] += r.ret();
						othersWonBox.putIfAbsent(key, flat);
						// the stack holds until it leaves; the payout joins it (one stack per spot for everybody else)
						continue;
					}
					// the payout column beside the bet (right of it; left when that would leave the felt row)
					Box col = payColumn(b, r, flat);
					List<Long> discs = discs(payout);
					double land = travel;
					for (int d = 0; d < discs.size(); d++) {
						double start = travel + (payOrder++) * PAY_STAGGER * f;
						double tx = col.x();
						double ty = col.bottom() - col.w() - d * STEP;
						land = start + PAY_MS * f;
						out.add(new Flight(Kind.PAY, i, true, discs.get(d), discs.get(d), col.w(), bank.x() + (bank.w() - col.w()) / 2.0, bank.y(), tx, ty, start,
							start, PAY_MS * f, beats.COLLECT, OUT_CUBIC, 0, 0, 1f, 1f, 1));
					}
					lastPayLand = Math.max(lastPayLand, land);
					pills.add(new Pill(flat, r.net(), land, beats.COLLECT + COLLECT_MS * f));
					// collect: the flat stack, the odds stack, the payout column
					collect.add(stackTo(Kind.COLLECT, i, flat, r.flat(), b.balance(), beats.CHIPS, f));
					if (r.odds() > 0) collect.add(stackTo(Kind.COLLECT, i, box(b, r, true, r.point()), r.odds(), b.balance(), beats.CHIPS, f));
					if (payout > 0) collect.add(stackTo(Kind.COLLECT, i, col, payout, b.balance(), beats.COLLECT, f));
				}
				case "push", "odds_off" -> {
					if (!r.mine()) {
						String key = box(b, r, false, r.point()).toString();
						othersWon.computeIfAbsent(key, k -> new long[1])[0] += r.ret();
						othersWonBox.putIfAbsent(key, box(b, r, false, r.point()));
						continue;
					}
					long back = r.ret();
					Box from = "odds_off".equals(r.outcome()) && r.odds() > 0 ? box(b, r, true, r.point()) : box(b, r, false, r.point());
					Box tray = b.tray(back);
					out.add(new Flight(Kind.PUSH, i, true, back, 0, from.w(), from.x(), from.y(), tray.x() + (tray.w() - from.w()) / 2.0,
						tray.y() + (tray.h() - from.h()) / 2.0, beats.CHIPS, travel, PUSH_MS * f, travel + PUSH_MS * f, OUT_CUBIC, 6, 0, 1f, 0.4f, 0));
					pills.add(new Pill(from, 0, beats.CHIPS, travel + PUSH_MS * f));
				}
				default -> {}
			}
		}
		// your collect: one wave, 30 ms apart
		for (int i = 0; i < collect.size(); i++) {
			Flight c = collect.get(i);
			double start = beats.COLLECT + i * COLLECT_STAGGER * f;
			out.add(new Flight(c.kind, c.res, true, c.amount, 0, c.size, c.x0, c.y0, c.x1, c.y1, c.shownFrom, start, COLLECT_MS * f, start + COLLECT_MS * f,
				IN_CUBIC, COLLECT_ARC, 0, 1f, 0.6f, 0.8));
		}
		// everybody else's won / pushed chips slide off at the bottom edge
		for (Map.Entry<String, long[]> e : othersWon.entrySet()) {
			Box from = othersWonBox.get(e.getKey());
			Box exit = b.exit(from.cx() < (bank.cx()));
			out.add(new Flight(Kind.EXIT, -1, false, e.getValue()[0], 0, from.w(), from.x(), from.y(), from.x() + (exit.cx() - from.cx()) * 0.25,
				exit.y(), beats.CHIPS, beats.COLLECT, EXIT_MS * f, beats.COLLECT + EXIT_MS * f, IN_CUBIC, 0, 0, 0.85f, 0f, 0.5));
		}
		double collectEnd = beats.COLLECT;
		for (Flight fl : out) if (fl.kind == Kind.COLLECT || fl.kind == Kind.EXIT) collectEnd = Math.max(collectEnd, fl.end());
		return new Plan(List.copyOf(out), List.copyOf(pills), List.copyOf(res), lastPayLand, collectEnd);
	}

	private static Flight stackTo(Kind kind, int res, Box from, long amount, Box to, double shownFrom, double f) {
		return new Flight(kind, res, true, amount, 0, from.w(), from.x(), from.y(), to.x() + (to.w() - from.w()) / 2.0, to.y() + (to.h() - from.h()) / 2.0,
			shownFrom, 0, 0, 0, IN_CUBIC, 0, 0, 1f, 1f, 1);
	}

	/** The payout column of a winning bet: one disc right of its stack (left when the row ends). */
	static Box payColumn(Boxes b, Res r, Box flat) {
		Box odds = r.odds() > 0 ? box(b, r, true, r.point()) : null;
		Box base = odds != null && odds.x() > flat.x() ? odds : flat;
		return base.offset(base.w() + 1, 0);
	}

	/** Greedy denominations of a payout, at most {@code CrapsLayout.MAX_DISCS} discs (largest first). */
	public static List<Long> discs(long amount) {
		List<Long> out = new ArrayList<>();
		long left = amount;
		long[] d = dev.nezo.burmaldaholic.core.ui.v2.BetView.DENOMS;
		for (int i = d.length - 1; i >= 0 && out.size() < CrapsLayout.MAX_DISCS; i--) {
			while (left >= d[i] && out.size() < CrapsLayout.MAX_DISCS) {
				out.add(d[i]);
				left -= d[i];
			}
		}
		if (out.isEmpty() && amount > 0) out.add(d[0]);
		return out;
	}

	/** Samples flight {@code fl} at roll time {@code t} into {@code out}. */
	public static void sample(Flight fl, double t, Pos out) {
		out.visible = t >= fl.shownFrom && t < fl.hideAt;
		double p = fl.dur <= 0 ? (t >= fl.start ? 1 : 0) : Math.max(0, Math.min(1, (t - fl.start) / fl.dur));
		out.p = p;
		double e = switch (fl.curve) {
			case IN_QUAD -> p * p;
			case OUT_CUBIC -> Ease.OUT_CUBIC.apply(p);
			case IN_CUBIC -> Ease.IN_CUBIC.apply(p);
			default -> Ease.IN_OUT_CUBIC.apply(p);
		};
		out.x = fl.x0 + (fl.x1 - fl.x0) * e;
		out.y = fl.y0 + (fl.y1 - fl.y0) * e - fl.arc * Math.sin(Math.PI * p);
		double fade = fl.fadeFrom >= 1 ? 0 : Math.max(0, (p - fl.fadeFrom) / (1 - fl.fadeFrom));
		out.alpha = (float) (fl.a0 + (fl.a1 - fl.a0) * fade);
		out.spread = fl.spread * Math.min(1, p * 3);
		if (p >= 1 && fl.hideAt <= fl.end()) out.visible = false;
	}
}
