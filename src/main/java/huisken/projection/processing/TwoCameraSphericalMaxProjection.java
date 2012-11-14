package huisken.projection.processing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;

/**
 * This class assumes we have two cameras and two angles
 * @author bschmid
 *
 */
public class TwoCameraSphericalMaxProjection {

	private final String outputdir;
	private final int nAngles, angleInc;
	private final int nPlanes;
	private final int nLayers;
	private final int camera;

	private final SphericalMaxProjection[][] smp;
	private final short[][][][] maxima;


	public static final int LEFT  = 0;
	public static final int RIGHT = 1;
	public static final int CAMERA1 = 0;
	public static final int CAMERA2 = 1;

	/**
	 *
	 * @param outputdir Output directory.
	 * @param camera Left or right camera.
	 * @param angleInc Angle increment.
	 * @param nAngles Number of angles.
	 * @param w Width of images taken.
	 * @param h Height of images taken.
	 * @param d Stack size.
	 * @param pw Pixel calibration.
	 * @param ph Pixel calibration.
	 * @param pd Pixel calibration.
	 * @param center Estimated center of the sphere.
	 * @param radius Estimated radius of the sphere
	 * @param transforms List of transformations, each specifying the forward transformation from angle[0] to angle[i].
	 */
	public TwoCameraSphericalMaxProjection(String outputdir,
			int camera,
			int angleInc, int nAngles,
			int w, int h, int d,
			double pw, double ph, double pd,
			Point3f center, float radius,
			double layerWidth,
			int nLayers,
			Matrix4f[] transforms) {

		if(!outputdir.endsWith(File.separator))
			outputdir += File.separator;

		this.outputdir = outputdir;
		this.angleInc = angleInc;
		this.nAngles = nAngles;
		this.nPlanes = d;
		this.camera = camera;
		this.nLayers = nLayers;

		try {
			// initialize the maximum projections
			this.smp = initSphericalMaximumProjection(
					center, radius,
					camera,
					w, h, d, pw, ph, pd,
					layerWidth, nLayers,
					transforms);
			this.maxima = new short[nAngles][2][nLayers][smp[0][0].getSphere().nVertices];
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
	}

	/**
	 * Returns the output directory.
	 * @return
	 */
	public String getOutputDirectory() {
		return outputdir;
	}

	/**
	 * Process the next image, given in form of a short[] array
	 * @param ip image as short[] array.
	 */
	public void process(short[] ip, int tp, int aIndex, int z, int ill) {

		// Start of stack
		if(z == 0)
			for(int s = 0; s < nLayers; s++)
				smp[aIndex][ill].resetMaxima(maxima[aIndex][ill][s]);

		// do the projection
		smp[aIndex][ill].projectPlaneMultilayer(z, ip, maxima[aIndex][ill]);

		// Not end of stack: nothing else to do
		if(z < nPlanes - 1)
			return;

		// save the result
		for(int l = 0; l < nLayers; l++) {
			String filename = getFileName(tp, aIndex, angleInc, camera, ill, l);
			String vpath = new File(outputdir, filename).getAbsolutePath();
			try {
				smp[aIndex][ill].saveMaxima(vpath, maxima[aIndex][ill][l]);
			} catch(Exception e) {
				throw new RuntimeException("Cannot save " + vpath);
			}
		}
	}

	public static String getFileName(int tp, int aIndex, int angleInc, int camera, int ill, int layer) {
		int angle = getAngle(aIndex, angleInc, camera, ill);

		return String.format("tp%04d_a%04d_l%02d.vertices", tp, angle, layer);
	}

	/**
	 * Calculates the angle for a combination of angle index,
	 * camera and illumination.
	 */
	public static int getAngle(int aIndex, int angleInc, int camera, int ill) {
		int angle =  camera == CAMERA1 ? 135 : 45;
		if(ill == RIGHT)
			angle = 360 - angle;

		angle += aIndex * angleInc;
		return angle;
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
			int w, int h, int d,
			double pw, double ph, double pd,
			double layerWidth, int nLayers,
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
			smp[a][LEFT].prepareForProjectionNew(w, h, d, pw, ph, pd, layerWidth, nLayers, new AngleWeighter2(AngleWeighter2.X_AXIS, angle, aperture, cen));

			// right illumination
			smp[a][RIGHT] = new SphericalMaxProjection(sphere, center, radius, transform);
			smp[a][RIGHT].prepareForProjectionNew(w, h, d, pw, ph, pd, layerWidth, nLayers, new AngleWeighter2(AngleWeighter2.X_AXIS, -angle, aperture, cen));

		}
		return smp;
	}

	/**
	 * Loads a list of transformations from a file, which has
	 * one transformation per line, given as 16 values separated by
	 * a space character.
	 * @param file The file to read the transformations from
	 * @return the loaded matrices
	 * @throws IOException
	 */
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

	/**
	 * Save the given transformations to file, one transformation per line,
	 * in form of 16 values separated by a space character.
	 * @param file The output file
	 * @param matrices The matrices to write
	 * @throws IOException
	 */
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

	/**
	 * Create a spherical mesh using a buckyball approximation
	 * @param center The center of the sphere.
	 * @param radius The radius of the sphere.
	 * @param subd The number of subdivisions of the icosahedron.
	 * @return
	 */
	private static IndexedTriangleMesh createSphere(Point3f center, float radius, int subd) {
		// calculate the sphere coordinates
		Icosahedron icosa = new Icosahedron(radius);

		IndexedTriangleMesh sphere = icosa.createBuckyball(radius, subd);
		for(Point3f p : sphere.getVertices())
			p.add(center);
		return sphere;
	}
}

