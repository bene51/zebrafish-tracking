package huisken.projection;

import ij.ImagePlus;
import ij.ImageStack;
import ij.IJ;

import ij.gui.Roi;

import ij.plugin.filter.PlugInFilter;

import ij.plugin.Duplicator;

import ij.process.ImageProcessor;
import ij.process.StackConverter;
import ij.process.ImageConverter;

import ij.measure.Calibration;

import javax.vecmath.Point3f;

import meshtools.IndexedTriangleMesh;

import customnode.*;

import vib.NaiveResampler;

import Jama.Matrix;

public class Fit_Sphere implements PlugInFilter {
	private ImagePlus image;
	private double x0, y0, z0, r;

	public Fit_Sphere() {}

	public Fit_Sphere(ImagePlus image) {
		this.image = image;
	}

	public int setup(String arg, ImagePlus image) {
		this.image = image;
		return DOES_8G | DOES_16 | DOES_32;
	}

	public void getCenter(Point3f center) {
		center.x = (float)x0;
		center.y = (float)y0;
		center.z = (float)z0;
	}

	public double getRadius() {
		return r;
	}

	public void run(ImageProcessor ip) {
		double threshold = IJ.getNumber(
			"Threshold", 127);
		if(threshold == IJ.CANCELED)
			return;
		fit(threshold);
		IJ.showMessage("x0 = " + x0 + "\ny0 = " + y0 + "\nz0 = " + z0 + "\nr = " + r);
		getControlImage().show();
	}

	public ImagePlus getControlImage() {
		Roi roi = image.getRoi();
		image.killRoi();
		ImagePlus imp = new Duplicator().run(image);
		ImageConverter.setDoScaling(true);
		new StackConverter(imp).convertToGray8();
		imp = NaiveResampler.resample(imp, 4);
		
		IndexedTriangleMesh mesh = new IndexedTriangleMesh(
			MeshMaker.createSphere(x0, y0, z0, r));
		imp = mesh.createOverlay(imp, 0xff0000);
		image.setRoi(roi);
		return imp;
	}

	public void fit(double threshold) {
		int w = image.getWidth();
		int h = image.getHeight();
		int d = image.getStackSize();

		Calibration cal = image.getCalibration();
		double pw = cal.pixelWidth;
		double ph = cal.pixelHeight;
		double pd = cal.pixelDepth;

		double[][] XX = new double[4][4];
		double[][] XY = new double[4][1];

		for(int zi = 0; zi < d; zi++) {
			ImageProcessor ip = image.getStack().getProcessor(zi + 1);
			double z = zi * pd;
			for(int yi = 0; yi < h; yi++) {
				double y = yi * ph;
				for(int xi = 0; xi < w; xi++) {
					if(ip.getf(xi, yi) < threshold)
						continue;
					if(image.getRoi() != null && !image.getRoi().contains(xi, yi))
						continue;
					double x = xi * pw;

					double xx = x * x;
					double yy = y * y;
					double zz = z * z;
					double xyz = -xx - yy - zz;
					XX[0][0] += xx;
					XX[0][1] += x * y;
					XX[0][2] += x * z;
					XX[0][3] += x;
					XX[1][1] += yy;
					XX[1][2] += y * z;
					XX[1][3] += y;
					XX[2][2] += z * z;
					XX[2][3] += z;
					XX[3][3] += 1;
					XY[0][0] += x * xyz;
					XY[1][0] += y * xyz;
					XY[2][0] += z * xyz;
					XY[3][0] += xyz;
				}
			}
		}
		XX[1][0] = XX[0][1];
		XX[2][0] = XX[0][2];
		XX[3][0] = XX[0][3];
		XX[2][1] = XX[1][2];
		XX[3][1] = XX[1][3];
		XX[3][2] = XX[2][3];

		int n = (int)XX[3][3];
		for(int r = 0; r < 4; r++)
			for(int c = 0; c < 4; c++)
				XX[r][c] /= n;
		for(int r = 0; r < 4; r++)
			XY[r][0] /= n;

		Matrix m = new Matrix(XX);
		double[][] s = m.inverse().times(new Matrix(XY)).getArray();

		double a = s[0][0], b = s[1][0], c = s[2][0], e = s[3][0];

		x0 = -a / 2;
		y0 = -b / 2;
		z0 = -c / 2;
		r = Math.sqrt(((a * a + b * b + c * c) / 4) - e);
	}
}
