package huisken.projection;

import distance.*;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import math3d.Point3d;
import pal.math.*;

import vib.FastMatrix;
import vib.TransformedImage;

public class FastRigidRegistration implements PlugIn {

	private FastMatrix matrix;
	private double[] parameters;

	public void run(String arg) {
		GenericDialog gd = new GenericDialog("Rigid registration");

		int startlevel = 4;
		int stoplevel = 2;
		double tolerance = 1.0;

		int[] wIDs = WindowManager.getIDList();
		if(wIDs == null || wIDs.length < 2){
			IJ.error("Two images needed.");
			return;
		}
		String[] titles = new String[wIDs.length];
		for(int i=0;i<wIDs.length;i++){
			titles[i] = WindowManager.getImage(wIDs[i]).getTitle();
		}

		gd.addStringField("initial transformation", "1 0 0 0 0 1 0 0 0 0 1 0", 30);
		gd.addNumericField("tolerance", 1.0, 3);
		gd.addNumericField("level", 4, 0);
		gd.addNumericField("stopLevel", 2, 0);

		gd.addChoice("Template", titles, titles[0]);
		gd.addChoice("Model", titles, titles[1]);

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		String initial = gd.getNextString();
		tolerance = gd.getNextNumber();
		startlevel = (int)gd.getNextNumber();
		stoplevel = (int)gd.getNextNumber();

		ImagePlus templ = WindowManager.getImage(gd.getNextChoice());
		ImagePlus model = WindowManager.getImage(gd.getNextChoice());

		FastMatrix m = null;
		try {
			m = FastMatrix.parseMatrix(initial);
		} catch(Exception e) {
			IJ.error("Couldn't parse initial transformation");
			return;
		}

		long start = System.currentTimeMillis();
		rigidRegistration(templ, model, m, startlevel, stoplevel, tolerance);
		long end = System.currentTimeMillis();
		System.out.println("needed " + (end - start) + "ms");

		getTransformed(templ, model, matrix).show();
	}

	public static ImagePlus getTransformed(ImagePlus templ, ImagePlus model, FastMatrix matrix) {
		FastTransformedImage trans = new FastTransformedImage(templ, model);
		trans.setTransformation(matrix);
		return trans.getTransformed();
	}

	public FastMatrix getMatrix() {
		return matrix;
	}

	public double[] getParameters() {
		return parameters;
	}

	private static Point3d getCenterOfGravity(ImagePlus img) {
		int w = img.getWidth();
		int h = img.getHeight();
		int d = img.getStackSize();
		long[] g = new long[3];
		long sum = 0;
		for(int z = 0; z < d; z++) {
			ImageProcessor ip = img.getStack().getProcessor(z + 1);
			for(int y = 0; y < h; y++) {
				for(int x = 0; x < w; x++) {
					int v = ip.get(x, y);
					g[0] += v * x;
					g[1] += v * y;
					g[2] += v * z;
					sum += v;
				}
			}
		}
		return new Point3d(
			img.getCalibration().pixelWidth  * g[0] / sum,
			img.getCalibration().pixelHeight * g[1] / sum,
			img.getCalibration().pixelDepth  * g[2] / sum);
	}

	public void rigidRegistration(ImagePlus templ,
					ImagePlus model,
					FastMatrix initial,
					int startlevel,
					int stoplevel,
					double tolerance) {

		FastTransformedImage trans = new FastTransformedImage(templ, model);
		trans.measure = new distance.Correlation();

		double[] params = new double[9];
		Point3d center = getCenterOfGravity(model);
		initial.guessEulerParameters(params, center);


		Optimizer opt = new Optimizer(
			trans, params, startlevel, stoplevel, tolerance);

		matrix = opt.multiResRegister(startlevel - stoplevel);
		parameters = opt.bestParameters;
	}

	private static class Optimizer {
		private FastTransformedImage t;
		private int start, stop;
		private double tolerance;
		private double[] bestParameters; // this is a 9-parameter array with aaa - ttt - ccc

		public Optimizer(FastTransformedImage trans, double[] initial,
				int startLevel, int stopLevel,
				double tol) {
			if (stopLevel < 2)
				t = trans;
			else
				t = trans.resample(1 << (stopLevel - 1));
			start = startLevel;
			stop = stopLevel;
			tolerance = tol;
			bestParameters = initial;
		}

		public FastMatrix multiResRegister(int level) {
			if (level > 0) {
				FastTransformedImage backup = t;
				int fx = 2, fy = 2;
				int fz = t.orig.d > 0.5 * t.orig.w ? 2 : 1;
				t = t.resample(fx, fy, fz);
				t.setTransformation(multiResRegister(level-1));
				t = backup;
				System.gc();
				System.gc();
			}

			double factor = (1 << (start - level));
			int minFactor = (1 << start);

			double angleMax = Math.PI / 4 * factor / minFactor;
			double transMax =        20.0 * factor / minFactor;
			FastMatrix fm = doRegister(tolerance / factor, angleMax, transMax);
			t.setTransformation(fm);
			t.getTransformed().show();
			return fm;
		}

