package dev.nezo.burmaldaholic.core.mode;

import dev.nezo.burmaldaholic.Burmaldaholic;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Casino mode is a per-world boolean game rule {@code burmaldaholic:casino_mode} (default OFF,
 * docs/design/GAME_DESIGN.md §2.1): the player opts in with the "Casino Mode" button directly below
 * "Difficulty" on the Create World "Game" tab (client mixin {@code CreateWorldGameTabMixin}). It is also
 * shown in Create World -> More -> Game Rules under the "Burmaldaholic" category, and existing worlds
 * switch with {@code /gamerule burmaldaholic:casino_mode true|false}. It is the single source of truth (config never
 * overrides it). Vanilla difficulty and hardcore are untouched.
 *
 * <p><b>Every feature must check {@link #isEnabled} before doing anything gameplay-related</b>
 * (opening tables, spawning collectors, chaos events, worldgen structures...).
 */
public final class CasinoMode {
	public static final GameRuleCategory CATEGORY = GameRuleCategory.register(Burmaldaholic.id("casino"));
	public static final boolean DEFAULT = false;
	// Lang: gamerule.burmaldaholic.casino_mode (+ ".description"), gamerule.category.burmaldaholic.casino
	private static GameRule<Boolean> rule;

	/** Set by the client entrypoint; answers for client-side levels (synced from the server). */
	private static BooleanSupplier clientLookup = () -> false;

	private CasinoMode() {}

	public static void register() {
		rule = GameRuleBuilder.forBoolean(DEFAULT).category(CATEGORY).buildAndRegister(Burmaldaholic.id("casino_mode"));
	}

	public static GameRule<Boolean> rule() {
		return rule;
	}

	public static boolean isEnabled(MinecraftServer server) {
		return server != null && server.getGameRules().get(rule);
	}

	public static boolean isEnabled(Level level) {
		if (level instanceof ServerLevel serverLevel) {
			return serverLevel.getGameRules().get(rule);
		}
		return level != null && clientLookup.getAsBoolean();
	}

	public static boolean isEnabled(Player player) {
		return player != null && isEnabled(player.level());
	}

	public static void setClientLookup(BooleanSupplier lookup) {
		clientLookup = lookup;
	}
}
