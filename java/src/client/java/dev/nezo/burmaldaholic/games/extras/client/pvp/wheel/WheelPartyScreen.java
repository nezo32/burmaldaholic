package dev.nezo.burmaldaholic.games.extras.client.pvp.wheel;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.core.bots.logic.BotDifficulty;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.client.pvp.coin.MatchView;
import dev.nezo.burmaldaholic.games.extras.client.pvp.coin.PvpPanel;
import dev.nezo.burmaldaholic.games.extras.client.pvp.coin.TauntScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Wheel Party live screen (PVP.md §6.5, 256 × 220), registered with {@code client.pvp.PvpScreens} as mode
 * {@code wheel} and fed by the pvp module's match sync ({@link MatchView}): the repainted wheel (one slice per
 * participant, arc = stake / pot, join order), the legend (name, stake, share), pot and house cut; during the
 * countdown [Add to my slice] and, for the host, [Spin the wheel!]; then the spin (3 turns + the drawn angle,
 * ease-out over the {@code spin} step), "By a hair!" and the UNDERDOG tag, and Rematch / Taunt… / Close.
 */
public final class WheelPartyScreen extends PvpPanel implements PvpScreens.ModeScreen {
	private static final int R = 60;

	private MatchView view;
	private int steps;
	private int stepStartTick;
	private int syncTick;
	private EditBox topUpBox;
	private long topUp;

	public WheelPartyScreen(JsonObject state) {
		super(Component.translatable("gui.burmaldaholic.pvp.wheel.title"), 256, 220);
		this.view = new MatchView(state);
		this.steps = view.steps().size();
		this.topUp = 10;
	}

	@Override
	public Screen screen() {
		return this;
	}

	@Override
	public void update(JsonObject state) {
		MatchView next = new MatchView(state);
		int n = next.steps().size();
		if (n != steps || !next.id().equals(view.id())) {
			stepStartTick = ticks;
		}
		steps = n;
		syncTick = ticks;
		topUp = parse(topUpBox, topUp);
		view = next;
		rebuild();
	}

	/** Display name of a slice owner: a player's name, or a bot's name with its tag. */
	static Component name(String name, boolean bot, String level) {
		if (!bot) {
			return Texts.raw(name);
		}
		BotDifficulty d = BotDifficulty.byId(level, BotDifficulty.NORMAL);
		return Component.translatable("gui.burmaldaholic.bots.display_level", Component.translatable(name), Component.translatable(d.styleKey()));
	}

	private long[] stakes() {
		List<MatchView.Seat> seats = view.seats();
		long[] out = new long[seats.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = seats.get(i).stake();
		}
		return out;
	}

	private boolean youIn() {
		return view.you() >= 0;
	}

	private boolean isHost() {
		MatchView.Seat me = view.seat(view.you());
		return me != null && me.host();
	}

	@Override
	protected int layout() {
		int y = top + 22 + 2 * R + 8;
		for (Component c : lines()) {
			y += wrappedHeight(c, panelWidth - 2 * PAD) + 1;
		}
		Flow flow = new Flow(y + 2);
		if (view.lobby() && youIn()) {
			topUpBox = amountBox(flow.reserve(70), flow.y(), 70, topUp);
			flow.button(Component.translatable("gui.burmaldaholic.pvp.wheel.add"), 70, b -> {
				topUp = parse(topUpBox, topUp);
				CompoundTag a = new CompoundTag();
				a.putLong("extra", topUp);
				WheelPartyClient.send("top_up", a);
			});
			if (isHost() && view.seats().size() >= 2) {
				flow.button(Component.translatable("gui.burmaldaholic.pvp.wheel.spin_now").withStyle(ChatFormatting.GOLD), 70,
					b -> WheelPartyClient.send("spin", new CompoundTag()));
			}
			flow.newRow();
			flow.button(Component.translatable("gui.burmaldaholic.pvp.lobby.leave"), 60, b -> WheelPartyClient.send("leave", new CompoundTag()));
		} else if (view.settled() && youIn()) {
			flow.button(Component.translatable("gui.burmaldaholic.pvp.rematch"), 60, b -> {
				CompoundTag a = new CompoundTag();
				a.putString("id", view.id());
				WheelPartyClient.send("rematch", a);
				b.active = false;
			});
		}
		if (youIn()) {
			flow.button(Component.translatable("gui.burmaldaholic.pvp.taunt.button"), 50, b -> {
				if (minecraft != null) {
					minecraft.gui.setScreen(new TauntScreen(this, line -> {
						CompoundTag a = new CompoundTag();
						a.putInt("line", line);
						WheelPartyClient.send("taunt", a);
					}));
				}
			});
		}
		flow.button(Component.translatable("gui.burmaldaholic.common.close"), 50, b -> onClose());
		return flow.bottom();
	}

