package dev.nezo.burmaldaholic.client.dealer;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Humanoid render state + the dealer's gesture pose of this frame (task J-C11). */
public class DealerRenderState extends HumanoidRenderState {
	public DealerMotion.Pose pose = DealerMotion.Pose.REST;
	/** A gesture is playing (otherwise the arms rest with the breathing sway). */
	public boolean gesturing;
}
