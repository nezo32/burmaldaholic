package dev.nezo.burmaldaholic.games.extras.client.pvp.wheel;

import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.bots.logic.SeatPolicy;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.client.pvp.coin.CoinDuelSetupScreen;
import dev.nezo.burmaldaholic.games.extras.client.pvp.coin.PvpPanel;
import dev.nezo.burmaldaholic.games.extras.client.pvp.coin.TauntScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Wheel Party panel at a Wheel of Fortune (PVP.md §6.5; task J-M2). Three faces, from the server's panel state:
 * no party here → host set-up (Max stake per player, Your stake, Seats, Bot style, Table size, [Start a Wheel
 * Party]); an open party → the slice legend with shares and win chances, and [Join the party: n/N] or, once in,
 * [Add to my slice] (+ [Spin the wheel!] for the host) and [Leave lobby]; a party spinning → the legend only.
 * The server re-sends the state while the panel is open.
 */
public final class WheelPartyPanel extends PvpPanel {
	private static final SeatPolicy[] POLICIES = {SeatPolicy.HUMANS_ONLY, SeatPolicy.MIXED, SeatPolicy.BOTS_ONLY};
	private static final BotDifficulty[] STYLES = {BotDifficulty.MIXED, BotDifficulty.EASY, BotDifficulty.NORMAL, BotDifficulty.HARD};
	private static final int WHEEL_R = 34;
	private static long lastCap;
	private static long lastStake;
	private static int lastPolicy;
	private static int lastStyle;
	private static int lastSize = 4;

	private CompoundTag state;
	private EditBox capBox;
	private EditBox stakeBox;
	private long cap;
	private long stake;
	private int policy = lastPolicy;
	private int style = lastStyle;
	private int size = lastSize;
	private int textTop;

	public WheelPartyPanel(CompoundTag state) {
		super(Component.translatable("gui.burmaldaholic.pvp.wheel.title"), 280, 180);
		this.state = state;
		long min = state.getLongOr("min", 1);
		this.cap = lastCap > 0 ? lastCap : state.getLongOr("default_cap", min);
		this.stake = lastStake > 0 ? lastStake : min;
	}

	void accept(CompoundTag newState) {
		keepTyped(); // typed amounts survive the server's refresh
		state = newState;
		rebuild();
	}

	private void keepTyped() {
		cap = parse(capBox, cap);
		stake = parse(stakeBox, stake);
	}

	private CompoundTag party() {
		return state.getCompoundOrEmpty("party");
	}

	private boolean hasParty() {
		return !party().getStringOr("id", "").isEmpty();
	}

	private boolean inParty() {
		return party().getIntOr("you", -1) >= 0;
	}

	private boolean betsOpen() {
		return "LOBBY".equals(party().getStringOr("state", ""));
	}

	private void send(String action, CompoundTag args) {
		WheelPartyClient.send(action, args);
	}

