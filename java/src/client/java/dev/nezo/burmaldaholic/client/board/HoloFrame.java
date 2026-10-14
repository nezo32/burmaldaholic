package dev.nezo.burmaldaholic.client.board;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The camera plane of one table for one frame (ui-v2.md §7.4): the camera's right / up vectors, the table rotation and
 * the block-space transform of table-local points. Plates and the board are positioned in BLOCK space (the pose of
 * {@code submit} is at the block origin, unrotated), then billboarded.
 */
final class HoloFrame {
	final Vector3f right = new Vector3f(), up = new Vector3f(), fwd = new Vector3f();
	/** Camera position relative to the block origin. */
	final Vector3f cam = new Vector3f();
	/** Table centre at the felt (block space). */
	final Vector3f centre = new Vector3f();
	final Quaternionf facing = new Quaternionf();
	final Quaternionf orientation = new Quaternionf();
	/** Yaw-only camera rotation (the board never swims with the pitch). */
	final Quaternionf yawOnly = new Quaternionf();
	private final Vector3f tmp = new Vector3f();
	/** Content scale around the centre (the fallback felt). */
	private float k = 1f;

	void set(CameraRenderState camera, BlockPos pos, Direction dir, float centreY, float contentScale) {
		k = contentScale;
		orientation.set(camera.orientation);
		right.set(1, 0, 0).rotate(orientation);
		up.set(0, 1, 0).rotate(orientation);
		fwd.set(0, 0, -1).rotate(orientation);
		cam.set((float) (camera.pos.x - pos.getX()), (float) (camera.pos.y - pos.getY()), (float) (camera.pos.z - pos.getZ()));
		facing.rotationY((float) Math.toRadians(-dir.toYRot()));
		yawOnly.rotationY((float) (Math.PI - Math.toRadians(camera.yRot)));
		toBlock(0.5f, centreY, 0.5f, centre);
	}

	/** Table-local (x, y, z) → block space. */
	Vector3f toBlock(float x, float y, float z, Vector3f out) {
		out.set((x - 0.5f) * k, y, (z - 0.5f) * k).rotate(facing);
		out.x += 0.5f;
		out.z += 0.5f;
		return out;
	}

	/** Plane u of a block-space point. */
	float u(Vector3f p) {
		tmp.set(p).sub(centre);
		return tmp.dot(right);
	}

	/** Plane v of a block-space point. */
	float v(Vector3f p) {
		tmp.set(p).sub(centre);
		return tmp.dot(up);
	}

	/** The block-space point at plane coordinates (u, v). */
	Vector3f onPlane(float u, float v, Vector3f out) {
		return out.set(centre).add(right.x * u + up.x * v, right.y * u + up.y * v, right.z * u + up.z * v);
	}

	/** Distance camera → block-space point. */
	float dist(Vector3f p) {
		return tmp.set(p).sub(cam).length();
	}
}
