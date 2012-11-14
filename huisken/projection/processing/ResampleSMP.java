package huisken.projection.processing;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.plugin.PlugIn;

import java.io.File;
import java.io.IOException;

import javax.vecmath.Point3f;

public class ResampleSMP implements PlugIn {

	@Override
	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Resample");
		gd.addDirectoryField("Input directory:", "");
		gd.addNumericField("Factor: ", 2, 3);
		gd.addNumericField("#layers", 1, 0);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		File datadir = new File(gd.getNextString());
		File outputdir = new File(datadir, "resampled");
		int f = (int)gd.getNextNumber();
		int nLayers = (int)gd.getNextNumber();

		try {
			resample(datadir, outputdir, f, nLayers);
		} catch(Exception e) {
			e.printStackTrace();
			IJ.error(e.getMessage());
		}
	}

	private File outputdir;
	private File inputdir;
	private File contribInDir, contribOutDir;
	private SphericalMaxProjection smp;
	private int factor;
	private int subd;
	IndexedTriangleMesh sphere;
	int[] indices;

	public void prepareResampling(File inputdir, File outputdir, int f) throws IOException {
		this.inputdir = inputdir;
		this.outputdir = outputdir;
		this.factor = f;
		this.smp = new SphericalMaxProjection(new File(inputdir, "Sphere.obj").getAbsolutePath());

		int n = smp.getSphere().nVertices;
		float radius = smp.getRadius();
		Point3f center = smp.getCenter();

		subd = (int)((-20 + Math.sqrt(400 - 40 * (12 - n))) / 20 + 1);
		subd /= factor;

		IJ.showStatus("Creating icosahedron");
		Icosahedron icosa = new Icosahedron(radius);

		IJ.showStatus("Creating buckyball");
		sphere = icosa.createBuckyball(radius, subd);
		for(Point3f p : sphere.getVertices())
			p.add(center);

		IJ.showStatus("Creating indices");
		indices = new int[sphere.nVertices];
		for(int i = 0; i < indices.length; i++)
			indices[i] = smp.getNearestNeighbor(sphere.getVertices()[i]);

		if(!outputdir.exists())
			outputdir.mkdir();

		contribInDir = new File(inputdir, "contributions");
		contribOutDir = new File(outputdir, "contributions");
		contribOutDir.mkdir();
		SphericalMaxProjection.saveSphere(sphere, new File(outputdir, "Sphere.obj").getAbsolutePath());

		// resample contributions if present
		File contfile = new File(inputdir, "contributions.vertices");
		if(!contfile.exists())
			return;
		int[] overlayold = SphericalMaxProjection.loadIntData(contfile.getAbsolutePath(), smp.getSphere().nVertices);
		int[] overlaynew = new int[sphere.nVertices];
		for(int j = 0; j < indices.length; j++)
			overlaynew[j] = overlayold[indices[j]];
		SphericalMaxProjection.saveIntData(overlaynew, new File(outputdir, "contributions.vertices").getAbsolutePath());
	}

	public void resampleTimepoint(int i, int nLayers) throws IOException {

		for(int l = 0; l < nLayers; l++) {
			String basename = String.format("tp%04d_%02d", i, l);
			String name = basename + ".vertices";
			File infile = new File(inputdir, name);
			File outfile = new File(outputdir, name);
			if(outfile.exists())
				continue;

			short[] maxima = smp.loadMaxima(infile.getAbsolutePath());
			for(int j = 0; j < factor; j++)
				smp.smooth(maxima);

			short[] resampled = new short[sphere.nVertices];
			for(int j = 0; j < indices.length; j++)
				resampled[j] = maxima[indices[j]];

			SphericalMaxProjection.saveShortData(resampled, outfile.getAbsolutePath());
		}

		String name = String.format("tp%04d_%02d", i, 0);
		File contfile = new File(contribInDir, name);
		if(!contfile.exists())
			return;
		int[] overlayold = SphericalMaxProjection.loadIntData(contfile.getAbsolutePath(), smp.getSphere().nVertices);
		int[] overlaynew = new int[sphere.nVertices];
		for(int j = 0; j < indices.length; j++)
			overlaynew[j] = overlayold[indices[j]];
		SphericalMaxProjection.saveIntData(overlaynew, new File(contribOutDir, name).getAbsolutePath());
	}

	/*
	 * Overall no of vertices = 12 * (Sum_{i=1}^{subd + 2}(i) - 3) + 12 - 30 * subd;
	 *                        =
	 */
	public static void resample(File inputdir, File outputdir, int f, int nLayers) throws IOException {
		ResampleSMP res = new ResampleSMP();
		res.prepareResampling(inputdir, outputdir, f);

		File[] files = inputdir.listFiles();
		for(int i = 0; i < files.length; i++) {
			File file = files[i];
			String name = file.getName();
			if(!name.endsWith(".vertices") || !name.startsWith("tp"))
				continue;

			int tp = Integer.parseInt(name.substring(2, 6));
			res.resampleTimepoint(tp, nLayers);
			IJ.showProgress(i+1, files.length);
		}
	}
}
