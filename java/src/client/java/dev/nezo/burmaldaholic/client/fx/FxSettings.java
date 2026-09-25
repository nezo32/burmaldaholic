package dev.nezo.burmaldaholic.client.fx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.nezo.burmaldaholic.core.anim.TimingProfile;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Per-player presentation settings (global.md §2.8), client-local file {@code config/burmaldaholic-client.json}.
 * Loaded by {@link ClientFx#init} ({@code CoreClientModule}); edited in {@link FxSettingsScreen} (Mod Menu →
 * "Client effects"; the Casino Menu → Settings rows open the same screen). Vanilla "Hide lightning flashes" or
 * "Screen effect scale" = 0 force flashes off. {@link #update} saves immediately.
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

		Data copy() {
			Data c = new Data();
			c.reduceMotion = reduceMotion;
			c.flashes = flashes;
			c.speed = speed;
			c.celebrations = celebrations;
			c.volume = volume;
			return c;
		}

		Data sanitized() {
			volume = Math.max(0, Math.min(100, volume));
			if (speed == null) speed = Speed.NORMAL;
			if (celebrations == null) celebrations = Celebrations.ALL;
			return this;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile Data current = new Data();
	private static volatile Path file;

	private FxSettings() {}

	public static Data get() {
		return current;
	}

	public static boolean reduceMotion() {
		return current.reduceMotion;
	}

	/**
	 * Flashes are off when the setting is off, reduce motion is on (global.md §2.8), or the vanilla accessibility
	 * options ask for it ("Hide lightning flashes", "Screen effect scale" = 0).
	 */
	public static boolean flashes() {
		if (!current.flashes || current.reduceMotion) return false;
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.options != null
				&& (mc.options.hideLightningFlash().get() || mc.options.screenEffectScale().get() <= 0.0)) return false;
		} catch (RuntimeException e) {
			// options not ready (early init): the setting alone decides
		}
		return true;
	}

	public static float volume() {
		return Math.max(0, Math.min(100, current.volume)) / 100f;
	}

	public static Speed speed() {
		return current.speed == null ? Speed.NORMAL : current.speed;
	}

	public static Celebrations celebrations() {
		return current.celebrations == null ? Celebrations.ALL : current.celebrations;
	}

	/** Other players' nearby FX and server-wide toasts are shown ({@code anim.celebrations = all}). */
	public static boolean othersCelebrations() {
		return celebrations() == Celebrations.ALL;
	}

	/** Profile for LOCAL beats built on this client. Shared beats always use {@link TimingProfile#SHARED}. */
	public static TimingProfile localProfile() {
		return new TimingProfile(speed().pct, current.reduceMotion, flashes());
	}

	/** Changes the settings (on a copy, so readers never see a half-written state) and saves them. */
	public static void update(Consumer<Data> change) {
		Data d = current.copy();
		change.accept(d);
		current = d.sanitized();
		Path f = file;
		if (f != null) {
			try {
				save(f);
			} catch (IOException e) {
				// client-local convenience file: a failed write must never break the game
			}
		}
	}

	/** Loads {@code file} (remembered for {@link #update}); a missing or broken file keeps the defaults. */
	public static void load(Path file) {
		FxSettings.file = file;
		if (!Files.isRegularFile(file)) return;
		try (Reader r = Files.newBufferedReader(file)) {
			Data d = GSON.fromJson(r, Data.class);
			if (d != null) current = d.sanitized();
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
