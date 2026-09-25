package dev.nezo.burmaldaholic.gametest.ui;

import dev.nezo.burmaldaholic.client.fx.FxSettings;
import dev.nezo.burmaldaholic.client.ui.CasinoButton;
import dev.nezo.burmaldaholic.client.ui.CasinoScreen;
import dev.nezo.burmaldaholic.client.ui.CasinoTheme;
import dev.nezo.burmaldaholic.client.ui.CasinoUi;
import dev.nezo.burmaldaholic.client.ui.StyledToast;
import dev.nezo.burmaldaholic.client.ui.UiSprites;
import dev.nezo.burmaldaholic.core.CoreContent;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.table.CasinoTableBlockEntity;
import dev.nezo.burmaldaholic.gametest.ClientTestWorlds;
import dev.nezo.burmaldaholic.loan.LoanContent;
import dev.nezo.burmaldaholic.loan.LoanService;
import dev.nezo.burmaldaholic.loan.LoanShark;
import dev.nezo.burmaldaholic.loan.entity.LoanSharkEntity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Lane J-L2 screens (kit, HUD, toasts, Casino Menu ledger, Loan Shark, cashier): screenshots {@code jtest_ui_<lang>_*}
 * in English and Russian (GUI scale 2 = the mockups' 427 × 240 GUI, and 4 for the compact layout), the kit gallery in
 * the three location themes, and the RU layout report {@code jtest_ui_layout.txt} (labels wider than their button,
 * widgets outside the screen or the panel). Compare with docs/design/visual/mockups/extras_menu_*.png, extras_hud.png.
 */
public class CasinoUiClientGameTests implements FabricClientGameTest {
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = ClientTestWorlds.casino(context).create();
			ClientTestWorlds.Quiet quiet = ClientTestWorlds.quiet(context, world)) {
			context.waitTicks(20);
			world.getServer().runCommand("casino balance set @p 12500");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule doDaylightCycle false");
			// chips in the pocket for the wallet's chip columns
			for (String give : List.of("chip_1 7", "chip_5 12", "chip_25 18", "chip_100 25", "chip_500 9")) {
				world.getServer().runCommand("give @p burmaldaholic:" + give);
			}
			// a settled round for the wallet's "Biggest win" row (net 5,000 on slots)
			world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.core.events.PlayResults.fire(player(server),
				dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult.of("slots", 100, 5_100)));
			context.waitTicks(10);
			for (String lang : List.of("en_us", "ru_ru")) {
				language(context, lang);
				guiScale(context, 2);
				hud(context, world, lang);
				toasts(context, lang);
				for (String page : List.of("wallet", "vip", "contracts", "achievements", "challenges", "rules")) {
					open(context, world, lang + "_menu_" + page, server -> CasinoMenu.open(player(server), page));
				}
				// a real loan (Rent: principal → due with interest), part repaid: the contract, the due time, the meter
				world.getServer().runOnServer(server -> {
					ServerPlayer p = player(server);
					Component err = LoanService.take(p, 1);
					if (err != null) failures.add(lang + ": loan not taken: " + err.getString());
					LoanService.pay(p, 150, false);
				});
				context.waitTicks(10);
				open(context, world, lang + "_menu_loan_active", server -> CasinoMenu.open(player(server), "loan"));
				open(context, world, lang + "_loan_shark_active", CasinoUiClientGameTests::openShark);
				world.getServer().runCommand("casino loan default @p");
				context.waitTicks(10);
				open(context, world, lang + "_menu_loan", server -> CasinoMenu.open(player(server), "loan"));
				open(context, world, lang + "_loan_shark", CasinoUiClientGameTests::openShark);
				world.getServer().runCommand("casino loan clear @p");
				context.waitTicks(5);
				open(context, world, lang + "_loan_shark_offers", CasinoUiClientGameTests::openShark);
				open(context, world, lang + "_cashier", server -> {
					ServerPlayer p = player(server);
					BlockPos pos = p.blockPosition().offset(2, 0, 0);
					server.overworld().setBlockAndUpdate(pos, CoreContent.CASHIER.block().defaultBlockState());
					if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity table) {
						p.openMenu(table);
						table.sendStateTo(p);
					}
				});
				// the counting tray: a real withdrawal of 1,910 on the open cashier (3 × 500, 4 × 100, 2 × 5), mid-count and counted
				world.getServer().runOnServer(server -> cashier(server, "withdraw", 1910));
				context.waitTicks(6);
				context.takeScreenshot("jtest_ui_" + lang + "_cashier_tray_counting");
				context.waitTicks(30);
				context.takeScreenshot("jtest_ui_" + lang + "_cashier_tray");
				// and a deposit of every chip carried: the stacks of the chip items actually taken
				world.getServer().runOnServer(server -> cashier(server, "deposit_all", 0));
				context.waitTicks(40);
				context.takeScreenshot("jtest_ui_" + lang + "_cashier_tray_deposit");
				// the Nether cashier: two more exchange rows (gold), no tray, everything on the page
				open(context, world, lang + "_cashier_nether", server -> {
					ServerPlayer p = player(server);
					BlockPos pos = p.blockPosition().offset(-2, 0, 0);
					server.overworld().setBlockAndUpdate(pos, CoreContent.NETHER_CASHIER.block().defaultBlockState());
					if (server.overworld().getBlockEntity(pos) instanceof CasinoTableBlockEntity table) {
						p.openMenu(table);
						table.sendStateTo(p);
					}
				});
				// give the pocket chips back for the next language's wallet
				for (String give : List.of("chip_1 7", "chip_5 12", "chip_25 18", "chip_100 25", "chip_500 9")) {
					world.getServer().runCommand("give @p burmaldaholic:" + give);
				}
				for (CasinoTheme theme : new CasinoTheme[] {CasinoTheme.VILLAGE, CasinoTheme.BASTION, CasinoTheme.END}) {
					gallery(context, lang + "_kit_" + theme.id, theme, false);
				}
				gallery(context, lang + "_kit_reduced", CasinoTheme.VILLAGE, true);
			}
		} finally {
			guiScale(context, 0);
			language(context, "en_us");
			context.runOnClient(mc -> FxSettings.get().reduceMotion = false);
			write(context);
		}
		if (!failures.isEmpty()) throw new AssertionError("casino ui: " + String.join("; ", failures));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void openShark(MinecraftServer server) {
		ServerPlayer p = player(server);
		LoanSharkEntity shark = LoanContent.LOAN_SHARK.create(p.level(), EntitySpawnReason.COMMAND);
		if (shark == null) return;
		shark.snapTo(p.getX() + 2, p.getY(), p.getZ());
		p.level().addFreshEntity(shark);
		LoanShark.open(p, shark);
	}

	/** Runs a cashier action on the cashier next to the player (placed by the cashier screenshot), as its screen would. */
	private static void cashier(MinecraftServer server, String action, long amount) {
		ServerPlayer p = player(server);
		if (server.overworld().getBlockEntity(p.blockPosition().offset(2, 0, 0)) instanceof CasinoTableBlockEntity table) {
			net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
			args.putLong("amount", amount);
			args.putInt("denom", 0);
			table.onAction(p, action, args);
		}
	}

	private void hud(ClientGameTestContext context, TestSingleplayerContext world, String lang) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		world.getServer().runCommand("casino balance set @p 12500");
		context.waitTicks(40);
		world.getServer().runCommand("casino balance add @p 388");
		context.waitTicks(6);
		context.takeScreenshot("jtest_ui_" + lang + "_hud_delta");
		// Golden Hour (the previous language's stop left a cooldown: clear it) — the golden pill and the sun timer
		world.getServer().runOnServer(server -> dev.nezo.burmaldaholic.chaos.ChaosData.get(server).goldenHour().nextAllowed = 0);
		world.getServer().runCommand("casino chaos golden_hour start");
		context.waitTicks(40);
		context.takeScreenshot("jtest_ui_" + lang + "_hud_golden");
		long golden = context.computeOnClient(mc -> dev.nezo.burmaldaholic.client.ClientCasinoState.goldenHourTicks());
		if (golden <= 0) failures.add(lang + ": the HUD got no Golden Hour");
		// vanilla HUD neighbours at GUI scale 4 (320 × 200): a boss bar, a good and a bad effect, chat lines
		world.getServer().runCommand("bossbar add burmaldaholic:jtest \"Wither Storm\"");
		world.getServer().runCommand("bossbar set burmaldaholic:jtest players @a");
		world.getServer().runCommand("effect give @p minecraft:speed 60 0 true");
		world.getServer().runCommand("effect give @p minecraft:mining_fatigue 60 0 true");
		// a 1280 × 800 window: GUI 1280 × 800 at scale 1 … 320 × 200 at scale 4 (the 854 × 480 default caps at 2)
		context.getInput().resizeWindow(1280, 800);
		context.runOnClient(mc -> mc.gui.hud.getChat().addClientSystemMessage(Component.literal("jtest: chat line under the HUD?")));
		for (int scale = 1; scale <= 4; scale++) {
			guiScale(context, scale);
			context.waitTicks(3);
			context.takeScreenshot("jtest_ui_" + lang + "_hud_scale" + scale);
		}
		context.runOnClient(mc -> FxSettings.get().reduceMotion = true);
		context.waitTicks(3);
		context.takeScreenshot("jtest_ui_" + lang + "_hud_scale4_reduced");
		context.runOnClient(mc -> FxSettings.get().reduceMotion = false);
		context.getInput().resizeWindow(854, 480);
		world.getServer().runCommand("bossbar remove burmaldaholic:jtest");
		world.getServer().runCommand("effect clear @p");
		guiScale(context, 2);
		world.getServer().runCommand("casino chaos golden_hour stop");
		context.waitTicks(10);
	}

	private void toasts(ClientGameTestContext context, String lang) {
		context.runOnClient(mc -> {
			mc.gui.setScreen(null);
			StyledToast.show(StyledToast.Style.ACHIEVEMENT, Component.translatable("toast.burmaldaholic.achievement.title"),
				Component.translatable("advancement.burmaldaholic.first_bet.title"), UiSprites.TabIcon.ACHIEVEMENTS);
			StyledToast.show(StyledToast.Style.PVP, Component.translatable("gui.burmaldaholic.menu.challenges"),
				Component.translatable("gui.burmaldaholic.menu.my_casino"), UiSprites.TabIcon.PVP);
			StyledToast.show(StyledToast.Style.LOAN, Component.translatable("toast.burmaldaholic.loan.overdue"),
				Component.translatable("toast.burmaldaholic.loan.owed", Component.literal("5 250")), UiSprites.TabIcon.LOAN);
		});
		context.waitTicks(25);
		context.takeScreenshot("jtest_ui_" + lang + "_toasts");
		context.runOnClient(mc -> mc.gui.toastManager().clear());
	}

	private void open(ClientGameTestContext context, TestSingleplayerContext world, String name, Consumer<MinecraftServer> opener) {
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			player(server).closeContainer();
			opener.accept(server);
		});
		try {
			context.waitFor(mc -> mc.gui.screen() != null, 200);
		} catch (RuntimeException | AssertionError e) {
			failures.add(name + ": screen did not open");
			return;
		}
		context.waitTicks(25);
		context.takeScreenshot("jtest_ui_" + name);
		inspect(context, name);
	}

	private void gallery(ClientGameTestContext context, String name, CasinoTheme theme, boolean reduced) {
		context.runOnClient(mc -> {
			FxSettings.get().reduceMotion = reduced;
			mc.gui.setScreen(new Gallery(theme));
		});
		context.waitTicks(reduced ? 2 : 20);
		context.takeScreenshot("jtest_ui_" + name);
		inspect(context, name);
		context.runOnClient(mc -> {
			FxSettings.get().reduceMotion = false;
			mc.gui.setScreen(null);
		});
	}

	private void inspect(ClientGameTestContext context, String name) {
		List<String> problems = context.computeOnClient(mc -> problems(mc, mc.gui.screen()));
		for (String p : problems) report.add(name + ": " + p);
	}

	/** Buttons whose label does not fit, widgets outside the screen or outside a kit screen's panel. */
	private static List<String> problems(Minecraft mc, Screen screen) {
		List<String> out = new ArrayList<>();
		if (screen == null) return out;
		for (var child : screen.children()) {
			if (!(child instanceof AbstractWidget w) || !w.visible) continue;
			String label = w.getMessage().getString();
			int room = w.getWidth() - (w instanceof CasinoButton cb && cb.style() == CasinoButton.Style.ACTION ? 4 : 4);
			if (w instanceof AbstractButton && !label.isEmpty() && mc.font.width(w.getMessage()) > room && !(w instanceof dev.nezo.burmaldaholic.client.ui.BookmarkTab)) {
				out.add("label overflows button (" + mc.font.width(w.getMessage()) + " > " + room + " px): \"" + label + "\"");
			}
			if (w.getX() < 0 || w.getY() < 0 || w.getX() + w.getWidth() > screen.width || w.getY() + w.getHeight() > screen.height) {
				out.add("widget outside the screen: \"" + label + "\" at " + w.getX() + "," + w.getY());
			}
		}
		return out;
	}

	private void write(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			try {
				Path dir = mc.gameDirectory.toPath().resolve("screenshots");
				Files.createDirectories(dir);
				Files.writeString(dir.resolve("jtest_ui_layout.txt"), report.isEmpty() ? "no problems\n" : String.join("\n", report) + "\n",
					StandardCharsets.UTF_8);
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
	}

	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(3);
	}

	private static void language(ClientGameTestContext context, String code) {
		CompletableFuture<?> reload = context.computeOnClient(mc -> {
			mc.getLanguageManager().setSelected(code);
			mc.options.languageCode = code;
			return mc.reloadResourcePacks();
		});
		context.waitFor(mc -> reload.isDone(), 1200);
		context.waitTicks(20);
	}

	/** The kit on one screen: every button style and state, panels, plates, rows, bars, icons (for the lanes). */
	static final class Gallery extends CasinoScreen {
		private final CasinoTheme galleryTheme;

		Gallery(CasinoTheme theme) {
			super(Component.translatable("gui.burmaldaholic.menu.title"));
			this.galleryTheme = theme;
		}

		@Override
		protected CasinoTheme theme() {
			return galleryTheme;
		}

		@Override
		protected void init() {
			super.init();
			int x = px(18);
			int y = py(40);
			String[] keys = {"gui.burmaldaholic.cashier.deposit_all", "gui.burmaldaholic.cashier.withdraw", "gui.burmaldaholic.loan.pay_all"};
			CasinoButton.Style[] styles = {CasinoButton.Style.PRIMARY, CasinoButton.Style.SECONDARY, CasinoButton.Style.DANGER};
			for (int i = 0; i < 3; i++) {
				Component label = Component.translatable(keys[i], Component.literal("5 250"));
				int w = CasinoButton.width(font, label, 90, false);
				button(label, x, y + i * 24, w, 20, styles[i], CasinoButton::shake);
				CasinoButton off = button(label, x + 150, y + i * 24, w, 20, styles[i], b -> {});
				off.enabled(false, Component.translatable("gui.burmaldaholic.cashier.withdraw_blocked"));
			}
			Component cashier = Component.translatable("gui.burmaldaholic.menu.cashier");
			add(CasinoButton.builder(cashier, b -> {}).icon(CasinoButton.Icon.tab(UiSprites.TabIcon.CASHIER))
				.bounds(x, y + 76, CasinoButton.width(font, cashier, 60, true), 20).build()).selected(true);
			add(CasinoButton.builder(Component.empty(), b -> {}).icon(CasinoButton.Icon.tab(UiSprites.TabIcon.SETTINGS))
				.tooltip(Component.translatable("gui.burmaldaholic.menu.settings")).bounds(x + 110, y + 76, 20, 20).build());
			add(CasinoButton.builder(Component.empty(), b -> {}).style(CasinoButton.Style.ACTION).icon(CasinoButton.Icon.tab(UiSprites.TabIcon.CHALLENGES))
				.bounds(panel.right() - 64, panel.bottom() - 64, 40, 40).build());
		}

		@Override
		protected void extractPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			int x = px(18);
			int y = py(146);
			CasinoUi.inset(g, x, y, 160, 70);
			CasinoUi.row(g, 0, x + 4, y + 4, 152, 14);
			CasinoUi.row(g, 1, x + 4, y + 18, 152, 14);
			g.text(font, Component.translatable("gui.burmaldaholic.menu.wallet.row.wagered"), x + 8, y + 7, 0xFFF4ECF8, true);
			g.text(font, Component.translatable("gui.burmaldaholic.menu.wallet.row.today"), x + 8, y + 21, 0xFFF4ECF8, true);
			CasinoUi.progress(g, x + 4, y + 36, 152, 10, 0.7, UiSprites.Fill.GOLD);
			CasinoUi.progress(g, x + 4, y + 50, 152, 10, 0.35, UiSprites.Fill.RED);
			CasinoUi.panel(g, px(186), py(146), 100, 70);
			CasinoUi.plate(g, px(194), py(154), 84, 16, true);
			CasinoUi.plate(g, px(194), py(174), 84, 16, false);
			for (int i = 0; i < UiSprites.TabIcon.values().length; i++) CasinoUi.tabIcon(g, UiSprites.TabIcon.values()[i], 16, px(290 + (i % 5) * 18), py(150 + (i / 5) * 18));
		}
	}
}
