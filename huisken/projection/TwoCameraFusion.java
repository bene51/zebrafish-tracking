package huisken.projection;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.plugin.PlugIn;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.vecmath.Point3f;

import Jama.Matrix;

public class TwoCameraFusion implements PlugIn {

	private static final int LEFT    = TwoCameraSphericalMaxProjection.LEFT;
	private static final int RIGHT   = TwoCameraSphericalMaxProjection.RIGHT;
	private static final int CAMERA1 = TwoCameraSphericalMaxProjection.CAMERA1;
	private static final int CAMERA2 = TwoCameraSphericalMaxProjection.CAMERA2;

	@Override
	public void run(String args) {
		GenericDialogPlus gd = new GenericDialogPlus("Fuse from 2 cameras");
		gd.addDirectoryField("Input folder", "");
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		try {
			fuse(gd.getNextString());
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public static void fuse(String indir) throws IOException {
		if(!indir.endsWith(File.separator))
			indir += File.separator;
		final String inputdir = indir;
		final String outputdir = inputdir + "fused" + File.separator;
		if(!new File(outputdir).exists())
			new File(outputdir).mkdirs();
		final SphericalMaxProjection smp = new SphericalMaxProjection(inputdir + "Sphere.obj");

		// copy the sphere
		smp.saveSphere(outputdir + "Sphere.obj");

		Point3f center = smp.center;
		final int nVertices = smp.getSphere().nVertices;
		int aperture = 90;

		final FusionWeight[][] weights = new FusionWeight[2][2];
		weights[CAMERA1][LEFT]  = new AngleWeighter2(AngleWeighter2.X_AXIS, false,  135, aperture, center);
		weights[CAMERA1][RIGHT] = new AngleWeighter2(AngleWeighter2.X_AXIS, false, -135, aperture, center);
		weights[CAMERA2][LEFT]  = new AngleWeighter2(AngleWeighter2.X_AXIS, false,   45, aperture, center);
		weights[CAMERA2][RIGHT] = new AngleWeighter2(AngleWeighter2.X_AXIS, false,  -45, aperture, center);


		final String format = "tp%04d_a%04d_ill%d.vertices";
		int nTimepoints = 0;
		while(new File(inputdir, String.format(format, nTimepoints, 0, LEFT)).exists())
			nTimepoints++;

		final int nProcessors = Runtime.getRuntime().availableProcessors();
		ExecutorService exec = Executors.newFixedThreadPool(nProcessors);
		int nTimepointsPerThread = (int)Math.ceil(nTimepoints / (double)nProcessors);
		for(int p = 0; p < nProcessors; p++) {
			final int start = p * nTimepointsPerThread;
			final int end = Math.min(nTimepoints, (p + 1) * nTimepointsPerThread);

			exec.submit(new Runnable() {
				@Override
				public void run() {
					for(int tp = start; tp < end; tp++) {
						try {
							IJ.log("Fusing timepoint " + tp);
							File out = new File(outputdir, String.format("tp%04d.vertices", tp));
							if(out.exists())
								continue;

							float[] m1 = SphericalMaxProjection.loadFloatData(inputdir + String.format(format, tp, 180, LEFT),  nVertices);
							float[] m2 = SphericalMaxProjection.loadFloatData(inputdir + String.format(format, tp, 180, RIGHT), nVertices);
							float[] m3 = SphericalMaxProjection.loadFloatData(inputdir + String.format(format, tp,   0, LEFT),  nVertices);
							float[] m4 = SphericalMaxProjection.loadFloatData(inputdir + String.format(format, tp,   0, RIGHT), nVertices);

							Point3f[] vertices = smp.getSphere().getVertices();
							for(int v = 0; v < vertices.length; v++) {
								Point3f vertex = vertices[v];
								float w1 = weights[CAMERA1][LEFT ].getWeight(vertex.x, vertex.y, vertex.z);
								float w2 = weights[CAMERA1][RIGHT].getWeight(vertex.x, vertex.y, vertex.z);
								float w3 = weights[CAMERA2][LEFT ].getWeight(vertex.x, vertex.y, vertex.z);
								float w4 = weights[CAMERA2][RIGHT].getWeight(vertex.x, vertex.y, vertex.z);
								float sum = w1 + w2 + w3 + w4;
								if(sum != 1)
									System.out.println("sum = " + sum);
								m1[v] = (w1 * m1[v] + w2 * m2[v] + w3 * m3[v] + w4 * m4[v]);

							}
							SphericalMaxProjection.saveFloatData(m1, out.getAbsolutePath());
						} catch(Exception e) {
							e.printStackTrace();
							System.out.println("Couldn't fuse timepoint " + tp);
						}
					}
				}
			});
		}
	}

	public static void fuse2(String inputdir) throws IOException {
		if(!inputdir.endsWith(File.separator))
			inputdir += File.separator;
		String outputdir = inputdir + "fused" + File.separator;
		if(new File(outputdir).exists())
			new File(outputdir).mkdirs();
		SphericalMaxProjection[][] smp = new SphericalMaxProjection[2][2];
		smp[CAMERA1][LEFT]  = new SphericalMaxProjection(inputdir + "Sphere.obj");
		smp[CAMERA1][RIGHT] = smp[CAMERA1][LEFT].clone();
		smp[CAMERA2][LEFT]  = smp[CAMERA1][LEFT].clone();
		smp[CAMERA2][RIGHT] = smp[CAMERA1][LEFT].clone();

		Point3f center = smp[CAMERA1][LEFT].center;
		int aperture = 90;

		AngleWeighter2[][] weights = new AngleWeighter2[2][2];
		weights[CAMERA1][LEFT]  = new AngleWeighter2(AngleWeighter2.X_AXIS, false,  135, aperture, center);
		weights[CAMERA1][RIGHT] = new AngleWeighter2(AngleWeighter2.X_AXIS, false, -135, aperture, center);
		weights[CAMERA2][LEFT]  = new AngleWeighter2(AngleWeighter2.X_AXIS, false,   45, aperture, center);
		weights[CAMERA2][RIGHT] = new AngleWeighter2(AngleWeighter2.X_AXIS, false,  -45, aperture, center);

		String format = "tp%04d_a%04d_ill%d.vertices";
		int tp = 0;
		while(new File(inputdir, String.format(format, tp, 0, LEFT)).exists()) {
			IJ.log("Fusing timepoint " + tp);
			smp[CAMERA1][LEFT ].loadMaxima(inputdir + String.format(format, tp, 180, LEFT));
			smp[CAMERA1][RIGHT].loadMaxima(inputdir + String.format(format, tp, 180, RIGHT));
			smp[CAMERA2][LEFT ].loadMaxima(inputdir + String.format(format, tp,   0, LEFT));
			smp[CAMERA2][RIGHT].loadMaxima(inputdir + String.format(format, tp,   0, RIGHT));

			Point3f[] vertices = smp[CAMERA1][LEFT].getSphere().getVertices();
			float[] m1 = smp[CAMERA1][LEFT].getMaxima();
			float[] m2 = smp[CAMERA1][RIGHT].getMaxima();
			float[] m3 = smp[CAMERA2][LEFT].getMaxima();
			float[] m4 = smp[CAMERA2][RIGHT].getMaxima();

			float[][][] mean = new float[2][2][2];
			float[][][] count = new float[2][2][2];

			for(int v = 0; v < vertices.length; v++) {
				Point3f vertex = vertices[v];
				double dx = vertex.x - center.x;
				double dy = vertex.y - center.y;
				double dz = vertex.z - center.z;

				for(int cam = CAMERA1; cam <= CAMERA2; cam++) {
					for(int ill = LEFT; ill <= RIGHT; ill++) {
						double angle = weights[cam][ill].getAngle(dx, dy, dz);
						if(weights[cam][ill].inLowerOverlap(angle)) {
							mean[cam][ill][0] += smp[cam][ill].getMaxima()[v];
							count[cam][ill][0]++;
						} else if(weights[cam][ill].inUpperOverlap(angle)) {
							mean[cam][ill][1] += smp[cam][ill].getMaxima()[v];
							count[cam][ill][1]++;
						}
					}
				}
			}
			for(int cam = CAMERA1; cam <= CAMERA2; cam++)
				for(int ill = LEFT; ill <= RIGHT; ill++)
					for(int i = 0; i < 2; i++)
						mean[cam][ill][i] /= count[cam][ill][i];

			/**
			This is the actual matrix, but it is singular, we get infinitely many solutions,
			because the whole level can be lifted. So we fix one level, e.g. a1, at zero.

			double[][] A = new double[][] {
				{ 2, -1,  0, -1},
				{-1,  2, -1,  0},
				{ 0, -1,  2, -1},
				{-1,  0, -1,  2}
			};

			float[] a1 = mean[CAMERA1][LEFT];
			float[] a2 = mean[CAMERA2][LEFT];
			float[] a3 = mean[CAMERA2][RIGHT];
			float[] a4 = mean[CAMERA1][RIGHT];
			double[] b = new double[] {
				a1[0] - a4[1] + a1[1] - a2[0],
				a2[0] - a1[1] + a2[1] - a3[0],
				a3[0] - a2[1] + a3[1] - a4[0],
				a4[0] - a3[1] + a4[1] - a1[0],
			};
			double[] x = new Matrix(A).solve(new Matrix(b, 4)).getColumnPackedCopy();
			*/
			double[][] A_inv = new double[][] {
				 { 0.75, 0.50, 0.25 },
				 { 0.50, 1.00, 0.25 },
				 { 0.25, 0.50, 0.25 },
			};
			float[] a1 = mean[CAMERA1][LEFT];
			float[] a2 = mean[CAMERA2][LEFT];
			float[] a3 = mean[CAMERA2][RIGHT];
			float[] a4 = mean[CAMERA1][RIGHT];
			double[] b = new double[] {
				-a2[0] + a1[1] - a2[1] + a3[0],
				-a3[0] + a2[1] - a3[1] + a4[0],
				-a4[0] + a3[1] - a4[1] + a1[0],
			};
			double[] tmp = new Matrix(A_inv).times(new Matrix(b, 3)).getColumnPackedCopy();
			double[] x = new double[] {0, tmp[0], tmp[1], tmp[2]};
			System.out.println("shifts: " + Arrays.toString(x));

			for(int v = 0; v < vertices.length; v++) {
				Point3f vertex = vertices[v];
				float w1 = weights[CAMERA1][LEFT ].getWeight(vertex.x, vertex.y, vertex.z);
				float w2 = weights[CAMERA1][RIGHT].getWeight(vertex.x, vertex.y, vertex.z);
				float w3 = weights[CAMERA2][LEFT ].getWeight(vertex.x, vertex.y, vertex.z);
				float w4 = weights[CAMERA2][RIGHT].getWeight(vertex.x, vertex.y, vertex.z);

				float sum = w1 + w2 + w3 + w4;
				if(sum != 1)
					System.out.println("sum = " + sum);
				m1[v] = (float)(
					w1 * (m1[v] + x[0]) +
					w2 * (m2[v] + x[3]) +
					w3 * (m3[v] + x[1]) +
					w4 * (m4[v] + x[2]));

			}

			smp[CAMERA1][LEFT].saveMaxima(outputdir + String.format("tp%04d.vertices", tp));
			tp++;
		}
	}
}
