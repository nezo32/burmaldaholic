package dev.nezo.burmaldaholic.games.extras.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pending PvP Dice Duel challenges (GAME_DESIGN.md §11.5). In memory: nothing is escrowed until a
 * challenge is accepted, so a restart only forgets pending invitations. Max one live OUTGOING challenge
 * per player. Times are world ticks. Pure Java, not thread-safe (server thread).
 */
public final class ChallengeBook {
	public record Challenge(int id, UUID from, UUID to, long stake, long expires) {}

	public enum Error {
		SELF, ALREADY_PENDING
	}

	/** Either a new challenge or the reason it was refused. */
	public record Created(Challenge challenge, Error error) {
		public boolean ok() {
			return challenge != null;
		}
	}

	private final List<Challenge> list = new ArrayList<>();
	private int seq;

	public Created create(UUID from, UUID to, long stake, long now, long timeoutTicks) {
		if (from.equals(to)) {
			return new Created(null, Error.SELF);
		}
		if (outgoing(from, now).isPresent()) {
			return new Created(null, Error.ALREADY_PENDING);
		}
		Challenge c = new Challenge(++seq, from, to, stake, now + timeoutTicks);
		list.add(c);
		return new Created(c, null);
	}

	public Optional<Challenge> outgoing(UUID from, long now) {
		return list.stream().filter(c -> c.from().equals(from) && c.expires() > now).findFirst();
	}

	/** Live challenges addressed to a player, oldest first. */
	public List<Challenge> incoming(UUID to, long now) {
		return list.stream().filter(c -> c.to().equals(to) && c.expires() > now).toList();
	}

	/** Removes and returns a live challenge (accept / decline); empty if unknown or expired. */
	public Optional<Challenge> take(int id, long now) {
		for (Iterator<Challenge> it = list.iterator(); it.hasNext();) {
			Challenge c = it.next();
			if (c.id() == id) {
				it.remove();
				return c.expires() > now ? Optional.of(c) : Optional.empty();
			}
		}
		return Optional.empty();
	}

	/** Removes and returns expired challenges. */
	public List<Challenge> expire(long now) {
		List<Challenge> out = new ArrayList<>();
		for (Iterator<Challenge> it = list.iterator(); it.hasNext();) {
			Challenge c = it.next();
			if (c.expires() <= now) {
				it.remove();
				out.add(c);
			}
		}
		return out;
	}

	/** Removes every challenge involving a player (disconnect). */
	public List<Challenge> dropPlayer(UUID id) {
		List<Challenge> out = new ArrayList<>();
		for (Iterator<Challenge> it = list.iterator(); it.hasNext();) {
			Challenge c = it.next();
			if (c.from().equals(id) || c.to().equals(id)) {
				it.remove();
				out.add(c);
			}
		}
		return out;
	}

	public int size() {
		return list.size();
	}

	public void clear() {
		list.clear();
	}
}
