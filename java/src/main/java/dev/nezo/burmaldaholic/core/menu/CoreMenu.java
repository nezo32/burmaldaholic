package dev.nezo.burmaldaholic.core.menu;

import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.network.MenuPayload;
import dev.nezo.burmaldaholic.core.network.MenuRequestPayload;
import dev.nezo.burmaldaholic.core.text.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Core's part of the Casino Menu: the payloads and the Achievements page (Java: summary + advancement screen). */
public final class CoreMenu {
	private CoreMenu() {}

	public static void register(ModuleContext ctx) {
		MenuPayload.TYPE = ctx.payloads().clientbound("menu", MenuPayload.CODEC);
		MenuRequestPayload.TYPE = ctx.payloads().serverbound("menu_request", MenuRequestPayload.CODEC,
			(payload, context) -> CasinoMenu.request(context.player(), payload.page(), payload.action(), payload.amount()));
		CasinoMenu.register(new CasinoMenu.Page() {
			@Override
			public String id() {
				return "achievements";
			}

			@Override
			public int order() {
				return 40;
			}

			@Override
			public Component label() {
				return Component.translatable("gui.burmaldaholic.menu.achievements");
			}

			@Override
			public void render(ServerPlayer player, CasinoMenu.PageBuilder out) {
				int have = 0;
				for (String id : CasinoAdvancements.IDS) {
					if (CasinoAdvancements.has(player, id)) {
						have++;
					}
				}
				out.line(Component.translatable("gui.burmaldaholic.achievements.progress", Texts.number(have), Texts.number(CasinoAdvancements.IDS.size())), 0xFFD700);
				out.button("client:advancements", Component.translatable("gui.advancements"));
				out.blank();
				for (String id : CasinoAdvancements.IDS) {
					boolean got = CasinoAdvancements.has(player, id);
					Component title = Component.translatable("advancement.burmaldaholic." + id + ".title");
					out.line(title.copy().withStyle(got ? ChatFormatting.BOLD : ChatFormatting.RESET), got ? 0x55FF55 : 0x777777);
					out.line(Component.translatable("advancement.burmaldaholic." + id + ".description"), got ? 0xAAAAAA : 0x555555);
				}
			}
		});
	}
}
