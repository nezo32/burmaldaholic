package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.v2.BetGates;
import dev.nezo.burmaldaholic.core.ui.v2.BetView;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.Gate;
import dev.nezo.burmaldaholic.core.ui.v2.KeyMap;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The same bet bar at every table (docs/architecture/ui-v2.md §4.2, §5.2): the chip tray (≤ 5 denominations from
 * {@link BetView#chips}; ids {@code chip.0..4}), the tools Undo / Clear / Rebet / ×2 (ids {@code tool.<id>}) and the bet
 * readout ({@code readout}: "Bet" + the 2× value).
 *
 * <p>Single-spot games: a chip click adds the chip to the bet. Multi-spot games ({@link #multiSpot}): a chip click arms
 * the chip, the felt spots light, a felt click places it ({@link #armOrDefault}: with no armed chip the last used or the
 * smallest affordable one — never nothing). Right click on a chip = remove one. Disabled chips / tools stay visible and
 * say why when clicked.
 */
public final class BetBar {
	private BetView view = BetView.builder().build();
	private @Nullable BetPort port;
	private boolean multiSpot;
	private String[] tools = BetGates.TOOLS.clone();
	private long[] chips = new long[0];
	private Gate[] chipGates = new Gate[0];
	private Gate[] toolGates = new Gate[0];
	private long selected = -1;
	/** A typed amount a felt click places (bet-entry control, multi-spot games; 0 = the selected chip). */
	private long unit;
	private boolean armed;
	private boolean visible;
	// layout
	private List<Box> chipBoxes = List.of();
	private int firstChip;
	private List<Box> toolBoxes = List.of();
	private Box readout = Box.EMPTY;
	private boolean compactReadout;
	private Tier tier = Tier.T2;
	java.util.function.ToIntFunction<String> shake = id -> 0;

	/** Binds the bet model and the port (call on every state change). */
	public void bind(BetView view, BetPort port) {
		this.view = view;
		this.port = port;
		this.chips = view.chips();
		if (selected < 0 || Arrays.stream(chips).noneMatch(c -> c == selected)) {
			selected = chips.length == 0 ? -1 : chips[Math.min(chips.length - 1, chips.length > 2 ? 2 : 0)];
			if (multiSpot) armed = false;
		}
	}

	/** Multi-spot game: chips arm, the felt places (default false: chips add to the single bet). */
	public void multiSpot(boolean on) {
		this.multiSpot = on;
	}

	public boolean isMultiSpot() {
		return multiSpot;
	}

	/** The tools this game offers, in the fixed order (subset of Undo, Clear, Rebet, ×2). */
	public void tools(String... ids) {
		List<String> keep = new ArrayList<>();
		for (String t : BetGates.TOOLS) if (Arrays.asList(ids).contains(t)) keep.add(t);
		tools = keep.toArray(String[]::new);
	}

	public String[] toolIds() {
		return tools.clone();
	}

	void gates(Gate[] chipGates, Gate[] toolGates) {
		this.chipGates = chipGates;
		this.toolGates = toolGates;
	}

	void visible(boolean v) {
		visible = v;
	}

	public boolean visible() {
		return visible;
	}

	public BetView view() {
		return view;
	}

	public @Nullable BetPort port() {
		return port;
	}

	/** Offered denominations (ascending). */
	public long[] chips() {
		return chips.clone();
	}

	/** What a felt click places: the typed {@link #unit} when set, else the selected chip. */
	public long selected() {
		return unit > 0 ? unit : selected;
	}

	public boolean armed() {
		return multiSpot && armed;
	}

	public void select(long denom, boolean arm) {
		selected = denom;
		unit = 0;
		armed = arm;
	}

	/** Sets the typed unit a felt click places (0 = back to the chips); arms it in multi-spot games. */
	public void unit(long amount) {
		unit = Math.max(0, amount);
		if (unit > 0) armed = true;
	}

	/** The typed unit (0 = none). */
	public long unit() {
		return unit;
	}

	public void disarm() {
		armed = false;
	}

	/** The chip a felt click places: the armed one, else the last used, else the smallest affordable (§5.2-4); -1 = none. */
	public long armOrDefault() {
		if (unit > 0 && unit <= view.balance()) {
			armed = true;
			return unit;
		}
		long c = BetGates.armDefault(chips, view.min(), view.balance(), selected);
		if (c > 0) {
			selected = c;
			armed = true;
		}
		return c;
	}

	/** Index of the selected chip among {@link #chips()} (for the Dock's visible window), or 0. */
	public int selectedIndex() {
		for (int i = 0; i < chips.length; i++) if (chips[i] == selected) return i;
		return 0;
	}

	public Gate chipGate(int index) {
		return index >= 0 && index < chipGates.length ? chipGates[index] : Gate.off(BetGates.CHIP, V2Keys.CHIP, V2Keys.REASON_ROUND_IN_PROGRESS);
	}

	public Gate toolGate(String id) {
		for (int i = 0; i < tools.length && i < toolGates.length; i++) if (tools[i].equals(id)) return toolGates[i];
		return Gate.off(id, V2Keys.TOOL_UNDO, V2Keys.REASON_ROUND_IN_PROGRESS);
	}

	/** Readout widths: {"Bet" / 2× value} and the 1× compact one. */
	public int[] measureReadout(Font font) {
		Component cap = caption();
		Component v = Texts.number(Math.max(view.myTotal(), 9999));
		int full = Math.max(font.width(cap), 2 * font.width(v)) + 4;
		int compact = Math.max(font.width(cap), font.width(v)) + 4;
		return new int[] {full, compact};
	}

	private Component caption() {
		// multi-spot: "On table" (not "15 spots", which read as the label of the value; DES roulette P2)
		return Component.translatable(multiSpot && !view.spots().isEmpty() ? V2Keys.ON_TABLE : V2Keys.BET);
	}

	void place(Tier tier, List<Box> chipBoxes, int firstChip, List<Box> toolBoxes, Box readout, boolean compactReadout) {
		this.tier = tier;
		this.chipBoxes = List.copyOf(chipBoxes);
		this.firstChip = firstChip;
		this.toolBoxes = List.copyOf(toolBoxes);
		this.readout = readout;
		this.compactReadout = compactReadout;
	}

	/** HitMap id of a visible chip box {@code i} → denomination index {@code firstChip + i}. */
	public int firstChip() {
		return firstChip;
	}

	public List<Box> chipBoxes() {
		return chipBoxes;
	}

	public List<Box> toolBoxes() {
		return toolBoxes;
	}

	public Box readout() {
		return readout;
	}

	/** Registers chips, tools and the readout. */
	void hits(dev.nezo.burmaldaholic.core.ui.v2.HitMap hits) {
		for (int i = 0; i < chipBoxes.size(); i++) {
			int d = firstChip + i;
			hits.add(KeyMap.Ids.chip(d), chipBoxes.get(i), chipGate(d).enabled());
		}
		for (int i = 0; i < toolBoxes.size() && i < tools.length; i++) {
			hits.add(KeyMap.Ids.tool(tools[i]), toolBoxes.get(i), toolGate(tools[i]).enabled());
		}
		hits.add(KeyMap.Ids.READOUT, readout, true);
	}

	public void draw(GuiGraphicsExtractor g, Font font, int mx, int my, String focusedId, @Nullable AckState pending) {
		if (!visible) return;
		for (int i = 0; i < chipBoxes.size(); i++) {
			int d = firstChip + i;
			if (d >= chips.length) break;
			Box b = chipBoxes.get(i).offset(shake.applyAsInt(KeyMap.Ids.chip(d)), 0);
			long denom = chips[d];
			boolean on = chipGate(d).enabled();
			boolean sel = denom == selected;
			boolean hover = b.contains(mx, my);
			Box cb = sel ? b.offset(0, -2) : hover && on ? b.offset(0, -1) : b;
			chip(g, font, cb, denom, on);
			if (sel) {
				Box ring = cb.inset(-2);
				if (!V2Draw.sprite(g, V2Sprites.chipRing(cb.w()), ring, 0xFFFFFFFF)) {
					FeltPrint.ring(g, ring, CasinoPalette.V2_GOLD);
				}
				g.fill(b.x(), b.bottom() + 1, b.right(), b.bottom() + 2, CasinoPalette.V2_GOLD);
			}
			if (KeyMap.Ids.chip(d).equals(focusedId)) V2Draw.outline(g, b.inset(-3), CasinoPalette.V2_GOLD);
		}
		for (int i = 0; i < toolBoxes.size() && i < tools.length; i++) {
			Box b = toolBoxes.get(i).offset(shake.applyAsInt(KeyMap.Ids.tool(tools[i])), 0);
			boolean on = toolGate(tools[i]).enabled();
			boolean hover = b.contains(mx, my);
			if (!V2Draw.sprite(g, V2Sprites.ButtonStyle.ICON.sprite(on, hover, false), b, 0xFFFFFFFF)) {
				V2Button.flat(g, b, V2Sprites.ButtonStyle.ICON, on, hover);
			}
			int gs = V2Sprites.iconSize(b.w());
			Box gl = b.centred(gs, gs).offset(0, -1);
			int c = on ? CasinoPalette.V2_TEXT : 0xFF6A5A7C;
			if (!V2Draw.sprite(g, V2Sprites.icon(tools[i], gs), gl, on ? 0xFFFFFFFF : 0x60FFFFFF)) Glyphs.draw(g, tools[i], gl, c);
			if (KeyMap.Ids.tool(tools[i]).equals(focusedId)) V2Draw.outline(g, b.inset(-1), CasinoPalette.V2_GOLD);
		}
		if (!readout.isEmpty()) {
			Component cap = caption();
			V2Draw.text(g, font, cap, readout.x(), readout.y(), 1, CasinoPalette.V2_MUTED, true);
			long shown = view.myTotal() + (pending != null && pending.spot().isEmpty() ? pending.amount() : 0);
			Component v = Texts.number(shown);
			int scale = compactReadout ? 1 : 2;
			int vy = readout.y() + 10;
			int color = pending != null && pending.spot().isEmpty() && pending.amount() > 0 ? V2Draw.alpha(CasinoPalette.V2_GOLD, 0.6f)
				: shown > 0 ? CasinoPalette.V2_GOLD : CasinoPalette.V2_MUTED;
			V2Draw.text(g, font, v, readout.x(), vy, scale, color, true);
		}
	}

	/** A tray chip (sprite with the value printed, or the flat fallback with the value as text). */
	public static void chip(GuiGraphicsExtractor g, Font font, Box b, long denom, boolean enabled) {
		if (V2Draw.sprite(g, V2Sprites.chip(denom, b.w()), b, enabled ? 0xFFFFFFFF : 0x70FFFFFF)) return;
		int size = b.w();
		ChipStack.disc(g, b.x(), b.y(), size, denom, enabled ? 1f : 0.45f);
		Component label = Texts.number(denom);
		int tc = denom >= 1000 || denom == 1 ? CasinoPalette.V2_INK : CasinoPalette.V2_TEXT;
		if (font.width(label) <= size - 4) V2Draw.centred(g, font, label, b, 1, enabled ? tc : V2Draw.alpha(tc, 0.5f), tc != CasinoPalette.V2_INK);
	}

	/** What the watchdog has pending (drawn as a 60 % chip / readout). */
	public record AckState(String action, String spot, long amount) {}
}
