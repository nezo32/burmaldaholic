package dev.nezo.burmaldaholic.gametest.pvp;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DynamicOps;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.pvp.Pvp;
import dev.nezo.burmaldaholic.core.pvp.PvpPresenter;
import dev.nezo.burmaldaholic.pvp.PvpAdminPage;
import dev.nezo.burmaldaholic.pvp.PvpAdvancements;
import dev.nezo.burmaldaholic.pvp.PvpHubPage;
import dev.nezo.burmaldaholic.pvp.PvpModule;
import dev.nezo.burmaldaholic.pvp.PvpScreensPresenter;
import dev.nezo.burmaldaholic.pvp.logic.PvpAchievementRules;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * pvp module in a real server: the hub took over the Challenges page (Dice Duel kept behind a button), every
 * hub view renders, the admin page is operator-only, {@code /casino pvp …} works for non-operators while the
 * other {@code /casino} commands stay operator-only, the five PvP advancements load and are granted.
 * (The engine itself is J-P1's; these tests do not depend on its behaviour.)
 */
public class PvpUiGameTests {
	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper) {
		return helper.makeMockServerPlayerInLevel();
	}

	private static void remove(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getServer().getPlayerList().remove(p);
	}

	@SuppressWarnings("unchecked")
	private static List<Object> render(CasinoMenu.Page page, ServerPlayer p) throws ReflectiveOperationException {
		Constructor<CasinoMenu.PageBuilder> ctor = CasinoMenu.PageBuilder.class.getDeclaredConstructor(DynamicOps.class);
		ctor.setAccessible(true);
		DynamicOps<Tag> ops = p.level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CasinoMenu.PageBuilder out = ctor.newInstance(ops);
		page.render(p, out);
		Field lines = CasinoMenu.PageBuilder.class.getDeclaredField("lines");
		lines.setAccessible(true);
		Field buttons = CasinoMenu.PageBuilder.class.getDeclaredField("buttons");
		buttons.setAccessible(true);
		List<Object> l = (List<Object>) lines.get(out);
		List<Object> b = (List<Object>) buttons.get(out);
		return List.of(l.size(), b.size(), b.toString());
	}

	@GameTest
	public void hubTakesOverChallengesAndKeepsDiceDuel(GameTestHelper helper) {
		CasinoMenu.Page page = CasinoMenu.pages().stream().filter(p -> p.id().equals("challenges")).findFirst().orElse(null);
		helper.assertTrue(page instanceof PvpHubPage, "Challenges page is the PvP hub");
		helper.assertTrue(PvpModule.hub() == page, "hub installed once");
		helper.assertTrue(Pvp.presenter() instanceof PvpScreensPresenter, "Java presenter installed");
		helper.assertTrue(Pvp.presenter() != PvpPresenter.NONE, "presenter set");
		ServerPlayer p = player(helper);
		try {
			PvpHubPage hub = (PvpHubPage) page;
			List<Object> main = render(hub, p);
			helper.assertTrue((int) main.get(0) >= 3, "main view has lines");
			helper.assertTrue(main.get(2).toString().contains("p:view:dice"), "Dice Duel button: " + main.get(2));
			helper.assertTrue(main.get(2).toString().contains("p:view:new"), "New match button");
			helper.assertTrue(hub.action(p, "p:view:dice", 0) == null, "dice view");
			List<Object> dice = render(hub, p);
			helper.assertTrue(dice.get(2).toString().contains("house"), "dice page's own buttons: " + dice.get(2));
			hub.action(p, "house", 0); // handed to the extras page (opens the dice screen; no-op for a mock player)
			for (String view : List.of("p:view:new", "p:view:rivals", "p:view:main")) {
				helper.assertTrue(hub.action(p, view, 0) == null, view);
				render(hub, p);
			}
			// the "Accept PvP challenges" toggle is stored in the PvP record (pvp-bots.md §5.2)
			MinecraftServer server = helper.getLevel().getServer();
			String before = dev.nezo.burmaldaholic.core.pvp.PvpRecordData.get(server).json(p.getUUID());
			hub.action(p, "p:invites", 0);
			String after = dev.nezo.burmaldaholic.core.pvp.PvpRecordData.get(server).json(p.getUUID());
			helper.assertTrue(after.contains("\"acceptInvites\":false"), "toggle off: " + before + " -> " + after);
			hub.action(p, "p:invites", 0);
			helper.assertTrue(dev.nezo.burmaldaholic.core.pvp.PvpRecordData.get(server).json(p.getUUID()).contains("\"acceptInvites\":true"), "toggle on");
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	@GameTest
	public void playersMayUseCasinoPvpButNotAdminCommands(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		MinecraftServer server = helper.getLevel().getServer();
		try {
			var dispatcher = server.getCommands().getDispatcher();
			int r = dispatcher.execute("casino pvp record", p.createCommandSourceStack());
			helper.assertTrue(r == 1, "record for a player");
			if (!Commands.LEVEL_GAMEMASTERS.check(p.permissions())) {
				boolean blocked = false;
				try {
					dispatcher.execute("casino balance get " + p.getName().getString(), p.createCommandSourceStack());
				} catch (CommandSyntaxException e) {
					blocked = true;
				}
				helper.assertTrue(blocked, "/casino balance stays operator-only");
				boolean listBlocked = false;
				try {
					dispatcher.execute("casino pvp list", p.createCommandSourceStack());
				} catch (CommandSyntaxException e) {
					listBlocked = true;
				}
				helper.assertTrue(listBlocked, "/casino pvp list is operator-only");
				helper.assertFalse(new PvpAdminPage().visible(p), "admin page hidden for players");
			}
			int list = dispatcher.execute("casino pvp list", server.createCommandSourceStack());
			helper.assertTrue(list >= 0, "list for the console");
			boolean noInvite = false;
			try {
				noInvite = dispatcher.execute("casino pvp accept", p.createCommandSourceStack()) == 0;
			} catch (CommandSyntaxException e) {
				noInvite = false;
			}
			helper.assertTrue(noInvite, "accept without an invite fails politely");
		} catch (CommandSyntaxException e) {
			throw new RuntimeException(e);
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}

	@GameTest
	public void pvpAdvancementsLoadAndGrant(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		for (String id : PvpAchievementRules.IDS) {
			helper.assertTrue(server.getAdvancements().get(PvpAdvancements.key(id)) != null, "advancement " + id);
		}
		ServerPlayer p = player(helper);
		try {
			helper.assertTrue(PvpAdvancements.grant(p, PvpAchievementRules.FIRST_WIN), "granted");
			helper.assertTrue(PvpAdvancements.has(p, PvpAchievementRules.FIRST_WIN), "has it");
			helper.assertFalse(PvpAdvancements.grant(p, PvpAchievementRules.FIRST_WIN), "only once");
		} finally {
			remove(helper, p);
		}
		helper.succeed();
	}
}
