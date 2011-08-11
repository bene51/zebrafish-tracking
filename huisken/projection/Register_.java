package huisken.projection;

import fiji.util.gui.GenericDialogPlus;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;

import ij.plugin.PlugIn;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.vecmath.Matrix4f;


public class Register_ implements PlugIn {

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

	public void register(String dataDirectory, String outputDirectory) throws IOException {
		// check and create files and folders
		if(!new File(dataDirectory).isDirectory())
			throw new IllegalArgumentException(dataDirectory + " is not a directory");

		File outputdir = new File(outputDirectory);
		if(outputdir.isDirectory()) {
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


		// obtain list of vertices files
		List<String> tmp = new ArrayList<String>();
		tmp.addAll(Arrays.asList(new File(dataDirectory).list()));
		for(int i = tmp.size() - 1; i >= 0; i--)
			if(!tmp.get(i).endsWith(".vertices"))
				tmp.remove(i);
		String[] files = new String[tmp.size()];
		tmp.toArray(files);
		Arrays.sort(files);
		if(!dataDirectory.endsWith(File.separator))
			dataDirectory += File.separator;
		if(!outputDirectory.endsWith(File.separator))
			outputDirectory += File.separator;

		// load spherical maximum projection for source and reference
		SphericalMaxProjection src = new SphericalMaxProjection(objfile.getAbsolutePath());
		SphericalMaxProjection tgt = new SphericalMaxProjection(objfile.getAbsolutePath());
		tgt.loadMaxima(dataDirectory + files[0]);

		// save sphere
		tgt.saveSphere(outputDirectory + "Sphere.obj");

		Matrix4f mat = new Matrix4f();
		mat.setIdentity();

		// register
		for(int i = 1; i < files.length; i++) {
			src.loadMaxima(dataDirectory + files[i]);
			new ICPRegistration(tgt, src).register(mat);
			src.saveMaxima(outputDirectory + files[i]);
			IJ.showProgress(i, files.length);
		}
		IJ.showProgress(1);
	}
}
