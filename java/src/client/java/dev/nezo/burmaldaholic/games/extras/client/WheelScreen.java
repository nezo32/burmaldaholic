package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.Wheel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Wheel of Fortune screen (UI.md §9): the 54-segment wheel with a pointer (drawn with rotated fills), the
 * segment legend, bet selector (chips or pawn stake) and [Spin]. The wheel eases onto the server-drawn index
 * over {@code spin_ticks}; the result line appears when it stops.
 */
final class WheelScreen extends ExtrasTableScreen {
	private static final int R = 56;
	private static final int CX = PAD + R + 4;
	private static final int CY = 22 + R;
	private static final int RIGHT_X = CX + R + 12;
	private static long lastAmount = 1;

	private final BetSelector bet;
	private double angle;
	private double startAngle;
	private double targetAngle;
	private int animStart = -1000;
	private int shownSeq = -1;
	private int legendBottom;

	WheelScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable("gui.burmaldaholic.extras.wheel.title"), 340, 236);
		this.bet = new BetSelector(true, lastAmount, this::rebuild);
	}

	private List<String> segments() {
		ListTag list = state().getListOrEmpty("segments");
		List<String> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			out.add(list.getStringOr(i, "B"));
		}
		return out.isEmpty() ? Wheel.DEFAULT_SEGMENTS : out;
	}

	private int spinTicks() {
		return state().getIntOr("spin_ticks", 80);
	}

	private boolean spinning() {
		return ticks - animStart < spinTicks();
	}

	@Override
	protected void stateArrived(CompoundTag newState) {
		CompoundTag r = newState.getCompoundOrEmpty("result");
		int seq = r.getIntOr("seq", -1);
		int n = Math.max(2, newState.getListOrEmpty("segments").size());
		double step = 360.0 / n;
		if (shownSeq < 0 && seq >= 0) {
			// Screen (re)opened: show the last result at rest.
			shownSeq = seq;
			angle = -r.getIntOr("index", 0) * step;
			return;
		}
		if (seq >= 0 && seq != shownSeq) {
			shownSeq = seq;
			startAngle = angle;
			double rest = Math.floorMod((long) Math.round(-r.getIntOr("index", 0) * step * 1000), 360_000L) / 1000.0;
			double from = ((startAngle % 360) + 360) % 360;
			double delta = ((rest - from) % 360 + 360) % 360;
			targetAngle = startAngle + 3 * 360 + delta;
			animStart = ticks;
		}
	}

	@Override
	protected int layout() {
		CompoundTag s = state();
		int rightW = imageWidth - RIGHT_X - PAD;
		int y = 20;
		for (Component row : legendRows()) {
			y += wrappedHeight(row, rightW - 10) + 1;
		}
		legendBottom = y;
		y += 4;
		int betLineY = y;
		y += 24;
		Flow flow = flow(RIGHT_X, y, rightW);
		bet.build(flow, s);
		flow.newRow();
		flow.button(Component.translatable("gui.burmaldaholic.common.spin").withStyle(ChatFormatting.BOLD), 60, b -> {
			lastAmount = Math.max(1, bet.amount());
			sendAction("spin", bet.args());
		}).active = !spinning();
		this.betLineY = betLineY;
		int leftBottom = topPos + CY + R + 8 + 22;
		return Math.max(flow.bottom(), leftBottom);
	}

	private int betLineY;

	private List<Component> legendRows() {
		List<Component> rows = new ArrayList<>();
		Map<String, Integer> counts = new TreeMap<>();
		List<String> segs = segments();
		segs.forEach(c -> counts.merge(c, 1, Integer::sum));
		CompoundTag mult = state().getCompoundOrEmpty("multipliers");
		List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
		entries.sort((a, b) -> {
			int c = Double.compare(mult.getDoubleOr(a.getKey(), 0), mult.getDoubleOr(b.getKey(), 0));
			return c != 0 ? c : Boolean.compare(Wheel.CREEPER.equals(a.getKey()), Wheel.CREEPER.equals(b.getKey()));
		});
		legendCodes.clear();
		for (Map.Entry<String, Integer> e : entries) {
			legendCodes.add(e.getKey());
			rows.add(Component.translatable("gui.burmaldaholic.extras.wheel.legend", Component.translatable(Wheel.segmentKey(e.getKey())),
				Texts.raw(Payouts.formatMultiplier(mult.getDoubleOr(e.getKey(), 0))), Texts.number(e.getValue())));
		}
		return rows;
	}

	private final List<String> legendCodes = new ArrayList<>();

	@Override
	protected void containerTick() {
		super.containerTick();
		if (ticks - animStart == spinTicks()) {
			rebuild();
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag s = state();
		List<String> segs = segments();
		int n = segs.size();
		double step = 360.0 / n;
		if (spinning()) {
			double t = Math.min(1.0, (ticks - animStart + partial) / spinTicks());
			double eased = 1 - Math.pow(1 - t, 3);
			angle = startAngle + (targetAngle - startAngle) * eased;
		} else if (animStart > -1000) {
			angle = targetAngle;
		}
		// wheel
		Art.disc(g, CX, CY, R + 3, 1.0, 0xFF5A3A1A);
		int hw = (int) Math.ceil(R * Math.sin(Math.toRadians(step / 2))) + 1;
		for (int k = 0; k < n; k++) {
			double a = angle + k * step - 90;
			g.pose().pushMatrix();
			g.pose().translate(CX, CY);
			g.pose().rotate((float) Math.toRadians(a));
			g.fill(0, -hw, R, hw, Wheel.color(segs.get(k)));
			g.fill(R - 3, -1, R, 1, 0x55000000);
			g.pose().popMatrix();
		}
		Art.disc(g, CX, CY, 10, 1.0, 0xFF3A2410);
		Art.disc(g, CX, CY, 6, 1.0, GOLD);
		Art.pointerDown(g, CX, CY - R + 8, 9, 0xFFFFFFFF);
		// result / status under the wheel
		CompoundTag r = s.getCompoundOrEmpty("result");
		Component status = null;
		if (spinning()) {
			status = Component.translatable("gui.burmaldaholic.extras.wheel.spinning");
		} else if (r.getIntOr("seq", -1) >= 0) {
			String code = r.getStringOr("code", "B");
			status = Component.translatable("gui.burmaldaholic.extras.wheel.result", Component.translatable(Wheel.segmentKey(code)), resultLine(r.getLongOr("net", 0)));
		}
		if (status != null) {
			graphicsWrapped(g, status, PAD, CY + R + 8, RIGHT_X - 2 * PAD, MUTED);
		}
		// legend
		int rightW = imageWidth - RIGHT_X - PAD;
		int y = 20;
		List<Component> rows = legendRows();
		for (int i = 0; i < rows.size(); i++) {
			g.fill(RIGHT_X, y + 1, RIGHT_X + 7, y + 8, Wheel.color(legendCodes.get(i)));
			Art.wrap(g, font, rows.get(i), RIGHT_X + 10, y, rightW - 10, 0xFFFFFFFF);
			y += wrappedHeight(rows.get(i), rightW - 10) + 1;
		}
		g.text(font, bet.line(s), RIGHT_X, betLineY, 0xFFFFFFFF, true);
		g.text(font, limitsLine(), RIGHT_X, betLineY + 11, MUTED, true);
	}

	private void graphicsWrapped(GuiGraphicsExtractor g, Component text, int x, int y, int w, int color) {
		Art.wrap(g, font, text, x, y, w, color);
	}

	static Component resultLine(long net) {
		if (net > 0) {
			return Component.translatable("gui.burmaldaholic.common.result.win", Texts.chips(net)).withStyle(ChatFormatting.GREEN);
		}
		if (net < 0) {
			return Component.translatable("gui.burmaldaholic.common.result.loss", Texts.chips(-net)).withStyle(ChatFormatting.RED);
		}
		return Component.translatable("gui.burmaldaholic.common.result.push").withStyle(ChatFormatting.GRAY);
	}
}
