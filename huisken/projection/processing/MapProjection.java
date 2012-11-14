package huisken.projection.processing;

import ij.process.ImageProcessor;

public interface MapProjection {

	public void prepareForProjection(SphericalMaxProjection smp, int w);

	public ImageProcessor project();
}