package dev.nezo.burmaldaholic.comps;

import dev.nezo.burmaldaholic.comps.logic.CompsRules;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Suite night (EC-SPEC §4.2): a bed used inside a generated casino (any dimension) is intercepted. With today's
 * suite credit the player checks in once: forced respawn at that bed, Regeneration II 60 s, Saturation 5 s and, at
 * Gold+, Absorption I 120 s. In the Overworld the vanilla sleep / night skip then still runs; in the Nether and
 * the End the bed never explodes inside a casino.
 */
final class SuiteNight {
	static final String CHECKED_IN = "suite_checked_in";

	private SuiteNight() {}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl) || !CompsService.enabled()) {
				return InteractionResult.PASS;
			}
			BlockState state = level.getBlockState(hit.getBlockPos());
			if (!(state.getBlock() instanceof BedBlock) || !inCasino(sl, hit.getBlockPos())) {
				return InteractionResult.PASS;
			}
			BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? hit.getBlockPos()
				: hit.getBlockPos().relative(state.getValue(BedBlock.FACING));
			boolean overworld = level.dimension() == Level.OVERWORLD;
			checkIn(sp, sl, head);
			// Overworld: vanilla sleep (night skip) continues; elsewhere the bed is fully handled here (no explosion)
			return overworld ? InteractionResult.PASS : InteractionResult.SUCCESS;
		});
	}

	/** Applies today's suite credit at {@code head} (once a day). @return true if the player checked in now. */
	static boolean checkIn(ServerPlayer player, ServerLevel level, BlockPos head) {
		CompsRules.State s = CompsService.state(player);
		long today = CompsService.today(level.getServer());
		if (s.suiteDay() != today) {
			player.sendOverlayMessage(Component.translatable("msg.burmaldaholic.comps.suite_needed"));
			return false;
		}
		if (s.redeemed(CHECKED_IN) > 0) {
			return false;
		}
		CompsService.put(player, s.spend(CHECKED_IN, 0));
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(
			net.minecraft.world.level.storage.LevelData.RespawnData.of(level.dimension(), head, player.getYRot(), 0), true), false);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200, 1));
		player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 100, 0));
		if (CompsService.tier(player) >= CompsRules.GOLD) {
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
		}
		player.sendSystemMessage(Component.translatable("msg.burmaldaholic.comps.suite_checked_in").withStyle(ChatFormatting.GOLD));
		return true;
	}

	@SuppressWarnings("unchecked")
	static boolean inCasino(ServerLevel level, BlockPos pos) {
		Object fn = FabricLoader.getInstance().getObjectShare().get("burmaldaholic:worldgen/casino_at");
		if (fn instanceof BiFunction<?, ?, ?> f) {
			Boolean in = ((BiFunction<ServerLevel, BlockPos, Boolean>) f).apply(level, pos);
			return in != null && in;
		}
		return false;
	}
}
