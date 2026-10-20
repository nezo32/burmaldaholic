package dev.nezo.burmaldaholic.comps;

import com.mojang.brigadier.arguments.LongArgumentType;
import dev.nezo.burmaldaholic.comps.net.CompsActionPayload;
import dev.nezo.burmaldaholic.comps.net.CompsSyncPayload;
import dev.nezo.burmaldaholic.core.cashier.counter.CounterSessions;
import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.events.CasinoEvents;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.text.Texts;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * Comps (EC-SPEC §4, package EC-2): comp points on theoretical loss, redemptions (food plates, suite night, free-bet
 * tokens, rides), the daily bonus and the Comps tab sync. See {@link CompsService}, {@link FreeBets},
 * {@link Rentals}, {@link SuiteNight}.
 */
public final class CompsModule implements CasinoModule {
	public static final String ID = "comps";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		CompsService.config = ctx.config("comps", CompsConfig.class, CompsConfig::new);
		CompsService.registerAttachment();
		FreeBets.register(ctx);
		Rentals.register();
		SuiteNight.register();
		CasinoEvents.PLAY_RESOLVED.register(CompsService::onPlayResolved);
		CounterSessions.registerTab(CounterSessions.TAB_COMPS, 20, s -> true); // cashier and merchant

		CompsSyncPayload.TYPE = ctx.payloads().clientbound("comps_sync", CompsSyncPayload.CODEC);
		CompsActionPayload.TYPE = ctx.payloads().serverbound("comps_action", CompsActionPayload.CODEC,
			(payload, context) -> handle(context.player(), payload));

		// activity gate (EC-SPEC §4.4): any interaction counts
		UseBlockCallback.EVENT.register((p, l, h, hit) -> touch(p));
		UseItemCallback.EVENT.register((p, l, h) -> touch(p));
		UseEntityCallback.EVENT.register((p, l, h, e, hit) -> touch(p));
		AttackBlockCallback.EVENT.register((p, l, h, pos, dir) -> touch(p));
		AttackEntityCallback.EVENT.register((p, l, h, e, hit) -> touch(p));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> FreeBets.deliver(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Rentals.onLogout(handler.getPlayer());
			CompsService.forget(handler.getPlayer().getUUID());
			CompsService.unwatchAll(handler.getPlayer().getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(CompsService::tick);

		CasinoCommands.extend(root -> root.then(Commands.literal("comps")
			.then(Commands.literal("points").then(Commands.argument("player", EntityArgument.player())
				.then(Commands.argument("amount", LongArgumentType.longArg(0)).executes(c -> {
					ServerPlayer p = EntityArgument.getPlayer(c, "player");
					long amount = LongArgumentType.getLong(c, "amount");
					var s = CompsService.state(p);
					CompsService.put(p, new dev.nezo.burmaldaholic.comps.logic.CompsRules.State(amount * 1000, s.lifetimeMilli(), s.earnedTodayMilli(),
						s.day(), s.dailyClaimDay(), s.activeTicksToday(), s.suiteDay(), s.redeemedToday()));
					CompsService.markSync(p);
					c.getSource().sendSuccess(() -> Component.translatable("gui.burmaldaholic.comps.points", Texts.number(amount)), true);
					return 1;
				}))))));
	}

	private static InteractionResult touch(Player p) {
		if (p instanceof ServerPlayer sp) {
			CompsService.interacted(sp);
		}
		return InteractionResult.PASS;
	}

	/** The payload receiver (also called directly by GameTests). */
	public static void handle(ServerPlayer player, CompsActionPayload payload) {
		CompsService.interacted(player);
		CompsService.watch(player);
		boolean act = CompsActionPayload.REDEEM.equals(payload.action()) || CompsActionPayload.CLAIM_DAILY.equals(payload.action());
		if (act) {
			// EC-SPEC §3: every store / comps action needs a live counter session (cashier or merchant in range, or the
			// op command) and passes the shared 4-tick cooldown; a second action in the same tick does nothing
			if (CounterSessions.validate(player) == null) {
				CounterSessions.result(player, false, Component.translatable("gui.burmaldaholic.comps.lock.unavailable"));
				return;
			}
			if (!CounterSessions.tryAct(player)) {
				return;
			}
			Component err = CompsActionPayload.REDEEM.equals(payload.action()) ? CompsService.redeem(player, payload.id(), payload.qty())
				: CompsService.claimDaily(player);
			if (err != null) {
				CounterSessions.result(player, false, err);
			} else {
				CounterSessions.result(player, true, CompsActionPayload.REDEEM.equals(payload.action())
					? Component.translatable("gui.burmaldaholic.comps.reward." + payload.id())
					: Component.translatable("toast.burmaldaholic.daily.title"));
			}
		}
		CompsService.send(player);
	}
}
