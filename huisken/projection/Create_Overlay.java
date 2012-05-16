package huisken.projection;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.io.File;

public class Create_Overlay implements PlugIn {

	@Override
	public void run(String arg0) {
		GenericDialogPlus gd = new GenericDialogPlus("Create 2D Maps");
		gd.addDirectoryField("Data directory", "");
		gd.addNumericField("min", 120, 0);
		gd.addNumericField("max", 450, 0);
		gd.showDialog();
		if(gd.wasCanceled())
			return;

		try {
			createOverlay(new File(gd.getNextString()), (int)gd.getNextNumber(), (int)gd.getNextNumber());
		} catch(Exception e) {
			e.printStackTrace();
			IJ.error(e.getMessage());
		}


	}

	public static void createOverlay(File datadir, int min, int max) {
		if(!datadir.isDirectory()) {
			IJ.error(datadir + " is not a directory");
			return;
		}

		File contribDir = new File(datadir, "contributions");
		File linesDir = new File(datadir, "lines");
		File outdir = new File(datadir, "overlay");
		outdir.mkdir();

		int cnt = 0, tot = datadir.list().length;
		for(File f : datadir.listFiles()) {
			String name = f.getName();
			IJ.showProgress(++cnt, tot);
			if(!name.startsWith("tp") || !name.endsWith(".tif"))
				continue;
			File outfile = new File(outdir, name);
			if(outfile.exists())
				continue;

			ImageProcessor ip = IJ.openImage(f.getAbsolutePath()).getProcessor();
			ImageProcessor co = IJ.openImage(new File(contribDir, name).getAbsolutePath()).getProcessor();
			ImageProcessor li = IJ.openImage(new File(linesDir, name).getAbsolutePath()).getProcessor();

			ip.setMinAndMax(min, max);
			ip = ip.convertToByte(true);

			int len = ip.getWidth() * ip.getHeight();
			for(int i = 0; i < len; i++) {
				int rgb = co.get(i);
				int r = (rgb & 0xff0000) >> 16;
				int g = (rgb & 0xff00) >> 8;
				int b = (rgb & 0xff);
				int v = ip.get(i);
				int l = li.get(i);

				if(l == 255)
					rgb = (255 << 16) + (57 << 8) + 0;
				else {
					// v == 0 : 0.5
					// v == 255: 0
					double c = 1 - (255/2.0 + v/2.0) / 255.0;
					r = Math.min(255 , (int)Math.round((c * r) + (1-c) * v));
					g = Math.min(255 , (int)Math.round((c * g) + (1-c) * v));
					b = Math.min(255 , (int)Math.round((c * b) + (1-c) * v));
					rgb = (r << 16) + (g << 8) + b;
				}

				co.set(i, rgb);
			}
			IJ.save(new ImagePlus("", co), outfile.getAbsolutePath());
		}
	}
}
