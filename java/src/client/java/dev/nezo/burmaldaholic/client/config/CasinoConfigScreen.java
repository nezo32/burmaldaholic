package dev.nezo.burmaldaholic.client.config;

import com.google.gson.JsonObject;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.config.ConfigIssue;
import dev.nezo.burmaldaholic.core.config.ConfigManager;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Config screen (Mod Menu → Burmaldaholic): one button per CONFIG.md section, each opening a
 * generated editor ({@link ConfigSectionScreen}) over a working copy of {@code config/burmaldaholic.json}.
 * "Done" validates (clamping as the server would), writes the file and reloads; problems are shown
 * as toasts with the {@code config.burmaldaholic.clamped/invalid} messages. Per-world overrides are
 * edited in-game with {@code /casino config set}.
 */
public final class CasinoConfigScreen extends Screen {
	/** Pages: label key suffix → config sections shown on that page. */
	static final List<Page> PAGES = List.of(
		new Page("core", List.of("core")), new Page("economy", List.of("economy")), new Page("contracts", List.of("contracts")),
		new Page("wager", List.of("wager")), new Page("vip", List.of("vip")), new Page("blackjack", List.of("blackjack")),
		new Page("poker", List.of("poker")), new Page("slots", List.of("slots")), new Page("roulette", List.of("roulette")),
		new Page("craps", List.of("craps")), new Page("extras", List.of("extras")), new Page("loan", List.of("loan")),
		new Page("chaos", List.of("chaos")), new Page("streak", List.of("streak")), new Page("lastchance", List.of("lastChance")),
		new Page("worldgen", List.of("worldgen")), new Page("ownership", List.of("ownership", "multiplayer")), new Page("debug", List.of("debug")));

	record Page(String labelSuffix, List<String> sections) {
		Component label() {
			return Component.translatable("config.burmaldaholic.section." + labelSuffix);
		}
	}

	private final Screen parent;
	private final JsonObject working;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

	public CasinoConfigScreen(Screen parent) {
		super(Component.translatable("config.burmaldaholic.title"));
		this.parent = parent;
		this.working = ConfigManager.get().globalJson();
	}

	@Override
	protected void init() {
		layout.addToHeader(new StringWidget(title, font));
		GridLayout grid = new GridLayout().columnSpacing(8).rowSpacing(4);
		GridLayout.RowHelper rows = grid.createRowHelper(2);
		for (Page page : PAGES) {
			rows.addChild(Button.builder(page.label(), b -> minecraft.gui.setScreen(new ConfigSectionScreen(this, page, working))).width(150).build());
		}
		layout.addToContents(grid);
		LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> save()).build());
		footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).build());
		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
	}

	private void save() {
		List<ConfigIssue> issues = ConfigManager.get().saveGlobal(working);
		var toasts = minecraft.gui.toastManager();
		if (issues.isEmpty()) {
			SystemToast.addOrUpdate(toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, Component.translatable("config.burmaldaholic.saved"));
		} else {
			for (ConfigIssue issue : issues.subList(0, Math.min(3, issues.size()))) {
				SystemToast.add(toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, CasinoCommands.issueMessage(issue));
			}
		}
		onClose();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
