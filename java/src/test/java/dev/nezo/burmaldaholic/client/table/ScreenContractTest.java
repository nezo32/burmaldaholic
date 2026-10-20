package dev.nezo.burmaldaholic.client.table;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import org.junit.jupiter.api.Test;

/**
 * Owner playtest B3: JEI / REI / EMI attach their item-list overlays to every {@code AbstractContainerScreen}, and
 * they drew over our tables. Loads (without initialising) every class of {@code src/client/java} and asserts that no
 * casino screen is a container screen, and that the table base still gives vanilla its {@link MenuAccess}.
 */
class ScreenContractTest {
	@Test
	void tableBaseIsAPlainScreenWithMenuAccess() {
		assertFalse(AbstractContainerScreen.class.isAssignableFrom(CasinoTableScreen.class));
		assertTrue(Screen.class.isAssignableFrom(CasinoTableScreen.class));
		assertTrue(MenuAccess.class.isAssignableFrom(CasinoTableScreen.class));
	}

	@Test
	void noCasinoScreenIsAContainerScreen() throws IOException {
		Path src = Path.of(System.getProperty("burmaldaholic.projectDir", ".")).resolve("src/client/java");
		List<String> names;
		try (Stream<Path> s = Files.walk(src)) {
			names = s.filter(p -> p.toString().endsWith(".java"))
				.map(p -> src.relativize(p).toString().replace('\\', '/').replace('/', '.').replaceAll("\\.java$", ""))
				.sorted().toList();
		}
		ClassLoader loader = getClass().getClassLoader();
		List<String> screens = new ArrayList<>();
		List<String> bad = new ArrayList<>();
		int tableScreens = 0;
		for (String n : names) {
			Class<?> c;
			try {
				c = Class.forName(n, false, loader);
			} catch (ClassNotFoundException | LinkageError e) {
				continue; // mixins / classes that need the game: not screens we register
			}
			if (!Screen.class.isAssignableFrom(c)) continue;
			screens.add(c.getSimpleName());
			if (CasinoTableScreen.class.isAssignableFrom(c)) tableScreens++;
			if (AbstractContainerScreen.class.isAssignableFrom(c)) bad.add(n);
		}
		assertTrue(bad.isEmpty(), "container screens (JEI/REI/EMI draw over these): " + bad);
		// the scan really saw the table screens (blackjack, baccarat, craps, roulette, poker, UTH, slots, cashier, plinko, wheel...)
		assertTrue(tableScreens >= 10, "table screens found: " + tableScreens + " of screens " + screens);
	}
}
