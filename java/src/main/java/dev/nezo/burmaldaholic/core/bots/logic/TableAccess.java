package dev.nezo.burmaldaholic.core.bots.logic;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Private tables (BOTS.md §2.5). {@code guests} = keeper/owner invites saved with the table;
 * {@code sessionInvites} = the session host's invites (cleared when the session ends). Mutable, server thread.
 */
public final class TableAccess {
	private boolean privateTable;
	private final Set<UUID> guests = new LinkedHashSet<>();
	private final Set<UUID> sessionInvites = new LinkedHashSet<>();

	public boolean isPrivate() {
		return privateTable;
	}

	/** Switching on adds every currently seated human to the session invites (nobody is locked out). */
	public void setPrivate(boolean on, Iterable<UUID> seatedHumans) {
		if (on && !privateTable) {
			seatedHumans.forEach(sessionInvites::add);
		}
		privateTable = on;
	}

	public boolean mayJoin(UUID player, boolean operator) {
		return !privateTable || operator || guests.contains(player) || sessionInvites.contains(player);
	}

	/** @return false if the list is full ({@code bots.private.maxInvites}) */
	public boolean invite(UUID player, boolean saved, int maxInvites) {
		Set<UUID> target = saved ? guests : sessionInvites;
		if (guests.size() + sessionInvites.size() >= maxInvites && !target.contains(player)) {
			return false;
		}
		target.add(player);
		return true;
	}

	public void uninvite(UUID player) {
		guests.remove(player);
		sessionInvites.remove(player);
	}

	public Set<UUID> guests() {
		return Set.copyOf(guests);
	}

	public Set<UUID> sessionInvites() {
		return Set.copyOf(sessionInvites);
	}

	/** Session over: host invites expire, saved guests stay. */
	public void endSession() {
		sessionInvites.clear();
	}
}
