package huisken.projection;

import ij.ImagePlus;

public abstract class Opener {

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

	public abstract ImagePlus openStack(int timepoint, int angle, int planeStart, int planeInc, int nPlanes);
}
