package huisken.projection.test;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class ArtificialEmbryo {

	static final int w = 256;
	static final int h = 256;
	static final int d = 128;

	static final double pw = 1.0;
	static final double ph = 1.0;
	static final double pd = 2.0;

	static final float cx = 128.0f;
	static final float cy = 128.0f;
	static final float cz = 128.0f;
	static final float radius = 100.0f;

	public static void main(String[] args) {
		new ij.ImageJ();
		File outputdir = new File("/Users/bschmid/Desktop/bla");

		File dir = new File(outputdir, "camera1");
		dir = new File(dir, "sample0");
		dir = new File(dir, String.format("tp%04d_a%03d", 0, 0));
		dir.mkdirs();

		ImagePlus imp = createCamera1Left(); imp.setTitle("c1l"); imp.show();
		saveStack(imp, 0, dir);
		imp = createCamera1Right(); imp.setTitle("c1r"); imp.show();
		saveStack(imp, 1, dir);

		dir = new File(outputdir, "camera2");
		dir = new File(dir, "sample0");
		dir = new File(dir, String.format("tp%04d_a%03d", 0, 0));
		dir.mkdirs();

		imp = createCamera2Left(); imp.setTitle("c2l"); imp.show();
		saveStack(imp, 0, dir);
		imp = createCamera2Right(); imp.setTitle("c2r"); imp.show();
		saveStack(imp, 1, dir);

		Properties props = new Properties();
		props.setProperty("nTimepoints", Integer.toString(1));
		props.setProperty("nAngles", Integer.toString(1));
		props.setProperty("angleInc", Integer.toString(90));
		props.setProperty("w", Integer.toString(w));
		props.setProperty("h", Integer.toString(h));
		props.setProperty("d", Integer.toString(d));
		props.setProperty("pixelwidth", Float.toString((float)pw));
		props.setProperty("pixelheight", Float.toString((float)ph));
		props.setProperty("pixeldepth", Float.toString((float)pd));
		props.setProperty("centerX", Float.toString(cx));
		props.setProperty("centerY", Float.toString(cy));
		props.setProperty("centerZ", Float.toString(cz));
		props.setProperty("radius", Float.toString(radius));
		props.setProperty("doublesided", Boolean.toString(true));
		props.setProperty("twocameras", Boolean.toString(true));

		dir = new File(outputdir, "camera1");
		try {
			props.store(new FileOutputStream(new File(dir, "RadialMaxProj.conf")), "");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void saveStack(ImagePlus imp, int ill, File dir) {
		int d = imp.getStackSize();
		for(int z = 0; z < d; z++) {
			String name = String.format("%04d_ill%d.tif", z, ill);
			IJ.save(new ImagePlus("", imp.getStack().getProcessor(z + 1)), new File(dir, name).getAbsolutePath());
		}
	}

	public static ImagePlus createCamera1Left() {
		Helper e1 = new Helper();
		for(int lo = -18; lo <= 0; lo++)
			for(int la = -9; la <= 0; la++)
				e1.drawSphere(10 * lo * Math.PI / 180.0, 10 * la * Math.PI / 180.0, cx, cy, cz, radius);

		//addDuplicate(e1.imp, d, 30000);
		//addDuplicate(e1.imp, d, 15000);
		return e1.imp;
	}

	public static ImagePlus createCamera2Left() {
		Helper e1 = new Helper();
		for(int lo = 0; lo <= 18; lo++)
			for(int la = -9; la <= 0; la++)
				e1.drawSphere(10 * lo * Math.PI / 180.0, 10 * la * Math.PI / 180.0, cx, cy, cz, radius);

		//addDuplicate(e1.imp, d, 30000);
		//addDuplicate(e1.imp, d, 15000);
		return e1.imp;
	}

	public static ImagePlus createCamera1Right() {
		Helper e1 = new Helper();
		for(int lo = -18; lo <= 0; lo++)
			for(int la = 0; la <= 9; la++)
				e1.drawSphere(10 * lo * Math.PI / 180.0, 10 * la * Math.PI / 180.0, cx, cy, cz, radius);

		//addDuplicate(e1.imp, d, 30000);
		//addDuplicate(e1.imp, d, 15000);
		return e1.imp;
	}

	public static ImagePlus createCamera2Right() {
		Helper e1 = new Helper();
		for(int lo = 0; lo <= 18; lo++)
			for(int la = 0; la <= 9; la++)
				e1.drawSphere(10 * lo * Math.PI / 180.0, 10 * la * Math.PI / 180.0, cx, cy, cz, radius);

		//addDuplicate(e1.imp, d, 30000);
		//addDuplicate(e1.imp, d, 15000);
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