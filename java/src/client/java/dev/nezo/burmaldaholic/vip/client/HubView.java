package dev.nezo.burmaldaholic.vip.client;

import dev.nezo.burmaldaholic.client.fx.CasinoPalette;
import dev.nezo.burmaldaholic.client.menu.ClientCasinoMenu;
import dev.nezo.burmaldaholic.client.pvp.kit.Kit;
import dev.nezo.burmaldaholic.core.ui.HubLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

/**
 * The PvP hub page of the Casino Menu (visual/extras.md §7.2 "Hub", PVP.md §3.11.2) read from the server page the pvp
 * module renders ({@code PvpHubPage}, tab {@code challenges}): the main view's lines and buttons are recognised by their
 * translation keys and actions ({@code p:join:<id>}, {@code p:new:<mode>}, …) and laid out as the hub — heading and
 * record chip, the nemesis with the skull, a 3 × 2 grid of mode cards (create), the status of your match and invites,
 * the open lobbies as plaques with Join, and the remaining buttons. Anything the parser does not know stays on the page
 * as plain rows and buttons, so no server content is ever lost. The other views (New match, Head-to-head, Dice) keep the
 * generic page.
 */
final class HubView {
	static final String TITLE = "gui.burmaldaholic.pvp.hub.title";
	static final String RECORD = "gui.burmaldaholic.pvp.hub.record";
	static final String NO_RECORD = "gui.burmaldaholic.pvp.hub.no_record";
	static final String NEMESIS = "gui.burmaldaholic.pvp.hub.nemesis";
	static final String NEARBY = "gui.burmaldaholic.pvp.hub.nearby";
	static final String NO_LOBBIES = "gui.burmaldaholic.pvp.hub.no_lobbies";
	static final String LOBBY_ROW = "gui.burmaldaholic.pvp.hub.lobby_row";
	static final String MACHINE_HINT = "gui.burmaldaholic.pvp.hub.machine_hint";
	static final String GAME = "gui.burmaldaholic.pvp.game.";
	/** Mode cards in the order of {@code mode_icons_40} (coin, wheel, plinko, scratch, slots). */
	static final String[] MODES = {"coin", "wheel", "plinko", "scratch", "slots"};

	/** A lobby plaque: its line, the game (icon), its Join button. */
	record Lobby(Component text, String mode, ClientCasinoMenu.@Nullable Button join) {}

	/** The parsed main view. */
	record Hub(@Nullable Component record, int wins, int losses, @Nullable Component nemesis, List<ClientCasinoMenu.Line> status,
			List<ClientCasinoMenu.Button> statusButtons, @Nullable Component lobbiesHeader, List<Lobby> lobbies,
			Map<String, ClientCasinoMenu.Button> create, ClientCasinoMenu.@Nullable Button rivals, List<ClientCasinoMenu.Button> bottom,
			@Nullable Component machineHint) {}

	private HubView() {}

	static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static Object[] args(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
	}

	private static int number(Object arg) {
		String s = arg instanceof Component c ? c.getString() : String.valueOf(arg);
		String digits = s.replaceAll("[^0-9]", "");
		try {
			return digits.isEmpty() ? -1 : Integer.parseInt(digits);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** True for the hub's main view (its first line is the hub heading). */
	static boolean isMain(List<ClientCasinoMenu.Line> lines) {
		return !lines.isEmpty() && TITLE.equals(key(lines.getFirst().text()));
	}

	static Hub parse(List<ClientCasinoMenu.Line> lines, List<ClientCasinoMenu.Button> buttons) {
		Component record = null;
		int wins = -1;
		int losses = -1;
		Component nemesis = null;
		Component header = null;
		Component hint = null;
		List<ClientCasinoMenu.Line> status = new ArrayList<>();
		List<Component> rows = new ArrayList<>();
		for (ClientCasinoMenu.Line l : lines) {
			String k = key(l.text());
			switch (k) {
				case TITLE -> {
				}
				case RECORD -> {
					record = l.text();
					Object[] a = args(l.text());
					if (a.length >= 2) {
						wins = number(a[0]);
						losses = number(a[1]);
					}
				}
				case NO_RECORD -> record = l.text();
				case NEMESIS -> nemesis = l.text();
				case NEARBY, NO_LOBBIES -> header = l.text();
				case LOBBY_ROW -> rows.add(l.text());
				case MACHINE_HINT -> hint = l.text();
				default -> {
					if (!l.text().getString().isEmpty()) status.add(l);
				}
			}
		}
		List<ClientCasinoMenu.Button> joins = new ArrayList<>();
		List<ClientCasinoMenu.Button> statusButtons = new ArrayList<>();
		List<ClientCasinoMenu.Button> bottom = new ArrayList<>();
		Map<String, ClientCasinoMenu.Button> create = new LinkedHashMap<>();
		ClientCasinoMenu.Button rivals = null;
		for (ClientCasinoMenu.Button b : buttons) {
			String a = b.action();
			if (a.startsWith("p:join:")) {
				joins.add(b);
			} else if (a.startsWith("p:new:")) {
				create.put(a.substring("p:new:".length()), b);
			} else if (a.equals("p:view:rivals")) {
				rivals = b;
			} else if (a.equals("p:show") || a.equals("p:leave") || a.startsWith("p:guest:") || a.startsWith("p:withdraw:") || a.startsWith("p:accept:")
				|| a.startsWith("p:decline:")) {
				statusButtons.add(b);
			} else {
				bottom.add(b);
			}
		}
		List<Lobby> lobbies = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			Object[] a = args(rows.get(i));
			String mode = a.length > 0 && a[0] instanceof Component c && key(c).startsWith(GAME) ? key(c).substring(GAME.length()) : "";
			lobbies.add(new Lobby(rows.get(i), mode, i < joins.size() ? joins.get(i) : null));
		}
		for (int i = rows.size(); i < joins.size(); i++) bottom.add(joins.get(i)); // never drop a server button
		return new Hub(record, wins, losses, nemesis, status, statusButtons, header, lobbies, create, rivals, bottom, hint);
	}

	/** Icon index of a mode in {@code mode_icons*} (−1 unknown). */
	static int icon(String mode) {
		for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(mode)) return i;
		return -1;
	}

