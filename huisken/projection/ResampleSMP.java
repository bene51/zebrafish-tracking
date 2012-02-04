package huisken.projection;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.plugin.PlugIn;

import java.io.File;
import java.io.IOException;

import javax.vecmath.Point3f;

import meshtools.IndexedTriangleMesh;

public class ResampleSMP implements PlugIn {

	@Override
	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Resample");
		gd.addDirectoryField("Input directory:", "");
		gd.addNumericField("Factor: ", 2, 3);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		String datadir = gd.getNextString();
		String outputdir = new File(datadir, "resampled").getAbsolutePath();
		int f = (int)gd.getNextNumber();

		try {
			resample(datadir, outputdir, f);
		} catch(Exception e) {
			e.printStackTrace();
			IJ.error(e.getMessage());
		}
	}

	/*
	 * Overall no of vertices = 12 * (Sum_{i=1}^{subd + 2}(i) - 3) + 12 - 30 * subd;
	 *                        =
	 */
	public static void resample(String datadir, String outputdir, int f) throws IOException {
		File dataf = new File(datadir);
		String objfile = new File(dataf, "Sphere.obj").getAbsolutePath();
		SphericalMaxProjection smp = new SphericalMaxProjection(objfile);

		int n = smp.getSphere().nVertices;
		float radius = smp.getRadius();
		Point3f center = smp.getCenter();

		int subd = (int)((-20 + Math.sqrt(400 - 40 * (12 - n))) / 20 + 1);
		subd /= f;

		IJ.showStatus("Creating icosahedron");
		Icosahedron icosa = new Icosahedron(radius);

		IJ.showStatus("Creating buckyball");
		IndexedTriangleMesh sphere = icosa.createBuckyball(radius, subd);
		for(Point3f p : sphere.getVertices())
			p.add(center);

		IJ.showStatus("Creating indices");
		int[] indices = new int[sphere.nVertices];
		for(int i = 0; i < indices.length; i++)
			indices[i] = smp.getNearestNeighbor(sphere.getVertices()[i]);
		File outf = new File(outputdir);
		if(outf.exists() && outf.list() != null) {
			boolean cancelled = !IJ.showMessageWithCancel("Overwrite?", outputdir + " already exists. Overwrite?");
			if(cancelled)
				return;
		}
		outf.mkdir();
		SphericalMaxProjection.saveSphere(sphere, new File(outf, "Sphere.obj").getAbsolutePath());

		IJ.showStatus("Resampling timepoints");
		File[] files = dataf.listFiles();
		for(int i = 0; i < files.length; i++) {
			File file = files[i];
			String name = file.getName();
			if(!name.endsWith(".vertices"))
				continue;
			float[] maxima = new float[sphere.nVertices];
			smp.loadMaxima(file.getAbsolutePath());
			for(int j = 0; j < f; j++)
				smp.smooth();

			for(int j = 0; j < indices.length; j++)
				maxima[j] = smp.getMaxima()[indices[j]];

			SphericalMaxProjection.saveFloatData(maxima, new File(outf, name).getAbsolutePath());
			IJ.showProgress(i+1, files.length);
		}
	}
}
