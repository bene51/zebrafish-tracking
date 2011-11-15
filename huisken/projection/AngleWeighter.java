package huisken.projection;

import javax.vecmath.Point3f;

public class AngleWeighter implements FusionWeight {

	public final int angleInc;
	private final Point3f center;

	public AngleWeighter(int nAngles, Point3f center) {
		this.angleInc = 360 / nAngles;
		this.center = center;
	}


	/*
	 *          Camera
	 *            |
	 *            |
	 *
	 *            x
	 *         _________
	 *      y /        /|
	 *       /        / |
	 *      /________/  |
	 *      |        |  /
	 *    z |        | /
	 *      |________|/
	 */
	public static final double overlap = 10;
	@Override
	public float getWeight(float px, float py, float pz) {

		double dz = pz - center.z;
		double dx = px - center.x;

		double angle = 180.0 * Math.atan2(-dx, -dz) / Math.PI;

		// inside
		if(angle > -angleInc/2.0 + overlap/2.0 && angle < angleInc/2.0 - overlap/2.0)
			return 1f;

		// outside
		if(angle < -angleInc/2.0 - overlap/2.0 || angle > angleInc/2.0 + overlap/2.0)
			return 0f;


		// within the overlap
		double exp = 10 / overlap;
		if(angle < 0) {
			double t = angle + angleInc/2.0;
			return (float)(1.0 / (1 + Math.exp(-exp * t)));
		} else {
			double t = angle - angleInc/2.0;
			return 1f - (float)(1.0 / (1 + Math.exp(-exp * t)));
		}
	}
}
