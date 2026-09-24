package dev.nezo.burmaldaholic.bots.client;

import dev.nezo.burmaldaholic.bots.BotTexts;
import dev.nezo.burmaldaholic.bots.net.BotsActionPayload;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.BotSettings;
import dev.nezo.burmaldaholic.core.bots.logic.BotSpeed;
import dev.nezo.burmaldaholic.core.bots.logic.BotsMode;
import dev.nezo.burmaldaholic.core.bots.logic.Personality;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.text.Texts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Table settings (BOTS.md §8.2): players policy, bot count, difficulty / style, keep-a-seat-free, chatter,
 * speed, private table + invites, seated bots with their personality, owner / keeper limits and the table
 * defaults. Opened from the [⚙] button on every bot table screen, {@code /casino table settings} or the
 * Casino Menu's Bots tab. Read-only for players who may not change anything (disabled controls explain why
 * in their tooltip). Radio groups are rows of auto-width buttons that wrap (Russian labels fit); the body
 * scrolls when the window is small. The server validates everything.
 */
public final class TableSettingsScreen extends Screen {
	private static final int BG = 0xFF1E5E3A;
	private static final int BORDER = 0xFF0E2E1C;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFBBBBBB;
	private static final int GOLD = 0xFFFFD700;
	private static final int WARN = 0xFFFFAA00;
	private static final int ERROR = 0xFFFF5555;
	private static final int PAD = 8;
	private static final String MINUS = "−";
	private static final String PLUS = "+";

	private enum View { MAIN, INVITE, MANAGE, LIMITS }

	private CompoundTag state;
	private View view = View.MAIN;
	// edited values
	private SeatPolicy policy = SeatPolicy.HUMANS_ONLY;
	private int count;
	private BotDifficulty difficulty = BotDifficulty.NORMAL;
	private boolean keepFree = true;
	private boolean chatter = true;
	private BotSpeed speed = BotSpeed.NORMAL;
	private BotsMode mode = BotsMode.ALLOWED;
	private boolean hostMayChange = true;
	private int maxBots;
	private boolean allowPrivate = true;
	// layout
	private int left;
	private int top;
	private int panelW;
	private int panelH;
	private int bodyTop;
	private int bodyBottom;
	private int scroll;
	private int contentHeight;
	private List<FlowLayout.Text> texts = List.of();
	private final List<AbstractWidget> bodyWidgets = new ArrayList<>();
	private int errorY;
	private @Nullable Component error;
	private int errorTicks;
	private boolean closeOnOk;

	public TableSettingsScreen(CompoundTag state) {
		super(Component.translatable("gui.burmaldaholic.bots.settings.open"));
		this.state = state;
		readEdits();
		readError();
	}

	/** New state from the server (after an action). */
	public void accept(CompoundTag newState) {
		boolean modeChanged = newState.getBooleanOr("defaults", false) != state.getBooleanOr("defaults", false);
		this.state = newState;
		readError();
		if (error == null && closeOnOk) {
			onClose();
			return;
		}
		closeOnOk = false;
		if (modeChanged || error == null) {
			readEdits();
		}
		if (minecraft != null) {
			rebuildWidgets();
		}
	}

	BlockPos pos() {
		return BlockPos.of(state.getLongOr("pos", 0));
	}

	private void readEdits() {
		policy = SeatPolicy.byId(state.getStringOr("policy", "humans_only"), SeatPolicy.HUMANS_ONLY);
		count = state.getIntOr("count", 0);
		difficulty = BotDifficulty.byId(state.getStringOr("difficulty", "normal"), BotDifficulty.NORMAL);
		keepFree = state.getBooleanOr("keepFree", true);
		chatter = state.getBooleanOr("chatter", true);
		speed = speedOf(state.getStringOr("speed", "NORMAL"));
		mode = modeOf(state.getStringOr("mode", "ALLOWED"));
		hostMayChange = state.getBooleanOr("hostMayChange", true);
		maxBots = state.getIntOr("maxBots", 0);
		allowPrivate = state.getBooleanOr("allowPrivate", true);
	}