		public FastMatrix doRegister(double tol, double angleMax, double transMax) {
			System.out.println();
			System.out.println("doRegister tol = " + (float)tol + ", transMax = " + transMax + ", angleMax = " + angleMax);
			System.out.println("==========");
			ConjugateDirectionSearch CG =
					new ConjugateDirectionSearch();
			CG.prin = 0;

			Refinement refinement = new Refinement(bestParameters, angleMax, transMax);
			double[] parameters = new double[refinement.getNumArguments()];
			double badness = Float.MAX_VALUE;
			FastMatrix bestMatrix = null;

			do {
				System.out.println("calling optimize()");
				System.out.println("initial = " + toString(refinement.initial));
				CG.optimize(refinement, parameters, tol,  tol);
				parameters = refinement.best;
				badness = refinement.evaluate(parameters);
				bestParameters = refinement.getParameters(parameters);
				bestMatrix     = refinement.getMatrix(parameters);
				System.out.println("badness = " + badness);
			} while(refinement.adjustInitial(parameters) > transMax / 8);


System.out.println("best parameters = " + toString(bestParameters));

// System.out.println("matrix = " + bestMatrix);
// System.out.println("distance = " + badness + " after " + refinement.calls + " calls");
refinement.calls = 0;

			return bestMatrix;
		}

private static String toString(double[] a) {
	StringBuffer sb = new StringBuffer();
	sb.append("[ ");
	for(int i = 0; i < 6; i++)
		sb.append((float)a[i] + "  ");
	sb.append("]");
	return sb.toString();
}

		/*
		 * All the arrays in this class have the order of parameters
		 * in common, which is as follows:
		 * phi - theta - psi (rotation)
		 * t_x - t_y - t_z (translation)
		 */
		class Refinement implements MultivariateFunction {

			private static final int N = 6;

			private final double angleMax;
			private final double translateMax;

			private double min = Double.MAX_VALUE;
			private double[] best; // normalized

			private int calls = 0;

			// this is a 9-el array containing the unnormalized best parameter guess
			private double[] initial;

			private double angleFactor;

			public Refinement(double[] initial, double angleMax, double translateMax) {
				this.initial = initial;
				this.angleMax = angleMax;
				this.translateMax = translateMax;
				this.angleFactor = angleMax / translateMax;
				evaluate(new double[6]);
			}

			/*
			 * @implements getNumArguments() in MultivariateFunction
			 */
			public int getNumArguments() {
				return N;
			}

			/*
			 * @implements getLowerBound() in MultivariateFunction
			 */
			public double getLowerBound(int n) {
				return n < 3 ? -angleMax / angleFactor : -translateMax;
			}

			/*
			 * @implements getUpperBound() in MultivariateFunction
			 */
			public double getUpperBound(int n) {
				return n < 3 ? +angleMax / angleFactor : +translateMax;
			}

			/*
			 * @implements evaluate() in MultivariateFunction
			 */
			public double evaluate(double[] a) {
				calls++;
				t.setTransformation(getMatrix(a));
				double diff = t.getDistance();
				if(diff < min) {
					min = diff;
					best = (double[])a.clone();
				}
				return diff;
			}

			/*
			 * Adjusts the initial parameters, resets the given
			 * array and returns the maximum absolute (normalized) adjustment.
			 */
			public double adjustInitial(double[] a) {
				initial[0] += a[0] * angleFactor;
				initial[1] += a[1] * angleFactor;
				initial[2] += a[2] * angleFactor;
				initial[3] += a[3];
				initial[4] += a[4];
				initial[5] += a[5];

				double maxAdjust = 0;
				for(int i = 0; i < 6; i++) {
					if(Math.abs(a[i]) > maxAdjust)
						maxAdjust = Math.abs(a[i]);
					a[i] = 0;
				}
				return maxAdjust;
			}

			/*
			 * Takes the optimized (normalized) 6-el parameter array
			 * and calculates the 9 real-world euler parameters.
			 */
			public double[] getParameters(double[] a) {
				double[] param = new double[9];
				param[0] = a[0] * angleFactor + initial[0];
				param[1] = a[1] * angleFactor + initial[1];
				param[2] = a[2] * angleFactor + initial[2];
				param[3] = a[3] + initial[3];
				param[4] = a[4] + initial[4];
				param[5] = a[5] + initial[5];
				param[6] = initial[6];
				param[7] = initial[7];
				param[8] = initial[8];
				return param;
			}

			/*
			 * Returns a matrix which applies first the rotation around the center,
			 * then the translation;
			 * order of a is again aaa - ttt - ccc
			 */
			public FastMatrix getMatrix(double[] a) {
				double[] param = getParameters(a);
				FastMatrix trans = FastMatrix.translate(param[3], param[4], param[5]);
				FastMatrix rot = FastMatrix.rotateEulerAt(
					param[0], param[1], param[2],
					param[6], param[7], param[8]);
				return trans.times(rot);
			}
		} // class refinement
	} // class optimizer
} // class AffineRegistration_
