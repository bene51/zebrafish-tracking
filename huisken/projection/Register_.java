package huisken.projection;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.plugin.PlugIn;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;

import java.awt.Polygon;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;


public class Register_ implements PlugIn {

	@Override
	public void run(String arg) {
		String datadir = Prefs.get("register_sphere_proj.datadir", "");
		String outputdir = Prefs.get("register_sphere_proj.outputdir", "");
		GenericDialogPlus gd = new GenericDialogPlus("Register sphere projections");
		gd.addDirectoryField("Data directory", datadir);
		gd.addDirectoryField("Output directory", outputdir);
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		datadir = gd.getNextString();
		outputdir = gd.getNextString();

		Prefs.set("register_sphere_proj.datadir", datadir);
		Prefs.set("register_sphere_proj.outputdir", outputdir);
		Prefs.savePreferences();

		try {
			register(datadir, outputdir);
		} catch(Exception e) {
		IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public void createFullerProjection(SphericalMaxProjection smp, String datadir) throws IOException {
		File outputdir = new File(datadir, "fuller");
		if(outputdir.exists()) {
			boolean cancelled = !IJ.showMessageWithCancel("Recalculate Fuller projection?", "Recalculate Fuller projection?");
			if(cancelled)
				return;
		} else {
			outputdir.mkdirs();
		}

		int w = 1000;

		FullerProjection proj = new FullerProjection();
		proj.prepareForProjection(smp, w);

		GaussianBlur gauss = new GaussianBlur();

		for(File file : new File(datadir).listFiles()) {
			String filename = file.getName();
			if(!filename.startsWith("tp") || !filename.endsWith(".vertices"))
				continue;

			smp.loadMaxima(file.getAbsolutePath());
			filename = filename.substring(0, filename.length() - 9) + ".tif";
			ImageProcessor fuller = proj.project(smp.getMaxima());
			IJ.save(new ImagePlus("", fuller), new File(outputdir, filename).getAbsolutePath());

			gauss.blur(fuller, 1.5);
			Polygon pt = new MaximumFinder().getMaxima(fuller, 10, true);

			ArrayList<Point3f> pts = new ArrayList<Point3f>();
			for(int i = 0; i < pt.npoints; i++) {
				int x = pt.xpoints[i];
				int y = pt.ypoints[i];

				Point3f ptmp = new Point3f();
				if(proj.getPointOnSphere(x, y, ptmp))
					pts.add(ptmp);
			}
			filename = file.getName();
			filename = filename.substring(0, filename.length() - 9) + ".pts";
			savePoints(pts, new File(outputdir, filename));
		}
	}

	private void savePoints(ArrayList<Point3f> pts, File outfile) throws IOException {
		PrintStream out = new PrintStream(new FileOutputStream(outfile));
		for(Point3f p : pts)
			out.println(p.x + " " + p.y + " " + p.z);
		out.close();
	}

	private ArrayList<Point3f> loadPoints(File file) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(file));
		String line = null;
		ArrayList<Point3f> list = new ArrayList<Point3f>();
		while((line = in.readLine()) != null) {
			String[] toks = line.split(" ");
			list.add(new Point3f(Float.parseFloat(toks[0]), Float.parseFloat(toks[1]), Float.parseFloat(toks[2])));
		}
		return list;
	}

