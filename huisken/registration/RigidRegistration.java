package huisken.registration;

import vib.TransformedImage;
import vib.FastMatrix;

import ij.ImagePlus;

public class RigidRegistration {

	public static void main(String[] arg) {
		ImagePlus src = ij.WindowManager.getImage("B");
		ImagePlus tgt = ij.WindowManager.getImage("A");

		long start = System.currentTimeMillis();
		float[] mat = RigidRegistration.register(src, tgt);
		long end = System.currentTimeMillis();
		System.out.println("Needed " + (end - start) + "ms");
	}

	public static float[] register(ImagePlus src, ImagePlus tgt) {

		TransformedImage transformed = new TransformedImage(tgt, src);
		transformed.measure = new distance.Correlation();

		String initial = "";
		int level = 4;
		int stopLevel = 1;
		
		FastMatrix m = new vib.RigidRegistration().rigidRegistration(
			transformed,
			"", // bbox
			initial,
			-1, // mat
			-1, // mat
			false, // noOptimization
			level,
			stopLevel,
			0.1, // tolerance,
			1, // nInitialPositions,
			true, // showTransformed,
			false, // showDifferenceImage,
			false, // fastButInaccurate,
			null ); // alsoTransform
		double[] d = m.rowwise16();
		float[] ret = new float[12];
		for(int i = 0; i < 12; i++)
			ret[i] = (float)d[i];
		return ret;
	}
}
