package dev.nezo.burmaldaholic.core.ui.v2.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nezo.burmaldaholic.core.table.parts.FeltFrame;
import dev.nezo.burmaldaholic.core.table.parts.TableGame;
import dev.nezo.burmaldaholic.core.table.parts.TablePart;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Felt;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.Holdem;
import dev.nezo.burmaldaholic.games.blackjack.present.BlackjackWorld;
import dev.nezo.burmaldaholic.games.roulette.present.RouletteWorld;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * W2 on MT's furniture, proven without screenshots (owner R3): the board's flat layers sit on the 14/16 felt in a
 * strict order with depth gaps (no z-fighting), the felt print decal stays under every prop standing on the felt, the
 * cards / chips / deck land on the felt inside the {@link FeltFrame} and off the props, the plates anchor on the
 * 15.5/16 padded rail at the seat, and the fades reach exactly zero.
 */
class BoardLayersTest {
	private static final Path MODELS = Path.of("src/main/resources/assets/burmaldaholic/models/block/tables");
	/** Smallest world-space gap between two coplanar layers we accept (24-bit depth at 8 blocks resolves ~1e-4). */
	private static final double MIN_WORLD_GAP = 1 / 2048.0;
	private static final List<TablePart> FULL_PARTS = List.of(TablePart.MAIN, TablePart.LEFT, TablePart.RIGHT, TablePart.BACK_LEFT, TablePart.BACK,
		TablePart.BACK_RIGHT);

	private static Felt designFelt(TableGame g) {
		return switch (g.shape()) {
			case CRESCENT -> Felt.CRESCENT;
			case OVAL -> Felt.OVAL;
			case RECT, ROULETTE -> Felt.RECT;
		};
	}

	private static FullTableMap map(TableGame g) {
		return FullTableMap.of(FeltFrame.of(g, TablePart.MAIN), designFelt(g), RouletteWorld.WHEEL_X, RouletteWorld.WHEEL_Z, RouletteWorld.WHEEL_R);
	}

	@Test
	void usesMtFeltAndRailHeights() {
		assertEquals(14 / 16.0, FeltFrame.FELT_Y, 0);
		assertEquals(15.5 / 16.0, FeltFrame.RAIL_TOP, 0);
		for (TableGame g : TableGame.values()) {
			FeltFrame f = FeltFrame.of(g, TablePart.MAIN);
			FullTableMap m = map(g);
			assertEquals(FeltFrame.FELT_Y, f.top(), 0, g + " felt");
			assertEquals(FeltFrame.FELT_Y + FeltFrame.CONTENT_LIFT, m.originY(), 1e-6, g + " content origin on the felt");
			assertEquals(FeltFrame.FELT_Y + FullTableMap.DECAL_LIFT, FullTableMap.decalY(f), 1e-6, g + " decal");
			assertEquals(FeltFrame.RAIL_TOP + FullTableMap.PLATE_ABOVE_RAIL, FullTableMap.PLATE_Y, 1e-6);
			// design TOP lands on the content origin, and the round trip is exact
			assertEquals(m.originY(), m.footY(BoardGeometry.TOP), 1e-6, g.toString());
			assertEquals(0.37f, m.designX(m.footX(0.37f)), 1e-5);
			assertEquals(-0.2f, m.designZ(m.footZ(-0.2f)), 1e-5);
		}
	}

