package dev.nezo.burmaldaholic.games.slots.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.nezo.burmaldaholic.client.module.CasinoClientModule;
import dev.nezo.burmaldaholic.client.module.ClientModuleContext;
import dev.nezo.burmaldaholic.core.table.TableType;
import dev.nezo.burmaldaholic.games.slots.SlotMachineBlockEntity;
import dev.nezo.burmaldaholic.games.slots.SlotsModule;
import dev.nezo.burmaldaholic.games.slots.client.reels.SymbolSheet;
import dev.nezo.burmaldaholic.games.slots.logic.Tier;
import dev.nezo.burmaldaholic.games.slots.v2.logic.Machine;
import dev.nezo.burmaldaholic.games.slots.v2.present.preview.PreviewTapes;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Client half of the "slots" module: the machine screen, item tooltips and the v2 preview gallery
 * ({@code /casino_slots_preview [machine] [tape]}: plays the fixed preview tapes on the v2 screen; no money, no server).
 * The v2 machine screen ({@link SlotMachineV2Screen}) replaces {@link SlotMachineScreen} in the slots cut-over (S-J5).
 */
public final class SlotsClientModule implements CasinoClientModule {
	private static Screen pending;

	@Override
	public String id() {
		return "slots";
	}

	@Override
	public void registerClient(ClientModuleContext ctx) {
		for (TableType<SlotMachineBlockEntity> type : SlotsModule.MACHINES.values()) {
			ctx.tableScreen(type, SlotMachineScreen::new);
		}
		SlotCabinetRenderer.register(); // lane J-L10: in-world cabinet reels (draws only once a v2 sync exists)
		SlotsParticles.register();
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			for (Tier tier : Tier.values()) {
				TableType<SlotMachineBlockEntity> type = SlotsModule.MACHINES.get(tier);
				if (type != null && stack.is(type.item())) {
					lines.add(Component.translatable("tooltip.burmaldaholic." + tier.blockName()).withStyle(ChatFormatting.GRAY));
				}
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(ClientCommands.literal("casino_slots_preview")
			.executes(c -> open(Machine.OVERWORLD, null))
			.then(ClientCommands.argument("machine", StringArgumentType.word())
				.suggests((c, b) -> {
					for (Machine m : Machine.values()) b.suggest(m.id);
					return b.buildFuture();
				})
				.executes(c -> open(machine(StringArgumentType.getString(c, "machine")), null))
				.then(ClientCommands.argument("tape", StringArgumentType.word())
					.suggests((c, b) -> {
						for (String n : PreviewTapes.NAMES) b.suggest(n);
						return b.buildFuture();
					})
					.executes(c -> open(machine(StringArgumentType.getString(c, "machine")), StringArgumentType.getString(c, "tape")))))));
		// the chat screen closes after a command: open the preview on the next free tick
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (pending != null && mc.gui.screen() == null) {
				mc.gui.setScreen(pending);
				pending = null;
			}
		});
	}

	private static Machine machine(String id) {
		for (Machine m : Machine.values()) if (m.id.equals(id)) return m;
		return Machine.OVERWORLD;
	}

	private static int open(Machine m, String tape) {
		SymbolSheet.invalidate();
		pending = new SlotPreviewScreen(m, tape != null && PreviewTapes.NAMES.contains(tape) ? tape : null);
		return 1;
	}
}
