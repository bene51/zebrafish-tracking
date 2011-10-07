package huisken.projection;

import fiji.util.gui.GenericDialogPlus;

import ij.IJ;
import ij.ImagePlus;

import ij.plugin.PlugIn;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class Map_Projection implements PlugIn {

	public static final int MERCATOR  = 0;
	public static final int GALLPETER = 1;
	public static final int FULLER    = 2;
	public static final int POLAR     = 3;

	public static final String[] MAP_TYPES = new String[] {"Mercator", "Gall-Peter", "Fuller", "Polar transform"};

	public void run(String arg) {
		GenericDialogPlus gd = new GenericDialogPlus("Create 2D Maps");
		gd.addDirectoryField("Data directory", "");
		gd.addDirectoryField("Output directory", "");
		gd.addChoice("Map type", MAP_TYPES, MAP_TYPES[0]);
		gd.addNumericField("Target width", 700, 0);
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		File datadir = new File(gd.getNextString());
		File outputdir = new File(gd.getNextString());
		int mapType = gd.getNextChoiceIndex();
		int width = (int)gd.getNextNumber();

		if(!datadir.isDirectory()) {
			IJ.error(datadir + " is not a directory");
			return;
		}

		if(outputdir.isDirectory()) {
			boolean cancelled = !IJ.showMessageWithCancel("Overwrite",
					outputdir + " already exists. Overwrite?");
			if(cancelled)
				return;
		} else {
			outputdir.mkdir();
		}

		File objfile = new File(datadir, "Sphere.obj");
		if(!objfile.exists()) {
			IJ.error("Cannot find " + objfile.getAbsolutePath());
			return;
		}
		try {
			createProjections(objfile.getAbsolutePath(), datadir.getAbsolutePath(), mapType, width, outputdir.getAbsolutePath());
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public void createProjections(String objfile, String datadir, int mapType, int width, String outputdir) throws IOException {
		SphericalMaxProjection smp = new SphericalMaxProjection(objfile);
		createProjections(smp, datadir, mapType, width, outputdir);
	}

	public void createProjections(SphericalMaxProjection smp, String datadir, int mapType, int width, String outputdir) throws IOException {
		MapProjection proj = null;
		switch(mapType) {
			case MERCATOR:  proj = new MercatorProjection();   break;
			case GALLPETER: proj = new GallPetersProjection(); break;
			case FULLER:    proj = new FullerProjection();     break;
			case POLAR:     proj = new PolarTransform();       break;
			default: throw new IllegalArgumentException("Unsupported map type: " + mapType);
		}
		proj.prepareForProjection(smp, width);

		// collect files
		List<String> tmp = new ArrayList<String>();
		tmp.addAll(Arrays.asList(new File(datadir).list()));
		for(int i = tmp.size() - 1; i >= 0; i--)
			if(!tmp.get(i).endsWith(".vertices"))
				tmp.remove(i);
		String[] files = new String[tmp.size()];
		tmp.toArray(files);
		for(int i = 0; i < files.length; i++)
			files[i] = datadir + File.separator + files[i];
		Arrays.sort(files);

		for(String file : files) {
			smp.loadMaxima(file);
			String filename = new File(file).getName();
			filename = filename.substring(0, filename.length() - 9) + ".tif";
			IJ.save(new ImagePlus("Mercator", proj.project()), outputdir + File.separator + filename);
		}
	}
}
