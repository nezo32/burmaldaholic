package dev.nezo.burmaldaholic.lastchance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persistent per-player Last Chance record (player attachment, copied on death).
 *
 * @param used       a flip has happened at least once
 * @param usedAt     world time (overworld game time) of the last flip
 * @param notified   the "recharged" message was already sent for this flip
 * @param scarHp     permanent max-health HP removed by High-Stakes saves
 */
public record LastChanceState(boolean used, long usedAt, boolean notified, int scarHp) {
	public static final LastChanceState EMPTY = new LastChanceState(false, 0, false, 0);

	public static final Codec<LastChanceState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.BOOL.optionalFieldOf("used", false).forGetter(LastChanceState::used),
		Codec.LONG.optionalFieldOf("used_at", 0L).forGetter(LastChanceState::usedAt),
		Codec.BOOL.optionalFieldOf("notified", false).forGetter(LastChanceState::notified),
		Codec.INT.optionalFieldOf("scar_hp", 0).forGetter(LastChanceState::scarHp)
	).apply(i, LastChanceState::new));

	public LastChanceState flipped(long now) {
		return new LastChanceState(true, now, false, scarHp);
	}

	public LastChanceState withNotified() {
		return new LastChanceState(used, usedAt, true, scarHp);
	}

	public LastChanceState withScar(int hp) {
		return new LastChanceState(used, usedAt, notified, Math.max(0, hp));
	}

	public LastChanceState cooldownReset() {
		return new LastChanceState(false, 0, false, scarHp);
	}
}
