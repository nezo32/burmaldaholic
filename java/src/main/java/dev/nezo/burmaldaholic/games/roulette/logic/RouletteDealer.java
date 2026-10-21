package dev.nezo.burmaldaholic.games.roulette.logic;

/**
 * The roulette table's launch bookkeeping (ROULETTE-ANIM-SPEC §2.2, §2.5), pure so the session behaviour is unit-tested:
 * at every NO_MORE_BETS the dealer takes the rotor where the previous model has it at that tick (never a reset), pushes it
 * the other way than last time and launches the ball with a fresh cosmetic seed. The result is NOT drawn here: it is still
 * drawn at NO_MORE_BETS → SPIN by {@link RouletteRound} with the same single {@code OddsService} call.
 *
 * <p>The published rotor state is quantised to the wire (centi-degrees, centi-degrees per second) and the dealer keeps the
 * quantised model, so the server and every client sample bit-identical rotors.
 */
public final class RouletteDealer {
	/** A launch: the spin counter, the cosmetic seed, the launch tick, the pushed rotor model and its direction. */
	public record Launch(int seq, int seed, long launchTick, RotorModel rotor, int dir) {}

	private int seq;
	private int seed;
	private long launchTick;
	private RotorModel model;

	/** A table that has not spun yet: the rotor coasts at idle speed from an angle of {@code tableSeed}. */
	public RouletteDealer(int tableSeed) {
		RotorModel idle = RotorModel.idle(tableSeed);
		this.model = new RotorModel(RotorModel.centiAngle(idle.angle0()) / 100.0, RotorModel.centiOmega(idle.omega0()) / 100.0, idle.dir(), false);
	}

	/** Launches the next spin at {@code now} with the cosmetic {@code spinSeed}. */
	public Launch launch(long now, int spinSeed) {
		double t = (now - launchTick) * 50.0;
		int a = RotorModel.centiAngle(model.angle(t));
		int w = RotorModel.centiOmega(model.omega(t));
		seq++;
		int dir = (seq & 1) == 0 ? 1 : -1;
		model = RotorModel.launch(a / 100.0, w / 100.0, dir);
		launchTick = now;
		seed = spinSeed;
		return new Launch(seq, seed, now, model, dir);
	}

	/** Rotor angle (deg) at game tick {@code tick} on the current model. */
	public double rotorAngleAt(long tick) {
		return model.angle((tick - launchTick) * 50.0);
	}

	public int seq() {
		return seq;
	}

	public int seed() {
		return seed;
	}

	public long launchTick() {
		return launchTick;
	}

	public RotorModel model() {
		return model;
	}

	/** The rotor at the launch as published (centi-degrees). */
	public int rotorCentiAngle() {
		return RotorModel.centiAngle(model.angle0());
	}

	/** The rotor speed at the launch as published (centi-degrees per second). */
	public int rotorCentiOmega() {
		return RotorModel.centiOmega(model.omega0());
	}
}
