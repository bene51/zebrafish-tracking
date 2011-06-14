package huisken.util;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;

public class ConvertTo8 {

	public static void main(String[] args) {
		System.out.println("bla");
		convert(ij.IJ.getImage(), 0.001f);
	}

	public static void convert(ImagePlus img, float ratio) {
		if(img.getType() == ImagePlus.GRAY32)
			convertTo16(img);

		int[] histo = getHistogram(img);
		int maxN = 0;
		for(int i = 0; i < histo.length; i++)
			if(histo[i] > maxN)
				maxN = histo[i];

		int min = 0, max = histo.length - 1;

		while(histo[min] < ratio * maxN && min < histo.length - 1)
			min++;

		while(histo[max] < ratio * maxN && max > 0)
			max--;

		System.out.println("min = " + min);
		System.out.println("max = " + max);
		convertTo8(img, min, max);
	}

	private static void convertTo16(ImagePlus img) {
		float min = getMin(img);
		float max = getMax(img);
		int bins = 1 << 16;
		int w = img.getWidth();
		int h = img.getHeight();
		int wh = w * h;
		int d = img.getStackSize();

		ImageStack stack = new ImageStack(w, h);

		for(int z = 0; z < d; z++) {
			ImageProcessor in = img.getStack().getProcessor(z + 1);
			ShortProcessor out = new ShortProcessor(w, h);
			for(int i = 0; i < wh; i++) {
				float v = in.getf(i);
				v = Math.min(v, max);
				v = Math.max(v, min);
				int res = (int)((bins - 1) * (v - min) / (max - min));
				out.set(i, res);
			}
			stack.addSlice("", out);
		}
		img.setStack(null, stack);
	}

	public static void convertTo8(ImagePlus img, int min, int max) {
		int bins = 256;
		int w = img.getWidth();
		int h = img.getHeight();
		int wh = w * h;
		int d = img.getStackSize();

		ImageStack stack = new ImageStack(w, h);

		for(int z = 0; z < d; z++) {
			ImageProcessor in = img.getStack().getProcessor(z + 1);
			ByteProcessor out = new ByteProcessor(w, h);
			for(int i = 0; i < wh; i++) {
				float v = in.get(i);
				v = Math.min(v, max);
				v = Math.max(v, min);
				int res = (int)((v - min) / (max - min) * (bins - 1));
				out.set(i, res);
			}
			stack.addSlice("", out);
		}
		img.setStack(null, stack);
	}

	public static int[] getHistogram(ImagePlus image) {
		int nBins = 1 << image.getBitDepth();
		int[] hi = new int[nBins];
		int w = image.getWidth(), h = image.getHeight();
		int d = image.getStackSize();
		for(int z = 0; z < d; z++) {
			ImageProcessor ip = image.getStack().getProcessor(z + 1);
			for(int i = 0; i < w * h; i++) {
				int v = ip.get(i);
				hi[v]++;
			}
		}
		return hi;
	}

	public static float getMin(ImagePlus img) {
		int w = img.getWidth();
		int h = img.getHeight();
		int wh = w * h;
		int d = img.getStackSize();

		float min = Float.MAX_VALUE;
		for(int z = 0; z < d; z++) {
			ImageProcessor ip = img.getStack().getProcessor(z + 1);
			for(int i = 0; i < wh; i++) {
				float v = ip.getf(i);
				if(v < min)
					min = v;
			}
		}
		return min;
	}

	public static float getMax(ImagePlus img) {
		int w = img.getWidth();
		int h = img.getHeight();
		int wh = w * h;
		int d = img.getStackSize();

		float max = 0f;
		for(int z = 0; z < d; z++) {
			ImageProcessor ip = img.getStack().getProcessor(z + 1);
			for(int i = 0; i < wh; i++) {
				float v = ip.getf(i);
				if(v > max)
					max = v;
			}
		}
		return max;
	}
}
