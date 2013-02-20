package huisken.projection.test;

import fiji.util.gui.GenericDialogPlus;
import huisken.projection.processing.AngleWeighter2;
import huisken.projection.processing.TwoCameraSphericalMaxProjection;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;

public class CreateArtificialDataset implements PlugIn {

	public static void main(String[] args) {
		new CreateArtificialDataset().run("");
	}

	private int w, h, d;
	private double pw, ph, pd;
	private double cx, cy, cz, radius;

	/**
	 * input:
	 *  - input folder containing SMP.xml and transformations
	 *  - #angles
	 *  - output folder
	 *
	 * @param args
	 */
	@Override
	public void run(String args) {

		GenericDialogPlus gd = new GenericDialogPlus("Create Artificial Dataset");
		gd.addDirectoryField("Input folder", "");
		gd.addDirectoryField("Output folder", "");
		gd.addNumericField("nAngles", 2, 0);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		File indir = new File(gd.getNextString());
		File outdir = new File(gd.getNextString());
		int nAngles = (int)gd.getNextNumber();

		if(!outdir.exists())
			outdir.mkdir();

		try {
			readSMPFile(new File(indir, "SMP.xml").getAbsolutePath());
			d /= 2;
			pd *= 2;
		} catch (IOException e) {
			e.printStackTrace();
		}

		int aperture = 90 / nAngles;

		for(int camera = 0; camera <= 1; camera++) {
			int angle = camera == TwoCameraSphericalMaxProjection.CAMERA1 ? 135 : 45;

			Matrix4f[] transforms = null;
			if(nAngles > 1) {
				try {
					transforms = TwoCameraSphericalMaxProjection.loadTransformations(new File(indir, "transformations").getAbsolutePath());
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				}
			}

			File outd = new File(outdir, "camera" + camera);
			outd.mkdir();

			for(int a = 0; a < nAngles; a++) {
				Matrix4f transform = null;
				if(a > 0)
					transform = transforms[a];
				else {
					transform = new Matrix4f();
					transform.setIdentity();
				}
				Point3f cen = new Point3f((float)cx, (float)cy, (float)cz);
				if(a > 0)
					transform.transform(cen);

				// left illumination
				AngleWeighter2 left = new AngleWeighter2(AngleWeighter2.X_AXIS, angle, aperture, cen);
				ImagePlus impLeft = Helper.createImage(w,  h, d, pw, ph, pd, cx, cy, cz, radius, left, transform);

				// right illumination
				AngleWeighter2 right = new AngleWeighter2(AngleWeighter2.X_AXIS, -angle, aperture, cen);
				ImagePlus impRight = Helper.createImage(w,  h, d, pw, ph, pd, cx, cy, cz, radius, right, transform);

				File outd2 = new File(outd, String.format("tp0000_a%03d", a));
				outd2.mkdir();

				for(int z = 0; z < d; z++) {
					String path = new File(outd2, String.format("%04d_ill0.tif", z)).getAbsolutePath();
					IJ.save(new ImagePlus("", impLeft.getStack().getProcessor(z + 1)), path);
					path = new File(outd2, String.format("%04d_ill1.tif", z)).getAbsolutePath();
					IJ.save(new ImagePlus("", impRight.getStack().getProcessor(z + 1)), path);
				}
			}
		}
	}

	private void readSMPFile(String path) throws IOException {
		FileInputStream config = new FileInputStream(new File(path));
		Properties props = new Properties();
		props.loadFromXML(config);
		config.close();

		w = Integer.parseInt(props.getProperty("w", "0"));
		h = Integer.parseInt(props.getProperty("h", "0"));
		d = Integer.parseInt(props.getProperty("d", "0"));
		pw = Double.parseDouble(props.getProperty("pw", "0"));
		ph = Double.parseDouble(props.getProperty("ph", "0"));
		pd = Double.parseDouble(props.getProperty("pd", "0"));
		cx = Double.parseDouble(props.getProperty("centerx"));
		cy = Double.parseDouble(props.getProperty("centery"));
		cz = Double.parseDouble(props.getProperty("centerz"));
		radius = (float)Double.parseDouble(props.getProperty("radius"));
	}


	private static class Helper {

		static ImagePlus createImage(int w, int h, int d, double pw, double ph, double pd, double cx, double cy, double cz, double radius, AngleWeighter2 aw, Matrix4f transform) {
			return new Helper(w, h, d, pw, ph, pd, cx, cy, cz, radius, aw, transform).draw();
		}

		private final int d;
		private final double pw, ph, pd;
		private final double cx, cy, cz, radius;

		private final AngleWeighter2 aw;
		private final Matrix4f transform;

		private final ImagePlus imp;
		private final ImageProcessor[] ips;

		Helper (int w, int h, int d, double pw, double ph, double pd, double cx, double cy, double cz, double radius, AngleWeighter2 aw, Matrix4f transform) {
			this.d = d;
			this.pw = pw;
			this.ph = ph;
			this.pd = pd;
			this.cx = cx;
			this.cy = cy;
			this.cz = cz;
			this.radius = radius;
			this.aw = aw;
			this.transform = transform;

			imp = IJ.createImage("ArtificialEmbryo", "16", w, h, d);
			Calibration cal = imp.getCalibration();
			cal.pixelWidth = pw;
			cal.pixelHeight = ph;
			cal.pixelDepth = pd;

			ips = new ImageProcessor[d];

			for(int z = 0; z < d; z++) {
				ips[z] = imp.getStack().getProcessor(z + 1);
				ips[z].setValue(255);
			}
		}

		ImagePlus draw() {
			for(int lo = -18; lo <= 18; lo++)
				for(int la = -9; la <= 9; la++)
					drawSphere(10 * lo * Math.PI / 180.0, 10 * la * Math.PI / 180.0);
			return imp;
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

		void drawSphere(double longitude, double latitude) {
			double so = Math.sin(longitude);
			double sa = Math.sin(latitude);
			double co = Math.cos(longitude);
			double ca = Math.cos(latitude);
			float rx = (float)(cx + radius * ca * co);
			float ry = (float)(cy - radius * sa);
			float rz = (float)(cz - radius * ca * so);
			Point3f p = new Point3f(rx, ry, rz);
			transform.transform(p);
			int x = (int)Math.round(p.x / pw);
			int z = (int)Math.round(p.z / pd);
			int y = (int)Math.round(p.y / ph);
			if(aw.getWeight(p.x, p.y, p.z) > 0.5)
				drawSphere(x, y, z, 10);
		}
	}
}
