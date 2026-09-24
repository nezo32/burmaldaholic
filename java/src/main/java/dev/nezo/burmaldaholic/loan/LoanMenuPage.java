package dev.nezo.burmaldaholic.loan;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord;
import dev.nezo.burmaldaholic.loan.logic.LoanRecord.Status;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** Casino Menu "Loan" tab (UI.md §2): status, owed, deadline countdown, Pay (amount field) / Pay all. */
final class LoanMenuPage implements CasinoMenu.Page {
	@Override
	public String id() {
		return "loan";
	}

	@Override
	public int order() {
		return 30;
	}

	@Override
	public Component label() {
		return Component.translatable("gui.burmaldaholic.menu.loan");
	}

	@Override
	public boolean visible(ServerPlayer player) {
		return LoanService.active(player.level().getServer());
	}

	@Override
	public void render(ServerPlayer player, CasinoMenu.PageBuilder out) {
		MinecraftServer server = player.level().getServer();
		LoanRecord rec = LoanData.get(server).peek(player.getUUID());
		long now = LoanService.now(server);
		if (rec == null || rec.status == Status.NONE || rec.owed <= 0) {
			out.line(Component.translatable("gui.burmaldaholic.loan.status.none"), 0x55FF55);
			if (rec != null && rec.cooldownUntil > now) {
				out.line(Component.translatable("gui.burmaldaholic.loan.status.cooldown", LoanService.days(rec.cooldownUntil - now)), 0xAAAAAA);
			}
			return;
		}
		if (rec.status == Status.DEFAULT) {
			out.line(Component.translatable("gui.burmaldaholic.loan.status.default", Texts.chips(rec.owed)), 0xFF5555);
		} else {
			out.line(Component.translatable("gui.burmaldaholic.loan.status.active", Texts.chips(rec.owed), LoanService.dhm(rec.deadlineTick - now)), 0xFFD700);
		}
		long balance = Economies.get().balance(player);
		long all = Math.min(rec.owed, balance);
		out.amountButton("pay", Component.translatable("gui.burmaldaholic.loan.pay"), all);
		out.button("pay_all", Component.translatable("gui.burmaldaholic.loan.pay_all", Texts.chips(rec.owed)), balance >= rec.owed);
	}

	@Override
	public @Nullable Component action(ServerPlayer player, String action, long amount) {
		MinecraftServer server = player.level().getServer();
		LoanRecord rec = LoanData.get(server).peek(player.getUUID());
		if (rec == null || rec.owed <= 0) {
			return null;
		}
		long want = switch (action) {
			case "pay" -> amount;
			case "pay_all" -> rec.owed;
			default -> 0;
		};
		if (want <= 0) {
			return Component.translatable("gui.burmaldaholic.error.invalid_amount");
		}
		if (Economies.get().balance(player) <= 0 || LoanService.pay(player, want, true) <= 0) {
			return Component.translatable("gui.burmaldaholic.error.insufficient_funds", Texts.number(Economies.get().balance(player)));
		}
		return null;
	}
}
