package dev.nezo.burmaldaholic.gametest.v2.dice;

import dev.nezo.burmaldaholic.client.table.fx.dice.DieAtlas;
import dev.nezo.burmaldaholic.core.anim.dice.DieAtlasTable;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Phase 2b client GameTests of lane DK (DCa; PHASE2B §4): the dice kit drawn on a real client at GUI 427×240,
 * 480×270 and 960×540 ({@link DiceKitScreen}: cells 14 / 22 / 32, two resting dice and one airborne die each, fixed
 * time). Probes on the real framebuffer: a resting die is exactly {@code cell·k} physical px wide (crisp 1:1 atlas
 * blit), its top face shows the server's pip count, and the floor shadow of the airborne die is ≥ 20 % darker than the
 * felt. Shots {@code p2b_dca_dice_kit_en_<w>}. Run alone with {@code BURMALDAHOLIC_CGT=DK}.
 */
public class DiceKitClientGameTests implements FabricClientGameTest {
	private static final int[][] SIZES = {{1280, 720, 3}, {960, 540, 2}, {1920, 1080, 2}};

	@Override
	public void runTest(ClientGameTestContext context) {
		String lane = System.getenv("BURMALDAHOLIC_CGT");
		if (lane != null && !lane.isBlank() && !lane.contains("DK")) return;
		List<String> errors = new ArrayList<>();
		try {
			for (int[] sz : SIZES) {
				context.getInput().resizeWindow(sz[0], sz[1]);
				context.runOnClient(mc -> {
					mc.options.guiScale().set(sz[2]);
					mc.resizeGui();
				});
				context.waitTicks(2);
				context.setScreen(DiceKitScreen::new);
				context.waitForScreen(DiceKitScreen.class);
				context.waitTicks(3);
				int[] gui = context.computeOnClient(mc -> new int[] {mc.gui.screen().width, mc.gui.screen().height,
					(int) Math.round(mc.getWindow().getGuiScale()), mc.getWindow().getWidth(), mc.getWindow().getHeight()});
				Path shot = context.takeScreenshot("p2b_dca_dice_kit_en_" + gui[0]);
				double[][] probes = context.computeOnClient(mc -> ((DiceKitScreen) mc.gui.screen()).probes.clone());
				int[][] restFrames = context.computeOnClient(mc -> {
					DiceKitScreen s = (DiceKitScreen) mc.gui.screen();
					int[][] out = new int[DiceKitScreen.CELLS.length][2];
					DieSample x = new DieSample();
					for (int i = 0; i < out.length; i++)
						for (int d = 0; d < 2; d++) {
							s.rolls[i].sample(d, s.rolls[i].restMs() + 400, x);
							out[i][d] = DieAtlasTable.frame(x.q);
						}
					return out;
				});
				BufferedImage img;
				try {
					img = ImageIO.read(shot.toFile());
				} catch (IOException e) {
					throw new AssertionError("screenshot " + shot, e);
				}
				String at = gui[0] + "x" + gui[1] + " k" + gui[2] + " (window " + gui[3] + "x" + gui[4] + ")";
				System.out.println("[DK] " + at + " shot " + shot);
				probe(img, gui[2], probes, restFrames, at, errors);
			}
		} finally {
			context.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
				mc.gui.setScreen(null);
			});
		}
		if (!errors.isEmpty()) throw new AssertionError("dice kit probes:\n" + String.join("\n", errors));
	}

	private static int lum(int rgb) {
		return (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 1000;
	}

	private static boolean felt(int rgb) {
		int f = DiceKitScreen.FELT;
		return Math.abs(((rgb >> 16) & 255) - ((f >> 16) & 255)) < 24 && Math.abs(((rgb >> 8) & 255) - ((f >> 8) & 255)) < 24
			&& Math.abs((rgb & 255) - (f & 255)) < 24;
	}

	static void probe(BufferedImage img, int k, double[][] probes, int[][] restFrames, String at, List<String> errors) {
		int feltLum = lum(DiceKitScreen.FELT);
		for (int i = 0; i < DiceKitScreen.CELLS.length; i++) {
			int cell = DiceKitScreen.CELLS[i];
			int ci = DieAtlasTable.cellIndex(cell);
			int f = DieAtlas.frameSize(cell);
			for (int d = 0; d < 2; d++) {
				double cx = probes[i][2 * d];
				double cy = probes[i][2 * d + 1];
				int fr = restFrames[i][d];
				int ri = -1;
				for (int r = 0; r < DieAtlasTable.REST_FRAMES.length; r++) if (DieAtlasTable.REST_FRAMES[r] == fr) ri = r;
				if (ri < 0) {
					errors.add(at + " cell " + cell + " die " + d + ": not a rest frame (" + fr + ")");
					continue;
				}
				double[] top = DieAtlasTable.TOP[ci][ri];
				double ox = cx - f / 2.0;
				double oy = cy - f / 2.0;
				// 1. width: the row through the top face centre spans exactly cell·k physical px
				int row = (int) Math.round((oy + (top[1] + top[5]) / 2) * k);
				int x0 = (int) Math.floor(ox * k);
				int x1 = (int) Math.ceil((ox + f) * k);
				int first = -1;
				int last = -1;
				for (int x = x0; x < x1; x++) {
					if (!felt(img.getRGB(x, row))) {
						if (first < 0) first = x;
						last = x;
					}
				}
				int width = last - first + 1;
				if (width != cell * k) errors.add(at + " cell " + cell + " die " + d + ": width " + width + " != " + cell * k);
				// 2. pips: dark clusters inside the top face (inset 2 art px) == the face
				int want = DiceKitScreen.FACES[i][d];
				int qx0 = (int) Math.ceil((ox + Math.min(top[0], top[6]) + 1.5) * k);
				int qx1 = (int) Math.floor((ox + Math.max(top[2], top[4]) - 1.5) * k);
				int qy0 = (int) Math.ceil((oy + Math.min(top[1], top[3]) + 1.5) * k);
				int qy1 = (int) Math.floor((oy + Math.max(top[5], top[7]) - 1.5) * k);
				int clusters = clusters(img, qx0, qy0, qx1, qy1, want == 1);
				if (clusters != want) errors.add(at + " cell " + cell + " die " + d + ": " + clusters + " pips, want " + want);
			}
			// 3. the airborne die's shadow is at least 20 % darker than the felt
			int sx = (int) Math.round(probes[i][4] * k);
			int sy = (int) Math.round(probes[i][5] * k);
			int sum = 0;
			for (int dy = -1; dy <= 1; dy++)
				for (int dx = -1; dx <= 1; dx++) sum += lum(img.getRGB(sx + dx, sy + dy));
			int shadowLum = sum / 9;
			if (shadowLum > feltLum * 0.8) errors.add(at + " cell " + cell + ": shadow luminance " + shadowLum + " vs felt " + feltLum);
		}
	}

	private static int clusters(BufferedImage img, int x0, int y0, int x1, int y1, boolean red) {
		int w = x1 - x0 + 1;
		int h = y1 - y0 + 1;
		if (w <= 0 || h <= 0) return -1;
		boolean[] seen = new boolean[w * h];
		int n = 0;
		ArrayDeque<int[]> st = new ArrayDeque<>();
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				if (seen[y * w + x] || !pip(img.getRGB(x0 + x, y0 + y), red)) continue;
				n++;
				seen[y * w + x] = true;
				st.push(new int[] {x, y});
				while (!st.isEmpty()) {
					int[] p = st.pop();
					for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						int nx = p[0] + dd[0];
						int ny = p[1] + dd[1];
						if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[ny * w + nx] || !pip(img.getRGB(x0 + nx, y0 + ny), red)) continue;
						seen[ny * w + nx] = true;
						st.push(new int[] {nx, ny});
					}
				}
			}
		return n;
	}

	private static boolean pip(int rgb, boolean red) {
		int r = (rgb >> 16) & 255;
		int g = (rgb >> 8) & 255;
		int b = rgb & 255;
		return red ? r > 150 && g < 110 : r + g + b < 200;
	}
}