	/** felt < decal < content origin < marks < texts < card shadows < chip shadows < chip base < cards, all apart. */
	@Test
	void flatLayersAreOrderedWithDepthGaps() {
		for (TableGame g : TableGame.values()) {
			FullTableMap m = map(g);
			double[] y = {FeltFrame.FELT_Y, FullTableMap.decalY(FeltFrame.of(g, TablePart.MAIN)), m.originY(),
				m.footY(BoardGeometry.TOP + BoardGeometry.MARK_Y), m.footY(BoardGeometry.TOP + BoardGeometry.TEXT_Y),
				m.footY(BoardGeometry.TOP + BoardGeometry.SHADOW_Y), m.footY(BoardGeometry.TOP + BoardGeometry.CHIP_SHADOW_Y),
				m.footY(BoardGeometry.TOP + BoardGeometry.CHIP_BASE_Y), m.footY(BoardGeometry.TOP + BoardGeometry.CARD_LIFT)};
			for (int i = 1; i < y.length; i++) {
				assertTrue(y[i] - y[i - 1] >= MIN_WORLD_GAP, g + " layer " + i + " gap " + (y[i] - y[i - 1]));
			}
			// overlapping cards step apart too, and everything flat stays below the rail top
			assertTrue(m.scale() * BoardGeometry.LAYER >= MIN_WORLD_GAP, g + " card layer step");
			assertTrue(y[y.length - 1] < FeltFrame.RAIL_TOP, g + " cards below the rail top");
		}
		// compact 1x1 tables (T-W2 D3): the block-top felt quad is at TOP itself and the content shrinks to as little as
		// 0.40 of the design size; the layers must still clear 1/2048 block in the world
		float[] y = {0, BoardGeometry.MARK_Y, BoardGeometry.TEXT_Y, BoardGeometry.SHADOW_Y, BoardGeometry.CHIP_SHADOW_Y, BoardGeometry.CHIP_BASE_Y,
			BoardGeometry.CARD_LIFT};
		for (Felt w : new Felt[] {Felt.CRESCENT, Felt.RECT, Felt.OVAL, Felt.FALLBACK}) {
			float k = BoardGeometry.contentScale(w, Felt.FALLBACK);
			assertTrue(k * BoardGeometry.LAYER >= MIN_WORLD_GAP, "compact " + w + " card layer step");
			for (int i = 1; i < y.length; i++) {
				assertTrue((y[i] - y[i - 1]) * k >= MIN_WORLD_GAP, "compact " + w + " scale " + k + " layer " + i);
				assertTrue(y[i] - y[i - 1] >= BoardGeometry.MIN_LAYER_GAP - 1e-6, "layer " + i + " >= MIN_LAYER_GAP");
			}
		}
	}

	/** A model element in footprint px (x right, z toward the players; main block = 0..16). */
	private record Box(String table, String tex, double x0, double x1, double y0, double y1, double z0, double z1) {}

	private static List<Box> boxes(String table) throws IOException {
		List<Box> out = new ArrayList<>();
		for (TablePart part : FULL_PARTS) {
			Path p = MODELS.resolve(table + "/" + part.getSerializedName() + ".json");
			JsonObject m = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
			for (JsonElement e : m.getAsJsonArray("elements")) {
				JsonObject el = e.getAsJsonObject();
				JsonArray from = el.getAsJsonArray("from"), to = el.getAsJsonArray("to");
				String tex = el.getAsJsonObject("faces").getAsJsonObject("up").get("texture").getAsString();
				// furniture.mjs element(): model x = 16 − (foot x − 16 r), z likewise with f
				double ox = 16 + 16 * part.r(), oz = 16 + 16 * part.f();
				out.add(new Box(table, tex, ox - to.get(0).getAsDouble(), ox - from.get(0).getAsDouble(), from.get(1).getAsDouble(), to.get(1).getAsDouble(),
					oz - to.get(2).getAsDouble(), oz - from.get(2).getAsDouble()));
			}
		}
		return out;
	}

	private static final String[] TABLES = {"blackjack_table", "baccarat_table", "uth_table", "poker_table", "roulette_table", "craps_table",
		"blackjack_table_high_roller", "uth_table_player_banked"};

	/** Props standing on the felt (dealer tray, placard post): above the felt, starting at it, inside the felt rectangle. */
	private static List<Box> props(String table, FeltFrame f) throws IOException {
		double felt = FeltFrame.FELT_Y * 16;
		List<Box> out = new ArrayList<>();
		for (Box b : boxes(table)) {
			if (b.tex.equals("#felt") || b.y1 <= felt + 1e-9 || b.y0 < felt - 1e-9) continue;
			double fx0 = (f.cx() - f.w() / 2) * 16, fx1 = (f.cx() + f.w() / 2) * 16, fz0 = (f.cz() - f.d() / 2) * 16, fz1 = (f.cz() + f.d() / 2) * 16;
			if (b.x1 <= fx0 || b.x0 >= fx1 || b.z1 <= fz0 || b.z0 >= fz1) continue;
			out.add(b);
		}
		return out;
	}