	private void readError() {
		error = decodeError(state);
		if (error != null) {
			errorTicks = 100;
		}
	}

	/** The server's error line in a state, or null. */
	static @Nullable Component decodeError(CompoundTag state) {
		CompoundTag e = state.getCompoundOrEmpty("error");
		if (e.isEmpty() || Minecraft.getInstance().level == null) {
			return null;
		}
		return ComponentSerialization.CODEC.parse(Minecraft.getInstance().level.registryAccess().createSerializationContext(NbtOps.INSTANCE), e)
			.result().orElse(null);
	}

	private static BotSpeed speedOf(String s) {
		for (BotSpeed v : BotSpeed.values()) {
			if (v.name().equalsIgnoreCase(s)) {
				return v;
			}
		}
		return BotSpeed.NORMAL;
	}

	private static BotsMode modeOf(String s) {
		for (BotsMode v : BotsMode.values()) {
			if (v.name().equalsIgnoreCase(s)) {
				return v;
			}
		}
		return BotsMode.ALLOWED;
	}

	private boolean flag(String key) {
		return state.getBooleanOr(key, false);
	}

	private @Nullable Component reason(String key) {
		String k = state.getStringOr(key, "");
		return k.isEmpty() ? null : Component.translatable(k);
	}

	private void send(String action, CompoundTag args) {
		if (!args.contains("screenDefaults")) {
			args.putBoolean("screenDefaults", flag("defaults"));
		}
		ClientPlayNetworking.send(new BotsActionPayload(pos(), action, args));
	}

	private Component tableName() {
		return Component.translatable(state.getStringOr("title", "gui.burmaldaholic.bots.admin.title"));
	}

	@Override
	public Component getTitle() {
		return Component.translatable(flag("defaults") ? "gui.burmaldaholic.bots.settings.defaults_title" : "gui.burmaldaholic.bots.settings.title", tableName());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (errorTicks > 0 && --errorTicks == 0) {
			error = null;
		}
	}

	// ---- layout -----------------------------------------------------------------------------------

	@Override
	protected void init() {
		panelW = Math.min(width - 8, 300);
		left = (width - panelW) / 2;
		int inner = panelW - 2 * PAD;
		// footer (fixed) measured at y = 0, body measured from y = 0; then placed.
		FlowLayout footer = new FlowLayout(font, left + PAD, 0, inner);
		buildFooter(footer);
		int footerH = footer.bottom();
		FlowLayout body = new FlowLayout(font, left + PAD, 0, inner);
		switch (view) {
			case MAIN -> buildMain(body);
			case INVITE -> buildInvite(body);
			case MANAGE -> buildManage(body);
			case LIMITS -> buildLimits(body);
		}
		contentHeight = body.bottom();
		int titleH = 10 + font.split(getTitle(), inner).size() * font.lineHeight;
		int errorH = 12;
		panelH = Math.min(height - 8, titleH + contentHeight + 6 + errorH + footerH + PAD);
		top = Math.max(4, (height - panelH) / 2);
		bodyTop = top + titleH;
		int footerTop = top + panelH - PAD - footerH;
		errorY = footerTop - errorH;
		bodyBottom = errorY - 2;
		scroll = Math.max(0, Math.min(scroll, contentHeight - (bodyBottom - bodyTop)));
		int dy = bodyTop - scroll;
		bodyWidgets.clear();
		for (AbstractWidget w : body.widgets) {
			w.setY(w.getY() + dy);
			w.visible = w.getY() >= bodyTop && w.getY() + w.getHeight() <= bodyBottom;
			bodyWidgets.add(w);
			addRenderableWidget(w);
		}
		List<FlowLayout.Text> t = new ArrayList<>();
		for (FlowLayout.Text x : body.texts) {
			t.add(new FlowLayout.Text(x.text(), x.x(), x.y() + dy, x.color()));
		}
		texts = t;
		for (AbstractWidget w : footer.widgets) {
			w.setY(w.getY() + footerTop);
			addRenderableWidget(w);
		}
	}

	private void refresh() {
		rebuildWidgets();
	}

