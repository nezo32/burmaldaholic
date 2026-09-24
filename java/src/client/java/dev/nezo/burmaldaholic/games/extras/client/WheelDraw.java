package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.games.extras.logic.Wheel;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The wheel block of visual/extras.md §4 (shared by Wheel of Fortune and Wheel Party): the rotating face (the baked
 * {@code wheel_face.png} for the Appendix B list, else wedges and icons drawn at run time), the static rim with its 24
 * bulbs, the hub, the flapper on its pivot and the stand. Block-local coordinates: rim 184² at (0, 0), face centre
 * (92, 92), flapper pivot (92, 5), stand at (28, 168).
 */
public final class WheelDraw {
	public static final Identifier FACE = Kit.sheet("extras/wheel_face");
	public static final Identifier RIM = Kit.sheet("extras/wheel_rim");
	public static final Identifier BULBS = Kit.sheet("extras/wheel_bulbs");
	public static final Identifier ICONS_8 = Kit.sheet("extras/wheel_icons_8");
	public static final Identifier ICONS_16 = Kit.sheet("extras/wheel_icons");
	public static final Identifier ICONS_40 = Kit.sheet("extras/wheel_icons_40");
	public static final String CODES = "BCHMDTEX";
	public static final int C = 92;
	public static final int R_FACE = 80;

	private WheelDraw() {}

	public static int iconIndex(String code) {
		int i = CODES.indexOf(code);
		return Math.max(0, i);
	}

	public static void stand(GuiGraphicsExtractor g, int wx, int wy) {
		Kit.sprite(g, Kit.extras("wheel_stand"), wx + C - 64, wy + 168, 128, 52);
	}

	/** The face rotated by {@code theta} degrees (clockwise). The baked texture when the list is Appendix B. */
	public static void face(GuiGraphicsExtractor g, int wx, int wy, List<String> segments, double theta) {
		g.pose().pushMatrix();
		g.pose().translate(wx + C, wy + C);
		g.pose().rotate((float) Math.toRadians(theta));
		if (segments.equals(Wheel.DEFAULT_SEGMENTS)) {
			Kit.region(g, FACE, 160, 160, 0, 0, 160, 160, -80, -80);
		} else {
			int n = segments.size();
			double s = 360.0 / n;
			Kit.disc(g, 0, 0, R_FACE, 0xFF7A4A08);
			for (int i = 0; i < n; i++) {
				wedge(g, i * s, s / 2, 22, R_FACE - 1, Wheel.color(segments.get(i)) | 0xFF000000);
			}
			for (int i = 0; i < n; i++) {
				g.pose().pushMatrix();
				g.pose().rotate((float) Math.toRadians(i * s));
				Kit.region(g, ICONS_8, 64, 8, iconIndex(segments.get(i)) * 8, 0, 8, 8, -4, -68);
				g.pose().popMatrix();
			}
			Kit.disc(g, 0, 0, 22, 0xFF2A1238);
		}
		g.pose().popMatrix();
	}

	/**
	 * A filled wedge (in the current pose, centred at the origin) around the clockwise angle {@code centerDeg} from the
	 * top with half-angle {@code halfDeg}, from radius r0 to r1, as radial bands of rotated fills (no overlap at seams).
	 */
	public static void wedge(GuiGraphicsExtractor g, double centerDeg, double halfDeg, int r0, int r1, int color) {
		g.pose().pushMatrix();
		g.pose().rotate((float) Math.toRadians(centerDeg));
		double tan = Math.tan(Math.toRadians(halfDeg));
		for (int r = r0; r < r1; r += 3) {
			int r2 = Math.min(r1, r + 3);
			int hw = (int) Math.floor(r * tan);
			if (hw <= 0) continue;
			g.fill(-hw, -r2, hw, -r, color);
		}
		g.pose().popMatrix();
	}

	public static void rim(GuiGraphicsExtractor g, int wx, int wy) {
		Kit.region(g, RIM, 184, 184, 0, 0, 184, 184, wx, wy);
	}

	/** 24 bulbs; {@code state(i)} 0 off, 1 warm, 2 gold. */
	public static void bulbs(GuiGraphicsExtractor g, int wx, int wy, java.util.function.IntUnaryOperator state) {
		for (int i = 0; i < 24; i++) {
			double a = Math.toRadians(i * 15 + 7.5);
			int bx = (int) Math.round(C + Math.sin(a) * 85.5 - 0.5);
			int by = (int) Math.round(C - Math.cos(a) * 85.5 - 0.5);
			int st = Math.max(0, Math.min(2, state.applyAsInt(i)));
			Kit.region(g, BULBS, 24, 8, st * 8, 0, 8, 8, wx + bx - 4, wy + by - 4);
		}
	}

	public static void hub(GuiGraphicsExtractor g, int wx, int wy) {
		Kit.sprite(g, Kit.extras("wheel_hub"), wx + C - 14, wy + C - 14, 28, 28);
	}

	/** The flapper at its pivot, deflected by {@code deg} (negative = pushed left). */
	public static void flapper(GuiGraphicsExtractor g, int wx, int wy, double deg) {
		g.pose().pushMatrix();
		g.pose().translate(wx + C, wy + 5);
		g.pose().rotate((float) Math.toRadians(deg));
		Kit.sprite(g, Kit.extras("wheel_flapper"), -7, -4, 14, 24);
		g.pose().popMatrix();
	}
}