	@Test
	void decalLiesOnTheFeltAndUnderEveryProp() throws IOException {
		double felt = FeltFrame.FELT_Y * 16;
		for (String t : TABLES) {
			TableGame g = TableGame.ofBlockName(t);
			FeltFrame f = FeltFrame.of(g, TablePart.MAIN);
			boolean feltSeen = false;
			for (Box b : boxes(t)) if (b.tex.equals("#felt")) {
				assertEquals(felt, b.y1, 1e-9, t + " felt top");
				feltSeen = true;
			}
			assertTrue(feltSeen, t);
			for (Box b : props(t, f)) {
				double top = b.y1 / 16 - FeltFrame.FELT_Y;
				assertTrue(top - FullTableMap.DECAL_LIFT >= MIN_WORLD_GAP, t + " prop " + b + " pokes " + top + " above the felt: the decal would hide it");
			}
		}
	}

	/** Corners (footprint) of a design card w × h at (x, z) turned yaw°, drawn at the full-table card scale. */
	private static double[][] corners(FullTableMap m, float x, float z, float yaw, float w, float h) {
		double a = Math.toRadians(yaw);
		double hw = w * BoardGeometry.FULL_CARD_SCALE / 2, hh = h * BoardGeometry.FULL_CARD_SCALE / 2;
		double[][] out = new double[4][];
		int k = 0;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				double lx = sx * hw, lz = sz * hh;
				out[k++] = new double[] {m.footX((float) (x + lx * Math.cos(a) - lz * Math.sin(a))), m.footZ((float) (z + lx * Math.sin(a) + lz * Math.cos(a)))};
			}
		}
		return out;
	}

	private static void onFeltOffProps(String what, FeltFrame f, List<Box> props, double[][] pts) {
		for (double[] p : pts) {
			assertTrue(f.onFelt(p[0], p[1]), what + " off the felt at " + p[0] + ", " + p[1]);
			assertTrue(Math.abs(p[0] - f.cx()) <= f.w() / 2 && Math.abs(p[1] - f.cz()) <= f.d() / 2, what + " outside the FeltFrame");
			for (Box b : props) {
				boolean in = p[0] * 16 > b.x0 && p[0] * 16 < b.x1 && p[1] * 16 > b.z0 && p[1] * 16 < b.z1;
				assertFalse(in, what + " on prop " + b);
			}
		}
	}

	private static double[][] disc(FullTableMap m, float x, float z, float r) {
		double[][] out = new double[8][];
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * i / 4;
			out[i] = new double[] {m.footX((float) (x + r * Math.cos(a))), m.footZ((float) (z + r * Math.sin(a)))};
		}
		return out;
	}

	@Test
	void blackjackContentLandsOnTheFeltOffTheTray() throws IOException {
		TableGame g = TableGame.BLACKJACK;
		FeltFrame f = FeltFrame.of(g, TablePart.MAIN);
		FullTableMap m = map(g);
		List<Box> props = props("blackjack_table", f);
		for (int n = 1; n <= 6; n++) {
			float[] d = BlackjackWorld.dealer(n - 1, n);
			onFeltOffProps("dealer card " + n, f, props, corners(m, d[0], d[1], 0, BlackjackWorld.DEALER_W, BlackjackWorld.DEALER_H));
		}
		float[] shoe = BlackjackWorld.shoe();
		onFeltOffProps("shoe", f, props, corners(m, shoe[0], shoe[1], 90, BlackjackWorld.SEAT_W, BlackjackWorld.SEAT_H));
		for (int seats = 1; seats <= 7; seats++) {
			for (int s = 0; s < seats; s++) {
				float[] seat = BlackjackWorld.seat(s, seats);
				for (int k = 0; k < 4; k++) {
					float[] c = BlackjackWorld.seatCard(seat, 0, 1, k, 4);
					onFeltOffProps(seats + " seats, seat " + s + " card " + k, f, props, corners(m, c[0], c[1], seat[2], BlackjackWorld.SEAT_W, BlackjackWorld.SEAT_H));
				}
				float[] st = BlackjackWorld.stack(seat);
				onFeltOffProps(seats + " seats, stack " + s, f, props, disc(m, st[0], st[1], BoardGeometry.CHIP_R));
			}
		}
	}

	@Test
	void holdemContentAndDeckLandOnTheFeltApart() throws IOException {
		TableGame g = TableGame.POKER;
		FeltFrame f = FeltFrame.of(g, TablePart.MAIN);
		FullTableMap m = map(g);
		List<Box> props = props("poker_table", f);
		float deckR = (float) Math.hypot(BoardGeometry.CARD_W, BoardGeometry.CARD_H) / 2 * BoardGeometry.FULL_CARD_SCALE;
		onFeltOffProps("deck", f, props, corners(m, Holdem.DECK_X, Holdem.DECK_Z, Holdem.DECK_YAW, BoardGeometry.CARD_W, BoardGeometry.CARD_H));
		for (int i = 0; i < 5; i++) {
			float[] b = Holdem.board(i);
			onFeltOffProps("board " + i, f, props, corners(m, b[0], b[1], 0, BoardGeometry.CARD_W, BoardGeometry.CARD_H));
			float r = (float) Math.hypot(BoardGeometry.CARD_W, BoardGeometry.CARD_H) / 2 * BoardGeometry.FULL_CARD_SCALE;
			assertTrue(Math.hypot(b[0] - Holdem.DECK_X, b[1] - Holdem.DECK_Z) > r + deckR, "deck clear of board " + i);
		}
		for (int n = 2; n <= 10; n++) {
			for (int s = 0; s < n; s++) {
				for (int j = 0; j < 2; j++) {
					float[] c = Holdem.holeCard(s, n, j);
					onFeltOffProps(n + " seats, seat " + s + " card " + j, f, props, corners(m, c[0], c[1], c[2], Holdem.SEAT_W, Holdem.SEAT_H));
					float r = (float) Math.hypot(Holdem.SEAT_W, Holdem.SEAT_H) / 2 * BoardGeometry.FULL_CARD_SCALE;
					assertTrue(Math.hypot(c[0] - Holdem.DECK_X, c[1] - Holdem.DECK_Z) > r + deckR, n + " seats: deck clear of seat " + s);
				}
				float[] b = Holdem.bet(s, n);
				// a bet may stand as two piles (the second CHIP_R · 2.15 to the right)
				onFeltOffProps(n + " seats, bet " + s, f, props, disc(m, b[0] + BoardGeometry.CHIP_R * 1.075f, b[1], BoardGeometry.CHIP_R * 2.1f));
				assertTrue(Math.hypot(b[0] - Holdem.DECK_X, b[1] - Holdem.DECK_Z) > BoardGeometry.CHIP_R * 3.2f + deckR, n + " seats: deck clear of bet " + s);
			}
		}
		onFeltOffProps("pot", f, props, disc(m, 0.5f, Holdem.POT_Z, BoardGeometry.CHIP_R));
	}

	/** Hold'em seat spots point where MT's seat points stand (the player is behind his cards), none at the dealer. */
	@Test
	void holdemSeatsFollowMtSeatPoints() {
		FeltFrame f = FeltFrame.of(TableGame.POKER, TablePart.MAIN);
		FullTableMap m = map(TableGame.POKER);
		for (int n = 2; n <= 10; n++) {
			for (int s = 0; s < n; s++) {
				float[] c = Holdem.seat(s, n, Holdem.SEAT_R);
				double[] p = dev.nezo.burmaldaholic.core.table.parts.TableFootprint.seatPoint(TableGame.POKER, TablePart.MAIN, s, n);
				double a1 = Math.atan2(m.footZ(c[1]) - f.cz(), m.footX(c[0]) - f.cx()), a2 = Math.atan2(p[1] - f.cz(), p[0] - f.cx());
				double diff = Math.abs(Math.IEEEremainder(a1 - a2, 2 * Math.PI));
				assertTrue(diff < Math.toRadians(16), n + " seats, seat " + s + ": cards " + Math.toDegrees(a1) + "° vs player " + Math.toDegrees(a2) + "°");
				assertTrue(m.footZ(c[1]) > f.cz() - f.d() / 2 + 0.2 || Math.abs(m.footX(c[0]) - f.cx()) > 0.6, n + " seats, seat " + s + " at the dealer");
			}
		}
	}

	@Test
	void deckIsASquaredStack() {
		assertEquals(BoardGeometry.DECK_H, BoardGeometry.deckHeight(52), 1e-6);
		assertEquals(0f, BoardGeometry.deckHeight(0));
		assertTrue(BoardGeometry.deckHeight(1) > 0 && BoardGeometry.deckHeight(1) < BoardGeometry.deckHeight(30));
		assertTrue(BoardGeometry.deckEdgeLines(52) >= 4, "edge lines every ~1 texel");
		assertTrue(BoardGeometry.deckEdgeLines(1) >= 2);
		// a full deck stays lower than a chip pile of 2 and well under the rail
		assertTrue(BoardGeometry.CHIP_BASE_Y + BoardGeometry.DECK_H < 2 * BoardGeometry.CHIP_H);
	}

	@Test
	void rouletteWheelLandsOnTheBowl() {
		FeltFrame f = FeltFrame.of(TableGame.ROULETTE, TablePart.MAIN);
		FullTableMap m = map(TableGame.ROULETTE);
		assertEquals(f.wheelCx(), m.footX(0.5f + RouletteWorld.WHEEL_X), 1e-5);
		assertEquals(f.wheelCz(), m.footZ(0.5f + RouletteWorld.WHEEL_Z), 1e-5);
		assertTrue(RouletteWorld.WHEEL_R * m.scale() < f.wheelDiameter() / 2, "rotor inside the bowl rim");
	}

	/** Plates and their leader ends sit on the padded rail at the seat: off the felt, inside the rail, at 15.5/16 + 0.03. */
	@Test
	void platesAnchorOnTheRailAtTheSeat() {
		for (TableGame g : new TableGame[] {TableGame.BLACKJACK, TableGame.BACCARAT, TableGame.UTH, TableGame.POKER, TableGame.CRAPS}) {
			FeltFrame f = FeltFrame.of(g, TablePart.MAIN);
			Felt felt = designFelt(g);
			FullTableMap m = map(g);
			for (int n = 1; n <= 9; n++) {
				for (int s = 0; s < n; s++) {
					float[] spot = felt == Felt.OVAL ? Holdem.seat(s, n, Holdem.SEAT_R) : felt == Felt.CRESCENT ? BoardGeometry.crescentSeat(s, n)
						: new float[] {0.5f + (s - (n - 1) / 2f) * 1.6f / Math.max(1, n), 1.0f};
					float[] d = BoardGeometry.plateAnchor(felt, spot[0], spot[1]);
					float[] a = m.plateAnchor(f, felt, d[0], d[2]);
					String what = g + " " + n + " seats, seat " + s + " at " + a[0] + ", " + a[2];
					assertEquals(FeltFrame.RAIL_TOP + FullTableMap.PLATE_ABOVE_RAIL, a[1], 1e-6, what);
					assertFalse(f.onFelt(a[0], a[2]), what + " on the felt");
					assertTrue(Math.abs(a[0] - f.cx()) <= f.w() / 2 + f.rail() + 1e-4 && Math.abs(a[2] - f.cz()) <= f.d() / 2 + f.rail() + 1e-4,
						what + " past the rail (over the floor)");
				}
			}
		}
	}

	@Test
	void boardFadesFromAboveAndNear() {
		assertEquals(1f, HoloScale.boardFade(30, 5), 0);
		assertEquals(1f, HoloScale.boardFade(55, 5), 0);
		assertEquals(0.5f, HoloScale.boardFade(60, 5), 1e-5);
		assertEquals(0f, HoloScale.boardFade(65, 5), 0);
		assertEquals(0f, HoloScale.boardFade(90, 5), 0);
		assertEquals(0f, HoloScale.boardFade(20, 1.2f), 0);
		assertEquals(0.5f, HoloScale.boardFade(20, 1.5f), 1e-5);
		assertFalse(HoloScale.drawn(HoloScale.boardFade(66, 3)), "not drawn from steeply above");
		float last = 1;
		for (int p = 0; p <= 90; p++) {
			float a = HoloScale.boardFade(p, 4);
			assertTrue(a <= last + 1e-6, "monotone in pitch");
			last = a;
		}
	}

	/** Ghost plates (visual QA round 1 #4): a plate that loses fades to exactly 0 within the fade time and is not drawn. */
	@Test
	void losingPlatesFadeToZero() {
		for (long frameNs : new long[] {6_944_444L, 16_666_667L, 50_000_000L}) {
			float a = 1;
			int frames = 0;
			while (a > 0 && frames < 1000) {
				a = HoloScale.plateFade(a, false, frameNs);
				frames++;
			}
			assertEquals(0f, a, 0, "reaches 0");
			assertTrue(frames * frameNs <= HoloScale.PLATE_FADE_NS + 2 * frameNs, "within the fade time (+ float rounding)");
			assertFalse(HoloScale.drawn(a));
			// a half-faded plate never sticks at a partial alpha
			float g = HoloScale.plateFade(0.3f, false, HoloScale.PLATE_FADE_NS);
			assertEquals(0f, g, 0);
			assertEquals(1f, HoloScale.plateFade(0.3f, true, HoloScale.PLATE_FADE_NS), 0);
		}
		assertFalse(HoloScale.drawn(0.039f));
		assertTrue(HoloScale.drawn(0.04f));
	}
}
