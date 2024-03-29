package huisken.projection.processing;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.plugin.PlugIn;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;

public class TwoCameraFusion implements PlugIn {

	private static final int LEFT    = TwoCameraSphericalMaxProjection.LEFT;
	private static final int RIGHT   = TwoCameraSphericalMaxProjection.RIGHT;
	private static final int CAMERA1 = TwoCameraSphericalMaxProjection.CAMERA1;
	private static final int CAMERA2 = TwoCameraSphericalMaxProjection.CAMERA2;

	private InputFolder inputdir;

	private File outputdir;
	private SphericalMaxProjection smp;
	private FusionWeight[][][] weights;
	private Matrix4f[] transforms;
	private boolean saveOutput;
	private int angleInc;
	private int nAngles;
	private int nLayers;

	// private static final String format = "tp%04d_a%04d.vertices";
	private static final boolean adjustModes = false;


	@Override
	public void run(String args) {
		GenericDialogPlus gd = new GenericDialogPlus("Fuse from 2 cameras");
		gd.addDirectoryField("Input folder", "");
		gd.addNumericField("#angles", 1, 0);
		gd.addNumericField("#angleInc", 45, 0);
		gd.addNumericField("#layers", 1, 0);
		gd.addFileField("Transformations", "");
		gd.addCheckbox("Adjust modes to compensate for intensity differences", false);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		String folder = gd.getNextString();
		int nAngles = (int)gd.getNextNumber();
		int angleInc = (int)gd.getNextNumber();
		int nLayers = (int)gd.getNextNumber();
		String transformationFile = gd.getNextString();
		boolean adjustModes = gd.getNextBoolean();

		Matrix4f[] transformations = new Matrix4f[1];
		if(nAngles > 1) {
			try {
				transformations = TwoCameraSphericalMaxProjection.loadTransformations(transformationFile);
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		final int[][][] colors = new int[2][2][nAngles];
//		final Button[][][] buttons = new Button[2][2][nAngles];
//		class ColorActionListener implements ActionListener {
//			private final int cam, ill, a;
//			public ColorActionListener(int cam, int ill, int a) {
//				this.cam = cam; this.ill = ill; this.a = a;
//			}
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				Color c = JColorChooser.showDialog(
//						null,
//						"Choose Background Color",
//						Color.RED);
//				colors[cam][ill][a] = c.getRGB();
//				buttons[cam][ill][a].setForeground(c);
//			}
//		}
//		gd = new GenericDialogPlus("Colors");
//		for(int a = 0; a < nAngles; a++) {
//			gd.addButton("Camera1_Left_Illumination", new ColorActionListener(CAMERA1, LEFT, a));
//			buttons[CAMERA1][LEFT] [a] = (Button)gd.getComponent(gd.getComponentCount() - 1);
//			gd.addButton("Camera1_Right_Illumination", new ColorActionListener(CAMERA1, RIGHT, a));
//			buttons[CAMERA1][RIGHT][a] = (Button)gd.getComponent(gd.getComponentCount() - 1);
//			gd.addButton("Camera2_Left_Illumination", new ColorActionListener(CAMERA2, LEFT, a));
//			buttons[CAMERA2][LEFT] [a] = (Button)gd.getComponent(gd.getComponentCount() - 1);
//			gd.addButton("Camera2_Right_Illumination", new ColorActionListener(CAMERA2, RIGHT, a));
//			buttons[CAMERA2][RIGHT][a] = (Button)gd.getComponent(gd.getComponentCount() - 1);
//		}
//		gd.showDialog();
//		if(gd.wasCanceled())
//			return;

		colors[CAMERA1][LEFT] [0] = colors[CAMERA1][RIGHT][0] = new Color(206, 47, 42).getRGB();
		colors[CAMERA2][LEFT] [0] = colors[CAMERA2][RIGHT][0] = new Color(241, 183, 51).getRGB();
//		colors[CAMERA1][LEFT] [1] = colors[CAMERA1][RIGHT][1] = new Color(162, 198, 231).getRGB();
//		colors[CAMERA2][LEFT] [1] = colors[CAMERA2][RIGHT][1] = new Color(42, 76, 149).getRGB();

		try {
			fuse(folder, nAngles, angleInc, nLayers, transformations, adjustModes, colors, true);
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public static void fuse(String indir, int nAngles, int angleInc, int nLayers, final Matrix4f[] transformations, final  boolean adjustModes, int[][][] colors, boolean saveOutput) throws IOException {
		if(!indir.endsWith(File.separator))
			indir += File.separator;
		final String inputdir = indir;

		final TwoCameraFusion tcf = new TwoCameraFusion();
		tcf.prepareFusion(inputdir, nAngles, angleInc, nLayers, transformations, saveOutput);
		tcf.indicateCameraContributions(colors);
		tcf.testCameraFusion();

		final Set<Integer> tps = new TreeSet<Integer>();
		for(File f : new File(inputdir).listFiles()) {
			String name = f.getName();
			if(name.startsWith("tp") && name.endsWith(".vertices")) {
				int n = Integer.parseInt(name.substring(2, 6));
				tps.add(n);
			}
		}


		int nTimepoints = tps.size();
		final ArrayList<Integer> timepoints = new ArrayList<Integer>(tps);

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
							tcf.fuse(timepoints.get(tp));
						} catch(Exception e) {
							e.printStackTrace();
							System.out.println("Couldn't fuse timepoint " + tp);
						}
					}
				}
			});
		}
		try {
			exec.shutdown();
			exec.awaitTermination(300, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Prepare fusion, using a single input folder.
	 * @param indir
	 * @param nAngles
	 * @param angleInc
	 * @param transformations
	 * @param saveOutput
	 * @throws IOException
	 */
	public void prepareFusion(String indir, int nAngles, int angleInc, int nLayers, Matrix4f[] transformations, boolean saveOutput) throws IOException {
		if(!indir.endsWith(File.separator))
			indir += File.separator;
		this.inputdir = new SingleInputFolder(new File(indir));
		this.saveOutput = saveOutput;
		this.smp = new SphericalMaxProjection(indir + "Sphere.obj");
		this.outputdir = new File(indir, "fused");
		this.angleInc = angleInc;
		this.nAngles = nAngles;
		this.nLayers = nLayers;
		this.transforms = transformations;

		// see TwoCameraSphericalMaxProjection.initSphericalMaximumProjection
		int aperture = 90 / nAngles;

		if(saveOutput) {
			if(!outputdir.exists())
				outputdir.mkdirs();

			smp.saveSphere(new File(outputdir, "Sphere.obj").getAbsolutePath());
		}

		Point3f center = smp.getCenter();

		weights = new FusionWeight[2][2][nAngles];

		for(int a = 0; a < nAngles; a++) {
			Point3f cen = new Point3f(center);
			if(transforms[a] != null)
				transforms[a].transform(cen);
			weights[CAMERA1][LEFT] [a] = new AngleWeighter2(AngleWeighter2.X_AXIS,  135, aperture, new Point3f(cen));
			weights[CAMERA1][RIGHT][a] = new AngleWeighter2(AngleWeighter2.X_AXIS, -135, aperture, new Point3f(cen));
			weights[CAMERA2][LEFT] [a] = new AngleWeighter2(AngleWeighter2.X_AXIS,   45, aperture, new Point3f(cen));
			weights[CAMERA2][RIGHT][a] = new AngleWeighter2(AngleWeighter2.X_AXIS,  -45, aperture, new Point3f(cen));
		}
	}

	public void prepareFusion(File cam1Folder, File cam2Folder, File outputdir, int nAngles, int angleInc, Matrix4f[] transformations, boolean saveOutput) throws IOException {
		this.inputdir = new DoubleInputFolder(cam1Folder, cam2Folder);
		this.saveOutput = saveOutput;
		this.smp = new SphericalMaxProjection(new File(cam1Folder, "Sphere.obj").getAbsolutePath());
		this.outputdir = outputdir;
		this.angleInc = angleInc;
		this.nAngles = nAngles;
		this.transforms = transformations;

		// see TwoCameraSphericalMaxProjection.initSphericalMaximumProjection
		int aperture = 90 / nAngles;

		if(saveOutput) {
			if(!outputdir.exists())
				outputdir.mkdirs();

			smp.saveSphere(new File(outputdir, "Sphere.obj").getAbsolutePath());
		}

		Point3f center = smp.getCenter();

		weights = new FusionWeight[2][2][nAngles];

		for(int a = 0; a < nAngles; a++) {
			Point3f cen = new Point3f(center);
			if(transforms[a] != null)
				transforms[a].transform(cen);
			weights[CAMERA1][LEFT] [a] = new AngleWeighter2(AngleWeighter2.X_AXIS,  135, aperture, new Point3f(cen));
			weights[CAMERA1][RIGHT][a] = new AngleWeighter2(AngleWeighter2.X_AXIS, -135, aperture, new Point3f(cen));
			weights[CAMERA2][LEFT] [a] = new AngleWeighter2(AngleWeighter2.X_AXIS,   45, aperture, new Point3f(cen));
			weights[CAMERA2][RIGHT][a] = new AngleWeighter2(AngleWeighter2.X_AXIS,  -45, aperture, new Point3f(cen));
		}
	}

	public void testCameraFusion() throws IOException {
		File dir = new File(outputdir, "test");
		dir.mkdir();

		Point3f[] vertices = smp.getSphere().getVertices();
		for(int ill = 0; ill < 2; ill++) {
			for(int cam = 0; cam < 2; cam++) {
				for(int a = 0; a < nAngles; a++) {
					File outf = new File(dir, getFile(0, a, angleInc, cam, ill, 0));
					if(outf.exists())
						continue;
					short[] res = new short[vertices.length];
					for(int v = 0; v < vertices.length; v++) {
						Point3f vertex = new Point3f(vertices[v]);
						if(transforms[a] != null)
							transforms[a].transform(vertex);
						res[v] = (short)(100 * weights[cam][ill][a].getWeight(vertex.x, vertex.y, vertex.z));
					}
					SphericalMaxProjection.saveShortData(res, outf.getAbsolutePath());
				}
			}
		}
	}

	public void indicateCameraContributions(int[][][] colors) throws IOException {
		File out = new File(outputdir, "contributions.vertices");
		if(out.exists())
			return;

		Point3f[] vertices = smp.getSphere().getVertices();
		int[] res = new int[vertices.length];
		for(int v = 0; v < vertices.length; v++) {
			Point3f vertex = vertices[v];
			double r = 0, g = 0, b = 0;
			double sum = 0;
			for(int a = 0; a < nAngles; a++) {
				Point3f xvtx = new Point3f(vertex);
				if(transforms[a] != null)
					transforms[a].transform(xvtx);

				float w1 = weights[CAMERA1][LEFT ][a].getWeight(xvtx.x, xvtx.y, xvtx.z);
				float w2 = weights[CAMERA1][RIGHT][a].getWeight(xvtx.x, xvtx.y, xvtx.z);
				float w3 = weights[CAMERA2][LEFT ][a].getWeight(xvtx.x, xvtx.y, xvtx.z);
				float w4 = weights[CAMERA2][RIGHT][a].getWeight(xvtx.x, xvtx.y, xvtx.z);

				int c1 = colors[CAMERA1][LEFT ][a];
				int c2 = colors[CAMERA1][RIGHT][a];
				int c3 = colors[CAMERA2][LEFT ][a];
				int c4 = colors[CAMERA2][RIGHT][a];

				int r1 = (c1 & 0xff0000) >> 16, g1 = (c1 & 0xff00) >> 8, b1 = (c1 & 0xff);
				int r2 = (c2 & 0xff0000) >> 16, g2 = (c2 & 0xff00) >> 8, b2 = (c2 & 0xff);
				int r3 = (c3 & 0xff0000) >> 16, g3 = (c3 & 0xff00) >> 8, b3 = (c3 & 0xff);
				int r4 = (c4 & 0xff0000) >> 16, g4 = (c4 & 0xff00) >> 8, b4 = (c4 & 0xff);

				double rc = w1 * r1 + w2 * r2 + w3 * r3 + w4 * r4;
				double gc = w1 * g1 + w2 * g2 + w3 * g3 + w4 * g4;
				double bc = w1 * b1 + w2 * b2 + w3 * b3 + w4 * b4;

				r += rc;
				g += gc;
				b += bc;

				sum += w1 + w2 + w3 + w4;
			}
			r /= sum;
			g /= sum;
			b /= sum;

			int ir = r > 255 ? 255 : (int)r;
			int ig = g > 255 ? 255 : (int)g;
			int ib = b > 255 ? 255 : (int)b;
			res[v] = (ir << 16) + (ig << 8) + ib;
		}
		SphericalMaxProjection.saveIntData(res, out.getAbsolutePath());
	}

	private String getFile(int tp, int aIndex, int angleInc, int camera, int ill, int layer) {
		return TwoCameraSphericalMaxProjection.getFileName(tp, aIndex, angleInc, camera, ill, layer);
	}

	public void fuse(int tp) throws IOException {
		boolean done = true;
		for(int l = 0; l < nLayers; l++) {
			if(!new File(outputdir, String.format("tp%04d_%02d.vertices", tp, l)).exists()) {
				done = false;
				break;
			}
		}
		if(done)
			return;

		int nVertices = smp.getSphere().nVertices;

		short[][][][][] m = new short[2][2][nAngles][nLayers][];
		for(int a = 0; a < nAngles; a++) {
			for(int l = 0; l < nLayers; l++) {
				m[CAMERA1][LEFT] [a][l] = SphericalMaxProjection.loadShortData(inputdir.getFile(CAMERA1, getFile(tp, a, angleInc, CAMERA1, LEFT, l)).getAbsolutePath(),  nVertices);
				m[CAMERA1][RIGHT][a][l] = SphericalMaxProjection.loadShortData(inputdir.getFile(CAMERA1, getFile(tp, a, angleInc, CAMERA1, RIGHT, l)).getAbsolutePath(), nVertices);
				m[CAMERA2][LEFT] [a][l] = SphericalMaxProjection.loadShortData(inputdir.getFile(CAMERA2, getFile(tp, a, angleInc, CAMERA2, LEFT, l)).getAbsolutePath(),  nVertices);
				m[CAMERA2][RIGHT][a][l] = SphericalMaxProjection.loadShortData(inputdir.getFile(CAMERA2, getFile(tp, a, angleInc, CAMERA2, RIGHT, l)).getAbsolutePath(), nVertices);
			}
		}


		if(adjustModes) {
			float refmode = SphericalMaxProjection.getMode(m[CAMERA1][LEFT][0][0]);
			for(int a = 0; a < nAngles; a++) {
				short[] data = m[CAMERA1][LEFT][a][0];
				float mode = SphericalMaxProjection.getMode(data);
				SphericalMaxProjection.add(data, (short)(refmode - mode));

				data = m[CAMERA1][RIGHT][a][0];
				mode = SphericalMaxProjection.getMode(data);
				SphericalMaxProjection.add(data, (short)(refmode - mode));

				data = m[CAMERA2][LEFT][a][0];
				mode = SphericalMaxProjection.getMode(data);
				SphericalMaxProjection.add(data, (short)(refmode - mode));

				data = m[CAMERA2][RIGHT][a][0];
				mode = SphericalMaxProjection.getMode(data);
				SphericalMaxProjection.add(data, (short)(refmode - mode));
			}
		}

		for(int l = 0; l < nLayers; l++) {
			File out = new File(outputdir, String.format("tp%04d_%02d.vertices", tp, l));
			Point3f[] vertices = smp.getSphere().getVertices();
			float[] res = new float[vertices.length];
			for(int v = 0; v < vertices.length; v++) {
				Point3f vertex = vertices[v];
				float sum = 0;
				res[v] = 0;
				for(int a = 0; a < nAngles; a++) {
					Point3f xvtx = new Point3f(vertex);
					if(transforms[a] != null)
						transforms[a].transform(xvtx);
					float w1 = weights[CAMERA1][LEFT ][a].getWeight(xvtx.x, xvtx.y, xvtx.z);
					float w2 = weights[CAMERA1][RIGHT][a].getWeight(xvtx.x, xvtx.y, xvtx.z);
					float w3 = weights[CAMERA2][LEFT ][a].getWeight(xvtx.x, xvtx.y, xvtx.z);
					float w4 = weights[CAMERA2][RIGHT][a].getWeight(xvtx.x, xvtx.y, xvtx.z);
					float m1 = (m[CAMERA1][LEFT ][a][l][v] & 0xffff);
					float m2 = (m[CAMERA1][RIGHT][a][l][v] & 0xffff);
					float m3 = (m[CAMERA2][LEFT ][a][l][v] & 0xffff);
					float m4 = (m[CAMERA2][RIGHT][a][l][v] & 0xffff);
					sum += w1 + w2 + w3 + w4;
					res[v] += (w1 * m1 + w2 * m2 + w3 * m3 + w4 * m4);
				}
				res[v] = sum == 0 ? 0 : res[v] / sum;
			}
			short[] sData = new short[res.length];
			for(int v = 0; v < res.length; v++)
				sData[v] = (short)res[v];

			if(saveOutput)
				SphericalMaxProjection.saveShortData(sData, out.getAbsolutePath());
		}
	}

	static interface InputFolder {
		File getFile(int camera, String filename);
	}

	static class SingleInputFolder implements InputFolder {

		private final File folder;

		SingleInputFolder(File folder) {
			this.folder = folder;
		}

		@Override
		public File getFile(int camera, String file) {
			return new File(folder, file);
		}
	}

	static class DoubleInputFolder implements InputFolder {

		private final File cam1Folder, cam2Folder;

		DoubleInputFolder(File cam1Folder, File cam2Folder) {
			this.cam1Folder = cam1Folder;
			this.cam2Folder = cam2Folder;
		}

		@Override
		public File getFile(int camera, String file) {
			File f = new File(camera == CAMERA1 ? cam1Folder : cam2Folder, file);
			while(!f.exists()) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			return f;
		}
	}
}
