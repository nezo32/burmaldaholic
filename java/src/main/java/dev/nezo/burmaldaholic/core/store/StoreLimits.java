package dev.nezo.burmaldaholic.core.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-player store purchase limits (EC-SPEC §5: player attachment {@code burmaldaholic:store_limits}, persistent, kept
 * on death). Pure: the attachment holds a {@link Data}; {@link Data#left} / {@link Data#record} are the rules.
 *
 * <p>{@code limitEveryDays = 1}: {@code limitPerDay} purchases per in-game day. {@code limitEveryDays > 1}: up to
 * {@code limitPerDay} on a buy day, then nothing until {@code limitEveryDays} days after that day.
 */
public final class StoreLimits {
	private StoreLimits() {}

	/**
	 * @param day          the in-game day {@code boughtToday} counts
	 * @param boughtToday  purchases per offer id on {@code day}
	 * @param lastBuyDay   last day each offer was bought
	 */
	public record Data(long day, Map<String, Integer> boughtToday, Map<String, Long> lastBuyDay) {
		public static final Data EMPTY = new Data(-1, Map.of(), Map.of());

		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Data::day),
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("boughtToday").forGetter(Data::boughtToday),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("lastBuyDay").forGetter(Data::lastBuyDay)).apply(i, Data::new));

		/** Purchases of {@code id} still allowed on {@code today}; -1 = unlimited. */
		public long left(String id, long today, int limitPerDay, int limitEveryDays) {
			if (limitPerDay <= 0) return -1;
			int boughtOnToday = day == today ? boughtToday.getOrDefault(id, 0) : 0;
			Long last = lastBuyDay.get(id);
			if (limitEveryDays > 1 && last != null && last != today && today - last < limitEveryDays && today >= last) return 0;
			return Math.max(0, limitPerDay - boughtOnToday);
		}

		/** After buying {@code n} of {@code id} on {@code today}. */
		public Data record(String id, long today, int n) {
			Map<String, Integer> bought = new HashMap<>(day == today ? boughtToday : Map.of());
			bought.merge(id, n, Integer::sum);
			Map<String, Long> last = new HashMap<>(lastBuyDay);
			last.put(id, today);
			return new Data(today, Map.copyOf(bought), Map.copyOf(last));
		}
	}
}
