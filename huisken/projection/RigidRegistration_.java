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

import vib.TransformedImage;
import vib.FastMatrix;

public class RigidRegistration_ implements PlugIn {

	private FastMatrix matrix;
	private double[] parameters;

	public void run(String arg) {
		GenericDialog gd = new GenericDialog("Rigid registration");

		int startlevel = 4;
		int stoplevel = 2;
		double tolerance = 1.0;

		gd.addStringField("initial transformation", "", 30);
		gd.addNumericField("tolerance", 1.0, 3);
		gd.addNumericField("level", 4, 0);
		gd.addNumericField("stopLevel", 2, 0);

		int[] wIDs = WindowManager.getIDList();
		if(wIDs == null){
			IJ.error("No images open");
			return;
		}
		String[] titles = new String[wIDs.length];
		for(int i=0;i<wIDs.length;i++){
			titles[i] = WindowManager.getImage(wIDs[i]).getTitle();
		}

		gd.addChoice("Template", titles, titles[0]);
		gd.addChoice("Model", titles, titles[0]);

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		String initial = gd.getNextString();
		tolerance = gd.getNextNumber();
		startlevel = (int)gd.getNextNumber();
		stoplevel = (int)gd.getNextNumber();

		ImagePlus templ = WindowManager.getImage(gd.getNextChoice());
		ImagePlus model = WindowManager.getImage(gd.getNextChoice());
		FastTransformedImage trans = new FastTransformedImage(templ, model);
		trans.measure = new distance.Correlation();


		// this is a1 - a2 - a3 - tx - ty - tz - cx - cy - cz
		double[] m = new double[9];
		try {
			String[] toks = initial.split(" ");
			for(int i = 0; i < toks.length; i++)
				m[i] = Double.parseDouble(toks[i]);
		} catch(Exception e) {
			IJ.error("Couldn't parse initial transformation");
			return;
		}

		rigidRegistration(trans, m, startlevel, stoplevel, tolerance);

		trans.setTransformation(matrix);
		trans.getTransformed().show();
	}

	public FastMatrix getMatrix() {
		return matrix;
	}

	public double[] getParameters() {
		return parameters;
	}

	public void rigidRegistration(TransformedImage trans,
						double[] initialParam,
						int startlevel,
						int stoplevel,
						double tolerance) {

		Optimizer opt = new Optimizer(
			trans, initialParam, startlevel, stoplevel, tolerance);

		opt.multiResRegister(startlevel-stoplevel);

		matrix = opt.bestMatrix;
		parameters = opt.bestParameters;
System.out.print("parameters = ");
for(int i = 0; i < parameters.length; i++) {
	System.out.print((float)parameters[i] + "  ");
}
System.out.println();
	}

	private static class Optimizer {
		private TransformedImage t;
		private int start, stop;
		private double tolerance;
		private double[] bestParameters; // this is a 9-parameter array with aaa - ttt - ccc
		private FastMatrix bestMatrix;

		public Optimizer(TransformedImage trans, double[] initial,
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

		public void multiResRegister(int level) {
			if (level > 0) {
				TransformedImage backup = t;
				t = t.resample(2);
				multiResRegister(level-1);
				t.setTransformation(bestMatrix);
				t = backup;
				System.gc();
				System.gc();
			}

			double factor = (1 << (start - level));
			int minFactor = (1 << start);
			double angleMax     = Math.PI / 4 * factor / minFactor;
			double translateMax =        20.0 * factor / minFactor;
			doRegister(tolerance / factor, level, angleMax, translateMax);
		}

		public void doRegister(double tol, int level, double angleMax, double translateMax) {
			ConjugateDirectionSearch CG =
					new ConjugateDirectionSearch();

			

			Refinement refinement = new Refinement(bestParameters, angleMax, translateMax);
			double[] parameters = new double[refinement.getNumArguments()];

			CG.optimize(refinement, parameters, tol,  tol);
			double badness = refinement.evaluate(parameters);
			bestParameters = refinement.getParameters(parameters);
			bestMatrix     = refinement.getMatrix(parameters);

System.out.println("distance = " + badness);
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

			// this is a 9-el array containing the unnormalized best parameter guess
			private double[] initial;

			private double angleFactor;

			public Refinement(double[] initial, double angleMax, double translateMax) {
				this.initial = initial;
				this.angleMax = angleMax;
				this.translateMax = translateMax;
				this.angleFactor = angleMax / translateMax;
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
				t.setTransformation(getMatrix(a));
				double diff = t.getDistance();
				return diff;
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
