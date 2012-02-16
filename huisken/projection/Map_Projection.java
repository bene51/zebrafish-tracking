package huisken.projection;

import fiji.util.gui.GenericDialogPlus;
import huisken.projection.viz.SphereProjectionViewer;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.WaitForUserDialog;
import ij.plugin.PlugIn;
import ij3d.Image3DUniverse;

import java.awt.geom.GeneralPath;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.media.j3d.Transform3D;
import javax.vecmath.Matrix4f;

import com.jhlabs.map.proj.AugustProjection;
import com.jhlabs.map.proj.BonneProjection;
import com.jhlabs.map.proj.GallProjection;
import com.jhlabs.map.proj.Kavraisky7Projection;
import com.jhlabs.map.proj.MercatorProjection;
import com.jhlabs.map.proj.OrthographicAzimuthalProjection;
import com.jhlabs.map.proj.WinkelTripelProjection;


public class Map_Projection implements PlugIn {

	public static final int MERCATOR              = 0;
	public static final int GALLPETER             = 1;
	public static final int KAVRAYSKIY            = 2;
	public static final int WINKEL_TRIPEL         = 3;
	public static final int AUGUSTUS_EPICYCLOIDAL = 4;
	public static final int BONNE                 = 5;
	public static final int ORTHO_AZIMUTAL        = 6;

	public static final String[] MAP_TYPES = new String[] {
		"Mercator",
		"Gall-Peter",
		"Kavrayskiy",
		"Winkel Tripel",
		"Augustus Epicycloidal",
		"Bonne",
		"Orthogonal Azimutal"};

	@Override
	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Create 2D Maps");
		gd.addDirectoryField("Data directory", "/Volumes/BENE/PostDoc/SphereProjection/registered");
		// gd.addDirectoryField("Output directory", "/Volumes/BENE/PostDoc/SphereProjection/registered");
		// gd.addChoice("Map type", MAP_TYPES, MAP_TYPES[3]);
		gd.addCheckbox("Create coastlines", false);
		gd.addCheckbox("Create Longitude/Latitude lines", false);
		for(int i = 0; i < MAP_TYPES.length; i++)
			gd.addCheckbox(MAP_TYPES[i], true);
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		File datadir = new File(gd.getNextString());
		// int mapType = gd.getNextChoiceIndex();
		boolean doCoast = gd.getNextBoolean();
		boolean doLines = gd.getNextBoolean();

		if(!datadir.isDirectory()) {
			IJ.error(datadir + " is not a directory");
			return;
		}

		File objfile = new File(datadir, "Sphere.obj");
		if(!objfile.exists()) {
			IJ.error("Cannot find " + objfile.getAbsolutePath());
			return;
		}

		File previewdir = new File(datadir, "resampled");
		if(!previewdir.exists() || !previewdir.isDirectory())
			previewdir = datadir;

		Matrix4f initial = new Matrix4f();
		Image3DUniverse univ = SphereProjectionViewer.show(previewdir.getAbsolutePath() + "/Sphere.obj", previewdir.getAbsolutePath(), null);
		new WaitForUserDialog("",
			"Please rotate the sphere to the desired orientation, then click OK").show();
		Transform3D trans = new Transform3D();
		univ.getContent("bla").getLocalRotate(trans);
		trans.get(initial);
		univ.close();

		for(int i = 0; i < MAP_TYPES.length; i++) {
			if(!gd.getNextBoolean())
				continue;
			File outputdir = new File(datadir, MAP_TYPES[i]);
			if(outputdir.isDirectory()) {
				boolean cancelled = !IJ.showMessageWithCancel("Overwrite",
						outputdir + " already exists. Overwrite?");
				if(cancelled)
					return;
			} else {
				outputdir.mkdir();
			}

			try {
				createProjections(objfile.getAbsolutePath(), datadir.getAbsolutePath(), initial, i, outputdir.getAbsolutePath(), doCoast, doLines);
			} catch(Exception e) {
				IJ.error(e.getMessage());
				e.printStackTrace();
			}
		}
	}

	public void createProjections(String objfile, String datadir, Matrix4f initial, int mapType, String outputdir, boolean doCoast, boolean doLines) throws IOException {
		SphericalMaxProjection smp = new SphericalMaxProjection(objfile, initial);
		createProjections(smp, datadir, mapType, outputdir, doCoast, doLines);
	}

	public void createProjections(final SphericalMaxProjection smp, final String datadir, final int mapType, final String outputdir, final boolean doCoast, final boolean doLines) throws IOException {
		final GeneralProjProjection proj;
		switch(mapType) {
			case MERCATOR:              proj = new GeneralProjProjection(new MercatorProjection());   break;
			case GALLPETER:             proj = new GeneralProjProjection(new GallProjection()); break;
			case KAVRAYSKIY:            proj = new GeneralProjProjection(new Kavraisky7Projection()); break;
			case WINKEL_TRIPEL:         proj = new GeneralProjProjection(new WinkelTripelProjection()); break;
			case AUGUSTUS_EPICYCLOIDAL: proj = new GeneralProjProjection(new AugustProjection()); break;
			case BONNE:                 proj = new GeneralProjProjection(new BonneProjection()); break;
			case ORTHO_AZIMUTAL:        proj = new GeneralProjProjection(new OrthographicAzimuthalProjection()); break;
			default: throw new IllegalArgumentException("Unsupported map type: " + mapType);
		}
		proj.prepareForProjection(smp);

		if(doCoast) {
			// create a postscript file with the coastline
			GeneralPath coast = GeneralProjProjection.readDatFile("/Users/bschmid/PostDoc/paper/SphereProj/figure2/coast.dat");
			coast = proj.transform(coast);
			GeneralProjProjection.savePath(coast, outputdir + File.separator + "coastline.ps", proj.getWidth(), proj.getHeight(), true);
		}

		if(doLines) {
			// create a postscript file with the lines
			GeneralPath lines = proj.createLines();
			lines = proj.transform(lines);
			GeneralProjProjection.savePath(lines, outputdir + File.separator + "lines.ps", proj.getWidth(), proj.getHeight(), false);
		}

		// collect files
		List<String> tmp = new ArrayList<String>();
		tmp.addAll(Arrays.asList(new File(datadir).list()));
		for(int i = tmp.size() - 1; i >= 0; i--)
			if(!tmp.get(i).endsWith(".vertices"))
				tmp.remove(i);
		final String[] files = new String[tmp.size()];
		tmp.toArray(files);
		for(int i = 0; i < files.length; i++)
			files[i] = datadir + File.separator + files[i];
		Arrays.sort(files);

		final int nProcessors = Runtime.getRuntime().availableProcessors();
		ExecutorService exec = Executors.newFixedThreadPool(nProcessors);
		int nFilesPerThread = (int)Math.ceil(files.length / (double)nProcessors);

		final int nVertices = smp.getSphere().nVertices;

		for(int p = 0; p < nProcessors; p++) {
			final int start = p * nFilesPerThread;
			final int end = Math.min(files.length, (p + 1) * nFilesPerThread);

			exec.submit(new Runnable() {
				@Override
				public void run() {
					for(int f = start; f < end; f++) {
						String file = files[f];
						try {
							String filename = new File(file).getName();
							filename = filename.substring(0, filename.length() - 9) + ".tif";
							float[] maxima = SphericalMaxProjection.loadFloatData(file, nVertices);
							IJ.save(new ImagePlus("", proj.project(maxima)), outputdir + File.separator + filename);
						} catch(Exception e) {
							System.err.println("Cannot project " + file);
							e.printStackTrace();
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
}
