package dev.nezo.burmaldaholic.client.board;

import dev.nezo.burmaldaholic.client.render.SpectatorRenderState;
import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;
import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * What a table's board shows this frame (docs/architecture/ui-v2.md §7.1). A game's {@link BoardRenderer#fill}
 * writes it, in block-local table coordinates ({@link BoardGeometry}: x right, z toward the players, the felt at
 * {@link BoardGeometry#TOP}); the kit's painters draw it. Everything is budgeted and pooled: calls past a budget are
 * dropped silently (a scratch object is returned), so a game can never blow the frame.
 *
 * <ul>
 *   <li>{@link #card} — a card on the felt (1.5 × screen size by default, leaning toward its owner);</li>
 *   <li>{@link #stack} — a chip stack (≤ 6 discs, denominations greedy);</li>
 *   <li>{@link #line} — the floating board's lines: 0 title (gold), 1 state, 2 turn / result (cached by key);</li>
 *   <li>{@link #plate} — a seat plate (name + one detail line), placed collision-free above the content;</li>
 *   <li>{@link #content} — an extra obstacle plates must stay above (a wheel, dice).</li>
 * </ul>
 * Text calls are cheap no-ops when the LOD hides text ({@link #showsBoard()}, {@link #showsPlates()}).
 */
public class BoardState extends SpectatorRenderState {
	/** Line slots of the board. */
	public static final int TITLE = 0, STATE = 1, RESULT = 2;
	/** Board colours (REDESIGN §9.2). */
	public static final int GOLD = 0xFFFFD640, WHITE = 0xFFF4ECF8, MUTED = 0xFFB8A8D0, CYAN = 0xFF5CE8E0, GREEN = 0xFF80FF40, RED = 0xFFFF6E6A;

	public Direction facing = Direction.NORTH;
	/**
	 * The felt the game lays out on (its full shape). When the overhang is blocked the kit draws
	 * {@link #drawnFelt} = {@link BoardGeometry.Felt#FALLBACK} and shrinks all content by {@link #contentScale} around
	 * the centre, so a game never has to handle the fallback itself.
	 */
	public BoardGeometry.Felt felt = BoardGeometry.Felt.CRESCENT;
	/** The felt actually drawn. */
	public BoardGeometry.Felt drawnFelt = BoardGeometry.Felt.CRESCENT;
	/** Content scale around the felt centre (1, or < 1 on the fallback felt). */
	public float contentScale = 1f;
	/** Felt theme: 0 village, 1 bastion, 2 end. */
	public int theme;
	/** Card back row of {@code faces_w2.png} (0 navy … 5 end; see {@link BoardTextures}). */
	public int back = 2;
	/** Board LOD this frame (with hysteresis). */
	public HoloScale.Lod hlod = HoloScale.Lod.MODEL;
	/** Distance camera → felt centre (blocks). */
	public double dist;
	/** Text pixel sizes in blocks for the board and the plates (from {@link HoloScale#textPx}). */
	public float boardPx = HoloScale.BOARD_BASE, platePx = HoloScale.PLATE_BASE;
	/** The local player's UUID (plates mark "you" in cyan). */
	public java.util.@Nullable UUID viewer;

	@Nullable BoardModel model;
	@Nullable Font font;
	String lang = "";
	final BoardCard scratchCard = new BoardCard();

	/** True when the felt, cards and chips are drawn at all (≤ 32 blocks). */
	public boolean visibleBoard() {
		return model != null && hlod != HoloScale.Lod.MODEL;
	}

	/** True when board lines are drawn (≤ 16 blocks; the title only ≤ 8). */
	public boolean showsBoard() {
		return hlod == HoloScale.Lod.FULL || hlod == HoloScale.Lod.BOARD;
	}

	/** True when seat plates are drawn (≤ 8 blocks). */
	public boolean showsPlates() {
		return hlod == HoloScale.Lod.FULL;
	}

	// ---- cards and chips ----------------------------------------------------------------------------------------

	/** A card of the default in-world size at (x, z), {@code yaw}° (0 = its top edge toward the dealer, −z). */
	public BoardCard card(float x, float z, float yaw, int face) {
		return card(x, z, yaw, BoardGeometry.CARD_W, BoardGeometry.CARD_H, face);
	}

	/** A card of an explicit size (blocks). Past the budget a scratch card is returned and nothing is drawn. */
	public BoardCard card(float x, float z, float yaw, float w, float h, int face) {
		BoardModel m = model;
		if (m == null || m.cardCount >= BoardModel.MAX_CARDS) {
			scratchCard.set(x, z, yaw, w, h, face);
			return scratchCard;
		}
		BoardCard c = m.cards[m.cardCount++];
		c.set(x, z, yaw, w, h, face);
		return c;
	}

	/** A chip stack of {@code amount} chips at (x, z). */
	public void stack(float x, float z, long amount) {
		stack(x, z, amount, 1f, 0);
	}

	/** A chip stack; {@code alpha} < 1 fades it (pending / leaving); {@code tint} ≠ 0 rings it in a player colour. */
	public void stack(float x, float z, long amount, float alpha, int tint) {
		BoardModel m = model;
		if (m == null || amount <= 0 || m.stackCount >= BoardModel.MAX_STACKS) return;
		int i = m.stackCount++;
		m.stackX[i] = x;
		m.stackZ[i] = z;
		m.stackAmount[i] = amount;
		m.stackAlpha[i] = alpha;
		m.stackTint[i] = tint;
	}

	/** An extra obstacle (a wheel, the dice) plates must stay above: centre (x, y, z) and radius in blocks. */
	public void content(float x, float y, float z, float r) {
		BoardModel m = model;
		if (m == null || m.contentCount >= BoardModel.MAX_CONTENT) return;
		int k = m.contentCount++ * 4;
		m.content[k] = x;
		m.content[k + 1] = y;
		m.content[k + 2] = z;
		m.content[k + 3] = r;
	}

	// ---- text ---------------------------------------------------------------------------------------------------

	/**
	 * Sets board line {@code slot} ({@link #TITLE}, {@link #STATE}, {@link #RESULT}). {@code text} is called only when
	 * {@code key} (any hash of what the line shows) or the language changed — never per frame.
	 */
	public void line(int slot, long key, Supplier<? extends @Nullable Component> text, int color) {
		BoardModel m = model;
		if (m == null || font == null || slot < 0 || slot >= BoardModel.MAX_LINES || !showsBoard()) return;
		m.lines[slot].set(font, key, lang, text, color);
	}

	/**
	 * A seat plate for {@code seat} anchored at table-local (ax, ay, az) — usually {@link BoardGeometry#plateAnchor}.
	 * Returns a handle to set the two lines; null (and nothing drawn) beyond 8 blocks or past the budget.
	 */
	public @Nullable PlateRef plate(int seat, float ax, float ay, float az) {
		BoardModel m = model;
		if (m == null || font == null || !showsPlates() || m.plateCount >= BoardModel.MAX_PLATES) return null;
		BoardModel.Plate p = m.plates[m.plateCount];
		// the text cache follows the seat: a plate keeps its slot while the seat is stable
		if (p.seat != seat) {
			for (int k = m.plateCount + 1; k < BoardModel.MAX_PLATES; k++) {
				if (m.plates[k].seat == seat) {
					BoardModel.Plate other = m.plates[k];
					m.plates[k] = p;
					m.plates[m.plateCount] = other;
					int sh = m.plateShift[k];
					m.plateShift[k] = m.plateShift[m.plateCount];
					m.plateShift[m.plateCount] = sh;
					p = other;
					break;
				}
			}
		}
		m.plateCount++;
		p.seat = seat;
		p.ax = ax;
		p.ay = ay;
		p.az = az;
		p.viewer = false;
		p.acting = false;
		p.on = true;
		p.name.on = false;
		p.detail.on = false;
		return new PlateRef(this, p);
	}

	/** A small free label (legacy billboard text, e.g. a pot amount) within 8 blocks. */
	public void label(float x, float y, float z, @Nullable FormattedCharSequence text, int color) {
		BoardModel m = model;
		if (m == null || text == null || !showsPlates() || m.labelCount >= BoardModel.MAX_LABELS) return;
		BoardModel.Label l = m.labels[m.labelCount++];
		l.x = x;
		l.y = y;
		l.z = z;
		l.seq = text;
		l.color = color;
	}

	/**
	 * The renderer subclass's own per-table cache (timeline, last seq …), created on first use. Lives as long as the
	 * block entity.
	 */
	@SuppressWarnings("unchecked")
	public <C> C cache(Supplier<C> create) {
		BoardModel m = model;
		if (m == null) return create.get();
		if (m.game == null) m.game = create.get();
		return (C) m.game;
	}

	/** Handle of one plate being filled. */
	public static final class PlateRef {
		private final BoardState state;
		private final BoardModel.Plate plate;

		PlateRef(BoardState state, BoardModel.Plate plate) {
			this.state = state;
			this.plate = plate;
		}

		/** The name line (cached by {@code key}); cyan for the viewer, white for others unless {@code color} ≠ 0. */
		public PlateRef name(long key, Supplier<? extends @Nullable Component> text, int color) {
			if (state.font != null) plate.name.set(state.font, key, state.lang, text, color);
			return this;
		}

		/** The detail line ({@code 50 · 15}, {@code +75 BLACKJACK}), cached by {@code key}. */
		public PlateRef detail(long key, Supplier<? extends @Nullable Component> text, int color) {
			if (state.font != null) plate.detail.set(state.font, key, state.lang, text, color);
			return this;
		}

		/** Marks the viewer's own plate (placed first, cyan name). */
		public PlateRef viewer(boolean on) {
			plate.viewer = on;
			return this;
		}

		/** Marks the acting seat (placed second, gold edge). */
		public PlateRef acting(boolean on) {
			plate.acting = on;
			return this;
		}
	}
}
