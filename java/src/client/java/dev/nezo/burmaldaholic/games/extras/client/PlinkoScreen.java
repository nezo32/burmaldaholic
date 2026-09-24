package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.core.table.CasinoTableMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.ExtrasModule;
import dev.nezo.burmaldaholic.games.extras.logic.Payouts;
import dev.nezo.burmaldaholic.games.extras.logic.Plinko;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Plinko screen (UI.md §9): 12 rows of pegs, 13 bins with the chosen risk's multipliers, risk toggle
 * Low | Medium | High, chip bet selector and [Drop ball]. The ball follows the exact server path, one row every
 * {@code step_ticks} (4) ticks, with a peg sound per row.
 */
final class PlinkoScreen extends ExtrasTableScreen {
	private static final int BIN_W = 22;
	private static final int ROW_H = 8;
	private static final int BOARD_Y = 24;
	private static final int BINS_Y = BOARD_Y + Plinko.ROWS * ROW_H + 2;
	private static long lastAmount = 1;
	private static Plinko.Risk risk = Plinko.Risk.LOW;

	private final BetSelector bet;
	private int shownSeq = -1;
	private int animStart = -1000;
	private int lastStep = -1;
	private int statusY;
	private int betLineY;

	PlinkoScreen(CasinoTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, Component.translatable("gui.burmaldaholic.extras.plinko.title"), 310, 230);
		this.bet = new BetSelector(false, lastAmount, this::rebuild);
	}

	private int stepTicks() {
		return Math.max(1, state().getIntOr("step_ticks", 4));
	}

	private int dropTicks() {
		return stepTicks() * Plinko.ROWS + 4;
	}

	private boolean dropping() {
		return ticks - animStart < dropTicks();
	}

	@Override
	protected void stateArrived(CompoundTag newState) {
		int seq = newState.getCompoundOrEmpty("result").getIntOr("seq", -1);
		if (shownSeq < 0) {
			shownSeq = seq;
			return;
		}
		if (seq >= 0 && seq != shownSeq) {
			shownSeq = seq;
			animStart = ticks;
			lastStep = -1;
		}
	}

	private double[] table(Plinko.Risk r) {
		ListTag tables = state().getListOrEmpty("tables");
		for (int i = 0; i < tables.size(); i++) {
			CompoundTag t = tables.getCompoundOrEmpty(i);
			if (r.id().equals(t.getStringOr("risk", ""))) {
				double[] out = new double[Plinko.BINS];
				for (int b = 0; b < Plinko.BINS; b++) {
					out[b] = t.getDoubleOr("m" + b, 0);
				}
				return out;
			}
		}
		return r.defaults();
	}

	private int boardLeft() {
		return (imageWidth - Plinko.BINS * BIN_W) / 2;
	}

	@Override
	protected int layout() {
		CompoundTag s = state();
		int w = imageWidth - 2 * PAD;
		statusY = BINS_Y + 16;
		int y = statusY + 12;
		Flow flow = flow(PAD, y, w);
		for (Plinko.Risk r : Plinko.Risk.values()) {
			Component label = Component.translatable(r.key());
			flow.button(r == risk ? label.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE) : label, 44, b -> {
				risk = r;
				rebuild();
			}).active = !dropping();
		}
		flow.button(Component.translatable("gui.burmaldaholic.extras.plinko.drop").withStyle(ChatFormatting.BOLD), 60, b -> {
			lastAmount = Math.max(1, bet.amount());
			CompoundTag args = bet.args();
			args.putString("risk", risk.id());
			sendAction("drop", args);
		}).active = !dropping();
		flow.newRow();
		betLineY = flow.y() - topPos;
		flow.gap(12);
		bet.build(flow, s);
		return flow.bottom();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (!dropping() && ticks - animStart == dropTicks()) {
			rebuild();
		}
		if (dropping()) {
			int step = (ticks - animStart) / stepTicks();
			if (step != lastStep && step < Plinko.ROWS && ExtrasModule.PLINKO_PEG_SOUND != null) {
				lastStep = step;
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ExtrasModule.PLINKO_PEG_SOUND, 0.9f + 0.05f * step, 0.4f));
			}
		}
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		CompoundTag s = state();
		CompoundTag r = s.getCompoundOrEmpty("result");
		boolean anim = dropping() && r.getIntOr("seq", -1) >= 0;
		Plinko.Risk shownRisk = anim ? Plinko.Risk.parse(r.getStringOr("risk", risk.id())) : risk;
		if (shownRisk == null) {
			shownRisk = risk;
		}
		double[] table = table(shownRisk);
		int left = boardLeft();
		int center = left + Plinko.BINS * BIN_W / 2;
		// pegs
		for (int row = 0; row < Plinko.ROWS; row++) {
			for (int j = 0; j <= row; j++) {
				int x = (int) Math.round(center + (j - row / 2.0) * BIN_W);
				int y = BOARD_Y + row * ROW_H;
				g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFE8E8E8);
			}
		}
		// bins
		int landed = !anim && r.getIntOr("seq", -1) >= 0 && shownRisk.id().equals(r.getStringOr("risk", "")) ? r.getIntOr("bin", -1) : -1;
		for (int b = 0; b < Plinko.BINS; b++) {
			int x = left + b * BIN_W;
			double m = table[b];
			int color = m >= 10 ? 0xFFB8860B : m >= 2 ? 0xFF2E7D32 : m >= 1 ? 0xFF3D5A80 : 0xFF8E2A2A;
			g.fill(x + 1, BINS_Y, x + BIN_W - 1, BINS_Y + 12, b == landed ? 0xFFFFFFFF : color);
			g.centeredText(font, Texts.decimal(Payouts.formatMultiplier(m)), x + BIN_W / 2, BINS_Y + 2, b == landed ? 0xFF000000 : 0xFFFFFFFF);
		}
		// ball
		if (r.getIntOr("seq", -1) >= 0 && (anim || landed >= 0)) {
			boolean[] path = Plinko.decode(r.getIntOr("path", 0));
			double t = anim ? (ticks - animStart + partial) / stepTicks() : Plinko.ROWS;
			double bx;
			double by;
			if (t >= Plinko.ROWS) {
				bx = center + (r.getIntOr("bin", 6) - Plinko.ROWS / 2.0) * BIN_W;
				by = BINS_Y - 4;
			} else {
				int row = (int) Math.floor(t);
				double frac = t - row;
				double x0 = ballX(center, path, row - 1);
				double x1 = ballX(center, path, row);
				bx = x0 + (x1 - x0) * frac;
				by = BOARD_Y + (row - 1) * ROW_H + ROW_H * frac - 3 - Math.sin(Math.PI * frac) * 3;
			}
			Art.disc(g, (int) Math.round(bx), (int) Math.round(by), 3, 1.0, 0xFFFF4040);
		}
		// status line
		Component status = null;
		if (anim) {
			status = Component.translatable("gui.burmaldaholic.extras.plinko.dropping");
		} else if (r.getIntOr("seq", -1) >= 0) {
			status = Component.translatable("gui.burmaldaholic.extras.plinko.result", Texts.decimal(Payouts.formatMultiplier(r.getDoubleOr("mult", 0))),
				WheelScreen.resultLine(r.getLongOr("net", 0)));
		}
		if (status != null) {
			g.centeredText(font, status, imageWidth / 2, statusY, MUTED);
		}
		g.text(font, bet.line(s), PAD, betLineY, 0xFFFFFFFF, true);
		Component limits = limitsLine();
		g.text(font, limits, imageWidth - PAD - font.width(limits), betLineY, MUTED, true);
	}

	/** Ball x after {@code row} rows (row −1 = start, centered). */
	private static double ballX(int center, boolean[] path, int row) {
		int rights = 0;
		for (int i = 0; i <= row && i < path.length; i++) {
			if (path[i]) {
				rights++;
			}
		}
		return center + (rights - (row + 1) / 2.0) * BIN_W;
	}
}
