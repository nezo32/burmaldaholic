package dev.nezo.burmaldaholic.multiplayer.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * All player-owned casinos and their tables (§18.2). PURE state + rules; persisted by
 * {@code multiplayer.MultiplayerData}. Server thread only.
 *
 * <ul>
 *   <li>Every table ever linked has a {@link TableRecord}; {@code casinoId == null} = inactive
 *       (the charter was removed; not a house table until re-linked).</li>
 *   <li>A new charter of the same owner re-links that owner's inactive tables inside the new claim.</li>
 * </ul>
 */
public final class CasinoBook {
	/** Block position key of a table. */
	public record TablePos(String dimension, int x, int y, int z) {
		public static Optional<TablePos> parse(String key) {
			int bar = key.lastIndexOf('|');
			if (bar <= 0) {
				return Optional.empty();
			}
			String[] parts = key.substring(bar + 1).split(",");
			if (parts.length != 3) {
				return Optional.empty();
			}
			try {
				return Optional.of(new TablePos(key.substring(0, bar), Integer.parseInt(parts[0].trim()),
					Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())));
			} catch (NumberFormatException e) {
				return Optional.empty();
			}
		}

		public String key() {
			return dimension + "|" + x + "," + y + "," + z;
		}
	}

	/** A player casino (charter at x/y/z). */
	public static final class Casino implements Claims.Claim {
		public final String id;
		public final UUID owner;
		public String ownerName;
		public final String dimension;
		public final int x;
		public final int y;
		public final int z;
		public final int radius;
		public final long created;
		/** Insolvent (§18.2): the bankroll cannot cover the cheapest open table. */
		public boolean broke;
		public final CasinoStats stats = new CasinoStats();

		public Casino(String id, UUID owner, String ownerName, String dimension, int x, int y, int z, int radius, long created) {
			this.id = id;
			this.owner = owner;
			this.ownerName = ownerName;
			this.dimension = dimension;
			this.x = x;
			this.y = y;
			this.z = z;
			this.radius = radius;
			this.created = created;
		}

		@Override
		public String dimension() {
			return dimension;
		}

		@Override
		public int x() {
			return x;
		}

		@Override
		public int z() {
			return z;
		}

		@Override
		public int radius() {
			return radius;
		}

		@Override
		public UUID owner() {
			return owner;
		}

		public boolean isCharterAt(String dim, int bx, int by, int bz) {
			return dimension.equals(dim) && x == bx && y == by && z == bz;
		}

		public String bankrollId() {
			return Claims.bankrollId(id);
		}
	}

	/** Owner settings of a linked table. {@code min/max} 0 = the game's own limits. */
	public static final class TableRecord {
		/** null = inactive. */
		public String casinoId;
		public UUID owner;
		public String game;
		public String tableName;
		/** Block description key for the charter screen, e.g. {@code block.burmaldaholic.blackjack_table}. */
		public String descriptionId;
		public boolean open = true;
		public long min;
		public long max;
		public boolean bots = true;
		/** Last known game minimum bet (insolvency rule); 1 until observed. */
		public long gameMin = 1;

		public TableRecord(String casinoId, UUID owner, String game, String tableName, String descriptionId) {
			this.casinoId = casinoId;
			this.owner = owner;
			this.game = game;
			this.tableName = tableName;
			this.descriptionId = descriptionId;
		}

		public Solvency.ExposedTable exposure() {
			return new Solvency.ExposedTable(game, tableName, OwnerLimits.effectiveMin(gameMin, min), open);
		}
	}

	private final Map<String, Casino> casinos = new LinkedHashMap<>();
	private final Map<String, TableRecord> tables = new HashMap<>();
	/** Casinos whose charter was removed: bankroll paid out once reservations are released. id → owner. */
	private final Map<String, UUID> closing = new LinkedHashMap<>();
	private int counter;

	public Collection<Casino> casinos() {
		return casinos.values();
	}

	public Optional<Casino> casino(String id) {
		return id == null ? Optional.empty() : Optional.ofNullable(casinos.get(id));
	}

	public List<Casino> ownedBy(UUID owner) {
		return casinos.values().stream().filter(c -> c.owner.equals(owner)).toList();
	}

	public Optional<Casino> casinoAt(String dimension, int x, int z) {
		return Claims.claimAt(casinos.values(), dimension, x, z);
	}

	public Optional<Casino> charterAt(String dimension, int x, int y, int z) {
		return casinos.values().stream().filter(c -> c.isCharterAt(dimension, x, y, z)).findFirst();
	}

	public Map<String, TableRecord> tables() {
		return tables;
	}

	public Optional<TableRecord> table(String key) {
		return Optional.ofNullable(tables.get(key));
	}

	/** The live casino a table is linked to (empty for unknown or inactive tables). */
	public Optional<Casino> activeCasinoOf(String tableKey) {
		TableRecord t = tables.get(tableKey);
		return t == null ? Optional.empty() : casino(t.casinoId);
	}

	/** Linked tables of a casino, ordered by key (stable list for the charter screen). */
	public List<Map.Entry<String, TableRecord>> tablesOf(String casinoId) {
		List<Map.Entry<String, TableRecord>> out = new ArrayList<>();
		tables.entrySet().stream().filter(e -> casinoId.equals(e.getValue().casinoId))
			.sorted(Map.Entry.comparingByKey()).forEach(out::add);
		return out;
	}

	public List<Solvency.ExposedTable> exposure(String casinoId) {
		return tablesOf(casinoId).stream().map(e -> e.getValue().exposure()).toList();
	}

	public Map<String, UUID> closing() {
		return closing;
	}

	public int counter() {
		return counter;
	}

	public Casino create(UUID owner, String ownerName, String dimension, int x, int y, int z, int radius, long created) {
		String id = Claims.nextId(counter);
		counter++;
		while (casinos.containsKey(id)) {
			id = Claims.nextId(counter);
			counter++;
		}
		Casino c = new Casino(id, owner, ownerName, dimension, x, y, z, radius, created);
		casinos.put(id, c);
		return c;
	}

	/** Loading: restores a casino/table/counter as saved. */
	public void restore(Casino c) {
		casinos.put(c.id, c);
	}

	public void restore(String key, TableRecord t) {
		tables.put(key, t);
	}

	public void restoreCounter(int value) {
		counter = Math.max(counter, value);
	}

	/**
	 * Links a table to a casino. Refuses tables linked to another live casino and inactive tables of
	 * another owner (they stay inactive — no stealing).
	 * @return true if the link changed
	 */
	public boolean link(Casino c, String key, String game, String tableName, String descriptionId) {
		TableRecord cur = tables.get(key);
		if (cur != null) {
			if (c.id.equals(cur.casinoId)) {
				return false;
			}
			if (casino(cur.casinoId).isPresent()) {
				return false;
			}
			if (!cur.owner.equals(c.owner)) {
				return false;
			}
			cur.casinoId = c.id;
			cur.game = game;
			cur.tableName = tableName;
			cur.descriptionId = descriptionId;
			return true;
		}
		tables.put(key, new TableRecord(c.id, c.owner, game, tableName, descriptionId));
		return true;
	}

	/** Re-links the owner's inactive tables that lie inside the claim. @return how many */
	public int relinkInactive(Casino c) {
		int n = 0;
		for (Map.Entry<String, TableRecord> e : tables.entrySet()) {
			TableRecord t = e.getValue();
			if (t.casinoId != null || !t.owner.equals(c.owner)) {
				continue;
			}
			Optional<TablePos> pos = TablePos.parse(e.getKey());
			if (pos.isPresent() && Claims.inClaim(c, pos.get().dimension(), pos.get().x(), pos.get().z())) {
				t.casinoId = c.id;
				n++;
			}
		}
		return n;
	}

	/** Table block gone: forget it. */
	public Optional<TableRecord> removeTable(String key) {
		return Optional.ofNullable(tables.remove(key));
	}

	/** Charter removed: tables go inactive, the bankroll is queued for payout. */
	public Optional<Casino> close(String casinoId) {
		Casino c = casinos.remove(casinoId);
		if (c == null) {
			return Optional.empty();
		}
		for (TableRecord t : tables.values()) {
			if (casinoId.equals(t.casinoId)) {
				t.casinoId = null;
			}
		}
		closing.put(casinoId, c.owner);
		return Optional.of(c);
	}
}
