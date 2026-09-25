package dev.nezo.burmaldaholic.games.roulette;

import dev.nezo.burmaldaholic.core.module.CasinoModule;
import dev.nezo.burmaldaholic.core.module.ModuleContext;
import dev.nezo.burmaldaholic.core.table.TableType;
import net.minecraft.sounds.SoundEvent;

/**
 * European roulette (GAME_DESIGN.md §9, UI.md §7): a normal table and a High-Roller table sharing one
 * block entity class. Rules live in {@code logic/} (pure, unit-tested); {@link RouletteTableBlockEntity}
 * runs the shared multiplayer spin and the money; the client screen is {@code client.RouletteScreen}.
 */
public final class RouletteModule implements CasinoModule {
	public static final String ID = "roulette";
	public static final String TABLE_NAME = "roulette_table";
	public static final String HIGH_ROLLER_NAME = "roulette_table_high_roller";

	public static TableType<RouletteTableBlockEntity> TABLE;
	public static TableType<RouletteTableBlockEntity> HIGH_ROLLER_TABLE;
	public static SoundEvent SPIN_SOUND;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public void register(ModuleContext ctx) {
		TABLE = ctx.tables().register(TABLE_NAME, RouletteTableBlockEntity::new);
		HIGH_ROLLER_TABLE = ctx.tables().register(HIGH_ROLLER_NAME, RouletteTableBlockEntity::new);
		SPIN_SOUND = ctx.registry().sound("roulette_spin");
		dev.nezo.burmaldaholic.core.sound.CasinoSounds.registerOwned(ctx, ID); // ball, bell, dolly (tables.md §0.8)
	}
}