	private Button radio(FlowLayout f, Component label, boolean selected, boolean enabled, @Nullable Component why, Runnable pick) {
		MutableComponent text = selected ? label.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE) : label.copy();
		Button b = f.button(text, 30, x -> {
			pick.run();
			refresh();
		});
		b.active = enabled;
		if (!enabled && why != null) {
			b.setTooltip(Tooltip.create(why));
		}
		return b;
	}

	private Button toggle(FlowLayout f, String labelKey, boolean value, boolean enabled, @Nullable Component why, Runnable flip) {
		Component label = Component.translatable("options.generic_value", Component.translatable(labelKey),
			Component.translatable(value ? "options.on" : "options.off"));
		Button b = f.button(label, 60, x -> {
			flip.run();
			refresh();
		});
		b.active = enabled;
		if (!enabled && why != null) {
			b.setTooltip(Tooltip.create(why));
		}
		return b;
	}

	private void buildFooter(FlowLayout f) {
		boolean mayEdit = flag("mayEdit");
		switch (view) {
			case MAIN -> {
				if (mayEdit) {
					boolean defaults = flag("defaults");
					Button save = f.button(Component.translatable(defaults ? "gui.burmaldaholic.bots.settings.save_short" : "gui.burmaldaholic.bots.settings.save"), 60,
						b -> save(defaults));
					if (!defaults) {
						save.setTooltip(Tooltip.create(Component.translatable("gui.burmaldaholic.bots.pending")));
					}
					if (!defaults && flag("mayDefaults")) {
						f.button(Component.translatable("gui.burmaldaholic.bots.settings.save_defaults"), 60, b -> save(true));
					}
					f.button(Component.translatable("gui.cancel"), 50, b -> onClose());
				} else {
					f.button(Component.translatable("gui.burmaldaholic.common.close"), 50, b -> onClose());
				}
			}
			case LIMITS -> {
				f.button(Component.translatable("gui.burmaldaholic.bots.settings.save_short"), 60, b -> saveLimits());
				f.button(Component.translatable("gui.back"), 50, b -> {
					view = View.MAIN;
					readEdits();
					refresh();
				});
			}
			default -> f.button(Component.translatable("gui.back"), 50, b -> {
				view = View.MAIN;
				refresh();
			});
		}
	}

	private void buildMain(FlowLayout f) {
		boolean mayEdit = flag("mayEdit");
		Component lock = reason("lock");
		boolean style = "STYLE".equals(state.getStringOr("diffMode", "LEVEL"));
		boolean defaults = flag("defaults");
		String host = state.getStringOr("host", "");
		BotSettings shown = new BotSettings(policy, count, difficulty, keepFree, chatter, speed);
		f.line(BotTexts.summary(shown, flag("private"), 0, host, style), GOLD);
		if (flag("pending")) {
			f.line(Component.translatable("gui.burmaldaholic.bots.pending"), WARN);
		}
		if (!host.isEmpty() && !defaults) {
			f.line(Component.translatable("gui.burmaldaholic.bots.settings.host", Texts.raw(host)), MUTED);
		}
		if (!mayEdit && lock != null) {
			f.line(lock, MUTED);
		}
		if (!flag("botsVisible")) {
			Component why = reason("botsReason");
			if (why != null) {
				f.line(why, WARN);
			}
		} else {
			buildBotControls(f, mayEdit, lock, style);
		}
		// access
		f.gap(4);
		f.line(Component.translatable("gui.burmaldaholic.bots.settings.access"), TEXT);
		boolean mayAccess = flag("mayAccess");
		boolean privateAllowed = flag("privateAllowed");
		boolean isPrivate = flag("private");
		toggle(f, "gui.burmaldaholic.bots.private.toggle", isPrivate, mayAccess && (privateAllowed || isPrivate), reason("privateReason"), () -> {
			CompoundTag a = new CompoundTag();
			a.putBoolean("on", !isPrivate);
			send("private", a);
		});
		f.newRow();
		f.line(Component.translatable("gui.burmaldaholic.bots.private.status",
			Component.translatable(isPrivate ? "gui.burmaldaholic.bots.private.yes" : "gui.burmaldaholic.bots.private.no"), invitedNames()), MUTED);
		Button inv = f.button(Component.translatable("gui.burmaldaholic.bots.private.invite"), 40, b -> {
			view = View.INVITE;
			scroll = 0;
			refresh();
		});
		inv.active = mayAccess && privateAllowed;
		Button man = f.button(Component.translatable("gui.burmaldaholic.bots.private.manage"), 40, b -> {
			view = View.MANAGE;
			scroll = 0;
			refresh();
		});
		man.active = mayAccess;
		// seated bots
		if (!defaults) {
			f.gap(4);
			f.line(Component.translatable("gui.burmaldaholic.bots.settings.seated"), TEXT);
			ListTag bots = state.getListOrEmpty("bots");
			if (bots.isEmpty()) {
				f.line(Component.translatable("gui.burmaldaholic.bots.settings.none_seated"), MUTED);
			}
			for (int i = 0; i < bots.size(); i++) {
				CompoundTag b = bots.getCompoundOrEmpty(i);
				BotDifficulty level = BotDifficulty.byId(b.getStringOr("level", "normal"), BotDifficulty.NORMAL);
				Personality p = Personality.byId(b.getStringOr("personality", "tag"));
				Component last = b.getBooleanOr("money", false) ? Texts.chips(b.getLongOr("stack", 0)) : Component.translatable(p.translationKey());
				f.line(Component.translatable("gui.burmaldaholic.bots.seat_line", Texts.number(b.getIntOr("seat", i + 1)),
					BotTexts.display(b.getStringOr("name", "")), BotTexts.level(level, style), last), TEXT);
				f.line(BotTexts.personality(p), MUTED);
			}
		}
		// manager
		if (flag("mayDefaults") || flag("mayLimits")) {
			f.gap(4);
			if (flag("mayDefaults")) {
				f.button(Component.translatable(defaults ? "gui.back" : "gui.burmaldaholic.bots.charter.defaults"), 50, b -> {
					CompoundTag a = new CompoundTag();
					a.putBoolean("defaults", !defaults);
					a.putBoolean("screenDefaults", !defaults);
					scroll = 0;
					send("open", a);
				});
			}
			if (flag("mayLimits")) {
				f.button(Component.translatable("gui.burmaldaholic.bots.settings.limits"), 50, b -> {
					view = View.LIMITS;
					scroll = 0;
					refresh();
				});
			}
		}
	}

	private void buildBotControls(FlowLayout f, boolean mayEdit, @Nullable Component lock, boolean style) {
		int max = state.getIntOr("maxCount", 0);
		f.line(Component.translatable("gui.burmaldaholic.bots.settings.players"), TEXT);
		boolean botsOnly = flag("botsOnly");
		for (SeatPolicy p : new SeatPolicy[] {SeatPolicy.HUMANS_ONLY, SeatPolicy.MIXED, SeatPolicy.BOTS_ONLY}) {
			boolean allowed = mayEdit && (p != SeatPolicy.BOTS_ONLY || botsOnly);
			Component why = !mayEdit ? lock : Component.translatable("gui.burmaldaholic.bots.error.others_seated");
			radio(f, Component.translatable(p.translationKey()), policy == p, allowed, why, () -> {
				policy = p;
				if (p != SeatPolicy.HUMANS_ONLY && count <= 0) {
					count = Math.min(1, max);
				}
			});
		}
		f.newRow();
		boolean withBots = policy != SeatPolicy.HUMANS_ONLY;
		f.inline(Component.translatable("gui.burmaldaholic.bots.settings.count"), TEXT);
		Button minus = f.button(Texts.raw(MINUS), 20, b -> {
			count = Math.max(policy == SeatPolicy.BOTS_ONLY ? 1 : 0, count - 1);
			refresh();
		});
		minus.active = mayEdit && withBots && count > (policy == SeatPolicy.BOTS_ONLY ? 1 : 0);
		f.inline(Texts.number(count), GOLD);
		Button plus = f.button(Texts.raw(PLUS), 20, b -> {
			count = Math.min(max, count + 1);
			refresh();
		});
		plus.active = mayEdit && withBots && count < max;
		f.inline(Component.translatable("gui.burmaldaholic.bots.settings.count_max", Texts.number(max)), MUTED);
		String diffMode = state.getStringOr("diffMode", "LEVEL");
		if ("HIDDEN".equals(diffMode)) {
			f.line(Component.translatable("gui.burmaldaholic.bots.luck_only"), MUTED);
		} else {
			f.line(Component.translatable(style ? "gui.burmaldaholic.bots.settings.style" : "gui.burmaldaholic.bots.settings.difficulty"), TEXT);
			for (BotDifficulty d : BotDifficulty.values()) {
				radio(f, BotTexts.level(d, style), difficulty == d, mayEdit && withBots, lock, () -> difficulty = d);
			}
			f.newRow();
			if (flag("heatHardOnly")) {
				f.line(Component.translatable("gui.burmaldaholic.bots.heat_hard_only"), WARN);
			}
		}
		f.gap(2);
		toggle(f, "gui.burmaldaholic.bots.settings.keep_free", keepFree, mayEdit && policy == SeatPolicy.MIXED, lock, () -> keepFree = !keepFree);
		f.newRow();
		toggle(f, "gui.burmaldaholic.bots.settings.chatter", chatter, mayEdit, lock, () -> chatter = !chatter);
		f.newRow();
		f.line(Component.translatable("gui.burmaldaholic.bots.settings.speed"), TEXT);
		boolean speedOn = mayEdit && policy == SeatPolicy.BOTS_ONLY;
		for (BotSpeed s : BotSpeed.values()) {
			radio(f, Component.translatable("gui.burmaldaholic.bots.speed." + s.name().toLowerCase(Locale.ROOT)), speed == s, speedOn,
				mayEdit ? Component.translatable("gui.burmaldaholic.bots.settings.speed_hint") : lock, () -> speed = s);
		}
		f.newRow();
		if (policy != SeatPolicy.BOTS_ONLY) {
			f.line(Component.translatable("gui.burmaldaholic.bots.settings.speed_hint"), MUTED);
		}
	}

	private Component invitedNames() {
		ListTag invited = state.getListOrEmpty("invited");
		if (invited.isEmpty()) {
			return Component.translatable("gui.burmaldaholic.bots.private.none_invited");
		}
		List<String> names = new ArrayList<>();
		for (int i = 0; i < invited.size(); i++) {
			names.add(invited.getCompoundOrEmpty(i).getStringOr("name", ""));
		}
		return Texts.raw(String.join(", ", names)); // literal-ok: player names
	}

	private void buildInvite(FlowLayout f) {
		f.line(Component.translatable("gui.burmaldaholic.bots.private.invite"), TEXT);
		ListTag nearby = state.getListOrEmpty("nearby");
		if (nearby.isEmpty()) {
			f.line(Component.translatable("gui.burmaldaholic.bots.private.none_nearby"), MUTED);
		}
		for (int i = 0; i < nearby.size(); i++) {
			CompoundTag p = nearby.getCompoundOrEmpty(i);
			f.inline(Texts.raw(p.getStringOr("name", "")), TEXT);
			f.button(Component.translatable("gui.burmaldaholic.bots.private.invite_submit"), 40, b -> {
				CompoundTag a = new CompoundTag();
				a.putString("id", p.getStringOr("id", ""));
				send("invite", a);
			});
			f.newRow();
		}
	}

	private void buildManage(FlowLayout f) {
		f.line(Component.translatable("gui.burmaldaholic.bots.private.manage"), TEXT);
		ListTag invited = state.getListOrEmpty("invited");
		if (invited.isEmpty()) {
			f.line(Component.translatable("gui.burmaldaholic.bots.private.none_invited"), MUTED);
		}
		for (int i = 0; i < invited.size(); i++) {
			CompoundTag p = invited.getCompoundOrEmpty(i);
			f.inline(Texts.raw(p.getStringOr("name", "")), TEXT);
			Button b = f.button(Component.translatable("gui.burmaldaholic.bots.private.uninvite_submit"), 40, x -> {
				CompoundTag a = new CompoundTag();
				a.putString("id", p.getStringOr("id", ""));
				send("uninvite", a);
			});
			b.active = flag("mayAccess");
			f.newRow();
		}
	}

	private void buildLimits(FlowLayout f) {
		boolean mayMode = flag("mayMode");
		int seats = state.getIntOr("seats", 1);
		if (flag("owned")) {
			f.line(Component.translatable("gui.burmaldaholic.bots.charter.bots"), TEXT);
			for (BotsMode m : BotsMode.values()) {
				radio(f, Component.translatable(m.translationKey()), mode == m, mayMode, null, () -> mode = m);
			}
			f.newRow();
		}
		toggle(f, "gui.burmaldaholic.bots.charter.host_may_change", hostMayChange, true, null, () -> hostMayChange = !hostMayChange);
		f.newRow();
		f.inline(Component.translatable("gui.burmaldaholic.bots.charter.max_bots"), TEXT);
		Button minus = f.button(Texts.raw(MINUS), 20, b -> {
			maxBots = Math.max(0, maxBots - 1);
			refresh();
		});
		minus.active = maxBots > 0;
		f.inline(Texts.number(maxBots), GOLD);
		Button plus = f.button(Texts.raw(PLUS), 20, b -> {
			maxBots = Math.min(Math.max(0, seats - 1), maxBots + 1);
			refresh();
		});
		plus.active = maxBots < seats - 1;
		f.newRow();
		toggle(f, "gui.burmaldaholic.bots.charter.allow_private", allowPrivate, true, null, () -> allowPrivate = !allowPrivate);
		f.newRow();
	}

	private void save(boolean asDefaults) {
		CompoundTag a = new CompoundTag();
		a.putString("policy", policy.id());
		a.putInt("count", count);
		a.putString("difficulty", difficulty.id());
		a.putBoolean("keepFree", keepFree);
		a.putBoolean("chatter", chatter);
		a.putString("speed", speed.name());
		a.putBoolean("defaults", asDefaults);
		a.putBoolean("screenDefaults", flag("defaults"));
		closeOnOk = !flag("defaults") && !asDefaults;
		send("save", a);
	}

	private void saveLimits() {
		CompoundTag a = new CompoundTag();
		a.putString("mode", mode.name());
		a.putBoolean("hostMayChange", hostMayChange);
		a.putInt("maxBots", maxBots);
		a.putBoolean("allowPrivate", allowPrivate);
		view = View.MAIN;
		send("limits", a);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int max = Math.max(0, contentHeight - (bodyBottom - bodyTop));
		int next = Math.max(0, Math.min(max, scroll - (int) Math.round(scrollY * 12)));
		if (next != scroll) {
			scroll = next;
			refresh();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---- rendering ----------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, BORDER);
		g.fill(left, top, left + panelW, top + panelH, BG);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int y = top + 6;
		for (var line : font.split(getTitle(), panelW - 2 * PAD)) {
			g.text(font, line, left + PAD, y, TEXT, true);
			y += font.lineHeight;
		}
		g.enableScissor(left + 1, bodyTop, left + panelW - 1, bodyBottom);
		for (FlowLayout.Text t : texts) {
			if (t.y() + font.lineHeight >= bodyTop && t.y() <= bodyBottom) {
				g.text(font, t.text(), t.x(), t.y(), t.color(), true);
			}
		}
		g.disableScissor();
		if (contentHeight > bodyBottom - bodyTop) {
			int track = bodyBottom - bodyTop;
			int barH = Math.max(10, track * track / Math.max(1, contentHeight));
			int barY = bodyTop + (track - barH) * scroll / Math.max(1, contentHeight - track);
			g.fill(left + panelW - 4, barY, left + panelW - 2, barY + barH, 0x88FFFFFF);
		}
		if (error != null) {
			var lines = font.split(error, panelW - 2 * PAD);
			if (!lines.isEmpty()) {
				g.text(font, lines.getFirst(), left + PAD, errorY, ERROR, true);
			}
		}
	}
}
