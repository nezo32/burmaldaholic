package dev.nezo.burmaldaholic.client.ui.v2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.client.table.CasinoTableScreen;
import dev.nezo.burmaldaholic.client.ui.CasinoScreen;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Owner playtest B6: every game screen is a v2 screen, so it sizes by the {@link dev.nezo.burmaldaholic.core.ui.v2.ViewportPolicy}
 * frame (Room, frame offset) and lays out in the shared TableGrid rows ({@code PlayFieldUnityTest}). Scans the compiled
 * client classes under {@code dev.nezo.burmaldaholic.games}: a concrete game screen on the casino screen bases
 * ({@link CasinoTableScreen} / {@link CasinoScreen}) must extend {@link V2TableScreen} or {@link V2SceneScreen}, and none
 * may re-declare the viewport hooks. {@link #PENDING} lists the screens not ported yet (their owners remove them).
 */
class V2ScreenUnityTest {
	/** Not on the v2 kit yet: owner package and why. A NEW non-v2 game screen fails. */
	static final Set<String> PENDING = Set.of(
		"dev.nezo.burmaldaholic.games.slots.client.SlotMachineV2Screen", // slots: legacy 400 × 240 body (request to SLT, K2 handoff)
		"dev.nezo.burmaldaholic.games.extras.client.DuelInviteScreen"); // a small invite pop-up, not a game screen

	/** The viewport hooks only the kit's bases define (final there; a re-declaration would change the rows). */
	static final Set<String> HOOKS = Set.of("applyFit", "offsetX", "offsetY", "fitScale", "entranceRect", "fitToScreen");

	@Test
	void everyGameScreenIsOnTheV2Kit() throws IOException, URISyntaxException {
		Path root = Path.of(V2TableScreen.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		Path games = root.resolve("dev/nezo/burmaldaholic/games");
		assertTrue(Files.isDirectory(games), "compiled client classes not found under " + root);
		List<String> v2 = new ArrayList<>();
		Set<String> bad = new TreeSet<>();
		try (Stream<Path> s = Files.walk(games)) {
			for (Path p : s.filter(f -> f.toString().endsWith(".class")).toList()) {
				String name = root.relativize(p).toString().replace('\\', '/').replace('/', '.').replaceAll("\\.class$", "");
				Class<?> c;
				try {
					c = Class.forName(name, false, V2ScreenUnityTest.class.getClassLoader());
				} catch (Throwable e) { // optional dependencies (Mod Menu) are not on the test classpath
					continue;
				}
				if (Modifier.isAbstract(c.getModifiers()) || c.isInterface()) continue;
				boolean casino = CasinoTableScreen.class.isAssignableFrom(c) || CasinoScreen.class.isAssignableFrom(c);
				if (!casino) continue;
				boolean kit = V2TableScreen.class.isAssignableFrom(c) || V2SceneScreen.class.isAssignableFrom(c);
				if (!kit) {
					if (!PENDING.contains(name)) bad.add(name + " is not a V2TableScreen / V2SceneScreen");
					continue;
				}
				v2.add(c.getSimpleName());
				for (Class<?> k = c; k != V2TableScreen.class && k != V2SceneScreen.class; k = k.getSuperclass()) {
					for (Method m : k.getDeclaredMethods()) if (HOOKS.contains(m.getName()) && !m.isSynthetic()) bad.add(k.getName() + " re-declares " + m.getName());
				}
			}
		}
		assertTrue(bad.isEmpty(), "game screens off the v2 viewport rules: " + bad);
		// the owner's list: cards, craps, roulette, Plinko, Wheel, Coin Flip, Scratch, Dice
		for (String want : List.of("BlackjackScreen", "BaccaratScreen", "PokerScreen", "UthScreen", "CrapsScreen", "RouletteScreen", "PlinkoScreen",
			"WheelScreen", "CoinFlipScreen", "ScratchScreen", "DiceScreen")) {
			assertTrue(v2.contains(want), want + " not found among the v2 screens " + v2);
		}
	}
}
