package dev.nezo.burmaldaholic.client.dealer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The shared dealer model (animation/cards.md §1.4, task J-C11): the vanilla player model whose arms rest "on the
 * table" and follow the {@link DealerMotion} pose of the table's current beat (deal, flip, peek, pay, sweep, shuffle,
 * wave-off). The head keeps the vanilla look-at and adds the gesture's nod.
 */
public class DealerModel extends HumanoidModel<DealerRenderState> {
	public DealerModel(ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(DealerRenderState state) {
		super.setupAnim(state);
		DealerMotion.Pose p = state.pose;
		rightArm.xRot = p.rightX();
		rightArm.yRot = p.rightY();
		rightArm.zRot = p.rightZ();
		leftArm.xRot = p.leftX();
		leftArm.yRot = p.leftY();
		leftArm.zRot = p.leftZ();
		if (state.gesturing) head.xRot += p.headX();
	}
}
