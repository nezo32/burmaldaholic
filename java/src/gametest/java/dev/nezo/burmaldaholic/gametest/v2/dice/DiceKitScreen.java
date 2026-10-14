package dev.nezo.burmaldaholic.gametest.v2.dice;

import dev.nezo.burmaldaholic.client.table.fx.dice.DiceDraw;
import dev.nezo.burmaldaholic.client.table.fx.dice.DieAtlas;
import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DiceSolver;
import dev.nezo.burmaldaholic.core.anim.dice.DiceTray;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.ThrowSpec;
import dev.nezo.burmaldaholic.core.anim.motion.StageMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Test stage of lane DK: for each cell size (14, 22, 32) two dice of one solved duel roll resting at a fixed time and one
 * die of the same roll caught in the air, on plain felt. The draw positions are exposed for the pixel probes of
 * {@link DiceKitClientGameTests}.
 */
public class DiceKitScreen extends Screen {
	static final int FELT = 0xFF2E6B3A;
	static final int[] CELLS = {14, 22, 32};
	static final int[][] FACES = {{5, 3}, {6, 2}, {4, 5}};
	/** Per cell: {restX0, restY0, restX1, restY1, airShadowX, airShadowY} in GUI px (die centres / shadow centre). */
	final double[][] probes = new double[CELLS.length][6];
	final DiceRoll[] rolls = new DiceRoll[CELLS.length];
	final double[] airMs = new double[CELLS.length];
	private final DiceDraw.Scratch scratch = new DiceDraw.Scratch();
	private final DieSample s = new DieSample();

	public DiceKitScreen() {
		super(Component.literal("dice kit"));
		for (int i = 0; i < CELLS.length; i++) {
			rolls[i] = DiceSolver.solve(DiceTray.duelLane(DiceTray.Side.LEFT), ThrowSpec.cup(DiceTray.Side.LEFT), 7 + i, FACES[i], 1200);
			// the first moment die 0 is well up in the air
			for (double t = 0; t < 600; t += 1) {
				rolls[i].sample(0, t, s);
				if (s.phase == DieSample.AIR && s.zLow > 2.2) {
					airMs[i] = t;
					break;
				}
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fill(0, 0, width, height, FELT);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		extractBackground(g, mouseX, mouseY, a);
		int k = Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()));
		double colW = width / (double) CELLS.length;
		for (int i = 0; i < CELLS.length; i++) {
			int cell = CELLS[i];
			int f = DieAtlas.frameSize(cell);
			double h = DiceDraw.halfEdge(cell);
			int cx0 = (int) Math.round(colW * i + colW * 0.25);
			int cx1 = (int) Math.round(colW * i + colW * 0.25 + f + 4);
			int cyRest = (int) Math.round(height * 0.35);
			DiceRoll roll = rolls[i];
			for (int d = 0; d < 2; d++) {
				roll.sample(d, roll.restMs() + 400, s);
				int cx = d == 0 ? cx0 : cx1;
				// a map that puts this die's floor point under (cx, cyRest + lift)
				StageMap base = DiceDraw.stage(0, 0, cell);
				double liftPx = base.lift(s.zc);
				StageMap map = DiceDraw.stage(cx - h * s.x, cyRest + liftPx - base.sy() * s.y, cell);
				DiceDraw.shadow(g, s, map, cell, k, 1);
				DiceDraw.die(g, s, map, cell, k, 0xFFFFFFFF, scratch);
				probes[i][2 * d] = cx;
				probes[i][2 * d + 1] = cyRest;
			}
			// the airborne die: its floor point at (cxAir, cyAir)
			roll.sample(0, airMs[i], s);
			int cxAir = (int) Math.round(colW * i + colW * 0.5);
			int cyAir = (int) Math.round(height * 0.78);
			StageMap air = DiceDraw.stage(cxAir - h * s.x, cyAir - DiceDraw.stage(0, 0, cell).sy() * s.y, cell);
			DiceDraw.shadow(g, s, air, cell, k, 1);
			DiceDraw.die(g, s, air, cell, k, 0xFFFFFFFF, scratch);
			// shadow centre exactly as DiceDraw places it (CRAPS height style: +0.25z, +0.15z, plus 0.15 h down)
			probes[i][4] = cxAir + 0.25 * s.zLow * air.sx();
			probes[i][5] = cyAir + 0.15 * s.zLow * air.sy() + h * 0.15;
		}
	}
}
