package huisken.projection;

import ij.process.ImageProcessor;

import java.io.File;

import javax.vecmath.Point3f;

import meshtools.IndexedTriangleMesh;
import vib.FastMatrix;

public class MultiViewSphericalMaxProjection {

	private final String outputdir;
	private final int timepointStart, timepointInc, nTimepoints;
	private final int angleStart, angleInc, nAngles;
	private final int nPlanes;

	private final SphericalMaxProjection[][] smp;
	private final AngleWeighter aw;
	private Iterator iterator;

	public static final int LEFT  = Opener.LEFT;
	public static final int RIGHT = Opener.RIGHT;

	public MultiViewSphericalMaxProjection(String outputdir,
			int timepointStart, int timepointInc, int nTimepoints,
			int angleStart, int angleInc, int nAngles,
			int w, int h, int d,
			double pw, double ph, double pd,
			Point3f[] centers, float radius) {

		if(!outputdir.endsWith(File.separator))
			outputdir += File.separator;

		this.outputdir = outputdir;
		this.timepointStart = timepointStart;
		this.timepointInc = timepointInc;
		this.nTimepoints = nTimepoints;
		this.angleStart = angleStart;
		this.angleInc = angleInc;
		this.nAngles = nAngles;
		this.nPlanes = d;


		// calculate sphere transformations for each angle
		FastMatrix[] transforms = new FastMatrix[nAngles];
		for(int a = 0; a < nAngles; a++) {
			double angle = angleStart + angleInc * a;
			angle = angle * Math.PI / 180.0;
			transforms[a] = FastMatrix.translate(
						centers[a].x - centers[0].x,
						centers[a].y - centers[0].y,
						centers[a].z - centers[0].z)
					.times(rotateY(-angle, centers[0]));
		}


		// initialize the maximum projections
		smp = initSphericalMaximumProjection(
				transforms,
				centers[0], radius,
				angleStart, angleInc, nAngles,
				w, h, d, pw, ph, pd);

		// save the sphere geometry
		String spherepath = new File(outputdir, "Sphere.obj").getAbsolutePath();
		try {
			smp[0][0].saveSphere(spherepath);
		} catch(Exception e) {
			throw new RuntimeException("Cannot save sphere: " + spherepath, e);
		}

		aw = new AngleWeighter(nAngles);
		iterator = new Iterator();
	}

	class Iterator implements java.util.Iterator<Iterator> {
		public int timepoint, angle, angleIndex, plane, illumination;

		public Iterator() {
			reset();
		}

		public void reset() {
			angleIndex = 0;
			angle = angleStart;
			timepoint = timepointStart;
			plane = -1;
			illumination = RIGHT;
		}

		@Override
		public boolean hasNext() {
			return  illumination == LEFT ||
				(plane + 1) < nPlanes ||
				(angle + angleInc) < (angleStart + angleInc * nAngles) ||
				(timepoint + timepointInc) < (timepointStart + timepointInc * nTimepoints);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Iterator next() {
			illumination++;
			if(illumination > RIGHT) {
				illumination = LEFT;
				plane++;
				if(plane >= nPlanes) {
					plane = 0;
					angle += angleInc;
					angleIndex++;
					if(angle >= (angleStart + angleInc * nAngles)) {
						angleIndex = 0;
						angle = angleStart;
						timepoint += timepointInc;
						if(timepoint >= (timepointStart + timepointInc * nTimepoints))
							return null;
					}
				}
			}
			return this;
		}
	}

//	public void process(ImageProcessor left, ImageProcessor right) {
//		iterator = iterator.next();
//		if(iterator == null)
//			throw new RuntimeException("Finished");
//
//		int a = iterator.angleIndex;
//		int tp = iterator.timepoint;
//		int p = iterator.plane;
//
//		// Start of stack
//		if(p == 0) {
//			smp[a][LEFT].startProjectStack();
//			smp[a][RIGHT].startProjectStack();
//		}
//
//		// do the projection
//		smp[a][LEFT].projectPlane(p, left);
//		smp[a][RIGHT].projectPlane(p, right);
//
//
//		// Not end of stack: nothing else to do
//		if(p < nPlanes - 1)
//			return;
//
//		// End of stack, further process the projections
//		smp[a][LEFT].finishProjectStack();
//		smp[a][RIGHT].finishProjectStack();
//
//		// sum up left and right illumination
//		smp[a][LEFT].addMaxima(smp[a][RIGHT].getMaxima());
//
//		// scale the resulting maxima according to angle
//		smp[a][0].scaleMaxima(aw);
//
//		// sum them all up
//		if(a > 0) {
//			smp[0][0].addMaxima(smp[a][0].getMaxima());
//		}
//
//		// if it's the last angle, save the result
//		if(a == nAngles - 1) {
//			String filename = String.format("tp%04d.tif", tp);
//			String vpath = new File(outputdir, filename + ".vertices").getAbsolutePath();
//			try {
//				smp[0][0].saveMaxima(vpath);
//			} catch(Exception e) {
//				throw new RuntimeException("Cannot save " + vpath);
//			}
//		}
//	}

