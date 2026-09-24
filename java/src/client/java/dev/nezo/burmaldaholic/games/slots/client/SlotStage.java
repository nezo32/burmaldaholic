package dev.nezo.burmaldaholic.games.slots.client;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.anim.Beat;
import dev.nezo.burmaldaholic.core.anim.SeedMix;
import dev.nezo.burmaldaholic.core.anim.Timeline;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import dev.nezo.burmaldaholic.games.slots.client.features.DragonWheelView;
import dev.nezo.burmaldaholic.games.slots.client.features.FreeSpinsView;
import dev.nezo.burmaldaholic.games.slots.client.features.HoardView;
import dev.nezo.burmaldaholic.games.slots.client.features.HuntView;
import dev.nezo.burmaldaholic.games.slots.client.features.TumbleView;
import dev.nezo.burmaldaholic.games.slots.client.features.WildView;
import dev.nezo.burmaldaholic.games.slots.client.fx.ScreenParticles;
import dev.nezo.burmaldaholic.games.slots.client.reels.ReelView;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.logic.MachineDef;
import dev.nezo.burmaldaholic.games.slots.v2.logic.SpinTape;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotFrames;
import dev.nezo.burmaldaholic.games.slots.v2.present.SlotScript;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The animated heart of the slot screen (lane J-L9): plays ONE spin's {@link Timeline} over the reel window —
 * reels (JS1), win show and way paths (JS3), anticipation (JS4), tumbles (JS5), sticky wilds (JS6), free spins
 * (JS7), Treasure Hunt (JS8), Piglin's Hoard (JS9), Dragon Wheel (JS10), jackpots (JS11) and the big-win sequence
 * (JS12) — and exposes the running amounts to the screen's panels. Server decides, the stage only animates to it:
 * every frame comes from {@link SlotFrames} sampled at the {@link SpinClock} time, so the final frame is the tape's
 * window whatever the speed, skip or reduce-motion path (F1–F10).
 *
 * <p>Drawing is split in two strata: {@link #drawWindow} (reels and in-window effects, under the widgets) and
 * {@link #drawOverlays} (feature overlays and celebrations, above the widgets).
 */
public final class SlotStage {
	private final StageHost host;
	private final Machine machine;
	private final MachineDef def;
	private final ScreenParticles particles = new ScreenParticles();
	private final ReelView reels = new ReelView();
	private final WinShow winShow = new WinShow();
	private final TumbleView tumble = new TumbleView();
	private final WildView wild = new WildView();
	private final FreeSpinsView freeSpins = new FreeSpinsView();
	private final HuntView hunt = new HuntView();
	private final HoardView hoard = new HoardView();
	private final DragonWheelView wheel = new DragonWheelView();
	private final JackpotFx jackpots = new JackpotFx();
	private final BigWinFx bigWin = new BigWinFx();

	private int wx;
	private int wy;
	private int cell = 44;
	private int[] restStops = new int[5];
	private int[] restCells;

	private SlotScript script;
	private SlotFrames.Sampler frames;
	private SpinClock clock;
	private SpinTape tape;
	private TimingProfile local = FxSettings.localProfile();
	private int seed;
	private double t;
	private double lastT = -1;
	private long now;
	private long idleSince;
	private boolean finished = true;
	private long lastWin;

	public SlotStage(StageHost host, Machine machine, MachineDef def) {
		this.host = host;
		this.machine = machine;
		this.def = def;
		this.restCells = window(def, restStops);
		this.idleSince = net.minecraft.util.Util.getMillis();
	}

	private static int[] window(MachineDef def, int[] stops) {
		int[] c = new int[15];
		for (int r = 0; r < 5; r++) for (int y = 0; y < 3; y++) c[r * 3 + y] = def.symbolAt(r, stops[r], y);
		return c;
	}

	// ---- setup ------------------------------------------------------------------------------------------------

	/** Reel window top-left (GUI px) and cell size (44 normal, 32 compact). */
	public void layout(int x, int y, int cellSize) {
		this.wx = x;
		this.wy = y;
		this.cell = cellSize;
	}

	/** The window shown at rest (the previous spin's, or the machine's rest window). */
	public void rest(int[] stops, int[] cells) {
		this.restStops = stops.clone();
		this.restCells = cells != null ? cells.clone() : window(def, stops);
	}

	/**
	 * Plays a spin. {@code timeline} is built by the pure builder from the tape; {@code clock} maps screen time to
	 * timeline time. The previous spin (if any) finishes first (overrun rule).
	 */
	public void play(SpinTape tape, Timeline timeline, SpinClock clock, int seed) {
		if (script != null && !finished) finishNow();
		this.tape = tape;
		this.seed = seed;
		this.local = FxSettings.localProfile();
		this.script = new SlotScript(timeline, def, restStops, restCells);
		this.frames = new SlotFrames.Sampler(script);
		this.clock = clock;
		this.lastT = -1;
		this.finished = false;
		particles.seed(SeedMix.mix(seed, 5));
		particles.enabled(!FxSettings.reduceMotion());
		reels.reset(this);
		winShow.reset();
		tumble.reset();
		wild.reset();
		freeSpins.reset();
		hunt.reset(this);
		hoard.reset();
		wheel.reset(this);
		jackpots.reset();
		bigWin.reset(this);
		// interactive points: the hunt waits for picks (the spinning player; everyone while the server waits for them),
		// the wheel for its button (spinning player, locally paced only: the server never waits for it)
		hunt.registerHold(this, host.interactive());
		if (host.interactive() && !host.serverPaced()) wheel.registerHold(this);
		this.playRestStops = restStops.clone();
		this.playRestCells = restCells.clone();
	}

	private int[] playRestStops = new int[5];
	private int[] playRestCells = new int[15];

	/**
	 * The server revealed more of the running spin's tape (the Treasure Hunt ended: the full tape with its total and
	 * jackpots): swap in the new timeline without touching the clock position (its shared part is identical).
	 */
	public void retape(SpinTape tape, Timeline timeline) {
		if (script == null || finished) return;
		this.tape = tape;
		this.script = new SlotScript(timeline, def, playRestStops, playRestCells);
		this.frames = new SlotFrames.Sampler(script);
		this.clock.retarget(timeline);
		frames.sample(t);
		jackpots.reset();
		bigWin.reset(this);
	}

	/** Outcome-free spin-up on click (F1, research §2.11): reels start moving before the tape arrives. */
	public void preRoll(long nowMs) {
		reels.preRoll(this, nowMs);
	}

	/** The action was rejected: decelerate onto the previous window (F1), then the invalid feedback. */
	public void reject(long nowMs) {
		reels.reject(nowMs);
	}

	public boolean preRolling() {
		return reels.preRolling();
	}

	/** Skip / Stop: compresses time to the end of the current step (never skips the result). */
	public void skip() {
		if (clock == null || finished) return;
		if (bigWin.skip(this) || jackpots.skip(this)) return;
		boolean reelsMoving = frames != null && anyMoving();
		clock.skip(now, reelsMoving);
	}

	/** Interrupt (Esc / close / disconnect, F8): jump to the terminal frame at once. */
	public void finishNow() {
		if (clock != null) clock.finish(net.minecraft.util.Util.getMillis());
		if (script != null) {
			t = script.timeline().endMs();
			frames.sample(t);
			settleRest();
		}
		stopLoops();
		finished = true;
	}

	private void settleRest() {
		SlotScript.Phase last = script.phases().isEmpty() ? null : script.phases().get(script.phases().size() - 1);
		if (last != null) {
			int[] stops = new int[5];
			for (int r = 0; r < 5; r++) stops[r] = last.reels[r] != null ? last.reels[r].stop() : last.restStops[r];
			rest(stops, script.finalCells(last));
		}
		if (tape != null) lastWin = tape.totalChips();
	}

	private boolean anyMoving() {
		for (boolean m : frames.moving) if (m) return true;
		return false;
	}

	// ---- per frame --------------------------------------------------------------------------------------------

	/** Advances time, fires the beat cues crossed since the last frame and updates the loops. */
	public void update(long nowMs) {
		this.now = nowMs;
		if (script == null || finished && lastT >= 0 && t >= script.timeline().endMs()) {
			reels.updateIdle(this);
			if (script != null) bigWin.update(this, false);
			return;
		}
		double newT = clock.t(nowMs);
		boolean jump = lastT >= 0 && (newT - lastT > 250 || clock.fastForwarding() || clock.late());
		t = newT;
		frames.sample(t);
		if (lastT < 0) lastT = -0.001;
		for (Beat b : script.timeline().beats()) {
			if (b.at() > t) break;
			if (b.at() > lastT || b.at() == 0 && lastT < 0) {
				if (!jump) cue(b);
			}
		}
		reels.update(this, lastT, t, jump);
		freeSpins.update(this);
		hunt.update(this);
		wheel.update(this, jump);
		bigWin.update(this, jump);
		jackpots.update(this, jump);
		lastT = t;
		if (t >= script.timeline().endMs() && !clock.holding()) {
			if (!finished) {
				finished = true;
				settleRest();
				stopLoops();
			}
		}
		if (!finished) idleSince = nowMs;
	}

	private void cue(Beat b) {
		reels.cue(this, b);
		winShow.cue(this, b);
		tumble.cue(this, b);
		wild.cue(this, b);
		freeSpins.cue(this, b);
		hunt.cue(this, b);
		hoard.cue(this, b);
		wheel.cue(this, b);
		jackpots.cue(this, b);
		bigWin.cue(this, b);
	}

	private void stopLoops() {
		reels.stopLoops();
		freeSpins.stopLoops();
	}

	/** Called when the screen closes. */
	public void close() {
		if (!finished) finishNow();
		stopLoops();
		particles.clear();
	}

	/** Stratum 1: the reel window and the in-window effects (drawn under the widgets). */
	public void drawWindow(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		boolean covered = hunt.covers(this) || hoard.covers(this);
		double coverAlpha = Math.max(hunt.coverAlpha(this), hoard.coverAlpha(this));
		if (!covered || coverAlpha < 1) {
			reels.draw(this, g);
			if (active()) {
				wild.draw(this, g);
				tumble.draw(this, g);
				winShow.draw(this, g);
			}
			reels.drawShading(this, g);
		}
		if (active()) {
			hunt.draw(this, g, mouseX, mouseY);
			hoard.draw(this, g);
		}
		reels.drawGlass(this, g);
	}

	/** Stratum 2/3: feature overlays and celebrations (above the widgets). */
	public void drawOverlays(GuiGraphicsExtractor g, int screenW, int screenH) {
		if (active()) {
			freeSpins.draw(this, g, screenW, screenH);
			wheel.draw(this, g, screenW, screenH);
			tumble.drawFloats(this, g);
			jackpots.draw(this, g, screenW, screenH);
			bigWin.draw(this, g, screenW, screenH);
		}
		particles.draw(g, now);
	}

	/** Mouse click inside the stage (hunt chests, wheel button); true when consumed. */
	public boolean click(double mx, double my) {
		if (!active()) return false;
		if (hunt.click(this, mx, my)) return true;
		return wheel.click(this);
	}

	/** Space / Enter while a feature waits for the player (wheel ready, hunt: next chest). */
	public boolean actionKey() {
		if (!active()) return false;
		if (wheel.click(this)) return true;
		return hunt.pickNext(this);
	}

	// ---- server replies ---------------------------------------------------------------------------------------

	/** The server revealed the next Treasure Hunt entry (the i-th pick shows entry i). */
	public void huntReveal(int value) {
		hunt.reveal(this, value);
	}

	/** End of the hunt: the remaining entries for the dimmed reveal. */
	public void huntRest(int[] remaining) {
		hunt.revealRest(this, remaining);
	}

	// ---- accessors for the layers and panels ------------------------------------------------------------------

	public StageHost host() {
		return host;
	}

	public Machine machine() {
		return machine;
	}

	public MachineDef def() {
		return def;
	}

	public SlotScript script() {
		return script;
	}

	public SlotFrames.Sampler frames() {
		return frames;
	}

	public SpinClock clock() {
		return clock;
	}

	public SpinTape tape() {
		return tape;
	}

	public int seed() {
		return seed;
	}

	public TimingProfile localProfile() {
		return local;
	}

	public double t() {
		return t;
	}

	public long now() {
		return now;
	}

	public long idleMs() {
		return now - idleSince;
	}

	public int wx() {
		return wx;
	}

	public int wy() {
		return wy;
	}

	public int cell() {
		return cell;
	}

	public int cellX(int reel) {
		return wx + reel * cell;
	}

	public int cellY(int row) {
		return wy + row * cell;
	}

	public int windowW() {
		return cell * 5;
	}

	public int windowH() {
		return cell * 3;
	}

	public int[] restCells() {
		return restCells;
	}

	public int[] restStops() {
		return restStops;
	}

	public boolean reduceMotion() {
		return FxSettings.reduceMotion();
	}

	public boolean flashes() {
		return FxSettings.flashes();
	}

	public ScreenParticles particles() {
		return particles;
	}

	public Font font() {
		return Minecraft.getInstance().font;
	}

	/** A spin is loaded and not finished (or finished but its end state is still shown). */
	public boolean active() {
		return script != null;
	}

	public boolean spinning() {
		return script != null && !finished || reels.preRolling();
	}

	public boolean finished() {
		return finished;
	}

	public long bet() {
		return tape == null ? 0 : tape.bet();
	}

	public long lastWin() {
		return lastWin;
	}

	/** Beats of a kind (shortcut). */
	public List<Beat> beats(String kind) {
		return script == null ? List.of() : script.kind(kind);
	}

	public WinShow winShow() {
		return winShow;
	}

	public TumbleView tumble() {
		return tumble;
	}

	public WildView wild() {
		return wild;
	}

	public FreeSpinsView freeSpins() {
		return freeSpins;
	}

	public HuntView hunt() {
		return hunt;
	}

	public HoardView hoard() {
		return hoard;
	}

	public DragonWheelView wheel() {
		return wheel;
	}

	public BigWinFx bigWin() {
		return bigWin;
	}

	public JackpotFx jackpots() {
		return jackpots;
	}

	public ReelView reels() {
		return reels;
	}
}
