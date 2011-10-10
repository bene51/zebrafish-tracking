package huisken.projection;

import ij.IJ;
import ij.ImagePlus;
import ij.process.Blitter;
import ij.process.ImageProcessor;

import java.io.File;


public class Correct {

	public static void correct(String file, String alternateFile, int threshold) {
		ImageProcessor ip = null;
		short[] pixels = null;
		try {
			ip = IJ.openImage(file).getProcessor();
			pixels = (short[])ip.getPixels();
		} catch(Exception e) {
			System.out.println("Not processing " + file + ": Cannot open");
			return;
		}
		int w = ip.getWidth();
		int h = ip.getHeight();

		// get the first line containing wrong pixels
		int firstbad = getFirstBad(pixels, w, h, threshold);

		// nothing found, so nothing to do
		if(firstbad == -1)
			return;

		// get the last bad line
		int lastbad = getLastBad(pixels, w, h, threshold) + 1;
		if(lastbad == -1)
			lastbad = h;

		System.out.println("Found pixel errors in " + file + ": correcting between " + firstbad + " and " + lastbad);

		// load the alternative file
		ImageProcessor ap = null;
		try {
			ap = IJ.openImage(alternateFile).getProcessor();
		} catch(Exception e) {
			System.out.println("Cannot open alternative file: " + alternateFile + ": aborting");
			return;
		}


		// copy bad region
		ap.setRoi(0, firstbad, w, lastbad - firstbad);
		ap = ap.crop();
		ip.copyBits(ap, 0, firstbad, Blitter.COPY);

		// make a backup of the original file
		if(new File(file).renameTo(new File(file + ".orig")))
			IJ.save(new ImagePlus("", ip), file);
		else
			System.out.println("Could not create backup for " + file);
	}

	public static int getFirstBad(short[] pixels, int w, int h, int threshold) {
		for(int y = 0; y < h; y++)
			for(int x = 0; x < w; x++)
				if((pixels[y * w + x] & 0xffff) > threshold)
					return y;
		return -1;
	}

	public static int getLastBad(short[] pixels, int w, int h, int threshold) {
		for(int y = h - 1; y >= 0; y--)
			for(int x = 0; x < w; x++)
				if((pixels[y * w + x] & 0xffff) > threshold)
					return y;
		return -1;
	}
}
