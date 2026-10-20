package dev.nezo.burmaldaholic.client.board;

import dev.nezo.burmaldaholic.core.ui.v2.board.HoloScale;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The pooled per-table buffers behind a {@link BoardState} (one per block entity, kept by {@link BoardRenderer}):
 * cards, stacks, board lines, plates and their cached text. The render state is created per frame by vanilla; the
 * heavy arrays live here so a frame allocates nothing. Text is cached per slot by the caller's key and the language:
 * a {@link FormattedCharSequence} is built only when the key changes (ui-v2.md §7.1).
 */
final class BoardModel {
	static final int MAX_FAN = 6;
	static final int MAX_CARDS = 32, MAX_STACKS = 16, MAX_LINES = 3, MAX_PLATES = 8, MAX_LABELS = 8, MAX_CONTENT = 6;

	/** A cached line of text. */
	static final class Line {
		long key = Long.MIN_VALUE;
		String lang = "";
		@Nullable FormattedCharSequence seq;
		int width;
		int color;
		/** Size of this line relative to the panel's text px (2 = the prominent line). */
		float scale = 1f;
		boolean on;

		void set(Font font, long key, String lang, Supplier<? extends @Nullable Component> text, int color) {
			if (key != this.key || !lang.equals(this.lang) || seq == null) {
				Component c = text.get();
				seq = c == null ? null : c.getVisualOrderText();
				width = seq == null ? 0 : font.width(seq);
				this.key = key;
				this.lang = lang;
			}
			this.color = color;
			this.scale = 1f;
			on = seq != null;
		}
	}

	/** A seat plate: anchor (table-local) + two lines + flags. */
	static final class Plate {
		int seat = -1;
		float ax, ay, az;
		final Line name = new Line(), detail = new Line();
		boolean viewer, acting, on;
		/** Visible cards of this seat shown on the plate (−1 = a back), within {@link BoardState#FAN_RANGE}. */
		final int[] cards = new int[MAX_FAN];
		int cardCount;
		/** Fade of the plate (0 … 1) toward its frame-level visibility ({@link PlateFrame}); a new plate fades in. */
		float alpha;
		/** The {@link PlateFrame} frame this plate was last laid out in (a gap restarts the fade-in). */
		long seenFrame = Long.MIN_VALUE;
		/** The handle {@link BoardState#plate} returns (pooled: no allocation per plate and frame). */
		final BoardState.PlateRef ref = new BoardState.PlateRef(this);
	}

	/** A free label (legacy billboard text, e.g. the poker pot). */
	static final class Label {
		float x, y, z;
		@Nullable FormattedCharSequence seq;
		int color;
	}

	final BoardCard[] cards = new BoardCard[MAX_CARDS];
	int cardCount;
	final float[] stackX = new float[MAX_STACKS], stackZ = new float[MAX_STACKS], stackAlpha = new float[MAX_STACKS];
	final long[] stackAmount = new long[MAX_STACKS];
	final int[] stackTint = new int[MAX_STACKS];
	int stackCount;
	final Line[] lines = new Line[MAX_LINES];
	/** The dealer's / board's visible cards shown on the floating board (−1 = a back). */
	final int[] boardCards = new int[MAX_FAN];
	int boardCardCount;
	final Plate[] plates = new Plate[MAX_PLATES];
	int plateCount;
	final Label[] labels = new Label[MAX_LABELS];
	int labelCount;
	/** Extra content obstacles (wheels, dice): x, y, z, radius. */
	final float[] content = new float[MAX_CONTENT * 4];
	int contentCount;
	/** The deck (≤ 1 per table): a squared card stack, see {@link BoardState#deck}. */
	boolean hasDeck;
	float deckX, deckZ, deckYaw, deckW, deckH;
	int deckCards;
	/** Table limits for the furniture's placard (−1 = not given this frame). */
	long limitMin = -1, limitMax = -1;
	/** The dealer / community card area (table-local x0, z0, x1, z1, yTop), an obstacle for the plates; see {@link BoardRenderer#dealerRect}. */
	final float[] dealer = new float[5];
	boolean hasDealer;

	// ---- per-table memory across frames ----
	HoloScale.@Nullable Lod lod;
	/** PlateLayout's 1-frame memory per plate slot. */
	final int[] plateShift = new int[MAX_PLATES];
	/** Game-side cache owned by the renderer subclass (timelines, keys). */
	@Nullable Object game;

	BoardModel() {
		for (int i = 0; i < MAX_CARDS; i++) cards[i] = new BoardCard();
		for (int i = 0; i < MAX_LINES; i++) lines[i] = new Line();
		for (int i = 0; i < MAX_PLATES; i++) plates[i] = new Plate();
		for (int i = 0; i < MAX_LABELS; i++) labels[i] = new Label();
		java.util.Arrays.fill(plateShift, -1);
	}

	void clear() {
		cardCount = 0;
		stackCount = 0;
		plateCount = 0;
		labelCount = 0;
		contentCount = 0;
		boardCardCount = 0;
		limitMin = limitMax = -1;
		hasDealer = false;
		hasDeck = false;
		for (Line l : lines) l.on = false;
	}
}
