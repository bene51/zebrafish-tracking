package huisken.projection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.vecmath.Point3f;

import meshtools.IndexedTriangleMesh;
import vib.FastMatrix;
import fiji.util.gui.GenericDialogPlus;

public class TwoCameraSphericalMaxProjection {

	private final String outputdir;
	private final int timepointStart, timepointInc, nTimepoints;
	private final int angleStart, angleInc, nAngles;
	private final int nPlanes;

	private final SphericalMaxProjection[][] smp;
	private Iterator iterator;

	public static final int LEFT  = Opener.LEFT;
	public static final int RIGHT = Opener.RIGHT;
	public static final int CAMERA1 = 0;
	public static final int CAMERA2 = 1;

	public TwoCameraSphericalMaxProjection(String outputdir,
			int timepointStart, int timepointInc, int nTimepoints,
			int camera,
			int angleInc, int nAngles,
			int w, int h, int d,
			double pw, double ph, double pd,
			Point3f center, float radius) {

		if(!outputdir.endsWith(File.separator))
			outputdir += File.separator;

		this.outputdir = outputdir;
		this.timepointStart = timepointStart;
		this.timepointInc = timepointInc;
		this.nTimepoints = nTimepoints;
		this.angleStart = camera == CAMERA1 ? 180 : 0;
		this.angleInc = angleInc;
		this.nAngles = nAngles;
		this.nPlanes = d;

		try {
			// initialize the maximum projections
			smp = initSphericalMaximumProjection(
					center, radius,
					camera,
					angleInc, nAngles,
					w, h, d, pw, ph, pd);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load transformations.");
		}

		// save the sphere geometry
		String spherepath = new File(outputdir, "Sphere.obj").getAbsolutePath();
		try {
			smp[0][0].saveSphere(spherepath);
		} catch(Exception e) {
			throw new RuntimeException("Cannot save sphere: " + spherepath, e);
		}

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

	public void process(short[] ip) {
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
			smp[a][ill].resetMaxima();

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

	/**
	 * @param transform Array with one transformation for each angle;
	 */
	private SphericalMaxProjection[][] initSphericalMaximumProjection(
			Point3f center, float radius,
			int camera,
			int angleInc, int nAngles,
			int w, int h, int d,
			double pw, double ph, double pd) throws IOException {

		int subd = (int)Math.round(radius / (Math.min(pw, Math.min(ph, pd))));
		// subd /= 4;

		IndexedTriangleMesh sphere = createSphere(center, radius, subd);

		SphericalMaxProjection[][] smp = new SphericalMaxProjection[nAngles][2];
		int aperture = 90 / nAngles;
		int angle = camera == CAMERA1 ? 135 : 45;

		FastMatrix[] transforms = null;
		if(nAngles > 1)
			transforms = readTransforms(nAngles);

		// TODO problem: sphere is not the right sphere for each angle.
		// need to transform it to get it right.
		for(int a = 0; a < nAngles; a++) {
			FastMatrix transform = a == 0 ? null : transforms[a - 1].inverse();
			// left illumination
			smp[a][LEFT] = new SphericalMaxProjection(sphere, center, radius, transform);
			smp[a][LEFT].prepareForProjection(w, h, d, pw, ph, pd, new AngleWeighter2(AngleWeighter2.X_AXIS, false, angle, aperture, center));

			// right illumination
			smp[a][RIGHT] = new SphericalMaxProjection(sphere, center, radius, transform);
			smp[a][RIGHT].prepareForProjection(w, h, d, pw, ph, pd, new AngleWeighter2(AngleWeighter2.X_AXIS, false, -angle, aperture, center));

		}
		return smp;
	}

	private static FastMatrix[] readTransforms(int nAngles) throws IOException {
		GenericDialogPlus gd = new GenericDialogPlus("Transformations");
		gd.addMessage("You are using more than 1 angle. \n" +
				"Please specify the transformations for the other angles");
		for(int i = 1; i < nAngles; i++)
			gd.addFileField("Angle_" + i, "");
		if(gd.wasCanceled())
			return null;
		FastMatrix[] ts = new FastMatrix[nAngles - 1];
		for(int i = 1; i < nAngles; i++)
			ts[i - 1] = readTransformation(gd.getNextString());
		return ts;
	}

	private static FastMatrix readTransformation(String file) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(file));
		double[][] mat = new double[3][4];
		for(int r = 0; r < 3; r++) {
			for(int c = 0; c < 4; c++) {
				String[] toks = in.readLine().split(": ");
				mat[r][c] = Double.parseDouble(toks[1]);
			}
		}
		in.close();
		return new FastMatrix(mat);
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

