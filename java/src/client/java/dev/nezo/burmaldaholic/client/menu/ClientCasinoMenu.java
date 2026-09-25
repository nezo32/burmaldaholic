package dev.nezo.burmaldaholic.client.menu;

import com.mojang.serialization.DynamicOps;
import dev.nezo.burmaldaholic.core.network.MenuPayload;
import dev.nezo.burmaldaholic.core.network.MenuRequestPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jspecify.annotations.Nullable;

/**
 * Client state of the Casino Menu's server pages ({@code core.menu.CasinoMenu}). The screen itself
 * belongs to the vip client module (it also draws the Wallet / VIP / Contracts tabs); it installs
 * {@link #setOpener} and {@link #setListener}. Everything here is display data; the server decides.
 */
public final class ClientCasinoMenu {
	/** A server tab. */
	public record Tab(String id, int order, Component label) {}

	/** A text line (color 0xRRGGBB). */
	public record Line(Component text, int color) {}

	/** A button; {@code amount} = has an amount field pre-filled with {@code suggested}. */
	public record Button(String action, Component label, boolean active, boolean amount, long suggested) {}

	private static List<Tab> tabs = List.of();
	private static String page = "";
	private static List<Line> lines = List.of();
	private static List<Button> buttons = List.of();
	private static @Nullable Component error;
	private static @Nullable Consumer<String> opener;
	private static @Nullable Runnable listener;

	private ClientCasinoMenu() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MenuPayload.TYPE, (payload, context) -> accept(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			tabs = List.of();
			lines = List.of();
			buttons = List.of();
			page = "";
			error = null;
		});
	}

	/** The menu screen's factory: opens the Casino Menu on {@code page} ("" = default tab). */
	public static void setOpener(Consumer<String> open) {
		opener = open;
	}

	/** Called after new data arrived (the open screen refreshes). */
	public static void setListener(@Nullable Runnable onChange) {
		listener = onChange;
	}

	public static List<Tab> tabs() {
		return tabs;
	}

	public static String page() {
		return page;
	}

	public static List<Line> lines() {
		return lines;
	}

	public static List<Button> buttons() {
		return buttons;
	}

	/** Error of the last action (consumed). */
	public static @Nullable Component takeError() {
		Component e = error;
		error = null;
		return e;
	}

	/** Asks the server for the tab list and page {@code id} ("" = just the tabs). */
	public static void request(String id) {
		send(id, "", 0);
	}

	/** Presses a button of the current page. */
	public static void press(String id, String action, long amount) {
		send(id, action, amount);
	}

	private static void send(String id, String action, long amount) {
		if (MenuRequestPayload.TYPE != null && ClientPlayNetworking.canSend(MenuRequestPayload.TYPE)) {
			ClientPlayNetworking.send(new MenuRequestPayload(id, action, amount));
		}
	}

	/** Opens the menu (Casino Card, key B). */
	public static void open(String id) {
		if (opener != null) {
			opener.accept(id);
		}
	}

	private static void accept(MenuPayload payload) {
		DynamicOps<Tag> ops = ops();
		CompoundTag d = payload.data();
		List<Tab> t = new ArrayList<>();
		d.getListOrEmpty("tabs").forEach(tag -> tag.asCompound().ifPresent(c ->
			t.add(new Tab(c.getStringOr("id", ""), c.getIntOr("order", 0), component(ops, c.get("label"))))));
		tabs = List.copyOf(t);
		String p = d.getStringOr("page", "");
		if (!p.isEmpty() || payload.open()) {
			page = p;
			List<Line> l = new ArrayList<>();
			d.getListOrEmpty("lines").forEach(tag -> tag.asCompound().ifPresent(c ->
				l.add(new Line(component(ops, c.get("text")), c.getIntOr("color", 0xFFFFFF)))));
			lines = List.copyOf(l);
			List<Button> b = new ArrayList<>();
			d.getListOrEmpty("buttons").forEach(tag -> tag.asCompound().ifPresent(c ->
				b.add(new Button(c.getStringOr("action", ""), component(ops, c.get("label")), c.getBooleanOr("active", true),
					c.getBooleanOr("amount", false), c.getLongOr("suggested", 0)))));
			buttons = List.copyOf(b);
		}
		error = d.contains("error") ? component(ops, d.get("error")) : null;
		if (payload.open()) {
			open(p);
		}
		if (listener != null) {
			listener.run();
		}
	}

	/** Test hook (client GameTests): installs page {@code id} as if the server had sent it and refreshes the open menu. */
	public static void setPageForTests(String id, List<Line> newLines, List<Button> newButtons) {
		page = id;
		lines = List.copyOf(newLines);
		buttons = List.copyOf(newButtons);
		if (listener != null) {
			listener.run();
		}
	}

	private static DynamicOps<Tag> ops() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
	}

	private static Component component(DynamicOps<Tag> ops, @Nullable Tag tag) {
		if (tag == null) {
			return Component.empty();
		}
		return ComponentSerialization.CODEC.parse(ops, tag).result().orElse(Component.empty());
	}
}
