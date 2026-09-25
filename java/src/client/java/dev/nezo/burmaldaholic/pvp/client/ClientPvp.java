package dev.nezo.burmaldaholic.pvp.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.client.pvp.PvpScreens;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.pvp.logic.PvpText;
import dev.nezo.burmaldaholic.pvp.net.PvpSyncPayload;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Client PvP state: the last {@code PvpSyncPayload} of the player's match, which screen shows it (lobby, the
 * mode's registered screen or the generic match screen, result window) and the one-line HUD ticker shown while
 * no PvP screen is open (PVP.md §3.11.1).
 */
public final class ClientPvp {
	private static @Nullable JsonObject state;
	private static String kind = "clear";
	private static int receivedTick;
	private static int clientTicks;
	/** The mode screen currently created from a {@code PvpScreens} factory. */
	private static PvpScreens.@Nullable ModeScreen modeScreen;
	private static String modeScreenMatch = "";
	/** Countdown / grudge / Final Reveal clock of the current match (the shared overlay). */
	private static final dev.nezo.burmaldaholic.client.pvp.kit.RevealState REVEAL = dev.nezo.burmaldaholic.client.pvp.kit.RevealState.current();

	private ClientPvp() {}

	static void tick() {
		clientTicks++;
	}

	public static @Nullable JsonObject state() {
		return state;
	}

	public static String kind() {
		return kind;
	}

	static String matchId() {
		return state == null ? "" : PvpScreen.str(state, "id", "");
	}

	static boolean isPvpScreen(@Nullable Screen s) {
		return s instanceof PvpScreen || (s != null && modeScreen != null && modeScreen.screen() == s);
	}

	/** Handles one payload on the client thread. */
	public static void receive(PvpSyncPayload p) {
		Minecraft mc = Minecraft.getInstance();
		Screen cur = mc.gui.screen();
		if (p.kind().equals("error")) {
			Component error = error(mc, p.json());
			if (cur instanceof PvpScreen ps && error != null) {
				ps.showError(error);
			}
			return;
		}
		if (p.kind().equals("clear")) {
			state = null;
			kind = "clear";
			if (cur instanceof PvpLobbyScreen || cur instanceof PvpMatchScreen) {
				mc.gui.setScreen(null);
			}
			return;
		}
		JsonObject s;
		try {
			s = JsonParser.parseString(p.json()).getAsJsonObject();
		} catch (RuntimeException e) {
			Burmaldaholic.LOGGER.warn("Bad PvP sync payload", e);
			return;
		}
		state = s;
		kind = p.kind();
		receivedTick = clientTicks;
		REVEAL.update(s);
		String id = PvpScreen.str(s, "id", "");
		boolean mayOpen = p.open() || isPvpScreen(cur);
		switch (p.kind()) {
			case "lobby" -> {
				if (cur instanceof PvpLobbyScreen l && l.matchId().equals(id)) {
					l.acceptState(s);
				} else if (mayOpen) {
					mc.gui.setScreen(new PvpLobbyScreen(s));
				}
			}
			case "match" -> {
				if (PvpScreen.bool(s, "spectator")) {
					return; // spectators: ticker only
				}
				if (modeScreen != null && modeScreenMatch.equals(id) && cur == modeScreen.screen()) {
					modeScreen.update(s);
				} else if (cur instanceof PvpMatchScreen m && m.matchId().equals(id)) {
					m.acceptState(s);
				} else if (mayOpen) {
					open(mc, s, id);
				}
			}
			case "result" -> {
				if (cur instanceof PvpResultScreen r && r.matchId().equals(id)) {
					r.acceptState(s);
				} else if (modeScreen != null && modeScreenMatch.equals(id) && cur == modeScreen.screen() && !p.open()) {
					modeScreen.update(s); // the mode screen shows the settle; the result window opens with open = true
				} else if (mayOpen) {
					mc.gui.setScreen(new PvpResultScreen(s));
				}
			}
			default -> {
			}
		}
	}

