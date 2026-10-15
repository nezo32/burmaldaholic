package dev.nezo.burmaldaholic.worldgen.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.worldgen.logic.Assembly;
import dev.nezo.burmaldaholic.worldgen.logic.CasinoPack;
import dev.nezo.burmaldaholic.worldgen.logic.Geometry;
import dev.nezo.burmaldaholic.worldgen.logic.Vec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The casino fit's probe sites (R-CB M6b/M7): rotation convention identical to the assembler's (= vanilla's), the
 * start-piece site (what the prefit samples) lies inside every assembly in every rotation, the door throat lies in
 * front of the door outside the building, the outline is sampled every ≤ 4 blocks, and the point list is golden.
 */
class FitSiteTest {
	private static FitSite.Box flat(Geometry.Box b) {
		return new FitSite.Box(b.minX(), b.minZ(), b.maxX(), b.maxZ());
	}

	private static boolean inAny(List<FitSite.Box> boxes, int x, int z) {
		for (FitSite.Box b : boxes) {
			if (b.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void rotationMatchesTheAssembler() {
		for (int turns = 0; turns < 4; turns++) {
			for (Vec size : List.of(new Vec(31, 20, 17), new Vec(41, 24, 41), new Vec(17, 15, 13))) {
				assertEquals(flat(Geometry.templateBox(new Vec(32, 64, -48), size, turns)),
					FitSite.templateBox(32, -48, size.x(), size.z(), turns), size + " turns " + turns);
			}
		}
	}

	@Test
	void startSiteLiesInsideEveryAssemblyInEveryRotation() {
		List<Assembly.Result> all = Assembly.all(CasinoPack.layouts());
		for (Assembly.Result r : all) {
			Vec size = r.pieces().getFirst().layout().size();
			for (int turns = 0; turns < 4; turns++) {
				List<FitSite.Box> rotated = new ArrayList<>();
				for (Assembly.Placed p : r.pieces()) {
					// the whole assembly turns rigidly about the start piece's origin (vanilla: one rotation per start)
					Geometry.Box b = p.box();
					Vec a = Geometry.rotate(new Vec(b.minX(), 0, b.minZ()), turns);
					Vec c = Geometry.rotate(new Vec(b.maxX(), 0, b.maxZ()), turns);
					rotated.add(new FitSite.Box(Math.min(a.x(), c.x()), Math.min(a.z(), c.z()), Math.max(a.x(), c.x()), Math.max(a.z(), c.z())));
				}
				FitSite.Box start = FitSite.templateBox(0, 0, size.x(), size.z(), turns);
				assertEquals(rotated.getFirst(), start, r.style() + "/" + r.name() + " turns " + turns);
				String where = r.style() + "/" + r.name() + " turns " + turns;
				for (int[] p : FitSite.points(List.of(start), List.of())) {
					assertTrue(inAny(rotated, p[0], p[1]), where + ": prefit probe " + p[0] + "," + p[1] + " outside the assembly");
				}
				List<int[]> throat = FitSite.throat(0, 0, size.x(), size.z(), turns);
				assertEquals(9, throat.size());
				for (int[] t : throat) {
					assertFalse(inAny(rotated, t[0], t[1]), where + ": door throat " + t[0] + "," + t[1] + " inside the building");
					int dx = Math.max(Math.max(start.minX() - t[0], t[0] - start.maxX()), 0);
					int dz = Math.max(Math.max(start.minZ() - t[1], t[1] - start.maxZ()), 0);
					assertTrue(Math.max(dx, dz) >= 1 && Math.max(dx, dz) <= FitSite.THROAT_DEPTH, where + ": throat " + t[0] + "," + t[1]);
				}
			}
		}
	}

	@Test
	void outlineIsSampledEveryFourBlocksAndSharedWallsAreSkipped() {
		FitSite.Box hall = new FitSite.Box(0, 0, 40, 20);
		FitSite.Box rear = new FitSite.Box(10, -13, 30, -1);
		List<FitSite.Box> boxes = List.of(hall, rear);
		List<int[]> pts = FitSite.points(boxes, List.of());
		for (FitSite.Box b : boxes) {
			for (int x = b.minX(); x <= b.maxX(); x++) {
				for (int z : new int[] {b.minZ(), b.maxZ()}) {
					if (hall.strictlyInside(x, z) || rear.strictlyInside(x, z)) {
						continue;
					}
					assertTrue(nearest(pts, x, z) <= FitSite.SPACING / 2 + 1, "outline " + x + "," + z + " unsampled");
				}
			}
		}
		for (int[] p : pts) {
			assertFalse(hall.strictlyInside(p[0], p[1]) && !(p[0] == hall.centerX() && p[1] == hall.centerZ()), "interior probe " + p[0] + "," + p[1]);
		}
		// coarse sets are subsets of the fine one (shared columns)
		for (int[] c : FitSite.points(boxes, List.of(), 16)) {
			assertTrue(pts.stream().anyMatch(p -> p[0] == c[0] && p[1] == c[1]), "coarse probe " + c[0] + "," + c[1] + " not in the fine set");
		}
	}

	private static int nearest(List<int[]> pts, int x, int z) {
		int best = Integer.MAX_VALUE;
		for (int[] p : pts) {
			best = Math.min(best, Math.abs(p[0] - x) + Math.abs(p[1] - z));
		}
		return best;
	}

	@Test
	void goldenAndDeterministic() {
		List<FitSite.Box> boxes = List.of(FitSite.templateBox(0, 0, 41, 41, 1));
		List<int[]> throat = FitSite.throat(0, 0, 41, 41, 1);
		List<int[]> a = FitSite.points(boxes, throat);
		List<int[]> b = FitSite.points(boxes, throat);
		assertEquals(a.size(), b.size());
		for (int i = 0; i < a.size(); i++) {
			assertEquals(a.get(i)[0], b.get(i)[0]);
			assertEquals(a.get(i)[1], b.get(i)[1]);
		}
		// 41×41 turned once: x -40..0, z 0..40; door on the template's +z face → world −x side, throat x −43..−41
		assertEquals(new FitSite.Box(-40, 0, 0, 40), boxes.getFirst());
		assertEquals("-40,0 0,40 0,0 -40,40", str(a.subList(0, 4)));
		assertEquals("-41,19 -41,20 -41,21 -42,19 -42,20 -42,21 -43,19 -43,20 -43,21", str(a.subList(4, 13)));
		assertEquals(4 + 9 + 4 * 9 + 1, a.size()); // corners, throat, outline (11 per side incl. the corners), centre
	}

	private static String str(List<int[]> pts) {
		StringBuilder sb = new StringBuilder();
		for (int[] p : pts) {
			sb.append(sb.isEmpty() ? "" : " ").append(p[0]).append(',').append(p[1]);
		}
		return sb.toString();
	}
}
