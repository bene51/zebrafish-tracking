package huisken.projection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;

import meshtools.IndexedTriangleMesh;

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
			Point3f center, float radius,
			Matrix4f[] transforms) {

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
					w, h, d, pw, ph, pd,
					transforms);
		} catch(Exception e) {
			throw new RuntimeException("Cannot load transformations.", e);
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

	public String getOutputDirectory() {
		return outputdir;
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
	 * the first angle is not transformed, all following angles are transformed;
	 * example:
	 * 	2 samples,
	 * 	2 angles each
	 *
	 * called twice with 2 transforms:
	 * 	- [I], [a1->a0]
	 */
	private SphericalMaxProjection[][] initSphericalMaximumProjection(
			Point3f center, float radius,
			int camera,
			int angleInc, int nAngles,
			int w, int h, int d,
			double pw, double ph, double pd,
			Matrix4f[] transforms) throws IOException {

		int subd = (int)Math.round(radius / (Math.min(pw, Math.min(ph, pd))));
		// subd /= 4;

		IndexedTriangleMesh sphere = createSphere(center, radius, subd);

		SphericalMaxProjection[][] smp = new SphericalMaxProjection[nAngles][2];
		int aperture = 90 / nAngles;
		int angle = camera == CAMERA1 ? 135 : 45;

		if(nAngles > 1)
			writeTransformations(new File(outputdir, "transformations").getAbsolutePath(), transforms);

		for(int a = 0; a < nAngles; a++) {
			Matrix4f transform = null;
			if(a > 0)
				transform = transforms[a];
			Point3f cen = new Point3f(center);
			if(a > 0)
				transform.transform(cen);

			// left illumination
			smp[a][LEFT] = new SphericalMaxProjection(sphere, center, radius, transform);
			smp[a][LEFT].prepareForProjection(w, h, d, pw, ph, pd, new AngleWeighter2(AngleWeighter2.X_AXIS, angle, aperture, cen));

			// right illumination
			smp[a][RIGHT] = new SphericalMaxProjection(sphere, center, radius, transform);
			smp[a][RIGHT].prepareForProjection(w, h, d, pw, ph, pd, new AngleWeighter2(AngleWeighter2.X_AXIS, -angle, aperture, cen));

		}
		return smp;
	}

//	private static Matrix4f[] readTransforms(int nAngles) throws IOException {
//		GenericDialogPlus gd = new GenericDialogPlus("Transformations");
//		gd.addFileField("Positions", "");
//		gd.showDialog();
//		if(gd.wasCanceled())
//			return null;
//
//		ArrayList<Point4f> positions = Stage_Calibration.readPositions(gd.getNextString());
//		if(nAngles != positions.size())
//			throw new IllegalArgumentException();
//
//		Point4f refpos = positions.get(0);
//		Matrix4f[] ret = new Matrix4f[nAngles];
//		for(int i = 1; i < nAngles; i++) {
//			ret[i] = Stage_Calibration.getRegistration(refpos, positions.get(i));
//			ret[i].invert();
//		}
//
//		return ret;
//	}

	public static Matrix4f[] loadTransformations(String file) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(file));
		String line = null;
		ArrayList<Matrix4f> matrices = new ArrayList<Matrix4f>();
		while((line = in.readLine()) != null) {
			if(line.equals("null")) {
				matrices.add(null);
				continue;
			}
			String[] toks = line.split(" ");
			float[] matrix = new float[16];
			for(int i = 0; i < 16; i++)
				matrix[i] = Float.parseFloat(toks[i]);
			matrices.add(new Matrix4f(matrix));
		}
		in.close();
		Matrix4f[] ret = new Matrix4f[matrices.size()];
		matrices.toArray(ret);
		return ret;
	}

	public static void writeTransformations(String file, Matrix4f[] matrices) throws IOException {
		PrintWriter out = new PrintWriter(new FileWriter(file));
		for(Matrix4f m : matrices) {
			if(m == null) {
				out.println("null");
				continue;
			}
			for(int r = 0; r < 4; r++) {
				for(int c = 0; c < 4; c++) {
					out.print(m.getElement(r,  c) + " ");
				}
			}
			out.println();
		}
		out.close();
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