	private List<Component> textLines() {
		List<Component> out = new ArrayList<>();
		out.add(Component.translatable("gui.burmaldaholic.pvp.wheel.rules").withStyle(ChatFormatting.GRAY));
		if (hasParty()) {
			CompoundTag p = party();
			out.add(Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.chips(p.getLongOr("pot", 0))).append(" · ")
				.append(Component.translatable("gui.burmaldaholic.pvp.lobby.rake", CoinDuelSetupScreen.percent(state.getIntOr("rake_bp", 300)))));
			out.add(Component.translatable("gui.burmaldaholic.pvp.wheel.cap").append(": ").append(Texts.chips(p.getLongOr("cap", 0))));
			if (inParty()) {
				out.add(Component.translatable("gui.burmaldaholic.pvp.wheel.chance", WheelArt.share(p.getLongOr("your_stake", 0), p.getLongOr("pot", 0)))
					.withStyle(ChatFormatting.GOLD));
			}
		}
		out.add(Component.translatable("gui.burmaldaholic.common.balance", Texts.number(state.getLongOr("balance", 0))).withStyle(ChatFormatting.GRAY));
		return out;
	}

	private List<Component> legend() {
		List<Component> out = new ArrayList<>();
		CompoundTag p = party();
		ListTag slices = p.getListOrEmpty("slices");
		long pot = Math.max(1, p.getLongOr("pot", 1));
		for (int i = 0; i < slices.size(); i++) {
			CompoundTag s = slices.getCompoundOrEmpty(i);
			Component name = WheelPartyScreen.name(s.getStringOr("name", "?"), s.getBooleanOr("bot", false), s.getStringOr("level", ""));
			if (s.getBooleanOr("host", false)) {
				name = Component.translatable("gui.burmaldaholic.pvp.lobby.host", name);
			}
			out.add(Component.translatable("gui.burmaldaholic.pvp.wheel.slice", name, Texts.chips(s.getLongOr("stake", 0)), WheelArt.share(s.getLongOr("stake", 0), pot)));
		}
		return out;
	}

	private long[] stakes() {
		ListTag slices = party().getListOrEmpty("slices");
		long[] out = new long[slices.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = slices.getCompoundOrEmpty(i).getLongOr("stake", 0);
		}
		return out;
	}

	private record Label(Component text, int x, int y) {}

	private final List<Label> labels = new ArrayList<>();

	/** Reserves a text label and an 80-px box after it on the current row; returns the box x. */
	private int labelled(Flow flow, Component label) {
		int lx = flow.reserve(font.width(label) + 2);
		labels.add(new Label(label, lx, flow.y() + 6));
		return flow.reserve(80);
	}

	@Override
	protected int layout() {
		labels.clear();
		int x0 = left + PAD + (hasParty() ? 2 * WHEEL_R + 12 : 0);
		int w = left + panelWidth - PAD - x0;
		int y = top + 20;
		textTop = y;
		for (Component c : textLines()) {
			y += wrappedHeight(c, w) + 1;
		}
		for (Component c : legend()) {
			y += wrappedHeight(c, w - 10) + 1;
		}
		if (hasParty()) {
			y = Math.max(y, top + 20 + 2 * WHEEL_R + 8);
		}
		Flow flow = new Flow(left + PAD, y + 4, panelWidth - 2 * PAD);
		CompoundTag p = party();
		if (!hasParty()) {
			capBox = amountBox(labelled(flow, Component.translatable("gui.burmaldaholic.pvp.wheel.cap")), flow.y(), 80, cap);
			flow.newRow();
			stakeBox = amountBox(labelled(flow, Component.translatable("gui.burmaldaholic.pvp.wheel.your_stake")), flow.y(), 80, stake);
			flow.newRow();
			if (state.getBooleanOr("bots", false)) {
				flow.button(Component.translatable("gui.burmaldaholic.pvp.bots.seats").append(": ").append(Component.translatable(POLICIES[policy].translationKey())), 80, b -> {
					keepTyped();
					policy = (policy + 1) % POLICIES.length;
					lastPolicy = policy;
					rebuild();
				});
				if (POLICIES[policy] != SeatPolicy.HUMANS_ONLY) {
					flow.button(Component.translatable("gui.burmaldaholic.pvp.bots.difficulty").append(": ").append(Component.translatable(STYLES[style].styleKey())), 80, b -> {
						keepTyped();
						style = (style + 1) % STYLES.length;
						lastStyle = style;
						rebuild();
					});
					int max = Math.max(2, Math.min(state.getIntOr("max_players", 8), state.getIntOr("max_bots", 3) + 1));
					size = Math.max(2, Math.min(max, size));
					flow.button(Component.translatable("gui.burmaldaholic.pvp.bots.table_size_value", Texts.number(size)), 60, b -> {
						keepTyped();
						size = size >= max ? 2 : size + 1;
						lastSize = size;
						rebuild();
					});
				}
				flow.newRow();
			}
			flow.button(Component.translatable("gui.burmaldaholic.pvp.wheel.host").withStyle(ChatFormatting.BOLD), 100, b -> {
				keepTyped();
				lastCap = cap;
				lastStake = stake;
				CompoundTag a = new CompoundTag();
				a.putLong("cap", cap);
				a.putLong("stake", stake);
				a.putString("policy", POLICIES[policy].id());
				a.putString("difficulty", STYLES[style].id());
				a.putInt("size", size);
				send("host", a);
			});
		} else if (betsOpen() && !inParty()) {
			stakeBox = amountBox(flow.reserve(80), flow.y(), 80, stake);
			int players = p.getListOrEmpty("slices").size();
			flow.button(Component.translatable("gui.burmaldaholic.pvp.wheel.join", Texts.number(players), Texts.number(state.getIntOr("max_players", 8)))
				.withStyle(ChatFormatting.BOLD), 80, b -> {
					keepTyped();
					lastStake = stake;
					CompoundTag a = new CompoundTag();
					a.putLong("stake", stake);
					send("join", a);
				});
		} else if (betsOpen()) {
			stakeBox = amountBox(flow.reserve(80), flow.y(), 80, Math.max(state.getLongOr("min", 1), stake));
			flow.button(Component.translatable("gui.burmaldaholic.pvp.wheel.add"), 80, b -> {
				keepTyped();
				CompoundTag a = new CompoundTag();
				a.putLong("extra", stake);
				send("top_up", a);
			});
			flow.newRow();
			if (p.getBooleanOr("is_host", false) && p.getListOrEmpty("slices").size() >= 2) {
				flow.button(Component.translatable("gui.burmaldaholic.pvp.wheel.spin_now").withStyle(ChatFormatting.GOLD), 80, b -> send("spin", new CompoundTag()));
			}
			flow.button(Component.translatable("gui.burmaldaholic.pvp.lobby.leave"), 60, b -> send("leave", new CompoundTag()));
		}
		if (inParty()) {
			flow.button(Component.translatable("gui.burmaldaholic.pvp.taunt.button"), 50, b -> {
				if (minecraft != null) {
					minecraft.gui.setScreen(new TauntScreen(this, line -> {
						CompoundTag a = new CompoundTag();
						a.putInt("line", line);
						send("taunt", a);
					}));
				}
			});
		}
		flow.button(Component.translatable("gui.burmaldaholic.common.close"), 50, b -> onClose());
		return flow.bottom();
	}

	@Override
	protected void content(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x0 = left + PAD + (hasParty() ? 2 * WHEEL_R + 12 : 0);
		int w = left + panelWidth - PAD - x0;
		if (hasParty()) {
			WheelArt.wheel(g, left + PAD + WHEEL_R + 2, top + 22 + WHEEL_R, WHEEL_R, stakes(), 0);
		}
		int y = textTop;
		for (Component c : textLines()) {
			y = wrap(g, c, x0, y, w, TEXT) + 1;
		}
		for (Label l : labels) {
			g.text(font, l.text(), l.x(), l.y(), TEXT, true);
		}
		List<Component> legend = legend();
		for (int i = 0; i < legend.size(); i++) {
			g.fill(x0, y + 1, x0 + 7, y + 8, WheelArt.color(i));
			y = wrap(g, legend.get(i), x0 + 10, y, w - 10, i == party().getIntOr("you", -1) ? GOLD : TEXT) + 1;
		}
	}

	@Override
	public void onClose() {
		send("close", new CompoundTag());
		super.onClose();
	}
}
