package dev.nezo.burmaldaholic.games.slots;

import dev.nezo.burmaldaholic.core.command.CasinoCommands;
import dev.nezo.burmaldaholic.core.config.CasinoConfig;
import dev.nezo.burmaldaholic.core.config.ConfigManager;
import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.sound.CasinoSounds;
import dev.nezo.burmaldaholic.core.table.TableRegistrar;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.core.text.Texts;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Slot machines (SLOTS.md): Overworld Riches, Nether Inferno and End Void on the three cabinet blocks
 * ({@code slot_machine_copper / _gold / _netherite}).
 *
 * <p>Registers the three cabinets (table framework: block + item + block entity + menu), the module-owned sounds
 * ({@code slots.*}) and cabinet particles, the {@code /casino jackpot} admin commands and the config hooks (machine
 * cache, {@code slots.validateRtp}). Other modules use {@link dev.nezo.burmaldaholic.games.slots.api.SlotsApi} only.
 */
public final class SlotsModule implements CasinoModule {
	public static final String ID = "slots";

	/** Machine per tier. */
	public static final Map<Tier, TableType<SlotMachineBlockEntity>> MACHINES = new EnumMap<>(Tier.class);

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		dev.nezo.burmaldaholic.games.slots.pvp.SlotsPvp.register(); // Slot Showdown (docs/architecture/pvp-bots.md)
		CasinoSounds.registerOwned(ctx, ID); // slots.* sound events (SLOTS.md §10.7)
		for (Tier tier : Tier.values()) {
			BlockBehaviour.Properties props = TableRegistrar.defaultProperties();
			switch (tier) {
				case COPPER -> props.mapColor(MapColor.COLOR_ORANGE).strength(3.0f, 6.0f).sound(SoundType.COPPER);
				case GOLD -> props.mapColor(MapColor.GOLD).strength(3.0f, 6.0f).sound(SoundType.METAL);
				case NETHERITE -> props.mapColor(MapColor.COLOR_BLACK).strength(5.0f, 1200.0f).sound(SoundType.NETHERITE_BLOCK);
			}
			TableType<SlotMachineBlockEntity> type = ctx.tables().register(tier.blockName(),
				(t, pos, state) -> new SlotMachineBlockEntity(t, pos, state, tier), props);
			MACHINES.put(tier, type);
		}
		SlotsFx.register(ctx); // lane J-L10: cabinet particle types
		ConfigManager.get().addListener(() -> {
			SlotMachinesV2.invalidate();
			// review J-L8: a runtime change (e.g. the buy price on the admin page) is re-validated at once, otherwise an
			// over-paying table or buy would go unflagged until the next restart
			if (CasinoConfig.slots().validateRtp) {
				SlotMachinesV2.validateAsync();
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SlotMachinesV2.invalidate();
			if (CasinoConfig.slots().validateRtp) {
				SlotMachinesV2.validateAsync(); // exact enumeration off the server thread (defaults: known numbers, instant)
			}
		});
		CasinoCommands.extend(root -> root.then(Commands.literal("jackpot")
			.executes(c -> showPools(c.getSource()))
			.then(Commands.literal("reset")
				.executes(c -> reset(c.getSource(), null))
				.then(Commands.literal("overworld").executes(c -> reset(c.getSource(), Machine.OVERWORLD)))
				.then(Commands.literal("nether").executes(c -> reset(c.getSource(), Machine.NETHER)))
				.then(Commands.literal("end").executes(c -> reset(c.getSource(), Machine.END))))
			.then(Commands.literal("rtp").executes(c -> rtp(c.getSource())))));
	}

	private static int showPools(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		JackpotPoolsV2 pools = JackpotPoolsV2.get(server);
		for (Machine m : Machine.values()) {
			long grand = pools.meter(m, SlotMachinesV2.def(m), 4);
			source.sendSuccess(() -> Component.translatable("msg.burmaldaholic.slots.jackpot_pool",
				Component.translatable("gui.burmaldaholic.slots.machine." + m.id), Texts.chips(grand)), false);
		}
		return 1;
	}

	/** Resets one machine's (or, with null, every machine's) progressive pools to their seeds. */
	private static int reset(CommandSourceStack source, Machine only) {
		JackpotPoolsV2 pools = JackpotPoolsV2.get(source.getServer());
		for (Machine m : Machine.values()) {
			if (only == null || only == m) {
				pools.reset(m);
			}
		}
		source.sendSuccess(() -> Component.translatable("gui.burmaldaholic.menu.admin.done",
			Component.translatable("gui.burmaldaholic.menu.admin.reset_jackpots")), true);
		return 1;
	}

	private static int rtp(CommandSourceStack source) {
		SlotMachinesV2.invalidate();
		boolean defaults = true;
		for (Machine m : Machine.values()) {
			defaults &= SlotMachinesV2.isDefault(m);
		}
		if (!defaults) {
			SlotMachinesV2.validateAsync(); // enumeration takes seconds: results go to the log and the admin page
			return 1;
		}
		source.sendSuccess(() -> Component.translatable("msg.burmaldaholic.slots.rtp_ok"), false);
		return 1;
	}
}