	public void register(String dataDirectory, String outputDirectory) throws IOException {
		// check and create files and folders
		if(!new File(dataDirectory).isDirectory())
			throw new IllegalArgumentException(dataDirectory + " is not a directory");

		File outputdir = new File(outputDirectory);
		if(outputdir.isDirectory() && outputdir.list().length > 0) {
			boolean cancelled = !IJ.showMessageWithCancel("Overwrite",
					outputdir + " already exists. Overwrite?");
			if(cancelled)
				return;
		} else {
			outputdir.mkdir();
		}

		File objfile = new File(dataDirectory, "Sphere.obj");
		if(!objfile.exists())
			throw new IllegalArgumentException("Cannot find " + objfile.getAbsolutePath());


		if(!dataDirectory.endsWith(File.separator))
			dataDirectory += File.separator;
		if(!outputDirectory.endsWith(File.separator))
			outputDirectory += File.separator;
		String contributionsDirectory = outputDirectory + File.separator + "contributions" + File.separator;
		new File(contributionsDirectory).mkdir();
		String matrixDirectory = outputDirectory + File.separator + "transformations" + File.separator;
		new File(matrixDirectory).mkdir();

		SphericalMaxProjection src = new SphericalMaxProjection(objfile.getAbsolutePath());
		createFullerProjection(src, dataDirectory);

		// obtain list of local maxima files
		List<String> tmp = new ArrayList<String>();
		File fullerDir = new File(dataDirectory, "fuller");
		tmp.addAll(Arrays.asList(fullerDir.list()));
		for(int i = tmp.size() - 1; i >= 0; i--)
			if(!tmp.get(i).endsWith(".pts"))
				tmp.remove(i);
		String[] files = new String[tmp.size()];
		tmp.toArray(files);
		Arrays.sort(files);

		// load spherical maximum projection for source
		src.saveSphere(outputDirectory + "Sphere.obj");
		String vName = files[0].substring(0, files[0].lastIndexOf('.')) + ".vertices";
		src.loadMaxima(dataDirectory + vName);
		src.saveMaxima(outputDirectory + vName);

		Matrix4f overall = new Matrix4f();
		overall.setIdentity();

		ArrayList<Point3f> tgtPts = loadPoints(new File(fullerDir, files[0]));
		float[] contrib = SphericalMaxProjection.loadFloatData(dataDirectory + "contributions.vertices",
					src.getSphere().nVertices);
		SphericalMaxProjection.saveFloatData(contrib, contributionsDirectory + vName);

		// register
		for(int i = 1; i < files.length; i++) {
			System.out.println(files[i]);
			ArrayList<Point3f> nextTgtPts = null, srcPts = null;

			String matName = files[i].substring(0, files[i].lastIndexOf('.')) + ".matrix";
			if(!new File(matrixDirectory, matName).exists()) {

				srcPts = loadPoints(new File(fullerDir, files[i]));

				// make a deep copy of src points, to be used as target points for the next iteration
				nextTgtPts = new ArrayList<Point3f>(srcPts.size());
				for(Point3f p : srcPts)
					nextTgtPts.add(new Point3f(p));

				Matrix4f mat = new Matrix4f();
				mat.setIdentity();
				ICPRegistration.register(tgtPts, srcPts, mat, src.center);
				overall.mul(mat);

				saveTransform(overall, matrixDirectory + matName);
			} else {
				overall = loadTransform(matrixDirectory + matName);
			}

			vName = files[i].substring(0, files[i].lastIndexOf('.')) + ".vertices";
			if(!new File(outputDirectory, vName).exists()) {
				src.loadMaxima(dataDirectory + vName);
				src.applyTransform(overall);
				src.saveMaxima(outputDirectory + vName);
			}

			if(!new File(contributionsDirectory, vName).exists()) {
				src.setMaxima(contrib);
				src.applyTransformNearestNeighbor(overall);
				src.saveMaxima(contributionsDirectory + vName);
			}

			tgtPts = nextTgtPts;
			IJ.showProgress(i, files.length);
		}
		IJ.showProgress(1);
	}

	public static Matrix4f loadTransform(String path) throws IOException {
		Matrix4f ret = new Matrix4f();
		BufferedReader in = new BufferedReader(new FileReader(path));
		for(int r = 0; r < 4; r++)
			for(int c = 0; c < 4; c++)
				ret.setElement(r, c, Float.parseFloat(in.readLine()));
		in.close();
		return ret;
	}

	public static void saveTransform(Matrix4f matrix, String path) throws IOException {
		PrintWriter out = new PrintWriter(new FileWriter(path));
		for(int r = 0; r < 4; r++)
			for(int c = 0; c < 4; c++)
				out.println(Float.toString(matrix.getElement(r, c)));
		out.close();
	}
}
