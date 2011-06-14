package huisken.kymo;

import ij.ImagePlus;
import ij.ImageStack;

import ij.gui.GenericDialog;

import ij.plugin.filter.PlugInFilter;

import ij.process.ImageProcessor;
import ij.process.FloatProcessor;

public class Cyclic_Kymograph implements PlugInFilter {

	public static final int LINEAR  = 0;
	public static final int NEAREST = 1;

	protected ImagePlus image;

	public int setup(String arg, ImagePlus image) {
		this.image = image;
		return DOES_8G | DOES_16 | DOES_32;
	}

	public void run(ImageProcessor ip) {
		GenericDialog gd = new GenericDialog("Cyclic Kymograph");
		gd.addChoice("Interpolation method",
			new String[] {"Linear", "Nearest Neighbor"},
			"Linear");
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		
		createKymograph(image, gd.getNextChoiceIndex()).show();
	}

	public static ImagePlus createKymograph(ImagePlus image, int interpolationMethod) {
		int d = image.getStackSize();
		int h = image.getHeight();
		ImageStack stack = new ImageStack(2 * h, 2 * h);
		for(int z = 0; z < d; z++)
			stack.addSlice("", createKymo(image.getStack().getProcessor(z + 1), interpolationMethod));

		return new ImagePlus("Cyclic Kymo", stack);
	}
	
	private static ImageProcessor createKymo(ImageProcessor ip, int interpolationMethod) {
		int w = ip.getWidth();
		int h = ip.getHeight();
		
		ip = ip.convertToFloat();
		float[] data = (float[])ip.getPixels();
		
		
		// split it into rows and invert order, as radius is 0 at the bottom
		float[][] rows = new float[h][];
		for(int r = 0; r < rows.length; r++) {
			rows[h - r - 1] = new float[w];
			System.arraycopy(data, r * w, rows[h - r - 1], 0, w);
		}
		
		// resample each row so that the new length is <= 2*pi*r
		// store for each row the calibration (in radians per pixel)
		double[] pw = new double[rows.length];
		for(int r = 0; r < rows.length; r++) {
			int len = r == 0 ? 1 : (int)Math.floor(2 * Math.PI * r);
			pw[r] = 2 * Math.PI / len; // TODO do this after appending 1st entry?
			float[] row = resample1D(rows[r], len);
		
			// append at each row the first entry
			rows[r] = new float[len + 1];
			System.arraycopy(row, 0, rows[r], 0, len);
			rows[r][len] = rows[r][0];
		}


		Interpolator intpol = null;
		switch(interpolationMethod) {
			case LINEAR:  intpol = new LinearInterpolator(rows, pw); break;
			case NEAREST: intpol = new NearestNeighborInterpolator(rows, pw); break;
			default: intpol = new LinearInterpolator(rows, pw);
		}

		// create the output image
		int sTarget = 2 * h;
		int center = h;
		ImageProcessor target = new FloatProcessor(sTarget, sTarget);
		for(int y = 0; y < sTarget; y++) {
			int dy = y - center;
			for(int x = 0; x < sTarget; x++) {
				int dx = x - center;
				double r = Math.sqrt(dx * dx + dy * dy);
				double phi = Math.atan2(dy, dx) + Math.PI;
				try {
					target.setf(x, y, intpol.get(r, phi));
				} catch(ArrayIndexOutOfBoundsException e) {}
			}
		}
		return target;
	}
	
	private static float[] resample1D(float[] data, int newlength) {
		if(newlength < data.length)
			return downsample1D(data, newlength);
		if(newlength > data.length)
			return upsample1D(data, newlength);
		return data;
	}

	private static float[] upsample1D(float[] data, int newlength) {
		// do linear interpolation
		int oldlength = data.length;
		float[] newdata = new float[newlength];
		for(int i = 0; i < newlength; i++) {
			double r = i * oldlength / (double)newlength;
			int l = (int)Math.floor(r);
			int u = l + 1;
			double dl = r - l;
	
			float vl = data[l];
			float vu = data[Math.min(u, oldlength - 1)];
	
			newdata[i] = (float)(vl + dl * (vu - vl));
		}
		return newdata;
	}

	private static float[] downsample1D(float[] data, int newlength) {
		int l = newlength;
		double factor = data.length / (double)newlength;
		float[] newdata = new float[l];
	
		// calculate the cumulative array of the old data
		double[] cumOld = new double[data.length + 1];
		cumOld[0] = 0;
		for(int i = 0; i < data.length; i++)
			cumOld[i + 1] = cumOld[i] + data[i];
	
		double[] cumNew = new double[l + 1];
		cumNew[0] = 0;
		for(int newIdx = 0; newIdx < l; newIdx++) {
			int uInt = (int)Math.floor((newIdx + 1) * factor);
			double partialOver = (newIdx + 1) * factor - uInt;
			double c = uInt >= cumOld.length ? cumOld[cumOld.length - 1] : cumOld[uInt];
			c -= cumNew[newIdx];
			if(partialOver > 10e-6 && uInt < data.length)
				c += partialOver * data[uInt];
			newdata[newIdx] = (float)(c / factor);
			cumNew[newIdx + 1] = cumNew[newIdx] + c;
		}
		return newdata;
	}

	private static interface Interpolator {
		public float get(double radius, double phi);
	}

	private static final class NearestNeighborInterpolator implements Interpolator {
		private final float[][] data;
		private final double[] pw;

		public NearestNeighborInterpolator(float[][] data, double[] pw) {
			this.data = data;
			this.pw = pw;
		}

		public float get(double radius, double phi) {
			int r = (int)Math.round(radius);
			int p = (int)Math.round(phi / pw[r]);
			return data[r][p];
		}
	}

	private static final class LinearInterpolator implements Interpolator {
		private final float[][] data;
		private final double[] pw;

		public LinearInterpolator(float[][] data, double[] pw) {
			this.data = data;
			this.pw = pw;
		}

		public float get(double radius, double phi) {
			int r = (int)Math.floor(radius);
			int R = r + 1;
		
			// linear interpolated at r
			int pr = (int)Math.floor(phi / pw[r]);
			int Pr = pr + 1;
			double prFraction = (phi / pw[r]) - pr;
			if(Pr >= data[r].length)
				Pr = 0;
			double vr = data[r][pr] + prFraction * (data[r][Pr] - data[r][pr]);
		
			// linear interpolated at R
			int pR = (int)Math.floor(phi / pw[R]);
			int PR = pR + 1;
			double pRFraction = (phi / pw[R]) - pR;
			if(PR >= data[R].length)
				PR = 0;
			double vR = data[R][pR] + pRFraction * (data[R][PR] - data[R][pR]);
		
			// final interpolation
			double rFraction = radius - r;
			return (float) (vr + rFraction * (vR - vr));
		}
	}
}