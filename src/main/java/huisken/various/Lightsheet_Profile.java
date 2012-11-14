package huisken.various;

import ij.IJ;
import ij.ImagePlus;

import ij.gui.Plot;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;

import ij.measure.CurveFitter;

import ij.plugin.filter.PlugInFilter;

import ij.process.ImageProcessor;

import java.awt.Rectangle;


public class Lightsheet_Profile implements PlugInFilter {

	protected ImagePlus image;

	public int setup(String arg, ImagePlus image) {
		this.image = image;
		return DOES_8G | DOES_16 | DOES_32;
	}

	public void run(ImageProcessor ip) {
		
		int d = image.getStackSize();
		double[] means = new double[d];
		double[] sigmas = new double[d];

		for(int z = 0; z < d; z++) {
			new WaitForUserDialog("Select a rectangle ROI, then click OK").show();
			Roi roi = image.getRoi();
			Rectangle r = roi.getBounds();
			double[] avg = new double[r.height];
			double[] y = new double[r.height];
			double[] param = new double[2];

			averageLines(ip, r.x, r.x + r.width, r.y, r.y + r.height, avg);
			for(int yi = 0; yi < r.height; yi++)
				y[yi] = (r.y + yi) * image.getCalibration().pixelHeight;
			fit(y, avg, param);
			means[z] = param[0];
			sigmas[z] = param[1];
		}

		double[] zs = new double[d];
		for(int z = 0; z < d; z++)
			zs[z] = z * image.getCalibration().pixelDepth;

		Plot plot = new Plot("Lightsheet thickness", "z", "std dev", zs, sigmas);
		plot.show();
	}

	public static void fit(double[] x, double[] v, double[] param) {
		CurveFitter cf = new CurveFitter(x, v);
		cf.doFit(CurveFitter.GAUSSIAN);
		double[] p = cf.getParams();
		param[0] = p[2];
		param[1] = p[3];
	}

	public static void averageLines(ImageProcessor ip, int x0, int x1, int y0, int y1, double[] result) {
		int w = ip.getWidth();
		int h = ip.getHeight();
		for(int y = y0; y < y1; y++) {
			result[y - y0] = 0;
			for(int x = x0; x < x1; x++)
				result[y - y0] += ip.getf(x, y);
			result[y - y0] /= (x1 - x0);
		}
	}
}