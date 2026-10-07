package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.FreeSlot;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The round's net ("YOU WIN +125", "LOST 50", "PUSH") for {@link #SHOW_MS}, then it folds into the status line
 * (docs/architecture/ui-v2.md §2.1). Placed only in a free region the game layout declares ({@link #regions}, via
 * {@link FreeSlot}) clear of its obstacles (cards, bets, layout numbers); skipped when nothing fits. Wins ≥ 10× the bet
 * are the game's {@code CelebrationOverlay} instead (the game simply does not call {@link #show}).
 */
public final class ResultBanner {
	public static final int SHOW_MS = 1500, IN_MS = 150;

	private List<Box> regions = List.of();
	private List<Box> obstacles = List.of();
	private @Nullable Component word;
	private long net;
	private long shownAt = -1;
	private int durationMs = SHOW_MS;
	private Tier tier = Tier.T2;
	private Box box = Box.EMPTY;

	/** The free regions (in preference order) and obstacles of the current layout; called from the game's layoutPlay. */
	public void regions(List<Box> regions, List<Box> obstacles) {
		this.regions = List.copyOf(regions);
		this.obstacles = List.copyOf(obstacles);
	}

	/** Shows the result: word = {@link V2Keys#BANNER_YOU_WIN} etc. by the net's sign. */
	public void show(long net) {
		show(Component.translatable(net > 0 ? V2Keys.BANNER_YOU_WIN : net < 0 ? V2Keys.BANNER_YOU_LOST : V2Keys.BANNER_PUSH), net, SHOW_MS);
	}

	public void show(Component word, long net, int ms) {
		this.word = word;
		this.net = net;
		this.shownAt = Util.getMillis();
		this.durationMs = ms;
	}

	public void hide() {
		shownAt = -1;
	}

	public boolean active() {
		return shownAt >= 0 && Util.getMillis() - shownAt < durationMs;
	}

	/** Whether the banner has been shown and is over (the status line then carries {@link #summary}). */
	public boolean folded() {
		return shownAt >= 0 && !active();
	}

	/** "YOU WIN +125" for the status line. */
	public @Nullable Component summary() {
		if (word == null) return null;
		return net == 0 ? word : word.copy().append(" ").append(V2Draw.net(net)); // literal-ok: a space
	}

	public long net() {
		return net;
	}

	void tier(Tier t) {
		this.tier = t;
	}

	/** The box it would use now (empty = skipped). */
	public Box box() {
		return box;
	}

	void layout(Font font) {
		box = Box.EMPTY;
		if (word == null) return;
		int scale = tier == Tier.T1 ? 1 : 2;
		int h = tier == Tier.T3 ? 36 : tier == Tier.T2 ? 28 : 18;
		int need = (font.width(word) + font.width(V2Draw.net(net))) * scale + 16 * scale + 16;
		int w = Math.max(tier == Tier.T3 ? 300 : tier == Tier.T2 ? 232 : 160, need);
		box = FreeSlot.first(regions, w, h, obstacles).or(() -> FreeSlot.first(regions, need, h, obstacles)).orElse(Box.EMPTY);
	}

	public void draw(GuiGraphicsExtractor g, Font font) {
		if (!active() || word == null) return;
		layout(font);
		if (box.isEmpty()) return;
		long age = Util.getMillis() - shownAt;
		float a = FxSettings.reduceMotion() ? 1f : Math.min(1f, age / (float) IN_MS);
		long left = durationMs - age;
		if (!FxSettings.reduceMotion() && left < IN_MS) a = Math.max(0, left / (float) IN_MS);
		if (a <= 0.02f) return;
		int scale = tier == Tier.T1 ? 1 : 2;
		if (!V2Draw.sprite(g, V2Sprites.BANNER, box, CasinoPalette.withAlpha(0xFFFFFFFF, a))) {
			V2Draw.fill(g, box, V2Draw.alpha(CasinoPalette.V2_GOLD, a));
			V2Draw.fill(g, box.inset(2), V2Draw.alpha(CasinoPalette.V2_INK, a));
		}
		int ty = box.y() + (box.h() - 7 * scale) / 2;
		int wordColor = net < 0 ? CasinoPalette.V2_LOSE : net == 0 ? CasinoPalette.V2_PUSH : CasinoPalette.V2_GOLD;
		V2Draw.text(g, font, word, box.x() + 8 * scale, ty, scale, V2Draw.alpha(wordColor, a), true);
		if (net != 0) {
			Component n = V2Draw.net(net);
			V2Draw.text(g, font, n, box.right() - 8 * scale - font.width(n) * scale, ty, scale, V2Draw.alpha(V2Draw.netColor(net), a), true);
		}
	}
}
