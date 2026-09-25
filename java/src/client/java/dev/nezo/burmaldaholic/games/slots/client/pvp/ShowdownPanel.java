package dev.nezo.burmaldaholic.games.slots.client.pvp;

import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Slot Showdown panel inside the slot machine screen (PVP.md §5.5 Java "Host set-up: panel on the machine
 * screen"): entry selector, spins toggle, seats / table size, [Open lobby]; nearby lobbies of the same tier
 * ([Join Showdown: 100 · 2/6]); while in a Showdown: [Start now] / [Leave lobby] / [Spin!]. Reads the
 * machine state's {@code pvp} tag ({@code SlotShowdownEntry.clientState}) and sends {@code pvp_*} table actions.
 * The machine screen hosts it through {@link Host} (one entry button + delegation while open).
 */
public final class ShowdownPanel {
	private static final int GOLD = 0xFFFFD24A;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int GRAY = 0xFFB0B0B0;
	private static final SeatPolicy[] POLICIES = {SeatPolicy.HUMANS_ONLY, SeatPolicy.MIXED, SeatPolicy.BOTS_ONLY};

	/** What the machine screen lends the panel (its protected helpers). */
	public interface Host {
		Button button(Component label, int x, int y, int minWidth, Button.OnPress onPress);

		void send(String action, CompoundTag args);

		CompoundTag state();

		void rebuild();

		Font font();
	}

	private final Host host;
	private boolean open;
	private long entry = -1;
	private int spins = -1;
	private int policy;
	private int size = -1;

	public ShowdownPanel(Host host) {
		this.host = host;
	}

	public boolean open() {
		return open;
	}

	private CompoundTag pvp() {
		return host.state().getCompoundOrEmpty("pvp");
	}

	private boolean enabled() {
		return pvp().getBooleanOr("enabled", false);
	}

	private CompoundTag mine() {
		CompoundTag m = pvp().getCompoundOrEmpty("mine");
		return "slots".equals(m.getStringOr("mode", "")) ? m : new CompoundTag();
	}

	/** The machine screen's entry button (hidden when the mode is off). */
	public void entryButton(int x, int y, int width) {
		if (!enabled()) {
			return;
		}
		ListTag lobbies = pvp().getListOrEmpty("lobbies");
		Component label = Component.translatable("gui.burmaldaholic.pvp.slots.title");
		if (!mine().isEmpty() && "DRAWN".equals(mine().getStringOr("state", ""))) {
			label = Component.translatable("gui.burmaldaholic.pvp.slots.spin_now");
		} else if (mine().isEmpty() && !lobbies.isEmpty()) {
			CompoundTag l = lobbies.getCompoundOrEmpty(0);
			label = join(l);
		}
		host.button(label, x, y, width, b -> {
			open = true;
			host.rebuild();
		});
	}

	private static Component join(CompoundTag l) {
		return Component.translatable("gui.burmaldaholic.pvp.slots.join", Texts.chips(l.getLongOr("entry", 0)), Texts.number(l.getIntOr("count", 0)),
			Texts.number(l.getIntOr("max", 0)));
	}

	private void normalize() {
		CompoundTag p = pvp();
		long min = p.getLongOr("min", 10);
		long max = Math.max(min, p.getLongOr("max", min));
		if (entry < min || entry > max) {
			entry = Math.max(min, Math.min(max, entry < 0 ? min : entry));
		}
		int[] choices = choices();
		if (spins < 0 || indexOf(choices, spins) < 0) {
			spins = p.getIntOr("spins", choices.length > 0 ? choices[(choices.length - 1) / 2] : 5);
		}
		int maxPlayers = Math.max(2, p.getIntOr("max_players", 6));
		if (size < 2 || size > maxPlayers) {
			size = maxPlayers;
		}
		if (!p.getBooleanOr("bots", false)) {
			policy = 0;
		}
	}

	private int[] choices() {
		int[] c = pvp().getIntArray("choices").orElse(new int[0]);
		return c.length == 0 ? new int[] {5} : c;
	}

	private static int indexOf(int[] a, int v) {
		for (int i = 0; i < a.length; i++) {
			if (a[i] == v) {
				return i;
			}
		}
		return -1;
	}

	/** Widgets while open: panel column at {@code px} (width {@code pw}); join buttons in the text column. */
	public void buildWidgets(int px, int pw, int textX, int textW, int bottom) {
		if (!enabled()) {
			open = false;
			host.rebuild();
			return;
		}
		normalize();
		CompoundTag mine = mine();
		if (!mine.isEmpty()) {
			String state = mine.getStringOr("state", "");
			if ("LOBBY".equals(state)) {
				if (mine.getBooleanOr("host", false)) {
					host.button(Component.translatable("gui.burmaldaholic.pvp.lobby.start"), px, 108, pw, b -> host.send("pvp_start", new CompoundTag()));
				}
				host.button(Component.translatable("gui.burmaldaholic.pvp.lobby.leave"), px, 132, pw, b -> host.send("pvp_leave", new CompoundTag()));
			} else if ("DRAWN".equals(state)) {
				host.button(Component.translatable("gui.burmaldaholic.pvp.slots.spin_now"), px, 108, pw, b -> host.send("pvp_spin", new CompoundTag()));
			}
			back(px, pw, bottom);
			return;
		}
		var minus = host.button(Texts.raw("−"), px, 46, 20, b -> step(-1)); // literal-ok: symbol
		var plus = host.button(Texts.raw("+"), px + 24, 46, 20, b -> step(1)); // literal-ok: symbol
		minus.active = entry > pvp().getLongOr("min", 10);
		plus.active = entry < pvp().getLongOr("max", 0);
		host.button(Component.translatable("gui.burmaldaholic.pvp.slots.spins_value", Texts.number(spins)), px, 70, pw, b -> {
			int[] c = choices();
			spins = c[(Math.max(0, indexOf(c, spins)) + 1) % c.length];
			host.rebuild();
		});
		int y = 94;
		if (pvp().getBooleanOr("bots", false)) {
			host.button(Component.translatable(POLICIES[policy].translationKey()), px, y, pw, b -> {
				policy = (policy + 1) % POLICIES.length;
				host.rebuild();
			});
			y += 24;
			if (POLICIES[policy] != SeatPolicy.HUMANS_ONLY) {
				host.button(Component.translatable("gui.burmaldaholic.pvp.bots.table_size_value", Texts.number(size)), px, y, pw, b -> {
					int max = Math.max(2, pvp().getIntOr("max_players", 6));
					size = size >= max ? 2 : size + 1;
					host.rebuild();
				});
				y += 24;
			}
		}
		var openLobby = host.button(Component.translatable(POLICIES[policy] == SeatPolicy.BOTS_ONLY ? "gui.burmaldaholic.pvp.bots.start_with_bots"
			: "gui.burmaldaholic.pvp.lobby.open"), px, y, pw, b -> {
				CompoundTag args = new CompoundTag();
				args.putLong("entry", entry);
				args.putInt("spins", spins);
				args.putString("policy", POLICIES[policy].id());
				args.putInt("size", size);
				host.send("pvp_open", args);
			});
		openLobby.active = pvp().getLongOr("max", 0) >= pvp().getLongOr("min", 10);
		ListTag lobbies = pvp().getListOrEmpty("lobbies");
		int ly = 44;
		for (int i = 0; i < lobbies.size(); i++) {
			CompoundTag l = lobbies.getCompoundOrEmpty(i);
			String id = l.getStringOr("id", "");
			host.button(join(l), textX, ly, textW, b -> {
				CompoundTag args = new CompoundTag();
				args.putString("id", id);
				host.send("pvp_join", args);
			});
			ly += 22;
		}
		back(px, pw, bottom);
	}

	private void back(int px, int pw, int bottom) {
		host.button(Component.translatable("gui.burmaldaholic.common.back"), px, bottom - 20, pw, b -> {
			open = false;
			host.rebuild();
		});
	}

	/** Entry −/+ with growing steps (as the line bet). */
	private void step(int dir) {
		long step = entry < 50 ? 5 : entry < 200 ? 10 : entry < 1000 ? 50 : 500;
		long min = pvp().getLongOr("min", 10);
		long max = Math.max(min, pvp().getLongOr("max", min));
		entry = Math.max(min, Math.min(max, entry + dir * step));
		host.rebuild();
	}

	/** Dark backdrop of the text column while open (absolute coordinates; drawn under the widgets). */
	public void drawBackground(GuiGraphicsExtractor g, int x, int y, int w, int bottom) {
		g.fill(x - 3, y - 3, x + w + 3, bottom + 3, 0xF0101010);
	}

	/** Labels while open: title + entry in the panel column, rules / lobby info in the text column. */
	public void drawLabels(GuiGraphicsExtractor g, int px, int textX, int textY, int textW, int bottom) {
		Font font = host.font();
		CompoundTag mine = mine();
		int y = textY;
		y = wrapped(g, font, Component.translatable("gui.burmaldaholic.pvp.slots.title"), textX, y, textW, GOLD);
		if (!mine.isEmpty()) {
			g.text(font, Component.translatable("gui.burmaldaholic.pvp.lobby.entry", Texts.chips(mine.getLongOr("entry", 0))), px, 34, TEXT, true);
			y = wrapped(g, font, Component.translatable("gui.burmaldaholic.pvp.lobby.players", Texts.number(mine.getIntOr("count", 0)),
				Texts.number(pvp().getIntOr("max_players", 6))), textX, y, textW, TEXT);
		} else {
			g.text(font, Component.translatable("gui.burmaldaholic.pvp.lobby.entry", Texts.chips(entry)), px, 34, TEXT, true);
			y += 22 * pvp().getListOrEmpty("lobbies").size();
		}
		List<Component> lines = new ArrayList<>();
		if (mine.isEmpty() && POLICIES[policy] == SeatPolicy.MIXED) {
			lines.add(Component.translatable("gui.burmaldaholic.pvp.bots.will_fill", Texts.number(size)));
		}
		if (mine.isEmpty() && POLICIES[policy] != SeatPolicy.HUMANS_ONLY) {
			lines.add(Component.translatable("gui.burmaldaholic.bots.luck_only"));
		}
		// the rules of the Showdown this server plays (ShowdownScoring on the frozen ShowdownTables; switches from the state)
		CompoundTag p = pvp();
		lines.add(Component.translatable("gui.burmaldaholic.pvp.slots.rules.1"));
		if (p.getBooleanOr("hot", true)) lines.add(Component.translatable("gui.burmaldaholic.pvp.slots.rules.2"));
		lines.add(Component.translatable("gui.burmaldaholic.pvp.slots.rules.3", Texts.number(p.getLongOr("pearl_points", 10)),
			Texts.number(p.getLongOr("clock_points", 50)), Texts.number(p.getIntOr("star", 500))));
		if (p.getBooleanOr("kaboom", true)) lines.add(Component.translatable("gui.burmaldaholic.pvp.slots.rules.4"));
		if (p.getBooleanOr("swap", true)) lines.add(Component.translatable("gui.burmaldaholic.pvp.slots.rules.5"));
		if (p.getBooleanOr("underdog", true)) lines.add(Component.translatable("gui.burmaldaholic.pvp.slots.rules.6"));
		lines.add(Component.translatable("gui.burmaldaholic.pvp.slots.rules.7"));
		lines.add(Component.translatable("gui.burmaldaholic.pvp.slots.tiebreak"));
		for (Component c : lines) {
			for (FormattedCharSequence l : font.split(c, textW)) {
				if (y + 9 > bottom) {
					return;
				}
				g.text(font, l, textX, y, GRAY, true);
				y += 10;
			}
			y += 2;
		}
	}

	private static int wrapped(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int width, int color) {
		for (FormattedCharSequence line : font.split(text, width)) {
			g.text(font, line, x, y, color, true);
			y += 10;
		}
		return y + 2;
	}
}
