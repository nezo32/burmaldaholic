package dev.nezo.burmaldaholic.multiplayer.client;

import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.multiplayer.logic.OwnerLimits;
import dev.nezo.burmaldaholic.multiplayer.net.CharterActionPayload;
import dev.nezo.burmaldaholic.multiplayer.net.CharterStatePayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Casino Charter owner screen (UI.md §11, Java 320 × 220): tabs Overview · Tables · Bankroll · Stats.
 * Renders the server state only; every change is a {@link CharterActionPayload}. Buttons size to their
 * translated labels and flow into new rows, text wraps, so Russian (~1.45× longer) never truncates.
 */
public class CharterScreen extends Screen {
	private static final int W = 320;
	private static final int H = 220;
	private static final int PAD = 8;
	private static final int PANEL = 0xF0202830;
	private static final int BORDER = 0xFFC9A227;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFFBBBBBB;
	private static final int GOLD = 0xFFFFD700;
	private static final int GOOD = 0xFF55FF55;
	private static final int BAD = 0xFFFF5555;
	private static final int TABLES_PER_PAGE = 2;

	private enum Tab {
		OVERVIEW("gui.burmaldaholic.charter.overview"),
		TABLES("gui.burmaldaholic.charter.tables"),
		BANKROLL("gui.burmaldaholic.charter.bankroll"),
		STATS("gui.burmaldaholic.charter.stats");

		final String key;

		Tab(String key) {
			this.key = key;
		}
	}

	/** Unsaved edits of one table row (kept across state refreshes until saved). */
	private static final class Draft {
		boolean open;
		boolean bots;
		String min;
		String max;
	}

	private CompoundTag state;
	private Tab tab = Tab.OVERVIEW;
	private int page;
	private int left;
	private int top;
	private int flowX;
	private int flowY;
	private int contentY;
	private @Nullable Component message;
	private boolean messageError;
	private int messageTicks;
	private @Nullable EditBox amount;
	private String amountText = "";
	private final Map<String, Draft> drafts = new HashMap<>();
	private final Map<String, EditBox[]> rowBoxes = new HashMap<>();
	private final Map<String, Integer> rowLabelY = new HashMap<>();
	private @Nullable String savingKey;

	public CharterScreen(CharterStatePayload payload) {
		super(Component.translatable("gui.burmaldaholic.charter.title"));
		this.state = payload.state();
		payload.message().ifPresent(m -> showMessage(m, payload.error()));
	}

	/** New state from the server (after an action or a refresh). */
	public void accept(CharterStatePayload payload) {
		if (!payload.state().getStringOr("id", "").equals(casinoId())) {
			drafts.clear();
		}
		this.state = payload.state();
		if (savingKey != null && !payload.error()) {
			drafts.remove(savingKey);
		}
		savingKey = null;
		payload.message().ifPresent(m -> showMessage(m, payload.error()));
		if (minecraft != null) {
			rebuild();
		}
	}

	private void showMessage(Component m, boolean error) {
		message = m;
		messageError = error;
		messageTicks = 100;
	}

	private String casinoId() {
		return state.getStringOr("id", "");
	}

	private boolean isOwner() {
		return state.getBooleanOr("is_owner", false);
	}

	private void send(String action, CompoundTag args) {
		ClientPlayNetworking.send(new CharterActionPayload(casinoId(), action, args));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (messageTicks > 0 && --messageTicks == 0) {
			message = null;
		}
	}

	@Override
	protected void init() {
		rebuild();
	}

	// ---- layout ----------------------------------------------------------------------------------

	private void rebuild() {
		captureText();
		clearWidgets();
		rowBoxes.clear();
		rowLabelY.clear();
		amount = null;
		left = (width - W) / 2;
		top = Math.max(4, (height - H) / 2);
		flowX = PAD;
		flowY = 20;
		for (Tab t : Tab.values()) {
			if (t == Tab.BANKROLL && !isOwner()) {
				continue;
			}
			Button b = flow(Component.translatable(t.key), 40, x -> {
				tab = t;
				page = 0;
				send("refresh", new CompoundTag());
				rebuild();
			});
			b.active = t != tab;
		}
		newRow();
		contentY = flowY;
		switch (tab) {
			case OVERVIEW -> buildOverview();
			case TABLES -> buildTables();
			case BANKROLL -> buildBankroll();
			case STATS -> {
			}
		}
		Component close = Component.translatable("gui.burmaldaholic.common.close");
		int cw = Math.max(50, font.width(close) + 8);
		addRenderableWidget(Button.builder(close, b -> onClose()).bounds(left + W - PAD - cw, top + H - PAD - 20, cw, 20).build());
	}

