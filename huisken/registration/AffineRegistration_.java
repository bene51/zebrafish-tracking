package huisken.registration;

import distance.*;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.YesNoCancelDialog;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import java.awt.Choice;
import java.util.StringTokenizer;
import math3d.Point3d;
import pal.math.*;

import vib.TransformedImage;
import vib.FastMatrix;

public class AffineRegistration_ implements PlugIn {

	private FastMatrix matrix;
	private double[] parameters;

	public static final int AFFINE = 0;
	public static final int RIGID_ANISOTROPIC_SCALE = 1;
	public static final int RIGID_ISOTROPIC_SCALE = 2;
	public static final int RIGID = 3;

	public void run(String arg) {
		GenericDialog gd = new GenericDialog("Affine registration");

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
		String[] methods = {
			"Affine",
			"Rigid with unisotropic scale",
			"Rigid with isotropic scale",
			"Rigid"};
		gd.addChoice("method", methods, "Affine");
		String[] measures = {
			"Euclidean", "Correlation", "MutualInfo", "Threshold55",
			"Threshold155" };
		gd.addChoice("measure", measures, "Euclidean");

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		String initial = gd.getNextString();
		tolerance = gd.getNextNumber();
		startlevel = (int)gd.getNextNumber();
		stoplevel = (int)gd.getNextNumber();

		ImagePlus templ = WindowManager.getImage(gd.getNextChoice());
		ImagePlus model = WindowManager.getImage(gd.getNextChoice());
		TransformedImage trans = new TransformedImage(templ, model);

		int methodIndex = gd.getNextChoiceIndex();
		Optimizer.method = methodIndex;
		int measureIndex = gd.getNextChoiceIndex();
		if (measureIndex == 1)
			trans.measure = new distance.Correlation();
		else if (measureIndex == 2)
			trans.measure = new distance.MutualInformation();
		else if (measureIndex == 3)
			trans.measure = new distance.Thresholded(55);
		else if (measureIndex == 4)
			trans.measure = new distance.Thresholded(155);
		else
			trans.measure = new distance.Euclidean();

		FastMatrix m = null;
		try {
			m = FastMatrix.parseMatrix(initial);
		} catch(Exception e) {
			IJ.error("Couldn't parse initial transformation");
			return;
		}

		affineRegistration(trans, m, startlevel, stoplevel, tolerance);

		trans.setTransformation(matrix);
		trans.getTransformed().show();
	}

	public FastMatrix getMatrix() {
		return matrix;
	}

	public double[] getParameters() {
		return parameters;
	}

	public static void restrict(int what) {
		Optimizer.method = what;
	}

	public void affineRegistration(TransformedImage trans,
						FastMatrix rigid,
						int startlevel,
						int stoplevel,
						double tolerance) {

		FastMatrix m = rigid;
		Optimizer opt = new Optimizer(
			trans, m, startlevel, stoplevel, tolerance);

		opt.multiResRegister(startlevel-stoplevel);

		matrix = opt.bestMatrix;
		parameters = opt.bestParameters;
System.out.print("parameters = ");
for(int i = 0; i < parameters.length; i++) {
	System.out.print((float)parameters[i] + "  ");
}
System.out.println();
	}

