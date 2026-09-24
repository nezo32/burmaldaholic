package dev.nezo.burmaldaholic.games.extras.client.pvp.scratch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.CoreSounds;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.extras.client.pvp.PvpModeScreen;
import dev.nezo.burmaldaholic.games.extras.client.pvp.PvpModeView;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ShowdownCard;
import dev.nezo.burmaldaholic.games.extras.pvp.scratch.ShowdownCard.Sym;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Scratch Showdown match screen (PVP.md §8.5), 400 × 240: up to six mini-cards (3 × 3 cells at 20 px,
 * silver until scratched) in a 3 × 2 grid, or two big cards (40 px cells) in a duel; each with name,
 * running score, the feet multiplier badge and ALL-IN. The current cell pulses on every card; a Creeper
 * chars the burned cell, a Rabbit's Foot turns the border gold. Top bar "SCRATCH SHOWDOWN · Cell 5/9 · Pot 400".
 */
public final class ScratchShowdownScreen extends PvpModeScreen {
	private static final int SILVER = 0xFFB8B8C0;
	private static final int CHARRED = 0xFF1A1A1A;
	private static final ItemStack[] ICONS = {new ItemStack(Items.COAL), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT),
		new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHER_STAR), new ItemStack(Items.CREEPER_HEAD),
		new ItemStack(Items.RABBIT_FOOT)};

	private @Nullable Component banner;
	private int bannerUntil;

	public ScratchShowdownScreen(JsonObject first) {
		super(Component.translatable("gui.burmaldaholic.pvp.scratch.title"), "gui.burmaldaholic.pvp.scratch.scratch_now",
			List.of("gui.burmaldaholic.pvp.scratch.rules.1", "gui.burmaldaholic.pvp.scratch.rules.2", "gui.burmaldaholic.pvp.scratch.rules.3",
				"gui.burmaldaholic.pvp.scratch.rules.4", "gui.burmaldaholic.pvp.scratch.tiebreak"), first);
	}

	/** Cells revealed so far (0…9). */
	private int revealed() {
		if (view.settled()) {
			return ShowdownCard.CELLS;
		}
		PvpModeView.StepView s = view.lastOf("cell");
		return s == null ? 0 : s.round() + 1;
	}

	@Override
	protected List<Component> topBar() {
		List<Component> parts = new ArrayList<>();
		parts.add(Component.translatable("gui.burmaldaholic.pvp.scratch.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		PvpModeView.StepView s = view.last();
		int cell = s == null ? 1 : Math.min(ShowdownCard.CELLS, s.round() + 1);
		parts.add(Component.translatable("gui.burmaldaholic.pvp.scratch.cell", Texts.number(cell)));
		parts.add(Component.translatable("gui.burmaldaholic.pvp.lobby.pot", Texts.chips(view.pot())));
		return parts;
	}

	/** Board per seat: symbol per cell (−1 = unscratched), burned flags, score, multiplier. */
	private record Board(int[] cells, boolean[] burned, long score, int mult) {}

	private Board[] boards() {
		int n = view.size();
		Board[] out = new Board[n];
		int[][] cells = new int[n][ShowdownCard.CELLS];
		boolean[][] burned = new boolean[n][ShowdownCard.CELLS];
		long[] scores = new long[n];
		int[] mult = new int[n];
		for (int[] c : cells) {
			Arrays.fill(c, -1);
		}
		Arrays.fill(mult, 1);
		for (PvpModeView.StepView s : view.steps()) {
			if (!s.kind().equals("cell")) {
				continue;
			}
			int c = s.round();
			int[] symbols = PvpModeView.ints(s.data(), "symbols");
			long[] sc = PvpModeView.longs(s.data(), "scores");
			int[] m = PvpModeView.ints(s.data(), "mult");
			for (int i = 0; i < n; i++) {
				if (c >= 0 && c < ShowdownCard.CELLS && i < symbols.length) {
					cells[i][c] = symbols[i];
				}
				if (i < sc.length) {
					scores[i] = sc[i];
				}
				if (i < m.length) {
					mult[i] = m[i];
				}
			}
			if (s.data().has("burns")) {
				for (JsonElement e : s.data().getAsJsonArray("burns")) {
					JsonObject b = e.getAsJsonObject();
					int seat = PvpModeView.seat(b);
					int cell = (int) PvpModeView.eventLong(b, "cell", -1);
					if (seat >= 0 && seat < n && cell >= 0 && cell < ShowdownCard.CELLS) {
						burned[seat][cell] = true;
					}
				}
			}
		}
		if (view.settled()) {
			long[] pts = view.outcomePoints();
			for (JsonObject e : view.events("final_cell")) {
				int seat = PvpModeView.seat(e);
				if (seat >= 0 && seat < n) {
					cells[seat][ShowdownCard.CELLS - 1] = (int) PvpModeView.eventLong(e, "symbol", -1);
					mult[seat] = (int) PvpModeView.eventLong(e, "mult", mult[seat]);
				}
			}
			for (JsonObject e : view.events("creeper")) {
				int seat = PvpModeView.seat(e);
				int cell = (int) PvpModeView.eventLong(e, "cell", -1);
				if (seat >= 0 && seat < n && cell >= 0 && cell < ShowdownCard.CELLS) {
					burned[seat][cell] = true;
				}
			}
			for (int i = 0; i < n && i < pts.length; i++) {
				scores[i] = pts[i];
			}
		}
		for (int i = 0; i < n; i++) {
			out[i] = new Board(cells[i], burned[i], scores[i], mult[i]);
		}
		return out;
	}

	@Override
	protected void onStep(PvpModeView.StepView step) {
		if (step.kind().equals("cell")) {
			play(CoreSounds.SCRATCH, 1.0f, 0.8f);
		} else if (step.kind().equals("cell_events")) {
			JsonObject d = step.data();
			if (d.has("creepers") && !d.getAsJsonArray("creepers").isEmpty()) {
				JsonObject c = d.getAsJsonArray("creepers").get(0).getAsJsonObject();
				int symbol = (int) PvpModeView.eventLong(c, "symbol", 0);
				showBanner(Component.translatable("msg.burmaldaholic.pvp.scratch.creeper", view.name(PvpModeView.seat(c)),
					Component.translatable(Sym.of(Math.max(0, Math.min(Sym.VALUED - 1, symbol))).key())));
				play(SoundEvents.CREEPER_PRIMED, 1.0f, 0.6f);
			} else if (d.has("fizzles") && !d.getAsJsonArray("fizzles").isEmpty()) {
				showBanner(Component.translatable("msg.burmaldaholic.pvp.scratch.fizzle",
					view.name(PvpModeView.seat(d.getAsJsonArray("fizzles").get(0).getAsJsonObject()))));
				play(SoundEvents.CREEPER_PRIMED, 1.0f, 0.6f);
			} else if (d.has("feet") && !d.getAsJsonArray("feet").isEmpty()) {
				JsonObject f = d.getAsJsonArray("feet").get(0).getAsJsonObject();
				showBanner(Component.translatable("msg.burmaldaholic.pvp.scratch.foot", view.name(PvpModeView.seat(f)),
					Texts.number(PvpModeView.eventLong(f, "mult", 2))));
				play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
			}
		}
	}

	private void showBanner(Component c) {
		banner = c;
		bannerUntil = ticks + 60;
	}

	@Override
	protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Board[] boards = boards();
		int n = boards.length;
		int current = waiting() ? revealed() : -1;
		boolean big = n <= 2;
		int cell = big ? 40 : 20;
		int cardW = 3 * cell + 4;
		int colW = big ? W / 2 : W / 3;
		int[] winners = view.winners();
		for (int i = 0; i < n; i++) {
			int col = big ? i : i % 3;
			int rowIdx = big ? 0 : i / 3;
			int x = left + col * colW + (colW - cardW) / 2;
			int y = top + 20 + rowIdx * (3 * cell + 28);
			boolean won = false;
			for (int w : winners) {
				won |= w == i;
			}
			// header: name / score + badges
			g.text(font, fit(nameTag(i), Math.max(cardW, colW - 8)), x, y, TEXT, true);
			Component score = Component.translatable("gui.burmaldaholic.pvp.scratch.score", Texts.number(boards[i].score()));
			g.text(font, score, x, y + 10, won ? GOLD : MUTED, true);
			if (boards[i].mult() > 1) {
				Component badge = Component.translatable("gui.burmaldaholic.pvp.scratch.multiplier", Texts.number(boards[i].mult()));
				g.text(font, badge, x + cardW - font.width(badge), y + 10, GOLD, true);
			}
			int gy = y + 21;
			int border = boards[i].mult() > 1 ? 0xFFFFD700 : 0xFF404040;
			g.fill(x, gy, x + cardW, gy + 3 * cell + 4, border);
			g.fill(x + 1, gy + 1, x + cardW - 1, gy + 3 * cell + 3, 0xFF2A2A2A);
			for (int c = 0; c < ShowdownCard.CELLS; c++) {
				int cx = x + 2 + (c % 3) * cell;
				int cy = gy + 2 + (c / 3) * cell;
				int sym = boards[i].cells()[c];
				if (sym < 0) {
					int color = SILVER;
					if (c == current && (ticks / 5) % 2 == 0) {
						color = 0xFFE8E8F0;
					}
					g.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, color);
					continue;
				}
				boolean charred = boards[i].burned()[c];
				g.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, charred ? CHARRED : 0xFFEFE6D0);
				if (sym < ICONS.length) {
					float scale = (cell - 4) / 16f;
					g.pose().pushMatrix();
					g.pose().translate(cx + 2, cy + 2);
					g.pose().scale(scale, scale);
					g.item(ICONS[sym], 0, 0);
					g.pose().popMatrix();
				}
				if (charred) {
					// cracks over the burned cell
					for (int k = 2; k < cell - 2; k++) {
						g.fill(cx + k, cy + k, cx + k + 1, cy + k + 1, 0xFFFF6020);
						g.fill(cx + cell - k - 1, cy + k, cx + cell - k, cy + k + 1, 0xCC000000);
					}
				}
			}
		}
		int by = top + H - 37;
		if (view.settled()) {
			Component result = winners.length == 1
				? Component.translatable("gui.burmaldaholic.pvp.result.winner_title", view.name(winners[0])).withStyle(ChatFormatting.GOLD)
				: Component.translatable("gui.burmaldaholic.pvp.result.dead_heat_title").withStyle(ChatFormatting.GOLD);
			g.centeredText(font, result, left + W / 2, by, GOLD);
		} else if (banner != null && ticks < bannerUntil) {
			g.centeredText(font, banner, left + W / 2, by, GOLD);
		} else {
			PvpModeView.StepView s = view.last();
			if (s != null && s.kind().equals("cell_wait")) {
				g.centeredText(font, Component.translatable("gui.burmaldaholic.pvp.scratch.auto_in", seconds(ticksLeft(s))), left + W / 2, by, MUTED);
			}
		}
	}
}
