package huisken.projection;

import ij.ImagePlus;
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

	public abstract ImagePlus openStack(int timepoint, int angle, int illumination);

	public abstract ImageProcessor openPlane(int timepoint, int angle, int plane, int illumination);
}
