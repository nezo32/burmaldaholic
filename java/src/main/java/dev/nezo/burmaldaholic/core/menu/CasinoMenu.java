package dev.nezo.burmaldaholic.core.menu;

import com.mojang.serialization.DynamicOps;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.network.MenuPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Casino Menu server pages (UI.md §2; Casino Card / key {@code B}). The vip client module owns the
 * screen and draws its own tabs (Wallet, VIP, Contracts); every other tab is a server-rendered
 * {@link Page} registered here by the owning module, so no module needs client code for it:
 *
 * <pre>
 * CasinoMenu.register(new CasinoMenu.Page() {
 *     public String id() { return "loan"; }
 *     public int order() { return 30; }
 *     public Component label() { return Component.translatable("gui.burmaldaholic.menu.loan"); }
 *     public void render(ServerPlayer p, CasinoMenu.PageBuilder out) { out.line(...); out.amountButton("pay", label, owed); }
 *     public Component action(ServerPlayer p, String action, long amount) { ...; return null; } // null = ok, else error
 * });
 * </pre>
 *
 * UI.md order: wallet 10, contracts 20, loan 30, achievements 40, challenges 50, my_casino 60, rules 70.
 * A button whose action starts with {@code client:} is handled by the client (e.g. {@code client:advancements}).
 */
public final class CasinoMenu {
	private static final List<Page> PAGES = new CopyOnWriteArrayList<>();

	private CasinoMenu() {}

	/** One server-rendered tab. */
	public interface Page {
		/** Unique id, owned by the module (e.g. "loan", "my_casino"). */
		String id();

		int order();

		Component label();

		/** Hide the tab for this player (e.g. My Casino without a charter). */
		default boolean visible(ServerPlayer player) {
			return true;
		}

		void render(ServerPlayer player, PageBuilder out);

		/** A button was pressed. Return null on success, else the error shown in red. The page is re-sent. */
		default @Nullable Component action(ServerPlayer player, String action, long amount) {
			return null;
		}
	}

	/** Content of a page: wrapped text lines, then buttons. */
	public static final class PageBuilder {
		final List<Component> lines = new ArrayList<>();
		final List<Integer> colors = new ArrayList<>();
		final List<CompoundTag> buttons = new ArrayList<>();
		private final DynamicOps<Tag> ops;

		PageBuilder(DynamicOps<Tag> ops) {
			this.ops = ops;
		}

		public PageBuilder line(Component text) {
			return line(text, 0xFFFFFF);
		}

		/** @param rgb text color (0xRRGGBB) */
		public PageBuilder line(Component text, int rgb) {
			lines.add(text);
			colors.add(rgb);
			return this;
		}

		public PageBuilder blank() {
			return line(Component.empty());
		}

		public PageBuilder button(String action, Component label) {
			return button(action, label, true);
		}

		public PageBuilder button(String action, Component label, boolean active) {
			return add(action, label, active, false, 0);
		}

		/** A button with an amount field (pre-filled with {@code suggested}); the amount arrives in {@link Page#action}. */
		public PageBuilder amountButton(String action, Component label, long suggested) {
			return add(action, label, true, true, suggested);
		}

		private PageBuilder add(String action, Component label, boolean active, boolean amount, long suggested) {
			CompoundTag b = new CompoundTag();
			b.putString("action", action);
			b.put("label", encode(ops, label));
			b.putBoolean("active", active);
			b.putBoolean("amount", amount);
			b.putLong("suggested", suggested);
			buttons.add(b);
			return this;
		}
	}

	/** Adds a page (module {@code register}). Replaces a page with the same id. */
	public static void register(Page page) {
		PAGES.removeIf(p -> p.id().equals(page.id()));
		PAGES.add(page);
		PAGES.sort(Comparator.comparingInt(Page::order));
	}

	public static List<Page> pages() {
		return List.copyOf(PAGES);
	}

	/** Opens the Casino Menu on the client ({@code page} empty = the default Wallet tab). */
	public static void open(ServerPlayer player, String page) {
		send(player, page, true, null);
	}

	/** Handles a client request (view or button). */
	public static void request(ServerPlayer player, String pageId, String action, long amount) {
		Component error = null;
		Page page = find(player, pageId);
		if (page != null && !action.isEmpty()) {
			try {
				error = page.action(player, action, Math.max(0, amount));
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("Casino Menu page {} action {} failed", pageId, action, e);
			}
		}
		send(player, pageId, false, error);
	}

	private static @Nullable Page find(ServerPlayer player, String id) {
		for (Page p : PAGES) {
			if (p.id().equals(id) && safeVisible(p, player)) {
				return p;
			}
		}
		return null;
	}

	private static boolean safeVisible(Page p, ServerPlayer player) {
		try {
			return p.visible(player);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Sends the tab list and the content of {@code pageId} (if it is a server page). */
	public static void send(ServerPlayer player, String pageId, boolean open, @Nullable Component error) {
		if (MenuPayload.TYPE == null || !ServerPlayNetworking.canSend(player, MenuPayload.TYPE)) {
			return;
		}
		if (!CasinoMode.isEnabled(player)) {
			player.sendOverlayMessage(Component.translatable("gui.burmaldaholic.error.casino_off"));
			return;
		}
		DynamicOps<Tag> ops = player.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag data = new CompoundTag();
		ListTag tabs = new ListTag();
		for (Page p : PAGES) {
			if (!safeVisible(p, player)) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			t.putString("id", p.id());
			t.putInt("order", p.order());
			t.put("label", encode(ops, p.label()));
			tabs.add(t);
		}
		data.put("tabs", tabs);
		data.putString("page", pageId == null ? "" : pageId);
		Page page = pageId == null ? null : find(player, pageId);
		if (page != null) {
			PageBuilder out = new PageBuilder(ops);
			try {
				page.render(player, out);
			} catch (RuntimeException e) {
				Burmaldaholic.LOGGER.error("Casino Menu page {} failed to render", pageId, e);
			}
			ListTag lines = new ListTag();
			for (int i = 0; i < out.lines.size(); i++) {
				CompoundTag l = new CompoundTag();
				l.put("text", encode(ops, out.lines.get(i)));
				l.putInt("color", out.colors.get(i));
				lines.add(l);
			}
			data.put("lines", lines);
			ListTag buttons = new ListTag();
			buttons.addAll(out.buttons);
			data.put("buttons", buttons);
		}
		if (error != null) {
			data.put("error", encode(ops, error));
		}
		ServerPlayNetworking.send(player, new MenuPayload(open, data));
	}

	static Tag encode(DynamicOps<Tag> ops, Component c) {
		return ComponentSerialization.CODEC.encodeStart(ops, c).result().orElseGet(CompoundTag::new);
	}
}