	private void captureText() {
		if (amount != null) {
			amountText = amount.getValue();
		}
		rowBoxes.forEach((key, boxes) -> {
			Draft d = drafts.get(key);
			if (d != null) {
				d.min = boxes[0].getValue();
				d.max = boxes[1].getValue();
			}
		});
	}

	private Button flow(Component label, int minWidth, Button.OnPress onPress) {
		int w = Math.max(minWidth, font.width(label) + 8);
		if (flowX + w > W - PAD && flowX > PAD) {
			newRow();
		}
		Button b = addRenderableWidget(Button.builder(label, onPress).bounds(left + flowX, top + flowY, w, 20).build());
		flowX += w + 4;
		return b;
	}

	private EditBox flowBox(int w, Component hint, String value) {
		if (flowX + w > W - PAD && flowX > PAD) {
			newRow();
		}
		EditBox box = new EditBox(font, left + flowX, top + flowY, w, 20, hint);
		box.setMaxLength(12);
		box.setHint(hint);
		box.setValue(value);
		box.setResponder(v -> {
			if (!v.chars().allMatch(Character::isDigit)) {
				box.setValue(v.replaceAll("\\D", ""));
			}
		});
		addRenderableWidget(box);
		flowX += w + 4;
		return box;
	}

	private void newRow() {
		flowX = PAD;
		flowY += 22;
	}

	private void buildOverview() {
		// text is drawn in extractRenderState; the link button sits above the close button
		Component link = Component.translatable("gui.burmaldaholic.charter.link_tables");
		int w = Math.min(W - 2 * PAD - 60, font.width(link) + 8);
		addRenderableWidget(Button.builder(link, b -> send("link", new CompoundTag()))
			.bounds(left + PAD, top + H - PAD - 20, Math.max(80, w), 20).build());
	}

	private ListTag tables() {
		return state.getListOrEmpty("tables");
	}

	private int pages() {
		return Math.max(1, (tables().size() + TABLES_PER_PAGE - 1) / TABLES_PER_PAGE);
	}

	private Draft draft(CompoundTag row) {
		return drafts.computeIfAbsent(row.getStringOr("key", ""), k -> {
			Draft d = new Draft();
			d.open = row.getBooleanOr("open", true);
			d.bots = row.getBooleanOr("bots", true);
			long min = row.getLongOr("min", 0);
			long max = row.getLongOr("max", 0);
			d.min = min > 0 ? Long.toString(min) : "";
			d.max = max > 0 ? Long.toString(max) : "";
			return d;
		});
	}

	private void buildTables() {
		ListTag list = tables();
		page = Math.min(page, pages() - 1);
		// hint text height (drawn in extractRenderState)
		flowY = contentY + wrappedHeight(limitHint(), W - 2 * PAD) + 4;
		for (int i = page * TABLES_PER_PAGE; i < Math.min(list.size(), (page + 1) * TABLES_PER_PAGE); i++) {
			CompoundTag row = list.getCompoundOrEmpty(i);
			String key = row.getStringOr("key", "");
			Draft d = draft(row);
			flowX = PAD;
			rowLabelY.put(key, flowY);
			flowY += 11; // row label
			flow(toggleLabel("gui.burmaldaholic.charter.table_open", d.open), 40, b -> {
				d.open = !d.open;
				rebuild();
			});
			if (row.getBooleanOr("poker", false)) {
				flow(toggleLabel("gui.burmaldaholic.charter.table_bots", d.bots), 40, b -> {
					d.bots = !d.bots;
					rebuild();
				});
			}
			EditBox min = flowBox(56, Component.translatable("gui.burmaldaholic.common.min"), d.min);
			EditBox max = flowBox(56, Component.translatable("gui.burmaldaholic.common.max"), d.max);
			rowBoxes.put(key, new EditBox[] {min, max});
			flow(Component.translatable("gui.burmaldaholic.multiplayer.save"), 40, b -> saveRow(key, d, min, max));
			flowY += 24;
		}
		if (pages() > 1) {
			flowX = PAD;
			flowY = H - PAD - 20;
			flow(Component.translatable("gui.burmaldaholic.common.back"), 40, b -> {
				page = Math.max(0, page - 1);
				rebuild();
			}).active = page > 0;
			flow(Component.translatable("gui.burmaldaholic.common.next"), 40, b -> {
				page = Math.min(pages() - 1, page + 1);
				rebuild();
			}).active = page < pages() - 1;
		}
	}

