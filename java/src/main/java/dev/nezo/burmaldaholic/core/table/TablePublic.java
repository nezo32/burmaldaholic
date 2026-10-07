package dev.nezo.burmaldaholic.core.table;

import dev.nezo.burmaldaholic.core.bots.logic.SeatOccupant;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The public seat header of a table (docs/architecture/ui-v2.md §7.2, D12): who sits where, for the in-world seat
 * plates every passer-by sees. Travels in the block entity's update tag under {@link #KEY} (written by
 * {@link CasinoTableBlockEntity#writePublic}); nothing private (no stakes, no hole cards). Humans carry their name,
 * bots their name translation key (rendered through {@code gui.burmaldaholic.bots.display}'s inner name only — the
 * plate itself marks bots).
 */
public final class TablePublic {
	/** Update-tag key of the seat header. */
	public static final String KEY = "burmaldaholic_seats";
	/** Longest name kept (vanilla names are ≤ 16). */
	public static final int NAME_MAX = 16;

	/**
	 * One occupied seat.
	 *
	 * @param uuid the player's id; null for a bot
	 * @param name the player's name, or a bot's name translation key
	 */
	public record SeatPub(int index, @Nullable UUID uuid, String name, boolean bot) {
		/** The name to show: the player's name, or the bot's translated name. */
		public Component displayName() {
			return bot ? Component.translatable(name) : Texts.raw(name);
		}

		public boolean is(@Nullable UUID player) {
			return uuid != null && uuid.equals(player);
		}
	}

	private TablePublic() {}

	/** The seat header from the base seat model. */
	public static List<SeatPub> of(TableSeats seats) {
		List<SeatPub> out = new ArrayList<>();
		for (TableSeats.Seat s : seats.occupied()) out.add(new SeatPub(s.index(), s.player(), clip(s.name()), false));
		return out;
	}

	/** The seat header from a bot-aware seat model ({@code BotTable.occupants()}: seat order, null = empty). */
	public static List<SeatPub> ofOccupants(List<? extends @Nullable SeatOccupant> occupants) {
		List<SeatPub> out = new ArrayList<>();
		for (int i = 0; i < occupants.size(); i++) {
			SeatOccupant o = occupants.get(i);
			if (o == null) continue;
			if (o instanceof SeatOccupant.Human h) out.add(new SeatPub(i, h.id(), clip(h.name()), false));
			else out.add(new SeatPub(i, null, o.name(), true));
		}
		return out;
	}

	/** Writes the header ({@code size} seats) into {@code tag} under {@link #KEY}. */
	public static void writeSeats(CompoundTag tag, int size, List<SeatPub> seats) {
		CompoundTag t = new CompoundTag();
		t.putInt("n", size);
		ListTag list = new ListTag();
		for (SeatPub s : seats) {
			CompoundTag e = new CompoundTag();
			e.putInt("i", s.index());
			if (s.uuid() != null) {
				e.putLong("m", s.uuid().getMostSignificantBits());
				e.putLong("l", s.uuid().getLeastSignificantBits());
			}
			e.putString("n", s.name());
			if (s.bot()) e.putBoolean("b", true);
			list.add(e);
		}
		t.put("s", list);
		tag.put(KEY, t);
	}

	/** Convenience: the base seat model. */
	public static void writeSeats(CompoundTag tag, TableSeats seats) {
		writeSeats(tag, seats.size(), of(seats));
	}

	/** Seat count of a header ({@code 0} when the tag has none). */
	public static int seatCount(CompoundTag tag) {
		return tag.getCompound(KEY).map(t -> t.getIntOr("n", 0)).orElse(0);
	}

	/**
	 * Reads the header: an array indexed by seat ({@code length} = the seat count; null = empty seat). An absent
	 * header reads as an empty array.
	 */
	public static @Nullable SeatPub[] readSeats(CompoundTag tag) {
		CompoundTag t = tag.getCompound(KEY).orElse(null);
		if (t == null) return new SeatPub[0];
		int n = Math.max(0, Math.min(16, t.getIntOr("n", 0)));
		SeatPub[] out = new SeatPub[n];
		ListTag list = t.getListOrEmpty("s");
		for (int k = 0; k < list.size(); k++) {
			CompoundTag e = list.getCompoundOrEmpty(k);
			int i = e.getIntOr("i", -1);
			if (i < 0 || i >= n) continue;
			UUID id = e.contains("m") ? new UUID(e.getLongOr("m", 0), e.getLongOr("l", 0)) : null;
			out[i] = new SeatPub(i, id, e.getStringOr("n", ""), e.getBooleanOr("b", false));
		}
		return out;
	}

	private static String clip(String name) {
		return name.length() <= NAME_MAX ? name : name.substring(0, NAME_MAX);
	}
}
