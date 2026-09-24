package dev.nezo.burmaldaholic.client.fx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Per-player presentation settings (global.md §2.8), client-local file {@code config/burmaldaholic-client.json}.
 * SKELETON: defaults + load/save + profile mapping; not loaded at startup yet. Lane J-L1 loads it in
 * {@code CoreClientModule}, adds the Casino Menu → Settings rows and the "Client effects" Mod Menu section,
 * and folds in vanilla "Hide lightning flashes" / "Screen effect scale = 0" (→ flashes off).
 */
public final class FxSettings {
	public enum Speed {
		SLOW(50),
		NORMAL(100),
		TURBO(150);

		public final int pct;

		Speed(int pct) {
			this.pct = pct;
		}
	}

	public enum Celebrations {
		ALL,
		MINE,
		OFF
	}

	/** Serialised form (field names = the JSON keys, ids of global.md §2.8 without the {@code anim.} prefix). */
	public static final class Data {
		public boolean reduceMotion = false;
		public boolean flashes = true;
		public Speed speed = Speed.NORMAL;
		public Celebrations celebrations = Celebrations.ALL;
		public int volume = 100;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile Data current = new Data();

	private FxSettings() {}

	public static Data get() {
		return current;
	}

	public static boolean reduceMotion() {
		return current.reduceMotion;
	}

	/** Flashes are off when the setting is off or reduce motion is on (global.md §2.8). */
	public static boolean flashes() {
		return current.flashes && !current.reduceMotion;
	}

	public static float volume() {
		return Math.max(0, Math.min(100, current.volume)) / 100f;
	}

	/** Profile for LOCAL beats built on this client. Shared beats always use {@link TimingProfile#SHARED}. */
	public static TimingProfile localProfile() {
		return new TimingProfile(current.speed.pct, current.reduceMotion, flashes());
	}

	public static void load(Path file) {
		if (!Files.isRegularFile(file)) return;
		try (Reader r = Files.newBufferedReader(file)) {
			Data d = GSON.fromJson(r, Data.class);
			if (d != null) current = d;
		} catch (IOException | RuntimeException e) {
			// keep defaults; a broken client file must never block the game
		}
	}

	public static void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		try (Writer w = Files.newBufferedWriter(file)) {
			GSON.toJson(current, w);
		}
	}
}
