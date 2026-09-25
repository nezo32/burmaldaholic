package dev.nezo.burmaldaholic.multiplayer.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nezo.burmaldaholic.multiplayer.logic.CasinoBook.Casino;
import dev.nezo.burmaldaholic.multiplayer.logic.CasinoBook.TablePos;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CasinoBookTest {
	private static final String OW = "minecraft:overworld";
	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();

	private static String key(int x, int y, int z) {
		return new TablePos(OW, x, y, z).key();
	}

	@Test
	void tableKeysRoundTrip() {
		TablePos p = new TablePos(OW, -12, 64, 300);
		assertEquals("minecraft:overworld|-12,64,300", p.key());
		assertEquals(p, TablePos.parse(p.key()).orElseThrow());
		assertTrue(TablePos.parse("garbage").isEmpty());
		assertTrue(TablePos.parse("x|1,2").isEmpty());
	}

	@Test
	void createAssignsSequentialIds() {
		CasinoBook book = new CasinoBook();
		assertEquals("c1", book.create(a, "A", OW, 0, 64, 0, 24, 0).id);
		assertEquals("c2", book.create(b, "B", OW, 100, 64, 0, 24, 0).id);
		assertEquals(2, book.ownedBy(a).size() + book.ownedBy(b).size());
		assertEquals("c1", book.charterAt(OW, 0, 64, 0).orElseThrow().id);
		assertTrue(book.charterAt(OW, 0, 65, 0).isEmpty());
	}

	@Test
	void linkingAndClosing() {
		CasinoBook book = new CasinoBook();
		Casino c = book.create(a, "A", OW, 0, 64, 0, 24, 0);
		assertTrue(book.link(c, key(3, 64, 3), "blackjack", "blackjack_table", "block.burmaldaholic.blackjack_table"));
		assertFalse(book.link(c, key(3, 64, 3), "blackjack", "blackjack_table", "d"), "already linked");
		assertEquals(c, book.activeCasinoOf(key(3, 64, 3)).orElseThrow());
		assertEquals(1, book.tablesOf(c.id).size());

		book.close(c.id);
		assertTrue(book.casino(c.id).isEmpty());
		assertTrue(book.activeCasinoOf(key(3, 64, 3)).isEmpty(), "inactive, not a house table");
		assertNull(book.table(key(3, 64, 3)).orElseThrow().casinoId);
		assertEquals(a, book.closing().get(c.id));

		// the same owner's new charter re-links the inactive table
		Casino again = book.create(a, "A", OW, 10, 64, 0, 24, 0);
		assertEquals(1, book.relinkInactive(again));
		assertEquals(again, book.activeCasinoOf(key(3, 64, 3)).orElseThrow());
	}

	@Test
	void otherOwnersInactiveTablesAreNotStolen() {
		CasinoBook book = new CasinoBook();
		Casino ca = book.create(a, "A", OW, 0, 64, 0, 24, 0);
		book.link(ca, key(3, 64, 3), "roulette", "roulette_table", "d");
		book.close(ca.id);
		Casino cb = book.create(b, "B", OW, 5, 64, 0, 24, 0);
		assertEquals(0, book.relinkInactive(cb));
		assertFalse(book.link(cb, key(3, 64, 3), "roulette", "roulette_table", "d"));
		assertTrue(book.activeCasinoOf(key(3, 64, 3)).isEmpty());
		// but a table linked to a live casino can't move either
		Casino cc = book.create(a, "A", OW, 500, 64, 0, 24, 0);
		book.link(cc, key(501, 64, 0), "craps", "craps_table", "d");
		assertFalse(book.link(cb, key(501, 64, 0), "craps", "craps_table", "d"));
	}

	@Test
	void relinkIgnoresTablesOutsideTheClaim() {
		CasinoBook book = new CasinoBook();
		Casino c = book.create(a, "A", OW, 0, 64, 0, 24, 0);
		book.link(c, key(20, 64, 0), "slots", "slot_machine_copper", "d");
		book.close(c.id);
		Casino far = book.create(a, "A", OW, 200, 64, 0, 24, 0);
		assertEquals(0, book.relinkInactive(far));
	}

	@Test
	void exposureUsesOwnerAndGameMinimum() {
		CasinoBook book = new CasinoBook();
		Casino c = book.create(a, "A", OW, 0, 64, 0, 24, 0);
		book.link(c, key(1, 64, 1), "roulette", "roulette_table", "d");
		book.link(c, key(2, 64, 2), "blackjack", "blackjack_table", "d");
		book.table(key(1, 64, 1)).orElseThrow().min = 5;          // 5 × 36 = 180
		book.table(key(2, 64, 2)).orElseThrow().gameMin = 10;     // 10 × 17.5 = 175
		assertEquals(175, Solvency.cheapestWorstCase(book.exposure(c.id)).orElseThrow());
		book.table(key(2, 64, 2)).orElseThrow().open = false;
		assertEquals(180, Solvency.cheapestWorstCase(book.exposure(c.id)).orElseThrow());
		book.removeTable(key(1, 64, 1));
		assertTrue(Solvency.cheapestWorstCase(book.exposure(c.id)).isEmpty());
	}
}
