package dev.nezo.burmaldaholic.client.board;

import dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry;

/**
 * One card on the felt (block-local table coordinates, see {@link BoardGeometry}). Pooled per table; a game's
 * {@code fill} gets it from {@link BoardState#card} and may adjust the public fields (lift for a winner, dim for a
 * folded hand, flip while it turns over).
 */
public final class BoardCard {
	public float x, z, yaw, w, h;
	/** Extra height above the felt (deal arcs, winners); the base lift {@link BoardGeometry#CARD_LIFT} is added. */
	public float lift;
	/** Lean toward the owner in degrees (0 = flat; default {@link BoardGeometry#CARD_TILT}). */
	public float tilt;
	/** Card id 0..51 (rank index × 4 + suit) face up, or −1 for the back. */
	public int face = -1;
	/** 0 = lying as {@link #face} says; (0, 1) = mid-flip back → face (the face shows after ½). */
	public float flip;
	public boolean dim;

	void set(float x, float z, float yaw, float w, float h, int face) {
		this.x = x;
		this.z = z;
		this.yaw = yaw;
		this.w = w;
		this.h = h;
		this.face = face;
		this.lift = 0;
		this.tilt = BoardGeometry.CARD_TILT;
		this.flip = 0;
		this.dim = false;
	}
}