	/** Status lines under the wheel: pot / cut, countdown, landing, by a hair, underdog. */
	private List<Component> lines() {
		List<Component> out = new ArrayList<>();
		out.add(Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.chips(view.pot())).append(" · ")
			.append(Component.translatable("gui.burmaldaholic.pvp.lobby.rake",
				dev.nezo.burmaldaholic.games.extras.client.pvp.coin.CoinDuelSetupScreen.percent(view.rakeBasisPoints()))));
		if (view.lobby() && view.ticksLeft() >= 0) {
			out.add(Component.translatable("gui.burmaldaholic.pvp.wheel.spins_in", seconds(view.ticksLeft() - (ticks - syncTick))));
		}
		MatchView.StepView spin = view.step("spin");
		boolean stopped = spin != null && (view.step("result") != null || ticks - stepStartTick >= spin.ticks());
		if (spin != null && stopped) {
			int winner = MatchView.integer(spin.data(), "winner", -1);
			out.add(Component.translatable("msg.burmaldaholic.pvp.wheel.lands", view.name(winner)).withStyle(ChatFormatting.GOLD));
			int hair = MatchView.integer(spin.data(), "hair", -1);
			if (hair >= 0) {
				out.add(Component.translatable("msg.burmaldaholic.pvp.wheel.by_a_hair", view.name(hair)).withStyle(ChatFormatting.GRAY));
			}
		}
		MatchView.StepView result = view.step("result");
		if (result != null && MatchView.bool(result.data(), "underdog", false)) {
			int winner = MatchView.integer(result.data(), "winner", -1);
			MatchView.Seat w = view.seat(winner);
			out.add(Component.translatable("msg.burmaldaholic.pvp.wheel.underdog", view.name(winner), WheelArt.share(w == null ? 0 : w.stake(), view.pot()))
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return out;
	}

	private double rotation() {
		MatchView.StepView spin = view.step("spin");
		if (spin == null) {
			return 0;
		}
		double angle = MatchView.lng(spin.data(), "angle1000", 0) / 1000.0;
		double total = 3 * 360 + angle;
		boolean animating = view.steps().getLast() == spin;
		double t = animating ? Math.min(1.0, (ticks - stepStartTick + partial) / Math.max(1, spin.ticks())) : 1.0;
		double eased = 1 - Math.pow(1 - t, 3);
		return -total * eased;
	}

	@Override
	protected void content(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int cx = left + PAD + R + 4;
		int cy = top + 22 + R;
		long[] stakes = stakes();
		WheelArt.wheel(g, cx, cy, R, stakes, rotation());
		// legend
		int lx = cx + R + 12;
		int lw = left + panelWidth - PAD - lx;
		int y = top + 22;
		long pot = Math.max(1, view.pot());
		List<MatchView.Seat> seats = view.seats();
		MatchView.StepView result = view.step("result");
		int underdog = result != null && MatchView.bool(result.data(), "underdog", false) ? MatchView.integer(result.data(), "winner", -1) : -1;
		for (int i = 0; i < seats.size(); i++) {
			MatchView.Seat s = seats.get(i);
			g.fill(lx, y + 1, lx + 7, y + 8, WheelArt.color(i));
			Component row = Component.translatable("gui.burmaldaholic.pvp.wheel.slice", s.name(), Texts.chips(s.stake()), WheelArt.share(s.stake(), pot));
			y = wrap(g, row, lx + 10, y, lw - 10, s.index() == view.you() ? GOLD : TEXT);
			if (s.index() == underdog) {
				y = wrap(g, Component.translatable("gui.burmaldaholic.pvp.wheel.underdog_tag").withStyle(ChatFormatting.LIGHT_PURPLE), lx + 10, y, lw - 10, TEXT);
			}
			y += 1;
		}
		y = top + 22 + 2 * R + 8;
		for (Component c : lines()) {
			y = wrap(g, c, left + PAD, y, panelWidth - 2 * PAD, TEXT) + 1;
		}
	}

	@Override
	public void tick() {
		super.tick();
		MatchView.StepView spin = view.step("spin");
		if (spin != null && ticks - stepStartTick == spin.ticks()) {
			rebuild(); // the landing lines appear when the wheel stops
		}
	}
}
