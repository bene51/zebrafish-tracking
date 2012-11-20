package huisken.projection.test;

import huisken.projection.acquisition.TwoCamera_MaxProjection;
import ij.IJ;
import ij.ImagePlus;

public class Test_TwoCamera_MaxProjection extends TwoCamera_MaxProjection {

	private ImagePlus image;

	@Override
	public void produce() {
		final int d2 = 2 * d;
		for(int t = 0; t < nTimepoints; t++) {
			long tStart = -1;
			for(int s = 0; s < nSamples; s++) {
				for(int a = 0; a < nAngles; a++) {
					long start = -1;
					for(int f = 0; f < d; f++) {
						for(int ill = 0; ill < 2; ill++) {
							short[] cache = (short[])image.getStack().getProcessor(2 * f + ill + 1).getPixels();
							if(start == -1)
								start = System.currentTimeMillis();
							if(tStart == -1)
								tStart = start;

							fifo.add(cache);
							System.out.println("--- buffer: " + fifo.size() + "/100");
						}
					}
					long end = System.currentTimeMillis();
					System.out.println("Acquisition: Needed " + (end - start) + "ms  " + 1000f * d2 / (end - start) + " fps");
				}
			}
			long tEnd = System.currentTimeMillis();
			System.out.println("Timepoint " + t + ": " + (tEnd - tStart) + "ms");
		}
	}

	@Override
	public void run(String arg) {
		this.image = IJ.getImage();
		super.run("");
	}
}
