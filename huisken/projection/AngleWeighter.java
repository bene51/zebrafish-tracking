package huisken.projection;

import javax.vecmath.Point3f;

public class AngleWeighter {

	public final int nAngles;
	public final int angleInc;

	public AngleWeighter(int nAngles) {
		this.nAngles = nAngles;
		this.angleInc = 360 / nAngles;
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
	public float getWeight(Point3f p, Point3f center) {

		double dz = p.z - center.z;
		double dx = p.x - center.x;

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
