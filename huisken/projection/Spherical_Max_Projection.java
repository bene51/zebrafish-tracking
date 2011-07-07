package huisken.projection;

import fiji.util.gui.GenericDialogPlus;

import ij.IJ;
import ij.ImagePlus;

import ij.plugin.PlugIn;

import java.io.File;

import meshtools.IndexedTriangleMesh;

import vib.FastMatrix;

import javax.vecmath.Point3f;



public class Spherical_Max_Projection implements PlugIn {


	public static final float FIT_SPHERE_THRESHOLD = 1600f;

	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Spherical_Max_Projection");
		gd.addDirectoryField("Data directory", "");
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		File datadir = new File(gd.getNextString());
		if(!datadir.isDirectory()) {
			IJ.error(datadir + " is not a directory");
			return;
		}

		File outputdir = new File(datadir, "SphereProjection");
		if(outputdir.isDirectory()) {
			boolean cancelled = !IJ.showMessageWithCancel("Overwrite",
					outputdir + " already exists. Overwrite?");
			if(cancelled)
				return;
		} else {
			outputdir.mkdir();
		}

		try {
			process(datadir.getAbsolutePath(), outputdir.getAbsolutePath());
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	private TimelapseOpener opener = null;

	public void process(String datadir, String outputdir) {
		try {
			opener = new TimelapseOpener(datadir, true);
		} catch(Exception e) {
			throw new RuntimeException("Cannot open timelapse", e);
		}


		int startTimepoint = opener.timepointStart;
		int nTimepoints    = opener.nTimepoints;

		Point3f center = new Point3f(586.8f, 572.5f, 389.3f);  // TODO
		float radius = 296f; // TODO

		FastMatrix[] transforms = new FastMatrix[2];
		transforms[1] = FastMatrix.translate(0, 0, (408.1 - 389.3)).times(
				FastMatrix.rotateEulerAt(Math.PI, Math.PI, 0, center.x, center.y, center.z));

		SphericalMaxProjection[][] smp = initSphericalMaximumProjection(transforms, center, radius);

		String spherepath = new File(outputdir, "Sphere.obj").getAbsolutePath();
		try {
			smp[0][0].saveSphere(spherepath);
		} catch(Exception e) {
			throw new RuntimeException("Cannot save sphere: " + spherepath, e);
		}

		for(int tp = startTimepoint; tp < startTimepoint + nTimepoints; tp++) {
			IJ.showStatus("Timepoint " + (tp - startTimepoint + 1) + "/" + (nTimepoints + 1));

			// 0 deg, left ill
			ImagePlus image = opener.openStack(tp, 0, 0, 2, -1);
			smp[0][0].project(image);

			// 0 deg, right ill
			image = opener.openStack(tp, 0, 1, 2, -1);
			smp[0][1].project(image);

			// 180 deg, left ill
			image = opener.openStack(tp, 180, 0, 2, -1);
			smp[1][0].project(image);

			// 180 deg, right ill
			image = opener.openStack(tp, 180, 1, 2, -1);
			smp[1][1].project(image);

			// sum up left and right illumination of 0 degree
			smp[0][0].addMaxima(smp[0][1].getMaxima());

			// sum up left and right illumination of 180 degree
			smp[1][0].addMaxima(smp[1][1].getMaxima());

			// scale the two resulting maxima
			float h1 = 389.3f;
			float h2 = 408.1f;
			smp[0][0].scaleMaxima(new SimpleUpWeighter(h1));
			smp[1][0].scaleMaxima(new SimpleUpWeighter(h2));  // again UpWeighter because the sphere was turned

			smp[0][0].addMaxima(smp[1][0].getMaxima());

			String filename = String.format("tp%04d.tif", tp);
			String path = new File(outputdir, filename + ".vertices").getAbsolutePath();
			try {
				smp[0][0].saveMaxima(path);
			} catch(Exception e) {
				throw new RuntimeException("Cannot save " + path);
			}
		}
	}

	/**
	 * @param transform Array with one transformation for each angle; the first entry
	 *                  in this array is ignored (it is assumed to be the identity matrix.
	 */
	private SphericalMaxProjection[][] initSphericalMaximumProjection(FastMatrix[] transform, Point3f center, float radius) {

		if(transform.length != opener.nAngles)
			throw new IllegalArgumentException("Need one transformation for each angle");

		int w = opener.w, h = opener.h, d = opener.d / 2;
		double pw = opener.pw, ph = opener.ph, pd = opener.pd;

		int subd = (int)Math.round(radius / (Math.min(pw, Math.min(ph, pd))));
		subd /= 4;

		SphericalMaxProjection[][] smp = new SphericalMaxProjection[opener.nAngles][2];

		// 0 degree, left illumination
		smp[0][0] = new SphericalMaxProjection(center, radius, subd);
		smp[0][0].prepareForProjection(w, h, d, pw, ph, pd, new SimpleLeftWeighter(center.x));

		IndexedTriangleMesh sphere = smp[0][0].getSphere();

		// 0 degree, right handed illumination
		smp[0][1] = new SphericalMaxProjection(sphere, center, radius);
		smp[0][1].prepareForProjection(w, h, d, pw, ph, pd, new SimpleRightWeighter(center.x));

		// 180 degree, left illumination
		smp[1][0] = new SphericalMaxProjection(sphere, center, radius, transform[1]);
		smp[1][0].prepareForProjection(w, h, d, pw, ph, pd, new SimpleRightWeighter(center.x));

		// 180 degree, right illumination
		smp[1][1] = new SphericalMaxProjection(sphere, center, radius, transform[1]);
		smp[1][1].prepareForProjection(w, h, d, pw, ph, pd, new SimpleLeftWeighter(center.x));

		return smp;
	}
}
