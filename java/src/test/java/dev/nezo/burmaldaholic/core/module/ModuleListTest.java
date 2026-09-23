package dev.nezo.burmaldaholic.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.ModuleList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ModuleListTest {
	private static Path projectDir() {
		return Path.of(System.getProperty("burmaldaholic.projectDir", "."));
	}

	@Test
	void moduleIdsAreValidUniqueAndCoreFirst() {
		ModuleLoader.validate(ModuleList.create().stream().map(CasinoModule::id).toList());
	}

	@Test
	void everyModuleHasALangFragmentAndViceVersa() throws IOException {
		TreeSet<String> ids = new TreeSet<>(ModuleList.create().stream().map(CasinoModule::id).toList());
		TreeSet<String> langDirs = new TreeSet<>();
		try (Stream<Path> s = Files.list(projectDir().resolve("src/main/lang"))) {
			s.filter(Files::isDirectory).forEach(p -> langDirs.add(p.getFileName().toString()));
		}
		assertEquals(ids, langDirs);
	}

	@Test
	void everyModuleHasANamespaceEntryAndOwnershipWorks() {
		Namespaces ns = Namespaces.get();
		ModuleList.create().forEach(m -> assertTrue(ns.knows(m.id()), m.id()));
		assertTrue(ns.owns("slots", "slot_machine_copper"));
		assertTrue(ns.owns("slots", "slots_lever"));
		assertTrue(ns.owns("chaos", "streak"));
		assertTrue(ns.owns("core", "anything"));
		assertFalse(ns.owns("blackjack", "slot_machine_copper"));
		assertFalse(ns.owns("slots", "slotsy"));
	}

	@Test
	void validationRejectsBadIds() {
		assertThrows(IllegalStateException.class, () -> ModuleLoader.validate(List.of("core", "Black-Jack")));
		assertThrows(IllegalStateException.class, () -> ModuleLoader.validate(List.of("core", "slots", "slots")));
		assertThrows(IllegalStateException.class, () -> ModuleLoader.validate(List.of("slots", "core")));
	}
}
