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
	/** The design → footprint mapping of this table ({@link BoardState#contentScale}, origin). */
	private float k = 1f, ox = 0.5f, oy = dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.TOP, oz = 0.5f;

	void set(CameraRenderState camera, BlockPos pos, Direction dir, BoardState s) {
		k = s.contentScale;
		ox = s.originX;
		oy = s.originY;
		oz = s.originZ;
		orientation.set(camera.orientation);
		right.set(1, 0, 0).rotate(orientation);
		up.set(0, 1, 0).rotate(orientation);
		fwd.set(0, 0, -1).rotate(orientation);
		cam.set((float) (camera.pos.x - pos.getX()), (float) (camera.pos.y - pos.getY()), (float) (camera.pos.z - pos.getZ()));
		facing.rotationY((float) Math.toRadians(-dir.toYRot()));
		yawOnly.rotationY((float) (Math.PI - Math.toRadians(camera.yRot)));
		footToBlock(ox, oy, oz, centre);
		tmp.set(centre).sub(cam);
		depth0 = Math.max(0.05f, tmp.dot(fwd));
		cu0 = tmp.dot(right);
		cv0 = tmp.dot(up);
	}

	/** Camera-space depth / right / up of the plane origin (the felt centre). */
	float depth0 = 1, cu0, cv0;
	/** Projection results of {@link #project}: plane u, v, the scale a size at that point gets in the plane, its depth. */
	float pu, pv, ps, pz;

	/**
	 * Projects a block-space point into the camera plane through the felt centre ALONG THE VIEW RAY (perspective), so a
	 * point behind or in front of the plane lands where the screen shows it relative to the plates (which live in the
	 * plane). Orthographic {@link #u}/{@link #v} put far content (the dealer row) too low under a raised or tilted camera,
	 * so plates covered it (design audit W P1). Results in {@link #pu}, {@link #pv}, {@link #ps}.
	 */
	void project(Vector3f p) {
		tmp.set(p).sub(cam);
		float z = tmp.dot(fwd);
		pz = z;
		if (z < 0.05f) {
			pu = u(p);
			pv = v(p);
			ps = 1;
			return;
		}
		float s = depth0 / z;
		pu = tmp.dot(right) * s - cu0;
		pv = tmp.dot(up) * s - cv0;
		ps = s;
	}

	/** Screen x / y (camera tangents) of plane coordinates: comparable across tables. */
	float sx(float u) {
		return (cu0 + u) / depth0;
	}

	float sy(float v) {
		return (cv0 + v) / depth0;
	}

	/** Screen x / y (camera tangents) of a block-space point; NaN behind the camera. */
	float screenX(Vector3f p) {
		tmp.set(p).sub(cam);
		float z = tmp.dot(fwd);
		return z < 0.05f ? Float.NaN : tmp.dot(right) / z;
	}

	float screenY(Vector3f p) {
		tmp.set(p).sub(cam);
		float z = tmp.dot(fwd);
		return z < 0.05f ? Float.NaN : tmp.dot(up) / z;
	}

	/** Design (x, y, z) → block space (the design → footprint mapping, then FACING about the main block centre). */
	Vector3f toBlock(float x, float y, float z, Vector3f out) {
		return footToBlock(ox + (x - 0.5f) * k, oy + (y - dev.nezo.burmaldaholic.core.ui.v2.board.BoardGeometry.TOP) * k, oz + (z - 0.5f) * k, out);
	}

	/** Footprint-local (x, y, z) → block space. */
	Vector3f footToBlock(float x, float y, float z, Vector3f out) {
		out.set(x - 0.5f, y, z - 0.5f).rotate(facing);
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
