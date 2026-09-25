package dev.nezo.burmaldaholic.loan;

import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.menu.CasinoMenu;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.wager.Wagers;
import net.minecraft.network.chat.Component;
import dev.nezo.burmaldaholic.loan.net.LoanActionPayload;
import dev.nezo.burmaldaholic.loan.net.LoanUiPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loan Shark NPC, debt and Debt Collectors (GAME_DESIGN.md §5).
 *
 * <ul>
 *   <li>{@link LoanService} — loan records, take/pay, warnings, default, late fees, garnishment, Asset Freeze, debt provider</li>
 *   <li>{@link LoanShark} — Loan Shark / Piglin Moneylender screen sessions and respawn</li>
 *   <li>{@link LoanSquads} — collector waves, squad state machine, negotiation, Repossession</li>
 *   <li>{@code logic/} — pure rules (unit-tested); {@code entity/} — the NPC and the four squad units</li>
 * </ul>
 */
public final class LoanModule implements CasinoModule {
	public static final String ID = "loan";
	static final Logger LOG = LoggerFactory.getLogger("burmaldaholic/loan");

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		LoanContent.register(ctx);
		LoanUiPayload.TYPE = ctx.payloads().clientbound("loan_ui", LoanUiPayload.CODEC);
		dev.nezo.burmaldaholic.loan.net.LoanFxPayload.TYPE = ctx.payloads().clientbound("loan_fx", dev.nezo.burmaldaholic.loan.net.LoanFxPayload.CODEC);
		LoanActionPayload.TYPE = ctx.payloads().serverbound("loan_action", LoanActionPayload.CODEC, (payload, context) -> {
			if (LoanUiPayload.NEGOTIATE.equals(payload.screen())) {
				LoanSquads.onAnswer(context.player(), payload);
			} else {
				LoanShark.onAction(context.player(), payload);
			}
		});
		CoreServices.setDebt(LoanService.PROVIDER);
		// Asset Freeze (§5.6): in default without collectors → no new stakes of any kind (chips, pawns, PvP).
		Wagers.addVeto((player, context) -> LoanService.frozen(player.level().getServer(), player.getUUID())
			? Component.translatable("gui.burmaldaholic.error.in_default") : null);
		CasinoMenu.register(new LoanMenuPage());
		Economies.get().addCreditHook(LoanService::beforeCredit);
		LoanCommands.register();

		ServerTickEvents.END_SERVER_TICK.register(LoanModule::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LoanSquads.onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LoanShark.onDisconnect(handler.getPlayer()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0) {
				LoanSquads.onDamage(entity, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				LoanSquads.onPlayerDeath(player, source);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LoanSquads.clear();
			LoanShark.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % 10 == 0) {
			LoanSquads.tick(server);
		}
		if (t % 20 != 0 || !LoanService.active(server)) {
			return;
		}
		LoanService.dormancy(server);
		LoanShark.prune(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			try {
				LoanService.tick(p);
			} catch (RuntimeException e) {
				LOG.error("Loan tick failed for {}", p.getName().getString(), e);
			}
		}
		if (t % 100 == 0) {
			LoanShark.respawnTick(server);
		}
	}
}
