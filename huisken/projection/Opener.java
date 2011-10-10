package huisken.projection;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

public abstract class Opener {

	public static final int LEFT  = 0;
	public static final int RIGHT = 1;

	public abstract int getWidth();
	public abstract int getHeight();
	public abstract int getDepth();
	public abstract int getAngleStart();
	public abstract int getAngleInc();
	public abstract int getNAngles();
	public abstract int getTimepointStart();
	public abstract int getTimepointInc();
	public abstract int getNTimepoints();
	public abstract double getPixelWidth();
	public abstract double getPixelHeight();
	public abstract double getPixelDepth();

	public ImagePlus openStack(int timepoint, int angle, int illumination) {
		ImageStack stack = new ImageStack(getWidth(), getHeight());

		int d = getDepth();
		for(int z = 0; z < d; z ++) {
			stack.addSlice("", openPlane(timepoint, angle, z, illumination));
			IJ.showProgress(z + 1, d);
		}
		ImagePlus imp = new ImagePlus("", stack);
		imp.getCalibration().pixelWidth  = getPixelWidth();
		imp.getCalibration().pixelHeight = getPixelHeight();
		imp.getCalibration().pixelDepth  = getPixelDepth();
		return imp;
	}

	public abstract ImageProcessor openPlane(int timepoint, int angle, int plane, int illumination);
}
