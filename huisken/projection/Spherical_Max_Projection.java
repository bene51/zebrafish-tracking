package huisken.projection;

import fiji.util.gui.GenericDialogPlus;
import huisken.fusion.HistogramFeatures;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.io.File;

import javax.vecmath.Point3f;

public class Spherical_Max_Projection implements PlugIn {

	public static final float FIT_SPHERE_THRESHOLD = 1600f;

	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Spherical_Max_Projection");
		gd.addDirectoryField("Data directory", "");
		gd.addNumericField("Timepoint used for sphere fitting", 1, 0);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		File datadir = new File(gd.getNextString());
		int fittingTimepoint = (int)gd.getNextNumber();
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
			process(datadir.getAbsolutePath(), outputdir.getAbsolutePath(), fittingTimepoint);
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	private TimelapseOpener opener = null;

	public void process(String datadir, String outputdir, int fittingTimepoint) {
		TimelapseOpener opener = null;
		try {
			opener = new TimelapseOpener(datadir, true);
		} catch(Exception e) {
			throw new RuntimeException("Cannot open timelapse", e);
		}
		GenericDialog gd = new GenericDialog("Limit processing");
		gd.addNumericField("Start_timepoint",      opener.timepointStart, 0);
		gd.addNumericField("Timepoint_Increment",  opener.timepointInc, 0);
		gd.addNumericField("Number_of_timepoints", opener.nTimepoints, 0);
		gd.addNumericField("Start_angle",          opener.angleStart, 0);
		gd.addNumericField("Angle_Increment",      opener.angleInc, 0);
		gd.addNumericField("Number_of_angles",     opener.nAngles, 0);
		gd.addCheckbox("Also save single views", false);
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		
		int timepointStart = (int)gd.getNextNumber();
		int timepointInc   = (int)gd.getNextNumber();
		int nTimepoints    = (int)gd.getNextNumber();
		int angleStart     = (int)gd.getNextNumber();
		int angleInc       = (int)gd.getNextNumber();
		int nAngles        = (int)gd.getNextNumber();
		boolean saveSingleViews = gd.getNextBoolean();
		
		// fit the spheres to the specified timepoint
		Point3f[] centers = new Point3f[nAngles];
		for(int i = 0; i < centers.length; i++)
			centers[i] = new Point3f();
		float radius = fitSpheres(fittingTimepoint, centers, angleStart, angleInc, nAngles);
		
		MultiViewSphericalMaxProjection mmsmp = new MultiViewSphericalMaxProjection(
				opener, outputdir, timepointStart, timepointInc, nTimepoints,
				angleStart, angleInc, nAngles,
				opener.w, opener.h, opener.d,
				opener.pw, opener.ph, opener.pd,
				centers, radius,
				saveSingleViews);

		mmsmp.process();
	}

	private float fitSpheres(int timepoint, Point3f[] centers, int angleStart, int angleInc, int nAngles) {
		float radius = 0;
		for(int a = 0; a < nAngles; a++) {
			int angle = angleStart + angleInc * a;

			// left illumination
			ImagePlus imp = opener.openStack(timepoint, angle, 0, 2, -1);
			limitAreaForFitSphere(imp, FIT_SPHERE_THRESHOLD);
			imp.show();
			IJ.runMacro("setThreshold(" + FIT_SPHERE_THRESHOLD + ", 16000);");
			IJ.run("Threshold...");
			new WaitForUserDialog("Fit sphere", "Adjust ROI and minimum threshold").show();
			float threshold = (float)imp.getProcessor().getMinThreshold();
			Fit_Sphere fs = new Fit_Sphere(imp);
			fs.fit(threshold);
			fs.getControlImage().show();
			fs.getCenter(centers[a]);
			radius += fs.getRadius();
			imp.close();
		}
		return radius / nAngles;
	}

	private static void limitAreaForFitSphere(ImagePlus imp, float threshold) {
		int w = imp.getWidth();
		int h = imp.getHeight();
		int d = imp.getStackSize();

		int[] xs = new int[w];
		int[] ys = new int[h];
		int sum = 0;
		for(int z = 0; z < d; z++) {
			ImageProcessor ip = imp.getStack().getProcessor(z + 1);
			for(int y = 0; y < h; y++) {
				for(int x = 0; x < w; x++) {
					float v = ip.getf(x, y);
					if(v >= threshold) {
						xs[x]++;
						ys[y]++;
						sum++;
					}
				}
			}
		}
		int xl = Math.round(HistogramFeatures.getQuantile(xs, sum, 0.1f));
		int xu = Math.round(HistogramFeatures.getQuantile(xs, sum, 0.9f));
		int yl = Math.round(HistogramFeatures.getQuantile(ys, sum, 0.1f));
		int yu = Math.round(HistogramFeatures.getQuantile(ys, sum, 0.9f));
		imp.setRoi(new Roi(xl, yl, xu - xl, yu - yl));
	}
}
