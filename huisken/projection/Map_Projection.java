package huisken.projection;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

import java.awt.geom.GeneralPath;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
		gd.addDirectoryField("Output directory", "/Volumes/BENE/PostDoc/SphereProjection/registered");
		gd.addChoice("Map type", MAP_TYPES, MAP_TYPES[3]);
		gd.showDialog();
		if(gd.wasCanceled())
			return;
		File datadir = new File(gd.getNextString());
		File outputdir = new File(gd.getNextString());
		int mapType = gd.getNextChoiceIndex();

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
			createProjections(objfile.getAbsolutePath(), datadir.getAbsolutePath(), mapType, outputdir.getAbsolutePath());
		} catch(Exception e) {
			IJ.error(e.getMessage());
			e.printStackTrace();
		}
	}

	public void createProjections(String objfile, String datadir, int mapType, String outputdir) throws IOException {
		SphericalMaxProjection smp = new SphericalMaxProjection(objfile);
		createProjections(smp, datadir, mapType, outputdir);
	}

	public void createProjections(SphericalMaxProjection smp, String datadir, int mapType, String outputdir) throws IOException {
		GeneralProjProjection proj = null;
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

		// create a postscript file with the coastline
		GeneralPath coast = GeneralProjProjection.readDatFile("/Users/bschmid/PostDoc/paper/SphereProj/figure2/coast.dat");
		coast = proj.transform(coast);
		GeneralProjProjection.savePath(coast, outputdir + File.separator + "coastline.ps", proj.getWidth(), proj.getHeight(), true);

		// create a postscript file with the lines
		GeneralPath lines = proj.createLines();
		lines = proj.transform(lines);
		GeneralProjProjection.savePath(lines, outputdir + File.separator + "lines.ps", proj.getWidth(), proj.getHeight(), false);
		
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
			IJ.save(new ImagePlus("", proj.project()), outputdir + File.separator + filename);
		}
	}
}