	private Component toggleLabel(String key, boolean on) {
		return Component.translatable("gui.burmaldaholic.multiplayer.toggle", Component.translatable(key),
			Component.translatable(on ? "gui.burmaldaholic.common.on" : "gui.burmaldaholic.common.off"));
	}

	private Component limitHint() {
		return Component.translatable("gui.burmaldaholic.multiplayer.limit_hint", Texts.chips(state.getLongOr("global_max", 0)));
	}

	private void saveRow(String key, Draft d, EditBox minBox, EditBox maxBox) {
		d.min = minBox.getValue();
		d.max = maxBox.getValue();
		long min = OwnerLimits.parseField(d.min);
		long max = OwnerLimits.parseField(d.max);
		if (min < 0 || max < 0) {
			showMessage(Component.translatable("gui.burmaldaholic.error.invalid_amount"), true);
			return;
		}
		CompoundTag args = new CompoundTag();
		args.putString("key", key);
		args.putBoolean("open", d.open);
		args.putBoolean("bots", d.bots);
		args.putLong("min", min);
		args.putLong("max", max);
		savingKey = key;
		send("table", args);
	}

	private void buildBankroll() {
		flowY = contentY + 4 * 11 + 6;
		flowX = PAD;
		amount = flowBox(80, Component.translatable("gui.burmaldaholic.common.amount"), amountText);
		flow(Component.translatable("gui.burmaldaholic.common.max"), 30, b -> {
			if (amount != null) {
				amount.setValue(Long.toString(state.getLongOr("available", 0)));
			}
		});
		newRow();
		flow(Component.translatable("gui.burmaldaholic.charter.deposit"), 60, b -> bankrollAction("deposit"));
		flow(Component.translatable("gui.burmaldaholic.charter.withdraw"), 60, b -> bankrollAction("withdraw"));
	}

	private void bankrollAction(String action) {
		long value = amount == null ? -1 : OwnerLimits.parseField(amount.getValue());
		if (value <= 0) {
			showMessage(Component.translatable("gui.burmaldaholic.error.invalid_amount"), true);
			return;
		}
		CompoundTag args = new CompoundTag();
		args.putLong("amount", value);
		amountText = "";
		if (amount != null) {
			amount.setValue("");
		}
		send(action, args);
	}

