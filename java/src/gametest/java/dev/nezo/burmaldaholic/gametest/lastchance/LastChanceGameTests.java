package dev.nezo.burmaldaholic.gametest.lastchance;

import com.mojang.authlib.GameProfile;
import dev.nezo.burmaldaholic.core.chips.Chips;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.sections.LastChanceConfig;
import dev.nezo.burmaldaholic.core.economy.Economies;
import dev.nezo.burmaldaholic.core.economy.Economy.Transaction;
import dev.nezo.burmaldaholic.core.mode.CasinoMode;
import dev.nezo.burmaldaholic.core.wager.Stakes;
import dev.nezo.burmaldaholic.lastchance.LastChance;
import dev.nezo.burmaldaholic.lastchance.LastChanceState;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Last Chance in a real world (headless server): heads revives in place, and whenever the coin is not
 * flipped or lands on tails the vanilla death (drops / keepInventory) is left untouched.
 */
public class LastChanceGameTests {
	private static final Transaction TEST = Transaction.of("lastchance", "gametest");

	/** A survival player (the vanilla mock player is creative and cannot die). */
	@SuppressWarnings("removal")
	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "lc-test"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		// players are invulnerable until their client reports the level as loaded
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().invulnerable = false;
		clearInvulnerableTime(player);
		return player;
	}

	/** Runs {@code body} with a fresh survival player and restores config, rules and the Hardcore override. */
	private static void scenario(GameTestHelper helper, double chance, Consumer<ServerPlayer> body) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		LastChanceConfig cfg = CasinoConfig.lastChance();
		double easy = cfg.chance.easy;
		double normal = cfg.chance.normal;
		double hard = cfg.chance.hard;
		double hcChance = cfg.hardcore.chance;
		boolean enabled = cfg.enabled;
		LastChanceConfig.HardcoreMode hcMode = cfg.hardcoreMode;
		boolean casino = level.getGameRules().get(CasinoMode.rule());
		boolean keepInv = level.getGameRules().get(GameRules.KEEP_INVENTORY);
		ServerPlayer player = survivalPlayer(helper);
		try {
			cfg.chance.easy = chance;
			cfg.chance.normal = chance;
			cfg.chance.hard = chance;
			cfg.hardcore.chance = chance;
			cfg.enabled = true;
			level.getGameRules().set(CasinoMode.rule(), true, server);
			level.getGameRules().set(GameRules.KEEP_INVENTORY, false, server);
			Economies.get().setBalance(server, player.getUUID(), 1000, TEST);
			player.getInventory().clearContent();
			LastChance.setState(player, LastChanceState.EMPTY);
			body.accept(player);
		} finally {
			cfg.chance.easy = easy;
			cfg.chance.normal = normal;
			cfg.chance.hard = hard;
			cfg.hardcore.chance = hcChance;
			cfg.enabled = enabled;
			cfg.hardcoreMode = hcMode;
			LastChance.setHardcoreOverrideForTests(null);
			level.getGameRules().set(CasinoMode.rule(), casino, server);
			level.getGameRules().set(GameRules.KEEP_INVENTORY, keepInv, server);
			server.getPlayerList().remove(player);
		}
	}

	private static void lethal(ServerPlayer player, DamageSource source) {
		clearInvulnerableTime(player);
		player.hurtServer(player.level(), source, 1000f);
	}

	private static void lethal(ServerPlayer player) {
		lethal(player, player.damageSources().generic());
	}

	private static int dirt(ServerPlayer player) {
		return player.getInventory().countItem(Items.DIRT);
	}

	@GameTest
	public void headsRevivesInPlaceWithFeeAndCooldown(GameTestHelper helper) {
		scenario(helper, 1.0, player -> {
			player.getInventory().add(new ItemStack(Items.DIRT, 5));
			lethal(player);
			helper.assertTrue(player.isAlive() && !player.isDeadOrDying(), "heads: alive");
			helper.assertTrue(player.getHealth() == 10f, "revived at ceil(max / 2), got " + player.getHealth());
			helper.assertTrue(Economies.get().balance(player) == 900, "10 % fee taken");
			helper.assertTrue(dirt(player) == 5, "nothing dropped");
			helper.assertTrue(player.hasEffect(MobEffects.RESISTANCE) && player.hasEffect(MobEffects.REGENERATION), "Resistance + Regeneration");
			helper.assertTrue(LastChance.state(player).used(), "cooldown started");

			// on cooldown: the next lethal hit is an ordinary vanilla death
			player.removeAllEffects();
			lethal(player);
			helper.assertTrue(player.isDeadOrDying(), "second death within the cooldown is final");
			helper.assertTrue(Economies.get().balance(player) == 900, "no second fee");
			helper.assertTrue(dirt(player) == 0, "vanilla drops (keepInventory off)");
		});
		helper.succeed();
	}

	@GameTest
	public void tailsLeavesVanillaDeathAndDropsUntouched(GameTestHelper helper) {
		scenario(helper, 0.0, player -> {
			player.getInventory().add(new ItemStack(Items.DIRT, 5));
			lethal(player);
			helper.assertTrue(player.isDeadOrDying(), "tails: dead");
			helper.assertTrue(dirt(player) == 0, "inventory dropped as in vanilla");
			helper.assertTrue(Economies.get().balance(player) == 1000, "no fee on tails");
			helper.assertTrue(LastChance.state(player).used(), "cooldown starts on either outcome");
		});
		helper.succeed();
	}

	@GameTest
	public void tailsRespectsKeepInventory(GameTestHelper helper) {
		scenario(helper, 0.0, player -> {
			helper.getLevel().getGameRules().set(GameRules.KEEP_INVENTORY, true, helper.getLevel().getServer());
			player.getInventory().add(new ItemStack(Items.DIRT, 5));
			lethal(player);
			helper.assertTrue(player.isDeadOrDying(), "tails: dead");
			helper.assertTrue(dirt(player) == 5, "keepInventory keeps the items");
		});
		helper.succeed();
	}

	@GameTest
	public void casinoModeOffIsPureVanilla(GameTestHelper helper) {
		scenario(helper, 1.0, player -> {
			helper.getLevel().getGameRules().set(CasinoMode.rule(), false, helper.getLevel().getServer());
			player.getInventory().add(new ItemStack(Items.DIRT, 5));
			lethal(player);
			helper.assertTrue(player.isDeadOrDying(), "no Last Chance with casino mode off");
			helper.assertTrue(dirt(player) == 0, "vanilla drops");
			helper.assertFalse(LastChance.state(player).used(), "no flip recorded");
		});
		helper.succeed();
	}

	@GameTest
	public void disabledInConfigIsPureVanilla(GameTestHelper helper) {
		scenario(helper, 1.0, player -> {
			CasinoConfig.lastChance().enabled = false;
			lethal(player);
			helper.assertTrue(player.isDeadOrDying(), "lastChance.enabled = false");
			helper.assertFalse(LastChance.state(player).used(), "no flip recorded");
		});
		helper.succeed();
	}

	@GameTest
	public void excludedCausesAreFinal(GameTestHelper helper) {
		scenario(helper, 1.0, player -> {
			player.kill(player.level());
			helper.assertTrue(player.isDeadOrDying(), "/kill is final");
			helper.assertFalse(LastChance.state(player).used(), "no flip for /kill");
		});
		scenario(helper, 1.0, player -> {
			lethal(player, player.damageSources().fellOutOfWorld());
			helper.assertTrue(player.isDeadOrDying(), "the void is final");
			helper.assertFalse(LastChance.state(player).used(), "no flip for the void");
		});
		scenario(helper, 1.0, player -> {
			lethal(player, player.damageSources().source(Stakes.SOUL_WAGER));
			helper.assertTrue(player.isDeadOrDying(), "a lost Soul Wager is final");
			helper.assertFalse(LastChance.state(player).used(), "no flip for the Soul Wager");
		});
		helper.succeed();
	}

	@GameTest
	public void totemTakesPriority(GameTestHelper helper) {
		scenario(helper, 1.0, player -> {
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
			lethal(player);
			helper.assertTrue(player.isAlive(), "saved by the totem");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "totem consumed");
			helper.assertFalse(LastChance.state(player).used(), "Last Chance not used");
			helper.assertTrue(Economies.get().balance(player) == 1000, "no fee");
		});
		helper.succeed();
	}

	@GameTest
	public void hardcoreDisabledByDefault(GameTestHelper helper) {
		scenario(helper, 1.0, player -> {
			LastChance.setHardcoreOverrideForTests(true);
			CasinoConfig.lastChance().hardcoreMode = LastChanceConfig.HardcoreMode.DISABLED;
			player.getInventory().add(new ItemStack(Items.DIRT, 5));
			lethal(player);
			helper.assertTrue(player.isDeadOrDying(), "Hardcore death is final by default");
			helper.assertTrue(dirt(player) == 0, "vanilla drops");
			helper.assertTrue(Economies.get().balance(player) == 1000, "nothing taken");
			helper.assertFalse(LastChance.state(player).used(), "no flip recorded");
		});
		helper.succeed();
	}

	@GameTest
	public void hardcoreHighStakesTakesEverythingAndScars(GameTestHelper helper) {
		scenario(helper, 1.0, player -> {
			LastChance.setHardcoreOverrideForTests(true);
			CasinoConfig.lastChance().hardcoreMode = LastChanceConfig.HardcoreMode.HIGH_STAKES;
			player.getInventory().add(new ItemStack(Chips.item(100), 3));
			player.getInventory().add(new ItemStack(Items.DIRT, 5));
			lethal(player);
			helper.assertTrue(player.isAlive(), "High Stakes heads: alive");
			helper.assertTrue(Economies.get().balance(player) == 0, "balance set to 0");
			helper.assertTrue(LastChance.chipsCarried(player) == 0, "chip items destroyed");
			helper.assertTrue(dirt(player) == 5, "other items kept");
			helper.assertTrue(player.getMaxHealth() == 18f, "permanent −2 HP, got " + player.getMaxHealth());
			helper.assertTrue(player.getHealth() == 9f, "half of the new max health");
			helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH).getModifier(net.minecraft.resources.Identifier.fromNamespaceAndPath("burmaldaholic", "last_chance_scar")) != null,
				"scar modifier present");
			helper.assertTrue(LastChance.state(player).scarHp() == 2, "scar stored");
			LastChance.refreshScar(player);
			helper.assertTrue(player.getMaxHealth() == 18f, "refresh keeps a single stacked modifier");
		});
		helper.succeed();
	}

	@GameTest
	public void hardcoreHighStakesNotEligibleWhenPoor(GameTestHelper helper) {
		scenario(helper, 1.0, player -> {
			LastChance.setHardcoreOverrideForTests(true);
			CasinoConfig.lastChance().hardcoreMode = LastChanceConfig.HardcoreMode.HIGH_STAKES;
			Economies.get().setBalance(helper.getLevel().getServer(), player.getUUID(), 10, TEST);
			lethal(player);
			helper.assertTrue(player.isDeadOrDying(), "not eligible: vanilla Hardcore death");
			helper.assertTrue(Economies.get().balance(player) == 10, "stake not taken");
			helper.assertFalse(LastChance.state(player).used(), "no flip recorded");
		});
		helper.succeed();
	}

	/**
	 * Clears the post-hit damage cooldown: {@code Entity.invulnerableTime} (public on 26.2, private on 26.3) and
	 * 26.3's {@code LivingEntity.damageCooldownTime}. Reflective so the same test compiles on both versions.
	 */
	private static void clearInvulnerableTime(net.minecraft.world.entity.Entity entity) {
		for (String name : new String[] {"invulnerableTime", "damageCooldownTime"}) {
			for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass()) {
				try {
					java.lang.reflect.Field f = c.getDeclaredField(name);
					f.setAccessible(true);
					f.setInt(entity, 0);
					break;
				} catch (NoSuchFieldException e) {
					// try the superclass / the other version's name
				} catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}
}
