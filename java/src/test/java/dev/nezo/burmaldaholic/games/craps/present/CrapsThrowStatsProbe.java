package dev.nezo.burmaldaholic.games.craps.present;

import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DiceSolver;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CrapsThrowStatsProbe {
	@Test
	void stats() {
		for (int target : new int[] {0}) {
			int n = 0, fb = 0, att = 0, feltFirst = 0;
			int[] rest = new int[2000];
			int[] wall = new int[2000];
			int k = 0;
			long t0 = System.nanoTime();
			for (int side = 0; side < 2; side++)
				for (int s = 0; s < 64; s++)
					for (int d1 = 1; d1 <= 6; d1 += 2)
						for (int d2 = 1; d2 <= 6; d2 += 3) {
							int seed = 0x5EED0000 ^ (s * 0x9E3779B1);
							DiceRoll r = CrapsThrow.solve(side, seed, d1, d2);
							int fl = 0; boolean wallSeen0 = false;
							for (var e : r.events()) { if (e.obj() == 0 && e.kind() == dev.nezo.burmaldaholic.core.anim.dice.DiceEvent.WALL) wallSeen0 = true; if (e.obj() == 0 && !wallSeen0 && e.kind() == dev.nezo.burmaldaholic.core.anim.dice.DiceEvent.FLOOR) fl++; }
							if (fl > 0) feltFirst++;
							n++;
							if (r.fallback()) fb++;
							att += r.attempt();
							rest[k] = r.restMs();
							wall[k++] = Math.max(r.wallMs(0), r.wallMs(1));
						}
			int[] rr = Arrays.copyOf(rest, k);
			int[] ww = Arrays.copyOf(wall, k);
			Arrays.sort(rr);
			Arrays.sort(ww);
			System.out.println("target " + target + " n=" + n + " fallback=" + fb + " avgAttempt=" + (att / (double) n) + " rest p0/50/100=" + rr[0] + "/" + rr[k / 2] + "/" + rr[k - 1]
				+ " wall " + ww[0] + "/" + ww[k / 2] + "/" + ww[k - 1] + " feltFirst=" + feltFirst + " ms/solve=" + (System.nanoTime() - t0) / 1e6 / n);
		}
	}
}
