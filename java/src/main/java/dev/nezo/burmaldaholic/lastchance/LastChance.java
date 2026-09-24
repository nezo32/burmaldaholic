package dev.nezo.burmaldaholic.lastchance;

import dev.nezo.burmaldaholic.core.advancement.CasinoAdvancements;
import dev.nezo.burmaldaholic.Burmaldaholic;
import dev.nezo.burmaldaholic.core.chips.ChipItem;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.LastChanceConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.rng.OddsContext;
import dev.nezo.burmaldaholic.core.rng.OddsService;
import dev.nezo.burmaldaholic.core.text.Plural;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.Decision;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.Difficulty;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.HardcoreMode;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.Mode;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.Settings;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.SkipReason;
import dev.nezo.burmaldaholic.lastchance.logic.LastChanceRules.SuccessPlan;
import dev.nezo.burmaldaholic.lastchance.net.CoinFlipPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Last Chance (GAME_DESIGN §15), server side.
 *
 * <p>Hooks Fabric's {@code ServerLivingEntityEvents.ALLOW_DEATH}, which vanilla reaches in
 * {@code LivingEntity#hurtServer} right before the totem check. The listener runs in a late phase
 * (after other mods' vetoes) and yields to a held totem, so the totem keeps priority. When the coin
 * says heads the death is vetoed and the player is revived in place (nothing dropped, nothing lost
 * except the fee). In every other case the listener returns {@code true} and changes nothing about
 * the death itself: vanilla drops, {@code keepInventory}, death messages and Hardcore spectator mode
 * all behave exactly as without the mod.
 *
 * <p>High Stakes (Hardcore, opt-in): the scar is a permanent {@code max_health} attribute modifier
 * {@code burmaldaholic:last_chance_scar} whose amount is the accumulated scar; the scar is also kept
 * in the persistent player attachment and re-applied on join/respawn as a safety net.
 */
public final class LastChance {
	public static final String GAME_ID = LastChanceModule.ID;
	private static final Identifier LATE_PHASE = Burmaldaholic.id("last_chance_late");
	private static final int TOTEM_PARTICLES = 60;

	private static AttachmentType<LastChanceState> stateType;
	private static Identifier scarId;
	private static SoundEvent sound;
	/** GameTest hook: pretend the world is (not) Hardcore. {@code null} = ask the server. */
	private static volatile Boolean hardcoreOverride;

	private LastChance() {}

	/** Public read-only view for other features (Casino Menu "Rules" page, statistics). */
	public record Status(Mode mode, double chance, int cooldownTicks, long remainingTicks, int scarHp) {
		public boolean applies() {
			return mode != null;
		}
	}

	static void register(dev.nezo.burmaldaholic.core.module.ModuleContext ctx) {
		stateType = AttachmentRegistry.create(ctx.id("last_chance_state"), builder -> builder
			.persistent(LastChanceState.CODEC)
			.copyOnDeath()
			.initializer(() -> LastChanceState.EMPTY));
		scarId = ctx.id("last_chance_scar");
		sound = ctx.registry().sound("last_chance");
		CoinFlipPayload.TYPE = ctx.payloads().clientbound("last_chance_flip", CoinFlipPayload.CODEC);

		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Event.DEFAULT_PHASE, LATE_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(LATE_PHASE, LastChance::allowDeath);
		ServerPlayerEvents.JOIN.register(LastChance::refreshScar);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refreshScar(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				tickSecond(server);
			}
		});
	}

	// ---- config / world facts ---------------------------------------------------------------

	public static Settings settings() {
		LastChanceConfig c = CasinoConfig.lastChance();
		return new Settings(c.enabled, c.chance.easy, c.chance.normal, c.chance.hard, c.cooldownTicks, c.costPercent,
			c.hardcoreMode == LastChanceConfig.HardcoreMode.HIGH_STAKES ? HardcoreMode.HIGH_STAKES : HardcoreMode.DISABLED,
			c.hardcore.chance, c.hardcore.cooldownTicks, c.hardcore.minStake, c.hardcore.heartCost, c.hardcore.minMaxHealth);
	}

	public static boolean hardcore(MinecraftServer server) {
		Boolean o = hardcoreOverride;
		return o != null ? o : server.isHardcore();
	}

	/** GameTests only: force (or with {@code null} stop forcing) the Hardcore flag seen by Last Chance. */
	public static void setHardcoreOverrideForTests(Boolean hardcore) {
		hardcoreOverride = hardcore;
	}

	private static Difficulty difficulty(ServerLevel level) {
		return switch (level.getDifficulty()) {
			case PEACEFUL -> Difficulty.PEACEFUL;
			case EASY -> Difficulty.EASY;
			case NORMAL -> Difficulty.NORMAL;
			case HARD -> Difficulty.HARD;
		};
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	private static MinecraftServer server(ServerPlayer player) {
		return player.level().getServer();
	}

	// ---- state ------------------------------------------------------------------------------

	public static LastChanceState state(ServerPlayer player) {
		LastChanceState s = player.getAttached(stateType);
		return s == null ? LastChanceState.EMPTY : s;
	}

	public static void setState(ServerPlayer player, LastChanceState state) {
		player.setAttached(stateType, state);
	}

	public static Status status(ServerPlayer player) {
		MinecraftServer server = server(player);
		Settings s = settings();
		Mode mode = CasinoMode.isEnabled(server) ? LastChanceRules.modeFor(hardcore(server), s) : null;
		LastChanceState st = state(player);
		if (mode == null) {
			return new Status(null, 0, 0, 0, st.scarHp());
		}
		int cd = LastChanceRules.cooldownFor(mode, s);
		double chance = mode == Mode.HIGH_STAKES ? LastChanceRules.clamp01(s.hardcoreChance()) : LastChanceRules.chanceFor(difficulty(player.level()), s);
		return new Status(mode, chance, cd, LastChanceRules.cooldownRemaining(now(server), st.used(), st.usedAt(), cd), st.scarHp());
	}

	// ---- the lethal hit ---------------------------------------------------------------------

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || player.isRemoved()) {
			return true;
		}
		try {
			return !tryRescue(player, source);
		} catch (RuntimeException e) {
			// Never let a bug in the casino eat a vanilla death.
			Burmaldaholic.LOGGER.error("Last Chance failed; letting the death proceed", e);
			return true;
		}
	}

	/** @return true if the death was cancelled (heads). */
	static boolean tryRescue(ServerPlayer player, DamageSource source) {
		MinecraftServer server = server(player);
		if (server == null) {
			return false;
		}
		Settings s = settings();
		boolean hardcore = hardcore(server);
		LastChanceState st = state(player);
		long balance = Economies.get().balance(player);
		Decision d = LastChanceRules.decide(
			new LastChanceRules.Hit(
				source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL),
				source.is(Stakes.SOUL_WAGER),
				attackerTypes(source)),
			new LastChanceRules.PlayerFacts(balance, chipsCarried(player), player.getMaxHealth(), holdsTotem(player), st.used(), st.usedAt()),
			new LastChanceRules.WorldFacts(CasinoMode.isEnabled(server), hardcore, difficulty(player.level()), now(server)),
			s);
		if (!d.flips()) {
			if (d.skip() == SkipReason.NOT_ELIGIBLE) {
				player.sendSystemMessage(Component.translatable("msg.burmaldaholic.lastchance.hardcore.not_eligible"));
			}
			return false;
		}
		boolean heads = roll(player, d);
		// §15.1: the cooldown starts on either outcome.
		setState(player, st.flipped(now(server)));
		if (ServerPlayNetworking.canSend(player, CoinFlipPayload.TYPE)) {
			ServerPlayNetworking.send(player, new CoinFlipPayload(heads, d.mode() == Mode.HIGH_STAKES));
		}
		if (heads) {
			revive(player, d, s);
		} else {
			lost(player, d, hardcore);
		}
		return heads;
	}

	private static boolean roll(ServerPlayer player, Decision d) {
		double p = d.chance();
		if (p >= 1.0) {
			return true;
		}
		if (p <= 0.0) {
			return false;
		}
		if (d.mode() == Mode.HIGH_STAKES) {
			// §15.3: fixed probability, no VIP/chaos tilt.
			return LastChanceRules.flip(OddsService.get().fair().nextDouble(), p);
		}
		// VIP / chaos odds modifiers may tilt the standard coin (order 200/300; ours would be 400).
		return OddsService.get().rng(new OddsContext(player.getUUID(), GAME_ID, 0)).chance(p);
	}

	private static void revive(ServerPlayer player, Decision d, Settings s) {
		MinecraftServer server = server(player);
		long balance = Economies.get().balance(player);
		SuccessPlan plan = LastChanceRules.planSuccess(d.mode(), s, balance);
		if (plan.destroyChips()) {
			destroyChips(player);
		}
		long fee = Math.min(plan.fee(), balance);
		if (fee > 0 && !Economies.get().tryWithdraw(player, fee, Transaction.of(GAME_ID, d.mode() == Mode.HIGH_STAKES ? "high_stakes" : "fee"))) {
			fee = 0;
		}
		if (plan.addScarHp() > 0) {
			LastChanceState st = state(player);
			setState(player, st.withScar(st.scarHp() + plan.addScarHp()));
			refreshScar(player);
		}

		player.setHealth(LastChanceRules.reviveHealth(player.getMaxHealth()));
		player.clearFire();
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 4));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), TOTEM_PARTICLES, 0.4, 0.8, 0.4, 0.5);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);

		CasinoAdvancements.grant(player, "not_today");
		if (d.mode() == Mode.HIGH_STAKES && hardcore(server)) {
			CasinoAdvancements.grant(player, "scarred");
		}
		if (d.mode() == Mode.HIGH_STAKES) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.lastchance.hardcore.success"));
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.lastchance.hardcore.scar", hearts(plan.addScarHp())));
			broadcast(server, player, Component.translatable("msg.burmaldaholic.lastchance.hardcore.broadcast", player.getDisplayName()));
		} else {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.lastchance.success", Texts.chips(fee)));
			broadcast(server, player, Component.translatable("msg.burmaldaholic.lastchance.broadcast_success", player.getDisplayName()));
		}
		if (d.cooldownTicks() > 0) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.lastchance.cooldown_started", duration(d.cooldownTicks())));
		}
	}

	private static void lost(ServerPlayer player, Decision d, boolean hardcore) {
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.lastchance.failure"));
		broadcast(server(player), player, Component.translatable("msg.burmaldaholic.lastchance.broadcast_failure", player.getDisplayName()));
		// A Hardcore death is final (spectator): no point announcing a recharge.
		if (!hardcore && d.cooldownTicks() > 0) {
			player.sendSystemMessage(Component.translatable("msg.burmaldaholic.lastchance.cooldown_started", duration(d.cooldownTicks())));
		}
	}

	private static void broadcast(MinecraftServer server, ServerPlayer except, Component message) {
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != except) {
				other.sendSystemMessage(message);
			}
		}
	}

	// ---- helpers ----------------------------------------------------------------------------

	private static List<String> attackerTypes(DamageSource source) {
		List<String> out = new ArrayList<>(2);
		for (Entity e : new Entity[] {source.getEntity(), source.getDirectEntity()}) {
			if (e != null) {
				out.add(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
			}
		}
		return out;
	}

	private static boolean holdsTotem(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).get(DataComponents.DEATH_PROTECTION) != null) {
				return true;
			}
		}
		return false;
	}

	/** Total chip value carried in the player's inventory. */
	public static long chipsCarried(ServerPlayer player) {
		Inventory inv = player.getInventory();
		long total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			total += ChipItem.valueOf(inv.getItem(i));
		}
		return total;
	}

	private static long destroyChips(ServerPlayer player) {
		Inventory inv = player.getInventory();
		long total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			long v = ChipItem.valueOf(stack);
			if (v > 0) {
				total += v;
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		return total;
	}

	/** "20 minutes" / "30 seconds" in the accusative ("recharges in …" / "через …"). */
	static Component duration(long ticks) {
		long minutes = LastChanceRules.wholeMinutes(ticks);
		return minutes > 0
			? Texts.plural("unit.burmaldaholic.minute_acc", minutes)
			: Texts.plural("unit.burmaldaholic.second_acc", LastChanceRules.wholeSeconds(ticks));
	}

	/** Scar HP as hearts: whole hearts pluralised normally, "1½ hearts" uses the "few" form. */
	static Component hearts(int hp) {
		if (hp % 2 == 0) {
			return Texts.plural("unit.burmaldaholic.heart", hp / 2);
		}
		return Component.translatable(Plural.key("unit.burmaldaholic.heart", 2), Texts.raw(LastChanceRules.heartsNumber(hp)));
	}

	/**
	 * Makes the {@code last_chance_scar} modifier match the stored scar and clamps health. While casino mode is
	 * off the scar is dormant (review m5, §2.1, like heart-wager penalties): the modifier is removed, the stored
	 * scar is kept and comes back when the mode is switched on again.
	 */
	public static void refreshScar(ServerPlayer player) {
		AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
		if (attr == null) {
			return;
		}
		int scar = CasinoMode.isEnabled(player) ? state(player).scarHp() : 0;
		AttributeModifier current = attr.getModifier(scarId);
		if (scar <= 0) {
			if (current != null) {
				attr.removeModifier(scarId);
			}
			return;
		}
		if (current == null || current.amount() != -scar || current.operation() != AttributeModifier.Operation.ADD_VALUE) {
			attr.addOrReplacePermanentModifier(new AttributeModifier(scarId, -scar, AttributeModifier.Operation.ADD_VALUE));
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void tickSecond(MinecraftServer server) {
		boolean casino = CasinoMode.isEnabled(server);
		Settings s = settings();
		Mode mode = LastChanceRules.modeFor(hardcore(server), s);
		long now = now(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			refreshScar(player);
			if (!casino || mode == null || !player.isAlive()) {
				continue;
			}
			LastChanceState st = state(player);
			if (LastChanceRules.readyDue(st.used(), st.notified(), st.usedAt(), now, LastChanceRules.cooldownFor(mode, s))) {
				setState(player, st.withNotified());
				player.sendSystemMessage(Component.translatable("msg.burmaldaholic.lastchance.ready"));
			}
		}
	}
}
