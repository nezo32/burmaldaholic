package dev.nezo.burmaldaholic.bots.logic;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Who invited whom to which private table (for the {@code members_only} advancement, BOTS.md §10). The
 * access list itself lives in core's {@code TableAccess}; this only remembers the inviter. In memory
 * (a session lasts while its humans play). Pure.
 */
public final class InviteBook {
	private final Map<String, Map<UUID, UUID>> byTable = new HashMap<>();

	public void record(String tableKey, UUID inviter, UUID invitee) {
		if (inviter.equals(invitee)) {
			return;
		}
		byTable.computeIfAbsent(tableKey, k -> new LinkedHashMap<>()).put(invitee, inviter);
	}

	public void remove(String tableKey, UUID invitee) {
		Map<UUID, UUID> m = byTable.get(tableKey);
		if (m != null) {
			m.remove(invitee);
			if (m.isEmpty()) {
				byTable.remove(tableKey);
			}
		}
	}

	public UUID inviterOf(String tableKey, UUID invitee) {
		Map<UUID, UUID> m = byTable.get(tableKey);
		return m == null ? null : m.get(invitee);
	}

	public Set<UUID> invitedBy(String tableKey, UUID inviter) {
		Map<UUID, UUID> m = byTable.get(tableKey);
		if (m == null) {
			return Set.of();
		}
		return m.entrySet().stream().filter(e -> e.getValue().equals(inviter)).map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * {@code members_only}: {@code player} settled a round at this private table while someone they invited
	 * is seated there ({@code seated}).
	 */
	public boolean playsWithInvitee(String tableKey, UUID player, Set<UUID> seated) {
		for (UUID invitee : invitedBy(tableKey, player)) {
			if (seated.contains(invitee)) {
				return true;
			}
		}
		return false;
	}

	public void clearTable(String tableKey) {
		byTable.remove(tableKey);
	}
}
