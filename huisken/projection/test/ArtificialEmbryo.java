package huisken.projection.test;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;

public class ArtificialEmbryo {

	static final float cx = 128.0f;
	static final float cy = 128.0f;
	static final float cz = 128.0f;
	static final float radius = 100.0f;
	static final int d = 128;

	public static ImagePlus createCamera1Left() {
		Helper e1 = new Helper();
		for(int lo = -18; lo <= 0; lo++)
			for(int la = -9; la <= 0; la++)
				e1.drawSphere(10 * lo * Math.PI / 180.0, 10 * la * Math.PI / 180.0, cx, cy, cz, radius);

		addDuplicate(e1.imp, d, 30000);
		addDuplicate(e1.imp, d, 15000);
		return e1.imp;
	}

	public static ImagePlus createCamera2Left() {
		Helper e1 = new Helper();
		for(int lo = 0; lo <= 18; lo++)
			for(int la = -9; la <= 0; la++)
				e1.drawSphere(10 * lo * Math.PI / 180.0, 10 * la * Math.PI / 180.0, cx, cy, cz, radius);

		addDuplicate(e1.imp, d, 30000);
		addDuplicate(e1.imp, d, 15000);
		return e1.imp;
	}

	public static ImagePlus createCamera1Right() {
		Helper e1 = new Helper();
		for(int lo = -18; lo <= 0; lo++)
			for(int la = 0; la <= 9; la++)
				e1.drawSphere(10 * lo * Math.PI / 180.0, 10 * la * Math.PI / 180.0, cx, cy, cz, radius);

		addDuplicate(e1.imp, d, 30000);
		addDuplicate(e1.imp, d, 15000);
		return e1.imp;
	}

	public static ImagePlus createCamera2Right() {
		Helper e1 = new Helper();
		for(int lo = 0; lo <= 18; lo++)
			for(int la = 0; la <= 9; la++)
				e1.drawSphere(10 * lo * Math.PI / 180.0, 10 * la * Math.PI / 180.0, cx, cy, cz, radius);

		addDuplicate(e1.imp, d, 30000);
		addDuplicate(e1.imp, d, 15000);
		return e1.imp;
	}

	static void addDuplicate(ImagePlus imp, int d, int value) {
		int[] lut = new int[65536];
		lut[lut.length - 1] = value;
		for(int z = 0; z < d; z++) {
			ImageProcessor ip = imp.getStack().getProcessor(z + 1).duplicate();
			ip.applyTable(lut);
			imp.getStack().addSlice("", ip);
		}
	}

	private static class Helper {

		private static final int w = 256;
		private static final int h = 256;
		private static final int d = 128;

		private static final double pw = 1.0;
		private static final double ph = 1.0;
		private static final double pd = 2.0;

		private final ImagePlus imp;
		private final ImageProcessor[] ips;

		public Helper () {
			imp = IJ.createImage("ArtificialEmbryo", "16", w, h, d);
			Calibration cal = imp.getCalibration();
			cal.pixelWidth = pw;
			cal.pixelHeight = ph;
			cal.pixelDepth = pd;

			ips = new ImageProcessor[d];

			for(int z = 0; z < d; z++) {
				ips[z] = imp.getStack().getProcessor(z + 1);
				ips[z].setValue(65535);
			}
		}


		void drawSphere(int x, int y, int z, int r) {
			for(int ri = 0; ri <= r / pd; ri++) {
				double alpha = Math.asin(pd * ri / r);
				int rz = (int)Math.round(r * Math.cos(alpha));
				int zi = z + ri;
				if(zi < d && zi >= 0)
					ips[zi].fillOval(x-rz, y-rz, 2 * rz, 2 * rz);
				zi = z - ri;
				if(zi < d && zi >= 0)
					ips[zi].fillOval(x-rz, y-rz, 2 * rz, 2 * rz);
			}
		}

		void drawSphere(double longitude, double latitude, float cx, float cy, float cz, float radius) {
			double so = Math.sin(longitude);
			double sa = Math.sin(latitude);
			double co = Math.cos(longitude);
			double ca = Math.cos(latitude);
			int x = (int)Math.round((cx + radius * ca * co) / pw);
			int z = (int)Math.round((cz - radius * ca * so) / pd);
			int y = (int)Math.round((cy - radius * sa) / ph);
			drawSphere(x, y, z, 3);
		}
	}
}