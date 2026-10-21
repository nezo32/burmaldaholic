package dev.nezo.burmaldaholic.client.cashier;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.UiSprites;
import dev.nezo.burmaldaholic.client.ui.v2.V2Draw;
import dev.nezo.burmaldaholic.core.anim.Ease;
import dev.nezo.burmaldaholic.core.text.Numbers;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.ui.ChipColumns;
import dev.nezo.burmaldaholic.core.ui.CountingTray;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The counting tray of the Exchange tab (extras.md §8.3, lane J-L2; carried over from the v0.1 cashier screen): every
 * move the server reports ({@code tray} in the cashier state: chip items deposited / withdrawn per denomination, the
 * greedy breakdown for exchanges and store buys) is replayed as chips flying into one well per denomination — from the
 * bottom (your inventory) for deposits and credits, from the balance readout for withdrawals and payments — 280 ms
 * flights, ≤ 70 ms stagger; the stacks then stay with "×N" until the next move. Click to skip; reduce motion shows the
 * final stacks at once. The amount is the server's, never recomputed. The move present when the screen opens is not
 * replayed.
 */
final class CounterTray {
	private static final int N = ChipColumns.DENOMS.length;
	private static final Identifier[] CHIP = new Identifier[N];
	private static final Identifier[] CHIP_SIDE = new Identifier[N];
	private static final String[] DENOM_LABEL = new String[N];
	private static final Component IDLE = Component.translatable("gui.burmaldaholic.cashier.tray");
	private static final Component COUNTING = Component.translatable("gui.burmaldaholic.cashier.counting");

	static {
		for (int i = 0; i < N; i++) {
			CHIP[i] = UiSprites.sprite("fx/chip_" + ChipColumns.DENOMS[i]);
			CHIP_SIDE[i] = UiSprites.sprite("fx/chip_side_" + ChipColumns.DENOMS[i]);
			DENOM_LABEL[i] = CounterDraw.compact(ChipColumns.DENOMS[i]);
		}
	}

	private @Nullable CountingTray tray;
	private long startedAt = -1;
	private boolean fromBottom;
	private Component head = Component.empty();
	private Component chips = Component.empty();
	private final String[] countLabel = new String[N];
	private int headColor;
	/** Last move sequence seen ({@link Integer#MIN_VALUE} = no state yet). */
	private int seq = Integer.MIN_VALUE;
	private int soundsPlayed;

	/** The tray being shown (tests). */
	@Nullable CountingTray current() {
		return tray;
	}

	/** Feeds the cashier state; starts a replay when the move sequence changed. */
	void accept(CompoundTag state) {
		if (!state.contains("balance")) return; // placeholder before the first server state
		CompoundTag move = state.getCompoundOrEmpty("tray");
		int s = move.getIntOr("seq", 0);
		if (seq != Integer.MIN_VALUE && s != seq && s != 0) start(move);
		seq = s;
	}

	private void start(CompoundTag move) {
		long[] counts = new long[N];
		for (int i = 0; i < N; i++) counts[i] = Math.max(0, move.getLongOr(Integer.toString(ChipColumns.DENOMS[i]), 0));
		String kind = move.getStringOr("kind", "deposit");
		long amount = Math.max(0, move.getLongOr("amount", 0));
		tray = new CountingTray(counts);
		startedAt = Util.getMillis();
		soundsPlayed = 0;
		fromBottom = kind.equals("deposit") || kind.equals("credit");
		String key = switch (kind) {
			case "withdraw" -> "gui.burmaldaholic.cashier.withdrawn";
			case "credit" -> "gui.burmaldaholic.cashier.credited";
			case "paid" -> "gui.burmaldaholic.cashier.paid";
			default -> "gui.burmaldaholic.cashier.deposited";
		};
		head = Component.translatable(key, Texts.number(amount));
		headColor = fromBottom ? CasinoPalette.BONUS : CasinoPalette.GOLD;
		chips = Texts.plural("unit.burmaldaholic.chip", tray.chips());
		for (int i = 0; i < N; i++) countLabel[i] = counts[i] > 0 ? "×" + Numbers.format(counts[i]) : ""; // literal-ok: count
	}

