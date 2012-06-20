package huisken.projection;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.io.File;

public class Create_Overlay implements PlugIn {

	public static final int LINECOLOR = new Color(255, 129, 93).getRGB();

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

			ImageProcessor ip = null, co = null, li = null;
			ip = IJ.openImage(f.getAbsolutePath()).getProcessor();

			try {
				co = IJ.openImage(new File(contribDir, name).getAbsolutePath()).getProcessor();
			}catch(Exception e) {
				co = new ColorProcessor(ip.getWidth(), ip.getHeight());
			}

			try {
				li = IJ.openImage(new File(linesDir, name).getAbsolutePath()).getProcessor();
			} catch (Exception e) {}

			ip.setMinAndMax(min, max);
			ip = ip.convertToByte(true);

			int len = ip.getWidth() * ip.getHeight();
			for(int i = 0; i < len; i++) {
				int v = ip.get(i);
				try {
					if(li != null && li.get(i) == 255) {
						co.set(i, LINECOLOR);
						int tmp = blend(0.3, LINECOLOR, v);
						co.set(i, tmp);
						continue;
					}
				} catch(Exception e) {}

				// v == 0 : 0.5
				// v == 255: 0
				double c = 1 - (255/2.0 + v/2.0) / 255.0;
				try {
					int rgb = co.get(i);
					co.set(i, blend(c, rgb, v));
					continue;
				} catch(Exception e) {}

				co.set(i, v);
			}
			IJ.save(new ImagePlus("", co), outfile.getAbsolutePath());
		}
	}

	private static int blend(double f, int rgb, int v) {
		int r = (rgb & 0xff0000) >> 16;
		int g = (rgb & 0xff00) >> 8;
		int b = (rgb & 0xff);
		r = Math.min(255 , (int)Math.round((f * r) + (1-f) * v));
		g = Math.min(255 , (int)Math.round((f * g) + (1-f) * v));
		b = Math.min(255 , (int)Math.round((f * b) + (1-f) * v));
		rgb = (r << 16) + (g << 8) + b;
		return rgb;
	}
}
