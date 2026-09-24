package dev.nezo.burmaldaholic.core.pvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.nezo.burmaldaholic.core.pvp.logic.EscrowRecord;
import dev.nezo.burmaldaholic.core.pvp.logic.EscrowRecord.Entry;
import dev.nezo.burmaldaholic.core.pvp.logic.EscrowRecord.Salvage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The escrow kept next to each persisted match, and what can be salvaged from a damaged record (re-review, low 3). */
class EscrowRecordTest {
	private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	@Test
	void roundTrip() {
		List<Entry> in = List.of(Entry.player(A, 100), Entry.bankroll("multiplayer:charter/c1", 250), Entry.player(B, 0));
		Salvage.Refund out = EscrowRecord.decode(EscrowRecord.encode("wheel", in));
		assertEquals("wheel", out.mode());
		assertEquals(in, out.entries());
	}

	@Test
	void damagedEscrowRecordsThrow() {
		assertThrows(RuntimeException.class, () -> EscrowRecord.decode("{\"e\":[[\"x\",\"a\",1]]}"));
		assertThrows(RuntimeException.class, () -> EscrowRecord.decode("{\"e\":[[\"p\",\"not-a-uuid\",1]]}"));
		assertThrows(RuntimeException.class, () -> EscrowRecord.decode("{\"e\":[[\"p\",\"" + A + "\",-5]]}"));
		assertThrows(RuntimeException.class, () -> EscrowRecord.decode("nope"));
	}

	@Test
	void salvagePackedParticipants() {
		String raw = "{\"id\":\"x\",\"mode\":\"plinko\",\"state\":\"DRAWN\",\"anchor\":null,\"p\":["
			+ "[\"h\",\"" + A + "\",\"Alex\",100,1],"
			+ "[\"b\",\"b1\",\"lucky_steve\",\"HARD\",\"TAG\",\"MONEY\",\"BANKROLL\",\"multiplayer:charter/c1\",100,0],"
			+ "[\"b\",\"b2\",\"diamond_dave\",\"EASY\",\"LAG\",\"MONEY\",\"BANK\",\"\",100,0]]}";
		Salvage s = EscrowRecord.salvage(raw);
		Salvage.Refund r = assertInstanceOf(Salvage.Refund.class, s);
		assertEquals("plinko", r.mode());
		assertEquals(List.of(Entry.player(A, 100), Entry.bankroll("multiplayer:charter/c1", 100)), r.entries(),
			"humans and bankroll bots are owed; a bank bot moved nothing");
	}

	@Test
	void salvageFullParticipants() {
		String raw = "{\"state\":\"LOBBY\",\"participants\":[{\"index\":0,\"stake\":70,\"occupant\":{\"kind\":\"human\",\"id\":\"" + B + "\"}},"
			+ "{\"index\":1,\"stake\":70,\"occupant\":{\"kind\":\"bot\",\"purse\":{\"kind\":\"BANK\"}}}]}";
		Salvage.Refund r = assertInstanceOf(Salvage.Refund.class, EscrowRecord.salvage(raw));
		assertEquals(List.of(Entry.player(B, 70)), r.entries());
	}

	@Test
	void settledRecordsHoldNothing() {
		for (String st : List.of("SETTLED", "CANCELLED", "CLOSED", "INVITED")) {
			assertInstanceOf(Salvage.Nothing.class, EscrowRecord.salvage("{\"state\":\"" + st + "\",\"p\":\"junk\"}"), st);
		}
	}

	@Test
	void unreadableRecordsAreQuarantinedNotGuessed() {
		assertInstanceOf(Salvage.Unknown.class, EscrowRecord.salvage("garbage"));
		assertInstanceOf(Salvage.Unknown.class, EscrowRecord.salvage("{broken"));
		assertInstanceOf(Salvage.Unknown.class, EscrowRecord.salvage("{\"p\":[]}"), "no state");
		assertInstanceOf(Salvage.Unknown.class, EscrowRecord.salvage("{\"state\":\"LOBBY\",\"p\":[[\"h\",\"not-a-uuid\",\"x\",5,0]]}"));
		assertInstanceOf(Salvage.Unknown.class, EscrowRecord.salvage("{\"state\":\"LOBBY\",\"p\":[[\"h\",\"" + A + "\",\"x\"]]}"), "no stake");
		assertInstanceOf(Salvage.Unknown.class, EscrowRecord.salvage("{\"state\":\"DRAWN\",\"p\":[[\"h\",\"" + A + "\",\"x\",-1,0]]}"), "negative");
		assertInstanceOf(Salvage.Unknown.class, EscrowRecord.salvage("{\"state\":\"DRAWN\"}"), "no participants");
	}
}