	/** True while chips are still flying. */
	boolean counting() {
		return tray != null && Util.getMillis() - startedAt < tray.flightsDoneMs() && !FxSettings.reduceMotion();
	}

	/** Skips to the final stacks; true when there was something to skip. */
	boolean skip() {
		if (!counting()) return false;
		startedAt = Util.getMillis() - tray.flightsDoneMs();
		soundsPlayed = tray.flights();
		return true;
	}

	/**
	 * @param box     the tray area
	 * @param from    where withdrawn / paid chips fly from (the balance readout)
	 * @param bottomY where deposited / credited chips fly from (the screen's bottom edge)
	 */
	void render(GuiGraphicsExtractor g, Font font, Box box, Box from, int bottomY) {
		if (box.isEmpty()) return;
		CasinoUi.inset(g, box.x(), box.y(), box.w(), box.h());
		CountingTray t = tray;
		long age = t == null ? 0 : Util.getMillis() - startedAt;
		boolean reduced = FxSettings.reduceMotion();
		if (t != null && reduced) age = Math.max(age, t.flightsDoneMs());
		boolean counting = t != null && age < t.flightsDoneMs();
		Box headBox = new Box(box.x() + 4, box.y() + 2, box.w() - 8, 10);
		if (t == null) {
			V2Draw.left(g, font, IDLE, headBox, 0, 1, CasinoPalette.BONE_SHADE, false);
		} else {
			Component line = counting ? COUNTING : Component.empty().append(head).append(" · ").append(chips); // literal-ok: separator
			V2Draw.left(g, font, line, headBox, 0, 1, counting ? CasinoPalette.BONE : headColor, false);
		}
		int colW = Math.max(16, (box.w() - 8) / N);
		int colsX = box.x() + (box.w() - colW * N) / 2;
		int base = box.bottom() - 12; // bottom of the lowest disc
		for (int k = 0; k < N; k++) {
			int di = N - 1 - k; // small denominations on the left (the wallet's order)
			int cx = colsX + k * colW + colW / 2;
			int half = Math.min(10, colW / 2 - 1);
			g.fill(cx - half, box.y() + 14, cx + half, base + 1, 0x40000000);
			g.fill(cx - half, base + 1, cx + half, base + 2, 0x30FFFFFF);
			if (t == null || t.count(di) <= 0) {
				V2Draw.centred(g, font, Texts.raw(DENOM_LABEL[di]), new Box(cx - colW / 2, base + 2, colW, 9), 1, CasinoPalette.BONE_SHADE, false);
				continue;
			}
			int landed = 0;
			for (int j = 0; j < t.discs(di); j++) {
				double p = t.flight(di, j, age);
				int slotY = CountingTray.discY(base, j);
				if (p >= 1) {
					CasinoUi.sprite(g, CHIP_SIDE[di], cx - 6, slotY, 12, 3);
					landed++;
					continue;
				}
				if (p < 0) continue;
				double e = Ease.OUT_CUBIC.apply(p);
				double fx = fromBottom ? cx : from.cx();
				double fy = fromBottom ? bottomY : from.cy();
				double px = fx + (cx - fx) * e;
				double py = fy + (slotY - 5 - fy) * e - 20 * 4 * e * (1 - e);
				CasinoUi.sprite(g, CHIP[di], (int) Math.round(px) - 4, (int) Math.round(py), 8, 8);
			}
			if (landed > 0 && landed == t.discs(di)) CasinoUi.sprite(g, CHIP[di], cx - 4, CountingTray.discY(base, landed - 1) - 5, 8, 8);
			String label = counting ? DENOM_LABEL[di] : countLabel[di];
			V2Draw.centred(g, font, Texts.raw(label), new Box(cx - colW / 2, base + 2, colW, 9), 1,
				!counting && t.count(di) > t.discs(di) ? CasinoPalette.GOLD : CasinoPalette.BONE, false);
		}
		if (t != null && !reduced && counting) {
			int landedAll = t.landed(age);
			if (landedAll > soundsPlayed) {
				soundsPlayed = landedAll;
				FxSounds.play("chip_stack", 0.6f, 1f + 0.05f * Math.min(8, landedAll));
			}
		}
	}
}
