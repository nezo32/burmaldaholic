package dev.nezo.burmaldaholic.client.ui.v2;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.core.ui.v2.Box;
import dev.nezo.burmaldaholic.core.ui.v2.FreeSlot;
import dev.nezo.burmaldaholic.core.ui.v2.Tier;
import dev.nezo.burmaldaholic.core.ui.v2.V2Keys;
import java.util.List;
import net.minecraft.ChatFormatting;
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
 *
 * <p>Two looks: the full banner (2× text; T2 232 × 28, T3 300 × 36) and the compact one ({@link #COMPACT_W} ×
 * {@link #COMPACT_H}, 1× bold; always at T1, and at T2 / T3 when the full one finds no free place — review DES 4, R-AB
 * m11). {@code net == 0} shows the word only ("17 RED" for a spectator, "PUSH"). Obstacles: the game's (from
 * {@link #regions}), any added by {@link #obstacles} during the layout pass, and the kit's (the Dock hint strip).
 */
public final class ResultBanner {
	public static final int SHOW_MS = 1500, IN_MS = 150;
	public static final int COMPACT_W = 200, COMPACT_H = 20;

	private List<Box> regions = List.of();
	private List<Box> obstacles = List.of();
	private List<Box> extra = List.of();
	private List<Box> kit = List.of();
	private boolean compact;
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

	/**
	 * More obstacles of the current layout (history pills, layout glyphs, …): kept until the next relayout. Call from
	 * {@code layoutPlay} or {@code Host.layoutOverlay}, in any order with {@link #regions}.
	 */
	public void obstacles(List<Box> more) {
		List<Box> all = new java.util.ArrayList<>(extra);
		all.addAll(more);
		extra = List.copyOf(all);
	}

	/** The chrome starts a layout pass: the {@link #obstacles} of the last pass go. */
	void beginLayout() {
		extra = List.of();
	}

	/** The kit's own obstacles of this layout (the Dock hint strip). */
	void kitObstacles(List<Box> boxes) {
		kit = List.copyOf(boxes);
	}

	/** Every obstacle the banner avoids now (tests). */
	public List<Box> allObstacles() {
		List<Box> all = new java.util.ArrayList<>(obstacles);
		all.addAll(extra);
		all.addAll(kit);
		return all;
	}

	/** Shows the result: word = {@link V2Keys#BANNER_YOU_WIN} etc. by the net's sign. */
	public void show(long net) {
		show(Component.translatable(net > 0 ? V2Keys.BANNER_YOU_WIN : net < 0 ? V2Keys.BANNER_YOU_LOST : V2Keys.BANNER_PUSH), net, SHOW_MS);
	}

	/** Shows {@code word} and the net ({@code net == 0}: the word only) for {@code ms} ms. */
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

	/** Whether the last layout chose the compact look. */
	public boolean compact() {
		return compact;
	}

	void layout(Font font) {
		box = Box.EMPTY;
		compact = false;
		if (word == null) return;
		List<Box> obs = allObstacles();
		if (tier != Tier.T1) {
			int scale = 2;
			int h = tier == Tier.T3 ? 36 : 28;
			int need = (font.width(word) + (net == 0 ? 0 : font.width(V2Draw.net(net)) + 8)) * scale + 16 * scale;
			int w = Math.max(tier == Tier.T3 ? 300 : 232, need);
			box = FreeSlot.first(regions, w, h, obs).or(() -> FreeSlot.first(regions, need, h, obs)).orElse(Box.EMPTY);
			if (!box.isEmpty()) return;
		}
		int need = font.width(bold(word)) + (net == 0 ? 0 : font.width(bold(V2Draw.net(net))) + 8) + 16;
		int w = Math.max(COMPACT_W, need);
		box = FreeSlot.first(regions, w, COMPACT_H, obs).or(() -> FreeSlot.first(regions, need, COMPACT_H, obs)).orElse(Box.EMPTY);
		compact = !box.isEmpty();
	}

	private static Component bold(Component c) {
		return c.copy().withStyle(ChatFormatting.BOLD);
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
		int scale = compact ? 1 : 2;
		Component shown = compact ? bold(word) : word;
		if (!V2Draw.sprite(g, V2Sprites.BANNER, box, CasinoPalette.withAlpha(0xFFFFFFFF, a))) {
			V2Draw.fill(g, box, V2Draw.alpha(CasinoPalette.V2_GOLD, a));
			V2Draw.fill(g, box.inset(2), V2Draw.alpha(CasinoPalette.V2_INK, a));
		}
		int ty = box.y() + (box.h() - 7 * scale) / 2;
		int wordColor = net < 0 ? CasinoPalette.V2_LOSE : net == 0 ? CasinoPalette.V2_PUSH : CasinoPalette.V2_GOLD;
		if (net == 0) {
			V2Draw.text(g, font, shown, box.x() + (box.w() - font.width(shown) * scale) / 2, ty, scale, V2Draw.alpha(wordColor, a), true);
			return;
		}
		V2Draw.text(g, font, shown, box.x() + 8 * scale, ty, scale, V2Draw.alpha(wordColor, a), true);
		Component n = compact ? bold(V2Draw.net(net)) : V2Draw.net(net);
		V2Draw.text(g, font, n, box.right() - 8 * scale - font.width(n) * scale, ty, scale, V2Draw.alpha(V2Draw.netColor(net), a), true);
	}
}
