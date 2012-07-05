package huisken.projection.test;

import fiji.util.gui.GenericDialogPlus;
import huisken.projection.TwoCameraSphericalMaxProjection;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

import java.io.File;

import javax.vecmath.Point3f;

public class Simulated_TwoCamera_MaxProjection implements PlugIn {

	private final int timepoints = 3;
	private final Point3f center = new Point3f(128, 128, 128);
	private final float radius = 100f;
	private final int w = 256;
	private final int h = 256;
	private final int d = 128;
	private final double pw = 1.0;
	private final double ph = 1.0;
	private final double pd = 2.0;

	@Override
	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Spherical_Max_Projection");
		gd.addDirectoryField("Output directory", "");
		gd.showDialog();
		if(gd.wasCanceled())
			return;


		File outputdir = new File(gd.getNextString());

		if(outputdir.exists() && !outputdir.isDirectory())
			throw new RuntimeException("Output directory must be a folder");

		if(outputdir.list().length > 0) {
			boolean cancelled = !IJ.showMessageWithCancel("Overwrite",
					outputdir + " already exists. Overwrite?");
			if(cancelled)
				return;
		}

		if(!outputdir.isDirectory())
			outputdir.mkdir();


		try {
			process(outputdir.getAbsolutePath(), timepoints, center, radius, w, h, d, pw, ph, pd);
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	private TwoCameraSphericalMaxProjection mmsmp;

	public void process(String outputdir, int timepoints, Point3f center, float radius, int w, int h, int d, double pw, double ph, double pd) {

		int timepointStart = 0;
		int timepointInc   = 1;
		int nTimepoints    = timepoints;

		mmsmp = new TwoCameraSphericalMaxProjection(
				outputdir,
				timepointStart, timepointInc, nTimepoints,
				TwoCameraSphericalMaxProjection.CAMERA1,
				180, 1,
				w, h, d,
				pw, ph, pd,
				center, radius, null); // null just inserted to avoid compilation error

		startCamera(timepoints * d, TwoCameraSphericalMaxProjection.CAMERA1);

		mmsmp = new TwoCameraSphericalMaxProjection(
				outputdir,
				timepointStart, timepointInc, nTimepoints,
				TwoCameraSphericalMaxProjection.CAMERA2,
				180, 1,
				w, h, d,
				pw, ph, pd,
				center, radius, null); // null just inserted to avoid compilation error

		startCamera(timepoints * d, TwoCameraSphericalMaxProjection.CAMERA2);
	}

	public void startCamera(int frames, int camera) {
		go(frames, camera);
	}

	public void go(int framecount, int camera) {
		ImagePlus left = camera == TwoCameraSphericalMaxProjection.CAMERA1
			? ArtificialEmbryo.createCamera1Left()
			: ArtificialEmbryo.createCamera2Left();
		ImagePlus right = camera == TwoCameraSphericalMaxProjection.CAMERA1
			? ArtificialEmbryo.createCamera1Right()
			: ArtificialEmbryo.createCamera2Right();
		for(int f = 0; f < framecount; f++) {
			mmsmp.process((short[])left.getStack().getProcessor(f + 1).getPixels());
			mmsmp.process((short[])right.getStack().getProcessor(f + 1).getPixels());
		}
	}
}