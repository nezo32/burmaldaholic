package dev.nezo.burmaldaholic.games.extras.client;

import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Client GameTest access to the Plinko and Wheel screens' poses (lane SYW, NetLag acceptance); null for other screens. */
public final class ExtrasSyncProbe {
	private ExtrasSyncProbe() {}

	/** {@link PlinkoScreen#poseProbe}. */
	public static double @Nullable [] plinko(@Nullable Screen s) {
		return s instanceof PlinkoScreen p ? p.poseProbe() : null;
	}

	/** {@link WheelScreen#poseProbe}. */
	public static double @Nullable [] wheel(@Nullable Screen s) {
		return s instanceof WheelScreen w ? w.poseProbe() : null;
	}

	/** {@link WheelScreen#angleAtProbe}. */
	public static double wheelAngleAt(Screen s, double ms) {
		return ((WheelScreen) s).angleAtProbe(ms);
	}

	/** {@link WheelScreen#endAngleProbe}. */
	public static double wheelEnd(Screen s) {
		return ((WheelScreen) s).endAngleProbe();
	}
}