	public void process(ImageProcessor ip) {
		iterator = iterator.next();
		if(iterator == null)
			throw new RuntimeException("Finished");

		int a = iterator.angleIndex;
		int angle = iterator.angle;
		int tp = iterator.timepoint;
		int p = iterator.plane;
		int ill = iterator.illumination;

		// Start of stack
		if(p == 0)
			smp[a][ill].startProjectStack();

		// do the projection
		smp[a][ill].projectPlane(p, ip);

		// Not end of stack: nothing else to do
		if(p < nPlanes - 1)
			return;

		// save the result
		String filename = String.format("tp%04d_a%04d_ill%d.vertices", tp, angle, ill);
		String vpath = new File(outputdir, filename).getAbsolutePath();
		try {
			smp[a][ill].saveMaxima(vpath);
		} catch(Exception e) {
			throw new RuntimeException("Cannot save " + vpath);
		}
	}


	private static FastMatrix rotateY(double rad, Point3f center) {
		FastMatrix rot = FastMatrix.rotate(rad, 1);
		rot.apply(center.x, center.y, center.z);
		return FastMatrix.translate(center.x - rot.x, center.y - rot.y, center.z - rot.z).times(rot);
	}

	/**
	 * @param transform Array with one transformation for each angle;
	 */
	private SphericalMaxProjection[][] initSphericalMaximumProjection(
			FastMatrix[] transform,
			Point3f center, float radius,
			int angleStart, int angleInc, int nAngles,
			int w, int h, int d,
			double pw, double ph, double pd) {

		if(transform.length != nAngles)
			throw new IllegalArgumentException("Need one transformation for each angle");

		int subd = (int)Math.round(radius / (Math.min(pw, Math.min(ph, pd))));
		subd /= 4;

		IndexedTriangleMesh sphere = createSphere(center, radius, subd);

		SphericalMaxProjection[][] smp = new SphericalMaxProjection[nAngles][2];

		// all other angles
		for(int a = 0; a < nAngles; a++) {
			// left illumination
			smp[a][LEFT] = new SphericalMaxProjection(sphere, center, radius, transform[a]);
			smp[a][LEFT].prepareForProjection(w, h, d, pw, ph, pd, new SimpleLeftWeighter(center.x));

			// right illumination
			smp[a][RIGHT] = new SphericalMaxProjection(sphere, center, radius, transform[a]);
			smp[a][RIGHT].prepareForProjection(w, h, d, pw, ph, pd, new SimpleRightWeighter(center.x));
		}

		return smp;
	}

	private static IndexedTriangleMesh createSphere(Point3f center, float radius, int subd) {
		// calculate the sphere coordinates
		Icosahedron icosa = new Icosahedron(radius);

		IndexedTriangleMesh sphere = icosa.createBuckyball(radius, subd);
		for(Point3f p : sphere.getVertices())
			p.add(center);
		return sphere;
	}
}