	/** A mode card (extras.md §7.2): {@code mode_card} (selected sprite on hover / focus), the 40 px icon and the name. */
	static final class ModeCard extends AbstractButton {
		private final int icon;
		private final @Nullable Consumer<ModeCard> onPress;

		/**
		 * @param icon    index in {@code mode_icons_40}, or −1 for the head-to-head card (rival badge)
		 * @param onPress null: the game starts at its machine (disabled, the hint as tooltip)
		 */
		ModeCard(int x, int y, int w, int h, Component name, int icon, @Nullable Consumer<ModeCard> onPress, @Nullable Component hint) {
			super(x, y, w, h, name);
			this.icon = icon;
			this.onPress = onPress;
			this.active = onPress != null;
			Component tip = onPress == null && hint != null ? hint : HubLayout.named(w) ? null : name;
			if (tip != null) setTooltip(Tooltip.create(tip));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (onPress != null) onPress.accept(this);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			int x = getX();
			int y = getY();
			int w = getWidth();
			int h = getHeight();
			boolean hot = active && isHoveredOrFocused();
			if (!Kit.sprite(g, Kit.pvp(hot ? "mode_card_selected" : "mode_card"), x, y, w, h)) {
				g.fill(x, y, x + w, y + h, 0xF026103C);
				Kit.frameRect(g, x, y, w, h, hot ? CasinoPalette.GOLD : CasinoPalette.FRAME);
			}
			int iy = y + (h - HubLayout.ICON) / 2 + (hot ? -1 : 0);
			int ix = HubLayout.named(w) ? x + 4 : x + (w - HubLayout.ICON) / 2;
			int tint = active ? 0xFFFFFFFF : 0xFF8A7AA0;
			if (icon >= 0) {
				Kit.region(g, Kit.sheet("pvp/mode_icons_40"), 200, 40, icon * 40, 0, 40, 40, ix, iy, 40, 40, tint);
			} else {
				Kit.region(g, Kit.sheet("pvp/badges_20"), 120, 20, 0, 0, 20, 20, ix, iy, 40, 40, tint); // the rival badge at 2×
			}
			if (isFocused()) g.outline(x - 1, y - 1, w + 2, h + 2, CasinoPalette.GLINT);
			if (!HubLayout.named(w)) return;
			Font font = Minecraft.getInstance().font;
			int tw = HubLayout.cardTextW(w);
			// word wrap that never breaks inside a word ("Head-to-he" / "ad" was the vanilla splitter's result)
			List<String> lines = HubLayout.wrapName(getMessage().getString(), tw, font::width);
			int n = Math.min(3, lines.size());
			int ty = y + (h - n * 10) / 2 + 1;
			for (int i = 0; i < n; i++) {
				String s = lines.get(i);
				if (font.width(s) > tw) s = font.plainSubstrByWidth(s, tw);
				g.text(font, s, x + 4 + HubLayout.ICON + 4, ty + i * 10, active ? CasinoPalette.GOLD : CasinoPalette.BONE_SHADE, true);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