	private static void open(Minecraft mc, JsonObject s, String id) {
		Function<JsonObject, PvpScreens.ModeScreen> factory = PvpScreens.factory(PvpScreen.str(s, "mode", ""));
		if (factory != null) {
			try {
				modeScreen = factory.apply(s);
				modeScreenMatch = id;
				mc.gui.setScreen(modeScreen.screen());
				return;
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("PvP mode screen for {} failed", PvpScreen.str(s, "mode", ""), e);
				modeScreen = null;
			}
		}
		mc.gui.setScreen(new PvpMatchScreen(s));
	}

	private static @Nullable Component error(Minecraft mc, String json) {
		try {
			JsonElement e = JsonParser.parseString(json).getAsJsonObject().get("error");
			if (e == null || mc.level == null) {
				return null;
			}
			return ComponentSerialization.CODEC.parse(mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), e).result().orElse(null);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	public static dev.nezo.burmaldaholic.client.pvp.kit.RevealState reveal() {
		return REVEAL;
	}

	/** The shared match overlay (grudge clash, countdown, Final Reveal) over a live mode screen. */
	static void drawOverlay(Screen screen, net.minecraft.client.gui.GuiGraphicsExtractor g) {
		JsonObject s = state;
		if (s == null || !kind.equals("match") || screen instanceof PvpResultScreen || !isPvpScreen(screen)) {
			return;
		}
		// drawn after the screen's pass: in the screen's own GUI when it runs the compact layout (FitScaled)
		boolean fit = dev.nezo.burmaldaholic.client.ui.FitScaled.push(g, screen);
		dev.nezo.burmaldaholic.client.pvp.kit.MatchOverlay.draw(g, Minecraft.getInstance().font, screen.width, screen.height, s, REVEAL);
		if (fit) dev.nezo.burmaldaholic.client.ui.FitScaled.pop(g);
	}

	/** Opens the taunt picker over {@code parent}. */
	static void openTaunts(Screen parent) {
		Minecraft.getInstance().gui.setScreen(new PvpTauntScreen(parent, matchId()));
	}

	/** Test hook: install a state as if it had been received (client GameTests). */
	public static void setStateForTests(@Nullable JsonObject s, String k) {
		state = s;
		kind = k;
		receivedTick = clientTicks;
	}

	// ---- HUD ticker ----------------------------------------------------------------------------

	/** Result lines stay on the ticker this long after the match settled. */
	static final int RESULT_TICKER_TICKS = 300;

	/** One-line ticker text for the current state, or null (nothing to show / a PvP screen is open). */
	public static @Nullable Component tickerLine() {
		JsonObject s = state;
		if (s == null) {
			return null;
		}
		int age = clientTicks - receivedTick;
		Component game = PvpScreen.game(s);
		switch (kind) {
			case "lobby" -> {
				long left = PvpScreen.num(s, "ticksLeft", -1);
				long secs = left < 0 ? 0 : PvpText.seconds(left - age);
				return Component.translatable("gui.burmaldaholic.pvp.lobby.waiting_bar", game, Texts.number(PvpScreen.participants(s).size()),
					Texts.number(PvpScreen.num(s, "max", 2)), Texts.plural("unit.burmaldaholic.second_acc", secs));
			}
			case "match" -> {
				if (PvpScreen.num(s, "final", -1) >= 0) {
					return Component.translatable("gui.burmaldaholic.pvp.match.final");
				}
				return Component.translatable("gui.burmaldaholic.pvp.match.live", game, Texts.chips(PvpScreen.num(s, "pot", 0)));
			}
			case "result" -> {
				if (age > RESULT_TICKER_TICKS) {
					return null;
				}
				JsonObject r = s.getAsJsonObject("result");
				if (r == null) {
					return null;
				}
				int[] winners = PvpScreen.ints(r, "winners");
				MutableComponent line = game.copy().append(Texts.raw(" · "));
				if (winners.length > 1) {
					return line.append(Component.translatable("gui.burmaldaholic.pvp.result.dead_heat_title"));
				}
				JsonObject w = winners.length == 0 ? null : PvpScreen.participant(s, winners[0]);
				return line.append(Component.translatable("gui.burmaldaholic.pvp.result.winner_title", w == null ? Component.empty() : PvpScreen.plainName(w)));
			}
			default -> {
				return null;
			}
		}
	}
}
