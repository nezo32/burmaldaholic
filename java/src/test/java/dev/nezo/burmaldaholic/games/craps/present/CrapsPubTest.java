package dev.nezo.burmaldaholic.games.craps.present;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrapsPubTest {
	@Test
	void roundTrip() {
		CrapsPub p = new CrapsPub(42, List.of(new CrapsPub.Stack(0, "pass", 0, 50, 75), new CrapsPub.Stack(2, "come", 6, 10, 0),
			new CrapsPub.Stack(2, "field", 0, 5, 0)), List.of(new CrapsPub.Net(0, 125), new CrapsPub.Net(2, -5)));
		CrapsPub q = CrapsPub.decode(p.encode());
		assertEquals(p, q);
		assertEquals(125, q.onTable(0));
		assertEquals(15, q.onTable(2));
		assertEquals(-5L, q.net(2));
		assertNull(q.net(1));
		assertEquals(CrapsPub.EMPTY, CrapsPub.decode(CrapsPub.EMPTY.encode()));
	}

	@Test
	void capsAndRejects() {
		List<CrapsPub.Stack> many = new ArrayList<>();
		for (int i = 0; i < 20; i++) many.add(new CrapsPub.Stack(i % 6, "field", 0, i + 1, 0));
		assertEquals(CrapsPub.MAX_STACKS, new CrapsPub(1, many, List.of()).stacks().size());
		assertThrows(IllegalArgumentException.class, () -> CrapsPub.decode(new int[] {1, 0, 0, 0}));
		assertThrows(IllegalArgumentException.class, () -> CrapsPub.decode(new int[] {CrapsPub.VERSION, 0, 3, 0}));
	}
}
