package huisken.util;

import ij.plugin.PlugIn;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import ij.ImagePlus;
import ij.IJ;
import ij.WindowManager;

public class Histogram_Specification implements PlugIn {
	
	private ImagePlus model;
	private ImagePlus template;

	/**
	 * Works only for 8- and 16-bit images
	 */
	public void run(String arg) {
		int[] ids = WindowManager.getIDList();
		if(ids.length < 2) {
			IJ.error("Two images required");
			return;
		}
		String[] images = new String[ids.length];
		for(int i = 0; i < ids.length; i++) {
			images[i] = WindowManager.getImage(ids[i]).getTitle();
		}
		GenericDialog gd = new GenericDialog(
					"Histogram Specification");
		gd.addChoice("Model: ", images, images[0]);
		gd.addChoice("Template: ", images, images[1]);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		model = WindowManager.getImage(gd.getNextChoice());
		template = WindowManager.getImage(gd.getNextChoice());

		int tm = model.getType();
		int tt = template.getType();
		if(tm != tt) {
			IJ.error("Images must be of the same type");
			return;
		}

		if(tm != ImagePlus.GRAY8 && tm != ImagePlus.GRAY16) {
			IJ.error("Only 8-bit and 16-bit grayscale images are supported");
			return;
		}

		int[] lut = adjustHistograms(model, template);
		applyLUT(model, lut);
 	}

	public void applyLUT(ImagePlus image, int[] lut) {
		int w = image.getWidth(), h = image.getHeight();
		int d = image.getStackSize();
		for(int z = 0; z < d; z++) {
			ImageProcessor ip = image.getStack().getProcessor(z + 1);
			for(int i = 0; i < w*h; i++) {
				int v = ip.get(i);
				ip.set(i, lut[v]);
			}
		}
	}

	public int[] adjustHistograms(ImagePlus model, ImagePlus template) {
		float[] P_t = getCumulativeHistogram(template);
		float[] P_m = getCumulativeHistogram(model);
		int[] lut = new int[P_m.length];

		for(int i = 0; i < P_m.length; i++) {
			for(int j = 1; j < P_t.length; j++) {
				if(P_t[j] >= P_m[i] || j == P_t.length-1) {
					lut[i] = j-1;
					break;
				}
			}
		}
		return lut;
	}

	private void printHistos(float[] h1, float[] h2) {
		for(int i = 0; i < h1.length; i++) {
			System.out.println(h1[i] + "\t" + h2[i]);
		}
	}

	public float[] getHistogram(ImagePlus image) {
		int nBins = 1 << image.getBitDepth();
		float[] hi = new float[nBins];
		int w = image.getWidth(), h = image.getHeight();
		int d = image.getStackSize();
		for(int z = 0; z < d; z++) {
			ImageProcessor ip = image.getStack().getProcessor(z + 1);
			for(int i = 0; i < w * h; i++) {
				int v = ip.get(i);
				hi[v]++;
			}
		}
		for(int i = 0; i < hi.length; i++)
			hi[i] /= (w*h*d);
		return hi;
	}

	public float[] getCumulativeHistogram(ImagePlus image) {
		float[] h = getHistogram(image);
		for(int i = 1; i < h.length; i++) {
			h[i] += h[i-1];
		}
		return h;
	}
}
