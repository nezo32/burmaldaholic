package dev.nezo.burmaldaholic.core.wager;

import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.WagerConfig;
import dev.nezo.burmaldaholic.core.data.CasinoWorldData;
import dev.nezo.burmaldaholic.core.data.PlayerRecord;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.events.CasinoEvents.PlayResult;
import dev.nezo.burmaldaholic.core.events.PlayResults;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.service.CoreServices;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.util.Inventories;
import dev.nezo.burmaldaholic.core.util.Result;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Reusable stake API for all games (GAME_DESIGN.md §4): chips, held item, XP levels, temporary
 * hearts and the Hardcore Soul Wager. Server thread only.
 *
 * <pre>
 * Result&lt;Stake&gt; r = Stakes.xp(player, "extras", 5);           // validates + takes the levels into escrow
 * if (!r.isOk()) { player.sendOverlayMessage(r.error()); return; }
 * Stake stake = r.value();
 * boolean win = ...;                                              // decide with OddsService
 * Stakes.settle(player, stake, win ? Outcome.WIN : Outcome.LOSS, (long) Math.floor(stake.value() * 0.96));
 * </pre>
 *
 * Pawn stakes (everything except chips) are only allowed on the one-bet games of §4.3 — the game
 * decides where to offer them. {@link #settle} reports the round ({@code PlayResults.fire}). Every new stake
 * passes the wager gate ({@link Wagers#check}: casino mode, owned-table rules, loan Asset Freeze ...).
 */
public final class Stakes {
	public static final ResourceKey<DamageType> SOUL_WAGER = ResourceKey.create(Registries.DAMAGE_TYPE, Burmaldaholic.id("soul_wager"));

	public enum Outcome {
		WIN, PUSH, LOSS
	}

	private Stakes() {}

	private static Component error(String key, Object... args) {
		return Component.translatable("gui.burmaldaholic.error." + key, args);
	}

	private static MinecraftServer server(ServerPlayer p) {
		return p.level().getServer();
	}

	private static long tierMax(ServerPlayer player) {
		return CoreServices.vip().maxBet(server(player), player.getUUID());
	}

	/** The wager gate ({@link Wagers#check}) for a pawn stake; null = allowed. */
	private static @Nullable Component gate(ServerPlayer player, String gameId, Stake.Kind kind, @Nullable BlockPos table) {
		return Wagers.check(player, new WagerVeto.Context(gameId, kind, table, false));
	}

	/** Debits a chip stake after {@link BetLimits} validation (house-banked). */
	public static Result<Stake> chips(ServerPlayer player, String gameId, long amount, long min, long tableMax) {
		if (!CasinoMode.isEnabled(player)) {
			return Result.fail(error("casino_off"));
		}
		Component err = BetLimits.validate(player, amount, min, tableMax, WagerVeto.Context.chips(gameId));
		if (err != null) {
			return Result.fail(err);
		}
		if (!Economies.get().tryWithdraw(player, amount, Transaction.bet(gameId))) {
			return Result.fail(error("insufficient_funds", Texts.number(Economies.get().balance(player))));
		}
		return Result.ok(new Stake(player.getUUID(), gameId, Stake.Kind.CHIPS, amount, ItemStack.EMPTY, 0, 0, 0));
	}

	/** Appraised value of a stack (0 = not accepted). Pure lookup in {@code wager.appraisal}. */
	public static long appraise(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		Map<String, Integer> table = CasinoConfig.wager().appraisal;
		Integer each = table.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		return each == null || each <= 0 ? 0 : (long) each * stack.getCount();
	}

	/** True if the stack may be pawned at all (undamaged, unenchanted, unnamed). */
	public static boolean pristine(ItemStack stack) {
		return !stack.isDamaged() && !stack.isEnchanted() && !stack.has(DataComponents.CUSTOM_NAME);
	}

	/** Takes the main-hand stack into escrow (§4.3.1). */
	public static Result<Stake> heldItem(ServerPlayer player, String gameId) {
		return heldItem(player, gameId, null);
	}

	/** Same, at a table / machine ({@code table} = its position, for the wager gate). */
	public static Result<Stake> heldItem(ServerPlayer player, String gameId, @Nullable BlockPos table) {
		WagerConfig cfg = CasinoConfig.wager();
		if (!CasinoMode.isEnabled(player) || !cfg.pawnEnabled || !cfg.items.enabled) {
			return Result.fail(error(CasinoMode.isEnabled(player) ? "disabled" : "casino_off"));
		}
		Component veto = gate(player, gameId, Stake.Kind.ITEM, table);
		if (veto != null) {
			return Result.fail(veto);
		}
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		long value = appraise(held);
		if (value <= 0) {
			return Result.fail(error("pawn_not_accepted"));
		}
		if (!pristine(held)) {
			return Result.fail(error("pawn_damaged"));
		}
		if (value > tierMax(player)) {
			return Result.fail(error("pawn_too_valuable", Texts.chips(value)));
		}
		ItemStack escrow = held.copy();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		return Result.ok(new Stake(player.getUUID(), gameId, Stake.Kind.ITEM, value, escrow, 0, 0, 0));
	}

	/** Takes {@code levels} XP levels into escrow (§4.3.2). */
	public static Result<Stake> xp(ServerPlayer player, String gameId, int levels) {
		return xp(player, gameId, levels, null);
	}

	public static Result<Stake> xp(ServerPlayer player, String gameId, int levels, @Nullable BlockPos table) {
		WagerConfig cfg = CasinoConfig.wager();
		if (!CasinoMode.isEnabled(player) || !cfg.pawnEnabled || !cfg.xp.enabled) {
			return Result.fail(error(CasinoMode.isEnabled(player) ? "disabled" : "casino_off"));
		}
		Component veto = gate(player, gameId, Stake.Kind.XP, table);
		if (veto != null) {
			return Result.fail(veto);
		}
		int current = player.experienceLevel;
		if (!PawnRules.xpStakeAllowed(current, levels, cfg.xp.maxLevels)) {
			return Result.fail(error("xp_not_enough"));
		}
		long value = PawnRules.xpStakeValue(current, levels, cfg.xp.pointsPerChip);
		if (value > tierMax(player)) {
			return Result.fail(error("pawn_too_valuable", Texts.chips(value)));
		}
		if (value <= 0) {
			return Result.fail(error("invalid_amount"));
		}
		// Review m3: only the whole levels are staked (they are what V counts); the partial progress towards
		// the next level stays with the player whatever the outcome.
		player.setExperienceLevels(current - levels);
		return Result.ok(new Stake(player.getUUID(), gameId, Stake.Kind.XP, value, ItemStack.EMPTY, levels, 0, 0));
	}

	/** Puts {@code hearts} max-health hearts at risk (§4.3.3); nothing is taken until a loss. */
	public static Result<Stake> hearts(ServerPlayer player, String gameId, int hearts) {
		return hearts(player, gameId, hearts, null);
	}

	public static Result<Stake> hearts(ServerPlayer player, String gameId, int hearts, @Nullable BlockPos table) {
		WagerConfig cfg = CasinoConfig.wager();
		if (!CasinoMode.isEnabled(player) || !cfg.pawnEnabled || !cfg.hearts.enabled) {
			return Result.fail(error(CasinoMode.isEnabled(player) ? "disabled" : "casino_off"));
		}
		Component veto = gate(player, gameId, Stake.Kind.HEARTS, table);
		if (veto != null) {
			return Result.fail(veto);
		}
		if (!PawnRules.heartStakeAllowed(hearts, HeartPenalties.activeHearts(player), cfg.hearts.maxPerBet, cfg.hearts.maxTotal, player.getMaxHealth())) {
			return Result.fail(error("hearts_cap"));
		}
		long value = (long) hearts * cfg.hearts.valuePerHeart;
		if (value > tierMax(player)) {
			return Result.fail(error("pawn_too_valuable", Texts.chips(value)));
		}
		return Result.ok(new Stake(player.getUUID(), gameId, Stake.Kind.HEARTS, value, ItemStack.EMPTY, 0, 0, hearts));
	}

	/** True if the Soul Wager may be offered to this player at all (Hardcore world + config). */
	public static boolean soulWagerAvailable(ServerPlayer player) {
		return CasinoMode.isEnabled(player) && server(player).isHardcore() && CasinoConfig.wager().hardcoreSoulWager;
	}

	/** Hardcore Soul Wager (§4.4): V = max(minValue, balance); cooldown per player. Coin Flip only. */
	public static Result<Stake> soul(ServerPlayer player, String gameId) {
		if (!soulWagerAvailable(player)) {
			return Result.fail(error("disabled"));
		}
		Component veto = gate(player, gameId, Stake.Kind.SOUL, null);
		if (veto != null) {
			return Result.fail(veto);
		}
		MinecraftServer server = server(player);
		PlayerRecord rec = CasinoWorldData.get(server).player(player.getUUID());
		long now = server.overworld().getGameTime();
		if (now < rec.soulReadyAt) {
			long minutes = Math.max(1, (rec.soulReadyAt - now + 1199) / 1200);
			return Result.fail(Component.translatable("msg.burmaldaholic.wager.soul_cooldown", Texts.plural("unit.burmaldaholic.minute", minutes)));
		}
		long value = PawnRules.soulValue(Economies.get().balance(player), CasinoConfig.wager().soul.minValue);
		return Result.ok(new Stake(player.getUUID(), gameId, Stake.Kind.SOUL, value, ItemStack.EMPTY, 0, 0, 0));
	}

	/**
	 * Settles a stake. {@code winnings} = chips won on top of the stake (e.g. coin flip {@code floor(V×0.96)});
	 * ignored for PUSH/LOSS. Chips stakes get stake + winnings back on WIN, the stake on PUSH.
	 * Pawn stakes: WIN returns the pawn + credits winnings, PUSH returns the pawn, LOSS forfeits it.
	 */
	public static void settle(ServerPlayer player, Stake stake, Outcome outcome, long winnings) {
		settle(player, stake, outcome, winnings, null);
	}

	/**
	 * Same; {@code detail} enriches the reported {@link PlayResult} (bet's own house edge, tags, table),
	 * e.g. {@code r -> r.withEdge(HouseEdges.WHEEL).withTable(level, pos, "")}.
	 */
	public static void settle(ServerPlayer player, Stake stake, Outcome outcome, long winnings, @Nullable UnaryOperator<PlayResult> detail) {
		long win = outcome == Outcome.WIN ? Math.max(0, winnings) : 0;
		Transaction payout = Transaction.payout(stake.gameId());
		switch (stake.kind()) {
			case CHIPS -> {
				// Review m8: the stake comes back as the player's own money (not garnishable); only winnings are a PAYOUT.
				if (outcome != Outcome.LOSS) {
					Economies.get().deposit(player, stake.value(), new Transaction(stake.gameId(), "stake_return", Transaction.Kind.TRANSFER));
				}
				if (outcome == Outcome.WIN && win > 0) {
					Economies.get().deposit(player, win, payout);
				}
			}
			case ITEM -> {
				if (outcome == Outcome.LOSS) {
					player.sendSystemMessage(Component.translatable("msg.burmaldaholic.wager.item_lost", stake.item().getHoverName()));
				} else {
					giveBack(player, stake);
					if (win > 0) {
						Economies.get().deposit(player, win, payout);
						player.sendSystemMessage(Component.translatable("msg.burmaldaholic.wager.item_returned", stake.item().getHoverName(), Texts.chips(win)));
					}
				}
			}
			case XP -> {
				if (outcome == Outcome.LOSS) {
					player.sendSystemMessage(Component.translatable("msg.burmaldaholic.wager.xp_lost", Texts.plural("unit.burmaldaholic.level", stake.xpLevels())));
				} else {
					giveBack(player, stake);
					if (win > 0) {
						Economies.get().deposit(player, win, payout);
					}
				}
			}
			case HEARTS -> {
				if (outcome == Outcome.LOSS) {
					HeartPenalties.add(player, stake.hearts(), CasinoConfig.wager().hearts.durationTicks);
					player.sendSystemMessage(Component.translatable("msg.burmaldaholic.wager.hearts_lost", Texts.plural("unit.burmaldaholic.heart", stake.hearts())));
				} else if (win > 0) {
					Economies.get().deposit(player, win, payout);
				}
			}
			case SOUL -> {
				MinecraftServer server = server(player);
				PlayerRecord rec = CasinoWorldData.get(server).player(player.getUUID());
				rec.soulReadyAt = server.overworld().getGameTime() + CasinoConfig.wager().soul.cooldownTicks;
				CasinoWorldData.get(server).setDirty();
				if (outcome == Outcome.WIN) {
					Economies.get().deposit(player, stake.value(), payout);
				} else if (outcome == Outcome.LOSS) {
					player.hurtServer(player.level(), player.level().damageSources().source(SOUL_WAGER), Float.MAX_VALUE);
				}
			}
		}
		long payoutTotal = switch (outcome) {
			case WIN -> stake.value() + (stake.kind() == Stake.Kind.SOUL ? stake.value() : win);
			case PUSH -> stake.value();
			case LOSS -> 0;
		};
		PlayResult result = PlayResult.of(stake.gameId(), stake.value(), payoutTotal).withKind(stake.kind());
		if (detail != null) {
			result = detail.apply(result);
		}
		PlayResults.fire(player, result);
	}

	/** Returns a stake without a result (cancelled round / server problem). No PLAY_RESOLVED. */
	public static void refund(ServerPlayer player, Stake stake) {
		switch (stake.kind()) {
			case CHIPS -> Economies.get().deposit(player, stake.value(), Transaction.refund(stake.gameId()));
			case ITEM, XP -> giveBack(player, stake);
			case HEARTS, SOUL -> {
			}
		}
	}

	private static void giveBack(ServerPlayer player, Stake stake) {
		if (stake.kind() == Stake.Kind.ITEM) {
			Inventories.giveOrDrop(player, stake.item().copy());
		} else if (stake.kind() == Stake.Kind.XP) {
			player.giveExperienceLevels(stake.xpLevels());
			player.giveExperiencePoints(stake.xpProgress());
		}
	}
}
