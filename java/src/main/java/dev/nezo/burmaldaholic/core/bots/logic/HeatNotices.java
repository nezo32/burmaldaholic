package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The ONE source of the heat lines (BOTS.md §5.4): {@code msg.burmaldaholic.bots.word_got_around} /
 * {@code …sulking} to a table, plus the matching bot quip ({@code word_got_around} / {@code sulk}). Pure.
 * A table tells a stage of a player once per Minecraft day (not once per bot session: standing up and
 * sitting down again does not repeat it); a higher stage later that day is told again. Only tables whose
 * bots are house-funded money bots hear them, and "Word got around" only at poker (the only game where it
 * changes the bots). {@link #reset} forgets a player ({@code /casino bots heat <player> reset}).
 */
public final class HeatNotices {
	/** A line to send to the table's humans and the bot quip event that goes with it. */
	public record Notice(UUID player, HeatStage stage, String message, String quip) {}

	private record Seen(long day, HeatStage stage) {}

	private record Key(String table, UUID player) {}

	private final Map<Key, Seen> seen = new HashMap<>();

	/**
	 * Notices due now at {@code tableKey}, in the order of {@code stages} (seated humans → today's stage).
	 * Records them as told.
	 */
	public List<Notice> due(String tableKey, String gameId, boolean houseMoney, Map<UUID, HeatStage> stages, long day) {
		List<Notice> out = new ArrayList<>();
		if (!houseMoney) {
			return out;
		}
		for (Map.Entry<UUID, HeatStage> e : stages.entrySet()) {
			HeatStage st = e.getValue();
			if (st == null || st == HeatStage.NONE || (st == HeatStage.HARD_ONLY && !"poker".equals(gameId))) {
				continue;
			}
			Key k = new Key(tableKey, e.getKey());
			Seen s = seen.get(k);
			HeatStage before = s == null || s.day() != day ? HeatStage.NONE : s.stage();
			if (st.ordinal() <= before.ordinal()) {
				continue;
			}
			seen.put(k, new Seen(day, st));
			out.add(st == HeatStage.SULKING
				? new Notice(e.getKey(), st, "msg.burmaldaholic.bots.sulking", "sulk")
				: new Notice(e.getKey(), st, "msg.burmaldaholic.bots.word_got_around", "word_got_around"));
		}
		return out;
	}

	/** Forgets what every table told about {@code player} (heat reset). */
	public void reset(UUID player) {
		seen.keySet().removeIf(k -> k.player().equals(player));
	}

	public void clear() {
		seen.clear();
	}
}
