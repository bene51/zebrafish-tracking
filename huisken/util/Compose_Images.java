package huisken.util;

import java.util.ArrayList;

import ij.ImagePlus;
import ij.IJ;

import ij.process.ImageProcessor;
import ij.process.Blitter;

public class Compose_Images {

	private static class Entry {
		ImagePlus image;
		int x, y, z;
		int w, h, d;

		Entry(ImagePlus image, int x, int y, int z, int w, int h, int d) {
			this.image = image;
			this.x = x;
			this.y = y;
			this.z = z;
			this.w = w;
			this.h = h;
			this.d = d;
		}
	}

	private ArrayList<Entry> entries = new ArrayList<Entry>();

	public void add(ImagePlus imp, int x, int y, int z, int w, int h, int d) {
		if(w < 0) w = imp.getWidth();
		if(h < 0) h = imp.getHeight();
		if(d < 0) d = imp.getStackSize();

		entries.add(new Entry(imp, x, y, z, w, h, d));
	}

	public ImagePlus create() {

		int rw = 0, rh = 0, rd = 0;

		for(Entry e : entries) {
			if(e.x + e.w > rw) rw = e.x + e.w;
			if(e.y + e.h > rh) rh = e.y + e.h;
			if(e.z + e.d > rd) rd = e.z + e.d;
		}

		ImagePlus res = IJ.createImage("Composed", "rgb white", rw, rh, rd);
		for(int z = 0; z < rd; z++) {
			ImageProcessor ipr = res.getStack().getProcessor(z + 1);

			for(Entry e : entries) {
				if(z >= e.z && z < e.z + e.d)
					ipr.copyBits(e.image.getStack().getProcessor(z - e.z + 1).resize(e.w, e.h).convertToRGB(), e.x, e.y, Blitter.COPY);
			}
		}
		return res;
	}
}
