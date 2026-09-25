package dev.nezo.burmaldaholic.games.slots.pvp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.pvp.logic.PvpEvent;
import dev.nezo.burmaldaholic.core.pvp.logic.Step;
import dev.nezo.burmaldaholic.games.slots.logic.Symbol;
import dev.nezo.burmaldaholic.games.slots.pvp.ShowdownScoring.LineScore;
import dev.nezo.burmaldaholic.games.slots.pvp.ShowdownScoring.Match;
import dev.nezo.burmaldaholic.games.slots.pvp.ShowdownScoring.Round;
import dev.nezo.burmaldaholic.games.slots.pvp.SlotShowdownMode.Params;
import dev.nezo.burmaldaholic.games.slots.pvp.SlotShowdownMode.Tape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Slot Showdown reveal timeline (PVP.md §5.3). PURE. Every step carries only what it reveals (the r-th
 * grids, the r-th scores), never later rounds. The final round's points are hidden: the engine's Final
 * Reveal (§3.11.4) is appended after the last {@code spin} step.
 *
 * <p>Step kinds and {@code data}:
 * <ul>
 *   <li>{@code underdog} (final round only, when someone is boosted): {@code seats[]};</li>
 *   <li>{@code round_wait} (waitForAll, {@code spinIntervalTicks}): {@code spins, hot} (symbol ordinal, -1 = off), {@code final};</li>
 *   <li>{@code spin} (40 t): {@code grids[player][9]} (symbol ordinals, row-major), {@code final};</li>
 *   <li>{@code score} (not after the final spin): {@code points[], totals[], mult[], lines[player][]} (paying line
 *       numbers), {@code hot_lines[player][]}, {@code events[]} ({@code {kind, seat, …data}}).</li>
 * </ul>
 * Presentation hints for the edition presenter (generic, mode-agnostic): {@code msgs[]} = chat lines
 * {@code {key, args[]}}, {@code titles[]} = {@code {seat, key, sub?}} (seat -1 = everyone), {@code sounds[]} =
 * {@code {id, seat, volume, pitch}} (vanilla / {@code burmaldaholic:} sound ids, seat -1 = everyone). An arg is
 * {@code {"seat":i}} (the participant's display name), {@code {"n":x}} (a number) or {@code {"key":k}} (translated).
 */
public final class ShowdownTimeline {
	private ShowdownTimeline() {}

	public static List<Step> build(Tape tape, Match match, Params params) {
		List<Step> steps = new ArrayList<>();
		int rounds = tape.hot().length;
		int n = tape.seatOrder().length;
		for (Round round : match.rounds()) {
			int r = round.round();
			boolean last = r == rounds - 1;
			if (round.underdogs().length > 0) {
				JsonObject d = new JsonObject();
				d.add("seats", SlotShowdownMode.ints(round.underdogs()));
				JsonArray msgs = new JsonArray();
				JsonArray titles = new JsonArray();
				JsonArray sounds = new JsonArray();
				for (int u : round.underdogs()) {
					msgs.add(msg("msg.burmaldaholic.pvp.slots.underdog", seat(u)));
					titles.add(title(u, "gui.burmaldaholic.pvp.slots.underdog", "msg.burmaldaholic.pvp.slots.underdog_you"));
				}
				sounds.add(sound("entity.firework_rocket.twinkle", -1, 1.0f));
				d.add("msgs", msgs);
				d.add("titles", titles);
				d.add("sounds", sounds);
				steps.add(new Step("underdog", SlotShowdownMode.UNDERDOG_TICKS, r, false, d));
			}
			JsonObject wait = new JsonObject();
			wait.addProperty("spins", rounds);
			wait.addProperty("hot", params.rules().hotSymbol() ? round.hot().ordinal() : -1);
			wait.addProperty("final", last);
			if (params.rules().hotSymbol()) {
				JsonArray msgs = new JsonArray();
				msgs.add(msg("msg.burmaldaholic.pvp.slots.hot", key(round.hot().translationKey())));
				wait.add("msgs", msgs);
				JsonArray sounds = new JsonArray();
				sounds.add(sound("item.firecharge.use", -1, 0.5f));
				wait.add("sounds", sounds);
			}
			steps.add(new Step("round_wait", params.rules().spinIntervalTicks(), r, true, wait));

			JsonObject spin = new JsonObject();
			JsonArray grids = new JsonArray();
			for (int i = 0; i < n; i++) {
				grids.add(SlotShowdownMode.ints(tape.grids()[i][r]));
			}
			spin.add("grids", grids);
			spin.addProperty("final", last);
			JsonArray spinSounds = new JsonArray();
			spinSounds.add(sound("burmaldaholic:slot_spin", -1, 1.0f));
			spin.add("sounds", spinSounds);
			steps.add(new Step("spin", SlotShowdownMode.SPIN_TICKS, r, false, spin));

			if (!last) {
				steps.add(scoreStep(round, n));
			}
		}
		return steps;
	}

	private static Step scoreStep(Round round, int n) {
		JsonObject d = new JsonObject();
		d.add("points", SlotShowdownMode.longs(round.spinPoints()));
		d.add("totals", SlotShowdownMode.longs(round.totals()));
		d.add("mult", SlotShowdownMode.ints(round.multiplier()));
		JsonArray lines = new JsonArray();
		JsonArray hotLines = new JsonArray();
		for (int i = 0; i < n; i++) {
			JsonArray l = new JsonArray();
			JsonArray h = new JsonArray();
			for (LineScore s : round.spins()[i].lines()) {
				if (s.points() > 0) {
					l.add(s.line());
					if (s.hot()) {
						h.add(s.line());
					}
				}
			}
			lines.add(l);
			hotLines.add(h);
		}
		d.add("lines", lines);
		d.add("hot_lines", hotLines);
		JsonArray events = new JsonArray();
		JsonArray msgs = new JsonArray();
		JsonArray titles = new JsonArray();
		JsonArray sounds = new JsonArray();
		for (PvpEvent e : round.events()) {
			if (e.kind().equals(ShowdownScoring.UNDERDOG)) {
				continue; // announced by its own step
			}
			JsonObject ev = new JsonObject();
			ev.addProperty("kind", e.kind());
			ev.addProperty("seat", e.seat());
			for (Map.Entry<String, Long> x : e.data().entrySet()) {
				ev.addProperty(x.getKey(), x.getValue());
			}
			events.add(ev);
			Map<String, Long> data = e.data();
			switch (e.kind()) {
				case ShowdownScoring.KABOOM -> {
					msgs.add(msg("msg.burmaldaholic.pvp.slots.kaboom", seat(e.seat()), num(data.get("before")), num(data.get("after"))));
					titles.add(title(e.seat(), "gui.burmaldaholic.pvp.slots.kaboom_title", null));
					sounds.add(sound("entity.generic.explode", e.seat(), 0.5f));
				}
				case ShowdownScoring.SWAP -> {
					int other = data.get("other").intValue();
					msgs.add(msg("msg.burmaldaholic.pvp.slots.swap", seat(e.seat()), seat(other), num(data.get("from")), num(data.get("to"))));
					titles.add(title(e.seat(), "gui.burmaldaholic.pvp.slots.swap_title", null));
					titles.add(title(other, "gui.burmaldaholic.pvp.slots.swap_title", null));
					sounds.add(sound("entity.enderman.teleport", -1, 0.8f));
				}
				case ShowdownScoring.TIME_WARP -> {
					msgs.add(msg("msg.burmaldaholic.pvp.slots.time_warp", seat(e.seat())));
					sounds.add(sound("block.bell.use", e.seat(), 0.7f));
				}
				case ShowdownScoring.STAR -> msgs.add(msg("msg.burmaldaholic.pvp.slots.star", seat(e.seat()), num(data.get("points"))));
				default -> {
				}
			}
		}
		d.add("events", events);
		d.add("msgs", msgs);
		d.add("titles", titles);
		d.add("sounds", sounds);
		return new Step("score", SlotShowdownMode.SCORE_TICKS + SlotShowdownMode.EVENT_TICKS * events.size(), round.round(), false, d);
	}

	// ---- presentation hint builders ------------------------------------------------------------

	static JsonObject msg(String key, JsonObject... args) {
		JsonObject o = new JsonObject();
		o.addProperty("key", key);
		JsonArray a = new JsonArray();
		for (JsonObject x : args) {
			a.add(x);
		}
		o.add("args", a);
		return o;
	}

	static JsonObject seat(int seat) {
		JsonObject o = new JsonObject();
		o.addProperty("seat", seat);
		return o;
	}

	static JsonObject num(long n) {
		JsonObject o = new JsonObject();
		o.addProperty("n", n);
		return o;
	}

	static JsonObject key(String key) {
		JsonObject o = new JsonObject();
		o.addProperty("key", key);
		return o;
	}

	/** @param selfSub subtitle key shown to that player only (null = none) */
	static JsonObject title(int seat, String key, String selfSub) {
		JsonObject o = new JsonObject();
		o.addProperty("seat", seat);
		o.addProperty("key", key);
		if (selfSub != null) {
			o.addProperty("sub", selfSub);
		}
		return o;
	}

	static JsonObject sound(String id, int seat, float volume) {
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		o.addProperty("seat", seat);
		o.addProperty("volume", volume);
		o.addProperty("pitch", 1.0f);
		return o;
	}

	/** Symbol of an ordinal (client helpers). */
	public static Symbol symbol(int ordinal) {
		return Symbol.byOrdinal(ordinal);
	}
}