	public void affineRegistration(TransformedImage trans,
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

	static Point3d getCenterOfGravity(ImagePlus image) {
		int w = image.getWidth();
		int h = image.getHeight();
		int d = image.getStackSize();

		Point3d p = new Point3d();

		for(int z = 0; z < d; z++) {
			ImageProcessor ip = image.getStack().getProcessor(z + 1);
			for(int y = 0; y < h; y++) {
				for(int x = 0; x < w; x++) {
					float v = ip.getf(x, y);
					p.x += x * v;
					p.y += y * v;
					p.z += z * v;
				}
			}
		}
		Calibration calib = image.getCalibration();
		p.x *= calib.pixelWidth;
		p.y *= calib.pixelHeight;
		p.z *= calib.pixelDepth;

		return p;
	}

	static class Optimizer {
		TransformedImage t;
		int start, stop;
		double tolerance;
		Refinement refinement;
		double[] bestParameters;
		FastMatrix bestMatrix;

		static int method = AFFINE;

		public Optimizer(TransformedImage trans, FastMatrix initial,
				int startLevel, int stopLevel,
				double tol) {
			if (stopLevel < 2)
				t = trans;
			else
				t = trans.resample(1 << (stopLevel - 1));
			start = startLevel;
			stop = stopLevel;
			tolerance = tol;
			refinement = new Refinement();
			refinement.setInitial(initial);
		}

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
			refinement = new Refinement();
			refinement.setInitial(initial);
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
			doRegister(tolerance / factor, level);
		}

		// These are the normalized parameters
		private double[] parameters = null;

		public void doRegister(double tol, int level) {
			ConjugateDirectionSearch CG =
					new ConjugateDirectionSearch();

			if(parameters == null)
				parameters = new double[refinement.N];
System.out.println("num args = " + refinement.getNumArguments());

			for(int i = 0; i < 5; i++) {
				CG.optimize(refinement, parameters, tol,  tol);
				double badness = refinement.evaluate(parameters);
	
System.out.println("distance = " + badness);
				bestMatrix = refinement.getMatrix(parameters);
				bestParameters = refinement.getParameters(parameters);
				refinement.adjustInitial(parameters);
			}
		}


		/*
		 * All the arrays in this class have the order of parameters
		 * in common, which is as follows:
		 * phi - theta - psi (rotation)
		 * t_x - t_y - t_z (translation)
		 * scaleX - scaleY - scaleZ (scale)
		 * s_xy - s_xz - s_yx - s_yz - s_zx - s_zy (shear)
		 *
		 * The shear matrix is given by
		 *  |  1   s_xy  s_xz   0 |
		 *  |s_yx    1   s_yz   0 |
		 *  |s_zx  s_zy    1    0 |
		 *  |  0     0     0    1 |
		 *
		 *  where s_xy denotes the shear parallel to x along y etc.
		 */
		class Refinement implements MultivariateFunction {

			private static final int N = 15;

			private double[] initial = new double[15];

			private final double[] max = new double[] {
				Math.PI/4, Math.PI/4, Math.PI/4,// rotation
				50, 50, 50,                     // translation
				0.1, 0.1, 0.1,                  // scale
				0.1, 0.1, 0.1, 0.1, 0.1, 0.1};  // shear
			private final double[] min = new double[] {
				-Math.PI/4, -Math.PI/4, -Math.PI/4,
				-50, -50, -50,
				-0.1, -0.1, -0.1,
				-0.1, -0.1, -0.1, -0.1, -0.1, -0.1};

			private double[] factor;

			public Refinement() {
				factor = new double[N];
				for(int i = 0; i < N; i++) {
					double span = max[i] - min[i];
					if(span == 0)
						factor[i] = 1;
					else
						factor[i] = (max[5]-min[5]) /
								span;
				}
			}

			public void setInitial(FastMatrix rigid) {
				double[] euler = new double[9];
				Point3d c = getCenterOfGravity(t.orig.getImage());
				rigid.guessEulerParameters(euler, c);
				initial = new double[] {
					euler[0], euler[1], euler[2],
					euler[3], euler[4], euler[5],
					1.0, 1.0, 1.0,        // scale
					0, 0, 0, 0, 0, 0};    // shear
				for(int i = 0; i < initial.length; i++)
					initial[i] *= factor[i];
			}

			public void setInitial(double[] param) {
				initial = new double[N];
				for(int i = 0; i < N; i++) {
					initial[i] = param[i] * factor[i];
				}
			}

			public void adjustInitial(double[] a) {
				for(int i = 0; i < N; i++) {
					initial[i] += a[i];
					a[i] = 0;
				}
			}

			/*
			 * @implements getNumArguments() in MultivariateFunction
			 */
			public int getNumArguments() {
				switch(method) {
					case AFFINE: return N;
					case RIGID_ANISOTROPIC_SCALE: return 9;
					case RIGID_ISOTROPIC_SCALE: return 7;
					case RIGID: return 6;
					default: return AFFINE;
				}
			}

			/*
			 * @implements getLowerBound() in MultivariateFunction
			 */
			public double getLowerBound(int n) {
				return min[n] * factor[n];
			}

			/*
			 * @implements getUpperBound() in MultivariateFunction
			 */
			public double getUpperBound(int n) {
				return max[n] * factor[n];
			}

			/*
			 * @implements evaluate() in MultivariateFunction
			 */
			public double evaluate(double[] a) {
				t.setTransformation(getMatrix(a));
				double diff = t.getDistance();
				System.out.println("diff = " + diff);
				return diff;
			}

			public double[] getParameters(double[] a) {
				double[] arg = new double[N];
				for(int i = 0; i < N; i++)
					arg[i] = (a[i]+initial[i]) / factor[i];
				return arg;
			}

			public FastMatrix getMatrix(double[] a) {
				double[] arg = new double[N];
				int i = 0;
				for(i = 0; i < a.length; i++)
					arg[i] = (a[i]+initial[i])/factor[i];
				if(a.length == 7) { // isotropic scale
					arg[7] = (a[6]+initial[7])/factor[7];
					arg[8] = (a[6]+initial[8])/factor[8];
					i = 9;
				}
				for(; i < N; i++)
					arg[i] = initial[i] / factor[i];
				Point3d c = getCenterOfGravity(t.orig.getImage());
				FastMatrix tr = FastMatrix.translate(
						-c.x, -c.y, -c.z);
				FastMatrix tr_1 = FastMatrix.translate(
						c.x, c.y, c.z);
				FastMatrix scale = new FastMatrix(
					new double[][]{
					{arg[6], 0.0, 0.0, 0.0},
					{0.0, arg[7], 0.0, 0.0},
					{0.0, 0.0, arg[8], 0.0}});
				FastMatrix shear = new FastMatrix(
					new double[][]{
					{1, arg[9], arg[10], 0},
					{arg[11], 1, arg[12], 0},
					{arg[13], arg[14], 1, 0}});
				FastMatrix rot = FastMatrix.rotateEuler(
					arg[0], arg[1], arg[2]);
				FastMatrix trans = FastMatrix.translate(
					arg[3], arg[4], arg[5]);

				FastMatrix matrix = trans.times(tr_1)
							.times(rot)
							.times(shear)
							.times(scale)
							.times(tr);
				return matrix;
			}
		} // class refinement
	} // class optimizer
} // class AffineRegistration_
