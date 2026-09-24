package dev.nezo.burmaldaholic.core.bots;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * {@code <world>/data/burmaldaholic/bots.dat} (pvp-bots.md §5.4): per player {day, net} of winnings from
 * house-funded bots; per table key {day, buyIns}; and the chips BANKROLL-funded bots hold right now
 * ({@code escrow.<tableKey>|<botId> = {bankroll, amount}}), so a crash can always return them to the
 * owner's bankroll (§3.5). Old days are overwritten lazily.
 */
public final class BotLedgerData extends SavedData {
	public static final SavedDataType<BotLedgerData> TYPE = new SavedDataType<>(
		Burmaldaholic.id("bots"), BotLedgerData::new, CompoundTag.CODEC.xmap(BotLedgerData::load, BotLedgerData::save), null);

	private record DayCount(long day, long value) {}

	/** Chips a bankroll-funded bot holds (stack + bank escrow). */
	public record Escrow(String tableKey, String botId, String bankrollId, long amount) {
		String id() {
			return tableKey + "|" + botId;
		}
	}

	private final Map<UUID, DayCount> net = new HashMap<>();
	private final Map<String, DayCount> buyIns = new HashMap<>();
	private final Map<String, Escrow> escrow = new LinkedHashMap<>();
	/** Adaptive heat (BOTS.md §5.4): per player {hands, net in big blinds} of poker hands vs house bots (rolling). */
	private final Map<UUID, double[]> adaptive = new HashMap<>();

	/** One poker hand vs house bots, net in big blinds; above the window both totals are halved. */
	public void recordPokerHand(UUID player, double bbNet) {
		double[] a = adaptive.computeIfAbsent(player, k -> new double[2]);
		a[0] += 1;
		a[1] += bbNet;
		if (a[0] > dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath.ADAPTIVE_WINDOW) {
			a[0] = Math.floor(a[0] / 2);
			a[1] = a[1] / 2;
		}
		a[1] = Math.round(a[1] * 100) / 100.0;
		setDirty();
	}

	/** Adaptive heat is on for the player (&gt; +20 BB/100 over ≥ 200 hands). */
	public boolean adaptive(UUID player) {
		double[] a = adaptive.get(player);
		return a != null && dev.nezo.burmaldaholic.core.bots.logic.BotEconomyMath.adaptiveHot((long) a[0], a[1]);
	}

	public static BotLedgerData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public long netToday(UUID player, long day) {
		DayCount d = net.get(player);
		return d == null || d.day() != day ? 0 : d.value();
	}

	public void add(UUID player, long day, long delta) {
		net.put(player, new DayCount(day, Math.addExact(netToday(player, day), delta)));
		setDirty();
	}

	public void reset(UUID player) {
		boolean changed = net.remove(player) != null;
		changed |= adaptive.remove(player) != null;
		if (changed) {
			setDirty();
		}
	}

	public long buyInsToday(String tableKey, long day) {
		DayCount d = buyIns.get(tableKey);
		return d == null || d.day() != day ? 0 : d.value();
	}

	public void countBuyIn(String tableKey, long day) {
		buyIns.put(tableKey, new DayCount(day, buyInsToday(tableKey, day) + 1));
		setDirty();
	}

	/** Records / updates what a bankroll bot holds (amount ≤ 0 removes the entry). */
	public void putEscrow(String tableKey, String botId, String bankrollId, long amount) {
		Escrow e = new Escrow(tableKey, botId, bankrollId, amount);
		if (amount <= 0) {
			removeEscrow(tableKey, botId);
			return;
		}
		if (!e.equals(escrow.put(e.id(), e))) {
			setDirty();
		}
	}

	public Escrow removeEscrow(String tableKey, String botId) {
		Escrow e = escrow.remove(tableKey + "|" + botId);
		if (e != null) {
			setDirty();
		}
		return e;
	}

	public List<Escrow> escrows() {
		return List.copyOf(escrow.values());
	}

	public List<Escrow> escrowsOf(String tableKey) {
		return escrow.values().stream().filter(e -> e.tableKey().equals(tableKey)).toList();
	}

	private static BotLedgerData load(CompoundTag tag) {
		BotLedgerData d = new BotLedgerData();
		CompoundTag n = tag.getCompoundOrEmpty("net");
		for (String k : n.keySet()) {
			try {
				CompoundTag e = n.getCompoundOrEmpty(k);
				d.net.put(UUID.fromString(k), new DayCount(e.getLongOr("day", 0), e.getLongOr("value", 0)));
			} catch (IllegalArgumentException ignored) {
				// corrupt key
			}
		}
		CompoundTag b = tag.getCompoundOrEmpty("buyIns");
		for (String k : b.keySet()) {
			CompoundTag e = b.getCompoundOrEmpty(k);
			d.buyIns.put(k, new DayCount(e.getLongOr("day", 0), e.getLongOr("value", 0)));
		}
		CompoundTag ad = tag.getCompoundOrEmpty("adaptive");
		for (String k : ad.keySet()) {
			try {
				CompoundTag e = ad.getCompoundOrEmpty(k);
				d.adaptive.put(UUID.fromString(k), new double[] {e.getDoubleOr("hands", 0), e.getDoubleOr("net", 0)});
			} catch (IllegalArgumentException ignored) {
				// corrupt key
			}
		}
		CompoundTag es = tag.getCompoundOrEmpty("escrow");
		for (String k : es.keySet()) {
			CompoundTag e = es.getCompoundOrEmpty(k);
			Escrow x = new Escrow(e.getStringOr("table", ""), e.getStringOr("bot", ""), e.getStringOr("bankroll", ""), e.getLongOr("amount", 0));
			if (!x.bankrollId().isEmpty() && x.amount() > 0) {
				d.escrow.put(x.id(), x);
			}
		}
		return d;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		CompoundTag n = new CompoundTag();
		net.forEach((k, v) -> n.put(k.toString(), entry(v)));
		CompoundTag b = new CompoundTag();
		buyIns.forEach((k, v) -> b.put(k, entry(v)));
		CompoundTag es = new CompoundTag();
		escrow.forEach((k, v) -> {
			CompoundTag e = new CompoundTag();
			e.putString("table", v.tableKey());
			e.putString("bot", v.botId());
			e.putString("bankroll", v.bankrollId());
			e.putLong("amount", v.amount());
			es.put(k, e);
		});
		CompoundTag ad = new CompoundTag();
		adaptive.forEach((k, v) -> {
			CompoundTag e = new CompoundTag();
			e.putDouble("hands", v[0]);
			e.putDouble("net", v[1]);
			ad.put(k.toString(), e);
		});
		tag.put("adaptive", ad);
		tag.put("net", n);
		tag.put("buyIns", b);
		tag.put("escrow", es);
		tag.putInt("format", 1);
		return tag;
	}

	private static CompoundTag entry(DayCount d) {
		CompoundTag e = new CompoundTag();
		e.putLong("day", d.day());
		e.putLong("value", d.value());
		return e;
	}
}
