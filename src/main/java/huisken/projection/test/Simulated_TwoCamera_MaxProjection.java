package huisken.projection.test;

import fiji.util.gui.GenericDialogPlus;
import huisken.projection.processing.TwoCameraSphericalMaxProjection;
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

		if(outputdir.list() != null && outputdir.list().length > 0) {
			boolean cancelled = !IJ.showMessageWithCancel("Overwrite",
					outputdir + " already exists. Overwrite?");
			if(cancelled)
				return;
		}

		if(!outputdir.isDirectory())
			outputdir.mkdir();


		try {
			process(outputdir.getAbsolutePath());
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	private TwoCameraSphericalMaxProjection mmsmp;

	public void process(String outputdir) {

		int nAngles = 1;
		int angleInc = 90;

		mmsmp = new TwoCameraSphericalMaxProjection(
				outputdir,
				TwoCameraSphericalMaxProjection.CAMERA1,
				angleInc, nAngles,
				w, h, d,
				pw, ph, pd,
				center, radius,
				0.4 * radius, 1,
				null);

		startCamera(timepoints, nAngles, d, TwoCameraSphericalMaxProjection.CAMERA1);

		mmsmp = new TwoCameraSphericalMaxProjection(
				outputdir,
				TwoCameraSphericalMaxProjection.CAMERA2,
				angleInc, nAngles,
				w, h, d,
				pw, ph, pd,
				center, radius,
				0.4 * radius, 1,
				null);

		startCamera(timepoints, nAngles, d, TwoCameraSphericalMaxProjection.CAMERA2);
	}

	public void startCamera(int nTimepoints, int nAngles, int d, int camera) {
		go(nTimepoints, nAngles, d, camera);
	}

	public void go(int nTimepoints, int nAngles, int d, int camera) {
		ImagePlus left = camera == TwoCameraSphericalMaxProjection.CAMERA1
			? ArtificialEmbryo.createCamera1Left()
			: ArtificialEmbryo.createCamera2Left();
		ImagePlus right = camera == TwoCameraSphericalMaxProjection.CAMERA1
			? ArtificialEmbryo.createCamera1Right()
			: ArtificialEmbryo.createCamera2Right();
		for(int tp = 0; tp < nTimepoints; tp++) {
			for(int a = 0; a < nAngles; a++) {
				for(int z = 0; z < d; z++) {
					mmsmp.process((short[])left.getStack().getProcessor(d + 1).getPixels(), tp, a, z, 0);
					mmsmp.process((short[])right.getStack().getProcessor(d + 1).getPixels(), tp, a, z, 1);
				}
			}
		}
	}
}