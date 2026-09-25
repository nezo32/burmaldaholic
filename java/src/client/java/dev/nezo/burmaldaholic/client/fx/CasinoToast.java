package dev.nezo.burmaldaholic.client.fx;

import dev.nezo.burmaldaholic.core.chips.Chips;
import dev.nezo.burmaldaholic.core.fx.ServerFx.ToastIcon;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Casino toast (global.md §4.10): 160 × 32, {@code toast/casino} background ({@code bg.deep}, gold frame, 24 × 24
 * icon well), title in gold, body in bone (one line; wrapped to 2 when the Russian text is longer than 124 px).
 * Vanilla toast slide; our {@code toast} sound once (× {@code anim.volume}), not the vanilla one. Used for contract
 * complete, cashback, VIP tier-up, big win / jackpot on the server, Golden Hour "ending in". Casino toasts queue in
 * the vanilla manager's 5 slots.
 */
public final class CasinoToast implements Toast {
	private static final int W = 160;
	private static final int H = 32;
	private static final long SHOW_MS = 5000;

	private final Component title;
	private final Component body;
	private final ItemStack icon;
	private Visibility visibility = Visibility.SHOW;
	private boolean sounded;
	private List<FormattedCharSequence> lines;

	public CasinoToast(Component title, Component body, ItemStack icon) {
		this.title = title;
		this.body = body == null ? Component.empty() : body;
		this.icon = icon == null ? ItemStack.EMPTY : icon;
	}

	public CasinoToast(Component title, Component body, ToastIcon icon) {
		this(title, body, iconStack(icon));
	}

	/** Adds a toast to the client's toast manager. */
	public static void show(Component title, Component body, ToastIcon icon) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.gui == null) return;
		mc.gui.toastManager().addToast(new CasinoToast(title, body, icon));
	}

	/** Icon item of a toast kind (sprites replace these when the generated art lands). */
	public static ItemStack iconStack(ToastIcon icon) {
		if (icon == null) icon = ToastIcon.CHIP;
		return switch (icon) {
			case CHIP -> new ItemStack(Chips.item(100));
			case JACKPOT -> new ItemStack(Items.GOLD_BLOCK);
			case BIG_WIN -> new ItemStack(Items.GOLD_INGOT);
			case CONTRACT -> new ItemStack(Items.WRITABLE_BOOK);
			case CASHBACK -> new ItemStack(Items.EMERALD);
			case VIP -> new ItemStack(Items.NETHER_STAR);
			case GOLDEN_HOUR -> new ItemStack(Items.CLOCK);
		};
	}

	@Override
	public Visibility getWantedVisibility() {
		return visibility;
	}

	@Override
	public void update(ToastManager manager, long visibleMs) {
		if (!sounded) {
			sounded = true;
			FxSounds.play("toast", 1f);
		}
		visibility = visibleMs >= SHOW_MS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long visibleMs) {
		if (!FxSprites.blit(g, FxSprites.TOAST, 0, 0, W, H, 0xFFFFFFFF)) {
			g.fill(0, 0, W, H, CasinoPalette.BG_DEEP);
			g.outline(0, 0, W, H, CasinoPalette.GOLD);
			g.fill(4, 4, 28, 28, CasinoPalette.withAlpha(CasinoPalette.BG_DARKEST, 0.8f));
		}
		if (!icon.isEmpty()) g.fakeItem(icon, 8, 8);
		if (lines == null) lines = font.split(body, 124);
		g.text(font, title, 32, lines.size() > 1 ? 3 : 7, CasinoPalette.GOLD, false);
		if (lines.size() == 1) {
			g.text(font, lines.get(0), 32, 18, CasinoPalette.BONE, false);
		} else {
			for (int i = 0; i < Math.min(2, lines.size()); i++) g.text(font, lines.get(i), 32, 12 + i * 9, CasinoPalette.BONE, false);
		}
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