	// ---- rendering -------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(left - 1, top - 1, left + W + 1, top + H + 1, BORDER);
		g.fill(left, top, left + W, top + H, PANEL);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.text(font, title, left + PAD, top + 6, GOLD, true);
		int x = left + PAD;
		int y = top + contentY + 2;
		int wrap = W - 2 * PAD;
		switch (tab) {
			case OVERVIEW -> drawOverview(g, x, y, wrap);
			case TABLES -> drawTables(g, x, y, wrap);
			case BANKROLL -> drawBankroll(g, x, y, wrap);
			case STATS -> drawStats(g, x, y, wrap);
		}
		if (message != null) {
			int my = top + H - PAD - 20 - 12;
			List<FormattedCharSequence> lines = font.split(message, wrap);
			for (int i = lines.size() - 1; i >= 0; i--) {
				g.text(font, lines.get(i), x, my, messageError ? BAD : GOOD, true);
				my -= 10;
			}
		}
	}

	private int line(GuiGraphicsExtractor g, Component text, int x, int y, int wrap, int color) {
		for (FormattedCharSequence seq : font.split(text, wrap)) {
			g.text(font, seq, x, y, color, true);
			y += 10;
		}
		return y + 1;
	}

	private int wrappedHeight(Component text, int wrap) {
		return font.split(text, wrap).size() * 10 + 1;
	}

	private void drawOverview(GuiGraphicsExtractor g, int x, int y, int wrap) {
		if (!isOwner()) {
			y = line(g, Component.translatable("gui.burmaldaholic.multiplayer.owner", Texts.raw(state.getStringOr("owner_name", ""))), x, y, wrap, DIM);
		}
		y = line(g, Component.translatable("gui.burmaldaholic.charter.bankroll_value", Texts.chips(state.getLongOr("bankroll", 0))), x, y, wrap, GOLD);
		y = line(g, Component.translatable("gui.burmaldaholic.charter.reserved", Texts.chips(state.getLongOr("reserved", 0))), x, y, wrap, TEXT);
		y = line(g, Component.translatable("gui.burmaldaholic.charter.available", Texts.chips(state.getLongOr("available", 0))), x, y, wrap, TEXT);
		boolean broke = state.getBooleanOr("broke", false);
		y = line(g, Component.translatable(broke ? "gui.burmaldaholic.charter.status_broke" : "gui.burmaldaholic.charter.status_open"), x, y, wrap, broke ? BAD : GOOD);
		y = line(g, Component.translatable("gui.burmaldaholic.multiplayer.claim", Texts.plural("unit.burmaldaholic.block", state.getIntOr("radius", 0)),
			Texts.raw(state.getIntOr("x", 0) + ", " + state.getIntOr("y", 0) + ", " + state.getIntOr("z", 0))), x, y, wrap, DIM);
		int count = tables().size();
		y = line(g, count == 0 ? Component.translatable("gui.burmaldaholic.charter.no_tables")
			: Component.translatable("gui.burmaldaholic.multiplayer.tables_count", Texts.number(count)), x, y, wrap, TEXT);
		line(g, tally("gui.burmaldaholic.charter.today", state.getCompoundOrEmpty("today")), x, y, wrap, TEXT);
	}

	private void drawTables(GuiGraphicsExtractor g, int x, int y, int wrap) {
		line(g, limitHint(), x, y, wrap, DIM);
		ListTag list = tables();
		if (list.isEmpty()) {
			line(g, Component.translatable("gui.burmaldaholic.charter.no_tables"), x, y + wrappedHeight(limitHint(), wrap) + 4, wrap, TEXT);
			return;
		}
		for (int i = page * TABLES_PER_PAGE; i < Math.min(list.size(), (page + 1) * TABLES_PER_PAGE); i++) {
			CompoundTag row = list.getCompoundOrEmpty(i);
			Integer labelY = rowLabelY.get(row.getStringOr("key", ""));
			if (labelY == null) {
				continue;
			}
			String desc = row.getStringOr("desc", "");
			Component name = desc.isEmpty() ? Component.translatable("gui.burmaldaholic.charter.tables") : Component.translatable(desc);
			Component label = Component.translatable("gui.burmaldaholic.charter.table_row", name,
				Texts.raw(row.getIntOr("x", 0) + ", " + row.getIntOr("y", 0) + ", " + row.getIntOr("z", 0)));
			g.text(font, font.split(label, wrap).getFirst(), x, top + labelY + 1, GOLD, true);
		}
		if (pages() > 1) {
			Component p = Component.translatable("gui.burmaldaholic.multiplayer.page", Texts.number(page + 1), Texts.number(pages()));
			g.text(font, p, left + W / 2 - font.width(p) / 2, top + H - PAD - 20 - 11, DIM, true);
		}
	}

	private void drawBankroll(GuiGraphicsExtractor g, int x, int y, int wrap) {
		y = line(g, Component.translatable("gui.burmaldaholic.charter.bankroll_value", Texts.chips(state.getLongOr("bankroll", 0))), x, y, wrap, GOLD);
		y = line(g, Component.translatable("gui.burmaldaholic.charter.reserved", Texts.chips(state.getLongOr("reserved", 0))), x, y, wrap, TEXT);
		y = line(g, Component.translatable("gui.burmaldaholic.charter.available", Texts.chips(state.getLongOr("available", 0))), x, y, wrap, TEXT);
		line(g, Component.translatable("gui.burmaldaholic.common.balance", Texts.chips(state.getLongOr("balance", 0))), x, y, wrap, DIM);
	}

	private void drawStats(GuiGraphicsExtractor g, int x, int y, int wrap) {
		CompoundTag today = state.getCompoundOrEmpty("today");
		CompoundTag total = state.getCompoundOrEmpty("total");
		y = line(g, tally("gui.burmaldaholic.charter.today", today), x, y, wrap, TEXT);
		y = line(g, tally("gui.burmaldaholic.charter.total", total), x, y, wrap, TEXT);
		y = line(g, Component.translatable("gui.burmaldaholic.charter.rake", Texts.chips(total.getLongOr("rake", 0))), x, y, wrap, TEXT);
		line(g, Component.translatable("gui.burmaldaholic.multiplayer.rounds", Texts.number(today.getLongOr("rounds", 0)),
			Texts.number(total.getLongOr("rounds", 0))), x, y, wrap, DIM);
	}

	private static Component tally(String key, CompoundTag t) {
		long profit = t.getLongOr("profit", 0);
		Component p = profit < 0 ? Component.translatable("gui.burmaldaholic.multiplayer.negative", Texts.chips(-profit)) : Texts.chips(profit);
		return Component.translatable(key, Texts.chips(t.getLongOr("handle", 0)), Texts.chips(t.getLongOr("paid", 0)), p);
	}
}
