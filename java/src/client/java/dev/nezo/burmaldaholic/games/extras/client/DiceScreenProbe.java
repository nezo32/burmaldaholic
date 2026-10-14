package dev.nezo.burmaldaholic.games.extras.client;

import dev.nezo.burmaldaholic.core.anim.dice.DiceRoll;
import dev.nezo.burmaldaholic.core.anim.dice.DieAtlasTable;
import dev.nezo.burmaldaholic.core.anim.dice.DieSample;
import dev.nezo.burmaldaholic.core.anim.dice.DuelTimeline;
import dev.nezo.burmaldaholic.core.anim.dice.Quat;
import dev.nezo.burmaldaholic.games.extras.present.DiceStage;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

/**
 * Read-only test access to the Dice Duel screen for client GameTests (lane DD, DI §10.2): inject a result as if the
 * server had sent it right after a Roll press, and map the drawn dice to layout px. Not used in production.
 */
public final class DiceScreenProbe {
	private DiceScreenProbe() {}

	public static boolean isDice(Screen s) {
		return s instanceof DiceScreen;
	}

	/** House: presses Roll (no packet) and delivers {@code result} at the current clock, as the server's answer would. */
	public static void houseResult(Screen s, CompoundTag result) {
		DiceScreen d = (DiceScreen) s;
		d.press();
		deliver(d, result);
	}

	/** Delivers a result (PvP: the duel's shared clock comes from its {@code start}). */
	public static void deliver(Screen s, CompoundTag result) {
		DiceScreen d = (DiceScreen) s;
		CompoundTag state = d.state().copy();
		state.put("result", result);
		d.acceptState(state);
	}

	/** Duel ms the screen shows now. */
	public static double t(Screen s) {
		return ((DiceScreen) s).t(0);
	}

	/** House: the shake ms at round time 0 (the clock alignment of the flick). */
	public static double offset(Screen s) {
		return show(s).offset;
	}

	private static DiceArena.Show show(Screen s) {
		DiceScreen d = (DiceScreen) s;
		try {
			var f = DiceScreen.class.getDeclaredField("show");
			f.setAccessible(true);
			return (DiceArena.Show) f.get(d);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The stage (layout px, art scale). */
	public static DiceStage stage(Screen s) {
		return ((DiceScreen) s).stage();
	}

	/**
	 * Die {@code die} of pair {@code order} (0 = this screen's own, 1 = the dealer / opponent) at duel ms {@code t}:
	 * {@code {layoutX, layoutY of the drawn centre, phase, atlas frame, artX, artY (floor), top face, zc}}.
	 */
	public static double[] die(Screen s, int order, int die, double t) {
		DiceArena.Show show = show(s);
		DiceStage st = stage(s);
		int round = DuelTimeline.roundAt(t, show.roundCount());
		double rt = t - DuelTimeline.roundStart(round);
		DiceRoll roll = show.rolls[round][order];
		DieSample x = new DieSample();
		roll.sample(die, rt - DuelTimeline.throwAt(0, order), x);
		var map = show.maps[order];
		double ax = map.x(x.x);
		double ay = map.y(x.y) - map.lift(x.zc);
		double[] q = map.isMirrored() ? Quat.mirrorXKeepUp(x.q, new double[4]) : x.q;
		return new double[] {st.x(ax), st.y(ay), x.phase, DieAtlasTable.frame(q), ax, map.y(x.y), roll.topFace(die, rt - DuelTimeline.throwAt(0, order)), x.zc};
	}

	/** The cup frame (Shake.F_*) of pair {@code order} at duel ms {@code t}. */
	public static int cupFrame(Screen s, int order, double t) {
		DiceArena.Show show = show(s);
		int round = DuelTimeline.roundAt(t, show.roundCount());
		double rt = t - DuelTimeline.roundStart(round);
		return show.cup(round, order).frame(rt + show.offset);
	}

	/** Release of die {@code die} of pair {@code order} on the round clock. */
	public static double release(Screen s, int order, int die) {
		return DuelTimeline.throwAt(0, order) + show(s).rolls[0][order].releaseMs(die);
	}

	/** First WALL / BUMPER contact of pair {@code order}'s die 0 on the round clock (−1: none). */
	public static double firstBank(Screen s, int order) {
		DiceRoll r = show(s).rolls[0][order];
		int w = r.wallMs(0);
		return w < 0 ? -1 : DuelTimeline.throwAt(0, order) + w;
	}
}
