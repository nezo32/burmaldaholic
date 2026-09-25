package dev.nezo.burmaldaholic.client.ui;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.fx.FxSounds;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The styled casino toasts of extras.md §9 (160 × 32, icon well 24² on the left, title line + body line):
 * {@link Style#ACHIEVEMENT} (gold edge, dotted gold rule), {@link Style#PVP} (lilac edge split blue/red, used by
 * challenge toasts; {@link #progress} draws the depleting gold bar along the bottom), {@link Style#LOAN} (steel, red
 * edge, title in {@code chip.light}) and the plain {@link Style#CASINO}. The icon is a 16-px menu icon, an item or none.
 */
public class StyledToast implements Toast {
	public enum Style {
		CASINO(UiSprites.TOAST_CASINO, CasinoPalette.GOLD),
		ACHIEVEMENT(UiSprites.TOAST_ACHIEVEMENT, CasinoPalette.GOLD),
		PVP(UiSprites.TOAST_PVP, CasinoPalette.GOLD),
		LOAN(UiSprites.TOAST_LOAN, CasinoPalette.CHIP_RED_LIGHT);

		final Identifier background;
		final int title;

		Style(Identifier background, int title) {
			this.background = background;
			this.title = title;
		}
	}

	private static final int W = 160;
	private static final int H = 32;

	private final Style style;
	private final Component title;
	private final Component body;
	private final UiSprites.@Nullable TabIcon icon;
	private final ItemStack item;
	private final long showMs;
	private Visibility visibility = Visibility.SHOW;
	private boolean sounded;
	private @Nullable List<FormattedCharSequence> lines;
	private long visible;

	public StyledToast(Style style, Component title, @Nullable Component body, UiSprites.@Nullable TabIcon icon, @Nullable ItemStack item, long showMs) {
		this.style = style;
		this.title = title;
		this.body = body == null ? Component.empty() : body;
		this.icon = icon;
		this.item = item == null ? ItemStack.EMPTY : item;
		this.showMs = showMs;
	}

	/** Shows a toast with a menu icon for 5 s. */
	public static void show(Style style, Component title, @Nullable Component body, UiSprites.@Nullable TabIcon icon) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.gui == null) return;
		mc.gui.toastManager().addToast(new StyledToast(style, title, body, icon, null, 5000));
	}

	/** Fraction (1 → 0) of the depleting bar along the bottom; negative = no bar. PvP challenge toasts override. */
	protected float progress(long visibleMs) {
		return style == Style.PVP ? Math.max(0f, 1f - visibleMs / (float) showMs) : -1f;
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long visibleMs) {
		visible = visibleMs;
		if (!sounded) {
			sounded = true;
			FxSounds.play("toast", 1f);
		}
		visibility = visibleMs >= showMs * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long visibleMs) {
		if (!CasinoUi.sprite(g, style.background, 0, 0, W, H)) {
			g.fill(0, 0, W, H, CasinoPalette.BG_DEEP);
			g.outline(0, 0, W, H, style == Style.LOAN ? CasinoPalette.CHIP_RED : CasinoPalette.GOLD);
		}
		if (icon != null) CasinoUi.tabIcon(g, icon, 16, 8, 8);
		else if (!item.isEmpty()) g.fakeItem(item, 8, 8);
		if (lines == null) lines = font.split(body, 124);
		g.text(font, CasinoUi.fit(font, title, 124), 32, lines.size() > 1 ? 3 : 6, style.title, false);
		if (lines.size() == 1) {
			g.text(font, lines.get(0), 32, 17, CasinoPalette.BONE, false);
		} else {
			for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, lines.get(i), 32, 12 + i * 9, CasinoPalette.BONE, false);
		}
		float p = progress(visible);
		if (p >= 0) g.fill(4, H - 3, 4 + Math.round((W - 8) * p), H - 2, CasinoPalette.GOLD);
	}

	@Override
	public int width() {
		return W;
	}

	@Override
	public int height() {
		return H;
	}
}
