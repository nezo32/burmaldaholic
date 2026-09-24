package dev.nezo.burmaldaholic.client.table.cards;

import dev.nezo.burmaldaholic.client.fx.FxSounds;
import dev.nezo.burmaldaholic.client.fx.FxSprites;
import dev.nezo.burmaldaholic.core.anim.cards.CardMotion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Per-slot card animator of the kit (J-C5; docs/design/animation/cards.md §0.2): every frame the screen declares the
 * cards of the PUBLISHED state ({@link #card}: key, code or {@code -1} for a back, size, target, the shared-clock time
 * the card left the shoe and the time its face turns); the animator diffs against the previous frame and plays
 *
 * <ul>
 *   <li>empty → card: K1 deal arc from the shoe ({@link #origin}), face down; a face-up card flips on landing (K2);</li>
 *   <li>back → face: K2 flip at the reveal time, or the K3 squeeze ({@link #squeeze});</li>
 *   <li>moved target: K4 slide from where it is drawn;</li>
 *   <li>card → gone: K5 gather to the tray ({@link #tray}), 25 ms stagger;</li>
 * </ul>
 * plus K9 glow, K11 dim and the lift of a best / active card. Times are the shared clock (level game time × 50 ms) so
 * every viewer sees the same beat; a tween is played only while {@code now − beat < 2 × duration}, otherwise the card
 * is drawn settled (catch-up, late join). Faces come only from the published codes, so no frame can show a card the
 * server has not revealed (§0.7.2). Reduced motion: fades and cross-fades (§0.6). Card objects are pooled per key; the
 * steady state allocates nothing.
 */
public final class CardAnimator {
	/** One card slot. */
	public static final class Slot {
		int key;
		int code = -1;
		int size;
		int back;
		double tx;
		double ty;
		double rot;
		double dealAt = Double.NaN;
		double revealAt = Double.NaN;
		double moveAt = Double.NaN;
		double fromX;
		double fromY;
		double drawnX;
		double drawnY;
		double squeezeAt = Double.NaN;
		int squeezeMs;
		boolean squeezeFromTop;
		boolean dim;
		double dimAt = Double.NaN;
		boolean glow;
		boolean lifted;
		int seen;
		boolean leaving;
		double leaveAt;
		boolean sDeal;
		boolean sLand;
		boolean sFlip;
		boolean sSqueeze;
		boolean sSnap;
	}

	private final Map<Integer, Slot> slots = new HashMap<>();
	private final List<Slot> order = new ArrayList<>();
	private final List<Slot> leaving = new ArrayList<>();
	private final CardMotion.Pose pose = new CardMotion.Pose();
	private final CardMotion.Pose flipPose = new CardMotion.Pose();
	private double originX;
	private double originY;
	private double trayX;
	private double trayY;
	private int frame;
	private double now;
	private boolean reduced;
	private boolean gather = true;
	private boolean sounds = true;

	/** Shoe mouth (K1 start) in layout pixels. */
	public CardAnimator origin(double x, double y) {
		this.originX = x;
		this.originY = y;
		return this;
	}

	/** Discard tray (K5 target). */
	public CardAnimator tray(double x, double y) {
		this.trayX = x;
		this.trayY = y;
		return this;
	}

	/** Whether removed cards gather to the tray (else they vanish). */
	public CardAnimator gatherOnRemove(boolean on) {
		this.gather = on;
		return this;
	}

	/** Mute the per-card sounds (spectator copies, tests). */
	public CardAnimator sounds(boolean on) {
		this.sounds = on;
		return this;
	}

	/** Starts a frame at shared time {@code nowMs}. */
	public void begin(double nowMs, boolean reducedMotion) {
		frame++;
		now = nowMs;
		reduced = reducedMotion;
		order.clear();
	}

	/**
	 * Declares a card of the published state.
	 *
	 * @param key      stable slot key (e.g. {@code seat × 1000 + hand × 100 + index})
	 * @param code     card code 0..51, or -1 for a face-down card
	 * @param size     {@link CardSprites#L} / M / S
	 * @param back     back design row
	 * @param x        target left (of the footprint: a sideways card is 49 × 37)
	 * @param y        target top
	 * @param rotDeg   resting rotation (0, or 90 for the sideways double / third card)
	 * @param dealMs   shared time the card left the shoe (NaN = it appears in place)
	 * @param revealMs shared time its face starts to turn (NaN = at once when the code is known)
	 */
	public Slot card(int key, int code, int size, int back, double x, double y, double rotDeg, double dealMs, double revealMs) {
		Slot s = slots.get(key);
		if (s == null || s.leaving) {
			if (s != null) leaving.remove(s);
			s = new Slot();
			s.key = key;
			s.code = code;
			s.size = size;
			s.tx = x;
			s.ty = y;
			s.drawnX = x;
			s.drawnY = y;
			s.rot = rotDeg;
			s.dealAt = dealMs;
			s.revealAt = code >= 0 ? (Double.isNaN(revealMs) ? (Double.isNaN(dealMs) ? Double.NaN : dealMs + CardMotion.DEAL_MS) : revealMs) : Double.NaN;
			// a tween that is long over is drawn settled, silently
			s.sDeal = s.sLand = Double.isNaN(dealMs) || now - dealMs > 2 * CardMotion.DEAL_MS;
			s.sFlip = Double.isNaN(s.revealAt) || now - s.revealAt > 2 * CardMotion.FLIP_MS;
			slots.put(key, s);
		} else {
			if (s.code < 0 && code >= 0) {
				s.revealAt = Double.isNaN(revealMs) ? now : revealMs;
				s.sFlip = now - s.revealAt > 2 * CardMotion.FLIP_MS;
			} else if (code != s.code && code >= 0 && s.code >= 0) {
				s.revealAt = Double.NaN; // a different card in the slot (layout change): no animation
			}
			s.code = code;
			if (s.size != size) s.size = size;
			if (Math.abs(s.tx - x) > 0.5 || Math.abs(s.ty - y) > 0.5 || s.rot != rotDeg) {
				s.fromX = s.drawnX;
				s.fromY = s.drawnY;
				s.moveAt = now;
				s.tx = x;
				s.ty = y;
				s.rot = rotDeg;
			}
		}
		s.back = back;
		s.seen = frame;
		s.glow = false;
		s.lifted = false;
		s.dim = false; // dim() re-arms it this frame; end() clears the fade of cards no longer dimmed
		order.add(s);
		return s;
	}

	/** K3 squeeze of the declared card from {@code startMs} for {@code durMs} (Banker hands peel from the top). */
	public void squeeze(Slot s, double startMs, int durMs, boolean fromTop) {
		if (s.squeezeAt != startMs) {
			s.squeezeAt = startMs;
			s.squeezeMs = Math.max(1, durMs);
			s.squeezeFromTop = fromTop;
			s.revealAt = Double.NaN;
			boolean old = now - (startMs + durMs) > 2 * CardMotion.FLIP_MS;
			s.sSqueeze = old;
			s.sSnap = old;
		}
	}

	/** K11 dim (losing hand, bust) for this frame; the fade starts the first frame it is set. */
	public void dim(Slot s, boolean on) {
		if (on) {
			if (Double.isNaN(s.dimAt)) s.dimAt = now;
			s.dim = true;
		} else {
			s.dimAt = Double.NaN;
		}
	}

	/** K9 glow ring behind the card this frame. */
	public void glow(Slot s, boolean on) {
		s.glow = on;
	}

	/** Lift the card 3 px (best five, the active card). */
	public void lift(Slot s, boolean on) {
		s.lifted = on;
	}

	/** Ends the frame's declarations: cards not declared start gathering (or vanish). */
	public void end() {
		for (Slot s : order) if (!s.dim) s.dimAt = Double.NaN;
		int n = 0;
		for (Iterator<Map.Entry<Integer, Slot>> it = slots.entrySet().iterator(); it.hasNext();) {
			Slot s = it.next().getValue();
			if (s.seen == frame || s.leaving) continue;
			if (gather && !reduced) {
				s.leaving = true;
				s.leaveAt = now + CardMotion.gatherDelay(n++);
				s.fromX = s.drawnX;
				s.fromY = s.drawnY;
				leaving.add(s);
			} else if (gather) {
				s.leaving = true;
				s.leaveAt = now;
				s.fromX = s.drawnX;
				s.fromY = s.drawnY;
				leaving.add(s);
			} else {
				it.remove();
			}
		}
		if (n > 0 && sounds) FxSounds.play("card_gather", 0.8f, 1f);
	}

	/** Drops every card at once (a new table / layout). */
	public void clear() {
		slots.clear();
		order.clear();
		leaving.clear();
	}

	/** Whether any card is still moving (tests: settle checks). */
	public boolean busy() {
		for (Slot s : order) {
			if (!Double.isNaN(s.dealAt) && now - s.dealAt < CardMotion.DEAL_MS) return true;
			if (!Double.isNaN(s.revealAt) && now - s.revealAt < CardMotion.FLIP_MS) return true;
			if (!Double.isNaN(s.squeezeAt) && now < s.squeezeAt + s.squeezeMs) return true;
		}
		return !leaving.isEmpty();
	}

	/** Shows whether slot {@code key}'s face is visible now (after its flip / squeeze). */
	public boolean faceShown(int key) {
		Slot s = slots.get(key);
		if (s == null || s.code < 0) return false;
		if (!Double.isNaN(s.squeezeAt)) return now >= s.squeezeAt + s.squeezeMs * 0.88;
		return Double.isNaN(s.revealAt) || now >= s.revealAt + CardMotion.FLIP_MS / 2.0;
	}

	/** Draws the declared cards (declaration order) and the gathering ones on top. */
	public void draw(GuiGraphicsExtractor g, Font font) {
		for (Slot s : order) drawSlot(g, font, s);
		for (Iterator<Slot> it = leaving.iterator(); it.hasNext();) {
			Slot s = it.next();
			double t = CardMotion.progress(now, s.leaveAt, reduced ? CardMotion.REDUCED_FADE_MS * 2 : CardMotion.GATHER_MS);
			if (t >= 1) {
				it.remove();
				slots.remove(s.key, s);
				continue;
			}
			CardMotion.gather(pose, s.fromX, s.fromY, trayX, trayY, t, s.code >= 0, reduced);
			paint(g, font, s, pose, s.code >= 0 && pose.face, 0);
		}
	}

	private void drawSlot(GuiGraphicsExtractor g, Font font, Slot s) {
		boolean dealing = !Double.isNaN(s.dealAt);
		if (dealing && now < s.dealAt) return; // not dealt yet on this clock
		double tx = s.tx;
		double ty = s.ty;
		if (!Double.isNaN(s.moveAt)) {
			double t = CardMotion.progress(now, s.moveAt, CardMotion.SLIDE_MS);
			if (t >= 1 || reduced) {
				s.moveAt = Double.NaN;
			} else {
				double p = CardMotion.slide(t);
				tx = s.fromX + (s.tx - s.fromX) * p;
				ty = s.fromY + (s.ty - s.fromY) * p;
			}
		}
		double dealElapsed = dealing ? now - s.dealAt : Double.MAX_VALUE;
		if (dealing && dealElapsed < CardMotion.DEAL_MS && CardMotion.playTween(dealElapsed, CardMotion.DEAL_MS)) {
			if (!s.sDeal) {
				s.sDeal = true;
				if (sounds) FxSounds.play("card_slide", 0.3f, 1f);
			}
			int seed = CardMotion.seed(0, 0, s.key);
			CardMotion.deal(pose, originX, originY, tx, ty, dealElapsed / CardMotion.DEAL_MS, s.rot, seed, reduced);
			s.drawnX = pose.x;
			s.drawnY = pose.y;
			paint(g, font, s, pose, false, 0);
			return;
		}
		if (!s.sLand) {
			s.sLand = true;
			if (sounds) FxSounds.play("card_deal", 1f, CardMotion.dealPitch(CardMotion.seed(0, 0, s.key)));
		}
		pose.rest(tx, ty, s.rot, s.code >= 0);
		if (s.lifted) pose.y -= 3;
		s.drawnX = tx;
		s.drawnY = ty;
		// K1b settle for a face-down landing
		if (dealing && s.code < 0 && !reduced && dealElapsed < CardMotion.DEAL_MS + CardMotion.SETTLE_MS) {
			pose.y += CardMotion.settleDy((dealElapsed - CardMotion.DEAL_MS) / CardMotion.SETTLE_MS);
		}
		if (s.code < 0) {
			paint(g, font, s, pose, false, 0);
			return;
		}
		// squeeze (K3)
		if (!Double.isNaN(s.squeezeAt)) {
			double e = now - s.squeezeAt;
			if (e < 0) {
				paint(g, font, s, pose, false, 0);
				return;
			}
			if (e < s.squeezeMs) {
				if (!s.sSqueeze) {
					s.sSqueeze = true;
					if (sounds) FxSounds.play("card_squeeze", 1f, 1f);
				}
				double u = e / s.squeezeMs;
				if (u >= 0.88 && !s.sSnap) {
					s.sSnap = true;
					if (sounds) FxSounds.play("card_flip", 1f, 1f);
				}
				paintSqueeze(g, font, s, pose, u);
				return;
			}
			paint(g, font, s, pose, true, 0);
			return;
		}
		// flip (K2)
		if (!Double.isNaN(s.revealAt)) {
			double e = now - s.revealAt;
			if (e < 0) {
				paint(g, font, s, pose, false, 0);
				return;
			}
			if (e < CardMotion.FLIP_MS && CardMotion.playTween(e, CardMotion.FLIP_MS)) {
				if (!s.sFlip && e >= 120) {
					s.sFlip = true;
					if (sounds) FxSounds.play("card_flip", 1f, 1f);
				}
				if (reduced) {
					double fa = CardMotion.reducedFlipFace(e / CardMotion.REDUCED_FLIP_MS);
					paint(g, font, s, pose, false, 0);
					if (fa > 0) {
						pose.alpha = fa;
						paint(g, font, s, pose, true, 0);
					}
					return;
				}
				CardMotion.flip(flipPose, e / CardMotion.FLIP_MS);
				pose.scaleX = flipPose.scaleX;
				pose.y += flipPose.y;
				pose.highlight = flipPose.highlight;
				paint(g, font, s, pose, flipPose.face, 0);
				return;
			}
			s.sFlip = true;
		}
		paint(g, font, s, pose, true, 0);
	}

	/** Footprint → box transform, then glow, shadow, card, dim and highlight. */
	private void paint(GuiGraphicsExtractor g, Font font, Slot s, CardMotion.Pose p, boolean face, int unused) {
		int w = CardSprites.w(s.size);
		int h = CardSprites.h(s.size);
		boolean side = Math.abs(p.rot - 90) < 45 && Math.abs(s.rot - 90) < 1;
		int fw = side ? h : w;
		int fh = side ? w : h;
		double cx = p.x + fw / 2.0;
		double cy = p.y + fh / 2.0;
		double sx = p.scale * p.scaleX;
		double sy = p.scale;
		if (sx <= 0.01) return;
		CardGfx.pushBox(g, cx - w / 2.0, cy - h / 2.0, w, h, p.rot, sx, sy);
		if (s.glow) CardSprites.glow(g, s.size, 0, 0, CardMotion.glowAlpha((long) now, reduced, true) * p.alpha);
		if (s.size != CardSprites.S || p.shadowDx > 1) {
			CardSprites.shadow(g, s.size, (int) Math.round(p.shadowDx), (int) Math.round(p.shadowDy - 1), p.alpha);
		}
		int tint = dimTint(s, p.alpha);
		if (face && s.code >= 0) CardSprites.face(g, font, s.code, s.size, 0, 0, tint);
		else CardSprites.back(g, s.back, s.size, 0, 0, tint);
		double d = dimAmount(s);
		if (d > 0) g.fill(0, 0, w, h, CardGfx.alpha(0x40706878, d * p.alpha));
		if (p.highlight > 0) g.fill(0, 0, w, h, CardGfx.white(p.highlight * p.alpha));
		CardGfx.pop(g);
	}

	private double dimAmount(Slot s) {
		if (!s.dim || Double.isNaN(s.dimAt)) return 0;
		return reduced ? 1 : CardMotion.desat(CardMotion.progress(now, s.dimAt, CardMotion.DESAT_MS));
	}

	/** Face tint: × 0.72 brightness when dimmed (K11), times the pose alpha. */
	private int dimTint(Slot s, double alpha) {
		double d = dimAmount(s);
		int c = (int) Math.round(255 - (255 - 184) * d);
		int a = (int) Math.round(255 * Math.max(0, Math.min(1, alpha)));
		return (a << 24) | (c << 16) | (c << 8) | c;
	}

	private void paintSqueeze(GuiGraphicsExtractor g, Font font, Slot s, CardMotion.Pose p, double u) {
		int w = CardSprites.w(s.size);
		int h = CardSprites.h(s.size);
		boolean side = Math.abs(s.rot - 90) < 1;
		int fw = side ? h : w;
		int fh = side ? w : h;
		double cx = p.x + fw / 2.0;
		double cy = p.y + fh / 2.0 - (reduced ? 0 : CardMotion.squeezeLift(u));
		double pop = reduced ? 1 : CardMotion.squeezePop(u);
		CardGfx.pushBox(g, cx - w / 2.0, cy - h / 2.0, w, h, s.rot, pop, pop);
		if (s.glow) CardSprites.glow(g, s.size, 0, 0, CardMotion.glowAlpha((long) now, reduced, true));
		CardSprites.shadow(g, s.size, 1, reduced ? 0 : 1, 1);
		if (reduced) {
			CardSprites.back(g, s.back, s.size, 0, 0, 0xFFFFFFFF);
			int bw = (int) Math.round(Math.min(1, u) * (w - 4));
			g.fill(2, h + 2, w - 2, h + 3, 0x80180A28);
			CardGfx.sprite(g, FxSprites.sprite("cards/fx/progress"), 2, h + 1, Math.max(1, bw), 3, 0xFFFFFFFF, 0xFFFFD640);
			CardGfx.pop(g);
			return;
		}
		double f = CardMotion.squeeze(u);
		int band = (int) Math.round(f * h);
		if (s.squeezeFromTop) {
			CardSprites.faceRows(g, font, s.code, s.size, 0, 0, 0, band, 0xFFFFFFFF);
			CardSprites.backRows(g, s.back, s.size, 0, 0, band, h, 0xFFFFFFFF);
			if (f < 1) CardSprites.curl(g, s.size, 0, band, CardMotion.curlScale(f), 1);
		} else {
			CardSprites.backRows(g, s.back, s.size, 0, 0, 0, h - band, 0xFFFFFFFF);
			CardSprites.faceRows(g, font, s.code, s.size, 0, 0, h - band, h, 0xFFFFFFFF);
			if (f < 1) CardSprites.curl(g, s.size, 0, h - band, CardMotion.curlScale(f), 1);
		}
		CardGfx.pop(g);
	}
}
