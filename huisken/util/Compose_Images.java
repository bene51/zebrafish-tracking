package util.ComposeImages;

import ij.ImagePlus;
import ij.IJ;

import ij.process.ImageProcessor;
import ij.process.Blitter;

public class Compose_Images {

	public static ImagePlus compose(ImagePlus i1, int x1, int y1, int z1, ImagePlus i2, int x2, int y2, int z2) {
		int w1 = i1.getWidth();
		int h1 = i1.getHeight();
		int d1 = i1.getStackSize();

		int w2 = i2.getWidth();
		int h2 = i2.getHeight();
		int d2 = i2.getStackSize();

		int rw = Math.max(x1 + w1, x2 + w2);
		int rh = Math.max(y1 + h1, y2 + h2);
		int rd = Math.max(z1 + d1, z1 + d2);

		ImagePlus res = IJ.createImage("Composed", "rgb black", rw, rh, rd);
		for(int z = 0; z < rd; z++) {
			ImageProcessor ipr = res.getStack().getProcessor(z + 1);
			// copy z1
			if(z >= z1 && z < z1 + d1)
				ipr.copyBits(i1.getStack().getProcessor(z - z1 + 1), x1, y1, Blitter.ADD);
			// copy z2
			if(z >= z2 && z < z2 + d2)
				ipr.copyBits(i2.getStack().getProcessor(z - z2 + 1), x2, y2, Blitter.ADD);
		}
		return res;
	}
}
